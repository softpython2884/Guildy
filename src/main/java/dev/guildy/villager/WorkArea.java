package dev.guildy.villager;

import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Where a gathering villager looks for work: the square of its zone, or (automatic mode) a disc kept within
 * {@code zones.maxZoneDistance} of the heart. Lumberjacks and farmers centre that disc on the nearest point of the
 * territory ({@link #anchored}, see {@link WorkAnchor}); the miner's stone search centres it on itself ({@link #auto}).
 * An optional margin widens the area for wanted items only ("objectifs prioritaires"). Other guilds' chunks are always
 * excluded.
 */
final class WorkArea {
	final @Nullable WorkZone zone;
	/** Scan bounds, margin included (inclusive). */
	final int minX;
	final int minZ;
	final int maxX;
	final int maxZ;
	/** Reference height: the fanion, or the villager in automatic mode. */
	final int refY;
	/** Blocks around the area where only wanted items are taken (0 = none). */
	final int margin;
	/** Automatic mode: centre of the disc (the anchor); zones: the fanion. */
	final int centerX;
	final int centerZ;
	private final int radius;
	private final @Nullable BlockPos heart;
	private final int maxDistance;

	private WorkArea(@Nullable WorkZone zone, int[] bounds, int refY, int centerX, int centerZ, int radius, @Nullable BlockPos heart,
			int maxDistance, int margin) {
		this.zone = zone;
		this.minX = bounds[0] - margin;
		this.minZ = bounds[1] - margin;
		this.maxX = bounds[2] + margin;
		this.maxZ = bounds[3] + margin;
		this.refY = refY;
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.radius = radius;
		this.heart = heart;
		this.maxDistance = maxDistance;
		this.margin = Math.max(0, margin);
	}

	static WorkArea of(WorkZone zone) {
		return of(zone, 0);
	}

	/** The zone square, widened by {@code margin} blocks for wanted items. */
	static WorkArea of(WorkZone zone, int margin) {
		return new WorkArea(zone, zone.bounds(), zone.pos.getY(), zone.pos.getX(), zone.pos.getZ(), 0, null, 0, margin);
	}

	/** Disc of {@code zones.autoSearchRadius} blocks around {@code center} (miner's stone search). */
	static WorkArea auto(Guild guild, BlockPos center, VillagerConfig config) {
		return disc(guild, center.getX(), center.getZ(), center.getY(), config, 0);
	}

	/**
	 * Automatic mode of lumberjacks and farmers: disc of {@code zones.autoSearchRadius} blocks around the point of the
	 * territory nearest to the villager (itself while inside it; {@code fallback} for a guild without territory).
	 */
	static WorkArea anchored(Guild guild, BlockPos villager, BlockPos fallback, VillagerConfig config, int margin) {
		int[] anchor = WorkAnchor.anchor(guild.claims(), villager.getX(), villager.getZ(), fallback.getX(), fallback.getZ());
		return disc(guild, anchor[0], anchor[1], villager.getY(), config, margin);
	}

	private static WorkArea disc(Guild guild, int x, int z, int y, VillagerConfig config, int margin) {
		int radius = config.zones.autoSearchRadius;
		int[] bounds = {x - radius, z - radius, x + radius, z + radius};
		return new WorkArea(null, bounds, y, x, z, radius, guild.heart, config.zones.maxZoneDistance, margin);
	}

	boolean isZone() {
		return zone != null;
	}

	/** Centre of the scan (nearest chunks first) and of the priorities: the anchor, or the fanion of a zone. */
	BlockPos center() {
		return new BlockPos(centerX, refY, centerZ);
	}

	/** Column (x, z) inside the area (other guilds' chunks are filtered by the scan). */
	boolean contains(int x, int z) {
		if (zone != null) {
			return zone.contains(x, z);
		}
		return inDisc(x, z, radius);
	}

	boolean contains(BlockPos pos) {
		return contains(pos.getX(), pos.getZ());
	}

	/** Inside the area or its margin (wanted items only there). */
	boolean reaches(int x, int z) {
		if (margin <= 0) {
			return contains(x, z);
		}
		if (zone != null) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}
		return inDisc(x, z, radius + margin);
	}

	boolean reaches(BlockPos pos) {
		return reaches(pos.getX(), pos.getZ());
	}

	private boolean inDisc(int x, int z, int r) {
		if (x < minX || x > maxX || z < minZ || z > maxZ) {
			return false;
		}
		return WorkAnchor.inArea(x, z, centerX, centerZ, r, heart != null, heart == null ? 0 : heart.getX(), heart == null ? 0 : heart.getZ(),
				maxDistance);
	}

	/**
	 * Incremental, nearest-first walk over the chunks of an area: each call returns the next chunk that is loaded,
	 * entity-ticking (a player is around) and not another guild's. Only loaded chunks are ever touched. A chunk whose
	 * reading stopped half-way (lists full) is resumed at the column where it stopped ({@link #resumeAt}).
	 */
	static final class Scan {
		private static final int MAX_SKIPS = 64;
		final WorkArea area;
		private final long[] chunks;
		private int next;
		private long resumeKey = Long.MIN_VALUE;
		private int resumeColumn;
		private int startColumn;
		private int read;
		private int unloaded;

		Scan(WorkArea area, BlockPos from) {
			this.area = area;
			this.chunks = ZoneMath.chunksNearestFirst(area.minX, area.minZ, area.maxX, area.maxZ, from.getX(), from.getZ());
		}

		boolean done() {
			return next >= chunks.length;
		}

		/** The last chunk returned will be returned again (its candidates did not all fit), read from its first column. */
		void retry() {
			if (next > 0) {
				next--;
			}
		}

		/** The last chunk returned will be returned again, read from {@code column} (0..255, x * 16 + z). */
		void resumeAt(int column) {
			if (next > 0) {
				next--;
				resumeKey = chunks[next];
				resumeColumn = column;
			}
		}

		/** First column (x * 16 + z) to read in the chunk returned by the last {@link #next} call. */
		int startColumn() {
			return startColumn;
		}

		/** Chunks of the area looked at so far, out of {@link #total()}. */
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

		/** Chunks skipped because nobody is around (not loaded or not entity-ticking). */
		int unloadedChunks() {
			return unloaded;
		}

		@Nullable LevelChunk next(WorkContext ctx) {
			int skips = 0;
			while (next < chunks.length && skips < MAX_SKIPS) {
				long key = chunks[next++];
				int cx = ChunkKey.x(key);
				int cz = ChunkKey.z(key);
				Guild owner = GuildManager.atChunk(key);
				if (owner != null && owner != ctx.guild) {
					skips++;
					continue;
				}
				LevelChunk chunk = ctx.level.getChunkSource().getChunkNow(cx, cz);
				boolean resumed = key == resumeKey;
				resumeKey = Long.MIN_VALUE;
				if (chunk != null && ctx.ticking(new BlockPos((cx << 4) + 8, area.refY, (cz << 4) + 8))) {
					if (resumed) {
						startColumn = resumeColumn;
					} else {
						startColumn = 0;
						read++;
					}
					return chunk;
				}
				unloaded++;
				skips++;
			}
			return null;
		}
	}
}
