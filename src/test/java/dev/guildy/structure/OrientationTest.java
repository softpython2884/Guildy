package dev.guildy.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrientationTest {
	/** {fx, fz} of the 4 horizontal directions. */
	private static final int[][] FACINGS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

	@Test
	void rightAxisIsTheViewerRightHand() {
		// Front facing south (+Z): the viewer stands south looking north, right hand = east (+X).
		assertEquals(1, Orientation.rightX(0, 1));
		assertEquals(0, Orientation.rightZ(0, 1));
		// Front facing north: right hand = west.
		assertEquals(-1, Orientation.rightX(0, -1));
		assertEquals(0, Orientation.rightZ(0, -1));
		// Front facing east: viewer looks west, right hand = north (-Z).
		assertEquals(0, Orientation.rightX(1, 0));
		assertEquals(-1, Orientation.rightZ(1, 0));
		// Front facing west: viewer looks east, right hand = south (+Z).
		assertEquals(0, Orientation.rightX(-1, 0));
		assertEquals(1, Orientation.rightZ(-1, 0));
	}

	@Test
	void rightAxisIsPerpendicularToTheFront() {
		for (int[] f : FACINGS) {
			int rx = Orientation.rightX(f[0], f[1]);
			int rz = Orientation.rightZ(f[0], f[1]);
			assertEquals(0, rx * f[0] + rz * f[1]);
			assertTrue(Orientation.isHorizontalUnit(rx, rz));
		}
		assertFalse(Orientation.isHorizontalUnit(1, 1));
	}

	@Test
	void cellOffsetsFromBottomLeftOrigin() {
		int height = 2;
		// Bottom-left cell is the origin.
		assertArrayEquals(new int[] {0, 0, 0}, Orientation.cellOffset(0, 1, height, 0, 1));
		// Top-right cell of a 3 x 2 wall facing south: 2 east, 1 up.
		assertArrayEquals(new int[] {2, 1, 0}, Orientation.cellOffset(2, 0, height, 0, 1));
		// Same wall facing east: columns go north.
		assertArrayEquals(new int[] {0, 1, -2}, Orientation.cellOffset(2, 0, height, 1, 0));
	}

	@Test
	void placementAndOriginAgreeForEveryFacing() {
		StructurePattern pattern = StructurePattern.parse(List.of("LBL", "LPL"));
		int cx = 100;
		int cy = 70;
		int cz = -40;
		for (int[] f : FACINGS) {
			for (int anchorRow = 0; anchorRow < pattern.height(); anchorRow++) {
				for (int anchorCol = 0; anchorCol < pattern.width(); anchorCol++) {
					PatternMatcher.Placement placement = new PatternMatcher.Placement(anchorCol, anchorRow);
					int[] originOffset = Orientation.offset(placement.originRight(), placement.originUp(pattern.height()), f[0], f[1]);
					int ox = cx + originOffset[0];
					int oy = cy + originOffset[1];
					int oz = cz + originOffset[2];
					for (int row = 0; row < pattern.height(); row++) {
						for (int col = 0; col < pattern.width(); col++) {
							int[] fromOrigin = Orientation.cellOffset(col, row, pattern.height(), f[0], f[1]);
							int[] fromClick = Orientation.offset(placement.right(col), placement.up(row), f[0], f[1]);
							assertArrayEquals(new int[] {cx + fromClick[0], cy + fromClick[1], cz + fromClick[2]},
									new int[] {ox + fromOrigin[0], oy + fromOrigin[1], oz + fromOrigin[2]});
						}
					}
				}
			}
		}
	}
}
