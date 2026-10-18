package dev.guildy.level;

/**
 * Per-level formulas past the classic levels (pure, unit-tested). Levels 1 to 10 keep each module's historical
 * per-level rate; past level 10 every formula has its own, smaller rate and an optional cap on what those levels add,
 * so that guild levels up to 500 never break the balance (horde difficulty, heart integrity, chunks, villagers, guard
 * damage and health, quest amounts and rewards).
 */
public final class LevelScaling {
	/** The historical maximum level: the per-level formulas are unchanged up to it. */
	public static final int CLASSIC_LEVELS = 10;

	private LevelScaling() {
	}

	/** Levels gained between level 1 and level 10 (0 to 9). */
	public static int classicSteps(int level) {
		return Math.max(0, Math.min(level, CLASSIC_LEVELS) - 1);
	}

	/** Levels gained past level 10 (0 below it). */
	public static int stepsAbove(int level) {
		return Math.max(0, level - CLASSIC_LEVELS);
	}

	/**
	 * Bonus of a level: {@code perLevel} for each level gained up to 10, then {@code perLevelAbove} for each level past
	 * 10, that second part at most {@code maxAbove} ({@code maxAbove <= 0} = no cap). Negative or NaN rates count as 0.
	 */
	public static double bonus(int level, double perLevel, double perLevelAbove, double maxAbove) {
		double classic = positive(perLevel) * classicSteps(level);
		double above = positive(perLevelAbove) * stepsAbove(level);
		if (maxAbove > 0) {
			above = Math.min(above, maxAbove);
		}
		return classic + above;
	}

	/**
	 * Level used by formulas that only take a level (quest amounts and rewards): the level itself up to 10, then
	 * {@code 10 + floor((level - 10) * rate)}, at most {@code max} (never below 10 past level 10).
	 */
	public static int softLevel(int level, double rate, int max) {
		if (level <= CLASSIC_LEVELS) {
			return Math.max(1, level);
		}
		long soft = CLASSIC_LEVELS + (long) Math.floor(positive(rate) * stepsAbove(level) + 1e-9);
		return (int) Math.max(CLASSIC_LEVELS, Math.min(Math.max(CLASSIC_LEVELS, max), soft));
	}

	private static double positive(double value) {
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}
}
