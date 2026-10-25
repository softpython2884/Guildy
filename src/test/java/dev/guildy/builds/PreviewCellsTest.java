package dev.guildy.builds;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a preview shows: every block, the shell, or a cloud of particles one per shell block. */
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
	void particlesWhenEvenTheShellDoesNotFit() {
		Grid grid = new Grid(10, 10, 10).fill(0, 0, 0, 9, 9, 9, true);
		assertNull(grid.ghosts(100));
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

	@Test
	void everyNearBlockHasItsParticleAndTheRestIsSampled() {
		// A 100x1x100 floor: the player stands on it at (50, 1, 50).
		Grid grid = new Grid(100, 1, 100).fill(0, 0, 0, 99, 0, 99, true);
		int[] shell = grid.shell();
		int[] picked = PreviewCells.pick(100, 1, 100, shell, 50.5, 1.0, 50.5, 10, 1000, 0);
		assertTrue(picked.length <= 1000);
		Set<Integer> set = new HashSet<>();
		for (int i : picked) {
			assertTrue(set.add(i), "no cell twice");
		}
		// Every block within 9 blocks of the player is there.
		for (int x = 42; x <= 58; x++) {
			for (int z = 42; z <= 58; z++) {
				double dx = x + 0.5 - 50.5;
				double dz = z + 0.5 - 50.5;
				if (dx * dx + dz * dz + 0.25 <= 81) {
					assertTrue(set.contains(grid.index(x, 0, z)), "near block " + x + "," + z);
				}
			}
		}
		// Far corners of the floor get samples too: the whole shape shows.
		assertTrue(set.stream().anyMatch(i -> i % 100 < 10 && i / 100 < 10), "a sample near one corner");
		assertTrue(set.stream().anyMatch(i -> i % 100 >= 90 && i / 100 >= 90), "a sample near the opposite corner");
	}

	@Test
	void theFarSampleChangesAtEachPulseAndCoversEverythingOverTime() {
		Grid grid = new Grid(60, 1, 60).fill(0, 0, 0, 59, 0, 59, true);
		int[] shell = grid.shell();
		Set<Integer> seen = new HashSet<>();
		int[] first = PreviewCells.pick(60, 1, 60, shell, -100, 0, -100, 4, 300, 0);
		int[] second = PreviewCells.pick(60, 1, 60, shell, -100, 0, -100, 4, 300, 1);
		assertTrue(first.length > 0 && first[0] != second[0], "another sample");
		for (int pulse = 0; pulse < 12; pulse++) {
			for (int i : PreviewCells.pick(60, 1, 60, shell, -100, 0, -100, 4, 300, pulse)) {
				seen.add(i);
			}
		}
		assertEquals(3600, seen.size(), "every block shown after a full cycle");
	}

	@Test
	void theBudgetIsKept() {
		Grid grid = new Grid(80, 40, 80).fill(0, 0, 0, 79, 39, 79, true);
		int[] shell = grid.shell();
		for (int max : new int[] {0, 1, 50, 2500}) {
			int[] picked = PreviewCells.pick(80, 40, 80, shell, 40, 20, 40, 24, max, 3);
			assertTrue(picked.length <= max, max + " -> " + picked.length);
		}
	}
}
