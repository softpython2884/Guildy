package dev.guildy.quest;

import dev.guildy.core.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Rank of a horde mob read from the markers the wave module puts on its mobs: every horde mob carries the
 * {@value #WAVE_TAG} entity tag, a boss gets a visible custom name, an elite a hidden "Élite ..." name (and glows by
 * default). Conversions (zombie → drowned...) keep tags and names. Name tags never make a name visible.
 */
public final class HordeMobs {
	/** Entity tag of every horde mob (wave module). */
	public static final String WAVE_TAG = "guildy_wave";

	public enum Rank {
		/** Not a horde mob. */
		NONE,
		NORMAL,
		ELITE,
		BOSS
	}

	private HordeMobs() {
	}

	public static Rank rank(Entity entity) {
		boolean wave = entity.entityTags().contains(WAVE_TAG);
		if (!wave) {
			return Rank.NONE;
		}
		Component name = entity.getCustomName();
		return rank(true, name != null, entity.isCustomNameVisible(), entity.hasGlowingTag(),
				name != null && Lang.strip(name.getString()).startsWith(elitePrefix()));
	}

	/**
	 * Pure rule (unit-tested): horde mobs only; a visible name marks a boss; a hidden name marks an elite when the mob glows
	 * or the name starts with the elite prefix (a horde mob renamed with a name tag stays a normal mob).
	 */
	static Rank rank(boolean waveTag, boolean named, boolean nameVisible, boolean glowing, boolean elitePrefix) {
		if (!waveTag) {
			return Rank.NONE;
		}
		if (named && nameVisible) {
			return Rank.BOSS;
		}
		if (named && (glowing || elitePrefix)) {
			return Rank.ELITE;
		}
		return Rank.NORMAL;
	}

	/** Plain text of the wave module's elite name prefix ("Élite"). */
	private static String elitePrefix() {
		String prefix = Lang.strip(Lang.raw("wave.elite.prefix")).trim();
		return prefix.isEmpty() ? "\u0000" : prefix;
	}
}
