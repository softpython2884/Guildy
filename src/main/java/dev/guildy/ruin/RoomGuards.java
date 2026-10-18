package dev.guildy.ruin;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.ruin.RuinConfig.GuardDef;
import dev.guildy.ruin.RuinConfig.MinionDef;
import dev.guildy.ruin.RuinConfig.RuinDef;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Room guards of the ruins: enemies posted in a dormant ruin when a survival / adventure player comes close (its box
 * inflated by {@code guards.triggerMargin}), at the {@code guildy:guard} markers or, in older templates, at fallback posts
 * (escort markers, just inside the entrance, a circle around the sleeping guardian), as many as the tier, the players
 * present and the threat level give ({@link RoomGuardRules#count}). Vanilla mobs of the ruin's {@code guards} definition
 * (escort types by default), persistent, tagged {@value #TAG} (never {@code guildy_ruin}: that tag belongs to the fights),
 * leashed to their post, attacking intruders with their vanilla AI, vanilla loot only.
 * <p>
 * Posted once per dormant period: killed guards stay dead until nobody has been near the ruin for its
 * {@code resetSeconds} (the survivors then leave and the next approach posts fresh ones) or until its guardian is defeated
 * (they leave with it). While guards live in the arena, the incantation is refused and the proximity awakening waits
 * ({@link #blocking}). Runtime only: a restart forgets them, their entities are refused at load. Runs once per second,
 * bounded by the players online and the guarded ruins.
 */
public final class RoomGuards {
	/** Tag of every room guard. */
	public static final String TAG = "guildy_ruin_guard";
	private static final Map<String, Watch> WATCHES = new LinkedHashMap<>();
	private static final Map<UUID, Watch> BY_GUARD = new HashMap<>();
	private static final Map<UUID, Long> REMINDED = new HashMap<>();
	private static final SplittableRandom RANDOM = new SplittableRandom();
	private static final int REMIND_TICKS = 60;

	/** The guards of one ruin during its current dormant period. */
	static final class Watch {
		final String ruinKey;
		final ResourceKey<Level> dimension;
		/** Living guards (loaded or not) and their post. */
		final Map<UUID, BlockPos> posts = new LinkedHashMap<>();
		/** The distinct posts used. */
		final List<BlockPos> postList = new ArrayList<>();
		int posted;
		int killed;
		int secondsAlone;

		Watch(String ruinKey, ResourceKey<Level> dimension) {
			this.ruinKey = ruinKey;
			this.dimension = dimension;
		}
	}

	private RoomGuards() {
	}

	static void init() {
		Scheduler.every("ruin.guards", 20, 11, RoomGuards::tickSecond);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!BY_GUARD.isEmpty()) {
				Safe.run("ruin.guards.death", () -> onDeath(entity));
			}
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, params) -> {
			if (!BY_GUARD.isEmpty()) {
				Safe.run("ruin.guards.conversion", () -> onConversion(previous, converted, params));
			}
		});
		// Restart / unload safety: a guard is never kept without its watch; leftovers are refused at load.
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) -> Safe.get("ruin.guards.load", () -> allowLoad(entity), true));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Safe.run("ruin.guards.load", () -> onEntityLoad(entity)));
		Lifecycle.STOPPING.add(server -> Safe.run("ruin.guards.stop", RoomGuards::stopAll));
	}

	// ================================================================== queries

	/** Guards were posted in this ruin during its current dormant period (alive or not). */
	public static boolean posted(RuinRecord ruin) {
		return WATCHES.containsKey(ruin.key);
	}

	/** Living guards of the ruin (loaded or not). */
	public static int alive(RuinRecord ruin) {
		Watch watch = WATCHES.get(ruin.key);
		return watch == null ? 0 : watch.posts.size();
	}

	/** Guards killed in the ruin during its current dormant period. */
	public static int killed(RuinRecord ruin) {
		Watch watch = WATCHES.get(ruin.key);
		return watch == null ? 0 : watch.killed;
	}

	/** Loaded, living guards of the ruin. */
	public static List<Mob> guards(RuinRecord ruin) {
		Watch watch = WATCHES.get(ruin.key);
		MinecraftServer server = GuildyServer.server();
		ServerLevel level = watch == null || server == null ? null : server.getLevel(watch.dimension);
		if (level == null) {
			return List.of();
		}
		List<Mob> list = new ArrayList<>();
		for (UUID uuid : watch.posts.keySet()) {
			if (level.getEntity(uuid) instanceof Mob mob && mob.isAlive()) {
				list.add(mob);
			}
		}
		return list;
	}

	/** A mob of the ruins: a guardian, an escort mob (tag guildy_ruin) or a room guard. */
	public static boolean isRuinMob(@Nullable Entity entity) {
		return entity != null && (entity.entityTags().contains(BossFactory.TAG) || entity.entityTags().contains(TAG));
	}

	/** The post of a guard, or null when it is not a tracked guard. */
	public static @Nullable BlockPos postOf(Mob guard) {
		Watch watch = BY_GUARD.get(guard.getUUID());
		return watch == null ? null : watch.posts.get(guard.getUUID());
	}

	/** The distinct posts used in the current dormant period (empty when no guard was posted). */
	public static List<BlockPos> posts(RuinRecord ruin) {
		Watch watch = WATCHES.get(ruin.key);
		return watch == null ? List.of() : List.copyOf(watch.postList);
	}

	/**
	 * Living guards inside the arena (incantation radius around the sleeping guardian) that keep it asleep: 0 when
	 * {@code guards.blockRitual} is off.
	 */
	public static int blocking(ServerLevel level, RuinRecord ruin) {
		RuinConfig cfg = RuinModule.config();
		Watch watch = WATCHES.get(ruin.key);
		if (watch == null || !cfg.guards.blockRitual || level.dimension() != watch.dimension) {
			return 0;
		}
		Vec3 center = Vec3.atBottomCenterOf(Incantations.marker(ruin));
		double radius = cfg.summon.arenaRadius;
		int inArena = 0;
		for (UUID uuid : watch.posts.keySet()) {
			if (level.getEntity(uuid) instanceof Mob mob && mob.isAlive() && mob.distanceToSqr(center) <= radius * radius) {
				inArena++;
			}
		}
		return RoomGuardRules.blocksRitual(cfg.guards.blockRitual, inArena) ? inArena : 0;
	}

	/** "Des sentinelles veillent encore…" in the action bar (proximity mode, at most every 3 seconds, never a count). */
	static void remind(ServerPlayer player, int guards) {
		long now = Scheduler.ticks();
		Long last = REMINDED.get(player.getUUID());
		if (last != null && now >= last && now - last < REMIND_TICKS) {
			return;
		}
		REMINDED.put(player.getUUID(), now);
		Msg.actionBar(player, "ruin.guards.sealing", guards);
	}

	// ================================================================== loop

	private static void tickSecond() {
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return;
		}
		RuinConfig cfg = RuinModule.config();
		for (Watch watch : new ArrayList<>(WATCHES.values())) {
			Safe.run("ruin.guards.update", () -> update(server, watch, cfg));
		}
		if (!cfg.guards.enabled || !cfg.bossesEnabled) {
			return;
		}
		for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
			Safe.run("ruin.guards.detect", () -> detect(player, cfg));
		}
		if (REMINDED.size() > 256) {
			REMINDED.clear();
		}
	}

	/** A survival / adventure player near a dormant ruin whose guards are not posted yet: they appear. */
	private static void detect(ServerPlayer player, RuinConfig cfg) {
		if (!intruder(player)) {
			return;
		}
		ServerLevel level = player.level();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		RuinRecord ruin = RuinIndex.containing(level.dimension(), player.getX(), player.getY(), player.getZ(), cfg.guards.triggerMargin);
		if (ruin == null || WATCHES.containsKey(ruin.key)) {
			return;
		}
		Encounters.rearmIfDue(level.getServer(), ruin);
		boolean fightOrRitual = Encounters.isActive(ruin) || Incantations.ritualRunning(ruin);
		if (RoomGuardRules.spawns(enabledFor(ruin, cfg), ruin.state, fightOrRitual, false, true)) {
			post(level, ruin, cfg);
		}
	}

	/** Guards of this ruin at all: guards enabled, and the ruin is known to ruins.json5 or generic ruins have guards. */
	private static boolean enabledFor(RuinRecord ruin, RuinConfig cfg) {
		return cfg.guards.enabled && (cfg.isKnown(ruin.ruinId) || cfg.generic.guards);
	}

	private static boolean intruder(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative();
	}

	private static List<ServerPlayer> intruders(ServerLevel level, RuinRecord ruin, int margin) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (intruder(player) && ruin.contains(player.getX(), player.getY(), player.getZ(), margin)) {
				list.add(player);
			}
		}
		return list;
	}

	private static void update(MinecraftServer server, Watch watch, RuinConfig cfg) {
		ServerLevel level = server.getLevel(watch.dimension);
		RuinRecord ruin = RuinIndex.get(watch.ruinKey);
		if (level == null || ruin == null) {
			dismiss(watch, level, false);
			return;
		}
		forgetGone(level, watch);
		if (!cfg.bossesEnabled || !enabledFor(ruin, cfg) || ruin.state == RuinState.CLEARED || level.getDifficulty() == Difficulty.PEACEFUL) {
			// Guardian defeated (or guards switched off): the remaining guards leave with it.
			dismiss(watch, level, true);
			return;
		}
		watch.secondsAlone = Encounters.present(level, ruin, cfg.encounter.presenceMargin).isEmpty() ? watch.secondsAlone + 1 : 0;
		if (RoomGuardRules.resets(watch.secondsAlone, Encounters.def(ruin).resetSeconds)) {
			dismiss(watch, level, false);
			return;
		}
		leash(level, watch, cfg);
	}

	/** Forgets the guards that died or vanished (a guard in an unloaded chunk is kept). */
	private static void forgetGone(ServerLevel level, Watch watch) {
		watch.posts.entrySet().removeIf(entry -> {
			Entity entity = level.getEntity(entry.getKey());
			boolean gone = entity == null ? level.isLoaded(entry.getValue()) && level.isPositionEntityTicking(entry.getValue())
					: entity.isRemoved() || entity instanceof LivingEntity living && !living.isAlive();
			if (gone) {
				BY_GUARD.remove(entry.getKey());
			}
			return gone;
		});
	}

	/** Guards stay near their post: too far, they walk back (chasing an intruder close to it excepted); lured away, brought back. */
	private static void leash(ServerLevel level, Watch watch, RuinConfig cfg) {
		double radius = cfg.guards.leashRadius;
		for (Map.Entry<UUID, BlockPos> entry : watch.posts.entrySet()) {
			if (!(level.getEntity(entry.getKey()) instanceof Mob mob) || !mob.isAlive()) {
				continue;
			}
			Vec3 home = Vec3.atBottomCenterOf(entry.getValue());
			LivingEntity target = mob.getTarget();
			boolean chasing = target != null && target.isAlive() && target.distanceToSqr(home) <= (radius + 4) * (radius + 4);
			switch (RoomGuardRules.leash(mob.distanceToSqr(home), radius, chasing)) {
				case TELEPORT -> {
					RuinFx.burst(level, ParticleTypes.POOF, mob.position().add(0, 0.6, 0), 8, 0.3, 0.02);
					mob.teleportTo(home.x, home.y, home.z);
					mob.getNavigation().stop();
					mob.setTarget(null);
					RuinFx.burst(level, ParticleTypes.SMOKE, home.add(0, 0.6, 0), 8, 0.3, 0.02);
				}
				case RETURN -> {
					mob.setTarget(null);
					mob.getNavigation().moveTo(home.x, home.y, home.z, 1.15);
				}
				case STAY -> {
				}
			}
		}
	}

	// ================================================================== posting

	/**
	 * Posts the guards of a ruin now (admin command: the previous ones leave first, whatever the period). @return the
	 * number of guards posted
	 */
	public static int postNow(ServerLevel level, RuinRecord ruin) {
		RuinConfig cfg = RuinModule.config();
		Watch old = WATCHES.get(ruin.key);
		if (old != null) {
			dismiss(old, level, true);
		}
		if (!cfg.bossesEnabled || !enabledFor(ruin, cfg) || ruin.state == RuinState.CLEARED || level.getDifficulty() == Difficulty.PEACEFUL) {
			// They would leave at once.
			return 0;
		}
		return post(level, ruin, cfg);
	}

	/** Spawns the guards of a ruin at their posts and starts watching them. @return the number of guards posted */
	private static int post(ServerLevel level, RuinRecord ruin, RuinConfig cfg) {
		RuinDef def = Encounters.def(ruin);
		int players = Math.max(1, intruders(level, ruin, cfg.guards.triggerMargin).size());
		int count = RoomGuardRules.count(def.guards.count, cfg.guards.countByTier, def.tier, players, cfg.guards.perExtraPlayer, Encounters.threatLevel(ruin),
				cfg.guards.threatLevelsPerGuard, cfg.guards.max);
		MinionDef spec = spec(def);
		if (count <= 0 || spec.types.isEmpty()) {
			return 0;
		}
		String probe = spec.types.keySet().iterator().next();
		List<BlockPos> posts = posts(level, ruin, cfg, probe);
		if (posts.isEmpty()) {
			return 0;
		}
		Watch watch = new Watch(ruin.key, level.dimension());
		// Marker posts in a random order (a few guards spread over the ruin), fallback posts by priority.
		List<Integer> assignment = RoomGuardRules.assign(posts.size(), count, ruin.guards.isEmpty() ? null : new Random(RANDOM.nextLong()));
		Component name = def.guards.name.isBlank() ? null : Component.literal(Lang.colorize(def.guards.name));
		for (int index : assignment) {
			BlockPos post = posts.get(index);
			UUID[] tracked = new UUID[1];
			Mob mob = BossFactory.spawnMinion(level, spec, post, RANDOM, m -> {
				// Guards are not part of a fight: their own tag (the fights refuse untracked guildy_ruin mobs at load).
				m.removeTag(BossFactory.TAG);
				m.addTag(TAG);
				if (name != null) {
					m.setCustomName(name);
					m.setCustomNameVisible(false);
				}
				m.setHomeTo(post, cfg.guards.leashRadius);
				tracked[0] = m.getUUID();
				watch.posts.put(m.getUUID(), post);
				BY_GUARD.put(m.getUUID(), watch);
				return true;
			});
			if (mob == null) {
				if (tracked[0] != null) {
					watch.posts.remove(tracked[0]);
					BY_GUARD.remove(tracked[0]);
				}
				continue;
			}
			watch.posted++;
			if (!watch.postList.contains(post)) {
				watch.postList.add(post);
			}
			RuinFx.burst(level, ParticleTypes.SMOKE, mob.position().add(0, 0.5, 0), 6, 0.3, 0.01);
		}
		if (watch.posts.isEmpty()) {
			return 0;
		}
		WATCHES.put(ruin.key, watch);
		String ruinName = Lang.colorize(def.name);
		for (ServerPlayer player : intruders(level, ruin, cfg.guards.triggerMargin)) {
			// No spoiler by default: a dull sound, then the hint of the chat at the entrance ("des sentinelles veillent…").
			if (cfg.guards.announceOnScreen) {
				Msg.actionBar(player, "ruin.guards.posted.screen", ruinName, watch.posts.size());
			}
			Msg.sound(player, SoundEvents.AMBIENT_CAVE, 0.8F, 0.7F);
		}
		return watch.posts.size();
	}

	/** Guard statistics: the ruin's {@code guards}, the escort's types / equipment / effects where left empty. */
	static MinionDef spec(RuinDef def) {
		GuardDef g = def.guards;
		MinionDef spec = new MinionDef();
		spec.types = g.types.isEmpty() ? def.minions.types : g.types;
		spec.equipment = g.equipment.isEmpty() ? def.minions.equipment : g.equipment;
		spec.effects = g.effects.isEmpty() ? def.minions.effects : g.effects;
		spec.healthMultiplier = g.healthMultiplier;
		spec.damageMultiplier = g.damageMultiplier;
		return spec;
	}

	/**
	 * Standable posts: the guard markers, else the fallback posts of {@link RoomGuardRules#fallbackPosts}; only in loaded
	 * chunks, inside the ruin.
	 */
	private static List<BlockPos> posts(ServerLevel level, RuinRecord ruin, RuinConfig cfg, String probe) {
		boolean markers = !ruin.guards.isEmpty();
		List<RoomGuardRules.Cell> cells = new ArrayList<>();
		if (markers) {
			for (BlockPos pos : ruin.guards) {
				cells.add(cell(pos));
			}
		} else {
			List<RoomGuardRules.Cell> minions = new ArrayList<>();
			for (BlockPos pos : ruin.minions) {
				minions.add(cell(pos));
			}
			cells = RoomGuardRules.fallbackPosts(cell(Incantations.marker(ruin)), ruin.entrance == null ? null : cell(ruin.entrance), minions, ruin.minX,
					ruin.minZ, ruin.maxX, ruin.maxZ, cfg.guards.fallbackRadius, cfg.guards.fallbackPoints);
		}
		List<BlockPos> posts = new ArrayList<>();
		for (RoomGuardRules.Cell c : cells) {
			BlockPos pos = new BlockPos(c.x(), c.y(), c.z());
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockPos spot = BossFactory.standable(level, pos, probe, markers ? 1 : 2);
			if (spot != null && !posts.contains(spot) && ruin.contains(spot, 1)) {
				posts.add(spot);
			}
		}
		return posts;
	}

	private static RoomGuardRules.Cell cell(BlockPos pos) {
		return new RoomGuardRules.Cell(pos.getX(), pos.getY(), pos.getZ());
	}

	// ================================================================== leaving

	/** Admin: the guards of the ruin leave now (fresh ones come at the next approach). @return true when some were posted */
	public static boolean reset(RuinRecord ruin) {
		Watch watch = WATCHES.get(ruin.key);
		if (watch == null) {
			return false;
		}
		MinecraftServer server = GuildyServer.server();
		dismiss(watch, server == null ? null : server.getLevel(watch.dimension), true);
		return true;
	}

	/** The period ends: the loaded guards vanish (unloaded ones are refused at load), the watch is forgotten. */
	private static void dismiss(Watch watch, @Nullable ServerLevel level, boolean effects) {
		WATCHES.remove(watch.ruinKey, watch);
		for (UUID uuid : watch.posts.keySet()) {
			BY_GUARD.remove(uuid);
			if (level != null && level.getEntity(uuid) instanceof Mob mob && !mob.isRemoved()) {
				if (effects) {
					RuinFx.burst(level, ParticleTypes.SOUL, mob.position().add(0, 0.8, 0), 8, 0.3, 0.05);
				}
				RuinFx.burst(level, ParticleTypes.POOF, mob.position().add(0, 0.5, 0), 6, 0.3, 0.02);
				mob.discard();
			}
		}
		watch.posts.clear();
	}

	/** Server stopping: every guard leaves before the chunks are saved. */
	private static void stopAll() {
		MinecraftServer server = GuildyServer.server();
		for (Watch watch : new ArrayList<>(WATCHES.values())) {
			dismiss(watch, server == null ? null : server.getLevel(watch.dimension), false);
		}
		WATCHES.clear();
		BY_GUARD.clear();
		REMINDED.clear();
	}

	// ================================================================== events

	private static void onDeath(LivingEntity entity) {
		Watch watch = BY_GUARD.remove(entity.getUUID());
		if (watch != null && watch.posts.remove(entity.getUUID()) != null) {
			watch.killed++;
		}
	}

	private static void onConversion(Mob previous, Mob converted, ConversionParams params) {
		Watch watch = BY_GUARD.get(previous.getUUID());
		if (watch == null || params.type() != ConversionType.SINGLE) {
			// Split children are not guards: refused at load (tagged, untracked).
			return;
		}
		// The converted mob is added right after this event: tracked first so that the load check accepts it.
		BY_GUARD.remove(previous.getUUID());
		BlockPos post = watch.posts.remove(previous.getUUID());
		if (post != null) {
			watch.posts.put(converted.getUUID(), post);
			BY_GUARD.put(converted.getUUID(), watch);
		}
		converted.addTag(TAG);
		converted.setPersistenceRequired();
	}

	private static boolean allowLoad(Entity entity) {
		return !entity.entityTags().contains(TAG) || BY_GUARD.containsKey(entity.getUUID());
	}

	/** A leftover loaded anyway (should have been refused): removed on the next tick. */
	private static void onEntityLoad(Entity entity) {
		if (!entity.entityTags().contains(TAG) || BY_GUARD.containsKey(entity.getUUID())) {
			return;
		}
		Scheduler.later("ruin.guards.cleanup", 1, () -> {
			if (!entity.isRemoved() && !BY_GUARD.containsKey(entity.getUUID())) {
				entity.discard();
			}
		});
	}
}
