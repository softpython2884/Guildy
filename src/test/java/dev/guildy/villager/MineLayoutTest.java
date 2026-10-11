package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineLayoutTest {
	private static final MineLayout.Settings SETTINGS = new MineLayout.Settings(4, 32, 3, 12, 5, 5, 3, 2);
	/** Fanion at (5, 70, 5): its chunk is blocks 0..15. */
	private static final BlockPos FANION = new BlockPos(5, 70, 5);

	private static int[] chunkSquare(BlockPos fanion, int radius) {
		return ZoneMath.squareBounds(fanion.getX(), fanion.getZ(), radius);
	}

	private static long column(BlockPos pos) {
		return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xffffffffL);
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
				int dx = Math.abs(cell.feet().getX() - from.getX());
				int dz = Math.abs(cell.feet().getZ() - from.getZ());
				assertEquals(1, dx + dz, "each step is next to the previous one (or to the fanion)");
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
		for (int side = MineLayout.MIN_SIDE; side <= MineLayout.MAX_SIDE; side++) {
			Set<Long> dug = new HashSet<>();
			int steps = 80;
			for (int k = 0; k < steps; k++) {
				MineLayout.Cell cell = MineLayout.stairCell(FANION, 2, side, k);
				for (int i = 0; i < cell.height(); i++) {
					assertTrue(dug.add(cell.feet().above(i).asLong()), "cells never overlap");
				}
			}
			for (int k = 0; k < steps; k++) {
				MineLayout.Cell cell = MineLayout.stairCell(FANION, 2, side, k);
				assertFalse(dug.contains(cell.floor().asLong()), "the floor of a step is never dug (side " + side + ")");
			}
		}
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
			MineLayout layout = MineLayout.build(FANION, orientation, 40, bounds, false, SETTINGS);
			assertEquals(30, layout.stairs().size(), "70 → 40: 30 steps");
			assertEquals(40, layout.stairs().getLast().feet().getY());
			assertTrue(layout.mainLength() > 0);
			Set<Long> stairColumns = new HashSet<>();
			for (int[] c : MineLayout.footprint(FANION, orientation, 4)) {
				stairColumns.add(column(new BlockPos(c[0], 0, c[1])));
			}
			MineLayout.Segment firstMain = layout.segments().getFirst();
			assertEquals(MineLayout.Kind.MAIN, firstMain.kind());
			assertEquals(-1, firstMain.after());
			BlockPos last = layout.stairs().getLast().feet();
			BlockPos start = firstMain.cells().getFirst().feet();
			assertEquals(1, Math.abs(start.getX() - last.getX()) + Math.abs(start.getZ() - last.getZ()), "the main tunnel leaves the last step");
			int branches = 0;
			for (MineLayout.Segment segment : layout.segments()) {
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
					MineLayout.Segment main = layout.segment(segment.after());
					assertEquals(MineLayout.Kind.MAIN, main.kind());
					BlockPos junction = main.cells().getLast().feet();
					BlockPos first = segment.cells().getFirst().feet();
					assertEquals(1, Math.abs(first.getX() - junction.getX()) + Math.abs(first.getZ() - junction.getZ()),
							"a branch starts next to its junction");
					assertEquals(layout.node(segment.stands()[0]), junction, "dug from the junction");
				}
			}
			assertEquals(branches, layout.galleryCount());
			assertTrue(branches >= 2);
		}
	}

	@Test
	void mainTunnelSegmentsAndJunctionsFollowTheSpacing() {
		int[] wide = {-200, -200, 200, 200};
		MineLayout layout = MineLayout.build(FANION, 1, 60, wide, false, new MineLayout.Settings(4, 12, 3, 5, 5, 5, 3, 2));
		assertEquals(12, layout.mainLength());
		int mains = 0;
		int branches = 0;
		int previousMain = -1;
		for (MineLayout.Segment segment : layout.segments()) {
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
		int[] wide = {-200, -200, 200, 200};
		MineLayout layout = MineLayout.build(FANION, 0, 58, wide, true, new MineLayout.Settings(4, 15, 3, 12, 5, 5, 3, 2));
		int rooms = 0;
		for (MineLayout.Segment segment : layout.segments()) {
			if (segment.kind() == MineLayout.Kind.ROOM) {
				rooms++;
				MineLayout.Segment main = layout.segment(segment.after());
				assertEquals(20, segment.cells().size(), "5 trunk cells × 2 cells on each side");
				for (MineLayout.Cell cell : segment.cells()) {
					assertEquals(3, cell.height());
					BlockPos trunk = null;
					for (MineLayout.Cell candidate : main.cells()) {
						BlockPos feet = candidate.feet();
						int along = layout.mainDirection() % 2 == 0 ? feet.getZ() - cell.feet().getZ() : feet.getX() - cell.feet().getX();
						if (along == 0) {
							trunk = feet;
						}
					}
					assertTrue(trunk != null, "a side cell is beside a trunk cell of its room");
					int across = Math.abs(trunk.getX() - cell.feet().getX()) + Math.abs(trunk.getZ() - cell.feet().getZ());
					assertTrue(across >= 1 && across <= 2, "half width 2");
				}
			}
		}
		assertEquals(2, rooms, "rooms at 2..6 and 9..13 of a 15-block tunnel (the 3rd would start at 16)");
		for (MineLayout.Segment segment : layout.segments()) {
			if (segment.kind() == MineLayout.Kind.MAIN) {
				for (MineLayout.Cell cell : segment.cells()) {
					int j = Math.abs(cell.feet().getX() - layout.stairs().getLast().feet().getX())
							+ Math.abs(cell.feet().getZ() - layout.stairs().getLast().feet().getZ());
					boolean inRoom = (j >= 2 && j <= 6) || (j >= 9 && j <= 13);
					assertEquals(inRoom ? 3 : 2, cell.height(), "rooms are 3 high, corridors 2 (j = " + j + ")");
				}
			}
		}
	}

	@Test
	void walkingGraphGoesUpThenDownTheTree() {
		int[] wide = {-200, -200, 200, 200};
		MineLayout layout = MineLayout.build(FANION, 3, 60, wide, false, SETTINGS);
		int stairsEnd = MineLayout.stairNode(layout.stairs().size() - 1);
		assertEquals(10, stairsEnd);
		// Fanion → last step: every step in order.
		List<Integer> down = layout.path(0, stairsEnd);
		assertEquals(10, down.size());
		for (int i = 0; i < down.size(); i++) {
			assertEquals(i + 1, down.get(i));
		}
		// From the end of a branch to the end of another one: back to the main tunnel, then into the other branch.
		MineLayout.Segment a = null;
		MineLayout.Segment b = null;
		for (MineLayout.Segment segment : layout.segments()) {
			if (segment.kind() == MineLayout.Kind.BRANCH) {
				if (a == null) {
					a = segment;
				} else if (b == null && segment.after() != a.after()) {
					b = segment;
				}
			}
		}
		int aEnd = layout.nearestNode(a.cells().getLast().feet().getX() + 0.5, a.cells().getLast().feet().getY(),
				a.cells().getLast().feet().getZ() + 0.5, 0.5, n -> true);
		int bEnd = layout.nearestNode(b.cells().getLast().feet().getX() + 0.5, b.cells().getLast().feet().getY(),
				b.cells().getLast().feet().getZ() + 0.5, 0.5, n -> true);
		List<Integer> across = layout.path(aEnd, bEnd);
		assertEquals(bEnd, across.getLast());
		BlockPos junctionA = layout.segment(a.after()).cells().getLast().feet();
		assertTrue(across.stream().anyMatch(n -> layout.node(n).equals(junctionA)), "goes back through the junction");
		for (int i = 1; i < across.size(); i++) {
			BlockPos p = layout.node(across.get(i - 1));
			BlockPos q = layout.node(across.get(i));
			assertEquals(1, Math.abs(p.getX() - q.getX()) + Math.abs(p.getZ() - q.getZ()), "neighbouring cells");
		}
		// Back up from a branch: everything up to the fanion.
		List<Integer> up = layout.path(aEnd, 0);
		assertEquals(0, up.getLast());
		assertTrue(layout.path(0, 0).isEmpty());
	}

	@Test
	void nearestNodeOnlyAmongWalkableNodes() {
		MineLayout layout = MineLayout.build(FANION, 0, 60, new int[]{-200, -200, 200, 200}, false, SETTINGS);
		BlockPos step = layout.stairs().get(4).feet();
		int node = layout.nearestNode(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 1.5, n -> true);
		assertEquals(MineLayout.stairNode(4), node);
		assertEquals(MineLayout.STAIRS, layout.nodeSegment(node));
		assertEquals(4, layout.nodeCell(node));
		int blocked = layout.nearestNode(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 1.5, n -> n != MineLayout.stairNode(4));
		assertNotEquals(MineLayout.stairNode(4), blocked);
		assertEquals(-1, layout.nearestNode(1000, 0, 1000, 1.5, n -> true));
	}

	@Test
	void fanionDeepEnoughDigsGalleriesAtItsOwnHeight() {
		MineLayout layout = MineLayout.build(FANION, 0, FANION.getY(), new int[]{-200, -200, 200, 200}, false, SETTINGS);
		assertTrue(layout.stairs().isEmpty());
		BlockPos start = layout.segments().getFirst().cells().getFirst().feet();
		assertEquals(FANION.getY(), start.getY());
		assertEquals(1, Math.abs(start.getX() - FANION.getX()) + Math.abs(start.getZ() - FANION.getZ()));
		assertEquals(0, layout.segments().getFirst().stands()[0], "dug from the fanion");
		assertEquals(-1, layout.parent(0), "the fanion is the root of the walking graph");
	}

	@Test
	void structureHoldsCellsAndFloors() {
		MineLayout layout = MineLayout.build(FANION, 0, 60, chunkSquare(FANION, 1), false, SETTINGS);
		MineLayout.Cell step = layout.stairs().get(2);
		assertTrue(layout.inStructure(step.feet()));
		assertTrue(layout.inStructure(step.top()));
		assertTrue(layout.inStructure(step.floor()));
		assertTrue(layout.inStructure(FANION.below()), "the fanion's support");
		MineLayout.Cell gallery = layout.segments().getFirst().cells().getFirst();
		assertTrue(layout.inStructure(gallery.feet().above()));
		assertFalse(layout.inStructure(gallery.feet().above(2)), "the rock above a 2-high tunnel is not part of it");
	}
}
