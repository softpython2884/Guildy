package dev.guildy.structure;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Position → structure lookup for the claim dimension (block breaks and sign clicks must be O(1)).
 * Rebuilt after the guild data is loaded, maintained on build/removal.
 */
final class StructureIndex {
	/** A structure and its owner. */
	record Ref(Guild guild, BuiltStructure structure) {
	}

	private static final Long2ObjectOpenHashMap<Ref> BLOCKS = new Long2ObjectOpenHashMap<>();
	private static final Long2ObjectOpenHashMap<Ref> SIGNS = new Long2ObjectOpenHashMap<>();

	private StructureIndex() {
	}

	static void clear() {
		BLOCKS.clear();
		SIGNS.clear();
	}

	static void rebuild() {
		clear();
		for (Guild guild : GuildManager.all()) {
			for (BuiltStructure structure : guild.structures.built) {
				add(guild, structure);
			}
		}
	}

	static void add(Guild guild, BuiltStructure structure) {
		Ref ref = new Ref(guild, structure);
		if (structure.blocks != null) {
			for (BlockPos pos : structure.blocks) {
				BLOCKS.put(pos.asLong(), ref);
			}
		}
		if (structure.signs != null) {
			for (BlockPos pos : structure.signs) {
				SIGNS.put(pos.asLong(), ref);
			}
		}
	}

	static void remove(BuiltStructure structure) {
		if (structure.blocks != null) {
			for (BlockPos pos : structure.blocks) {
				Ref ref = BLOCKS.get(pos.asLong());
				if (ref != null && ref.structure() == structure) {
					BLOCKS.remove(pos.asLong());
				}
			}
		}
		if (structure.signs != null) {
			for (BlockPos pos : structure.signs) {
				Ref ref = SIGNS.get(pos.asLong());
				if (ref != null && ref.structure() == structure) {
					SIGNS.remove(pos.asLong());
				}
			}
		}
	}

	static @Nullable Ref block(BlockPos pos) {
		return BLOCKS.get(pos.asLong());
	}

	static @Nullable Ref sign(BlockPos pos) {
		return SIGNS.get(pos.asLong());
	}

	/** True when the position is a block or a sign of any structure. */
	static boolean occupied(BlockPos pos) {
		long key = pos.asLong();
		return BLOCKS.containsKey(key) || SIGNS.containsKey(key);
	}
}
