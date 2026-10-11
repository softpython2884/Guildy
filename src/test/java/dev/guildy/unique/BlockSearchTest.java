package dev.guildy.unique;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Searches of the mining powers: connected logs / ores (bounded, never below the cut), leaves counting, 3x3 planes.
 */
class BlockSearchTest {

	@Test
	void connectedFindsTheTreeAboveTheCut() {
		Set<BlockPos> logs = new HashSet<>();
		BlockPos base = new BlockPos(0, 64, 0);
		for (int y = 0; y < 6; y++) {
			logs.add(base.above(y));
		}
		logs.add(base.offset(1, 4, 1)); // diagonal branch
		logs.add(base.offset(2, 5, 1));
		logs.add(base.below()); // root below the cut: never felled
		logs.add(new BlockPos(5, 64, 0)); // another tree, not connected
		List<BlockPos> found = BlockSearch.connected(base, 100, base.getY(), logs::contains);
		assertEquals(7, found.size(), "trunk above the cut + branch: " + found);
		assertFalse(found.contains(base), "origin excluded");
		assertFalse(found.contains(base.below()), "never below the cut");
		assertFalse(found.contains(new BlockPos(5, 64, 0)));
		assertEquals(base.above(), found.getFirst(), "nearest first");

		assertEquals(3, BlockSearch.connected(base, 3, base.getY(), logs::contains).size(), "bounded");
		assertTrue(BlockSearch.connected(base, 0, base.getY(), logs::contains).isEmpty());
		assertEquals(8, BlockSearch.connected(base, 100, Integer.MIN_VALUE, logs::contains).size(), "ore veins may go down");
	}

	@Test
	void countAroundStopsEarly() {
		Set<BlockPos> leaves = new HashSet<>();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				leaves.add(new BlockPos(x, 10, z));
			}
		}
		List<BlockPos> trunk = List.of(new BlockPos(0, 9, 0));
		assertEquals(9, BlockSearch.countAround(trunk, 100, leaves::contains));
		assertEquals(4, BlockSearch.countAround(trunk, 4, leaves::contains));
		assertEquals(0, BlockSearch.countAround(List.of(new BlockPos(50, 50, 50)), 4, leaves::contains));
	}

	@Test
	void planesAndDiggingAxis() {
		BlockPos center = new BlockPos(3, 70, -2);
		for (Direction.Axis axis : Direction.Axis.values()) {
			List<BlockPos> plane = BlockSearch.plane(center, axis);
			assertEquals(8, plane.size());
			assertEquals(8, new HashSet<>(plane).size());
			assertFalse(plane.contains(center));
			for (BlockPos pos : plane) {
				assertEquals(center.get(axis), pos.get(axis), "plane perpendicular to " + axis);
				assertTrue(Math.abs(pos.getX() - center.getX()) <= 1 && Math.abs(pos.getY() - center.getY()) <= 1
						&& Math.abs(pos.getZ() - center.getZ()) <= 1);
			}
		}
		assertEquals(Direction.Axis.Y, BlockSearch.diggingAxis(80, 0), "looking down: horizontal 3x3");
		assertEquals(Direction.Axis.Y, BlockSearch.diggingAxis(-70, 0));
		assertEquals(Direction.Axis.Z, BlockSearch.diggingAxis(10, 0), "facing south: wall perpendicular to z");
		assertEquals(Direction.Axis.X, BlockSearch.diggingAxis(0, 90), "facing west");
	}
}
