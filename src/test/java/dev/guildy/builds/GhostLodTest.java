package dev.guildy.builds;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ghost blocks of a preview: every block, the shell, then coarser cubes that keep the building's shape. */
class GhostLodTest {
	private static final int AIR = 0;
	private static final int STONE = 1;
	private static final int GLASS = 2;

	private static final class Grid {
		final int sx;
		final int sy;
		final int sz;
		final int[] cells;

		Grid(int sx, int sy, int sz) {
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.cells = new int[sx * sy * sz];
		}

		int index(int x, int y, int z) {
			return (y * sz + z) * sx + x;
		}

		Grid fill(int x0, int y0, int z0, int x1, int y1, int z1, int palette) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						cells[index(x, y, z)] = palette;
					}
				}
			}
			return this;
		}

		List<GhostLod.Ghost> ghosts(int max) {
			return GhostLod.select(sx, sy, sz, cells, i -> cells[i] != AIR, p -> p == STONE, max);
		}

		/** Cells covered by the ghosts. */
		Set<Integer> covered(List<GhostLod.Ghost> ghosts) {
			Set<Integer> set = new HashSet<>();
			for (GhostLod.Ghost g : ghosts) {
				for (int y = g.y(); y < g.y() + g.sizeY(); y++) {
					for (int z = g.z(); z < g.z() + g.sizeZ(); z++) {
						for (int x = g.x(); x < g.x() + g.sizeX(); x++) {
							set.add(index(x, y, z));
						}
					}
				}
			}
			return set;
		}
	}

	@Test
	void everyBlockWhenTheyFit() {
		Grid grid = new Grid(4, 4, 4).fill(0, 0, 0, 3, 3, 3, STONE);
		List<GhostLod.Ghost> ghosts = grid.ghosts(64);
		assertEquals(64, ghosts.size());
		assertTrue(ghosts.stream().allMatch(g -> g.side() == 1));
	}

	@Test
	void onlyTheShellWhenTheBlocksDoNotFit() {
		Grid grid = new Grid(5, 5, 5).fill(0, 0, 0, 4, 4, 4, STONE);
		List<GhostLod.Ghost> ghosts = grid.ghosts(100);
		assertEquals(125 - 27, ghosts.size(), "the 3x3x3 core is hidden");
		assertTrue(ghosts.stream().allMatch(g -> g.side() == 1));
	}

	@Test
	void largeBuildingsAreShownInCubesThatKeepTheirShape() {
		// A hollow 64x32x64 hall (walls, floor and roof one block thick) and a tower 8x64x8 on its corner.
		Grid grid = new Grid(64, 96, 64)
				.fill(0, 0, 0, 63, 31, 63, STONE)
				.fill(1, 1, 1, 62, 30, 62, AIR)
				.fill(0, 32, 0, 7, 95, 7, STONE);
		List<GhostLod.Ghost> ghosts = grid.ghosts(1000);
		assertTrue(ghosts.size() <= 1000, "budget kept: " + ghosts.size());
		assertTrue(ghosts.stream().anyMatch(g -> g.side() > 1), "coarser cubes");
		// The hall's walls and the top of the tower are both covered: the shape is still there.
		Set<Integer> covered = grid.covered(ghosts);
		assertTrue(covered.contains(grid.index(0, 10, 30)), "west wall");
		assertTrue(covered.contains(grid.index(63, 10, 30)), "east wall");
		assertTrue(covered.contains(grid.index(30, 31, 30)), "roof");
		assertTrue(covered.contains(grid.index(3, 95, 3)), "top of the tower");
		// Nothing stands for the empty sky beside the tower.
		assertTrue(!covered.contains(grid.index(40, 80, 40)), "no ghost in the air beside the tower");
		// Cubes never leave the box.
		for (GhostLod.Ghost g : ghosts) {
			assertTrue(g.x() + g.sizeX() <= 64 && g.y() + g.sizeY() <= 96 && g.z() + g.sizeZ() <= 64, "inside the box: " + g);
		}
	}

	@Test
	void cubesHugTheirBlocksSoThatWallsStayInPlace() {
		// A floor and a wall one block thick at x = 5, far too many blocks for the budget.
		Grid grid = new Grid(64, 64, 64).fill(0, 0, 0, 63, 0, 63, STONE).fill(5, 1, 0, 5, 63, 63, STONE);
		List<GhostLod.Ghost> ghosts = grid.ghosts(300);
		assertTrue(ghosts.size() <= 300);
		assertTrue(ghosts.stream().anyMatch(g -> g.side() > 1), "coarser cubes");
		for (GhostLod.Ghost g : ghosts) {
			if (g.y() > 0) {
				assertTrue(g.x() == 5 && g.sizeX() == 1, "the wall stays at x = 5, one block thick: " + g);
			} else {
				// On the floor: one block thick, except the cubes where the wall stands on it.
				assertTrue(g.sizeY() == 1 || (g.x() <= 5 && 5 < g.x() + g.sizeX()), "the floor stays one block thick: " + g);
			}
		}
	}

	@Test
	void cubesShowTheirMainBlockWithFullBlocksFirst() {
		// The corner 4x4x4 cube: 24 stone and 40 glass -> stone counts double (48) and wins.
		Grid grid = new Grid(8, 8, 8).fill(0, 0, 0, 7, 7, 7, GLASS).fill(0, 0, 0, 3, 0, 3, STONE).fill(0, 1, 0, 3, 1, 1, STONE);
		List<GhostLod.Ghost> ghosts = grid.ghosts(20);
		assertTrue(ghosts.size() <= 20);
		GhostLod.Ghost corner = ghosts.stream().filter(g -> g.x() == 0 && g.y() == 0 && g.z() == 0).findFirst().orElseThrow();
		assertEquals(STONE, corner.palette());
	}

	@Test
	void loneScatteredBlocksDoNotFillACube() {
		// A big platform, plus a lone post far above it.
		Grid grid = new Grid(64, 64, 64).fill(0, 0, 0, 63, 0, 63, STONE).fill(32, 50, 32, 32, 50, 32, STONE);
		List<GhostLod.Ghost> ghosts = grid.ghosts(200);
		assertTrue(ghosts.size() <= 200);
		assertTrue(ghosts.stream().allMatch(g -> g.y() == 0), "only the platform: " + ghosts.size());
	}

	@Test
	void noGhostWithAZeroBudget() {
		Grid grid = new Grid(4, 4, 4).fill(0, 0, 0, 3, 3, 3, STONE);
		assertEquals(0, grid.ghosts(0).size());
	}

	@Test
	void anyBudgetIsKeptEvenForTheLargestBoxes() {
		Grid grid = new Grid(200, 60, 200).fill(0, 0, 0, 199, 59, 199, STONE);
		for (int max : new int[] {1, 7, 50, 4096}) {
			List<GhostLod.Ghost> ghosts = grid.ghosts(max);
			assertTrue(!ghosts.isEmpty() && ghosts.size() <= max, max + " -> " + ghosts.size());
		}
	}
}
