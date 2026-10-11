package dev.guildy.quest;

/**
 * One active quest of a guild (persisted). Fields are owned by the quest module.
 */
public class QuestInstance {
	/** Unique id (random). */
	public String id;
	/** Template id from quests.json5. */
	public String template;
}
