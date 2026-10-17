package dev.guildy.scroll;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drop rolls: the table chance gates the roll, the weights pick the scroll, the count stays within min..max; observed
 * frequencies match the computed probabilities.
 */
class ScrollRollsTest {
	/** nextDouble() == 0: every chance passes, the first pickable entry is picked. */
	private static final RandomGenerator LOWEST = () -> 0L;
	/** nextDouble() just below 1: every chance below 1 fails. */
	private static final RandomGenerator HIGHEST = () -> -1L;

	private static ScrollConfig.DropTable table(double chance, int min, int max, Object... idWeight) {
		ScrollConfig.DropTable table = new ScrollConfig.DropTable();
		table.chance = chance;
		table.min = min;
		table.max = max;
		for (int i = 0; i < idWeight.length; i += 2) {
			table.items.put((String) idWeight[i], ((Number) idWeight[i + 1]).doubleValue());
		}
		return table;
	}

	@Test
	void chanceGatesTheRoll() {
		ScrollConfig.DropTable table = table(0.3, 1, 1, "a", 1);
		ScrollRolls.Drop drop = ScrollRolls.roll(table, id -> true, LOWEST);
		assertNotNull(drop);
		assertEquals("a", drop.id());
		assertEquals(1, drop.count());
		assertNull(ScrollRolls.roll(table, id -> true, HIGHEST));
		assertNull(ScrollRolls.roll(table(0, 1, 1, "a", 1), id -> true, LOWEST), "chance 0: never");
		assertNotNull(ScrollRolls.roll(table(1, 1, 1, "a", 1), id -> true, HIGHEST), "chance 1: always");
		assertNull(ScrollRolls.roll(null, id -> true, LOWEST));
		assertNull(ScrollRolls.roll(table(1, 1, 1, "a", 1), id -> false, LOWEST), "unusable scrolls are never picked");
		assertNull(ScrollRolls.roll(table(1, 1, 1, "a", 0), id -> true, LOWEST), "weight 0: never");
	}

	@Test
	void frequenciesMatchTheProbabilities() {
		ScrollConfig.DropTable table = table(0.5, 1, 3, "a", 3, "b", 1, "zero", 0);
		RandomGenerator random = new SplittableRandom(42);
		Map<String, Integer> counts = new HashMap<>();
		int rolls = 200_000;
		long scrolls = 0;
		for (int i = 0; i < rolls; i++) {
			ScrollRolls.Drop drop = ScrollRolls.roll(table, id -> true, random);
			if (drop != null) {
				counts.merge(drop.id(), 1, Integer::sum);
				assertTrue(drop.count() >= 1 && drop.count() <= 3, "count within min..max");
				scrolls += drop.count();
			}
		}
		assertEquals(0.375, ScrollRolls.probability(table, "a", id -> true), 1e-9);
		assertEquals(0.125, ScrollRolls.probability(table, "b", id -> true), 1e-9);
		assertEquals(0.0, ScrollRolls.probability(table, "zero", id -> true));
		assertEquals(0.375, counts.getOrDefault("a", 0) / (double) rolls, 0.01);
		assertEquals(0.125, counts.getOrDefault("b", 0) / (double) rolls, 0.01);
		assertNull(counts.get("zero"));
		assertEquals(1.0, ScrollRolls.expected(table, id -> true), 1e-9, "0.5 x (1 + 3) / 2");
		assertEquals(1.0, scrolls / (double) rolls, 0.02);
		assertEquals(0.0, ScrollRolls.expected(table(1, 1, 1, "a", 1), id -> false));
	}

	@Test
	void countRange() {
		assertEquals(1, ScrollRolls.count(table(1, 1, 1), LOWEST));
		assertEquals(2, ScrollRolls.count(table(1, 2, 5), LOWEST));
		assertEquals(4, ScrollRolls.count(table(1, 4, 2), LOWEST), "max below min: min");
		assertEquals(1, ScrollRolls.count(table(1, 0, 0), LOWEST), "one at least");
	}
}
