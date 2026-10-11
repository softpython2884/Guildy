package dev.guildy.quest;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-guild quests (persisted inside the guild).
 */
public class GuildQuestData {
	/** Number of quest slots (one per quest-board sign). */
	public static final int SLOTS = 6;

	/** Active quest slots (max 6, one per quest-board sign). */
	public List<QuestInstance> active = new ArrayList<>();
	public int completedTotal;
	public int expiredTotal;
	public int abandonedTotal;
	/** Game time from which each empty slot may receive a new quest (index = slot). */
	public long[] slotReadyAt = new long[SLOTS];
	/** Game time of the last legendary contract put on the board (0 = never): cooldown between two of them. */
	public long lastLegendaryAt;
	public int legendaryCompleted;

	public @Nullable QuestInstance bySlot(int slot) {
		for (QuestInstance quest : active) {
			if (quest.slot == slot) {
				return quest;
			}
		}
		return null;
	}

	/** True when a legendary contract is on the board (active or waiting for its reward). */
	public boolean hasLegendary() {
		for (QuestInstance quest : active) {
			if (quest.legendary) {
				return true;
			}
		}
		return false;
	}

	public @Nullable QuestInstance byId(String id) {
		for (QuestInstance quest : active) {
			if (quest.id != null && quest.id.equals(id)) {
				return quest;
			}
		}
		return null;
	}

	/** Fixes nulls / invalid slots after loading (drops duplicates and out-of-range slots). */
	public void normalize() {
		if (active == null) {
			active = new ArrayList<>();
		}
		if (slotReadyAt == null || slotReadyAt.length != SLOTS) {
			long[] fixed = new long[SLOTS];
			if (slotReadyAt != null) {
				System.arraycopy(slotReadyAt, 0, fixed, 0, Math.min(SLOTS, slotReadyAt.length));
			}
			slotReadyAt = fixed;
		}
		Set<Integer> used = new HashSet<>();
		active.removeIf(q -> q == null || q.id == null || q.slot < 0 || q.slot >= SLOTS || q.state == QuestState.EXPIRED || !used.add(q.slot));
		for (QuestInstance quest : active) {
			quest.normalize();
		}
	}

	public long readyAt(int slot) {
		return slotReadyAt != null && slot >= 0 && slot < slotReadyAt.length ? slotReadyAt[slot] : 0L;
	}

	public void setReadyAt(int slot, long time) {
		if (slotReadyAt == null || slotReadyAt.length != SLOTS) {
			normalize();
		}
		if (slot >= 0 && slot < SLOTS) {
			slotReadyAt[slot] = time;
		}
	}
}
