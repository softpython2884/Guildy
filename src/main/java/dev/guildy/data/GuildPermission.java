package dev.guildy.data;

/**
 * Guild actions gated by role. The minimum role for each one is configurable (config.json5 → roles).
 */
public enum GuildPermission {
	/** Break / place blocks in the territory. */
	BUILD,
	/** Use doors, chests, buttons, entities in the territory. */
	INTERACT,
	INVITE,
	KICK,
	/** Claim / unclaim chunks, plant extension flags. */
	CLAIM,
	/** Withdraw gold from the treasury, spend treasury gold (flags, unlocks). */
	TREASURY,
	/** Accept / dismiss villagers, assign tasks, configure storage chests. */
	VILLAGERS,
	/** Unlock / validate / dismantle structures. */
	STRUCTURES,
	/** Edit the guild banner. */
	BANNER,
	SET_HOME,
	/** Abandon / reroll quests. */
	QUESTS
}
