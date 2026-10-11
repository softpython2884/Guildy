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
	GUARD;

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
		};
	}

	/** Item held while working (visual only), null for IDLE. */
	public @Nullable Item handItem() {
		return this == IDLE ? null : icon();
	}

	/** Tasks that need a work position set from the menu. */
	public boolean needsWorkPos() {
		return this == MINER || this == LUMBERJACK || this == FARMER;
	}

	/** Workers that collect items and bring them to a deposit chest. */
	public boolean isGatherer() {
		return this == MINER || this == LUMBERJACK || this == FARMER;
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
