package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;

/**
 * villagers.json5, section "farmer.fieldWork": the field works of the farmers in their field zones (tilling,
 * clearing, levelling, bone meal, retilling the trampled farmland), allowed zone by zone by the "Autorisations du
 * fanion". Free of Minecraft classes.
 */
@Json5.Section
public final class FieldWorkConfig {
	@Comment({
			"Travaux des champs (zones de champs seulement, selon les autorisations du fanion de chaque zone) : labourer, degager",
			"le sol, creuser des points d'eau, aplanir, poudre d'os, relabourer la terre pietinee. false = aucun de ces travaux",
			"(le fermier recolte, replante et seme seulement)"
	})
	public boolean enabled = true;
	@Comment("Actions (labour, trou d'eau, aplanissement, poudre d'os...) par seconde au plus (1 a 16)")
	public int actionsPerSecond = 3;
	@Comment("Actions par tournee de travaux avant de chercher de nouveau des cultures mures (1 a 512)")
	public int perRound = 48;
	@Comment("Chunks de la zone inspectes par seconde avant une tournee de travaux (1 a 16)")
	public int surveyChunksPerStep = 2;
	@Comment("Attente (secondes) avant d'inspecter de nouveau un champ ou il n'y avait plus rien a faire (10 a 3600)")
	public int pauseSeconds = 60;
	@Comment({
			"Terres labourees redevenues de la terre (pietinees, dessechees) retenues par zone pour etre relabourees puis",
			"ressemees (0 a 4096, 0 = rien n'est retenu)"
	})
	public int retillMemory = 256;
	@Comment("Prendre de la terre (aplanir) et de la poudre d'os dans les coffres du stockage quand son sac n'en a plus")
	public boolean fromStorage = true;
	@Comment("Terre ou poudre d'os prise au stockage en un voyage (1 a 64)")
	public int fetchAmount = 16;
	@Comment("Poudre d'os : cultures traitees par tournee au plus (1 a 256)")
	public int boneMealPerRound = 16;

	void validate() {
		actionsPerSecond = clamp(actionsPerSecond, 1, 16);
		perRound = clamp(perRound, 1, 512);
		surveyChunksPerStep = clamp(surveyChunksPerStep, 1, 16);
		pauseSeconds = clamp(pauseSeconds, 10, 3600);
		retillMemory = clamp(retillMemory, 0, 4096);
		fetchAmount = clamp(fetchAmount, 1, 64);
		boneMealPerRound = clamp(boneMealPerRound, 1, 256);
	}

	static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
