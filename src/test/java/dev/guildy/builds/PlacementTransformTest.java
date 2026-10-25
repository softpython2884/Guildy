package dev.guildy.builds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Placement of a schematic: the same formulas as vanilla structures (StructureTemplate.transform, pivot 0), the inverse,
 * the world box, a rotation keeping the box corner, the default placement in front of a player.
 */
class PlacementTransformTest {
	/** Vanilla StructureTemplate.transform with pivot (0, 0, 0), copied as reference: {x, z}. */
	private static int[] vanilla(int x, int z, PlacementTransform.Flip flip, PlacementTransform.Turn turn) {
		switch (flip) {
			case LEFT_RIGHT -> z = -z;
			case FRONT_BACK -> x = -x;
			default -> {
			}
		}
		return switch (turn) {
			case COUNTERCLOCKWISE_90 -> new int[] {z, -x};
			case CLOCKWISE_90 -> new int[] {-z, x};
			case CLOCKWISE_180 -> new int[] {-x, -z};
			case NONE -> new int[] {x, z};
		};
	}

	@Test
	void matchesVanillaAndInverts() {
		for (PlacementTransform.Turn turn : PlacementTransform.Turn.values()) {
			for (PlacementTransform.Flip flip : PlacementTransform.Flip.values()) {
				PlacementTransform t = new PlacementTransform(100, 64, -40, turn, flip);
				for (int x = -3; x <= 5; x++) {
					for (int z = -2; z <= 4; z++) {
						int[] expected = vanilla(x, z, flip, turn);
						assertEquals(100 + expected[0], t.worldX(x, z), turn + " " + flip);
						assertEquals(-40 + expected[1], t.worldZ(x, z), turn + " " + flip);
						assertArrayEquals(new int[] {x, z}, t.local(t.worldX(x, z), t.worldZ(x, z)), "inverse " + turn + " " + flip);
					}
				}
				assertEquals(70, t.worldY(6));
			}
		}
	}

	@Test
	void boxOfEachRotation() {
		// 5 wide (x), 3 high, 2 deep (z), origin 10 64 20.
		assertArrayEquals(new int[] {10, 64, 20, 14, 66, 21}, new PlacementTransform(10, 64, 20, PlacementTransform.Turn.NONE,
				PlacementTransform.Flip.NONE).box(5, 3, 2));
		// Clockwise: x → z, z → -x.
		assertArrayEquals(new int[] {9, 64, 20, 10, 66, 24}, new PlacementTransform(10, 64, 20, PlacementTransform.Turn.CLOCKWISE_90,
				PlacementTransform.Flip.NONE).box(5, 3, 2));
		assertArrayEquals(new int[] {6, 64, 19, 10, 66, 20}, new PlacementTransform(10, 64, 20, PlacementTransform.Turn.CLOCKWISE_180,
				PlacementTransform.Flip.NONE).box(5, 3, 2));
	}

	@Test
	void reorientingKeepsTheBoxCorner() {
		PlacementTransform t = new PlacementTransform(10, 64, 20, PlacementTransform.Turn.NONE, PlacementTransform.Flip.NONE);
		for (PlacementTransform.Turn turn : PlacementTransform.Turn.values()) {
			for (PlacementTransform.Flip flip : PlacementTransform.Flip.values()) {
				int[] box = t.reoriented(turn, flip, 5, 3, 2).box(5, 3, 2);
				assertEquals(10, box[0]);
				assertEquals(20, box[2]);
				assertEquals(64, box[1]);
			}
		}
		assertEquals(PlacementTransform.Turn.CLOCKWISE_90, PlacementTransform.Turn.NONE.clockwise());
		assertEquals(PlacementTransform.Turn.COUNTERCLOCKWISE_90, PlacementTransform.Turn.NONE.counterClockwise());
		assertEquals(PlacementTransform.Turn.NONE, PlacementTransform.Turn.COUNTERCLOCKWISE_90.clockwise());
		assertEquals(PlacementTransform.Flip.LEFT_RIGHT, PlacementTransform.Flip.NONE.next());
	}

	@Test
	void inFrontOfThePlayer() {
		// Looking south (yaw 0): the schematic extends towards +z, its front edge centred on the anchor.
		PlacementTransform south = PlacementTransform.inFront(0, 70, 0, 0F, 5);
		assertEquals(PlacementTransform.Turn.NONE, south.turn());
		assertArrayEquals(new int[] {-2, 70, 0, 2, 72, 3}, south.box(5, 3, 4));
		// Looking north (yaw 180): towards -z.
		PlacementTransform north = PlacementTransform.inFront(0, 70, 0, 180F, 5);
		assertEquals(PlacementTransform.Turn.CLOCKWISE_180, north.turn());
		int[] box = north.box(5, 3, 4);
		assertEquals(-3, box[2]);
		assertEquals(0, box[5]);
		assertEquals(0, north.worldX(2, 0));
		// West (yaw 90) and east (yaw -90 = 270).
		assertEquals(PlacementTransform.Turn.CLOCKWISE_90, PlacementTransform.Turn.facing(90F));
		assertEquals(PlacementTransform.Turn.COUNTERCLOCKWISE_90, PlacementTransform.Turn.facing(-90F));
		assertEquals(PlacementTransform.Turn.COUNTERCLOCKWISE_90, PlacementTransform.Turn.facing(260F));
		PlacementTransform west = PlacementTransform.inFront(0, 70, 0, 90F, 5);
		assertEquals(-1, west.worldX(2, 1), "one step deeper goes west");
		assertEquals(0, west.worldZ(2, 1));
	}
}
