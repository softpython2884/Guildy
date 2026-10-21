package dev.guildy.scroll;

/**
 * Bounds of the spells that weaken or hold monsters (pure Java, unit-tested): the damage taken by a monster cursed by
 * Fléau ({@link #vulnerability}, {@link #multiplier}, never above {@link #MAX_VULNERABILITY} %, several curses never add
 * up: {@link #stack}) and how long Chaînes spectrales hold a monster ({@link #holdTicks}). Bosses (ruin guardians, horde
 * bosses, vanilla bosses) only get their {@code bossShare} percentage of the effect.
 */
public final class DebuffRules {
	/** Largest extra damage (percent) a cursed monster ever takes. */
	public static final int MAX_VULNERABILITY = 100;

	private DebuffRules() {
	}

	/**
	 * Extra damage (percent, 0..{@link #MAX_VULNERABILITY}) a monster takes under a curse of {@code percent}: the full value,
	 * or {@code bossShare} percent of it on a boss.
	 */
	public static double vulnerability(double percent, boolean boss, double bossShare) {
		double value = finite(percent);
		if (boss) {
			value = value * clamp(finite(bossShare), 0, 100) / 100.0;
		}
		return clamp(value, 0, MAX_VULNERABILITY);
	}

	/** Damage multiplier of a vulnerability (percent): 1 to 2. */
	public static double multiplier(double percent) {
		return 1.0 + clamp(finite(percent), 0, MAX_VULNERABILITY) / 100.0;
	}

	/** Two curses on the same monster: the strongest one counts (they never add up). */
	public static double stack(double current, double added) {
		return Math.max(clamp(finite(current), 0, MAX_VULNERABILITY), clamp(finite(added), 0, MAX_VULNERABILITY));
	}

	/** Ticks a monster stays chained: the spell's duration, or {@code bossShare} percent of it on a boss (1 tick at least). */
	public static int holdTicks(int ticks, boolean boss, double bossShare) {
		int full = Math.max(1, ticks);
		if (!boss) {
			return full;
		}
		return Math.max(1, (int) Math.round(full * clamp(finite(bossShare), 0, 100) / 100.0));
	}

	private static double finite(double value) {
		return Double.isFinite(value) ? value : 0;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
