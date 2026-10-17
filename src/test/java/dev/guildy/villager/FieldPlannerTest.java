package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldPlannerTest {
	private static final int R = FieldPlanner.RANGE;
	private static final int Y = 64;

	/** Zone square of side {@code n} (flat soil at y 64, every cell safe for a hole) with the margin ring around it. */
	private static FieldPlanner.Grid plain(int n) {
		FieldPlanner.Grid grid = new FieldPlanner.Grid(n + 2 * R, n + 2 * R);
		for (int z = 0; z < grid.depth; z++) {
			for (int x = 0; x < grid.width; x++) {
				int cell = grid.index(x, z);
				grid.ground[cell] = Y;
				if (inZone(n, x, z)) {
					grid.zone[cell] = true;
					grid.soil[cell] = true;
					grid.hole[cell] = true;
					grid.level[cell] = true;
				}
			}
		}
		return grid;
	}

	private static boolean inZone(int n, int x, int z) {
		return x >= R && z >= R && x < R + n && z < R + n;
	}

	private static boolean[] need(FieldPlanner.Grid grid) {
		boolean[] need = new boolean[grid.size()];
		for (int i = 0; i < need.length; i++) {
			need[i] = grid.zone[i] && grid.soil[i];
		}
		return need;
	}

	/** Every cell to hydrate is a hole or within range of a hole at its level or one above (or of water). */
	private static void assertCovered(FieldPlanner.Grid grid, boolean[] need, FieldPlanner.Plan plan) {
		for (int z = 0; z < grid.depth; z++) {
			for (int x = 0; x < grid.width; x++) {
				int cell = grid.index(x, z);
				if (!need[cell] || plan.hydrated()[cell]) {
					continue;
				}
				assertTrue(plan.covered()[cell], "cell " + x + "," + z + " covered");
				boolean reached = false;
				for (int[] hole : plan.holes()) {
					int h = grid.index(hole[0], hole[1]);
					if (Math.max(Math.abs(hole[0] - x), Math.abs(hole[1] - z)) <= R && FieldPlanner.hydrates(grid.ground[h], grid.ground[cell])) {
						reached = true;
						break;
					}
				}
				assertTrue(reached, "cell " + x + "," + z + " within range of a hole");
			}
		}
	}

	private static void assertSafe(FieldPlanner.Grid grid, FieldPlanner.Plan plan) {
		for (int[] hole : plan.holes()) {
			int h = grid.index(hole[0], hole[1]);
			assertTrue(grid.zone[h] && grid.hole[h], "hole " + hole[0] + "," + hole[1] + " on a safe zone cell");
		}
	}

	@Test
	void plainSquareUsesTheFewestHoles() {
		// A hole hydrates a 9×9 square: an n×n square needs at least ceil(n / 9)² of them (cells 9 apart need distinct holes).
		for (int n : new int[]{1, 5, 9, 10, 16, 20, 27, 48, 50}) {
			FieldPlanner.Grid grid = plain(n);
			boolean[] need = need(grid);
			FieldPlanner.Plan plan = FieldPlanner.irrigation(grid, need, true, 10_000);
			int perSide = (n + FieldPlanner.SPACING - 1) / FieldPlanner.SPACING;
			assertEquals(perSide * perSide, plan.holes().size(), "holes for a " + n + "×" + n + " square");
			assertEquals(0, plan.unreachable(), "nothing left dry on a " + n + "×" + n + " square");
			assertEquals(n * n, plan.needed());
			assertCovered(grid, need, plan);
			assertSafe(grid, plan);
		}
	}

	@Test
	void latticeLinesAreCentred() {
		assertArrayEquals(new int[]{4, 13, 22}, FieldPlanner.latticeLine(0, 26));
		assertArrayEquals(new int[]{3, 12}, FieldPlanner.latticeLine(0, 15));
		assertArrayEquals(new int[]{1, 10, 19, 28, 37, 46}, FieldPlanner.latticeLine(0, 47));
		assertArrayEquals(new int[]{5}, FieldPlanner.latticeLine(5, 5));
	}

	@Test
	void unsafeLatticeCellsShiftTheHoles() {
		FieldPlanner.Grid grid = plain(27);
		// Columns of the lattice (and their neighbours) are unsafe: a tree line, a building...
		for (int z = 0; z < grid.depth; z++) {
			for (int lx : new int[]{R + 4, R + 13, R + 22}) {
				grid.hole[grid.index(lx, z)] = false;
			}
		}
		boolean[] need = need(grid);
		FieldPlanner.Plan plan = FieldPlanner.irrigation(grid, need, true, 10_000);
		assertEquals(0, plan.unreachable());
		assertSafe(grid, plan);
		assertCovered(grid, need, plan);
		assertTrue(plan.holes().size() <= 13, "a few more holes at most: " + plan.holes().size());
	}

	@Test
	void onlySafeHolesAndUnreachableCellsCounted() {
		FieldPlanner.Grid grid = plain(9);
		for (int i = 0; i < grid.size(); i++) {
			grid.hole[i] = false;
		}
		boolean[] need = need(grid);
		FieldPlanner.Plan plan = FieldPlanner.irrigation(grid, need, true, 10_000);
		assertTrue(plan.holes().isEmpty(), "no safe cell: no hole");
		assertEquals(81, plan.unreachable());
	}

	@Test
	void existingWaterSavesHoles() {
		// A pond in the middle of a 9×9 field hydrates all of it.
		FieldPlanner.Grid pond = plain(9);
		int middle = pond.index(R + 4, R + 4);
		pond.water[middle] = Y;
		pond.soil[middle] = false;
		pond.hole[middle] = false;
		FieldPlanner.Plan pondPlan = FieldPlanner.irrigation(pond, need(pond), true, 10_000);
		assertTrue(pondPlan.holes().isEmpty(), "no hole next to a pond");
		assertEquals(0, pondPlan.unreachable());
		// Water just outside the zone hydrates it too.
		FieldPlanner.Grid edge = plain(4);
		edge.water[edge.index(R - 1, R + 1)] = Y;
		FieldPlanner.Plan edgePlan = FieldPlanner.irrigation(edge, need(edge), true, 10_000);
		assertTrue(edgePlan.holes().isEmpty(), "no hole next to the water of the margin");
		// A river across a larger field: the rows it hydrates need no hole.
		FieldPlanner.Grid grid = plain(18);
		int riverZ = R + 8;
		for (int x = 0; x < grid.width; x++) {
			int cell = grid.index(x, riverZ);
			grid.water[cell] = Y;
			grid.soil[cell] = false;
			grid.hole[cell] = false;
		}
		boolean[] need = need(grid);
		FieldPlanner.Plan plan = FieldPlanner.irrigation(grid, need, true, 10_000);
		for (int x = R; x < R + 18; x++) {
			for (int z = riverZ - R; z <= riverZ + R; z++) {
				assertTrue(plan.hydrated()[grid.index(x, z)], "hydrated by the river at " + x + "," + z);
			}
		}
		assertEquals(0, plan.unreachable());
		assertCovered(grid, need, plan);
		assertTrue(plan.holes().size() <= 4, "never more holes than without the river: " + plan.holes().size());
	}

	@Test
	void withoutIrrigationOnlyTheCurrentHydrationCounts() {
		FieldPlanner.Grid grid = plain(16);
		int water = grid.index(R + 3, R + 3);
		grid.water[water] = Y;
		grid.soil[water] = false;
		boolean[] need = need(grid);
		FieldPlanner.Plan plan = FieldPlanner.irrigation(grid, need, false, 10_000);
		assertTrue(plan.holes().isEmpty());
		assertArrayEquals(plan.hydrated(), plan.covered());
		assertTrue(plan.hydrated()[grid.index(R + 7, R + 7)]);
		assertFalse(plan.hydrated()[grid.index(R + 8, R + 3)]);
		assertTrue(plan.unreachable() > 0);
	}

	@Test
	void hydrationFollowsTheVanillaHeights() {
		assertTrue(FieldPlanner.hydrates(64, 64), "water at the farmland level");
		assertTrue(FieldPlanner.hydrates(65, 64), "water one block above the farmland");
		assertFalse(FieldPlanner.hydrates(66, 64), "water two blocks above");
		assertFalse(FieldPlanner.hydrates(63, 64), "water below the farmland");
		assertFalse(FieldPlanner.hydrates(FieldPlanner.UNKNOWN, 64));
	}

	@Test
	void terracesGetHolesOnEachLevel() {
		FieldPlanner.Grid grid = plain(18);
		// Eastern half two blocks higher: holes of one level do not hydrate the other.
		for (int z = 0; z < grid.depth; z++) {
			for (int x = R + 9; x < grid.width; x++) {
				grid.ground[grid.index(x, z)] = Y + 2;
			}
		}
		boolean[] need = need(grid);
		FieldPlanner.Plan plan = FieldPlanner.irrigation(grid, need, true, 10_000);
		assertEquals(0, plan.unreachable());
		assertCovered(grid, need, plan);
		assertSafe(grid, plan);
		boolean low = false;
		boolean high = false;
		for (int[] hole : plan.holes()) {
			int y = grid.ground[grid.index(hole[0], hole[1])];
			low |= y == Y;
			high |= y == Y + 2;
		}
		assertTrue(low && high, "holes on both levels");
	}

	@Test
	void holeLimitLeavesCellsDry() {
		FieldPlanner.Grid grid = plain(27);
		boolean[] need = need(grid);
		FieldPlanner.Plan plan = FieldPlanner.irrigation(grid, need, true, 4);
		assertEquals(4, plan.holes().size());
		assertEquals(27 * 27 - 4 * 81, plan.unreachable());
	}

	@Test
	void levelJobsFillPitsAndShaveBumps() {
		FieldPlanner.Grid grid = plain(9);
		int pit = grid.index(R + 2, R + 2);
		int bump = grid.index(R + 6, R + 2);
		int deep = grid.index(R + 2, R + 6);
		int reservedPit = grid.index(R + 6, R + 6);
		grid.ground[pit] = Y - 1;
		grid.ground[bump] = Y + 1;
		grid.ground[deep] = Y - 2;
		grid.ground[reservedPit] = Y - 1;
		boolean[] reserved = new boolean[grid.size()];
		reserved[reservedPit] = true;
		List<FieldPlanner.LevelJob> jobs = FieldPlanner.levelJobs(grid, reserved, 100);
		assertEquals(2, jobs.size(), "the 1-block pit and the 1-block bump only: " + jobs);
		assertTrue(jobs.contains(new FieldPlanner.LevelJob(R + 2, R + 2, true)), "pit filled");
		assertTrue(jobs.contains(new FieldPlanner.LevelJob(R + 6, R + 2, false)), "bump shaved");
	}

	@Test
	void levelJobsLeaveWaterAndBuildingsAlone() {
		FieldPlanner.Grid grid = plain(9);
		int pit = grid.index(R + 4, R + 4);
		grid.ground[pit] = Y - 1;
		// Next to water: left alone.
		grid.water[grid.index(R + 5, R + 4)] = Y;
		assertTrue(FieldPlanner.levelJobs(grid, new boolean[grid.size()], 100).isEmpty());
		grid.water[grid.index(R + 5, R + 4)] = FieldPlanner.UNKNOWN;
		// Not natural soil (a building around: the survey clears the level flag): left alone.
		grid.level[pit] = false;
		assertTrue(FieldPlanner.levelJobs(grid, new boolean[grid.size()], 100).isEmpty());
		grid.level[pit] = true;
		assertEquals(1, FieldPlanner.levelJobs(grid, new boolean[grid.size()], 100).size());
		assertTrue(FieldPlanner.levelJobs(grid, new boolean[grid.size()], 0).isEmpty(), "bounded");
	}
}
