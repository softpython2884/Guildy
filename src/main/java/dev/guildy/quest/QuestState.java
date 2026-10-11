package dev.guildy.quest;

/**
 * ACTIVE → COMPLETED (reward claimable by any member) → claimed (removed, slot frees);
 * ACTIVE → EXPIRED / abandoned (removed, slot frees).
 */
public enum QuestState {
	ACTIVE,
	COMPLETED,
	EXPIRED
}
