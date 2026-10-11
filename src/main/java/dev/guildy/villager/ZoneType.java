package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Kind of work zone (marked by a fanion). Persisted by name. No Minecraft classes (unit tested).
 */
public enum ZoneType {
	/** Simulated mining at the fanion (its height sets the depth). */
	MINE,
	/** Lumberjacks cut the natural trees of the zone. */
	FOREST,
	/** Farmers harvest and replant the crops of the zone. */
	FARM,
	/** Guards walk random points of the zone and fight the monsters inside. */
	PATROL;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String langKey() {
		return "villager.zone.type." + id();
	}

	public String descKey() {
		return "villager.zone.type." + id() + ".desc";
	}

	/** Task working in this kind of zone. */
	public Task task() {
		return switch (this) {
			case MINE -> Task.MINER;
			case FOREST -> Task.LUMBERJACK;
			case FARM -> Task.FARMER;
			case PATROL -> Task.GUARD;
		};
	}

	/** Dye color name of the fanion. */
	public String color() {
		return switch (this) {
			case MINE -> "gray";
			case FOREST -> "green";
			case FARM -> "yellow";
			case PATROL -> "red";
		};
	}

	/** The radius matters (a mine only uses its fanion: depth and meeting point). */
	public boolean usesRadius() {
		return this != MINE;
	}

	/** Zone type a task works in, or null (idle, sorter, expeditions). */
	public static @Nullable ZoneType forTask(Task task) {
		return switch (task) {
			case MINER -> MINE;
			case LUMBERJACK -> FOREST;
			case FARMER -> FARM;
			case GUARD -> PATROL;
			default -> null;
		};
	}

	public static ZoneType parse(@Nullable String value, ZoneType fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
