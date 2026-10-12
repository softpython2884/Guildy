package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the gathering work (lumberjack / farmer search, automatic mode around the territory), of
 * "Appeler ici" and of the "objectifs prioritaires" (wishlist) menus.
 */
final class WorkLang {
	private WorkLang() {
	}

	/** Lore of the automatic mode of a zone type (lumberjacks and farmers now work around the territory). */
	static String autoLoreKey(ZoneType type) {
		return switch (type) {
			case FOREST -> "villager.work.auto.lore.forest";
			case FARM -> "villager.work.auto.lore.farm";
			default -> "villager.gui.picker.auto.lore." + type.id();
		};
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// lumberjack / farmer statuses
		m.put("villager.work.lumberjack.searching_zone", "&7Cherche un arbre dans « {0} » &8({1}/{2})");
		m.put("villager.work.lumberjack.searching_auto", "&7Cherche un arbre près du territoire &8({0}/{1})");
		m.put("villager.work.lumberjack.searching_wanted", "&7Cherche d'abord un arbre prioritaire &8({0}/{1})");
		m.put("villager.work.lumberjack.no_tree_auto", "&7Aucun arbre naturel à couper à moins de {0} blocs du territoire");
		m.put("villager.work.lumberjack.no_tree_zone_big", "&7Aucun arbre ({1}) à couper dans « {0} » &8({2} trop grand(s) : plus de {3} bûches)");
		m.put("villager.work.lumberjack.no_tree_auto_big",
				"&7Aucun arbre naturel à couper à moins de {0} blocs du territoire &8({1} trop grand(s) : plus de {2} bûches)");
		m.put("villager.work.lumberjack.going_wanted", "&eSe rend à un arbre prioritaire ({0} {1} {2})");
		m.put("villager.work.lumberjack.leaves", "&aRécolte le feuillage ({0} feuille(s) restante(s))");
		m.put("villager.work.farmer.searching_wanted", "&7Cherche d'abord des cultures prioritaires");
		m.put("villager.work.farmer.waiting_auto", "&7Aucune culture mûre à moins de {0} blocs du territoire");
		m.put("villager.work.farmer.going_wanted", "&eSe rend à une culture prioritaire ({0} {1} {2})");
		m.put("villager.work.area_unloaded", "&8Environs du territoire hors de portée (aucun joueur à proximité)");
		// automatic mode
		m.put("villager.work.auto.lore.forest", "&7Coupe les arbres naturels les plus proches\n&7du territoire : dedans et jusqu'à {0} blocs\n&7autour, jamais plus loin.");
		m.put("villager.work.auto.lore.farm", "&7Récolte les cultures mûres les plus proches\n&7du territoire : dedans et jusqu'à {0} blocs\n&7autour, jamais plus loin.");
		m.put("villager.work.zones.help.lore", "&7Un &efanion&7 planté marque une zone ; son &erayon\n&7se compte en chunks autour de son chunk.\n"
				+ "&7Les zones peuvent sortir du territoire (à moins\n&7de {0} blocs du cœur), jamais chez une autre guilde.\n"
				+ "&7Retirer un fanion supprime sa zone.\n&7Sans zone (mode automatique), bûcherons et\n"
				+ "&7fermiers travaillent dans le territoire et jusqu'à\n&7{1} blocs autour ; le mineur, autour de lui.");
		// "Appeler ici"
		m.put("villager.work.call.lore", "&7Téléporte {0} à côté de vous, où qu'il soit\n&7(sa dernière position est chargée un instant).\n"
				+ "&7Vous devez être dans le territoire ou dans\n&7une zone de la guilde.");
		m.put("villager.call.loading", "&7Appel de {0} en cours… &8(chargement de sa position {1} {2} {3})");
		m.put("villager.call.already", "&e{0} est déjà appelé, un instant…");
		m.put("villager.call.no_position", "&c{0} n'a pas de position connue : allez le chercher.");
		m.put("villager.call.not_found", "&c{0} est introuvable près de sa dernière position connue ({1} {2} {3}).");
		m.put("villager.call.too_many", "&cTrop d'appels en cours, réessayez dans quelques secondes.");
		// wishlist ("objectifs prioritaires")
		m.put("villager.wishlist.button", "&6&lObjectifs prioritaires");
		m.put("villager.wishlist.button.lore", "&7Objets que {0} récupère en priorité,\n&7même un peu hors de sa zone ({1} blocs).");
		m.put("villager.wishlist.button.zone_lore", "&7Objets que les villageois de la zone\n&7récupèrent en priorité, même un peu\n&7hors de la zone ({0} blocs).");
		m.put("villager.wishlist.button.own", "&7Les siens :");
		m.put("villager.wishlist.button.zone", "&7Ceux de sa zone « {0} » :");
		m.put("villager.wishlist.button.none", "&8 (aucun)");
		m.put("villager.wishlist.button.click", "&eClic : modifier");
		m.put("villager.wishlist.line", "&8- ");
		m.put("villager.wishlist.summary", "&7Objectifs : ");
		m.put("villager.wishlist.summary.more", "&7 +{0}");
		m.put("villager.wishlist.title.villager", "Objectifs de {0}");
		m.put("villager.wishlist.title.zone", "Objectifs de « {0} »");
		m.put("villager.wishlist.slot.empty", "&8Emplacement libre");
		m.put("villager.wishlist.slot.empty.lore", "&7Cliquez ici avec un objet sur le curseur,\n&7ou shift-clic sur un objet de votre\n"
				+ "&7inventaire, pour l'ajouter.\n&8(votre objet n'est ni pris ni déplacé)");
		m.put("villager.wishlist.entry.lore", "&cClic : retirer");
		m.put("villager.wishlist.entry.useless", "&8Ce métier ne récolte pas cet objet.");
		m.put("villager.wishlist.info", "&e&lObjectifs prioritaires");
		m.put("villager.wishlist.info.lore", "&7Le villageois récupère ces objets en priorité,\n&7même un peu hors de sa zone ({0} blocs),\n"
				+ "&7jamais chez une autre guilde.\n&7Bûcheron : bûches, planches, pousses, pommes,\n&7bâtons (il récolte alors le feuillage).\n"
				+ "&7Fermier : blé, graines, carottes, pommes de\n&7terre, betteraves, melons, citrouilles, canne.\n&7Mineur : pierres et minerais.\n"
				+ "&7{1} objets au plus.");
		m.put("villager.wishlist.info.villager", "&7Les objectifs de sa zone s'ajoutent aux siens.");
		m.put("villager.wishlist.info.zone", "&7Partagés par tous les villageois de la zone.");
		m.put("villager.wishlist.zone_shortcut", "&6Objectifs de la zone « {0} »");
		m.put("villager.wishlist.zone_shortcut.lore", "&7Partagés par tous ses villageois.\n&eClic : modifier");
		m.put("villager.wishlist.clear", "&c&lVider");
		m.put("villager.wishlist.clear.lore", "&7Retire tous les objectifs de cette liste.");
		m.put("villager.wishlist.full", "&cListe pleine ({0} objets au plus) : retirez-en un d'abord.");
		m.put("villager.wishlist.duplicate", "&eCet objet est déjà dans la liste.");
		m.put("villager.wishlist.no_item", "&7Prenez un objet sur le curseur, ou shift-clic dans votre inventaire.");
		m.put("villager.wishlist.cleared", "&7Objectifs prioritaires vidés.");
		m.put("villager.wishlist.gone", "&cCette liste n'existe plus.");
		Lang.addDefaults(m);
	}
}
