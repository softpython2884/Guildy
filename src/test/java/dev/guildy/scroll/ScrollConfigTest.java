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
		assertEquals(18, cfg.usable().size(), "eighteen scrolls");
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
		for (String type : List.of("arcane", "sort_foudre", "sort_brasier", "sort_givre")) {
			check(missing, "/data/guildy/damage_type/" + type + ".json");
		}
		check(missing, "/guildy.scroll.mixins.json");
		assertTrue(missing.isEmpty(), "missing resources: " + missing);
		// Spell damage ignores armor like vanilla magic: every spell damage type is in the vanilla tag.
		try (InputStream in = ScrollConfigTest.class.getResourceAsStream("/data/minecraft/tags/damage_type/bypasses_armor.json")) {
			assertNotNull(in, "bypasses_armor tag shipped");
			String tag = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			assertTrue(tag.contains("\"replace\": false"), "the vanilla entries are kept");
			for (String type : List.of("arcane", "sort_foudre", "sort_brasier", "sort_givre")) {
				assertTrue(tag.contains("\"guildy:" + type + "\""), type + " ignores armor");
			}
			for (String proc : List.of("foudre", "brasier", "givre")) {
				assertFalse(tag.contains("\"guildy:" + proc + "\""), "the procs of the unique weapons keep the armor: " + proc);
			}
		}
	}

	@Test
	void damageSpellsHitHarder() {
		Map<Spell, Double> old = ScrollConfig.OLD_DAMAGE;
		assertEquals(8, old.size(), "every damage spell");
		for (Map.Entry<Spell, Double> entry : old.entrySet()) {
			double now = entry.getKey().param("damage").defaultValue();
			double ratio = now / entry.getValue();
			assertTrue(ratio >= 1.5 && ratio <= 2.5, entry.getKey() + ": x1.5 to x2.5, got x" + ratio);
			assertTrue(now <= entry.getKey().param("damage").max(), entry.getKey() + ": within the bounds");
		}
		double meteor = Spell.METEOR_SHOWER.param("damage").defaultValue() / old.get(Spell.METEOR_SHOWER);
		assertTrue(meteor >= 2.0, "the meteor shower clearly more: x" + meteor);
		// A full shower (every meteor on the boss) is a real dent in a guardian of 650 health points.
		assertTrue(Spell.METEOR_SHOWER.param("count").defaultValue() * Spell.METEOR_SHOWER.param("damage").defaultValue() >= 250);
		// The cursed grimoire stays far above the scroll and the grimoire, within the bounds of SpellPower.
		ScrollConfig cfg = new ScrollConfig();
		assertTrue(cfg.rebuild(id -> true).isEmpty());
		for (Spell spell : old.keySet()) {
			Spell.Param damage = spell.param("damage");
			double scroll = damage.defaultValue();
			double grimoire = SpellPower.of(cfg.grimoire.powerMultiplier).apply(damage, scroll);
			double cursed = cfg.cursedGrimoire.power(spell).apply(damage, scroll);
			assertTrue(cursed >= 1.5 * grimoire && grimoire >= scroll, spell + ": cursed " + cursed + ", grimoire " + grimoire);
			assertTrue(cursed < damage.max() * SpellPower.POWER_CEILING, spell + ": never on the ceiling");
		}
	}

	@Test
	void oldSpellDamageIsMigrated() {
		// A scrolls.json5 of Guildy 1.9 (format 2): every damage still on its old default, but the earthquake changed by the
		// admin; plus a scroll of the admin on an old default.
		ScrollConfig written = new ScrollConfig();
		written.configVersion = 2;
		for (ScrollConfig.ScrollDef def : written.scrolls) {
			Spell spell = Spell.byId(def.spell);
			if (ScrollConfig.OLD_DAMAGE.containsKey(spell)) {
				def.params.put("damage", ScrollConfig.OLD_DAMAGE.get(spell));
			}
			if (def.id.equals("seisme")) {
				def.params.put("damage", 13.0);
			}
		}
		ScrollConfig.ScrollDef own = new ScrollConfig.ScrollDef();
		own.id = "petite_pluie";
		own.spell = "meteor_shower";
		own.params = new LinkedHashMap<>(Map.of("count", 4.0, "damage", 12.0));
		written.scrolls.add(own);
		ScrollConfig.ScrollDef blessing = new ScrollConfig.ScrollDef();
		blessing.id = "soin_maison";
		blessing.spell = "blessing";
		blessing.params = new LinkedHashMap<>(Map.of("heal", 12.0));
		written.scrolls.add(blessing);
		ScrollConfig old = Json5.fromString(Json5.GSON.toJson(written), ScrollConfig.class);
		assertTrue(old.rebuild(id -> true).isEmpty());
		assertEquals(ScrollConfig.CURRENT_VERSION, old.configVersion);
		assertEquals(28.0, old.scroll("pluie_de_meteores").param("damage"), "12 -> 28");
		assertEquals(24.0, old.scroll("tempete_de_foudre").param("damage"));
		assertEquals(7.0, old.scroll("blizzard").param("damage"));
		assertEquals(34.0, old.scroll("nova_solaire").param("damage"));
		assertEquals(48.0, old.scroll("singularite").param("damage"));
		assertEquals(4.0, old.scroll("egide").param("damage"));
		assertEquals(8.0, old.scroll("rayon_arcanique").param("damage"));
		assertEquals(13.0, old.scroll("seisme").param("damage"), "the admin's value is kept");
		assertEquals(28.0, old.scroll("petite_pluie").param("damage"), "an admin scroll on the old default of its spell");
		assertEquals(4.0, old.scroll("petite_pluie").param("count"), "its other values are kept");
		assertEquals(12.0, old.scroll("soin_maison").param("heal"), "no damage, nothing to migrate");
		assertEquals(8, old.migrations().size(), "every migrated scroll reported: " + old.migrations());
		assertTrue(old.migrations().contains("petite_pluie.damage"));

		// Already in format 3: an old value is now the admin's choice.
		ScrollConfig current = Json5.fromString(Json5.GSON.toJson(written), ScrollConfig.class);
		current.configVersion = ScrollConfig.CURRENT_VERSION;
		assertTrue(current.rebuild(id -> true).isEmpty());
		assertEquals(12.0, current.scroll("pluie_de_meteores").param("damage"));
		assertTrue(current.migrations().isEmpty());

		// A fresh file: nothing to migrate; the round trip keeps the new values.
		ScrollConfig fresh = new ScrollConfig();
		assertTrue(fresh.rebuild(id -> true).isEmpty());
		assertTrue(fresh.migrations().isEmpty(), "fresh defaults: " + fresh.migrations());
		ScrollConfig again = Json5.fromString(Json5.GSON.toJson(old), ScrollConfig.class);
		assertTrue(again.rebuild(id -> true).isEmpty());
		assertEquals(28.0, again.scroll("pluie_de_meteores").param("damage"));
		assertTrue(again.migrations().isEmpty(), "migrated once");
	}

	@Test
	void newScrollsAreAddedOnce() {
		// A scrolls.json5 of Guildy 1.11 (format 3): the twelve old scrolls only, their drop tables (one weight edited by the
		// admin), a cursed grimoire section without the new spells, the expedition table removed by the admin.
		ScrollConfig written = new ScrollConfig();
		written.configVersion = 3;
		written.scrolls.removeIf(def -> ScrollConfig.NEW_SCROLLS.contains(def.id));
		for (ScrollConfig.DropTable table : written.drops.values()) {
			table.items.keySet().removeIf(ScrollConfig.NEW_SCROLLS::contains);
		}
		written.drops.get("boss").items.put("rappel", 99.0);
		written.drops.remove("expedition");
		written.cursedGrimoire.spells.remove(Spell.SCOURGE.id);
		written.cursedGrimoire.spells.remove(Spell.SPECTRAL_CHAINS.id);
		written.cursedGrimoire.spells.put(Spell.METEOR_SHOWER.id, new LinkedHashMap<>(Map.of("count", 1.1)));
		assertEquals(12, written.scrolls.size());

		ScrollConfig old = Json5.fromString(Json5.GSON.toJson(written), ScrollConfig.class);
		assertTrue(old.rebuild(id -> true).isEmpty(), "migrated without warnings");
		assertEquals(ScrollConfig.CURRENT_VERSION, old.configVersion);
		assertEquals(18, old.usable().size(), "the six new scrolls are added");
		for (String id : ScrollConfig.NEW_SCROLLS) {
			assertNotNull(old.scroll(id), id + " added");
			assertTrue(old.migrations().contains("+" + id), id + " reported: " + old.migrations());
		}
		ScrollConfig defaults = new ScrollConfig();
		defaults.rebuild(id -> true);
		for (String source : ScrollConfig.SOURCES) {
			if (source.equals("expedition")) {
				continue;
			}
			for (String id : ScrollConfig.NEW_SCROLLS) {
				assertEquals(defaults.table(source).items.get(id), old.table(source).items.get(id), source + "." + id + ": default weight added");
			}
		}
		assertEquals(99.0, old.table("boss").items.get("rappel"), "the admin's weight is kept");
		assertNull(old.table("expedition"), "a table removed by the admin is not brought back");
		assertEquals(Map.of("count", 1.1), old.cursedGrimoire.spells.get(Spell.METEOR_SHOWER.id), "the admin's cursed settings are kept");
		assertEquals(defaults.cursedGrimoire.spells.get(Spell.SCOURGE.id), old.cursedGrimoire.spells.get(Spell.SCOURGE.id), "new cursed settings");
		assertEquals(defaults.cursedGrimoire.spells.get(Spell.SPECTRAL_CHAINS.id), old.cursedGrimoire.spells.get(Spell.SPECTRAL_CHAINS.id));

		// Once migrated, a scroll or an entry the admin removes stays removed.
		ScrollConfig edited = Json5.fromString(Json5.GSON.toJson(old), ScrollConfig.class);
		edited.scrolls.removeIf(def -> def.id.equals("fleau"));
		for (ScrollConfig.DropTable table : edited.drops.values()) {
			table.items.remove("fleau");
		}
		edited.drops.get("quest").items.remove("permutation");
		ScrollConfig again = Json5.fromString(Json5.GSON.toJson(edited), ScrollConfig.class);
		assertTrue(again.rebuild(id -> true).isEmpty());
		assertNull(again.scroll("fleau"), "removed by the admin after the migration");
		assertFalse(again.table("quest").items.containsKey("permutation"));
		assertTrue(again.migrations().isEmpty(), "migrated once: " + again.migrations());
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
		assertEquals(Spell.METEOR_SHOWER.param("damage").defaultValue(), fixed.param("damage"), "NaN: default");
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
