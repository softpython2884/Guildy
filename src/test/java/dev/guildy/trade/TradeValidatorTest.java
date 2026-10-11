package dev.guildy.trade;

import dev.guildy.shop.ShopCategory;
import dev.guildy.shop.ShopEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeValidatorTest {
	private static final Map<String, Double> NONE = Map.of();

	private static String check(TradeEntry entry, Map<String, Double> sells, Map<String, Double> buys, List<String> warnings) {
		return TradeValidator.check(entry, id -> !id.contains("unknown"), sells, buys, warnings);
	}

	@Test
	void goldAndUnknownItemsAreRefused() {
		List<String> warnings = new ArrayList<>();
		assertNotNull(check(TradeEntry.of("minecraft:gold_ingot", 1, 5, 1, 1), NONE, NONE, warnings));
		assertNotNull(check(TradeEntry.of("gold_block", 1, 50, 1, 1), NONE, NONE, warnings), "ids are normalized before the check");
		assertNotNull(check(TradeEntry.of("minecraft:raw_gold", 1, 5, 1, 1), NONE, NONE, warnings));
		assertNotNull(check(TradeEntry.of("minecraft:unknown_thing", 1, 5, 1, 1), NONE, NONE, warnings));
		assertNotNull(check(TradeEntry.of("", 1, 5, 1, 1), NONE, NONE, warnings));
	}

	@Test
	void valuesAreClamped() {
		List<String> warnings = new ArrayList<>();
		TradeEntry entry = TradeEntry.of("Oak_Sapling ", 0, 0, 0, -3);
		assertNull(check(entry, NONE, NONE, warnings));
		assertEquals("minecraft:oak_sapling", entry.item);
		assertEquals(1, entry.count);
		assertEquals(1, entry.price);
		assertEquals(1, entry.maxUses);
		assertEquals(0, entry.weight);
		TradeEntry big = TradeEntry.of("minecraft:stone", 500, 100_000, 500, 1);
		assertNull(check(big, NONE, NONE, warnings));
		assertEquals(TradeValidator.MAX_COUNT, big.count);
		assertEquals(TradeRolls.MAX_PRICE, big.price);
		assertEquals(TradeValidator.MAX_USES, big.maxUses);
	}

	@Test
	void merchantsNeverUndercutTheShop() {
		List<ShopCategory> shop = List.of(new ShopCategory("c", "c", "minecraft:chest", new ArrayList<>(List.of(
				new ShopEntry("minecraft:oak_sapling", 8, 3, 0, 1),      // sold 0.375 / unit
				new ShopEntry("minecraft:rotten_flesh", 32, 0, 1, 1),    // bought back 1/32 per unit
				new ShopEntry("minecraft:emerald", 4, 12, 4, 1)))));      // sold 3 / unit, bought back 1 / unit
		Map<String, Double>[] prices = TradeValidator.shopPrices(shop);
		assertEquals(0.375, prices[0].get("minecraft:oak_sapling"), 1e-9);
		assertEquals(3.0, prices[0].get("minecraft:emerald"), 1e-9);
		assertEquals(1.0, prices[1].get("minecraft:emerald"), 1e-9);

		List<String> warnings = new ArrayList<>();
		TradeEntry saplings = TradeEntry.of("minecraft:oak_sapling", 16, 2, 4, 1);
		assertNull(check(saplings, prices[0], prices[1], warnings));
		assertEquals(6, saplings.price, "16 saplings at the shop cost 6 gold: price raised");
		TradeEntry emeralds = TradeEntry.of("minecraft:emerald", 4, 14, 4, 1);
		assertNull(check(emeralds, prices[0], prices[1], warnings));
		assertEquals(14, emeralds.price, "already above the shop price");
		TradeEntry flesh = TradeEntry.of("minecraft:rotten_flesh", 64, 1, 4, 1);
		assertNull(check(flesh, prices[0], prices[1], warnings));
		assertEquals(2, flesh.price, "never below the shop buy-back price");
		TradeEntry other = TradeEntry.of("minecraft:cactus", 4, 1, 4, 1);
		assertNull(check(other, prices[0], prices[1], warnings));
		assertEquals(1, other.price, "items the shop does not deal keep their price");
	}

	@Test
	void enchantedEntriesAreNormalized() {
		List<String> warnings = new ArrayList<>();
		TradeEntry book = TradeEntry.book("Mending", 0, 40, 1);
		assertNull(check(book, NONE, NONE, warnings));
		assertEquals("minecraft:mending", book.enchantment);
		assertEquals(1, book.level);
		TradeEntry high = TradeEntry.book("minecraft:efficiency", 99, 40, 1);
		assertNull(check(high, NONE, NONE, warnings));
		assertEquals(TradeValidator.MAX_LEVEL, high.level);
		TradeEntry plain = TradeEntry.of("minecraft:stone", 1, 1, 1, 1);
		plain.enchantment = null;
		assertNull(check(plain, NONE, NONE, warnings));
		assertEquals("", plain.enchantment);
	}

	@Test
	void cleanKeepsOnlyUsableEntries() {
		List<String> warnings = new ArrayList<>();
		List<TradeEntry> entries = new ArrayList<>();
		entries.add(TradeEntry.of("minecraft:stone", 1, 1, 1, 1));
		entries.add(null);
		entries.add(TradeEntry.of("minecraft:gold_nugget", 1, 1, 1, 1));
		List<TradeEntry> usable = TradeValidator.clean("t", entries, id -> true, NONE, NONE, warnings);
		assertEquals(1, usable.size());
		assertTrue(warnings.stream().anyMatch(w -> w.contains("gold_nugget")));
		assertEquals(3, entries.size(), "refused entries stay in the config file");
	}
}
