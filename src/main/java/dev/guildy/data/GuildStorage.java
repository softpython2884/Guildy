package dev.guildy.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.guildy.Guildy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Persistence of all guilds in {@code <world>/guildy/guilds.json} (versioned, atomic writes, .bak fallback).
 */
public final class GuildStorage {
	public static final int DATA_VERSION = 1;
	private static final String FILE = "guilds.json";

	private GuildStorage() {
	}

	public static Path dir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("guildy");
	}

	private static final class Root {
		int dataVersion = DATA_VERSION;
		List<Guild> guilds = new ArrayList<>();
	}

	public static List<Guild> load(MinecraftServer server) {
		Path file = dir(server).resolve(FILE);
		Path backup = dir(server).resolve(FILE + ".bak");
		if (!Files.exists(file) && !Files.exists(backup)) {
			return new ArrayList<>();
		}
		try {
			return read(file);
		} catch (Exception e) {
			Guildy.LOGGER.error("Could not read {} - trying backup", file, e);
			try {
				Path broken = dir(server).resolve(FILE + ".broken-" + System.currentTimeMillis());
				if (Files.exists(file)) {
					Files.copy(file, broken, StandardCopyOption.REPLACE_EXISTING);
				}
				List<Guild> guilds = read(backup);
				Guildy.LOGGER.warn("Loaded guild data from backup {}", backup);
				return guilds;
			} catch (Exception e2) {
				Guildy.LOGGER.error("Could not read backup {} either - starting with no guilds (files kept)", backup, e2);
				return new ArrayList<>();
			}
		}
	}

	private static List<Guild> read(Path file) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		JsonObject json = JsonParser.parseString(text).getAsJsonObject();
		int version = json.has("dataVersion") ? json.get("dataVersion").getAsInt() : 0;
		if (version > DATA_VERSION) {
			Guildy.LOGGER.warn("guilds.json has dataVersion {} (newer than {}), loading anyway", version, DATA_VERSION);
		}
		// Future migrations go here (version < DATA_VERSION).
		Gson gson = JsonAdapters.createGson();
		Root root = gson.fromJson(json, Root.class);
		List<Guild> guilds = new ArrayList<>();
		if (root != null && root.guilds != null) {
			for (Guild guild : root.guilds) {
				if (guild != null && guild.id != null && guild.name != null) {
					guild.fixNulls();
					guilds.add(guild);
				}
			}
		}
		return guilds;
	}

	public static void save(MinecraftServer server, Collection<Guild> guilds) throws IOException {
		Path dir = dir(server);
		Files.createDirectories(dir);
		Root root = new Root();
		root.guilds = new ArrayList<>(guilds);
		String text = JsonAdapters.createGson().toJson(root);
		Path file = dir.resolve(FILE);
		Path tmp = dir.resolve(FILE + ".tmp");
		Path backup = dir.resolve(FILE + ".bak");
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		if (Files.exists(file)) {
			Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
