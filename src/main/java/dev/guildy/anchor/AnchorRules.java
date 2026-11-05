package dev.guildy.anchor;

/**
 * Pure rules of the anchor flags (unit tested): how many a guild may plant, and the vanilla distances that make an
 * anchor count as a player for mob spawning (from the 26.3 sources).
 */
public final class AnchorRules {
	/**
	 * A chunk spawns mobs when its centre is within 128 blocks (horizontally) of a player
	 * (ChunkMap.playerIsCloseEnoughForSpawning).
	 */
	public static final double SPAWN_REACH_SQR = 128.0 * 128.0;
	/**
	 * Largest instant-despawn distance of the mob categories (MobCategory.despawnDistance): farther than this from every
	 * player and anchor, an anchor changes nothing (the mob can neither spawn nor stay).
	 */
	public static final double MOB_REACH_SQR = 128.0 * 128.0;
	/** Chunks a player adds to the global mob caps: 17 × 17 (NaturalSpawner.MAGIC_NUMBER). */
	public static final int SPAWN_CHUNKS_PER_ANCHOR = 17 * 17;

	private AnchorRules() {
	}

	/**
	 * Anchor flags allowed at a guild level: {@code base}, +1 every {@code every} levels (0 = never), at most {@code max};
	 * none below {@code minLevel}.
	 */
	public static int allowed(int level, int base, int every, int max, int minLevel) {
		if (level < minLevel || max <= 0) {
			return 0;
		}
		long count = base + (every > 0 ? level / every : 0);
		return (int) Math.max(0, Math.min(max, count));
	}

	/** Level at which the next anchor flag comes, or -1 when none comes any more. */
	public static int nextLevel(int level, int base, int every, int max, int minLevel) {
		int now = allowed(level, base, every, max, minLevel);
		if (now >= max) {
			return -1;
		}
		if (level < minLevel && allowed(minLevel, base, every, max, minLevel) > 0) {
			return minLevel;
		}
		if (every <= 0) {
			return -1;
		}
		return (level / every + 1) * every;
	}

	/** True when the centre of the chunk is within the spawning reach of the point (x, z), like a player standing there. */
	public static boolean chunkInReach(double x, double z, int chunkX, int chunkZ) {
		double dx = chunkX * 16 + 8 - x;
		double dz = chunkZ * 16 + 8 - z;
		return dx * dx + dz * dz < SPAWN_REACH_SQR;
	}

	/** True when a chunk is within the spawning reach of one of the points ({x, y, z} triples). */
	public static boolean chunkInReach(double[] points, int chunkX, int chunkZ) {
		for (int i = 0; i + 2 < points.length; i += 3) {
			if (chunkInReach(points[i], points[i + 2], chunkX, chunkZ)) {
				return true;
			}
		}
		return false;
	}

	/** Squared distance to the nearest point ({x, y, z} triples), {@link Double#MAX_VALUE} without points. */
	public static double nearestSqr(double[] points, double x, double y, double z) {
		double best = Double.MAX_VALUE;
		for (int i = 0; i + 2 < points.length; i += 3) {
			double dx = points[i] - x;
			double dy = points[i + 1] - y;
			double dz = points[i + 2] - z;
			best = Math.min(best, dx * dx + dy * dy + dz * dz);
		}
		return best;
	}

	/** True when a point is strictly closer than {@code range} (EntityGetter.hasNearbyAlivePlayer). */
	public static boolean anyWithin(double[] points, double x, double y, double z, double range) {
		return nearestSqr(points, x, y, z) < range * range;
	}

	/**
	 * Distance an anchor gives to a mob or a spawn position: the anchor's when it is in reach and nearer than the player
	 * (or there is no player, {@code playerSqr} &lt; 0), else -1 (vanilla decides with the player).
	 */
	public static double anchorDistance(double anchorSqr, double playerSqr) {
		if (anchorSqr >= MOB_REACH_SQR) {
			return -1.0;
		}
		return playerSqr >= 0.0 && playerSqr <= anchorSqr ? -1.0 : anchorSqr;
	}
}
