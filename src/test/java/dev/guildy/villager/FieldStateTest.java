package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldStateTest {
	private static BlockPos pos(int i) {
		return new BlockPos(i, 64, -i);
	}

	@Test
	void retillMemoryIsBoundedOldestFirst() {
		List<FieldState.Trampled> list = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			assertTrue(FieldState.remember(list, pos(i), "minecraft:wheat_seeds", 4));
		}
		assertEquals(4, list.size(), "bounded");
		assertEquals(List.of(pos(6), pos(7), pos(8), pos(9)), list.stream().map(entry -> entry.pos).toList(), "newest kept");
		// Trampled again: no duplicate, it becomes the newest (with the crop seen this time).
		FieldState.remember(list, pos(7), "minecraft:carrot", 4);
		assertEquals(List.of(pos(6), pos(8), pos(9), pos(7)), list.stream().map(entry -> entry.pos).toList());
		assertEquals("minecraft:carrot", list.getLast().seed);
		assertTrue(FieldState.forget(list, pos(8)));
		assertFalse(FieldState.forget(list, pos(8)));
		assertEquals(3, list.size());
		assertTrue(FieldState.remember(list, pos(20), "", 0), "memory off: everything forgotten");
		assertTrue(list.isEmpty());
		assertFalse(FieldState.remember(list, pos(21), "", 0));
	}

	@Test
	void holesAreUniqueAndBounded() {
		List<BlockPos> holes = new ArrayList<>();
		assertTrue(FieldState.addHole(holes, pos(1), 3));
		assertFalse(FieldState.addHole(holes, pos(1), 3), "once");
		FieldState.addHole(holes, pos(2), 3);
		FieldState.addHole(holes, pos(3), 3);
		FieldState.addHole(holes, pos(4), 3);
		assertEquals(List.of(pos(2), pos(3), pos(4)), holes);
		assertFalse(FieldState.addHole(holes, pos(5), 0));
	}

	@Test
	void sanitizeDropsNullsAndDuplicates() {
		FieldState state = new FieldState();
		state.holes = new ArrayList<>(Arrays.asList(pos(1), null, pos(1), pos(2)));
		FieldState.Trampled noSeed = new FieldState.Trampled(pos(3), "x");
		noSeed.seed = null;
		state.trampled = new ArrayList<>(Arrays.asList(new FieldState.Trampled(pos(3), "a"), null, new FieldState.Trampled(null, ""),
				noSeed, new FieldState.Trampled(pos(4), null)));
		state.sanitize();
		assertEquals(List.of(pos(1), pos(2)), state.holes);
		assertEquals(2, state.trampled.size());
		assertEquals("a", state.trampled.getFirst().seed);
		assertEquals("", state.trampled.get(1).seed);
		FieldState empty = new FieldState();
		empty.holes = null;
		empty.trampled = null;
		empty.sanitize();
		assertTrue(empty.holes.isEmpty() && empty.trampled.isEmpty());
	}
}
