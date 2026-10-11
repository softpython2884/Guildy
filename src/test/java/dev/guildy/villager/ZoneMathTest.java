package dev.guildy.villager;

import dev.guildy.data.ChunkKey;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneMathTest {

	@Test
	void radiusZeroIsTheFanionChunk() {
		// Fanion at (5, 5): chunk (0, 0) = blocks 0..15.
		assertTrue(ZoneMath.inChunkSquare(0, 0, 5, 5, 0));
		assertTrue(ZoneMath.inChunkSquare(15, 15, 5, 5, 0));
		assertFalse(ZoneMath.inChunkSquare(16, 0, 5, 5, 0));
		assertFalse(ZoneMath.inChunkSquare(-1, 0, 5, 5, 0));
		assertFalse(ZoneMath.inChunkSquare(0, -1, 5, 5, 0));
	}

	@Test
	void radiusCountsChunksAroundTheFanionChunk() {
		// Radius 1: chunks -1..1 = blocks -16..31.
		assertTrue(ZoneMath.inChunkSquare(-16, 31, 5, 5, 1));
		assertTrue(ZoneMath.inChunkSquare(31, -16, 5, 5, 1));
		assertFalse(ZoneMath.inChunkSquare(-17, 0, 5, 5, 1));
		assertFalse(ZoneMath.inChunkSquare(32, 0, 5, 5, 1));
		// Radius 3 = 7x7 chunks.
		assertTrue(ZoneMath.inChunkSquare(5 + 3 * 16, 5 - 3 * 16, 5, 5, 3));
		assertFalse(ZoneMath.inChunkSquare(5 + 4 * 16, 5, 5, 5, 3));
		assertFalse(ZoneMath.inChunkSquare(0, 0, 5, 5, -1));
	}

	@Test
	void negativeCoordinatesUseFloorChunks() {
		// Fanion at (-1, -1): chunk (-1, -1) = blocks -16..-1.
		assertTrue(ZoneMath.inChunkSquare(-16, -1, -1, -1, 0));
		assertFalse(ZoneMath.inChunkSquare(0, -1, -1, -1, 0));
		assertFalse(ZoneMath.inChunkSquare(-17, -1, -1, -1, 0));
		assertArrayEquals(new int[]{-16, -16, -1, -1}, ZoneMath.squareBounds(-1, -1, 0));
		assertArrayEquals(new int[]{-32, -32, 15, 15}, ZoneMath.squareBounds(-1, -1, 1));
	}

	@Test
	void marginWidensTheSquare() {
		assertTrue(ZoneMath.inChunkSquare(-3.5, 8.0, 5, 5, 0, 4));
		assertTrue(ZoneMath.inChunkSquare(19.9, 8.0, 5, 5, 0, 4));
		assertFalse(ZoneMath.inChunkSquare(20.0, 8.0, 5, 5, 0, 4));
		assertFalse(ZoneMath.inChunkSquare(-4.5, 8.0, 5, 5, 0, 4));
	}

	@Test
	void chunksAreListedNearestFirst() {
		int[] bounds = ZoneMath.squareBounds(5, 5, 1);
		long[] chunks = ZoneMath.chunksNearestFirst(bounds[0], bounds[1], bounds[2], bounds[3], 5, 5);
		assertEquals(9, chunks.length);
		assertEquals(ChunkKey.of(0, 0), chunks[0]);
		Set<Long> distinct = new HashSet<>();
		for (long key : chunks) {
			distinct.add(key);
			assertTrue(Math.abs(ChunkKey.x(key)) <= 1 && Math.abs(ChunkKey.z(key)) <= 1);
		}
		assertEquals(9, distinct.size());
		// Corners come last.
		long last = chunks[8];
		assertEquals(1, Math.abs(ChunkKey.x(last)));
		assertEquals(1, Math.abs(ChunkKey.z(last)));
		// From a corner, the corner chunk comes first.
		long[] fromCorner = ZoneMath.chunksNearestFirst(bounds[0], bounds[1], bounds[2], bounds[3], 30, 30);
		assertEquals(ChunkKey.of(1, 1), fromCorner[0]);
	}

	@Test
	void horizontalDistance() {
		assertTrue(ZoneMath.within(192, 0, 0, 0, 192));
		assertFalse(ZoneMath.within(136, 136, 0, 0, 192));
		assertTrue(ZoneMath.within(-100, 100, 0, 0, 192));
	}

	@Test
	void loopRoutesWrapAround() {
		assertEquals(new ZoneMath.Step(1, 1), ZoneMath.next(0, 1, 3, false));
		assertEquals(new ZoneMath.Step(2, 1), ZoneMath.next(1, 1, 3, false));
		assertEquals(new ZoneMath.Step(0, 1), ZoneMath.next(2, 1, 3, false));
		// A shorter route after a redraw: the old index is wrapped.
		assertEquals(new ZoneMath.Step(0, 1), ZoneMath.next(5, 1, 3, false));
		assertEquals(new ZoneMath.Step(0, 1), ZoneMath.next(0, 1, 1, false));
	}

	@Test
	void backAndForthRoutesBounce() {
		ZoneMath.Step step = new ZoneMath.Step(0, 1);
		int[] expected = {1, 2, 1, 0, 1, 2, 1};
		for (int index : expected) {
			step = ZoneMath.next(step.index(), step.direction(), 3, true);
			assertEquals(index, step.index());
		}
		// Two points: 0, 1, 0, 1...
		step = new ZoneMath.Step(0, 1);
		for (int i = 0; i < 4; i++) {
			step = ZoneMath.next(step.index(), step.direction(), 2, true);
			assertEquals(i % 2 == 0 ? 1 : 0, step.index());
		}
	}

	@Test
	void nearestWaypointAndDistanceToPath() {
		List<BlockPos> points = List.of(new BlockPos(0, 64, 0), new BlockPos(10, 64, 0), new BlockPos(10, 64, 10));
		assertEquals(1, ZoneMath.nearestIndex(points, 9.0, 2.0));
		assertEquals(2, ZoneMath.nearestIndex(points, 12.0, 14.0));
		assertEquals(-1, ZoneMath.nearestIndex(List.of(), 0, 0));
		// Next to the first segment (block centres are at +0.5).
		assertEquals(3.0, ZoneMath.distanceToPath(points, false, 5.5, 3.5), 1e-9);
		// Near the closing segment (10,10) -> (0,0): only counted for a loop.
		double open = ZoneMath.distanceToPath(points, false, 2.5, 7.5);
		double loop = ZoneMath.distanceToPath(points, true, 2.5, 7.5);
		assertTrue(loop < 4.0, "loop " + loop);
		assertTrue(open > 6.0, "open " + open);
		assertEquals(Double.MAX_VALUE, ZoneMath.distanceToPath(List.of(), true, 0, 0));
		assertEquals(20, ZoneMath.pathLength(points, false));
		assertEquals(34, ZoneMath.pathLength(points, true));
	}

	@Test
	void defaultAndTypedNames() {
		assertEquals("Mine 1", ZoneMath.nextName("Mine", Set.of()));
		assertEquals("Mine 2", ZoneMath.nextName("Mine", Set.of("Mine 1", "Mine 3")));
		assertEquals("Forêt du nord", ZoneMath.cleanName("  Forêt   du &anord  ", 24));
		assertEquals("abc", ZoneMath.cleanName("§aabc", 24));
		assertEquals("12345", ZoneMath.cleanName("123456789", 5));
		assertEquals("", ZoneMath.cleanName("  &&  ", 24));
	}
}
