package dev.guildy.villager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A gathering pit of a guild (persisted in {@link GuildVillagerData#pits}): its geometry ({@link PitPlan}: corner, side,
 * ground height, depth, ramp entry and sense), how far it is dug, and the columns given up. The blocks still to dig
 * are read from the world (a block already gone is simply done), so nothing else is stored.
 */
public class SupplyPit {
	/** Unique per guild. */
	public int id;
	/** Minimum corner of the footprint. */
	public int x;
	public int z;
	public int side = 6;
	/** Ground height: level k digs y = top + 1 - k. */
	public int top;
	/** Highest ground of the footprint when it was planned (blocks above {@link #top} are dug first). */
	public int highest;
	/** Levels the pit may reach (lowered when a ramp step cannot be dug). */
	public int depth;
	public int entry;
	public boolean clockwise = true;
	/** Levels finished (0 = only the ground above the top cleared or nothing yet). */
	public int levels;
	/** The ground above {@link #top} was cleared. */
	public boolean levelled;
	/** Nothing more to dig (depth reached, or stopped). */
	public boolean finished;
	/** Zone of a mine pit ({@link GuildVillagerData#zones} id), -1 = outside the territory. */
	public int zoneId = -1;
	/** Columns given up (footprint cell index × 256 + level): never dug deeper. */
	public List<Integer> stopped = new ArrayList<>();
	public long createdAt;
	public long usedAt;
	/** Blocks dug, and items brought back per item id. */
	public long dug;
	public Map<String, Long> found = new LinkedHashMap<>();

	public SupplyPit() {
	}

	SupplyPit(int id, PitRules.Site site, int highest, int zoneId, long now) {
		this.id = id;
		this.x = site.x0();
		this.z = site.z0();
		this.side = site.side();
		this.top = site.top();
		this.highest = Math.max(site.top(), highest);
		this.depth = site.depth();
		this.entry = site.entry();
		this.clockwise = site.clockwise();
		this.zoneId = zoneId;
		this.createdAt = now;
		this.usedAt = now;
	}

	PitPlan plan() {
		return new PitPlan(x, z, side, top, depth, entry, clockwise);
	}

	/** Level from which the column {@code cell} was given up, or Integer.MAX_VALUE. */
	int stoppedAt(int cell) {
		int best = Integer.MAX_VALUE;
		for (int packed : stopped) {
			if (packed / 256 == cell) {
				best = Math.min(best, packed % 256);
			}
		}
		return best;
	}

	void stop(int cell, int level) {
		if (stoppedAt(cell) > level) {
			stopped.add(cell * 256 + Math.max(0, Math.min(255, level)));
		}
	}

	boolean overlaps(int x0, int z0, int side0, int gap) {
		return x0 < x + side + gap && x < x0 + side0 + gap && z0 < z + side + gap && z < z0 + side0 + gap;
	}

	/** Fixes the pits of a guild after deserialization (nulls, duplicate ids, values out of range). */
	static void sanitizeAll(GuildVillagerData data) {
		if (data.pits == null) {
			data.pits = new ArrayList<>();
		}
		data.pits.removeIf(pit -> pit == null);
		int max = 0;
		for (SupplyPit pit : data.pits) {
			max = Math.max(max, pit.id);
		}
		data.nextPitId = Math.max(Math.max(1, data.nextPitId), max + 1);
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (SupplyPit pit : data.pits) {
			if (pit.id <= 0 || !ids.add(pit.id)) {
				pit.id = data.nextPitId++;
				ids.add(pit.id);
			}
			pit.sanitize();
		}
	}

	void sanitize() {
		side = Math.max(3, Math.min(16, side));
		depth = Math.max(0, Math.min(PitPlan.maxDepthFor(side), depth));
		levels = Math.max(0, Math.min(depth, levels));
		if (stopped == null) {
			stopped = new ArrayList<>();
		}
		stopped.removeIf(value -> value == null || value < 0);
		if (found == null) {
			found = new LinkedHashMap<>();
		}
		highest = Math.max(top, highest);
	}
}
