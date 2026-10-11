package dev.guildy.wave;

/**
 * Pure balance formulas of the night hordes (no Minecraft access, unit-tested).
 */
public final class WaveFormula {
	public static final int DAY_TICKS = 24000;

	private WaveFormula() {
	}

	// ------------------------------------------------------------------ difficulty

	/** D = base + perNight * night + perLevel * (level - 1) + perExtraPlayer * (players - 1), clamped to [base, max]. */
	public static double difficulty(WaveConfig.DifficultySettings c, int night, int level, int nearbyPlayers) {
		double d = c.base
				+ c.perNight * Math.max(0, night)
				+ c.perLevel * Math.max(0, level - 1)
				+ c.perExtraPlayer * Math.max(0, nearbyPlayers - 1);
		return clamp(d, c.base, Math.max(c.base, c.max));
	}

	/** Total mobs = clamp(round((mobsBase + mobsPerDifficulty * D) * multiplier), minMobs, maxMobsPerWave). */
	public static int mobCount(WaveConfig.DifficultySettings c, double difficulty, double multiplier) {
		double raw = (c.mobsBase + c.mobsPerDifficulty * difficulty) * Math.max(0, multiplier);
		long rounded = Math.round(raw);
		int max = Math.max(1, c.maxMobsPerWave);
		int min = Math.max(1, Math.min(c.minMobs, max));
		return (int) Math.max(min, Math.min(max, rounded));
	}

	/** Elite chance = min(eliteMax, eliteBase + elitePerDifficulty * D) + theme bonus, clamped to [0, 1]. */
	public static double eliteChance(WaveConfig.DifficultySettings c, double difficulty, double themeBonus) {
		double chance = Math.min(c.eliteMax, c.eliteBase + c.elitePerDifficulty * difficulty) + Math.max(0, themeBonus);
		return clamp(chance, 0, 1);
	}

	/** Boss night every {@code bossEveryNights} steps starting at {@code bossMinNight} (never when bossEveryNights <= 0). */
	public static boolean isScheduledBossNight(WaveConfig.DifficultySettings c, int night) {
		if (c.bossEveryNights <= 0 || night < c.bossMinNight) {
			return false;
		}
		return (night - c.bossMinNight) % c.bossEveryNights == 0;
	}

	/** Night steps until the next scheduled boss night (0 = this night), or -1 when boss nights are disabled. */
	public static int nightsUntilBoss(WaveConfig.DifficultySettings c, int night) {
		if (c.bossEveryNights <= 0) {
			return -1;
		}
		if (night <= c.bossMinNight) {
			return c.bossMinNight - Math.max(0, night);
		}
		int mod = (night - c.bossMinNight) % c.bossEveryNights;
		return mod == 0 ? 0 : c.bossEveryNights - mod;
	}

	// ------------------------------------------------------------------ mob buffs

	public static double healthMultiplier(WaveConfig.MobSettings m, double base, double difficulty) {
		return clamp(1 + m.healthPerDifficulty * Math.max(0, difficulty - base), 1, Math.max(1, m.maxHealthMultiplier));
	}

	public static double damageMultiplier(WaveConfig.MobSettings m, double base, double difficulty) {
		return clamp(1 + m.damagePerDifficulty * Math.max(0, difficulty - base), 1, Math.max(1, m.maxDamageMultiplier));
	}

	public static double armorChance(WaveConfig.MobSettings m, double difficulty, boolean special) {
		double chance = Math.min(m.armorChanceMax, m.armorChancePerDifficulty * difficulty);
		return clamp(chance + (special ? m.eliteGearBonus : 0), 0, 1);
	}

	public static double weaponChance(WaveConfig.MobSettings m, double difficulty, boolean special) {
		double chance = Math.min(m.weaponChanceMax, m.weaponChancePerDifficulty * difficulty);
		return clamp(chance + (special ? m.eliteGearBonus : 0), 0, 1);
	}

	/** Index of the highest tier whose minDifficulty <= D (tiers sorted ascending), or -1. */
	public static int gearTierIndex(java.util.List<WaveConfig.GearTier> tiers, double difficulty) {
		int index = -1;
		for (int i = 0; i < tiers.size(); i++) {
			if (tiers.get(i).minDifficulty <= difficulty) {
				index = i;
			}
		}
		return index;
	}

	// ------------------------------------------------------------------ spawning

	/** Group size so that the whole horde arrives within the spawn phase: clamp(ceil(total / groups), min, max). */
	public static int groupSize(WaveConfig.Spawning s, int total, double phaseSeconds) {
		double groups = Math.max(1, Math.floor(Math.max(1, phaseSeconds) / Math.max(1, s.spawnGroupIntervalSeconds)));
		int size = (int) Math.ceil(total / groups);
		return Math.max(s.groupSizeMin, Math.min(s.groupSizeMax, size));
	}

	// ------------------------------------------------------------------ heart

	public static int heartIntegrity(WaveConfig.HeartSettings h, int level) {
		long value = (long) h.baseIntegrity + (long) h.integrityPerLevel * Math.max(0, level - 1);
		return (int) Math.max(1, Math.min(Integer.MAX_VALUE, value));
	}

	// ------------------------------------------------------------------ rewards & penalties

	public static double killRatio(int kills, int total) {
		if (total <= 0) {
			return 0;
		}
		return clamp((double) kills / total, 0, 1);
	}

	/** minRewardFactor at 0 % kills, 1.0 at 100 %. */
	public static double rewardFactor(WaveConfig.Rewards r, double killRatio) {
		double ratio = clamp(killRatio, 0, 1);
		return r.minRewardFactor + (1 - r.minRewardFactor) * ratio;
	}

	public static long goldReward(WaveConfig.Rewards r, double difficulty, double killRatio) {
		return Math.max(0, Math.round((r.goldBase + r.goldPerDifficulty * difficulty) * rewardFactor(r, killRatio)));
	}

	public static long xpReward(WaveConfig.Rewards r, double difficulty, double killRatio) {
		return Math.max(0, Math.round((r.xpBase + r.xpPerDifficulty * difficulty) * rewardFactor(r, killRatio)));
	}

	/** Item rolls per present member: min(itemRollsMax, round((itemRollsBase + itemRollsPerDifficulty * D) * killRatio)). */
	public static int itemRolls(WaveConfig.Rewards r, double difficulty, double killRatio) {
		long rolls = Math.round((r.itemRollsBase + r.itemRollsPerDifficulty * difficulty) * clamp(killRatio, 0, 1));
		return (int) Math.max(0, Math.min(r.itemRollsMax, rolls));
	}

	public static long treasuryLoss(WaveConfig.Defeat d, long treasury) {
		if (treasury <= 0) {
			return 0;
		}
		return Math.max(0, Math.min(treasury, treasury * Math.max(0, Math.min(100, d.treasuryLossPercent)) / 100));
	}

	public static int nightAfterDefeat(WaveConfig.Defeat d, int night) {
		return Math.max(0, night - Math.max(0, d.nightRollback));
	}

	// ------------------------------------------------------------------ kill loot

	public static int nuggetMax(WaveConfig.KillLoot k, double difficulty) {
		return Math.max(k.nuggetMin, k.nuggetMaxBase + (int) Math.floor(k.nuggetsPerDifficulty * Math.max(0, difficulty)));
	}

	public static int mapTier(WaveConfig.KillLoot k, double difficulty) {
		int tier = 1 + (int) Math.floor(Math.max(0, difficulty) / Math.max(0.1, k.mapTierEveryDifficulty));
		return Math.max(1, Math.min(k.mapMaxTier, tier));
	}

	// ------------------------------------------------------------------ time

	public static long timeOfDay(long clock) {
		return Math.floorMod(clock, DAY_TICKS);
	}

	public static long dayIndex(long clock) {
		return Math.floorDiv(clock, DAY_TICKS);
	}

	public static boolean inNight(WaveConfig.Timing t, long timeOfDay) {
		return timeOfDay >= t.nightStart && timeOfDay < t.nightEnd;
	}

	/** True when a wave may start now (between nightfall and lateStartUntil). */
	public static boolean inStartWindow(WaveConfig.Timing t, long timeOfDay) {
		return timeOfDay >= t.nightStart && timeOfDay < t.lateStartUntil;
	}

	/** Ticks until the next nightfall (0 when inside the night window). */
	public static long ticksUntilNight(WaveConfig.Timing t, long timeOfDay) {
		if (inNight(t, timeOfDay)) {
			return 0;
		}
		return Math.floorMod(t.nightStart - timeOfDay, DAY_TICKS);
	}

	public static double clamp(double value, double min, double max) {
		if (Double.isNaN(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}
}
