package dev.guildy.villager;

import dev.guildy.data.ChunkKey;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkAnchorTest {
	/** 3×3 founding square around chunk (0, 0): blocks -16..31 on both axes. */
	private static Set<Long> square() {
		Set<Long> claims = new HashSet<>();
		for (int cx = -1; cx <= 1; cx++) {
			for (int cz = -1; cz <= 1; cz++) {
				claims.add(ChunkKey.of(cx, cz));
			}
		}
		return claims;
	}

	@Test
	void insideTheTerritoryTheAnchorIsTheVillager() {
		assertArrayEquals(new int[]{5, -7}, WorkAnchor.nearestClaimPoint(square(), 5, -7));
		assertArrayEquals(new int[]{31, 31}, WorkAnchor.nearestClaimPoint(square(), 31, 31));
		assertEquals(0.0, WorkAnchor.distanceToClaims(square(), -16, 20));
	}

	@Test
	void outsideTheAnchorIsTheNearestBorderBlock() {
		// East of the square: same z, x clamped to the last claimed column.
		assertArrayEquals(new int[]{31, 10}, WorkAnchor.nearestClaimPoint(square(), 100, 10));
		// North-west corner.
		assertArrayEquals(new int[]{-16, -16}, WorkAnchor.nearestClaimPoint(square(), -40, -50));
		assertEquals(69.0, WorkAnchor.distanceToClaims(square(), 100, 10), 1e-9);
	}

	@Test
	void irregularTerritoryUsesTheNearestChunk() {
		Set<Long> claims = square();
		// A tongue of claims eastwards along z chunk 0 (x chunks 2..5 = blocks 32..95).
		for (int cx = 2; cx <= 5; cx++) {
			claims.add(ChunkKey.of(cx, 0));
		}
		assertArrayEquals(new int[]{90, 15}, WorkAnchor.nearestClaimPoint(claims, 90, 40));
		assertArrayEquals(new int[]{95, 8}, WorkAnchor.nearestClaimPoint(claims, 120, 8));
	}

	@Test
	void withoutTerritoryTheFallbackIsUsed() {
		assertNull(WorkAnchor.nearestClaimPoint(Set.of(), 3, 4));
		assertEquals(Double.MAX_VALUE, WorkAnchor.distanceToClaims(List.of(), 3, 4));
		assertArrayEquals(new int[]{-8, 12}, WorkAnchor.anchor(Set.of(), 300, 300, -8, 12));
		assertArrayEquals(new int[]{31, 31}, WorkAnchor.anchor(square(), 300, 300, -8, 12));
	}

	@Test
	void areaIsTheDiscAroundTheAnchorWithinReachOfTheHeart() {
		assertTrue(WorkAnchor.inArea(79, 10, 31, 10, 48, true, 0, 0, 192));
		assertFalse(WorkAnchor.inArea(80, 10, 31, 10, 48, true, 0, 0, 192));
		// The heart limit still applies.
		assertFalse(WorkAnchor.inArea(200, 0, 180, 0, 48, true, 0, 0, 192));
		assertTrue(WorkAnchor.inArea(200, 0, 180, 0, 48, false, 0, 0, 192));
	}

	/**
	 * The drift of the old automatic mode: each search was centred on the villager, so going every time to the farthest
	 * tree (away from the base) took it further and further. Centred on the territory, it never gets further than the
	 * radius, whatever the trees it picks.
	 */
	@Test
	void villagerNeverDriftsFurtherThanTheRadiusFromTheTerritory() {
		Set<Long> claims = square();
		int radius = 48;
		int[] anchored = {0, 0};
		int[] drifting = {0, 0};
		Random random = new Random(42);
		for (int step = 0; step < 200; step++) {
			// Next tree: on the edge of the search disc, roughly away from the heart (0, 0).
			double away = Math.atan2(anchored[1], anchored[0]) + (random.nextDouble() - 0.5);
			int[] center = WorkAnchor.anchor(claims, anchored[0], anchored[1], 0, 0);
			anchored = edge(center, radius - 1, away);
			assertTrue(WorkAnchor.inArea(anchored[0], anchored[1], center[0], center[1], radius, false, 0, 0, 0));
			assertTrue(WorkAnchor.distanceToClaims(claims, anchored[0], anchored[1]) <= radius,
					"step " + step + ": " + anchored[0] + " " + anchored[1]);
			drifting = edge(drifting, radius - 1, Math.atan2(drifting[1], drifting[0]));
		}
		assertTrue(WorkAnchor.distanceToClaims(claims, drifting[0], drifting[1]) > 50 * radius,
				"a search centred on the villager drifts away");
	}

	private static int[] edge(int[] center, int distance, double angle) {
		return new int[]{center[0] + (int) (Math.cos(angle) * distance), center[1] + (int) (Math.sin(angle) * distance)};
	}
}
