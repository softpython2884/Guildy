package dev.guildy.villager;

import dev.guildy.claim.ClaimGuard;
import dev.guildy.claim.ClaimRules;
import dev.guildy.core.GuildyServer;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * World side of the gathering pits ({@link PitRules}, {@link PitPlan}, persisted {@link SupplyPit}): the grid seen by
 * the rules (the real miners' block classes of {@link MineWorld}, heights from the loaded chunks only, the columns a pit
 * may use), the search of a site for a kind of block and the places a pit may go:
 * <ul>
 * <li><b>Outside the territory</b>: within {@code gathering.radius} blocks of the heart, never in a claimed chunk (of any
 * guild, its own included), never near the world spawn ({@code territory.minDistanceFromSpawn} chunks, and the vanilla
 * spawn protection of a dedicated server), only in entity-ticking chunks.</li>
 * <li><b>In a real mine zone</b> of the guild (not a quarry: its benches take the whole square): inside the zone square,
 * at least {@code gathering.mineMargin} blocks from every column of the staircase, of the fanion and of the galleries of
 * the levels the mine plans around the pit's heights (a private copy of the mine's {@link MineLayout}, never the shared
 * one), so a pit never touches the mine's structure nor the miners' leased work.</li>
 * </ul>
 * Pits are placed on a grid ({@code pitSide + pitGap}): side by side, never overlapping, tidy rows. A pit already
 * started that still holds the wanted blocks is always continued first.
 */
final class SupplyPits {
	/** Excluded columns of a real mine, per zone and layout identity (small cache). */
	private static final Map<String, LongOpenHashSet> MINE_EXCLUSIONS = new LinkedHashMap<>(8, 0.75f, true);
	private static final int MAX_EXCLUSIONS = 32;
	/** Probes of sites (five columns) worth one full site check. */
	private static final int PROBES_PER_SITE = 8;

	private SupplyPits() {
	}

	// ================================================================ places

	/** The guild has a real (non-quarry) mine zone whose staircase is planned. */
	static boolean hasMineZone(WorkContext ctx) {
		for (WorkZone zone : ctx.guild.villagers.zones) {
			if (minePitZone(zone, ctx.config)) {
				return true;
			}
		}
		return false;
	}

	static boolean minePitZone(WorkZone zone, VillagerConfig config) {
		return zone.type == ZoneType.MINE && zone.pos != null && zone.isRealMine() && !Mines.quarry(zone, config.realMining)
				&& zone.mine().orientation >= 0;
	}

	/** Columns of a real mine a pit keeps away from (dilated by {@code gathering.mineMargin}), around heights lo..hi. */
	static LongOpenHashSet mineExclusion(ServerLevel level, WorkZone zone, VillagerConfig config, int lo, int hi) {
		VillagerConfig.RealMining real = config.realMining;
		MineState state = zone.mine();
		int margin = Supply.config().gathering.mineMargin;
		MineRules.LevelPlan plan = Mines.levels(level, zone, state, real);
		int spacing = Mines.spacing(zone, real);
		List<Integer> heights = new ArrayList<>();
		for (int y : plan.levels()) {
			if (y >= lo - MineLayout.MAX_CELL_HEIGHT && y <= hi + 1) {
				heights.add(y);
			}
		}
		for (int y = plan.gridBottom() - spacing; y >= lo - MineLayout.MAX_CELL_HEIGHT; y -= Math.max(1, spacing)) {
			heights.add(y);
		}
		String key = zone.id + "|" + zone.settingsKey() + "|" + state.orientation + "|" + state.stairWidth + "|" + margin + "|" + heights;
		LongOpenHashSet cached = MINE_EXCLUSIONS.get(key);
		if (cached != null) {
			return cached;
		}
		MineLayout layout = MineLayout.build(zone.pos, state.orientation, MineRules.minLevel(level.getMinY()), zone.bounds(),
				zone.objective == ZoneObjective.STONE, Mines.settings(real, state.stairWidth));
		LongOpenHashSet columns = new LongOpenHashSet();
		int[] bounds = zone.bounds();
		for (int x = bounds[0]; x <= bounds[2]; x++) {
			for (int z = bounds[1]; z <= bounds[3]; z++) {
				if (layout.inStairColumns(x, z)) {
					dilate(columns, x, z, margin);
				}
			}
		}
		dilate(columns, zone.pos.getX(), zone.pos.getZ(), margin + 1);
		for (int y : heights) {
			MineLayout.Level built = layout.level(y);
			if (built == null) {
				continue;
			}
			for (MineLayout.Segment segment : built.segments()) {
				for (MineLayout.Cell cell : segment.cells()) {
					dilate(columns, cell.feet().getX(), cell.feet().getZ(), margin);
				}
			}
		}
		if (MINE_EXCLUSIONS.size() >= MAX_EXCLUSIONS) {
			Iterator<String> it = MINE_EXCLUSIONS.keySet().iterator();
			it.next();
			it.remove();
		}
		MINE_EXCLUSIONS.put(key, columns);
		return columns;
	}

	private static void dilate(LongOpenHashSet columns, int x, int z, int margin) {
		for (int dx = -margin; dx <= margin; dx++) {
			for (int dz = -margin; dz <= margin; dz++) {
				columns.add(BlockPos.asLong(x + dx, 0, z + dz));
			}
		}
	}

	static void clear() {
		MINE_EXCLUSIONS.clear();
	}

	/** True when the column is near the world spawn (Guildy's distance in chunks, vanilla spawn protection). */
	static boolean nearSpawn(ServerLevel level, int x, int z) {
		int chunks = GuildyServer.config().territory.minDistanceFromSpawn;
		if (ClaimRules.nearSpawn(List.of(ChunkKey.ofBlock(x, z)), ClaimGuard.spawnChunk(), chunks)) {
			return true;
		}
		if (level.getServer() instanceof DedicatedServer dedicated && level.dimension() == level.getServer().getRespawnData().dimension()) {
			int radius = dedicated.spawnProtectionRadius();
			BlockPos spawn = level.getServer().getRespawnData().pos();
			return radius > 0 && Math.max(Math.abs(x - spawn.getX()), Math.abs(z - spawn.getZ())) <= radius;
		}
		return false;
	}

	// ================================================================ grid

	/** What a pit sees of the world (loaded, entity-ticking chunks only), for one kind of wanted block. */
	static final class World implements PitRules.Grid {
		final ServerLevel level;
		final Guild guild;
		final MineWorld mine;
		private final Set<Block> sources;
		/** Null: outside the territory; else the mine zone. */
		final @Nullable WorkZone zone;
		private final @Nullable LongOpenHashSet excluded;
		private final int @Nullable [] bounds;
		private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		private final Map<Long, Boolean> claimed = new HashMap<>();

		World(ServerLevel level, Guild guild, Set<Block> sources, @Nullable WorkZone zone, @Nullable LongOpenHashSet excluded) {
			this.level = level;
			this.guild = guild;
			this.mine = new MineWorld(level, guild);
			this.sources = sources;
			this.zone = zone;
			this.excluded = excluded;
			this.bounds = zone == null ? null : zone.bounds();
		}

		@Override
		public MineBlock at(int x, int y, int z) {
			if (!ticking(x, z)) {
				return MineBlock.UNLOADED;
			}
			return mine.at(x, y, z);
		}

		@Override
		public boolean foreign(int x, int y, int z) {
			return mine.foreign(x, y, z);
		}

		@Override
		public boolean sturdy(int x, int y, int z) {
			return mine.sturdy(x, y, z);
		}

		@Override
		public int surface(int x, int z) {
			if (!ticking(x, z)) {
				return Integer.MIN_VALUE;
			}
			LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
			if (chunk == null) {
				return Integer.MIN_VALUE;
			}
			return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		}

		@Override
		public boolean allowed(int x, int z) {
			if (zone != null) {
				int[] b = bounds;
				if (b == null || x < b[0] - 1 || x > b[2] + 1 || z < b[1] - 1 || z > b[3] + 1) {
					return false;
				}
				if (excluded != null && excluded.contains(BlockPos.asLong(x, 0, z))) {
					return false;
				}
				Guild owner = GuildManager.at(level, cursor.set(x, 64, z));
				return owner == null || owner == guild;
			}
			long chunk = ChunkKey.ofBlock(x, z);
			Boolean known = claimed.get(chunk);
			if (known == null) {
				known = GuildManager.atChunk(chunk) != null || nearSpawn(level, x, z);
				claimed.put(chunk, known);
			}
			return !known;
		}

		@Override
		public boolean source(int x, int y, int z) {
			cursor.set(x, y, z);
			return level.isLoaded(cursor) && sources.contains(level.getBlockState(cursor).getBlock());
		}

		boolean ticking(int x, int z) {
			cursor.set(x, 64, z);
			return level.isLoaded(cursor) && level.isPositionEntityTicking(cursor);
		}

		String place() {
			WorkZone current = zone;
			return current == null ? dev.guildy.core.Lang.raw("supply.place.outside") : dev.guildy.core.Lang.raw("supply.place.zone", current.name);
		}
	}

	/** Blocks of the registry named in a set of block ids. */
	static Set<Block> blocks(Set<String> ids) {
		Set<Block> blocks = new HashSet<>();
		for (String id : ids) {
			try {
				Identifier key = Identifier.parse(id);
				if (BuiltInRegistries.BLOCK.containsKey(key)) {
					blocks.add(BuiltInRegistries.BLOCK.getValue(key));
				}
			} catch (RuntimeException ignored) {
				// unknown id
			}
		}
		return blocks;
	}

	/** World of a pit already planned (its zone, or outside). Null when its zone is gone or no longer a real mine. */
	static @Nullable World world(WorkContext ctx, SupplyPit pit, Set<Block> sources) {
		if (pit.zoneId < 0) {
			return new World(ctx.level, ctx.guild, sources, null, null);
		}
		WorkZone zone = ctx.guild.villagers.zone(pit.zoneId);
		if (zone == null || !minePitZone(zone, ctx.config)) {
			return null;
		}
		LongOpenHashSet excluded = mineExclusion(ctx.level, zone, ctx.config, pit.top - pit.depth - 1, pit.highest + 3);
		return new World(ctx.level, ctx.guild, sources, zone, excluded);
	}

	// ================================================================ survey

	/** A site found by the survey: the pit to dig (new or continued) and the blocks of the wanted kind it holds. */
	record Found(SupplyPit pit, World world, int score) {
	}

	/**
	 * Incremental search of a pit for a kind of block: first the guild's pits already started (the best one that still
	 * holds the blocks), then the sites of its real mine zones, then the sites outside the territory, nearest to the heart
	 * first. A few sites per step; the best scoring site wins (enough blocks for the trip, then the nearest).
	 */
	static final class Survey {
		private final Set<Block> sources;
		private final int wanted;
		private final List<Candidate> candidates = new ArrayList<>();
		private int next;
		private int seen;
		private @Nullable Found best;
		private boolean prepared;

		/** A site to check: corner, zone (null = outside). */
		private record Candidate(int x0, int z0, @Nullable WorkZone zone, @Nullable SupplyPit pit) {
		}

		Survey(Set<Block> sources, int wanted) {
			this.sources = sources;
			this.wanted = Math.max(1, wanted);
		}

		int seen() {
			return seen;
		}

		/** One step: null while searching; then the site found (empty when there is none). */
		java.util.@Nullable Optional<Found> step(WorkContext ctx) {
			SupplyConfig.Gathering cfg = Supply.config().gathering;
			if (!prepared) {
				prepare(ctx, cfg);
				prepared = true;
			}
			// A full check costs PROBES_PER_SITE probes: sites without any wanted block are skipped quickly.
			int budget = cfg.sitesPerStep * PROBES_PER_SITE;
			while (budget > 0 && next < candidates.size()) {
				Candidate candidate = candidates.get(next++);
				budget -= check(ctx, cfg, candidate) ? PROBES_PER_SITE : 1;
				Found current = best;
				if (current != null && current.score() >= wantedBlocks() && (candidate.pit() != null || candidate.zone() != null || seen > 4)) {
					// Enough for the trip: an existing pit or a mine zone are preferred, else the nearest good site.
					return finish(ctx);
				}
			}
			if (next >= candidates.size()) {
				return finish(ctx);
			}
			return null;
		}

		private int wantedBlocks() {
			return Math.max(Supply.config().gathering.minSiteBlocks, Math.min(wanted, 64));
		}

		private java.util.Optional<Found> finish(WorkContext ctx) {
			Found found = best;
			if (found == null) {
				return java.util.Optional.empty();
			}
			SupplyPit pit = found.pit();
			if (pit.id == 0) {
				// A new pit: registered now.
				GuildVillagerData data = ctx.guild.villagers;
				pit.id = data.nextPitId++;
				data.pits.add(pit);
				trim(data, Supply.config().gathering.maxPits);
				GuildManager.markDirty();
			}
			return java.util.Optional.of(found);
		}

		private void prepare(WorkContext ctx, SupplyConfig.Gathering cfg) {
			GuildVillagerData data = ctx.guild.villagers;
			SupplyGuild shared = SupplyGuild.of(ctx.guild);
			// 1. pits already started
			for (SupplyPit pit : data.pits) {
				if (!pit.finished && !shared.pitTaken(pit.id, ctx.record.uuid, ctx.now)) {
					WorkZone zone = pit.zoneId < 0 ? null : data.zone(pit.zoneId);
					if (pit.zoneId < 0 ? cfg.outside : zone != null && cfg.mineZones) {
						candidates.add(new Candidate(pit.x, pit.z, zone, pit));
					}
				}
			}
			int spacing = cfg.pitSide + cfg.pitGap;
			// 2. real mine zones
			if (cfg.mineZones) {
				for (WorkZone zone : data.zones) {
					if (!minePitZone(zone, ctx.config)) {
						continue;
					}
					int[] b = zone.bounds();
					List<Candidate> inZone = new ArrayList<>();
					for (int x = Math.floorDiv(b[0], spacing) * spacing; x + cfg.pitSide - 1 <= b[2]; x += spacing) {
						for (int z = Math.floorDiv(b[1], spacing) * spacing; z + cfg.pitSide - 1 <= b[3]; z += spacing) {
							if (x >= b[0] && z >= b[1]) {
								inZone.add(new Candidate(x, z, zone, null));
							}
						}
					}
					BlockPos from = zone.pos;
					inZone.sort((p, q) -> Double.compare(dist(p, from, cfg.pitSide), dist(q, from, cfg.pitSide)));
					candidates.addAll(inZone);
				}
			}
			// 3. outside the territory, nearest to the heart first
			BlockPos heart = ctx.guild.heart;
			if (cfg.outside && heart != null) {
				int r = cfg.radius;
				List<Candidate> outside = new ArrayList<>();
				for (int x = Math.floorDiv(heart.getX() - r, spacing) * spacing; x <= heart.getX() + r; x += spacing) {
					for (int z = Math.floorDiv(heart.getZ() - r, spacing) * spacing; z <= heart.getZ() + r; z += spacing) {
						Candidate candidate = new Candidate(x, z, null, null);
						if (dist(candidate, heart, cfg.pitSide) <= (double) r * r) {
							outside.add(candidate);
						}
					}
				}
				outside.sort((p, q) -> Double.compare(dist(p, heart, cfg.pitSide), dist(q, heart, cfg.pitSide)));
				candidates.addAll(outside);
			}
		}

		private static double dist(Candidate c, BlockPos from, int side) {
			double dx = c.x0() + side / 2.0 - from.getX();
			double dz = c.z0() + side / 2.0 - from.getZ();
			return dx * dx + dz * dz;
		}

		/** Checks a site; false when it was skipped cheaply (overlap, nothing wanted in its probe columns). */
		private boolean check(WorkContext ctx, SupplyConfig.Gathering cfg, Candidate candidate) {
			seen++;
			SupplyPit existing = candidate.pit();
			if (existing != null) {
				World world = world(ctx, existing, sources);
				if (world == null) {
					return false;
				}
				int score = PitRules.count(world, existing.plan(), existing.levelled ? existing.top : existing.highest, existing.levels);
				offer(new Found(existing, world, score));
				return true;
			}
			for (SupplyPit pit : ctx.guild.villagers.pits) {
				if (pit.overlaps(candidate.x0(), candidate.z0(), cfg.pitSide, cfg.pitGap)) {
					return false;
				}
			}
			WorkZone zone = candidate.zone();
			World world;
			if (zone == null) {
				world = new World(ctx.level, ctx.guild, sources, null, null);
			} else {
				int ground = zone.pos.getY();
				LongOpenHashSet excluded = mineExclusion(ctx.level, zone, ctx.config, ground - cfg.pitMaxDepth - 8, ground + 8);
				world = new World(ctx.level, ctx.guild, sources, zone, excluded);
			}
			if (!probe(world, candidate.x0(), candidate.z0(), cfg.pitSide, cfg.pitMaxDepth)) {
				return false;
			}
			BlockPos from = zone != null ? zone.pos : ctx.guild.heart != null ? ctx.guild.heart : ctx.villager.blockPosition();
			PitRules.Site site = PitRules.evaluate(world, candidate.x0(), candidate.z0(), cfg.pitSide, cfg.pitMaxDepth, cfg.pitMaxUneven,
					from.getX(), from.getZ());
			if (!site.ok() || site.score() < cfg.minSiteBlocks) {
				return true;
			}
			int highest = site.top();
			for (int x = site.x0(); x < site.x0() + site.side(); x++) {
				for (int z = site.z0(); z < site.z0() + site.side(); z++) {
					highest = Math.max(highest, world.surface(x, z));
				}
			}
			SupplyPit pit = new SupplyPit(0, site, highest, zone == null ? -1 : zone.id, System.currentTimeMillis());
			offer(new Found(pit, world, site.score()));
			return true;
		}

		/**
		 * Any wanted block in five columns of the site (centre and quarters) from the ground down to the pit's depth: layers
		 * (sand, stone, clay) always show there; a site without any is not worth a full check.
		 */
		private static boolean probe(World world, int x0, int z0, int side, int depth) {
			int q = Math.max(1, side / 4);
			int[][] columns = {{side / 2, side / 2}, {q, q}, {side - 1 - q, q}, {q, side - 1 - q}, {side - 1 - q, side - 1 - q}};
			for (int[] column : columns) {
				int x = x0 + column[0];
				int z = z0 + column[1];
				int top = world.surface(x, z);
				if (top == Integer.MIN_VALUE) {
					continue;
				}
				for (int y = top; y >= top - depth; y--) {
					if (world.source(x, y, z)) {
						return true;
					}
				}
			}
			return false;
		}

		private void offer(Found found) {
			if (found.score() <= 0) {
				return;
			}
			Found current = best;
			int need = wantedBlocks();
			if (current == null || Math.min(found.score(), need) > Math.min(current.score(), need)) {
				best = found;
			}
		}
	}

	/** Keeps at most {@code max} pits: the oldest finished ones are forgotten first. */
	static void trim(GuildVillagerData data, int max) {
		while (data.pits.size() > max) {
			SupplyPit oldest = null;
			for (SupplyPit pit : data.pits) {
				if (pit.finished && (oldest == null || pit.usedAt < oldest.usedAt)) {
					oldest = pit;
				}
			}
			if (oldest == null) {
				oldest = data.pits.getFirst();
			}
			data.pits.remove(oldest);
		}
	}
}
