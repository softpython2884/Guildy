package dev.guildy.scroll;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the spell scroll module (keys prefixed with "scroll."). Spell descriptions use named placeholders
 * ({radius}, {damage}...) replaced by the values of each scroll (damage and heals in hearts).
 */
final class ScrollLang {
	private ScrollLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// item description
		m.put("scroll.item.header", "&8✦ Parchemin de sort · {0}");
		m.put("scroll.item.story", "&7&o{0}");
		m.put("scroll.item.spell", "&b✧ Sort : &f{0}");
		m.put("scroll.item.spell_line", "&7{0}");
		m.put("scroll.item.cast", "&8Incantation : {0} s · Recharge : {1} s");
		m.put("scroll.item.group", "&8Recharge partagée : {0}");
		m.put("scroll.item.single_use", "&5Usage unique &8· maintenir le clic droit");
		// cooldown groups
		m.put("scroll.group.attaque", "Attaque");
		m.put("scroll.group.soutien", "Soutien");
		m.put("scroll.group.rappel", "Rappel");
		m.put("scroll.group.invocation", "Invocation");
		m.put("scroll.group.permutation", "Permutation");
		// spells
		m.put("scroll.spell.meteor_shower.name", "Pluie de météores");
		m.put("scroll.spell.meteor_shower.lore", "Fait pleuvoir {count} météores sur {radius} blocs\nautour du point visé (portée {range} blocs).\n"
				+ "Chaque impact : {damage} ❤ aux monstres à\n{impactRadius} blocs, qui brûlent {fireSeconds} s.");
		m.put("scroll.spell.chain_storm.name", "Tempête de foudre");
		m.put("scroll.spell.chain_storm.lore", "La foudre jaillit de vos mains et bondit\nde monstre en monstre : {count} cibles au plus,\n"
				+ "{damage} ❤ chacune, étourdies {stunSeconds} s.");
		m.put("scroll.spell.blizzard.name", "Blizzard");
		m.put("scroll.spell.blizzard.lore", "Une tempête de neige s'abat sur la zone visée\n({radius} blocs) pendant {duration} s : chaque seconde,\n"
				+ "les monstres gèlent (Lenteur {slowLevel})\net subissent {damage} ❤ de froid.");
		m.put("scroll.spell.solar_nova.name", "Nova solaire");
		m.put("scroll.spell.solar_nova.lore", "Une sphère de feu solaire jaillit de vous\njusqu'à {radius} blocs : {damage} ❤ aux monstres\n"
				+ "({undeadMultiplier} aux morts-vivants), embrasés\net repoussés.");
		m.put("scroll.spell.singularity.name", "Singularité");
		m.put("scroll.spell.singularity.lore", "Ouvre un trou noir au point visé : pendant\n{pullSeconds} s il aspire les monstres à {radius} blocs,\n"
				+ "puis implose ({damage} ❤).");
		m.put("scroll.spell.aegis.name", "Égide");
		m.put("scroll.spell.aegis.lore", "Un dôme doré de {radius} blocs protège la guilde\npendant {duration} s : Absorption {absorptionLevel} et\n"
				+ "Résistance {resistanceLevel} aux alliés ; les monstres\nsont repoussés, leurs projectiles se brisent.");
		m.put("scroll.spell.recall.name", "Rappel");
		m.put("scroll.spell.recall.lore", "Après une longue incantation, vous ramène\nau foyer de la guilde (ou à son cœur) avec\n"
				+ "les membres à {radius} blocs ({maxAllies} au plus).");
		m.put("scroll.spell.guardians.name", "Gardiens");
		m.put("scroll.spell.guardians.lore", "Invoque {count} golems de fer qui combattent\nles monstres à vos côtés pendant {duration} s,\n"
				+ "puis retournent à la poussière.");
		m.put("scroll.spell.blessing.name", "Bénédiction");
		m.put("scroll.spell.blessing.lore", "Soigne {heal} ❤ à vous, aux membres et aux\nvillageois de la guilde à {radius} blocs :\n"
				+ "Régénération {regenLevel} ({regenSeconds} s), effets\nnéfastes et flammes dissipés.");
		m.put("scroll.spell.revelation.name", "Révélation");
		m.put("scroll.spell.revelation.lore", "Révèle les monstres à {radius} blocs :\nils brillent à travers les murs pendant {duration} s.");
		m.put("scroll.spell.earthquake.name", "Séisme");
		m.put("scroll.spell.earthquake.lore", "La terre tremble autour de vous : {waves} ondes\nde choc jusqu'à {radius} blocs, {damage} ❤ par onde ;\n"
				+ "les monstres sont projetés en l'air et ralentis.");
		m.put("scroll.spell.arcane_ray.name", "Rayon arcanique");
		m.put("scroll.spell.arcane_ray.lore", "Un rayon jaillit de votre main pendant\n{duration} s et suit votre visée ({length} blocs) :\n"
				+ "{damage} ❤ par contact, cinq fois par seconde.");
		m.put("scroll.spell.war_banner.name", "Bannière de guerre");
		m.put("scroll.spell.war_banner.lore", "Plante une bannière de guerre pour {duration} s :\nà {radius} blocs autour d'elle, vous, les membres et\n"
				+ "les villageois de la guilde gagnez Force {strengthLevel}\net Célérité {hasteLevel} (attaques plus rapides).");
		m.put("scroll.spell.scourge.name", "Fléau");
		m.put("scroll.spell.scourge.lore", "Une nuée maudite s'abat sur la zone visée\n({radius} blocs) pendant {duration} s : les monstres\n"
				+ "brillent, faiblissent (Faiblesse {weaknessLevel}, Lenteur\n{slowLevel}) et subissent +{vulnerability} de dégâts de toutes\n"
				+ "sources (boss : {bossShare} de l'effet).");
		m.put("scroll.spell.swap.name", "Permutation");
		m.put("scroll.spell.swap.lore", "Échangez votre place avec la créature visée\n(en vue, {range} blocs) : un monstre est étourdi\n"
				+ "{stunSeconds} s, un allié de la guilde est protégé\n{shieldSeconds} s (Résistance II). Jamais dans un mur,\n"
				+ "dans la lave ou au-dessus du vide.");
		m.put("scroll.spell.rally.name", "Ralliement");
		// Guildy 1.13: every member, any distance, any dimension by default (one text per setting, see Spell.loreKey).
		m.put("scroll.spell.rally.lore.everywhere", "Après une longue incantation, sonne le\nralliement : tous les membres de la guilde\n"
				+ "en ligne, où qu'ils soient (autres dimensions\ncomprises), reçoivent [Rejoindre] pendant\n"
				+ "{acceptSeconds} s ; les {maxAllies} premiers qui cliquent\nvous rejoignent.");
		m.put("scroll.spell.rally.lore.dimension", "Après une longue incantation, sonne le\nralliement : tous les membres de la guilde\n"
				+ "en ligne dans votre dimension reçoivent\n[Rejoindre] pendant {acceptSeconds} s ; les {maxAllies} premiers\n"
				+ "qui cliquent vous rejoignent.");
		m.put("scroll.spell.rally.lore.range_cross", "Après une longue incantation, sonne le\nralliement : les membres de la guilde à {range}\n"
				+ "blocs (et ceux des autres dimensions)\nreçoivent [Rejoindre] pendant {acceptSeconds} s ; les\n"
				+ "{maxAllies} premiers qui cliquent vous rejoignent.");
		m.put("scroll.spell.rally.lore.range", "Après une longue incantation, sonne le\nralliement : les membres de la guilde à {range}\n"
				+ "blocs dans votre dimension reçoivent\n[Rejoindre] pendant {acceptSeconds} s ; les {maxAllies} premiers\n"
				+ "qui cliquent vous rejoignent.");
		m.put("scroll.spell.spectral_chains.name", "Chaînes spectrales");
		m.put("scroll.spell.spectral_chains.lore", "Des chaînes jaillissent du sol et enchaînent\nle monstre visé et ses voisins ({count} au plus,\n"
				+ "{radius} blocs) pendant {duration} s (boss : {bossShare}\ndu temps), puis se brisent ({damage} ❤).");
		m.put("scroll.spell.tailwind.name", "Hâte du vent");
		m.put("scroll.spell.tailwind.lore", "Un vent arrière se lève : pendant {duration} s,\nvous et la guilde à {radius} blocs gagnez\n"
				+ "Vitesse {speedLevel} et Saut amélioré {jumpLevel}, et ne\nsubissez plus de dégâts de chute.");
		// feedback
		m.put("scroll.cast", "&d✦ &f{0} &d!");
		m.put("scroll.fail.unknown", "&cCe parchemin est illisible (inconnu de scrolls.json5).");
		m.put("scroll.fail.disabled", "&cLes parchemins sont désactivés sur ce serveur.");
		m.put("scroll.fail.dimension", "&cLes parchemins ne fonctionnent pas dans cette dimension.");
		m.put("scroll.fail.cooldown", "&cParchemin en recharge : &f{0} s");
		m.put("scroll.fail.limit_player", "&cTrop de sorts actifs : attendez que les précédents se dissipent.");
		m.put("scroll.fail.limit_server", "&cTrop de sorts actifs sur le serveur, réessayez dans un instant.");
		m.put("scroll.fail.no_target", "&7Aucun monstre à portée : le parchemin est intact.");
		m.put("scroll.fail.no_monsters", "&7Aucun monstre aux alentours : le parchemin est intact.");
		m.put("scroll.fail.no_guild", "&cIl faut faire partie d'une guilde pour lire ce parchemin.");
		m.put("scroll.fail.no_home", "&cVotre guilde n'a ni foyer ni cœur où revenir.");
		m.put("scroll.fail.no_space", "&cPas assez de place autour de vous.");
		m.put("scroll.fail.guardian_limit", "&cVous avez déjà trop de gardiens.");
		m.put("scroll.fail.interrupted", "&7Incantation interrompue : le parchemin est intact.");
		m.put("scroll.fail.no_swap_target", "&7Visez un monstre ou un allié de la guilde (en vue) : le parchemin est intact.");
		m.put("scroll.fail.swap_blocked", "&cPas assez de place à l'arrivée : la permutation est annulée.");
		m.put("scroll.fail.swap_hazard", "&cDestination dangereuse (lave, feu, cactus...) : la permutation est annulée.");
		m.put("scroll.fail.swap_fall", "&cLe vide sous la cible : la permutation est annulée.");
		// Nobody to call: why, with the number of members concerned (RallyRules.refusal).
		m.put("scroll.fail.rally.alone", "&7Aucun autre membre de la guilde n'est en ligne : le parchemin est intact.");
		m.put("scroll.fail.rally.near", "&7Les membres en ligne ({0}) sont déjà à moins de {1} blocs de vous : le parchemin est intact.");
		m.put("scroll.fail.rally.unavailable", "&7Les membres en ligne ({0}) dorment, sont morts ou spectateurs : le parchemin est intact.");
		m.put("scroll.fail.rally.far", "&7Les membres en ligne ({0}) sont à plus de {1} blocs, la portée du ralliement : le parchemin est intact.");
		m.put("scroll.fail.rally.dimension", "&7Les membres en ligne ({0}) sont dans une autre dimension et le ralliement ne la traverse pas : "
				+ "le parchemin est intact.");
		m.put("scroll.fail.rally.mixed", "&7Aucun membre à appeler ({0}) : le parchemin est intact.");
		m.put("scroll.rally.reason.near", "{0} déjà à moins de {1} blocs de vous");
		m.put("scroll.rally.reason.unavailable", "{0} endormi(s), mort(s) ou spectateur(s)");
		m.put("scroll.rally.reason.far", "{0} à plus de {1} blocs");
		m.put("scroll.rally.reason.dimension", "{0} dans une autre dimension");
		m.put("scroll.rally.reason.separator", ", ");
		m.put("scroll.fail.rally_unsafe", "&cImpossible de sonner le ralliement ici (vide, lave ou feu sous vos pieds).");
		m.put("scroll.recall.pulled", "&b{0}&3 vous ramène à la guilde !");
		m.put("scroll.swap.swapped", "&d{0}&5 a échangé sa place avec vous !");
		m.put("scroll.rally.invite", "&6⚑ &e{0}&6 sonne le ralliement de la guilde &7({1})&6 ! ");
		m.put("scroll.rally.where.distance", "à {0} blocs");
		m.put("scroll.rally.where.dimension", "dans une autre dimension");
		m.put("scroll.rally.join", "&a&l[Rejoindre]");
		m.put("scroll.rally.join.hover", "&7Cliquez pour être téléporté près de &e{0}\n&8Valable {1} s · {2} place(s)");
		m.put("scroll.rally.called", "&6⚑ Ralliement : &e{0}&6 membre(s) appelé(s).");
		m.put("scroll.rally.status", "&6⚑ Ralliement : &e{0}/{1}&6 ont rejoint · &e{2} s");
		m.put("scroll.rally.joined", "&a{0}&2 rejoint le ralliement ! &7({1}/{2})");
		m.put("scroll.rally.arrived", "&6⚑ Vous rejoignez &e{0}&6 !");
		m.put("scroll.rally.fail.not_invited", "&cCet appel au ralliement ne vous est pas adressé.");
		m.put("scroll.rally.fail.closed", "&7Cet appel au ralliement est terminé.");
		m.put("scroll.rally.fail.expired", "&7Cet appel au ralliement a expiré.");
		m.put("scroll.rally.fail.already", "&7Vous avez déjà rejoint ce ralliement.");
		m.put("scroll.rally.fail.full", "&7Le ralliement est complet.");
		m.put("scroll.rally.fail.dimension", "&cVous n'êtes plus dans la dimension du ralliement.");
		m.put("scroll.rally.fail.unavailable", "&cImpossible de rejoindre le ralliement maintenant.");
		m.put("scroll.rally.fail.unsafe", "&cLe point de ralliement n'est plus sûr : impossible de vous y téléporter.");
		m.put("scroll.rally.fail.teleport", "&cLe voyage jusqu'au ralliement a échoué : cliquez à nouveau sur [Rejoindre].");
		m.put("scroll.revelation.count", "&b{0} &3monstre(s) révélé(s).");
		m.put("scroll.guardian.name", "&7Gardien de {0}");
		// rewards
		m.put("scroll.reward.quest", "&d&l✦ &e{0}&d reçoit un parchemin : {1}&d !");
		// admin
		m.put("scroll.admin.list.header", "&6&lParchemins de sort &7({0}) &8- cliquez sur un nom pour le donner");
		m.put("scroll.admin.list.entry", "&8- {0} &8[&7{1}&8] {2} &8· &7{3}");
		m.put("scroll.admin.list.hover", "&7Sort : &e{0}\n&7Incantation {1} s, recharge {2} s ({3})\n&7Butin : {4}\n"
				+ "&eCliquez pour préparer la commande give");
		m.put("scroll.admin.list.no_drop", "&8jamais");
		m.put("scroll.admin.list.tables", "&7Chances par source : {0}");
		m.put("scroll.admin.list.disabled", "&c(parchemins désactivés : enabled = false dans scrolls.json5)");
		m.put("scroll.admin.list.drops_disabled", "&c(butin désactivé : dropsEnabled = false dans scrolls.json5)");
		m.put("scroll.admin.list.effects", "&7Sorts actifs en ce moment : {0}");
		m.put("scroll.admin.give", "&a{0}&a ×{1} donné(s) à &e{2}&a.");
		m.put("scroll.admin.list.incantation", "&8- {0} &8[&7{1}&8] &7{2}");
		m.put("scroll.admin.list.incantation.kind", "rituel des ruines : réveille leur gardien");
		m.put("scroll.admin.list.incantation_cursed.kind", "rituel maudit : réveille le gardien sous sa forme maudite");
		m.put("scroll.admin.list.incantation.hover", "&7Lu près du gardien endormi d'une ruine dormante,\n&7il lance le rituel qui le réveille.\n"
				+ "&8Aussi : /guild admin ruin incantation <joueur> [nombre]");
		m.put("scroll.admin.list.incantation_cursed.hover", "&7Comme le parchemin d'incantation, mais le gardien\n&7s'éveille &5maudit&7 (3 phases et une forme finale).\n"
				+ "&8Aussi : /guild admin ruin incantation <joueur> [nombre] cursed");
		m.put("scroll.admin.unknown", "&cParchemin inconnu : &e{0}&c (voir /guild admin scroll list).");
		Lang.addDefaults(m);
	}
}
