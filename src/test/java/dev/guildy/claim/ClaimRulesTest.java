package dev.guildy.claim;

import dev.guildy.claim.ClaimRules.Check;
import dev.guildy.claim.ClaimRules.Result;
import dev.guildy.data.ChunkKey;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimRulesTest {
	private static final UUID A = UUID.randomUUID();
	private static final UUID B = UUID.randomUUID();

	private static long k(int x, int z) {
		return ChunkKey.of(x, z);
	}

	private static Set<Long> set(long... keys) {
		Set<Long> set = new HashSet<>();
		for (long key : keys) {
			set.add(key);
		}
		return set;
	}

	/** Mutable world of claims for the tests. */
	private static final class World {
		final Map<Long, UUID> owners = new HashMap<>();

		World claim(UUID guild, long... keys) {
			for (long key : keys) {
				owners.put(key, guild);
			}
			return this;
		}

		World claim(UUID guild, List<Long> keys) {
			for (long key : keys) {
				owners.put(key, guild);
			}
			return this;
		}

		Set<Long> of(UUID guild) {
			Set<Long> keys = new HashSet<>();
			owners.forEach((key, owner) -> {
				if (owner.equals(guild)) {
					keys.add(key);
				}
			});
			return keys;
		}

		ClaimRules.Owners lookup() {
			return owners::get;
		}
	}

	@Test
	void chunkKeyRoundTripsNegativeCoordinates() {
		long key = k(-5, -1_000_000);
		assertEquals(-5, ChunkKey.x(key));
		assertEquals(-1_000_000, ChunkKey.z(key));
		assertEquals(k(-4, -1_000_001), ChunkKey.offset(key, 1, -1));
		assertEquals(3, ChunkKey.distance(k(-1, 0), k(2, -2)));
	}

	@Test
	void squareHasExpectedSizeAndContent() {
		assertEquals(List.of(k(3, 4)), ClaimRules.square(k(3, 4), 0));
		List<Long> square = ClaimRules.square(k(0, 0), 1);
		assertEquals(9, square.size());
		assertTrue(square.contains(k(-1, -1)));
		assertTrue(square.contains(k(1, 1)));
		assertFalse(square.contains(k(2, 0)));
		assertEquals(25, ClaimRules.square(k(-10, 7), 2).size());
	}

	@Test
	void touchesUsesSideNeighboursOnly() {
		Set<Long> territory = set(k(0, 0));
		assertTrue(ClaimRules.touches(territory, k(1, 0)));
		assertTrue(ClaimRules.touches(territory, k(0, -1)));
		assertFalse(ClaimRules.touches(territory, k(1, 1)), "diagonal is not adjacent");
		assertFalse(ClaimRules.touches(territory, k(2, 0)));
		assertTrue(ClaimRules.anyTouches(territory, List.of(k(5, 5), k(-1, 0))));
	}

	@Test
	void unclaimMustKeepTerritoryConnected() {
		// line A(0,0) - (1,0) - (2,0)
		Set<Long> line = set(k(0, 0), k(1, 0), k(2, 0));
		assertTrue(ClaimRules.staysConnected(line, k(0, 0), k(2, 0)), "removing the tip is fine");
		assertFalse(ClaimRules.staysConnected(line, k(0, 0), k(1, 0)), "removing the middle cuts the line");
		assertFalse(ClaimRules.staysConnected(line, k(0, 0), k(0, 0)), "the root can never be removed");
		assertTrue(ClaimRules.staysConnected(line, k(0, 0), k(9, 9)), "not owned: nothing to cut");

		// ring around (1,1): every chunk can be removed alone
		Set<Long> ring = set(k(0, 0), k(1, 0), k(2, 0), k(2, 1), k(2, 2), k(1, 2), k(0, 2), k(0, 1));
		assertTrue(ClaimRules.staysConnected(ring, k(0, 0), k(2, 1)));
		assertTrue(ClaimRules.staysConnected(ring, k(0, 0), k(1, 0)));

		// already disconnected leftover chunk can always be removed
		Set<Long> broken = set(k(0, 0), k(1, 0), k(10, 10));
		assertTrue(ClaimRules.staysConnected(broken, k(0, 0), k(10, 10)));
		assertTrue(ClaimRules.staysConnected(broken, k(0, 0), k(1, 0)));
	}

	@Test
	void foreignDistanceRule() {
		World world = new World().claim(B, k(10, 0));
		// minDistance 3: distance 3 is too close, distance 4 is fine
		assertTrue(ClaimRules.nearForeign(List.of(k(7, 0)), 3, A, world.lookup()));
		assertFalse(ClaimRules.nearForeign(List.of(k(6, 0)), 3, A, world.lookup()));
		assertTrue(ClaimRules.nearForeign(List.of(k(7, 3)), 3, A, world.lookup()), "Chebyshev distance");
		// own chunks never count
		assertFalse(ClaimRules.nearForeign(List.of(k(9, 0)), 3, B, world.lookup()));
		// area: closest chunk decides (x 4..6 -> distance 4; x 5..7 -> distance 3)
		assertFalse(ClaimRules.nearForeign(ClaimRules.square(k(5, 0), 1), 3, A, world.lookup()));
		assertTrue(ClaimRules.nearForeign(ClaimRules.square(k(6, 0), 1), 3, A, world.lookup()));
	}

	@Test
	void spawnDistanceRule() {
		assertTrue(ClaimRules.nearSpawn(List.of(k(3, 0)), k(0, 0), 4));
		assertFalse(ClaimRules.nearSpawn(List.of(k(4, 0)), k(0, 0), 4));
		assertFalse(ClaimRules.nearSpawn(List.of(k(0, 0)), null, 4), "spawn in another dimension");
		assertFalse(ClaimRules.nearSpawn(List.of(k(0, 0)), k(0, 0), 0));
	}

	@Test
	void foundingChecks() {
		World world = new World().claim(B, k(20, 20));
		List<Long> area = ClaimRules.square(k(0, 10), 1);
		Check ok = ClaimRules.checkFounding(A, area, world.lookup(), 3, k(0, 0), 4);
		assertTrue(ok.ok());
		assertEquals(9, ok.newChunks().size());

		assertEquals(Result.TOO_CLOSE_TO_SPAWN,
				ClaimRules.checkFounding(A, ClaimRules.square(k(0, 4), 1), world.lookup(), 3, k(0, 0), 4).result());
		assertEquals(Result.TAKEN,
				ClaimRules.checkFounding(A, ClaimRules.square(k(21, 21), 1), world.lookup(), 3, null, 4).result());
		assertEquals(Result.TOO_CLOSE,
				ClaimRules.checkFounding(A, ClaimRules.square(k(16, 20), 1), world.lookup(), 3, null, 4).result());
		assertTrue(ClaimRules.checkFounding(A, ClaimRules.square(k(15, 20), 1), world.lookup(), 3, null, 4).ok());
	}

	@Test
	void extensionChecks() {
		World world = new World().claim(A, ClaimRules.square(k(0, 0), 1)).claim(B, k(10, 0));
		Set<Long> own = world.of(A);

		Check east = ClaimRules.checkExtension(A, own, List.of(k(2, 0)), world.lookup(), 12, true, 3, null, 4);
		assertTrue(east.ok());
		assertEquals(List.of(k(2, 0)), east.newChunks());

		assertEquals(Result.ALREADY_OWNED,
				ClaimRules.checkExtension(A, own, List.of(k(1, 1)), world.lookup(), 12, true, 3, null, 4).result());
		assertEquals(Result.NOT_CONNECTED,
				ClaimRules.checkExtension(A, own, List.of(k(2, 2)), world.lookup(), 12, true, 3, null, 4).result(),
				"diagonal only");
		assertTrue(ClaimRules.checkExtension(A, own, List.of(k(2, 2)), world.lookup(), 12, false, 3, null, 4).ok(),
				"connection not required");
		assertEquals(Result.MAX_CHUNKS,
				ClaimRules.checkExtension(A, own, List.of(k(2, 0)), world.lookup(), 9, true, 3, null, 4).result());
		assertEquals(Result.TAKEN,
				ClaimRules.checkExtension(A, own, List.of(k(10, 0)), world.lookup(), 12, false, 3, null, 4).result());

		// growing towards B: (6,0) is at distance 4 from B (ok), (7,0) at distance 3 (too close)
		World line = new World().claim(A, k(5, 0)).claim(B, k(10, 0));
		assertTrue(ClaimRules.checkExtension(A, line.of(A), List.of(k(6, 0)), line.lookup(), 12, true, 3, null, 4).ok());
		line.claim(A, k(6, 0));
		assertEquals(Result.TOO_CLOSE,
				ClaimRules.checkExtension(A, line.of(A), List.of(k(7, 0)), line.lookup(), 12, true, 3, null, 4).result());

		// radius-1 extension partially overlapping our territory only claims the free chunks
		Check partial = ClaimRules.checkExtension(A, own, ClaimRules.square(k(2, 0), 1), world.lookup(), 30, true, 3, null, 4);
		assertTrue(partial.ok());
		assertEquals(6, partial.newChunks().size());

		// spawn rule applies to new chunks
		assertEquals(Result.TOO_CLOSE_TO_SPAWN,
				ClaimRules.checkExtension(A, own, List.of(k(2, 0)), world.lookup(), 12, true, 3, k(5, 0), 4).result());
	}
}
