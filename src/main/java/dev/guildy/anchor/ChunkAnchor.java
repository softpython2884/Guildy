package dev.guildy.anchor;

import net.minecraft.core.BlockPos;

/**
 * A planted anchor flag (persisted inside the guild, {@code Guild.anchors}): the banner at {@link #pos} in
 * {@link #dimension} keeps the chunks around it active while the anchor is active (see {@link AnchorTickets}).
 */
public final class ChunkAnchor {
	/** Never reused within the guild. */
	public int id;
	/** Dimension id, e.g. "minecraft:overworld". */
	public String dimension = "minecraft:overworld";
	/** Position of the banner block. */
	public BlockPos pos = BlockPos.ZERO;
	/** Name of the player who planted it (display only). */
	public String placedBy = "";
	/** Real time of the planting (ms). */
	public long placedAt;

	public ChunkAnchor() {
	}

	ChunkAnchor(int id, String dimension, BlockPos pos, String placedBy) {
		this.id = id;
		this.dimension = dimension;
		this.pos = pos.immutable();
		this.placedBy = placedBy;
		this.placedAt = System.currentTimeMillis();
	}

	public int chunkX() {
		return pos.getX() >> 4;
	}

	public int chunkZ() {
		return pos.getZ() >> 4;
	}
}
