package dev.guildy.ruin;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure rules of the ruins (no Minecraft classes, unit-tested): encounter state machine, reset and respawn timing,
 * escort size, reward splitting, research progress and template / marker naming.
 */
public final class RuinRules {
	/** Data marker metadata of the boss spawn cell (exactly one per ruin). */
	public static final String MARKER_BOSS = "guildy:boss";
	/** Data marker metadata of an escort spawn cell. */
	public static final String MARKER_MINION = "guildy:minion";
	/** Data marker metadata of the entrance (shown by maps and compasses). */
	public static final String MARKER_ENTRANCE = "guildy:entrance";
	/** Template paths of ruins start with this prefix ({@code guildy:ruin/<id>} or {@code guildy:ruin/<id>/<piece>}). */
	public static final String TEMPLATE_PREFIX = "ruin/";

	public enum Marker {
		BOSS,
		MINION,
		ENTRANCE
	}

	/** What the encounter loop must do for a ruin this second. */
	public enum Action {
		NONE,
		/** Spawn the guardian and its escort. */
		AWAKEN,
		/** Nobody around for too long: despawn, the guardian is back at full health next time. */
		RESET,
		/** The guardian's chunk was unloaded (or it vanished): the fight is cancelled, nothing is rewarded. */
		CANCEL,
		/** A defeated guardian is back. */
		REARM
	}

	private RuinRules() {
	}

	/**
	 * Encounter state machine (one call per ruin and per second).
	 *
	 * @param playerInActivation a survival / adventure player is inside the awakening area
	 * @param secondsAlone       seconds since the last player was seen in the fight area
	 * @param bossLoaded         the guardian entity is loaded and alive (AWAKE only)
	 * @param rearmed            a defeated guardian is due back (CLEARED only, see {@link #rearmed})
	 */
	public static Action decide(RuinState state, boolean enabled, boolean playerInActivation, int secondsAlone, int resetSeconds,
			boolean bossLoaded, boolean rearmed) {
		return switch (state) {
			case DORMANT -> enabled && playerInActivation ? Action.AWAKEN : Action.NONE;
			case AWAKE -> !bossLoaded ? Action.CANCEL : secondsAlone >= Math.max(1, resetSeconds) ? Action.RESET : Action.NONE;
			case CLEARED -> rearmed ? Action.REARM : Action.NONE;
		};
	}

	/** A defeated guardian comes back once {@code respawnDays} in-game days have passed (never when 0 or less). */
	public static boolean rearmed(long clearedDay, long nowDay, int respawnDays) {
		return respawnDays > 0 && nowDay - clearedDay >= respawnDays;
	}

	/**
	 * Days before a defeated guardian is back (0 = due now), or -1 when it never comes back. A clock moved backwards
	 * (time set) never makes the wait longer than {@code respawnDays}.
	 */
	public static long daysLeft(long clearedDay, long nowDay, int respawnDays) {
		if (respawnDays <= 0) {
			return -1;
		}
		long elapsed = Math.max(0, nowDay - clearedDay);
		return Math.max(0, respawnDays - elapsed);
	}

	/** Escort size: {@code base} + {@code perExtraPlayer} for each player beyond the first, capped by {@code max}. */
	public static int minionCount(int base, int perExtraPlayer, int max, int players) {
		int count = Math.max(0, base) + Math.max(0, perExtraPlayer) * Math.max(0, players - 1);
		return Math.max(0, Math.min(Math.max(0, max), count));
	}

	/** Summon ability: how many minions may still join (never above the alive cap). */
	public static int summonCount(int wanted, int alive, int maxAlive) {
		return Math.max(0, Math.min(wanted, maxAlive - alive));
	}

	/**
	 * Splits the guild XP of a victory between the guilds of the participants, proportionally to their number of
	 * participants, at least {@code minPerGuild} each (rounded down, the remainder goes to the largest groups first).
	 *
	 * @return XP per guild (insertion order of {@code participants}); empty when nobody took part
	 */
	public static <K> Map<K, Long> splitXp(Map<K, Integer> participants, long total, long minPerGuild) {
		Map<K, Long> result = new LinkedHashMap<>();
		long people = 0;
		for (int n : participants.values()) {
			people += Math.max(0, n);
		}
		if (people <= 0 || total <= 0) {
			for (K key : participants.keySet()) {
				if (participants.get(key) > 0) {
					result.put(key, Math.max(0, Math.min(minPerGuild, total)));
				}
			}
			return result;
		}
		long given = 0;
		for (Map.Entry<K, Integer> entry : participants.entrySet()) {
			int n = Math.max(0, entry.getValue());
			if (n == 0) {
				continue;
			}
			long share = total * n / people;
			result.put(entry.getKey(), share);
			given += share;
		}
		long remainder = total - given;
		List<K> order = new ArrayList<>(result.keySet());
		order.sort(Comparator.comparingInt((K key) -> participants.get(key)).reversed());
		for (int i = 0; remainder > 0 && !order.isEmpty(); i = (i + 1) % order.size()) {
			K key = order.get(i);
			result.put(key, result.get(key) + 1);
			remainder--;
		}
		for (Map.Entry<K, Long> entry : result.entrySet()) {
			entry.setValue(Math.max(entry.getValue(), minPerGuild));
		}
		return result;
	}

	/** Research seconds added this second by {@code workers} cartographers (at most {@code maxWorkers} count). */
	public static int researchWorkers(int workers, int maxWorkers) {
		return Math.max(0, Math.min(workers, Math.max(1, maxWorkers)));
	}

	/** Progress in percent (0..100). */
	public static int percent(double progress, double required) {
		if (required <= 0) {
			return 100;
		}
		return (int) Math.max(0, Math.min(100, Math.floor(progress * 100.0 / required)));
	}

	/** Text progress bar of {@code width} cells: filled cells use {@code full}, the others {@code empty}. */
	public static String bar(double ratio, int width, char full, char empty) {
		int cells = Math.max(1, width);
		int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * cells);
		StringBuilder sb = new StringBuilder(cells);
		for (int i = 0; i < cells; i++) {
			sb.append(i < filled ? full : empty);
		}
		return sb.toString();
	}

	/** Seconds of work still needed by {@code workers} cartographers (rounded up, -1 when nobody works). */
	public static long secondsLeft(double progress, double required, int workers) {
		if (workers <= 0) {
			return -1;
		}
		double left = Math.max(0, required - progress);
		return (long) Math.ceil(left / workers);
	}

	/** True for a ruin template path ({@code ruin/<id>} or {@code ruin/<id>/<piece>}). */
	public static boolean isRuinTemplate(@Nullable String path) {
		return path != null && path.startsWith(TEMPLATE_PREFIX) && path.length() > TEMPLATE_PREFIX.length();
	}

	/** Ruin id of a template or structure path: the first segment after {@code ruin/} (null if not a ruin path). */
	public static @Nullable String ruinIdOf(@Nullable String path) {
		if (!isRuinTemplate(path)) {
			return null;
		}
		String rest = path.substring(TEMPLATE_PREFIX.length());
		int slash = rest.indexOf('/');
		String id = slash < 0 ? rest : rest.substring(0, slash);
		return id.isEmpty() ? null : id;
	}

	/** Kind of a data marker from its metadata, or null when the marker is not one of ours. */
	public static @Nullable Marker marker(@Nullable String metadata) {
		if (metadata == null) {
			return null;
		}
		return switch (metadata.trim()) {
			case MARKER_BOSS -> Marker.BOSS;
			case MARKER_MINION -> Marker.MINION;
			case MARKER_ENTRANCE -> Marker.ENTRANCE;
			default -> null;
		};
	}

	/**
	 * Picks the abilities ready now (cooldown over, health condition met), in their list order. The caller uses the
	 * first one (rotated by {@code offset} so that a boss with several ready abilities does not always use the same).
	 */
	public static List<Integer> readyAbilities(long now, long[] readyAt, double[] healthBelow, double healthRatio, int offset) {
		List<Integer> ready = new ArrayList<>();
		int n = Math.min(readyAt.length, healthBelow.length);
		for (int k = 0; k < n; k++) {
			int i = Math.floorMod(k + offset, n);
			if (now >= readyAt[i] && healthRatio <= healthBelow[i] + 1.0E-9) {
				ready.add(i);
			}
		}
		return Collections.unmodifiableList(ready);
	}

	/** Cooldown in ticks after an ability, shortened by enrage ({@code factor} &lt; 1), at least one second. */
	public static long cooldownTicks(double seconds, double factor) {
		return Math.max(20L, Math.round(Math.max(0, seconds) * 20.0 * Math.max(0.05, factor)));
	}
}
