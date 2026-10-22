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
		m.put("unique.power.jaguar_pounce.name", "Bond du jaguar");
		m.put("unique.power.jaguar_pounce.lore", "Clic droit (charge) : vous bondissez comme un jaguar ;\nà l'atterrissage, les griffes lacèrent les monstres\n"
				+ "à {0} blocs ({1} ❤, Lenteur {2} pendant {3} s).\nAucun dégât de chute pendant le bond.");
		m.put("unique.power.frost_crown.name", "Couronne de givre");
		m.put("unique.power.frost_crown.lore", "Un monstre qui vous frappe est pris dans la glace\n(Lenteur {0} pendant {1} s, {2} ❤ de froid) et le froid\n"
				+ "ralentit les monstres proches. Vous ne gelez jamais.");
		m.put("unique.power.soul_aegis.name", "Rempart des âmes");
		m.put("unique.power.soul_aegis.lore", "Un coup de monstre paré au bouclier revient à son\nauteur ({0} % des dégâts, {1} ❤ au moins) et son\n"
				+ "âme vous rend {2} ❤.");
		m.put("unique.power.solar_leap.name", "Ascension solaire");
		m.put("unique.power.solar_leap.lore", "Clic droit : vous jaillissez vers le ciel (sans dégâts\nde chute). À l'atterrissage ou au coup écrasant, un\n"
				+ "soleil éclate : les monstres à {0} blocs brûlent\n({1} ❤ de feu, ×{2} contre les morts-vivants).");
		m.put("unique.power.pact_seal.name", "Sceau du pacte");
		m.put("unique.power.pact_seal.lore", "Chaque coup scelle le monstre. Au {0}e sceau, un\npentagramme maudit s'ouvre sous lui et éclate\n"
				+ "({1} ❤, Flétrissement) ; la malédiction gagne\nles monstres à {2} blocs.");
		m.put("unique.power.infernal_rift.name", "Faille infernale");
		m.put("unique.power.infernal_rift.lore", "Clic droit : la hache fend le sol ; une faille de\nmagma file devant vous sur {0} blocs et embrase\n"
				+ "les monstres qu'elle traverse ({1} ❤ de feu).");
		m.put("unique.power.jade_gale.name", "Souffle de jade");
		m.put("unique.power.jade_gale.lore", "Sous {0} % de vie, le souffle du Serpent à plumes\nrepousse et empoisonne les monstres à {1} blocs ;\n"
				+ "vous régénérez et planez comme une plume.");
		// armor sets (uniques.json5 "sets"): lore of the pieces, bonuses, powers
		m.put("unique.set.header", "{0} &8({1} pièces)");
		m.put("unique.set.header_worn", "{0} &8({1}/{2} portées)");
		m.put("unique.set.piece", "&8 • &7{0}");
		m.put("unique.set.piece.worn", "&a ✔ &f{0}");
		m.put("unique.set.piece.missing", "&8 ✘ {0}");
		m.put("unique.set.bonus.neutral", "&6{0} pièces : &e{1}");
		m.put("unique.set.bonus.on", "&a✔ {0} pièces : {1}");
		m.put("unique.set.bonus.off", "&8{0} pièces : {1}");
		m.put("unique.set.line.neutral", "&7  {0}");
		m.put("unique.set.line.on", "&f  {0}");
		m.put("unique.set.line.off", "&8  {0}");
		m.put("unique.set.cooldown", "Recharge : {0} s");
		m.put("unique.set.tier_up", "{0} &8({1}/{2}) &a✔ {3}");
		m.put("unique.set.tier_down", "{0} &8({1}/{2}) &7✘ {3}");
		m.put("unique.attribute.value", "{0} {1}");
		m.put("unique.attribute.percent", "{0} % {1}");
		m.put("unique.attribute.armor", "Armure");
		m.put("unique.attribute.armor_toughness", "Robustesse");
		m.put("unique.attribute.max_health", "Vie maximale");
		m.put("unique.attribute.movement_speed", "Vitesse");
		m.put("unique.attribute.safe_fall_distance", "Hauteur de chute sans dégâts");
		m.put("unique.attribute.attack_damage", "Dégâts d'attaque");
		m.put("unique.attribute.attack_speed", "Vitesse d'attaque");
		m.put("unique.attribute.knockback_resistance", "Résistance au recul");
		m.put("unique.attribute.burning_time", "Durée de combustion");
		m.put("unique.attribute.jump_strength", "Force de saut");
		m.put("unique.attribute.step_height", "Hauteur de pas");
		m.put("unique.attribute.luck", "Chance");
		m.put("unique.attribute.oxygen_bonus", "Souffle");
		m.put("unique.attribute.sneaking_speed", "Vitesse accroupi");
		m.put("unique.attribute.max_absorption", "Absorption maximale");
		m.put("unique.attribute.entity_interaction_range", "Portée d'attaque");
		m.put("unique.attribute.block_interaction_range", "Portée");
		m.put("unique.setpower.frost_blood.lore", "Vous ne gelez jamais (neige poudreuse).");
		m.put("unique.setpower.winter_heart.lore", "Insensible à la lenteur. Sous {0} % de vie après\nun coup, une nova de givre gèle les monstres\n"
				+ "à {1} blocs ({2} ❤, Lenteur {3} pendant {4} s) ;\nRésistance {5} et absorption pendant {6} s.");
		m.put("unique.setpower.jaguar_ambush.lore", "Accroupi sans attaquer {0} s (moins dans le\nfeuillage) : camouflé, les monstres perdent\n"
				+ "votre trace. L'attaque suivante est une embuscade\n(+{1} ❤) et les griffes lacèrent les monstres\nà {2} blocs ({3} ❤).");
		m.put("unique.setpower.soul_pyre.lore", "Toutes les {0} s, draine l'âme des monstres\nà {1} blocs ({2} ❤ chacun) et vous rend {3} %\n"
				+ "du butin. En courant, une traînée de feu d'âme\nembrase les monstres qui la traversent.");
		m.put("unique.setpower.winter_heart.fired", "&b❄ Cœur de l'hiver &8· &7la nova de givre vous protège");
		m.put("unique.setpower.jaguar_ambush.camouflaged", "&6Camouflé &8· &7votre prochaine attaque est une embuscade");
		m.put("unique.setpower.jaguar_ambush.strike", "&6Embuscade du jaguar !");
		m.put("unique.setpower.jaguar_ambush.ready", "&6Embuscade du jaguar &8· &7prête");
		// collection menu: the sets
		m.put("unique.collection.set", "&8✧ {0}");
		m.put("unique.collection.set.count", "&7Pièces obtenues : &e{0}&7 / {1}");
		m.put("unique.collection.set.obtained", "&a ✔ &f{0}");
		m.put("unique.collection.set.missing", "&8 • ???");
		m.put("unique.collection.set.hint", "&8Portez plusieurs pièces de la panoplie\n&8pour éveiller ses bonus.");
		// favourite weapons of some guardians (uniques.json5 "favorites", context after the '@')
		m.put("unique.favorite.grand_temple_maya", "&6le gardien du Grand Temple du Jaguar céleste");
		m.put("unique.favorite.temple_maya", "&2le gardien du Temple du Serpent à plumes");
		m.put("unique.favorite.bastion_de_givre", "&ble gardien du Bastion de givre");
		m.put("unique.favorite.ziggourat_des_sables", "&ele gardien de la Ziggourat des sables");
		m.put("unique.favorite.citadelle_des_ames", "&3le gardien de la Citadelle des âmes");
		m.put("unique.favorite.autel_infernal", "&cle gardien de l'Autel infernal");
		m.put("unique.favorite.crypte_oubliee", "&5le gardien de la Crypte oubliée");
		m.put("unique.favorite.tour_du_mage", "&9le gardien de la Tour du Mage déchu");
		m.put("unique.favorite.cursed", "&5&ples gardiens maudits");
		m.put("unique.favorite.unknown", "{0} &8({1})");
		// feedback
		m.put("unique.cooldown", "&7{0} &8» &crecharge : &f{1} s");
		m.put("unique.blink.no_target", "&7Aucun monstre en vue.");
		m.put("unique.blink.blocked", "&cImpossible de vous téléporter là-bas.");
		m.put("unique.leap.blocked", "&cPas assez de place au-dessus de vous.");
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
