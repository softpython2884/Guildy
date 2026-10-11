package dev.guildy.upgrade;

/**
 * Pure arithmetic of the upgrade bonuses (unit-tested). Percentages below 0 count as 0.
 */
public final class UpgradeMath {
	private static final double EPSILON = 1e-9;

	private UpgradeMath() {
	}

	/** base x (1 + pct / 100), rounded to the nearest integer. */
	public static long boost(long base, double pct) {
		double p = positive(pct);
		if (base <= 0 || p == 0) {
			return base;
		}
		return Math.round(base * (100.0 + p) / 100.0);
	}

	public static int boost(int base, double pct) {
		return (int) Math.min(Integer.MAX_VALUE, boost((long) base, pct));
	}

	public static double boost(double base, double pct) {
		return base * (100.0 + positive(pct)) / 100.0;
	}

	/** base x (1 - pct / 100) rounded down (pct capped at 100), never negative. */
	public static long reduce(long base, double pct) {
		if (base <= 0) {
			return 0;
		}
		return Math.max(0, (long) Math.floor(base * (100.0 - clamp(pct, 100)) / 100.0 + EPSILON));
	}

	public static double reduce(double base, double pct) {
		return Math.max(0, base * (100.0 - clamp(pct, 100)) / 100.0);
	}

	/** Discounted price, rounded up: at least 1 unless the base price is free. */
	public static int discount(int price, double pct) {
		if (price <= 0) {
			return 0;
		}
		long value = (long) Math.ceil(price * (100.0 - clamp(pct, 100)) / 100.0 - EPSILON);
		return (int) Math.max(1, Math.min(price, value));
	}

	/** Work duration shortened by pct (capped at 75 %), rounded, at least 1. */
	public static int workSeconds(int seconds, double pct) {
		if (seconds <= 1) {
			return Math.max(1, seconds);
		}
		double p = clamp(pct, UpgradeEffects.MAX_WORK_SPEED_PCT);
		return (int) Math.max(1, Math.round(seconds * (100.0 - p) / 100.0));
	}

	/** Work actions per step: 1 at normal speed, up to 4 at the 75 % cap. */
	public static double workRate(double pct) {
		return 100.0 / (100.0 - clamp(pct, UpgradeEffects.MAX_WORK_SPEED_PCT));
	}

	/**
	 * Treasury gold of a claimed quest. DELIVER quests keep their base gold: their reward is kept below the shop price
	 * of the delivered items, a bonus could turn "buy at the shop then deliver" into a gold loop.
	 */
	public static long questGold(long base, double pct, boolean delivery) {
		return delivery ? base : boost(base, pct);
	}

	private static double positive(double pct) {
		return Double.isFinite(pct) ? Math.max(0, pct) : 0;
	}

	private static double clamp(double pct, double max) {
		return Math.min(max, positive(pct));
	}
}
