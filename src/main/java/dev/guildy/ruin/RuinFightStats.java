package dev.guildy.ruin;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.stats.FightArena;
import dev.guildy.stats.FightKind;
import dev.guildy.stats.FightModule;
import dev.guildy.stats.FightReport;
import dev.guildy.stats.FightReports;
import dev.guildy.stats.FightSession;
import dev.guildy.stats.FightStatsSettings;
import dev.guildy.stats.FightTracker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The ruin side of the fight statistics ({@link dev.guildy.stats}): one session per ruin, opened when its room guards are
 * posted or its guardian appears, whose arena is the ruin inflated by {@code encounter.presenceMargin}; the guardian, its
 * escort (shadows of a cursed guardian included) and the room guards are its monsters. The victory sends the full
 * "Bilan du combat", a failure (reset by absence, guardian unloaded, admin reset) the short one; a session without a fight
 * is forgotten once nobody has been around for the ruin's {@code resetSeconds}. Server stop: forgotten without summary.
 */
final class RuinFightStats {
	private static final String PREFIX = "ruin:";
	/** A session is never kept longer than this (bounded memory, whatever happens). */
	private static final long MAX_SESSION_TICKS = 3 * 60 * 60 * 20L;
	private static final int MIN_IDLE_SECONDS = 15;

	private RuinFightStats() {
	}

	static void init() {
		FightModule.init(() -> RuinModule.config().stats);
		FightTracker.addClassifier(new FightTracker.Classifier() {
			@Override
			public boolean interested(LivingEntity entity) {
				return RoomGuards.isRuinMob(entity);
			}

			@Override
			public FightTracker.@Nullable Hit classify(LivingEntity entity) {
				return RuinFightStats.classify(entity);
			}
		});
		Scheduler.every("ruin.stats", 20, 9, () -> Safe.run("ruin.stats", RuinFightStats::tickSecond));
	}

	private static FightStatsSettings cfg() {
		return RuinModule.config().stats;
	}

	static String key(RuinRecord ruin) {
		return PREFIX + ruin.key;
	}

	/** The open session of a ruin, or null. */
	static @Nullable FightSession session(RuinRecord ruin) {
		return FightTracker.get(key(ruin));
	}

	private static @Nullable FightSession open(ResourceKey<Level> dimension, RuinRecord ruin) {
		if (!cfg().enabled) {
			return null;
		}
		return FightTracker.open(key(ruin), new Arena(dimension, ruin), cfg().maxPlayers);
	}

	// ================================================================== hooks (Encounters, RoomGuards)

	/** RoomGuards: guards were posted in the ruin (the session starts with them). */
	static void guardsPosted(ServerLevel level, RuinRecord ruin, int count) {
		FightSession session = open(level.dimension(), ruin);
		if (session != null) {
			session.addGuardsPosted(count);
		}
	}

	/** Encounters: the guardian appeared. */
	static void awakened(ServerLevel level, Encounter encounter) {
		FightSession session = open(level.dimension(), encounter.ruin);
		if (session != null) {
			session.fightStarted(encounter.startedTick);
		}
	}

	/** Encounters: the guardian is dead. Final blow, then the full summary for every participant. */
	static void victory(ServerLevel level, Encounter encounter, Mob boss, DamageSource source, List<ServerPlayer> present) {
		FightSession session = FightTracker.close(key(encounter.ruin));
		if (session == null || !cfg().enabled) {
			return;
		}
		FightTracker.creditFinalBlow(session, source);
		deliver(session, encounter, FightReport.Outcome.VICTORY, -1, present);
	}

	/** Encounters: the fight ended without victory (reset, guardian gone, admin). The short summary when configured. */
	static void ended(Encounter encounter, @Nullable ServerLevel level) {
		FightSession session = FightTracker.close(key(encounter.ruin));
		if (session == null || !cfg().enabled || !cfg().onFailure) {
			return;
		}
		Mob boss = level == null ? null : encounter.bossEntity(level);
		double left = boss == null ? -1 : boss.getHealth() / Math.max(1.0F, boss.getMaxHealth());
		List<ServerPlayer> present = level == null ? List.of() : session.arena().present();
		deliver(session, encounter, FightReport.Outcome.FAILURE, left, present);
	}

	/** Server stop: every session is forgotten (no summary). */
	static void clear() {
		for (FightSession session : FightTracker.sessions()) {
			if (session.key.startsWith(PREFIX)) {
				FightTracker.close(session.key);
			}
		}
	}

	private static void deliver(FightSession session, Encounter encounter, FightReport.Outcome outcome, double bossHealthLeft, List<ServerPlayer> present) {
		long now = Scheduler.ticks();
		long fightStart = session.fightStartTick() >= 0 ? session.fightStartTick() : encounter.startedTick;
		FightReport.Header header = new FightReport.Header(Lang.colorize(encounter.def.name), Lang.colorize(encounter.def.boss.name),
				Encounters.threatLabel(encounter.threat), encounter.cursed != null, encounter.risen, outcome, now - fightStart, now - session.openedTick,
				session.guardsPosted(), session.killedCount(FightKind.GUARD), session.killedCount(FightKind.ESCORT), bossHealthLeft);
		List<UUID> there = new ArrayList<>();
		for (ServerPlayer player : present) {
			if (FightTracker.countable(player)) {
				there.add(player.getUUID());
			}
		}
		FightReport report = FightReport.of(header, session.names(), session.tallies(), cfg().minDamage, there);
		if (!report.recipients().isEmpty()) {
			FightReports.deliver(report, cfg());
		}
	}

	// ================================================================== classification

	/** A guardian / escort mob of a running fight, or a room guard: its ruin's session (opened if needed) and kind. */
	private static FightTracker.@Nullable Hit classify(LivingEntity entity) {
		if (!cfg().enabled || !(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		UUID uuid = entity.getUUID();
		Encounter encounter = Encounters.encounterOf(uuid);
		if (encounter != null) {
			FightSession session = open(level.dimension(), encounter.ruin);
			if (session == null) {
				return null;
			}
			session.fightStarted(encounter.startedTick);
			return new FightTracker.Hit(session, uuid.equals(encounter.boss) ? FightKind.BOSS : FightKind.ESCORT);
		}
		RuinRecord ruin = RuinIndex.get(RoomGuards.ruinKeyOf(uuid));
		if (ruin != null) {
			FightSession session = open(level.dimension(), ruin);
			return session == null ? null : new FightTracker.Hit(session, FightKind.GUARD);
		}
		return null;
	}

	// ================================================================== idle sessions

	/** A session with no fight (guards only, ritual broken...) is forgotten when nobody has been around for a while. */
	private static void tickSecond() {
		long now = Scheduler.ticks();
		for (FightSession session : FightTracker.sessions()) {
			if (!session.key.startsWith(PREFIX)) {
				continue;
			}
			RuinRecord ruin = RuinIndex.get(session.key.substring(PREFIX.length()));
			if (ruin == null || !cfg().enabled || now - session.openedTick > MAX_SESSION_TICKS) {
				FightTracker.close(session.key);
				continue;
			}
			if (Encounters.isActive(ruin)) {
				// The fight ends through victory() or ended().
				session.secondsAlone = 0;
				continue;
			}
			if (ruin.state == RuinState.CLEARED) {
				FightTracker.close(session.key);
				continue;
			}
			if (Incantations.ritualRunning(ruin) || !session.arena().present().isEmpty()) {
				session.secondsAlone = 0;
				continue;
			}
			session.secondsAlone++;
			if (session.secondsAlone >= Math.max(MIN_IDLE_SECONDS, Encounters.def(ruin).resetSeconds)) {
				FightTracker.close(session.key);
			}
		}
	}

	/** The ruin and its surroundings (presence margin: where the boss bar is seen). */
	private record Arena(ResourceKey<Level> dimension, RuinRecord ruin) implements FightArena {
		@Override
		public boolean contains(ServerPlayer player) {
			return player.level().dimension() == dimension
					&& ruin.contains(player.getX(), player.getY(), player.getZ(), RuinModule.config().encounter.presenceMargin);
		}

		@Override
		public List<ServerPlayer> present() {
			MinecraftServer server = GuildyServer.server();
			ServerLevel level = server == null ? null : server.getLevel(dimension);
			if (level == null) {
				return List.of();
			}
			List<ServerPlayer> list = new ArrayList<>();
			for (ServerPlayer player : level.players()) {
				if (player.isAlive() && FightTracker.countable(player) && contains(player)) {
					list.add(player);
				}
			}
			return list;
		}
	}
}
