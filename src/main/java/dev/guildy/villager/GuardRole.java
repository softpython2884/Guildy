package dev.guildy.villager;

import java.util.Locale;

/**
 * Fighting style of a guard (persisted by name in {@link GuardData}).
 */
public enum GuardRole {
	/** Melee with a sword ("Épéiste"). */
	SWORD,
	/** Real arrows from a bow, keeps its distance ("Archer"). */
	BOW;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String langKey() {
		return "villager.guard.role." + id();
	}
}
