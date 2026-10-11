package dev.guildy.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One quest of a guild (persisted). Everything needed to display and finish it is copied from the template at
 * generation, so editing quests.json5 never breaks running quests.
 */
public class QuestInstance {
	/** Unique id (random). */
	public String id;
	/** Template id from quests.json5. */
	public String template;
	/** Board slot 0..5 (= quest board sign index). */
	public int slot;
	public QuestType type = QuestType.GATHER;
	public String name = "";
	public String description = "";
	public String target = "";
	public String targetName = "";
	/** Menu icon item id (empty = from the target). */
	public String icon = "";
	/** Required amount (blocks, kills, items, waves; 1 for EXPLORE). */
	public int amount = 1;
	public int progress;
	public QuestState state = QuestState.ACTIVE;
	/** Game time (overworld ticks) of creation / time limit (0 = none) / completion. */
	public long createdAt;
	public long expiresAt;
	public long completedAt;
	public int rewardGold;
	public int rewardXp;
	public List<RewardStack> rewardItems = new ArrayList<>();
	public int rewardFlags;
	public boolean rewardMap;
	/** EXPLORE destination (claim dimension). */
	public int targetX;
	public int targetZ;
	/** Player name → amount contributed. */
	public Map<String, Integer> contributions = new LinkedHashMap<>();

	public boolean isActive() {
		return state == QuestState.ACTIVE;
	}

	public boolean isCompleted() {
		return state == QuestState.COMPLETED;
	}

	public int remaining() {
		return Math.max(0, amount - progress);
	}

	/** Fixes nulls after loading. */
	void normalize() {
		if (type == null) {
			type = QuestType.GATHER;
		}
		if (state == null) {
			state = QuestState.ACTIVE;
		}
		if (name == null) {
			name = "?";
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
		if (rewardItems == null) {
			rewardItems = new ArrayList<>();
		}
		if (contributions == null) {
			contributions = new LinkedHashMap<>();
		}
		amount = Math.max(1, amount);
		progress = Math.max(0, Math.min(amount, progress));
	}
}
