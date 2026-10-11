package dev.guildy.structure;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parsed rectangular grid of a structure definition (pure: no Minecraft classes).
 * Row 0 is the top row, column 0 the leftmost block as seen by a player standing in front of the wall.
 * Wildcard cells ('.', or blanks) accept any block and are not part of the structure.
 */
public final class StructurePattern {
	public static final char ANY = '.';

	private final int width;
	private final int height;
	private final char[][] cells;

	private StructurePattern(char[][] cells, int width) {
		this.cells = cells;
		this.width = width;
		this.height = cells.length;
	}

	/**
	 * Parses rows (top to bottom). Shorter rows are padded with wildcards.
	 *
	 * @throws IllegalArgumentException with a readable message when the grid is empty
	 */
	public static StructurePattern parse(List<String> rows) {
		if (rows == null || rows.isEmpty()) {
			throw new IllegalArgumentException("'rows' is empty");
		}
		int width = 0;
		for (String row : rows) {
			if (row == null) {
				throw new IllegalArgumentException("'rows' contains null");
			}
			width = Math.max(width, row.length());
		}
		if (width == 0) {
			throw new IllegalArgumentException("'rows' only contains empty strings");
		}
		char[][] cells = new char[rows.size()][width];
		boolean any = false;
		for (int r = 0; r < rows.size(); r++) {
			String row = rows.get(r);
			for (int c = 0; c < width; c++) {
				char ch = c < row.length() ? row.charAt(c) : ANY;
				if (isWildcard(ch)) {
					ch = ANY;
				} else {
					any = true;
				}
				cells[r][c] = ch;
			}
		}
		if (!any) {
			throw new IllegalArgumentException("'rows' only contains wildcards");
		}
		return new StructurePattern(cells, width);
	}

	/** Wildcard characters: '.', space and tab (tabs can appear when a config file is re-indented). */
	public static boolean isWildcard(char c) {
		return c == ANY || c == ' ' || c == '\t';
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	/** Pattern character at (col, row); row 0 = top. */
	public char at(int col, int row) {
		return cells[row][col];
	}

	public boolean isWildcard(int col, int row) {
		return cells[row][col] == ANY;
	}

	public boolean inBounds(int col, int row) {
		return col >= 0 && row >= 0 && col < width && row < height;
	}

	/** Distinct non-wildcard characters, in reading order. */
	public Set<Character> keys() {
		Set<Character> keys = new LinkedHashSet<>();
		for (char[] row : cells) {
			for (char c : row) {
				if (c != ANY) {
					keys.add(c);
				}
			}
		}
		return keys;
	}

	/** Number of non-wildcard cells (blocks that make up the structure). */
	public int blockCount() {
		int count = 0;
		for (char[] row : cells) {
			for (char c : row) {
				if (c != ANY) {
					count++;
				}
			}
		}
		return count;
	}

	/** The rows as strings (wildcards normalized to '.'). */
	public List<String> rows() {
		String[] rows = new String[height];
		for (int r = 0; r < height; r++) {
			rows[r] = new String(cells[r]);
		}
		return List.of(rows);
	}
}
