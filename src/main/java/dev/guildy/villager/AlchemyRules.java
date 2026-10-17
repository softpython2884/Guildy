package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/**
 * Pure rules of the alchemist (no Minecraft classes, unit tested): enchanting power of a table, enchant level of the
 * vanilla third slot, work time, table choice, the daily caps and the spell scrolls it sometimes writes instead of a
 * book (chance, paper, level of the rarer scrolls, weighted pick).
 */
public final class AlchemyRules {
	/** Vanilla caps the enchanting power at 15 bookshelves. */
	public static final int MAX_POWER = 15;
	public static final int MAX_LEVEL = 30;
	public static final long DAY_TICKS = 24000L;
	/** The scroll rarity always allowed; the rarer ones need {@code alchemist.epicScrollLevel}. */
	public static final String BASIC_RARITY = "RARE";

	/** What a finished work cycle gives. */
	public enum Outcome {
		/** An enchanted book (no scroll rolled, scroll cap reached or no scroll possible at that level). */
		BOOK,
		/** A spell scroll (uses the book, the lapis and the paper). */
		SCROLL,
		/** A scroll was rolled but the bag lacks paper: an enchanted book instead. */
		BOOK_NO_PAPER
	}

	private AlchemyRules() {
	}

	// ------------------------------------------------------------------ spell scrolls

	/**
	 * Outcome of a finished cycle: a scroll with chance {@code scrollChance} ({@code roll} uniform in [0, 1)), when a
	 * scroll is possible at that level and today's scroll cap is not reached; without enough paper, a book instead.
	 */
	public static Outcome outcome(double roll, double scrollChance, boolean scrollPossible, int scrollsToday, int maxScrollsPerDay,
			int paper, int paperPerScroll) {
		if (!(roll < scrollChance) || !scrollPossible || scrollsToday >= Math.max(0, maxScrollsPerDay)) {
			return Outcome.BOOK;
		}
		return paper >= Math.max(0, paperPerScroll) ? Outcome.SCROLL : Outcome.BOOK_NO_PAPER;
	}

	/** May a scroll of this rarity be written at this enchant level? RARE ones always, rarer ones from {@code epicLevel}. */
	public static boolean scrollAllowed(@Nullable String rarity, int level, int epicLevel) {
		return BASIC_RARITY.equals(rarity) || level >= epicLevel;
	}

	/**
	 * Weighted pick of a scroll among the table's entries allowed at this level (table order); ids the lookup does not know
	 * ({@code rarity} null) and weights that are not positive are ignored.
	 *
	 * @param rarity scroll id → rarity name, null for an unknown (or not obtainable) id
	 * @param roll   uniform in [0, 1)
	 * @return the id, or null when no scroll is possible
	 */
	public static @Nullable String pickScroll(@Nullable Map<String, Double> weights, Function<String, @Nullable String> rarity, int level,
			int epicLevel, double roll) {
		double total = totalWeight(weights, rarity, level, epicLevel);
		if (total <= 0 || weights == null) {
			return null;
		}
		double target = Math.max(0, Math.min(1, roll)) * total;
		String last = null;
		for (Map.Entry<String, Double> entry : weights.entrySet()) {
			double weight = weight(entry, rarity, level, epicLevel);
			if (weight <= 0) {
				continue;
			}
			last = entry.getKey();
			if (target < weight) {
				return last;
			}
			target -= weight;
		}
		return last;
	}

	/** True when at least one scroll of the table may be written at this level. */
	public static boolean anyScroll(@Nullable Map<String, Double> weights, Function<String, @Nullable String> rarity, int level, int epicLevel) {
		return totalWeight(weights, rarity, level, epicLevel) > 0;
	}

	private static double totalWeight(@Nullable Map<String, Double> weights, Function<String, @Nullable String> rarity, int level,
			int epicLevel) {
		if (weights == null) {
			return 0;
		}
		double total = 0;
		for (Map.Entry<String, Double> entry : weights.entrySet()) {
			total += weight(entry, rarity, level, epicLevel);
		}
		return total;
	}

	private static double weight(Map.Entry<String, Double> entry, Function<String, @Nullable String> rarity, int level, int epicLevel) {
		Double weight = entry.getValue();
		if (entry.getKey() == null || weight == null || !Double.isFinite(weight) || weight <= 0) {
			return 0;
		}
		String found = rarity.apply(entry.getKey());
		return found != null && scrollAllowed(found, level, epicLevel) ? weight : 0;
	}

	/** Sheets of paper to fetch: enough for the scrolls still possible today on this trip, minus what the bag holds. */
	public static int paperWanted(int paperPerScroll, int scrollsLeftToday, int worksLeftOnTrip, int paperInBag) {
		int scrolls = Math.max(0, Math.min(scrollsLeftToday, worksLeftOnTrip));
		if (paperPerScroll <= 0 || scrolls <= 0) {
			return 0;
		}
		return Math.max(0, paperPerScroll * scrolls - Math.max(0, paperInBag));
	}

	// ------------------------------------------------------------------ books

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
