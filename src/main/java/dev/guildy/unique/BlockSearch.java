package dev.guildy.unique;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Bounded block searches of the mining powers (unit-tested): connected blocks (tree logs, ore veins), leaves around a
 * tree, the 3x3 plane of the excavation. Pure: the world is seen through predicates.
 */
public final class BlockSearch {
	private BlockSearch() {
	}

	/**
	 * Blocks connected to {@code origin} (26 neighbours, origin excluded) that match, breadth first (nearest first), at
	 * most {@code max}.
	 *
	 * @param minY lowest y explored (the search never goes below the cut, e.g. the stump of a tree)
	 */
	public static List<BlockPos> connected(BlockPos origin, int max, int minY, Predicate<BlockPos> matches) {
		List<BlockPos> found = new ArrayList<>();
		if (max <= 0) {
			return found;
		}
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(origin.asLong());
		queue.add(origin);
		while (!queue.isEmpty() && found.size() < max) {
			BlockPos current = queue.poll();
			for (int dx = -1; dx <= 1 && found.size() < max; dx++) {
				for (int dy = -1; dy <= 1 && found.size() < max; dy++) {
					for (int dz = -1; dz <= 1 && found.size() < max; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos next = current.offset(dx, dy, dz);
						if (next.getY() < minY || !seen.add(next.asLong())) {
							continue;
						}
						if (matches.test(next)) {
							found.add(next);
							queue.add(next);
						}
					}
				}
			}
		}
		return found;
	}

	/** Number of distinct positions matching around (26 neighbours of) the given blocks, counting stops at {@code enough}. */
	public static int countAround(Collection<BlockPos> blocks, int enough, Predicate<BlockPos> matches) {
		Set<Long> seen = new HashSet<>();
		int count = 0;
		for (BlockPos pos : blocks) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = pos.offset(dx, dy, dz);
						if (seen.add(next.asLong()) && matches.test(next)) {
							count++;
							if (count >= enough) {
								return count;
							}
						}
					}
				}
			}
		}
		return count;
	}

	/** The 8 positions around {@code center} in the plane perpendicular to {@code axis} (the 3x3 minus its center). */
	public static List<BlockPos> plane(BlockPos center, Direction.Axis axis) {
		List<BlockPos> out = new ArrayList<>(8);
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) {
					continue;
				}
				out.add(switch (axis) {
					case X -> center.offset(0, a, b);
					case Y -> center.offset(a, 0, b);
					case Z -> center.offset(a, b, 0);
				});
			}
		}
		return out;
	}

	/** Axis the player digs along: vertical when looking steeply up or down, else the horizontal facing. */
	public static Direction.Axis diggingAxis(float pitch, float yaw) {
		if (pitch > 50 || pitch < -50) {
			return Direction.Axis.Y;
		}
		return Direction.fromYRot(yaw).getAxis();
	}
}
