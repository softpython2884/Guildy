package dev.guildy.command;

import dev.guildy.data.GuildRole;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure guild-management rules and formatting math (no Minecraft dependency, unit-tested).
 */
public final class GuildRules {
	private GuildRules() {
	}

	/** An actor may only kick members of a strictly lower role. */
	public static boolean canKick(GuildRole actor, GuildRole target) {
		return actor.ordinal() > target.ordinal();
	}

	/** Role after a promotion (MEMBER → OFFICER), or null when not promotable (leadership goes through transfer). */
	public static @Nullable GuildRole promoted(GuildRole role) {
		return role == GuildRole.MEMBER ? GuildRole.OFFICER : null;
	}

	/** Role after a demotion (OFFICER → MEMBER), or null when not demotable. */
	public static @Nullable GuildRole demoted(GuildRole role) {
		return role == GuildRole.OFFICER ? GuildRole.MEMBER : null;
	}

	/** True when no new member can join ({@code maxMembers} 0 = unlimited). */
	public static boolean isFull(int size, int maxMembers) {
		return maxMembers > 0 && size >= maxMembers;
	}

	/** Trims and collapses inner whitespace of a typed guild name. */
	public static String normalizeName(@Nullable String raw) {
		return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
	}

	/** Treasury after adding {@code delta} (negative removes), clamped at 0. */
	public static long clampTreasury(long treasury, long delta) {
		long result = treasury + delta;
		if (delta > 0 && result < treasury) {
			return Long.MAX_VALUE;
		}
		return Math.max(0, result);
	}

	// ---------------------------------------------------------------- progress

	/** Percentage (0..100) of {@code current} over {@code total}; 100 when total <= 0. */
	public static int percent(long current, long total) {
		if (total <= 0) {
			return 100;
		}
		long clamped = Math.max(0, Math.min(current, total));
		return (int) (clamped * 100 / total);
	}

	/** Number of filled segments of a progress bar of {@code segments} cells. */
	public static int filledSegments(long current, long total, int segments) {
		if (segments <= 0) {
			return 0;
		}
		if (total <= 0) {
			return segments;
		}
		long clamped = Math.max(0, Math.min(current, total));
		return (int) (clamped * segments / total);
	}

	/** Text progress bar using legacy '&' colors, e.g. "&a||||||&8||||". */
	public static String progressBar(long current, long total, int segments, String filledColor, String emptyColor, char cell) {
		int filled = filledSegments(current, total, segments);
		StringBuilder sb = new StringBuilder(segments + 8);
		sb.append(filledColor);
		for (int i = 0; i < filled; i++) {
			sb.append(cell);
		}
		sb.append(emptyColor);
		for (int i = filled; i < segments; i++) {
			sb.append(cell);
		}
		return sb.toString();
	}

	/**
	 * XP progress inside the current level.
	 *
	 * @param xp        total guild XP
	 * @param levelXp   total XP required for the current level
	 * @param nextXp    total XP required for the next level (ignored at max level)
	 * @param maxLevel  true when the guild cannot level up anymore
	 * @return {gained in level, needed in level}; {0, 0} at max level
	 */
	public static long[] levelProgress(long xp, long levelXp, long nextXp, boolean maxLevel) {
		if (maxLevel || nextXp <= levelXp) {
			return new long[]{0, 0};
		}
		long needed = nextXp - levelXp;
		long gained = Math.max(0, Math.min(needed, xp - levelXp));
		return new long[]{gained, needed};
	}

	// ---------------------------------------------------------------- paging

	public static int pageCount(int total, int perPage) {
		if (perPage <= 0) {
			return 1;
		}
		return Math.max(1, (total + perPage - 1) / perPage);
	}

	/** Clamps a 0-based page index into [0, pages - 1]. */
	public static int clampPage(int page, int pages) {
		return Math.max(0, Math.min(page, Math.max(1, pages) - 1));
	}

	/** Entries of a 0-based page (empty when out of range). */
	public static <T> List<T> page(List<T> list, int page, int perPage) {
		if (perPage <= 0 || page < 0) {
			return List.of();
		}
		int from = page * perPage;
		if (from >= list.size()) {
			return List.of();
		}
		return list.subList(from, Math.min(list.size(), from + perPage));
	}

	// ---------------------------------------------------------------- text

	/**
	 * Word-wraps a text containing legacy formatting codes ('§' or '&' followed by a code) into lines of at most
	 * {@code width} visible characters (longer words are kept whole). The active color/format is repeated at the
	 * start of each continuation line.
	 */
	public static List<String> wrapLegacy(String text, int width) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			lines.add("");
			return lines;
		}
		StringBuilder line = new StringBuilder();
		int visible = 0;
		for (String word : text.split(" ")) {
			int length = visibleLength(word);
			if (visible > 0 && visible + 1 + length > width) {
				String done = line.toString();
				lines.add(done);
				line = new StringBuilder(activeFormat(done));
				visible = 0;
			}
			if (visible > 0) {
				line.append(' ');
				visible++;
			}
			line.append(word);
			visible += length;
		}
		lines.add(line.toString());
		return lines;
	}

	private static boolean isCodeChar(char c) {
		return c == '§' || c == '&';
	}

	private static boolean isCode(char c) {
		return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) >= 0;
	}

	/** Length of the text without formatting codes. */
	public static int visibleLength(String text) {
		int length = 0;
		for (int i = 0; i < text.length(); i++) {
			if (isCodeChar(text.charAt(i)) && i + 1 < text.length() && isCode(text.charAt(i + 1))) {
				i++;
			} else {
				length++;
			}
		}
		return length;
	}

	/** Formatting codes still active at the end of the text (last color + following styles). */
	public static String activeFormat(String text) {
		StringBuilder active = new StringBuilder();
		for (int i = 0; i + 1 < text.length(); i++) {
			char c = text.charAt(i);
			char code = Character.toLowerCase(text.charAt(i + 1));
			if (isCodeChar(c) && isCode(code)) {
				boolean style = code >= 'k' && code <= 'o';
				if (!style) {
					active.setLength(0);
				}
				if (code != 'r') {
					active.append(c).append(code);
				}
				i++;
			}
		}
		return active.toString();
	}

	// ---------------------------------------------------------------- ranking

	/** Guild ranking order: level desc, then XP desc, then name (case-insensitive) asc. */
	public static int compareRanking(int levelA, long xpA, String nameA, int levelB, long xpB, String nameB) {
		if (levelA != levelB) {
			return Integer.compare(levelB, levelA);
		}
		if (xpA != xpB) {
			return Long.compare(xpB, xpA);
		}
		return String.CASE_INSENSITIVE_ORDER.compare(nameA, nameB);
	}
}
