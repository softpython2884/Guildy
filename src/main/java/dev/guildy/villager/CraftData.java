package dev.guildy.villager;

/**
 * Blacksmith / alchemist data of a guild villager (persisted in {@link VillagerRecord#craft}, created the first time
 * one of these tasks works): statistics and the alchemist's daily count. Kept when the task changes.
 */
public class CraftData {
	// blacksmith
	/** Items repaired up to their full durability. */
	public long repaired;
	/** Durability points given back. */
	public long durability;
	/** Repair steps (anvil uses). */
	public long repairSteps;
	/** Anvils used until they broke. */
	public long anvilsWorn;

	// alchemist
	/** Enchanted books made. */
	public long books;
	/** Best enchantment made (id + level, see {@link AlchemyRules}), "" before the first book. */
	public String bestEnchant = "";
	public int bestLevel;
	/** Enchanting power that enchantment needs (its minimum cost): the higher, the rarer. */
	public int bestScore;
	/** Enchantments of the last book ("id=level;id=level"), "" before the first book. */
	public String lastBook = "";
	/** Spell scrolls written (instead of a book). */
	public long scrolls;
	/** Scroll id of the last scroll written, "" before the first one. */
	public String lastScroll = "";
	/** Claim-level day of {@link #booksToday} and {@link #scrollsToday} (-1 = none yet). */
	public long day = -1;
	/** Works of that day: enchanted books and scrolls (the daily cap counts both). */
	public int booksToday;
	/** Scrolls of that day (their own cap, inside the one of {@link #booksToday}). */
	public int scrollsToday;

	/** Works done today (books and scrolls). */
	public int worksToday(long today) {
		return AlchemyRules.madeToday(day, booksToday, today);
	}

	public int scrollsToday(long today) {
		return AlchemyRules.madeToday(day, scrollsToday, today);
	}

	/** Counts one finished work of {@code today} (both daily counts start over on a new day). */
	public void countWork(long today, boolean scroll) {
		int works = worksToday(today) + 1;
		int written = scrollsToday(today) + (scroll ? 1 : 0);
		booksToday = works;
		scrollsToday = Math.min(written, works);
		day = today;
	}

	void sanitize() {
		if (bestEnchant == null) {
			bestEnchant = "";
		}
		if (lastBook == null) {
			lastBook = "";
		}
		if (lastScroll == null) {
			lastScroll = "";
		}
		scrolls = Math.max(0, scrolls);
		scrollsToday = Math.max(0, scrollsToday);
		repaired = Math.max(0, repaired);
		durability = Math.max(0, durability);
		repairSteps = Math.max(0, repairSteps);
		anvilsWorn = Math.max(0, anvilsWorn);
		books = Math.max(0, books);
		booksToday = Math.max(0, booksToday);
	}
}
