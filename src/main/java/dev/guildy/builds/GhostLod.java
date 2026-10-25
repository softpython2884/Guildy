package dev.guildy.builds;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Ghost blocks of a preview (pure): every block when they fit, else the blocks touching air or the outside, else the
 * schematic seen through coarser cubes (2, 3, 4… blocks a side). A cube holding enough blocks becomes one ghost, at
 * scale 1:1, stretched over the blocks it holds and showing the one it holds the most: a large building keeps its whole
 * shape, its outer faces exactly where they will be built, instead of being sampled into scattered blocks.
 */
final class GhostLod {
	/** Cube sides tried in turn (up to the largest schematic side allowed). */
	private static final int[] SIDES = {2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 768, 1024};

	/** One ghost: local corner, size in blocks (clipped to the box) and the palette entry shown. */
	record Ghost(int x, int y, int z, int sizeX, int sizeY, int sizeZ, int palette) {
		int side() {
			return Math.max(sizeX, Math.max(sizeY, sizeZ));
		}
	}

	private GhostLod() {
	}

	/**
	 * Ghosts of a schematic.
	 *
	 * @param cells palette entry of each cell, index {@code (y * sz + z) * sx + x}
	 * @param shown cells that may be shown (by index)
	 * @param solid palette entries that stand best for a cube (full blocks): they count double
	 * @param max   ghosts at most (0: none)
	 */
	static List<Ghost> select(int sx, int sy, int sz, int[] cells, IntPredicate shown, IntPredicate solid, int max) {
		List<Ghost> ghosts = new ArrayList<>();
		if (max <= 0 || sx <= 0 || sy <= 0 || sz <= 0) {
			return ghosts;
		}
		BitSet on = new BitSet(cells.length);
		int total = 0;
		int palettes = 1;
		for (int i = 0; i < cells.length; i++) {
			if (shown.test(i)) {
				on.set(i);
				total++;
				palettes = Math.max(palettes, cells[i] + 1);
			}
		}
		if (total <= max) {
			for (int i = on.nextSetBit(0); i >= 0; i = on.nextSetBit(i + 1)) {
				ghosts.add(single(sx, sz, i, cells[i]));
			}
			return ghosts;
		}
		List<Integer> shell = new ArrayList<>();
		for (int i = on.nextSetBit(0); i >= 0; i = on.nextSetBit(i + 1)) {
			if (exposed(on, sx, sy, sz, i)) {
				shell.add(i);
			}
		}
		if (shell.size() <= max) {
			for (int i : shell) {
				ghosts.add(single(sx, sz, i, cells[i]));
			}
			return ghosts;
		}
		// The shell shrinks with the square of the cube side: start near the side that should fit.
		int largest = Math.max(sx, Math.max(sy, sz));
		int guess = Math.min(largest, (int) Math.sqrt(shell.size() / (double) max));
		int[] frequency = new int[palettes];
		for (int side : SIDES) {
			if (side < guess && side < largest) {
				continue;
			}
			Grid grid = new Grid(side, sx, sy, sz, on);
			List<Integer> visible = grid.exposed();
			if (visible.size() <= max || side >= largest) {
				for (int v : visible) {
					if (ghosts.size() >= max) {
						break;
					}
					ghosts.add(grid.ghost(v, cells, on, solid, frequency));
				}
				return ghosts;
			}
		}
		// Larger than every side tried: a single cube.
		Grid grid = new Grid(largest, sx, sy, sz, on);
		for (int v : grid.exposed()) {
			ghosts.add(grid.ghost(v, cells, on, solid, frequency));
		}
		return ghosts;
	}

	private static Ghost single(int sx, int sz, int index, int palette) {
		int x = index % sx;
		int z = (index / sx) % sz;
		int y = index / (sx * sz);
		return new Ghost(x, y, z, 1, 1, 1, palette);
	}

	private static boolean exposed(BitSet on, int sx, int sy, int sz, int index) {
		int x = index % sx;
		int z = (index / sx) % sz;
		int y = index / (sx * sz);
		return x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1
				|| !on.get(index - 1) || !on.get(index + 1)
				|| !on.get(index - sx) || !on.get(index + sx)
				|| !on.get(index - sx * sz) || !on.get(index + sx * sz);
	}

	/** The schematic cut into cubes of one side; a cube is filled when it holds at least half a slice of blocks. */
	private static final class Grid {
		final int side;
		final int sx;
		final int sy;
		final int sz;
		final int gx;
		final int gy;
		final int gz;
		final BitSet filled;

		Grid(int side, int sx, int sy, int sz, BitSet on) {
			this.side = side;
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.gx = (sx + side - 1) / side;
			this.gy = (sy + side - 1) / side;
			this.gz = (sz + side - 1) / side;
			int[] counts = new int[gx * gy * gz];
			for (int i = on.nextSetBit(0); i >= 0; i = on.nextSetBit(i + 1)) {
				int x = i % sx;
				int z = (i / sx) % sz;
				int y = i / (sx * sz);
				counts[((y / side) * gz + z / side) * gx + x / side]++;
			}
			filled = new BitSet(counts.length);
			for (int v = 0; v < counts.length; v++) {
				int ex = extent(v % gx, gx, sx);
				int ez = extent((v / gx) % gz, gz, sz);
				int ey = extent(v / (gx * gz), gy, sy);
				// Half a slice through the cube: a wall or a floor one block thick stays, a lone post does not.
				int need = Math.max(1, ex * ey * ez / (2 * Math.max(ex, Math.max(ey, ez))));
				if (counts[v] >= need) {
					filled.set(v);
				}
			}
		}

		private int extent(int g, int count, int size) {
			return g < count - 1 ? side : size - side * (count - 1);
		}

		List<Integer> exposed() {
			List<Integer> list = new ArrayList<>();
			for (int v = filled.nextSetBit(0); v >= 0; v = filled.nextSetBit(v + 1)) {
				int x = v % gx;
				int z = (v / gx) % gz;
				int y = v / (gx * gz);
				if (x == 0 || y == 0 || z == 0 || x == gx - 1 || y == gy - 1 || z == gz - 1
						|| !filled.get(v - 1) || !filled.get(v + 1)
						|| !filled.get(v - gx) || !filled.get(v + gx)
						|| !filled.get(v - gx * gz) || !filled.get(v + gx * gz)) {
					list.add(v);
				}
			}
			return list;
		}

		/**
		 * The cube as a ghost, shrunk to the blocks it holds (a wall stays exactly where it will be built, one block thick)
		 * and showing its most frequent block (full blocks count double).
		 */
		Ghost ghost(int v, int[] cells, BitSet on, IntPredicate solid, int[] frequency) {
			int x0 = (v % gx) * side;
			int z0 = ((v / gx) % gz) * side;
			int y0 = (v / (gx * gz)) * side;
			int ex = Math.min(side, sx - x0);
			int ey = Math.min(side, sy - y0);
			int ez = Math.min(side, sz - z0);
			List<Integer> seen = new ArrayList<>();
			int best = -1;
			int minX = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxX = -1;
			int maxY = -1;
			int maxZ = -1;
			for (int y = y0; y < y0 + ey; y++) {
				for (int z = z0; z < z0 + ez; z++) {
					int row = (y * sz + z) * sx;
					for (int x = x0; x < x0 + ex; x++) {
						int i = row + x;
						if (!on.get(i)) {
							continue;
						}
						minX = Math.min(minX, x);
						minY = Math.min(minY, y);
						minZ = Math.min(minZ, z);
						maxX = Math.max(maxX, x);
						maxY = Math.max(maxY, y);
						maxZ = Math.max(maxZ, z);
						int p = cells[i];
						if (frequency[p] == 0) {
							seen.add(p);
						}
						frequency[p] += solid.test(p) ? 2 : 1;
						if (best < 0 || frequency[p] > frequency[best]) {
							best = p;
						}
					}
				}
			}
			for (int p : seen) {
				frequency[p] = 0;
			}
			return new Ghost(minX, minY, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, best);
		}
	}
}
