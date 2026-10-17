package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;

/**
 * villagers.json5, section "blacksmith": the "Forgeron" task (repairs the equipment worn by the armor stands of the
 * territory at the nearest anvil). Free of Minecraft classes (unit tests).
 */
@Json5.Section
public final class BlacksmithConfig {
	@Comment("Niveau de guilde minimum pour donner cette tache a un villageois (1 a 100)")
	public int minGuildLevel = 3;
	@Comment({
			"Distance maximum (blocs) entre un porte-armure et l'enclume utilisee (la plus proche, dans le territoire) ;",
			"un porte-armure sans enclume assez proche est ignore (2 a 32)"
	})
	public int anvilRadius = 12;
	@Comment("Temps de travail (secondes) pour une etape de reparation ; le bonus de vitesse de travail s'applique (1 a 600)")
	public int secondsPerRepair = 8;
	@Comment({
			"Durabilite rendue a chaque etape, en pourcentage de la durabilite maximum de l'objet (1 a 100). L'objet le plus",
			"abime du porte-armure (plus petite part de durabilite restante) passe en premier ; il reste toujours sur le porte-armure"
	})
	public int repairPercent = 10;
	@Comment({
			"Chaque etape est une utilisation de l'enclume : chance (0 a 1) de l'abimer d'un cran comme en vanilla (0.12) :",
			"enclume -> ebrechee -> endommagee -> detruite. Detruite : le forgeron cherche une autre enclume"
	})
	public double anvilDamageChance = 0.12;
	@Comment("Intervalle minimum (secondes) entre deux recherches des porte-armures du territoire (5 a 600)")
	public int scanIntervalSeconds = 30;

	void validate() {
		minGuildLevel = clamp(minGuildLevel, 1, 100);
		anvilRadius = clamp(anvilRadius, 2, 32);
		secondsPerRepair = clamp(secondsPerRepair, 1, 600);
		repairPercent = clamp(repairPercent, 1, 100);
		anvilDamageChance = Double.isNaN(anvilDamageChance) ? 0.12 : Math.max(0.0, Math.min(1.0, anvilDamageChance));
		scanIntervalSeconds = clamp(scanIntervalSeconds, 5, 600);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
