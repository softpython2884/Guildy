package dev.guildy.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.guildy.Guildy;
import dev.guildy.data.GuildStorage;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Server-wide event state, persisted in {@code <world>/guildy/events.json} (written atomically when it changes).
 */
final class EventState {
	private static final String FILE = "events.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Day index of the last dusk roll (one per day). */
	long lastDuskRollDay = -1;
	/** Day index of the night of the scheduled / running blood night (-1 = none). */
	long bloodNightDay = -1;
	/** Day index of the last blood night (cooldown). */
	long lastBloodNightDay = -1000;

	static Path path(MinecraftServer server) {
		return GuildStorage.dir(server).resolve(FILE);
	}

	static EventState load(MinecraftServer server) {
		Path file = path(server);
		if (!Files.exists(file)) {
			return new EventState();
		}
		try {
			EventState state = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), EventState.class);
			return state == null ? new EventState() : state;
		} catch (Exception e) {
			Guildy.LOGGER.error("Could not read {} (event state reset)", file, e);
			return new EventState();
		}
	}

	void save(MinecraftServer server) {
		Path file = path(server);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(FILE + ".tmp");
			Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			Guildy.LOGGER.error("Could not save {}", file, e);
		}
	}
}
