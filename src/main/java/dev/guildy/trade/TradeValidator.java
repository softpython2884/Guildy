package dev.guildy.trade;

import dev.guildy.shop.ShopCategory;
import dev.guildy.shop.ShopEntry;
import dev.guildy.shop.ShopValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Pure validation of merchant trade tables (unit-tested). Merchants only sell, and:
 * <ul>
 * <li>never sell gold items (gold is the currency) nor unknown items;</li>
 * <li>never sell an item cheaper (per unit) than the guild shop sells it, nor cheaper than the shop buys it back: no gold
 * loop with the shop, nor with the delivery quests (paid below the shop prices).</li>
 * </ul>
 * Entries are fixed in place; refused ones stay in the config file but are not used.
 */
public final class TradeValidator {
	public static final int MAX_COUNT = 64;
	public static final int MAX_USES = 64;
	public static final int MAX_LEVEL = 10;

	private TradeValidator() {
	}

	/**
	 * Unit prices of the shop: [0] = cheapest unit price the shop SELLS each item at, [1] = highest unit price it BUYS
	 * each item back at. Keys are normalized item ids.
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Double>[] shopPrices(List<ShopCategory> categories) {
		Map<String, Double> sells = new HashMap<>();
		Map<String, Double> buys = new HashMap<>();
		if (categories != null) {
			for (ShopCategory category : categories) {
				if (category == null || category.entries == null) {
					continue;
				}
				for (ShopEntry entry : category.entries) {
					if (entry == null || entry.item == null || entry.amount <= 0) {
						continue;
					}
					String id = ShopValidator.normalizeId(entry.item);
					if (entry.buy > 0) {
						sells.merge(id, (double) entry.buy / entry.amount, Math::min);
					}
					if (entry.sell > 0) {
						buys.merge(id, (double) entry.sell / entry.amount, Math::max);
					}
				}
			}
		}
		return new Map[] {sells, buys};
	}

	/** Lowest allowed price for {@code count} items given the shop unit prices (0 when the shop does not deal it). */
	public static int minPrice(String item, int count, Map<String, Double> shopSells, Map<String, Double> shopBuys) {
		double unit = Math.max(shopSells.getOrDefault(item, 0.0), shopBuys.getOrDefault(item, 0.0));
		return unit <= 0 ? 0 : (int) Math.ceil(unit * count - 1e-9);
	}

	/**
	 * Fixes one entry in place.
	 *
	 * @return null when usable, otherwise why it is refused
	 */
	public static String check(TradeEntry entry, Predicate<String> itemExists, Map<String, Double> shopSells, Map<String, Double> shopBuys,
			List<String> warnings) {
		if (entry.isUnique()) {
			return checkUnique(entry, itemExists, warnings);
		}
		entry.unique = "";
		if (entry.item == null || entry.item.isBlank()) {
			return "missing item";
		}
		String id = ShopValidator.normalizeId(entry.item);
		if (ShopValidator.FORBIDDEN.contains(id)) {
			return "'" + id + "' cannot be sold (gold is the currency)";
		}
		if (!itemExists.test(id)) {
			return "unknown item '" + id + "'";
		}
		entry.item = id;
		if (entry.count < 1 || entry.count > MAX_COUNT) {
			int fixed = Math.max(1, Math.min(MAX_COUNT, entry.count));
			warnings.add(id + ": count " + entry.count + " -> " + fixed);
			entry.count = fixed;
		}
		if (entry.price < 1 || entry.price > TradeRolls.MAX_PRICE) {
			int fixed = Math.max(1, Math.min(TradeRolls.MAX_PRICE, entry.price));
			warnings.add(id + ": price " + entry.price + " -> " + fixed);
			entry.price = fixed;
		}
		entry.maxUses = Math.max(1, Math.min(MAX_USES, entry.maxUses));
		if (!Double.isFinite(entry.weight) || entry.weight < 0) {
			entry.weight = 0;
		}
		if (entry.enchantment == null) {
			entry.enchantment = "";
		}
		if (entry.enchanted()) {
			entry.enchantment = ShopValidator.normalizeId(entry.enchantment);
			entry.level = Math.max(1, Math.min(MAX_LEVEL, entry.level));
		} else {
			entry.enchantment = "";
			entry.level = 1;
			// Plain items only: enchanted ones cannot be sold to the shop nor delivered.
			int min = minPrice(id, entry.count, shopSells, shopBuys);
			if (entry.price < min) {
				if (min > TradeRolls.MAX_PRICE) {
					return "'" + id + "': the shop price of " + entry.count + " items is above the maximum merchant price";
				}
				warnings.add(id + ": price " + entry.price + " for " + entry.count + " is below the shop price -> " + min);
				entry.price = min;
			}
		}
		return null;
	}

	/**
	 * A unique weapon offer (uniques.json5): one weapon, sold once per visit; its item is only the menu icon (nether star when
	 * unknown). Never compared with the shop: unique weapons cannot be sold to it.
	 */
	private static String checkUnique(TradeEntry entry, Predicate<String> itemExists, List<String> warnings) {
		entry.unique = entry.unique.trim().toLowerCase(Locale.ROOT);
		String icon = entry.item == null ? "" : ShopValidator.normalizeId(entry.item);
		entry.item = icon.isEmpty() || !itemExists.test(icon) ? TradeEntry.UNIQUE_ICON : icon;
		entry.count = 1;
		entry.maxUses = 1;
		entry.enchantment = "";
		entry.level = 1;
		if (entry.price < 1 || entry.price > TradeRolls.MAX_PRICE) {
			int fixed = Math.max(1, Math.min(TradeRolls.MAX_PRICE, entry.price));
			warnings.add("unique '" + entry.unique + "': price " + entry.price + " -> " + fixed);
			entry.price = fixed;
		}
		if (!Double.isFinite(entry.weight) || entry.weight < 0) {
			entry.weight = 0;
		}
		return null;
	}

	/** Validates a table: entries fixed in place, the usable ones returned (refusals reported in {@code warnings}). */
	public static List<TradeEntry> clean(String table, List<TradeEntry> entries, Predicate<String> itemExists, Map<String, Double> shopSells,
			Map<String, Double> shopBuys, List<String> warnings) {
		List<TradeEntry> usable = new ArrayList<>();
		if (entries == null) {
			return usable;
		}
		for (TradeEntry entry : entries) {
			if (entry == null) {
				continue;
			}
			String refused = check(entry, itemExists, shopSells, shopBuys, warnings);
			if (refused != null) {
				warnings.add("refused in '" + table + "': " + refused);
			} else {
				usable.add(entry);
			}
		}
		return usable;
	}

	/** "Mending" -> "minecraft:mending". */
	public static String normalizeId(String id) {
		return ShopValidator.normalizeId(id == null ? "" : id.toLowerCase(Locale.ROOT));
	}
}
