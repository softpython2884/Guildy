package dev.guildy.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One quest of a guild (persisted). Everything needed to display and finish it is copied from the template at
 * generation, so editing quests.json5 never breaks running quests. Fields added later keep their default value when an
 * older guilds.json is loaded (no legendary flag, no assortment).
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
	/** Menu icon item id (empty = from the target / the type). */
	public String icon = "";
	/** Required amount (blocks, kills, items, waves...; 1 for EXPLORE / BIOME; sum of the parts for COLLECTION). */
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
	/** Legendary contract: harder and longer, also rewards a unique weapon. */
	public boolean legendary;
	/** COLLECTION: the items to deliver, each with its own progress ({@link #amount} / {@link #progress} are their sums). */
	public List<QuestPart> parts = new ArrayList<>();
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

	public boolean isGoalReached() {
		return progress >= amount;
	}

	/** True when the quest has per-item progress (COLLECTION). */
	public boolean hasParts() {
		return !parts.isEmpty();
	}

	/** Number of finished items of an assortment. */
	public int partsDone() {
		int done = 0;
		for (QuestPart part : parts) {
			if (part.isDone()) {
				done++;
			}
		}
		return done;
	}

	/**
	 * Adds progress, capped at the goal (pure). Quests with parts only progress through {@link #addPartProgress}.
	 *
	 * @return the amount actually added
	 */
	public int addProgress(int value) {
		if (hasParts() || value <= 0) {
			return 0;
		}
		int added = Math.min(value, remaining());
		progress += added;
		return added;
	}

	/**
	 * COLLECTION: adds progress to one item, capped at its amount, and updates the totals (pure).
	 *
	 * @return the amount actually added
	 */
	public int addPartProgress(int index, int value) {
		if (index < 0 || index >= parts.size() || value <= 0) {
			return 0;
		}
		QuestPart part = parts.get(index);
		int added = Math.min(value, part.remaining());
		part.progress += added;
		syncParts();
		return added;
	}

	/** COLLECTION: totals = sums of the parts. */
	void syncParts() {
		if (parts.isEmpty()) {
			return;
		}
		int total = 0;
		int done = 0;
		for (QuestPart part : parts) {
			total += part.amount;
			done += Math.min(part.amount, part.progress);
		}
		amount = Math.max(1, total);
		progress = Math.min(amount, done);
	}

	/** Marks the quest completed: full progress (every item of an assortment too). */
	void markCompleted(long time) {
		for (QuestPart part : parts) {
			part.progress = part.amount;
		}
		syncParts();
		progress = amount;
		state = QuestState.COMPLETED;
		completedAt = time;
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
		if (parts == null) {
			parts = new ArrayList<>();
		}
		parts.removeIf(part -> part == null);
		for (QuestPart part : parts) {
			part.normalize();
		}
		syncParts();
		amount = Math.max(1, amount);
		progress = Math.max(0, Math.min(amount, progress));
	}
}
