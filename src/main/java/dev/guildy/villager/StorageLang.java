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
		Lang.addDefaults(m);
	}
}
