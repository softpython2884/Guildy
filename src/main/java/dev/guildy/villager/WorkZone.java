package dev.guildy.villager;

import net.minecraft.core.BlockPos;

/**
 * A work zone (persisted in {@link GuildVillagerData#zones}): a square of chunks around a fanion. Zones may lie
 * outside the territory, never inside another guild's (checked when the fanion is planted, and again for every
 * block the villagers touch).
 */
public class WorkZone {
	/** Unique per guild, never reused. */
	public int id;
	public ZoneType type = ZoneType.MINE;
	public String name = "";
	/** Fanion position: its chunk is the middle of the square, its height the depth of a mine. */
	public BlockPos pos;
	/** Half-size in chunks: 0 = the fanion's chunk only, 1 = 3×3 chunks... (mines ignore it). */
	public int radius = 1;
	public ZoneObjective objective = ZoneObjective.MIXED;
	/**
	 * A fanion was planted for this zone: only then is a banner at {@link #pos} its fanion (named after it, protected,
	 * removed with it). False for zones created "here" from the menu and for migrated work positions.
	 */
	public boolean fanion;
	public long createdAt;

	public WorkZone() {
	}

	public WorkZone(int id, ZoneType type, String name, BlockPos pos, int radius, long createdAt) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.pos = pos;
		this.radius = radius;
		this.objective = ZoneObjective.defaultFor(type);
		this.createdAt = createdAt;
	}

	/** Radius actually used: a mine works at its fanion only. */
	public int effectiveRadius() {
		return type.usesRadius() ? Math.max(0, radius) : 0;
	}

	/** Side of the square, in chunks. */
	public int sizeChunks() {
		return effectiveRadius() * 2 + 1;
	}

	public boolean contains(int x, int z) {
		return ZoneMath.inChunkSquare(x, z, pos.getX(), pos.getZ(), effectiveRadius());
	}

	public boolean contains(BlockPos block) {
		return contains(block.getX(), block.getZ());
	}

	/** Inside the square widened by {@code margin} blocks. */
	public boolean containsWithMargin(double x, double z, int margin) {
		return ZoneMath.inChunkSquare(x, z, pos.getX(), pos.getZ(), effectiveRadius(), margin);
	}

	/** Block bounds {minX, minZ, maxX, maxZ} (inclusive). */
	public int[] bounds() {
		return ZoneMath.squareBounds(pos.getX(), pos.getZ(), effectiveRadius());
	}

	/** Identity of the work settings: a change restarts the search of the villagers working here. */
	public String settingsKey() {
		return "zone:" + id + ":" + pos.asLong() + ":" + effectiveRadius() + ":" + objective;
	}

	/** Fixes nulls / out-of-range values after deserialization (blank names are renamed by the guild data). */
	void sanitize(int maxRadius) {
		if (type == null) {
			type = ZoneType.MINE;
		}
		objective = ZoneObjective.validFor(type, objective);
		radius = Math.max(0, Math.min(maxRadius, radius));
		if (name == null) {
			name = "";
		}
	}
}
