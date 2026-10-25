package dev.guildy.villager;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Job of a guild villager. Persisted by name.
 */
public enum Task {
	IDLE,
	SORTER,
	MINER,
	LUMBERJACK,
	FARMER,
	GUARD,
	/** Expedition in the wilds (simulated, the villager is away for a while). */
	EXPLORER,
	/** Expedition in the Nether through a portal of the territory (simulated, riskier, better loot). */
	NETHER,
	/** Repairs the equipment of the territory's armor stands at the nearest anvil (see {@link BlacksmithTask}). */
	BLACKSMITH,
	/** Turns books into enchanted books at an enchanting table of the territory (see {@link AlchemistTask}). */
	ALCHEMIST,
	/** Works at the "Atelier du cartographe" on the guild's ruin research (dev.guildy.ruin). */
	CARTOGRAPHER,
	/** "Bâtisseur": fetches the materials of the guild's construction sites (see {@link BuilderTask}). */
	BUILDER;

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Lang key of the display name. */
	public String langKey() {
		return "villager.task." + id();
	}

	public String descKey() {
		return "villager.task." + id() + ".desc";
	}

	/** Menu icon. */
	public Item icon() {
		return switch (this) {
			case IDLE -> Items.VILLAGER_SPAWN_EGG;
			case SORTER -> Items.CHEST;
			case MINER -> Items.IRON_PICKAXE;
			case LUMBERJACK -> Items.IRON_AXE;
			case FARMER -> Items.IRON_HOE;
			case GUARD -> Items.IRON_SWORD;
			case EXPLORER -> Items.COMPASS;
			case NETHER -> Items.FLINT_AND_STEEL;
			case BLACKSMITH -> Items.ANVIL;
			case ALCHEMIST -> Items.ENCHANTING_TABLE;
			case CARTOGRAPHER -> Items.MAP;
			case BUILDER -> Items.BRICKS;
		};
	}

	/** Item held while working (visual only), null for IDLE. */
	public @Nullable Item handItem() {
		return switch (this) {
			case IDLE -> null;
			case BLACKSMITH, ALCHEMIST -> CraftJobs.handItem(this);
			default -> icon();
		};
	}

	/** Kind of work zone this task works in (null: no zone, e.g. the sorter or expeditions). */
	public @Nullable ZoneType zoneType() {
		return ZoneType.forTask(this);
	}

	/** Workers that collect items and bring them to a deposit chest. */
	public boolean isGatherer() {
		return this == MINER || this == LUMBERJACK || this == FARMER;
	}

	/** Tasks that send the villager away on a simulated trip. */
	public boolean isExpedition() {
		return this == EXPLORER || this == NETHER;
	}

	/** Tasks whose collected items count toward a mission goal. */
	public boolean hasGoal() {
		return isGatherer() || isExpedition();
	}

	public static Task parse(@Nullable String value, Task fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
