package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The three jobs of an alchemist: enchanted books and spell scrolls at an enchanting table, potions and elixirs at a
 * brewing stand. Persisted by id ({@link CraftData#role}).
 */
public enum AlchemyJob {
	BOOKS,
	SCROLLS,
	POTIONS;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Lang key of the name ("Livres"). */
	public String langKey() {
		return "villager.alchemy.job." + id();
	}

	/** Uses the enchanting table (books and scrolls). */
	public boolean atTable() {
		return this != POTIONS;
	}

	/** The job of an id ("books", "livres" is not one), null for "" / auto / unknown. */
	public static @Nullable AlchemyJob parse(@Nullable String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		String value = id.trim().toUpperCase(Locale.ROOT);
		for (AlchemyJob job : values()) {
			if (job.name().equals(value)) {
				return job;
			}
		}
		return null;
	}
}
