package dev.guildy.villager;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure geometry of a gathering pit (no world access: unit tested): a square of {@code side} × {@code side} columns
 * (minimum corner {@code x0, z0}) dug level by level from the ground at {@code top}: level k removes the blocks at
 * {@code y = top + 1 - k}, so after k levels the floor of the pit is at {@code top - k}.
 * <p>
 * <b>Way out.</b> A ramp runs along the inside of the walls: the ring of border columns, in order from the
 * {@code entry} column (clockwise seen from above, or counter-clockwise). Ramp column i is only dug down to level i, so
 * it is a step whose floor is at {@code top - i}: one block lower than the step before it, one block higher than the
 * step after it, and the open sky above (every block above it is dug). From the floor at depth d the step d is level with
 * the floor, then the steps climb one block at a time to the entry at ground level: the vanilla ground navigation walks
 * it both ways. The ramp needs one ring column per level, so a pit is at most {@link #maxDepthFor} levels deep.
 * <p>
 * Every other column (and the steps deeper than the current level) is dug down to {@link #depth}.
 */
public final class PitPlan {
	private final int x0;
	private final int z0;
	private final int side;
	private final int top;
	private final int depth;
	private final int entry;
	private final boolean clockwise;
	/** Ramp step of each footprint cell (index dz * side + dx), -1 = not a step. */
	private final int[] steps;
	/** Footprint cell of each ramp step. */
	private final int[] stepCells;

	public PitPlan(int x0, int z0, int side, int top, int depth, int entry, boolean clockwise) {
		this.x0 = x0;
		this.z0 = z0;
		this.side = Math.max(3, side);
		this.top = top;
		this.depth = Math.max(0, Math.min(depth, maxDepthFor(this.side)));
		int ring = ringLength(this.side);
		this.entry = Math.floorMod(entry, ring);
		this.clockwise = clockwise;
		this.steps = new int[this.side * this.side];
		java.util.Arrays.fill(steps, -1);
		this.stepCells = new int[this.depth + 1];
		for (int i = 0; i <= this.depth; i++) {
			int index = Math.floorMod(clockwise ? this.entry + i : this.entry - i, ring);
			int[] local = ringLocal(this.side, index);
			int cell = local[1] * this.side + local[0];
			steps[cell] = i;
			stepCells[i] = cell;
		}
	}

	/** Columns of the ring of border columns of a square of {@code side} (4 · (side − 1)). */
	public static int ringLength(int side) {
		return 4 * (Math.max(3, side) - 1);
	}

	/** Deepest pit of a side: the ramp needs one distinct ring column per level, plus the entry. */
	public static int maxDepthFor(int side) {
		return ringLength(side) - 1;
	}

	/**
	 * Local (dx, dz) of ring column {@code index}: from the minimum corner along +x (north edge), then +z (east edge),
	 * then −x (south edge), then −z (west edge): clockwise seen from above.
	 */
	public static int[] ringLocal(int side, int index) {
		int s = Math.max(3, side) - 1;
		int i = Math.floorMod(index, 4 * s);
		if (i < s) {
			return new int[]{i, 0};
		}
		if (i < 2 * s) {
			return new int[]{s, i - s};
		}
		if (i < 3 * s) {
			return new int[]{s - (i - 2 * s), s};
		}
		return new int[]{0, s - (i - 3 * s)};
	}

	/** Outward direction (dx, dz) of a ring column (corners: along the edge they start). */
	public static int[] outward(int side, int index) {
		int s = Math.max(3, side) - 1;
		int i = Math.floorMod(index, 4 * s);
		if (i < s) {
			return new int[]{0, -1};
		}
		if (i < 2 * s) {
			return new int[]{1, 0};
		}
		if (i < 3 * s) {
			return new int[]{0, 1};
		}
		return new int[]{-1, 0};
	}

	public int x0() {
		return x0;
	}

	public int z0() {
		return z0;
	}

	public int side() {
		return side;
	}

	public int top() {
		return top;
	}

	public int depth() {
		return depth;
	}

	public int entry() {
		return entry;
	}

	public boolean clockwise() {
		return clockwise;
	}

	public boolean contains(int x, int z) {
		return x >= x0 && x < x0 + side && z >= z0 && z < z0 + side;
	}

	/** Footprint cell index of a column (dz · side + dx), -1 outside. */
	public int cell(int x, int z) {
		return contains(x, z) ? (z - z0) * side + (x - x0) : -1;
	}

	public int cellX(int cell) {
		return x0 + cell % side;
	}

	public int cellZ(int cell) {
		return z0 + cell / side;
	}

	public int cells() {
		return side * side;
	}

	/** Ramp step of the column (0 = entry, at ground level), -1 when it is not a step. */
	public int step(int cell) {
		return cell < 0 || cell >= steps.length ? -1 : steps[cell];
	}

	/** Deepest level dug in the column: its step index for a ramp column, the depth of the pit otherwise. */
	public int limit(int cell) {
		int step = step(cell);
		return step >= 0 ? Math.min(step, depth) : depth;
	}

	/** Feet position of a villager standing on ramp step {@code i} (its floor is at {@code top - i}). */
	public BlockPos stepFeet(int i) {
		int cell = stepCells[Math.max(0, Math.min(i, depth))];
		return new BlockPos(cellX(cell), top - Math.max(0, Math.min(i, depth)) + 1, cellZ(cell));
	}

	/** Height of the blocks dug at level k. */
	public int levelY(int k) {
		return top + 1 - k;
	}

	/** Feet height on the floor of the pit after k levels. */
	public int floorFeet(int k) {
		return top + 1 - k;
	}

	/**
	 * Blocks dug at level {@code k} (1..depth), in dig order: from the step above this level (where the villager comes
	 * down) outwards, row by row on equal distance.
	 */
	public List<BlockPos> level(int k) {
		List<BlockPos> blocks = new ArrayList<>();
		if (k < 1 || k > depth) {
			return blocks;
		}
		int y = levelY(k);
		int from = stepCells[k - 1];
		int fx = cellX(from);
		int fz = cellZ(from);
		List<Integer> cells = new ArrayList<>();
		for (int cell = 0; cell < cells(); cell++) {
			if (limit(cell) >= k) {
				cells.add(cell);
			}
		}
		cells.sort(Comparator.<Integer>comparingInt(cell -> Math.abs(cellX(cell) - fx) + Math.abs(cellZ(cell) - fz)).thenComparingInt(cell -> cell));
		for (int cell : cells) {
			blocks.add(new BlockPos(cellX(cell), y, cellZ(cell)));
		}
		return blocks;
	}

	/** Blocks of the pit's volume (every level), level by level: what it will have dug once finished. */
	public List<BlockPos> volume() {
		List<BlockPos> blocks = new ArrayList<>();
		for (int k = 1; k <= depth; k++) {
			blocks.addAll(level(k));
		}
		return blocks;
	}

	/** The entry column and the column just outside it (where the villager walks in from). */
	public BlockPos entryOutside() {
		int[] local = ringLocal(side, entry);
		int[] out = outward(side, entry);
		return new BlockPos(x0 + local[0] + out[0], top + 1, z0 + local[1] + out[1]);
	}

	/** True when (x, y, z) is part of the pit's volume (dug, or to dig). */
	public boolean inVolume(int x, int y, int z) {
		int cell = cell(x, z);
		if (cell < 0) {
			return false;
		}
		int k = top + 1 - y;
		return k >= 1 && k <= limit(cell);
	}
}
