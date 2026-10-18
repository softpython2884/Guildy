package dev.guildy.wave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveFormulaTest {
	private static final double EPS = 1.0E-9;

	private static WaveConfig config() {
		WaveConfig config = new WaveConfig();
		config.validate();
		return config;
	}

	// ------------------------------------------------------------------ difficulty

	@Test
	void difficultyStartsAtBase() {
		WaveConfig.DifficultySettings d = config().difficulty;
		assertEquals(d.base, WaveFormula.difficulty(d, 0, 1, 1), EPS);
	}

	@Test
	void difficultyAddsAllTerms() {
		WaveConfig.DifficultySettings d = config().difficulty;
		// 1 + 0.5 * 10 + 0.5 * (5 - 1) + 0.75 * (3 - 1) = 9.5
		assertEquals(9.5, WaveFormula.difficulty(d, 10, 5, 3), EPS);
	}

	@Test
	void difficultyIsClamped() {
		WaveConfig.DifficultySettings d = config().difficulty;
		assertEquals(d.max, WaveFormula.difficulty(d, 10_000, 50, 20), EPS);
		// Nonsense inputs never go below the base.
		assertEquals(d.base, WaveFormula.difficulty(d, -5, 0, 0), EPS);
	}

	@Test
	void mobCountFollowsFormula() {
		WaveConfig.DifficultySettings d = config().difficulty;
		assertEquals(9, WaveFormula.mobCount(d, 1.0, 1.0));   // 6 + 3
		assertEquals(35, WaveFormula.mobCount(d, 9.5, 1.0));  // 34.5 rounds up
		assertEquals(66, WaveFormula.mobCount(d, 20.0, 1.0)); // 6 + 60
	}

	@Test
	void mobCountIsClamped() {
		WaveConfig.DifficultySettings d = config().difficulty;
		assertEquals(d.minMobs, WaveFormula.mobCount(d, 1.0, 0.0));
		assertEquals(d.maxMobsPerWave, WaveFormula.mobCount(d, 20.0, 5.0));
		d.minMobs = 50;
		d.maxMobsPerWave = 10; // inconsistent bounds: max wins
		assertEquals(10, WaveFormula.mobCount(d, 1.0, 1.0));
	}

	@Test
	void eliteChanceIsCappedThenBonused() {
		WaveConfig.DifficultySettings d = config().difficulty;
		assertEquals(0.02 + 0.015, WaveFormula.eliteChance(d, 1.0, 0), EPS);
		assertEquals(d.eliteMax, WaveFormula.eliteChance(d, 1000, 0), EPS);
		assertEquals(d.eliteMax + 0.25, WaveFormula.eliteChance(d, 1000, 0.25), EPS);
		assertEquals(1.0, WaveFormula.eliteChance(d, 1000, 5.0), EPS);
		assertEquals(0.0, WaveFormula.eliteChance(zeroElite(d), 10, 0), EPS);
	}

	private static WaveConfig.DifficultySettings zeroElite(WaveConfig.DifficultySettings d) {
		d.eliteBase = 0;
		d.elitePerDifficulty = 0;
		return d;
	}

	@Test
	void buffMultipliersGrowAndAreCapped() {
		WaveConfig cfg = config();
		double base = cfg.difficulty.base;
		assertEquals(1.0, WaveFormula.healthMultiplier(cfg.mobs, base, base), EPS);
		assertEquals(1.0, WaveFormula.damageMultiplier(cfg.mobs, base, base), EPS);
		assertEquals(1 + 0.08 * 9, WaveFormula.healthMultiplier(cfg.mobs, base, 10), EPS);
		assertEquals(1 + 0.05 * 9, WaveFormula.damageMultiplier(cfg.mobs, base, 10), EPS);
		assertEquals(cfg.mobs.maxHealthMultiplier, WaveFormula.healthMultiplier(cfg.mobs, base, 1000), EPS);
		assertEquals(cfg.mobs.maxDamageMultiplier, WaveFormula.damageMultiplier(cfg.mobs, base, 1000), EPS);
	}

	@Test
	void gearChancesAndTiers() {
		WaveConfig cfg = config();
		assertEquals(0.04 * 5, WaveFormula.armorChance(cfg.mobs, 5, false), EPS);
		assertEquals(cfg.mobs.armorChanceMax, WaveFormula.armorChance(cfg.mobs, 100, false), EPS);
		assertEquals(Math.min(1, cfg.mobs.armorChanceMax + cfg.mobs.eliteGearBonus), WaveFormula.armorChance(cfg.mobs, 100, true), EPS);
		assertEquals(0, WaveFormula.gearTierIndex(cfg.mobs.armorTiers, 1));
		assertEquals(2, WaveFormula.gearTierIndex(cfg.mobs.armorTiers, 8));
		assertEquals(3, WaveFormula.gearTierIndex(cfg.mobs.armorTiers, 100));
		cfg.mobs.armorTiers.get(0).minDifficulty = 2;
		assertEquals(-1, WaveFormula.gearTierIndex(cfg.mobs.armorTiers, 1));
	}

	// ------------------------------------------------------------------ boss nights

	@Test
	void scheduledBossNights() {
		WaveConfig.DifficultySettings d = config().difficulty; // min 5, every 5
		for (int night = 0; night < 5; night++) {
			assertFalse(WaveFormula.isScheduledBossNight(d, night), "night " + night);
		}
		assertTrue(WaveFormula.isScheduledBossNight(d, 5));
		assertFalse(WaveFormula.isScheduledBossNight(d, 7));
		assertTrue(WaveFormula.isScheduledBossNight(d, 10));
		assertTrue(WaveFormula.isScheduledBossNight(d, 15));
		assertEquals(5, WaveFormula.nightsUntilBoss(d, 0));
		assertEquals(0, WaveFormula.nightsUntilBoss(d, 5));
		assertEquals(3, WaveFormula.nightsUntilBoss(d, 7));
		d.bossEveryNights = 0;
		assertFalse(WaveFormula.isScheduledBossNight(d, 5));
		assertEquals(-1, WaveFormula.nightsUntilBoss(d, 5));
	}

	// ------------------------------------------------------------------ heart, rewards, penalties

	@Test
	void heartIntegrityScalesWithLevel() {
		WaveConfig.HeartSettings h = config().heart;
		assertEquals(150, WaveFormula.heartIntegrity(h, 1));
		assertEquals(210, WaveFormula.heartIntegrity(h, 3));
		assertEquals(150, WaveFormula.heartIntegrity(h, 0));
	}

	@Test
	void heartIntegrityPastLevelTenGrowsSlowlyAndIsCapped() {
		WaveConfig.HeartSettings h = config().heart;
		assertEquals(420, WaveFormula.heartIntegrity(h, 10), "unchanged up to level 10");
		assertEquals(425, WaveFormula.heartIntegrity(h, 11));
		assertEquals(870, WaveFormula.heartIntegrity(h, 100));
		assertEquals(1020, WaveFormula.heartIntegrity(h, 500), "capped");
		h.maxIntegrityAbove10 = 0;
		assertEquals(420 + 5 * 490, WaveFormula.heartIntegrity(h, 500), "0 = no cap");
	}

	@Test
	void levelDifficultyPastTenStaysPlayable() {
		WaveConfig.DifficultySettings d = config().difficulty;
		assertEquals(4.5, WaveFormula.levelDifficulty(d, 10), EPS, "unchanged up to level 10");
		assertEquals(5.0, WaveFormula.levelDifficulty(d, 20), EPS);
		assertEquals(9.0, WaveFormula.levelDifficulty(d, 100), EPS);
		assertEquals(9.0, WaveFormula.levelDifficulty(d, 500), EPS, "capped");
		for (int level = 2; level <= 500; level++) {
			assertTrue(WaveFormula.levelDifficulty(d, level) >= WaveFormula.levelDifficulty(d, level - 1), "never decreases: " + level);
		}
		// A level 100 or 500 guild at night step 0, alone: half of the maximum, so a defeat still lowers the horde.
		assertEquals(10.0, WaveFormula.difficulty(d, 0, 100, 1), EPS);
		assertEquals(10.0, WaveFormula.difficulty(d, 0, 500, 1), EPS);
		assertTrue(WaveFormula.difficulty(d, 10, 500, 1) < d.max, "night step 10: not yet the maximum");
		assertTrue(WaveFormula.difficulty(d, 8, 500, 1) < WaveFormula.difficulty(d, 10, 500, 1), "the anti-spiral rollback still works");
		assertEquals(d.max, WaveFormula.difficulty(d, 40, 500, 4), EPS);
	}

	@Test
	void rewardsScaleWithKillRatio() {
		WaveConfig.Rewards r = config().rewards;
		assertEquals(0.0, WaveFormula.killRatio(0, 0), EPS);
		assertEquals(0.5, WaveFormula.killRatio(5, 10), EPS);
		assertEquals(1.0, WaveFormula.killRatio(12, 10), EPS);
		assertEquals(r.minRewardFactor, WaveFormula.rewardFactor(r, 0), EPS);
		assertEquals(1.0, WaveFormula.rewardFactor(r, 1), EPS);
		// (4 + 2 * 5) * 1.0 = 14 gold ; (20 + 10 * 5) = 70 xp
		assertEquals(14, WaveFormula.goldReward(r, 5, 1.0));
		assertEquals(70, WaveFormula.xpReward(r, 5, 1.0));
		// half kills: factor 0.25 + 0.75 * 0.5 = 0.625
		assertEquals(Math.round(14 * 0.625), WaveFormula.goldReward(r, 5, 0.5));
		assertEquals(0, WaveFormula.itemRolls(r, 5, 0));
		assertEquals(2, WaveFormula.itemRolls(r, 4, 1.0)); // 1 + 0.25 * 4
		assertEquals(r.itemRollsMax, WaveFormula.itemRolls(r, 1000, 1.0));
	}

	@Test
	void defeatPenalties() {
		WaveConfig.Defeat d = config().defeat;
		assertEquals(25, WaveFormula.treasuryLoss(d, 100));
		assertEquals(0, WaveFormula.treasuryLoss(d, 3));
		assertEquals(0, WaveFormula.treasuryLoss(d, 0));
		assertEquals(0, WaveFormula.treasuryLoss(d, -10));
		assertEquals(3, WaveFormula.nightAfterDefeat(d, 5));
		assertEquals(0, WaveFormula.nightAfterDefeat(d, 1));
		d.treasuryLossPercent = 100;
		assertEquals(77, WaveFormula.treasuryLoss(d, 77));
	}

	// ------------------------------------------------------------------ spawning & loot

	@Test
	void groupSizeSpreadsTheHordeOverThePhase() {
		WaveConfig.Spawning s = config().spawning; // interval 12, min 2, max 8
		assertEquals(3, WaveFormula.groupSize(s, 60, 240));  // 20 groups
		assertEquals(2, WaveFormula.groupSize(s, 10, 240));  // min
		assertEquals(8, WaveFormula.groupSize(s, 500, 240)); // max
		assertEquals(8, WaveFormula.groupSize(s, 60, 5));    // short phase: one big group (capped)
	}

	@Test
	void killLootScaling() {
		WaveConfig.KillLoot k = config().killLoot;
		assertEquals(2, WaveFormula.nuggetMax(k, 1));
		assertEquals(4, WaveFormula.nuggetMax(k, 8));
		assertEquals(1, WaveFormula.mapTier(k, 1));
		assertEquals(2, WaveFormula.mapTier(k, 7));
		assertEquals(k.mapMaxTier, WaveFormula.mapTier(k, 100));
	}

	// ------------------------------------------------------------------ time

	@Test
	void timeHelpers() {
		WaveConfig.Timing t = config().timing;
		long clock = 3 * 24000L + 13500;
		assertEquals(13500, WaveFormula.timeOfDay(clock));
		assertEquals(3, WaveFormula.dayIndex(clock));
		assertTrue(WaveFormula.inNight(t, 13000));
		assertTrue(WaveFormula.inNight(t, 22999));
		assertFalse(WaveFormula.inNight(t, 23000));
		assertFalse(WaveFormula.inNight(t, 12999));
		assertTrue(WaveFormula.inStartWindow(t, 17999));
		assertFalse(WaveFormula.inStartWindow(t, 18000));
		assertEquals(12000, WaveFormula.ticksUntilNight(t, 1000));
		assertEquals(13500, WaveFormula.ticksUntilNight(t, 23500));
		assertEquals(0, WaveFormula.ticksUntilNight(t, 15000));
	}
}
