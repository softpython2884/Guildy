package dev.guildy.builds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Placements shared with Syncmatica (the sharing mod of Litematica, on the server and the players' clients), read from
 * its files and never through its code: Guildy works with or without it. A player places a schematic with Litematica,
 * shares it, then makes it a site from the sites menu; the site lands exactly where Litematica shows it.
 * <p>
 * Syncmatica keeps the placements in {@code <world>/syncmatica/placements.json} ({@code {"placements": [{id, file_name,
 * display_name, hash, origin: {position: [x, y, z], dimension}, rotation, mirror, owner: {uuid, name}, subregionData?}]}})
 * and their files in {@code <server>/syncmatics/<hash>.litematic}. Litematica places a block at
 * {@code origin + transform(position in the file)}, mirror then rotation around the origin, like {@link PlacementTransform}.
 */
public final class SyncmaticaLink {
	public static final String KEY_PREFIX = "syncmatica:";

	/** A shared placement. {@code subregions}: sub-regions moved in Litematica (Guildy keeps them in their place). */
	public record Shared(UUID id, String name, UUID hash, String dimension, int x, int y, int z, PlacementTransform.Turn turn,
			PlacementTransform.Flip flip, String owner, @Nullable UUID ownerId, boolean subregions) {
		public String key() {
			return KEY_PREFIX + id;
		}
	}

	private static List<Shared> cached = List.of();
	private static @Nullable Path cachedPath;
	private static long cachedModified = Long.MIN_VALUE;
	private static final Set<UUID> SEEN = new HashSet<>();
	private static boolean primed;

	private SyncmaticaLink() {
	}

	static void init() {
		Scheduler.every("build.syncmatica", 100, 37, SyncmaticaLink::announce);
		Lifecycle.STOPPING.add(server -> clear());
	}

	private static synchronized void clear() {
		cached = List.of();
		cachedPath = null;
		cachedModified = Long.MIN_VALUE;
		SEEN.clear();
		primed = false;
	}

	// ================================================================ files

	/** Syncmatica is there (mod loaded, or its placements file exists) and the link is enabled. */
	public static boolean available() {
		MinecraftServer server = GuildyServer.server();
		return BuildModule.config().litematica.enabled && server != null
				&& (FabricLoader.getInstance().isModLoaded("syncmatica") || Files.isRegularFile(placementsFile(server)));
	}

	static Path placementsFile(MinecraftServer server) {
		String custom = BuildModule.config().litematica.placementsFile;
		if (custom != null && !custom.isBlank()) {
			return FabricLoader.getInstance().getGameDir().resolve(custom).normalize();
		}
		return server.getWorldPath(LevelResource.ROOT).resolve("syncmatica").resolve("placements.json").normalize();
	}

	/** The file of a placement (null when it would leave the folder). */
	static @Nullable Path file(Shared shared) {
		Path dir = FabricLoader.getInstance().getGameDir().resolve(BuildModule.config().litematica.filesFolder).normalize();
		Path file = dir.resolve(shared.hash() + ".litematic").normalize();
		return file.startsWith(dir) ? file : null;
	}

	/** Every shared placement, read again when the file changed. */
	public static synchronized List<Shared> all() {
		MinecraftServer server = GuildyServer.server();
		if (server == null || !BuildModule.config().litematica.enabled) {
			return List.of();
		}
		Path path = placementsFile(server);
		try {
			long modified = Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : -1;
			if (!path.equals(cachedPath) || modified != cachedModified) {
				cached = modified < 0 ? List.of() : parse(Files.readString(path, StandardCharsets.UTF_8));
				cachedPath = path;
				cachedModified = modified;
			}
		} catch (IOException | RuntimeException e) {
			Guildy.LOGGER.warn("Could not read the Syncmatica placements {}: {}", path, e.toString());
		}
		return cached;
	}

	public static @Nullable Shared get(UUID id) {
		for (Shared shared : all()) {
			if (shared.id().equals(id)) {
				return shared;
			}
		}
		return null;
	}

	/** The placement of a key ({@code syncmatica:<id>}), null when it is no longer shared. */
	public static @Nullable Shared byKey(String key) {
		if (!key.startsWith(KEY_PREFIX)) {
			return null;
		}
		try {
			return get(UUID.fromString(key.substring(KEY_PREFIX.length())));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The schematic entry of a placement key ({@code syncmatica:<id>}), null when the placement or its file is gone. */
	static SchematicLibrary.@Nullable Entry entry(String key) {
		Shared shared = byKey(key);
		Path file = shared == null ? null : file(shared);
		if (file == null || !Files.isRegularFile(file)) {
			return null;
		}
		try {
			return new SchematicLibrary.Entry(key, true, shared.name(), file, Files.size(file), Files.getLastModifiedTime(file).toMillis());
		} catch (IOException e) {
			return null;
		}
	}

	/** The placement is in the guild's level and its origin in the guild's territory, or a member shared it. */
	static boolean concerns(Shared shared, Guild guild) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null || !shared.dimension().equals(level.dimension().identifier().toString())) {
			return false;
		}
		return GuildManager.atChunk(ChunkKey.of(shared.x() >> 4, shared.z() >> 4)) == guild
				|| shared.ownerId() != null && guild.isMember(shared.ownerId());
	}

	static List<Shared> forGuild(Guild guild) {
		List<Shared> list = new ArrayList<>();
		for (Shared shared : all()) {
			if (concerns(shared, guild)) {
				list.add(shared);
			}
		}
		return list;
	}

	// ================================================================ placement

	/**
	 * Where a site lands for a placement of Litematica: Guildy counts from the min corner of the regions, Litematica from
	 * the origin the file was saved with ({@code originX/Y/Z} from it), both turned and mirrored the same way.
	 */
	static PlacementTransform transform(int x, int y, int z, PlacementTransform.Turn turn, PlacementTransform.Flip flip,
			int originX, int originY, int originZ) {
		PlacementTransform at = new PlacementTransform(x, y, z, turn, flip);
		return new PlacementTransform(at.worldX(originX, originZ), at.worldY(originY), at.worldZ(originX, originZ), turn, flip);
	}

	static PlacementTransform transform(Shared shared, SchematicData data) {
		return transform(shared.x(), shared.y(), shared.z(), shared.turn(), shared.flip(), data.originX, data.originY, data.originZ);
	}

	// ================================================================ announce

	/** New placements shared in a territory: the guild hears of it (those present at startup are not announced). */
	private static void announce() {
		if (!available()) {
			return;
		}
		List<Shared> all = all();
		synchronized (SyncmaticaLink.class) {
			if (!primed) {
				primed = true;
				all.forEach(shared -> SEEN.add(shared.id()));
				return;
			}
		}
		for (Shared shared : all) {
			if (!SEEN.add(shared.id()) || !BuildModule.config().litematica.announce) {
				continue;
			}
			ServerLevel level = GuildyServer.claimLevel();
			if (level == null || !shared.dimension().equals(level.dimension().identifier().toString())) {
				continue;
			}
			Guild guild = GuildManager.atChunk(ChunkKey.of(shared.x() >> 4, shared.z() >> 4));
			if (guild != null) {
				Msg.guild(guild, "build.litematica.announce", shared.owner(), shared.name());
			}
		}
	}

	// ================================================================ parsing

	/** The placements of a Syncmatica file (pure): entries without an id, a hash or an origin are skipped. */
	static List<Shared> parse(String json) {
		List<Shared> list = new ArrayList<>();
		JsonElement root = JsonParser.parseString(json);
		if (!root.isJsonObject() || !root.getAsJsonObject().has("placements") || !root.getAsJsonObject().get("placements").isJsonArray()) {
			return list;
		}
		JsonArray placements = root.getAsJsonObject().getAsJsonArray("placements");
		for (JsonElement element : placements) {
			try {
				Shared shared = parseOne(element.getAsJsonObject());
				if (shared != null) {
					list.add(shared);
				}
			} catch (RuntimeException e) {
				// A broken entry: skipped, the others still count.
			}
		}
		return list;
	}

	private static @Nullable Shared parseOne(JsonObject obj) {
		if (!obj.has("id") || !obj.has("hash") || !obj.has("origin")) {
			return null;
		}
		UUID id = UUID.fromString(obj.get("id").getAsString());
		UUID hash = UUID.fromString(obj.get("hash").getAsString());
		JsonObject origin = obj.getAsJsonObject("origin");
		JsonArray position = origin.getAsJsonArray("position");
		if (position == null || position.size() < 3) {
			return null;
		}
		String dimension = origin.has("dimension") ? origin.get("dimension").getAsString() : "minecraft:overworld";
		String name = string(obj, "display_name");
		if (name.isBlank()) {
			name = fileName(string(obj, "file_name"));
		}
		String owner = "?";
		UUID ownerId = null;
		if (obj.has("owner") && obj.get("owner").isJsonObject()) {
			JsonObject o = obj.getAsJsonObject("owner");
			owner = string(o, "name");
			try {
				ownerId = o.has("uuid") ? UUID.fromString(o.get("uuid").getAsString()) : null;
			} catch (IllegalArgumentException e) {
				ownerId = null;
			}
		}
		boolean subregions = obj.has("subregionData") && obj.get("subregionData").isJsonArray()
				&& !obj.getAsJsonArray("subregionData").isEmpty();
		return new Shared(id, name.isBlank() ? "Litematica" : name, hash, dimension, position.get(0).getAsInt(), position.get(1).getAsInt(),
				position.get(2).getAsInt(), PlacementTransform.Turn.parse(string(obj, "rotation")),
				PlacementTransform.Flip.parse(string(obj, "mirror")), owner, ownerId, subregions);
	}

	private static String string(JsonObject obj, String key) {
		JsonElement value = obj.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}

	/** "maison" from "C:\\schematics\\maison.litematic". */
	private static String fileName(String path) {
		String name = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
		return name.endsWith(".litematic") ? name.substring(0, name.length() - ".litematic".length()) : name;
	}
}
