package dev.guildy.ruin;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;

/**
 * Section {@code escalation.dread} of ruins.json5: the dread form ("Forme redoutable") of the guardians from the threat
 * level {@code escalation.dreadThreshold} (VII by default): much more health (an effective health beyond the vanilla cap
 * of 1024, see {@link DreadRules#vitality}), more damage to the players (after their armour, the guardian and its escort),
 * a faster rhythm, its own telegraphed abilities (ruins.json5 {@code dreadAbilities} of each ruin) and better rewards.
 * Free of Minecraft classes (unit tests); {@link #fix} clamps every value.
 */
@Json5.Section
public final class DreadSettings {
	@Comment("Forme redoutable active (false = les gardiens restent les memes a tous les niveaux de menace)")
	public boolean enabled = true;

	@Comment({
			"Vie du gardien redoutable (multiplicateur, en plus de la menace, du groupe et de la malediction) : healthMultiplier au",
			"seuil, plus healthPerLevel par niveau au-dela, au plus maxHealthMultiplier. La vie depasse le plafond vanilla (1024) :",
			"au-dela, chaque coup recu est divise d'autant (vie effective, affichee sur la barre du boss)"
	})
	public double healthMultiplier = 2.5;
	public double healthPerLevel = 0.5;
	public double maxHealthMultiplier = 8.0;
	@Comment("Vie effective maximale par joueur compte a l'apparition (0 = sans plafond ; 8000 = un groupe de 4 : 32000 au plus)")
	public double healthCapPerPlayer = 8000;

	@Comment({
			"Degats aux joueurs (multiplicateur applique apres leur armure, a toutes les attaques du gardien et de son escorte) :",
			"damageMultiplier au seuil (1.5 = +50 %), plus damagePerLevel par niveau au-dela (X : +125 %), au plus maxDamageMultiplier"
	})
	public double damageMultiplier = 1.5;
	public double damagePerLevel = 0.25;
	public double maxDamageMultiplier = 4.0;
	@Comment({
			"Ce bonus ne fait jamais depasser a un coup cette part de la vie maximale du joueur touche (0.8 = 80 % ; un coup deja",
			"plus fort sans le bonus reste tel quel ; 0 = sans limite)"
	})
	public double maxHitHealthRatio = 0.8;
	@Comment("Vie des sbires de l'escorte d'un gardien redoutable (multiplicateur)")
	public double escortHealthMultiplier = 1.5;

	@Comment({
			"Rythme : recharges de toutes ses capacites multipliees par cooldownFactor au seuil, moins cooldownPerLevel par niveau",
			"au-dela, jamais sous minCooldownFactor (en plus de la menace, de la rage et de la malediction)"
	})
	public double cooldownFactor = 0.8;
	public double cooldownPerLevel = 0.05;
	public double minCooldownFactor = 0.5;

	@Comment({
			"Capacites redoutables (liste 'dreadAbilities' de chaque ruine) : delai avant la premiere, delai minimal entre deux,",
			"nombre au plus en meme temps, et duree de l'alerte (cercles rouges, lignes, cones) avant qu'elles ne frappent"
	})
	public double firstAttackSeconds = 6.0;
	public double globalCooldownSeconds = 4.0;
	public int maxRunning = 2;
	public double telegraphSeconds = 1.25;

	@Comment({
			"Recompenses : or et XP de guilde multiplies (goldMultiplier au seuil + goldPerLevel par niveau au-dela), tirages en plus",
			"dans la table de butin, chance d'un tirage de parchemin et d'arme unique en plus"
	})
	public double goldMultiplier = 1.5;
	public double goldPerLevel = 0.1;
	public double xpMultiplier = 1.5;
	public double xpPerLevel = 0.1;
	public int extraLootRolls = 1;
	public double extraScrollChance = 0.25;
	public double extraUniqueChance = 0.1;

	@Comment("Particules envoyees au maximum par tick pour toutes les capacites redoutables (au-dela, l'animation est allegee)")
	public int particleBudget = 900;

	/** Clamps every value (pure). */
	public void fix() {
		healthMultiplier = clamp(healthMultiplier, 1, 20);
		healthPerLevel = clamp(healthPerLevel, 0, 5);
		maxHealthMultiplier = clamp(maxHealthMultiplier, healthMultiplier, 50);
		healthCapPerPlayer = clamp(healthCapPerPlayer, 0, 1_000_000);
		damageMultiplier = clamp(damageMultiplier, 1, 10);
		damagePerLevel = clamp(damagePerLevel, 0, 2);
		maxDamageMultiplier = clamp(maxDamageMultiplier, damageMultiplier, 20);
		maxHitHealthRatio = clamp(maxHitHealthRatio, 0, 5);
		escortHealthMultiplier = clamp(escortHealthMultiplier, 1, 10);
		cooldownFactor = clamp(cooldownFactor, 0.1, 1);
		cooldownPerLevel = clamp(cooldownPerLevel, 0, 0.5);
		minCooldownFactor = clamp(minCooldownFactor, 0.1, cooldownFactor);
		firstAttackSeconds = clamp(firstAttackSeconds, 0, 120);
		globalCooldownSeconds = clamp(globalCooldownSeconds, 0.5, 60);
		maxRunning = Math.max(1, Math.min(8, maxRunning));
		telegraphSeconds = clamp(telegraphSeconds, 0.5, 5);
		goldMultiplier = clamp(goldMultiplier, 1, 10);
		goldPerLevel = clamp(goldPerLevel, 0, 2);
		xpMultiplier = clamp(xpMultiplier, 1, 10);
		xpPerLevel = clamp(xpPerLevel, 0, 2);
		extraLootRolls = Math.max(0, Math.min(10, extraLootRolls));
		extraScrollChance = clamp(extraScrollChance, 0, 1);
		extraUniqueChance = clamp(extraUniqueChance, 0, 1);
		particleBudget = Math.max(100, Math.min(10_000, particleBudget));
	}

	static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
