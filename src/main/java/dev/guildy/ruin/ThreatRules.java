package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.EscalationSettings;
import dev.guildy.ruin.RuinConfig.ResurrectionSettings;

/**
 * Pure rules (no Minecraft classes, unit-tested) of the ruins' threat level ("Menace" I, II, III...: each victory over a
 * guardian makes it stronger when it comes back) and of the resurrection of a guardian killed too fast (once per fight).
 */
public final class ThreatRules {
	/** Hard cap of {@code escalation.maxLevel}. */
	public static final int MAX_LEVEL = 50;
	/** Part of the resurrection during which the body sinks into the ground (progress 0..1). */
	public static final double SINK_END = 0.2;
	/** Part of the resurrection during which the body lifts above the ground. */
	public static final double LIFT_END = 0.8;

	/** Statistics of a guardian at a threat level. */
	public record Scaling(int level, double health, double damage, double armor, int extraMinions, double cooldownFactor) {
		public static final Scaling NONE = new Scaling(1, 1.0, 1.0, 0.0, 0, 1.0);
	}

	/**
	 * Rewards of a victory at a threat level (and over a risen guardian): multipliers of the gold and the guild XP, extra
	 * rolls in the loot table, chance of one extra unique / scroll roll, and the guaranteed bonus rolls of a risen guardian.
	 */
	public record Rewards(double gold, double xp, int extraLootRolls, double extraUniqueChance, double extraScrollChance, int bonusUniqueRolls,
			int bonusScrollRolls) {
		public static final Rewards NONE = new Rewards(1.0, 1.0, 0, 0.0, 0.0, 0, 0);
	}

	private ThreatRules() {
	}

	// ================================================================== threat level

	/** Stored level of a ruin: an older save (0, no level yet) starts at 1 + its past victories. */
	public static int stored(int stored, int timesCleared) {
		return stored > 0 ? stored : 1 + Math.max(0, timesCleared);
	}

	/** Level used in a fight: the stored level capped by {@code maxLevel} (always 1 when the escalation is disabled). */
	public static int level(int stored, int timesCleared, int maxLevel, boolean enabled) {
		if (!enabled) {
			return 1;
		}
		return Math.max(1, Math.min(cap(maxLevel), stored(stored, timesCleared)));
	}

	/** Level after a victory at {@code level} (never above the cap). */
	public static int next(int level, int maxLevel) {
		return Math.max(1, Math.min(cap(maxLevel), level + 1));
	}

	private static int cap(int maxLevel) {
		return Math.max(1, Math.min(MAX_LEVEL, maxLevel));
	}

	/** {@code 1 + perLevel * (level - 1)}: level I is the configured guardian. */
	public static double multiplier(double perLevel, int level) {
		return 1.0 + Math.max(0, perLevel) * Math.max(0, level - 1);
	}

	/** Flat bonus (armor points) at a level. */
	public static double bonus(double perLevel, int level) {
		return Math.max(0, perLevel) * Math.max(0, level - 1);
	}

	/** One more escort mob every {@code everyLevels} levels (0 = never). */
	public static int extraMinions(int level, int everyLevels) {
		return everyLevels <= 0 ? 0 : Math.max(0, level - 1) / everyLevels;
	}

	/** Ability cooldowns multiplied by this factor: {@code -perLevel} per level, never under {@code floor}. */
	public static double cooldownFactor(double perLevel, int level, double floor) {
		double f = Math.max(0.05, Math.min(1.0, floor));
		return Math.max(f, Math.min(1.0, 1.0 - Math.max(0, perLevel) * Math.max(0, level - 1)));
	}

	/**
	 * Health ratio under which the guardian enrages: raised by {@code perLevel} per level up to {@code max} (a configured
	 * threshold already above {@code max} is kept).
	 */
	public static double enrageThreshold(double base, double perLevel, int level, double max) {
		double raised = base + Math.max(0, perLevel) * Math.max(0, level - 1);
		return Math.max(0, Math.min(1, Math.max(base, Math.min(max, raised))));
	}

	/** Chance (0..1) of one extra unique / scroll roll at a level. */
	public static double extraRollChance(double perLevel, int level) {
		return Math.max(0, Math.min(1, bonus(perLevel, level)));
	}

	/** {@code base * multiplier}, rounded to the nearest integer, never negative. */
	public static int scaled(int base, double multiplier) {
		return (int) Math.max(0, Math.round(Math.max(0, base) * Math.max(0, multiplier)));
	}

	/** Every statistic of a guardian at a level. */
	public static Scaling scaling(EscalationSettings e, int level) {
		if (e == null || !e.enabled || level <= 1) {
			return Scaling.NONE;
		}
		return new Scaling(level, multiplier(e.healthPerLevel, level), multiplier(e.damagePerLevel, level), bonus(e.armorPerLevel, level),
				extraMinions(level, e.minionsEveryLevels), cooldownFactor(e.cooldownPerLevel, level, e.minCooldownFactor));
	}

	/** Rewards of a victory at a level, with the bonus of a risen guardian. */
	public static Rewards rewards(EscalationSettings e, ResurrectionSettings r, int level, boolean risen) {
		boolean scaled = e != null && e.enabled && level > 1;
		double gold = scaled ? multiplier(e.goldPerLevel, level) : 1.0;
		double xp = scaled ? multiplier(e.xpPerLevel, level) : 1.0;
		double unique = scaled ? extraRollChance(e.uniqueChancePerLevel, level) : 0.0;
		double scroll = scaled ? extraRollChance(e.scrollChancePerLevel, level) : 0.0;
		if (!risen || r == null) {
			return new Rewards(gold, xp, 0, unique, scroll, 0, 0);
		}
		return new Rewards(gold * (1 + Math.max(0, r.bonusGold)), xp * (1 + Math.max(0, r.bonusXp)), Math.max(0, r.bonusLootRolls), unique, scroll,
				Math.max(0, r.bonusUniqueRolls), Math.max(0, r.bonusScrollRolls));
	}

	// ================================================================== resurrection

	/**
	 * Length (seconds) of the fast-kill window: the ruin's own value when set ({@code perRuin} &gt;= 0, 0 = never), else
	 * {@code base} + {@code perTier} for each tier above 1 + {@code perThreat} for each threat level above I.
	 */
	public static double fastKillSeconds(double perRuin, double base, double perTier, double perThreat, int tier, int level) {
		if (perRuin >= 0) {
			return perRuin;
		}
		return Math.max(0, base) + Math.max(0, perTier) * Math.max(0, tier - 1) + Math.max(0, perThreat) * Math.max(0, level - 1);
	}

	public static long windowTicks(double seconds) {
		return Math.max(0, Math.round(seconds * 20));
	}

	/** A guardian killed {@code aliveTicks} after it appeared rises again: enabled, not risen yet, inside the window. */
	public static boolean resurrects(boolean enabled, boolean alreadyRose, long aliveTicks, long windowTicks) {
		return enabled && !alreadyRose && windowTicks > 0 && aliveTicks >= 0 && aliveTicks < windowTicks;
	}

	/** Seconds left in the fast-kill window (rounded up), 0 once it is closed. */
	public static long secondsLeft(long aliveTicks, long windowTicks) {
		long left = windowTicks - Math.max(0, aliveTicks);
		return left <= 0 ? 0 : (left + 19) / 20;
	}

	/** "0:32", "1:05". */
	public static String clock(long seconds) {
		long s = Math.max(0, seconds);
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}

	/**
	 * Vertical offset of the body during the resurrection: it sinks to {@code -sinkDepth}, lifts to {@code +liftHeight},
	 * then floats there until it lands at the end (smooth steps).
	 */
	public static double bodyOffset(double progress, double sinkDepth, double liftHeight) {
		double p = Math.max(0, Math.min(1, progress));
		if (p <= SINK_END) {
			return -sinkDepth * smooth(p / SINK_END);
		}
		if (p <= LIFT_END) {
			return -sinkDepth + (sinkDepth + liftHeight) * smooth((p - SINK_END) / (LIFT_END - SINK_END));
		}
		return liftHeight;
	}

	/** Health during the resurrection: grows from 1 to {@code max * ratio} once the body starts to lift. */
	public static float risingHealth(double progress, float max, double ratio) {
		double target = Math.max(1, max * Math.max(0.05, Math.min(1, ratio)));
		double k = Math.max(0, Math.min(1, (progress - SINK_END) / (1 - SINK_END)));
		return (float) Math.max(1, Math.min(target, 1 + (target - 1) * k));
	}

	private static double smooth(double t) {
		double k = Math.max(0, Math.min(1, t));
		return k * k * (3 - 2 * k);
	}

	// ================================================================== display

	/** Roman numeral (1..3999), "I" for anything smaller. */
	public static String roman(int n) {
		int value = Math.max(1, Math.min(3999, n));
		int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < numbers.length; i++) {
			while (value >= numbers[i]) {
				sb.append(letters[i]);
				value -= numbers[i];
			}
		}
		return sb.toString();
	}

	/** '&' colour code of a threat level: green at I, yellow, gold, red, dark red near the maximum. */
	public static String color(int level, int maxLevel) {
		int max = Math.max(2, cap(maxLevel));
		double k = (double) (Math.max(1, level) - 1) / (max - 1);
		if (level <= 1) {
			return "&a";
		}
		if (k < 0.25) {
			return "&e";
		}
		if (k < 0.5) {
			return "&6";
		}
		if (k < 0.85) {
			return "&c";
		}
		return "&4&l";
	}
}
