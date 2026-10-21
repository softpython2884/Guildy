package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the alchemist's jobs, roles, potions and stock menu, plus the French names of the brewing reagents
 * used in its statuses (the server only knows the English names).
 */
final class AlchemistLang {
	private AlchemistLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// jobs and roles
		m.put("villager.alchemy.job.books", "Livres");
		m.put("villager.alchemy.job.scrolls", "Parchemins");
		m.put("villager.alchemy.job.potions", "Potions");
		m.put("villager.alchemy.role.auto", "Auto");
		m.put("villager.alchemy.role.distributed", "réparti");
		m.put("villager.alchemy.role.manual", "choisi");
		// why a job is impossible
		m.put("villager.alchemy.block.no_table", "pas de table d'enchantement");
		m.put("villager.alchemy.block.no_stand", "pas d'alambic");
		m.put("villager.alchemy.block.unloaded", "territoire hors de portée");
		m.put("villager.alchemy.block.no_books", "pas de livres");
		m.put("villager.alchemy.block.no_lapis", "pas assez de lapis-lazuli ({0} par livre)");
		m.put("villager.alchemy.block.no_paper", "pas de papier ({0} par parchemin)");
		m.put("villager.alchemy.block.no_scroll", "aucun parchemin possible à cette table");
		m.put("villager.alchemy.block.cap", "plafond du jour atteint ({0}/{1}), reprend demain");
		m.put("villager.alchemy.block.disabled", "désactivé");
		m.put("villager.alchemy.block.stock_full", "stocks de potions complets");
		m.put("villager.alchemy.block.no_bottles", "pas de fioles (d'eau, ou vides avec de l'eau près d'un alambic)");
		m.put("villager.alchemy.block.no_fuel", "pas de {0}");
		m.put("villager.alchemy.block.no_reagents", "ingrédients manquants pour {0}");
		m.put("villager.alchemy.reason", "&f{0}&7 : {1}");
		m.put("villager.alchemy.reason.separator", " &8· &7");
		// statuses
		m.put("villager.status.alchemy.scan", "&7Cherche les tables d'enchantement et les alambics &8({0}/{1})");
		m.put("villager.status.alchemy.scan_wait", "&7Un autre alchimiste explore le territoire");
		m.put("villager.status.alchemy.nothing", "&7Rien à faire : {0}");
		m.put("villager.status.alchemy.missing", "&c{0}");
		m.put("villager.status.alchemy.writing", "&dÉcrit un parchemin (niveau {0}) {1} %");
		m.put("villager.status.alchemy.fetch_water", "&eVa chercher des fioles d'eau au stockage");
		m.put("villager.status.alchemy.fetch_glass", "&eVa chercher des fioles vides au stockage");
		m.put("villager.status.alchemy.fetch_bottles", "&eVa chercher des fioles ({0}) au stockage");
		m.put("villager.status.alchemy.fetch_reagent", "&eVa chercher : {0}");
		m.put("villager.status.alchemy.fetch_fuel", "&eVa chercher : {0}");
		m.put("villager.status.alchemy.brew_going", "&eVa à l'alambic &8· &7{0} &8({1} fiole(s))");
		m.put("villager.status.alchemy.filling", "&bRemplit {0} fiole(s) d'eau");
		m.put("villager.status.alchemy.no_water", "&cPas d'eau près de l'alambic pour remplir les fioles vides");
		m.put("villager.status.alchemy.brewing", "&dBrasse {0} &8· &7{1} ({2}/{3}) {4} %");
		m.put("villager.status.alchemy.brewed", "&aA préparé {0} × &d{1}");
		m.put("villager.status.alchemy.delivering_potions", "&eRange {0} potion(s) au stockage");
		m.put("villager.status.alchemy.no_room_potions", "&cAucun coffre libre pour ranger les potions");
		m.put("villager.status.alchemy.stowing", "&eRapporte {0} objet(s) au stockage pour faire de la place");
		// villager menu
		m.put("villager.gui.lore.alchemy.role", "&7Rôle : &f{0} &8({1})");
		m.put("villager.gui.lore.alchemy.fallback", "&e↪ {0} → {1}");
		m.put("villager.gui.lore.stat.potions", "&7Potions préparées : &f{0} &8(aujourd'hui : {1}/{2})");
		m.put("villager.gui.lore.stat.last_potion", "&7Dernière potion : &d{0}");
		m.put("villager.gui.alchemy.role", "&dRôle : {0}");
		m.put("villager.gui.alchemy.role.lore", "&7Travail préféré de l'alchimiste. Quand il est\n&7impossible, il passe au suivant possible :\n&f{0}\n&7Auto : la guilde répartit les alchimistes\n&7(un peu de tout en même temps).");
		m.put("villager.gui.alchemy.role.now", "&7En ce moment : &f{0}");
		m.put("villager.gui.alchemy.role.click", "&eClic gauche : rôle suivant &8· &eclic droit : précédent");
		m.put("villager.gui.alchemy.role.set", "&a{0} a maintenant le rôle : &f{1}");
		m.put("villager.gui.alchemy.stock", "&dStocks de potions");
		m.put("villager.gui.alchemy.stock.lore", "&7Combien de fioles de chaque potion et élixir\n&7les alchimistes gardent au stockage.\n&7Ils préparent d'abord ce qui manque le plus.");
		m.put("villager.gui.alchemy.stock.click", "&eClic : ouvrir");
		// stock menu
		m.put("villager.gui.stock.title", "Stocks de potions");
		m.put("villager.gui.stock.target", "&7Stock voulu : &f{0} &8(défaut {1})");
		m.put("villager.gui.stock.held", "&7Au stockage : &f{0}");
		m.put("villager.gui.stock.recipe", "&8Recette : {0}");
		m.put("villager.gui.stock.no_recipe", "&cAucune recette connue (alchemy.json5)");
		m.put("villager.gui.stock.click", "&eClic gauche +1 · droit -1 · Maj : ±8");
		m.put("villager.gui.stock.reset", "&cRemettre les stocks par défaut");
		m.put("villager.gui.stock.reset.lore", "&7Les stocks de alchemy.json5 pour tous les produits.");
		m.put("villager.gui.stock.reset.done", "&aStocks de potions remis par défaut.");
		m.put("villager.gui.stock.page", "&7Page {0}/{1}");
		// reagents (statuses)
		m.put("villager.alchemy.item.nether_wart", "verrue du Nether");
		m.put("villager.alchemy.item.glistering_melon_slice", "tranche de pastèque scintillante");
		m.put("villager.alchemy.item.glowstone_dust", "poudre lumineuse");
		m.put("villager.alchemy.item.redstone", "poudre de redstone");
		m.put("villager.alchemy.item.gunpowder", "poudre à canon");
		m.put("villager.alchemy.item.dragon_breath", "souffle de dragon");
		m.put("villager.alchemy.item.fermented_spider_eye", "œil d'araignée fermenté");
		m.put("villager.alchemy.item.spider_eye", "œil d'araignée");
		m.put("villager.alchemy.item.sugar", "sucre");
		m.put("villager.alchemy.item.magma_cream", "crème de magma");
		m.put("villager.alchemy.item.golden_carrot", "carotte dorée");
		m.put("villager.alchemy.item.rabbit_foot", "patte de lapin");
		m.put("villager.alchemy.item.ghast_tear", "larme de Ghast");
		m.put("villager.alchemy.item.blaze_powder", "poudre de blaze");
		m.put("villager.alchemy.item.pufferfish", "poisson-globe");
		m.put("villager.alchemy.item.turtle_helmet", "carapace de tortue");
		m.put("villager.alchemy.item.phantom_membrane", "membrane de Phantom");
		m.put("villager.alchemy.item.breeze_rod", "bâton de Breeze");
		m.put("villager.alchemy.item.cobweb", "toile d'araignée");
		m.put("villager.alchemy.item.slime_block", "bloc de slime");
		m.put("villager.alchemy.item.stone", "pierre");
		m.put("villager.alchemy.item.armadillo_scute", "écaille de tatou");
		m.put("villager.alchemy.item.amethyst_shard", "éclat d'améthyste");
		m.put("villager.alchemy.item.echo_shard", "éclat d'écho");
		m.put("villager.alchemy.item.heart_of_the_sea", "cœur de la mer");
		m.put("villager.alchemy.item.golden_apple", "pomme dorée");
		m.put("villager.alchemy.item.glow_berries", "baies lumineuses");
		m.put("villager.alchemy.item.glass_bottle", "fiole vide");
		Lang.addDefaults(m);
	}

	/** A blocking reason as text. */
	static String block(String key, Object... args) {
		return Lang.raw("villager.alchemy.block." + key, args);
	}

	/** "Livres : pas de livres". */
	static String reason(AlchemyJob job, String text) {
		return Lang.raw("villager.alchemy.reason", Lang.raw(job.langKey()), text);
	}

	static String separator() {
		return Lang.raw("villager.alchemy.reason.separator");
	}

	/** French name of a reagent ("minecraft:glowstone_dust" -> "poudre lumineuse"), else its id path. */
	static String itemName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		String key = "villager.alchemy.item." + path;
		return Lang.has(key) ? Lang.raw(key) : path.replace('_', ' ');
	}
}
