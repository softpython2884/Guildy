package dev.guildy.builds;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * What a preview shows of a schematic (pure), cells indexed {@code (y * sz + z) * sx + x}: ghost blocks for every block
 * when they fit, else for the blocks seen from outside (the shell); a larger schematic is shown as a cloud of particles
 * at scale 1:1, one per shell block: each one near the player, a sample changing at every pulse further away.
 */
final class PreviewCells {
	private PreviewCells() {
	}

	/** Cells shown as ghost blocks: all of them, else the shell, else null (too many: particles instead). */
	static int @Nullable [] ghosts(int sx, int sy, int sz, IntPredicate shown, int max, Supplier<int[]> shell) {
		if (max <= 0) {
			return new int[0];
		}
		int total = sx * sy * sz;
		int[] all = new int[Math.min(total, max)];
		int count = 0;
		for (int i = 0; i < total; i++) {
			if (shown.test(i)) {
				if (count == max) {
					count = -1;
					break;
				}
				all[count++] = i;
			}
		}
		if (count >= 0) {
			return Arrays.copyOf(all, count);
		}
		int[] outside = shell.get();
		return outside.length <= max ? outside : null;
	}

	/** Shown cells touching a cell not shown or the outside of the box, in index order. */
	static int[] shell(int sx, int sy, int sz, IntPredicate shown) {
		int total = sx * sy * sz;
		int[] list = new int[1024];
		int count = 0;
		for (int i = 0; i < total; i++) {
			if (!shown.test(i)) {
				continue;
			}
			int x = i % sx;
			int z = (i / sx) % sz;
			int y = i / (sx * sz);
			if (x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1
					|| !shown.test(i - 1) || !shown.test(i + 1) || !shown.test(i - sx) || !shown.test(i + sx)
					|| !shown.test(i - sx * sz) || !shown.test(i + sx * sz)) {
				if (count == list.length) {
					list = Arrays.copyOf(list, count * 2);
				}
				list[count++] = i;
			}
		}
		return Arrays.copyOf(list, count);
	}

	/**
	 * The shell cells given a particle at one pulse: those within {@code radius} of the player (local position), nearest
	 * first, on up to two thirds of {@code max}; then, with what is left, one cell every N of the rest, starting further at
	 * each pulse, so that the whole shape shows and fills in over time.
	 */
	static int[] pick(int sx, int sy, int sz, int[] shell, double px, double py, double pz, int radius, int max, int pulse) {
		if (max <= 0 || shell.length == 0) {
			return new int[0];
		}
		double r2 = (double) radius * radius;
		// Shell cells are in index order, so a slab of layers is one range of the array.
		int fromY = Math.max(0, (int) Math.floor(py - radius));
		int toY = Math.min(sy - 1, (int) Math.floor(py + radius));
		List<long[]> near = new ArrayList<>();
		if (fromY <= toY) {
			int start = lowerBound(shell, fromY * sx * sz);
			int end = lowerBound(shell, (toY + 1) * sx * sz);
			for (int k = start; k < end; k++) {
				double d = distance2(shell[k], sx, sz, px, py, pz);
				if (d <= r2) {
					near.add(new long[] {Double.doubleToLongBits(d), shell[k]});
				}
			}
		}
		int nearMax = Math.max(1, max * 2 / 3);
		if (near.size() > nearMax) {
			near.sort((a, b) -> Double.compare(Double.longBitsToDouble(a[0]), Double.longBitsToDouble(b[0])));
			near = near.subList(0, nearMax);
		}
		int[] picked = new int[max];
		int count = 0;
		for (long[] cell : near) {
			picked[count++] = (int) cell[1];
		}
		int left = max - count;
		if (left > 0) {
			int stride = Math.max(1, (shell.length + left - 1) / left);
			for (int k = Math.floorMod(pulse, stride); k < shell.length && count < max; k += stride) {
				if (distance2(shell[k], sx, sz, px, py, pz) > r2) {
					picked[count++] = shell[k];
				}
			}
		}
		return Arrays.copyOf(picked, count);
	}

	private static double distance2(int index, int sx, int sz, double px, double py, double pz) {
		double dx = index % sx + 0.5 - px;
		double dz = (index / sx) % sz + 0.5 - pz;
		double dy = index / (sx * sz) + 0.5 - py;
		return dx * dx + dy * dy + dz * dz;
	}

	/** First position whose value is at least {@code key} (array sorted). */
	private static int lowerBound(int[] sorted, int key) {
		int low = 0;
		int high = sorted.length;
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (sorted[mid] < key) {
				low = mid + 1;
			} else {
				high = mid;
			}
		}
		return low;
	}
}
