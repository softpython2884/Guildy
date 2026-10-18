package dev.guildy.ruin;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.ruin.StatueRecord.Route;
import dev.guildy.ruin.StatueRecord.Status;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The jaguar statues of the trails to the grand temples: planning a trail (legend research, admin command), placing the
 * pending statues lazily when their chunks are loaded (one per tick, on natural ground near the planned point, never in a
 * guild's territory nor over a build, foundations down to the ground, overhanging vegetation cleared), their targets, their
 * protection and the indexes the guidance ({@link StatueGuide}) reads. Data in ruins.json ({@link RuinIndex}).
 */
public final class Statues {
	/** Statues and trails kept at most (abandoned, then the oldest pending statues are forgotten first). */
	static final int MAX_STATUES = 4096;
	static final int MAX_TRAILS = 1024;
	private static final int SCAN_TICKS = 40;
	private static final int CANDIDATE_STEP = 4;
	private static final int CHAIN_MAX = 64;
	/** Margin (blocks) kept from the box of a known ruin and from another statue. */
	private static final int RUIN_MARGIN = 8;
	private static final int STATUE_MARGIN = 4;

	private static final Map<Integer, StatueRecord> BY_ID = new HashMap<>();
	/** Dimension → chunk key → placed statues whose box touches the chunk (guidance, protection). */
	private static final Map<String, Map<Long, List<StatueRecord>>> PLACED = new HashMap<>();
	/** Dimension → chunk key of the planned point → pending statues. */
	private static final Map<String, Map<Long, List<StatueRecord>>> PENDING = new HashMap<>();
	private static final ArrayDeque<Integer> QUEUE = new ArrayDeque<>();
	private static final Set<Integer> QUEUED = new HashSet<>();
	private static final Map<String, Optional<StatueTemplate>> TEMPLATES = new HashMap<>();
	private static @Nullable Ground ground;
	private static long lastScan;
	private static int placedCount;

	private Statues() {
	}

	public static StatueSettings settings() {
		return RuinModule.config().cartography.statues;
	}

	static void init() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) ->
				Safe.run("ruin.statue.chunk", () -> onChunkLoad(level, chunk.getPos().x(), chunk.getPos().z())));
		Scheduler.every("ruin.statues", 1, 3, () -> {
			MinecraftServer server = GuildyServer.server();
			if (server != null) {
				tick(server);
			}
		});
		StatueGuide.init();
		Lifecycle.RELOADED.add(() -> {
			// ground blocks, template id and ground layer may have changed
			TEMPLATES.clear();
			ground = null;
		});
	}

	// ================================================================== indexes

	/** Rebuilds the indexes from the persisted statues (load, clear, GameTests). */
	static void reindex() {
		BY_ID.clear();
		PLACED.clear();
		PENDING.clear();
		QUEUE.clear();
		QUEUED.clear();
		TEMPLATES.clear();
		ground = null;
		placedCount = 0;
		for (StatueRecord statue : RuinIndex.statues()) {
			BY_ID.put(statue.id, statue);
			index(statue);
		}
	}

	private static void index(StatueRecord statue) {
		if (statue.status == Status.PLACED) {
			placedCount++;
			Map<Long, List<StatueRecord>> chunks = PLACED.computeIfAbsent(statue.dimension, k -> new HashMap<>());
			for (int cx = statue.minX >> 4; cx <= statue.maxX >> 4; cx++) {
				for (int cz = statue.minZ >> 4; cz <= statue.maxZ >> 4; cz++) {
					chunks.computeIfAbsent(ChunkKey.of(cx, cz), k -> new ArrayList<>(1)).add(statue);
				}
			}
		} else if (statue.status == Status.PENDING) {
			PENDING.computeIfAbsent(statue.dimension, k -> new HashMap<>())
					.computeIfAbsent(ChunkKey.ofBlock(statue.plannedX, statue.plannedZ), k -> new ArrayList<>(1)).add(statue);
		}
	}

	private static void unindexPending(StatueRecord statue) {
		Map<Long, List<StatueRecord>> chunks = PENDING.get(statue.dimension);
		if (chunks == null) {
			return;
		}
		long key = ChunkKey.ofBlock(statue.plannedX, statue.plannedZ);
		List<StatueRecord> list = chunks.get(key);
		if (list != null) {
			list.remove(statue);
			if (list.isEmpty()) {
				chunks.remove(key);
			}
		}
	}

	// ================================================================== queries

	public static List<StatueRecord> all() {
		return List.copyOf(RuinIndex.statues());
	}

	public static @Nullable StatueRecord get(int id) {
		return BY_ID.get(id);
	}

	public static @Nullable StatueTrail trail(int id) {
		for (StatueTrail trail : RuinIndex.trails()) {
			if (trail.id == id) {
				return trail;
			}
		}
		return null;
	}

	public static List<StatueTrail> trails() {
		return List.copyOf(RuinIndex.trails());
	}

	/** The statues of a trail, in order (missing ones skipped). */
	public static List<StatueRecord> statuesOf(StatueTrail trail) {
		List<StatueRecord> list = new ArrayList<>();
		for (int id : trail.statues) {
			StatueRecord statue = BY_ID.get(id);
			if (statue != null) {
				list.add(statue);
			}
		}
		return list;
	}

	static int placedCount() {
		return placedCount;
	}

	/** The placed statue of the dimension whose anchor is the nearest within {@code radius} blocks (horizontal), or null. */
	static @Nullable StatueRecord nearbyPlaced(ResourceKey<Level> dimension, double x, double y, double z, int radius) {
		Map<Long, List<StatueRecord>> chunks = PLACED.get(dimension.identifier().toString());
		if (chunks == null) {
			return null;
		}
		StatueRecord best = null;
		double bestDist = (double) radius * radius;
		int r = (radius >> 4) + 1;
		int pcx = (int) Math.floor(x) >> 4;
		int pcz = (int) Math.floor(z) >> 4;
		for (int cx = pcx - r; cx <= pcx + r; cx++) {
			for (int cz = pcz - r; cz <= pcz + r; cz++) {
				List<StatueRecord> list = chunks.get(ChunkKey.of(cx, cz));
				if (list == null) {
					continue;
				}
				for (StatueRecord statue : list) {
					double dx = statue.x + 0.5 - x;
					double dz = statue.z + 0.5 - z;
					double dist = dx * dx + dz * dz;
					if (dist <= bestDist && y > statue.minY - 24 && y < statue.maxY + 24) {
						bestDist = dist;
						best = statue;
					}
				}
			}
		}
		return best;
	}

	// ================================================================== planning

	/**
	 * Plans a trail of statues from {@code origin} to a temple: the waypoints of {@link StatueRules#waypoints}, each moved to
	 * dry, unclaimed land by sampling the terrain noise (no chunk is loaded), or an existing statue reused when one stands (or
	 * waits) close enough: one that already leads to this temple ends the planning (the new trail joins its chain), another
	 * one gets a route toward this temple. The new statues wait for their chunks to load.
	 */
	public static @Nullable StatueTrail createTrail(ServerLevel level, BlockPos origin, RuinRecord temple, @Nullable Guild guild) {
		StatueSettings cfg = settings();
		double[] aim = templeAim(level.getServer(), temple);
		long seed = temple.key.hashCode() * 31L + origin.getX() * 341_873_128_712L + origin.getZ() * 132_897_987_541L;
		List<StatueRules.Point> points = StatueRules.waypoints(origin.getX() + 0.5, origin.getZ() + 0.5, aim[0], aim[2], cfg.spacing(), seed);
		if (points.isEmpty()) {
			return null;
		}
		prune();
		String dimension = level.dimension().identifier().toString();
		StatueTrail trail = new StatueTrail();
		trail.id = RuinIndex.allocateTrailId();
		trail.temple = temple.key;
		trail.dimension = dimension;
		trail.guild = guild == null ? null : guild.id;
		trail.guildName = guild == null ? "" : guild.name;
		trail.originX = origin.getX();
		trail.originZ = origin.getZ();
		trail.createdAt = System.currentTimeMillis();
		StatueRecord previous = null;
		double previousDist = Math.hypot(aim[0] - origin.getX(), aim[2] - origin.getZ());
		boolean joined = false;
		List<StatueRecord> created = new ArrayList<>();
		for (StatueRules.Point point : points) {
			StatueRecord reused = reusable(dimension, point, trail, aim, previousDist);
			if (reused != null) {
				trail.statues.add(reused.id);
				link(previous, reused, temple.key);
				if (reused.route(temple.key) != null) {
					appendChain(trail, reused, temple.key);
					joined = true;
					break;
				}
				reused.routes.add(new Route(temple.key, trail.id));
				previous = reused;
				previousDist = Math.hypot(aim[0] - reused.posX(), aim[2] - reused.posZ());
				continue;
			}
			StatueRecord statue = new StatueRecord();
			statue.id = RuinIndex.allocateStatueId();
			statue.dimension = dimension;
			statue.template = cfg.template;
			int[] land = Safe.get("ruin.statue.plan", () -> dryLand(level, point.x(), point.z()),
					new int[] {point.x(), level.getSeaLevel(), point.z()});
			statue.plannedX = land[0];
			statue.plannedY = land[1];
			statue.plannedZ = land[2];
			statue.createdAt = System.currentTimeMillis();
			statue.routes.add(new Route(temple.key, trail.id));
			RuinIndex.statues().add(statue);
			BY_ID.put(statue.id, statue);
			index(statue);
			created.add(statue);
			link(previous, statue, temple.key);
			trail.statues.add(statue.id);
			previous = statue;
			previousDist = Math.hypot(aim[0] - statue.plannedX, aim[2] - statue.plannedZ);
		}
		if (!joined && previous != null) {
			Route route = previous.route(temple.key);
			if (route != null) {
				route.next = -1;
				route.toX = (int) Math.floor(aim[0]);
				route.toY = (int) Math.floor(aim[1]);
				route.toZ = (int) Math.floor(aim[2]);
			}
		}
		RuinIndex.trails().add(trail);
		while (RuinIndex.trails().size() > MAX_TRAILS) {
			RuinIndex.trails().removeFirst();
		}
		RuinIndex.markDirty();
		for (StatueRecord statue : created) {
			if (level.getChunkSource().getChunkNow(statue.plannedX >> 4, statue.plannedZ >> 4) != null) {
				enqueue(statue.id);
			}
		}
		Guildy.LOGGER.info("[ruins] statue trail #{} to {} from {} {}: {} statue(s) ({} new{})", trail.id, temple.key, origin.getX(),
				origin.getZ(), trail.statues.size(), created.size(), joined ? ", joins an existing trail" : "");
		return trail;
	}

	/** A statue of the dimension (not abandoned, not in the trail yet) close enough to a waypoint and closer to the temple. */
	private static @Nullable StatueRecord reusable(String dimension, StatueRules.Point point, StatueTrail trail, double[] aim, double previousDist) {
		int radius = settings().reuseRadius;
		if (radius <= 0) {
			return null;
		}
		List<StatueRules.Candidate> candidates = new ArrayList<>();
		for (StatueRecord statue : RuinIndex.statues()) {
			if (!dimension.equals(statue.dimension) || statue.status == Status.FAILED || trail.statues.contains(statue.id)) {
				continue;
			}
			double toTemple = Math.hypot(aim[0] - statue.posX(), aim[2] - statue.posZ());
			candidates.add(new StatueRules.Candidate(statue.id, statue.posX(), statue.posZ(), toTemple < previousDist));
		}
		int id = StatueRules.pickReuse(point.x(), point.z(), candidates, radius);
		return id < 0 ? null : BY_ID.get(id);
	}

	/** The route of {@code from} toward the temple now leads to {@code to}. */
	private static void link(@Nullable StatueRecord from, StatueRecord to, String temple) {
		if (from == null) {
			return;
		}
		Route route = from.route(temple);
		if (route != null) {
			route.next = to.id;
			double[] aim = to.aim();
			route.toX = (int) Math.floor(aim[0]);
			route.toY = (int) Math.floor(aim[1]);
			route.toZ = (int) Math.floor(aim[2]);
		}
	}

	/** Appends the statues that follow {@code start} toward the temple (its chain) to the trail. */
	private static void appendChain(StatueTrail trail, StatueRecord start, String temple) {
		Set<Integer> seen = new HashSet<>(trail.statues);
		StatueRecord current = start;
		for (int steps = 0; steps < CHAIN_MAX; steps++) {
			Route route = current.route(temple);
			if (route == null || route.next < 0) {
				return;
			}
			StatueRecord next = BY_ID.get(route.next);
			if (next == null || !seen.add(next.id)) {
				return;
			}
			trail.statues.add(next.id);
			current = next;
		}
	}

	/**
	 * Moves a waypoint to dry land: the planned point or the first point of rings around it (every 12 blocks up to 48) whose
	 * terrain noise has no water on five samples, a gentle slope, and no guild claim. No chunk is loaded or generated.
	 *
	 * @return x, estimated ground y, z
	 */
	static int[] dryLand(ServerLevel level, int x, int z) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		boolean claims = GuildyServer.isClaimLevel(level);
		int slope = settings().maxSlope + 3;
		int[] fallback = null;
		for (int ring = 0; ring <= 4; ring++) {
			int directions = ring == 0 ? 1 : 8;
			for (int d = 0; d < directions; d++) {
				double angle = Math.PI * 2 * d / directions + ring * 0.4;
				int px = x + (int) Math.round(Math.cos(angle) * ring * 12);
				int pz = z + (int) Math.round(Math.sin(angle) * ring * 12);
				if (claims && (GuildManager.isClaimed(ChunkKey.ofBlock(px, pz)) || GuildManager.isClaimed(ChunkKey.ofBlock(px + 8, pz + 8))
						|| GuildManager.isClaimed(ChunkKey.ofBlock(px - 8, pz - 8)))) {
					continue;
				}
				int lo = Integer.MAX_VALUE;
				int hi = Integer.MIN_VALUE;
				boolean dry = true;
				for (int[] s : new int[][] {{0, 0}, {6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
					int floor = generator.getBaseHeight(px + s[0], pz + s[1], Heightmap.Types.OCEAN_FLOOR_WG, level, random);
					int surface = generator.getBaseHeight(px + s[0], pz + s[1], Heightmap.Types.WORLD_SURFACE_WG, level, random);
					if (surface > floor) {
						dry = false;
						break;
					}
					lo = Math.min(lo, floor);
					hi = Math.max(hi, floor);
				}
				if (!dry) {
					continue;
				}
				if (hi - lo <= slope) {
					return new int[] {px, hi - 1, pz};
				}
				if (fallback == null) {
					fallback = new int[] {px, hi - 1, pz};
				}
			}
		}
		if (fallback != null) {
			return fallback;
		}
		return new int[] {x, generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1, z};
	}

	/** Forgets the oldest abandoned statues, then the oldest pending ones, beyond {@link #MAX_STATUES}. */
	private static void prune() {
		List<StatueRecord> statues = RuinIndex.statues();
		if (statues.size() < MAX_STATUES) {
			return;
		}
		statues.removeIf(s -> s.status == Status.FAILED);
		List<StatueRecord> pending = new ArrayList<>();
		for (StatueRecord statue : statues) {
			if (statue.status == Status.PENDING) {
				pending.add(statue);
			}
		}
		pending.sort(Comparator.comparingLong(s -> s.createdAt));
		int excess = statues.size() - MAX_STATUES + 64;
		for (int i = 0; i < excess && i < pending.size(); i++) {
			statues.remove(pending.get(i));
		}
		RuinIndex.markDirty();
		reindex();
	}

	// ================================================================== targets

	/** Where a guidance of this statue on this route points: the next live statue, else the temple. */
	static double[] target(@Nullable MinecraftServer server, StatueRecord statue, Route route) {
		int next = StatueRules.liveTarget(route.next, id -> {
			StatueRecord s = BY_ID.get(id);
			return s == null || s.status == Status.FAILED;
		}, id -> {
			StatueRecord s = BY_ID.get(id);
			Route r = s == null ? null : s.route(route.temple);
			return r == null ? -1 : r.next;
		}, CHAIN_MAX);
		double[] target;
		StatueRecord nextStatue = next < 0 ? null : BY_ID.get(next);
		if (nextStatue != null) {
			target = nextStatue.aim();
		} else {
			RuinRecord temple = RuinIndex.get(route.temple);
			target = temple != null ? templeAim(server, temple) : new double[] {route.toX + 0.5, route.toY + 0.5, route.toZ + 0.5};
		}
		int tx = (int) Math.floor(target[0]);
		int ty = (int) Math.floor(target[1]);
		int tz = (int) Math.floor(target[2]);
		if (tx != route.toX || ty != route.toY || tz != route.toZ) {
			route.toX = tx;
			route.toY = ty;
			route.toZ = tz;
			RuinIndex.markDirty();
		}
		return target;
	}

	/** True when this route leads straight to the temple (no live statue left after this one). */
	static boolean leadsToTemple(StatueRecord statue, Route route) {
		return StatueRules.liveTarget(route.next, id -> {
			StatueRecord s = BY_ID.get(id);
			return s == null || s.status == Status.FAILED;
		}, id -> {
			StatueRecord s = BY_ID.get(id);
			Route r = s == null ? null : s.route(route.temple);
			return r == null ? -1 : r.next;
		}, CHAIN_MAX) < 0;
	}

	/**
	 * The point of a temple the last statue looks at: its beacon when its chunks are loaded, else the guardian's cell, else the
	 * middle of its box.
	 */
	static double[] templeAim(@Nullable MinecraftServer server, RuinRecord temple) {
		ResourceKey<Level> dimension = RuinItems.dimension(temple);
		ServerLevel level = server == null || dimension == null ? null : server.getLevel(dimension);
		if (level != null) {
			List<Sanctuaries.Beacon> beacons = Safe.get("ruin.statue.beacon", () -> Sanctuaries.beacons(level, temple), List.of());
			if (!beacons.isEmpty()) {
				BlockPos beacon = beacons.getFirst().pos();
				return new double[] {beacon.getX() + 0.5, beacon.getY() + 0.5, beacon.getZ() + 0.5};
			}
		}
		if (temple.boss != null) {
			return new double[] {temple.boss.getX() + 0.5, temple.boss.getY() + 0.5, temple.boss.getZ() + 0.5};
		}
		BlockPos center = temple.center();
		return new double[] {center.getX() + 0.5, temple.maxY, center.getZ() + 0.5};
	}

	/** Quarter turns that face a statue toward the target of its first route. */
	static int facing(@Nullable MinecraftServer server, StatueRecord statue) {
		if (statue.routes.isEmpty()) {
			return 0;
		}
		double[] target = target(server, statue, statue.routes.getFirst());
		return StatueRules.quarterTurnsToward(statue.posX() + 0.5, statue.posZ() + 0.5, target[0], target[2]);
	}

	// ================================================================== placement

	static void onChunkLoad(ServerLevel level, int chunkX, int chunkZ) {
		Map<Long, List<StatueRecord>> chunks = PENDING.get(level.dimension().identifier().toString());
		if (chunks == null || chunks.isEmpty()) {
			return;
		}
		List<StatueRecord> list = chunks.get(ChunkKey.of(chunkX, chunkZ));
		if (list != null) {
			for (StatueRecord statue : list) {
				enqueue(statue.id);
			}
		}
	}

	/** Places the pending statue at the head of the queue (at most one per tick); every 2 s, queues those whose chunk is loaded. */
	static void tick(MinecraftServer server) {
		long now = Scheduler.ticks();
		if (now - lastScan >= SCAN_TICKS || now < lastScan) {
			lastScan = now;
			scanPending(server);
		}
		Integer id = QUEUE.poll();
		if (id == null) {
			return;
		}
		QUEUED.remove(id);
		StatueRecord statue = BY_ID.get(id);
		if (statue == null || statue.status != Status.PENDING) {
			return;
		}
		ServerLevel level = level(server, statue.dimension);
		if (level != null) {
			Safe.run("ruin.statue.place", () -> tryPlace(level, statue));
		}
	}

	private static void scanPending(MinecraftServer server) {
		for (Map.Entry<String, Map<Long, List<StatueRecord>>> entry : PENDING.entrySet()) {
			ServerLevel level = level(server, entry.getKey());
			if (level == null) {
				continue;
			}
			for (List<StatueRecord> list : entry.getValue().values()) {
				for (StatueRecord statue : list) {
					if (level.getChunkSource().getChunkNow(statue.plannedX >> 4, statue.plannedZ >> 4) != null) {
						enqueue(statue.id);
					}
				}
			}
		}
	}

	static void enqueue(int id) {
		if (QUEUED.add(id)) {
			QUEUE.add(id);
		}
	}

	/** Outcome of a placement attempt (GameTests). */
	public enum Outcome {
		PLACED, WAITING, NO_SITE, FAILED, NO_TEMPLATE
	}

	/**
	 * Tries to place a pending statue now: waits while the chunks around its planned point are not all loaded, then looks
	 * for a site within {@code siteRadius} (twice that on the second attempt; nothing then: the statue is abandoned).
	 */
	public static Outcome tryPlace(ServerLevel level, StatueRecord statue) {
		if (statue.status != Status.PENDING) {
			return statue.status == Status.PLACED ? Outcome.PLACED : Outcome.FAILED;
		}
		StatueTemplate template = template(level, statue.template);
		if (template == null) {
			return Outcome.NO_TEMPLATE;
		}
		StatueSettings cfg = settings();
		int turns = facing(level.getServer(), statue);
		StatueTemplate.Shape shape = template.shape(turns);
		int radius = statue.attempts == 0 ? cfg.siteRadius : cfg.siteRadius * 2;
		if (!areaLoaded(level, statue.plannedX, statue.plannedZ, radius + shape.span() + 2)) {
			return Outcome.WAITING;
		}
		Site site = findSite(level, statue, shape, radius);
		if (site == null) {
			statue.attempts++;
			RuinIndex.markDirty();
			if (statue.attempts >= 2) {
				statue.status = Status.FAILED;
				unindexPending(statue);
				Guildy.LOGGER.info("[ruins] statue #{} abandoned: no site around {} {}", statue.id, statue.plannedX, statue.plannedZ);
				return Outcome.FAILED;
			}
			return Outcome.NO_SITE;
		}
		place(level, statue, template, shape, turns, site);
		return Outcome.PLACED;
	}

	/** Every chunk within {@code reach} blocks of a point is loaded (nothing is loaded here). */
	static boolean areaLoaded(ServerLevel level, int x, int z, int reach) {
		for (int cx = (x - reach) >> 4; cx <= (x + reach) >> 4; cx++) {
			for (int cz = (z - reach) >> 4; cz <= (z + reach) >> 4; cz++) {
				if (level.getChunkSource().getChunkNow(cx, cz) == null) {
					return false;
				}
			}
		}
		return true;
	}

	/** A site: the anchor column, the ground layer height and the natural ground height of each column of the shape. */
	private record Site(int x, int z, int base, int[] tops) {
	}

	private static @Nullable Site findSite(ServerLevel level, StatueRecord statue, StatueTemplate.Shape shape, int radius) {
		List<int[]> offsets = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx += CANDIDATE_STEP) {
			for (int dz = -radius; dz <= radius; dz += CANDIDATE_STEP) {
				if (dx * dx + dz * dz <= radius * radius) {
					offsets.add(new int[] {dx, dz});
				}
			}
		}
		offsets.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1]).thenComparingInt(o -> o[0]).thenComparingInt(o -> o[1]));
		for (int[] offset : offsets) {
			Site site = checkSite(level, statue, shape, statue.plannedX + offset[0], statue.plannedZ + offset[1]);
			if (site != null) {
				return site;
			}
		}
		return null;
	}

	/**
	 * A site for the anchor at (x, z): no claimed chunk under the statue (one block of margin), away from the known ruins and
	 * the other statues, natural ground blocks (no fluid) under every column it stands on with a slope of at most
	 * {@code maxSlope}, and only air or vegetation above the ground up to two blocks over the statue.
	 */
	private static @Nullable Site checkSite(ServerLevel level, StatueRecord statue, StatueTemplate.Shape shape, int x, int z) {
		int minX = x + shape.minDx();
		int maxX = x + shape.maxDx();
		int minZ = z + shape.minDz();
		int maxZ = z + shape.maxDz();
		if (GuildyServer.isClaimLevel(level)) {
			for (int cx = (minX - 1) >> 4; cx <= (maxX + 1) >> 4; cx++) {
				for (int cz = (minZ - 1) >> 4; cz <= (maxZ + 1) >> 4; cz++) {
					if (GuildManager.isClaimed(ChunkKey.of(cx, cz))) {
						return null;
					}
				}
			}
		}
		String dimension = level.dimension().identifier().toString();
		for (RuinRecord ruin : RuinIndex.all()) {
			if (dimension.equals(ruin.dimension) && ruin.minX - RUIN_MARGIN <= maxX && ruin.maxX + RUIN_MARGIN >= minX
					&& ruin.minZ - RUIN_MARGIN <= maxZ && ruin.maxZ + RUIN_MARGIN >= minZ) {
				return null;
			}
		}
		Map<Long, List<StatueRecord>> placed = PLACED.get(dimension);
		if (placed != null) {
			for (int cx = (minX - STATUE_MARGIN) >> 4; cx <= (maxX + STATUE_MARGIN) >> 4; cx++) {
				for (int cz = (minZ - STATUE_MARGIN) >> 4; cz <= (maxZ + STATUE_MARGIN) >> 4; cz++) {
					List<StatueRecord> list = placed.get(ChunkKey.of(cx, cz));
					if (list == null) {
						continue;
					}
					for (StatueRecord other : list) {
						if (other.minX - STATUE_MARGIN <= maxX && other.maxX + STATUE_MARGIN >= minX && other.minZ - STATUE_MARGIN <= maxZ
								&& other.maxZ + STATUE_MARGIN >= minZ) {
							return null;
						}
					}
				}
			}
		}
		Ground groundBlocks = ground();
		List<int[]> columns = shape.columns();
		int[] tops = new int[columns.size()];
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		int slope = settings().maxSlope;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < columns.size(); i++) {
			int[] column = columns.get(i);
			if (column[2] > 0) {
				tops[i] = Integer.MIN_VALUE;
				continue;
			}
			int wx = x + column[0];
			int wz = z + column[1];
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
			BlockState state = level.getBlockState(pos.set(wx, top, wz));
			if (!groundBlocks.matches(state)) {
				return null;
			}
			tops[i] = top;
			lowest = Math.min(lowest, top);
			highest = Math.max(highest, top);
			if (highest - lowest > slope) {
				return null;
			}
		}
		if (highest == Integer.MIN_VALUE) {
			return null;
		}
		int base = highest;
		int ceiling = base + shape.top() + 2;
		if (base - slope - 2 <= level.getMinY() || ceiling >= level.getMaxY()) {
			return null;
		}
		for (var player : level.players()) {
			// never built around a player who happens to stand there
			if (player.getX() >= minX - 1 && player.getX() <= maxX + 2 && player.getZ() >= minZ - 1 && player.getZ() <= maxZ + 2
					&& player.getY() >= base - slope - 2 && player.getY() <= ceiling) {
				return null;
			}
		}
		for (int i = 0; i < columns.size(); i++) {
			int[] column = columns.get(i);
			int from = column[2] > 0 ? base + column[2] : tops[i] + 1;
			for (int y = from; y <= ceiling - 1; y++) {
				if (!isVegetation(level.getBlockState(pos.set(x + column[0], y, z + column[1])))) {
					return null;
				}
			}
		}
		return new Site(x, z, base, tops);
	}

	private static void place(ServerLevel level, StatueRecord statue, StatueTemplate template, StatueTemplate.Shape shape, int turns, Site site) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<int[]> columns = shape.columns();
		int ceiling = site.base() + shape.top() + 2;
		// Overhanging vegetation over the statue goes (never a log nor a build: checked with the site).
		for (int i = 0; i < columns.size(); i++) {
			int[] column = columns.get(i);
			int from = column[2] > 0 ? site.base() + column[2] : site.tops()[i] + 1;
			for (int y = from; y <= ceiling; y++) {
				pos.set(site.x() + column[0], y, site.z() + column[1]);
				BlockState state = level.getBlockState(pos);
				if (!state.isAir() && isVegetation(state)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		// Foundations under the columns it stands on, down to the natural ground.
		BlockState foundation = foundation();
		for (int i = 0; i < columns.size(); i++) {
			int[] column = columns.get(i);
			if (column[2] > 0) {
				continue;
			}
			for (int y = site.base() + column[2] - 1; y > site.tops()[i]; y--) {
				level.setBlock(pos.set(site.x() + column[0], y, site.z() + column[1]), foundation, Block.UPDATE_CLIENTS);
			}
		}
		BlockPos anchor = new BlockPos(site.x(), site.base(), site.z());
		BlockPos origin = template.origin(anchor);
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setRotation(StatueTemplate.rotation(turns))
				.setRotationPivot(template.pivot)
				.setIgnoreEntities(true)
				.setKnownShape(true)
				.setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
		template.template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
		BoundingBox box = template.template.getBoundingBox(settings, origin);
		unindexPending(statue);
		statue.status = Status.PLACED;
		statue.x = anchor.getX();
		statue.y = anchor.getY();
		statue.z = anchor.getZ();
		statue.rotation = Math.floorMod(turns, 4);
		statue.minX = box.minX();
		statue.minY = box.minY();
		statue.minZ = box.minZ();
		statue.maxX = box.maxX();
		statue.maxY = box.maxY();
		statue.maxZ = box.maxZ();
		statue.eyes = new ArrayList<>();
		for (int[] eye : shape.eyes()) {
			statue.eyes.add(anchor.offset(eye[0], eye[1], eye[2]));
		}
		statue.placedAt = System.currentTimeMillis();
		index(statue);
		// The routes that lead to this statue now aim at its eyes.
		for (StatueRecord other : RuinIndex.statues()) {
			for (Route route : other.routes) {
				if (route.next == statue.id) {
					double[] aim = statue.aim();
					route.toX = (int) Math.floor(aim[0]);
					route.toY = (int) Math.floor(aim[1]);
					route.toZ = (int) Math.floor(aim[2]);
				}
			}
		}
		RuinIndex.markDirty();
		Guildy.LOGGER.info("[ruins] jaguar statue #{} placed at {} {} {} (planned {} {}, facing {})", statue.id, anchor.getX(), anchor.getY(),
				anchor.getZ(), statue.plannedX, statue.plannedZ, StatueRules.COMPASS[statue.rotation * 2]);
	}

	// ================================================================== blocks

	/** Air, leaves, plants, vines, snow layers, bamboo... (what the placement may clear), without any fluid. */
	static boolean isVegetation(BlockState state) {
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.REPLACEABLE)
				|| state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.CAVE_VINES) || state.is(Blocks.BAMBOO)
				|| state.is(Blocks.BAMBOO_SAPLING) || state.is(Blocks.COCOA) || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.SUGAR_CANE)
				|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
				|| state.is(Blocks.COBWEB) || state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT)
				|| state.is(Blocks.SPORE_BLOSSOM) || state.is(Blocks.PINK_PETALS);
	}

	private static BlockState foundation() {
		Identifier id = Identifier.tryParse(settings().foundationBlock);
		Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
		return block == null || block == Blocks.AIR ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : block.defaultBlockState();
	}

	/** The natural ground blocks of the config (blocks and tags; unknown ids ignored), resolved once per config. */
	private static final class Ground {
		final List<String> source;
		final Set<Block> blocks = new HashSet<>();
		final List<TagKey<Block>> tags = new ArrayList<>();

		Ground(List<String> source) {
			this.source = source;
			for (String entry : source) {
				if (entry.startsWith("#")) {
					Identifier id = Identifier.tryParse(entry.substring(1));
					if (id != null) {
						tags.add(TagKey.create(Registries.BLOCK, id));
					}
				} else {
					Identifier id = Identifier.tryParse(entry);
					BuiltInRegistries.BLOCK.getOptional(id == null ? Identifier.withDefaultNamespace("air") : id)
							.filter(b -> b != Blocks.AIR).ifPresent(blocks::add);
				}
			}
		}

		boolean matches(BlockState state) {
			if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
				return false;
			}
			if (blocks.contains(state.getBlock())) {
				return true;
			}
			for (TagKey<Block> tag : tags) {
				if (state.is(tag)) {
					return true;
				}
			}
			return false;
		}
	}

	private static Ground ground() {
		List<String> source = settings().groundBlocks;
		Ground current = ground;
		if (current == null || current.source != source) {
			current = new Ground(source);
			ground = current;
		}
		return current;
	}

	static @Nullable StatueTemplate template(ServerLevel level, String id) {
		Optional<StatueTemplate> cached = TEMPLATES.get(id);
		if (cached == null) {
			Identifier identifier = Identifier.tryParse(id);
			Optional<StructureTemplate> found = identifier == null ? Optional.empty() : level.getStructureTemplateManager().get(identifier);
			cached = found.map(t -> new StatueTemplate(identifier, t, settings().groundLayer));
			if (cached.isEmpty()) {
				Guildy.LOGGER.warn("[ruins] statue template {} not found: the statues wait", id);
			}
			TEMPLATES.put(id, cached);
		}
		return cached.orElse(null);
	}

	private static @Nullable ServerLevel level(MinecraftServer server, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	// ================================================================== protection

	/** A block of a placed statue (the cells of its template, rotated), when the statues are protected. */
	public static boolean protects(Level level, BlockPos pos) {
		if (placedCount == 0 || !(level instanceof ServerLevel server) || !settings().protect) {
			return false;
		}
		Map<Long, List<StatueRecord>> chunks = PLACED.get(level.dimension().identifier().toString());
		List<StatueRecord> list = chunks == null ? null : chunks.get(ChunkKey.ofBlock(pos.getX(), pos.getZ()));
		if (list == null) {
			return false;
		}
		for (StatueRecord statue : list) {
			if (!statue.contains(pos.getX(), pos.getY(), pos.getZ(), 0)) {
				continue;
			}
			StatueTemplate template = template(server, statue.template);
			if (template != null && template.shape(statue.rotation).hasCell(pos.getX() - statue.x, pos.getY() - statue.y, pos.getZ() - statue.z)) {
				return true;
			}
		}
		return false;
	}

	/** Removes the protected statue blocks an explosion would destroy. */
	static void filterExplosion(Level level, List<BlockPos> positions) {
		if (placedCount == 0 || positions.isEmpty() || !settings().protect) {
			return;
		}
		Map<Long, List<StatueRecord>> chunks = PLACED.get(level.dimension().identifier().toString());
		if (chunks == null || chunks.isEmpty()) {
			return;
		}
		Set<Long> touched = new LinkedHashSet<>();
		for (BlockPos pos : positions) {
			touched.add(ChunkKey.ofBlock(pos.getX(), pos.getZ()));
		}
		boolean any = false;
		for (long key : touched) {
			if (chunks.containsKey(key)) {
				any = true;
				break;
			}
		}
		if (any) {
			positions.removeIf(pos -> protects(level, pos));
		}
	}
}
