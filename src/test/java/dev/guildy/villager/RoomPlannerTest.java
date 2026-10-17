package dev.guildy.villager;

import dev.guildy.villager.RoomPlanner.Line;
import dev.guildy.villager.RoomPlanner.Result;
import dev.guildy.villager.RoomPlanner.Settings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomPlannerTest {
	private static final List<String> CATEGORIES = List.of("Bois", "Pierre", "Minerais", "Armures");
	private static final String FALLBACK = "Divers";
	/** 25 % headroom, grow under 4 free slots, a category needs 9 stacks for its first line, at most 3 blocks shifted. */
	private static final Settings SETTINGS = new Settings(25, 4, 9, 3);

	/** {@code count} lines of 2 chests (ids = line * 10 + 0/1), 27 slots each. */
	private static List<Line> lines(int count) {
		List<Line> lines = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			lines.add(new Line(List.of(i * 10, i * 10 + 1), 54));
		}
		return lines;
	}

	/** Category of each line (from its first chest). */
	private static List<String> perLine(Result result, int count) {
		List<String> categories = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			assertEquals(result.assignment().get(i * 10), result.assignment().get(i * 10 + 1), "both chests of line " + i);
			categories.add(result.assignment().get(i * 10));
		}
		return categories;
	}

	/** Current assignment given per line. */
	private static Map<Integer, String> current(String... perLine) {
		Map<Integer, String> current = new HashMap<>();
		for (int i = 0; i < perLine.length; i++) {
			current.put(i * 10, perLine[i]);
			current.put(i * 10 + 1, perLine[i]);
		}
		return current;
	}

	private static Result plan(int lines, Map<Integer, String> current, Map<String, Integer> demand, Set<Integer> idle) {
		return RoomPlanner.plan(lines(lines), current, CATEGORIES, FALLBACK, demand, idle, SETTINGS, false);
	}

	@Test
	void firstPlanSizesBlocksInCategoryOrder() {
		// Bois holds 60 stacks (needs 75 slots = 2 lines), Minerais 10 (1 line), the fallback 1 line, the rest is free.
		Result result = plan(6, Map.of(), Map.of("Minerais", 10, "Bois", 60), Set.of());
		assertEquals(List.of("Bois", "Bois", "Minerais", FALLBACK, "", ""), perLine(result, 6));
		assertTrue(result.unplaced().isEmpty());
	}

	@Test
	void shortOfLinesTheFallbackAndTheBiggestCategoriesFirst() {
		Result result = plan(3, Map.of(), Map.of("Bois", 2, "Pierre", 40, "Minerais", 20, "Armures", 1), Set.of());
		assertEquals(List.of("Pierre", "Minerais", FALLBACK), perLine(result, 3));
		assertEquals(List.of("Bois", "Armures"), result.unplaced());
	}

	@Test
	void aStablePlanChangesNothing() {
		Map<String, Integer> demand = Map.of("Bois", 60, "Minerais", 10);
		Result first = plan(6, Map.of(), demand, Set.of());
		Result second = plan(6, first.assignment(), demand, Set.of());
		assertEquals(first.assignment(), second.assignment());
		assertTrue(second.changed().isEmpty());
	}

	@Test
	void aFewItemsOfANewCategoryNeverMoveTheRoom() {
		Map<Integer, String> current = current("Bois", "Minerais", FALLBACK, "", "");
		Result few = plan(5, current, Map.of("Bois", 10, "Minerais", 10, "Pierre", 3), Set.of());
		assertTrue(few.changed().isEmpty(), "3 stacks of stone stay in the fallback");
		// 9 stacks: Pierre gets the line after Bois; Minerais and the fallback move one line each (2 blocks shifted).
		Result enough = plan(5, current, Map.of("Bois", 10, "Minerais", 10, "Pierre", 9), Set.of());
		assertEquals(List.of("Bois", "Pierre", "Minerais", FALLBACK, ""), perLine(enough, 5));
	}

	@Test
	void aFullCategoryTakesTheFreeLineNextToIt() {
		// Minerais holds 52 stacks in its 54 slots: fewer than 4 free slots, one more line.
		Map<Integer, String> current = current("Bois", "Minerais", "", FALLBACK, "");
		Result result = plan(5, current, Map.of("Bois", 5, "Minerais", 52), Set.of());
		assertEquals(List.of("Bois", "Minerais", "Minerais", FALLBACK, ""), perLine(result, 5));
		assertEquals(Set.of(20, 21), result.changed());
	}

	@Test
	void tooManyBlocksToShiftTakesTheNearestFreeLine() {
		// Bois is full; four other blocks stand between it and the only free line (more than maxShift = 3).
		Map<Integer, String> current = current("Bois", "Pierre", "Minerais", "Armures", FALLBACK, "");
		Result result = plan(6, current, Map.of("Bois", 53, "Pierre", 1, "Minerais", 1, "Armures", 1), Set.of());
		assertEquals(List.of("Bois", "Pierre", "Minerais", "Armures", FALLBACK, "Bois"), perLine(result, 6));
		assertEquals(Set.of(50, 51), result.changed());
	}

	@Test
	void anIdleLineGoesBackToTheReserve() {
		// Line 1 (Bois) stayed empty for long and Bois fits in line 0: freed. Line 3 = the only fallback line: kept.
		Map<Integer, String> current = current("Bois", "Bois", "Pierre", FALLBACK);
		Result result = plan(4, current, Map.of("Bois", 5, "Pierre", 5), Set.of(1, 3));
		assertEquals(List.of("Bois", "", "Pierre", FALLBACK), perLine(result, 4));
		// A category that holds nothing loses its idle lines.
		Result empty = plan(4, current, Map.of("Pierre", 5), Set.of(0, 1));
		assertEquals(List.of("", "", "Pierre", FALLBACK), perLine(empty, 4));
	}

	@Test
	void theFallbackIsSizedForTheCategoriesWithoutLine() {
		// 45 stacks of misc items fit in the fallback line, but the 8 stacks of stone (no line yet) go there too: it grows.
		Map<Integer, String> current = current("Bois", FALLBACK, "", "");
		Result alone = plan(4, current, Map.of("Bois", 5, FALLBACK, 45), Set.of());
		assertTrue(alone.changed().isEmpty());
		Result result = plan(4, current, Map.of("Bois", 5, "Pierre", 8, FALLBACK, 45), Set.of());
		assertEquals(List.of("Bois", FALLBACK, FALLBACK, ""), perLine(result, 4));
		// Short of lines in a first plan: the categories left out are counted in the fallback too.
		Result shorter = plan(3, Map.of(), Map.of("Bois", 40, "Pierre", 40, "Minerais", 1, FALLBACK, 1), Set.of());
		assertEquals(List.of("Bois", "Pierre", FALLBACK), perLine(shorter, 3));
		assertEquals(List.of("Minerais"), shorter.unplaced());
	}

	@Test
	void onlyTheGivenAutomaticChestsAreAssigned() {
		// A manual chest (id 99) is not part of any line: never in the assignment, never changed.
		Map<Integer, String> current = new HashMap<>(current("Bois", ""));
		current.put(99, "Armures");
		Result result = plan(2, current, Map.of("Bois", 5, "Armures", 40), Set.of());
		assertFalse(result.assignment().containsKey(99));
		assertFalse(result.changed().contains(99));
	}

	@Test
	void unknownCategoriesAndFreshPlansStartOver() {
		// "Ancienne" no longer exists: its line counts as free. A fresh plan ignores the current assignment.
		Map<Integer, String> current = current("Ancienne", "Pierre", FALLBACK);
		Result result = plan(3, current, Map.of("Pierre", 5, "Bois", 20), Set.of());
		assertEquals(List.of("Bois", "Pierre", FALLBACK), perLine(result, 3));
		Result fresh = RoomPlanner.plan(lines(3), current, CATEGORIES, FALLBACK, Map.of("Pierre", 5, "Bois", 20), Set.of(), SETTINGS, true);
		assertEquals(List.of("Bois", "Pierre", FALLBACK), perLine(fresh, 3));
	}

	@Test
	void planRoomUsesTheColumnsOrRowsOfTheWall() {
		// 3 columns × 2 chests facing south (x = 0..2, y = 65 top / 64 bottom); the top chest of the middle column is
		// manual (id 99): it keeps its category and takes no part in the plan.
		List<RoomPlanner.RoomChest> chests = new ArrayList<>();
		int id = 0;
		for (int x = 0; x < 3; x++) {
			for (int y = 65; y >= 64; y--) {
				boolean manual = x == 1 && y == 65;
				chests.add(new RoomPlanner.RoomChest(ChestOrder.Unit.single(manual ? 99 : id, x, y, 10, ChestOrder.Facing.SOUTH), 27, !manual));
				id++;
			}
		}
		Map<Integer, String> current = new HashMap<>(Map.of(99, "Armures"));
		Map<String, Integer> demand = Map.of("Bois", 20, "Pierre", 5);
		Result columns = RoomPlanner.planRoom(chests, ChestOrder.Mode.COLUMNS, current, CATEGORIES, FALLBACK, demand, Set.of(), SETTINGS, false);
		// Column x=0 (ids 0, 1) Bois, column x=1 (id 3 only) Pierre, column x=2 (ids 4, 5) the fallback.
		assertEquals(Map.of(0, "Bois", 1, "Bois", 3, "Pierre", 4, FALLBACK, 5, FALLBACK), columns.assignment());
		assertFalse(columns.changed().contains(99));
		// By rows: the top row (ids 0, 4; 99 manual) then the bottom row (ids 1, 3, 5). Only 2 lines: Bois and the
		// fallback, which also receives the stone.
		Result rows = RoomPlanner.planRoom(chests, ChestOrder.Mode.ROWS, current, CATEGORIES, FALLBACK, demand, Set.of(), SETTINGS, false);
		assertEquals(Map.of(0, "Bois", 4, "Bois", 1, FALLBACK, 3, FALLBACK, 5, FALLBACK), rows.assignment());
		assertEquals(List.of("Pierre"), rows.unplaced());
	}

	@Test
	void needAddsHeadroom() {
		assertEquals(0, RoomPlanner.need(0, SETTINGS));
		assertEquals(14, RoomPlanner.need(10, SETTINGS));
		assertEquals(125, RoomPlanner.need(100, SETTINGS));
	}
}
