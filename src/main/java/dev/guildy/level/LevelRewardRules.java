package dev.guildy.level;

import dev.guildy.config.GuildyConfig;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rewards of the levels past 10 and guild ranks (pure, unit-tested). One-shot rewards (gold, free extension flags) are
 * granted once per level: a level at or below {@code alreadyRewarded} (the highest level ever rewarded) gives nothing,
 * so lowering a guild with the admin command and levelling it up again never pays twice. Villager places are not
 * stored: they follow the current level.
 */
public final class LevelRewardRules {
	/** One-shot rewards of a level-up. */
	public record Gains(long gold, int flags, List<Integer> milestones) {
		public static final Gains NONE = new Gains(0, 0, List.of());

		public boolean any() {
			return gold > 0 || flags > 0 || !milestones.isEmpty();
		}
	}

	private LevelRewardRules() {
	}

	/** Gold of reaching {@code level}: {@code min(goldMax, goldBase + goldPerLevel * (level - 10))}, 0 up to level 10. */
	public static long levelGold(GuildyConfig.LevelRewards cfg, int level) {
		int above = LevelScaling.stepsAbove(level);
		if (above <= 0) {
			return 0;
		}
		double gold = cfg.goldBase + (Double.isFinite(cfg.goldPerLevel) ? cfg.goldPerLevel : 0) * above;
		return Math.max(0, Math.min(Math.max(0, cfg.goldMax), (long) Math.floor(gold + 1e-9)));
	}

	/** True for the levels 10 + k * milestoneEvery (k >= 1). */
	public static boolean isMilestone(GuildyConfig.LevelRewards cfg, int level) {
		int above = LevelScaling.stepsAbove(level);
		return cfg.milestoneEvery > 0 && above > 0 && above % cfg.milestoneEvery == 0;
	}

	/** Next milestone strictly above {@code level}, or -1 when there is none up to {@code maxLevel}. */
	public static int nextMilestone(GuildyConfig.LevelRewards cfg, int level, int maxLevel) {
		if (cfg.milestoneEvery <= 0) {
			return -1;
		}
		int above = LevelScaling.stepsAbove(level);
		int next = LevelScaling.CLASSIC_LEVELS + (above / cfg.milestoneEvery + 1) * cfg.milestoneEvery;
		return next <= maxLevel ? next : -1;
	}

	/** One-shot rewards of the levels in ({@code from}, {@code to}] not rewarded yet. */
	public static Gains between(GuildyConfig.LevelRewards cfg, int from, int to, int alreadyRewarded) {
		if (!cfg.enabled || to <= from) {
			return Gains.NONE;
		}
		int start = Math.max(Math.max(from, alreadyRewarded), LevelScaling.CLASSIC_LEVELS) + 1;
		long gold = 0;
		int flags = 0;
		List<Integer> milestones = new ArrayList<>();
		for (int level = start; level <= to; level++) {
			gold += levelGold(cfg, level);
			if (isMilestone(cfg, level)) {
				milestones.add(level);
				gold += Math.max(0, cfg.milestoneGold);
				flags += Math.max(0, cfg.milestoneFlags);
			}
		}
		return new Gains(gold, flags, List.copyOf(milestones));
	}

	/** Extra villager places of a level: one every {@code villagerSlotEvery} levels past 10, at most {@code maxVillagerSlots}. */
	public static int villagerSlots(GuildyConfig.LevelRewards cfg, int level) {
		if (cfg.villagerSlotEvery <= 0 || cfg.maxVillagerSlots <= 0) {
			return 0;
		}
		return Math.min(cfg.maxVillagerSlots, LevelScaling.stepsAbove(level) / cfg.villagerSlotEvery);
	}

	/** Rank of a level: the one with the highest {@code level} not above it (ranks sorted ascending), or null. */
	public static GuildyConfig.@Nullable Rank rank(@Nullable List<GuildyConfig.Rank> ranks, int level) {
		GuildyConfig.Rank found = null;
		if (ranks != null) {
			for (GuildyConfig.Rank rank : ranks) {
				if (rank != null && rank.level <= level && (found == null || rank.level >= found.level)) {
					found = rank;
				}
			}
		}
		return found;
	}

	/** Next rank above {@code level}, or null. */
	public static GuildyConfig.@Nullable Rank nextRank(@Nullable List<GuildyConfig.Rank> ranks, int level) {
		GuildyConfig.Rank found = null;
		if (ranks != null) {
			for (GuildyConfig.Rank rank : ranks) {
				if (rank != null && rank.level > level && (found == null || rank.level < found.level)) {
					found = rank;
				}
			}
		}
		return found;
	}

	/** Bounds of the reward settings. */
	public static void validate(GuildyConfig.LevelRewards cfg) {
		cfg.goldBase = Math.max(0, Math.min(1_000_000, cfg.goldBase));
		cfg.goldPerLevel = Double.isFinite(cfg.goldPerLevel) ? Math.max(0, Math.min(10_000, cfg.goldPerLevel)) : 0;
		cfg.goldMax = Math.max(0, Math.min(1_000_000, cfg.goldMax));
		cfg.milestoneEvery = Math.max(0, Math.min(100, cfg.milestoneEvery));
		cfg.milestoneGold = Math.max(0, Math.min(1_000_000, cfg.milestoneGold));
		cfg.milestoneFlags = Math.max(0, Math.min(16, cfg.milestoneFlags));
		cfg.villagerSlotEvery = Math.max(0, Math.min(100, cfg.villagerSlotEvery));
		cfg.maxVillagerSlots = Math.max(0, Math.min(50, cfg.maxVillagerSlots));
	}

	/** Ranks without a name are dropped, levels are kept within 1..500, sorted ascending (a missing list = defaults). */
	public static List<GuildyConfig.Rank> cleanRanks(@Nullable List<GuildyConfig.Rank> ranks) {
		if (ranks == null) {
			return GuildyConfig.defaultRanks();
		}
		List<GuildyConfig.Rank> cleaned = new ArrayList<>();
		for (GuildyConfig.Rank rank : ranks) {
			if (rank == null || rank.name == null || rank.name.isBlank()) {
				continue;
			}
			rank.level = Math.max(1, Math.min(LevelCurve.ABSOLUTE_MAX_LEVEL, rank.level));
			cleaned.add(rank);
		}
		cleaned.sort(Comparator.comparingInt(rank -> rank.level));
		return cleaned;
	}
}
