package dev.guildy.quest;

import dev.guildy.shop.ShopCategory;
import dev.guildy.shop.ShopConfig;
import dev.guildy.shop.ShopEntry;
import dev.guildy.upgrade.UpgradeMath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Default content invariants: a DELIVER or COLLECTION quest fulfilled with items bought at the shop never pays back more
 * gold than those items cost (no gold loop between the shop and the quest board), legendary contracts included; CRAFT /
 * SMELT quests never pay more gold than their products are worth at the shop.
 */
class EconomyBalanceTest {
	/** Shop article used for tag targets. */
	private static final Map<String, String> TAG_TO_ITEM = Map.of(
			"#minecraft:wool", "minecraft:white_wool",
			"#minecraft:logs", "minecraft:oak_log",
			"#minecraft:planks", "minecraft:oak_planks",
			"#minecraft:meat", "minecraft:cooked_beef");
	private static final QuestGenerator.Legendary LEGENDARY = new QuestConfig().legendary.multipliers();
	/** Highest level used by the quest formulas (guild levels past 10 scale slower, up to this level). */
	private static final int MAX_SCALING_LEVEL = new QuestConfig().maxScalingLevel;

	private static Map<String, Double> unitPrices() {
		Map<String, Double> unitPrice = new HashMap<>();
		for (ShopCategory category : ShopConfig.defaults()) {
			for (ShopEntry entry : category.entries) {
				if (entry.buy > 0) {
					unitPrice.merge(entry.item, (double) entry.buy / entry.amount, Math::min);
				}
			}
		}
		return unitPrice;
	}

	private static Double price(Map<String, Double> prices, String target) {
		return prices.get(TAG_TO_ITEM.getOrDefault(target, target));
	}

	private static int minAmount(int min, double perLevel, int level) {
		return Math.max(1, min) + (int) Math.floor(perLevel * (level - 1));
	}

	private static int maxGold(QuestTemplate template, int level) {
		return template.rewardGoldMax + (int) Math.floor(template.rewardGoldPerLevel * (level - 1));
	}

	@Test
	void deliveriesNeverBeatShopPrices() {
		Map<String, Double> prices = unitPrices();
		int checked = 0;
		for (QuestTemplate template : QuestConfig.defaults()) {
			if (template.type != QuestType.DELIVER) {
				continue;
			}
			Double price = price(prices, template.target);
			if (price == null) {
				continue;
			}
			checked++;
			for (int level = 1; level <= MAX_SCALING_LEVEL; level++) {
				int amount = minAmount(template.amountMin, template.amountPerLevel, level);
				int gold = maxGold(template, level);
				assertTrue(gold < amount * price, template.id + " level " + level + ": reward " + gold + " >= cost " + amount * price);
				// Legendary contract of the same quest.
				QuestGenerator.Rolled rolled = new QuestGenerator.Rolled(template, amount, gold, 0, List.of(), 0, false, 1, List.of(), false);
				QuestGenerator.Rolled legendary = QuestGenerator.legendary(rolled, LEGENDARY);
				assertTrue(legendary.gold() < legendary.amount() * price, template.id + " legendary level " + level);
			}
		}
		assertTrue(checked >= 4, "deliveries priced at the shop: " + checked);
		// Delivery gold is never boosted by the upgrade tree.
		assertEquals(10, UpgradeMath.questGold(10, 50, QuestType.DELIVER.isDelivery()));
		assertEquals(10, UpgradeMath.questGold(10, 50, QuestType.COLLECTION.isDelivery()));
	}

	/** Every assortment the generator can draw entirely from the shop costs more than the quest pays. */
	@Test
	void assortmentsNeverBeatShopPrices() {
		Map<String, Double> prices = unitPrices();
		int checked = 0;
		for (QuestTemplate template : QuestConfig.defaults()) {
			if (template.type != QuestType.COLLECTION) {
				continue;
			}
			assertTrue(template.check().isEmpty(), template.id);
			List<QuestTemplate.Part> items = template.collection.items;
			int max = Math.min(template.collection.max, items.size());
			for (int level = 1; level <= MAX_SCALING_LEVEL; level++) {
				int gold = maxGold(template, level);
				for (List<QuestTemplate.Part> subset : subsets(items, template.collection.min, max)) {
					double cost = 0;
					double legendaryCost = 0;
					boolean buyable = true;
					List<QuestPart> parts = new ArrayList<>();
					for (QuestTemplate.Part part : subset) {
						Double price = price(prices, part.item);
						if (price == null) {
							buyable = false; // must be gathered: no pure shop loop
							break;
						}
						int amount = minAmount(part.amountMin, part.amountPerLevel, level);
						cost += amount * price;
						parts.add(new QuestPart(part.item, "", amount));
					}
					if (!buyable) {
						continue;
					}
					checked++;
					assertTrue(gold < cost, template.id + " level " + level + " " + names(subset) + ": reward " + gold + " >= cost " + cost);
					QuestGenerator.Rolled rolled = new QuestGenerator.Rolled(template, 1, gold, 0, List.of(), 0, false, 1, parts, false);
					QuestGenerator.Rolled legendary = QuestGenerator.legendary(rolled, LEGENDARY);
					for (QuestPart part : legendary.parts()) {
						legendaryCost += part.amount * price(prices, part.target);
					}
					assertTrue(legendary.gold() < legendaryCost, template.id + " legendary level " + level + " " + names(subset));
				}
			}
		}
		assertTrue(checked > 0, "no assortment fully buyable at the shop: the test checks nothing");
	}

	/** CRAFT / SMELT: the products stay with the player; their shop value always exceeds the gold paid. */
	@Test
	void productionNeverPaysMoreThanItsProducts() {
		Map<String, Double> prices = unitPrices();
		for (QuestTemplate template : QuestConfig.defaults()) {
			if (template.type != QuestType.CRAFT && template.type != QuestType.SMELT) {
				continue;
			}
			Double price = price(prices, template.target);
			if (price == null) {
				continue;
			}
			for (int level = 1; level <= MAX_SCALING_LEVEL; level++) {
				int amount = minAmount(template.amountMin, template.amountPerLevel, level);
				int gold = maxGold(template, level);
				assertTrue(gold < amount * price, template.id + " level " + level + ": reward " + gold + " >= value " + amount * price);
			}
		}
	}

	private static List<List<QuestTemplate.Part>> subsets(List<QuestTemplate.Part> items, int min, int max) {
		List<List<QuestTemplate.Part>> result = new ArrayList<>();
		int n = items.size();
		for (int mask = 1; mask < (1 << n); mask++) {
			int size = Integer.bitCount(mask);
			if (size < min || size > max) {
				continue;
			}
			List<QuestTemplate.Part> subset = new ArrayList<>(size);
			for (int i = 0; i < n; i++) {
				if ((mask & (1 << i)) != 0) {
					subset.add(items.get(i));
				}
			}
			result.add(subset);
		}
		return result;
	}

	private static String names(List<QuestTemplate.Part> subset) {
		return subset.stream().map(p -> p.item).toList().toString();
	}
}
