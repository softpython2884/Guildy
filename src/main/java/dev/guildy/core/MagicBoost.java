package dev.guildy.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * Magic boosts of a player (elixirs brewed by the alchemists, implemented by dev.guildy.alchemy): read by the spell
 * scrolls and grimoires at the single points where a cast's power, its cooldown, its channel and a grimoire's food cost
 * are computed. The default provider changes nothing; every call is safe (a failing provider means no boost).
 */
public final class MagicBoost {
	/** What the alchemy module provides; multipliers of 1 = no boost. */
	public interface Provider {
		/** Damage, heals and durations of the player's spells (1 = unchanged, 1.25 = +25 %). */
		default double power(ServerPlayer player) {
			return 1.0;
		}

		/** Cooldown of a scroll or grimoire cast (1 = unchanged, 0.7 = −30 %). */
		default double cooldown(ServerPlayer player) {
			return 1.0;
		}

		/** Food cost of a grimoire cast after the boost. */
		default int food(ServerPlayer player, int cost) {
			return cost;
		}

		/** Shortens the item use the player just started (the incantation), when a boost does. */
		default void shortenUse(ServerPlayer player) {
		}
	}

	public static Provider provider = new Provider() {
	};

	private MagicBoost() {
	}

	/** Spell power multiplier of the player (1 = none). */
	public static double powerMultiplier(ServerPlayer player) {
		double value = Safe.get("magic.power", () -> provider.power(player), 1.0);
		return Double.isFinite(value) && value > 0 ? value : 1.0;
	}

	/** Cooldown multiplier of the player (1 = none). */
	public static double cooldownMultiplier(ServerPlayer player) {
		double value = Safe.get("magic.cooldown", () -> provider.cooldown(player), 1.0);
		return Double.isFinite(value) && value > 0 ? Math.min(1.0, value) : 1.0;
	}

	/** Food points of a grimoire cast of {@code cost} for this player (never more than the cost). */
	public static int foodCost(ServerPlayer player, int cost) {
		int value = Safe.get("magic.food", () -> provider.food(player, cost), cost);
		return Math.max(0, Math.min(cost, value));
	}

	/**
	 * Shortens the incantation the player just started ({@code startUsingItem}) when a boost does; call before reading the
	 * remaining use ticks.
	 */
	public static void shortenChannel(ServerPlayer player) {
		Safe.run("magic.channel", () -> provider.shortenUse(player));
	}
}
