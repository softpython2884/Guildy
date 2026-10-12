package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;

/**
 * villagers.json5, section "night": workers going home before the night and their shelter at nightfall / when a horde
 * starts. Guards are not concerned (they stay out), nor villagers without a task. Free of Minecraft classes.
 */
@Json5.Section
public final class NightConfig {
	@Comment({
			"Heure (ticks du jour, 0 a 24000) a laquelle les travailleurs arretent le travail et rentrent a leur lit, avec un",
			"bonus de vitesse (repere : coucher des villageois vanilla 12000, horde 13000) ; -1 = jamais (repos vanilla a 12000)"
	})
	public int homeTime = 11000;
	@Comment("Niveau de l'effet Vitesse pendant le retour (0 = aucun, 2 = Vitesse II)")
	public int speedLevel = 1;
	@Comment({
			"Tombee de la nuit (ticks du jour) : un travailleur encore dehors (ni pres de son lit, ni pres d'un lit du territoire,",
			"ni au bureau d'accueil) est teleporte a cote de son lit (sinon d'un lit libre du territoire, du bureau d'accueil, du",
			"coeur) ; -1 = jamais (il continue de marcher)"
	})
	public int shelterTime = 12500;
	@Comment("Au debut d'une horde (de nuit ou d'evenement), les travailleurs dehors sont mis a l'abri de la meme facon")
	public boolean shelterOnHorde = true;
	@Comment("Distance (blocs) a son lit (ou au bureau d'accueil, sans lit) en dessous de laquelle un villageois est a l'abri")
	public int safeRadius = 4;
	@Comment({
			"Un travailleur sans lit prend un lit libre du territoire en rentrant (les villageois vanilla ne cherchent un lit",
			"qu'a moins de 48 blocs d'eux et perdent le leur s'ils ne l'atteignent pas pendant une minute)"
	})
	public boolean claimFreeBeds = true;

	void validate() {
		homeTime = time(homeTime);
		shelterTime = time(shelterTime);
		if (homeTime >= 0 && shelterTime >= 0 && homeTime > shelterTime) {
			homeTime = shelterTime;
		}
		speedLevel = Math.max(0, Math.min(5, speedLevel));
		safeRadius = Math.max(2, Math.min(16, safeRadius));
	}

	/** -1 (off) or a time of day in [0, 23999]. */
	private static int time(int value) {
		return value < 0 ? -1 : Math.min(23999, value);
	}
}
