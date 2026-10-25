package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

/**
 * Pure rules of the gathering pits (unit tested on fake grids, world adapter {@link SupplyPits}): where a pit may be dug,
 * how deep, which entry, and how many blocks of the wanted kind it holds.
 * <p>
 * A site is accepted when every column of the pit and of the ring around it is allowed (claims, spawn, mine layout: see
 * {@link Grid#allowed}), loaded, nearly flat ({@code maxUneven} blocks between the lowest and the highest ground of the
 * footprint, one more for the ring), free above the ground (no tree, no overhang) and holds only natural blocks around
 * its rim (no building, fixture, fluid or block entity within one block: a pit never opens next to a player's work).
 * Its depth stops above the first level that would open into a cave or a fluid, or where a ramp step cannot be dug. The
 * score is the number of wanted blocks in the volume that will really be dug.
 */
public final class PitRules {
	/** A pit shallower than this is not worth starting. */
	public static final int MIN_DEPTH = 2;
	/** Free blocks above the ground of the pit and of its rim. */
	private static final int HEADROOM = 3;

	private PitRules() {
	}

	/** World access (or a fake grid in tests). */
	public interface Grid extends MineRules.Grid {
		/** Height of the highest block with collision of the column, {@link Integer#MIN_VALUE} when not loaded. */
		int surface(int x, int z);

		/** The column may hold this pit or its rim (never another guild's land, nor, depending on the pit, a claim...). */
		boolean allowed(int x, int z);

		/** The block is one of the wanted kind (a source of the item looked for). */
		boolean source(int x, int y, int z);
	}

	public enum Reject {
		NONE,
		UNLOADED,
		NOT_ALLOWED,
		UNEVEN,
		/** A non-natural block, a fixture, a fluid or a tree on or around the pit. */
		OBSTACLE,
		/** Not deep enough (cave, water or a building right below the ground). */
		SHALLOW
	}

	/** Result of a site check: the pit to dig ({@link #plan}) and its wanted blocks, or the reason it was rejected. */
	public record Site(int x0, int z0, int side, int top, int depth, int entry, boolean clockwise, int score, Reject reject) {
		public boolean ok() {
			return reject == Reject.NONE;
		}

		public PitPlan plan() {
			return new PitPlan(x0, z0, side, top, depth, entry, clockwise);
		}

		static Site rejected(int x0, int z0, int side, Reject reject) {
			return new Site(x0, z0, side, 0, 0, 0, true, 0, reject);
		}
	}

	/**
	 * Checks a pit of {@code side} columns at (x0, z0) at most {@code maxDepth} levels deep; the entry is the ring column
	 * nearest to ({@code fromX}, {@code fromZ}) whose outside neighbour is at most one block above or below the ground.
	 */
	public static Site evaluate(Grid grid, int x0, int z0, int side, int maxDepth, int maxUneven, int fromX, int fromZ) {
		int s = Math.max(3, side);
		// Every column of the pit and of its rim: allowed and loaded.
		int top = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int x = x0 - 1; x <= x0 + s; x++) {
			for (int z = z0 - 1; z <= z0 + s; z++) {
				if (!grid.allowed(x, z)) {
					return Site.rejected(x0, z0, s, Reject.NOT_ALLOWED);
				}
				int surface = grid.surface(x, z);
				if (surface == Integer.MIN_VALUE) {
					return Site.rejected(x0, z0, s, Reject.UNLOADED);
				}
				if (inside(x, z, x0, z0, s)) {
					top = Math.min(top, surface);
					highest = Math.max(highest, surface);
				}
			}
		}
		if (highest - top > Math.max(0, maxUneven)) {
			return Site.rejected(x0, z0, s, Reject.UNEVEN);
		}
		// Rim: not much higher than the ground (the entry must be walkable), only natural blocks, free above.
		for (int x = x0 - 1; x <= x0 + s; x++) {
			for (int z = z0 - 1; z <= z0 + s; z++) {
				int surface = grid.surface(x, z);
				boolean pit = inside(x, z, x0, z0, s);
				if (!pit && surface > top + Math.max(0, maxUneven) + 1) {
					return Site.rejected(x0, z0, s, Reject.UNEVEN);
				}
				int from = pit ? top + 1 : Math.min(surface, top) - 1;
				int to = Math.max(surface, highest) + HEADROOM;
				for (int y = from; y <= to; y++) {
					MineBlock block = grid.at(x, y, z);
					boolean above = y > surface;
					if (block == MineBlock.UNLOADED) {
						return Site.rejected(x0, z0, s, Reject.UNLOADED);
					}
					if (above ? !block.passable() || block == MineBlock.FIXTURE : !block.breakable()) {
						return Site.rejected(x0, z0, s, Reject.OBSTACLE);
					}
				}
				if (!pit && grid.at(x, surface, z).liquid()) {
					return Site.rejected(x0, z0, s, Reject.OBSTACLE);
				}
			}
		}
		int entry = entry(grid, x0, z0, s, top, fromX, fromZ);
		if (entry < 0) {
			return Site.rejected(x0, z0, s, Reject.UNEVEN);
		}
		// Depth: down to the first level that is not plain natural rock (cave, fluid), or below a ramp step that stays.
		PitPlan full = new PitPlan(x0, z0, s, top, maxDepth, entry, true);
		int depth = full.depth();
		for (int k = 1; k <= full.depth(); k++) {
			int y = full.levelY(k);
			boolean stop = false;
			for (int cell = 0; cell < full.cells() && !stop; cell++) {
				if (full.limit(cell) < k) {
					continue;
				}
				MineBlock block = grid.at(full.cellX(cell), y, full.cellZ(cell));
				if (block == MineBlock.UNLOADED) {
					return Site.rejected(x0, z0, s, Reject.UNLOADED);
				}
				if (!block.breakable() && (block != MineBlock.PROTECTED || full.step(cell) >= 0)) {
					stop = true;
				}
			}
			if (stop) {
				depth = k - 1;
				break;
			}
		}
		if (depth < MIN_DEPTH) {
			return Site.rejected(x0, z0, s, Reject.SHALLOW);
		}
		PitPlan plan = new PitPlan(x0, z0, s, top, depth, entry, true);
		int score = count(grid, plan, highest, 0);
		return new Site(x0, z0, s, top, depth, entry, true, score, Reject.NONE);
	}

	/**
	 * Wanted blocks still in the volume of a pit (the ground above {@code top} up to {@code highest} included when
	 * {@code levelsDone} is 0), below the {@code levelsDone} levels already dug.
	 */
	public static int count(Grid grid, PitPlan plan, int highest, int levelsDone) {
		int score = 0;
		if (levelsDone <= 0) {
			for (int cell = 0; cell < plan.cells(); cell++) {
				for (int y = plan.top() + 1; y <= highest; y++) {
					if (grid.at(plan.cellX(cell), y, plan.cellZ(cell)).breakable() && grid.source(plan.cellX(cell), y, plan.cellZ(cell))) {
						score++;
					}
				}
			}
		}
		for (int k = Math.max(1, levelsDone + 1); k <= plan.depth(); k++) {
			int y = plan.levelY(k);
			for (int cell = 0; cell < plan.cells(); cell++) {
				if (plan.limit(cell) >= k && grid.at(plan.cellX(cell), y, plan.cellZ(cell)).breakable()
						&& grid.source(plan.cellX(cell), y, plan.cellZ(cell))) {
					score++;
				}
			}
		}
		return score;
	}

	/**
	 * The ring column nearest to (fromX, fromZ) (corners last) whose outside neighbour stands at most one block above
	 * or below the ground: the villager walks in there. -1 when there is none.
	 */
	static int entry(Grid grid, int x0, int z0, int side, int top, int fromX, int fromZ) {
		int ring = PitPlan.ringLength(side);
		int best = -1;
		double bestScore = Double.MAX_VALUE;
		for (int index = 0; index < ring; index++) {
			int[] local = PitPlan.ringLocal(side, index);
			int[] out = PitPlan.outward(side, index);
			int ox = x0 + local[0] + out[0];
			int oz = z0 + local[1] + out[1];
			int surface = grid.surface(ox, oz);
			if (surface == Integer.MIN_VALUE || Math.abs(surface - top) > 1) {
				continue;
			}
			boolean corner = (local[0] == 0 || local[0] == side - 1) && (local[1] == 0 || local[1] == side - 1);
			double dx = ox - fromX;
			double dz = oz - fromZ;
			double score = dx * dx + dz * dz + (corner ? 1_000_000 : 0);
			if (score < bestScore) {
				bestScore = score;
				best = index;
			}
		}
		return best;
	}

	/** Remaining wanted blocks of a pit already started: see {@link #count}. */
	public static int remaining(Grid grid, PitPlan plan, int highest, int levelsDone, @Nullable int[] unused) {
		return count(grid, plan, highest, levelsDone);
	}

	private static boolean inside(int x, int z, int x0, int z0, int side) {
		return x >= x0 && x < x0 + side && z >= z0 && z < z0 + side;
	}
}
