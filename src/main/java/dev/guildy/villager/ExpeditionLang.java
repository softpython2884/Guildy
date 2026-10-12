package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the expedition destinations (keys "villager.destination.*", "villager.gui.destination*",
 * "villager.expedition.departed.*").
 */
final class ExpeditionLang {
	private ExpeditionLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// default destinations (the others are named in villagers.json5)
		m.put("villager.destination." + ExpeditionDestinations.PLAINS, "Forêt et plaines");
		m.put("villager.destination." + ExpeditionDestinations.NETHER_WASTES, "Terres du Nether");
		m.put("villager.destination.risk.low", "&afaible");
		m.put("villager.destination.risk.medium", "&emodéré");
		m.put("villager.destination.risk.high", "&6élevé");
		m.put("villager.destination.risk.very_high", "&ctrès élevé");
		// messages
		m.put("villager.expedition.departed.explorer", "&e{0}&7 est parti explorer : &f{1}&7 (retour dans ~&f{2} min&7).");
		m.put("villager.expedition.departed.nether", "&e{0}&7 est parti au Nether : &f{1}&7 (retour dans ~&f{2} min&7).");
		m.put("villager.status.going_departure_to", "&eSe prépare au départ : {0}");
		m.put("villager.destination.set", "&a{0} partira explorer : &e{1}&a.");
		m.put("villager.destination.set.nether", "&a{0} partira au Nether : &e{1}&a.");
		m.put("villager.destination.set_next", "&a{0} est en voyage : prochaine destination &e{1}&a.");
		m.put("villager.destination.locked", "&c« {0} » demande une guilde de niveau {1}.");
		m.put("villager.destination.unknown", "&cCette destination n'existe plus.");
		m.put("villager.destination.no_expedition", "&7{0} ne part pas en expédition : donnez-lui la tâche « Exploration » ou « Expédition au Nether ».");
		// villager menus
		m.put("villager.gui.lore.destination", "&7Destination : &f{0}");
		m.put("villager.gui.destination", "&b&lDestination : &f{0}");
		m.put("villager.gui.destination.click", "&eClic : choisir la destination");
		m.put("villager.gui.destination.away", "&dVoyage en cours : &f{0}");
		m.put("villager.gui.destination.duration", "&7Durée : &f~{0} min");
		m.put("villager.gui.destination.risk", "&7Risque de ne pas revenir : {0} &8({1} %)");
		m.put("villager.gui.destination.level", "&7Niveau de guilde requis : &a{0}");
		m.put("villager.gui.destination.typical", "&7Butin typique :");
		m.put("villager.gui.destination.loot_line", "&8- &f{0} &7×{1}-{2}");
		m.put("villager.gui.destination.loot_line.fixed", "&8- &f{0} &7×{1}");
		m.put("villager.gui.destination.rare", "&7Trouvailles rares : &b{0}");
		m.put("villager.gui.destination.map", "&7Carte au trésor : &f{0} %");
		m.put("villager.gui.destination.unique", "&dArmes uniques : chance ×{0}");
		m.put("villager.gui.destination.favoured", "&aFavorise : &f{0} &8(×{1})");
		m.put("villager.gui.destination.name", "&e{0}");
		m.put("villager.gui.destination.name.current", "&a&l{0}");
		m.put("villager.gui.destination.name.locked", "&8{0}");
		m.put("villager.gui.destination.current", "&a▶ Destination actuelle");
		m.put("villager.gui.destination.choose", "&eClic : choisir cette destination");
		m.put("villager.gui.destination.locked", "&cVerrouillée : niveau de guilde {0} requis &8(guilde : {1})");
		m.put("villager.gui.destinations.title", "Exploration : {0}");
		m.put("villager.gui.destinations.title.nether", "Nether : {0}");
		m.put("villager.gui.destinations.help.lore", "&7Choisissez où {0} part en expédition.\n&7Les lieux lointains ou dangereux rapportent\n&7un meilleur butin, mais le risque de ne\n&7jamais revenir est plus grand.\n&7Ses objectifs prioritaires trouvés là-bas\n&7sont favorisés (poids ×{1}).\n&7Le choix vaut pour les prochains départs.");
		m.put("villager.gui.destinations.help.nether", "&dDépart par un portail du Nether construit\n&ddans le territoire.");
		Lang.addDefaults(m);
	}
}
