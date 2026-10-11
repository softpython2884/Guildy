package dev.guildy.claim;

import dev.guildy.data.ChunkKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure claim math over chunk keys ({@link ChunkKey}): areas, adjacency, connectivity, distance rules.
 * No Minecraft dependency (unit tested).
 */
public final class ClaimRules {
	private ClaimRules() {
	}

	/** Outcome of a claim validation, mapped to lang keys by the caller. */
	public enum Result {
		OK,
		/** Part of the area belongs to another guild. */
		TAKEN,
		/** Every chunk of the area already belongs to the guild. */
		ALREADY_OWNED,
		MAX_CHUNKS,
		/** No new chunk touches (4-neighbour) the existing territory. */
		NOT_CONNECTED,
		/** Too close to another guild's territory. */
		TOO_CLOSE,
		TOO_CLOSE_TO_SPAWN
	}

	/** Validation result with the chunks that would actually be claimed. */
	public record Check(Result result, List<Long> newChunks) {
		public boolean ok() {
			return result == Result.OK;
		}

		static Check fail(Result result) {
			return new Check(result, List.of());
		}
	}

	/** Owner lookup of a chunk (null = wilderness). */
	@FunctionalInterface
	public interface Owners {
		@Nullable UUID ownerOf(long chunkKey);
	}

	/** Keys of the (2r+1)² square centred on {@code center}. */
	public static List<Long> square(long center, int radius) {
		int r = Math.max(0, radius);
		int cx = ChunkKey.x(center);
		int cz = ChunkKey.z(center);
		List<Long> keys = new ArrayList<>((2 * r + 1) * (2 * r + 1));
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				keys.add(ChunkKey.of(cx + dx, cz + dz));
			}
		}
		return keys;
	}

	/** The 4 side neighbours of a chunk. */
	public static long[] neighbours(long key) {
		return new long[]{
				ChunkKey.offset(key, 1, 0),
				ChunkKey.offset(key, -1, 0),
				ChunkKey.offset(key, 0, 1),
				ChunkKey.offset(key, 0, -1)
		};
	}

	/** True when {@code key} has a 4-neighbour in {@code territory}. */
	public static boolean touches(Set<Long> territory, long key) {
		for (long n : neighbours(key)) {
			if (territory.contains(n)) {
				return true;
			}
		}
		return false;
	}

	/** True when at least one of {@code keys} touches {@code territory}. */
	public static boolean anyTouches(Set<Long> territory, Collection<Long> keys) {
		for (long key : keys) {
			if (touches(territory, key)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True when removing {@code removed} does not cut any chunk off the part of the territory that is 4-connected to
	 * {@code root}. Chunks that were already disconnected (corrupted data) are ignored. Removing the root is never allowed.
	 */
	public static boolean staysConnected(Set<Long> territory, long root, long removed) {
		if (removed == root || !territory.contains(root)) {
			return false;
		}
		if (!territory.contains(removed)) {
			return true;
		}
		Set<Long> before = connectedTo(territory, root, null);
		if (!before.contains(removed)) {
			// already cut off from the root: removing it cannot disconnect anything else
			return true;
		}
		return connectedTo(territory, root, removed).size() == before.size() - 1;
	}

	/** Chunks 4-connected to {@code root} (BFS), treating {@code excluded} (if any) as absent. */
	public static Set<Long> connectedTo(Set<Long> territory, long root, @Nullable Long excluded) {
		Set<Long> seen = new HashSet<>();
		if (!territory.contains(root) || (excluded != null && excluded == root)) {
			return seen;
		}
		ArrayDeque<Long> queue = new ArrayDeque<>();
		seen.add(root);
		queue.add(root);
		while (!queue.isEmpty()) {
			long current = queue.poll();
			for (long n : neighbours(current)) {
				if ((excluded == null || n != excluded) && territory.contains(n) && seen.add(n)) {
					queue.add(n);
				}
			}
		}
		return seen;
	}

	/** Smallest Chebyshev distance (chunks) between {@code key} and any chunk of {@code area}. */
	public static int distance(Collection<Long> area, long key) {
		int best = Integer.MAX_VALUE;
		for (long a : area) {
			best = Math.min(best, ChunkKey.distance(a, key));
		}
		return best;
	}

	/**
	 * True when a chunk owned by a guild other than {@code self} lies within Chebyshev distance {@code minDistance}
	 * of the area (i.e. the gap rule "distance > minDistance" is violated). Scans the bounding box only.
	 */
	public static boolean nearForeign(Collection<Long> area, int minDistance, UUID self, Owners owners) {
		if (area.isEmpty()) {
			return false;
		}
		int d = Math.max(0, minDistance);
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (long key : area) {
			minX = Math.min(minX, ChunkKey.x(key));
			maxX = Math.max(maxX, ChunkKey.x(key));
			minZ = Math.min(minZ, ChunkKey.z(key));
			maxZ = Math.max(maxZ, ChunkKey.z(key));
		}
		for (int x = minX - d; x <= maxX + d; x++) {
			for (int z = minZ - d; z <= maxZ + d; z++) {
				long key = ChunkKey.of(x, z);
				UUID owner = owners.ownerOf(key);
				if (owner != null && !owner.equals(self) && distance(area, key) <= d) {
					return true;
				}
			}
		}
		return false;
	}

	/** True when a chunk of the area is closer than {@code minSpawnDistance} chunks to the spawn chunk. */
	public static boolean nearSpawn(Collection<Long> area, @Nullable Long spawnChunk, int minSpawnDistance) {
		if (spawnChunk == null || minSpawnDistance <= 0) {
			return false;
		}
		return distance(area, spawnChunk) < minSpawnDistance;
	}

	/** Founding flag: the whole area must be free (or already ours), far enough from other guilds and from spawn. */
	public static Check checkFounding(UUID guild, List<Long> area, Owners owners, int minDistance,
			@Nullable Long spawnChunk, int minSpawnDistance) {
		List<Long> fresh = new ArrayList<>();
		for (long key : area) {
			UUID owner = owners.ownerOf(key);
			if (owner == null) {
				fresh.add(key);
			} else if (!owner.equals(guild)) {
				return Check.fail(Result.TAKEN);
			}
		}
		if (nearForeign(area, minDistance, guild, owners)) {
			return Check.fail(Result.TOO_CLOSE);
		}
		if (nearSpawn(area, spawnChunk, minSpawnDistance)) {
			return Check.fail(Result.TOO_CLOSE_TO_SPAWN);
		}
		return new Check(Result.OK, fresh);
	}

	/**
	 * Extension: chunks of the area must be free or already ours; at least one new chunk; total within the limit;
	 * connected to the territory when required; new chunks far enough from other guilds and from spawn.
	 */
	public static Check checkExtension(UUID guild, Set<Long> own, List<Long> area, Owners owners, int maxChunks,
			boolean requireConnected, int minDistance, @Nullable Long spawnChunk, int minSpawnDistance) {
		List<Long> fresh = new ArrayList<>();
		for (long key : area) {
			UUID owner = owners.ownerOf(key);
			if (owner != null && !owner.equals(guild)) {
				return Check.fail(Result.TAKEN);
			}
			if (!own.contains(key)) {
				fresh.add(key);
			}
		}
		if (fresh.isEmpty()) {
			return Check.fail(Result.ALREADY_OWNED);
		}
		if (own.size() + fresh.size() > maxChunks) {
			return Check.fail(Result.MAX_CHUNKS);
		}
		if (requireConnected && !own.isEmpty() && !anyTouches(own, fresh)) {
			return Check.fail(Result.NOT_CONNECTED);
		}
		if (nearForeign(fresh, minDistance, guild, owners)) {
			return Check.fail(Result.TOO_CLOSE);
		}
		if (nearSpawn(fresh, spawnChunk, minSpawnDistance)) {
			return Check.fail(Result.TOO_CLOSE_TO_SPAWN);
		}
		return new Check(Result.OK, fresh);
	}
}
