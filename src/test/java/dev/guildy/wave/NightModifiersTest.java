package dev.guildy.wave;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Event modifiers read by the planner and the rewards (blood night, raids). */
class NightModifiersTest {
	private static final NightModifiers BLOOD = new NightModifiers("Nuit de sang", 1.6, 0.15, 0.6, 1.25, 1.25, 0.6, 2, 2, 1.5);

	private static WaveConfig config() {
		WaveConfig config = new WaveConfig();
		config.validate();
		return config;
	}

	@Test
	void noneKeepsTheNormalPlan() {
		WaveConfig cfg = config();
		WaveConfig.Theme theme = cfg.themes.get(0);
		WavePlanner.Plan plain = WavePlanner.plan(cfg, theme, 6, false, id -> true, new Random(8));
		WavePlanner.Plan none = WavePlanner.plan(cfg, theme, 6, false, id -> true, new Random(8), NightModifiers.NONE);
		assertEquals(plain.mobs(), none.mobs());
	}

	@Test
	void bloodNightHordesAreBigger() {
		WaveConfig cfg = config();
		WaveConfig.Theme theme = cfg.themes.get(0);
		for (double difficulty : new double[] {1, 4, 9}) {
			int normal = WavePlanner.plan(cfg, theme, difficulty, false, id -> true, new Random(1)).mobs().size();
			int blood = WavePlanner.plan(cfg, theme, difficulty, false, id -> true, new Random(1), BLOOD).mobs().size();
			assertEquals(WaveFormula.mobCount(cfg.difficulty, difficulty, 1.6), blood);
			assertTrue(blood > normal, "difficulty " + difficulty + ": " + blood + " > " + normal);
		}
		// Still capped by maxMobsPerWave.
		assertEquals(cfg.difficulty.maxMobsPerWave, WavePlanner.plan(cfg, theme, 20, false, id -> true, new Random(1),
				new NightModifiers("", 10, 0, 1, 1, 1, 0, 1, 1, 1)).mobs().size());
	}

	@Test
	void eliteBonusIsCapped() {
		assertEquals(0.35, BLOOD.eliteChance(0.2), 1e-9, "+0.15");
		assertEquals(0.6, BLOOD.eliteChance(0.5), 1e-9, "capped at 0.6");
		assertEquals(0.8, BLOOD.eliteChance(0.8), 1e-9, "never below the normal chance");
		assertEquals(0.3, NightModifiers.NONE.eliteChance(0.3), 1e-9);

		WaveConfig cfg = config();
		cfg.difficulty.eliteBase = 0.55;
		cfg.difficulty.eliteMax = 0.55;
		cfg.difficulty.maxMobsPerWave = 500;
		cfg.difficulty.mobsBase = 400;
		WaveConfig.Theme theme = WaveConfig.Theme.of("t", "t", 1, 0, "RED");
		theme.mobs.add(WaveConfig.MobEntry.of("minecraft:zombie", 1, 0, 0));
		WavePlanner.Plan plan = WavePlanner.plan(cfg, theme, 1, false, id -> true, new Random(3), BLOOD);
		double share = plan.eliteCount() / (double) plan.mobs().size();
		assertTrue(Math.abs(share - 0.6) < 0.06, "elite share " + share + " (0.55 + 0.15 capped at 0.6)");
	}

	@Test
	void rewardsAreMultiplied() {
		assertEquals(20, BLOOD.gold(10));
		assertEquals(60, BLOOD.xp(30));
		assertEquals(6, BLOOD.itemRolls(4));
		NightModifiers raid = NightModifiers.rewards("", 1.2, 1.2, 1.2);
		assertEquals(12, raid.gold(10));
		assertEquals(1.0, raid.mobCountMultiplier(), 1e-9);
		assertEquals(10, NightModifiers.NONE.gold(10));
		// Out-of-range values are clamped.
		NightModifiers wild = new NightModifiers(null, -5, 3, 7, 0, 1000, 9, -1, -1, -1);
		assertEquals("", wild.label());
		assertEquals(0.1, wild.mobCountMultiplier(), 1e-9);
		assertEquals(1, wild.bossChance(), 1e-9);
		assertEquals(0, wild.gold(10));
	}
}
