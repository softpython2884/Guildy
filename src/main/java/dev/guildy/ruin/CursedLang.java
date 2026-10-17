package dev.guildy.ruin;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French texts of the cursed guardians (lang.json, '&' colour codes).
 */
final class CursedLang {
	private CursedLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// The cursed incantation scroll
		m.put("ruin.cursed.item.name", "&5&lParchemin d'incantation &4&lmaudit");
		m.put("ruin.cursed.item.header", "&4☠ &5Objet rituel maudit &8— usage unique &4☠");
		m.put("ruin.cursed.item.story", "&7Un rouleau noir qui suinte un sang violacé.\n&7Lu au cœur d'une &fruine&7 dont le gardien dort,\n&7il l'éveille &5maudit&7 : bien plus fort, bien plus fou.");
		m.put("ruin.cursed.item.curse", "&5Malédiction : &dvie ×{0}&5, &ddégâts ×{1}&5, &d3 phases");
		m.put("ruin.cursed.item.curse.attacks", "&8(pentagrammes, chaînes d'âmes, météores, foudre…)");
		m.put("ruin.cursed.item.price", "&4Prix du sang : &c{0} ❤ &4par joueur présent");
		m.put("ruin.cursed.item.price.min", "&8(jamais en dessous de {0} ❤)");
		m.put("ruin.cursed.item.price.none", "&7Aucun prix du sang sur ce serveur.");
		m.put("ruin.cursed.item.rewards", "&6Récompenses : &eor et XP ×{0}&6, armes uniques,");
		m.put("ruin.cursed.item.rewards.more", "&6parchemins de sort, grimoire… &8(menace +{0})");
		m.put("ruin.cursed.item.use", "&7Clic droit maintenu &e{0} s&7, à moins de &e{1}&7 blocs");
		m.put("ruin.cursed.item.ritual", "&7du gardien endormi : le rituel dure &e{0} s&7.");
		m.put("ruin.cursed.item.dormant", "&8Ne fonctionne que dans une ruine dormante.");
		m.put("ruin.cursed.item.single_use", "&8Rendu si le rituel échoue, perdu une fois le gardien éveillé.");
		m.put("ruin.cursed.reward.quest", "&5&l☠ &e{0}&5 reçoit un objet maudit : {1}&5 !");
		m.put("ruin.summon.fail.cursed_disabled", "&cLes gardiens maudits sont désactivés sur ce serveur.");

		// Cursed ritual
		m.put("ruin.cursed.ritual.bar", "&5&l☠ &4Rituel maudit &7— {0} &5&l☠");
		m.put("ruin.cursed.ritual.title", "&5&l☠ Rituel maudit ☠");
		m.put("ruin.cursed.ritual.subtitle", "&4Votre sang réveille {0}&4… maudit");
		m.put("ruin.cursed.ritual.chat", "&5☠ {0} &7lit un &5parchemin maudit&7 : le sang des présents va éveiller {1} &5&lMaudit&7 dans la &f{2}&7 ! Restez près du cercle… et préparez-vous.");
		m.put("ruin.cursed.ritual.drain", "&4❤ &5Le pentagramme boit votre sang… &7restez près du cercle !");
		m.put("ruin.cursed.ritual.drain.none", "&5La malédiction puise dans votre âme… &7restez près du cercle !");
		m.put("ruin.cursed.ritual.screams", "&8&oDes âmes hurlent sous la ruine…");

		// The fight
		m.put("ruin.cursed.name", "{0} &5&lMaudit");
		m.put("ruin.cursed.bar", "{0} &5&lMaudit &8— {1} &8— &dPhase {2}");
		m.put("ruin.cursed.bar.transition", "&5&l☠ {0} &dse transforme… &8— &dPhase {1} &5&l☠");
		m.put("ruin.cursed.awaken.title", "&5&l☠ {0} &5&l☠");
		m.put("ruin.cursed.awaken.subtitle", "{0} &5&lMaudit &ds'éveille…");
		m.put("ruin.cursed.awaken.chat", "&5☠ {0} &5&lMaudit&7 surgit de la malédiction (&f{1}&7) : &dvie ×{2}&7, &ddégâts ×{3}&7, &d3 phases&7. Il ne ressuscite pas : chaque phase le rend plus fort.");
		m.put("ruin.cursed.phase.title.2", "&5&lPHASE II");
		m.put("ruin.cursed.phase.subtitle.2", "{0} &dpuise dans la malédiction…");
		m.put("ruin.cursed.phase.title.3", "&4&lPHASE III");
		m.put("ruin.cursed.phase.subtitle.3", "{0} &clibère toute sa haine…");
		m.put("ruin.cursed.phase.chat.2", "&5☠ Phase II : &dpentagrammes&7 au sol (sortez-en, ils boivent votre vie et le soignent), &dchaînes d'âmes&7, &dpluie de météores noirs&7.");
		m.put("ruin.cursed.phase.chat.3", "&4☠ Phase III : &cténèbres&7, &ccages de foudre&7 (sortez du cercle), &cnova de sang&7 (éloignez-vous), &combres&7 et aura maudite.");
		m.put("ruin.cursed.phase.back", "{0} &5revient à la charge, plus fort !");
		m.put("ruin.cursed.warn.pentagram", "&5⚠ Un pentagramme s'embrase sous vos pieds !");
		m.put("ruin.cursed.warn.chains", "&5⚠ Des chaînes d'âmes vous agrippent !");
		m.put("ruin.cursed.warn.meteors", "&4⚠ Des météores noirs tombent du ciel !");
		m.put("ruin.cursed.warn.darkness", "&8⚠ Les ténèbres se resserrent…");
		m.put("ruin.cursed.warn.cage", "&e⚠ Une cage de foudre se forme : sortez du cercle !");
		m.put("ruin.cursed.warn.nova", "&4⚠ {0} &caspire la vie de l'arène : éloignez-vous !");
		m.put("ruin.cursed.warn.clones", "&8⚠ Des ombres se détachent de {0}&8…");
		m.put("ruin.cursed.clone.name", "&8Ombre de {0}");
		m.put("ruin.cursed.vanish", "&5{0} &5&lMaudit &7se dissipe dans un hurlement : la malédiction retourne dormir… &8(le parchemin maudit est perdu)");
		m.put("ruin.cursed.victory.title", "&5&lLa malédiction est brisée !");
		m.put("ruin.cursed.victory.subtitle", "{0} &7est anéanti");
		m.put("ruin.cursed.victory.announce", "&5&l☠ La malédiction est brisée… &d{0} &7ont anéanti {1} &5&lMaudit &8(&f{2}&8) &7: &6butin légendaire !");
		m.put("ruin.cursed.victory.threat", "&f{0}&7 : la malédiction attise la menace, {1}&7. Son gardien reviendra bien plus fort.");

		// Admin
		m.put("ruin.cursed.admin.incantation", "&a{0} parchemin(s) d'incantation maudit(s) donné(s) à {1}.");
		m.put("ruin.cursed.admin.awakened", "&a{0} &5&lMaudit &as'éveille dans la &f{1}&a.");
		m.put("ruin.cursed.admin.not_cursed", "&cAucun combat maudit en cours dans la ruine la plus proche.");
		m.put("ruin.cursed.admin.phase", "&aTransition vers la phase {0} lancée.");
		m.put("ruin.cursed.admin.phase.failed", "&cImpossible : transition déjà en cours ou dernière phase atteinte.");
		m.put("ruin.cursed.admin.attack", "&aAttaque maudite '{0}' lancée.");
		m.put("ruin.cursed.admin.attack.failed", "&cAttaque '{0}' impossible : aucun joueur en survie à portée ou transition en cours ({1}).");
		Lang.addDefaults(m);
	}
}
