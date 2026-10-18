package dev.guildy.ruin;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * Pure rules of the statue trail that leads to the grand temple (no Minecraft classes): where the statues go between the
 * atelier and the temple, which way they look, the frame of the statue map, the reuse of an existing statue, the checks
 * of the legend research and the guidance cooldown.
 */
public final class StatueRules {
	/** Ids of the 8 compass points ("ruin.direction.<id>"), clockwise from the north. */
	public static final String[] COMPASS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
	/** Half width of a vanilla map in map pixels (a decoration further than this is clamped to the edge). */
	private static final int MAP_HALF = 63;

	private StatueRules() {
	}

	/** The spacing settings of a trail (blocks). */
	public record Spacing(int count, int maxCount, int minSpacing, int maxSpacing, int lastMin, int lastMax, int lateral) {
	}

	/** A planned point (block coordinates). */
	public record Point(int x, int z) {
	}

	/** Centre (as given to the vanilla map creation, already on the map grid) and zoom of a map. */
	public record MapView(int centerX, int centerZ, int zoom) {
	}

	/**
	 * The statues between an origin (the atelier) and the temple, from the origin to the temple: the last one {@code lastMin}
	 * to {@code lastMax} blocks before the temple (in sight of its beacon), the others evenly spaced between the origin and
	 * the last one, shifted sideways a little (never the last). The number of statues is {@code count}, more when the spacing
	 * would exceed {@code maxSpacing} (at most {@code maxCount}), fewer when it would fall under {@code minSpacing}; a temple
	 * very close to the origin gets a single statue half way.
	 *
	 * @param seed randomness of the trail (distance of the last statue, lateral shifts): the same inputs give the same trail
	 */
	public static List<Point> waypoints(double originX, double originZ, double templeX, double templeZ, Spacing s, long seed) {
		List<Point> points = new ArrayList<>();
		double dx = templeX - originX;
		double dz = templeZ - originZ;
		double distance = Math.hypot(dx, dz);
		if (distance < 1) {
			points.add(new Point((int) Math.floor(originX), (int) Math.floor(originZ)));
			return points;
		}
		Random random = new Random(seed);
		double ux = dx / distance;
		double uz = dz / distance;
		int lastMin = Math.max(0, Math.min(s.lastMin(), s.lastMax()));
		int lastMax = Math.max(lastMin, s.lastMax());
		double last = lastMin + (lastMax - lastMin) * random.nextDouble();
		double reach = distance - last;
		int n = count(reach, s);
		if (reach < Math.max(1, s.minSpacing())) {
			// The temple is barely farther than the last statue should stand: one statue half way (or before the temple).
			double at = Math.max(reach, distance / 2);
			points.add(new Point((int) Math.floor(originX + ux * at), (int) Math.floor(originZ + uz * at)));
			return points;
		}
		double step = reach / n;
		double shift = Math.min(Math.max(0, s.lateral()), step * 0.25);
		for (int k = 1; k <= n; k++) {
			double at = step * k;
			double side = k == n ? 0 : (random.nextDouble() * 2 - 1) * shift;
			double x = originX + ux * at - uz * side;
			double z = originZ + uz * at + ux * side;
			points.add(new Point((int) Math.floor(x), (int) Math.floor(z)));
		}
		return points;
	}

	/** Number of statues before the last one's reach (see {@link #waypoints}). */
	static int count(double reach, Spacing s) {
		int min = Math.max(1, s.count());
		int max = Math.max(min, s.maxCount());
		int n = min;
		if (s.maxSpacing() > 0 && reach / n > s.maxSpacing()) {
			n = (int) Math.min(max, Math.ceil(reach / s.maxSpacing()));
		}
		if (s.minSpacing() > 0 && reach / n < s.minSpacing()) {
			n = (int) Math.max(1, Math.floor(reach / s.minSpacing()));
		}
		return n;
	}

	/**
	 * Quarter turns (clockwise seen from above) that turn a template whose front faces north toward a target: the nearest of
	 * the four directions (0 north, 1 east, 2 south, 3 west).
	 */
	public static int quarterTurnsToward(double fromX, double fromZ, double toX, double toZ) {
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? 1 : 3;
		}
		return dz > 0 ? 2 : 0;
	}

	/** Compass point ({@link #COMPASS}) of a horizontal direction (north = -z). */
	public static String compass(double dx, double dz) {
		if (Math.abs(dx) < 1e-9 && Math.abs(dz) < 1e-9) {
			return COMPASS[0];
		}
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = (int) Math.floorMod(Math.round(angle / 45.0), 8L);
		return COMPASS[sector];
	}

	/**
	 * The map that shows the most of the points: the smallest zoom from {@code minZoom} to {@code maxZoom} whose map (on the
	 * vanilla map grid, 128 << zoom blocks wide) holds every point at least {@code margin} blocks inside its edges (the grid
	 * cell of the middle of the points or one of its neighbours); none: the largest zoom centred on the middle of the points
	 * (the statues off the map are drawn on its edge, in their direction).
	 */
	public static MapView mapView(List<Point> points, int margin, int minZoom, int maxZoom) {
		int lo = Math.max(0, Math.min(4, minZoom));
		int hi = Math.max(lo, Math.min(4, maxZoom));
		if (points.isEmpty()) {
			return new MapView(cellCenter(0, 128 << lo), cellCenter(0, 128 << lo), lo);
		}
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (Point p : points) {
			minX = Math.min(minX, p.x());
			maxX = Math.max(maxX, p.x());
			minZ = Math.min(minZ, p.z());
			maxZ = Math.max(maxZ, p.z());
		}
		double midX = (minX + maxX) / 2.0;
		double midZ = (minZ + maxZ) / 2.0;
		for (int zoom = lo; zoom <= hi; zoom++) {
			int size = 128 << zoom;
			int cx = cellCenter(midX, size);
			int cz = cellCenter(midZ, size);
			for (int i = -1; i <= 1; i++) {
				for (int j = -1; j <= 1; j++) {
					int x = cx + i * size;
					int z = cz + j * size;
					if (fits(points, x, z, zoom, margin)) {
						return new MapView(x, z, zoom);
					}
				}
			}
		}
		int size = 128 << hi;
		return new MapView(cellCenter(midX, size), cellCenter(midZ, size), hi);
	}

	/** Centre of the vanilla map grid cell holding a coordinate (MapItemSavedData.createFresh). */
	static int cellCenter(double coordinate, int size) {
		int area = (int) Math.floor((coordinate + 64.0) / size);
		return area * size + size / 2 - 64;
	}

	/** Every point is drawn inside a map of this centre and zoom, {@code margin} blocks from its edges. */
	static boolean fits(List<Point> points, int centerX, int centerZ, int zoom, int margin) {
		double half = (double) MAP_HALF * (1 << zoom) - Math.max(0, margin);
		for (Point p : points) {
			if (Math.abs(p.x() - centerX) > half || Math.abs(p.z() - centerZ) > half) {
				return false;
			}
		}
		return true;
	}

	/** A statue that a new trail could reuse. */
	public record Candidate(int id, int x, int z, boolean usable) {
	}

	/** The nearest usable candidate within {@code radius} blocks of a planned point, or -1. */
	public static int pickReuse(double x, double z, List<Candidate> candidates, int radius) {
		int best = -1;
		double bestDist = (double) radius * radius;
		for (Candidate c : candidates) {
			if (!c.usable()) {
				continue;
			}
			double dist = (c.x() - x) * (c.x() - x) + (c.z() - z) * (c.z() - z);
			if (dist <= bestDist) {
				bestDist = dist;
				best = c.id();
			}
		}
		return best;
	}

	/**
	 * The statue a guidance points to, skipping the abandoned ones: from {@code next}, follows the chain while the statue
	 * is abandoned. -1 = the temple.
	 *
	 * @param abandoned true for an abandoned statue
	 * @param nextOf next statue of an abandoned one on the same route (-1 = the temple, -2 = unknown: the temple)
	 */
	public static int liveTarget(int next, IntPredicate abandoned, IntUnaryOperator nextOf, int maxSteps) {
		int current = next;
		for (int steps = 0; current >= 0 && abandoned.test(current); steps++) {
			if (steps >= maxSteps) {
				return -1;
			}
			current = nextOf.applyAsInt(current);
		}
		return Math.max(-1, current);
	}

	/** Why the legend research is refused (lang key), or null: disabled, guild level too low. */
	public static @Nullable String legendRefusal(boolean enabled, int guildLevel, int minLevel) {
		if (!enabled) {
			return "ruin.research.legend.disabled";
		}
		if (guildLevel < minLevel) {
			return "ruin.research.legend.level";
		}
		return null;
	}

	/** First item of a cost the player does not have enough of, or null when everything is there. */
	public static Map.@Nullable Entry<String, Integer> missing(Map<String, Integer> cost, ToIntFunction<String> have) {
		for (Map.Entry<String, Integer> entry : cost.entrySet()) {
			if (have.applyAsInt(entry.getKey()) < entry.getValue()) {
				return entry;
			}
		}
		return null;
	}

	/** A statue guides a player again once the cooldown is over (never in the past: a reset clock guides at once). */
	public static boolean guideReady(long now, long last, long cooldownTicks) {
		return last < 0 || now < last || now - last >= cooldownTicks;
	}
}
