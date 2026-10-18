package dev.guildy.villager;

import dev.guildy.level.LevelScaling;

/**
 * Pure health rules of the guild villagers (no Minecraft classes, unit tested): extra max health from the guild level,
 * what happens to the current health when the maximum changes, and the out-of-combat delay of the slow regeneration.
 */
final class VitalityRules {
	private static final double EPSILON = 1e-6;

	private VitalityRules() {
	}

	/** {@link #bonusHealth(int, boolean, double, double, double, double, double)} with the same rate at every level. */
	static double bonusHealth(int guildLevel, boolean guard, double perLevel, double guardMultiplier, double guardBonus, double max) {
		return bonusHealth(guildLevel, guard, perLevel, perLevel, guardMultiplier, guardBonus, max);
	}

	/**
	 * Extra max health of a guild villager: {@code perLevel} per guild level above 1 up to level 10, then
	 * {@code perLevelAbove10} per level; guards get that bonus times {@code guardMultiplier}, plus a flat
	 * {@code guardBonus}. Never negative, at most {@code max}.
	 */
	static double bonusHealth(int guildLevel, boolean guard, double perLevel, double perLevelAbove10, double guardMultiplier,
			double guardBonus, double max) {
		double fromLevel = LevelScaling.bonus(guildLevel, perLevel, perLevelAbove10, 0);
		double bonus = guard ? fromLevel * positive(guardMultiplier) + positive(guardBonus) : fromLevel;
		return Math.min(bonus, positive(max));
	}

	/**
	 * Health after the maximum changed from {@code oldMax} to {@code newMax}: never above the new maximum; a villager at
	 * full health stays at full health; otherwise the gain is added only when {@code healGain} (guild level-up), so that
	 * switching tasks back and forth never heals.
	 */
	static double healthAfterChange(double health, double oldMax, double newMax, boolean healGain) {
		if (newMax <= oldMax + EPSILON) {
			return Math.min(health, newMax);
		}
		if (health >= oldMax - EPSILON) {
			return newMax;
		}
		return healGain ? Math.min(newMax, health + (newMax - oldMax)) : health;
	}

	/** Neither hurt nor fighting for {@code delayTicks}: the slow regeneration runs. */
	static boolean outOfCombat(long now, long lastCombat, long delayTicks) {
		return now - lastCombat >= delayTicks;
	}

	/** Health shown in the menus ("12/36 ❤"): rounded up, so that a living villager never shows 0. */
	static int display(double health) {
		return GuardRules.displayHealth(health);
	}

	private static double positive(double value) {
		return Double.isNaN(value) ? 0.0 : Math.max(0.0, value);
	}
}
