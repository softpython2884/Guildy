package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.ScalingSettings;

/**
 * Pure rules (no Minecraft classes, unit-tested) of the group scaling of a ruin fight: the more survival / adventure
 * players are present when a guardian appears, the stronger it is (health, damage of its attack and abilities, a bigger
 * escort) and the more it gives (gold and guild XP, the gold being shared between the participants).
 * <p>
 * Combination with the threat level ({@link ThreatRules}): both are multipliers of the configured value, applied one after
 * the other (vanilla multiplies {@code ADD_MULTIPLIED_TOTAL} modifiers), so a guardian at threat level III (+40 % health)
 * facing 3 players (+100 %) has {@code 1.4 x 2.0 = 2.8} times its configured health; the extra escort mobs add up.
 */
public final class GroupScaling {
	/** Statistics of a fight for a group of {@code players} (players = 1: no change). */
	public record Party(int players, double health, double damage, int extraMinions, double gold, double xp) {
		public static final Party NONE = new Party(1, 1.0, 1.0, 0, 1.0, 1.0);

		/** More than one player counted. */
		public boolean group() {
			return players > 1;
		}
	}

	private GroupScaling() {
	}

	/** Players counted: at least one, at most {@code maxPlayers}. */
	public static int players(int counted, int maxPlayers) {
		return Math.max(1, Math.min(Math.max(1, maxPlayers), counted));
	}

	/** The group statistics for {@code counted} players present (NONE when disabled or alone). */
	public static Party party(ScalingSettings s, int counted) {
		if (s == null || !s.enabled) {
			return Party.NONE;
		}
		int n = players(counted, s.maxPlayers);
		if (n <= 1) {
			return Party.NONE;
		}
		return new Party(n, ThreatRules.multiplier(s.healthPerPlayer, n), ThreatRules.multiplier(s.damagePerPlayer, n), extraMinions(s.minionsPerPlayer, n),
				ThreatRules.multiplier(s.goldPerPlayer, n), ThreatRules.multiplier(s.xpPerPlayer, n));
	}

	/** Escort mobs added: {@code perPlayer} for each player beyond the first, rounded down. */
	public static int extraMinions(double perPlayer, int players) {
		return (int) Math.floor(Math.max(0, perPlayer) * Math.max(0, players - 1) + 1.0E-9);
	}

	/** Threat level and group combined: multiplicative. */
	public static double combined(double threat, double group) {
		return Math.max(0, threat) * Math.max(0, group);
	}

	/** {@code factor - 1} as a whole percentage ("+50 %"). */
	public static int percent(double factor) {
		return (int) Math.round((factor - 1.0) * 100);
	}

	/**
	 * Splits {@code total} between {@code recipients} (the first ones get one more when it does not divide evenly).
	 *
	 * @return one share per recipient (a single share holding everything when there is nobody)
	 */
	public static int[] shares(int total, int recipients) {
		int amount = Math.max(0, total);
		if (recipients <= 1) {
			return new int[] {amount};
		}
		int[] shares = new int[recipients];
		int base = amount / recipients;
		int rest = amount % recipients;
		for (int i = 0; i < recipients; i++) {
			shares[i] = base + (i < rest ? 1 : 0);
		}
		return shares;
	}
}
