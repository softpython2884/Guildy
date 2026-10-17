package dev.guildy.trade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unique weapons at the trade hub: never sold by default (removed once from older files), an admin's own unique offer is
 * kept and never compared with the shop prices.
 */
class TradeUniqueOfferTest {

	private static long uniques(TradeConfig cfg) {
		return cfg.tiers.stream().flatMap(t -> t.trades.stream()).filter(TradeEntry::isUnique).count();
	}

	@Test
	void uniqueWeaponsAreNeverSoldByDefault() {
		TradeConfig cfg = new TradeConfig();
		assertTrue(cfg.rebuild(id -> true, Map.of(), Map.of()).isEmpty());
		assertEquals(0, uniques(cfg), "unique weapons are won in fights, never bought");
		assertEquals(TradeConfig.CURRENT_VERSION, cfg.configVersion);
	}

	@Test
	void oldFilesLoseTheirUniqueOffersOnce() {
		TradeConfig old = new TradeConfig();
		old.tier(3).trades.add(TradeEntry.unique(TradeEntry.RANDOM_UNIQUE, 360, 3));
		old.configVersion = 2;
		old.rebuild(id -> true, Map.of(), Map.of());
		assertEquals(0, uniques(old), "the version 2 offer is removed");

		TradeConfig readded = new TradeConfig();
		readded.rebuild(id -> true, Map.of(), Map.of());
		readded.tier(3).trades.add(TradeEntry.unique("foudrelame", 999, 1));
		readded.rebuild(id -> true, Map.of(), Map.of());
		assertEquals(1, uniques(readded), "an admin may add one back after the migration");
	}

	@Test
	void uniqueOffersAreNotShopItems() {
		List<String> warnings = new ArrayList<>();
		TradeEntry entry = TradeEntry.unique(" Foudrelame ", 99_999, -1);
		entry.item = "minecraft:unknown_icon";
		entry.count = 5;
		entry.maxUses = 9;
		entry.enchantment = "minecraft:mending";
		// The shop sells nether stars for a fortune: irrelevant for a unique offer.
		assertNull(TradeValidator.check(entry, id -> !id.contains("unknown"), Map.of(TradeEntry.UNIQUE_ICON, 10_000.0), Map.of(), warnings));
		assertEquals("foudrelame", entry.unique);
		assertEquals(TradeEntry.UNIQUE_ICON, entry.item, "unknown icon replaced");
		assertEquals(1, entry.count);
		assertEquals(1, entry.maxUses);
		assertEquals("", entry.enchantment);
		assertEquals(TradeRolls.MAX_PRICE, entry.price);
		assertEquals(0, entry.weight);
		assertEquals(1, warnings.size(), "price fixed: " + warnings);

		TradeEntry plain = TradeEntry.of("minecraft:stone", 1, 1, 1, 1);
		plain.unique = null;
		assertNull(TradeValidator.check(plain, id -> true, Map.of(), Map.of(), warnings));
		assertEquals("", plain.unique);
		TradeEntry copy = entry.copy();
		assertEquals(entry.unique, copy.unique);
	}
}
