package dev.guildy.quest;

import dev.guildy.shop.ShopCategory;
import dev.guildy.shop.ShopConfig;
import dev.guildy.shop.ShopEntry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Default content invariant: a DELIVER quest fulfilled with items bought at the shop never pays back more gold
 * than those items cost (no gold loop between the shop and the quest board).
 */
class EconomyBalanceTest {
	/** Shop article used for tag targets. */
	private static final Map<String, String> TAG_TO_ITEM = Map.of("#minecraft:wool", "minecraft:white_wool");

	@Test
	void deliveriesNeverBeatShopPrices() {
		Map<String, Double> unitPrice = new HashMap<>();
		for (ShopCategory category : ShopConfig.defaults()) {
			for (ShopEntry entry : category.entries) {
				if (entry.buy > 0) {
					unitPrice.merge(entry.item, (double) entry.buy / entry.amount, Math::min);
				}
			}
		}
		for (QuestTemplate template : QuestConfig.defaults()) {
			if (template.type != QuestType.DELIVER) {
				continue;
			}
			String item = TAG_TO_ITEM.getOrDefault(template.target, template.target);
			Double price = unitPrice.get(item);
			if (price == null) {
				continue;
			}
			for (int level = 1; level <= 10; level++) {
				int minAmount = template.amountMin + (int) Math.floor(template.amountPerLevel * (level - 1));
				int maxGold = template.rewardGoldMax + (int) Math.floor(template.rewardGoldPerLevel * (level - 1));
				double cheapestCost = minAmount * price;
				assertTrue(maxGold < cheapestCost, template.id + " level " + level + ": reward " + maxGold + " >= cost " + cheapestCost);
			}
		}
	}
}
