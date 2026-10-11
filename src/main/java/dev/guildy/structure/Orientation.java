package dev.guildy.structure;

/**
 * Horizontal orientation math with plain ints (pure, unit-tested).
 * <p>
 * A structure is a vertical wall. Its front face points to {@code (fx, fz)} (a horizontal unit vector, e.g. south =
 * (0, 1)). A player standing in front looks towards {@code -facing}; their right-hand side is the structure's
 * "right" axis {@code (fz, -fx)}: columns grow along it, rows grow downwards.
 */
public final class Orientation {
	private Orientation() {
	}

	public static int rightX(int fx, int fz) {
		return fz;
	}

	public static int rightZ(int fx, int fz) {
		return -fx;
	}

	/**
	 * Offset {dx, dy, dz} of cell (col, row) from the origin, the bottom-left block as seen from the front.
	 */
	public static int[] cellOffset(int col, int row, int height, int fx, int fz) {
		return new int[] {rightX(fx, fz) * col, height - 1 - row, rightZ(fx, fz) * col};
	}

	/** World offset {dx, dy, dz} for a displacement of {@code right} blocks along the right axis and {@code up} blocks up. */
	public static int[] offset(int right, int up, int fx, int fz) {
		return new int[] {rightX(fx, fz) * right, up, rightZ(fx, fz) * right};
	}

	/** True for the 4 horizontal unit vectors. */
	public static boolean isHorizontalUnit(int fx, int fz) {
		return Math.abs(fx) + Math.abs(fz) == 1;
	}
}
