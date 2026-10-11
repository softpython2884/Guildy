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
		m.put("unique.source.unknown", "&7origine mystérieuse");
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
		Lang.addDefaults(m);
	}
}
