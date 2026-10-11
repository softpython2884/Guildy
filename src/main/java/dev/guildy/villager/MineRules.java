package dev.guildy.villager;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Pure rules of real mining (unit tested on fake grids): what may be broken (never a non-natural block, never another
 * guild's land, never a natural block carrying a non-natural one, never into water or lava without sealing it first),
 * floors to fill, ore veins to follow, the choice of the staircase orientation and the gallery levels.
 */
public final class MineRules {
	/** Faces checked for fluids before breaking a block (the block above is checked apart: it is never sealed). */
	private static final int[][] SEALED_FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
	private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}, {0, 1, 0}};

	private MineRules() {
	}

	/** Read access to the world (or a fake grid in tests). */
	public interface Grid {
		MineBlock at(int x, int y, int z);

		/** In another guild's territory. */
		boolean foreign(int x, int y, int z);

		/** The top face can carry a villager safely (full, not magma...). */
		boolean sturdy(int x, int y, int z);

		default MineBlock at(BlockPos pos) {
			return at(pos.getX(), pos.getY(), pos.getZ());
		}
	}

	public enum Verdict {
		/** Nothing to break: walkable. */
		CLEAR,
		/** May be broken (after sealing the listed fluids). */
		DIG,
		/** Not loaded: try again later. */
		WAIT,
		WATER,
		LAVA,
		/** Not a natural block (player block, block entity, bedrock...). */
		BUILDING,
		/** Natural block carrying a non-natural one. */
		UNDERMINE,
		FOREIGN,
		/** Under a falling block (vein blocks only). */
		CAVE_IN;

		public boolean blocked() {
			return this != CLEAR && this != DIG && this != WAIT;
		}
	}

	/** Verdict for one block; {@code seal}: fluid blocks next to it to fill before breaking it. */
	public record Check(Verdict verdict, List<BlockPos> seal) {
		static Check of(Verdict verdict) {
			return new Check(verdict, List.of());
		}
	}

	public enum FloorFix {
		/** Solid floor. */
		OK,
		/** Hole or fluid: place a block. */
		FILL,
		/** Unsafe natural block (magma...): break it, then place a block. */
		REPLACE,
		BLOCKED,
		WAIT
	}

	public record FloorCheck(FloorFix fix, Verdict reason) {
	}

	// ------------------------------------------------------------------ blocks

	/** May the block at (x, y, z) of a cell be broken? */
	public static Check dig(Grid grid, int x, int y, int z) {
		if (grid.foreign(x, y, z)) {
			return Check.of(Verdict.FOREIGN);
		}
		switch (grid.at(x, y, z)) {
			case AIR, FIXTURE -> {
				return Check.of(Verdict.CLEAR);
			}
			case UNLOADED -> {
				return Check.of(Verdict.WAIT);
			}
			case WATER, WET -> {
				return Check.of(Verdict.WATER);
			}
			case LAVA -> {
				return Check.of(Verdict.LAVA);
			}
			case PROTECTED -> {
				return Check.of(Verdict.BUILDING);
			}
			default -> {
			}
		}
		// Never undermine: what stands on the block must be natural (a falling block simply comes down into the tunnel).
		switch (grid.at(x, y + 1, z)) {
			case WATER, WET -> {
				return Check.of(Verdict.WATER);
			}
			case LAVA -> {
				return Check.of(Verdict.LAVA);
			}
			case FIXTURE, PROTECTED -> {
				return Check.of(Verdict.UNDERMINE);
			}
			case UNLOADED -> {
				return Check.of(Verdict.WAIT);
			}
			default -> {
			}
		}
		List<BlockPos> seal = new ArrayList<>(0);
		for (int[] face : SEALED_FACES) {
			int nx = x + face[0];
			int ny = y + face[1];
			int nz = z + face[2];
			MineBlock side = grid.at(nx, ny, nz);
			if (side == MineBlock.UNLOADED) {
				return Check.of(Verdict.WAIT);
			}
			if (side == MineBlock.WET) {
				return Check.of(Verdict.WATER);
			}
			if (side.liquid()) {
				if (grid.foreign(nx, ny, nz)) {
					return Check.of(Verdict.FOREIGN);
				}
				seal.add(new BlockPos(nx, ny, nz));
			}
		}
		return new Check(Verdict.DIG, seal);
	}

	/** Vein blocks around the tunnel: same rules, but never next to a fluid (no sealing) nor under a falling block. */
	public static Verdict digVein(Grid grid, int x, int y, int z) {
		Check check = dig(grid, x, y, z);
		if (check.verdict() != Verdict.DIG) {
			return check.verdict();
		}
		if (!check.seal().isEmpty()) {
			return grid.at(check.seal().getFirst()) == MineBlock.LAVA ? Verdict.LAVA : Verdict.WATER;
		}
		return grid.at(x, y + 1, z) == MineBlock.FALLING ? Verdict.CAVE_IN : Verdict.DIG;
	}

	/** Floor block under a cell (x, y, z = the floor position). */
	public static FloorCheck floor(Grid grid, int x, int y, int z) {
		if (grid.foreign(x, y, z)) {
			return new FloorCheck(FloorFix.BLOCKED, Verdict.FOREIGN);
		}
		MineBlock block = grid.at(x, y, z);
		if (block == MineBlock.UNLOADED) {
			return new FloorCheck(FloorFix.WAIT, Verdict.WAIT);
		}
		if (block == MineBlock.WET) {
			return new FloorCheck(FloorFix.BLOCKED, Verdict.WATER);
		}
		if (!block.liquid() && grid.sturdy(x, y, z)) {
			return new FloorCheck(FloorFix.OK, Verdict.CLEAR);
		}
		return switch (block) {
			case AIR, WATER, LAVA -> new FloorCheck(FloorFix.FILL, Verdict.CLEAR);
			case NATURAL, FALLING, ORE -> new FloorCheck(FloorFix.REPLACE, Verdict.CLEAR);
			default -> new FloorCheck(FloorFix.BLOCKED, Verdict.BUILDING);
		};
	}

	/**
	 * Ore blocks connected to the walls / ceiling of a dug cell (breadth first, so each one is exposed by the previous
	 * ones), at most {@code max}, within {@code reach} blocks of the cell, never in {@code excluded} (the tunnels and
	 * their floors).
	 */
	public static List<BlockPos> vein(Grid grid, MineLayout.Cell cell, Set<Long> excluded, int max, int reach) {
		List<BlockPos> found = new ArrayList<>();
		if (max <= 0) {
			return found;
		}
		BlockPos feet = cell.feet();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (int i = 0; i < cell.height(); i++) {
			BlockPos block = feet.above(i);
			queue.add(block.east());
			queue.add(block.west());
			queue.add(block.south());
			queue.add(block.north());
		}
		queue.add(cell.top().above());
		Set<Long> seen = new HashSet<>();
		while (!queue.isEmpty() && found.size() < max) {
			BlockPos pos = queue.poll();
			if (!seen.add(pos.asLong()) || excluded.contains(pos.asLong())) {
				continue;
			}
			if (Math.abs(pos.getX() - feet.getX()) > reach || Math.abs(pos.getZ() - feet.getZ()) > reach
					|| pos.getY() < feet.getY() - reach || pos.getY() > cell.top().getY() + reach) {
				continue;
			}
			if (grid.at(pos) != MineBlock.ORE) {
				continue;
			}
			found.add(pos);
			for (int[] face : FACES) {
				queue.add(pos.offset(face[0], face[1], face[2]));
			}
		}
		return found;
	}

	// ------------------------------------------------------------------ orientation

	/**
	 * How good a staircase is: steps diggable before the first obstacle (more is better), non-natural blocks next to its
	 * first steps (fewer: stay away from buildings), floor holes to fill (fewer), main tunnel length (longer).
	 */
	public record Score(int clearSteps, int buildingsNear, int holes, int mainLength) {
		public boolean betterThan(Score other) {
			if (clearSteps != other.clearSteps) {
				return clearSteps > other.clearSteps;
			}
			if (buildingsNear != other.buildingsNear) {
				return buildingsNear < other.buildingsNear;
			}
			if (holes != other.holes) {
				return holes < other.holes;
			}
			return mainLength > other.mainLength;
		}
	}

	/**
	 * Scores the staircase of a layout over its first {@code maxSteps} steps; {@code nearSteps}: first steps checked for
	 * neighbouring buildings.
	 */
	public static Score score(Grid grid, MineLayout layout, int nearSteps, int maxSteps) {
		List<MineLayout.Cell> stairs = layout.stairs();
		int steps = Math.min(stairs.size(), Math.max(0, maxSteps));
		int clear = steps;
		int near = 0;
		int holes = 0;
		for (int k = 0; k < steps; k++) {
			MineLayout.Cell cell = stairs.get(k);
			if (!stepDiggable(grid, cell)) {
				clear = k;
				break;
			}
			BlockPos floor = cell.floor();
			FloorCheck check = floor(grid, floor.getX(), floor.getY(), floor.getZ());
			if (check.fix() == FloorFix.BLOCKED || check.fix() == FloorFix.WAIT) {
				clear = k;
				break;
			}
			if (check.fix() != FloorFix.OK) {
				holes++;
			}
			if (k < nearSteps) {
				near += buildingsAround(grid, layout, cell);
			}
		}
		return new Score(clear, near, holes, layout.mainLength());
	}

	/** Every block of the step can be dug top-down without sealing anything. */
	private static boolean stepDiggable(Grid grid, MineLayout.Cell cell) {
		for (int i = cell.height() - 1; i >= 0; i--) {
			BlockPos block = cell.feet().above(i);
			Check check = dig(grid, block.getX(), block.getY(), block.getZ());
			if (check.verdict() == Verdict.CLEAR) {
				continue;
			}
			if (check.verdict() != Verdict.DIG || !check.seal().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static int buildingsAround(Grid grid, MineLayout layout, MineLayout.Cell cell) {
		int count = 0;
		for (int i = 0; i < cell.height(); i++) {
			BlockPos block = cell.feet().above(i);
			for (BlockPos side : new BlockPos[]{block.east(), block.west(), block.south(), block.north()}) {
				if (layout.inStructure(side)) {
					continue;
				}
				MineBlock kind = grid.at(side);
				if (kind == MineBlock.FIXTURE || kind == MineBlock.PROTECTED || kind == MineBlock.WET) {
					count++;
				}
			}
		}
		return count;
	}

	// ------------------------------------------------------------------ levels

	/** Default gallery level of an objective: deep for ores, a few blocks under the fanion for stone. */
	public static int defaultTarget(ZoneObjective objective, int fanionY, int oresY, int mixedY, int stoneDepth) {
		return switch (objective) {
			case ORES -> oresY;
			case STONE -> fanionY - stoneDepth;
			default -> mixedY;
		};
	}

	/** Highest gallery level: {@code minDepth} blocks under the fanion (no trench in the surface). */
	public static int maxLevel(int fanionY, int minDepth) {
		return fanionY - Math.max(0, minDepth);
	}

	/** Lowest gallery level: 5 blocks above the bottom of the world (bedrock). */
	public static int minLevel(int worldMinY) {
		return worldMinY + 5;
	}

	/**
	 * Gallery level for a wished height, kept between {@link #minLevel} and {@link #maxLevel}; a fanion planted too
	 * deep for that (right above the bedrock) digs at its own height.
	 */
	public static int clampLevel(int wanted, int fanionY, int worldMinY, int minDepth) {
		int lo = minLevel(worldMinY);
		int hi = maxLevel(fanionY, minDepth);
		if (hi < lo) {
			return fanionY;
		}
		return Math.max(lo, Math.min(hi, wanted));
	}

	/** Lowest level the stairs can reach when step {@code blockedAt} cannot be dug (-1 = not blocked). */
	public static int lowestReachable(int fanionY, int blockedAt) {
		return blockedAt < 0 ? Integer.MIN_VALUE : fanionY - blockedAt;
	}

	/**
	 * Next level once a level is finished: {@code target} ± k × spacing (deeper first), within [lo, hi], not finished
	 * yet; empty when there is none.
	 */
	public static OptionalInt nextLevel(int target, Collection<Integer> finished, int spacing, int lo, int hi) {
		if (lo > hi) {
			return OptionalInt.empty();
		}
		int step = Math.max(1, spacing);
		int base = Math.max(lo, Math.min(hi, target));
		for (int k = 0; ; k++) {
			int down = base - k * step;
			int up = base + k * step;
			if (down < lo && up > hi) {
				return OptionalInt.empty();
			}
			if (down >= lo && !finished.contains(down)) {
				return OptionalInt.of(down);
			}
			if (k > 0 && up <= hi && !finished.contains(up)) {
				return OptionalInt.of(up);
			}
		}
	}
}
