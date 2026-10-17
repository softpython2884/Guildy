package dev.guildy.unique;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * How unique the unique weapons are (uniques.json5 "uniqueness", pure Java): GUILD = a guild never receives a weapon it
 * already holds, SERVER = one copy of each weapon on the whole server, NONE = no limit (rolls ignore the registry).
 */
public enum UniquenessMode {
	GUILD,
	SERVER,
	NONE;

	/** "guild" / "server" / "none" (config value). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Parses a config value (null when unknown). */
	public static @Nullable UniquenessMode parse(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String key = value.trim().toUpperCase(Locale.ROOT);
		for (UniquenessMode mode : values()) {
			if (mode.name().equals(key)) {
				return mode;
			}
		}
		return null;
	}
}
