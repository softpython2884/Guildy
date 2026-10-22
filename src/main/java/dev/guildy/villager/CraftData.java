package dev.guildy.villager;

import dev.guildy.rune.RuneRules;

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
	/** Runes inscribed on the armor of the armor stands. */
	public long runes;
	/** Id of the last rune inscribed, "" before the first one. */
	public String lastRune = "";
	/** Claim-level day of {@link #runesToday} (-1 = none yet). */
	public long runeDay = -1;
	/** Runes of that day (daily cap: runes.json5 inscription.maxPerDay). */
	public int runesToday;

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
	/** Potions and elixirs brewed (bottles). */
	public long potions;
	/** Product of the last batch ("healing", "elixir:arcanique"), "" before the first one. */
	public String lastPotion = "";
	/** Brews left from the fuel already burnt (a blaze powder gives {@code alchemist.brewsPerFuel}). */
	public int brewFuel;
	/** Role chosen in the menu ({@link AlchemyJob} id), "" = automatic (the guild's distribution). */
	public String role = "";
	/** Claim-level day of the daily counts (-1 = none yet). */
	public long day = -1;
	/** Enchanted books of that day (an older save counted the scrolls in it too). */
	public int booksToday;
	/** Scrolls of that day (their own cap). */
	public int scrollsToday;
	/** Potion and elixir bottles of that day (their own cap). */
	public int potionsToday;
	/** Elixir bottles of that day, also counted in {@link #potionsToday} (simple brewing: brewing.maxElixirsPerDay). */
	public int elixirsToday;

	/** Works done at the enchanting table today (books and scrolls). */
	public int worksToday(long today) {
		return booksToday(today) + scrollsToday(today);
	}

	public int booksToday(long today) {
		return AlchemyRules.madeToday(day, booksToday, today);
	}

	public int scrollsToday(long today) {
		return AlchemyRules.madeToday(day, scrollsToday, today);
	}

	public int potionsToday(long today) {
		return AlchemyRules.madeToday(day, potionsToday, today);
	}

	public int elixirsToday(long today) {
		return AlchemyRules.madeToday(day, elixirsToday, today);
	}

	/** Counts one finished book or scroll of {@code today} (every daily count starts over on a new day). */
	public void countWork(long today, boolean scroll) {
		rollDay(today);
		if (scroll) {
			scrollsToday++;
		} else {
			booksToday++;
		}
	}

	/** Runes inscribed on {@code today}. */
	public int runesToday(long today) {
		return RuneRules.madeToday(runeDay, runesToday, today);
	}

	/** Counts one rune inscribed on {@code today}. */
	public void countRune(long today, String id) {
		if (runeDay != today) {
			runesToday = 0;
			runeDay = today;
		}
		runesToday++;
		runes++;
		lastRune = id == null ? "" : id;
	}

	/** Counts the bottles of a finished batch of {@code today}. */
	public void countPotions(long today, int bottles) {
		countPotions(today, bottles, false);
	}

	/** Counts the bottles of a finished batch of {@code today} (an elixir batch also counts towards the elixir cap). */
	public void countPotions(long today, int bottles, boolean elixir) {
		rollDay(today);
		potionsToday += Math.max(0, bottles);
		potions += Math.max(0, bottles);
		if (elixir) {
			elixirsToday += Math.max(0, bottles);
		}
	}

	private void rollDay(long today) {
		if (day != today) {
			booksToday = 0;
			scrollsToday = 0;
			potionsToday = 0;
			elixirsToday = 0;
			day = today;
		}
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
		if (lastPotion == null) {
			lastPotion = "";
		}
		if (lastRune == null) {
			lastRune = "";
		}
		runes = Math.max(0, runes);
		runesToday = Math.max(0, runesToday);
		if (role == null || AlchemyJob.parse(role) == null) {
			role = "";
		}
		potions = Math.max(0, potions);
		potionsToday = Math.max(0, potionsToday);
		elixirsToday = Math.max(0, elixirsToday);
		brewFuel = Math.max(0, brewFuel);
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
