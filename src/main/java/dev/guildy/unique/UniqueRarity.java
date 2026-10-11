package dev.guildy.unique;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Rarity of a unique item (pure Java): name color, bold name for the two highest tiers, lang key of its French name.
 */
public enum UniqueRarity {
	RARE("&9", false),
	EPIC("&d", false),
	LEGENDARY("&6", true),
	MYTHIC("&c", true);

	/** Legacy color code of the item name. */
	public final String color;
	/** Bold item name. */
	public final boolean bold;

	UniqueRarity(String color, boolean bold) {
		this.color = color;
		this.bold = bold;
	}

	/** "&6&l" style prefix of the item name. */
	public String namePrefix() {
		return bold ? color + "&l" : color;
	}

	/** Lang key of the French name ("Légendaire"...). */
	public String langKey() {
		return "unique.rarity." + name().toLowerCase(Locale.ROOT);
	}

	/** Parses "LEGENDARY", "legendary"... (null when unknown). */
	public static @Nullable UniqueRarity parse(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String key = value.trim().toUpperCase(Locale.ROOT);
		for (UniqueRarity rarity : values()) {
			if (rarity.name().equals(key)) {
				return rarity;
			}
		}
		return null;
	}
}
