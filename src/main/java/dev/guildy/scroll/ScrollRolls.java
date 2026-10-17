package dev.guildy.scroll;

import dev.guildy.wave.WeightedPicker;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Pure drop maths (unit-tested): one roll in a source table = the table chance, then a weighted pick among its usable
 * scrolls, then a count between the table's min and max.
 */
public final class ScrollRolls {
	private ScrollRolls() {
	}

	/** The result of a successful roll. */
	public record Drop(String id, int count) {
	}

	/**
	 * @param usable scrolls that can be created (known definitions)
	 * @return the rolled scroll and count, or null (failed chance, empty table)
	 */
	public static @Nullable Drop roll(ScrollConfig.@Nullable DropTable table, Predicate<String> usable, RandomGenerator random) {
		if (table == null || table.items == null || table.chance <= 0) {
			return null;
		}
		if (table.chance < 1 && random.nextDouble() >= table.chance) {
			return null;
		}
		String id = pick(table, usable, random);
		return id == null ? null : new Drop(id, count(table, random));
	}

	/** Weighted pick ignoring the chance (null when nothing is pickable). */
	public static @Nullable String pick(ScrollConfig.DropTable table, Predicate<String> usable, RandomGenerator random) {
		List<Map.Entry<String, Double>> entries = new ArrayList<>();
		for (Map.Entry<String, Double> entry : table.items.entrySet()) {
			if (entry.getValue() != null && entry.getValue() > 0 && usable.test(entry.getKey())) {
				entries.add(entry);
			}
		}
		Map.Entry<String, Double> picked = WeightedPicker.pick(entries, Map.Entry::getValue, random);
		return picked == null ? null : picked.getKey();
	}

	/** Number of scrolls of a drop: uniform in [min, max] (1 at least). */
	public static int count(ScrollConfig.DropTable table, RandomGenerator random) {
		int min = Math.max(1, table.min);
		int max = Math.max(min, table.max);
		return max > min ? min + random.nextInt(max - min + 1) : min;
	}

	/** Probability (0..1) that one roll of the table gives this scroll. */
	public static double probability(ScrollConfig.@Nullable DropTable table, String id, Predicate<String> usable) {
		if (table == null || table.items == null || table.chance <= 0) {
			return 0;
		}
		double total = 0;
		double own = 0;
		for (Map.Entry<String, Double> entry : table.items.entrySet()) {
			if (entry.getValue() != null && entry.getValue() > 0 && usable.test(entry.getKey())) {
				total += entry.getValue();
				if (entry.getKey().equals(id)) {
					own += entry.getValue();
				}
			}
		}
		return total <= 0 ? 0 : Math.min(1, table.chance) * own / total;
	}

	/** Expected number of scrolls per roll of the table. */
	public static double expected(ScrollConfig.@Nullable DropTable table, Predicate<String> usable) {
		if (table == null || table.items == null) {
			return 0;
		}
		double weights = 0;
		for (Map.Entry<String, Double> entry : table.items.entrySet()) {
			if (entry.getValue() != null && entry.getValue() > 0 && usable.test(entry.getKey())) {
				weights += entry.getValue();
			}
		}
		if (weights <= 0) {
			return 0;
		}
		int min = Math.max(1, table.min);
		int max = Math.max(min, table.max);
		return Math.min(1, Math.max(0, table.chance)) * (min + max) / 2.0;
	}
}
