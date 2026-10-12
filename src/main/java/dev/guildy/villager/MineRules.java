package dev.guildy.villager;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.TreeSet;
import java.util.function.LongPredicate;

/**
 * Pure rules of real mining (unit tested on fake grids): what may be broken (never a non-natural block, never another
 * guild's land, never a natural block carrying a non-natural one, never into water or lava without sealing it first),
 * floors to fill, ore veins and wanted blocks to follow (also a little outside the zone), what a miner can reach, the
 * choice of the staircase orientation and the gallery levels.
 */
public final class MineRules {
	/** Faces checked for fluids before breaking a block (the block above is checked apart: it is never sealed). */
	private static final int[][] SEALED_FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
	private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}, {0, 1, 0}};
	/** {@link Targets#key} of every ore (any ore joins a vein). */
	public static final int ORE_KEY = 1;
	/** Eye height used for reach checks (villager). */
	private static final double EYE = 1.5;

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

	/**
	 * What the miners collect apart from the rock they dig through: 0 = nothing special, {@link #ORE_KEY} = an ore,
	 * any other value = a wanted block ("objectifs prioritaires"; one value per kind of block). A vein is a connected
	 * cluster of blocks of the same key.
	 */
	public interface Targets {
		int key(int x, int y, int z);
	}

	/** Box where veins are followed: the zone square widened by a margin, between two heights. */
	public record Region(int minX, int minZ, int maxX, int maxZ, int minY, int maxY) {
		public boolean contains(int x, int y, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
		}

		/** {@code bounds} = {minX, minZ, maxX, maxZ} widened by {@code margin} blocks. */
		public static Region around(int[] bounds, int margin, int minY, int maxY) {
			int m = Math.max(0, margin);
			return new Region(bounds[0] - m, bounds[1] - m, bounds[2] + m, bounds[3] + m, minY, maxY);
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

	/** Vein blocks around the tunnels: same rules, but never next to a fluid (no sealing) nor under a falling block. */
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

	/** A face of the block touches the open (air, torch...): it can be seen and reached by a pickaxe. */
	public static boolean exposed(Grid grid, BlockPos pos) {
		for (int[] face : FACES) {
			if (grid.at(pos.getX() + face[0], pos.getY() + face[1], pos.getZ() + face[2]).passable()) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ veins

	/**
	 * Veins around open blocks (a freshly dug cell): target blocks touching them, then the target blocks touching those
	 * (breadth first, so each one is exposed by an open block or a previous one: a block left by a vein becomes visible
	 * once the vein is mined, an ore next to a wanted block is taken too), at most {@code max}, inside {@code region},
	 * never {@code excluded} (the mine's cells and floors), only blocks the vein rules allow (nothing next to a fluid,
	 * under a falling block or a building, in another guild's land, nor unloaded).
	 */
	public static List<BlockPos> vein(Grid grid, Targets targets, Collection<BlockPos> open, Region region, LongPredicate excluded,
			int max) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos block : open) {
			for (int[] face : FACES) {
				queue.add(block.offset(face[0], face[1], face[2]));
			}
		}
		return follow(grid, targets, queue, region, excluded, max);
	}

	/** The vein of {@code seed}: the seed, then the target blocks connected to it, breadth first. */
	public static List<BlockPos> cluster(Grid grid, Targets targets, BlockPos seed, Region region, LongPredicate excluded, int max) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(seed);
		return follow(grid, targets, queue, region, excluded, max);
	}

	private static List<BlockPos> follow(Grid grid, Targets targets, ArrayDeque<BlockPos> queue, Region region, LongPredicate excluded,
			int max) {
		List<BlockPos> found = new ArrayList<>();
		if (max <= 0) {
			return found;
		}
		LongOpenHashSet seen = new LongOpenHashSet();
		while (!queue.isEmpty() && found.size() < max) {
			BlockPos pos = queue.poll();
			long packed = pos.asLong();
			if (!seen.add(packed)) {
				continue;
			}
			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();
			if (!region.contains(x, y, z) || excluded.test(packed) || targets.key(x, y, z) == 0
					|| digVein(grid, x, y, z) != Verdict.DIG) {
				continue;
			}
			found.add(pos);
			for (int[] face : FACES) {
				queue.add(pos.offset(face[0], face[1], face[2]));
			}
		}
		return found;
	}

	/** A villager standing at {@code feet} can mine {@code block} (eye to block centre). */
	public static boolean reachable(BlockPos feet, BlockPos block, double reach) {
		double dx = feet.getX() - block.getX();
		double dy = feet.getY() + EYE - (block.getY() + 0.5);
		double dz = feet.getZ() - block.getZ();
		return dx * dx + dy * dy + dz * dz <= reach * reach;
	}

	/**
	 * Next cell of an approach tunnel from {@code from} toward {@code target}: one block along the axis where the target
	 * is farther, at the same height (null when already in the target's column).
	 */
	public static @Nullable BlockPos approach(BlockPos from, BlockPos target) {
		int dx = target.getX() - from.getX();
		int dz = target.getZ() - from.getZ();
		if (dx == 0 && dz == 0) {
			return null;
		}
		if (Math.abs(dx) >= Math.abs(dz)) {
			return from.offset(Integer.signum(dx), 0, 0);
		}
		return from.offset(0, 0, Integer.signum(dz));
	}

	/** Blocks of (x, z) beyond the zone square (0 inside): veins are followed outside up to a limit. */
	public static int outside(int x, int z, int[] bounds) {
		int dx = Math.max(0, Math.max(bounds[0] - x, x - bounds[2]));
		int dz = Math.max(0, Math.max(bounds[1] - z, z - bounds[3]));
		return Math.max(dx, dz);
	}

	// ------------------------------------------------------------------ orientation

	/**
	 * How good a staircase is: steps diggable before the first obstacle (more is better), non-natural blocks next to its
	 * first steps or blocking its outer lane (fewer: stay away from buildings), floor holes to fill (fewer), main tunnel
	 * length (longer).
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
	 * Scores the staircase of a layout over its first {@code maxSteps} steps (the inner lane must be diggable, the outer
	 * lane should be); {@code nearSteps}: first steps checked for neighbouring buildings. {@code levelY}: a level whose
	 * main tunnel length is compared last (no level: 0).
	 */
	public static Score score(Grid grid, MineLayout layout, int nearSteps, int maxSteps, @Nullable Integer levelY) {
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
			FloorCheck check = floorOf(grid, cell);
			if (check.fix() == FloorFix.BLOCKED || check.fix() == FloorFix.WAIT) {
				clear = k;
				break;
			}
			if (check.fix() != FloorFix.OK) {
				holes++;
			}
			for (MineLayout.Cell extra : layout.outer(k)) {
				FloorCheck floor = floorOf(grid, extra);
				if (!stepDiggable(grid, extra) || floor.fix() == FloorFix.BLOCKED || floor.fix() == FloorFix.WAIT) {
					near++;
				} else if (floor.fix() != FloorFix.OK) {
					holes++;
				}
			}
			if (k < nearSteps) {
				for (MineLayout.Cell part : layout.step(k)) {
					near += buildingsAround(grid, layout, part);
				}
			}
		}
		MineLayout.Level level = levelY == null ? null : layout.level(levelY);
		return new Score(clear, near, holes, level == null ? 0 : level.mainLength());
	}

	public static Score score(Grid grid, MineLayout layout, int nearSteps, int maxSteps) {
		return score(grid, layout, nearSteps, maxSteps, null);
	}

	private static FloorCheck floorOf(Grid grid, MineLayout.Cell cell) {
		BlockPos floor = cell.floor();
		return floor(grid, floor.getX(), floor.getY(), floor.getZ());
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

	/** Default target level of an objective: deep for ores, a few blocks under the fanion for stone. */
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
	 * Level for a wished height, kept between {@link #minLevel} and {@link #maxLevel}; a fanion planted too deep for
	 * that (right above the bedrock) digs at its own height.
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

	/** Levels of a mine (feet heights, highest first) and the lowest one on the grid of the target. */
	public record LevelPlan(List<Integer> levels, int gridBottom) {
		public boolean isEmpty() {
			return levels.isEmpty();
		}

		/** Deepest level: the stairs go down to it. */
		public int lowest() {
			return levels.getLast();
		}
	}

	/**
	 * Gallery levels of a mine (feet heights, highest first): every {@code spacing} blocks on the grid of the target
	 * level, from the highest one allowed ({@code hi}) down to {@code bottom} (never under the target unless asked, never
	 * under {@code lo}). {@code extra} levels between lo and hi are added when they stay at least {@code minGap} blocks
	 * from the others (the level of a mine planned before levels existed, the one where blocked stairs stop).
	 */
	public static LevelPlan levels(int target, int spacing, int lo, int hi, int bottom, Collection<Integer> extra, int minGap) {
		if (lo > hi) {
			return new LevelPlan(List.of(), lo);
		}
		int step = Math.max(1, spacing);
		int top = target + Math.floorDiv(hi - target, step) * step;
		int b = Math.max(lo, Math.min(Math.min(bottom, target), top));
		TreeSet<Integer> levels = new TreeSet<>();
		int gridBottom = top;
		for (int y = top; y >= b; y -= step) {
			levels.add(y);
			gridBottom = y;
		}
		for (Integer y : extra) {
			if (y == null || y < lo || y > hi || levels.contains(y)) {
				continue;
			}
			Integer above = levels.ceiling(y);
			Integer below = levels.floor(y);
			if ((above == null || above - y >= minGap) && (below == null || y - below >= minGap)) {
				levels.add(y);
			}
		}
		return new LevelPlan(new ArrayList<>(levels.descendingSet()), gridBottom);
	}

	/** Next level under {@code gridBottom} on the grid of the levels, if the world (and the stairs) go that deep. */
	public static OptionalInt deeper(int gridBottom, int spacing, int lo) {
		int next = gridBottom - Math.max(1, spacing);
		return next >= lo ? OptionalInt.of(next) : OptionalInt.empty();
	}
}
