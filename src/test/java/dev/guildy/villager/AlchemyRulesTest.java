package dev.guildy.villager;

import dev.guildy.config.Json5;
import dev.guildy.scroll.ScrollConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlchemyRulesTest {

	/** EnchantmentHelper.getEnchantmentCost (26.3), slot 2, with the same two draws. */
	private static int vanillaThirdSlot(int roll8, int rollShelves, int bookcases) {
		if (bookcases > 15) {
			bookcases = 15;
		}
		int selected = roll8 + 1 + (bookcases >> 1) + rollShelves;
		return Math.max(selected, bookcases * 2);
	}

	@Test
	void levelFollowsTheVanillaThirdSlot() {
		for (int shelves = 0; shelves <= 15; shelves++) {
			for (int a = 0; a < 8; a++) {
				for (int b = 0; b <= shelves; b++) {
					assertEquals(Math.max(1, vanillaThirdSlot(a, b, shelves)), AlchemyRules.enchantLevel(a, b, shelves),
							"shelves " + shelves + ", draws " + a + "/" + b);
				}
			}
		}
	}

	@Test
	void levelRangeByBookshelves() {
		assertEquals(30, AlchemyRules.minLevel(15), "15 bookshelves: always level 30");
		assertEquals(30, AlchemyRules.maxLevel(15));
		assertEquals(30, AlchemyRules.maxLevel(32), "power capped at 15 bookshelves");
		assertEquals(15, AlchemyRules.power(40));
		assertEquals(1, AlchemyRules.minLevel(0), "bare table: low levels");
		assertEquals(8, AlchemyRules.maxLevel(0));
		assertEquals(8, AlchemyRules.minLevel(4));
		assertEquals(14, AlchemyRules.maxLevel(4));
		for (int shelves = 0; shelves < 15; shelves++) {
			assertTrue(AlchemyRules.maxLevel(shelves) <= AlchemyRules.maxLevel(shelves + 1), "more bookshelves never lower the best level");
		}
		// Draws out of range are clamped.
		assertEquals(AlchemyRules.maxLevel(6), AlchemyRules.enchantLevel(99, 99, 6));
		assertEquals(AlchemyRules.minLevel(6), AlchemyRules.enchantLevel(-5, -5, 6));
	}

	@Test
	void levelsStayInRangeWithRandomDraws() {
		Random random = new Random(42);
		for (int i = 0; i < 2000; i++) {
			int shelves = random.nextInt(20);
			int power = AlchemyRules.power(shelves);
			int level = AlchemyRules.enchantLevel(random.nextInt(8), random.nextInt(power + 1), shelves);
			assertTrue(level >= AlchemyRules.minLevel(shelves) && level <= AlchemyRules.maxLevel(shelves) && level <= 30);
		}
	}

	@Test
	void workTimeAndProgress() {
		assertEquals(180, AlchemyRules.workSeconds(30, 6), "level 30, 6 s per level: 3 minutes");
		assertEquals(6, AlchemyRules.workSeconds(0, 6), "at least one level");
		assertEquals(0, AlchemyRules.progressPercent(0, 600));
		assertEquals(45, AlchemyRules.progressPercent(270, 600));
		assertEquals(99, AlchemyRules.progressPercent(599, 600), "never 100 % before the end");
		assertEquals(100, AlchemyRules.progressPercent(600, 600));
		assertEquals(100, AlchemyRules.progressPercent(5, 0));
	}

	@Test
	void dailyCap() {
		long today = AlchemyRules.day(5 * 24000L + 1000);
		assertEquals(5, today);
		assertEquals(4, AlchemyRules.madeToday(5, 4, today));
		assertEquals(0, AlchemyRules.madeToday(4, 8, today), "new day: the count starts over");
		assertEquals(0, AlchemyRules.madeToday(6, 8, today), "clock set back: starts over");
		assertEquals(0, AlchemyRules.madeToday(-1, 3, today), "never counted");
		assertFalse(AlchemyRules.capReached(7, 8));
		assertTrue(AlchemyRules.capReached(8, 8));
		assertTrue(AlchemyRules.capReached(1, 0), "at least one book a day");
		assertEquals(0, AlchemyRules.day(23999));
		assertEquals(1, AlchemyRules.day(24000));
	}

	@Test
	void tableChoice() {
		assertTrue(AlchemyRules.compareTables(15, 400, 4, 1) < 0, "the most bookshelves first");
		assertTrue(AlchemyRules.compareTables(4, 100, 4, 25) > 0, "ties: the nearest");
		assertEquals(0, AlchemyRules.compareTables(20, 9, 15, 9), "bookshelves beyond 15 do not count");
		assertTrue(AlchemyRules.compareTables(20, 4, 15, 9) < 0);
	}

	@Test
	void configDefaultsAndBounds() {
		AlchemistConfig cfg = new AlchemistConfig();
		cfg.validate();
		assertEquals(5, cfg.minGuildLevel);
		assertEquals(6, cfg.secondsPerLevel);
		assertEquals(8, cfg.maxBooksPerDay);
		assertEquals(0, cfg.lapisPerBook, "only time and books by default");
		cfg.secondsPerLevel = 0;
		cfg.maxBooksPerDay = 0;
		cfg.lapisPerBook = 9;
		cfg.booksPerTrip = 100;
		cfg.scanIntervalSeconds = 0;
		cfg.minGuildLevel = 500;
		cfg.validate();
		assertEquals(1, cfg.secondsPerLevel);
		assertEquals(1, cfg.maxBooksPerDay);
		assertEquals(3, cfg.lapisPerBook);
		assertEquals(64, cfg.booksPerTrip);
		assertEquals(10, cfg.scanIntervalSeconds);
		assertEquals(100, cfg.minGuildLevel);
		cfg.secondsPerLevel = 1000;
		cfg.validate();
		assertTrue(AlchemyRules.workSeconds(AlchemyRules.maxLevel(15), cfg.secondsPerLevel) <= 3600, "one book takes an hour at most");
	}

	// ------------------------------------------------------------------ spell scrolls

	/** Rarities of the default scrolls.json5. */
	private static Function<String, String> defaultRarities() {
		ScrollConfig scrolls = new ScrollConfig();
		scrolls.rebuild(id -> true);
		return id -> {
			ScrollConfig.ScrollDef def = scrolls.scroll(id);
			return def == null ? null : def.rarity().name();
		};
	}

	@Test
	void scrollOutcomeChanceCapAndPaper() {
		// chance
		assertEquals(AlchemyRules.Outcome.SCROLL, AlchemyRules.outcome(0.10, 0.15, true, 0, 2, 2, 2));
		assertEquals(AlchemyRules.Outcome.BOOK, AlchemyRules.outcome(0.15, 0.15, true, 0, 2, 2, 2), "roll above the chance: a book");
		assertEquals(AlchemyRules.Outcome.BOOK, AlchemyRules.outcome(0.0, 0.0, true, 0, 2, 64, 2), "chance 0: never a scroll");
		assertEquals(AlchemyRules.Outcome.SCROLL, AlchemyRules.outcome(0.9999, 1.0, true, 0, 2, 2, 2), "chance 1: always a scroll");
		assertEquals(AlchemyRules.Outcome.BOOK, AlchemyRules.outcome(0.5, Double.NaN, true, 0, 2, 2, 2), "NaN chance: never");
		// no scroll possible at that level, daily scroll cap
		assertEquals(AlchemyRules.Outcome.BOOK, AlchemyRules.outcome(0.0, 1.0, false, 0, 2, 2, 2), "nothing possible at that level");
		assertEquals(AlchemyRules.Outcome.BOOK, AlchemyRules.outcome(0.0, 1.0, true, 2, 2, 2, 2), "daily scroll cap reached");
		assertEquals(AlchemyRules.Outcome.BOOK, AlchemyRules.outcome(0.0, 1.0, true, 0, 0, 2, 2), "cap 0: never");
		assertEquals(AlchemyRules.Outcome.SCROLL, AlchemyRules.outcome(0.0, 1.0, true, 1, 2, 2, 2));
		// paper
		assertEquals(AlchemyRules.Outcome.BOOK_NO_PAPER, AlchemyRules.outcome(0.0, 1.0, true, 0, 2, 1, 2), "not enough paper: a book instead");
		assertEquals(AlchemyRules.Outcome.BOOK_NO_PAPER, AlchemyRules.outcome(0.0, 1.0, true, 0, 2, 0, 2));
		assertEquals(AlchemyRules.Outcome.SCROLL, AlchemyRules.outcome(0.0, 1.0, true, 0, 2, 0, 0), "no paper needed");
		assertEquals(AlchemyRules.Outcome.BOOK, AlchemyRules.outcome(0.0, 1.0, true, 2, 2, 0, 2), "the cap comes before the paper message");
		assertEquals(AlchemyRules.Outcome.BOOK, AlchemyRules.outcome(0.5, 0.15, true, 0, 2, 0, 2), "no roll: no paper message");
	}

	@Test
	void rarerScrollsNeedTheirLevel() {
		assertTrue(AlchemyRules.scrollAllowed("RARE", 1, 30), "basic scrolls at any level");
		assertFalse(AlchemyRules.scrollAllowed("EPIC", 29, 30));
		assertTrue(AlchemyRules.scrollAllowed("EPIC", 30, 30), "epic scrolls with a full table");
		assertFalse(AlchemyRules.scrollAllowed("LEGENDARY", 8, 30));
		assertTrue(AlchemyRules.scrollAllowed("MYTHIC", 12, 10));
		assertFalse(AlchemyRules.scrollAllowed(null, 30, 31), "unknown rarity: a rare one only from the level");

		Map<String, Double> table = new LinkedHashMap<>();
		table.put("blizzard", 50.0);
		table.put("rappel", 1.0);
		Function<String, String> rarity = defaultRarities();
		for (int i = 0; i < 100; i++) {
			assertEquals("rappel", AlchemyRules.pickScroll(table, rarity, AlchemyRules.maxLevel(0), 30, i / 100.0),
					"a bare table only writes rare scrolls");
		}
		assertTrue(AlchemyRules.anyScroll(table, rarity, 8, 30));
		Map<String, Double> epicOnly = Map.of("blizzard", 1.0, "egide", 1.0);
		assertFalse(AlchemyRules.anyScroll(epicOnly, rarity, 29, 30), "only epic scrolls below level 30: none possible");
		assertNull(AlchemyRules.pickScroll(epicOnly, rarity, 29, 30, 0.3));
		assertTrue(AlchemyRules.anyScroll(epicOnly, rarity, 30, 30));
	}

	@Test
	void scrollPickFollowsTheWeights() {
		Map<String, Double> table = new LinkedHashMap<>();
		table.put("rappel", 6.0);
		table.put("unknown_scroll", 100.0);
		table.put("benediction", 3.0);
		table.put("revelation", 0.0);
		table.put("seisme", 1.0);
		Function<String, String> rarity = defaultRarities();
		// Unknown ids and weights of 0 are left out: rappel [0, 0.6), benediction [0.6, 0.9), seisme [0.9, 1).
		assertEquals("rappel", AlchemyRules.pickScroll(table, rarity, 30, 30, 0.0));
		assertEquals("rappel", AlchemyRules.pickScroll(table, rarity, 30, 30, 0.59));
		assertEquals("benediction", AlchemyRules.pickScroll(table, rarity, 30, 30, 0.61));
		assertEquals("seisme", AlchemyRules.pickScroll(table, rarity, 30, 30, 0.95));
		assertEquals("seisme", AlchemyRules.pickScroll(table, rarity, 30, 30, 1.0), "roll out of range: clamped");
		// Below the epic level the share of the rare ones grows: rappel 6/9.
		assertEquals("benediction", AlchemyRules.pickScroll(table, rarity, 10, 30, 0.95));

		Map<String, Integer> counts = new HashMap<>();
		Random random = new Random(7);
		Map<String, Double> defaults = AlchemistConfig.defaultScrolls();
		for (int i = 0; i < 20000; i++) {
			counts.merge(AlchemyRules.pickScroll(defaults, rarity, 30, 30, random.nextDouble()), 1, Integer::sum);
		}
		assertTrue(counts.get("rappel") > counts.get("benediction") && counts.get("benediction") > counts.get("revelation"),
				"Rappel the most common, then Bénédiction, Révélation: " + counts);
		int epic = counts.getOrDefault("blizzard", 0) + counts.getOrDefault("seisme", 0) + counts.getOrDefault("egide", 0)
				+ counts.getOrDefault("tempete_de_foudre", 0);
		assertTrue(epic > 0 && epic < 20000 / 5, "epic scrolls are rare even at a full table: " + epic);
		assertNull(AlchemyRules.pickScroll(Map.of(), rarity, 30, 30, 0.5), "empty table");
		assertNull(AlchemyRules.pickScroll(null, rarity, 30, 30, 0.5));
		assertNull(AlchemyRules.pickScroll(table, id -> null, 30, 30, 0.5), "scroll module absent: nothing known");
	}

	@Test
	void defaultScrollTable() {
		AlchemistConfig cfg = new AlchemistConfig();
		cfg.validate();
		assertEquals(0.15, cfg.scrollChance);
		assertEquals(2, cfg.scrollPaper);
		assertEquals(2, cfg.maxScrollsPerDay);
		assertEquals(30, cfg.epicScrollLevel);
		assertTrue(cfg.cleanScrolls().isEmpty());
		Function<String, String> rarity = defaultRarities();
		assertTrue(cfg.unknownScrolls(rarity).isEmpty(), "every default id exists in scrolls.json5");
		String best = null;
		for (Map.Entry<String, Double> entry : cfg.scrolls.entrySet()) {
			String found = rarity.apply(entry.getKey());
			assertTrue("RARE".equals(found) || "EPIC".equals(found), "never legendary or mythic by default: " + entry.getKey());
			if (best == null || entry.getValue() > cfg.scrolls.get(best)) {
				best = entry.getKey();
			}
		}
		assertEquals("rappel", best, "Rappel is the most common");
		assertTrue(AlchemyRules.anyScroll(cfg.scrolls, rarity, 1, cfg.epicScrollLevel), "a bare table writes basic scrolls");
	}

	@Test
	void scrollSettingsAreBounded() {
		AlchemistConfig cfg = new AlchemistConfig();
		cfg.scrollChance = 4;
		cfg.scrollPaper = -1;
		cfg.maxBooksPerDay = 3;
		cfg.maxScrollsPerDay = 10;
		cfg.epicScrollLevel = 99;
		cfg.validate();
		assertEquals(1.0, cfg.scrollChance);
		assertEquals(0, cfg.scrollPaper);
		assertEquals(10, cfg.maxScrollsPerDay, "its own daily cap, beside the one of books");
		assertEquals(30, cfg.epicScrollLevel);
		cfg.scrollChance = Double.NaN;
		cfg.maxScrollsPerDay = -4;
		cfg.epicScrollLevel = 0;
		cfg.scrollPaper = 500;
		cfg.validate();
		assertEquals(0.0, cfg.scrollChance);
		assertEquals(0, cfg.maxScrollsPerDay);
		assertEquals(1, cfg.epicScrollLevel);
		assertEquals(64, cfg.scrollPaper);

		Map<String, Double> table = new LinkedHashMap<>();
		table.put(" Rappel ", 5.0);
		table.put("Bad id!", 3.0);
		table.put("seisme", -1.0);
		table.put("egide", null);
		table.put("revelation", Double.POSITIVE_INFINITY);
		table.put("pas_un_parchemin", 2.0);
		cfg.scrolls = table;
		List<String> warnings = cfg.cleanScrolls();
		assertEquals(4, warnings.size(), "invalid id and three bad weights: " + warnings);
		assertEquals(Map.of("rappel", 5.0, "pas_un_parchemin", 2.0), cfg.scrolls, "normalized ids, bad entries removed");
		List<String> unknown = cfg.unknownScrolls(defaultRarities());
		assertEquals(1, unknown.size());
		assertTrue(unknown.getFirst().contains("pas_un_parchemin"), "unknown id warned: " + unknown);
		cfg.scrolls = null;
		cfg.validate();
		assertEquals(AlchemistConfig.defaultScrolls(), cfg.scrolls, "missing table: defaults");
	}

	@Test
	void paperForTheScrollsOfTheTrip() {
		assertEquals(4, AlchemyRules.paperWanted(2, 2, 4, 0), "two scrolls left today");
		assertEquals(2, AlchemyRules.paperWanted(2, 2, 1, 0), "one work left on this trip");
		assertEquals(3, AlchemyRules.paperWanted(2, 2, 4, 1), "minus the paper in the bag");
		assertEquals(0, AlchemyRules.paperWanted(2, 0, 4, 0), "scroll cap reached");
		assertEquals(0, AlchemyRules.paperWanted(0, 2, 4, 0), "no paper needed");
		assertEquals(0, AlchemyRules.paperWanted(2, 2, 4, 9));
	}

	@Test
	void dailyCountsOfBooksAndScrolls() {
		CraftData data = new CraftData();
		long day = 7;
		data.countWork(day, false);
		data.countWork(day, true);
		data.countWork(day, true);
		assertEquals(3, data.worksToday(day), "scrolls count in the daily cap");
		assertEquals(2, data.scrollsToday(day));
		assertTrue(AlchemyRules.capReached(data.worksToday(day), 3));
		assertEquals(0, data.worksToday(day + 1), "new day: both counts start over");
		assertEquals(0, data.scrollsToday(day + 1));
		// A book of the next day must not carry yesterday's scrolls over.
		data.countWork(day + 1, false);
		assertEquals(1, data.worksToday(day + 1));
		assertEquals(0, data.scrollsToday(day + 1));
		data.countWork(day + 1, true);
		assertEquals(2, data.worksToday(day + 1));
		assertEquals(1, data.scrollsToday(day + 1));
		assertEquals(0, data.scrollsToday(day), "clock set back: starts over");
	}

	@Test
	void scrollSettingsSurviveTheJson5RoundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("villagers.json5");
		VillagerConfig cfg = new VillagerConfig();
		cfg.validate();
		cfg.alchemist.scrolls = new LinkedHashMap<>(Map.of("rappel", 3.0));
		cfg.alchemist.scrollChance = 0.5;
		Json5.write(file, cfg);
		String text = Files.readString(file);
		assertTrue(text.contains("scrollChance") && text.contains("maxScrollsPerDay") && text.contains("epicScrollLevel"), "keys written");
		VillagerConfig read = Json5.read(file, VillagerConfig.class);
		read.validate();
		assertEquals(Map.of("rappel", 3.0), read.alchemist.scrolls);
		assertEquals(0.5, read.alchemist.scrollChance);
		// A file written before the scrolls gets the defaults.
		Files.writeString(file, "{ alchemist: { maxBooksPerDay: 5 } }");
		VillagerConfig old = Json5.read(file, VillagerConfig.class);
		old.validate();
		assertEquals(AlchemistConfig.defaultScrolls(), old.alchemist.scrolls);
		assertEquals(2, old.alchemist.maxScrollsPerDay);
		assertEquals(5, old.alchemist.maxBooksPerDay);
	}
}
