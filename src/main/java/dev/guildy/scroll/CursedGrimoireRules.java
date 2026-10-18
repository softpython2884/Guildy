package dev.guildy.scroll;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Pure rules of the cursed grimoire (unit-tested): its blood price (health points taken at each cast, never below a floor:
 * a cast that would go under it is refused) and where it drops (only from the cursed guardians of the ruins listed in
 * {@code cursedGrimoire.ruinDrops}: by default the fallen archmage of the Tour du Mage déchu, never a normal guardian, never
 * another source).
 */
public final class CursedGrimoireRules {
	/** The only ruin whose cursed guardian drops the cursed grimoire by default (the fallen archmage). */
	public static final String MAGE_TOWER = "tour_du_mage";
	/** Health is compared with this tolerance (floating point health). */
	private static final double EPSILON = 1.0E-4;

	private CursedGrimoireRules() {
	}

	/** Points (0 to 20) a spell of this rarity costs (map by rarity name, 0 when missing): food or health. */
	public static int points(@Nullable Map<String, Integer> costs, @Nullable String rarity) {
		return GrimoireRules.foodCost(costs, rarity);
	}

	/** The blood price can be paid: after it, the caster still has at least {@code minHealth} health points. */
	public static boolean canBleed(float health, int cost, double minHealth) {
		return cost <= 0 || health - cost >= minHealth - EPSILON;
	}

	/**
	 * Health after paying {@code cost}: never below {@code minHealth} (a caster already under it loses nothing).
	 */
	public static float bleed(float health, int cost, double minHealth) {
		if (cost <= 0 || health <= minHealth + EPSILON) {
			return health;
		}
		return (float) Math.max(minHealth, health - cost);
	}

	/**
	 * Chance (0..1) that the defeated guardian of ruin {@code ruinId} drops a blank cursed grimoire: only a cursed guardian,
	 * only a ruin listed in {@code ruinDrops}, 0 when the cursed grimoire is disabled.
	 */
	public static double dropChance(@Nullable Map<String, Double> ruinDrops, @Nullable String ruinId, boolean cursed, boolean enabled) {
		if (!enabled || !cursed || ruinDrops == null || ruinId == null) {
			return 0;
		}
		Double chance = ruinDrops.get(ruinId.trim().toLowerCase(Locale.ROOT));
		return chance == null || !Double.isFinite(chance) ? 0 : Math.max(0, Math.min(1, chance));
	}

	/** One roll of a drop of chance {@code chance} with {@code roll} uniform in [0, 1). */
	public static boolean drops(double chance, double roll) {
		return chance >= 1 || chance > 0 && roll < chance;
	}
}
