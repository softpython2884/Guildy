package dev.guildy.builds;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which air cells of a schematic are dug out in each interior mode. */
class InteriorRulesTest {
	private static final class Grid {
		final int sx;
		final int sy;
		final int sz;
		final byte[] kinds;

		Grid(int sx, int sy, int sz, byte fill) {
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.kinds = new byte[sx * sy * sz];
			java.util.Arrays.fill(kinds, fill);
		}

		int index(int x, int y, int z) {
			return (y * sz + z) * sx + x;
		}

		Grid set(int x, int y, int z, byte kind) {
			kinds[index(x, y, z)] = kind;
			return this;
		}

		BitSet clear(InteriorRules.Mode mode, List<InteriorRules.Door> doors) {
			return InteriorRules.clearCells(sx, sy, sz, kinds, doors, mode);
		}
	}

	/** A 5 × 4 × 5 house: floor (y 0), walls, roof (y 3), air inside; the box is otherwise air. */
	private static Grid house() {
		Grid g = new Grid(5, 4, 5, InteriorRules.AIR);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				g.set(x, 0, z, InteriorRules.BLOCK);
				g.set(x, 3, z, InteriorRules.BLOCK);
				if (x == 0 || z == 0 || x == 4 || z == 4) {
					g.set(x, 1, z, InteriorRules.BLOCK);
					g.set(x, 2, z, InteriorRules.BLOCK);
				}
			}
		}
		return g;
	}

	@Test
	void hollowDigsTheRoomsOnly() {
		Grid g = house();
		// An opening in a wall: the room stays enclosed by floor and roof (column rule).
		g.set(2, 1, 0, InteriorRules.AIR);
		BitSet clear = g.clear(InteriorRules.Mode.HOLLOW, List.of());
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				assertTrue(clear.get(g.index(x, 1, z)) && clear.get(g.index(x, 2, z)), "inside " + x + "," + z);
			}
		}
		assertTrue(clear.get(g.index(2, 1, 0)), "the opening is between floor and roof");
		assertEquals(9 * 2 + 1, clear.cardinality());
	}

	@Test
	void hollowKeepsTheTerrainAroundAndAbove() {
		// A tower without roof on a bigger box: air around it and above it stays terrain.
		Grid g = new Grid(7, 6, 7, InteriorRules.AIR);
		for (int y = 0; y < 4; y++) {
			for (int x = 2; x <= 4; x++) {
				for (int z = 2; z <= 4; z++) {
					if (x == 2 || x == 4 || z == 2 || z == 4 || y == 0) {
						g.set(x, y, z, InteriorRules.BLOCK);
					}
				}
			}
		}
		BitSet clear = g.clear(InteriorRules.Mode.HOLLOW, List.of());
		// Inside the walls (no roof): enclosed on each layer.
		assertTrue(clear.get(g.index(3, 1, 3)));
		assertTrue(clear.get(g.index(3, 3, 3)));
		// Above the walls, around the tower: kept.
		assertFalse(clear.get(g.index(3, 4, 3)));
		assertFalse(clear.get(g.index(0, 1, 0)));
		assertFalse(clear.get(g.index(1, 2, 3)));
		assertEquals(3, clear.cardinality());
	}

	@Test
	void courtyardEnclosedByAWall() {
		// One layer: a ring of wall, air inside and outside.
		Grid g = new Grid(5, 1, 5, InteriorRules.AIR);
		for (int i = 0; i < 5; i++) {
			g.set(i, 0, 0, InteriorRules.BLOCK).set(i, 0, 4, InteriorRules.BLOCK).set(0, 0, i, InteriorRules.BLOCK).set(4, 0, i, InteriorRules.BLOCK);
		}
		assertEquals(9, g.clear(InteriorRules.Mode.HOLLOW, List.of()).cardinality());
		// A gap in the ring: the courtyard leaks to the edge, nothing enclosed.
		g.set(2, 0, 0, InteriorRules.AIR);
		assertEquals(0, g.clear(InteriorRules.Mode.HOLLOW, List.of()).cardinality());
	}

	@Test
	void doorsOpenOntoAir() {
		// A door in the middle of a wall line (x = 2) of a one-layer-thick box: front and back cleared, both heights.
		Grid g = new Grid(5, 3, 3, InteriorRules.AIR);
		for (int z = 0; z < 3; z++) {
			for (int y = 0; y < 3; y++) {
				g.set(2, y, z, InteriorRules.BLOCK);
			}
		}
		BitSet clear = g.clear(InteriorRules.Mode.HOLLOW, List.of(new InteriorRules.Door(2, 0, 1, true)));
		assertTrue(clear.get(g.index(1, 0, 1)));
		assertTrue(clear.get(g.index(3, 0, 1)));
		assertTrue(clear.get(g.index(1, 1, 1)));
		assertTrue(clear.get(g.index(3, 1, 1)));
		assertEquals(4, clear.cardinality());
	}

	@Test
	void keepAndBoxModes() {
		Grid g = house();
		assertEquals(0, g.clear(InteriorRules.Mode.KEEP, List.of()).cardinality());
		long air = 0;
		for (byte kind : g.kinds) {
			if (kind == InteriorRules.AIR) {
				air++;
			}
		}
		assertEquals(air, g.clear(InteriorRules.Mode.BOX, List.of()).cardinality());
	}

	@Test
	void voidCellsAreNeverDug() {
		Grid g = house();
		g.set(2, 1, 2, InteriorRules.VOID);
		BitSet clear = g.clear(InteriorRules.Mode.BOX, List.of());
		assertFalse(clear.get(g.index(2, 1, 2)));
		assertFalse(g.clear(InteriorRules.Mode.HOLLOW, List.of()).get(g.index(2, 1, 2)));
		assertEquals(InteriorRules.Mode.BOX, InteriorRules.Mode.HOLLOW.next());
		assertEquals(InteriorRules.Mode.HOLLOW, InteriorRules.Mode.parse("Hollow", InteriorRules.Mode.KEEP));
		assertEquals(InteriorRules.Mode.KEEP, InteriorRules.Mode.parse("?", InteriorRules.Mode.KEEP));
	}
}
