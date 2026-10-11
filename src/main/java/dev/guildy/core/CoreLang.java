package dev.guildy.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults for core texts (prefix, generic errors, GUI labels, special items, roles, levels).
 */
public final class CoreLang {
	private CoreLang() {
	}

	public static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("prefix", "&6[&eGuilde&6]&r ");
		// generic errors
		m.put("error.unavailable", "&cCette fonction n'est pas disponible.");
		m.put("error.no_guild", "&cVous n'appartenez à aucune guilde. &7(/guild create <nom>)");
		m.put("error.already_in_guild", "&cVous faites déjà partie d'une guilde.");
		m.put("error.no_permission", "&cVotre rôle ne permet pas cette action.");
		m.put("error.not_enough_gold", "&cPas assez d'or : il faut &e{0} or&c.");
		m.put("error.not_enough_treasury", "&cLe trésor de la guilde ne contient pas assez d'or (&e{0}&c requis).");
		m.put("error.player_only", "&cCommande réservée aux joueurs.");
		m.put("error.unknown_guild", "&cGuilde introuvable : &e{0}");
		m.put("error.name.length", "&cLe nom doit faire entre 3 et 16 caractères.");
		m.put("error.name.chars", "&cLe nom contient des caractères interdits (lettres, chiffres, espaces, - _ ' uniquement).");
		m.put("error.name.taken", "&cCe nom de guilde est déjà pris.");
		m.put("error.internal", "&cUne erreur interne est survenue (voir la console).");
		// admin
		m.put("admin.reloaded", "&aConfiguration rechargée.");
		m.put("admin.reload_errors", "&cErreurs de rechargement : &7{0}");
		m.put("admin.saved", "&aDonnées des guildes sauvegardées.");
		// gui
		m.put("gui.back", "&7« Retour");
		m.put("gui.close", "&cFermer");
		m.put("gui.confirm.yes", "&a&lConfirmer");
		m.put("gui.confirm.no", "&c&lAnnuler");
		m.put("gui.input.confirm", "&aValider");
		m.put("gui.page.next", "&ePage suivante »");
		m.put("gui.page.prev", "&e« Page précédente");
		m.put("gui.page", "&7Page {0}/{1}");
		// roles
		m.put("role.member", "Membre");
		m.put("role.officer", "Officier");
		m.put("role.leader", "Chef");
		// levels
		m.put("guild.levelup", "&a&lLa guilde passe au niveau {0} !");
		m.put("guild.levelup.title", "&6&lNiveau supérieur !");
		m.put("guild.levelup.subtitle", "&eVotre guilde est maintenant niveau {0}");
		// special items
		m.put("item.founding_flag.name", "&6&lDrapeau fondateur &7({0})");
		m.put("item.founding_flag.lore1", "&7Guilde : &e{0}");
		m.put("item.founding_flag.lore2", "&7Plantez-le pour fonder votre base :");
		m.put("item.founding_flag.lore3", "&7il devient le &ccœur&7 du territoire.");
		m.put("item.extension_flag.name", "&e&lDrapeau d'extension");
		m.put("item.extension_flag.lore1", "&7Plantez-le dans un chunk voisin de");
		m.put("item.extension_flag.lore2", "&7votre territoire pour l'agrandir.");
		m.put("item.plan.name", "&b&lPlan : &f{0}");
		m.put("item.treasure_map.name", "&6&lCarte au trésor &7(rang {0})");
		m.put("item.treasure_map.lore1", "&7Un trésor est enterré vers &eX {0}&7, &eZ {1}");
		m.put("item.treasure_map.lore2", "&7Rendez-vous sur place avec la carte en main.");
		m.put("item.decor_banner.name", "&fBannière de {0}");
		Lang.addDefaults(m);
	}
}
