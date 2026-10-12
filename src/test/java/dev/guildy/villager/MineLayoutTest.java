package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineLayoutTest {
	/** Galleries of mines planned before levels existed (no landing). */
	private static final MineLayout.Settings LEGACY = new MineLayout.Settings(4, 0, 32, 3, 12, 5, 5, 3, 2);
	private static final MineLayout.Settings WIDE = new MineLayout.Settings(4, 2, 32, 3, 12, 5, 5, 3, 2);
	private static final MineLayout.Settings NARROW = new MineLayout.Settings(4, 1, 32, 3, 12, 5, 5, 3, 2);
	/** Fanion at (5, 70, 5): its chunk is blocks 0..15. */
	private static final BlockPos FANION = new BlockPos(5, 70, 5);
	private static final int[] WIDE_BOUNDS = {-200, -200, 200, 200};

	private static int[] chunkSquare(BlockPos fanion, int radius) {
		return ZoneMath.squareBounds(fanion.getX(), fanion.getZ(), radius);
	}

	private static long column(BlockPos pos) {
		return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xffffffffL);
	}

	private static boolean adjacent(BlockPos a, BlockPos b) {
		return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) == 1;
	}

	@Test
	void stairsGoDownOneBlockPerStepAlongTheSquare() {
		for (int orientation = 0; orientation < MineLayout.ORIENTATIONS; orientation++) {
			MineLayout.Cell previous = null;
			Set<Long> footprint = new HashSet<>();
			for (int[] c : MineLayout.footprint(FANION, orientation, 4)) {
				footprint.add(column(new BlockPos(c[0], 0, c[1])));
			}
			assertEquals(16, footprint.size());
			assertFalse(footprint.contains(column(FANION)), "the fanion column is never dug");
			for (int k = 0; k < 40; k++) {
				MineLayout.Cell cell = MineLayout.stairCell(FANION, orientation, 4, k);
				assertEquals(FANION.getY() - 1 - k, cell.feet().getY(), "one block lower per step");
				assertEquals(3, cell.height(), "3 blocks of headroom");
				assertTrue(footprint.contains(column(cell.feet())), "inside the square");
				BlockPos from = previous == null ? FANION : previous.feet();
				assertTrue(adjacent(cell.feet(), from), "each step is next to the previous one (or to the fanion)");
				previous = cell;
			}
			// One turn later: same column, one perimeter lower.
			MineLayout.Cell first = MineLayout.stairCell(FANION, orientation, 4, 3);
			MineLayout.Cell turned = MineLayout.stairCell(FANION, orientation, 4, 3 + MineLayout.perimeter(4));
			assertEquals(column(first.feet()), column(turned.feet()));
			assertEquals(first.feet().getY() - 12, turned.feet().getY());
		}
	}

	@Test
	void turnsOfTheSpiralNeverCutTheStepsAbove() {
		for (int width = 1; width <= 2; width++) {
			for (int side = MineLayout.MIN_SIDE; side <= MineLayout.MAX_SIDE; side++) {
				Set<Long> dug = new HashSet<>();
				int steps = 80;
				for (int k = 0; k < steps; k++) {
					for (MineLayout.Cell cell : MineLayout.stepCells(FANION, 2, side, width, k)) {
						for (int i = 0; i < cell.height(); i++) {
							assertTrue(dug.add(cell.feet().above(i).asLong()), "cells never overlap (width " + width + ", side " + side + ")");
						}
					}
				}
				for (int k = 0; k < steps; k++) {
					for (MineLayout.Cell cell : MineLayout.stepCells(FANION, 2, side, width, k)) {
						assertFalse(dug.contains(cell.floor().asLong()), "the floor of a step is never dug (width " + width + ", side " + side + ")");
					}
				}
			}
		}
	}

	@Test
	void wideStairsHaveTwoLanesAndCornerLandings() {
		for (int orientation = 0; orientation < MineLayout.ORIENTATIONS; orientation++) {
			Set<Long> footprint = new HashSet<>();
			for (int[] c : MineLayout.footprint(FANION, orientation, 4, 2)) {
				footprint.add(column(new BlockPos(c[0], 0, c[1])));
			}
			assertEquals(36, footprint.size(), "6×6: the outer lane around the 4×4 spiral");
			assertFalse(footprint.contains(column(FANION)), "the fanion column is never dug");
			List<MineLayout.Cell> previous = null;
			for (int k = 0; k < 30; k++) {
				List<MineLayout.Cell> step = MineLayout.stepCells(FANION, orientation, 4, 2, k);
				MineLayout.Cell inner = step.getFirst();
				boolean corner = MineLayout.outward(orientation, 4, k).size() == 2;
				assertEquals(corner ? 4 : 2, step.size(), "two cells per step, a 2×2 landing at the corners");
				for (MineLayout.Cell cell : step) {
					assertEquals(inner.feet().getY(), cell.feet().getY(), "both lanes at the same height");
					assertTrue(footprint.contains(column(cell.feet())));
				}
				// The inner lane is the 1-wide spiral moved one block away from the fanion along both sides of the square.
				MineLayout.Cell narrow = MineLayout.stairCell(FANION, orientation, 4, k);
				int d0 = MineLayout.firstDirection(orientation);
				int d1 = MineLayout.sideDirection(orientation);
				assertEquals(narrow.feet().offset(MineLayout.dx(d0) + MineLayout.dx(d1), 0, MineLayout.dz(d0) + MineLayout.dz(d1)), inner.feet());
				// Each step touches the previous one: inner lanes are neighbours, the walking path stays 1 block per step.
				if (previous != null) {
					assertTrue(adjacent(previous.getFirst().feet(), inner.feet()));
				} else {
					boolean nextToFanion = false;
					for (MineLayout.Cell cell : step) {
						nextToFanion |= adjacent(cell.feet(), FANION);
					}
					assertTrue(nextToFanion, "the first step starts next to the fanion");
				}
				previous = step;
			}
		}
		// Width 2 needs a bigger square: in the corner of a chunk only 2 orientations fit, as for width 1.
		BlockPos corner = new BlockPos(0, 70, 0);
		int fitting = 0;
		for (int orientation = 0; orientation < MineLayout.ORIENTATIONS; orientation++) {
			if (MineLayout.fits(corner, orientation, 4, 2, chunkSquare(corner, 0))) {
				fitting++;
			}
		}
		assertEquals(2, fitting);
	}

	@Test
	void eightOrientationsAreDistinctAndMustFitTheZone() {
		Set<String> footprints = new HashSet<>();
		for (int orientation = 0; orientation < MineLayout.ORIENTATIONS; orientation++) {
			StringBuilder key = new StringBuilder();
			MineLayout.footprint(FANION, orientation, 4).stream().map(c -> c[0] + "," + c[1]).sorted().forEach(key::append);
			key.append("|").append(MineLayout.stairCell(FANION, orientation, 4, 1).feet());
			footprints.add(key.toString());
		}
		assertEquals(8, footprints.size());
		// Fanion in the corner of its chunk with radius 0: only the spirals going into the chunk fit.
		BlockPos corner = new BlockPos(0, 70, 0);
		int[] bounds = chunkSquare(corner, 0);
		int fitting = 0;
		for (int orientation = 0; orientation < MineLayout.ORIENTATIONS; orientation++) {
			if (MineLayout.fits(corner, orientation, 4, bounds)) {
				fitting++;
				for (int[] c : MineLayout.footprint(corner, orientation, 4)) {
					assertTrue(c[0] >= 0 && c[0] <= 15 && c[1] >= 0 && c[1] <= 15);
				}
			}
		}
		assertEquals(2, fitting, "east then south, or south then east");
	}

	@Test
	void branchesEveryThreeBlocksInsideTheZoneSquare() {
		int[] bounds = chunkSquare(FANION, 1);
		for (int orientation = 0; orientation < MineLayout.ORIENTATIONS; orientation++) {
			if (!MineLayout.fits(FANION, orientation, 4, bounds)) {
				continue;
			}
			MineLayout layout = MineLayout.build(FANION, orientation, 40, bounds, false, LEGACY);
			assertEquals(30, layout.stairs().size(), "70 → 40: 30 steps");
			assertEquals(40, layout.stairs().getLast().feet().getY());
			MineLayout.Level level = layout.level(40);
			assertNotNull(level);
			assertTrue(level.mainLength() > 0);
			Set<Long> stairColumns = new HashSet<>();
			for (int[] c : MineLayout.footprint(FANION, orientation, 4)) {
				stairColumns.add(column(new BlockPos(c[0], 0, c[1])));
			}
			MineLayout.Segment firstMain = level.segments().getFirst();
			assertEquals(MineLayout.Kind.MAIN, firstMain.kind());
			assertEquals(-1, firstMain.after());
			BlockPos last = layout.stairs().getLast().feet();
			BlockPos start = firstMain.cells().getFirst().feet();
			assertTrue(adjacent(start, last), "the main tunnel leaves the step of the level");
			int branches = 0;
			for (MineLayout.Segment segment : level.segments()) {
				for (MineLayout.Cell cell : segment.cells()) {
					BlockPos feet = cell.feet();
					assertTrue(feet.getX() >= bounds[0] && feet.getX() <= bounds[2] && feet.getZ() >= bounds[1] && feet.getZ() <= bounds[3],
							"inside the zone square");
					assertEquals(40, feet.getY(), "galleries are level");
					assertEquals(MineLayout.TUNNEL_HEIGHT, cell.height());
					assertFalse(stairColumns.contains(column(feet)), "galleries never cut the staircase");
				}
				if (segment.kind() == MineLayout.Kind.BRANCH) {
					branches++;
					assertEquals(branches, segment.ordinal());
					MineLayout.Segment main = level.segment(segment.after());
					assertEquals(MineLayout.Kind.MAIN, main.kind());
					BlockPos junction = main.cells().getLast().feet();
					BlockPos first = segment.cells().getFirst().feet();
					assertTrue(adjacent(first, junction), "a branch starts next to its junction");
					assertEquals(layout.node(segment.stands()[0]), junction, "dug from the junction");
				}
			}
			assertEquals(branches, level.galleryCount());
			assertTrue(branches >= 2);
		}
	}

	@Test
	void mainTunnelSegmentsAndJunctionsFollowTheSpacing() {
		MineLayout layout = MineLayout.build(FANION, 1, 60, WIDE_BOUNDS, false, new MineLayout.Settings(4, 0, 12, 3, 5, 5, 5, 3, 2));
		MineLayout.Level level = layout.level(60);
		assertEquals(12, level.mainLength());
		int mains = 0;
		int branches = 0;
		int previousMain = -1;
		for (MineLayout.Segment segment : level.segments()) {
			if (segment.kind() == MineLayout.Kind.MAIN) {
				mains++;
				assertEquals(3, segment.cells().size());
				assertEquals(previousMain, segment.after(), "the main tunnel is dug in order");
				previousMain = segment.id();
			} else {
				branches++;
				assertEquals(5, segment.cells().size());
			}
		}
		assertEquals(4, mains);
		assertEquals(8, branches, "two branches per junction (3, 6, 9, 12)");
	}

	@Test
	void stoneObjectiveDigsQuarryRooms() {
		MineLayout layout = MineLayout.build(FANION, 0, 58, WIDE_BOUNDS, true, new MineLayout.Settings(4, 0, 15, 3, 12, 5, 5, 3, 2));
		MineLayout.Level level = layout.level(58);
		int rooms = 0;
		for (MineLayout.Segment segment : level.segments()) {
			if (segment.kind() == MineLayout.Kind.ROOM) {
				rooms++;
				MineLayout.Segment main = level.segment(segment.after());
				assertEquals(20, segment.cells().size(), "5 trunk cells × 2 cells on each side");
				for (MineLayout.Cell cell : segment.cells()) {
					assertEquals(3, cell.height());
					BlockPos trunk = null;
					for (MineLayout.Cell candidate : main.cells()) {
						BlockPos feet = candidate.feet();
						int along = level.mainDirection() % 2 == 0 ? feet.getZ() - cell.feet().getZ() : feet.getX() - cell.feet().getX();
						if (along == 0) {
							trunk = feet;
						}
					}
					assertNotNull(trunk, "a side cell is beside a trunk cell of its room");
					int across = Math.abs(trunk.getX() - cell.feet().getX()) + Math.abs(trunk.getZ() - cell.feet().getZ());
					assertTrue(across >= 1 && across <= 2, "half width 2");
				}
			}
		}
		assertEquals(2, rooms, "rooms at 2..6 and 9..13 of a 15-block tunnel (the 3rd would start at 16)");
		BlockPos stair = layout.stairs().get(layout.stepAt(58)).feet();
		for (MineLayout.Segment segment : level.segments()) {
			if (segment.kind() == MineLayout.Kind.MAIN) {
				for (MineLayout.Cell cell : segment.cells()) {
					int j = Math.abs(cell.feet().getX() - stair.getX()) + Math.abs(cell.feet().getZ() - stair.getZ());
					boolean inRoom = (j >= 2 && j <= 6) || (j >= 9 && j <= 13);
					assertEquals(inRoom ? 3 : 2, cell.height(), "rooms are 3 high, corridors 2 (j = " + j + ")");
				}
			}
		}
	}

	@Test
	void levelsEveryFewBlocksHaveALandingThenGalleries() {
		int[] bounds = chunkSquare(FANION, 1);
		for (MineLayout.Settings settings : List.of(WIDE, NARROW)) {
			int width = settings.stairWidth();
			for (int orientation = 0; orientation < MineLayout.ORIENTATIONS; orientation++) {
				if (!MineLayout.fits(FANION, orientation, 4, width, bounds)) {
					continue;
				}
				MineLayout layout = MineLayout.build(FANION, orientation, -59, bounds, false, settings);
				Set<Long> stairColumns = new HashSet<>();
				for (int[] c : MineLayout.footprint(FANION, orientation, 4, width)) {
					stairColumns.add(column(new BlockPos(c[0], 0, c[1])));
				}
				Set<Long> blocks = new HashSet<>();
				for (int y = 64; y >= -58; y -= 6) {
					MineLayout.Level level = layout.level(y);
					assertNotNull(level, "a level at every step height (" + y + ")");
					assertEquals(layout.stepAt(y), level.step());
					MineLayout.Segment landing = level.segments().getFirst();
					assertEquals(MineLayout.Kind.LANDING, landing.kind(), "a landing first");
					assertEquals(-1, landing.after());
					assertEquals(MineLayout.stairNode(level.step()), landing.stands()[0], "dug from the step of the level");
					BlockPos inner = layout.stairs().get(level.step()).feet();
					assertTrue(adjacent(landing.cells().getFirst().feet(), inner), "the landing touches the stairs");
					if (width == 2) {
						assertEquals(MineLayout.STAIR_HEIGHT, landing.cells().getFirst().height(), "the way in is the outer lane cell");
						assertTrue(layout.outer(level.step()).contains(landing.cells().getFirst()));
					}
					assertTrue(landing.cells().size() >= 4, "a small platform (" + landing.cells().size() + ")");
					boolean mainAfterLanding = false;
					for (MineLayout.Segment segment : level.segments()) {
						if (segment.kind() == MineLayout.Kind.MAIN && segment.after() == landing.id()) {
							mainAfterLanding = true;
						}
						for (MineLayout.Cell cell : segment.cells()) {
							assertEquals(y, cell.feet().getY());
							assertTrue(MineLayout.inside(cell.feet().getX(), cell.feet().getZ(), bounds), "inside the zone square");
							boolean entry = width == 2 && cell == landing.cells().getFirst();
							assertTrue(entry || !stairColumns.contains(column(cell.feet())), "galleries never cut the staircase");
							assertTrue(layout.inStructure(cell.feet()) && layout.inStructure(cell.floor()));
							for (int i = -1; i < cell.height() && !entry; i++) {
								assertTrue(blocks.add(cell.feet().above(i).asLong()), "cells and floors of the levels never overlap");
							}
						}
					}
					assertTrue(mainAfterLanding, "the main tunnel goes on from the landing");
				}
				assertNull(layout.level(FANION.getY() + 1), "no level above the fanion");
				assertNull(layout.level(-80), "no level under the deepest step");
			}
		}
	}

	@Test
	void walkingGraphGoesUpThenDownTheTree() {
		MineLayout layout = MineLayout.build(FANION, 3, 60, WIDE_BOUNDS, false, LEGACY);
		MineLayout.Level level = layout.level(60);
		int stairsEnd = MineLayout.stairNode(layout.stairs().size() - 1);
		assertEquals(10, stairsEnd);
		// Fanion → last step: every step in order.
		List<Integer> down = layout.path(0, stairsEnd);
		assertEquals(10, down.size());
		for (int i = 0; i < down.size(); i++) {
			assertEquals(i + 1, down.get(i));
		}
		assertEquals(10, layout.distance(0, stairsEnd));
		// From the end of a branch to the end of another one: back to the main tunnel, then into the other branch.
		MineLayout.Segment a = null;
		MineLayout.Segment b = null;
		for (MineLayout.Segment segment : level.segments()) {
			if (segment.kind() == MineLayout.Kind.BRANCH) {
				if (a == null) {
					a = segment;
				} else if (b == null && segment.after() != a.after()) {
					b = segment;
				}
			}
		}
		int aEnd = layout.nodeAt(a.cells().getLast().feet());
		int bEnd = layout.nodeAt(b.cells().getLast().feet());
		assertEquals(aEnd, layout.nearestNode(a.cells().getLast().feet().getX() + 0.5, a.cells().getLast().feet().getY(),
				a.cells().getLast().feet().getZ() + 0.5, 0.5, n -> true));
		List<Integer> across = layout.path(aEnd, bEnd);
		assertEquals(bEnd, across.getLast());
		assertEquals(across.size(), layout.distance(aEnd, bEnd));
		BlockPos junctionA = level.segment(a.after()).cells().getLast().feet();
		assertTrue(across.stream().anyMatch(n -> layout.node(n).equals(junctionA)), "goes back through the junction");
		for (int i = 1; i < across.size(); i++) {
			assertTrue(adjacent(layout.node(across.get(i - 1)), layout.node(across.get(i))), "neighbouring cells");
		}
		// Back up from a branch: everything up to the fanion.
		List<Integer> up = layout.path(aEnd, 0);
		assertEquals(0, up.getLast());
		assertTrue(layout.path(0, 0).isEmpty());
	}

	@Test
	void walkingFromOneLevelToAnotherGoesThroughTheStairs() {
		MineLayout layout = MineLayout.build(FANION, 1, -59, WIDE_BOUNDS, false, WIDE);
		MineLayout.Level upper = layout.level(64);
		MineLayout.Level lower = layout.level(46);
		MineLayout.Segment branchUp = upper.segments().stream().filter(s -> s.kind() == MineLayout.Kind.BRANCH).findFirst().orElseThrow();
		MineLayout.Segment branchDown = lower.segments().stream().filter(s -> s.kind() == MineLayout.Kind.BRANCH).findFirst().orElseThrow();
		int from = layout.nodeAt(branchUp.cells().getLast().feet());
		int to = layout.nodeAt(branchDown.cells().getLast().feet());
		assertTrue(from > 0 && to > 0);
		assertEquals(64, layout.nodeLevel(from));
		assertEquals(46, layout.nodeLevel(to));
		List<Integer> path = layout.path(from, to);
		assertEquals(path.size(), layout.distance(from, to));
		for (int k = upper.step(); k <= lower.step(); k++) {
			assertTrue(path.contains(MineLayout.stairNode(k)), "every step between the two levels (" + k + ")");
		}
		assertFalse(path.contains(MineLayout.stairNode(upper.step() - 1)), "never above the upper level");
		assertEquals(MineLayout.STAIRS_LEVEL, layout.nodeLevel(MineLayout.stairNode(upper.step())));
		// The nearest node of a stair step is found among many levels.
		BlockPos step = layout.stairs().get(40).feet();
		assertEquals(MineLayout.stairNode(40), layout.nearestNode(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 1.6, n -> true));
	}

	@Test
	void nearestNodeOnlyAmongWalkableNodes() {
		MineLayout layout = MineLayout.build(FANION, 0, 60, WIDE_BOUNDS, false, LEGACY);
		BlockPos step = layout.stairs().get(4).feet();
		int node = layout.nearestNode(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 1.5, n -> true);
		assertEquals(MineLayout.stairNode(4), node);
		assertEquals(MineLayout.STAIRS_LEVEL, layout.nodeLevel(node));
		assertEquals(4, layout.nodeCell(node));
		int blocked = layout.nearestNode(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 1.5, n -> n != MineLayout.stairNode(4));
		assertNotEquals(MineLayout.stairNode(4), blocked);
		assertEquals(-1, layout.nearestNode(1000, 0, 1000, 1.5, n -> true));
		assertEquals(MineLayout.ENTRANCE, layout.nearestNode(FANION.getX() + 0.5, FANION.getY(), FANION.getZ() + 0.5, 1.0, n -> true));
	}

	@Test
	void fanionDeepEnoughDigsGalleriesAtItsOwnHeight() {
		MineLayout layout = MineLayout.build(FANION, 0, FANION.getY(), WIDE_BOUNDS, false, LEGACY);
		assertTrue(layout.stairs().isEmpty());
		MineLayout.Level level = layout.level(FANION.getY());
		assertNotNull(level);
		BlockPos start = level.segments().getFirst().cells().getFirst().feet();
		assertEquals(FANION.getY(), start.getY());
		assertTrue(adjacent(start, FANION));
		assertEquals(0, level.segments().getFirst().stands()[0], "dug from the fanion");
		assertEquals(-1, layout.parent(0), "the fanion is the root of the walking graph");
	}

	@Test
	void structureHoldsCellsAndFloors() {
		MineLayout layout = MineLayout.build(FANION, 0, 60, chunkSquare(FANION, 1), false, LEGACY);
		MineLayout.Level level = layout.level(60);
		MineLayout.Cell step = layout.stairs().get(2);
		assertTrue(layout.inStructure(step.feet()));
		assertTrue(layout.inStructure(step.top()));
		assertTrue(layout.inStructure(step.floor()));
		assertTrue(layout.inStructure(FANION.below()), "the fanion's support");
		MineLayout.Cell gallery = level.segments().getFirst().cells().getFirst();
		assertTrue(layout.inStructure(gallery.feet().above()));
		assertFalse(layout.inStructure(gallery.feet().above(2)), "the rock above a 2-high tunnel is not part of it");
	}

	@Test
	void stairColumnsCoverTheWholeSquare() {
		for (int width = 1; width <= 2; width++) {
			MineLayout layout = MineLayout.build(FANION, 6, 40, WIDE_BOUNDS, false, width == 2 ? WIDE : NARROW);
			Set<Long> footprint = new HashSet<>();
			for (int[] c : MineLayout.footprint(FANION, 6, 4, width)) {
				footprint.add(column(new BlockPos(c[0], 0, c[1])));
				assertTrue(layout.inStairColumns(c[0], c[1]));
			}
			assertTrue(layout.inStairColumns(FANION.getX(), FANION.getZ()));
			for (int x = FANION.getX() - 10; x <= FANION.getX() + 10; x++) {
				for (int z = FANION.getZ() - 10; z <= FANION.getZ() + 10; z++) {
					boolean fanion = x == FANION.getX() && z == FANION.getZ();
					assertEquals(fanion || footprint.contains(column(new BlockPos(x, 0, z))), layout.inStairColumns(x, z));
				}
			}
		}
	}
}
