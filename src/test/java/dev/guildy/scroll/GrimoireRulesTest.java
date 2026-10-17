package dev.guildy.scroll;

import dev.guildy.config.Json5;
import dev.guildy.unique.UniqueRarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grimoire rules: pages (normalized), fusion (first blank page, duplicate, full), erasing, turning pages, the
 * custom_model_data value, food costs (saturation first) and the power multiplier; the grimoire section of scrolls.json5.
 */
class GrimoireRulesTest {
	private static GrimoireRules.Pages pages(int selected, String... ids) {
		return new GrimoireRules.Pages(Arrays.asList(ids), selected);
	}

	@Test
	void pagesAreNormalized() {
		GrimoireRules.Pages pages = new GrimoireRules.Pages(Arrays.asList(" Blizzard ", null, "SEISME", "", "extra"), 7);
		assertEquals(List.of("blizzard", "", "seisme", ""), pages.spells(), "four pages, lower case, blank for null, extra dropped");
		assertEquals(0, pages.selected(), "selected page out of range: first page");
		assertEquals(2, pages.filled());
		assertEquals(4, GrimoireRules.Pages.EMPTY.spells().size());
		assertEquals(0, GrimoireRules.Pages.EMPTY.filled());
		assertEquals("", pages.spell(-1));
		assertEquals("", pages.spell(4));
		assertEquals(pages(1, "a", "b"), pages(1, "a", "b", "", ""), "same pages, same record");
	}

	@Test
	void fusingFillsTheFirstBlankPage() {
		GrimoireRules.FuseResult first = GrimoireRules.fuse(GrimoireRules.Pages.EMPTY, "blizzard");
		assertEquals(GrimoireRules.Fuse.ADDED, first.outcome());
		assertEquals(0, first.page());
		assertEquals(0, first.pages().selected(), "the first spell is selected");
		GrimoireRules.FuseResult second = GrimoireRules.fuse(first.pages().select(0), "Seisme");
		assertEquals(GrimoireRules.Fuse.ADDED, second.outcome());
		assertEquals(1, second.page());
		assertEquals(List.of("blizzard", "seisme", "", ""), second.pages().spells());
		assertEquals(0, second.pages().selected(), "the selection does not move");

		GrimoireRules.FuseResult twice = GrimoireRules.fuse(second.pages(), "BLIZZARD");
		assertEquals(GrimoireRules.Fuse.DUPLICATE, twice.outcome(), "never twice the same spell");
		assertEquals(second.pages(), twice.pages(), "unchanged");
		assertEquals(-1, twice.page());

		GrimoireRules.Pages full = pages(2, "a", "b", "c", "d");
		assertEquals(GrimoireRules.Fuse.FULL, GrimoireRules.fuse(full, "e").outcome(), "four pages at most");
		assertEquals(full, GrimoireRules.fuse(full, "e").pages());
		assertEquals(GrimoireRules.Fuse.INVALID, GrimoireRules.fuse(full, " ").outcome());
		assertEquals(GrimoireRules.Fuse.INVALID, GrimoireRules.fuse(GrimoireRules.Pages.EMPTY, null).outcome());

		// A hole left by an erased page is filled first; a blank selected page selects the new spell.
		GrimoireRules.FuseResult hole = GrimoireRules.fuse(pages(3, "a", "", "c", "d"), "b");
		assertEquals(1, hole.page());
		assertEquals(List.of("a", "b", "c", "d"), hole.pages().spells());
		assertEquals(3, hole.pages().selected(), "selected page written: kept");
		GrimoireRules.FuseResult blankSelected = GrimoireRules.fuse(pages(2, "a", "b", "", ""), "c");
		assertEquals(2, blankSelected.pages().selected(), "the blank selected page gets the new spell and stays selected");
		GrimoireRules.FuseResult elsewhere = GrimoireRules.fuse(pages(3, "", "b", "c", ""), "a");
		assertEquals(0, elsewhere.page());
		assertEquals(0, elsewhere.pages().selected(), "blank selected page: the new page is selected");
	}

	@Test
	void erasingAPage() {
		GrimoireRules.Pages pages = pages(1, "a", "b", "", "d");
		GrimoireRules.Pages erased = GrimoireRules.erase(pages, 1);
		assertEquals(List.of("a", "", "", "d"), erased.spells());
		assertEquals(3, erased.selected(), "the selected page erased: the next written page is selected");
		GrimoireRules.Pages other = GrimoireRules.erase(pages, 0);
		assertEquals(1, other.selected(), "another page erased: the selection stays");
		assertEquals(pages, GrimoireRules.erase(pages, 2), "blank page: unchanged");
		assertEquals(pages, GrimoireRules.erase(pages, 9), "out of range: unchanged");
		GrimoireRules.Pages last = GrimoireRules.erase(pages(2, "", "", "c", ""), 2);
		assertEquals(0, last.filled());
		assertEquals(2, last.selected(), "nothing left: the selection stays on the blank page");
	}

	@Test
	void turningPages() {
		GrimoireRules.Pages pages = pages(0, "a", "", "c", "d");
		assertEquals(2, GrimoireRules.next(pages, id -> true), "blank pages are skipped");
		assertEquals(3, GrimoireRules.next(pages.select(2), id -> true));
		assertEquals(0, GrimoireRules.next(pages.select(3), id -> true), "cyclic");
		assertEquals(3, GrimoireRules.next(pages, id -> !id.equals("c")), "unusable (unknown) spells are skipped");
		assertEquals(0, GrimoireRules.next(pages(0, "a"), id -> true), "a single spell: itself");
		assertEquals(-1, GrimoireRules.next(GrimoireRules.Pages.EMPTY, id -> true), "nothing to select");
		assertEquals(-1, GrimoireRules.next(pages, id -> false));

		// Hand-made data (loot table) selecting a blank page: the next written page is selected.
		assertEquals(2, GrimoireRules.normalize(pages(1, "", "", "c")).selected());
		assertEquals(pages, GrimoireRules.normalize(pages), "written selected page: unchanged");
		assertEquals(GrimoireRules.Pages.EMPTY, GrimoireRules.normalize(GrimoireRules.Pages.EMPTY), "blank grimoire: unchanged");
	}

	@Test
	void customModelDataIsTheSelectedScroll() {
		assertEquals("empty", GrimoireRules.modelValue(GrimoireRules.Pages.EMPTY));
		assertEquals(GrimoireRules.NO_SPELL_MODEL, GrimoireRules.modelValue(pages(1, "a", "", "c")), "blank selected page");
		assertEquals("blizzard", GrimoireRules.modelValue(pages(2, "seisme", "rappel", "blizzard")));
	}

	@Test
	void foodCostsByRarity() {
		ScrollConfig cfg = new ScrollConfig();
		cfg.rebuild(id -> true);
		ScrollConfig.Grimoire g = cfg.grimoire;
		assertEquals(3, g.foodCost(UniqueRarity.RARE));
		assertEquals(5, g.foodCost(UniqueRarity.EPIC));
		assertEquals(7, g.foodCost(UniqueRarity.LEGENDARY));
		assertEquals(10, g.foodCost(UniqueRarity.MYTHIC));
		assertEquals(0, GrimoireRules.foodCost(Map.of("RARE", 3), "EPIC"), "missing rarity: free");
		assertEquals(20, GrimoireRules.foodCost(Map.of("RARE", 99), "rare"), "at most the whole bar");
		assertEquals(0, GrimoireRules.foodCost(null, "RARE"));
		assertTrue(GrimoireRules.canAfford(5, 5));
		assertFalse(GrimoireRules.canAfford(4, 5), "food below the cost: refused (saturation does not count)");
		assertTrue(GrimoireRules.canAfford(0, 0));
	}

	@Test
	void saturationIsSpentFirst() {
		GrimoireRules.Meal fromFood = GrimoireRules.spend(20, 0.0F, 5);
		assertEquals(15, fromFood.food());
		assertEquals(0.0F, fromFood.saturation());
		assertEquals(0.0F, fromFood.exhaustion());

		GrimoireRules.Meal fromSaturation = GrimoireRules.spend(20, 5.0F, 3);
		assertEquals(20, fromSaturation.food(), "enough saturation: the food bar does not move");
		assertEquals(2.0F, fromSaturation.saturation(), 1.0E-5);

		GrimoireRules.Meal both = GrimoireRules.spend(18, 2.0F, 7);
		assertEquals(13, both.food(), "2 from saturation, 5 from food");
		assertEquals(0.0F, both.saturation());

		GrimoireRules.Meal fraction = GrimoireRules.spend(10, 2.5F, 3);
		assertEquals(10, fraction.food(), "half a point left");
		assertEquals(0.0F, fraction.saturation());
		assertEquals(2.0F, fraction.exhaustion(), 1.0E-4, "half a point = 2 exhaustion (4 per point)");

		GrimoireRules.Meal free = GrimoireRules.spend(7, 1.0F, 0);
		assertEquals(7, free.food());
		assertEquals(1.0F, free.saturation());

		assertEquals(0, GrimoireRules.spend(2, 0.0F, 10).food(), "never below zero");
		assertEquals(17, GrimoireRules.spend(20, Float.NaN, 3).food(), "invalid saturation counts as none");
	}

	@Test
	void powerMultipliesDamageHealsAndDurations() {
		Spell.Param damage = Spell.METEOR_SHOWER.param("damage");
		Spell.Param fire = Spell.METEOR_SHOWER.param("fireSeconds");
		Spell.Param range = Spell.METEOR_SHOWER.param("range");
		Spell.Param count = Spell.METEOR_SHOWER.param("count");
		Spell.Param heal = Spell.BLESSING.param("heal");
		Spell.Param level = Spell.BLESSING.param("regenLevel");
		assertEquals(18.0, damage.powered(12, 1.5), 1.0E-9);
		assertEquals(6.0, fire.powered(4, 1.5), 1.0E-9);
		assertEquals(24.0, heal.powered(16, 1.5), 1.0E-9);
		assertEquals(40.0, range.powered(40, 1.5), "ranges never change");
		assertEquals(10.0, count.powered(10, 1.5), "counts never change");
		assertEquals(2.0, level.powered(2, 1.5), "levels never change");
		assertEquals(12.0, damage.powered(12, 1.0));
		for (Spell spell : Spell.values()) {
			for (Spell.Param param : spell.params()) {
				assertEquals(param.unit() == Spell.Unit.HEARTS || param.unit() == Spell.Unit.SECONDS, param.scalesWithPower(),
						spell.id + "." + param.name());
			}
		}
	}

	@Test
	void grimoireSectionDefaultsAndBounds() {
		ScrollConfig cfg = new ScrollConfig();
		assertTrue(cfg.rebuild(id -> true).isEmpty(), "defaults need no fix");
		ScrollConfig.Grimoire g = cfg.grimoire;
		assertTrue(g.channelSeconds > 0 && g.channelSeconds <= 0.5, "a very short channel");
		assertEquals(8, g.channelTicks());
		assertTrue(g.cooldownSeconds > 0 && g.cooldownSeconds <= 5, "a very short cooldown");
		assertEquals(1.0, g.powerMultiplier);
		assertEquals(0.08, g.dropChance("ruin_boss"));
		assertEquals(0.12, g.dropChance("nether_ruin_boss"));
		assertTrue(g.dropChance("legendary_quest") > 0 && g.dropChance("legendary_quest") < 0.1, "a small chance from legendary contracts");
		assertEquals(0.0, g.dropChance("boss"), "not from the horde bosses");
		assertTrue(g.dropChance("trade") > 0);
		assertTrue(g.tradePrice >= 300 && g.tradeMinTier == 3, "a tier III luxury");
		assertEquals("grimoire", g.model);
		for (ScrollConfig.ScrollDef def : cfg.usable()) {
			assertTrue(g.foodCost(def.rarity()) > 0 && g.foodCost(def.rarity()) <= 10, def.id + ": costs food");
		}

		ScrollConfig bad = new ScrollConfig();
		bad.grimoire.item = "Ender_Pearl";
		bad.grimoire.channelSeconds = 0;
		bad.grimoire.cooldownSeconds = -3;
		bad.grimoire.powerMultiplier = 99;
		bad.grimoire.animation = "dance";
		bad.grimoire.foodCost = new LinkedHashMap<>(Map.of("legendary", 50, "COMMON", 2));
		bad.grimoire.drops = new LinkedHashMap<>(Map.of("Ruin_Boss", 3.0, "weird", 0.5));
		bad.grimoire.tradePrice = -5;
		bad.grimoire.tradeMinTier = 42;
		bad.grimoire.model = "Bad Model";
		List<String> warnings = bad.rebuild(id -> !id.contains("ender_pearl"));
		assertFalse(warnings.isEmpty());
		ScrollConfig.Grimoire fixed = bad.grimoire;
		assertEquals("minecraft:enchanted_book", fixed.item);
		assertEquals(0.05, fixed.channelSeconds);
		assertEquals(1, fixed.channelTicks());
		assertEquals(0.05, fixed.cooldownSeconds);
		assertEquals(5.0, fixed.powerMultiplier);
		assertEquals("toot_horn", fixed.animation);
		assertEquals(20, fixed.foodCost.get("LEGENDARY"));
		assertFalse(fixed.foodCost.containsKey("COMMON"));
		assertEquals(3, fixed.foodCost.get("RARE"), "missing rarities get their default cost");
		assertEquals(1.0, fixed.dropChance("ruin_boss"));
		assertEquals(0.5, fixed.dropChance("weird"), "kept (with a warning)");
		assertEquals(1, fixed.tradePrice);
		assertEquals(9, fixed.tradeMinTier);
		assertEquals("", fixed.model);
		bad.grimoire.enabled = false;
		assertEquals(0.0, bad.grimoire.dropChance("ruin_boss"), "disabled: never dropped");
	}

	@Test
	void grimoireSectionSurvivesTheJson5RoundTripAndOldFiles(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("scrolls.json5");
		ScrollConfig defaults = new ScrollConfig();
		defaults.rebuild(id -> true);
		defaults.grimoire.powerMultiplier = 1.5;
		defaults.grimoire.foodCost.put("EPIC", 6);
		Json5.write(file, defaults);
		ScrollConfig read = Json5.read(file, ScrollConfig.class);
		assertTrue(read.rebuild(id -> true).isEmpty());
		assertEquals(1.5, read.grimoire.powerMultiplier);
		assertEquals(6, read.grimoire.foodCost.get("EPIC"));
		assertEquals(defaults.grimoire.drops, read.grimoire.drops);
		assertEquals(defaults.grimoire.fingerprint(), read.grimoire.fingerprint(), "same fingerprint after a reload");

		// A scrolls.json5 written before the grimoire: the section gets its defaults.
		Path old = dir.resolve("old.json5");
		Files.writeString(old, "{ \"configVersion\": 1, \"enabled\": true }");
		ScrollConfig upgraded = Json5.read(old, ScrollConfig.class);
		assertTrue(upgraded.rebuild(id -> true).isEmpty());
		assertEquals(0.08, upgraded.grimoire.dropChance("ruin_boss"));
		assertEquals(7, upgraded.grimoire.foodCost(UniqueRarity.LEGENDARY));
	}
}
