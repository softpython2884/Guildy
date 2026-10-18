package dev.guildy.level;

import dev.guildy.config.GuildyConfig;
import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelRewardRulesTest {

	private static GuildyConfig config() {
		GuildyConfig config = new GuildyConfig();
		config.validate();
		return config;
	}

	@Test
	void goldOfEachLevelPastTen() {
		GuildyConfig.LevelRewards r = config().levels.rewards;
		assertEquals(0, LevelRewardRules.levelGold(r, 10), "nothing up to level 10");
		assertEquals(21, LevelRewardRules.levelGold(r, 11));
		assertEquals(60, LevelRewardRules.levelGold(r, 50));
		assertEquals(110, LevelRewardRules.levelGold(r, 100));
		assertEquals(120, LevelRewardRules.levelGold(r, 500), "capped by goldMax");
	}

	@Test
	void milestonesEveryFiveLevelsPastTen() {
		GuildyConfig config = config();
		GuildyConfig.LevelRewards r = config.levels.rewards;
		assertFalse(LevelRewardRules.isMilestone(r, 10));
		assertFalse(LevelRewardRules.isMilestone(r, 5));
		assertTrue(LevelRewardRules.isMilestone(r, 15));
		assertTrue(LevelRewardRules.isMilestone(r, 100));
		assertFalse(LevelRewardRules.isMilestone(r, 101));
		assertEquals(15, LevelRewardRules.nextMilestone(r, 3, 100));
		assertEquals(15, LevelRewardRules.nextMilestone(r, 10, 100));
		assertEquals(20, LevelRewardRules.nextMilestone(r, 15, 100));
		assertEquals(100, LevelRewardRules.nextMilestone(r, 99, 100));
		assertEquals(-1, LevelRewardRules.nextMilestone(r, 100, 100), "none past the maximum level");
		r.milestoneEvery = 0;
		assertFalse(LevelRewardRules.isMilestone(r, 15));
		assertEquals(-1, LevelRewardRules.nextMilestone(r, 10, 100));
	}

	@Test
	void levelUpRewardsAreGrantedOnce() {
		GuildyConfig.LevelRewards r = config().levels.rewards;
		assertFalse(LevelRewardRules.between(r, 1, 10, 0).any(), "levels 2-10: no reward");
		LevelRewardRules.Gains eleven = LevelRewardRules.between(r, 10, 11, 0);
		assertEquals(21, eleven.gold());
		assertEquals(0, eleven.flags());
		LevelRewardRules.Gains jump = LevelRewardRules.between(r, 9, 20, 0);
		// 21 + 22 + ... + 30 = 255, two milestones (15, 20) = 200 gold and 2 flags
		assertEquals(255 + 200, jump.gold());
		assertEquals(2, jump.flags());
		assertEquals(List.of(15, 20), jump.milestones());
		assertFalse(LevelRewardRules.between(r, 15, 20, 20).any(), "levels already rewarded give nothing");
		LevelRewardRules.Gains partial = LevelRewardRules.between(r, 12, 16, 14);
		assertEquals(25 + 26 + 100, partial.gold(), "only 15 and 16 (15 = milestone)");
		assertEquals(1, partial.flags());
		r.enabled = false;
		assertFalse(LevelRewardRules.between(r, 10, 100, 0).any(), "disabled");
	}

	@Test
	void totalGoldOfTheLevelsStaysModest() {
		GuildyConfig.LevelRewards r = config().levels.rewards;
		LevelRewardRules.Gains all = LevelRewardRules.between(r, 10, 100, 0);
		// 90 levels: sum(21..110) = 5 895 + 18 milestones x 100 = 7 695 gold, 18 free flags.
		assertEquals(7_695, all.gold());
		assertEquals(18, all.flags());
	}

	@Test
	void villagerPlacesEveryTenLevelsBounded() {
		GuildyConfig config = config();
		GuildyConfig.LevelRewards r = config.levels.rewards;
		assertEquals(0, LevelRewardRules.villagerSlots(r, 10));
		assertEquals(0, LevelRewardRules.villagerSlots(r, 19));
		assertEquals(1, LevelRewardRules.villagerSlots(r, 20));
		assertEquals(9, LevelRewardRules.villagerSlots(r, 100));
		assertEquals(10, LevelRewardRules.villagerSlots(r, 500), "at most maxVillagerSlots");
		assertEquals(9, config.levelVillagerSlots(100));
		r.enabled = false;
		assertEquals(0, config.levelVillagerSlots(100), "rewards disabled");
	}

	@Test
	void ranksByLevelBand() {
		GuildyConfig config = config();
		assertEquals("&7Novice", config.rank(1).name);
		assertEquals("&7Novice", config.rank(9).name);
		assertEquals("&6Bronze", config.rank(10).name);
		assertEquals("&bDiamant", config.rank(74).name);
		assertEquals("&d&lLégendaire", config.rank(100).name);
		assertEquals("&6&lDivine", config.rank(500).name);
		assertEquals(20, LevelRewardRules.nextRank(config.levels.ranks, 10).level);
		assertNull(LevelRewardRules.nextRank(config.levels.ranks, 400));
	}

	@Test
	void ranksAreCleanedAndSorted() {
		List<GuildyConfig.Rank> ranks = new ArrayList<>();
		ranks.add(new GuildyConfig.Rank(30, "&eOr"));
		ranks.add(null);
		ranks.add(new GuildyConfig.Rank(5, " "));
		ranks.add(new GuildyConfig.Rank(-3, "&7Base"));
		ranks.add(new GuildyConfig.Rank(9999, "&cTop"));
		List<GuildyConfig.Rank> cleaned = LevelRewardRules.cleanRanks(ranks);
		assertEquals(3, cleaned.size());
		assertEquals(1, cleaned.get(0).level);
		assertEquals(30, cleaned.get(1).level);
		assertEquals(LevelCurve.ABSOLUTE_MAX_LEVEL, cleaned.get(2).level);
		assertNull(LevelRewardRules.rank(List.of(new GuildyConfig.Rank(20, "&fArgent")), 19), "below every rank");
		assertEquals(GuildyConfig.defaultRanks().size(), LevelRewardRules.cleanRanks(null).size());
	}

	@Test
	void rewardSettingsAreBounded() {
		GuildyConfig config = Json5.fromString("{ \"levels\": { \"rewards\": { \"goldBase\": -5, \"goldPerLevel\": \"NaN\","
				+ " \"milestoneEvery\": 1000, \"milestoneFlags\": 99, \"maxVillagerSlots\": -1 } } }", GuildyConfig.class);
		config.validate();
		GuildyConfig.LevelRewards r = config.levels.rewards;
		assertEquals(0, r.goldBase);
		assertEquals(0, r.goldPerLevel, 1e-9);
		assertEquals(100, r.milestoneEvery);
		assertEquals(16, r.milestoneFlags);
		assertEquals(0, r.maxVillagerSlots);
	}
}
