package dev.guildy.unique;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Java powers of the armor set bonuses (uniques.json5 "sets", reached with a number of pieces worn). Pure Java: the
 * behaviour lives in {@link SetPowers}, the balance in uniques.json5 ({@link UniqueConfig.Powers}). Monsters only.
 */
public enum SetPower {
	/** Hiver éternel, 2 pieces: the wearer never freezes (powder snow) nor takes freezing damage. */
	FROST_BLOOD("frost_blood"),
	/** Hiver éternel, 4 pieces: no slowness; under a health threshold after a blow, a frost nova freezes the monsters around. */
	WINTER_HEART("winter_heart"),
	/** Guerrier jaguar, 4 pieces: crouched and still, the wearer is camouflaged; the next blow is an ambush (claws around). */
	JAGUAR_AMBUSH("jaguar_ambush"),
	/** Brasier des âmes, 4 pieces: an aura drains the souls of the monsters around (heals the wearer), a soul fire trail burns. */
	SOUL_PYRE("soul_pyre");

	public final String id;

	SetPower(String id) {
		this.id = id;
	}

	/** Lang key prefix: unique.setpower.{id}.lore */
	public String langKey() {
		return "unique.setpower." + id;
	}

	public static @Nullable SetPower byId(@Nullable String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		String key = id.trim().toLowerCase(Locale.ROOT);
		for (SetPower power : values()) {
			if (power.id.equals(key)) {
				return power;
			}
		}
		return null;
	}

	public static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (SetPower power : values()) {
			ids.add(power.id);
		}
		return ids;
	}
}
