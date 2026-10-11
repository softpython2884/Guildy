package dev.guildy.shop;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure validation of the shop catalog (unit-tested): clamps values, refuses gold items (gold is the currency) and
 * unknown items, and prevents buy/sell loops (selling an item never pays more than buying it costs).
 */
public final class ShopValidator {
	/** Gold is the currency: these items can never be traded. */
	public static final Set<String> FORBIDDEN = Set.of(
			"minecraft:gold_ingot", "minecraft:gold_nugget", "minecraft:gold_block", "minecraft:raw_gold",
			"minecraft:raw_gold_block", "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore");
	public static final int MAX_LOT = 64 * 9;

	private ShopValidator() {
	}

	/** "Iron_Ingot " → "minecraft:iron_ingot". */
	public static String normalizeId(String id) {
		String text = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
		return text.isEmpty() || text.contains(":") ? text : "minecraft:" + text;
	}

	/**
	 * Fixes one entry in place.
	 *
	 * @param itemExists tells whether a normalized item id exists
	 * @return null when the entry is usable, otherwise why it is refused
	 */
	public static @Nullable String check(ShopEntry entry, Predicate<String> itemExists, List<String> warnings) {
		if (entry.item == null || entry.item.isBlank()) {
			return "missing item";
		}
		String id = normalizeId(entry.item);
		if (FORBIDDEN.contains(id)) {
			return "'" + id + "' cannot be traded (gold is the currency)";
		}
		if (!itemExists.test(id)) {
			return "unknown item '" + id + "'";
		}
		entry.item = id;
		if (entry.amount < 1 || entry.amount > MAX_LOT) {
			int fixed = Math.max(1, Math.min(MAX_LOT, entry.amount));
			warnings.add(id + ": amount " + entry.amount + " -> " + fixed);
			entry.amount = fixed;
		}
		if (entry.buy < 0) {
			warnings.add(id + ": negative buy price -> 0");
			entry.buy = 0;
		}
		if (entry.sell < 0) {
			warnings.add(id + ": negative sell price -> 0");
			entry.sell = 0;
		}
		entry.minLevel = Math.max(1, entry.minLevel);
		if (entry.buy > 0 && entry.sell > entry.buy) {
			warnings.add(id + ": sell price " + entry.sell + " > buy price " + entry.buy + " -> " + entry.buy);
			entry.sell = entry.buy;
		}
		if (entry.buy == 0 && entry.sell == 0) {
			return "'" + id + "' is neither sold nor bought (buy = sell = 0)";
		}
		return null;
	}

	/**
	 * Validates the whole catalog: entries are fixed in place; the returned categories only contain usable entries
	 * (refused ones stay in the config file but are not shown). Across entries of the same item, the sell price per
	 * item is capped by the cheapest buy price per item.
	 */
	public static List<ShopCategory> validate(List<ShopCategory> categories, Predicate<String> itemExists, List<String> warnings) {
		List<ShopCategory> usable = new ArrayList<>();
		if (categories == null) {
			return usable;
		}
		List<ShopEntry> all = new ArrayList<>();
		for (ShopCategory category : categories) {
			if (category == null) {
				continue;
			}
			if (category.id == null || category.id.isBlank()) {
				category.id = "category" + usable.size();
			}
			if (category.name == null || category.name.isBlank()) {
				category.name = category.id;
			}
			if (category.icon == null || category.icon.isBlank() || !itemExists.test(normalizeId(category.icon))) {
				category.icon = "minecraft:chest";
			}
			List<ShopEntry> entries = new ArrayList<>();
			if (category.entries != null) {
				for (ShopEntry entry : category.entries) {
					if (entry == null) {
						continue;
					}
					String refused = check(entry, itemExists, warnings);
					if (refused != null) {
						warnings.add("refused in '" + category.id + "': " + refused);
					} else {
						entries.add(entry);
					}
				}
			}
			all.addAll(entries);
			usable.add(new ShopCategory(category.id, category.name, normalizeId(category.icon), entries));
		}
		capCrossEntrySellPrices(all, warnings);
		return usable;
	}

	/** Same item listed several times: selling never pays more per item than the cheapest purchase costs. */
	static void capCrossEntrySellPrices(List<ShopEntry> entries, List<String> warnings) {
		Map<String, Double> cheapestBuy = new HashMap<>();
		for (ShopEntry entry : entries) {
			if (entry.buy > 0) {
				double unit = (double) entry.buy / entry.amount;
				cheapestBuy.merge(entry.item, unit, Math::min);
			}
		}
		for (ShopEntry entry : entries) {
			Double unit = cheapestBuy.get(entry.item);
			if (unit != null && entry.sell > 0 && (double) entry.sell / entry.amount > unit + 1e-9) {
				int capped = (int) Math.floor(unit * entry.amount + 1e-9);
				warnings.add(entry.item + ": sell price " + entry.sell + " for " + entry.amount
						+ " exceeds the cheapest purchase of the same item -> " + capped);
				entry.sell = capped;
			}
		}
	}

	/** Gold paid for {@code lots} lots (capped to avoid overflow; -1 if not for sale). */
	public static long buyTotal(ShopEntry entry, int lots) {
		return entry.buy <= 0 || lots <= 0 ? -1 : (long) entry.buy * lots;
	}

	/** Lots a player can sell from {@code owned} items (1 or all). */
	public static int sellableLots(ShopEntry entry, int owned, boolean all) {
		if (entry.sell <= 0 || entry.amount <= 0) {
			return 0;
		}
		int lots = owned / entry.amount;
		return all ? lots : Math.min(1, lots);
	}
}
