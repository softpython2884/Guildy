package dev.guildy.event;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the event module (keys prefixed with "event.").
 */
final class EventLang {
	private EventLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// names
		m.put("event.blood_night.name", "Nuit de sang");
		m.put("event.pillager_raid.name", "Raid de pillards");
		m.put("event.caravan.name", "Caravane marchande");
		m.put("event.book_merchant.name", "Marchand de livres enchantés");
		m.put("event.merchant.name", "Marchand de passage (Carrefour)");
		// blood night
		m.put("event.blood_night.label", "&4&l☾ Nuit de sang");
		m.put("event.blood_night.title", "&4&lNuit de sang");
		m.put("event.blood_night.subtitle", "&cLa lune rougit… les hordes sont déchaînées");
		m.put("event.blood_night.chat", "&4☾ &lNuit de sang ! &r&cCette nuit, les hordes sont plus nombreuses (&f×{0}&c), plus fortes "
				+ "(&f+{1} %&c de vie et de dégâts) et souvent menées par un boss... mais les récompenses sont multipliées par &6{2}&c !");
		m.put("event.blood_night.cancelled", "&7La lune de sang pâlit : la nuit sera ordinaire.");
		m.put("event.blood_night.over", "&7L'aube se lève : la lune de sang s'éteint.");
		// pillager raid
		m.put("event.pillager_raid.warn.title", "&6&lDes pillards approchent !");
		m.put("event.pillager_raid.warn.subtitle", "&eIls attaquent dans {0}");
		m.put("event.pillager_raid.warn.chat", "&6⚔ Des pillards approchent… ils attaquent dans &e{0}&6 ! "
				+ "Préparez-vous à défendre le cœur de la base.");
		m.put("event.pillager_raid.gone", "&7La nuit tombe : les pillards ont renoncé à attaquer la base.");
		m.put("event.pillager_raid.spotted", "&6⚔ Des éclaireurs pillards rôdent autour de la base : ils attaqueront dès qu'un "
				+ "membre sera près du cœur (avant la nuit).");
		// visitors
		m.put("event.caravan.trader", "&eCaravane marchande");
		m.put("event.caravan.arrive.title", "&e&lCaravane marchande");
		m.put("event.caravan.arrive.subtitle", "&7Des marchandises venues de loin, pour {0} min");
		m.put("event.caravan.arrive.chat", "&e✦ Une caravane marchande s'installe à la base &7({0} {1} {2})&e pour &f{3} min&e : "
				+ "des marchandises rares contre de l'or !");
		m.put("event.book_merchant.trader", "&dÉrudit itinérant");
		m.put("event.book_merchant.arrive.title", "&d&lÉrudit itinérant");
		m.put("event.book_merchant.arrive.subtitle", "&7Des livres enchantés à vendre, pour {0} min");
		m.put("event.book_merchant.arrive.chat", "&d✎ Un érudit itinérant est arrivé à la base &7({0} {1} {2})&d avec des "
				+ "&flivres enchantés&d : il repart dans &f{3} min&d.");
		// waves menu lines
		m.put("event.lore.blood_night.tonight", "&4☾ Nuit de sang cette nuit ! &7(récompenses &6×{0}&7)");
		m.put("event.lore.blood_night.now", "&4☾ Nuit de sang en cours ! &7(récompenses &6×{0}&7)");
		m.put("event.lore.raid.pending", "&6⚔ Des pillards rôdent : ils attaqueront dès qu'un membre sera au cœur");
		m.put("event.lore.raid.soon", "&6⚔ Raid de pillards dans &e{0}");
		m.put("event.lore.visitor", "&e✦ {0}&7 à la base (encore {1})");
		m.put("event.lore.visitor.pending", "&e✦ {0}&7 attendu aujourd'hui");
		m.put("event.time.seconds", "{0} s");
		m.put("event.time.minutes", "{0} min {1} s");
		m.put("event.time.minutes_exact", "{0} min");
		// admin
		m.put("event.admin.unknown", "&cÉvénement inconnu : &e{0}&c. Événements : &f{1}");
		m.put("event.admin.need_guild", "&cPrécisez la guilde : /guild admin event {0} {1} <guilde>");
		m.put("event.admin.blood_night.started", "&aNuit de sang programmée pour la nuit du jour &f{0}&a.");
		m.put("event.admin.blood_night.stopped", "&aNuit de sang annulée.");
		m.put("event.admin.blood_night.none", "&cAucune nuit de sang n'est programmée.");
		m.put("event.admin.raid.started", "&aRaid de pillards lancé contre &e{0}&a.");
		m.put("event.admin.raid.waiting", "&eRaid de pillards en attente : il attaquera &f{0}&e dès qu'un membre sera près du cœur.");
		m.put("event.admin.raid.failed", "&cImpossible de lancer le raid contre &e{0}&c : {1}");
		m.put("event.admin.raid.stopped", "&aRaid de pillards contre &e{0}&a annulé.");
		m.put("event.admin.raid.none", "&c&e{0}&c n'a aucun raid en cours ni en attente.");
		m.put("event.admin.visit.started", "&a{0}&a : arrivé chez &e{1}&a.");
		m.put("event.admin.visit.failed", "&c{0}&c : impossible chez &e{1}&c ({2}).");
		m.put("event.admin.visit.pending", "&e{0}&e : attendu chez &f{1}&e, arrivée dès que la base sera chargée et qu'un membre sera en ligne.");
		m.put("event.admin.visit.stopped", "&a{0}&a : reparti de chez &e{1}&a.");
		m.put("event.admin.visit.none", "&c&e{0}&c n'a pas ce visiteur.");
		m.put("event.admin.reason.unknown_kind", "visiteur inconnu");
		m.put("event.admin.reason.no_target", "pas de point d'arrivée (Carrefour commercial ou cœur)");
		m.put("event.admin.reason.not_loaded", "zone d'arrivée non chargée");
		m.put("event.admin.reason.no_space", "aucune place libre près du point d'arrivée");
		m.put("event.admin.reason.no_offers", "aucun échange valide (voir la configuration)");
		m.put("event.admin.reason.failed", "échec de l'apparition");
		m.put("event.admin.reason.unavailable", "hordes désactivées ou difficulté paisible");
		m.put("event.admin.reason.not_founded", "pas de cœur de base");
		m.put("event.admin.reason.already_active", "une horde est déjà en cours");
		m.put("event.admin.reason.no_mob", "aucun monstre valide dans la composition");
		m.put("event.admin.reason.no_defender", "aucun membre près du cœur, et la nuit tombe");
		m.put("event.admin.list.header", "&8&m          &r &6Événements &8&m          ");
		m.put("event.admin.list.random_off", "&cTirages aléatoires désactivés (events.json5 : randomEvents).");
		m.put("event.admin.list.entry", "&e{0} &8[{1}] &7: {2}");
		m.put("event.admin.list.blood_night", "&7chance &f{0} %&7, palier ≥ &f{1}&7, &f{2}&7 j entre deux{3}");
		m.put("event.admin.list.guild_event", "&7chance &f{0} %&7, niveau ≥ &f{1}&7, recharge &f{2}&7 j");
		m.put("event.admin.list.merchant", "&7chance &f{0} %&7 + &f{1} %&7 par palier du Carrefour");
		m.put("event.admin.list.disabled", "&c(désactivé)");
		m.put("event.admin.list.scheduled", "&4 - prévue la nuit du jour {0}");
		m.put("event.admin.list.raid", "raid en attente");
		m.put("event.admin.list.wave", "assaut {0} ({1}/{2})");
		m.put("event.admin.list.visitor", "{0} ({1})");
		m.put("event.admin.list.pending", "{0} attendu");
		m.put("event.admin.list.active", "&e{0}&7 : {1}");
		m.put("event.admin.list.separator", ", ");
		m.put("event.admin.list.none", "&8Aucun événement en cours dans les guildes.");
		Lang.addDefaults(m);
	}
}
