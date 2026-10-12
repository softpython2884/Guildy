package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;

/**
 * villagers.json5 → chunkLoading: chunks kept active around working guild villagers (see {@link WorkChunkLoader}).
 * Must stay free of Minecraft classes (unit tests).
 */
@Json5.Section
public final class ChunkLoadingConfig {
	@Comment({
			"Quand garder les chunks actifs : \"online\" = tant qu'au moins un membre de la guilde est connecte (par defaut),",
			"\"always\" = toujours, meme sans aucun joueur connecte, \"never\" = jamais (les villageois ne travaillent que pres des joueurs)"
	})
	public String mode = "online";
	@Comment("Rayon (chunks) maintenu actif autour de chaque villageois au travail et de sa destination : 0 = son chunk, 1 = 3x3 (0 a 3)")
	public int radius = 1;
	@Comment({
			"Rayon maximum (chunks) maintenu actif autour du fanion de la zone de travail (le rayon de la zone, plafonne a cette",
			"valeur) ou, sans zone, du point du territoire le plus proche (bucheron, fermier) ; 0 a 5"
	})
	public int zoneRadius = 2;
	@Comment({
			"Nombre maximum de chunks maintenus actifs par guilde (0 a 1024) : les villageois eux-memes d'abord (les plus proches",
			"du coeur en premier), puis leurs destinations, leurs zones, enfin les coffres du rangement"
	})
	public int maxChunksPerGuild = 64;
	@Comment("Nombre maximum de chunks maintenus actifs sur tout le serveur, toutes guildes confondues, servies a tour de role (0 a 8192)")
	public int maxChunksTotal = 512;
	@Comment({
			"Intervalle (secondes) entre deux mises a jour des tickets (1 a 5) ; un ticket qui n'est plus renouvele expire seul",
			"apres 10 secondes"
	})
	public int refreshSeconds = 2;
	@Comment({
			"A la tombee de la nuit, duree (secondes) pendant laquelle un villageois qui rentre a son lit garde ses chunks",
			"actifs (0 = aucune, jusqu'a 600) ; couche, il ne charge plus rien jusqu'au matin"
	})
	public int bedTravelSeconds = 90;
	@Comment("Garder aussi actifs les chunks des gardes (inutile en general : aucun monstre n'apparait loin des joueurs)")
	public boolean guards = false;

	/** Parsed {@link #mode} (valid after {@link #validate}). */
	public ChunkLoadPlanner.Mode mode() {
		ChunkLoadPlanner.Mode parsed = ChunkLoadPlanner.Mode.parse(mode);
		return parsed == null ? ChunkLoadPlanner.Mode.ONLINE : parsed;
	}

	/** Bounds every value; an unknown mode becomes "online". @return true when the mode had to be replaced */
	public boolean validate() {
		ChunkLoadPlanner.Mode parsed = ChunkLoadPlanner.Mode.parse(mode);
		boolean replaced = parsed == null;
		mode = (parsed == null ? ChunkLoadPlanner.Mode.ONLINE : parsed).id();
		radius = clamp(radius, 0, 3);
		zoneRadius = clamp(zoneRadius, 0, 5);
		maxChunksPerGuild = clamp(maxChunksPerGuild, 0, 1024);
		maxChunksTotal = clamp(maxChunksTotal, 0, 8192);
		refreshSeconds = clamp(refreshSeconds, 1, 5);
		bedTravelSeconds = clamp(bedTravelSeconds, 0, 600);
		return replaced;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
