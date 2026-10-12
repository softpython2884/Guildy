package dev.guildy.villager;

import dev.guildy.quest.QuestConfig;
import dev.guildy.shop.ShopCategory;
import dev.guildy.shop.ShopConfig;
import dev.guildy.shop.ShopEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Default content invariant: expedition loot is never a cheap source of gold compared with the shop. Per trip the gold
 * brought back (nuggets, ingots, raw gold) stays modest, and everything the villager brings back, sold to the shop at its
 * buy-back price (plus the gold of a treasure map), is worth little per minute of trip, even when the villager's wishlist
 * favours the most valuable items of the destination.
 */
class ExpeditionEconomyTest {
	private static final Map<String, Double> GOLD = Map.of(
			"minecraft:gold_nugget", 1.0 / 9, "minecraft:gold_ingot", 1.0, "minecraft:raw_gold", 1.0,
			"minecraft:gold_block", 9.0, "minecraft:raw_gold_block", 9.0);
	private static final double MAX_GOLD_PER_TRIP = 2.0;
	private static final double MAX_VALUE_PER_MINUTE = 0.5;
	private static final double MAX_WISHLIST_GOLD_PER_TRIP = 4.0;
	private static final double MAX_WISHLIST_GOLD_PER_MINUTE = 0.25;
	private static final double MAX_WISHLIST_VALUE_PER_MINUTE = 0.75;

	/** Gold paid by the shop per item it buys back. */
	private static Map<String, Double> buyBack() {
		Map<String, Double> prices = new HashMap<>();
		for (ShopCategory category : ShopConfig.defaults()) {
			for (ShopEntry entry : category.entries) {
				if (entry.sell > 0) {
					prices.merge(entry.item, (double) entry.sell / entry.amount, Math::max);
				}
			}
		}
		return prices;
	}

	private static double itemValue(String item, Map<String, Double> shop) {
		return GOLD.getOrDefault(item, 0.0) + shop.getOrDefault(item, 0.0);
	}

	/** Expected {direct gold, total value (gold + shop buy-back + treasure map gold)} of one trip with this loot table. */
	private static double[] trip(ExpeditionDestinations.Destination destination, List<VillagerConfig.LootEntry> loot, Map<String, Double> shop, double mapGold) {
		double totalWeight = 0;
		for (VillagerConfig.LootEntry entry : loot) {
			totalWeight += entry.weight;
		}
		double gold = 0;
		double value = destination.treasureMapChance() * mapGold;
		for (VillagerConfig.LootEntry entry : loot) {
			double count = destination.rolls() * entry.weight / totalWeight * (entry.min + entry.max) / 2.0;
			gold += count * GOLD.getOrDefault(entry.item, 0.0);
			value += count * itemValue(entry.item, shop);
		}
		return new double[]{gold, value};
	}

	@Test
	void expeditionsAreNeverACheapSourceOfGold() {
		VillagerConfig config = new VillagerConfig();
		config.validate();
		Map<String, Double> shop = buyBack();
		double mapGold = new QuestConfig().treasure.tiers.getFirst().goldMax;
		double boost = config.expeditions.wishlistWeightMultiplier;
		int checked = 0;
		for (boolean nether : new boolean[]{false, true}) {
			for (ExpeditionDestinations.Destination destination : ExpeditionDestinations.list(config.expeditions, nether)) {
				double minutes = destination.durationSeconds() / 60.0;
				double[] plain = trip(destination, destination.loot(), shop, mapGold);
				String id = destination.id();
				assertTrue(plain[0] <= MAX_GOLD_PER_TRIP, id + ": " + plain[0] + " gold per trip");
				assertTrue(plain[1] / minutes <= MAX_VALUE_PER_MINUTE, id + ": worth " + plain[1] / minutes + " gold per minute");

				// Worst wishlist: the k most valuable items of the table (k up to the 9 wishlist entries).
				List<VillagerConfig.LootEntry> byValue = new ArrayList<>(destination.loot());
				byValue.sort(Comparator.comparingDouble((VillagerConfig.LootEntry e) -> (e.min + e.max) * itemValue(e.item, shop)).reversed());
				for (int k = 1; k <= Math.min(9, byValue.size()); k++) {
					List<String> wanted = byValue.subList(0, k).stream().map(e -> e.item).toList();
					double[] favoured = trip(destination, ExpeditionDestinations.boosted(destination.loot(), wanted, boost), shop, mapGold);
					assertTrue(favoured[0] <= MAX_WISHLIST_GOLD_PER_TRIP, id + " wishing " + wanted + ": " + favoured[0] + " gold per trip");
					assertTrue(favoured[0] / minutes <= MAX_WISHLIST_GOLD_PER_MINUTE, id + " wishing " + wanted + ": " + favoured[0] / minutes + " gold/min");
					assertTrue(favoured[1] / minutes <= MAX_WISHLIST_VALUE_PER_MINUTE, id + " wishing " + wanted + ": worth " + favoured[1] / minutes + " gold/min");
				}
				checked++;
			}
		}
		assertTrue(checked >= 23, "every destination checked: " + checked);
	}
}
