package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;

/**
 * villagers.json5, section "vitality": health of the guild villagers by guild level and their slow regeneration.
 * Free of Minecraft classes (unit tests).
 */
@Json5.Section
public final class VitalityConfig {
	@Comment("Points de vie max ajoutes par niveau de guilde au-dessus du niveau 1 (tous les villageois ; 2 points = 1 coeur, un villageois en a 20)")
	public double healthPerLevel = 4.0;
	@Comment("Gardes : le bonus par niveau est multiplie par cette valeur")
	public double guardMultiplier = 1.5;
	@Comment("Gardes : points de vie max ajoutes des le niveau 1")
	public double guardBonus = 10.0;
	@Comment("Bonus de vie maximum (points de vie)")
	public double maxBonus = 200.0;
	@Comment({
			"Regeneration lente hors combat des villageois qui ne sont pas gardes (points de vie par seconde, 0 = aucune ;",
			"les villageois vanilla ne regenerent pas seuls). Gardes : guard.passiveRegenPerSecond"
	})
	public double regenPerSecond = 0.1;
	@Comment("Hors combat = ni blesse, ni en train de combattre (gardes) depuis ce nombre de secondes")
	public int outOfCombatSeconds = 10;

	/** Extra max health of a villager of a guild of this level (see {@link VitalityRules#bonusHealth}). */
	public double bonusHealth(int guildLevel, boolean guard) {
		return VitalityRules.bonusHealth(guildLevel, guard, healthPerLevel, guardMultiplier, guardBonus, maxBonus);
	}

	void validate() {
		healthPerLevel = clamp(healthPerLevel, 0.0, 100.0);
		guardMultiplier = clamp(guardMultiplier, 0.0, 10.0);
		guardBonus = clamp(guardBonus, 0.0, 200.0);
		maxBonus = clamp(maxBonus, 0.0, 1000.0);
		regenPerSecond = clamp(regenPerSecond, 0.0, 20.0);
		outOfCombatSeconds = Math.max(0, Math.min(600, outOfCombatSeconds));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
