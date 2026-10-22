package dev.guildy.rune;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Rarity tier of a rune: common runes are small stat buffs, rare runes small playful powers, mythic runes (netherite
 * chestplates only) legendary powers. Free of Minecraft classes (unit tests).
 */
public enum RuneTier {
	COMMON("common", "&a"),
	RARE("rare", "&b"),
	MYTHIC("mythic", "&d");

	public final String id;
	/** Legacy colour code of the tier (lore, messages). */
	public final String color;

	RuneTier(String id, String color) {
		this.id = id;
		this.color = color;
	}

	/** Lang key of the French tier name ("Commune", "Rare", "Mythique"). */
	public String langKey() {
		return "rune.tier." + id;
	}

	/** The tier of a config value ("common", "RARE"...), null when unknown. */
	public static @Nullable RuneTier parse(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String text = value.trim().toLowerCase(Locale.ROOT);
		for (RuneTier tier : values()) {
			if (tier.id.equals(text)) {
				return tier;
			}
		}
		return null;
	}
}
