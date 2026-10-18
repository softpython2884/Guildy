package dev.guildy.villager;

import dev.guildy.villager.StorageLayout.Rules;
import dev.guildy.villager.StorageLayout.Stack;
import dev.guildy.villager.StorageLayout.Transfer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageLayoutTest {
	/** Keys 0..9 stack to 64, keys 10..19 to 16, keys 20+ do not stack (armor, tools). */
	private static final Rules RULES = key -> key < 10 ? 64 : key < 20 ? 16 : 1;

	private static Stack s(int key, int count) {
		return new Stack(key, count);
	}

	private static Stack[][] contents(Stack[]... containers) {
		return containers;
	}

	private static Stack[] container(int size, Stack... stacks) {
		Stack[] slots = new Stack[size];
		System.arraycopy(stacks, 0, slots, 0, stacks.length);
		return slots;
	}

	@Test
	void layoutMergesAndSortsInReadingOrder() {
		// Half stacks scattered over two chests of 4 slots.
		Stack[][] current = contents(
				container(4, s(3, 10), null, s(1, 30), s(3, 50)),
				container(4, s(1, 40), null, s(21, 1), s(20, 1)));
		Stack[][] target = StorageLayout.layout(new int[]{4, 4}, StorageLayout.totals(current, null), RULES);
		assertNotNull(target);
		assertArrayEquals(new Stack[]{s(1, 64), s(1, 6), s(3, 60), s(20, 1)}, target[0]);
		assertArrayEquals(new Stack[]{s(21, 1), null, null, null}, target[1]);
		assertEquals(StorageLayout.totals(current, null), StorageLayout.totals(target, null));
	}

	@Test
	void armorOfAGroupFollowsTheCanonicalOrderAcrossChests() {
		// 4 chests of 2 slots, 8 unstackable pieces (keys 20..27 = helmet, chestplate... of each material), shuffled.
		Stack[][] current = contents(
				container(2, s(27, 1), s(22, 1)),
				container(2, s(20, 1), s(25, 1)),
				container(2, null, s(23, 1)),
				container(2, s(21, 1), s(24, 1)));
		current[2][0] = s(26, 1);
		Stack[][] target = StorageLayout.layout(new int[]{2, 2, 2, 2}, StorageLayout.totals(current, null), RULES);
		assertNotNull(target);
		int expected = 20;
		for (Stack[] chest : target) {
			for (Stack stack : chest) {
				assertEquals(s(expected++, 1), stack);
			}
		}
	}

	@Test
	void refusedStackTakesTheNextAcceptingSlot() {
		// Container 0 refuses key 5 (a shulker box in a shulker box): it goes to container 1, the rest keeps its order.
		Rules rules = new Rules() {
			@Override
			public int maxStack(int key) {
				return 64;
			}

			@Override
			public boolean accepts(int container, int key) {
				return !(container == 0 && key == 5);
			}
		};
		SortedMap<Integer, Long> totals = StorageLayout.totals(contents(container(3, s(5, 1), s(1, 2), s(7, 3))), null);
		Stack[][] target = StorageLayout.layout(new int[]{2, 2}, totals, rules);
		assertNotNull(target);
		assertArrayEquals(new Stack[]{s(1, 2), s(7, 3)}, target[0]);
		assertArrayEquals(new Stack[]{s(5, 1), null}, target[1]);
	}

	@Test
	void layoutThatDoesNotFitIsRefused() {
		SortedMap<Integer, Long> totals = StorageLayout.totals(contents(container(3, s(20, 1), s(21, 1), s(22, 1))), null);
		assertNull(StorageLayout.layout(new int[]{2}, totals, RULES));
	}

	@Test
	void arrangeOfAWholeGroupGivesTheTarget() {
		Stack[][] current = contents(
				container(3, s(2, 5), s(1, 64), s(2, 5)),
				container(3, null, s(1, 1), null));
		Stack[][] target = StorageLayout.layout(new int[]{3, 3}, StorageLayout.totals(current, null), RULES);
		Stack[][] arranged = StorageLayout.arrange(new int[]{0, 1}, target, current, RULES);
		assertNotNull(arranged);
		assertTrue(StorageLayout.sameLayout(target, arranged));
		// Idempotent: arranging the result changes nothing.
		Stack[][] again = StorageLayout.arrange(new int[]{0, 1}, target, arranged, RULES);
		assertTrue(StorageLayout.sameLayout(arranged, again));
	}

	@Test
	void arrangeOfASectionKeepsItsItemsAndPutsTheOthersLast() {
		// Group of 3 chests; the section = chests 1 and 2. Target: chest 0 = [1x64,1x64], chest 1 = [1x10, 2x5], chest 2 = [3x7, -].
		Stack[][] current = contents(
				container(2, s(3, 7), s(2, 5)),
				container(2, s(1, 64), null),
				container(2, s(1, 64), s(1, 10)));
		Stack[][] target = StorageLayout.layout(new int[]{2, 2, 2}, StorageLayout.totals(current, null), RULES);
		assertNotNull(target);
		assertArrayEquals(new Stack[]{s(1, 64), s(1, 64)}, target[0]);
		Stack[][] arranged = StorageLayout.arrange(new int[]{1, 2}, target, current, RULES);
		assertNotNull(arranged);
		assertEquals(StorageLayout.totals(current, new int[]{1, 2}), StorageLayout.totals(arranged, null));
		// The section's own target slot (key 1 x 10 in chest 1) is filled first and topped up to a full stack.
		assertEquals(s(1, 64), arranged[0][0]);
		// The other key-1 stack (belongs to chest 0) stays merged, at the end.
		assertEquals(s(1, 10), arranged[1][1]);
	}

	@Test
	void transfersBringEachSectionItsItems() {
		Stack[][] current = contents(
				container(2, s(4, 3), s(2, 64)),
				container(2, s(1, 64), null));
		Stack[][] target = StorageLayout.layout(new int[]{2, 2}, StorageLayout.totals(current, null), RULES);
		List<Transfer> transfers = StorageLayout.transfers(List.of(new int[]{0}, new int[]{1}), target, current);
		assertEquals(List.of(new Transfer(1, 0, 1, 64), new Transfer(0, 1, 4, 3)), transfers);
	}

	@Test
	void keysAbsentFromTheTargetAreNotCarriedBetweenSections() {
		// Key 9 is to be taken out of the group (not in the target): no transfer for it.
		Stack[][] current = contents(container(2, s(9, 3), null), container(2, null, null));
		SortedMap<Integer, Long> totals = StorageLayout.totals(current, null);
		totals.remove(9);
		Stack[][] target = StorageLayout.layout(new int[]{2, 2}, totals, RULES);
		assertTrue(StorageLayout.transfers(List.of(new int[]{0}, new int[]{1}), target, current).isEmpty());
		assertTrue(!StorageLayout.sameLayout(current, target));
	}

	// ------------------------------------------------------------------ by columns

	/** Kinds: keys 0..9 are their own kind, keys 10 and 11 are two variants of one item (same kind). */
	private static Rules columns(Rules base) {
		return new Rules() {
			@Override
			public int maxStack(int key) {
				return base.maxStack(key);
			}

			@Override
			public boolean accepts(int container, int key) {
				return base.accepts(container, key);
			}

			@Override
			public int kind(int key) {
				return key == 11 ? 10 : key;
			}
		};
	}

	/** Slot of a 9-wide container at (column, row), 0-based. */
	private static int slot(int column, int row) {
		return row * 9 + column;
	}

	private static SortedMap<Integer, Long> totals(long... keyCounts) {
		SortedMap<Integer, Long> totals = new java.util.TreeMap<>();
		for (int i = 0; i + 1 < keyCounts.length; i += 2) {
			totals.put((int) keyCounts[i], keyCounts[i + 1]);
		}
		return totals;
	}

	@Test
	void columnSlotOrderOfChestsBarrelsAndOddSizes() {
		assertArrayEquals(new int[]{0, 9, 18, 1, 10, 19, 2, 11, 20, 3, 12, 21, 4, 13, 22, 5, 14, 23, 6, 15, 24, 7, 16, 25, 8, 17, 26},
				StorageLayout.slotOrder(27, StorageLayout.Fill.COLUMNS));
		int[] doubleChest = StorageLayout.slotOrder(54, StorageLayout.Fill.COLUMNS);
		assertArrayEquals(new int[]{0, 9, 18, 27, 36, 45, 1, 10}, java.util.Arrays.copyOf(doubleChest, 8));
		assertArrayEquals(new int[]{0, 1, 2, 3, 4}, StorageLayout.slotOrder(5, StorageLayout.Fill.COLUMNS), "a hopper-sized container keeps its order");
		assertArrayEquals(new int[]{0, 1, 2, 3}, java.util.Arrays.copyOf(StorageLayout.slotOrder(27, StorageLayout.Fill.ROWS), 4));
		assertEquals(StorageLayout.Fill.COLUMNS, StorageLayout.Fill.parse("colonnes"));
		assertEquals(StorageLayout.Fill.ROWS, StorageLayout.Fill.parse(" Lignes "));
		assertEquals(StorageLayout.Fill.ROWS, StorageLayout.Fill.parse("rangées"));
		assertNull(StorageLayout.Fill.parse("diagonal"));
	}

	/**
	 * The server owner's screenshot: a double chest (6 rows) with 13 full stacks + 38 wheat, one stack of sugar cane and
	 * 27 stacks of wheat seeds. Wheat fills columns 1 and 2 top to bottom and the top of column 3 (64, 38); the cane starts
	 * column 4; the seeds start column 5 and fill columns 5 to 9 (the last one with 3 stacks).
	 */
	@Test
	void ownerScreenshotIsLaidOutByColumns() {
		int wheat = 0;
		int cane = 1;
		int seeds = 2;
		Stack[][] target = StorageLayout.layout(new int[]{54}, totals(wheat, 13 * 64 + 38, cane, 20, seeds, 27 * 64), RULES, StorageLayout.Fill.COLUMNS);
		assertNotNull(target);
		Stack[] chest = target[0];
		for (int row = 0; row < 6; row++) {
			assertEquals(s(wheat, 64), chest[slot(0, row)], "column 1, row " + row);
			assertEquals(s(wheat, 64), chest[slot(1, row)], "column 2, row " + row);
		}
		assertEquals(s(wheat, 64), chest[slot(2, 0)]);
		assertEquals(s(wheat, 38), chest[slot(2, 1)]);
		for (int row = 2; row < 6; row++) {
			assertNull(chest[slot(2, row)], "the rest of column 3 stays free");
		}
		assertEquals(s(cane, 20), chest[slot(3, 0)], "the cane starts a new column");
		for (int row = 1; row < 6; row++) {
			assertNull(chest[slot(3, row)]);
		}
		for (int column = 4; column < 8; column++) {
			for (int row = 0; row < 6; row++) {
				assertEquals(s(seeds, 64), chest[slot(column, row)], "seeds in column " + (column + 1));
			}
		}
		for (int row = 0; row < 6; row++) {
			assertEquals(row < 3 ? s(seeds, 64) : null, chest[slot(8, row)], "last column only partly");
		}
		// Already arranged: nothing to do (stable).
		assertTrue(StorageLayout.sameLayout(target, StorageLayout.arrange(new int[]{0}, target, target, RULES)));
	}

	@Test
	void variantsOfOneItemStayTogetherInTheirColumn() {
		// Keys 10 / 11 = the same item (plain and enchanted, max 16): no fresh column between them; key 12 starts a new one.
		Stack[][] target = StorageLayout.layout(new int[]{27}, totals(10, 16 * 2, 11, 1, 12, 5), columns(RULES), StorageLayout.Fill.COLUMNS);
		assertNotNull(target);
		assertEquals(s(10, 16), target[0][slot(0, 0)]);
		assertEquals(s(10, 16), target[0][slot(0, 1)]);
		assertEquals(s(11, 1), target[0][slot(0, 2)], "the variant follows its item in the same column");
		assertEquals(s(12, 5), target[0][slot(1, 0)]);
		// Two different items in the same place: the second one starts the next column.
		Stack[][] split = StorageLayout.layout(new int[]{27}, totals(10, 16, 12, 5), columns(RULES), StorageLayout.Fill.COLUMNS);
		assertNotNull(split);
		assertEquals(s(12, 5), split[0][slot(1, 0)]);
		assertNull(split[0][slot(0, 1)]);
	}

	@Test
	void newKindFollowsThePreviousOneWhenColumnsRunOut() {
		// A single chest (3 rows) with 9 kinds of one stack each: one column each.
		long[] nine = new long[18];
		for (int k = 0; k < 9; k++) {
			nine[2 * k] = k;
			nine[2 * k + 1] = 10;
		}
		Stack[][] target = StorageLayout.layout(new int[]{27}, totals(nine), RULES, StorageLayout.Fill.COLUMNS);
		assertNotNull(target);
		for (int k = 0; k < 9; k++) {
			assertEquals(s(k, 10), target[0][slot(k, 0)], "kind " + k + " at the top of column " + (k + 1));
		}
		// 25 stacks of key 0 then one stack of each of keys 1, 2: no room for a fresh column, they follow right after.
		Stack[][] full = StorageLayout.layout(new int[]{27}, totals(0, 25 * 64, 1, 3, 2, 4), RULES, StorageLayout.Fill.COLUMNS);
		assertNotNull(full);
		assertEquals(s(0, 64), full[0][slot(8, 0)]);
		assertEquals(s(1, 3), full[0][slot(8, 1)], "continues right after the previous kind");
		assertEquals(s(2, 4), full[0][slot(8, 2)]);
	}

	@Test
	void columnsContinueAcrossTheChestsOfAGroup() {
		// Two single chests (3 rows each): key 0 = 28 stacks spills into chest 2; key 1 starts at the top of its next column.
		Stack[][] target = StorageLayout.layout(new int[]{27, 27}, totals(0, 28 * 64, 1, 2 * 64), RULES, StorageLayout.Fill.COLUMNS);
		assertNotNull(target);
		assertEquals(27, StorageLayout.used(target[0]));
		assertEquals(s(0, 64), target[1][slot(0, 0)], "the 28th stack opens chest 2");
		assertNull(target[1][slot(0, 1)]);
		assertEquals(s(1, 64), target[1][slot(1, 0)], "the next kind starts a fresh column of chest 2");
		assertEquals(s(1, 64), target[1][slot(1, 1)]);
	}

	@Test
	void refusedStackStillFitsByColumns() {
		// Container 0 (a shulker box) refuses key 5: the other keys keep their columns, key 5 goes to container 1.
		Rules rules = new Rules() {
			@Override
			public int maxStack(int key) {
				return 64;
			}

			@Override
			public boolean accepts(int container, int key) {
				return !(container == 0 && key == 5);
			}
		};
		Stack[][] target = StorageLayout.layout(new int[]{27, 27}, totals(1, 2, 5, 1, 7, 3), rules, StorageLayout.Fill.COLUMNS);
		assertNotNull(target);
		assertEquals(s(1, 2), target[0][slot(0, 0)]);
		assertEquals(s(7, 3), target[0][slot(1, 0)], "the column key 5 could not take goes to the next kind");
		assertEquals(s(5, 1), target[1][slot(0, 0)]);
		// A full layout with refusals: never worse than packing the columns.
		Stack[][] tight = StorageLayout.layout(new int[]{2, 1}, totals(1, 64, 5, 1, 7, 64), rules, StorageLayout.Fill.COLUMNS);
		assertNotNull(tight);
		assertEquals(s(5, 1), tight[1][0]);
	}

	/** Random groups by columns: same guarantees as by rows, and the layout fits whenever the rows layout fits. */
	@Test
	void randomGroupsByColumnsConserveItems() {
		Random random = new Random(4321);
		for (int round = 0; round < 300; round++) {
			int chests = 1 + random.nextInt(4);
			int[] sizes = new int[chests];
			Stack[][] current = new Stack[chests][];
			for (int c = 0; c < chests; c++) {
				sizes[c] = random.nextBoolean() ? 27 : 54;
				current[c] = new Stack[sizes[c]];
				for (int slot = 0; slot < sizes[c]; slot++) {
					if (random.nextInt(4) == 0) {
						int key = random.nextInt(25);
						current[c][slot] = s(key, 1 + random.nextInt(RULES.maxStack(key)));
					}
				}
			}
			SortedMap<Integer, Long> totals = StorageLayout.totals(current, null);
			Stack[][] target = StorageLayout.layout(sizes, totals, columns(RULES), StorageLayout.Fill.COLUMNS);
			assertNotNull(target, "the merged layout always fits in the same slots");
			assertEquals(totals, StorageLayout.totals(target, null));
			// Keys in ascending order along the columns, at most one partial stack per key.
			int lastKey = -1;
			Map<Integer, Integer> partial = new HashMap<>();
			for (int c = 0; c < chests; c++) {
				for (int slot : StorageLayout.slotOrder(sizes[c], StorageLayout.Fill.COLUMNS)) {
					Stack stack = target[c][slot];
					if (stack == null) {
						continue;
					}
					assertTrue(stack.key() >= lastKey, "keys in order along the columns");
					lastKey = stack.key();
					if (stack.count() < RULES.maxStack(stack.key())) {
						partial.merge(stack.key(), 1, Integer::sum);
					}
				}
			}
			partial.values().forEach(count -> assertEquals(1, count, "at most one partial stack per key"));
			Stack[][] arranged = StorageLayout.arrange(java.util.stream.IntStream.range(0, chests).toArray(), target, current, RULES);
			assertNotNull(arranged);
			assertTrue(StorageLayout.sameLayout(target, arranged), "arranging the whole group gives the target");
			assertTrue(StorageLayout.sameLayout(arranged, StorageLayout.arrange(java.util.stream.IntStream.range(0, chests).toArray(), target, arranged, RULES)),
					"idempotent");
		}
	}

	/** Random groups: the layout keeps every item, merges every key and follows the key order; arrange never loses anything. */
	@Test
	void randomGroupsConserveItems() {
		Random random = new Random(1234);
		for (int round = 0; round < 300; round++) {
			int chests = 1 + random.nextInt(5);
			int[] sizes = new int[chests];
			Stack[][] current = new Stack[chests][];
			for (int c = 0; c < chests; c++) {
				sizes[c] = 3 + random.nextInt(6);
				current[c] = new Stack[sizes[c]];
				for (int slot = 0; slot < sizes[c]; slot++) {
					if (random.nextInt(3) > 0) {
						int key = random.nextInt(25);
						current[c][slot] = s(key, 1 + random.nextInt(RULES.maxStack(key)));
					}
				}
			}
			SortedMap<Integer, Long> totals = StorageLayout.totals(current, null);
			Stack[][] target = StorageLayout.layout(sizes, totals, RULES);
			assertNotNull(target, "the merged layout always fits in the same slots");
			assertEquals(totals, StorageLayout.totals(target, null));
			int lastKey = -1;
			Map<Integer, Integer> partial = new HashMap<>();
			List<Stack> flat = new ArrayList<>();
			for (Stack[] chest : target) {
				for (Stack stack : chest) {
					if (stack != null) {
						flat.add(stack);
					}
				}
			}
			for (int i = 0; i < flat.size(); i++) {
				Stack stack = flat.get(i);
				assertTrue(stack.key() >= lastKey, "keys in order");
				lastKey = stack.key();
				if (stack.count() < RULES.maxStack(stack.key())) {
					partial.merge(stack.key(), 1, Integer::sum);
				}
			}
			partial.values().forEach(count -> assertEquals(1, count, "at most one partial stack per key"));
			// Any section, any target: the section keeps exactly its items.
			int from = random.nextInt(chests);
			int to = from + random.nextInt(chests - from);
			int[] section = new int[to - from + 1];
			for (int i = 0; i < section.length; i++) {
				section[i] = from + i;
			}
			Stack[][] arranged = StorageLayout.arrange(section, target, current, RULES);
			if (arranged != null) {
				assertEquals(StorageLayout.totals(current, section), StorageLayout.totals(arranged, null));
			}
		}
	}
}
