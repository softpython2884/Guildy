package dev.guildy.quest;

/**
 * Pure text helpers for quest displays (progress bar, durations).
 */
public final class QuestFormat {
	private QuestFormat() {
	}

	/** Legacy-colored progress bar: green part done, gray part left ('§' codes). */
	public static String bar(int progress, int amount, int length) {
		int total = Math.max(1, amount);
		int filled = (int) Math.floor((double) Math.max(0, Math.min(progress, total)) * length / total);
		if (progress > 0 && filled == 0) {
			filled = 1;
		}
		if (progress < total && filled == length) {
			filled = length - 1;
		}
		return "§a" + "|".repeat(filled) + "§7" + "|".repeat(length - filled);
	}

	/** Duration in ticks as French text: "2 h 05 min", "12 min", "45 s". */
	public static String duration(long ticks) {
		long seconds = Math.max(0, ticks / 20);
		if (seconds < 60) {
			return seconds + " s";
		}
		long minutes = seconds / 60;
		if (minutes < 60) {
			return minutes + " min";
		}
		long hours = minutes / 60;
		long rest = minutes % 60;
		if (hours < 48) {
			return hours + " h " + (rest < 10 ? "0" : "") + rest + " min";
		}
		return (hours / 24) + " j " + (hours % 24) + " h";
	}

	/** Whole minutes (rounded up) for sign countdowns. */
	public static long minutesCeil(long ticks) {
		return Math.max(0, (ticks + 1199) / 1200);
	}

	/** A chance (0..1) as a French percentage without the sign: 0.03 → "3", 0.025 → "2,5", 0.001 → "0,1". */
	public static String percent(double chance) {
		double value = Double.isFinite(chance) ? Math.max(0, Math.min(1, chance)) * 100.0 : 0;
		long tenths = Math.round(value * 10);
		if (tenths % 10 == 0) {
			return String.valueOf(tenths / 10);
		}
		return (tenths / 10) + "," + (tenths % 10);
	}
}
