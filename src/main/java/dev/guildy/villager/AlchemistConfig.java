package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;

/**
 * villagers.json5, section "alchemist": the "Alchimiste" task (turns plain books into enchanted books at the best
 * enchanting table of the territory). Free of Minecraft classes (unit tests).
 */
@Json5.Section
public final class AlchemistConfig {
	@Comment("Niveau de guilde minimum pour donner cette tache a un villageois (1 a 100)")
	public int minGuildLevel = 5;
	@Comment({
			"Temps de travail (secondes) par niveau d'enchantement : un livre de niveau 30 prend 30 x cette valeur ; le bonus",
			"de vitesse de travail s'applique (1 a 120). Le niveau suit la 3e ligne de la table vanilla selon le nombre de",
			"bibliotheques (15 bibliotheques : niveau 30 ; table seule : niveau 1 a 8)"
	})
	public int secondsPerLevel = 6;
	@Comment("Livres enchantes au plus par alchimiste et par jour de jeu (1 a 1000)")
	public int maxBooksPerDay = 8;
	@Comment({
			"Lapis-lazuli consomme par livre (0 a 3) ; 0 = seulement du temps et des livres. Pris dans son sac puis dans les",
			"coffres du stockage, comme les livres"
	})
	public int lapisPerBook = 0;
	@Comment("Livres pris dans les coffres du stockage en un voyage (1 a 64)")
	public int booksPerTrip = 4;
	@Comment("Intervalle minimum (secondes) entre deux recherches des tables d'enchantement du territoire (10 a 3600)")
	public int scanIntervalSeconds = 120;

	void validate() {
		minGuildLevel = clamp(minGuildLevel, 1, 100);
		secondsPerLevel = clamp(secondsPerLevel, 1, 120);
		maxBooksPerDay = clamp(maxBooksPerDay, 1, 1000);
		lapisPerBook = clamp(lapisPerBook, 0, 3);
		booksPerTrip = clamp(booksPerTrip, 1, 64);
		scanIntervalSeconds = clamp(scanIntervalSeconds, 10, 3600);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
