package dev.guildy.structure;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A data-driven multiblock (one entry of structures.json5). Pure data + pure validation, no Minecraft classes.
 */
public class StructureDefinition {
	/** Max size so the plan fits in the menu (9 columns, 4 rows). */
	public static final int MAX_WIDTH = 9;
	public static final int MAX_HEIGHT = 4;

	public String id = "";
	public String name = "";
	public List<String> description = new ArrayList<>();
	/** Lines shown to the guild the first time this structure is built. */
	public List<String> tutorial = new ArrayList<>();
	/** Menu icon (item id). */
	public String icon = "minecraft:paper";
	public int minLevel = 1;
	/** Treasury gold needed to unlock it (0 = unlocked from the start). */
	public int unlockCost = 0;
	public int maxPerGuild = 1;
	/** Rows top to bottom, characters left to right as seen from the front. '.' = any block. */
	public List<String> rows = new ArrayList<>();
	/** Character → block id ("minecraft:bookshelf") or block tag ("#minecraft:logs"). */
	public Map<String, String> key = new LinkedHashMap<>();
	/** Optional display names of the key characters (used for tags). */
	public Map<String, String> keyLabels = new LinkedHashMap<>();
	/** Cells [col, row] receiving a managed sign on their front face; the list order is the sign index. */
	public List<int[]> signCells = new ArrayList<>();

	private transient @Nullable StructurePattern pattern;

	public StructureDefinition() {
	}

	/** Parsed pattern (null until {@link #check()} succeeded). */
	public @Nullable StructurePattern pattern() {
		return pattern;
	}

	public @Nullable String keyFor(char c) {
		return key == null ? null : key.get(String.valueOf(c));
	}

	public @Nullable String labelFor(char c) {
		return keyLabels == null ? null : keyLabels.get(String.valueOf(c));
	}

	public int signCount() {
		return signCells == null ? 0 : signCells.size();
	}

	/**
	 * Pure validation: fixes nulls, clamps numbers, parses the pattern.
	 *
	 * @return blocking errors (empty = usable). Block ids/tags are checked separately (needs the game registries).
	 */
	public List<String> check() {
		List<String> errors = new ArrayList<>();
		pattern = null;
		if (id == null || !id.matches("[a-z0-9_]{1,32}")) {
			errors.add("invalid id '" + id + "' (allowed: a-z 0-9 _, max 32)");
		}
		if (name == null || name.isBlank()) {
			name = id == null ? "?" : id;
		}
		if (description == null) {
			description = new ArrayList<>();
		}
		if (tutorial == null) {
			tutorial = new ArrayList<>();
		}
		if (icon == null || icon.isBlank()) {
			icon = "minecraft:paper";
		}
		if (key == null) {
			key = new LinkedHashMap<>();
		}
		if (keyLabels == null) {
			keyLabels = new LinkedHashMap<>();
		}
		if (signCells == null) {
			signCells = new ArrayList<>();
		}
		minLevel = Math.max(1, minLevel);
		unlockCost = Math.max(0, unlockCost);
		maxPerGuild = Math.max(1, maxPerGuild);
		for (String k : key.keySet()) {
			if (k == null || k.length() != 1) {
				errors.add("key '" + k + "' must be a single character");
			}
		}
		StructurePattern parsed;
		try {
			parsed = StructurePattern.parse(rows);
		} catch (IllegalArgumentException e) {
			errors.add(e.getMessage());
			return errors;
		}
		if (parsed.width() > MAX_WIDTH || parsed.height() > MAX_HEIGHT) {
			errors.add("pattern too large: " + parsed.width() + "x" + parsed.height() + " (max " + MAX_WIDTH + "x" + MAX_HEIGHT + ")");
		}
		for (char c : parsed.keys()) {
			String value = keyFor(c);
			if (value == null || value.isBlank()) {
				errors.add("no key entry for '" + c + "'");
			}
		}
		Set<Long> seen = new HashSet<>();
		for (int[] cell : signCells) {
			if (cell == null || cell.length != 2) {
				errors.add("signCells entries must be [col, row]");
				continue;
			}
			int col = cell[0];
			int row = cell[1];
			if (!parsed.inBounds(col, row)) {
				errors.add("sign cell [" + col + "," + row + "] is outside the pattern");
			} else if (parsed.isWildcard(col, row)) {
				errors.add("sign cell [" + col + "," + row + "] is a wildcard cell (needs a solid block)");
			} else if (!seen.add(((long) col << 32) | (row & 0xFFFFFFFFL))) {
				errors.add("duplicate sign cell [" + col + "," + row + "]");
			}
		}
		if (errors.isEmpty()) {
			pattern = parsed;
		}
		return errors;
	}
}
