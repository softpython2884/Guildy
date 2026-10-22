package dev.guildy.rune;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Java powers a rune can carry (runes.json5 "power"). The rare ones are small playful powers, the mythic ones (Phénix,
 * Dragon) only go on mythic runes. Every power only affects monsters and only works while the piece is worn by a player.
 * Free of Minecraft classes (unit tests).
 */
public enum RunePower {
	/** Étincelles: a monster striking in melee gets a shower of sparks (small damage). */
	SPARKS("sparks", false),
	/** Braise: a monster that hits the wearer may catch fire. */
	EMBER("ember", false),
	/** Lueur: when the wearer is hit, the monsters around glow for a few seconds. */
	GLOW("glow", false),
	/** Écho: sneaking still sends a sonar ping that reveals the monsters around (glowing). */
	ECHO("echo", false),
	/** Renard: dropped items nearby fly to the wearer. */
	FOX("fox", false),
	/** Rebond: a landing after a high fall bounces the wearer back up (with the rune's fall damage reduction). */
	BOUNCE("bounce", false),
	/** Phénix (mythic): a lethal blow raises the wearer from the ashes in a burst of flames, on a long cooldown. */
	PHOENIX("phoenix", true),
	/** Dragon (mythic): the chestplate glides like an elytra and breathes dragon fire on sneak + jump. */
	DRAGON("dragon", true);

	public final String id;
	/** Only allowed on a mythic rune. */
	public final boolean mythic;

	RunePower(String id, boolean mythic) {
		this.id = id;
		this.mythic = mythic;
	}

	/** Lang key prefix of the power ("rune.power.sparks"). */
	public String langKey() {
		return "rune.power." + id;
	}

	/** The power of a config value, null for "" or an unknown value. */
	public static @Nullable RunePower parse(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String text = value.trim().toLowerCase(Locale.ROOT);
		for (RunePower power : values()) {
			if (power.id.equals(text)) {
				return power;
			}
		}
		return null;
	}
}
