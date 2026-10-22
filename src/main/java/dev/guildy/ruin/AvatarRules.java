package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.DreadAbilityDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules (no Minecraft classes, unit-tested) of the Avatar, the super form of a cursed guardian: only a cursed
 * awakening with the Nether star in the circle summons it (never a normal one); it fights at least at {@code minThreat}
 * (dread form included); its effective health is the cursed one times its own multiplier, kept between a floor and a cap per
 * player; it hits harder (after armour) than its dread form; its abilities are its theme's dread abilities amplified plus
 * the Nether attacks of its phase; it keeps the cursed phases and the final form; its rewards come on top of the curse.
 */
public final class AvatarRules {
	private AvatarRules() {
	}

	/** An Avatar rises: a cursed awakening, the Avatar enabled (and the curse) and the star found in the circle. */
	public static boolean summons(boolean avatarEnabled, boolean cursedEnabled, boolean cursedRitual, boolean starFound) {
		return avatarEnabled && cursedEnabled && cursedRitual && starFound;
	}

	/** Threat level the Avatar fights at: the ruin's, never under {@code minThreat}. */
	public static int threat(int level, int minThreat) {
		return Math.max(Math.max(1, level), Math.max(1, minThreat));
	}

	/** Effective health: {@code uncapped} (cursed, threat, group, dread) times its multiplier, between the floor and the cap. */
	public static double health(double uncapped, int players, AvatarSettings a) {
		return DreadRules.clampHealth(uncapped * Math.max(1, a.healthMultiplier), players, a.healthMinPerPlayer, a.healthCapPerPlayer);
	}

	/** Damage factor on the players (after armour): its dread form's times its own. */
	public static double playerDamage(double dreadDamage, AvatarSettings a) {
		return Math.max(1, dreadDamage) * Math.max(1, a.damageMultiplier);
	}

	/**
	 * Its abilities: its theme's dread abilities, amplified and used from the first phase, then the Nether attacks (each from
	 * its own phase).
	 */
	public static List<DreadAbilityDef> abilities(List<DreadAbilityDef> theme, List<DreadAbilityDef> nether, AvatarSettings a) {
		List<DreadAbilityDef> list = new ArrayList<>();
		if (theme != null) {
			for (DreadAbilityDef def : theme) {
				DreadAbilityDef amplified = def.amplified(a.themeDamageMultiplier, a.themeCountBonus);
				amplified.phase = CursedRules.FIRST_PHASE;
				list.add(amplified);
			}
		}
		if (nether != null) {
			list.addAll(nether);
		}
		return list;
	}

	/** Cooldown factor of its attacks: faster in its final form. */
	public static double cooldownFactor(boolean finalForm, AvatarSettings a) {
		return finalForm ? Math.max(0.2, Math.min(1, a.finalFormCooldownFactor)) : 1.0;
	}

	/** Phase used to unlock its attacks: its cursed phase, every attack in its final form. */
	public static int unlockPhase(int phase, boolean finalForm) {
		return finalForm ? CursedRules.LAST_PHASE : Math.max(CursedRules.FIRST_PHASE, Math.min(CursedRules.LAST_PHASE, phase));
	}

	/** Rewards of its fall: the cursed (and dread) rewards with its multipliers and extra rolls on top. */
	public static ThreatRules.Rewards rewards(ThreatRules.Rewards cursed, AvatarSettings.RewardSettings r) {
		ThreatRules.Rewards b = cursed == null ? ThreatRules.Rewards.NONE : cursed;
		return new ThreatRules.Rewards(b.gold() * Math.max(1, r.goldMultiplier), b.xp() * Math.max(1, r.xpMultiplier),
				Math.min(20, b.extraLootRolls() + Math.max(0, r.extraLootRolls)), b.extraUniqueChance(), b.extraScrollChance(),
				Math.min(20, b.bonusUniqueRolls() + Math.max(0, r.extraUniqueRolls)), Math.min(20, b.bonusScrollRolls() + Math.max(0, r.extraScrollRolls)));
	}
}
