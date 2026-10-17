package dev.guildy.ruin;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Ticks the cursed fights every server tick (transitions, attacks, clones, aura), each one isolated by {@link Safe}, with
 * one particle budget ({@code cursed.attacks.particleBudget}) shared between them; plus the queries and admin actions of
 * the cursed fights (the fights themselves are run by {@link Encounters}, which delegates to {@link CursedEncounter}).
 */
public final class CursedFights {
	/** Smallest particle budget of one cursed fight per tick when several run at once. */
	private static final int MIN_SHARE = 200;

	private CursedFights() {
	}

	static void init() {
		Scheduler.every("ruin.cursed", 1, 0, CursedFights::tick);
	}

	private static void tick() {
		List<Encounter> fights = Encounters.cursedFights();
		if (fights.isEmpty()) {
			return;
		}
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return;
		}
		RuinConfig cfg = RuinModule.config();
		int share = Math.max(MIN_SHARE, cfg.cursed.attacks.particleBudget / fights.size());
		for (Encounter encounter : fights) {
			Safe.run("ruin.cursed", () -> step(server, encounter, cfg, share));
		}
	}

	private static void step(MinecraftServer server, Encounter encounter, RuinConfig cfg, int share) {
		CursedEncounter cursed = encounter.cursed;
		ServerLevel level = server.getLevel(encounter.dimension);
		Mob boss = level == null || cursed == null ? null : encounter.bossEntity(level);
		if (boss == null) {
			// Unloaded or gone: the next second of the fight loop cancels it.
			return;
		}
		List<ServerPlayer> present = Encounters.present(level, encounter.ruin, cfg.encounter.presenceMargin);
		RitualFx fx = new RitualFx(level, Vec3.atCenterOf(encounter.spawn), share, cfg.summon.viewDistance);
		try {
			cursed.tick(level, encounter, boss, present, fx, cfg);
		} finally {
			fx.flush();
		}
	}

	// ================================================================== queries

	private static @Nullable CursedEncounter of(RuinRecord ruin) {
		Encounter encounter = Encounters.active(ruin);
		return encounter == null ? null : encounter.cursed;
	}

	/** The running fight of this ruin is a cursed one. */
	public static boolean isCursed(RuinRecord ruin) {
		return of(ruin) != null;
	}

	/** Phase (1..3) of the cursed fight of this ruin, 0 without one. */
	public static int phase(RuinRecord ruin) {
		CursedEncounter cursed = of(ruin);
		return cursed == null ? 0 : cursed.phase;
	}

	/** The cursed guardian of this ruin is changing phase (invulnerable) right now. */
	public static boolean transitioning(RuinRecord ruin) {
		CursedEncounter cursed = of(ruin);
		return cursed != null && cursed.transition != null;
	}

	/** Centers of the pentagrams on the arena floor (empty without a cursed fight). */
	public static List<Vec3> pentagrams(RuinRecord ruin) {
		CursedEncounter cursed = of(ruin);
		return cursed == null ? List.of() : cursed.pentagrams();
	}

	/** Radius of the arena pentagrams. */
	public static double pentagramRadius() {
		return RuinModule.config().cursed.attacks.pentagramRadius;
	}

	/** Health drained from the players by the cursed fight of this ruin (pentagrams, nova). */
	public static double drained(RuinRecord ruin) {
		CursedEncounter cursed = of(ruin);
		return cursed == null ? 0 : cursed.drained;
	}

	/** Health the cursed guardian of this ruin regained by draining the players. */
	public static double healed(RuinRecord ruin) {
		CursedEncounter cursed = of(ruin);
		return cursed == null ? 0 : cursed.healed;
	}

	// ================================================================== admin

	/** Starts the transition to the next phase now. @return false without a cursed fight, during one or in the last phase */
	public static boolean nextPhase(RuinRecord ruin) {
		Encounter encounter = Encounters.active(ruin);
		CursedEncounter cursed = encounter == null ? null : encounter.cursed;
		MinecraftServer server = GuildyServer.server();
		ServerLevel level = cursed == null || server == null ? null : server.getLevel(encounter.dimension);
		Mob boss = level == null ? null : encounter.bossEntity(level);
		if (boss == null || cursed.phase >= CursedRules.LAST_PHASE) {
			return false;
		}
		CursedSettings.PhaseSettings p = RuinModule.config().cursed.phases;
		int next = cursed.phase + 1;
		float floor = (float) Math.max(1, CursedRules.threshold(next, p.phase2Below, p.phase3Below) * boss.getMaxHealth());
		if (!cursed.startTransition(level, encounter, boss, next)) {
			return false;
		}
		boss.setHealth(Math.min(boss.getHealth(), floor));
		return true;
	}

	/** Uses a cursed attack now (cooldowns ignored). @return false without a cursed fight, a target or during a transition */
	public static boolean forceAttack(RuinRecord ruin, CursedRules.Attack attack) {
		Encounter encounter = Encounters.active(ruin);
		CursedEncounter cursed = encounter == null ? null : encounter.cursed;
		MinecraftServer server = GuildyServer.server();
		ServerLevel level = cursed == null || server == null ? null : server.getLevel(encounter.dimension);
		Mob boss = level == null ? null : encounter.bossEntity(level);
		return boss != null && cursed.force(attack, level, encounter, boss, RuinModule.config());
	}
}
