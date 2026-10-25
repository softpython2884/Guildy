package dev.guildy.builds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a preview shows: every block, the shell, or only the outline. */
class PreviewCellsTest {
	private static final class Grid {
		final int sx;
		final int sy;
		final int sz;
		final boolean[] solid;

		Grid(int sx, int sy, int sz) {
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.solid = new boolean[sx * sy * sz];
		}

		int index(int x, int y, int z) {
			return (y * sz + z) * sx + x;
		}

		Grid fill(int x0, int y0, int z0, int x1, int y1, int z1, boolean value) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						solid[index(x, y, z)] = value;
					}
				}
			}
			return this;
		}

		int[] shell() {
			return PreviewCells.shell(sx, sy, sz, i -> solid[i]);
		}

		int[] ghosts(int max) {
			return PreviewCells.ghosts(sx, sy, sz, i -> solid[i], max, this::shell);
		}
	}

	@Test
	void everyBlockWhenTheyFit() {
		Grid grid = new Grid(4, 4, 4).fill(0, 0, 0, 3, 3, 3, true);
		int[] ghosts = grid.ghosts(64);
		assertNotNull(ghosts);
		assertEquals(64, ghosts.length);
	}

	@Test
	void onlyTheShellWhenTheBlocksDoNotFit() {
		Grid grid = new Grid(5, 5, 5).fill(0, 0, 0, 4, 4, 4, true);
		int[] ghosts = grid.ghosts(100);
		assertNotNull(ghosts);
		assertEquals(125 - 27, ghosts.length, "the 3x3x3 core is hidden");
	}

	@Test
	void onlyTheOutlineWhenEvenTheShellDoesNotFit() {
		Grid grid = new Grid(10, 10, 10).fill(0, 0, 0, 9, 9, 9, true);
		assertNull(grid.ghosts(100), "too many: only the outline");
		assertEquals(0, grid.ghosts(0).length, "0: only the outline");
	}

	@Test
	void theShellIsInIndexOrderAndSkipsHiddenBlocks() {
		Grid grid = new Grid(6, 6, 6).fill(0, 0, 0, 5, 5, 5, true);
		int[] shell = grid.shell();
		assertEquals(216 - 64, shell.length);
		for (int k = 1; k < shell.length; k++) {
			assertTrue(shell[k - 1] < shell[k]);
		}
		for (int i : shell) {
			int x = i % 6;
			int z = (i / 6) % 6;
			int y = i / 36;
			assertTrue(x == 0 || y == 0 || z == 0 || x == 5 || y == 5 || z == 5, "outside block " + i);
		}
	}
}
