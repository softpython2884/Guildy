package dev.guildy.quest;

/**
 * Quest kinds. The target of GATHER is a block (id or #tag), KILL an entity type, DELIVER an item;
 * SURVIVE counts night wave victories and EXPLORE a destination point (no target).
 */
public enum QuestType {
	GATHER,
	KILL,
	DELIVER,
	SURVIVE,
	EXPLORE;

	public boolean hasTarget() {
		return this == GATHER || this == KILL || this == DELIVER;
	}

	/** Lang key suffix ("quest.type.gather"...). */
	public String key() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
