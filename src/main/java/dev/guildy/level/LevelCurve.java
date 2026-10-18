package dev.guildy.level;

import java.util.List;

/**
 * Guild XP curve (pure, unit-tested): explicit total-XP thresholds for the first levels (index 0 = level 2), then an
 * arithmetic progression of the XP needed per level, {@code stepBase + stepGrowth * (level - lastListedLevel)}, so the
 * total XP grows with the square of the level. Every value is a {@code long}: no overflow up to level 500 with the
 * largest accepted settings.
 */
public final class LevelCurve {
	/** Smallest accepted {@code levels.maxLevel}. */
	public static final int MIN_MAX_LEVEL = 10;
	/** Largest accepted {@code levels.maxLevel}. */
	public static final int ABSOLUTE_MAX_LEVEL = 500;

	private LevelCurve() {
	}

	/** Last level defined by the list (1 when the list is empty). */
	public static int lastListedLevel(List<Integer> thresholds) {
		return thresholds.size() + 1;
	}

	/** Total XP needed to reach {@code level} (0 for level 1 and below). */
	public static long xpForLevel(int level, List<Integer> thresholds, long stepBase, long stepGrowth) {
		if (level <= 1) {
			return 0;
		}
		int listed = thresholds.size();
		if (level - 2 < listed) {
			return value(thresholds.get(level - 2));
		}
		long lastXp = listed == 0 ? 0 : value(thresholds.get(listed - 1));
		long k = (long) level - lastListedLevel(thresholds);
		long base = Math.max(0, stepBase);
		long growth = Math.max(0, stepGrowth);
		return lastXp + base * k + growth * k * (k + 1) / 2;
	}

	/** XP needed to go from {@code level - 1} to {@code level}. */
	public static long stepXp(int level, List<Integer> thresholds, long stepBase, long stepGrowth) {
		return Math.max(0, xpForLevel(level, thresholds, stepBase, stepGrowth) - xpForLevel(level - 1, thresholds, stepBase, stepGrowth));
	}

	/** Level reached with {@code xp} total XP, at most {@code maxLevel}. */
	public static int levelFor(long xp, int maxLevel, List<Integer> thresholds, long stepBase, long stepGrowth) {
		int level = 1;
		while (level < maxLevel && xp >= xpForLevel(level + 1, thresholds, stepBase, stepGrowth)) {
			level++;
		}
		return level;
	}

	private static long value(Integer threshold) {
		return threshold == null ? 0 : Math.max(0, threshold);
	}
}
