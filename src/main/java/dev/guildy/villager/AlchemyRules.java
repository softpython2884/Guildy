package dev.guildy.villager;

/**
 * Pure rules of the alchemist (no Minecraft classes, unit tested): enchanting power of a table, enchant level of the
 * vanilla third slot, work time, table choice and the daily cap.
 */
public final class AlchemyRules {
	/** Vanilla caps the enchanting power at 15 bookshelves. */
	public static final int MAX_POWER = 15;
	public static final long DAY_TICKS = 24000L;

	private AlchemyRules() {
	}

	/** Enchanting power of a table with that many valid bookshelves (0 to 15). */
	public static int power(int bookshelves) {
		return Math.max(0, Math.min(MAX_POWER, bookshelves));
	}

	/**
	 * Enchant level of the third slot of a vanilla enchanting table (EnchantmentHelper.getEnchantmentCost, slot 2):
	 * {@code max(rand(8) + 1 + power / 2 + rand(power + 1), 2 * power)}, at least 1 (vanilla shows nothing below 3 in
	 * that slot; the alchemist still enchants at a bare table, at a low level).
	 *
	 * @param roll8      the first draw, 0 to 7
	 * @param rollShelves the second draw, 0 to power
	 */
	public static int enchantLevel(int roll8, int rollShelves, int bookshelves) {
		int p = power(bookshelves);
		int selected = Math.max(0, Math.min(7, roll8)) + 1 + (p >> 1) + Math.max(0, Math.min(p, rollShelves));
		return Math.max(1, Math.max(selected, p * 2));
	}

	public static int minLevel(int bookshelves) {
		return enchantLevel(0, 0, bookshelves);
	}

	public static int maxLevel(int bookshelves) {
		return enchantLevel(7, power(bookshelves), bookshelves);
	}

	/** Work seconds for one book (before the work speed upgrade). */
	public static int workSeconds(int level, int secondsPerLevel) {
		return Math.max(1, level) * Math.max(1, secondsPerLevel);
	}

	/** Progress in percent (0 to 99 while working, 100 when done). */
	public static int progressPercent(long done, long total) {
		if (total <= 0 || done >= total) {
			return 100;
		}
		return (int) Math.max(0, Math.min(99, done * 100 / total));
	}

	/** In-game day of a clock time (ticks). */
	public static long day(long clockTime) {
		return Math.floorDiv(clockTime, DAY_TICKS);
	}

	/** Books made today: the saved count when it was saved today, else 0 (new day, or the clock was set back). */
	public static int madeToday(long savedDay, int savedCount, long today) {
		return savedDay == today ? Math.max(0, savedCount) : 0;
	}

	public static boolean capReached(int madeToday, int maxPerDay) {
		return madeToday >= Math.max(1, maxPerDay);
	}

	/**
	 * Table order: the most bookshelves first (power capped at 15), then the nearest. Negative when table A comes first.
	 */
	public static int compareTables(int bookshelvesA, double distSqA, int bookshelvesB, double distSqB) {
		int byPower = Integer.compare(power(bookshelvesB), power(bookshelvesA));
		return byPower != 0 ? byPower : Double.compare(distSqA, distSqB);
	}
}
