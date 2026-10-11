package dev.guildy.trade;

import dev.guildy.config.Json5;
import dev.guildy.event.EventConfig;
import dev.guildy.quest.QuestConfig;
import dev.guildy.quest.QuestTemplate;
import dev.guildy.quest.QuestType;
import dev.guildy.shop.ShopConfig;
import dev.guildy.shop.ShopValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Default content invariants: the default merchant tables are valid as written (no gold item, no price below the default
 * shop, sane stocks), the three hub tiers are ordered, and trade.json5 / events.json5 survive the Json5 round trip.
 */
class TradeDefaultsTest {

	@SuppressWarnings("unchecked")
	private static Map<String, Double>[] shopPrices() {
		return TradeValidator.shopPrices(ShopConfig.defaults());
	}

	@Test
	void defaultTablesNeedNoFix() {
		Map<String, Double>[] shop = shopPrices();
		TradeConfig trade = new TradeConfig();
		List<String> warnings = trade.rebuild(id -> true, shop[0], shop[1]);
		assertTrue(warnings.isEmpty(), "trade.json5 defaults: " + warnings);
		EventConfig events = new EventConfig();
		warnings = events.rebuild(id -> true, shop[0], shop[1]);
		assertTrue(warnings.isEmpty(), "events.json5 defaults: " + warnings);

		assertEquals(3, trade.maxTier());
		for (int t = 1; t <= 3; t++) {
			assertEquals(trade.tier(t).trades.size(), trade.tier(t).usable().size(), "tier " + t + " fully usable");
			assertTrue(trade.tier(t).usable().size() >= 2 * trade.tier(t).offersMax, "tier " + t + " has enough variety");
		}
		assertEquals(events.caravan.trades.size(), events.caravan.usable().size());
		assertEquals(events.bookMerchant.books.size(), events.bookMerchant.usable().size());
		assertTrue(events.bookMerchant.usable().stream().allMatch(TradeEntry::enchanted), "the scholar only sells enchanted books");
	}

	@Test
	void tiersAreOrderedAndCostMore() {
		TradeConfig trade = new TradeConfig();
		trade.rebuild(id -> true, Map.of(), Map.of());
		assertEquals(0, trade.tier(1).costGold);
		assertEquals(3, trade.tier(2).minLevel);
		assertEquals(150, trade.tier(2).costGold);
		assertEquals("minecraft:emerald", trade.tier(2).costItems.getFirst().item);
		assertEquals(16, trade.tier(2).costItems.getFirst().count);
		assertEquals(5, trade.tier(3).minLevel);
		assertEquals(400, trade.tier(3).costGold);
		assertEquals("minecraft:diamond_block", trade.tier(3).costItems.getFirst().item);
		for (int t = 2; t <= trade.maxTier(); t++) {
			assertTrue(trade.tier(t).minLevel >= trade.tier(t - 1).minLevel);
			assertTrue(trade.tier(t).offersMax >= trade.tier(t - 1).offersMax);
		}
		// Unknown cost items are dropped, levels never decrease.
		trade.tiers.get(1).costItems.add(new TradeConfig.Cost("minecraft:nope", 3));
		trade.tiers.get(2).minLevel = 1;
		List<String> warnings = trade.rebuild(id -> !id.contains("nope"), Map.of(), Map.of());
		assertEquals(1, trade.tier(2).costItems.size());
		assertEquals(3, trade.tier(3).minLevel);
		assertFalse(warnings.isEmpty());
	}

	@Test
	void merchantsNeverSellDeliveryQuestItemsBelowTheShop() {
		Map<String, Double>[] shop = shopPrices();
		List<TradeEntry> all = new ArrayList<>();
		TradeConfig trade = new TradeConfig();
		for (TradeConfig.Tier tier : trade.tiers) {
			all.addAll(tier.trades);
		}
		all.addAll(EventConfig.defaultCaravanTrades());
		all.addAll(EventConfig.defaultBooks());
		for (QuestTemplate template : QuestConfig.defaults()) {
			if (template.type != QuestType.DELIVER) {
				continue;
			}
			String target = ShopValidator.normalizeId(template.target);
			// Tag targets accept any wool: compared with the shop's white wool (as EconomyBalanceTest does).
			boolean wool = target.equals("#minecraft:wool");
			String priced = wool ? "minecraft:white_wool" : target;
			for (TradeEntry entry : all) {
				String item = ShopValidator.normalizeId(entry.item);
				if (!entry.enchanted() && (wool ? item.endsWith("_wool") : item.equals(target))) {
					Double shopUnit = shop[0].get(priced);
					assertTrue(shopUnit != null && entry.unitPrice() >= shopUnit,
							entry + " is a delivery quest item sold below the shop price");
				}
			}
		}
		for (TradeEntry entry : all) {
			assertFalse(ShopValidator.FORBIDDEN.contains(ShopValidator.normalizeId(entry.item)), entry + " sells gold");
		}
	}

	@Test
	void configsSurviveTheJson5RoundTrip(@TempDir Path dir) throws Exception {
		Path tradeFile = dir.resolve("trade.json5");
		Json5.write(tradeFile, new TradeConfig());
		TradeConfig trade = Json5.read(tradeFile, TradeConfig.class);
		TradeConfig defaults = new TradeConfig();
		assertEquals(defaults.tiers.size(), trade.tiers.size());
		for (int i = 0; i < defaults.tiers.size(); i++) {
			assertEquals(defaults.tiers.get(i).trades.size(), trade.tiers.get(i).trades.size());
			assertEquals(defaults.tiers.get(i).costGold, trade.tiers.get(i).costGold);
		}
		assertEquals(defaults.visits.staySeconds, trade.visits.staySeconds);

		Path eventsFile = dir.resolve("events.json5");
		Json5.write(eventsFile, new EventConfig());
		EventConfig events = Json5.read(eventsFile, EventConfig.class);
		EventConfig expected = new EventConfig();
		assertEquals(expected.bloodNight.chance, events.bloodNight.chance);
		assertEquals(expected.pillagerRaid.theme.mobs.size(), events.pillagerRaid.theme.mobs.size());
		assertEquals(expected.pillagerRaid.theme.bosses.getFirst().entity, events.pillagerRaid.theme.bosses.getFirst().entity);
		assertEquals(expected.caravan.trades.size(), events.caravan.trades.size());
		assertEquals(expected.bookMerchant.books.getFirst().enchantment, events.bookMerchant.books.getFirst().enchantment);
		assertTrue(events.rebuild(id -> true, Map.of(), Map.of()).isEmpty());
	}
}
