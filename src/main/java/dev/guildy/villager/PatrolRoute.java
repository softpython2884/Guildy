package dev.guildy.villager;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A guard patrol route (persisted in {@link GuildVillagerData#routes}): waypoints walked in a loop (or back and
 * forth). Each waypoint is the standing position above the block the player clicked.
 */
public class PatrolRoute {
	/** Unique per guild, never reused. */
	public int id;
	public String name = "";
	public List<BlockPos> points = new ArrayList<>();
	/** False = loop (last point → first), true = back and forth (walls, roads). */
	public boolean pingPong;
	public long createdAt;

	public PatrolRoute() {
	}

	public PatrolRoute(int id, String name, List<BlockPos> points, long createdAt) {
		this.id = id;
		this.name = name;
		this.points = new ArrayList<>(points);
		this.createdAt = createdAt;
	}

	public boolean loops() {
		return !pingPong;
	}

	/** Walking length of one full round (blocks). */
	public int length() {
		return ZoneMath.pathLength(points, loops());
	}

	/** Identity of the path: a change makes the guards start over at the nearest waypoint. */
	public String settingsKey() {
		return "route:" + id + ":" + points.hashCode() + ":" + pingPong;
	}

	/** Fixes nulls after deserialization (blank names are renamed by the guild data). */
	void sanitize() {
		if (points == null) {
			points = new ArrayList<>();
		}
		points.removeIf(point -> point == null);
		if (name == null) {
			name = "";
		}
	}
}
