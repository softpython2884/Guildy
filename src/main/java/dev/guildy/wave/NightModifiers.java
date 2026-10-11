package dev.guildy.wave;

/**
 * Temporary balance changes applied to ONE wave by an event (blood night, pillager raid...). Read when the wave is planned
 * (size, elites, boss), when its mobs spawn (health, damage) and when it ends (rewards). {@link #NONE} = normal balance.
 * Pure data: night waves get theirs from {@link WaveApi#nightModifiers}, event waves from their {@link WaveApi.EventWave}.
 *
 * @param label              colored name shown on the progress bar, start message and recap ("" = none)
 * @param mobCountMultiplier horde size multiplier (still capped by difficulty.maxMobsPerWave)
 * @param eliteBonus         added to the elite chance...
 * @param eliteCap           ...which then cannot exceed this cap (the cap never lowers the normal chance)
 * @param healthMultiplier   extra health multiplier of every mob of the wave
 * @param damageMultiplier   extra melee damage multiplier of every mob of the wave
 * @param bossChance         chance (0..1) that the wave gets a boss when it would not have one
 * @param goldMultiplier     victory gold multiplier
 * @param xpMultiplier       victory guild XP multiplier
 * @param itemRollMultiplier victory item rolls multiplier
 */
public record NightModifiers(String label, double mobCountMultiplier, double eliteBonus, double eliteCap, double healthMultiplier,
		double damageMultiplier, double bossChance, double goldMultiplier, double xpMultiplier, double itemRollMultiplier) {

	public static final NightModifiers NONE = new NightModifiers("", 1, 0, 1, 1, 1, 0, 1, 1, 1);

	public NightModifiers {
		label = label == null ? "" : label;
		mobCountMultiplier = WaveFormula.clamp(mobCountMultiplier, 0.1, 10);
		eliteBonus = WaveFormula.clamp(eliteBonus, 0, 1);
		eliteCap = WaveFormula.clamp(eliteCap, 0, 1);
		healthMultiplier = WaveFormula.clamp(healthMultiplier, 0.1, 50);
		damageMultiplier = WaveFormula.clamp(damageMultiplier, 0.1, 50);
		bossChance = WaveFormula.clamp(bossChance, 0, 1);
		goldMultiplier = WaveFormula.clamp(goldMultiplier, 0, 100);
		xpMultiplier = WaveFormula.clamp(xpMultiplier, 0, 100);
		itemRollMultiplier = WaveFormula.clamp(itemRollMultiplier, 0, 100);
	}

	/** Same values with only the reward multipliers (gold, XP, item rolls) set. */
	public static NightModifiers rewards(String label, double gold, double xp, double itemRolls) {
		return new NightModifiers(label, 1, 0, 1, 1, 1, 0, gold, xp, itemRolls);
	}

	public boolean hasLabel() {
		return !label.isBlank();
	}

	/** Elite chance after the bonus, capped by {@link #eliteCap} (never below the normal chance). */
	public double eliteChance(double base) {
		if (eliteBonus <= 0) {
			return base;
		}
		return Math.max(base, Math.min(eliteCap, base + eliteBonus));
	}

	public long gold(long base) {
		return Math.max(0, Math.round(base * goldMultiplier));
	}

	public long xp(long base) {
		return Math.max(0, Math.round(base * xpMultiplier));
	}

	public int itemRolls(int base) {
		return (int) Math.max(0, Math.min(1000, Math.round(base * itemRollMultiplier)));
	}
}
