package dev.guildy.ruin;

import dev.guildy.Guildy;
import dev.guildy.core.Safe;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ruins known by the server: registered from their data markers (generation), from structure starts of the ruin tags
 * found in loaded chunks, and from the cartographers' locates. Server thread only (the generation threads only feed the
 * {@link MarkerCapture} queues, drained every tick with a bounded budget).
 */
public final class RuinIndex {
	/** All ruins (data pack tag, the builder fills it). */
	public static final TagKey<Structure> TAG_ALL = tag("ruins");
	public static final TagKey<Structure> TAG_OVERWORLD = tag("ruins/overworld");
	public static final TagKey<Structure> TAG_NETHER = tag("ruins/nether");
	private static final int EVENTS_PER_TICK = 256;
	private static final int MAX_ORPHANS = 4096;
	private static final long ORPHAN_TTL_MS = 30L * 24 * 3600 * 1000;

	private static RuinStore.Root root = new RuinStore.Root();
	private static final Map<String, RuinRecord> BY_KEY = new LinkedHashMap<>();
	private static boolean dirty;

	private RuinIndex() {
	}

	private static TagKey<Structure> tag(String path) {
		return TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("guildy", path));
	}

	// ================================================================== lifecycle

	static void load(MinecraftServer server) {
		root = RuinStore.load(server);
		BY_KEY.clear();
		for (RuinRecord record : root.ruins) {
			BY_KEY.put(record.key, record);
		}
		// Guild data of guilds that no longer exist (disbanded while the module was absent, test worlds...).
		root.guilds.keySet().removeIf(id -> {
			try {
				return GuildManager.get(UUID.fromString(id)) == null;
			} catch (IllegalArgumentException e) {
				return true;
			}
		});
		long now = System.currentTimeMillis();
		root.orphans.removeIf(o -> now - o.createdAt > ORPHAN_TTL_MS);
		Statues.reindex();
		dirty = false;
		Guildy.LOGGER.info("[ruins] {} known ruin(s), {} jaguar statue(s) loaded", BY_KEY.size(), root.statues.size());
	}

	static void save(MinecraftServer server, boolean force) {
		if (!dirty && !force) {
			return;
		}
		try {
			root.ruins = new ArrayList<>(BY_KEY.values());
			RuinStore.save(server, root);
			dirty = false;
		} catch (Exception e) {
			Guildy.LOGGER.error("Could not save ruins.json", e);
		}
	}

	static void clear() {
		root = new RuinStore.Root();
		BY_KEY.clear();
		MarkerCapture.MARKERS.clear();
		MarkerCapture.STARTS.clear();
		Statues.reindex();
		dirty = false;
	}

	// ================================================================== statues of the trails to the grand temples

	/** Every statue (mutable: {@link Statues} keeps its indexes in step). */
	static List<StatueRecord> statues() {
		return root.statues;
	}

	static List<StatueTrail> trails() {
		return root.trails;
	}

	static int allocateStatueId() {
		dirty = true;
		return root.nextStatueId++;
	}

	static int allocateTrailId() {
		dirty = true;
		return root.nextTrailId++;
	}

	public static void markDirty() {
		dirty = true;
	}

	// ================================================================== queries

	public static Collection<RuinRecord> all() {
		return Collections.unmodifiableCollection(BY_KEY.values());
	}

	public static @Nullable RuinRecord get(@Nullable String key) {
		return key == null ? null : BY_KEY.get(key);
	}

	/** First ruin of the dimension whose box, inflated by {@code margin}, contains the position. */
	public static @Nullable RuinRecord containing(ResourceKey<Level> dimension, double x, double y, double z, int margin) {
		String dim = dimension.identifier().toString();
		for (RuinRecord record : BY_KEY.values()) {
			if (dim.equals(record.dimension) && record.contains(x, y, z, margin)) {
				return record;
			}
		}
		return null;
	}

	/** Nearest ruin of the dimension (horizontal distance to its box), optionally of one ruin id. */
	public static @Nullable RuinRecord nearest(ResourceKey<Level> dimension, double x, double z, @Nullable String ruinId) {
		String dim = dimension.identifier().toString();
		RuinRecord best = null;
		double bestDist = Double.MAX_VALUE;
		for (RuinRecord record : BY_KEY.values()) {
			if (!dim.equals(record.dimension) || ruinId != null && !ruinId.equals(record.ruinId)) {
				continue;
			}
			double dist = record.distanceSq(x, z);
			if (dist < bestDist) {
				bestDist = dist;
				best = record;
			}
		}
		return best;
	}

	/** Cartography data of a guild (created on demand). */
	public static GuildRuinData guild(UUID guildId) {
		return root.guilds.computeIfAbsent(guildId.toString(), k -> new GuildRuinData());
	}

	static @Nullable GuildRuinData peekGuild(UUID guildId) {
		return root.guilds.get(guildId.toString());
	}

	static void removeGuild(UUID guildId) {
		if (root.guilds.remove(guildId.toString()) != null) {
			dirty = true;
		}
	}

	// ================================================================== registration

	/** Drains the generation queues (bounded per call). Public for the GameTests (placement is synchronous there). */
	public static void processPending(MinecraftServer server) {
		int budget = EVENTS_PER_TICK;
		MarkerCapture.StartFound found;
		while (budget-- > 0 && (found = MarkerCapture.STARTS.poll()) != null) {
			MarkerCapture.dequeued();
			MarkerCapture.StartFound event = found;
			Safe.run("ruin.start", () -> registerStart(server, event.dimension(), event.start()));
		}
		MarkerCapture.Captured captured;
		while (budget-- > 0 && (captured = MarkerCapture.MARKERS.poll()) != null) {
			MarkerCapture.dequeued();
			MarkerCapture.Captured marker = captured;
			Safe.run("ruin.marker", () -> applyMarker(server, marker));
		}
	}

	/** A chunk was loaded: its ruin starts (structures of the ruin tags) are registered. */
	static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		Map<Structure, StructureStart> starts = chunk.getAllStarts();
		if (starts.isEmpty()) {
			return;
		}
		for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
			StructureStart start = entry.getValue();
			if (start != null && start.isValid() && isRuin(level, entry.getKey())) {
				MarkerCapture.startFound(level.dimension(), start);
			}
		}
	}

	/** Structure in one of the ruin tags, or whose path is under {@code ruin/}. */
	static boolean isRuin(ServerLevel level, Structure structure) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Holder<Structure> holder = registry.wrapAsHolder(structure);
		if (holder.is(TAG_ALL) || holder.is(TAG_OVERWORLD) || holder.is(TAG_NETHER)) {
			return true;
		}
		Identifier id = registry.getKey(structure);
		return id != null && RuinRules.isRuinTemplate(id.getPath());
	}

	/** Registers (or updates the box of) the ruin of a structure start. */
	static @Nullable RuinRecord registerStart(MinecraftServer server, ResourceKey<Level> dimension, MarkerCapture.StartInfo start) {
		ServerLevel level = server.getLevel(dimension);
		if (level == null) {
			return null;
		}
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Identifier id = registry.getKey(start.structure());
		if (id == null) {
			return null;
		}
		String dim = dimension.identifier().toString();
		String key = RuinRecord.key(dim, start.chunkX(), start.chunkZ(), id.toString());
		RuinRecord record = BY_KEY.get(key);
		if (record == null) {
			record = new RuinRecord();
			record.key = key;
			record.dimension = dim;
			record.structure = id.toString();
			String ruinId = RuinRules.ruinIdOf(id.getPath());
			record.ruinId = ruinId != null ? ruinId : id.getPath().replace('/', '_');
			record.setBox(start.box());
			record.discoveredAt = System.currentTimeMillis();
			BY_KEY.put(key, record);
			dirty = true;
			absorbOrphans(record);
			Guildy.LOGGER.debug("[ruins] registered {} at {}", key, record.center());
		} else if (!record.box().equals(start.box())) {
			record.setBox(start.box());
			dirty = true;
			absorbOrphans(record);
		}
		return record;
	}

	/** Public entry point for a start obtained elsewhere (locate, GameTests). */
	public static @Nullable RuinRecord register(ServerLevel level, StructureStart start) {
		MarkerCapture.StartInfo info = MarkerCapture.startInfo(start);
		return info == null ? null : registerStart(level.getServer(), level.dimension(), info);
	}

	private static void applyMarker(MinecraftServer server, MarkerCapture.Captured marker) {
		RuinRecord record = null;
		if (marker.start() != null) {
			record = registerStart(server, marker.dimension(), marker.start());
		}
		if (record == null) {
			BlockPos pos = marker.pos();
			record = containing(marker.dimension(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0);
		}
		if (record == null) {
			if (root.orphans.size() >= MAX_ORPHANS) {
				root.orphans.removeFirst();
			}
			root.orphans.add(new RuinStore.Orphan(marker.dimension().identifier().toString(), marker.ruinId(), marker.type(), marker.pos()));
			dirty = true;
			return;
		}
		attach(record, marker.type(), marker.pos());
	}

	private static void absorbOrphans(RuinRecord record) {
		Iterator<RuinStore.Orphan> it = root.orphans.iterator();
		while (it.hasNext()) {
			RuinStore.Orphan orphan = it.next();
			if (record.dimension.equals(orphan.dimension) && record.contains(orphan.pos, 0)) {
				it.remove();
				attach(record, orphan.type, orphan.pos);
			}
		}
	}

	static void attach(RuinRecord record, RuinRules.Marker type, BlockPos pos) {
		switch (type) {
			case BOSS -> {
				if (!pos.equals(record.boss)) {
					record.boss = pos;
					dirty = true;
				}
			}
			case ENTRANCE -> {
				if (!pos.equals(record.entrance)) {
					record.entrance = pos;
					dirty = true;
				}
			}
			case MINION -> {
				if (!record.minions.contains(pos) && record.minions.size() < 32) {
					record.minions.add(pos);
					dirty = true;
				}
			}
			case GUARD -> {
				if (!record.guards.contains(pos) && record.guards.size() < 32) {
					record.guards.add(pos);
					dirty = true;
				}
			}
		}
	}
}
