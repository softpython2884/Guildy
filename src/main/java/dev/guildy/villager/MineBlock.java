package dev.guildy.villager;

/**
 * What a real miner sees in one block (pure, see {@link MineRules}; the world adapter is {@link MineWorld}).
 */
public enum MineBlock {
	/** Air, or natural plants without collision: walkable. */
	AIR,
	/** Thing without collision placed by players (torch, rail, carpet, button...): walkable, never broken nor undermined. */
	FIXTURE,
	/** Natural block the miner may break (villagers.json5 list). */
	NATURAL,
	/** Natural block that falls when unsupported (sand, gravel). */
	FALLING,
	/** Ore: breakable, followed in veins. */
	ORE,
	/** Water (source or flowing): may be sealed with a block. */
	WATER,
	/** Lava: may be sealed with a block. */
	LAVA,
	/** Block holding water that cannot be replaced (waterlogged stairs...): never touched, never opened. */
	WET,
	/** Anything else (player blocks, block entities, bedrock...): never broken nor undermined. */
	PROTECTED,
	/** Chunk not loaded. */
	UNLOADED;

	public boolean passable() {
		return this == AIR || this == FIXTURE;
	}

	public boolean breakable() {
		return this == NATURAL || this == FALLING || this == ORE;
	}

	public boolean liquid() {
		return this == WATER || this == LAVA;
	}
}
