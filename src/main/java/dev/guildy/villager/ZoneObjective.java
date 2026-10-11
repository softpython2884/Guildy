package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the villagers of a zone go for. Persisted by name. Block / item ids only, no Minecraft classes (unit tested).
 */
public enum ZoneObjective {
	// mines: loot table set (villagers.json5 -> miner)
	MIXED(ZoneType.MINE, "minecraft:iron_pickaxe", ""),
	ORES(ZoneType.MINE, "minecraft:raw_iron", ""),
	STONE(ZoneType.MINE, "minecraft:cobblestone", ""),
	// forests: log block of the trees to cut ("" = any natural tree)
	ANY_WOOD(ZoneType.FOREST, "minecraft:oak_sapling", ""),
	OAK(ZoneType.FOREST, "minecraft:oak_log", "minecraft:oak_log"),
	SPRUCE(ZoneType.FOREST, "minecraft:spruce_log", "minecraft:spruce_log"),
	BIRCH(ZoneType.FOREST, "minecraft:birch_log", "minecraft:birch_log"),
	JUNGLE(ZoneType.FOREST, "minecraft:jungle_log", "minecraft:jungle_log"),
	ACACIA(ZoneType.FOREST, "minecraft:acacia_log", "minecraft:acacia_log"),
	DARK_OAK(ZoneType.FOREST, "minecraft:dark_oak_log", "minecraft:dark_oak_log"),
	MANGROVE(ZoneType.FOREST, "minecraft:mangrove_log", "minecraft:mangrove_log"),
	CHERRY(ZoneType.FOREST, "minecraft:cherry_log", "minecraft:cherry_log"),
	PALE_OAK(ZoneType.FOREST, "minecraft:pale_oak_log", "minecraft:pale_oak_log"),
	POPLAR(ZoneType.FOREST, "minecraft:poplar_log", "minecraft:poplar_log"),
	// fields: crop block to harvest ("" = every crop, plus melons, pumpkins and sugar cane)
	ANY_CROP(ZoneType.FARM, "minecraft:wheat", ""),
	WHEAT(ZoneType.FARM, "minecraft:wheat_seeds", "minecraft:wheat"),
	CARROTS(ZoneType.FARM, "minecraft:carrot", "minecraft:carrots"),
	POTATOES(ZoneType.FARM, "minecraft:potato", "minecraft:potatoes"),
	BEETROOTS(ZoneType.FARM, "minecraft:beetroot", "minecraft:beetroots"),
	// patrols have a single behaviour
	PATROL(ZoneType.PATROL, "minecraft:iron_sword", "");

	private final ZoneType type;
	private final String icon;
	private final String block;

	ZoneObjective(ZoneType type, String icon, String block) {
		this.type = type;
		this.icon = icon;
		this.block = block;
	}

	public ZoneType type() {
		return type;
	}

	/** Item id of the menu icon. */
	public String icon() {
		return icon;
	}

	/** Block id matched by the objective ("" = any). */
	public String block() {
		return block;
	}

	public boolean isAny() {
		return block.isEmpty();
	}

	public String langKey() {
		return "villager.objective." + name().toLowerCase(Locale.ROOT);
	}

	public String descKey() {
		return langKey() + ".desc";
	}

	/** Lumberjack: may a natural tree made of this log block be cut? */
	public boolean acceptsLog(String blockId) {
		return type == ZoneType.FOREST && (block.isEmpty() || block.equals(blockId));
	}

	/** Farmer: may this (mature) crop block be harvested? */
	public boolean acceptsCrop(String blockId) {
		return type == ZoneType.FARM && (block.isEmpty() || block.equals(blockId));
	}

	/** Melons, pumpkins and sugar cane are only harvested by "any crop" fields. */
	public boolean acceptsExtras() {
		return this == ANY_CROP;
	}

	/** Objective of a new zone (and of the automatic mode). */
	public static ZoneObjective defaultFor(ZoneType type) {
		return switch (type) {
			case MINE -> MIXED;
			case FOREST -> ANY_WOOD;
			case FARM -> ANY_CROP;
			case PATROL -> PATROL;
		};
	}

	/** The objectives offered for a zone type, in menu order. */
	public static List<ZoneObjective> forType(ZoneType type) {
		List<ZoneObjective> list = new ArrayList<>();
		for (ZoneObjective objective : values()) {
			if (objective.type == type) {
				list.add(objective);
			}
		}
		return list;
	}

	/** {@code objective} when it belongs to the type, else the type's default. */
	public static ZoneObjective validFor(ZoneType type, @Nullable ZoneObjective objective) {
		return objective != null && objective.type == type ? objective : defaultFor(type);
	}
}
