package dev.guildy.ruin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One ruin known by the server (persisted in {@code <world>/guildy/ruins.json}): where it is, its data markers and
 * the state of its guardian. Registered when its pieces are generated (data markers), when its start chunk is loaded
 * or when a cartographer locates it.
 */
public class RuinRecord {
	/** Unique key: {@code <dimension>@<startChunkX>,<startChunkZ>@<structure id>}. */
	public String key = "";
	/** Dimension id ("minecraft:overworld", "minecraft:the_nether"). */
	public String dimension = "";
	/** Structure id ("guildy:ruin/crypte_oubliee"). */
	public String structure = "";
	/** Ruin id (template / structure path after "ruin/"), the key of its definition in ruins.json5. */
	public String ruinId = "";
	public int minX;
	public int minY;
	public int minZ;
	public int maxX;
	public int maxY;
	public int maxZ;
	/** Boss spawn cell (data marker guildy:boss), null until its piece is generated (or when the template has none). */
	public @Nullable BlockPos boss;
	/** Escort spawn cells (data markers guildy:minion). */
	public List<BlockPos> minions = new ArrayList<>();
	/** Entrance shown by maps and compasses (data marker guildy:entrance). */
	public @Nullable BlockPos entrance;
	/** Room guard posts (data markers guildy:guard; empty in older templates and saves: fallback posts are used). */
	public List<BlockPos> guards = new ArrayList<>();
	public RuinState state = RuinState.DORMANT;
	/** In-game day (overworld clock / 24000) of the last victory, -1 if never defeated. */
	public long clearedDay = -1;
	public int timesCleared;
	/**
	 * Threat level ("Menace" I, II, III...), raised by each victory over the guardian: its next return is stronger. 0 in
	 * saves older than the threat levels (read as 1 + {@link #timesCleared}, see {@link ThreatRules#stored}).
	 */
	public int threat;
	/** Names of the players of the last victory. */
	public String lastVictors = "";
	public long discoveredAt;

	public RuinRecord() {
	}

	public static String key(String dimension, int chunkX, int chunkZ, String structure) {
		return dimension + "@" + chunkX + "," + chunkZ + "@" + structure;
	}

	public BoundingBox box() {
		return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public void setBox(BoundingBox box) {
		minX = box.minX();
		minY = box.minY();
		minZ = box.minZ();
		maxX = box.maxX();
		maxY = box.maxY();
		maxZ = box.maxZ();
	}

	/** Inside the box inflated by {@code margin} blocks on every side. */
	public boolean contains(double x, double y, double z, int margin) {
		return x >= minX - margin && x < maxX + 1 + margin && y >= minY - margin && y < maxY + 1 + margin
				&& z >= minZ - margin && z < maxZ + 1 + margin;
	}

	public boolean contains(BlockPos pos, int margin) {
		return contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, margin);
	}

	public BlockPos center() {
		return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
	}

	/** Where maps and compasses point: the entrance, else the boss cell, else the middle of the box (ground level). */
	public BlockPos target() {
		if (entrance != null) {
			return entrance;
		}
		if (boss != null) {
			return boss;
		}
		return new BlockPos((minX + maxX) / 2, minY, (minZ + maxZ) / 2);
	}

	public boolean isNether() {
		return "minecraft:the_nether".equals(dimension);
	}

	/** Horizontal squared distance from the box (0 inside). */
	public double distanceSq(double x, double z) {
		double dx = x < minX ? minX - x : x > maxX + 1 ? x - maxX - 1 : 0;
		double dz = z < minZ ? minZ - z : z > maxZ + 1 ? z - maxZ - 1 : 0;
		return dx * dx + dz * dz;
	}

	void fixNulls() {
		if (key == null) {
			key = "";
		}
		if (dimension == null) {
			dimension = "";
		}
		if (structure == null) {
			structure = "";
		}
		if (ruinId == null) {
			ruinId = "";
		}
		if (minions == null) {
			minions = new ArrayList<>();
		}
		minions.removeIf(p -> p == null);
		if (guards == null) {
			guards = new ArrayList<>();
		}
		guards.removeIf(p -> p == null);
		if (state == null || state == RuinState.AWAKE) {
			// A fight never survives a restart: the guardian sleeps again.
			state = RuinState.DORMANT;
		}
		if (lastVictors == null) {
			lastVictors = "";
		}
		threat = Math.min(ThreatRules.MAX_LEVEL, ThreatRules.stored(threat, timesCleared));
	}
}
