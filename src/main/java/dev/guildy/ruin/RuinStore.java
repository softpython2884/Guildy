package dev.guildy.ruin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.guildy.Guildy;
import dev.guildy.data.GuildStorage;
import dev.guildy.data.JsonAdapters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistence of the ruins in {@code <world>/guildy/ruins.json} (versioned, atomic writes, .bak fallback), like
 * {@code guilds.json}.
 */
final class RuinStore {
	static final int DATA_VERSION = 1;
	private static final String FILE = "ruins.json";

	private RuinStore() {
	}

	/** File content. */
	static final class Root {
		int dataVersion = DATA_VERSION;
		List<RuinRecord> ruins = new ArrayList<>();
		/** Data markers generated before their ruin was registered (absorbed when a ruin box contains them). */
		List<Orphan> orphans = new ArrayList<>();
		/** Guild id → cartography data. */
		Map<String, GuildRuinData> guilds = new LinkedHashMap<>();
		/** Jaguar statues of the trails to the grand temples (absent from older saves). */
		List<StatueRecord> statues = new ArrayList<>();
		List<StatueTrail> trails = new ArrayList<>();
		int nextStatueId = 1;
		int nextTrailId = 1;

		void fixNulls() {
			if (statues == null) {
				statues = new ArrayList<>();
			}
			statues.removeIf(s -> s == null || s.id <= 0);
			for (StatueRecord statue : statues) {
				statue.fixNulls();
				nextStatueId = Math.max(nextStatueId, statue.id + 1);
			}
			if (trails == null) {
				trails = new ArrayList<>();
			}
			trails.removeIf(t -> t == null || t.id <= 0);
			for (StatueTrail trail : trails) {
				trail.fixNulls();
				nextTrailId = Math.max(nextTrailId, trail.id + 1);
			}
			nextStatueId = Math.max(1, nextStatueId);
			nextTrailId = Math.max(1, nextTrailId);
			if (ruins == null) {
				ruins = new ArrayList<>();
			}
			ruins.removeIf(r -> r == null || r.key == null || r.key.isEmpty());
			for (RuinRecord record : ruins) {
				record.fixNulls();
			}
			if (orphans == null) {
				orphans = new ArrayList<>();
			}
			orphans.removeIf(o -> o == null || o.pos == null || o.type == null || o.dimension == null);
			if (guilds == null) {
				guilds = new LinkedHashMap<>();
			}
			guilds.values().removeIf(g -> g == null);
			for (GuildRuinData data : guilds.values()) {
				data.fixNulls();
			}
		}
	}

	/** A data marker waiting for its ruin. */
	static final class Orphan {
		String dimension;
		String ruinId;
		RuinRules.Marker type;
		BlockPos pos;
		long createdAt;

		Orphan() {
		}

		Orphan(String dimension, String ruinId, RuinRules.Marker type, BlockPos pos) {
			this.dimension = dimension;
			this.ruinId = ruinId;
			this.type = type;
			this.pos = pos;
			this.createdAt = System.currentTimeMillis();
		}
	}

	static Path path(MinecraftServer server) {
		return GuildStorage.dir(server).resolve(FILE);
	}

	static Root load(MinecraftServer server) {
		Path file = path(server);
		Path backup = file.resolveSibling(FILE + ".bak");
		if (!Files.exists(file) && !Files.exists(backup)) {
			return new Root();
		}
		try {
			return read(file);
		} catch (Exception e) {
			Guildy.LOGGER.error("Could not read {} - trying backup", file, e);
			try {
				if (Files.exists(file)) {
					Files.copy(file, file.resolveSibling(FILE + ".broken-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
				}
				Root root = read(backup);
				Guildy.LOGGER.warn("Loaded ruin data from backup {}", backup);
				return root;
			} catch (Exception e2) {
				Guildy.LOGGER.error("Could not read backup {} either - starting with no known ruins (files kept)", backup, e2);
				return new Root();
			}
		}
	}

	private static Root read(Path file) throws IOException {
		return parse(Files.readString(file, StandardCharsets.UTF_8));
	}

	/** File content → data (nulls fixed). */
	static Root parse(String text) {
		JsonObject json = JsonParser.parseString(text).getAsJsonObject();
		int version = json.has("dataVersion") ? json.get("dataVersion").getAsInt() : 0;
		if (version > DATA_VERSION) {
			Guildy.LOGGER.warn("ruins.json has dataVersion {} (newer than {}), loading anyway", version, DATA_VERSION);
		}
		Root root = gson().fromJson(json, Root.class);
		if (root == null) {
			root = new Root();
		}
		root.fixNulls();
		return root;
	}

	static void save(MinecraftServer server, Root root) throws IOException {
		Path file = path(server);
		Files.createDirectories(file.getParent());
		String text = toJson(root);
		Path tmp = file.resolveSibling(FILE + ".tmp");
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		if (Files.exists(file)) {
			Files.copy(file, file.resolveSibling(FILE + ".bak"), StandardCopyOption.REPLACE_EXISTING);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/** Data → file content. */
	static String toJson(Root root) {
		root.dataVersion = DATA_VERSION;
		return gson().toJson(root);
	}

	private static @Nullable Gson cached;

	private static Gson gson() {
		Gson gson = cached;
		if (gson == null) {
			gson = JsonAdapters.createGson();
			cached = gson;
		}
		return gson;
	}
}
