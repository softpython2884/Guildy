package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the chest groups, the tidying by the sorters, the chest rooms and their menus.
 */
final class TidyLang {
	private TidyLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// sorter statuses
		m.put("villager.status.sorter.all_tidy", "&aTout est rangé");
		m.put("villager.status.sorter.checking", "&7Vérifie le rangement des coffres");
		m.put("villager.status.sorter.busy", "&7Attend qu'un joueur ferme un coffre {0}");
		m.put("villager.status.sorter.going_tidy", "&eVa ranger les coffres {0}");
		m.put("villager.status.sorter.going_take_out", "&eVa sortir les objets mal rangés des coffres {0}");
		m.put("villager.status.sorter.taking_out", "&eSort les objets mal rangés du coffre {0} ({1}/{2})");
		m.put("villager.status.sorter.tidy_chest", "&eRange le coffre {0} ({1}/{2})");
		m.put("villager.status.sorter.arranging.columns", "&eRéorganise la colonne {0}");
		m.put("villager.status.sorter.arranging.rows", "&eRéorganise la rangée {0}");
		m.put("villager.gui.lore.stat.tidied", "&8Objets re-rangés : {0}");
		// roles and names
		m.put("villager.storage.role.reserve", "Réserve");
		m.put("villager.storage.label.reserve", "Réserve");
		m.put("villager.storage.order.columns", "par colonnes");
		m.put("villager.storage.order.rows", "par rangées");
		m.put("villager.storage.order.columns.word", "colonne(s)");
		m.put("villager.storage.order.rows.word", "rangée(s)");
		m.put("villager.storage.order.changed", "&aOrdre de rangement : &e{0}&a. Les trieurs réorganisent les coffres.");
		// chest rooms (chat)
		m.put("villager.storage.room.base", "Salle");
		m.put("villager.storage.room.select_first", "&eSalle des coffres : clic droit sur le coffre d'un coin de la salle (&6{0} s&e).");
		m.put("villager.storage.room.select_second", "&ePremier coin : &f{0} {1} {2}&e. Clic droit maintenant sur le coffre du coin opposé.");
		m.put("villager.storage.room.limit", "&cLimite de {0} salle(s) des coffres atteinte.");
		m.put("villager.storage.room.too_big", "&cZone trop grande ({0} × {1} × {2}) : {3} blocs de côté au maximum.");
		m.put("villager.storage.room.unloaded", "&cUne partie de la zone n'est pas chargée : rapprochez-vous.");
		m.put("villager.storage.room.empty", "&cAucun coffre, tonneau ou boîte de Shulker du territoire dans cette zone.");
		m.put("villager.storage.room.created", "&aSalle des coffres « {0} » créée : &e{1}&a conteneur(s) sur &e{2}&a {3}. Les trieurs y répartissent les catégories puis rangent tout.");
		m.put("villager.storage.room.chest_limit", "&cLimite de {0} coffres automatiques atteinte : les autres conteneurs de la zone ne sont pas enregistrés.");
		m.put("villager.storage.room.removed", "&7Salle des coffres « {0} » supprimée : ses coffres automatiques quittent le stockage (leurs objets restent dedans).");
		m.put("villager.storage.room.rescanned", "&a{0} nouveau(x) conteneur(s) ajouté(s) à la salle « {1} ».");
		m.put("villager.storage.room.selection_cancelled", "&7Création de la salle des coffres annulée.");
		m.put("villager.storage.group.prioritized", "&aLes coffres « {0} » seront vérifiés et réorganisés en premier.");
		m.put("villager.storage.group.no_sorter", "&7Aucun villageois n'a la tâche « Ranger la base » : personne ne range ces coffres.");
		// storage menu (tool row)
		m.put("villager.gui.storage.groups.button", "&6&lGroupes de coffres");
		m.put("villager.gui.storage.groups.button.lore", "&7Les coffres d'une même catégorie forment\n&7un groupe, rangé comme un mur qu'on lit :\n&7état, remplissage, « Réorganiser ».\n&eClic : voir les {0} groupe(s)");
		m.put("villager.gui.storage.rooms.button", "&6&lSalles des coffres");
		m.put("villager.gui.storage.rooms.button.lore", "&7{0} salle(s), {1} conteneur(s) automatique(s).\n&7Les trieurs y choisissent eux-mêmes la\n&7catégorie de chaque colonne.\n&eClic : gérer les salles");
		m.put("villager.gui.storage.order", "&b&lOrdre de rangement : &e{0}");
		m.put("villager.gui.storage.order.lore", "&7Par colonnes : chaque colonne de haut en\n&7bas, colonnes de gauche à droite (vues de\n&7face). Par rangées : chaque rangée de gauche\n&7à droite, rangées de haut en bas.\n&eClic : passer {0}");
		m.put("villager.gui.storage.entry.lore.auto", "&dAutomatique &7(salle « {0} »)");
		m.put("villager.gui.storage.entry.lore.room", "&7Salle : &f{0}");
		m.put("villager.gui.storage.entry.reserve", "&8&lRéserve");
		// groups menu
		m.put("villager.gui.groups.title", "Groupes de coffres ({0})");
		m.put("villager.gui.groups.none", "&7Aucun coffre de catégorie enregistré.");
		m.put("villager.gui.group.name", "&e&l{0}");
		m.put("villager.gui.group.room", "&7Salle : &f{0}");
		m.put("villager.gui.group.outside", "&7Hors des salles des coffres");
		m.put("villager.gui.group.chests", "&7Coffres : &f{0}");
		m.put("villager.gui.group.fill", "&7Rempli : &f{0} %&7 ({1}/{2} emplacements)");
		m.put("villager.gui.group.tidy", "&a✔ Rangé");
		m.put("villager.gui.group.untidy", "&6✖ À ranger");
		m.put("villager.gui.group.busy", "&7Un coffre est ouvert par un joueur");
		m.put("villager.gui.group.unknown", "&8Pas encore vérifié par un trieur");
		m.put("villager.gui.group.worker", "&7En cours : &f{0}");
		m.put("villager.gui.group.click", "&eClic gauche : les coffres du groupe\n&eClic droit : réorganiser maintenant");
		// group detail
		m.put("villager.gui.group.title", "{0} ({1} coffres)");
		m.put("villager.gui.group.chest", "&f{0}. &e{1}");
		m.put("villager.gui.group.reorganize", "&a&lRéorganiser maintenant");
		m.put("villager.gui.group.reorganize.lore", "&7Le prochain trieur vérifie ce groupe en\n&7premier : objets d'une autre catégorie\n&7sortis, piles fusionnées, ordre de lecture.");
		m.put("villager.gui.group.show", "&b&lAfficher ces coffres");
		m.put("villager.gui.group.show.lore", "&7Particules au-dessus de ces coffres\n&7pendant {0} s (visibles par vous seul).");
		m.put("villager.gui.group.order.lore", "&7Ordre de lecture : &f{0}");
		// rooms menu
		m.put("villager.gui.rooms.title", "Salles des coffres ({0})");
		m.put("villager.gui.rooms.none", "&7Aucune salle des coffres.");
		m.put("villager.gui.room.name", "&6&l{0}");
		m.put("villager.gui.room.box", "&7Zone : &f{0} {1} {2} &7→ &f{3} {4} {5}");
		m.put("villager.gui.room.chests", "&7Conteneurs : &f{0}&7 (automatiques : &f{1}&7, en réserve : &f{2}&7)");
		m.put("villager.gui.room.lines", "&7{0} : &f{1}");
		m.put("villager.gui.room.lines.columns", "Colonnes");
		m.put("villager.gui.room.lines.rows", "Rangées");
		m.put("villager.gui.room.categories", "&7Catégories : &f{0}");
		m.put("villager.gui.room.unplaced", "&cSans place (salle pleine) : &f{0}");
		m.put("villager.gui.room.click", "&eClic gauche : afficher ses coffres\n&eShift + clic : ajouter les nouveaux coffres\n&cClic droit : supprimer la salle");
		m.put("villager.gui.room.remove.confirm", "Supprimer cette salle des coffres ?");
		m.put("villager.gui.room.remove.lore", "&7Ses coffres automatiques quittent le\n&7stockage (leurs objets restent dedans).");
		m.put("villager.gui.rooms.add", "&a&lAjouter une salle des coffres");
		m.put("villager.gui.rooms.add.lore", "&7Clic droit sur le coffre d'un coin, puis\n&7sur celui du coin opposé ({0} s) : tous\n&7les conteneurs du territoire entre les deux\n&7deviennent automatiques ({1} blocs de côté\n&7au plus). Les trieurs leur donnent une\n&7catégorie par colonnes entières, dans\n&7l'ordre des catégories, « {2} » à la fin.");
		m.put("villager.gui.rooms.disabled", "&cSalles des coffres désactivées sur ce serveur.");
		m.put("villager.gui.rooms.help.text", "&7Une salle des coffres range toute seule :\n&7chaque catégorie reçoit des colonnes\n&7entières à sa taille (plus une marge),\n&7les colonnes libres restent en réserve.\n&7Une catégorie qui se remplit gagne une\n&7colonne ; une colonne vide longtemps\n&7revient à la réserve. Un coffre dont vous\n&7choisissez le rôle n'est jamais réattribué.");
		// role picker
		m.put("villager.gui.role.auto", "&d&lAutomatique (salle des coffres)");
		m.put("villager.gui.role.auto.lore", "&7Le trieur choisit la catégorie de ce\n&7coffre selon la répartition de la salle.");
		Lang.addDefaults(m);
	}
}
