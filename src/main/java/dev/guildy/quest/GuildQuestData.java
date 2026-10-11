package dev.guildy.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-guild quests (persisted inside the guild).
 */
public class GuildQuestData {
	/** Active quest slots (max 6, one per quest-board sign). */
	public List<QuestInstance> active = new ArrayList<>();
	public int completedTotal;
}
