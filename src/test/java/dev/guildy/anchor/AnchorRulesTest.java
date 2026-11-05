package dev.guildy.anchor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How many anchor flags a guild gets, and the vanilla distances that make an anchor count as a player. */
class AnchorRulesTest {
	@Test
	void countGrowsWithTheLevelUpToTheCap() {
		// Defaults: 1, +1 every 25 levels, at most 3.
		assertEquals(1, AnchorRules.allowed(1, 1, 25, 3, 1));
		assertEquals(1, AnchorRules.allowed(24, 1, 25, 3, 1));
		assertEquals(2, AnchorRules.allowed(25, 1, 25, 3, 1));
		assertEquals(3, AnchorRules.allowed(50, 1, 25, 3, 1));
		assertEquals(3, AnchorRules.allowed(100, 1, 25, 3, 1));
		// No extra flag with extraEveryLevels = 0; none below the minimum level; none with maxCount = 0.
		assertEquals(2, AnchorRules.allowed(80, 2, 0, 5, 1));
		assertEquals(0, AnchorRules.allowed(4, 1, 25, 3, 5));
		assertEquals(1, AnchorRules.allowed(5, 1, 25, 3, 5));
		assertEquals(0, AnchorRules.allowed(50, 1, 25, 0, 1));
		// baseCount 0: the first flag comes with the levels.
		assertEquals(0, AnchorRules.allowed(9, 0, 10, 3, 1));
		assertEquals(1, AnchorRules.allowed(10, 0, 10, 3, 1));
	}

	@Test
	void nextFlagLevel() {
		assertEquals(25, AnchorRules.nextLevel(1, 1, 25, 3, 1));
		assertEquals(50, AnchorRules.nextLevel(30, 1, 25, 3, 1));
		assertEquals(-1, AnchorRules.nextLevel(50, 1, 25, 3, 1));
		assertEquals(5, AnchorRules.nextLevel(2, 1, 25, 3, 5));
		assertEquals(-1, AnchorRules.nextLevel(10, 1, 0, 3, 1));
		assertEquals(10, AnchorRules.nextLevel(3, 0, 10, 3, 1));
	}

	@Test
	void spawningReachIsMeasuredFromTheChunkCentre() {
		// Chunk (0, 0) has its centre at (8, 8).
		assertTrue(AnchorRules.chunkInReach(8.5, 8.5, 0, 0));
		assertTrue(AnchorRules.chunkInReach(8 + 127.9, 8, 0, 0));
		assertFalse(AnchorRules.chunkInReach(8 + 128, 8, 0, 0));
		// 7 chunks away along x: centre 112 blocks away.
		assertTrue(AnchorRules.chunkInReach(8, 8, 7, 0));
		// 6 chunks along both axes: about 136 blocks.
		assertFalse(AnchorRules.chunkInReach(8, 8, 6, 6));
		double[] points = {1000.5, 64.5, 1000.5, 8.5, 70.5, 8.5};
		assertTrue(AnchorRules.chunkInReach(points, 0, 0));
		assertTrue(AnchorRules.chunkInReach(points, 62, 62));
		assertFalse(AnchorRules.chunkInReach(points, 30, 30));
	}

	@Test
	void nearestDistanceAndRange() {
		assertEquals(Double.MAX_VALUE, AnchorRules.nearestSqr(new double[0], 0, 0, 0));
		double[] points = {0, 0, 0, 10, 0, 0};
		assertEquals(4.0, AnchorRules.nearestSqr(points, 8, 0, 0));
		assertEquals(25.0, AnchorRules.nearestSqr(points, 0, 3, 4));
		// Strictly closer than the range, like EntityGetter.hasNearbyAlivePlayer.
		assertTrue(AnchorRules.anyWithin(points, 0, 15.9, 0, 16));
		assertFalse(AnchorRules.anyWithin(points, 0, 16, 0, 16));
	}

	@Test
	void anchorDistanceReplacesAFartherOrMissingPlayer() {
		// No player: the anchor's distance when within 128 blocks.
		assertEquals(400.0, AnchorRules.anchorDistance(400.0, -1.0));
		assertEquals(-1.0, AnchorRules.anchorDistance(128.0 * 128.0, -1.0));
		// The nearer of the two counts; a player at the same distance is kept.
		assertEquals(400.0, AnchorRules.anchorDistance(400.0, 90_000.0));
		assertEquals(-1.0, AnchorRules.anchorDistance(400.0, 100.0));
		assertEquals(-1.0, AnchorRules.anchorDistance(400.0, 400.0));
	}
}
