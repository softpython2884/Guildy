package dev.guildy.villager;

import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Where a gathering villager looks for work: the square of its zone, or (automatic mode) a disc around where it
 * stands, kept within {@code zones.maxZoneDistance} of the heart. Other guilds' chunks are always excluded.
 */
final class WorkArea {
	final @Nullable WorkZone zone;
	final int minX;
	final int minZ;
	final int maxX;
	final int maxZ;
	/** Reference height: the fanion, or the villager in automatic mode. */
	final int refY;
	private final int centerX;
	private final int centerZ;
	private final int radius;
	private final @Nullable BlockPos heart;
	private final int maxDistance;

	private WorkArea(@Nullable WorkZone zone, int[] bounds, int refY, int centerX, int centerZ, int radius, @Nullable BlockPos heart,
			int maxDistance) {
		this.zone = zone;
		this.minX = bounds[0];
		this.minZ = bounds[1];
		this.maxX = bounds[2];
		this.maxZ = bounds[3];
		this.refY = refY;
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.radius = radius;
		this.heart = heart;
		this.maxDistance = maxDistance;
	}

	static WorkArea of(WorkZone zone) {
		return new WorkArea(zone, zone.bounds(), zone.pos.getY(), zone.pos.getX(), zone.pos.getZ(), 0, null, 0);
	}

	static WorkArea auto(Guild guild, BlockPos center, VillagerConfig config) {
		int radius = config.zones.autoSearchRadius;
		int[] bounds = {center.getX() - radius, center.getZ() - radius, center.getX() + radius, center.getZ() + radius};
		return new WorkArea(null, bounds, center.getY(), center.getX(), center.getZ(), radius, guild.heart, config.zones.maxZoneDistance);
	}

	boolean isZone() {
		return zone != null;
	}

	/** Column (x, z) inside the area (other guilds' chunks are filtered by the scan). */
	boolean contains(int x, int z) {
		if (x < minX || x > maxX || z < minZ || z > maxZ) {
			return false;
		}
		if (zone != null) {
			return true;
		}
		return ZoneMath.within(x, z, centerX, centerZ, radius)
				&& (heart == null || ZoneMath.within(x, z, heart.getX(), heart.getZ(), maxDistance));
	}

	boolean contains(BlockPos pos) {
		return contains(pos.getX(), pos.getZ());
	}

	/**
	 * Incremental, nearest-first walk over the chunks of an area: each call returns the next chunk that is loaded,
	 * entity-ticking (a player is around) and not another guild's. Only loaded chunks are ever touched.
	 */
	static final class Scan {
		private static final int MAX_SKIPS = 64;
		final WorkArea area;
		private final long[] chunks;
		private int next;

		Scan(WorkArea area, BlockPos from) {
			this.area = area;
			this.chunks = ZoneMath.chunksNearestFirst(area.minX, area.minZ, area.maxX, area.maxZ, from.getX(), from.getZ());
		}

		boolean done() {
			return next >= chunks.length;
		}

		/** The last chunk returned will be returned again (its candidates did not all fit). */
		void retry() {
			if (next > 0) {
				next--;
			}
		}

		@Nullable LevelChunk next(WorkContext ctx) {
			int skips = 0;
			while (next < chunks.length && skips < MAX_SKIPS) {
				long key = chunks[next++];
				int cx = ChunkKey.x(key);
				int cz = ChunkKey.z(key);
				Guild owner = GuildManager.atChunk(key);
				LevelChunk chunk = owner == null || owner == ctx.guild ? ctx.level.getChunkSource().getChunkNow(cx, cz) : null;
				if (chunk != null && ctx.ticking(new BlockPos((cx << 4) + 8, area.refY, (cz << 4) + 8))) {
					return chunk;
				}
				skips++;
			}
			return null;
		}
	}
}
