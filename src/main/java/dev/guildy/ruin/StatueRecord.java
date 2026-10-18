package dev.guildy.ruin;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One jaguar statue of the trails that lead to the grand temple (persisted in {@code <world>/guildy/ruins.json}): planned
 * by the legend research, placed by the mod when its chunk loads, then guiding the players who come near toward its next
 * target. A statue used by several trails toward different temples has one route per temple.
 */
public class StatueRecord {
	public enum Status {
		/** Planned, waiting for its chunk to load. */
		PENDING,
		/** Standing in the world. */
		PLACED,
		/** No site found around the planned point: the trail skips it. */
		FAILED
	}

	public int id;
	/** Dimension id ("minecraft:overworld"). */
	public String dimension = "";
	public Status status = Status.PENDING;
	/** Planned point (block coordinates) and the ground height estimated from the terrain noise (0 when unknown). */
	public int plannedX;
	public int plannedY;
	public int plannedZ;
	/** Structure template id ("guildy:ruin/statue_jaguar"). */
	public String template = "";
	/** Placed: the centre of the statue at the level of the ground (its anchor). */
	public int x;
	public int y;
	public int z;
	/** Placed: quarter turns clockwise of the template (0 = its front faces north). */
	public int rotation;
	/** Placed: the box of the template in the world. */
	public int minX;
	public int minY;
	public int minZ;
	public int maxX;
	public int maxY;
	public int maxZ;
	/** Placed: the glowing eyes (light blocks of the template). */
	public List<BlockPos> eyes = new ArrayList<>();
	/** Where the statue leads: one route per temple (the first one turns it). */
	public List<Route> routes = new ArrayList<>();
	public long createdAt;
	public long placedAt;
	/** Site searches that found nothing (the second one looks twice as far, then the statue is abandoned). */
	public int attempts;
	/** Guidances played for players (statistics, admin list). */
	public int timesGuided;

	public StatueRecord() {
	}

	/** One temple the statue leads to: its next statue (-1 = the temple itself) and the last known target position. */
	public static class Route {
		/** Ruin key of the temple. */
		public String temple = "";
		/** Next statue on the way to the temple, -1 = the temple. */
		public int next = -1;
		/** The next target (the next statue's eyes or planned point, the temple's aim point), remembered. */
		public int toX;
		public int toY;
		public int toZ;
		/** Trail that created the route. */
		public int trail;

		public Route() {
		}

		public Route(String temple, int trail) {
			this.temple = temple;
			this.trail = trail;
		}
	}

	public boolean isPlaced() {
		return status == Status.PLACED;
	}

	/** The route toward a temple, or null. */
	public @Nullable Route route(String temple) {
		for (Route route : routes) {
			if (route.temple.equals(temple)) {
				return route;
			}
		}
		return null;
	}

	/** Where it stands (placed) or is planned (block x / z). */
	public int posX() {
		return isPlaced() ? x : plannedX;
	}

	public int posZ() {
		return isPlaced() ? z : plannedZ;
	}

	/** What a guidance toward this statue aims at: the middle of its eyes (placed), else its planned point. */
	public double[] aim() {
		if (isPlaced() && !eyes.isEmpty()) {
			double sx = 0;
			double sy = 0;
			double sz = 0;
			for (BlockPos eye : eyes) {
				sx += eye.getX() + 0.5;
				sy += eye.getY() + 0.5;
				sz += eye.getZ() + 0.5;
			}
			return new double[] {sx / eyes.size(), sy / eyes.size(), sz / eyes.size()};
		}
		if (isPlaced()) {
			return new double[] {x + 0.5, y + 8.5, z + 0.5};
		}
		return new double[] {plannedX + 0.5, plannedY + 8.5, plannedZ + 0.5};
	}

	/** Inside the placed box, inflated by {@code margin}. */
	public boolean contains(int bx, int by, int bz, int margin) {
		return isPlaced() && bx >= minX - margin && bx <= maxX + margin && by >= minY - margin && by <= maxY + margin
				&& bz >= minZ - margin && bz <= maxZ + margin;
	}

	void fixNulls() {
		if (dimension == null) {
			dimension = "";
		}
		if (status == null) {
			status = Status.PENDING;
		}
		if (template == null) {
			template = "";
		}
		if (eyes == null) {
			eyes = new ArrayList<>();
		}
		eyes.removeIf(p -> p == null);
		if (routes == null) {
			routes = new ArrayList<>();
		}
		routes.removeIf(r -> r == null || r.temple == null || r.temple.isEmpty());
	}
}
