package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

/**
 * Guard settings of a guild villager (persisted in {@link VillagerRecord#guard}, created the first time the villager
 * becomes a guard). The equipment itself lives on the entity (armor slots, real weapon in the off hand).
 */
public class GuardData {
	/** "Auto": the role is chosen to keep the guild's guards balanced; false = {@link #role} forced from the menu. */
	public boolean autoRole = true;
	/** Effective role, resolved when the villager becomes a guard (null = not resolved yet). */
	public @Nullable GuardRole role;

	void sanitize() {
		if (!autoRole && role == null) {
			autoRole = true;
		}
	}
}
