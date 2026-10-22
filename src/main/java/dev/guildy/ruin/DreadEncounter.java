package dev.guildy.ruin;

import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.ruin.RuinConfig.DreadAbilityDef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The dread abilities of one running fight ({@link Encounter#dreadFight}): those of the guardian's dread form (its ruin's
 * {@code dreadAbilities}), and for the Avatar its theme's amplified plus its Nether attacks unlocked phase after phase. One
 * starts in rotation as soon as it is ready, after a global delay, a few at once at most; nothing runs while the guardian
 * rises or changes phase (the running ones stop: never a trap). Runtime only. Ticked by {@link DreadFights}.
 */
final class DreadEncounter {
	final List<DreadAbilityDef> abilities;
	private final int[] phases;
	private final long[] readyAt;
	final List<DreadAttacks.Running> running = new ArrayList<>();
	/** Summoned mobs that vanish at a game tick (mirror images). */
	final Map<UUID, Long> fading = new LinkedHashMap<>();
	/** Mobs that came out of the Nether rifts. */
	final Set<UUID> riftMobs = new LinkedHashSet<>();
	private long nextTick;
	private int rotation;
	private final SplittableRandom random = new SplittableRandom();
	/** Attacks started (statistics, tests). */
	int started;

	DreadEncounter(List<DreadAbilityDef> abilities, long now, double firstSeconds) {
		this.abilities = List.copyOf(abilities);
		this.phases = new int[this.abilities.size()];
		this.readyAt = new long[this.abilities.size()];
		for (int i = 0; i < this.abilities.size(); i++) {
			phases[i] = Math.max(1, this.abilities.get(i).phase);
			// Staggered: the first one quickly, then the others in their order.
			readyAt[i] = now + Math.round(firstSeconds * 20) + i * 60L;
		}
		nextTick = now + Math.round(firstSeconds * 20);
	}

	// ================================================================== settings of this fight

	private static boolean avatar(Encounter encounter) {
		return encounter.avatar != null;
	}

	private static int maxRunning(Encounter encounter, RuinConfig cfg) {
		return avatar(encounter) ? cfg.cursed.avatar.maxRunning : cfg.escalation.dread.maxRunning;
	}

	/** Cooldown factor of the dread abilities: the dread rhythm, the cursed phase, the Avatar's final form. */
	static double cooldownFactor(Encounter encounter, RuinConfig cfg) {
		double f = encounter.dread.active() ? encounter.dread.cooldown() : 1.0;
		if (encounter.cursed != null) {
			f *= CursedRules.phaseCooldown(encounter.cursed.phase, cfg.cursed.phases.cooldownFactor);
		}
		if (avatar(encounter)) {
			f *= AvatarRules.cooldownFactor(encounter.risen, cfg.cursed.avatar);
		}
		return Math.max(0.25, f);
	}

	private static long globalTicks(Encounter encounter, RuinConfig cfg) {
		double seconds = avatar(encounter) ? cfg.cursed.avatar.globalCooldownSeconds : cfg.escalation.dread.globalCooldownSeconds;
		return Math.max(10, Math.round(seconds * 20 * Math.max(0.5, cooldownFactor(encounter, cfg))));
	}

	/** Phase that unlocks the attacks now (the Avatar's cursed phase, every one in its final form; 1 otherwise). */
	private static int unlockPhase(Encounter encounter) {
		if (!avatar(encounter) || encounter.cursed == null) {
			return CursedRules.LAST_PHASE;
		}
		return AvatarRules.unlockPhase(encounter.cursed.phase, encounter.risen);
	}

	static int telegraph(RuinConfig cfg) {
		return DreadRules.telegraphTicks(cfg.escalation.dread.telegraphSeconds);
	}

	/** The guardian is frozen (rising, changing phase): no dread attack. */
	static boolean frozen(Encounter encounter) {
		return encounter.rising != null || encounter.cursed != null && encounter.cursed.invulnerable();
	}

	// ================================================================== tick

	void tick(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present, RitualFx fx, RuinConfig cfg, long now) {
		DreadAttacks.Context ctx = new DreadAttacks.Context(level, encounter, boss, targets(level, boss, present, cfg), fx, random, now);
		DreadSummons.tickFading(level, encounter, fading, fx);
		if (frozen(encounter)) {
			stopAll(ctx);
			return;
		}
		if (now >= nextTick && running.size() < maxRunning(encounter, cfg) && !ctx.targets.isEmpty() && !abilities.isEmpty()) {
			choose(ctx, cfg, now);
		}
		Iterator<DreadAttacks.Running> it = running.iterator();
		while (it.hasNext()) {
			DreadAttacks.Running attack = it.next();
			boolean keep = Safe.get("ruin.dread.attack", () -> attack.tick(ctx), false);
			attack.age++;
			if (!keep) {
				Safe.run("ruin.dread.stop", () -> attack.stop(ctx));
				it.remove();
			}
		}
	}

	private void choose(DreadAttacks.Context ctx, RuinConfig cfg, long now) {
		int index = DreadRules.pick(now, readyAt, phases, unlockPhase(ctx.encounter), rotation);
		if (index < 0) {
			return;
		}
		DreadAbilityDef def = abilities.get(index);
		DreadAttacks.Running attack = Safe.get("ruin.dread.cast." + def.type, () -> DreadAttacks.start(def, ctx, telegraph(cfg)), null);
		if (attack == null) {
			// Not now (no target in reach, no room...): retried a little later.
			readyAt[index] = now + 40;
			rotation = index + 1;
			return;
		}
		running.add(attack);
		started++;
		readyAt[index] = now + DreadRules.cooldownTicks(def.cooldownSeconds, cooldownFactor(ctx.encounter, cfg));
		nextTick = now + globalTicks(ctx.encounter, cfg);
		rotation = index + 1;
	}

	/** Survival / adventure players in reach of the guardian. */
	static List<ServerPlayer> targets(ServerLevel level, Mob boss, List<ServerPlayer> present, RuinConfig cfg) {
		double range = cfg.encounter.abilityRange;
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : present) {
			if (BossAbilities.valid(player, level) && player.distanceToSqr(boss) <= range * range) {
				list.add(player);
			}
		}
		return list;
	}

	/** Stops every running attack (they give back what they took: a held guardian...). */
	void stopAll(DreadAttacks.Context ctx) {
		for (DreadAttacks.Running attack : running) {
			Safe.run("ruin.dread.stop", () -> attack.stop(ctx));
		}
		running.clear();
	}

	/** The fight ends: running attacks forgotten, mirror images gone (the escort is removed by the fight itself). */
	void end(ServerLevel level) {
		for (UUID uuid : fading.keySet()) {
			Entity entity = level.getEntity(uuid);
			if (entity != null) {
				entity.discard();
			}
		}
		fading.clear();
		riftMobs.clear();
		running.clear();
	}

	/** Mobs of the Nether rifts still alive. */
	int riftAlive(ServerLevel level) {
		riftMobs.removeIf(uuid -> !(level.getEntity(uuid) instanceof Mob mob) || !mob.isAlive());
		return riftMobs.size();
	}

	/** After a phase transition or the rising: the attacks resume shortly. */
	void resume(long now) {
		nextTick = Math.max(nextTick, now + 40);
		for (int i = 0; i < readyAt.length; i++) {
			readyAt[i] = Math.min(readyAt[i], now + 60 + i * 30L);
		}
	}

	/**
	 * Admin / tests: starts the ability {@code type} now (its configured settings, else the default ones), cooldowns ignored.
	 *
	 * @return false while the guardian is frozen or without a target
	 */
	boolean force(String type, ServerLevel level, Encounter encounter, Mob boss, RuinConfig cfg) {
		if (frozen(encounter)) {
			return false;
		}
		DreadAbilityDef def = null;
		for (DreadAbilityDef candidate : abilities) {
			if (candidate.type.equals(type)) {
				def = candidate;
				break;
			}
		}
		if (def == null) {
			def = RuinDefaults.defaultDread(type);
		}
		if (def == null) {
			return false;
		}
		List<ServerPlayer> present = Encounters.present(level, encounter.ruin, cfg.encounter.presenceMargin);
		RitualFx fx = new RitualFx(level, boss.position(), cfg.escalation.dread.particleBudget, cfg.summon.viewDistance);
		DreadAttacks.Context ctx = new DreadAttacks.Context(level, encounter, boss, targets(level, boss, present, cfg), fx, random,
				Scheduler.ticks());
		if (ctx.targets.isEmpty()) {
			return false;
		}
		DreadAbilityDef chosen = def;
		DreadAttacks.Running attack = Safe.get("ruin.dread.force." + type, () -> DreadAttacks.start(chosen, ctx, telegraph(cfg)), null);
		fx.flush();
		if (attack == null) {
			return false;
		}
		running.add(attack);
		started++;
		return true;
	}

	/** Types of the running attacks (tests, admin info). */
	List<String> runningTypes() {
		List<String> list = new ArrayList<>();
		for (DreadAttacks.Running attack : running) {
			list.add(attack.def.type);
		}
		return list;
	}

	/** A running attack of this type is still in its warning (telegraph), or null when none runs. */
	@Nullable Boolean warning(String type) {
		for (DreadAttacks.Running attack : running) {
			if (attack.def.type.equals(type)) {
				return attack.warning();
			}
		}
		return null;
	}

	/** Centers of the floor zones of the running attacks (tests). */
	List<Vec3> zones() {
		List<Vec3> list = new ArrayList<>();
		for (DreadAttacks.Running attack : running) {
			if (attack instanceof DreadZones.Patches patches) {
				list.addAll(patches.centers());
			} else if (attack instanceof DreadZones.Storm storm) {
				list.addAll(storm.eyes());
			} else if (attack instanceof DreadZones.Prison prison) {
				list.add(prison.center());
			} else if (attack instanceof DreadZones.Well well) {
				list.add(well.center());
			}
		}
		return list;
	}
}
