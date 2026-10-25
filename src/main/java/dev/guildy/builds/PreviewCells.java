package dev.guildy.builds;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * What a preview shows of a schematic (pure), cells indexed {@code (y * sz + z) * sx + x}: ghost blocks for every block
 * when they fit, else for the blocks seen from outside (the shell); a larger schematic only shows its outline (Litematica
 * shows it block by block: {@link SyncmaticaLink}).
 */
final class PreviewCells {
	private PreviewCells() {
	}

	/** Cells shown as ghost blocks: all of them, else the shell, else null (too many: only the outline). */
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
}
