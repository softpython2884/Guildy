package dev.guildy.anchor;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Anchor flags planted by a guild (persisted inside the guild, {@code Guild.anchors}).
 */
public class GuildAnchorData {
	public List<ChunkAnchor> flags = new ArrayList<>();
	/** Next anchor id (never reused). */
	public int nextId = 1;

	public void fixNulls() {
		if (flags == null) {
			flags = new ArrayList<>();
		}
		flags.removeIf(anchor -> anchor == null || anchor.pos == null);
		for (ChunkAnchor anchor : flags) {
			if (anchor.dimension == null || anchor.dimension.isBlank()) {
				anchor.dimension = "minecraft:overworld";
			}
			if (anchor.placedBy == null) {
				anchor.placedBy = "";
			}
			nextId = Math.max(nextId, anchor.id + 1);
		}
		flags.sort(Comparator.comparingInt(anchor -> anchor.id));
	}

	public @Nullable ChunkAnchor get(int id) {
		for (ChunkAnchor anchor : flags) {
			if (anchor.id == id) {
				return anchor;
			}
		}
		return null;
	}

	public @Nullable ChunkAnchor at(String dimension, BlockPos pos) {
		for (ChunkAnchor anchor : flags) {
			if (anchor.pos.equals(pos) && anchor.dimension.equals(dimension)) {
				return anchor;
			}
		}
		return null;
	}

	ChunkAnchor add(String dimension, BlockPos pos, String placedBy) {
		ChunkAnchor anchor = new ChunkAnchor(nextId++, dimension, pos, placedBy);
		flags.add(anchor);
		return anchor;
	}
}
