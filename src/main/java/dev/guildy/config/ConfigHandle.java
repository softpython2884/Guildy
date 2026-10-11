package dev.guildy.config;

import dev.guildy.Guildy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A config file bound to a POJO class. {@link #get()} always returns a valid instance (defaults on error).
 */
public final class ConfigHandle<T> {
	private final String fileName;
	private final Class<T> type;
	private final Supplier<T> defaults;
	private final List<Runnable> reloadListeners = new ArrayList<>();
	private T value;

	ConfigHandle(String fileName, Class<T> type, Supplier<T> defaults) {
		this.fileName = fileName;
		this.type = type;
		this.defaults = defaults;
		this.value = defaults.get();
	}

	public T get() {
		return value;
	}

	public String fileName() {
		return fileName;
	}

	public Path path() {
		return ConfigManager.dir().resolve(fileName);
	}

	public void onReload(Runnable listener) {
		reloadListeners.add(listener);
	}

	/**
	 * Loads the file (creating it with defaults if missing), validates, and re-writes it so new keys/comments appear.
	 *
	 * @return null on success, otherwise an error message (the previous value is kept)
	 */
	String load() {
		Path file = path();
		try {
			T loaded;
			if (Files.exists(file)) {
				loaded = Json5.read(file, type);
				if (loaded == null) {
					loaded = defaults.get();
				}
			} else {
				loaded = defaults.get();
			}
			if (loaded instanceof Validatable validatable) {
				validatable.validate();
			}
			Json5.write(file, loaded);
			this.value = loaded;
			for (Runnable listener : reloadListeners) {
				try {
					listener.run();
				} catch (Exception e) {
					Guildy.LOGGER.error("Config reload listener failed for {}", fileName, e);
				}
			}
			return null;
		} catch (Exception e) {
			Guildy.LOGGER.error("Failed to load config {} (keeping previous values)", fileName, e);
			return fileName + ": " + e.getMessage();
		}
	}
}
