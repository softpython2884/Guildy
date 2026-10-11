package dev.guildy.upgrade;

import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import org.jspecify.annotations.Nullable;

/**
 * Bonus API of the upgrade tree, used by the other modules at their hook points. Every helper is safe with a null
 * guild or before the module is loaded (no bonus).
 */
public final class Upgrades {
	private Upgrades() {
	}

	/** Current usable tree (empty before the config is loaded). */
	public static UpgradeTree tree() {
		UpgradeConfig config = UpgradeModule.config();
		return config == null ? UpgradeTree.EMPTY : config.tree();
	}

	/** Sum of an effect over the guild's unlocked nodes that still exist in upgrades.json5 (0 when none). */
	public static double value(@Nullable Guild guild, String effectKey) {
		if (guild == null || guild.upgrades == null || guild.upgrades.unlocked == null) {
			return 0;
		}
		return tree().value(guild.upgrades.unlocked, effectKey);
	}

	public static boolean has(@Nullable Guild guild, String effectKey) {
		return value(guild, effectKey) > 0;
	}

	/** Whole amount of a count or level effect (extra chunks, villagers, effect levels), capped like the game applies it. */
	public static int count(@Nullable Guild guild, String effectKey) {
		double value = UpgradeEffects.effective(effectKey, value(guild, effectKey));
		return (int) Math.min(100_000, Math.floor(value + 1e-9));
	}

	/** base x (1 + pct / 100), rounded. */
	public static long boost(@Nullable Guild guild, String pctKey, long base) {
		return UpgradeMath.boost(base, value(guild, pctKey));
	}

	public static int boost(@Nullable Guild guild, String pctKey, int base) {
		return UpgradeMath.boost(base, value(guild, pctKey));
	}

	public static double boost(@Nullable Guild guild, String pctKey, double base) {
		return UpgradeMath.boost(base, value(guild, pctKey));
	}

	/** base x (1 - pct / 100) rounded down, pct capped at 100. */
	public static long reduce(@Nullable Guild guild, String pctKey, long base) {
		return UpgradeMath.reduce(base, value(guild, pctKey));
	}

	/** Chance or percentage x (1 - pct / 100), pct capped at 100. */
	public static double reduce(@Nullable Guild guild, String pctKey, double base) {
		return UpgradeMath.reduce(base, value(guild, pctKey));
	}

	/** Price after a percentage discount: rounded up, at least 1 unless the base price is 0. */
	public static int discount(@Nullable Guild guild, String pctKey, int price) {
		return UpgradeMath.discount(price, value(guild, pctKey));
	}

	/** Villager work duration (seconds) shortened by work_speed_pct (75 % max), at least 1. */
	public static int workSeconds(@Nullable Guild guild, int seconds) {
		return UpgradeMath.workSeconds(seconds, value(guild, UpgradeEffects.WORK_SPEED_PCT));
	}

	/** Villager work actions per step: 1 normally, more with work_speed_pct (4 max). */
	public static double workRate(@Nullable Guild guild) {
		return UpgradeMath.workRate(value(guild, UpgradeEffects.WORK_SPEED_PCT));
	}

	/** Unlocked nodes that exist in the tree. */
	public static int unlockedCount(@Nullable Guild guild) {
		return guild == null || guild.upgrades == null ? 0 : tree().unlockedCount(guild.upgrades.unlocked);
	}

	/** True when a node could be unlocked now with the treasury alone (level, requirements and gold; items not checked). */
	public static boolean hasAffordable(@Nullable Guild guild) {
		if (guild == null || guild.upgrades == null) {
			return false;
		}
		for (UpgradeTree.Node node : tree().nodes()) {
			if (UpgradeRules.state(node, guild.upgrades.unlocked, guild.level) == UpgradeRules.State.AVAILABLE
					&& guild.treasury >= node.costGold()) {
				return true;
			}
		}
		return false;
	}

	/** Plain French description of an effect value ("+25 % d'intégrité du cœur"). */
	public static String describe(String effectKey, double value) {
		return Lang.raw("upgrade.effect." + effectKey, UpgradeEffects.displayValue(effectKey, value));
	}
}
