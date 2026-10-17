package dev.guildy.villager;

import java.util.Locale;

/**
 * One toggle of the "Autorisations du fanion" of a work zone (see {@link ZonePermissions}): what the villagers of the
 * zone may do there. Persisted by {@link #id()} in {@link WorkZone#options}; an option absent from a zone takes its
 * {@link #defaultOn() default}, which reproduces the behaviour of the zones made before the toggles existed. Ids are
 * unique within a zone type only. No Minecraft classes (unit tested).
 */
public enum ZoneOption {
	// fields (farmers)
	/** Harvest the ripe crops. */
	HARVEST(ZoneType.FARM, "harvest", "minecraft:wheat", true),
	/** Replant a harvested crop with one of its seeds. */
	REPLANT(ZoneType.FARM, "replant", "minecraft:wheat_seeds", true),
	/** Sow the empty farmland with seeds of the bag or the storage. */
	SOW(ZoneType.FARM, "sow", "minecraft:beetroot_seeds", true),
	/** Till again (and sow again) the farmland that turned back to dirt (trampled, dried out). */
	RETILL(ZoneType.FARM, "retill", "minecraft:coarse_dirt", true),
	/**
	 * Till grass, dirt, coarse and rooted dirt that is hydrated: by the water already there (the behaviour of the older
	 * field zones, "till near water"), or by the water holes once {@link #IRRIGATE} has dug them.
	 */
	TILL(ZoneType.FARM, "till", "minecraft:iron_hoe", true),
	/** Dig 1-block water holes on a grid so that the whole soil of the zone is hydrated; refill them. */
	IRRIGATE(ZoneType.FARM, "irrigate", "minecraft:water_bucket", false),
	/** Remove grass, ferns, flowers and dead bushes from the soil to till. */
	CLEAR(ZoneType.FARM, "clear", "minecraft:short_grass", false),
	/** Fill 1-block holes and shave 1-block bumps of natural soil. */
	LEVEL(ZoneType.FARM, "level", "minecraft:iron_shovel", false),
	/** Bone meal from the storage on the growing crops. */
	BONEMEAL(ZoneType.FARM, "bonemeal", "minecraft:bone_meal", false),
	// forests (lumberjacks)
	/** Cut the natural trees. */
	CUT(ZoneType.FOREST, "cut", "minecraft:iron_axe", true),
	/** Replant a sapling at the base of a cut tree. */
	TREE_REPLANT(ZoneType.FOREST, "replant", "minecraft:oak_sapling", true),
	/** Plant new saplings on the free ground when nothing is left to cut. */
	TREE_PLANT(ZoneType.FOREST, "plant", "minecraft:birch_sapling", true);

	private final ZoneType type;
	private final String id;
	private final String icon;
	private final boolean defaultOn;

	ZoneOption(ZoneType type, String id, String icon, boolean defaultOn) {
		this.type = type;
		this.id = id;
		this.icon = icon;
		this.defaultOn = defaultOn;
	}

	/** Zone type the option belongs to. */
	public ZoneType type() {
		return type;
	}

	/** Persisted name (unique within the zone type). */
	public String id() {
		return id;
	}

	/** Item id of the menu icon. */
	public String icon() {
		return icon;
	}

	/** Value of a zone that never changed this option (older zones included). */
	public boolean defaultOn() {
		return defaultOn;
	}

	public String langKey() {
		return "villager.field.option." + type.id() + "." + id.toLowerCase(Locale.ROOT);
	}

	public String descKey() {
		return langKey() + ".desc";
	}
}
