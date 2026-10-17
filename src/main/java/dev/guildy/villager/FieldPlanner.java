package dev.guildy.villager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure planning of the field works of a farmer (no Minecraft classes, unit tested), on a grid of columns read from
 * the world ({@link Grid}: the zone square plus {@link #RANGE} columns around it, for the water nearby).
 * <ul>
 *   <li>{@link #irrigation}: which cells are hydrated now (vanilla rule of {@code FarmlandBlock.isNearWater}: water
 *   within 4 blocks horizontally, at the cell's level or one above), and where to dig 1-block water holes so that every
 *   cell to hydrate ends up hydrated: a regular lattice of holes 9 blocks apart first (the fewest holes on a plain
 *   square, each hydrating its 9×9 square), each lattice hole shifted by up to {@link #SHIFT} blocks when its cell is
 *   not safe, then a greedy pass for the cells left (the safe hole covering the most of them). Holes only on safe
 *   cells ({@link Grid#hole}).</li>
 *   <li>{@link #levelJobs}: 1-block pits to fill and 1-block bumps to shave so that natural soil matches the level of
 *   the field around it.</li>
 * </ul>
 */
public final class FieldPlanner {
	/** Vanilla hydration range of farmland (blocks, horizontally). */
	public static final int RANGE = PlantingRules.HYDRATION_RANGE;
	/** Distance between two lattice holes: their hydrated squares touch without overlapping. */
	public static final int SPACING = 2 * RANGE + 1;
	/** Column not read (chunk not loaded), or no water there. */
	public static final int UNKNOWN = Integer.MIN_VALUE;
	/** A lattice hole may move this far (blocks, Chebyshev) when its own cell is not safe. */
	public static final int SHIFT = 2;
	/**
	 * Candidate holes the greedy pass evaluates at most per plan (each costs a 9×9 count): on rough ground the cells left
	 * are planned by the next survey, once the first holes are dug.
	 */
	public static final int GREEDY_BUDGET = 40_000;

	private FieldPlanner() {
	}

	/** Columns of the surveyed area, indexed {@code z * width + x} (local coordinates). */
	public static final class Grid {
		public final int width;
		public final int depth;
		/** Y of the top block of the column (soil, farmland, water...), {@link #UNKNOWN} when not read. */
		public final int[] ground;
		/** Y of the water at the top of the column, {@link #UNKNOWN} when none. */
		public final int[] water;
		/** Cell of the work area: only those are worked (the others only bring their water). */
		public final boolean[] zone;
		/** Soil to till (grass, dirt, coarse or rooted dirt), room above, nothing built around. */
		public final boolean[] soil;
		/** Farmland (sown or not). */
		public final boolean[] farmland;
		/** A water hole may replace the top block of this cell (safe: full blocks under and around, nothing built near). */
		public final boolean[] hole;
		/** Natural soil (not farmland) with room above and nothing built around: it may be filled on or shaved. */
		public final boolean[] level;

		public Grid(int width, int depth) {
			this.width = Math.max(1, width);
			this.depth = Math.max(1, depth);
			int size = this.width * this.depth;
			ground = new int[size];
			water = new int[size];
			Arrays.fill(ground, UNKNOWN);
			Arrays.fill(water, UNKNOWN);
			zone = new boolean[size];
			soil = new boolean[size];
			farmland = new boolean[size];
			hole = new boolean[size];
			level = new boolean[size];
		}

		public int size() {
			return width * depth;
		}

		public int index(int x, int z) {
			return z * width + x;
		}

		public boolean inside(int x, int z) {
			return x >= 0 && z >= 0 && x < width && z < depth;
		}
	}

	/**
	 * Result of {@link #irrigation}.
	 *
	 * @param holes       new holes {x, z} (local), in digging order
	 * @param hydrated    cells hydrated by the water already there
	 * @param covered     cells hydrated once the holes are dug (hydrated ones included, holes included)
	 * @param needed      cells that had to be hydrated
	 * @param unreachable cells to hydrate that no safe hole can reach (or beyond the hole limit)
	 */
	public record Plan(List<int[]> holes, boolean[] hydrated, boolean[] covered, int needed, int unreachable) {
	}

	/**
	 * Hydration and water holes.
	 *
	 * @param need     cells that must end up hydrated (soil to till, farmland)
	 * @param irrigate false: no hole is planned, only the current hydration is computed
	 * @param maxHoles new holes at most
	 */
	public static Plan irrigation(Grid grid, boolean[] need, boolean irrigate, int maxHoles) {
		int size = grid.size();
		boolean[] hydrated = new boolean[size];
		for (int z = 0; z < grid.depth; z++) {
			for (int x = 0; x < grid.width; x++) {
				int wy = grid.water[grid.index(x, z)];
				if (wy != UNKNOWN) {
					mark(grid, hydrated, x, z, wy);
				}
			}
		}
		boolean[] covered = hydrated.clone();
		int needed = 0;
		for (int i = 0; i < size; i++) {
			if (need[i]) {
				needed++;
			}
		}
		List<int[]> holes = new ArrayList<>();
		if (irrigate && maxHoles > 0) {
			boolean[] taken = new boolean[size];
			lattice(grid, need, covered, taken, holes, maxHoles);
			greedy(grid, need, covered, taken, holes, maxHoles);
		}
		int unreachable = 0;
		for (int i = 0; i < size; i++) {
			if (need[i] && !covered[i]) {
				unreachable++;
			}
		}
		return new Plan(holes, hydrated, covered, needed, unreachable);
	}

	/** Regular holes 9 blocks apart, centred on the bounding box of the zone cells. */
	private static void lattice(Grid grid, boolean[] need, boolean[] covered, boolean[] taken, List<int[]> holes, int maxHoles) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int z = 0; z < grid.depth; z++) {
			for (int x = 0; x < grid.width; x++) {
				if (grid.zone[grid.index(x, z)]) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minZ = Math.min(minZ, z);
					maxZ = Math.max(maxZ, z);
				}
			}
		}
		if (minX > maxX) {
			return;
		}
		int[] xs = latticeLine(minX, maxX);
		int[] zs = latticeLine(minZ, maxZ);
		for (int lz : zs) {
			for (int lx : xs) {
				if (holes.size() >= maxHoles) {
					return;
				}
				int bestX = 0;
				int bestZ = 0;
				int bestGain = 0;
				for (int r = 0; r <= SHIFT && bestGain == 0; r++) {
					for (int dz = -r; dz <= r; dz++) {
						for (int dx = -r; dx <= r; dx++) {
							if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
								continue;
							}
							int x = lx + dx;
							int z = lz + dz;
							if (!grid.inside(x, z) || !candidate(grid, taken, grid.index(x, z))) {
								continue;
							}
							int gain = gain(grid, need, covered, x, z);
							if (gain > bestGain) {
								bestGain = gain;
								bestX = x;
								bestZ = z;
							}
						}
					}
				}
				if (bestGain > 0) {
					place(grid, need, covered, taken, holes, bestX, bestZ);
				}
			}
		}
	}

	/** Lattice coordinates covering [min, max] with the fewest holes, centred (each hole covers 9 cells). */
	static int[] latticeLine(int min, int max) {
		int span = max - min + 1;
		int count = (span + SPACING - 1) / SPACING;
		int excess = count * SPACING - span;
		int start = min + RANGE - excess / 2;
		int[] line = new int[count];
		for (int i = 0; i < count; i++) {
			line[i] = Math.max(min, Math.min(max, start + i * SPACING));
		}
		return line;
	}

	/** Cells the lattice missed (unsafe spots, slopes): the safe hole covering the most of them, row by row. */
	private static void greedy(Grid grid, boolean[] need, boolean[] covered, boolean[] taken, List<int[]> holes, int maxHoles) {
		int budget = GREEDY_BUDGET;
		for (int z = 0; z < grid.depth; z++) {
			for (int x = 0; x < grid.width; x++) {
				int cell = grid.index(x, z);
				if (!need[cell] || covered[cell]) {
					continue;
				}
				if (holes.size() >= maxHoles || budget <= 0) {
					return;
				}
				int cy = grid.ground[cell];
				int bestX = 0;
				int bestZ = 0;
				int bestGain = 0;
				int bestReach = Integer.MIN_VALUE;
				for (int dz = -RANGE; dz <= RANGE; dz++) {
					for (int dx = -RANGE; dx <= RANGE; dx++) {
						int hx = x + dx;
						int hz = z + dz;
						if (!grid.inside(hx, hz)) {
							continue;
						}
						int h = grid.index(hx, hz);
						int hy = grid.ground[h];
						if (!candidate(grid, taken, h) || (hy != cy && hy != cy + 1)) {
							continue;
						}
						int gain = gain(grid, need, covered, hx, hz);
						budget--;
						// Ties: towards the cells not read yet (rows below, columns to the right).
						int reach = dx + dz;
						if (gain > bestGain || (gain == bestGain && gain > 0 && reach > bestReach)) {
							bestGain = gain;
							bestReach = reach;
							bestX = hx;
							bestZ = hz;
						}
					}
				}
				if (bestGain > 0) {
					place(grid, need, covered, taken, holes, bestX, bestZ);
				}
			}
		}
	}

	private static boolean candidate(Grid grid, boolean[] taken, int cell) {
		return grid.zone[cell] && grid.hole[cell] && !taken[cell] && grid.water[cell] == UNKNOWN && grid.ground[cell] != UNKNOWN;
	}

	/** Cells to hydrate a hole at (x, z) would newly hydrate (its own cell included). */
	private static int gain(Grid grid, boolean[] need, boolean[] covered, int x, int z) {
		int hy = grid.ground[grid.index(x, z)];
		int gain = 0;
		for (int dz = -RANGE; dz <= RANGE; dz++) {
			for (int dx = -RANGE; dx <= RANGE; dx++) {
				int cx = x + dx;
				int cz = z + dz;
				if (!grid.inside(cx, cz)) {
					continue;
				}
				int cell = grid.index(cx, cz);
				if (need[cell] && !covered[cell] && hydrates(hy, grid.ground[cell])) {
					gain++;
				}
			}
		}
		return gain;
	}

	private static void place(Grid grid, boolean[] need, boolean[] covered, boolean[] taken, List<int[]> holes, int x, int z) {
		int cell = grid.index(x, z);
		taken[cell] = true;
		covered[cell] = true;
		mark(grid, covered, x, z, grid.ground[cell]);
		holes.add(new int[]{x, z});
	}

	/** Marks the cells water at height {@code wy} in column (x, z) hydrates. */
	private static void mark(Grid grid, boolean[] target, int x, int z, int wy) {
		for (int dz = -RANGE; dz <= RANGE; dz++) {
			for (int dx = -RANGE; dx <= RANGE; dx++) {
				int cx = x + dx;
				int cz = z + dz;
				if (!grid.inside(cx, cz)) {
					continue;
				}
				int cell = grid.index(cx, cz);
				if (hydrates(wy, grid.ground[cell])) {
					target[cell] = true;
				}
			}
		}
	}

	/** Water at {@code waterY} hydrates farmland at {@code groundY}: same level or one above (vanilla box). */
	public static boolean hydrates(int waterY, int groundY) {
		return waterY != UNKNOWN && groundY != UNKNOWN && (waterY == groundY || waterY == groundY + 1);
	}

	// ------------------------------------------------------------------ levelling

	/** A block to place ({@code fill}: on top of the pit's floor) or to remove (the bump's top block). */
	public record LevelJob(int x, int z, boolean fill) {
	}

	/**
	 * 1-block pits and bumps of natural soil: a pit is a cell one block lower than at least 3 of its 4 neighbours, none
	 * of them lower than it; a bump is one block higher than at least 3 of them, none higher. Neighbours must be natural
	 * soil or farmland, read, dry and not reserved.
	 *
	 * @param reserved cells left alone (water holes, planned or dug)
	 * @param max      jobs at most
	 */
	public static List<LevelJob> levelJobs(Grid grid, boolean[] reserved, int max) {
		List<LevelJob> jobs = new ArrayList<>();
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int z = 0; z < grid.depth && jobs.size() < max; z++) {
			for (int x = 0; x < grid.width && jobs.size() < max; x++) {
				int cell = grid.index(x, z);
				int y = grid.ground[cell];
				if (!grid.zone[cell] || !grid.level[cell] || reserved[cell] || grid.water[cell] != UNKNOWN || y == UNKNOWN) {
					continue;
				}
				int up = 0;
				int down = 0;
				int lowest = Integer.MAX_VALUE;
				int highest = Integer.MIN_VALUE;
				boolean ok = true;
				for (int[] side : sides) {
					int nx = x + side[0];
					int nz = z + side[1];
					if (!grid.inside(nx, nz)) {
						ok = false;
						break;
					}
					int n = grid.index(nx, nz);
					int ny = grid.ground[n];
					if (ny == UNKNOWN || grid.water[n] != UNKNOWN || reserved[n] || !(grid.level[n] || grid.farmland[n])) {
						ok = false;
						break;
					}
					if (ny == y + 1) {
						up++;
					} else if (ny == y - 1) {
						down++;
					}
					lowest = Math.min(lowest, ny);
					highest = Math.max(highest, ny);
				}
				if (!ok) {
					continue;
				}
				if (up >= 3 && lowest >= y) {
					jobs.add(new LevelJob(x, z, true));
				} else if (down >= 3 && highest <= y) {
					jobs.add(new LevelJob(x, z, false));
				}
			}
		}
		return jobs;
	}
}
