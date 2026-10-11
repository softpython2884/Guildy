package dev.guildy.shop;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopValidatorTest {
	private static final Set<String> KNOWN = Set.of("minecraft:stone", "minecraft:oak_log", "minecraft:iron_ingot",
			"minecraft:gold_ingot", "minecraft:gold_nugget", "minecraft:gold_block", "minecraft:raw_gold", "minecraft:gold_ore",
			"minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore", "minecraft:raw_gold_block", "minecraft:chest");
	private static final Predicate<String> EXISTS = KNOWN::contains;

	@Test
	void sellIsClampedToBuy() {
		ShopEntry entry = new ShopEntry("minecraft:stone", 16, 4, 9, 1);
		List<String> warnings = new ArrayList<>();
		assertNull(ShopValidator.check(entry, EXISTS, warnings));
		assertEquals(4, entry.sell);
		assertEquals(1, warnings.size());
	}

	@Test
	void sellWithoutBuyIsKept() {
		ShopEntry entry = new ShopEntry("stone", 32, 0, 3, 1);
		List<String> warnings = new ArrayList<>();
		assertNull(ShopValidator.check(entry, EXISTS, warnings));
		assertEquals("minecraft:stone", entry.item);
		assertEquals(3, entry.sell);
		assertTrue(warnings.isEmpty());
	}

	@Test
	void goldItemsAreRefused() {
		for (String id : List.of("minecraft:gold_ingot", "gold_nugget", "minecraft:gold_block", "minecraft:raw_gold",
				"minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore", "RAW_GOLD_BLOCK")) {
			assertNotNull(ShopValidator.check(new ShopEntry(id, 1, 5, 1, 1), EXISTS, new ArrayList<>()), id);
		}
	}

	@Test
	void unknownAndEmptyEntriesAreRefused() {
		assertNotNull(ShopValidator.check(new ShopEntry("minecraft:unobtainium", 1, 5, 1, 1), EXISTS, new ArrayList<>()));
		assertNotNull(ShopValidator.check(new ShopEntry("", 1, 5, 1, 1), EXISTS, new ArrayList<>()));
		assertNotNull(ShopValidator.check(new ShopEntry("minecraft:stone", 1, 0, 0, 1), EXISTS, new ArrayList<>()));
	}

	@Test
	void valuesAreClamped() {
		ShopEntry entry = new ShopEntry("minecraft:stone", 0, -4, -2, -1);
		entry.buy = 3;
		List<String> warnings = new ArrayList<>();
		assertNull(ShopValidator.check(entry, EXISTS, warnings));
		assertEquals(1, entry.amount);
		assertEquals(0, entry.sell);
		assertEquals(1, entry.minLevel);
		ShopEntry huge = new ShopEntry("minecraft:stone", 100000, 3, 1, 1);
		ShopValidator.check(huge, EXISTS, warnings);
		assertEquals(ShopValidator.MAX_LOT, huge.amount);
	}

	@Test
	void sameItemCannotBeSoldAboveItsCheapestPurchase() {
		ShopEntry cheap = new ShopEntry("minecraft:oak_log", 16, 4, 1, 1);
		ShopEntry generous = new ShopEntry("minecraft:oak_log", 8, 0, 3, 1);
		List<ShopCategory> categories = List.of(
				new ShopCategory("a", "A", "minecraft:chest", new ArrayList<>(List.of(cheap))),
				new ShopCategory("b", "B", "minecraft:unknown_icon", new ArrayList<>(List.of(generous))));
		List<String> warnings = new ArrayList<>();
		List<ShopCategory> usable = ShopValidator.validate(categories, EXISTS, warnings);
		assertEquals(2, usable.size());
		// 4 gold for 16 logs = 0.25 per log: 8 logs can be sold for at most 2.
		assertEquals(2, generous.sell);
		assertEquals("minecraft:chest", usable.get(1).icon);
		assertTrue(warnings.stream().anyMatch(w -> w.contains("cheapest")));
	}

	@Test
	void refusedEntriesAreHidden() {
		List<ShopCategory> categories = List.of(new ShopCategory("a", "A", "minecraft:chest", new ArrayList<>(List.of(
				new ShopEntry("minecraft:gold_ingot", 1, 2, 1, 1),
				new ShopEntry("minecraft:stone", 16, 2, 1, 1)))));
		List<String> warnings = new ArrayList<>();
		List<ShopCategory> usable = ShopValidator.validate(categories, EXISTS, warnings);
		assertEquals(1, usable.get(0).entries.size());
		assertEquals("minecraft:stone", usable.get(0).entries.get(0).item);
		// The refused entry stays in the config list (so the admin can fix it).
		assertEquals(2, categories.get(0).entries.size());
	}

	@Test
	void lotsAndTotals() {
		ShopEntry entry = new ShopEntry("minecraft:stone", 16, 5, 2, 1);
		assertEquals(40, ShopValidator.buyTotal(entry, 8));
		assertEquals(0, ShopValidator.sellableLots(entry, 15, false));
		assertEquals(1, ShopValidator.sellableLots(entry, 40, false));
		assertEquals(2, ShopValidator.sellableLots(entry, 40, true));
		ShopEntry notSold = new ShopEntry("minecraft:stone", 16, 0, 2, 1);
		assertEquals(-1, ShopValidator.buyTotal(notSold, 1));
		ShopEntry notBought = new ShopEntry("minecraft:stone", 16, 5, 0, 1);
		assertEquals(0, ShopValidator.sellableLots(notBought, 64, true));
	}

	@Test
	void defaultCatalogIsConsistent() {
		List<ShopCategory> defaults = ShopConfig.defaults();
		List<String> warnings = new ArrayList<>();
		List<ShopCategory> usable = ShopValidator.validate(defaults, id -> true, warnings);
		assertTrue(warnings.isEmpty(), "warnings: " + warnings);
		assertEquals(defaults.size(), usable.size());
		for (int i = 0; i < defaults.size(); i++) {
			assertEquals(defaults.get(i).entries.size(), usable.get(i).entries.size(), defaults.get(i).id);
			for (ShopEntry entry : usable.get(i).entries) {
				assertTrue(!ShopValidator.FORBIDDEN.contains(entry.item), entry.item);
				assertTrue(entry.buy == 0 || entry.sell <= entry.buy, entry.item);
			}
		}
	}
}
