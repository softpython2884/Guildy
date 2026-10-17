package dev.guildy.unique;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.guildy.Guildy;
import dev.guildy.data.GuildStorage;
import dev.guildy.data.JsonAdapters;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Persistence of the unique weapon registry in {@code <world>/guildy/uniques.json} (versioned, atomic writes, .bak
 * fallback), like {@code guilds.json}.
 */
final class UniqueStore {
	static final String FILE = "uniques.json";

	private UniqueStore() {
	}

	static Path path(MinecraftServer server) {
		return GuildStorage.dir(server).resolve(FILE);
	}

	static UniqueLedger load(MinecraftServer server) {
		Path file = path(server);
		Path backup = file.resolveSibling(FILE + ".bak");
		if (!Files.exists(file) && !Files.exists(backup)) {
			return fresh();
		}
		try {
			return read(file);
		} catch (Exception e) {
			Guildy.LOGGER.error("Could not read {} - trying backup", file, e);
			try {
				if (Files.exists(file)) {
					Files.copy(file, file.resolveSibling(FILE + ".broken-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
				}
				UniqueLedger ledger = read(backup);
				Guildy.LOGGER.warn("Loaded the unique weapon registry from backup {}", backup);
				return ledger;
			} catch (Exception e2) {
				Guildy.LOGGER.error("Could not read backup {} either - starting with an empty unique weapon registry (files kept;"
						+ " the weapons in the world register themselves again when seen)", backup, e2);
				return fresh();
			}
		}
	}

	private static UniqueLedger fresh() {
		UniqueLedger ledger = new UniqueLedger();
		ledger.fixNulls();
		return ledger;
	}

	private static UniqueLedger read(Path file) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		JsonObject json = JsonParser.parseString(text).getAsJsonObject();
		int version = json.has("dataVersion") ? json.get("dataVersion").getAsInt() : 0;
		if (version > UniqueLedger.DATA_VERSION) {
			Guildy.LOGGER.warn("uniques.json has dataVersion {} (newer than {}), loading anyway", version, UniqueLedger.DATA_VERSION);
		}
		UniqueLedger ledger = gson().fromJson(json, UniqueLedger.class);
		if (ledger == null) {
			ledger = new UniqueLedger();
		}
		ledger.fixNulls();
		return ledger;
	}

	static void save(MinecraftServer server, UniqueLedger ledger) throws IOException {
		Path file = path(server);
		Files.createDirectories(file.getParent());
		String text = gson().toJson(ledger);
		Path tmp = file.resolveSibling(FILE + ".tmp");
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		if (Files.exists(file)) {
			Files.copy(file, file.resolveSibling(FILE + ".bak"), StandardCopyOption.REPLACE_EXISTING);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static Gson cached;

	private static Gson gson() {
		Gson gson = cached;
		if (gson == null) {
			gson = JsonAdapters.createGson();
			cached = gson;
		}
		return gson;
	}
}
