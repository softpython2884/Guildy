package dev.guildy.wave;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildEvents.WaveOutcome.Result;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Night detection, wave start/stop and global event handling. Work is bounded and staggered: every tick handles
 * the sessions and guilds of one of 20 phases, so each guild is processed once per second.
 */
public final class WaveManager {
	/** guild id -> running wave. */
	private static final Map<UUID, WaveSession> SESSIONS = new LinkedHashMap<>();
	/** mob uuid -> wave tracking it. */
	private static final Map<UUID, WaveSession> MOB_INDEX = new HashMap<>();
	private static final Map<WaveSession, Integer> ERRORS = new HashMap<>();
	private static final Set<String> WARNED_ENTITIES = new HashSet<>();
	private static final SplittableRandom RANDOM = new SplittableRandom();
	private static final int MAX_CONSECUTIVE_ERRORS = 10;
	private static List<Guild> snapshot = List.of();
	private static long ticks;
	private static long seconds;
	private static int nextSlot;

	private WaveManager() {
	}

	static void init() {
		Scheduler.every("wave.tick", 1, 0, WaveManager::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> Safe.run("wave.death", () -> onAfterDeath(entity)));
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, params) ->
				Safe.run("wave.conversion", () -> onConversion(previous, converted, params)));
		// Restart safety: wave mobs are never persisted with their session; leftovers are refused at load.
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) -> Safe.get("wave.load", () -> allowLoad(entity), true));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Safe.run("wave.load", () -> onEntityLoad(entity)));
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> Safe.get("wave.sleep", () -> onAllowSleep(player, pos), null));
		GuildEvents.DISBANDED.add(guild -> {
			WaveSession session = SESSIONS.get(guild.id);
			if (session != null) {
				session.finish(Result.CANCELLED, "wave.reason.disbanded");
			}
		});
		GuildEvents.TERRITORY_CHANGED.add(WaveManager::onTerritoryChanged);
		Lifecycle.STARTED.add(server -> reset());
		Lifecycle.STOPPING.add(server -> stopAll("wave.reason.server"));
	}

	// ================================================================== queries

	public static boolean isActive(Guild guild) {
		return SESSIONS.containsKey(guild.id);
	}

	static @Nullable WaveSession session(Guild guild) {
		return SESSIONS.get(guild.id);
	}

	static int totalAlive() {
		int total = 0;
		for (WaveSession session : SESSIONS.values()) {
			total += session.alive();
		}
		return total;
	}

	static void indexMob(UUID mob, WaveSession session) {
		MOB_INDEX.put(mob, session);
	}

	static void unindexMob(UUID mob) {
		MOB_INDEX.remove(mob);
	}

	static void unregister(WaveSession session) {
		SESSIONS.remove(session.guildId(), session);
		ERRORS.remove(session);
	}

	static void warnInvalidEntity(String id) {
		if (WARNED_ENTITIES.add(id)) {
			Guildy.LOGGER.warn("[waves] Entity '{}' cannot be spawned (unknown id or not a mob): removed from the wave", id);
		}
	}

	/** Overworld clock (ticks), 0 when no server. */
	static long clock() {
		ServerLevel level = GuildyServer.claimLevel();
		return level == null ? 0 : level.getOverworldClockTime();
	}

	static boolean advanceTime() {
		ServerLevel level = GuildyServer.claimLevel();
		return level != null && level.getGameRules().get(GameRules.ADVANCE_TIME);
	}

	// ================================================================== tick

	private static void tick() {
		ticks++;
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null || !level.tickRateManager().runsNormally()) {
			return;
		}
		int phase = (int) (ticks % 20);
		if (phase == 0) {
			seconds++;
			snapshot = new ArrayList<>(GuildManager.all());
		}
		long clock = level.getOverworldClockTime();
		boolean advanceTime = level.getGameRules().get(GameRules.ADVANCE_TIME);
		if (!SESSIONS.isEmpty()) {
			for (WaveSession session : new ArrayList<>(SESSIONS.values())) {
				if (session.slot() == phase) {
					tickSession(session, level, clock, advanceTime);
				}
			}
		}

		WaveConfig cfg = WaveModule.config();
		if (!cfg.enabled || !advanceTime || snapshot.isEmpty() || level.getDifficulty() == Difficulty.PEACEFUL
				|| !WaveFormula.inStartWindow(cfg.timing, WaveFormula.timeOfDay(clock))) {
			return;
		}
		for (int i = phase; i < snapshot.size(); i += 20) {
			Guild guild = snapshot.get(i);
			int index = i;
			Safe.run("wave.start", () -> checkStart(level, guild, clock, index));
		}
	}

	/** A session failing repeatedly is aborted so that a bug never leaves a horde running forever. */
	private static void tickSession(WaveSession session, ServerLevel level, long clock, boolean advanceTime) {
		boolean ok = Safe.get("wave.session", () -> {
			session.secondTick(level, clock, advanceTime);
			return true;
		}, false);
		if (ok) {
			ERRORS.remove(session);
			return;
		}
		int errors = ERRORS.merge(session, 1, Integer::sum);
		if (errors >= MAX_CONSECUTIVE_ERRORS) {
			Guildy.LOGGER.error("[waves] Aborting the wave of {} after {} consecutive errors", session.guild().name, errors);
			Safe.run("wave.abort", () -> session.finish(Result.CANCELLED, "wave.reason.error"));
			unregister(session);
			Safe.run("wave.abort", () -> session.emergencyCleanup(level));
			MOB_INDEX.values().removeIf(value -> value == session);
		}
	}

	private static void checkStart(ServerLevel level, Guild guild, long clock, int index) {
		if (SESSIONS.containsKey(guild.id) || guild.heart == null || GuildManager.get(guild.id) != guild || !guild.owns(guild.heartChunk())) {
			return; // (the snapshot may still hold a guild disbanded during this second)
		}
		WaveConfig cfg = WaveModule.config();
		long day = WaveFormula.dayIndex(clock);
		if (guild.waves.lastWaveDay == day) {
			return; // at most one wave per night
		}
		WaveTruce.Night truce = WaveTruce.spareTonight(cfg.truce, guild.waves, day);
		if (truce != WaveTruce.Night.NONE) {
			if (truce == WaveTruce.Night.SPARED_NOW) {
				GuildManager.markDirty();
				if (guild.waves.truceNights > 0) {
					Msg.guild(guild, "wave.truce.night", guild.waves.truceNights);
				} else {
					Msg.guild(guild, "wave.truce.last");
				}
			}
			return; // truce after repeated defeats: no horde tonight
		}
		if ((seconds + index) % cfg.timing.startRetrySeconds != 0) {
			return;
		}
		int nearby = nearbyMembers(level, guild);
		if (nearby <= 0) {
			return; // nobody home: retried until lateStartUntil, never an offline raid
		}
		startWave(level, guild, nearby, false, clock);
	}

	/** Online members (not spectating) in the claim level within activationDistance of the heart or inside the territory. */
	static int nearbyMembers(@Nullable ServerLevel level, Guild guild) {
		if (level == null || guild.heart == null) {
			return 0;
		}
		BlockPos heart = guild.heart;
		double max = WaveModule.config().timing.activationDistance;
		max *= max;
		int count = 0;
		for (ServerPlayer player : guild.onlineMembers()) {
			if (player.level() != level || player.isSpectator()) {
				continue;
			}
			if (player.distanceToSqr(heart.getX() + 0.5, heart.getY() + 0.5, heart.getZ() + 0.5) <= max
					|| guild.owns(ChunkKey.ofBlock(player.getBlockX(), player.getBlockZ()))) {
				count++;
			}
		}
		return count;
	}

	/** How a wave was started. TEST and EVENT waves are "timed": they ignore the day/night clock and end after a duration. */
	enum Kind {
		/** Natural night horde. */
		NIGHT,
		/** Admin test wave (waves.json5 test settings). */
		TEST,
		/** Started by another module through {@link WaveApi#startEventWave} (fixed theme, never changes the night step). */
		EVENT
	}

	/**
	 * Start request. {@code theme == null}: drawn among the night themes; {@code modifiers == null}: asked to
	 * {@link WaveApi#nightModifiers} (night and test waves).
	 */
	record Request(Kind kind, WaveConfig.@Nullable Theme theme, int durationSeconds, @Nullable NightModifiers modifiers, String eventId) {
		static final Request NIGHT = new Request(Kind.NIGHT, null, 0, null, "");
		static final Request TEST = new Request(Kind.TEST, null, 0, null, "");

		static Request event(WaveApi.EventWave wave) {
			return new Request(Kind.EVENT, wave.theme(), Math.max(10, wave.durationSeconds()), wave.modifiers(), wave.eventId());
		}
	}

	/**
	 * Starts a wave now. Test waves (admin) ignore the time of day and end after test.durationSeconds.
	 *
	 * @return the session, or null when no valid theme / mob could be planned
	 */
	static @Nullable WaveSession startWave(ServerLevel level, Guild guild, int nearby, boolean test, long clock) {
		return startWave(level, guild, nearby, test ? Request.TEST : Request.NIGHT, clock);
	}

	static @Nullable WaveSession startWave(ServerLevel level, Guild guild, int nearby, Request request, long clock) {
		if (guild.heart == null || SESSIONS.containsKey(guild.id)) {
			return null;
		}
		WaveConfig cfg = WaveModule.config();
		int night = guild.waves.night;
		int players = Math.max(1, nearby);
		double difficulty = WaveFormula.difficulty(cfg.difficulty, night, guild.level, players);
		long day = WaveFormula.dayIndex(clock);
		long timeOfDay = WaveFormula.timeOfDay(clock);
		boolean inNight = WaveFormula.inNight(cfg.timing, timeOfDay);
		NightModifiers modifiers = request.modifiers() != null ? request.modifiers()
				: Safe.get("wave.modifiers", () -> WaveApi.nightModifiers.apply(guild, clock), NightModifiers.NONE);
		if (modifiers == null) {
			modifiers = NightModifiers.NONE;
		}
		// Event modifiers may add a boss (blood night: high boss chance); event themes ignore the boss-night schedule.
		boolean bossNight = request.theme() == null && WaveFormula.isScheduledBossNight(cfg.difficulty, night)
				|| modifiers.bossChance() > 0 && RANDOM.nextDouble() < modifiers.bossChance();

		WaveConfig.Theme theme = request.theme() != null ? request.theme() : WavePlanner.pickTheme(cfg.themes, night, bossNight, RANDOM);
		WavePlanner.Plan plan = theme == null ? null
				: WavePlanner.plan(cfg, theme, difficulty, bossNight, WaveSpawner::isValidEntity, RANDOM, modifiers);
		if (plan == null || plan.mobs().isEmpty()) {
			Guildy.LOGGER.warn("[waves] No valid theme/mob for guild {} at night step {} (check the theme of {})", guild.name, night,
					request.kind() == Kind.EVENT ? "event '" + request.eventId() + "'" : "waves.json5");
			if (request.kind() == Kind.NIGHT) {
				guild.waves.lastWaveDay = day; // do not retry every few seconds
				GuildManager.markDirty();
			}
			return null;
		}

		double phaseSeconds;
		if (request.kind() != Kind.NIGHT) {
			int duration = request.kind() == Kind.TEST ? cfg.test.durationSeconds : request.durationSeconds();
			phaseSeconds = Math.min(cfg.spawning.spawnPhaseSeconds, duration * 0.6);
		} else {
			double nightLeft = Math.max(20, cfg.timing.nightEnd - timeOfDay) / 20.0;
			phaseSeconds = Math.min(cfg.spawning.spawnPhaseSeconds, nightLeft * 0.7);
		}
		WaveSession session = new WaveSession(guild, plan, difficulty, night, players, request, modifiers, nextSlot(), clock, phaseSeconds);
		SESSIONS.put(guild.id, session);
		if (request.kind() == Kind.NIGHT || request.kind() == Kind.TEST && inNight) {
			guild.waves.lastWaveDay = day;
		}
		GuildManager.markDirty();
		Safe.run("wave.start", () -> session.start(level));
		GuildEvents.fire(GuildEvents.WAVE_STARTED, guild);
		return session;
	}

	private static int nextSlot() {
		int slot = nextSlot;
		nextSlot = (nextSlot + 7) % 20; // 7 is coprime with 20: cycles through every phase
		return slot;
	}

	/** Cancels the wave of a guild (admin). */
	static boolean stop(Guild guild, String reasonKey) {
		WaveSession session = SESSIONS.get(guild.id);
		if (session == null) {
			return false;
		}
		session.finish(Result.CANCELLED, reasonKey);
		return true;
	}

	private static void stopAll(String reasonKey) {
		for (WaveSession session : new ArrayList<>(SESSIONS.values())) {
			Safe.run("wave.stop", () -> session.finish(Result.CANCELLED, reasonKey));
		}
		SESSIONS.clear();
		MOB_INDEX.clear();
		ERRORS.clear();
	}

	private static void reset() {
		SESSIONS.clear();
		MOB_INDEX.clear();
		ERRORS.clear();
		snapshot = List.of();
	}

	// ================================================================== events

	private static void onAfterDeath(LivingEntity entity) {
		if (!(entity instanceof Mob mob)) {
			return;
		}
		WaveSession session = MOB_INDEX.get(mob.getUUID());
		if (session == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		TrackedMob tracked = session.tracked(mob.getUUID());
		if (tracked == null) {
			unindexMob(mob.getUUID());
			return;
		}
		session.onMobDeath(level, mob, tracked);
	}

	private static void onConversion(Mob previous, Mob converted, ConversionParams params) {
		WaveSession session = MOB_INDEX.get(previous.getUUID());
		if (session == null) {
			return;
		}
		// Single conversions (zombie -> drowned...) keep counting as the same wave mob. Split-on-death children
		// (magma cubes) inherit the tags but are not tracked: ALLOW_LOAD refuses them (no extra mobs).
		if (params.type() == ConversionType.SINGLE) {
			session.transfer(previous, converted);
		}
	}

	private static boolean allowLoad(Entity entity) {
		return !entity.entityTags().contains(WaveSpawner.TAG) || MOB_INDEX.containsKey(entity.getUUID());
	}

	/**
	 * Tracked mob (re)loaded: (re)attach its steering goal (goals are not persisted). Untracked leftover loaded anyway
	 * (should be refused by ALLOW_LOAD): discarded on the next tick.
	 */
	private static void onEntityLoad(Entity entity) {
		if (!entity.entityTags().contains(WaveSpawner.TAG)) {
			return;
		}
		WaveSession session = MOB_INDEX.get(entity.getUUID());
		if (session != null) {
			if (entity instanceof Mob mob && session.isRunning()) {
				WaveSpawner.attachSteering(mob, session);
			}
			return;
		}
		Scheduler.later("wave.cleanup", 1, () -> {
			if (!entity.isRemoved() && !MOB_INDEX.containsKey(entity.getUUID())) {
				entity.discard();
			}
		});
	}

	private static Player.@Nullable BedSleepingProblem onAllowSleep(Player player, BlockPos pos) {
		if (!(player instanceof ServerPlayer serverPlayer) || SESSIONS.isEmpty()) {
			return null;
		}
		Guild own = GuildManager.of(serverPlayer);
		boolean denied = own != null && SESSIONS.containsKey(own.id);
		if (!denied) {
			Guild at = GuildManager.at(serverPlayer.level(), pos);
			denied = at != null && SESSIONS.containsKey(at.id);
		}
		if (!denied) {
			return null;
		}
		Component message = Lang.get("wave.sleep.denied");
		serverPlayer.sendSystemMessage(dev.guildy.core.Msg.prefixed(message));
		return new Player.BedSleepingProblem(message);
	}

	private static void onTerritoryChanged(Guild guild) {
		WaveSession session = SESSIONS.get(guild.id);
		if (session == null) {
			return;
		}
		if (guild.heart == null || !guild.heart.equals(session.heart()) || !guild.owns(guild.heartChunk())) {
			session.finish(Result.CANCELLED, "wave.reason.territory");
		} else {
			session.markTerritoryChanged();
		}
	}

	// ================================================================== info (menus)

	/** Estimated difficulty of the guild's next wave (present members, at least 1). */
	static double estimateDifficulty(Guild guild) {
		int players = Math.max(1, nearbyMembers(GuildyServer.claimLevel(), guild));
		return WaveFormula.difficulty(WaveModule.config().difficulty, guild.waves.night, guild.level, players);
	}

	/** 2-3 lines for the main menu "waves" button (+ the event lines of {@link WaveApi#eventLore}). */
	static List<Component> statusLore(Guild guild) {
		List<Component> lines = new ArrayList<>();
		WaveSession session = SESSIONS.get(guild.id);
		WaveConfig cfg = WaveModule.config();
		if (session != null) {
			lines.add(Lang.get("wave.lore.active", session.displayName()));
			lines.add(Lang.get("wave.lore.progress", session.kills(), session.total(), session.heartPercent()));
		} else if (!cfg.enabled) {
			lines.add(Lang.get("wave.lore.disabled"));
		} else if (!guild.isFounded()) {
			lines.add(Lang.get("wave.lore.notfounded"));
		} else {
			double difficulty = estimateDifficulty(guild);
			List<Component> truce = truceLore(guild);
			lines.addAll(truce);
			lines.add(Lang.get("wave.lore.idle", WaveSession.formatDifficulty(difficulty)));
			lines.add(Lang.get("wave.lore.expected", WaveFormula.mobCount(cfg.difficulty, difficulty, 1.0)));
			lines.add(Lang.get("wave.lore.stats", guild.waves.victories, guild.waves.defeats));
		}
		lines.addAll(eventLore(guild));
		return lines;
	}

	/** "Trêve" line while a truce is running (empty otherwise). */
	static List<Component> truceLore(Guild guild) {
		WaveConfig cfg = WaveModule.config();
		long day = WaveFormula.dayIndex(clock());
		if (!WaveTruce.active(cfg.truce, guild.waves, day)) {
			return List.of();
		}
		return List.of(Lang.get("wave.lore.truce", WaveTruce.nightsLeft(guild.waves, day)));
	}

	/** A truce is running for this guild (no horde, and no raid when the config says so). */
	static boolean inTruce(Guild guild) {
		return WaveTruce.active(WaveModule.config().truce, guild.waves, WaveFormula.dayIndex(clock()));
	}

	/** Event lines from the event module (never fails). */
	static List<Component> eventLore(Guild guild) {
		List<Component> lines = Safe.get("wave.event_lore", () -> WaveApi.eventLore.apply(guild), List.of());
		return lines == null ? List.of() : lines;
	}
}
