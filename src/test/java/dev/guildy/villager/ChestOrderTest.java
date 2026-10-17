package dev.guildy.villager;

import dev.guildy.villager.ChestOrder.Facing;
import dev.guildy.villager.ChestOrder.Mode;
import dev.guildy.villager.ChestOrder.Result;
import dev.guildy.villager.ChestOrder.Unit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestOrderTest {
	/**
	 * A wall of {@code width} × {@code height} single chests facing {@code facing}; unit ids encode the position along
	 * the wall as seen from the front: id = column * 10 + row (column 0 = leftmost, row 0 = top).
	 */
	private static List<Unit> wall(Facing facing, int width, int height) {
		List<Unit> units = new ArrayList<>();
		for (int column = 0; column < width; column++) {
			// "Right" of a viewer facing the front: (facing.dz, -facing.dx).
			int x = column * facing.dz;
			int z = -column * facing.dx;
			for (int row = 0; row < height; row++) {
				units.add(Unit.single(column * 10 + row, 100 + x, 70 - row, 200 + z, facing));
			}
		}
		// Shuffled input: the order never depends on it.
		List<Unit> shuffled = new ArrayList<>();
		for (int i = units.size() - 1; i >= 0; i -= 2) {
			shuffled.add(units.get(i));
		}
		for (int i = units.size() - 2; i >= 0; i -= 2) {
			shuffled.add(units.get(i));
		}
		return shuffled;
	}

	@Test
	void wallsFacingEachDirectionAreReadByColumns() {
		for (Facing facing : Facing.values()) {
			Result result = ChestOrder.order(wall(facing, 3, 2), Mode.COLUMNS);
			assertEquals(List.of(0, 1, 10, 11, 20, 21), result.order(), "facing " + facing);
			assertEquals(List.of(List.of(0, 1), List.of(10, 11), List.of(20, 21)), result.lines(), "columns facing " + facing);
		}
	}

	@Test
	void wallsFacingEachDirectionAreReadByRows() {
		for (Facing facing : Facing.values()) {
			Result result = ChestOrder.order(wall(facing, 3, 2), Mode.ROWS);
			assertEquals(List.of(0, 10, 20, 1, 11, 21), result.order(), "facing " + facing);
			assertEquals(List.of(List.of(0, 10, 20), List.of(1, 11, 21)), result.lines(), "rows facing " + facing);
		}
	}

	@Test
	void northFacingWallRunsTowardsTheWest() {
		// A chest facing north is seen from the north: its left is the east (+x).
		List<Unit> units = List.of(
				Unit.single(1, 10, 64, 0, Facing.NORTH),
				Unit.single(2, 11, 64, 0, Facing.NORTH),
				Unit.single(3, 12, 64, 0, Facing.NORTH));
		assertEquals(List.of(3, 2, 1), ChestOrder.order(units, Mode.COLUMNS).order());
		List<Unit> south = List.of(
				Unit.single(1, 10, 64, 0, Facing.SOUTH),
				Unit.single(2, 11, 64, 0, Facing.SOUTH),
				Unit.single(3, 12, 64, 0, Facing.SOUTH));
		assertEquals(List.of(1, 2, 3), ChestOrder.order(south, Mode.COLUMNS).order());
	}

	@Test
	void doubleChestIsOneUnitAtItsLeftHalf() {
		// Facing south: left to right = +x. Double chest x=0..1 at the top, singles below at x=0 and x=1.
		List<Unit> units = List.of(
				Unit.single(5, 1, 63, 0, Facing.SOUTH),
				Unit.pair(7, 1, 64, 0, 0, 0, Facing.SOUTH),
				Unit.single(6, 0, 63, 0, Facing.SOUTH));
		Result columns = ChestOrder.order(units, Mode.COLUMNS);
		assertEquals(List.of(7, 6, 5), columns.order());
		assertEquals(List.of(List.of(7, 6), List.of(5)), columns.lines());
		Result rows = ChestOrder.order(units, Mode.ROWS);
		assertEquals(List.of(7, 6, 5), rows.order());
		assertEquals(List.of(List.of(7), List.of(6, 5)), rows.lines());
	}

	@Test
	void barrelWithoutFacingJoinsTheWallOfItsPlane() {
		// Chests facing south at z = 5; a barrel facing up in the same plane between them.
		List<Unit> units = List.of(
				Unit.single(1, 0, 64, 5, Facing.SOUTH),
				Unit.single(2, 2, 64, 5, Facing.SOUTH),
				Unit.single(3, 1, 64, 5, null));
		assertEquals(List.of(1, 3, 2), ChestOrder.order(units, Mode.COLUMNS).order());
	}

	@Test
	void scatteredUnitsUseTheMainAxisAndTheHeight() {
		// No facing at all, spread along z: columns follow z, rows go from the top.
		List<Unit> units = List.of(
				Unit.single(1, 0, 64, 9, null),
				Unit.single(2, 1, 66, 0, null),
				Unit.single(3, 0, 64, 4, null),
				Unit.single(4, 2, 65, 4, null));
		Result columns = ChestOrder.order(units, Mode.COLUMNS);
		assertEquals(List.of(2, 4, 3, 1), columns.order());
		Result rows = ChestOrder.order(units, Mode.ROWS);
		assertEquals(List.of(2, 4, 3, 1), rows.order());
		assertEquals(3, rows.lines().size());
	}

	@Test
	void severalWallsAreOrderedByFacingThenDepth() {
		List<Unit> units = List.of(
				Unit.single(1, 0, 64, 10, Facing.SOUTH),
				Unit.single(2, 0, 64, 0, Facing.NORTH),
				Unit.single(3, 1, 64, 0, Facing.NORTH),
				Unit.single(4, 5, 64, 5, Facing.WEST));
		assertEquals(List.of(3, 2, 1, 4), ChestOrder.order(units, Mode.COLUMNS).order());
	}

	@Test
	void sectionsStayWithinReach() {
		// One row of 12 chests facing south (x = 0..11): reach 3 keeps at most 7 consecutive chests (|dx| <= 3).
		List<Unit> units = new ArrayList<>();
		for (int x = 0; x < 12; x++) {
			units.add(Unit.single(x, x, 64, 0, Facing.SOUTH));
		}
		List<List<Integer>> sections = ChestOrder.sections(units, 3.0, 64, null);
		assertEquals(List.of(List.of(0, 1, 2, 3, 4, 5, 6), List.of(7, 8, 9, 10, 11)), sections);
		// A unit cap and a skipped (busy) unit cut the runs too.
		boolean[] skip = new boolean[12];
		skip[2] = true;
		List<List<Integer>> capped = ChestOrder.sections(units, 10.0, 4, skip);
		assertEquals(List.of(List.of(0, 1), List.of(3, 4, 5, 6), List.of(7, 8, 9, 10), List.of(11)), capped);
	}

	@Test
	void modeAndFacingParse() {
		assertEquals(Mode.ROWS, ChestOrder.Mode.parse(" Rows "));
		assertNull(ChestOrder.Mode.parse("diagonal"));
		assertEquals(Facing.EAST, Facing.of("east"));
		assertNull(Facing.of("up"));
		assertTrue(Mode.COLUMNS.id().equals("columns"));
	}
}
