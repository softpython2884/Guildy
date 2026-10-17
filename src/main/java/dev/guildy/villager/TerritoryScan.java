package dev.guildy.villager;

import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Incremental, nearest-first walk over the claimed chunks of a guild (blacksmith and alchemist searches): each call
 * returns the next chunk that is loaded and entity-ticking (a player or the chunk loader keeps it active). Chunks are
 * never loaded by the scan.
 */
final class TerritoryScan {
	private static final int MAX_SKIPS = 64;
	private final long[] chunks;
	private int next;
	private int read;
	private int unloaded;

	TerritoryScan(Guild guild, BlockPos from) {
		int fx = from.getX() >> 4;
		int fz = from.getZ() >> 4;
		List<Long> claims = new ArrayList<>(guild.claims());
		claims.sort(Comparator.comparingLong(key -> {
			long dx = ChunkKey.x(key) - fx;
			long dz = ChunkKey.z(key) - fz;
			return dx * dx + dz * dz;
		}));
		chunks = new long[claims.size()];
		for (int i = 0; i < chunks.length; i++) {
			chunks[i] = claims.get(i);
		}
	}

	boolean done() {
		return next >= chunks.length;
	}

	int position() {
		return Math.min(next, chunks.length);
	}

	int total() {
		return chunks.length;
	}

	/** Chunks read (loaded and entity-ticking). */
	int readChunks() {
		return read;
	}

	int unloadedChunks() {
		return unloaded;
	}

	@Nullable LevelChunk next(WorkContext ctx) {
		int skips = 0;
		while (next < chunks.length && skips < MAX_SKIPS) {
			long key = chunks[next++];
			if (GuildManager.atChunk(key) != ctx.guild) {
				skips++;
				continue;
			}
			int cx = ChunkKey.x(key);
			int cz = ChunkKey.z(key);
			LevelChunk chunk = ctx.level.getChunkSource().getChunkNow(cx, cz);
			if (chunk != null && ctx.ticking(new BlockPos((cx << 4) + 8, ctx.villager.getBlockY(), (cz << 4) + 8))) {
				read++;
				return chunk;
			}
			unloaded++;
			skips++;
		}
		return null;
	}
}
