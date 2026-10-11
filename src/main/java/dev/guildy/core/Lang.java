package dev.guildy.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import dev.guildy.Guildy;
import dev.guildy.config.ConfigManager;
import dev.guildy.config.Json5;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Player-facing texts. Every module registers its French defaults with {@link #addDefaults(Map)} at init;
 * the merged table is written to config/guildy/lang.json where admins can edit it.
 * Placeholders: {0}, {1}... ; colors: '&' codes (&a, &l, &r...).
 */
public final class Lang {
	private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
	private static final Map<String, String> VALUES = new LinkedHashMap<>();

	private Lang() {
	}

	public static void addDefaults(Map<String, String> defaults) {
		DEFAULTS.putAll(defaults);
	}

	public static void addDefault(String key, String value) {
		DEFAULTS.put(key, value);
	}

	public static Path path() {
		return ConfigManager.dir().resolve("lang.json");
	}

	/** Loads lang.json, adds missing keys, re-writes the file sorted by key. */
	public static String load() {
		VALUES.clear();
		VALUES.putAll(DEFAULTS);
		Path file = path();
		try {
			if (Files.exists(file)) {
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					JsonReader jsonReader = new JsonReader(reader);
					jsonReader.setStrictness(Strictness.LENIENT);
					JsonObject obj = JsonParser.parseReader(jsonReader).getAsJsonObject();
					for (var entry : obj.entrySet()) {
						if (entry.getValue().isJsonPrimitive()) {
							VALUES.put(entry.getKey(), entry.getValue().getAsString());
						}
					}
				}
			}
			JsonObject out = new JsonObject();
			for (var entry : new TreeMap<>(VALUES).entrySet()) {
				out.addProperty(entry.getKey(), entry.getValue());
			}
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("lang.json.tmp");
			Files.writeString(tmp, Json5.GSON.toJson(out), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return null;
		} catch (Exception e) {
			Guildy.LOGGER.error("Failed to load lang.json (using defaults)", e);
			return "lang.json: " + e.getMessage();
		}
	}

	/** Raw formatted string with '&' converted to section signs. */
	public static String raw(String key, Object... args) {
		String pattern = VALUES.get(key);
		if (pattern == null) {
			pattern = DEFAULTS.getOrDefault(key, key);
		}
		return colorize(format(pattern, args));
	}

	/** Formatted text component (legacy color codes are rendered by vanilla clients). */
	public static MutableComponent get(String key, Object... args) {
		return Component.literal(raw(key, args));
	}

	public static boolean has(String key) {
		return VALUES.containsKey(key) || DEFAULTS.containsKey(key);
	}

	public static String format(String pattern, Object... args) {
		if (args == null || args.length == 0 || pattern.indexOf('{') < 0) {
			return pattern;
		}
		StringBuilder sb = new StringBuilder(pattern.length() + 16);
		int i = 0;
		while (i < pattern.length()) {
			char c = pattern.charAt(i);
			if (c == '{') {
				int end = pattern.indexOf('}', i);
				if (end > i + 1) {
					String index = pattern.substring(i + 1, end);
					try {
						int n = Integer.parseInt(index);
						if (n >= 0 && n < args.length) {
							Object arg = args[n];
							sb.append(arg instanceof Component comp ? comp.getString() : String.valueOf(arg));
							i = end + 1;
							continue;
						}
					} catch (NumberFormatException ignored) {
						// not a placeholder
					}
				}
			}
			sb.append(c);
			i++;
		}
		return sb.toString();
	}

	public static String colorize(String text) {
		if (text.indexOf('&') < 0) {
			return text;
		}
		char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length - 1; i++) {
			if (chars[i] == '&' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(chars[i + 1]) >= 0) {
				chars[i] = '§';
				chars[i + 1] = Character.toLowerCase(chars[i + 1]);
			}
		}
		return new String(chars);
	}

	/** Removes legacy formatting codes (for logs / sign length checks). */
	public static String strip(String text) {
		return text.replaceAll("§[0-9a-fk-or]", "");
	}
}
