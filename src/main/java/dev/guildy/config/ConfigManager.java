package dev.guildy.config;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Registry of all Guildy config files (config/guildy/*.json5). Modules register their own config class
 * during mod init; everything is loaded at server start and reloaded by /guild admin reload.
 */
public final class ConfigManager {
	private static final List<ConfigHandle<?>> HANDLES = new ArrayList<>();

	private ConfigManager() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("guildy");
	}

	public static <T> ConfigHandle<T> register(String fileName, Class<T> type, Supplier<T> defaults) {
		ConfigHandle<T> handle = new ConfigHandle<>(fileName, type, defaults);
		HANDLES.add(handle);
		return handle;
	}

	/** @return list of error messages (empty when everything loaded fine) */
	public static List<String> loadAll() {
		List<String> errors = new ArrayList<>();
		for (ConfigHandle<?> handle : HANDLES) {
			String error = handle.load();
			if (error != null) {
				errors.add(error);
			}
		}
		return errors;
	}
}
