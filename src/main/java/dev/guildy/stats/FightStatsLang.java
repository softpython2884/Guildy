package dev.guildy.stats;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/** French texts of the fight summaries (lang.json, '&' colour codes). */
final class FightStatsLang {
	private FightStatsLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// Chat summary
		m.put("fight.report.header", "&6&l⚔ Bilan du combat &8— {0}");
		m.put("fight.report.header.failure", "&c&l⚔ Bilan du combat &7(échec) &8— {0}");
		m.put("fight.report.info", "&7  {0} &8· &7{1} &8· &7durée &f{2} &8· &f{3}&7 participant(s)");
		m.put("fight.report.cursed", "{0} &5☠ Maudit");
		m.put("fight.report.risen", "{0} &c— Ressuscité");
		m.put("fight.report.failure.health", "&7  Le gardien avait encore &c{0} %&7 de sa vie.");
		m.put("fight.report.kills", "&7  Gardiens de salle abattus : &f{0}&7/&f{1} &8· &7sbires tués : &f{2}");
		m.put("fight.report.title", "&7  {0} &7: ");
		m.put("fight.report.name", "&f{0}");
		m.put("fight.report.and", "&7, ");
		m.put("fight.report.more", "&7 +{0}");
		m.put("fight.report.you", "&e  ➤ Vous &7(#{0}/{1}) : &c{2}&7 au gardien &8· &c{3}&7 aux gardes et sbires &8· &a{4}&7 soins/absorption &8· &c{5}&7 encaissés");
		m.put("fight.report.you.none", "&7  ➤ Vous n'apparaissez pas dans ce bilan (moins de {0} points infligés, soignés ou encaissés).");
		m.put("fight.report.indent", "&7  ");
		m.put("fight.report.detail", "&b&n[Voir le détail]");
		m.put("fight.report.detail.hover", "&7Une tête par participant, avec toutes ses statistiques.\n&8/guild bilan : revoir ce bilan");
		// Titles
		m.put("fight.title.guardian", "&6✦ Fléau du gardien");
		m.put("fight.title.guards", "&6✦ Briseur de gardes");
		m.put("fight.title.escort", "&6✦ Chasseur de sbires");
		m.put("fight.title.healing", "&a✚ Ange gardien");
		m.put("fight.title.shield", "&e⛨ Protecteur");
		m.put("fight.title.tank", "&9⛨ Rempart");
		m.put("fight.title.final_blow", "&c☠ Coup fatal");
		m.put("fight.title.kills", "&6✦ Moissonneur");
		m.put("fight.title.spells", "&d✧ Arcaniste");
		m.put("fight.value.guardian", "&8 — &c{0}&7 dégâts");
		m.put("fight.value.guards", "&8 — &c{0}&7 dégâts");
		m.put("fight.value.escort", "&8 — &c{0}&7 dégâts");
		m.put("fight.value.healing", "&8 — &a{0}&7 PV soignés");
		m.put("fight.value.shield", "&8 — &e{0}&7 PV d'absorption donnés");
		m.put("fight.value.tank", "&8 — &c{0}&7 dégâts encaissés");
		m.put("fight.value.final_blow", "&8 — &7a achevé {0}");
		m.put("fight.value.kills", "&8 — &f{0}&7 monstres tués");
		m.put("fight.value.spells", "&8 — &d{0}&7 sorts lancés");
		// Statistics of one player (hover text and menu lore)
		m.put("fight.stat.header", "&e&l{0}");
		m.put("fight.stat.boss", "&7Dégâts au gardien : &c{0}");
		m.put("fight.stat.guards", "&7Dégâts aux gardiens de salle : &c{0} &8({1} abattu(s))");
		m.put("fight.stat.escort", "&7Dégâts aux sbires : &c{0} &8({1} tué(s))");
		m.put("fight.stat.final_blow", "&c☠ Coup fatal sur le gardien");
		m.put("fight.stat.taken", "&7Dégâts encaissés : &c{0} &8· &7morts : &f{1}");
		m.put("fight.stat.healing", "&7Soins donnés : &a{0} &8· &7absorption donnée : &e{1}");
		m.put("fight.stat.misc", "&7Sorts lancés : &d{0} &8· &7temps dans l'arène : &f{1}");
		// Menu
		m.put("fight.menu.title", "Bilan — {0}");
		m.put("fight.menu.header", "&6&lVictoire &8— {0}");
		m.put("fight.menu.header.failure", "&c&lÉchec &8— {0}");
		m.put("fight.menu.boss", "&7Gardien : {0}");
		m.put("fight.menu.threat", "&7Menace : {0}");
		m.put("fight.menu.duration", "&7Durée du combat : &f{0} &8(gardiens de salle compris : {1})");
		m.put("fight.menu.kills", "&7Gardiens de salle abattus : &f{0}&7/&f{1} &8· &7sbires tués : &f{2}");
		m.put("fight.menu.players", "&7Participants : &f{0}");
		m.put("fight.menu.more", "&8… et {0} autre(s), non affiché(s)");
		m.put("fight.menu.head", "&e#{0} &f{1}");
		m.put("fight.menu.head.self", "&e#{0} &a{1} &7(vous)");
		m.put("fight.menu.title.held", "{0}");
		m.put("fight.menu.empty", "&7Personne n'a assez participé pour figurer ici.");
		// Command
		m.put("fight.none", "&7Aucun bilan de combat récent (chacun reste consultable {0} min après un combat de ruine).");
		m.put("fight.disabled", "&cLe bilan des combats est désactivé sur ce serveur.");
		Lang.addDefaults(m);
	}
}
