package dev.guildy.level;

import dev.guildy.config.GuildyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelScalingTest {
	private static final double EPS = 1e-9;

	@Test
	void classicLevelsKeepTheirRate() {
		for (int level = 1; level <= 10; level++) {
			assertEquals(0.5 * (level - 1), LevelScaling.bonus(level, 0.5, 0.05, 4.5), EPS, "level " + level);
		}
		assertEquals(0, LevelScaling.bonus(0, 0.5, 0.05, 4.5), EPS, "nonsense level");
		assertEquals(9, LevelScaling.classicSteps(10));
		assertEquals(9, LevelScaling.classicSteps(400));
		assertEquals(0, LevelScaling.stepsAbove(10));
		assertEquals(90, LevelScaling.stepsAbove(100));
	}

	@Test
	void levelsPastTenUseTheirOwnCappedRate() {
		assertEquals(4.5 + 0.5, LevelScaling.bonus(20, 0.5, 0.05, 4.5), EPS);
		assertEquals(4.5 + 4.5, LevelScaling.bonus(100, 0.5, 0.05, 4.5), EPS);
		assertEquals(4.5 + 4.5, LevelScaling.bonus(500, 0.5, 0.05, 4.5), EPS, "capped");
		assertEquals(4.5 + 24.5, LevelScaling.bonus(500, 0.5, 0.05, 0), EPS, "0 = no cap");
		assertEquals(4.5, LevelScaling.bonus(500, 0.5, 0, 4.5), EPS, "no growth past 10");
		assertEquals(0, LevelScaling.bonus(50, -1, Double.NaN, 4.5), EPS, "negative or NaN rates count as 0");
	}

	@Test
	void softLevelOfTheQuests() {
		assertEquals(1, LevelScaling.softLevel(1, 0.2, 30));
		assertEquals(10, LevelScaling.softLevel(10, 0.2, 30));
		assertEquals(10, LevelScaling.softLevel(14, 0.2, 30));
		assertEquals(11, LevelScaling.softLevel(15, 0.2, 30));
		assertEquals(18, LevelScaling.softLevel(50, 0.2, 30));
		assertEquals(28, LevelScaling.softLevel(100, 0.2, 30));
		assertEquals(30, LevelScaling.softLevel(500, 0.2, 30), "capped");
		assertEquals(10, LevelScaling.softLevel(500, 0, 30), "rate 0: frozen at 10");
		assertEquals(10, LevelScaling.softLevel(500, 0.2, 3), "never below 10 past level 10");
	}

	@Test
	void defaultTerritoryStaysBounded() {
		GuildyConfig config = new GuildyConfig();
		config.validate();
		assertEquals(12, config.maxChunks(1));
		assertEquals(12 + 6 * 9, config.maxChunks(10), "unchanged up to level 10");
		assertEquals(66 + 10, config.maxChunks(20));
		assertEquals(156, config.maxChunks(100));
		assertEquals(256, config.maxChunks(500), "bounded total");
		for (int level = 2; level <= 500; level++) {
			assertTrue(config.maxChunks(level) >= config.maxChunks(level - 1), "never shrinks at level " + level);
		}
	}
}
