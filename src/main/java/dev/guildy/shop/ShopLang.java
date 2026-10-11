package dev.guildy.shop;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults for shop texts (keys "shop.*").
 */
final class ShopLang {
	private ShopLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("shop.sign.line1", "&1&l[Boutique]");
		m.put("shop.sign.line2", "&8Clic droit");
		m.put("shop.counter.none", "&cVotre guilde n'a pas de comptoir de commerce. &7(/guild build)");
		m.put("shop.counter.far", "&cRendez-vous au comptoir de commerce de votre guilde (&e{0} {1} {2}&c) pour commercer.");
		m.put("shop.level", "&cIl faut une guilde de niveau &e{0}&c pour cet article.");
		m.put("shop.buy.unavailable", "&cCet article n'est pas en vente.");
		m.put("shop.buy.no_gold", "&cPas assez d'or : il faut &e{0} or&c (vous en avez &e{1}&c).");
		m.put("shop.buy.done", "&aAchat : &f{0} x {1}&a pour &e{2} or");
		m.put("shop.sell.unavailable", "&cLa boutique ne rachète pas cet article.");
		m.put("shop.sell.creative", "&cImpossible de vendre en mode créatif.");
		m.put("shop.sell.not_enough", "&cIl vous faut au moins &e{0} x {1}&c (objets non modifiés) pour vendre un lot.");
		m.put("shop.sell.done", "&aVente : &f{0} x {1}&a pour &e{2} or");
		m.put("shop.gui.title", "Comptoir de commerce");
		m.put("shop.gui.empty", "&cLa boutique est vide.");
		m.put("shop.gui.category.lore", "&7{0} article(s)\n&eClic : ouvrir");
		m.put("shop.gui.gold", "&6Votre or : &e{0}");
		m.put("shop.gui.help", "&7Paiement avec les lingots (et blocs) d'or\n&7de votre inventaire.\n&eClic gauche&7 : acheter un lot\n&eMaj + clic gauche&7 : acheter 8 lots\n&eClic droit&7 : vendre un lot\n&eMaj + clic droit&7 : tout vendre");
		m.put("shop.gui.entry.name", "&e{0}");
		m.put("shop.gui.entry.name_locked", "&c{0}");
		m.put("shop.gui.entry.lot", "&7Lot : &fx{0}");
		m.put("shop.gui.entry.buy", "&7Achat : &e{0} or &8(8 lots : {1} or)");
		m.put("shop.gui.entry.no_buy", "&8Non vendu");
		m.put("shop.gui.entry.sell", "&7Vente : &e{0} or &8par lot");
		m.put("shop.gui.entry.no_sell", "&8Non racheté");
		m.put("shop.gui.entry.owned", "&7Vous en possédez : &f{0}");
		m.put("shop.gui.entry.locked", "&cNiveau de guilde requis : {0} &8(actuel : {1})");
		m.put("shop.gui.entry.hint_buy", "&eClic gauche&7 : acheter &8| &eMaj&7 : {0} lots");
		m.put("shop.gui.entry.hint_sell", "&eClic droit&7 : vendre &8| &eMaj&7 : tout vendre");
		Lang.addDefaults(m);
	}
}
