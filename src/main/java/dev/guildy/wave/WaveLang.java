package dev.guildy.wave;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the wave module (keys prefixed with "wave.").
 */
final class WaveLang {
	private WaveLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// start
		m.put("wave.start.title", "&4&lLa horde approche !");
		m.put("wave.start.subtitle", "{0}");
		m.put("wave.start.chat", "&c☠ &lLa horde approche ! &r&7Thème : {0}&7 - difficulté &f{1}&7 - &f{2}&7 monstres.");
		m.put("wave.start.goal", "&7Défendez le &ccœur de la base&7 jusqu'à l'aube !");
		m.put("wave.start.goal.event", "&7Défendez le &ccœur de la base&7 jusqu'à ce que l'assaut soit repoussé !");
		m.put("wave.start.modifiers", "{0}&7 : la horde est renforcée, mais les récompenses sont multipliées par &6{1}&7 !");
		m.put("wave.start.boss", "&4Un boss accompagne la horde cette nuit !");
		m.put("wave.start.test", "&e[Test] &7Vague de test lancée par un administrateur.");
		// boss bars
		m.put("wave.bar.progress", "{0} &7- &f{1}&7/&f{2} &7tués &8(&7{3} restants&8)");
		m.put("wave.bar.label", "{0} &8» {1}");
		m.put("wave.bar.heart", "&cCœur de la base &7- &f{0}&7/&f{1}");
		m.put("wave.bar.boss", "{0} &7- &f{1}&7/&f{2} ❤");
		// heart
		m.put("wave.heart.alert", "&c&lLe cœur de la base est attaqué ! &r&7(intégrité &f{0}%&7)");
		// boss
		m.put("wave.boss.spawn.title", "&4&lUn boss arrive !");
		m.put("wave.boss.spawn.subtitle", "{0}");
		m.put("wave.boss.spawn.chat", "&4☠ {0}&c rejoint la horde !");
		m.put("wave.boss.killed", "&a&l✔ {0}&a a été vaincu !");
		// elites
		m.put("wave.elite.prefix", "&6&lÉlite ");
		// absence
		m.put("wave.absent.warning", "&eAucun défenseur près du cœur : la horde se dispersera dans &f{0}&e s, sans récompense.");
		// end
		m.put("wave.end.victory.title", "&a&lVictoire !");
		m.put("wave.end.victory.subtitle", "&7La horde a été repoussée");
		m.put("wave.end.defeat.title", "&4&lDéfaite...");
		m.put("wave.end.defeat.subtitle", "&cLe cœur de la base est tombé");
		m.put("wave.end.cancelled.title", "&7Horde dispersée");
		m.put("wave.end.cancelled.subtitle", "&8{0}");
		m.put("wave.recap.header", "&8&m          &r &6Bilan de la horde &8&m          ");
		m.put("wave.recap.theme", "&7Thème : {0} &8| &7Difficulté : &f{1} &8| &7Palier : &f{2}");
		m.put("wave.recap.kills", "&7Monstres tués : &f{0}&7/&f{1} &8| &7Élites : &f{2} &8| &7Boss : {3}");
		m.put("wave.recap.boss.none", "&8aucun");
		m.put("wave.recap.boss.killed", "&avaincu");
		m.put("wave.recap.boss.alive", "&csurvivant");
		m.put("wave.recap.victory", "&a✔ Victoire ! &7Trésor : &6+{0} or&7, XP de guilde : &b+{1}");
		m.put("wave.recap.items", "&7Butin : &f{0}&7 tirage(s) d'objets pour chaque défenseur présent.");
		m.put("wave.recap.next", "&7Nouveau palier de nuit : &f{0}&7 (série de victoires : &f{1}&7)");
		m.put("wave.recap.event", "&7Assaut repoussé : le palier de nuit ne change pas (série de victoires : &f{0}&7)");
		m.put("wave.recap.modifiers", "{0}&7 : récompenses multipliées par &6{1}");
		m.put("wave.recap.defeat", "&4✘ Défaite... &7Pertes : &c-{0} or&7 du trésor (&c{1}%&7).");
		m.put("wave.recap.rollback", "&7La difficulté recule : palier de nuit &f{0}&7 → &f{1}&7.");
		m.put("wave.recap.cancelled", "&7Horde annulée (&f{0}&7) : aucune récompense ni pénalité.");
		m.put("wave.reward.received", "&aVous recevez &f{0}&a objet(s) pour avoir défendu la base !");
		// cancel reasons
		m.put("wave.reason.skipped", "nuit écourtée");
		m.put("wave.reason.absent", "base désertée");
		m.put("wave.reason.territory", "territoire abandonné");
		m.put("wave.reason.disbanded", "guilde dissoute");
		m.put("wave.reason.server", "arrêt du serveur");
		m.put("wave.reason.admin", "annulée par un administrateur");
		m.put("wave.reason.peaceful", "difficulté paisible");
		m.put("wave.reason.frozen", "temps figé");
		m.put("wave.reason.test", "vague de test");
		m.put("wave.reason.empty", "aucun monstre n'a pu apparaître");
		m.put("wave.reason.error", "erreur interne");
		// results (stored in guild.waves.lastResult)
		m.put("wave.result.victory", "&aVictoire");
		m.put("wave.result.defeat", "&cDéfaite");
		m.put("wave.result.cancelled", "&7Annulée");
		// sleep
		m.put("wave.sleep.denied", "&cImpossible de dormir : une horde attaque la base !");
		// status lore (main menu)
		m.put("wave.lore.active", "&c⚔ Horde en cours : {0}");
		m.put("wave.lore.progress", "&7Tués : &f{0}&7/&f{1} &8| &7Cœur : &f{2}%");
		m.put("wave.lore.idle", "&7Prochaine horde : difficulté &f{0}");
		m.put("wave.lore.expected", "&7Monstres attendus : &f~{0}");
		m.put("wave.lore.stats", "&7Victoires : &a{0} &8| &7Défaites : &c{1}");
		m.put("wave.lore.truce", "&a☮ Trêve : encore &f{0}&a nuit(s) sans horde");
		m.put("wave.truce.started", "&a☮ Trêve ! Après &f{0}&a défaites d'affilée, aucune horde n'attaquera votre base pendant &f{1}&a nuit(s). Profitez-en pour vous renforcer.");
		m.put("wave.truce.night", "&a☮ Trêve : pas de horde cette nuit (encore &f{0}&a nuit(s) de trêve ensuite).");
		m.put("wave.truce.last", "&a☮ Trêve : pas de horde cette nuit. &7C'est la dernière nuit de trêve.");
		m.put("wave.lore.notfounded", "&8Plantez le drapeau fondateur pour affronter les hordes.");
		m.put("wave.lore.disabled", "&8Les hordes nocturnes sont désactivées.");
		// GUI
		m.put("wave.gui.title", "&4Hordes nocturnes");
		m.put("wave.gui.current.name", "&c&lHorde en cours");
		m.put("wave.gui.current.lore", "&7Thème : {0}\n&7Difficulté : &f{1}\n&7Tués : &f{2}&7/&f{3} &8(&7{4} restants&8)\n"
				+ "&7Élites tuées : &f{5}\n&7Cœur : &f{6}&7/&f{7}\n&7Temps restant : &f{8}");
		m.put("wave.gui.current.boss.pending", "&7Boss : &eà venir");
		m.put("wave.gui.current.boss.alive", "&7Boss : {0} &7(&f{1}&7/&f{2} ❤&7)");
		m.put("wave.gui.current.boss.killed", "&7Boss : &avaincu");
		m.put("wave.gui.current.test", "&e[Vague de test]");
		m.put("wave.gui.idle.name", "&a&lAucune horde en cours");
		m.put("wave.gui.idle.night", "&7La nuit est tombée : la horde attaquera\n&7dès qu'un membre sera près du cœur.");
		m.put("wave.gui.idle.done", "&7La horde de cette nuit est passée.");
		m.put("wave.gui.idle.late", "&7Trop tard pour une horde cette nuit.");
		m.put("wave.gui.idle.day", "&7Prochaine tombée de la nuit dans &f{0}");
		m.put("wave.gui.idle.frozen", "&7Le cycle jour/nuit est arrêté.");
		m.put("wave.gui.idle.notfounded", "&7Plantez le drapeau fondateur pour\n&7que votre base affronte les hordes.");
		m.put("wave.gui.idle.disabled", "&7Les hordes nocturnes sont désactivées.");
		m.put("wave.gui.next.name", "&e&lProchaine nuit");
		m.put("wave.gui.next.lore", "&7Palier de nuit : &f{0}\n&7Difficulté estimée : &f{1}\n&7Monstres attendus : &f~{2}\n"
				+ "&7Chance d'élite : &f{3}%\n&7Intégrité du cœur : &f{4}");
		m.put("wave.gui.next.players", "&8Estimation pour {0} défenseur(s), +{1}\n&8de difficulté par défenseur supplémentaire.");
		m.put("wave.gui.next.boss", "&4☠ Nuit de boss !");
		m.put("wave.gui.next.bossin", "&7Prochaine nuit de boss dans &f{0}&7 palier(s)");
		m.put("wave.gui.next.themes", "&7Thèmes possibles :");
		m.put("wave.gui.next.theme", "&8 • {0}");
		m.put("wave.gui.stats.name", "&b&lStatistiques");
		m.put("wave.gui.stats.lore", "&7Palier de nuit : &f{0}\n&7Victoires : &a{1}\n&7Défaites : &c{2}\n&7Série actuelle : &f{3}\n"
				+ "&7Meilleure série : &f{4}\n&7Monstres tués : &f{5}\n&7Boss vaincus : &f{6}");
		m.put("wave.gui.last.name", "&6&lDernière horde");
		m.put("wave.gui.last.none", "&8Aucune horde pour l'instant.");
		m.put("wave.gui.last.lore", "&7Résultat : {0}\n&7Thème : {1}\n&7Tués : &f{2}&7/&f{3}\n&7Il y a &f{4}");
		m.put("wave.gui.last.gain", "&7Gains : &6+{0} or &8| &b+{1} XP");
		m.put("wave.gui.last.loss", "&7Pertes : &c-{0} or");
		m.put("wave.gui.rules.name", "&f&lRègles des hordes");
		m.put("wave.gui.rules.lore", "&7Chaque nuit, si un membre est à moins de &f{0}\n"
				+ "&7blocs du cœur, une horde attaque la base. Les\n"
				+ "&7monstres surgissent en bordure du territoire\n"
				+ "&7et marchent vers le &ccœur&7 pour l'abîmer.\n"
				+ " \n"
				+ "&a✔ Victoire : &7le cœur tient jusqu'à l'aube (ou\n"
				+ "&7toute la horde est tuée). Or au trésor, XP de\n"
				+ "&7guilde et objets pour les défenseurs présents,\n"
				+ "&7selon la part de monstres tués. Palier +1.\n"
				+ " \n"
				+ "&4✘ Défaite : &7l'intégrité du cœur tombe à 0.\n"
				+ "&7Perte de &c{1}%&7 du trésor, des villageois peuvent\n"
				+ "&7partir, palier de nuit &c-{2}&7 (difficulté réduite).\n"
				+ "&7Votre équipement n'est jamais touché.\n"
				+ " \n"
				+ "&7Dormir est impossible pendant une horde. Nuit\n"
				+ "&7sautée ou base désertée : horde annulée, sans\n"
				+ "&7récompense ni pénalité.");
		m.put("wave.gui.themes.name", "&d&lThèmes de horde");
		m.put("wave.gui.themes.unlocked", "&a✔ {0} &8(palier {1}+)");
		m.put("wave.gui.themes.locked", "&8✘ {0} &8(palier {1}+)");
		m.put("wave.time.minutes", "{0} min {1} s");
		m.put("wave.time.seconds", "{0} s");
		m.put("wave.time.hours", "{0} h {1} min");
		m.put("wave.time.days", "{0} j {1} h");
		// commands
		m.put("wave.cmd.started", "&aHorde de test lancée pour &e{0}&a : {1}&a, {2} monstres.");
		m.put("wave.cmd.already", "&cUne horde est déjà en cours pour &e{0}&c.");
		m.put("wave.cmd.notfounded", "&c&e{0}&c n'a pas encore de cœur de base.");
		m.put("wave.cmd.startfailed", "&cImpossible de lancer la horde (aucun thème ou monstre valide).");
		m.put("wave.cmd.stopped", "&aHorde de &e{0}&a annulée.");
		m.put("wave.cmd.none", "&c&e{0}&c n'a aucune horde en cours.");
		m.put("wave.cmd.night", "&aPalier de nuit de &e{0}&a réglé sur &f{1}&a.");
		m.put("wave.cmd.usage.setnight", "&cUsage : /guild admin wave setnight <guilde> <palier>");
		m.put("wave.cmd.truce", "&aTrêve de &e{0}&a : &f{1}&a nuit(s) sans horde.");
		m.put("wave.cmd.usage.truce", "&cUsage : /guild admin wave truce <guilde> <nuits> (0 = fin de la trêve)");
		m.put("wave.cmd.status.active", "&e{0}&7 : horde {1}&7 - {2}/{3} tués, {4} vivants, {5} à venir, cœur {6}/{7}, D={8}{9}");
		m.put("wave.cmd.status.idle", "&e{0}&7 : aucune horde - palier {1}, difficulté estimée {2}, dernière horde jour {3} (jour actuel {4})");
		m.put("wave.cmd.disabled", "&cLes hordes sont désactivées (waves.json5).");
		Lang.addDefaults(m);
	}
}
