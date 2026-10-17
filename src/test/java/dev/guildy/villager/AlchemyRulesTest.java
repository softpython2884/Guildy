package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
