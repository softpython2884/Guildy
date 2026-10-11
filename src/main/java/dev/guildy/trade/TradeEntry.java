package dev.guildy.trade;

/**
 * One possible merchant offer (config entry, pure data): the merchant SELLS {@code count} x {@code item} for {@code price}
 * gold. Prices up to 64 are paid in gold ingots, above in gold blocks (9 gold) + ingots for the rest.
 * Merchants never buy anything (no gold loop).
 */
public final class TradeEntry {
	/** Default icon of the unique weapon offers. */
	public static final String UNIQUE_ICON = "minecraft:nether_star";
	/** {@link #unique} value of an offer drawn from the "trade" table of uniques.json5. */
	public static final String RANDOM_UNIQUE = "random";

	public String item = "minecraft:oak_sapling";
	public int count = 1;
	/** Gold (ingots) for the lot. */
	public int price = 1;
	/** Number of times the offer can be used during one visit. */
	public int maxUses = 4;
	/** Relative chance to be offered. */
	public double weight = 10;
	/** Optional enchantment ("minecraft:mending"): stored in an enchanted book, applied on any other item. */
	public String enchantment = "";
	public int level = 1;
	/**
	 * Optional unique weapon sold instead of {@link #item} (uniques.json5): "random" = drawn from the "trade" table when the
	 * merchant arrives, or the id of a unique. {@link #item} is then only the icon of the menus.
	 */
	public String unique = "";

	public TradeEntry() {
	}

	public static TradeEntry of(String item, int count, int price, int maxUses, double weight) {
		TradeEntry entry = new TradeEntry();
		entry.item = item;
		entry.count = count;
		entry.price = price;
		entry.maxUses = maxUses;
		entry.weight = weight;
		return entry;
	}

	/** Enchanted book with one stored enchantment. */
	public static TradeEntry book(String enchantment, int level, int price, double weight) {
		TradeEntry entry = of("minecraft:enchanted_book", 1, price, 1, weight);
		entry.enchantment = enchantment;
		entry.level = level;
		return entry;
	}

	/** Unique weapon offer: "random" (drawn from the "trade" table of uniques.json5) or a unique id. */
	public static TradeEntry unique(String unique, int price, double weight) {
		TradeEntry entry = of(UNIQUE_ICON, 1, price, 1, weight);
		entry.unique = unique;
		return entry;
	}

	public boolean enchanted() {
		return enchantment != null && !enchantment.isBlank();
	}

	public boolean isUnique() {
		return unique != null && !unique.isBlank();
	}

	/** Gold paid per item. */
	public double unitPrice() {
		return count <= 0 ? price : (double) price / count;
	}

	public TradeEntry copy() {
		TradeEntry copy = of(item, count, price, maxUses, weight);
		copy.enchantment = enchantment;
		copy.level = level;
		copy.unique = unique;
		return copy;
	}

	@Override
	public String toString() {
		if (isUnique()) {
			return "unique weapon '" + unique + "' for " + price;
		}
		return count + "x " + item + (enchanted() ? " [" + enchantment + " " + level + "]" : "") + " for " + price;
	}
}
