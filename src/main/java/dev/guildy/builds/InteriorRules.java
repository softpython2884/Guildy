package dev.guildy.builds;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/**
 * Which air cells of a schematic are dug out of the terrain (pure, unit tested; schematic coordinates, cell order of
 * {@link SchematicData}). The cells that receive a block always replace the natural block there; the air cells depend
 * on the interior mode:
 * <ul>
 * <li>{@link Mode#KEEP}: never (the terrain stays wherever the schematic has air);</li>
 * <li>{@link Mode#HOLLOW} (default): only the air the structure encloses: between the lowest and the highest block of
 * its column (rooms with a floor and a roof), or cut off from the edges of its layer by blocks (a walled courtyard, a
 * roofless room), plus the two cells in front of and behind each door (a door dug into a hillside opens onto air).
 * The terrain around the structure and above its roof stays intact: a house placed in a mountain does not cut it in
 * two;</li>
 * <li>{@link Mode#BOX}: every air cell of the box.</li>
 * </ul>
 * Void cells (not listed by a vanilla structure, structure voids) are never touched and count as "outside".
 */
public final class InteriorRules {
	public enum Mode {
		KEEP,
		HOLLOW,
		BOX;

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Mode parse(@Nullable String text, Mode fallback) {
			if (text != null) {
				for (Mode mode : values()) {
					if (mode.name().equalsIgnoreCase(text.trim())) {
						return mode;
					}
				}
			}
			return fallback;
		}
	}

	/** What a cell holds. */
	public static final byte VOID = 0;
	public static final byte AIR = 1;
	public static final byte BLOCK = 2;

	/** A door (lower half) of the schematic: its cell and the axis it opens along (true = x, false = z). */
	public record Door(int x, int y, int z, boolean alongX) {
	}

	private InteriorRules() {
	}

	/**
	 * Air cells to clear.
	 *
	 * @param kinds one of {@link #VOID}, {@link #AIR}, {@link #BLOCK} per cell
	 */
	public static BitSet clearCells(int sx, int sy, int sz, byte[] kinds, List<Door> doors, Mode mode) {
		BitSet clear = new BitSet(kinds.length);
		if (mode == Mode.KEEP) {
			return clear;
		}
		if (mode == Mode.BOX) {
			for (int i = 0; i < kinds.length; i++) {
				if (kinds[i] == AIR) {
					clear.set(i);
				}
			}
			return clear;
		}
		// Column rule: between the lowest and the highest block of the column.
		for (int z = 0; z < sz; z++) {
			for (int x = 0; x < sx; x++) {
				int low = -1;
				int high = -1;
				for (int y = 0; y < sy; y++) {
					if (kinds[(y * sz + z) * sx + x] == BLOCK) {
						if (low < 0) {
							low = y;
						}
						high = y;
					}
				}
				for (int y = low + 1; y < high; y++) {
					int index = (y * sz + z) * sx + x;
					if (kinds[index] == AIR) {
						clear.set(index);
					}
				}
			}
		}
		// Layer rule: air that cannot reach the edge of its layer without crossing a block.
		boolean[] reached = new boolean[sx * sz];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int y = 0; y < sy; y++) {
			java.util.Arrays.fill(reached, false);
			int base = y * sz * sx;
			for (int z = 0; z < sz; z++) {
				for (int x = 0; x < sx; x++) {
					if ((x == 0 || z == 0 || x == sx - 1 || z == sz - 1) && kinds[base + z * sx + x] != BLOCK) {
						int column = z * sx + x;
						if (!reached[column]) {
							reached[column] = true;
							queue.add(column);
						}
					}
				}
			}
			while (!queue.isEmpty()) {
				int column = queue.poll();
				int x = column % sx;
				int z = column / sx;
				visit(x + 1, z, sx, sz, base, kinds, reached, queue);
				visit(x - 1, z, sx, sz, base, kinds, reached, queue);
				visit(x, z + 1, sx, sz, base, kinds, reached, queue);
				visit(x, z - 1, sx, sz, base, kinds, reached, queue);
			}
			for (int column = 0; column < sx * sz; column++) {
				if (!reached[column] && kinds[base + column] == AIR) {
					clear.set(base + column);
				}
			}
		}
		// Doors: the cells in front of and behind them, both heights.
		for (Door door : doors) {
			for (int side = -1; side <= 1; side += 2) {
				int x = door.x() + (door.alongX() ? side : 0);
				int z = door.z() + (door.alongX() ? 0 : side);
				for (int dy = 0; dy <= 1; dy++) {
					int y = door.y() + dy;
					if (x < 0 || z < 0 || y < 0 || x >= sx || z >= sz || y >= sy) {
						continue;
					}
					int index = (y * sz + z) * sx + x;
					if (kinds[index] == AIR) {
						clear.set(index);
					}
				}
			}
		}
		return clear;
	}

	private static void visit(int x, int z, int sx, int sz, int base, byte[] kinds, boolean[] reached, ArrayDeque<Integer> queue) {
		if (x < 0 || z < 0 || x >= sx || z >= sz) {
			return;
		}
		int column = z * sx + x;
		if (reached[column] || kinds[base + column] == BLOCK) {
			return;
		}
		reached[column] = true;
		queue.add(column);
	}
}
