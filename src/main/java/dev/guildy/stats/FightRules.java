package dev.guildy.stats;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure rules of the fight statistics: who a hit or a heal belongs to (owner chains of projectiles, pets and summons),
 * healing without overheal, measured losses and gains, and the formatting of the numbers of a summary.
 */
public final class FightRules {
	/** Owners followed at most (an arrow of a skeleton horse of... nothing legit goes deeper). */
	public static final int MAX_OWNER_DEPTH = 4;

	private FightRules() {
	}

	/**
	 * The player behind {@code start}: itself when it is one, else the first player up its chain of owners (the shooter of a
	 * projectile, the owner of a pet, the caster of a summoned guardian, the owner of the pet that shot an arrow...), at most
	 * {@code maxDepth} steps up; null without a player (a monster, a loop, a chain too long).
	 */
	public static <T> @Nullable T owner(@Nullable T start, Function<T, @Nullable T> parent, Predicate<T> isPlayer, int maxDepth) {
		T current = start;
		for (int depth = 0; current != null && depth <= Math.max(0, maxDepth); depth++) {
			if (isPlayer.test(current)) {
				return current;
			}
			T next = parent.apply(current);
			if (next == current) {
				return null;
			}
			current = next;
		}
		return null;
	}

	/** Health actually given back by a heal of {@code amount}: never above the missing health (no overheal), 0 on a dead target. */
	public static double effectiveHeal(double health, double maxHealth, double amount) {
		if (!(health > 0) || !(amount > 0) || !Double.isFinite(amount) || !Double.isFinite(health) || !(maxHealth > health)) {
			return 0;
		}
		return Math.min(amount, maxHealth - health);
	}

	/** What went from {@code before} to a larger {@code after} (damage lost, absorption gained), else 0. */
	public static double gained(double before, double after) {
		double delta = after - before;
		return delta > 0 && Double.isFinite(delta) ? delta : 0;
	}

	/** "1 240": rounded, thousands separated by a space. */
	public static String number(double value) {
		long rounded = Math.round(Double.isFinite(value) ? value : 0);
		String digits = Long.toString(Math.abs(rounded));
		StringBuilder sb = new StringBuilder(digits.length() + 4);
		int first = digits.length() % 3;
		if (first > 0) {
			sb.append(digits, 0, first);
		}
		for (int i = first; i < digits.length(); i += 3) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(digits, i, i + 3);
		}
		return rounded < 0 ? "-" + sb : sb.toString();
	}

	/** "3:07", "1:02:45" (ticks). */
	public static String clock(long ticks) {
		long seconds = Math.max(0, ticks) / 20;
		long hours = seconds / 3600;
		long minutes = seconds / 60 % 60;
		long secs = seconds % 60;
		return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs) : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
	}

	/** Whole percent of a ratio (0..1), clamped. */
	public static int percent(double ratio) {
		return (int) Math.round(Math.max(0, Math.min(1, Double.isFinite(ratio) ? ratio : 0)) * 100);
	}

	/** The value as shown (and compared for ties): a whole number. */
	public static long shown(double value) {
		return Math.round(Double.isFinite(value) ? value : 0);
	}
}
