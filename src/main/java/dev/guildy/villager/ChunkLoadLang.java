package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the chunk loading of working villagers ({@link WorkChunkLoader}): villagers menu item, villager
 * lore and the admin command /guild admin villager chunks.
 */
final class ChunkLoadLang {
	private ChunkLoadLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("villager.chunks.mode.online", "&aquand un membre est connecté");
		m.put("villager.chunks.mode.always", "&atoujours");
		m.put("villager.chunks.mode.never", "&cjamais");
		// villagers menu
		m.put("villager.chunks.menu.name", "&b&lChunks des villageois");
		m.put("villager.chunks.menu.lore.active", "&7Chargement : {0}\n&7Chunks maintenus actifs : &f{1}&7 / {2}\n"
				+ "&7Les villageois au travail gardent chargés\n&7leurs alentours, leur destination et leur\n"
				+ "&7zone : ils travaillent même sans joueur\n&7à proximité. Couchés la nuit, ils ne\n&7chargent plus rien jusqu'au matin.");
		m.put("villager.chunks.menu.lore.never", "&7Chargement : {0}\n&7Les villageois ne travaillent que lorsqu'un\n&7joueur est dans les parages.");
		m.put("villager.chunks.menu.lore.capped", "&e{0} villageois au-delà de la limite :\n&eleurs chunks ne sont pas tous gardés actifs.");
		// villager icon
		m.put("villager.chunks.lore.kept", "&8Chunks maintenus actifs (sans joueur)");
		m.put("villager.chunks.lore.capped", "&8Chunks : limite de chargement atteinte");
		// admin
		m.put("villager.chunks.admin.header", "&6Chunks des villageois : mode {0}&6, {1} chunk(s) actif(s) (max {2}), {3} ticket(s), "
				+ "{4} chunk(s) max par guilde");
		m.put("villager.chunks.admin.guild", "&7- &f{0}&7 : {1} chunk(s), {2} villageois maintenu(s), {3} au-delà de la limite");
		m.put("villager.chunks.admin.none", "&7Aucun chunk maintenu actif par les villageois.");
		m.put("villager.chunks.admin.pause", "&eLe serveur se met en pause après {0} s sans joueur connecté "
				+ "(pause-when-empty-seconds) : rien ne tourne alors, villageois compris. Mettez pause-when-empty-seconds=0 "
				+ "dans server.properties pour le mode always.");
		Lang.addDefaults(m);
	}
}
