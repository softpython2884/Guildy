package dev.guildy.trade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeRollsTest {

	private static List<TradeEntry> table(String prefix, int size) {
		List<TradeEntry> list = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			list.add(TradeEntry.of(prefix + i, 1, 1, 1, 10));
		}
		return list;
	}

	@Test
	void countStaysInRange() {
		Random random = new Random(1);
		for (int i = 0; i < 200; i++) {
			int n = TradeRolls.count(3, 5, random);
			assertTrue(n >= 3 && n <= 5, "count " + n);
		}
		assertEquals(4, TradeRolls.count(4, 4, random));
		assertEquals(2, TradeRolls.count(5, 2, random), "min above max: max wins");
	}

	@Test
	void rollDrawsDistinctEntriesAndSkipsWeightless() {
		List<TradeEntry> table = table("a", 5);
		table.get(0).weight = 0;
		Random random = new Random(2);
		for (int i = 0; i < 50; i++) {
			List<TradeEntry> rolled = TradeRolls.roll(table, 3, random);
			assertEquals(3, rolled.size());
			assertEquals(3, new HashSet<>(rolled).size(), "no duplicate");
			assertTrue(!rolled.contains(table.get(0)), "weight 0 never drawn");
		}
		// Not enough pickable entries: everything pickable once.
		assertEquals(4, TradeRolls.roll(table, 10, random).size());
		assertTrue(TradeRolls.roll(List.of(), 3, random).isEmpty());
	}

	@Test
	void rollFollowsWeights() {
		List<TradeEntry> table = table("w", 2);
		table.get(0).weight = 90;
		table.get(1).weight = 10;
		Random random = new Random(3);
		int first = 0;
		for (int i = 0; i < 2000; i++) {
			if (TradeRolls.roll(table, 1, random).getFirst() == table.get(0)) {
				first++;
			}
		}
		assertTrue(first > 1700 && first < 1900, "heavy entry drawn " + first + "/2000");
	}

	@Test
	void tieredRollUsesOwnAndLowerTiersOnly() {
		List<List<TradeEntry>> tables = List.of(table("t1-", 30), table("t2-", 30), table("t3-", 30));
		Random random = new Random(4);
		int own = 0;
		int total = 0;
		for (int i = 0; i < 300; i++) {
			List<TradeEntry> rolled = TradeRolls.rollTiered(tables, 2, 6, 3, 1, random);
			assertEquals(6, rolled.size());
			assertEquals(6, new HashSet<>(rolled).size(), "no duplicate");
			for (TradeEntry entry : rolled) {
				assertTrue(!entry.item.startsWith("t3-"), "tier 2 hub never gets tier 3 offers");
				if (entry.item.startsWith("t2-")) {
					own++;
				}
				total++;
			}
		}
		double share = own / (double) total;
		assertTrue(Math.abs(share - 0.75) < 0.05, "own tier share " + share + " (weights 3:1)");
		// Tier 1 hub: tier 1 only.
		for (TradeEntry entry : TradeRolls.rollTiered(tables, 1, 8, 3, 1, random)) {
			assertTrue(entry.item.startsWith("t1-"));
		}
	}

	@Test
	void tieredRollSkipsExhaustedTiers() {
		List<List<TradeEntry>> tables = List.of(table("t1-", 10), table("t2-", 0), table("t3-", 2));
		List<TradeEntry> rolled = TradeRolls.rollTiered(tables, 3, 6, 3, 1, new Random(5));
		assertEquals(6, rolled.size(), "empty tier 2 skipped, tier 3 exhausted after 2 offers");
		Set<String> items = new HashSet<>();
		for (TradeEntry entry : rolled) {
			items.add(entry.item);
		}
		assertEquals(6, items.size());
		// Tier above the configured tiers: clamped to the highest one.
		assertEquals(4, TradeRolls.rollTiered(List.of(table("x", 4)), 3, 10, 3, 1, new Random(6)).size());
	}

	@Test
	void visitChanceGrowsWithTierAndIsClamped() {
		assertEquals(0.5, TradeRolls.visitChance(0.5, 0.15, 1), 1e-9);
		assertEquals(0.65, TradeRolls.visitChance(0.5, 0.15, 2), 1e-9);
		assertEquals(0.8, TradeRolls.visitChance(0.5, 0.15, 3), 1e-9);
		assertEquals(1.0, TradeRolls.visitChance(0.9, 0.15, 3), 1e-9);
		assertEquals(0.0, TradeRolls.visitChance(-1, 0, 1), 1e-9);
	}

	@Test
	void pricesAbove64ArePaidInGoldBlocks() {
		assertArrayEquals(new int[] {0, 1}, TradeRolls.priceSplit(0), "at least 1 gold");
		assertArrayEquals(new int[] {0, 64}, TradeRolls.priceSplit(64));
		assertArrayEquals(new int[] {7, 2}, TradeRolls.priceSplit(65));
		assertArrayEquals(new int[] {11, 0}, TradeRolls.priceSplit(99));
		assertArrayEquals(new int[] {30, 0}, TradeRolls.priceSplit(270));
		assertArrayEquals(new int[] {64, 8}, TradeRolls.priceSplit(100_000), "capped to one stack per cost slot");
		for (int price = 65; price <= TradeRolls.MAX_PRICE; price++) {
			int[] split = TradeRolls.priceSplit(price);
			assertEquals(price, split[0] * 9 + split[1]);
			assertTrue(split[0] <= 64 && split[1] < 9);
		}
	}
}
