package dev.guildy.scroll;

import dev.guildy.config.Json5;
import dev.guildy.unique.UniqueRarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * scrolls.json5: valid defaults (every spell has its scroll, every source a table, every model and damage type exists in the
 * mod resources), sensible drop chances, clamping of bad values, Json5 round trip.
 */
class ScrollConfigTest {
	@Test
	void defaultsNeedNoFix() {
		ScrollConfig cfg = new ScrollConfig();
		List<String> warnings = cfg.rebuild(id -> true);
		assertTrue(warnings.isEmpty(), "scrolls.json5 defaults: " + warnings);
		assertEquals(cfg.scrolls.size(), cfg.usable().size(), "every default scroll is usable");
		assertEquals(12, cfg.usable().size(), "twelve scrolls");
		Set<Spell> spells = EnumSet.noneOf(Spell.class);
		Set<UniqueRarity> rarities = EnumSet.noneOf(UniqueRarity.class);
		for (ScrollConfig.ScrollDef def : cfg.usable()) {
			spells.add(def.spell());
			rarities.add(def.rarity());
			assertFalse(def.lore.isEmpty(), def.id + " has a story");
			assertTrue(def.item.startsWith("minecraft:"), def.id + " base item");
			assertTrue(def.channelSeconds >= 0.5 && def.channelSeconds <= 5, def.id + " channel time");
			assertTrue(def.cooldownSeconds >= 10, def.id + " real cooldown");
			assertEquals(def.spell().params().size(), def.params.size(), def.id + ": every parameter written in the file");
		}
		assertEquals(EnumSet.allOf(Spell.class), spells, "one scroll per spell");
		assertEquals(EnumSet.allOf(UniqueRarity.class), rarities, "every rarity is used");
		for (String source : ScrollConfig.SOURCES) {
			ScrollConfig.DropTable table = cfg.table(source);
			assertNotNull(table, "table for " + source);
			assertFalse(table.items.isEmpty(), source + " drops something");
			assertTrue(table.chance > 0 && table.chance <= 1, source + " chance");
		}
	}

	@Test
	void ruinBossesAreTheMainSource() {
		ScrollConfig cfg = new ScrollConfig();
		cfg.rebuild(id -> true);
		double ruin = ScrollRolls.expected(cfg.table("ruin_boss"), id -> true);
		double nether = ScrollRolls.expected(cfg.table("nether_ruin_boss"), id -> true);
		for (String source : List.of("boss", "blood_boss", "legendary_quest", "quest", "expedition", "trade")) {
			double other = ScrollRolls.expected(cfg.table(source), id -> true);
			assertTrue(ruin > other, "ruin bosses drop more scrolls than " + source);
			assertTrue(nether > other, "nether ruin bosses drop more scrolls than " + source);
		}
		assertTrue(cfg.table("boss").chance < cfg.table("blood_boss").chance, "blood night bosses drop scrolls more often");
		assertTrue(cfg.table("quest").chance < cfg.table("legendary_quest").chance, "legendary contracts are generous");
		assertTrue(cfg.table("quest").chance <= 0.1 && cfg.table("expedition").chance <= 0.1, "common sources stay rare");
		// The mythic scroll only comes from the hardest sources.
		for (ScrollConfig.ScrollDef def : cfg.usable()) {
			if (def.rarity() != UniqueRarity.MYTHIC) {
				continue;
			}
			for (String source : List.of("boss", "quest", "expedition", "trade")) {
				assertFalse(cfg.table(source).items.containsKey(def.id), "no mythic scroll in '" + source + "': " + def.id);
			}
			assertTrue(ScrollRolls.probability(cfg.table("nether_ruin_boss"), def.id, id -> true) > 0, "mythic from the nether ruins");
		}
		// The best scrolls weigh more in the ruins than at the horde bosses.
		double bestInRuins = share(cfg, "ruin_boss");
		double bestAtBosses = share(cfg, "boss");
		assertTrue(bestInRuins > bestAtBosses, "legendary / mythic share: ruins " + bestInRuins + " vs bosses " + bestAtBosses);
	}

	/** Share (0..1) of legendary and mythic scrolls in a table. */
	private static double share(ScrollConfig cfg, String source) {
		double best = 0;
		for (ScrollConfig.ScrollDef def : cfg.usable()) {
			if (def.rarity() == UniqueRarity.LEGENDARY || def.rarity() == UniqueRarity.MYTHIC) {
				best += ScrollRolls.probability(cfg.table(source), def.id, id -> true);
			}
		}
		return best / cfg.table(source).chance;
	}

	@Test
	void tradePricesFollowRarityAndTier() {
		ScrollConfig cfg = new ScrollConfig();
		cfg.rebuild(id -> true);
		assertEquals(0, cfg.tradePrice(UniqueRarity.RARE, 1), "no scroll at the tier I hub");
		assertTrue(cfg.tradePrice(UniqueRarity.RARE, 2) > 0);
		assertEquals(0, cfg.tradePrice(UniqueRarity.LEGENDARY, 2), "legendary scrolls from tier III");
		assertTrue(cfg.tradePrice(UniqueRarity.LEGENDARY, 3) > cfg.tradePrice(UniqueRarity.EPIC, 3));
		assertTrue(cfg.tradePrice(UniqueRarity.EPIC, 3) > cfg.tradePrice(UniqueRarity.RARE, 3));
	}

	@Test
	void defaultsOnlyReferenceShippedContent() throws Exception {
		ScrollConfig cfg = new ScrollConfig();
		cfg.rebuild(id -> true);
		List<String> missing = new ArrayList<>();
		Set<String> models = new HashSet<>();
		for (ScrollConfig.ScrollDef def : cfg.usable()) {
			assertFalse(def.model.isEmpty(), def.id + " has a model for the resource pack");
			assertTrue(models.add(def.model), "own model for " + def.id);
			check(missing, "/assets/guildy/items/" + def.model + ".json");
			check(missing, "/assets/guildy/models/item/" + def.model + ".json");
			check(missing, "/assets/guildy/textures/item/" + def.model + ".png");
		}
		for (String type : List.of("arcane", "foudre", "brasier", "givre")) {
			check(missing, "/data/guildy/damage_type/" + type + ".json");
		}
		check(missing, "/guildy.scroll.mixins.json");
		assertTrue(missing.isEmpty(), "missing resources: " + missing);
	}

	private static void check(List<String> missing, String path) throws Exception {
		try (InputStream in = ScrollConfigTest.class.getResourceAsStream(path)) {
			if (in == null) {
				missing.add(path);
			}
		}
	}

	@Test
	void badValuesAreFixed() {
		ScrollConfig cfg = new ScrollConfig();
		ScrollConfig.ScrollDef bad = new ScrollConfig.ScrollDef();
		bad.id = "Not Valid!";
		bad.spell = "blessing";
		ScrollConfig.ScrollDef noSpell = new ScrollConfig.ScrollDef();
		noSpell.id = "ghost";
		noSpell.spell = "summon_dragon";
		ScrollConfig.ScrollDef duplicate = new ScrollConfig.ScrollDef();
		duplicate.id = "rappel";
		duplicate.spell = "recall";
		duplicate.name = "Second";
		ScrollConfig.ScrollDef odd = new ScrollConfig.ScrollDef();
		odd.id = " Odd_Scroll ";
		odd.spell = "Meteor_Shower";
		odd.item = "Ender_Pearl";
		odd.rarity = "divine";
		odd.animation = "dance";
		odd.cooldownGroup = "Not a group!";
		odd.channelSeconds = 99;
		odd.cooldownSeconds = -5;
		odd.maxStack = 500;
		odd.model = "Bad Model";
		odd.params = new LinkedHashMap<>(Map.of("count", 900.0, "damage", Double.NaN, "explosions", 3.0));
		cfg.scrolls.addAll(List.of(bad, noSpell, duplicate, odd));
		ScrollConfig.DropTable table = cfg.drops.get("ruin_boss");
		table.chance = 7;
		table.min = 0;
		table.max = 99;
		table.items.put("ghost", 5.0);
		table.items.put("odd_scroll", -3.0);
		cfg.drops.put("Weird_Source", new ScrollConfig.DropTable());
		cfg.casting.moveSpeed = 4;
		cfg.casting.disabledDimensions = new ArrayList<>(List.of("The_End", "not a dimension!"));
		cfg.limits.maxEffectsPerPlayer = 0;
		cfg.limits.particleBudget = 1_000_000;
		cfg.trade.prices = new LinkedHashMap<>(Map.of("legendary", 999_999, "COMMON", 3));

		List<String> warnings = cfg.rebuild(id -> !id.contains("ender_pearl"));
		assertFalse(warnings.isEmpty());
		assertNull(cfg.scroll("not valid!"));
		assertNull(cfg.scroll("ghost"), "unknown spell refused");
		assertEquals("Rappel", cfg.scroll("rappel").name, "the first definition of an id wins");
		ScrollConfig.ScrollDef fixed = cfg.scroll("odd_scroll");
		assertNotNull(fixed);
		assertEquals(Spell.METEOR_SHOWER, fixed.spell());
		assertEquals("minecraft:paper", fixed.item, "base item with its own use replaced by paper");
		assertEquals(UniqueRarity.RARE, fixed.rarity());
		assertEquals("toot_horn", fixed.animation);
		assertEquals("odd_scroll", fixed.cooldownGroup, "invalid group: own group");
		assertEquals(10.0, fixed.channelSeconds);
		assertEquals(0.05, fixed.cooldownSeconds);
		assertEquals(64, fixed.maxStack);
		assertEquals("", fixed.model);
		assertEquals(30.0, fixed.param("count"), "count clamped to its maximum");
		assertEquals(12.0, fixed.param("damage"), "NaN: default");
		assertFalse(fixed.params.containsKey("explosions"), "unknown parameter dropped");
		assertEquals(Spell.METEOR_SHOWER.params().size(), fixed.params.size(), "missing parameters filled with defaults");
		assertEquals(1.0, cfg.table("ruin_boss").chance);
		assertEquals(1, cfg.table("ruin_boss").min);
		assertEquals(16, cfg.table("ruin_boss").max);
		assertFalse(cfg.table("ruin_boss").items.containsKey("ghost"));
		assertEquals(0.0, (double) cfg.table("ruin_boss").items.get("odd_scroll"));
		assertNotNull(cfg.table("weird_source"), "kept (with a warning) for later use");
		assertEquals(1.0, cfg.casting.moveSpeed);
		assertEquals(List.of("minecraft:the_end"), cfg.casting.disabledDimensions);
		assertTrue(cfg.dimensionDisabled("minecraft:the_end"));
		assertEquals(1, cfg.limits.maxEffectsPerPlayer);
		assertEquals(20000, cfg.limits.particleBudget);
		assertEquals(100000, cfg.trade.prices.get("LEGENDARY"));
		assertFalse(cfg.trade.prices.containsKey("COMMON"));
		assertEquals(40, cfg.trade.prices.get("RARE"), "missing rarities get their default price");
	}

	@Test
	void survivesTheJson5RoundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("scrolls.json5");
		ScrollConfig defaults = new ScrollConfig();
		defaults.rebuild(id -> true);
		Json5.write(file, defaults);
		ScrollConfig read = Json5.read(file, ScrollConfig.class);
		assertTrue(read.rebuild(id -> true).isEmpty());
		assertEquals(defaults.usable().size(), read.usable().size());
		for (ScrollConfig.ScrollDef def : defaults.usable()) {
			ScrollConfig.ScrollDef copy = read.scroll(def.id);
			assertNotNull(copy, def.id);
			assertEquals(def.name, copy.name);
			assertEquals(def.spell, copy.spell);
			assertEquals(def.params, copy.params);
			assertEquals(def.cooldownGroup, copy.cooldownGroup);
			assertEquals(def.fingerprint(), copy.fingerprint(), def.id + ": same fingerprint after a reload (no needless rebuild)");
		}
		assertEquals(defaults.table("ruin_boss").items, read.table("ruin_boss").items);
		assertEquals(defaults.table("ruin_boss").max, read.table("ruin_boss").max);
		assertEquals(defaults.trade.prices, read.trade.prices);
	}
}
