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
	/** Claim-level day of {@link #booksToday} (-1 = none yet). */
	public long day = -1;
	public int booksToday;

	void sanitize() {
		if (bestEnchant == null) {
			bestEnchant = "";
		}
		if (lastBook == null) {
			lastBook = "";
		}
		repaired = Math.max(0, repaired);
		durability = Math.max(0, durability);
		repairSteps = Math.max(0, repairSteps);
		anvilsWorn = Math.max(0, anvilsWorn);
		books = Math.max(0, books);
		booksToday = Math.max(0, booksToday);
	}
}
