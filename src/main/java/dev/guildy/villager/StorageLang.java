package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the storage categories, container names and storage menus (the older storage keys, unchanged,
 * stay in {@link VillagerLang}).
 */
final class StorageLang {
	private StorageLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// chat
		m.put("villager.storage.broken", "&7Conteneur « {0} » cassé : retiré du stockage.");
		m.put("villager.storage.show.done", "&7{0} conteneur(s) affiché(s) pendant {1} s (particules visibles par vous seul) :");
		m.put("villager.storage.show.entry", "{0}■ &f{1} &7({2})   ");
		m.put("villager.storage.show.none", "&7Aucun conteneur à afficher.");
		// container names (title shown when a registered container is opened)
		m.put("villager.storage.label.deposit", "Dépôt");
		m.put("villager.storage.label.category", "{0}");
		// rule that chose a category (menu "Où ranger l'objet en main ?")
		m.put("villager.storage.rule.id", "objet {0}");
		m.put("villager.storage.rule.tag", "tag {0}");
		m.put("villager.storage.rule.pattern", "motif {0}");
		m.put("villager.storage.rule.property", "propriété {0}");
		m.put("villager.storage.rule.tab", "onglet créatif {0}");
		m.put("villager.storage.rule.fallback", "aucune règle");
		m.put("villager.storage.fallback.description", "Objets sans catégorie, ou dont les coffres sont pleins ou absents");
		// storage menu
		m.put("villager.gui.storage.entry.fallback", "&7&l{0}");
		m.put("villager.gui.storage.entry.lore.stale", "&8Catégorie « {0} » supprimée :\n&8sert de coffre « {1} ».");
		m.put("villager.gui.storage.entry.lore.click", "&eClic gauche : changer le rôle\n&cClic droit : retirer du stockage");
		m.put("villager.gui.storage.categories.button", "&b&lCatégories de rangement");
		m.put("villager.gui.storage.categories.button.lore", "&7{0} catégories, plus « {1} » pour le reste.\n&7Modifiables dans villagers.json5.\n&eClic : voir les catégories et leurs coffres");
		m.put("villager.gui.storage.show", "&b&lAfficher les coffres");
		m.put("villager.gui.storage.show.lore", "&7Colonnes de particules au-dessus des\n&7conteneurs enregistrés pendant {0} s :\n&7une couleur par rôle, visibles par vous seul.");
		m.put("villager.gui.storage.held", "&6&lOù ranger l'objet en main ?");
		m.put("villager.gui.storage.held.item", "&7Objet : &f");
		m.put("villager.gui.storage.held.category", "&7Catégorie : &e{0}");
		m.put("villager.gui.storage.held.rule", "&8Règle : {0}");
		m.put("villager.gui.storage.held.chests", "&7Coffres de cette catégorie : &f{0}");
		m.put("villager.gui.storage.held.none", "&7Prenez un objet en main puis rouvrez ce\n&7menu pour savoir dans quel coffre le\n&7trieur le rangera.");
		m.put("villager.gui.storage.help.text", "&7Les travailleurs déposent leur récolte dans\n&7les coffres de &bdépôt&7. Le trieur (tâche\n&7« Ranger la base ») range chaque objet dans\n&7un coffre de sa catégorie, sinon dans un\n&7coffre « {0} », sinon il le laisse au dépôt.\n&7Puis il re-range les coffres d'une même\n&7catégorie : piles fusionnées, ordre de\n&7l'inventaire créatif, coffre 1 rempli avant\n&7le coffre 2 (ordre de lecture du mur).\n&7Chaque coffre prend le nom de son rôle\n&7(titre affiché à l'ouverture).\n&dCoffres filtrés&7 : n'acceptent que leur\n&7famille (« diorite ») ou leurs objets, avant\n&7les coffres de catégorie. &f/guild stockage\n&fscan <chunks>&7 les crée d'après le contenu\n&7des coffres autour de vous.");
		// categories menu
		m.put("villager.gui.categories.title", "Catégories de rangement");
		m.put("villager.gui.category.name", "&e&l{0}");
		m.put("villager.gui.category.fallback", "&7&l{0}");
		m.put("villager.gui.category.chests", "&7Coffres : &f{0}");
		m.put("villager.gui.category.no_chest", "&7Coffres : &caucun &8(rangé dans « {0} »)");
		m.put("villager.gui.category.show", "&eClic : afficher ces coffres");
		// role picker
		m.put("villager.gui.role.fallback", "&7&l{0}");
		m.put("villager.gui.role.current", "&a▶ Rôle actuel");
		m.put("villager.gui.role.choose", "&eClic : ranger ici ces objets");
		m.put("villager.gui.role.choose_deposit", "&eClic : en faire un dépôt");
		m.put("villager.gui.role.info", "&6&lConteneur en {0} {1} {2}");
		m.put("villager.gui.role.info.current", "&7Rôle actuel : &f{0}");
		m.put("villager.gui.role.info.new", "&7Pas encore enregistré : choisissez\n&7son rôle (il en prendra le nom).");
		// sub-categories, transversal categories, routing order (1.12)
		m.put("villager.gui.category.sub", "&8Sous-catégorie de « {0} » : sans coffre\n&8« {1} », ces objets vont dans les\n&8coffres « {0} ».");
		m.put("villager.gui.category.transversal", "&dTransversale : prend ces objets dans\n&dtoutes les catégories, avant elles. Sans\n&dcoffre « {0} », ils vont dans leur\n&dcatégorie habituelle.");
		m.put("villager.gui.category.children", "&8Sous-catégories : {0}");
		m.put("villager.gui.category.usual", "leur catégorie habituelle");
		m.put("villager.gui.role.fallback.warning", "&c⚠ Reçoit TOUT ce qui n'a pas d'autre\n&ccoffre : ne le choisissez pas pour une\n&ccollection (livres, potions...) : prenez sa\n&ccatégorie ou un coffre filtré.");
		m.put("villager.gui.role.filter.hand.lore", "&7N'accepte que les objets qu'il contient\n&7déjà, ou leur famille (« diorite » : diorite,\n&7diorite polie, dalles, escaliers, murets...).\n&7Conteneur vide : l'objet tenu en main\n&7(un livre enchanté → un coffre de livres\n&7enchantés). Les trieurs y rangent ces\n&7objets avant leur coffre de catégorie.");
		m.put("villager.gui.role.choose_filter_hand", "&eClic : filtrer d'après son contenu\n&e(vide : d'après l'objet en main)");
		m.put("villager.gui.storage.held.route", "&7Ordre du trieur : &f{0}");
		m.put("villager.gui.page.jump", "&8Shift + clic : 10 pages");
		m.put("villager.gui.group.part", "&7Partie &f{0}&7/&f{1} &8(plus de {2} coffres :\n&8chaque partie est rangée à part)");
		m.put("villager.gui.storage.help.text.v2", "&7Les travailleurs déposent leur récolte dans\n&7les coffres de &bdépôt&7. Le trieur (tâche\n&7« Ranger la base ») range chaque objet dans\n&7le coffre le plus précis qui a de la place :\n&dcoffre filtré&7, catégorie &dtransversale&7\n&7(« Objets rares »...), &esous-catégorie&7\n&7(« Livres enchantés »), sa catégorie\n&7(« Livres et enchantements »), sinon un\n&7coffre « {0} », sinon il reste au dépôt.\n&7Puis il re-range les coffres d'une même\n&7catégorie : piles fusionnées, ordre de\n&7l'inventaire créatif, coffre 1 rempli avant\n&7le coffre 2 (ordre de lecture du mur).\n&7Chaque coffre prend le nom de son rôle.\n&f/guild stockage scan <chunks>&7 propose des\n&7rôles d'après le contenu des coffres.");
		m.put("villager.gui.rooms.help.text.v2", "&7Une salle des coffres range toute seule :\n&7chaque catégorie reçoit des colonnes\n&7entières à sa taille (plus une marge),\n&7les colonnes libres restent en réserve.\n&7Une catégorie qui se remplit gagne une\n&7colonne ; une colonne vide longtemps\n&7revient à la réserve. Un coffre dont vous\n&7choisissez le rôle n'est jamais réattribué.\n&6Une salle peut être réservée à quelques\n&6catégories (menu de la salle) : un\n&6bâtiment des livres enchantés, des\n&6potions... ne reçoit alors rien d'autre.");
		// filter chests and limits (1.12)
		m.put("villager.storage.filter.off", "&cCoffres filtrés désactivés sur ce serveur (storage.filterChests = false).");
		m.put("villager.storage.filter.empty_hand", "&cCe conteneur est vide : rangez-y les objets qu'il doit accepter, ou tenez l'un d'eux en main.");
		m.put("villager.storage.room.disabled", "&cSalles des coffres désactivées sur ce serveur (storage.chestRooms = false).");
		// chest room menu and reserved rooms (1.12)
		m.put("villager.gui.room.click.menu", "&eClic gauche : ouvrir la salle\n&cClic droit : supprimer la salle");
		m.put("villager.gui.room.title", "Salle « {0} »");
		m.put("villager.gui.room.show", "&b&lAfficher ses coffres");
		m.put("villager.gui.room.rescan", "&a&lAjouter les nouveaux coffres");
		m.put("villager.gui.room.rescan.lore", "&7Enregistre les conteneurs posés depuis\n&7dans la zone de la salle.");
		m.put("villager.gui.room.theme", "&6&lCatégories de la salle");
		m.put("villager.gui.room.theme.lore.all", "&7Toutes les catégories (salle ordinaire).\n&eClic : réserver la salle à quelques\n&ecatégories (bâtiment des livres...)");
		m.put("villager.gui.room.theme.lore.some", "&7Réservée à : &f{0}");
		m.put("villager.gui.room.theme.lore.change", "&eClic : changer");
		m.put("villager.gui.room.remove", "&c&lSupprimer la salle");
		m.put("villager.gui.room.reserved", "&6Réservée à : &f{0}");
		m.put("villager.gui.room.theme.title", "Catégories de « {0} »");
		m.put("villager.gui.room.theme.in", "&a✔ Dans la salle");
		m.put("villager.gui.room.theme.out", "&7Pas dans la salle");
		m.put("villager.gui.room.theme.toggle", "&eClic : ajouter / retirer");
		m.put("villager.gui.room.theme.all", "&f&lToutes les catégories");
		m.put("villager.gui.room.theme.all.lore", "&7La salle reçoit toutes les catégories\n&7principales, « {0} » à la fin.\n&eClic : salle ordinaire");
		m.put("villager.gui.room.theme.help", "&7Une salle réservée ne reçoit que ses\n&7catégories : chacune y garde au moins\n&7une colonne, même vide. Sans « {0} »,\n&7les autres objets vont ailleurs.\n&7Exemple : « Livres enchantés » seule =\n&7le bâtiment des enchantements.");
		m.put("villager.storage.room.theme.changed", "&aSalle « {0} » : {1}.");
		m.put("villager.storage.room.theme.every", "toutes les catégories");
		Lang.addDefaults(m);
	}
}
