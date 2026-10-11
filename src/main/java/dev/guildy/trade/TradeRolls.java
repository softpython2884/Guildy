package dev.guildy.trade;

import dev.guildy.wave.WeightedPicker;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Pure merchant maths (unit-tested): weighted offer rolls, tiered rolls of the trade hub, visit chance, gold price split.
 */
public final class TradeRolls {
	/** Highest price of one offer: 64 gold blocks + 8 ingots (the two trade cost slots hold at most one stack each). */
	public static final int MAX_PRICE = 64 * 9 + 8;
	/** Prices above this are paid in gold blocks (+ ingots for the rest). */
	public static final int MAX_INGOTS = 64;

	private TradeRolls() {
	}

	/** Random count in [min, max]. */
	public static int count(int min, int max, RandomGenerator random) {
		int lo = Math.max(0, Math.min(min, max));
		int hi = Math.max(lo, max);
		return lo + (hi > lo ? random.nextInt(hi - lo + 1) : 0);
	}

	/** Up to {@code count} different entries, drawn by weight (entries with a weight <= 0 are never drawn). */
	public static List<TradeEntry> roll(List<TradeEntry> table, int count, RandomGenerator random) {
		List<TradeEntry> left = new ArrayList<>(table);
		List<TradeEntry> out = new ArrayList<>();
		while (out.size() < count) {
			TradeEntry entry = WeightedPicker.pick(left, e -> e.weight, random);
			if (entry == null) {
				break;
			}
			left.remove(entry);
			out.add(entry);
		}
		return out;
	}

	/**
	 * Offers of a trade hub of tier {@code tier} (1-based): each offer first draws its source tier (the hub's own tier with
	 * weight {@code ownWeight}, every lower tier with {@code lowerWeight}), then an entry of that tier by weight.
	 * An entry is never offered twice; tiers without entries left are skipped.
	 *
	 * @param tables trade tables, index 0 = tier 1
	 */
	public static List<TradeEntry> rollTiered(List<List<TradeEntry>> tables, int tier, int count, double ownWeight, double lowerWeight,
			RandomGenerator random) {
		int top = Math.max(1, Math.min(tier, tables.size()));
		List<List<TradeEntry>> left = new ArrayList<>();
		for (int t = 0; t < top; t++) {
			left.add(new ArrayList<>(tables.get(t)));
		}
		List<Integer> tiers = new ArrayList<>();
		for (int t = 0; t < top; t++) {
			tiers.add(t);
		}
		List<TradeEntry> out = new ArrayList<>();
		while (out.size() < count) {
			Integer source = WeightedPicker.pick(tiers, t -> hasPickable(left.get(t)) ? (t == top - 1 ? ownWeight : lowerWeight) : 0, random);
			if (source == null) {
				break;
			}
			TradeEntry entry = WeightedPicker.pick(left.get(source), e -> e.weight, random);
			if (entry == null) {
				break;
			}
			left.get(source).remove(entry);
			out.add(entry);
		}
		return out;
	}

	private static boolean hasPickable(List<TradeEntry> entries) {
		for (TradeEntry entry : entries) {
			if (Double.isFinite(entry.weight) && entry.weight > 0) {
				return true;
			}
		}
		return false;
	}

	/** Chance (0..1) that a merchant visits a trade hub of this tier on a given morning. */
	public static double visitChance(double base, double perTier, int tier) {
		double chance = base + perTier * Math.max(0, tier - 1);
		return Double.isNaN(chance) ? 0 : Math.max(0, Math.min(1, chance));
	}

	/**
	 * Gold price split into the two trade cost slots: {gold blocks, gold ingots}. Up to {@value #MAX_INGOTS} the price is paid
	 * in ingots only; above, in blocks (9 gold each) plus the remaining ingots. Clamped to [1, {@value #MAX_PRICE}].
	 */
	public static int[] priceSplit(int price) {
		int p = Math.max(1, Math.min(MAX_PRICE, price));
		if (p <= MAX_INGOTS) {
			return new int[] {0, p};
		}
		return new int[] {p / 9, p % 9};
	}
}
