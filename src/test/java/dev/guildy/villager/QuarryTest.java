package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarryTest {
	private static final MineLayout.Settings WIDE = new MineLayout.Settings(4, 2, 32, 3, 12, 5, 5, 3, 2);
	private static final MineLayout.Settings NARROW = new MineLayout.Settings(4, 1, 32, 3, 12, 5, 5, 3, 2);
	private static final Quarry.Settings SETTINGS = new Quarry.Settings(3, 0, 2, 8, 8, 8);
	/** Fanion in the middle of a 3×3 chunks zone. */
	private static final BlockPos FANION = new BlockPos(8, 70, 8);
	private static final int[] BOUNDS = ZoneMath.squareBounds(FANION.getX(), FANION.getZ(), 1);
	private static final int LOWEST = -59;

	private static int orientation(int width, int[] bounds) {
		int orientation = 0;
		while (!MineLayout.fits(FANION, orientation, 4, width, bounds)) {
			orientation++;
		}
		return orientation;
	}

	private static Quarry quarry(Quarry.Settings settings) {
		return new Quarry(FANION, orientation(2, BOUNDS), 4, 2, BOUNDS, settings);
	}

	private static MineLayout layout(Quarry.Facts facts) {
		return MineLayout.quarry(FANION, orientation(2, BOUNDS), LOWEST, BOUNDS, WIDE, SETTINGS, facts);
	}

	private static boolean adjacent(int x0, int z0, int x1, int z1) {
		return Math.abs(x0 - x1) + Math.abs(z0 - z1) == 1;
	}

	// ------------------------------------------------------------------ tower, causeway, margin

	@Test
	void towerKeepsTheStairsSquareAndAOneBlockShell() {
		Quarry quarry = quarry(SETTINGS);
		int orientation = orientation(2, BOUNDS);
		int footprint = 0;
		int shell = 0;
		for (int x = BOUNDS[0]; x <= BOUNDS[2]; x++) {
			for (int z = BOUNDS[1]; z <= BOUNDS[3]; z++) {
				footprint += quarry.inFootprint(x, z) ? 1 : 0;
				shell += quarry.inShell(x, z) ? 1 : 0;
				assertEquals(quarry.inTower(x, z), quarry.inFootprint(x, z) || quarry.inShell(x, z));
				assertFalse(quarry.inQuarry(x, z) && quarry.inTower(x, z), "the tower is never quarried");
			}
		}
		assertEquals(36, footprint, "6×6 square of a 2-wide staircase");
		assertEquals(28, shell, "a one-block ring around it");
		assertTrue(quarry.inShell(FANION.getX(), FANION.getZ()), "the fanion stands on the shell");
		for (int k = 0; k < 60; k++) {
			for (MineLayout.Cell cell : MineLayout.stepCells(FANION, orientation, 4, 2, k)) {
				assertTrue(quarry.inFootprint(cell.feet().getX(), cell.feet().getZ()), "every stair cell is inside the tower");
			}
		}
		// The shell surrounds the square: every footprint column has its four neighbours in the tower.
		for (int x = BOUNDS[0]; x <= BOUNDS[2]; x++) {
			for (int z = BOUNDS[1]; z <= BOUNDS[3]; z++) {
				if (quarry.inFootprint(x, z)) {
					assertTrue(quarry.inTower(x + 1, z) && quarry.inTower(x - 1, z) && quarry.inTower(x, z + 1) && quarry.inTower(x, z - 1));
				}
			}
		}
	}

	@Test
	void causewayJoinsTheFanionToTheQuarryEdge() {
		Quarry quarry = quarry(SETTINGS);
		// From the fanion, walking on causeway (or shell) columns reaches the border of the quarry square.
		Set<Long> seen = new HashSet<>();
		List<int[]> queue = new ArrayList<>();
		queue.add(new int[]{FANION.getX(), FANION.getZ()});
		seen.add(BlockPos.asLong(FANION.getX(), 0, FANION.getZ()));
		boolean edge = false;
		int causeway = 0;
		while (!queue.isEmpty()) {
			int[] c = queue.removeLast();
			if (c[0] == BOUNDS[0] || c[0] == BOUNDS[2] || c[1] == BOUNDS[1] || c[1] == BOUNDS[3]) {
				edge = true;
			}
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int x = c[0] + d[0];
				int z = c[1] + d[1];
				boolean way = quarry.inCauseway(x, z) && quarry.inSquare(x, z) && !quarry.inFootprint(x, z);
				if (way && seen.add(BlockPos.asLong(x, 0, z))) {
					queue.add(new int[]{x, z});
				}
			}
		}
		for (int x = BOUNDS[0]; x <= BOUNDS[2]; x++) {
			for (int z = BOUNDS[1]; z <= BOUNDS[3]; z++) {
				if (quarry.inCauseway(x, z) && !quarry.inTower(x, z)) {
					causeway++;
					assertFalse(quarry.inQuarry(x, z), "the causeway is never quarried");
				}
			}
		}
		assertTrue(edge, "the causeway reaches the quarry edge");
		assertTrue(causeway > 0 && causeway <= 2 * 32, "two blocks wide, the shortest way (" + causeway + ")");
	}

	@Test
	void marginIsLeftAlongTheZoneBorder() {
		Quarry quarry = quarry(new Quarry.Settings(3, 3, 2, 8, 8, 8));
		for (int x = BOUNDS[0] - 2; x <= BOUNDS[2] + 2; x++) {
			for (int z = BOUNDS[1] - 2; z <= BOUNDS[3] + 2; z++) {
				int border = Math.min(Math.min(x - BOUNDS[0], BOUNDS[2] - x), Math.min(z - BOUNDS[1], BOUNDS[3] - z));
				if (quarry.inQuarry(x, z)) {
					assertTrue(border >= 3, "3 blocks left along the border");
				}
				if (border < 3) {
					assertEquals(-1, quarry.column(x, z));
				}
				if (quarry.inZone(x, z) && !quarry.inQuarry(x, z)) {
					assertTrue(quarry.kept(x, z));
				}
			}
		}
		int[] square = quarry.square();
		assertEquals((square[2] - square[0] + 1) * (square[3] - square[1] + 1), quarry.columns());
		for (int c = 0; c < quarry.columns(); c++) {
			assertEquals(c, quarry.column(quarry.columnX(c), quarry.columnZ(c)), "column index round trip");
		}
	}

	// ------------------------------------------------------------------ benches and openings

	@Test
	void benchesEveryThreeBlocksFromTheGroundDown() {
		Quarry quarry = quarry(SETTINGS);
		assertEquals(69, quarry.ground());
		assertEquals(List.of(67, 64, 61, 58), quarry.benches(LOWEST + 5, 58));
		assertEquals(List.of(67, 64, 61, 58), quarry.benches(LOWEST + 5, 57), "never under the target");
		assertEquals(List.of(67), quarry.benches(LOWEST + 5, 69), "the first bench at least");
		assertEquals(List.of(), quarry.benches(68, 0), "not even the first bench above the bottom of the world");
		assertTrue(quarry.isBench(67) && quarry.isBench(64) && !quarry.isBench(66) && !quarry.isBench(70));
		assertEquals(0, quarry.benchIndex(67));
		assertEquals(1, quarry.benchIndex(64));
		assertEquals(Integer.valueOf(55), quarry.deeper(58, LOWEST));
		assertNull(quarry.deeper(LOWEST + 1, LOWEST));
	}

	@Test
	void eachBenchIsEnteredThroughTheShellFromAStairStep() {
		for (int width : new int[]{2, 1}) {
			int orientation = orientation(width, BOUNDS);
			Quarry quarry = new Quarry(FANION, orientation, 4, width, BOUNDS, SETTINGS);
			for (int i = 0; i < 12; i++) {
				int y = quarry.benchY(i);
				int k = FANION.getY() - 1 - y;
				List<Quarry.Opening> openings = quarry.openings(y, 200);
				assertFalse(openings.isEmpty(), "a way onto bench " + i + " (room all around the tower)");
				for (Quarry.Opening opening : openings) {
					assertTrue(opening.step() >= k - 1 && opening.step() <= k + 1);
					BlockPos inner = MineLayout.stairCell(FANION, orientation, 4, width, opening.step()).feet();
					BlockPos wall = opening.wall().feet();
					BlockPos entry = opening.entry().feet();
					assertTrue(quarry.inShell(wall.getX(), wall.getZ()), "the opening goes through the shell");
					assertTrue(quarry.inQuarry(entry.getX(), entry.getZ()), "onto a quarry column");
					assertTrue(adjacent(wall.getX(), wall.getZ(), entry.getX(), entry.getZ()));
					assertEquals(inner.getY(), wall.getY(), "the opening at the height of its step");
					assertEquals(y, entry.getY(), "the first cell on the bench");
					assertEquals(3, opening.entry().height());
					assertEquals(opening.step() > k ? 3 : 2, opening.wall().height(), "2 high, 3 for a step up");
					if (width == 2) {
						MineLayout.Cell outer = opening.outer();
						assertNotNull(outer);
						assertTrue(MineLayout.outerCells(FANION, orientation, 4, width, opening.step()).contains(outer), "beside its step");
						assertTrue(adjacent(outer.feet().getX(), outer.feet().getZ(), wall.getX(), wall.getZ()));
					} else {
						assertNull(opening.outer());
						assertTrue(adjacent(inner.getX(), inner.getZ(), wall.getX(), wall.getZ()));
					}
					assertTrue(quarry.openingWall(wall, 200), "never patched");
				}
				if (openings.stream().anyMatch(o -> o.step() == k)) {
					assertEquals(k, openings.getFirst().step(), "from the step at the bench's height first");
				}
			}
		}
	}

	@Test
	void benchTreeCoversTheSquareWithStraightStripsAndKeepsTheTower() {
		Quarry quarry = quarry(SETTINGS);
		Quarry.Bench bench = quarry.bench(67, 200, Quarry.Facts.NONE);
		Quarry.Opening opening = bench.opening();
		assertNotNull(opening);
		assertEquals(quarry.column(opening.entry().feet().getX(), opening.entry().feet().getZ()), bench.root());
		assertEquals(quarry.quarryColumns(), bench.size(), "every quarry column, nothing of the tower or the causeway");
		Set<Integer> placed = new HashSet<>();
		for (int c : bench.order()) {
			assertTrue(quarry.inQuarry(quarry.columnX(c), quarry.columnZ(c)));
			int parent = bench.parent(c);
			if (c == bench.root()) {
				assertEquals(Quarry.ROOT, parent);
			} else {
				assertTrue(placed.contains(parent), "dug from a column dug before");
				assertTrue(adjacent(quarry.columnX(c), quarry.columnZ(c), quarry.columnX(parent), quarry.columnZ(parent)));
			}
			placed.add(c);
		}
		Set<Integer> inStrips = new HashSet<>();
		for (int i = 0; i < bench.strips().size(); i++) {
			Quarry.Strip strip = bench.strips().get(i);
			int[] columns = strip.columns();
			assertTrue(columns.length >= 1 && columns.length <= 8, "at most 8 cells");
			assertEquals(strip.parent(), bench.parent(columns[0]));
			int dx = quarry.columnX(columns[0]) - quarry.columnX(strip.parent());
			int dz = quarry.columnZ(columns[0]) - quarry.columnZ(strip.parent());
			for (int j = 0; j < columns.length; j++) {
				assertTrue(inStrips.add(columns[j]), "each column in one strip");
				assertEquals(i, bench.stripOf(columns[j]));
				if (j > 0) {
					assertEquals(columns[j - 1], bench.parent(columns[j]));
					assertEquals(dx, quarry.columnX(columns[j]) - quarry.columnX(columns[j - 1]), "straight");
					assertEquals(dz, quarry.columnZ(columns[j]) - quarry.columnZ(columns[j - 1]), "straight");
				}
			}
		}
		assertEquals(bench.size() - 1, inStrips.size(), "every column but the first cell (part of the opening)");
	}

	@Test
	void supportColumnsAndSkippedColumnsAreNeverDugAndRoutedAround() {
		Quarry quarry = quarry(SETTINGS);
		Quarry.Bench free = quarry.bench(67, 200, Quarry.Facts.NONE);
		// A wall of support columns across the square (a building's foundations) west of the tower, with a gap at its end.
		int wallX = BOUNDS[0] + 12;
		Set<Long> support = new HashSet<>();
		for (int z = BOUNDS[1]; z < BOUNDS[3] - 2; z++) {
			assertTrue(quarry.inQuarry(wallX, z), "away from the tower and the causeway");
			support.add(BlockPos.asLong(wallX, 0, z));
		}
		int skipped = quarry.column(BOUNDS[2], BOUNDS[3]);
		Quarry.Facts facts = new Quarry.Facts() {
			@Override
			public boolean support(int x, int z) {
				return support.contains(BlockPos.asLong(x, 0, z));
			}

			@Override
			public boolean skipped(int benchY, int column) {
				return benchY == 67 && column == skipped;
			}

			@Override
			public boolean open(int benchY) {
				return true;
			}
		};
		Quarry.Bench bench = quarry.bench(67, 200, facts);
		assertEquals(free.size() - support.size() - 1, bench.size(), "all the others, behind the wall too (around it)");
		for (long column : support) {
			int c = quarry.column(BlockPos.getX(column), BlockPos.getZ(column));
			assertFalse(bench.contains(c), "a support column is never dug");
			assertFalse(quarry.bench(64, 200, facts).contains(c), "on any bench");
		}
		assertFalse(bench.contains(skipped), "skipped on its bench");
		assertTrue(quarry.bench(64, 200, facts).contains(skipped), "dug on the next one");
	}

	// ------------------------------------------------------------------ stacks

	/** Fake column: natural rock up to the ground, air above, unless set. */
	private static final class FakeSurface implements Quarry.SurfaceGrid {
		private final Map<Long, Quarry.Surface> blocks = new HashMap<>();
		private final Set<Long> leaves = new HashSet<>();
		private final int ground;

		FakeSurface(int ground) {
			this.ground = ground;
		}

		FakeSurface set(int y, Quarry.Surface surface) {
			blocks.put(BlockPos.asLong(0, y, 0), surface);
			return this;
		}

		FakeSurface set(int from, int to, Quarry.Surface surface) {
			for (int y = from; y <= to; y++) {
				set(y, surface);
			}
			return this;
		}

		FakeSurface leavesNear(int y) {
			leaves.add(BlockPos.asLong(0, y, 0));
			return this;
		}

		@Override
		public Quarry.Surface surface(int x, int y, int z) {
			return blocks.getOrDefault(BlockPos.asLong(x, y, z), y <= ground ? Quarry.Surface.NATURAL : Quarry.Surface.AIR);
		}

		@Override
		public boolean leavesNear(int x, int y, int z) {
			return leaves.contains(BlockPos.asLong(x, y, z));
		}
	}

	/** Bench cell 67..69 under the ground at 69; overburden cleared up to 77. */
	private static Quarry.Stack stack(FakeSurface grid) {
		return Quarry.stack(grid, 0, 67, 69, 0, 77, 48);
	}

	private static List<Integer> heights(Quarry.Stack stack) {
		List<Integer> heights = new ArrayList<>();
		for (BlockPos pos : stack.clear()) {
			heights.add(pos.getY());
		}
		return heights;
	}

	@Test
	void naturalVegetationIsClearedTopDown() {
		assertEquals(Quarry.StackVerdict.OK, stack(new FakeSurface(69)).verdict());
		assertEquals(List.of(), heights(stack(new FakeSurface(69))));
		assertEquals(List.of(70), heights(stack(new FakeSurface(69).set(70, Quarry.Surface.PLANT))), "grass");
		assertEquals(List.of(70), heights(stack(new FakeSurface(69).set(70, Quarry.Surface.VEGETATION))), "snow");
		Quarry.Stack tree = stack(new FakeSurface(69).set(70, 74, Quarry.Surface.LOG).set(75, 76, Quarry.Surface.LEAVES));
		assertEquals(Quarry.StackVerdict.OK, tree.verdict());
		assertEquals(List.of(76, 75, 74, 73, 72, 71, 70), heights(tree), "a natural tree, from the top");
		Quarry.Stack acacia = stack(new FakeSurface(69).set(70, 73, Quarry.Surface.LOG).leavesNear(73));
		assertEquals(List.of(73, 72, 71, 70), heights(acacia), "a trunk whose leaves are beside its top");
		assertEquals(List.of(70), heights(stack(new FakeSurface(69).set(70, Quarry.Surface.TORCH))), "the quarry's own floor torch");
		assertEquals(List.of(68), heights(stack(new FakeSurface(69).set(68, Quarry.Surface.LIGHT))), "a torch inside the cell is taken");
		assertEquals(List.of(70, 69, 68), heights(stack(new FakeSurface(67).set(68, 69, Quarry.Surface.LOG).set(70, Quarry.Surface.LEAVES))),
				"a tree standing in the cell (a valley)");
	}

	@Test
	void nonNaturalBlocksKeepASupportColumn() {
		Quarry.Stack plank = stack(new FakeSurface(69).set(70, Quarry.Surface.OTHER));
		assertEquals(Quarry.StackVerdict.SUPPORT, plank.verdict(), "a plank on the ground");
		assertEquals(new BlockPos(0, 70, 0), plank.at());
		assertEquals(Quarry.StackVerdict.SUPPORT, stack(new FakeSurface(69).set(68, Quarry.Surface.OTHER)).verdict(), "a block in the cell");
		assertEquals(Quarry.StackVerdict.SUPPORT, stack(new FakeSurface(69).set(70, 72, Quarry.Surface.LOG)).verdict(),
				"a log pillar without leaves");
		assertEquals(Quarry.StackVerdict.SUPPORT, stack(new FakeSurface(69).set(70, 72, Quarry.Surface.NATURAL)
				.set(73, Quarry.Surface.OTHER)).verdict(), "a house on a hill");
		assertEquals(Quarry.StackVerdict.SUPPORT, stack(new FakeSurface(69).set(70, Quarry.Surface.LEAVES)
				.set(71, Quarry.Surface.OTHER)).verdict(), "on leaves");
		assertEquals(Quarry.StackVerdict.SUPPORT, stack(new FakeSurface(69).set(70, Quarry.Surface.LIGHT)).verdict(),
				"a player's torch on the ground");
		// High above the overburden cleared, a building still rests on the column.
		Quarry.Stack cliff = Quarry.stack(new FakeSurface(69).set(70, 80, Quarry.Surface.NATURAL).set(81, Quarry.Surface.OTHER),
				0, 67, 69, 0, 72, 48);
		assertEquals(Quarry.StackVerdict.SUPPORT, cliff.verdict());
		// Floating above an air gap: nothing rests on the column.
		assertEquals(Quarry.StackVerdict.OK, stack(new FakeSurface(68).set(70, Quarry.Surface.OTHER)).verdict());
	}

	@Test
	void overburdenIsClearedUpToItsLimitAndFluidsStop() {
		Quarry.Stack hill = Quarry.stack(new FakeSurface(75), 0, 67, 69, 0, 72, 48);
		assertEquals(Quarry.StackVerdict.OK, hill.verdict());
		assertEquals(List.of(72, 71, 70), heights(hill), "the hill up to the limit, the rest left");
		assertEquals(Quarry.StackVerdict.FLUID, stack(new FakeSurface(69).set(70, Quarry.Surface.FLUID)).verdict(), "water on the column");
		assertEquals(Quarry.StackVerdict.FLUID, stack(new FakeSurface(69).set(68, Quarry.Surface.FLUID)).verdict(), "water in the cell");
		// Deeper bench (nothing cleared above its top): a lake held by rock left in place stays there.
		Quarry.Stack lake = Quarry.stack(new FakeSurface(72).set(73, Quarry.Surface.FLUID), 0, 67, 69, 0, 69, 48);
		assertEquals(Quarry.StackVerdict.OK, lake.verdict());
		assertEquals(List.of(), heights(lake));
		Quarry.Stack sand = Quarry.stack(new FakeSurface(72).set(70, Quarry.Surface.FALLING).set(73, Quarry.Surface.FLUID), 0, 67, 69, 0, 69, 48);
		assertEquals(Quarry.StackVerdict.FLUID, sand.verdict(), "sand would fall and let the water in");
		assertEquals(Quarry.StackVerdict.FLUID, Quarry.stack(new FakeSurface(71).set(72, Quarry.Surface.FLUID), 0, 67, 69, 0, 77, 48).verdict(),
				"a pond on the overburden");
		assertEquals(Quarry.StackVerdict.WAIT, stack(new FakeSurface(69).set(70, Quarry.Surface.UNLOADED)).verdict());
	}

	// ------------------------------------------------------------------ jobs

	@Test
	void openingFirstThenStripsFromDugCells() {
		MineLayout layout = layout(Quarry.Facts.NONE);
		Quarry quarry = layout.quarry();
		assertNotNull(quarry);
		MineState state = new MineState(MineMode.REAL);
		List<Integer> benches = quarry.benches(LOWEST + 5, 58);
		List<MineJobs.Candidate> open = MineJobs.available(layout, state, benches, 20);
		assertEquals(1, open.size(), "nothing but the stairs at first");
		assertEquals(MineJobs.Type.STAIRS, open.getFirst().job().type());
		state.stairsDone = 20;
		open = strips(MineJobs.available(layout, state, benches, 20));
		assertEquals(1, open.size(), "the opening of the first bench only (the second one lies under it)");
		MineLayout.Level first = layout.level(67);
		assertEquals(MineLayout.Kind.OPENING, first.segment(open.getFirst().job().id()).kind());
		assertEquals(67, open.getFirst().job().levelY());
		Quarry.Bench bench = first.bench();
		MineState.Level progress = state.levelOrCreate(67);
		Quarry.setBit(progress.cleared, bench.root());
		open = strips(MineJobs.available(layout, state, benches, 20));
		assertFalse(open.isEmpty());
		for (MineJobs.Candidate candidate : open) {
			Quarry.Strip strip = bench.strips().get(candidate.job().id() - 2);
			assertEquals(bench.root(), strip.parent(), "only strips leaving the first cell");
			assertEquals(layout.nodeAt(first.segment(1).cells().getLast().feet()), candidate.stand(), "dug from the first cell");
		}
		// A strip half dug: open again from its first cell left.
		Quarry.Strip spine = bench.strips().getFirst();
		Quarry.setBit(progress.cleared, spine.columns()[0]);
		Quarry.setBit(progress.cleared, spine.columns()[1]);
		MineJobs.Candidate resumed = strips(MineJobs.available(layout, state, benches, 20)).stream()
				.filter(c -> c.job().id() == 2).findFirst().orElseThrow();
		assertEquals(2, QuarryJobs.firstOpen(spine, progress));
		assertEquals(first.segment(2).stands()[2], resumed.stand());
	}

	@Test
	void nextBenchOnlyUnderTheDugPartsOfTheBenchAbove() {
		MineLayout layout = layout(Quarry.Facts.NONE);
		Quarry quarry = layout.quarry();
		MineState state = new MineState(MineMode.REAL);
		state.stairsDone = 30;
		List<Integer> benches = quarry.benches(LOWEST + 5, 58);
		Quarry.Bench upper = layout.level(67).bench();
		Quarry.Bench lower = layout.level(64).bench();
		MineState.Level top = state.levelOrCreate(67);
		// Everything dug on the first bench but one leaf column far from the tower.
		int leaf = -1;
		for (int c : upper.order()) {
			Quarry.setBit(top.cleared, c);
		}
		for (int i = upper.order().length - 1; i >= 0; i--) {
			int c = upper.order()[i];
			boolean hasChild = false;
			for (int other : upper.order()) {
				hasChild |= upper.parent(other) == c;
			}
			if (!hasChild) {
				leaf = c;
				break;
			}
		}
		top.cleared.clear();
		for (int c : upper.order()) {
			if (c != leaf) {
				Quarry.setBit(top.cleared, c);
			}
		}
		Set<Integer> blocked = new HashSet<>();
		for (int c = leaf; c >= 0; c = upper.parent(c)) {
			blocked.add(c);
		}
		assertFalse(QuarryJobs.finished(upper, top));
		List<MineJobs.Candidate> open = strips(MineJobs.available(layout, state, benches, 30));
		boolean lowerOpening = open.stream().anyMatch(c -> c.job().levelY() == 64);
		assertEquals(!blocked.contains(lower.root()), lowerOpening, "the opening of the next bench waits for the cells above it");
		// The next bench's first cell dug: its strips open, never under the column left above nor on the way to it.
		MineState.Level next = state.levelOrCreate(64);
		Quarry.setBit(next.cleared, lower.root());
		open = strips(MineJobs.available(layout, state, benches, 30));
		int lowerStrips = 0;
		for (MineJobs.Candidate candidate : open) {
			if (candidate.job().levelY() != 64) {
				continue;
			}
			lowerStrips++;
			for (int c : lower.strips().get(candidate.job().id() - 2).columns()) {
				assertFalse(blocked.contains(c), "never under a part of the bench above still worked or walked");
			}
		}
		assertTrue(lowerStrips > 0, "the next bench is worked meanwhile");
		// The first bench finished: no constraint any more.
		Quarry.setBit(top.cleared, leaf);
		assertTrue(QuarryJobs.finished(upper, top));
		assertEquals(List.of(64, 61), QuarryJobs.window(benches, finished(state, 67)));
	}

	@Test
	void quarryLayoutWalksFromTheFanionThroughTheOpening() {
		Set<Integer> open = Set.of(67);
		MineLayout layout = layout(new Quarry.Facts() {
			@Override
			public boolean support(int x, int z) {
				return false;
			}

			@Override
			public boolean skipped(int benchY, int column) {
				return false;
			}

			@Override
			public boolean open(int benchY) {
				return open.contains(benchY);
			}
		});
		assertNull(layout.level(64), "benches out of the window are never planned");
		assertNull(layout.level(66), "not a bench");
		MineLayout.Level level = layout.level(67);
		assertNotNull(level);
		Quarry.Bench bench = level.bench();
		MineLayout.Segment opening = level.segment(1);
		assertEquals(MineLayout.Kind.OPENING, opening.kind());
		assertEquals(MineLayout.stairNode(bench.opening().step()), opening.stands()[0], "from its stair step");
		MineLayout.Segment last = level.segments().getLast();
		assertEquals(MineLayout.Kind.STRIP, last.kind());
		int target = layout.nodeAt(last.cells().getLast().feet());
		assertTrue(target > 0);
		List<Integer> path = layout.path(MineLayout.ENTRANCE, target);
		assertTrue(path.contains(MineLayout.stairNode(bench.opening().step())), "down the stairs");
		assertTrue(path.contains(layout.nodeAt(bench.opening().wall().feet())), "through the opening");
		// (The first step of a 2-wide staircase is reached over its outer lane, open to the sky.)
		for (int i = 2; i < path.size(); i++) {
			BlockPos a = layout.node(path.get(i - 1));
			BlockPos b = layout.node(path.get(i));
			assertTrue(adjacent(a.getX(), a.getZ(), b.getX(), b.getZ()) && Math.abs(a.getY() - b.getY()) <= 1, "one block at a time");
		}
		BlockPos cell = last.cells().getLast().feet();
		assertEquals(target, layout.nearestNode(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 1.6, n -> true));
		// Veins are followed outside the zone only; torches hang on the tower, never on what is to dig.
		Quarry quarry = layout.quarry();
		assertTrue(layout.inStructure(new BlockPos(BOUNDS[0], 30, BOUNDS[1])));
		assertFalse(layout.inStructure(new BlockPos(BOUNDS[0] - 1, 30, BOUNDS[1])));
		assertTrue(layout.digVolume(cell.below(5)));
		BlockPos shell = bench.opening().wall().feet().above(5);
		assertTrue(quarry.inShell(shell.getX(), shell.getZ()) && !layout.digVolume(shell));
	}

	private static MineState finished(MineState state, int y) {
		state.levelOrCreate(y).finished = true;
		return state;
	}

	private static List<MineJobs.Candidate> strips(List<MineJobs.Candidate> candidates) {
		List<MineJobs.Candidate> list = new ArrayList<>();
		for (MineJobs.Candidate candidate : candidates) {
			if (candidate.job().type() == MineJobs.Type.STRIP) {
				list.add(candidate);
			}
		}
		return list;
	}

	@Test
	void narrowStairsKeepTheirTowerToo() {
		int orientation = orientation(1, BOUNDS);
		MineLayout layout = MineLayout.quarry(FANION, orientation, LOWEST, BOUNDS, NARROW, SETTINGS, Quarry.Facts.NONE);
		Quarry quarry = layout.quarry();
		int footprint = 0;
		for (int x = BOUNDS[0]; x <= BOUNDS[2]; x++) {
			for (int z = BOUNDS[1]; z <= BOUNDS[3]; z++) {
				footprint += quarry.inFootprint(x, z) ? 1 : 0;
			}
		}
		assertEquals(16, footprint);
		MineLayout.Level level = layout.level(64);
		assertNotNull(level.bench().opening());
		assertEquals(2, level.segment(1).cells().size(), "opening + first cell, no outer lane");
		assertEquals(quarry.quarryColumns(), level.bench().size());
	}
}
