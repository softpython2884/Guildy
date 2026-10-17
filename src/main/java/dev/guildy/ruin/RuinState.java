package dev.guildy.ruin;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * State of a ruin's guardian. Persisted by name; {@link #AWAKE} never survives a restart (the fight is cancelled).
 */
public enum RuinState {
	/** The guardian sleeps: the next survival player who comes close awakens it. */
	DORMANT,
	/** A fight is running (runtime only). */
	AWAKE,
	/** The guardian was defeated: re-armed after {@code respawnDays} in-game days (never if 0). */
	CLEARED;

	public String langKey() {
		return "ruin.state." + name().toLowerCase(Locale.ROOT);
	}

	public static RuinState parse(@Nullable String value) {
		if (value == null) {
			return DORMANT;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return DORMANT;
		}
	}
}
