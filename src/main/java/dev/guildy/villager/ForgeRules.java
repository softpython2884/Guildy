package dev.guildy.villager;

/**
 * Pure rules of the blacksmith (no Minecraft classes, unit tested): repair step maths, choice of the most damaged item
 * and the vanilla anvil wear chain.
 */
public final class ForgeRules {
	/** Wear stage of an anvil, in the vanilla order (anvil → chipped → damaged → destroyed). */
	public enum AnvilStage {
		INTACT,
		CHIPPED,
		DAMAGED,
		BROKEN;

		/** Stage after one wearing use (vanilla AnvilBlock.damage; a damaged anvil is destroyed). */
		public AnvilStage next() {
			return switch (this) {
				case INTACT -> CHIPPED;
				case CHIPPED -> DAMAGED;
				case DAMAGED, BROKEN -> BROKEN;
			};
		}
	}

	private ForgeRules() {
	}

	/** Durability given back by one step: {@code percent} % of the max durability, rounded up, at least 1. */
	public static int repairAmount(int maxDamage, int percent) {
		if (maxDamage <= 0) {
			return 0;
		}
		int pct = Math.max(1, Math.min(100, percent));
		return Math.max(1, (int) Math.ceil(maxDamage * pct / 100.0));
	}

	/** Damage value after one step (never below 0). */
	public static int damageAfterRepair(int damage, int maxDamage, int percent) {
		int current = Math.max(0, Math.min(damage, Math.max(0, maxDamage)));
		return Math.max(0, current - repairAmount(maxDamage, percent));
	}

	/** Steps needed to repair the item completely. */
	public static int stepsToRepair(int damage, int maxDamage, int percent) {
		int amount = repairAmount(maxDamage, percent);
		if (amount <= 0 || damage <= 0) {
			return 0;
		}
		return (Math.min(damage, maxDamage) + amount - 1) / amount;
	}

	/** Part of the durability left (1 = new, 0 = about to break). */
	public static double durabilityRatio(int damage, int maxDamage) {
		if (maxDamage <= 0) {
			return 1.0;
		}
		int current = Math.max(0, Math.min(damage, maxDamage));
		return (maxDamage - current) / (double) maxDamage;
	}

	/** Durability left in percent, rounded down (a damaged item never shows 100 %). */
	public static int durabilityPercent(int damage, int maxDamage) {
		if (maxDamage <= 0) {
			return 100;
		}
		int current = Math.max(0, Math.min(damage, maxDamage));
		return (int) Math.floor((maxDamage - current) * 100.0 / maxDamage);
	}

	/**
	 * Index of the item to repair first: the lowest durability ratio, then the biggest damage, then the first index.
	 * Entries with no damage or no max durability (empty slot, unbreakable item) are skipped.
	 *
	 * @return the index, or -1 when nothing needs a repair
	 */
	public static int mostDamaged(int[] damage, int[] maxDamage) {
		int best = -1;
		double bestRatio = Double.MAX_VALUE;
		int bestDamage = -1;
		int n = Math.min(damage.length, maxDamage.length);
		for (int i = 0; i < n; i++) {
			if (maxDamage[i] <= 0 || damage[i] <= 0) {
				continue;
			}
			double ratio = durabilityRatio(damage[i], maxDamage[i]);
			int dmg = Math.min(damage[i], maxDamage[i]);
			if (ratio < bestRatio - 1e-9 || (Math.abs(ratio - bestRatio) <= 1e-9 && dmg > bestDamage)) {
				best = i;
				bestRatio = ratio;
				bestDamage = dmg;
			}
		}
		return best;
	}

	/** One anvil use wears it when the roll (0 inclusive to 1 exclusive) is below the chance, like vanilla. */
	public static boolean wears(double roll, double chance) {
		return roll < chance;
	}
}
