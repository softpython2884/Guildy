package dev.guildy.villager;

import dev.guildy.data.ChunkKey;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Pure geometry of work zones and patrol routes (unit tested): chunk squares, nearest-first chunk order, waypoint
 * progression, distance to a path, default names.
 */
public final class ZoneMath {
	private ZoneMath() {
	}

	// ------------------------------------------------------------------ zones

	/** True when block (x, z) lies in the square of (2r+1)² chunks centred on the chunk of (centerX, centerZ). */
	public static boolean inChunkSquare(int x, int z, int centerX, int centerZ, int radius) {
		return radius >= 0 && Math.abs((x >> 4) - (centerX >> 4)) <= radius && Math.abs((z >> 4) - (centerZ >> 4)) <= radius;
	}

	/** Same square widened by {@code margin} blocks on every side (positions with decimals, e.g. a mob). */
	public static boolean inChunkSquare(double x, double z, int centerX, int centerZ, int radius, int margin) {
		int[] bounds = squareBounds(centerX, centerZ, radius);
		return x >= bounds[0] - margin && x < bounds[2] + 1 + margin && z >= bounds[1] - margin && z < bounds[3] + 1 + margin;
	}

	/** Block bounds {minX, minZ, maxX, maxZ} (inclusive) of that square. */
	public static int[] squareBounds(int centerX, int centerZ, int radius) {
		int r = Math.max(0, radius);
		int cx = centerX >> 4;
		int cz = centerZ >> 4;
		return new int[]{(cx - r) << 4, (cz - r) << 4, ((cx + r) << 4) + 15, ((cz + r) << 4) + 15};
	}

	/** Horizontal distance between (x, z) and (cx, cz) at most {@code distance} blocks. */
	public static boolean within(int x, int z, int cx, int cz, int distance) {
		long dx = (long) x - cx;
		long dz = (long) z - cz;
		return dx * dx + dz * dz <= (long) distance * distance;
	}

	/** Chunk keys ({@link ChunkKey} layout) of the chunks overlapping the block box, nearest chunk centre first. */
	public static long[] chunksNearestFirst(int minX, int minZ, int maxX, int maxZ, int fromX, int fromZ) {
		int minCx = minX >> 4;
		int minCz = minZ >> 4;
		int maxCx = maxX >> 4;
		int maxCz = maxZ >> 4;
		if (maxCx < minCx || maxCz < minCz) {
			return new long[0];
		}
		int width = maxCx - minCx + 1;
		int count = width * (maxCz - minCz + 1);
		long[][] sorted = new long[count][2];
		int i = 0;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				long dx = (cx << 4) + 8L - fromX;
				long dz = (cz << 4) + 8L - fromZ;
				sorted[i][0] = dx * dx + dz * dz;
				sorted[i][1] = ChunkKey.of(cx, cz);
				i++;
			}
		}
		Arrays.sort(sorted, (a, b) -> Long.compare(a[0], b[0]));
		long[] keys = new long[count];
		for (int j = 0; j < count; j++) {
			keys[j] = sorted[j][1];
		}
		return keys;
	}

	// ------------------------------------------------------------------ routes

	/** Waypoint index and walking direction (+1 / -1, only meaningful for back-and-forth routes). */
	public record Step(int index, int direction) {
	}

	/** Waypoint after {@code index}: a loop wraps around (last → first), a back-and-forth route bounces at both ends. */
	public static Step next(int index, int direction, int size, boolean pingPong) {
		if (size <= 1) {
			return new Step(0, 1);
		}
		int current = Math.floorMod(index, size);
		if (!pingPong) {
			return new Step((current + 1) % size, 1);
		}
		int dir = direction >= 0 ? 1 : -1;
		int next = current + dir;
		if (next < 0 || next >= size) {
			dir = -dir;
			next = current + dir;
		}
		return new Step(next, dir);
	}

	/** Index of the waypoint nearest to (x, z) (horizontal), -1 when there is none. */
	public static int nearestIndex(List<BlockPos> points, double x, double z) {
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for (int i = 0; i < points.size(); i++) {
			BlockPos point = points.get(i);
			double dx = point.getX() + 0.5 - x;
			double dz = point.getZ() + 0.5 - z;
			double dist = dx * dx + dz * dz;
			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	/**
	 * Horizontal distance from (x, z) to the path: segments between consecutive waypoints, plus the closing segment
	 * (last → first) for a loop. {@link Double#MAX_VALUE} without waypoints.
	 */
	public static double distanceToPath(List<BlockPos> points, boolean loop, double x, double z) {
		if (points.isEmpty()) {
			return Double.MAX_VALUE;
		}
		BlockPos first = points.getFirst();
		double best = distanceSq(first.getX() + 0.5, first.getZ() + 0.5, x, z);
		for (int i = 0; i + 1 < points.size(); i++) {
			best = Math.min(best, segmentDistanceSq(points.get(i), points.get(i + 1), x, z));
		}
		if (loop && points.size() > 2) {
			best = Math.min(best, segmentDistanceSq(points.getLast(), first, x, z));
		}
		return Math.sqrt(best);
	}

	private static double segmentDistanceSq(BlockPos a, BlockPos b, double x, double z) {
		double ax = a.getX() + 0.5;
		double az = a.getZ() + 0.5;
		double bx = b.getX() + 0.5;
		double bz = b.getZ() + 0.5;
		double vx = bx - ax;
		double vz = bz - az;
		double lengthSq = vx * vx + vz * vz;
		double t = lengthSq <= 0 ? 0 : ((x - ax) * vx + (z - az) * vz) / lengthSq;
		t = Math.max(0, Math.min(1, t));
		return distanceSq(ax + t * vx, az + t * vz, x, z);
	}

	private static double distanceSq(double ax, double az, double bx, double bz) {
		double dx = ax - bx;
		double dz = az - bz;
		return dx * dx + dz * dz;
	}

	/** Walking length (blocks) of the path, closing segment included for a loop. */
	public static int pathLength(List<BlockPos> points, boolean loop) {
		double total = 0;
		for (int i = 0; i + 1 < points.size(); i++) {
			total += Math.sqrt(points.get(i).distSqr(points.get(i + 1)));
		}
		if (loop && points.size() > 2) {
			total += Math.sqrt(points.getLast().distSqr(points.getFirst()));
		}
		return (int) Math.round(total);
	}

	// ------------------------------------------------------------------ names

	/** "base n" with the smallest n ≥ 1 not used yet. */
	public static String nextName(String base, Collection<String> used) {
		for (int n = 1; ; n++) {
			String name = base + " " + n;
			if (!used.contains(name)) {
				return name;
			}
		}
	}

	/** A name typed by a player: formatting codes removed, spaces collapsed, at most {@code max} characters. */
	public static String cleanName(String input, int max) {
		if (input == null) {
			return "";
		}
		String cleaned = input.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "").replace("§", "").replace("&", "")
				.replaceAll("\\s+", " ").trim();
		return cleaned.length() > max ? cleaned.substring(0, max).trim() : cleaned;
	}
}
