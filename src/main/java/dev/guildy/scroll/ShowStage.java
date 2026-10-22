package dev.guildy.scroll;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the launch circles of a show stand (pure Java, unit-tested). The caster faces their audience: the stage is a line
 * perpendicular to their view, {@link #BACK} blocks behind them, {@code width} blocks long and centred on them, slightly
 * curved toward the audience at both ends (a crescent around the caster, {@link #ARC} of the width). Points are ordered
 * from left to right as the audience sees them, never closer than {@link #MIN_SPACING} blocks.
 */
public final class ShowStage {
	/** Distance (blocks) from the caster back to the middle of the stage. */
	public static final double BACK = 3.0;
	/** How far the ends of the stage come toward the audience, as a share of the width. */
	public static final double ARC = 0.12;
	/** Smallest gap (blocks) between two launch points. */
	public static final double MIN_SPACING = 1.5;

	private ShowStage() {
	}

	/** A launch point: its rank from the audience's left, its position along the stage (-1 = left end, 1 = right end). */
	public record Spot(int index, double u, double x, double z) {
	}

	/**
	 * The stage: the caster's horizontal view direction (toward the audience, unit), the audience's right (unit), the middle of
	 * the stage, its width and the gap between two neighbouring points, and the points.
	 */
	public record Layout(double forwardX, double forwardZ, double rightX, double rightZ, double centerX, double centerZ, double width, double spacing,
			List<Spot> spots) {
		public Layout {
			spots = List.copyOf(spots);
		}
	}

	/** Points of a stage this wide (asked count, at most one every {@link #MIN_SPACING} blocks, at least 1). */
	public static int count(double width, int points) {
		int fit = 1 + (int) Math.floor(Math.max(0, width) / MIN_SPACING + 1.0E-9);
		return Math.max(1, Math.min(points, fit));
	}

	/**
	 * The stage of a caster standing at (x, z) and looking along (lookX, lookZ) (any length; a vertical look = south).
	 *
	 * @param width  blocks from the first point to the last
	 * @param points launch points asked for (see {@link #count})
	 */
	public static Layout layout(double x, double z, double lookX, double lookZ, double width, int points) {
		double length = Math.hypot(lookX, lookZ);
		double fx = length < 1.0E-6 ? 0 : lookX / length;
		double fz = length < 1.0E-6 ? 1 : lookZ / length;
		// The audience looks back at the caster (along -forward): their right hand is (forward.z, -forward.x).
		double rx = fz;
		double rz = -fx;
		double w = Math.max(0, width);
		int n = count(w, points);
		double cx = x - fx * BACK;
		double cz = z - fz * BACK;
		List<Spot> spots = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double u = n == 1 ? 0 : -1 + 2.0 * i / (n - 1);
			double side = u * w / 2;
			double toward = ARC * w * u * u;
			spots.add(new Spot(i, u, cx + rx * side + fx * toward, cz + rz * side + fz * toward));
		}
		return new Layout(fx, fz, rx, rz, cx, cz, w, n > 1 ? w / (n - 1) : 0, spots);
	}

	/** Rank of point {@code index} from the middle outward (0 = middle; neighbours at the same distance share a rank). */
	public static int fromMiddle(int index, int count) {
		double middle = (count - 1) / 2.0;
		return (int) Math.floor(Math.abs(index - middle) + 1.0E-9);
	}
}
