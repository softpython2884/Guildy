package dev.guildy.villager;

import dev.guildy.data.ChunkKey;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Pure geometry of the automatic mode of lumberjacks and farmers (unit tested). Without a zone they work around the
 * point of the territory nearest to them (around themselves while inside it), within a bounded radius: a villager that
 * cut a tree at the edge of that disc searches again from the territory, never from where it stands, so it can never
 * drift away tree after tree.
 */
public final class WorkAnchor {
	private WorkAnchor() {
	}

	/**
	 * Point of the territory nearest to (x, z): (x, z) itself inside a claimed chunk, else the nearest block of the
	 * nearest claimed chunk. Null without claims.
	 */
	public static int @Nullable [] nearestClaimPoint(Collection<Long> claims, int x, int z) {
		if (claims.isEmpty()) {
			return null;
		}
		if (claims.contains(ChunkKey.ofBlock(x, z))) {
			return new int[]{x, z};
		}
		long best = Long.MAX_VALUE;
		int bestX = x;
		int bestZ = z;
		for (long key : claims) {
			int minX = ChunkKey.x(key) << 4;
			int minZ = ChunkKey.z(key) << 4;
			int px = Math.max(minX, Math.min(minX + 15, x));
			int pz = Math.max(minZ, Math.min(minZ + 15, z));
			long dx = (long) px - x;
			long dz = (long) pz - z;
			long dist = dx * dx + dz * dz;
			if (dist < best) {
				best = dist;
				bestX = px;
				bestZ = pz;
			}
		}
		return new int[]{bestX, bestZ};
	}

	/** Horizontal distance (blocks) from (x, z) to the territory: 0 inside, {@link Double#MAX_VALUE} without claims. */
	public static double distanceToClaims(Collection<Long> claims, int x, int z) {
		int[] point = nearestClaimPoint(claims, x, z);
		if (point == null) {
			return Double.MAX_VALUE;
		}
		double dx = point[0] - x;
		double dz = point[1] - z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Centre of the automatic search: the nearest point of the territory, or {@code fallback} (where the task started)
	 * for a guild without territory.
	 */
	public static int[] anchor(Collection<Long> claims, int x, int z, int fallbackX, int fallbackZ) {
		int[] point = nearestClaimPoint(claims, x, z);
		return point != null ? point : new int[]{fallbackX, fallbackZ};
	}

	/**
	 * Column (x, z) inside the automatic area: at most {@code radius} blocks from the anchor and, when the guild has a
	 * heart, at most {@code maxDistance} blocks from it.
	 */
	public static boolean inArea(int x, int z, int anchorX, int anchorZ, int radius, boolean hasHeart, int heartX, int heartZ,
			int maxDistance) {
		return ZoneMath.within(x, z, anchorX, anchorZ, radius) && (!hasHeart || ZoneMath.within(x, z, heartX, heartZ, maxDistance));
	}
}
