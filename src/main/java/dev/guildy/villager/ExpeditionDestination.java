package dev.guildy.villager;

import java.util.ArrayList;
import java.util.List;

/**
 * One expedition destination of villagers.json5 ({@code expeditions.destinations}): pure config data, cleaned by
 * {@link ExpeditionDestinations#validate}. The default destination of each kind is not in this list: it is the
 * {@code expeditions.exploration} / {@code expeditions.nether} section (ids {@link ExpeditionDestinations#PLAINS} /
 * {@link ExpeditionDestinations#NETHER_WASTES}).
 */
public final class ExpeditionDestination {
	/** Unique id (lowercase letters, digits, '_'), stored on the villagers that chose it. */
	public String id = "";
	/** Name shown in menus and messages ('&' colours allowed). */
	public String name = "";
	/** Menu icon (item id). */
	public String icon = "";
	/** {@link ExpeditionDestinations#EXPLORATION} or {@link ExpeditionDestinations#NETHER} (needs a nether portal). */
	public String kind = ExpeditionDestinations.EXPLORATION;
	/** Guild level needed to choose it. */
	public int minLevel = 1;
	/** Trip duration in seconds; 0 = {@link #durationMultiplier} x the duration of the default destination of the kind. */
	public int durationSeconds;
	public double durationMultiplier = 1.0;
	/** Chance (0..1) that the villager never comes back. */
	public double lossChance = 0.05;
	/** Weighted rolls in {@link #loot} per trip. */
	public int rolls = 6;
	/** Chance (0..1) to also bring back a treasure map. */
	public double treasureMapChance;
	/** Multiplier of the "expedition" unique weapon chance of uniques.json5 (0 = never, 1 = unchanged). */
	public double uniqueChanceMultiplier = 1.0;
	/** Loot table ("minecraft:enchanted_book" = a book with a random enchantment, like vanilla chests). */
	public List<VillagerConfig.LootEntry> loot = new ArrayList<>();

	public ExpeditionDestination() {
	}

	ExpeditionDestination(String id, String name, String icon, String kind) {
		this.id = id;
		this.name = name;
		this.icon = icon;
		this.kind = kind;
	}

	boolean isNether() {
		return ExpeditionDestinations.NETHER.equals(kind);
	}
}
