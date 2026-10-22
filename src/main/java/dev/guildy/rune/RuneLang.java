package dev.guildy.rune;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the rune module (keys prefixed with "rune."); the blacksmith's statuses are in villager.CraftLang.
 */
final class RuneLang {
	private RuneLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// tiers
		m.put("rune.tier.common", "Commune");
		m.put("rune.tier.rare", "Rare");
		m.put("rune.tier.mythic", "Mythique");
		// lore of a runed piece ({0} colour, {1} glyph, {2} name, {3} tier)
		m.put("rune.lore.header", "{0}{1} {2} &8· {3}");
		m.put("rune.lore.attribute", "&7 {0}");
		m.put("rune.lore.power", "&7 {0}");
		m.put("rune.lore.story", "&8&o {0}");
		// attribute bonuses ({0} = "+1", "+5 %")
		m.put("rune.attr.generic", "{0} {1}");
		m.put("rune.attr.armor", "{0} armure");
		m.put("rune.attr.armor_toughness", "{0} robustesse");
		m.put("rune.attr.knockback_resistance", "{0} résistance au recul");
		m.put("rune.attr.explosion_knockback_resistance", "{0} résistance au souffle des explosions");
		m.put("rune.attr.max_health", "{0} points de vie");
		m.put("rune.attr.max_absorption", "{0} points d'absorption");
		m.put("rune.attr.movement_speed", "{0} vitesse");
		m.put("rune.attr.luck", "{0} chance");
		m.put("rune.attr.attack_damage", "{0} dégâts d'attaque");
		m.put("rune.attr.attack_speed", "{0} vitesse d'attaque");
		m.put("rune.attr.attack_knockback", "{0} recul infligé");
		m.put("rune.attr.oxygen_bonus", "{0} souffle sous l'eau");
		m.put("rune.attr.water_movement_efficiency", "{0} aisance dans l'eau");
		m.put("rune.attr.movement_efficiency", "{0} aisance sur les sols difficiles");
		m.put("rune.attr.safe_fall_distance", "{0} blocs de chute sans dégâts");
		m.put("rune.attr.fall_damage_multiplier", "{0} dégâts de chute");
		m.put("rune.attr.jump_strength", "{0} force de saut");
		m.put("rune.attr.block_break_speed", "{0} vitesse de minage");
		m.put("rune.attr.mining_efficiency", "{0} efficacité de minage");
		m.put("rune.attr.submerged_mining_speed", "{0} minage sous l'eau");
		m.put("rune.attr.burning_time", "{0} durée des brûlures");
		m.put("rune.attr.sneaking_speed", "{0} vitesse accroupi");
		m.put("rune.attr.block_interaction_range", "{0} bloc de portée");
		m.put("rune.attr.entity_interaction_range", "{0} portée d'attaque");
		m.put("rune.attr.step_height", "{0} hauteur de pas");
		m.put("rune.attr.sweeping_damage_ratio", "{0} dégâts de balayage");
		// powers ({n} = values from runes.json5, ❤ = hearts)
		m.put("rune.power.sparks.name", "Étincelles");
		m.put("rune.power.sparks.lore", "Un monstre qui vous frappe au corps à corps\nprend une gerbe d'étincelles ({0} ❤, recharge {1} s).");
		m.put("rune.power.ember.name", "Braise");
		m.put("rune.power.ember.lore", "Un monstre qui vous frappe a {0} % de chances\nde prendre feu ({1} s).");
		m.put("rune.power.glow.name", "Lueur");
		m.put("rune.power.glow.lore", "Quand un monstre vous frappe, les monstres\nà {0} blocs brillent pendant {1} s.");
		m.put("rune.power.echo.name", "Écho");
		m.put("rune.power.echo.lore", "Restez accroupi et immobile {0} s : une onde\nrévèle les monstres à {1} blocs ({2} s).");
		m.put("rune.power.fox.name", "Renard");
		m.put("rune.power.fox.lore", "Les objets au sol à {0} blocs volent\nvers vous (sauf accroupi).");
		m.put("rune.power.bounce.name", "Rebond");
		m.put("rune.power.bounce.lore", "Après une chute de {0} blocs ou plus,\nvous rebondissez (sauf accroupi).");
		m.put("rune.power.phoenix.name", "Renaissance du Phénix");
		m.put("rune.power.phoenix.lore", "Un coup mortel vous relève de vos cendres\n({0} % de vie) dans une explosion de flammes\n"
				+ "qui embrase les monstres à {1} blocs.\nRecharge : {2} min (avant un totem).");
		m.put("rune.power.dragon.name", "Ailes du Dragon");
		m.put("rune.power.dragon.breath", "Souffle du dragon");
		m.put("rune.power.dragon.lore", "Le plastron plane comme des élytres\n(sautez en l'air). Accroupi + saut : souffle\n"
				+ "du dragon ({0} ❤ aux monstres, {1} blocs,\nrecharge {2} s).");
		// inscription
		m.put("rune.inscribed", "&7Le forgeron &e{0}&7 a gravé une rune {1}&7 : {2}&7 sur {3}&7 !");
		m.put("rune.announce.mythic", "&d✦ &7Le forgeron &e{0}&7 de la guilde &6{1}&7 a gravé la &l{2}&7 sur {3}&7 !");
		m.put("rune.mythic.title", "&d&l✦ Rune mythique ✦");
		m.put("rune.mythic.subtitle", "{0}");
		// powers
		m.put("rune.cooldown", "&7{0} : encore &f{1} s");
		m.put("rune.echo.ping", "&3Écho : &f{0}&3 monstre(s) révélé(s)");
		m.put("rune.phoenix.title", "&6&lRenaissance !");
		m.put("rune.phoenix.subtitle", "&eLa Rune du Phénix vous relève de vos cendres");
		m.put("rune.phoenix.rise", "&6La Rune du Phénix vous relève de vos cendres ! &7Elle se rallume dans {0} min.");
		m.put("rune.phoenix.ready", "&6La Rune du Phénix brûle de nouveau.");
		// grindstone
		m.put("rune.ground", "&7La {0}&7 s'efface sous la meule.");
		m.put("rune.unknown", "rune");
		// admin
		m.put("rune.admin.list.header", "&6Runes ({0}) :");
		m.put("rune.admin.list.line", "&7- &f{0} &8({1}&8, {2}, poids {3}) &7{4}");
		m.put("rune.admin.list.disabled", "&c(désactivée)");
		m.put("rune.admin.unknown", "&cRune inconnue : {0}");
		m.put("rune.admin.no_item", "&c{0} ne tient aucune pièce d'armure qui puisse recevoir une rune (main principale).");
		m.put("rune.admin.not_eligible", "&cLa {0}&c ne va pas sur cette pièce.");
		m.put("rune.admin.inscribed", "&aRune gravée : {0}&a sur l'objet en main de {1}.");
		m.put("rune.admin.removed", "&aRune retirée de l'objet en main de {0}.");
		m.put("rune.admin.no_rune", "&c{0} ne tient aucun objet gravé d'une rune.");
		Lang.addDefaults(m);
	}
}
