package dev.guildy.ruin;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ticks every server tick the dread abilities of the guardians in their dread form and the Avatars (aura, red sky, attacks),
 * each fight isolated by {@link Safe}, with one particle budget shared between them; and lifts the roots that ended
 * ({@link Roots}). The fights themselves are run by {@link Encounters}.
 */
final class DreadFights {
	/** Smallest particle budget of one fight per tick when several run at once. */
	private static final int MIN_SHARE = 200;

	private DreadFights() {
	}

	static void init() {
		Scheduler.every("ruin.dread", 1, 0, DreadFights::tick);
	}

	private static void tick() {
		long now = Scheduler.ticks();
		Safe.run("ruin.dread.roots", () -> Roots.tick(now));
		List<Encounter> fights = Encounters.dreadFights();
		if (fights.isEmpty()) {
			return;
		}
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return;
		}
		RuinConfig cfg = RuinModule.config();
		for (Encounter encounter : fights) {
			int budget = encounter.avatar != null ? cfg.cursed.avatar.particleBudget : cfg.escalation.dread.particleBudget;
			int share = Math.max(MIN_SHARE, budget / fights.size());
			Safe.run("ruin.dread", () -> step(server, encounter, cfg, share, now));
		}
	}

	private static void step(MinecraftServer server, Encounter encounter, RuinConfig cfg, int share, long now) {
		ServerLevel level = server.getLevel(encounter.dimension);
		Mob boss = level == null ? null : encounter.bossEntity(level);
		if (boss == null) {
			// Unloaded or gone: the next second of the fight loop cancels it.
			return;
		}
		List<ServerPlayer> present = Encounters.present(level, encounter.ruin, cfg.encounter.presenceMargin);
		RitualFx fx = new RitualFx(level, Vec3.atCenterOf(encounter.spawn), share, cfg.summon.viewDistance);
		try {
			if (encounter.dreadFight != null) {
				encounter.dreadFight.tick(level, encounter, boss, present, fx, cfg, now);
			}
			if (encounter.avatar != null && encounter.rising == null) {
				encounter.avatar.tick(level, encounter, boss, present, fx, now);
			}
		} finally {
			fx.flush();
		}
	}
}
