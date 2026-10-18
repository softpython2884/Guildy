package dev.guildy.ruin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Pure rules of the cursed guardians (no Minecraft classes, unit-tested): phases and their thresholds (a phase is never
 * skipped), the timeline of a phase transition, the multipliers of the guardian and of each phase, the attacks unlocked by
 * each phase, the damage rules of the arena pentagrams and of the other attacks, and the reward rolls.
 */
public final class CursedRules {
	/** Phases of a cursed fight (1 = start). */
	public static final int FIRST_PHASE = 1;
	public static final int LAST_PHASE = 3;
	/** Transition timeline (progress 0..1): the guardian rises until RISE_END, hovers, slams down at SLAM. */
	public static final double RISE_END = 0.28;
	public static final double CIRCLE_START = 0.05;
	public static final double CIRCLE_DRAWN = 0.4;
	public static final double CHAINS_START = 0.25;
	public static final double CHAINS_END = 0.78;
	public static final double SLAM = 0.8;
	public static final double MINIONS = 0.86;
	/** Telegraph of every cursed attack: the warning circle is shown this many ticks before it strikes. */
	public static final int TELEGRAPH_TICKS = 20;
	/** Ticks between two pulses of a damaging zone (pentagrams, cages). */
	public static final int PULSE_TICKS = 10;
	/** Vertical tolerance (blocks) of the floor zones: a player jumping in a pentagram is still in it. */
	public static final double ZONE_HEIGHT = 2.0;

	/** The attacks of a cursed guardian and the phase that unlocks them. */
	public enum Attack {
		PENTAGRAMS(2),
		CHAINS(2),
		METEORS(2),
		DARKNESS(3),
		CAGE(3),
		NOVA(3),
		CLONES(3);

		public final int phase;

		Attack(int phase) {
			this.phase = phase;
		}

		/** Admin / command id ("pentagrams", "chains"...). */
		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public static Attack byId(String id) {
			for (Attack attack : values()) {
				if (attack.id().equals(id)) {
					return attack;
				}
			}
			return null;
		}
	}

	/** Statistics of the cursed guardian: multipliers applied on top of the threat level (and of any group scaling). */
	public record Stats(double health, double damage, double armor, double toughness, double scale, double speed, double cooldown) {
	}

	private CursedRules() {
	}

	// ================================================================== phases

	/** Health ratio under which {@code phase} starts (2 or 3), 1 for the first phase, 0 above the last. */
	public static double threshold(int phase, double phase2Below, double phase3Below) {
		if (phase <= FIRST_PHASE) {
			return 1.0;
		}
		if (phase == 2) {
			return phase2Below;
		}
		if (phase == 3) {
			return Math.min(phase3Below, phase2Below);
		}
		return 0.0;
	}

	/**
	 * The phase a guardian enters after a hit: the next one when its health ratio fell under the next threshold (never two
	 * at once: a big hit stops at the next threshold, see {@link #floorRatio}), else the current one.
	 */
	public static int next(int phase, double ratio, double phase2Below, double phase3Below) {
		if (phase >= LAST_PHASE) {
			return LAST_PHASE;
		}
		int candidate = Math.max(FIRST_PHASE, phase) + 1;
		return ratio < threshold(candidate, phase2Below, phase3Below) ? candidate : Math.max(FIRST_PHASE, phase);
	}

	/**
	 * Health ratio the guardian keeps after a hit in {@code phase}: the hit never takes it below the threshold of the next
	 * phase (the overflow is lost, the transition starts there); in the last phase nothing is kept.
	 */
	public static double floorRatio(int phase, double ratio, double phase2Below, double phase3Below) {
		if (phase >= LAST_PHASE) {
			return ratio;
		}
		return Math.max(ratio, threshold(Math.max(FIRST_PHASE, phase) + 1, phase2Below, phase3Below));
	}

	/** A guardian killed in {@code phase} dies for good only in the last phase (else the next phase starts). */
	public static boolean diesIn(int phase) {
		return phase >= LAST_PHASE;
	}

	/**
	 * A cursed guardian killed in {@code phase} rises again into its final form: always once, at the end of the last phase
	 * (however fast it was beaten: no fast-kill window), never during a phase transition, never a second time.
	 */
	public static boolean risesAgain(boolean finalFormEnabled, int phase, boolean alreadyRisen, boolean transitioning) {
		return finalFormEnabled && phase >= LAST_PHASE && !alreadyRisen && !transitioning;
	}

	/** A cursed guardian dies for good: in the last phase, once its final form is over (or without a final form). */
	public static boolean diesForGood(boolean finalFormEnabled, int phase, boolean alreadyRisen) {
		return diesIn(phase) && (alreadyRisen || !finalFormEnabled);
	}

	/**
	 * The reward bonus of a risen guardian applies: a normal guardian that rose (killed too fast), a cursed one only when
	 * {@code cursed.finalForm.risenRewards} (its final form is part of the cursed fight: no double count by default).
	 */
	public static boolean risenBonus(boolean cursed, boolean risen, boolean risenRewards) {
		return risen && (!cursed || risenRewards);
	}

	// ================================================================== transitions

	/** Height of the guardian above its anchor during a transition: rises smoothly, hovers, slams down at {@link #SLAM}. */
	public static double lift(double progress, double maxLift) {
		double p = clamp01(progress);
		double h = Math.max(0, maxLift);
		if (p >= SLAM) {
			return 0;
		}
		if (p <= RISE_END) {
			return h * smooth(p / RISE_END);
		}
		// Hover with a slow bob, then a quick wind-up just before the slam.
		double bob = 0.12 * Math.sin((p - RISE_END) * Math.PI * 6);
		double windUp = p > SLAM - 0.06 ? 0.25 * smooth((p - (SLAM - 0.06)) / 0.06) : 0;
		return Math.max(0, h + bob * Math.min(1, h) + windUp * Math.min(1, h));
	}

	/** How high a guardian can rise: the free blocks above it minus its height (0 when it already touches the ceiling). */
	public static double maxLift(double headroom, double height, double wanted) {
		return Math.max(0, Math.min(wanted, headroom - height - 0.3));
	}

	/** Scale the guardian may take: {@code wanted}, limited by the free blocks above its spawn (never smaller than 1). */
	public static double scale(double wanted, double headroom, double height) {
		if (height <= 0) {
			return Math.max(1, wanted);
		}
		return Math.max(1.0, Math.min(wanted, (headroom - 0.3) / height));
	}

	// ================================================================== statistics

	/** Multipliers of the guardian when it appears. */
	public static Stats stats(CursedSettings.GuardianSettings g) {
		return new Stats(Math.max(1, g.healthMultiplier), Math.max(1, g.damageMultiplier), Math.max(0, g.armorBonus), Math.max(0, g.toughnessBonus),
				Math.max(1, g.scale), Math.max(0, g.speedBonus), clampFactor(g.cooldownFactor));
	}

	/** Damage multiplier of a phase: {@code 1 + bonus} for each phase above the first. */
	public static double phaseDamage(int phase, double bonus) {
		return 1.0 + Math.max(0, bonus) * Math.max(0, Math.min(LAST_PHASE, phase) - FIRST_PHASE);
	}

	/** Speed multiplier of a phase. */
	public static double phaseSpeed(int phase, double bonus) {
		return phaseDamage(phase, bonus);
	}

	/** Cooldown factor of a phase: {@code factor} for each phase above the first (never below 0.2). */
	public static double phaseCooldown(int phase, double factor) {
		int steps = Math.max(0, Math.min(LAST_PHASE, phase) - FIRST_PHASE);
		return Math.max(0.2, Math.pow(clampFactor(factor), steps));
	}

	/** Damage factor of the normal abilities of a cursed guardian in a phase. */
	public static double abilityDamage(CursedSettings s, int phase) {
		return Math.max(1, s.guardian.damageMultiplier) * phaseDamage(phase, s.phases.damageBonus);
	}

	/** Cooldown factor of the normal abilities of a cursed guardian in a phase (never below 0.15). */
	public static double abilityCooldown(CursedSettings s, int phase) {
		return Math.max(0.15, clampFactor(s.guardian.cooldownFactor) * phaseCooldown(phase, s.phases.cooldownFactor));
	}

	/** Escort of a cursed guardian: {@code base * multiplier + bonus}, within {@code max}. */
	public static int escort(int base, double multiplier, int bonus, int max) {
		long n = Math.round(Math.max(0, base) * Math.max(0, multiplier)) + Math.max(0, bonus);
		return (int) Math.max(0, Math.min(Math.max(0, max), n));
	}

	// ================================================================== attacks

	/** Attacks of a phase (the earlier ones stay), in their rotation order. */
	public static List<Attack> unlocked(int phase) {
		List<Attack> list = new ArrayList<>();
		for (Attack attack : Attack.values()) {
			if (attack.phase <= phase) {
				list.add(attack);
			}
		}
		return list;
	}

	/** Ticks of a cooldown in seconds, scaled by a factor (at least one second). */
	public static long cooldownTicks(double seconds, double factor) {
		return Math.max(20, Math.round(Math.max(0, seconds) * 20 * Math.max(0.05, factor)));
	}

	/** Pentagrams of a set in a phase: the configured count, one more in the last phase. */
	public static int pentagrams(int count, int phase) {
		return Math.max(1, count) + (phase >= LAST_PHASE ? 1 : 0);
	}

	/** A player stands in a floor zone (pentagram, cage): horizontally within its radius, close to its floor. */
	public static boolean inside(double dx, double dy, double dz, double radius) {
		return dx * dx + dz * dz <= radius * radius && dy >= -0.5 && dy <= ZONE_HEIGHT;
	}

	/** A zone (telegraphed {@link #TELEGRAPH_TICKS}) hurts on this tick of its life: active and on a pulse. */
	public static boolean pulse(int age, int telegraphTicks, int activeTicks) {
		int t = age - Math.max(0, telegraphTicks);
		return t >= 0 && t < Math.max(1, activeTicks) && t % PULSE_TICKS == 0;
	}

	/** Health the guardian regains from {@code drained} health with a heal ratio. */
	public static float heal(double drained, double ratio) {
		return (float) Math.max(0, drained * Math.max(0, ratio));
	}

	/** Impact damage at {@code distance} of a meteor: full in the inner half, down to 40 % at the edge, none outside. */
	public static double impact(double damage, double distance, double radius) {
		if (distance > radius || radius <= 0) {
			return 0;
		}
		double inner = radius * 0.5;
		if (distance <= inner) {
			return Math.max(0, damage);
		}
		double k = (distance - inner) / (radius - inner);
		return Math.max(0, damage) * (1.0 - 0.6 * k);
	}

	/** A ring wave ({@code front} = its radius now) hits a player at {@code distance} once: on its front. */
	public static boolean onFront(double distance, double front) {
		return distance <= front + 0.6 && distance >= front - 1.8;
	}

	// ================================================================== rewards

	/** Rewards of a cursed victory: the threat rewards with the cursed multipliers and extra rolls on top. */
	public static ThreatRules.Rewards rewards(ThreatRules.Rewards base, CursedSettings.RewardSettings r) {
		ThreatRules.Rewards b = base == null ? ThreatRules.Rewards.NONE : base;
		return new ThreatRules.Rewards(b.gold() * Math.max(1, r.goldMultiplier), b.xp() * Math.max(1, r.xpMultiplier),
				Math.min(20, b.extraLootRolls() + Math.max(0, r.extraLootRolls)), b.extraUniqueChance(), b.extraScrollChance(),
				Math.min(15, b.bonusUniqueRolls() + Math.max(0, r.extraUniqueRolls)), Math.min(15, b.bonusScrollRolls() + Math.max(0, r.extraScrollRolls)));
	}

	/**
	 * Drops of {@code attempts} rolls that stop once {@code cap} succeeded ({@code success} = one roll, given its index).
	 * Never more than the attempts, never more than the cap.
	 */
	public static int cappedRolls(int attempts, int cap, IntPredicate success) {
		int got = 0;
		int limit = Math.max(0, cap);
		for (int i = 0; i < Math.max(0, attempts) && got < limit; i++) {
			if (success.test(i)) {
				got++;
			}
		}
		return got;
	}

	/** Chance that at least one of {@code attempts} rolls of chance {@code chance} succeeds. */
	public static double atLeastOne(int attempts, double chance) {
		double c = Math.max(0, Math.min(1, chance));
		return 1.0 - Math.pow(1.0 - c, Math.max(0, attempts));
	}

	/** Threat level after a cursed victory: {@code gain} levels at once, never above the cap. */
	public static int nextThreat(int level, int gain, int maxLevel) {
		int next = Math.max(1, level);
		for (int i = 0; i < Math.max(0, gain); i++) {
			next = ThreatRules.next(next, maxLevel);
		}
		return Math.min(next, Math.max(1, Math.min(ThreatRules.MAX_LEVEL, maxLevel)));
	}

	// ================================================================== helpers

	/** Roman numeral of a phase ("I", "II", "III"). */
	public static String roman(int phase) {
		return ThreatRules.roman(Math.max(FIRST_PHASE, Math.min(LAST_PHASE, phase)));
	}

	private static double clampFactor(double f) {
		return Math.max(0.05, Math.min(1.0, f));
	}

	private static double clamp01(double v) {
		return Math.max(0, Math.min(1, v));
	}

	static double smooth(double t) {
		double k = clamp01(t);
		return k * k * (3 - 2 * k);
	}
}
