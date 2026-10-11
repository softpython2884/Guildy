package dev.guildy.trade;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the trade module (keys prefixed with "trade.").
 */
final class TradeLang {
	private TradeLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// visitors
		m.put("trade.visitor.leave", "&7{0}&7 a repris la route.");
		m.put("trade.visitor.killed", "&c{0}&c a été tué !");
		m.put("trade.merchant.name", "&6Marchand de passage &8(&7palier {0}&8)");
		m.put("trade.merchant.arrive.title", "&6&lMarchand de passage");
		m.put("trade.merchant.arrive.subtitle", "&eIl s'installe au Carrefour pour {0} min");
		m.put("trade.merchant.arrive.chat", "&6☘ Un marchand de passage s'est arrêté au &eCarrefour commercial &7({0} {1} {2})&6 : "
				+ "il repart dans &e{3} min&6. Payez-le en or !");
		// sign
		m.put("trade.sign.title", "&2&lCarrefour");
		m.put("trade.sign.tier", "&7Palier &f{0}");
		m.put("trade.sign.present", "&aMarchand ici !");
		m.put("trade.sign.pending", "&eEn approche...");
		m.put("trade.sign.none", "&8Pas de marchand");
		m.put("trade.sign.hint", "&8(clic droit)");
		// upgrades
		m.put("trade.upgrade.no_hub", "&cConstruisez d'abord le Carrefour commercial.");
		m.put("trade.upgrade.max", "&eLe Carrefour commercial est déjà au palier maximum.");
		m.put("trade.upgrade.level", "&cIl faut une guilde de niveau &e{0}&c (actuel : {1}).");
		m.put("trade.upgrade.creative", "&cImpossible en mode créatif : passez en survie pour fournir les matériaux.");
		m.put("trade.upgrade.items", "&cIl vous manque : {0}");
		m.put("trade.upgrade.items.entry", "&e{0} &f{1}");
		m.put("trade.upgrade.payment", "&cLe paiement a échoué : rien n'a été pris.");
		m.put("trade.upgrade.done", "&a{0} a amélioré le Carrefour commercial : palier &e{1}&a ({2}&a) !");
		m.put("trade.upgrade.title", "&6&lCarrefour amélioré !");
		m.put("trade.upgrade.subtitle", "&ePalier {0} : {1}");
		m.put("trade.confirm.title", "&2Améliorer le Carrefour ?");
		m.put("trade.confirm.question", "&7Passer au palier &e{0} &7({1}&7) pour :");
		m.put("trade.confirm.gold", "&7- &6{0} or &7du trésor");
		m.put("trade.confirm.item", "&7- &f{0} {1} &7de votre inventaire");
		// menu
		m.put("trade.gui.title", "&2Carrefour commercial");
		m.put("trade.gui.hub.name", "&2&lCarrefour commercial");
		m.put("trade.gui.hub.missing", "&cPas encore construit.\n&7Débloquez-le dans le menu Constructions\n&7(&e/guild build&7), bâtissez son mur\n"
				+ "&7(laine · bloc d'émeraude · laine /\n&7tonneau · table de cartographie · tonneau)\n&7puis validez-le avec le plan.");
		m.put("trade.gui.hub.at", "&7Position : &f{0} {1} {2}");
		m.put("trade.gui.hub.tier", "&7Palier : &f{0}&7/&f{1} &8({2}&8)");
		m.put("trade.gui.hub.chance", "&7Chance de visite chaque matin : &f{0}%");
		m.put("trade.gui.hub.visits", "&7Marchands accueillis : &f{0}");
		m.put("trade.gui.tier.name", "&e&lPalier {0} &8- {1}");
		m.put("trade.gui.tier.lore", "&7Échanges par visite : &f{0} à {1}\n&7Origine des échanges :");
		m.put("trade.gui.tier.source", "&8 • &7Palier {0} ({1}&7) : &f{2}%");
		m.put("trade.gui.upgrade.name", "&a&lAméliorer : palier {0} &8- {1}");
		m.put("trade.gui.upgrade.offers", "&7Échanges par visite : &f{0} à {1}&7, visite : &f{2}%");
		m.put("trade.gui.upgrade.cost", "&7Coût :");
		m.put("trade.gui.upgrade.gold", "&a ✔ &6{0} or &7du trésor (&f{1}&7)");
		m.put("trade.gui.upgrade.gold.missing", "&c ✘ &6{0} or &7du trésor (&c{1}&7)");
		m.put("trade.gui.upgrade.item", "&a ✔ &f{0} {1} &7(vous en avez {2})");
		m.put("trade.gui.upgrade.item.missing", "&c ✘ &f{0} {1} &7(vous en avez &c{2}&7)");
		m.put("trade.gui.upgrade.level", "&a ✔ &7Guilde niveau &f{0}");
		m.put("trade.gui.upgrade.level.missing", "&c ✘ &7Guilde niveau &f{0} &7(actuel : &c{1}&7)");
		m.put("trade.gui.upgrade.click", "&eCliquez pour améliorer.");
		m.put("trade.gui.upgrade.permission", "&8Réservé aux rôles qui gèrent le trésor.");
		m.put("trade.gui.upgrade.max", "&6&lPalier maximum");
		m.put("trade.gui.upgrade.max.lore", "&7Le Carrefour attire déjà les\n&7marchands les plus prestigieux.");
		m.put("trade.gui.visitors.name", "&e&lMarchands à la base");
		m.put("trade.gui.visitors.entry", "&f{0} &8» &7{1} {2} {3} &8- &7encore &f{4}");
		m.put("trade.gui.visitors.none", "&8Aucun marchand pour l'instant.");
		m.put("trade.gui.visitors.pending", "&eUn marchand est attendu aujourd'hui :\n&eil arrive dès qu'un membre est à la base.");
		m.put("trade.gui.visitors.next", "&7Prochaine visite possible demain matin (&f{0}%&7).");
		m.put("trade.gui.preview.name", "&b&lÉchanges du palier {0} &8- {1}");
		m.put("trade.gui.preview.lore", "&7{0} échange(s) possible(s), &f{1} à {2}\n&7tirés à chaque visite.\n&eCliquez pour les voir.");
		m.put("trade.gui.preview.title", "&2Palier {0} - {1}");
		m.put("trade.gui.preview.empty", "&8Aucun échange à ce palier.");
		m.put("trade.gui.entry.name", "&f{0} × {1}");
		m.put("trade.gui.entry.enchantment", "&d{0}");
		m.put("trade.gui.entry.price", "&7Prix : {0}");
		m.put("trade.gui.entry.uses", "&7Stock par visite : &f{0}");
		m.put("trade.gui.entry.chance", "&7Fréquence dans ce palier : &f{0}%");
		m.put("trade.gui.help.name", "&f&lComment ça marche ?");
		m.put("trade.gui.help.lore", "&7Certains matins, un &eMarchand de passage&7 s'arrête\n&7au Carrefour pour environ &f{0} min&7 (il repart\n"
				+ "&7au plus tard à la tombée de la nuit).\n \n&7Clic droit sur lui pour commercer : on paie en\n"
				+ "&6lingots d'or&7 (blocs d'or pour les objets chers).\n&7Il ne rachète rien.\n \n"
				+ "&7Seuls les membres commercent dans le territoire.\n&7Améliorez le Carrefour : visites plus fréquentes\n"
				+ "&7et échanges de plus en plus fous !\n \n&7Caravanes et érudit itinérant s'arrêtent\n&7aussi au Carrefour.");
		m.put("trade.price.ingots", "&6{0} or");
		m.put("trade.price.blocks", "&6{0} bloc(s) d'or &8({1} or)");
		m.put("trade.price.mixed", "&6{0} bloc(s) d'or + {1} lingot(s) &8({2} or)");
		m.put("trade.time.seconds", "{0} s");
		m.put("trade.time.minutes", "{0} min {1} s");
		// admin
		m.put("trade.admin.tier", "&aCarrefour de &e{0}&a réglé sur le palier &f{1}&a.");
		Lang.addDefaults(m);
	}
}
