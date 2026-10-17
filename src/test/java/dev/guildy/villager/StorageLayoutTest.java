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
