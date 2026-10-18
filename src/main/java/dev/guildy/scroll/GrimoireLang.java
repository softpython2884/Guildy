package dev.guildy.scroll;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the grimoire (keys prefixed with "grimoire.").
 */
final class GrimoireLang {
	private GrimoireLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// item
		m.put("grimoire.item.name", "&5&lGrimoire &8— {0} &7({1}/{2})");
		m.put("grimoire.item.name_empty", "&5&lGrimoire &8(vierge)");
		m.put("grimoire.item.header", "&8✦ Grimoire de sorts · {0}/{1} pages");
		m.put("grimoire.item.page_selected", "&e▶ {0}. {1} &8· &6{2} faim");
		m.put("grimoire.item.page", "&8   {0}. {1} &8· &7{2} faim");
		m.put("grimoire.item.page_blank", "&8   {0}. &7&o(page vierge)");
		m.put("grimoire.item.page_unknown", "&8   {0}. &c&o(page illisible : {1})");
		m.put("grimoire.item.no_spell", "&7Clic droit d'un parchemin sur le grimoire\n&7(dans l'inventaire) : il devient une page.");
		m.put("grimoire.item.cost", "&6Coût : &e{0} &6points de faim &8(saturation d'abord)");
		m.put("grimoire.item.cast", "&8Incantation : {0} s · Recharge : {1} s");
		m.put("grimoire.item.power", "&8Puissance des sorts : ×{0}");
		m.put("grimoire.item.usage", "&7Clic droit : lancer le sort &8· &7accroupi : page suivante\n"
				+ "&7Clic droit d'un parchemin dessus (inventaire) : le relier\n&8Accroupi + F ou /guild grimoire : les pages");
		// use
		m.put("grimoire.cast", "&d✦ &f{0} &d! &8(−{1} faim)");
		m.put("grimoire.empty", "&7Ce grimoire est vierge : reliez-y des parchemins.");
		m.put("grimoire.page.turned", "{0} &8· &6{1} faim");
		m.put("grimoire.page.none", "&7Aucune page lisible dans ce grimoire.");
		m.put("grimoire.fail.disabled", "&cLe grimoire est désactivé sur ce serveur.");
		m.put("grimoire.fail.unreadable", "&cCette page est illisible ({0}) : effacez-la (/guild grimoire).");
		m.put("grimoire.fail.cooldown", "&cGrimoire en recharge : &f{0} s");
		m.put("grimoire.fail.hungry", "&cTrop faim : ce sort coûte &e{0}&c points de faim, vous en avez &e{1}&c.");
		m.put("grimoire.fail.hungry_now", "&cVous avez trop faim pour achever le sort.");
		m.put("grimoire.fail.interrupted", "&7Incantation interrompue.");
		m.put("grimoire.fail.no_target", "&7Aucun monstre à portée.");
		m.put("grimoire.fail.no_monsters", "&7Aucun monstre aux alentours.");
		// fusion
		m.put("grimoire.fuse.added", "&d✦ &7{0} &7est relié au grimoire (page {1}/{2}).");
		m.put("grimoire.fuse.duplicate", "&cLe grimoire contient déjà {0}&c.");
		m.put("grimoire.fuse.full", "&cLes {0} pages du grimoire sont écrites : effacez-en une (/guild grimoire).");
		m.put("grimoire.fuse.unknown", "&cCe parchemin est illisible : il ne peut pas être relié.");
		// menu
		m.put("grimoire.menu.title", "&5Grimoire");
		m.put("grimoire.menu.gone", "&cTenez un grimoire en main (main principale ou secondaire).");
		m.put("grimoire.menu.help", "&eComment ça marche ?");
		m.put("grimoire.menu.help.lore", "&7Le grimoire relie jusqu'à {0} parchemins.\n&7Relier : &fMaj-clic&7 sur un parchemin de votre\n"
				+ "&7inventaire, ou clic droit d'un parchemin sur\n&7le grimoire (le parchemin est consommé).\n"
				+ "&7Clic droit avec le grimoire : lancer le sort.\n&7Accroupi + clic droit : page suivante.\n"
				+ "&7Chaque sort coûte de la nourriture.");
		m.put("grimoire.menu.page", "&7Page {0} : {1}");
		m.put("grimoire.menu.page_selected", "&e▶ Page {0} : {1}");
		m.put("grimoire.menu.page.select", "&eClic gauche : choisir cette page");
		m.put("grimoire.menu.page.selected", "&a✔ Page choisie");
		m.put("grimoire.menu.page.erase", "&cClic droit : effacer la page &8(le parchemin est perdu)");
		m.put("grimoire.menu.blank", "&8Page {0} : vierge");
		m.put("grimoire.menu.blank.lore", "&7Maj-clic sur un parchemin de votre\n&7inventaire pour le relier ici.");
		m.put("grimoire.menu.unknown", "&cPage {0} : illisible ({1})");
		m.put("grimoire.menu.unknown.lore", "&7Ce parchemin n'existe plus sur le serveur.\n&cClic droit : effacer la page");
		m.put("grimoire.menu.erase.title", "&cEffacer la page ?");
		m.put("grimoire.menu.erase.question", "&7Page {0} : {1}\n&cLe parchemin relié est perdu.");
		m.put("grimoire.menu.erased", "&7Page {0} effacée.");
		m.put("grimoire.menu.not_scroll", "&cSeuls les parchemins de sort peuvent être reliés au grimoire.");
		// admin
		m.put("grimoire.admin.give", "&aGrimoire donné à &e{0}&a.");
		m.put("grimoire.admin.unknown", "&cParchemin inconnu : &e{0}&c (voir /guild admin scroll list).");
		m.put("grimoire.admin.list", "&8- {0} &8[&7grimoire&8] &8· &7{1} pages, {2} s, recharge {3} s");
		m.put("grimoire.admin.list.hover", "&7Coût en faim : {0}\n&7Puissance : ×{1}\n&7Butin : {2}\n&7Carrefour : palier {3}, {4} or\n"
				+ "&eCliquez pour préparer la commande");
		cursed(m);
		Lang.addDefaults(m);
	}

	/** The cursed grimoire's own texts ("grimoire.cursed." + the grimoire key they replace, and its own keys). */
	private static void cursed(Map<String, String> m) {
		m.put("grimoire.cursed.item.name", "&4&lGrimoire maudit &8— {0} &7({1}/{2})");
		m.put("grimoire.cursed.item.name_empty", "&4&lGrimoire maudit &8(vierge)");
		m.put("grimoire.cursed.item.header", "&8☠ Grimoire maudit · {0}/{1} pages");
		m.put("grimoire.cursed.item.page_selected", "&c▶ {0}. {1} &8· &6{2} faim &8· &c{3} ❤");
		m.put("grimoire.cursed.item.page", "&8   {0}. {1} &8· &7{2} faim &8· &4{3} ❤");
		m.put("grimoire.cursed.item.cost", "&6Coût : &e{0} &6faim &8+ &c{1} ❤ de sang &8(jamais sous {2} ❤)");
		m.put("grimoire.cursed.item.curse", "&5☠ Malédiction : &dsorts bien plus puissants\n&8(dégâts, durées, portées, nombre de cibles…)");
		m.put("grimoire.cursed.cast", "&4☠ &f{0} &4! &8(−{1} faim, −{2} ❤)");
		m.put("grimoire.cursed.page.turned", "{0} &8· &6{1} faim &8· &c{2} ❤");
		m.put("grimoire.cursed.fail.disabled", "&cLe grimoire maudit est désactivé sur ce serveur.");
		m.put("grimoire.cursed.fail.cooldown", "&cGrimoire maudit en recharge : &f{0} s");
		m.put("grimoire.cursed.fail.blood", "&4Le grimoire réclame &c{0} ❤&4 de sang : il faut garder au moins &c{1} ❤&4.");
		m.put("grimoire.cursed.fail.blood_now", "&4Votre sang est trop faible pour achever le sort.");
		m.put("grimoire.cursed.menu.title", "&4Grimoire maudit");
		m.put("grimoire.cursed.admin.give", "&aGrimoire maudit donné à &e{0}&a.");
		m.put("grimoire.cursed.admin.list", "&8- {0} &8[&4grimoire maudit&8] &8· &7{1} pages, recharge {2} s");
		m.put("grimoire.cursed.admin.list.hover", "&7Faim : {0}\n&7Sang : {1} (jamais sous {2} PV)\n&7Puissance : dégâts/durées ×{3}, portées ×{4},"
				+ " nombres ×{5}, niveaux +{6}\n&7Butin (gardiens maudits) : {7}\n&eCliquez pour préparer la commande");
	}
}
