package dev.guildy.trade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unique weapon offer of the trade hub: one in the default tier III table (high gold price, sold once), added once
 * to the third tier of the files written before it, never compared with the shop prices.
 */
class TradeUniqueOfferTest {

	private static long uniques(TradeConfig cfg) {
		return cfg.tiers.stream().flatMap(t -> t.trades.stream()).filter(TradeEntry::isUnique).count();
	}

	@Test
	void tierThreeSellsOneUniqueWeapon() {
		TradeConfig cfg = new TradeConfig();
		assertTrue(cfg.rebuild(id -> true, Map.of(), Map.of()).isEmpty());
		assertEquals(1, uniques(cfg));
		TradeEntry offer = cfg.tier(3).usable().stream().filter(TradeEntry::isUnique).findFirst().orElseThrow();
		assertEquals(TradeEntry.RANDOM_UNIQUE, offer.unique);
		assertEquals(1, offer.count);
		assertEquals(1, offer.maxUses);
		assertTrue(offer.price >= 9 * 32, "a high gold price (blocks of gold): " + offer.price);
		assertTrue(offer.price <= TradeRolls.MAX_PRICE);
		assertEquals(2, cfg.configVersion);
		assertTrue(cfg.tier(1).usable().stream().noneMatch(TradeEntry::isUnique), "lower tiers never sell uniques");
	}

	@Test
	void oldFilesGetTheOfferOnce() {
		TradeConfig old = new TradeConfig();
		for (TradeConfig.Tier tier : old.tiers) {
			tier.trades.removeIf(TradeEntry::isUnique);
		}
		old.configVersion = 0;
		old.rebuild(id -> true, Map.of(), Map.of());
		assertEquals(1, uniques(old), "added to the third tier");
		assertTrue(old.tier(3).trades.stream().anyMatch(TradeEntry::isUnique));
		old.configVersion = 0;
		old.rebuild(id -> true, Map.of(), Map.of());
		assertEquals(1, uniques(old), "never twice");

		TradeConfig removed = new TradeConfig();
		removed.rebuild(id -> true, Map.of(), Map.of());
		removed.tier(3).trades.removeIf(TradeEntry::isUnique);
		removed.rebuild(id -> true, Map.of(), Map.of());
		assertEquals(0, uniques(removed), "an admin who removed it keeps it removed");

		TradeConfig twoTiers = new TradeConfig();
		twoTiers.tiers = new ArrayList<>(twoTiers.tiers.subList(0, 2));
		twoTiers.rebuild(id -> true, Map.of(), Map.of());
		assertEquals(0, uniques(twoTiers), "no third tier: nothing added");
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
