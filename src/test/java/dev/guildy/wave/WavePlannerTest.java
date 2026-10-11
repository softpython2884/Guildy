package dev.guildy.wave;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WavePlannerTest {

	private static WaveConfig config() {
		WaveConfig config = new WaveConfig();
		config.validate();
		return config;
	}

	private static WaveConfig.Theme theme(String id, double weight, int minNight, WaveConfig.MobEntry... entries) {
		WaveConfig.Theme theme = WaveConfig.Theme.of(id, id, weight, minNight, "RED");
		theme.mobs.addAll(List.of(entries));
		return theme;
	}

	private static long count(WavePlanner.Plan plan, String entity) {
		return plan.mobs().stream().filter(m -> m.entity().equals(entity)).count();
	}

	// ------------------------------------------------------------------ themes

	@Test
	void defaultThemesUnlockWithNights() {
		WaveConfig cfg = config();
		List<String> night0 = WavePlanner.availableThemes(cfg.themes, 0).stream().map(t -> t.id).toList();
		assertEquals(List.of("undead"), night0);
		List<String> night1 = WavePlanner.availableThemes(cfg.themes, 1).stream().map(t -> t.id).toList();
		assertEquals(List.of("undead", "arachnids"), night1);
		assertEquals(cfg.themes.size(), WavePlanner.availableThemes(cfg.themes, 50).size());
	}

	@Test
	void unavailableThemesAreSkipped() {
		WaveConfig.Theme capped = theme("capped", 10, 0, WaveConfig.MobEntry.of("minecraft:zombie", 1, 0, 0));
		capped.maxNight = 2;
		WaveConfig.Theme weightless = theme("weightless", 0, 0, WaveConfig.MobEntry.of("minecraft:zombie", 1, 0, 0));
		WaveConfig.Theme empty = theme("empty", 10, 0);
		List<WaveConfig.Theme> themes = List.of(capped, weightless, empty);
		assertEquals(1, WavePlanner.availableThemes(themes, 2).size());
		assertTrue(WavePlanner.availableThemes(themes, 3).isEmpty());
		assertNull(WavePlanner.pickTheme(themes, 3, false, new Random(1)));
	}

	@Test
	void bossNightsPreferThemesWithBosses() {
		WaveConfig.Theme plain = theme("plain", 1000, 0, WaveConfig.MobEntry.of("minecraft:zombie", 1, 0, 0));
		WaveConfig.Theme withBoss = theme("boss", 1, 0, WaveConfig.MobEntry.of("minecraft:zombie", 1, 0, 0));
		withBoss.bosses.add(WaveConfig.BossDef.of("minecraft:ravager", "Boss", 1, 3, 1));
		List<WaveConfig.Theme> themes = List.of(plain, withBoss);
		Random random = new Random(7);
		for (int i = 0; i < 50; i++) {
			assertEquals("boss", WavePlanner.pickTheme(themes, 0, true, random).id);
		}
		int plainCount = 0;
		for (int i = 0; i < 200; i++) {
			if (WavePlanner.pickTheme(themes, 0, false, random) == plain) {
				plainCount++;
			}
		}
		assertTrue(plainCount > 190, "plain picked " + plainCount);
		// No theme with bosses: boss nights fall back to any theme.
		assertEquals("plain", WavePlanner.pickTheme(List.of(plain), 0, true, random).id);
	}

	// ------------------------------------------------------------------ plans

	@Test
	void planSizeMatchesFormula() {
		WaveConfig cfg = config();
		WaveConfig.Theme theme = theme("t", 1, 0, WaveConfig.MobEntry.of("minecraft:zombie", 1, 0, 0));
		WavePlanner.Plan plan = WavePlanner.plan(cfg, theme, 9.5, false, id -> true, new Random(3));
		assertEquals(WaveFormula.mobCount(cfg.difficulty, 9.5, 1.0), plan.mobs().size());
		assertFalse(plan.hasBoss());
		theme.mobCountMultiplier = 0.5;
		plan = WavePlanner.plan(cfg, theme, 9.5, false, id -> true, new Random(3));
		assertEquals(WaveFormula.mobCount(cfg.difficulty, 9.5, 0.5), plan.mobs().size());
	}

	@Test
	void maxCountIsRespected() {
		WaveConfig cfg = config();
		WaveConfig.Theme theme = theme("t", 1, 0,
				WaveConfig.MobEntry.of("minecraft:evoker", 1000, 0, 2),
				WaveConfig.MobEntry.of("minecraft:pillager", 1, 0, 0));
		for (long seed = 0; seed < 20; seed++) {
			WavePlanner.Plan plan = WavePlanner.plan(cfg, theme, 20, false, id -> true, new Random(seed));
			assertTrue(count(plan, "minecraft:evoker") <= 2);
			assertEquals(WaveFormula.mobCount(cfg.difficulty, 20, 1.0), plan.mobs().size());
		}
		// Every entry capped: the plan is shorter than the formula.
		WaveConfig.Theme capped = theme("capped", 1, 0, WaveConfig.MobEntry.of("minecraft:evoker", 1, 0, 2));
		assertEquals(2, WavePlanner.plan(cfg, capped, 20, false, id -> true, new Random(1)).mobs().size());
	}

	@Test
	void minDifficultyFiltersEntries() {
		WaveConfig cfg = config();
		WaveConfig.Theme theme = theme("t", 1, 0,
				WaveConfig.MobEntry.of("minecraft:zombie", 1, 0, 0),
				WaveConfig.MobEntry.of("minecraft:husk", 1000, 10, 0));
		WavePlanner.Plan easy = WavePlanner.plan(cfg, theme, 1, false, id -> true, new Random(5));
		assertEquals(0, count(easy, "minecraft:husk"));
		WavePlanner.Plan hard = WavePlanner.plan(cfg, theme, 10, false, id -> true, new Random(5));
		assertTrue(count(hard, "minecraft:husk") > hard.mobs().size() / 2);
		// Nothing qualifies: the lowest-difficulty entries are used instead of an empty wave.
		WaveConfig.Theme hardOnly = theme("hardOnly", 1, 0,
				WaveConfig.MobEntry.of("minecraft:stray", 1, 5, 0),
				WaveConfig.MobEntry.of("minecraft:wither_skeleton", 1, 9, 0));
		WavePlanner.Plan fallback = WavePlanner.plan(cfg, hardOnly, 1, false, id -> true, new Random(5));
		assertFalse(fallback.mobs().isEmpty());
		assertEquals(fallback.mobs().size(), count(fallback, "minecraft:stray"));
	}

	@Test
	void invalidEntitiesAreExcluded() {
		WaveConfig cfg = config();
		WaveConfig.Theme theme = theme("t", 1, 0,
				WaveConfig.MobEntry.of("minecraft:not_a_mob", 1000, 0, 0),
				WaveConfig.MobEntry.of("minecraft:zombie", 1, 0, 0));
		theme.forceBoss = true;
		theme.bosses.add(WaveConfig.BossDef.of("minecraft:not_a_mob", "Boss", 1, 3, 1));
		WavePlanner.Plan plan = WavePlanner.plan(cfg, theme, 5, false, id -> !id.contains("not_a_mob"), new Random(9));
		assertEquals(0, count(plan, "minecraft:not_a_mob"));
		assertFalse(plan.hasBoss());
		WaveConfig.Theme allInvalid = theme("bad", 1, 0, WaveConfig.MobEntry.of("minecraft:not_a_mob", 1, 0, 0));
		assertTrue(WavePlanner.plan(cfg, allInvalid, 5, false, id -> false, new Random(9)).mobs().isEmpty());
	}

	@Test
	void bossIsInsertedOnBossNightsOrForcedThemes() {
		WaveConfig cfg = config();
		WaveConfig.Theme theme = theme("t", 1, 0, WaveConfig.MobEntry.of("minecraft:zombie", 1, 0, 0));
		theme.bosses.add(WaveConfig.BossDef.of("minecraft:ravager", "Boss", 1, 3, 1));

		WavePlanner.Plan normal = WavePlanner.plan(cfg, theme, 5, false, id -> true, new Random(11));
		assertFalse(normal.hasBoss());
		assertEquals(0, normal.mobs().stream().filter(WavePlanner.PlannedMob::isBoss).count());

		WavePlanner.Plan scheduled = WavePlanner.plan(cfg, theme, 5, true, id -> true, new Random(11));
		assertTrue(scheduled.hasBoss());
		int regular = WaveFormula.mobCount(cfg.difficulty, 5, 1.0);
		assertEquals(regular + 1, scheduled.mobs().size());
		int bossIndex = -1;
		for (int i = 0; i < scheduled.mobs().size(); i++) {
			if (scheduled.mobs().get(i).isBoss()) {
				assertEquals(-1, bossIndex, "only one boss");
				bossIndex = i;
			}
		}
		assertEquals((int) Math.round(cfg.spawning.bossSpawnProgress * regular), bossIndex);
		WavePlanner.PlannedMob boss = scheduled.mobs().get(bossIndex);
		assertEquals("minecraft:ravager", boss.entity());
		assertNotNull(boss.boss());

		theme.forceBoss = true;
		assertTrue(WavePlanner.plan(cfg, theme, 5, false, id -> true, new Random(11)).hasBoss());
		cfg.spawning.bossSpawnProgress = 0;
		assertTrue(WavePlanner.plan(cfg, theme, 5, false, id -> true, new Random(11)).mobs().get(0).isBoss());
	}

	@Test
	void eliteShareFollowsChance() {
		WaveConfig cfg = config();
		WaveConfig.Theme theme = theme("t", 1, 0, WaveConfig.MobEntry.of("minecraft:zombie", 1, 0, 0));
		theme.eliteBonus = 1.0;
		WavePlanner.Plan allElite = WavePlanner.plan(cfg, theme, 5, false, id -> true, new Random(2));
		assertEquals(allElite.mobs().size(), allElite.eliteCount());

		theme.eliteBonus = 0;
		cfg.difficulty.eliteBase = 0;
		cfg.difficulty.elitePerDifficulty = 0;
		assertEquals(0, WavePlanner.plan(cfg, theme, 5, false, id -> true, new Random(2)).eliteCount());

		cfg.difficulty.eliteBase = 0.3;
		cfg.difficulty.eliteMax = 0.3;
		cfg.difficulty.maxMobsPerWave = 500;
		cfg.difficulty.mobsBase = 400;
		WavePlanner.Plan big = WavePlanner.plan(cfg, theme, 1, false, id -> true, new Random(2));
		double share = big.eliteCount() / (double) big.mobs().size();
		assertTrue(Math.abs(share - 0.3) < 0.07, "elite share " + share);
	}

	@Test
	void planIsDeterministicForASeed() {
		WaveConfig cfg = config();
		WaveConfig.Theme theme = cfg.themes.get(0);
		List<String> first = new ArrayList<>();
		List<String> second = new ArrayList<>();
		WavePlanner.plan(cfg, theme, 8, true, id -> true, new Random(99)).mobs().forEach(m -> first.add(m.entity() + m.kind()));
		WavePlanner.plan(cfg, theme, 8, true, id -> true, new Random(99)).mobs().forEach(m -> second.add(m.entity() + m.kind()));
		assertEquals(first, second);
	}

	@Test
	void everyDefaultThemeProducesAWave() {
		WaveConfig cfg = config();
		for (WaveConfig.Theme theme : cfg.themes) {
			assertFalse(theme.mobs.isEmpty(), theme.id);
			assertTrue(theme.hasBosses(), theme.id + " should define a boss");
			for (double difficulty : new double[] {1, 6, 20}) {
				WavePlanner.Plan plan = WavePlanner.plan(cfg, theme, difficulty, false, id -> true, new Random(4));
				assertFalse(plan.mobs().isEmpty(), theme.id + " at " + difficulty);
				assertEquals(theme.forceBoss, plan.hasBoss(), theme.id);
			}
		}
	}
}
