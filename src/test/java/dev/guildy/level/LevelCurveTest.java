package dev.guildy.level;

import dev.guildy.config.GuildyConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelCurveTest {
	private static final List<Integer> CLASSIC = List.of(100, 250, 450, 700, 1000, 1400, 1900, 2500, 3200);

	private static GuildyConfig config() {
		GuildyConfig config = new GuildyConfig();
		config.validate();
		return config;
	}

	@Test
	void classicLevelsAreUnchanged() {
		GuildyConfig config = config();
		assertEquals(0, config.xpForLevel(1));
		assertEquals(0, config.xpForLevel(0));
		for (int level = 2; level <= 10; level++) {
			assertEquals((long) CLASSIC.get(level - 2), config.xpForLevel(level), "level " + level);
		}
	}

	@Test
	void defaultCurvePastLevelTen() {
		GuildyConfig config = config();
		assertEquals(100, config.maxLevel());
		assertEquals(4_540, config.xpForLevel(11), "1 340 XP for level 11");
		assertEquals(11_300, config.xpForLevel(15));
		assertEquals(22_900, config.xpForLevel(20));
		assertEquals(56_600, config.xpForLevel(30));
		assertEquals(166_000, config.xpForLevel(50));
		assertEquals(381_500, config.xpForLevel(75));
		assertEquals(684_500, config.xpForLevel(100));
		assertEquals(13_800, LevelCurve.stepXp(100, CLASSIC, 1200, 140));
	}

	@Test
	void curveIsStrictlyIncreasingAndNeverOverflows() {
		long previous = -1;
		long previousStep = 0;
		for (int level = 1; level <= LevelCurve.ABSOLUTE_MAX_LEVEL; level++) {
			long xp = LevelCurve.xpForLevel(level, CLASSIC, 1200, 140);
			assertTrue(xp > previous, "level " + level);
			if (level > 11) {
				assertTrue(xp - previous >= previousStep, "each level past 10 costs at least the previous one: " + level);
			}
			previousStep = xp - previous;
			previous = xp;
		}
		// Largest accepted settings at the largest level: still far below Long.MAX_VALUE.
		long huge = LevelCurve.xpForLevel(LevelCurve.ABSOLUTE_MAX_LEVEL, List.of(Integer.MAX_VALUE), 10_000_000, 1_000_000);
		assertTrue(huge > 0 && huge < Long.MAX_VALUE / 1000, "no overflow: " + huge);
	}

	@Test
	void longerListsKeepTheirExplicitThresholds() {
		List<Integer> list = new ArrayList<>(CLASSIC);
		for (int i = 0; i < 6; i++) {
			list.add(4000 + 1000 * i); // levels 11..16
		}
		assertEquals(16, LevelCurve.lastListedLevel(list));
		assertEquals(4000, LevelCurve.xpForLevel(11, list, 1200, 140));
		assertEquals(9000, LevelCurve.xpForLevel(16, list, 1200, 140));
		assertEquals(9000 + 1340, LevelCurve.xpForLevel(17, list, 1200, 140), "the formula continues from the last listed level");
		assertEquals(9000 + 1340 + 1480, LevelCurve.xpForLevel(18, list, 1200, 140));
	}

	@Test
	void shortListsContinueWithTheFormula() {
		List<Integer> list = List.of(100, 300);
		assertEquals(300, LevelCurve.xpForLevel(3, list, 500, 0));
		assertEquals(800, LevelCurve.xpForLevel(4, list, 500, 0));
		assertEquals(1300, LevelCurve.xpForLevel(5, list, 500, 0));
		assertEquals(500 * 2 + 10 * 3, LevelCurve.xpForLevel(3, List.of(), 500, 10), "empty list: formula from level 1");
	}

	@Test
	void levelForMatchesTheThresholds() {
		assertEquals(1, LevelCurve.levelFor(0, 100, CLASSIC, 1200, 140));
		assertEquals(10, LevelCurve.levelFor(3_200, 100, CLASSIC, 1200, 140));
		assertEquals(10, LevelCurve.levelFor(4_539, 100, CLASSIC, 1200, 140));
		assertEquals(11, LevelCurve.levelFor(4_540, 100, CLASSIC, 1200, 140));
		assertEquals(50, LevelCurve.levelFor(166_000, 100, CLASSIC, 1200, 140));
		assertEquals(100, LevelCurve.levelFor(Long.MAX_VALUE, 100, CLASSIC, 1200, 140), "capped by the maximum level");
	}
}
