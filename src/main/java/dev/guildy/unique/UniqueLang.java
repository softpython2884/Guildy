package dev.guildy.unique;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the unique items module (keys prefixed with "unique.").
 */
final class UniqueLang {
	private UniqueLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// rarities
		m.put("unique.rarity.rare", "Rare");
		m.put("unique.rarity.epic", "Épique");
		m.put("unique.rarity.legendary", "Légendaire");
		m.put("unique.rarity.mythic", "Mythique");
		// item description
		m.put("unique.item.header", "&8✦ Objet unique · {0}");
		m.put("unique.item.story", "&7&o{0}");
		m.put("unique.item.power", "&e⚡ Pouvoir : &f{0}");
		m.put("unique.item.power_line", "&7{0}");
		m.put("unique.item.cooldown", "&8Recharge : {0} s");
		// powers ({n} = values from uniques.json5, ❤ = hearts)
		m.put("unique.power.storm_call.name", "Appel de la foudre");
		m.put("unique.power.storm_call.lore", "Clic droit : la foudre s'abat sur {0} monstres\nau plus autour du point visé ({1} ❤ chacun).");
		m.put("unique.power.frost_nova.name", "Onde de givre");
		m.put("unique.power.frost_nova.lore", "Clic droit : gèle les monstres à {0} blocs\n(Lenteur {1} pendant {2} s, {3} ❤ de froid).");
		m.put("unique.power.blink_strike.name", "Pas de l'ombre");
		m.put("unique.power.blink_strike.lore", "Clic droit : téléportation derrière le monstre\nvisé (jusqu'à {0} blocs) et coup de {1} ❤.");
		m.put("unique.power.tree_feller.name", "Abattage");
		m.put("unique.power.tree_feller.lore", "Coupe tout l'arbre d'un seul coup\n(arbres naturels, {0} bûches au plus).\nAccroupi : une seule bûche.");
		m.put("unique.power.excavate.name", "Excavation");
		m.put("unique.power.excavate.lore", "Creuse en 3×3 dans la roche naturelle.\nAccroupi : un seul bloc.");
		m.put("unique.power.vein_miner.name", "Filon");
		m.put("unique.power.vein_miner.lore", "Extrait tout le filon d'un coup\n({0} minerais au plus). Accroupi : un seul bloc.");
		m.put("unique.power.double_jump.name", "Double saut");
		m.put("unique.power.double_jump.lore", "Appuyez deux fois sur Sauter pour rebondir\ndans les airs. Plus aucun dégât de chute.");
		m.put("unique.power.shockwave.name", "Onde de choc");
		m.put("unique.power.shockwave.lore", "Une attaque écrasante (en tombant) frappe\naussi les monstres à {0} blocs ({1} ❤).");
		m.put("unique.power.phoenix_arrows.name", "Flèches du phénix");
		m.put("unique.power.phoenix_arrows.lore", "Chaque flèche qui touche embrase les\nmonstres à {0} blocs autour ({1} ❤ de feu).");
		m.put("unique.power.tide_call.name", "Colère des abysses");
		m.put("unique.power.tide_call.lore", "Lancé, il foudroie sa cible ({0} ❤) et l'éclair\nrebondit sur {1} monstres proches.");
		m.put("unique.power.soul_harvest.name", "Moisson des âmes");
		m.put("unique.power.soul_harvest.lore", "Chaque monstre tué vous rend {0} ❤\net vous donne un regain de vitesse.");
		// feedback
		m.put("unique.cooldown", "&7{0} &8» &crecharge : &f{1} s");
		m.put("unique.blink.no_target", "&7Aucun monstre en vue.");
		m.put("unique.blink.blocked", "&cImpossible de vous téléporter là-bas.");
		// announcements
		m.put("unique.announce", "&6✦ &e{0} &6a obtenu {1} &8({2}&8) &6!");
		m.put("unique.announce.title", "&6&l✦ Arme unique ✦");
		m.put("unique.announce.subtitle", "{0}");
		m.put("unique.source.boss", "&7butin d'un boss de horde");
		m.put("unique.source.blood_boss", "&cbutin d'un boss de la nuit de sang");
		m.put("unique.source.quest", "&7récompense de quête");
		m.put("unique.source.legendary_quest", "&7récompense d'une quête légendaire");
		m.put("unique.source.expedition", "&7rapportée d'une expédition");
		m.put("unique.source.trade", "&7achetée au Carrefour commercial");
		m.put("unique.source.admin", "&7offerte par un administrateur");
		m.put("unique.source.ruin_boss", "&7butin d'un gardien de ruine");
		m.put("unique.source.nether_ruin_boss", "&cbutin d'un gardien de ruine du Nether");
		m.put("unique.source.legacy", "&7obtenue avant le registre");
		m.put("unique.source.unknown", "&7origine mystérieuse");
		// uniqueness (registry of the copies)
		m.put("unique.mode.guild", "une par guilde");
		m.put("unique.mode.server", "une seule sur le serveur");
		m.put("unique.mode.none", "sans limite");
		m.put("unique.fallback.scroll", "&7Votre guilde détient déjà toutes les armes uniques de ce butin : &dun parchemin de sort&7 à la place.");
		m.put("unique.where.inventory", "inventaire");
		m.put("unique.where.ender_chest", "coffre de l'Ender");
		m.put("unique.where.container", "coffre ouvert");
		m.put("unique.lost.killed", "&cdétruite (/kill)");
		m.put("unique.lost.destroyed", "&cdétruite (lave, feu, cactus, vide ou disparition)");
		m.put("unique.lost.broken", "&ccassée");
		m.put("unique.lost.admin", "&clibérée par un admin");
		m.put("unique.lost.unknown", "&cperdue");
		m.put("unique.time.never", "jamais");
		m.put("unique.time.now", "à l'instant");
		m.put("unique.time.minutes", "il y a {0} min");
		m.put("unique.time.hours", "il y a {0} h");
		m.put("unique.time.days", "il y a {0} j");
		// collection (guild menu)
		m.put("unique.menu.name", "&d&lArmes uniques");
		m.put("unique.menu.lore", "&7La collection d'armes uniques\n&7de la guilde : qui les détient,\n&7où trouver les autres.");
		m.put("unique.menu.count", "&7Collection : &e{0}&7 / {1}");
		m.put("unique.collection.title", "Armes uniques · {0} / {1}");
		m.put("unique.collection.title_paged", "Armes uniques · {0} / {1} ({2}/{3})");
		m.put("unique.collection.empty", "&7Aucune arme unique n'est configurée.");
		m.put("unique.collection.header.name", "&d&lCollection d'armes uniques");
		m.put("unique.collection.header.count", "&7Obtenues : &e{0}&7 / {1}");
		m.put("unique.collection.header.held", "&7Détenues aujourd'hui : &a{0}");
		m.put("unique.collection.mode.guild", "&8Chaque arme n'existe qu'une fois par guilde :\n&8le butin ne vous donne jamais une arme\n&8que la guilde détient déjà.");
		m.put("unique.collection.mode.server", "&8Chaque arme n'existe qu'une fois sur le serveur :\n&8détruite, elle peut tomber à nouveau.");
		m.put("unique.collection.mode.none", "&8Une même arme peut tomber plusieurs fois.");
		m.put("unique.collection.held", "&a✔ &f{0} &8(vue {1})");
		m.put("unique.collection.held_more", "&8… et {0} autre(s) exemplaire(s)");
		m.put("unique.collection.holder.guild", "au village");
		m.put("unique.collection.gone", "&8Obtenue autrefois : elle n'est plus\n&8entre les mains de la guilde.");
		m.put("unique.collection.unknown", "{0}???");
		m.put("unique.collection.where", "&7Où la trouver :");
		m.put("unique.collection.where_line", "&8• {0} &8({1} %)");
		m.put("unique.collection.where_none", "&8• nulle part pour l'instant");
		m.put("unique.collection.complete", "&6✦ La guilde &e{0}&6 a réuni les {1} armes uniques !");
		m.put("unique.collection.complete.title", "&6&l✦ Collection complète ✦");
		m.put("unique.collection.complete.subtitle", "&eLes {0} armes uniques ont rejoint la guilde");
		// trade hub preview
		m.put("unique.trade.random.name", "&d&lArme unique &7(tirée au hasard)");
		m.put("unique.trade.random.lore", "&7Une arme unique de la table « trade »\n&7d'uniques.json5, tirée à l'arrivée du marchand.");
		// admin
		m.put("unique.admin.list.header", "&6&lArmes uniques &7({0}) &8- cliquez sur un nom pour la donner");
		m.put("unique.admin.list.entry", "&8- {0} &8[&7{1}&8] {2} &8· &7{3}");
		m.put("unique.admin.list.hover", "&7Pouvoir : &e{0}\n&7Butin : {1}\n&eCliquez pour préparer la commande give");
		m.put("unique.admin.list.no_power", "aucun");
		m.put("unique.admin.list.no_drop", "&8jamais");
		m.put("unique.admin.list.tables", "&7Chances par source : {0}");
		m.put("unique.admin.list.disabled", "&c(butin désactivé : dropsEnabled = false dans uniques.json5)");
		m.put("unique.admin.list.models", "&7Modèles personnalisés : {0}");
		m.put("unique.admin.models.on", "&aactivés");
		m.put("unique.admin.models.off", "&7désactivés (apparence vanilla)");
		m.put("unique.admin.give", "&aArme unique {0}&a donnée à &e{1}&a.");
		m.put("unique.admin.unknown", "&cArme unique inconnue : &e{0}&c (voir /guild admin unique list).");
		m.put("unique.admin.list.mode", "&7Unicité : &e{0}&7 (registre : /guild admin unique registry)");
		m.put("unique.admin.give.duplicate_guild", "&eAttention : la guilde &6{1}&e détenait déjà {0}&e : doublon créé (les dons ignorent l'unicité).");
		m.put("unique.admin.give.duplicate_server", "&eAttention : {0}&e existait déjà sur le serveur : doublon créé (les dons ignorent l'unicité).");
		m.put("unique.admin.registry.header", "&6&lRegistre des armes uniques &7(unicité : {0} · {1} exemplaire(s), {2} actif(s))");
		m.put("unique.admin.registry.hover", "&eCliquez pour voir les exemplaires");
		m.put("unique.admin.registry.entry", "&8- {0} &8[&7{1}&8] &a{2} actif(s)&8, &7{3} perdu(s)");
		m.put("unique.admin.registry.duplicates", "&c&lDoublons détectés ({0}) &7- même numéro vu à deux endroits à la fois :");
		m.put("unique.admin.registry.duplicate", "&8- &f#{0} {1} &8: &7{2} &8/ &7{3} &8(vu {4} fois, dernière fois {5})");
		m.put("unique.admin.registry.copies", "&6&l{0} &7: {1} exemplaire(s)");
		m.put("unique.admin.registry.copy.active", "&8- &f#{0} &aACTIVE &8· &e{1} &8· &7vue {2} &8· {3}");
		m.put("unique.admin.registry.copy.lost", "&8- &f#{0} &cPERDUE &8· {1} &7{2} &8· &7dernier détenteur : {3}");
		m.put("unique.admin.registry.copy.hover", "&7Source : {0}\n&7Obtenue : {1}\n&7Joueur : {2}");
		m.put("unique.admin.registry.copy.free_hint", "&eCliquez pour préparer /guild admin unique free");
		m.put("unique.admin.registry.more", "&8… {0} exemplaire(s) plus ancien(s) non affiché(s)");
		m.put("unique.admin.free.no_guild", "&cGuilde inconnue : &e{0}");
		m.put("unique.admin.free.of_guild", " de la guilde {0}");
		m.put("unique.admin.free.none", "&cAucun exemplaire actif de {0}&c{1}.");
		m.put("unique.admin.free.done", "&a{0} exemplaire(s) de {1}&a marqué(s) perdu(s) ({2}) : l'arme peut de nouveau tomber."
				+ " &7(Un exemplaire revu dans un inventaire redevient actif.)");
		Lang.addDefaults(m);
	}
}
