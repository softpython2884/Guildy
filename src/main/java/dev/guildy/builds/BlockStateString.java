package dev.guildy.builds;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A block state written as text, "minecraft:oak_stairs[facing=north,half=bottom]" (pure, unit tested): the id is
 * namespaced ("stone" becomes "minecraft:stone") and the properties are sorted, so that two spellings of the same state
 * give the same canonical text (the palette of a schematic is deduplicated with it).
 *
 * @param id    namespaced block id
 * @param props properties (sorted by name, unmodifiable)
 */
public record BlockStateString(String id, Map<String, String> props) {
	public static final String AIR = "minecraft:air";
	public static final String STRUCTURE_VOID = "minecraft:structure_void";

	public BlockStateString {
		props = Collections.unmodifiableMap(new TreeMap<>(props));
	}

	/** Parses "ns:id[k=v,...]" (spaces tolerated); null when the text is not a block state. */
	public static @Nullable BlockStateString parse(@Nullable String text) {
		if (text == null) {
			return null;
		}
		String value = text.trim();
		if (value.isEmpty()) {
			return null;
		}
		String id = value;
		Map<String, String> props = new TreeMap<>();
		int open = value.indexOf('[');
		if (open >= 0) {
			int close = value.lastIndexOf(']');
			if (close < open) {
				return null;
			}
			id = value.substring(0, open).trim();
			String inner = value.substring(open + 1, close).trim();
			if (!inner.isEmpty()) {
				for (String part : inner.split(",")) {
					int eq = part.indexOf('=');
					if (eq <= 0) {
						return null;
					}
					String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
					String val = part.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
					if (key.isEmpty() || val.isEmpty()) {
						return null;
					}
					props.put(key, val);
				}
			}
		}
		id = normalizeId(id);
		return id == null ? null : new BlockStateString(id, props);
	}

	/** "Stone" → "minecraft:stone"; null for an invalid id. */
	public static @Nullable String normalizeId(String raw) {
		String id = raw.trim().toLowerCase(Locale.ROOT);
		if (id.isEmpty()) {
			return null;
		}
		if (id.indexOf(':') < 0) {
			id = "minecraft:" + id;
		}
		int colon = id.indexOf(':');
		if (colon == 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
			return null;
		}
		for (int i = 0; i < id.length(); i++) {
			char c = id.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/' || c == ':';
			if (!ok) {
				return null;
			}
		}
		return id;
	}

	/** Path of the id ("oak_stairs" for "minecraft:oak_stairs"). */
	public String path() {
		int colon = id.indexOf(':');
		return colon < 0 ? id : id.substring(colon + 1);
	}

	public boolean isMinecraft() {
		return id.startsWith("minecraft:");
	}

	public @Nullable String prop(String name) {
		return props.get(name);
	}

	/** Air, cave air or void air. */
	public boolean isAir() {
		return id.equals(AIR) || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
	}

	public boolean isStructureVoid() {
		return id.equals(STRUCTURE_VOID);
	}

	/** Same state with one property changed (or added). */
	public BlockStateString with(String name, String value) {
		Map<String, String> copy = new TreeMap<>(props);
		copy.put(name, value);
		return new BlockStateString(id, copy);
	}

	/** Canonical text: id, then the sorted properties in brackets (none: the id alone). */
	@Override
	public String toString() {
		if (props.isEmpty()) {
			return id;
		}
		StringBuilder sb = new StringBuilder(id).append('[');
		boolean first = true;
		for (Map.Entry<String, String> entry : props.entrySet()) {
			if (!first) {
				sb.append(',');
			}
			sb.append(entry.getKey()).append('=').append(entry.getValue());
			first = false;
		}
		return sb.append(']').toString();
	}
}
