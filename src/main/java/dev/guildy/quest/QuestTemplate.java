package dev.guildy.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * A quest template from quests.json5 (pure data + pure validation, no Minecraft classes).
 * Values scale with the guild level: {@code value = random(min..max) + floor(perLevel * (level - 1))}.
 */
public class QuestTemplate {
	public String id = "";
	public QuestType type = QuestType.GATHER;
	public String name = "";
	public String description = "";
	/** Block (GATHER), entity (KILL) or item (DELIVER) id, or a tag "#namespace:path". Unused for SURVIVE / EXPLORE. */
	public String target = "";
	/** Display name of the target (recommended for tags); empty = the game's translated name. */
	public String targetName = "";
	/** Menu icon (item id); empty = chosen from the target. */
	public String icon = "";
	public int amountMin = 1;
	public int amountMax = 1;
	public double amountPerLevel = 0;
	public int rewardGoldMin = 0;
	public int rewardGoldMax = 0;
	public double rewardGoldPerLevel = 0;
	public int rewardXp = 0;
	public double rewardXpPerLevel = 0;
	public List<RewardItem> rewardItems = new ArrayList<>();
	/** Chance (0..1) to also reward an extension flag. */
	public double flagChance = 0;
	/** Chance (0..1) to also reward a treasure map. */
	public double treasureMapChance = 0;
	/** Relative weight in the random choice. */
	public int weight = 10;
	public int minLevel = 1;
	/** 0 = no maximum. */
	public int maxLevel = 0;
	/** Time limit in in-game days (1 day = 24000 ticks = 20 minutes of play). */
	public double durationDays = 3;

	/** Possible item reward. */
	public static class RewardItem {
		public String item = "";
		public int count = 1;
		/** Chance (0..1) that this item is part of the reward. */
		public double chance = 1.0;

		public RewardItem() {
		}

		public RewardItem(String item, int count, double chance) {
			this.item = item;
			this.count = count;
			this.chance = chance;
		}
	}

	public QuestTemplate() {
	}

	/** True when the template can be offered to a guild of this level. */
	public boolean levelAllows(int level) {
		return level >= minLevel && (maxLevel <= 0 || level <= maxLevel);
	}

	/**
	 * Pure validation: fixes nulls, clamps numbers.
	 *
	 * @return blocking errors (empty = usable). Ids are checked separately (needs the game registries).
	 */
	public List<String> check() {
		List<String> errors = new ArrayList<>();
		if (id == null || !id.matches("[a-z0-9_]{1,48}")) {
			errors.add("invalid id '" + id + "' (allowed: a-z 0-9 _)");
		}
		if (type == null) {
			errors.add("unknown type (GATHER, KILL, DELIVER, SURVIVE, EXPLORE)");
			return errors;
		}
		if (name == null || name.isBlank()) {
			name = id == null ? "?" : id;
		}
		if (description == null) {
			description = "";
		}
		if (target == null) {
			target = "";
		}
		if (targetName == null) {
			targetName = "";
		}
		if (icon == null) {
			icon = "";
		}
		if (type.hasTarget() && target.isBlank()) {
			errors.add("type " + type + " needs a target");
		}
		amountMin = Math.max(1, amountMin);
		amountMax = Math.max(amountMin, amountMax);
		amountPerLevel = finiteNonNegative(amountPerLevel);
		if (type == QuestType.EXPLORE) {
			amountMin = 1;
			amountMax = 1;
			amountPerLevel = 0;
		}
		rewardGoldMin = Math.max(0, rewardGoldMin);
		rewardGoldMax = Math.max(rewardGoldMin, rewardGoldMax);
		rewardGoldPerLevel = finiteNonNegative(rewardGoldPerLevel);
		rewardXp = Math.max(0, rewardXp);
		rewardXpPerLevel = finiteNonNegative(rewardXpPerLevel);
		if (rewardItems == null) {
			rewardItems = new ArrayList<>();
		}
		rewardItems.removeIf(r -> r == null || r.item == null || r.item.isBlank());
		for (RewardItem reward : rewardItems) {
			reward.count = Math.max(1, Math.min(64 * 9, reward.count));
			reward.chance = clamp01(reward.chance);
		}
		flagChance = clamp01(flagChance);
		treasureMapChance = clamp01(treasureMapChance);
		weight = Math.max(0, weight);
		minLevel = Math.max(1, minLevel);
		maxLevel = Math.max(0, maxLevel);
		if (maxLevel > 0 && maxLevel < minLevel) {
			maxLevel = minLevel;
		}
		if (!Double.isFinite(durationDays) || durationDays <= 0) {
			durationDays = 3;
		}
		durationDays = Math.max(0.05, Math.min(60, durationDays));
		return errors;
	}

	private static double clamp01(double value) {
		return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
	}

	private static double finiteNonNegative(double value) {
		return Double.isFinite(value) ? Math.max(0, value) : 0;
	}
}
