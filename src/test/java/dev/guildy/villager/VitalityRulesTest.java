package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VitalityRulesTest {

	@Test
	void healthGrowsWithTheGuildLevel() {
		VitalityConfig config = new VitalityConfig();
		assertEquals(0.0, config.bonusHealth(1, false), 1e-9, "a level 1 worker keeps the vanilla 20 HP");
		assertEquals(16.0, config.bonusHealth(5, false), 1e-9, "+4 HP per level above 1");
		assertEquals(36.0, config.bonusHealth(10, false), 1e-9);
		assertTrue(config.bonusHealth(5, false) > config.bonusHealth(1, false), "level 5 villagers are tougher than level 1 ones");
	}

	@Test
	void guardsGetMoreHealth() {
		VitalityConfig config = new VitalityConfig();
		assertEquals(10.0, config.bonusHealth(1, true), 1e-9, "flat guard bonus from level 1");
		assertEquals(10.0 + 16.0 * 1.5, config.bonusHealth(5, true), 1e-9, "per level bonus x 1.5 + flat bonus");
		for (int level = 1; level <= 10; level++) {
			assertTrue(config.bonusHealth(level, true) > config.bonusHealth(level, false), "guards above workers at level " + level);
		}
	}

	@Test
	void bonusIsBoundedAndNeverNegative() {
		assertEquals(50.0, VitalityRules.bonusHealth(100, true, 4.0, 1.5, 10.0, 50.0), 1e-9, "capped by maxBonus");
		assertEquals(0.0, VitalityRules.bonusHealth(0, false, 4.0, 1.5, 10.0, 200.0), 1e-9, "level below 1");
		assertEquals(0.0, VitalityRules.bonusHealth(5, true, -4.0, -1.0, -10.0, 200.0), 1e-9, "negative settings count as 0");
		assertEquals(0.0, VitalityRules.bonusHealth(5, false, Double.NaN, 1.5, 10.0, 200.0), 1e-9);
	}

	@Test
	void validateBoundsTheSettings() {
		VitalityConfig config = new VitalityConfig();
		config.healthPerLevel = -3;
		config.guardMultiplier = Double.NaN;
		config.maxBonus = 1e9;
		config.regenPerSecond = 99;
		config.outOfCombatSeconds = -5;
		config.validate();
		assertEquals(0.0, config.healthPerLevel, 1e-9);
		assertEquals(0.0, config.guardMultiplier, 1e-9);
		assertEquals(1000.0, config.maxBonus, 1e-9);
		assertEquals(20.0, config.regenPerSecond, 1e-9);
		assertEquals(0, config.outOfCombatSeconds);
	}

	@Test
	void fullHealthStaysFullWhenTheMaximumGrows() {
		assertEquals(36.0, VitalityRules.healthAfterChange(20.0, 20.0, 36.0, false), 1e-9);
		assertEquals(36.0, VitalityRules.healthAfterChange(20.0, 20.0, 36.0, true), 1e-9);
	}

	@Test
	void woundedVillagersOnlyGainHealthOnLevelUp() {
		assertEquals(10.0, VitalityRules.healthAfterChange(10.0, 20.0, 36.0, false), 1e-9, "task switch / first application: no free heal");
		assertEquals(14.0, VitalityRules.healthAfterChange(10.0, 32.0, 36.0, true), 1e-9, "level-up: the gain is added");
		assertEquals(36.0, VitalityRules.healthAfterChange(35.0, 32.0, 36.0, true), 1e-9, "never above the maximum");
	}

	@Test
	void lowerMaximumCapsTheHealth() {
		assertEquals(36.0, VitalityRules.healthAfterChange(50.0, 54.0, 36.0, false), 1e-9);
		assertEquals(12.0, VitalityRules.healthAfterChange(12.0, 54.0, 36.0, true), 1e-9);
	}

	@Test
	void regenerationWaitsForTheEndOfTheFight() {
		assertFalse(VitalityRules.outOfCombat(1000, 900, 200));
		assertTrue(VitalityRules.outOfCombat(1100, 900, 200));
		assertTrue(VitalityRules.outOfCombat(1000, 1000, 0));
		assertEquals(1, VitalityRules.display(0.2), "a living villager never shows 0");
		assertEquals(0, VitalityRules.display(0.0));
	}
}
