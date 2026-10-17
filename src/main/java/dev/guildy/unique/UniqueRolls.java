package dev.guildy.unique;

import dev.guildy.wave.WeightedPicker;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Pure drop maths (unit-tested): one roll in a source table = the table chance, then a weighted pick among its usable
 * items.
 */
public final class UniqueRolls {
	private UniqueRolls() {
	}

	/**
	 * @param usable items that can be created (known definitions)
	 * @return the rolled item id, or null (failed chance, empty table)
	 */
	public static @Nullable String roll(UniqueConfig.@Nullable DropTable table, Predicate<String> usable, RandomGenerator random) {
		if (table == null || table.items == null || table.chance <= 0) {
			return null;
		}
		if (table.chance < 1 && random.nextDouble() >= table.chance) {
			return null;
		}
		return pick(table, usable, random);
	}

	/** Result of a roll with uniqueness: nothing (failed chance, empty table), an item, or every item already taken. */
	public enum Kind {
		NOTHING,
		ITEM,
		EXHAUSTED
	}

	public record Outcome(Kind kind, @Nullable String id) {
		static final Outcome NOTHING = new Outcome(Kind.NOTHING, null);
		static final Outcome EXHAUSTED = new Outcome(Kind.EXHAUSTED, null);
	}

	/**
	 * One roll that skips the {@code excluded} ids (held by the recipient's guild, or anywhere in "server" mode): the table
	 * chance first (unchanged drop rate), then the weighted pick among the usable, non-excluded items. EXHAUSTED when the
	 * chance passed but every usable item is excluded.
	 */
	public static Outcome roll(UniqueConfig.@Nullable DropTable table, Predicate<String> usable, Set<String> excluded, RandomGenerator random) {
		if (table == null || table.items == null || table.chance <= 0) {
			return Outcome.NOTHING;
		}
		if (table.chance < 1 && random.nextDouble() >= table.chance) {
			return Outcome.NOTHING;
		}
		String id = pick(table, key -> usable.test(key) && !excluded.contains(key), random);
		if (id != null) {
			return new Outcome(Kind.ITEM, id);
		}
		for (Map.Entry<String, Double> entry : table.items.entrySet()) {
			if (entry.getValue() != null && entry.getValue() > 0 && usable.test(entry.getKey())) {
				return Outcome.EXHAUSTED;
			}
		}
		return Outcome.NOTHING;
	}

	/** Weighted pick ignoring the chance (null when nothing is pickable). */
	public static @Nullable String pick(UniqueConfig.DropTable table, Predicate<String> usable, RandomGenerator random) {
		List<Map.Entry<String, Double>> entries = new ArrayList<>();
		for (Map.Entry<String, Double> entry : table.items.entrySet()) {
			if (entry.getValue() != null && entry.getValue() > 0 && usable.test(entry.getKey())) {
				entries.add(entry);
			}
		}
		Map.Entry<String, Double> picked = WeightedPicker.pick(entries, Map.Entry::getValue, random);
		return picked == null ? null : picked.getKey();
	}

	/** Probability (0..1) that one roll of the table gives this item. */
	public static double probability(UniqueConfig.DropTable table, String id, Predicate<String> usable) {
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
}
