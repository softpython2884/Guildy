package dev.guildy.ruin;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French texts of the ruins (lang.json, '&' colour codes).
 */
final class RuinLang {
	private RuinLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// Villager task (cartographer)
		m.put("villager.task.cartographer", "Cartographe");
		m.put("villager.task.cartographer.desc", "&7Travaille à l'&eAtelier du cartographe&7 pour\n&7localiser les &6ruines&7 gardées par des boss\n&7(recherches lancées depuis le panneau de\n&7l'atelier). Plusieurs cartographes vont plus vite.");
		m.put("villager.status.cartographer.no_atelier", "Attend un Atelier du cartographe");
		m.put("villager.status.cartographer.waiting", "Attend une recherche à l'atelier");
		m.put("villager.status.cartographer.walking", "Se rend à l'atelier");
		m.put("villager.status.cartographer.working", "Étudie les cartes ({0} %)");

		// States
		m.put("ruin.state.dormant", "dormante");
		m.put("ruin.state.awake", "réveillée");
		m.put("ruin.state.cleared", "vaincue");
		m.put("ruin.dimension.overworld", "Surface");
		m.put("ruin.dimension.nether", "Nether");

		// Fights
		m.put("ruin.awaken.subtitle", "&c{0} &7s'éveille…");
		m.put("ruin.awaken.chat", "&c{0} &7s'éveille (&f{1}&7) ! Tenez bon.");
		m.put("ruin.bar", "{0} &7— &c{1}&7/&c{2} ❤");
		m.put("ruin.enrage", "&4&l{0} entre dans une rage folle !");
		m.put("ruin.reset", "&7{0} &7retourne dans l'ombre : la ruine se rendort.");
		m.put("ruin.cleared.hint", "&7{0} : gardien vaincu, de retour dans &e{1}&7 jour(s).");
		m.put("ruin.cleared.never", "&7{0} : gardien vaincu, la ruine est apaisée.");
		m.put("ruin.victory.title", "&6&lVictoire !");
		m.put("ruin.victory.subtitle", "&e{0} &7est vaincu");
		m.put("ruin.victory.someone", "Des aventuriers");
		m.put("ruin.victory.announce", "&c{1}&7 est tombé (&f{2}&7) ! Vainqueurs : &e{0}");
		m.put("ruin.victory.xp", "&a+{0} XP de guilde &7pour la victoire dans la &f{1}&7.");

		// Threat ("Menace") and resurrection
		m.put("ruin.threat", "{1}Menace {0}");
		m.put("ruin.bar.name", "{0} &8— {1}");
		m.put("ruin.bar.name.risen", "{0} &4&lEnragé &8— {1}");
		m.put("ruin.bar.chrono", "{0} &8| &eRésurrection possible : &f{1}");
		m.put("ruin.bar.rising", "&4&l☠ &c{0} &crevient d'entre les morts… &8— {1} &4&l☠");
		m.put("ruin.awaken.threat.chrono", "&8» {0}&7 : abattu en moins de &e{1}&7, il se relèvera, fou de rage…");
		m.put("ruin.risen.name", "{0} &4&lEnragé");
		m.put("ruin.risen.title", "&4&l☠ Il se relève ! ☠");
		m.put("ruin.risen.subtitle", "&c{0} &crefuse de mourir…");
		m.put("ruin.risen.chat", "&4☠ &cVous l'avez abattu trop vite… &4&lil revient ! &7({0}&7 est tombé en &e{1}&7, il fallait tenir &e{2}&7)");
		m.put("ruin.risen.back.title", "&4&lENRAGÉ !");
		m.put("ruin.risen.back.subtitle", "{0} &cest de retour, fou de rage");
		m.put("ruin.risen.back.chat", "&4&l☠ {0} &cest revenu d'entre les morts : plus grand, plus rapide, plus fort. &6Achevez-le pour un butin exceptionnel !");
		m.put("ruin.risen.victory.title", "&6&lVictoire légendaire !");
		m.put("ruin.risen.victory.subtitle", "&e{0} &7ne se relèvera plus");
		m.put("ruin.risen.victory.announce", "&6&l☠ &e{0} &7ont terrassé {1} &4&lressuscité &8(&f{2}&8) &7: &6butin exceptionnel !");

		// The jaguar's spirits
		m.put("ruin.jaguar.spirit.name", "&eEsprit jaguar");
		m.put("ruin.jaguar.spirit.cursed", "&5Esprit d'obsidienne");

		// Incantation scroll and ritual
		m.put("ruin.summon.item.name", "&4&lParchemin d'incantation");
		m.put("ruin.summon.item.header", "&5Objet rituel &8— usage unique");
		m.put("ruin.summon.item.story", "&7Un rouleau taché de sang séché. Lu au cœur\n&7d'une &fruine&7 dont le gardien dort, il l'arrache\n&7au sommeil avec toute son escorte.");
		m.put("ruin.summon.item.price", "&4Prix du sang : &c{0} ❤ &4par joueur présent");
		m.put("ruin.summon.item.price.min", "&8(jamais en dessous de {0} ❤)");
		m.put("ruin.summon.item.price.none", "&7Aucun prix du sang sur ce serveur.");
		m.put("ruin.summon.item.use", "&7Clic droit maintenu &e{0} s&7, à moins de &e{1}&7 blocs");
		m.put("ruin.summon.item.ritual", "&7du gardien endormi : le rituel dure &e{0} s&7.");
		m.put("ruin.summon.item.dormant", "&8Ne fonctionne que dans une ruine dormante.");
		m.put("ruin.summon.item.single_use", "&8Consommé au début du rituel, rendu s'il échoue.");
		m.put("ruin.summon.fail.not_in_ruin", "&cCe parchemin n'agit que dans une ruine gardée par un boss.");
		m.put("ruin.summon.fail.wrong_dimension", "&cCette ruine n'appartient pas à cette dimension.");
		m.put("ruin.summon.fail.disabled", "&cLes gardiens des ruines sont désactivés sur ce serveur.");
		m.put("ruin.summon.fail.peaceful", "&cLes gardiens restent sourds à l'appel en difficulté Paisible.");
		m.put("ruin.summon.fail.cleared", "&c{0}&c : gardien vaincu, de retour dans &e{1}&c jour(s).");
		m.put("ruin.summon.fail.cleared_forever", "&c{0}&c : le gardien est vaincu pour toujours, la ruine est apaisée.");
		m.put("ruin.summon.fail.awake", "&c{2} &cest déjà éveillé !");
		m.put("ruin.summon.fail.ritual", "&cUn rituel est déjà en cours dans cette ruine.");
		m.put("ruin.summon.fail.too_far", "&cApprochez-vous du gardien endormi : à moins de &e{3}&c blocs de son repaire.");
		m.put("ruin.summon.fail.busy", "&cTrop de gardiens sont déjà éveillés sur le serveur : réessayez plus tard.");
		m.put("ruin.summon.fail.not_loaded", "&cLa ruine n'est pas entièrement chargée : approchez-vous.");
		m.put("ruin.summon.interrupted", "&7L'incantation a été interrompue : le parchemin est intact.");
		m.put("ruin.summon.bar", "&4Rituel d'incantation &7— {0}");
		m.put("ruin.summon.start.title", "&4&lRituel d'incantation");
		m.put("ruin.summon.start.subtitle", "&7Votre sang éveille {0}&7…");
		m.put("ruin.summon.start.chat", "&5{0} &7lit le parchemin d'incantation : le sang des présents va éveiller {1}&7 dans la &f{2}&7 ! Restez près du cercle.");
		m.put("ruin.summon.drain", "&4❤ &cLe rituel boit votre sang… &7restez près du cercle !");
		m.put("ruin.summon.drain.none", "&5Le rituel puise dans votre énergie… &7restez près du cercle !");
		m.put("ruin.summon.cancelled", "&7Le rituel de la &f{0}&7 se brise : plus personne ne le nourrit. &8(parchemin rendu à {1})");
		m.put("ruin.summon.refunded", "&7Le parchemin d'incantation vous a été rendu.");
		m.put("ruin.summon.failed", "&c{0} &cn'a pas répondu à l'appel : le parchemin est rendu.");
		m.put("ruin.summon.hint.subtitle", "&7Une présence dort ici…");
		m.put("ruin.summon.hint.actionbar", "&5Un parchemin d'incantation &7pourrait l'éveiller.");
		m.put("ruin.summon.hint.chat", "&7Une présence dort dans la &f{0}&7 : {1}&7. Un &5parchemin d'incantation&7, lu au cœur de la ruine, pourrait l'éveiller.");
		m.put("ruin.summon.start.threat", "&8» &7Menace de la ruine : {0}");
		m.put("ruin.summon.fail.guarded", "&5Une présence scelle encore l'entité…");
		m.put("ruin.summon.sealed", "&5Une présence scelle encore l'entité… &7le cercle se brise.");
		m.put("ruin.summon.sealed.cursed", "&5Une présence scelle encore l'entité… &7le pentagramme maudit se brise.");

		// Room guards
		m.put("ruin.guards.posted.screen", "&f{0}&7 : des sentinelles prennent leur poste…");
		m.put("ruin.guards.watch", "&7Des &csentinelles&7 veillent sur ce lieu… tant qu'elles se dressent, le sommeil du gardien ne peut être troublé.");
		m.put("ruin.guards.sealing", "&7Des sentinelles veillent encore… &8tant qu'elles se dressent, le gardien ne s'éveillera pas.");
		m.put("ruin.known.state.watched", "&7État : &adormante &8— &7des sentinelles veillent");

		// Group scaling
		m.put("ruin.bar.group", "{0} &8— &e{1} joueurs");
		m.put("ruin.awaken.group", "&8» &7Groupe de &e{0}&7 joueurs : gardien renforcé (&c+{1} %&7 de vie, &c+{2} %&7 de dégâts), butin plus riche et or partagé.");

		// Beacon sanctuaries
		m.put("ruin.sanctuary.protected", "&6✦ &eUne force ancienne protège ce sanctuaire.");

		// Cartography
		m.put("ruin.research.no_atelier", "&cLa guilde n'a pas d'Atelier du cartographe.");
		m.put("ruin.research.busy", "&cUne recherche est déjà en cours (ou attend d'être récupérée).");
		m.put("ruin.research.creative", "&cImpossible de payer une recherche en mode créatif.");
		m.put("ruin.research.missing", "&cIl vous manque des objets : &e{0} × {1}&c.");
		m.put("ruin.research.started", "&a{0} a lancé la recherche d'une ruine &f(surface)&a à l'Atelier du cartographe.");
		m.put("ruin.research.started.nether", "&a{0} a lancé la recherche d'une ruine &c(Nether)&a à l'Atelier du cartographe.");
		m.put("ruin.research.no_cartographer", "&eAucun villageois n'a la tâche Cartographe : la recherche n'avancera pas sans lui.");
		m.put("ruin.research.none", "&cAucune recherche en cours.");
		m.put("ruin.research.cancelled", "&7{0} a annulé la recherche : le coût lui est rendu.");
		m.put("ruin.research.not_ready", "&cLa recherche n'est pas terminée.");
		m.put("ruin.research.refunded", "&7Aucune ruine trouvée : le coût de la recherche vous est rendu.");
		m.put("ruin.research.lost", "&cLa ruine trouvée n'est plus connue : récupérez le remboursement.");
		m.put("ruin.research.claimed", "&a{0} a récupéré la carte de la &f{1}&a (&e{2}, {3}&a).");
		m.put("ruin.research.claimed.nether", "&a{0} a récupéré la boussole de la &f{1}&a (Nether : &e{2}, {3}&a).");
		m.put("ruin.research.failed", "&eLes cartographes n'ont trouvé aucune ruine : récupérez le remboursement à l'atelier.");
		m.put("ruin.research.ready", "&6&lCarte prête ! &eUne ruine a été localisée : récupérez la carte à l'Atelier du cartographe.");
		m.put("ruin.research.ready.nether", "&6&lBoussole prête ! &eUne ruine du Nether a été localisée : récupérez-la à l'Atelier du cartographe.");
		m.put("ruin.research.incantation", "&5+{0} parchemin(s) d'incantation &7: lisez-le au cœur de la ruine pour éveiller son gardien.");

		// Items
		m.put("ruin.item.map.name", "&6Carte : {0}");
		m.put("ruin.item.compass.name", "&cBoussole : {0}");
		m.put("ruin.item.lore.overworld", "&7Ruine de la &fsurface");
		m.put("ruin.item.lore.nether", "&7Ruine du &cNether");
		m.put("ruin.item.lore.coords", "&7Coordonnées : &e{0}, {1}, {2}");
		m.put("ruin.item.lore.tier", "&7Palier : &e{0}&7/5");
		m.put("ruin.item.lore.threat", "&7Niveau de menace : {0}");
		m.put("ruin.item.lore.boss", "&7Gardien : {0}");
		m.put("ruin.item.lore.hint", "&8La croix marque l'entrée de la ruine.");
		m.put("ruin.item.lore.hint.nether", "&8L'aiguille indique la ruine (dans le Nether).");

		// Atelier sign
		m.put("ruin.sign.title", "&lCartographe");
		m.put("ruin.sign.cartographers", "{0} cartographe(s)");
		m.put("ruin.sign.idle", "Au repos");
		m.put("ruin.sign.progress", "Recherche {0} %");
		m.put("ruin.sign.ready", "Carte prête !");
		m.put("ruin.sign.ready.nether", "Boussole prête !");
		m.put("ruin.sign.ready.legend", "Statues trouvées !");
		m.put("ruin.sign.failed", "Rien trouvé");

		// The legend of the celestial jaguar: research, statue map, statues
		m.put("ruin.menu.research.legend", "&2La légende du Jaguar céleste");
		m.put("ruin.menu.research.legend.lore", "&7Les anciens racontent qu'une lignée de\n&7jaguars de pierre veille sur le chemin du\n&6Grand Temple du Jaguar céleste&7. Aucune\n&7carte ne montre le temple : vos cartographes\n&7retrouvent la piste des statues.\n&7Résultat : une &ecarte des statues&7 ; chaque\n&7statue montre la suivante.");
		m.put("ruin.menu.research.legend.level.ok", "&a ✔ Guilde niveau {0}");
		m.put("ruin.menu.research.legend.level.missing", "&c ✘ Guilde niveau {0} &7(votre guilde : {1})");
		m.put("ruin.menu.research.legend.locked", "&cRéservée aux guildes plus expérimentées");
		m.put("ruin.menu.progress.legend", "&7Cible : &2la légende du Jaguar céleste");
		m.put("ruin.menu.claim.legend.lore", "&7La piste des jaguars de pierre est retrouvée !\n&eClic : recevoir la carte des statues.");
		m.put("ruin.research.legend.disabled", "&cLa légende du Jaguar céleste est désactivée sur ce serveur.");
		m.put("ruin.research.legend.level", "&cLa légende du Jaguar céleste ne se confie qu'aux guildes de niveau &e{0}&c (votre guilde : &e{1}&c).");
		m.put("ruin.research.started.legend", "&a{0} a lancé la recherche de &2la légende du Jaguar céleste&a à l'Atelier du cartographe.");
		m.put("ruin.research.ready.legend", "&6&lPiste retrouvée ! &eLes cartographes ont retrouvé les jaguars de pierre : récupérez la carte des statues à l'Atelier du cartographe.");
		m.put("ruin.research.failed.legend", "&eLes cartographes n'ont trouvé aucune trace du Jaguar céleste : récupérez le remboursement à l'atelier.");
		m.put("ruin.research.claimed.legend", "&a{0} a récupéré la &2carte des statues&a : &e{1}&a jaguar(s) de pierre jalonnent le chemin du Grand Temple du Jaguar céleste.");
		m.put("ruin.item.statue_map.name", "&2Carte des statues du Jaguar céleste");
		m.put("ruin.item.statue_map.temple", "Grand Temple du Jaguar céleste");
		m.put("ruin.item.statue_map.lore.1", "&7Des jaguars de pierre jalonnent le chemin");
		m.put("ruin.item.statue_map.lore.2", "&7du &6{0}&7.");
		m.put("ruin.item.statue_map.lore.count", "&7Statues : &e{0}");
		m.put("ruin.item.statue_map.lore.first", "&7Première statue : à ~&e{0}&7 blocs vers le &e{1}");
		m.put("ruin.item.statue_map.lore.hint.1", "&8Approchez-vous d'une statue : son regard");
		m.put("ruin.item.statue_map.lore.hint.2", "&8montre la suite du chemin. Aucune carte");
		m.put("ruin.item.statue_map.lore.hint.3", "&8ne montre le temple : suivez la lumière.");
		m.put("ruin.statue.hint", "&7Le regard du jaguar de pierre se tourne vers le &e{0}&7… le chemin continue.");
		m.put("ruin.statue.hint.last", "&6Au loin, la lumière du Jaguar céleste perce la canopée.");
		m.put("ruin.statue.actionbar", "&2Le jaguar de pierre regarde vers le &e{0}");
		m.put("ruin.statue.actionbar.last", "&6Le Jaguar céleste vous attend, vers le &e{0}");
		m.put("ruin.direction.n", "nord");
		m.put("ruin.direction.ne", "nord-est");
		m.put("ruin.direction.e", "est");
		m.put("ruin.direction.se", "sud-est");
		m.put("ruin.direction.s", "sud");
		m.put("ruin.direction.sw", "sud-ouest");
		m.put("ruin.direction.w", "ouest");
		m.put("ruin.direction.nw", "nord-ouest");
		m.put("ruin.admin.statues.given", "&aCarte des statues ({0} statue(s), chemin #{1}) donnée à {2}.");
		m.put("ruin.admin.statues.none", "&cAucun grand temple trouvé (structure {0}).");
		m.put("ruin.admin.statues.failed", "&cImpossible de tracer un chemin de statues vers ce temple.");
		m.put("ruin.admin.statues.header", "&6Statues du Jaguar : &e{0}&6 en attente, &a{1}&6 posée(s), &c{2}&6 abandonnée(s) &7— {3} chemin(s)");
		m.put("ruin.admin.statues.line", "&7#{0} {1} &f{2} {3} {4} &8→ {5} &7({6} route(s), vue {7} fois)");
		m.put("ruin.admin.statues.state.pending", "&een attente");
		m.put("ruin.admin.statues.state.placed", "&aposée");
		m.put("ruin.admin.statues.state.failed", "&cabandonnée");
		m.put("ruin.admin.statues.next.temple", "temple");
		m.put("ruin.admin.statues.more", "&7… et {0} autre(s).");
		m.put("ruin.admin.statues.hover", "&7Clic : proposer la téléportation");
		m.put("ruin.sign.click", "Clic : ouvrir");
		m.put("ruin.atelier.built.hint", "&7Donnez la tâche &eCartographe&7 à un villageois (menu des villageois) pour lancer des recherches.");

		// Atelier menu
		m.put("ruin.menu.title", "Atelier du cartographe");
		m.put("ruin.menu.info", "&6Atelier du cartographe");
		m.put("ruin.menu.info.lore", "&7Les cartographes de la guilde y localisent\n&7des &6ruines&7 gardées par de puissants boss :\n&7une carte pour la surface, une boussole\n&7pour le Nether.");
		m.put("ruin.menu.info.cartographers", "&7Cartographes : &e{0} &7(au travail : &a{1}&7)");
		m.put("ruin.menu.info.no_cartographer", "&cAucun cartographe : donnez la tâche\n&cCartographe à un villageois.");
		m.put("ruin.menu.research", "&6Chercher une ruine &7(surface)");
		m.put("ruin.menu.research.nether", "&cChercher une ruine &7(Nether)");
		m.put("ruin.menu.research.lore", "&7Localise la ruine de la surface la plus\n&7proche qu'aucune carte ne montre encore.\n&7Résultat : une &ecarte d'explorateur&7.");
		m.put("ruin.menu.research.nether.lore", "&7Localise une ruine du Nether (autel\n&7infernal, citadelle des âmes...).\n&7Résultat : une &eboussole&7 qui pointe\n&7vers elle dans le Nether.");
		m.put("ruin.menu.research.cost", "&7Coût (votre inventaire) :");
		m.put("ruin.menu.research.cost.ok", "&a ✔ {0} × {1}");
		m.put("ruin.menu.research.cost.missing", "&c ✘ {0} × {1}");
		m.put("ruin.menu.research.cost.gold", "&e • {0} or du trésor");
		m.put("ruin.menu.research.duration", "&7Durée : &e{0}&7 de travail d'un cartographe");
		m.put("ruin.menu.research.click", "&eClic : lancer la recherche");
		m.put("ruin.menu.progress", "&eRecherche en cours");
		m.put("ruin.menu.progress.bar", "{0} &f{1} %");
		m.put("ruin.menu.progress.overworld", "&7Cible : ruine de la &fsurface");
		m.put("ruin.menu.progress.nether", "&7Cible : ruine du &cNether");
		m.put("ruin.menu.progress.left", "&7Temps restant : &e{0}");
		m.put("ruin.menu.progress.paused", "&7En pause : aucun cartographe au travail (nuit ?)");
		m.put("ruin.menu.progress.workers", "&7Cartographes au travail : &a{0}");
		m.put("ruin.menu.progress.by", "&8Lancée par {0}");
		m.put("ruin.menu.cancel", "&cAnnuler la recherche");
		m.put("ruin.menu.cancel.lore", "&7Le coût vous est rendu.");
		m.put("ruin.menu.claim", "&6&lRécupérer");
		m.put("ruin.menu.claim.lore", "&7La ruine est localisée !\n&eClic : recevoir la carte / la boussole.");
		m.put("ruin.menu.refund", "&eRécupérer le remboursement");
		m.put("ruin.menu.refund.lore", "&7Aucune ruine trouvée dans les environs.\n&eClic : récupérer le coût de la recherche.");
		m.put("ruin.menu.known", "&bRuines connues &7({0})");
		m.put("ruin.menu.known.lore", "&7Ruines localisées par vos cartographes\n&7ou vaincues par vos membres.");
		m.put("ruin.known.title", "Ruines connues ({0})");
		m.put("ruin.known.state.dormant", "&7État : &adormante");
		m.put("ruin.known.state.awake", "&7État : &créveillée");
		m.put("ruin.known.state.ritual", "&7État : &5rituel d'incantation en cours");
		m.put("ruin.menu.incantation", "&7+ &5{0} parchemin(s) d'incantation &7(pour éveiller le gardien)");
		m.put("ruin.known.state.cleared", "&7État : &8vaincue &7— de retour dans &e{0}&7 jour(s)");
		m.put("ruin.known.state.cleared_forever", "&7État : &8vaincue &7(ne revient pas)");
		m.put("ruin.known.victories", "&7Victoires : &e{0} &8(dernière : {1})");
		m.put("ruin.known.click", "&eClic : coordonnées dans le chat");
		m.put("ruin.known.coords", "&f{0}&7 : &e{1}, {2}, {3} &7({4})");

		// Admin
		m.put("ruin.admin.none", "&cAucune ruine connue dans cette dimension.");
		m.put("ruin.admin.already_awake", "&cLe gardien de cette ruine est déjà réveillé.");
		m.put("ruin.admin.wrong_dimension", "&cPlacez-vous dans la dimension de la ruine.");
		m.put("ruin.admin.no_spawn", "&cAucun emplacement pour le gardien (zone non chargée ?).");
		m.put("ruin.admin.not_loaded", "&cLa ruine n'est pas chargée : approchez-vous.");
		m.put("ruin.admin.spawn_failed", "&cLe gardien n'a pas pu apparaître (voir le journal du serveur).");
		m.put("ruin.admin.awakened", "&a{0} &as'éveille dans la &f{1}&a.");
		m.put("ruin.admin.reset", "&a{0} &a: combat arrêté, gardien réarmé.");
		m.put("ruin.admin.list.header", "&6Ruines connues : &e{0} &7(combats en cours : {1})");
		m.put("ruin.admin.list.line", "&7- &f{0} &8({1}) &7{2} &e{3} {4} {5} &7{6}");
		m.put("ruin.admin.list.more", "&7... et {0} autre(s).");
		m.put("ruin.admin.list.hover", "&7Clic : commande de téléportation");
		m.put("ruin.admin.info.header", "&6{0} &8({1}, {2})");
		m.put("ruin.admin.info.configured", "définie dans ruins.json5");
		m.put("ruin.admin.info.generic", "boss générique");
		m.put("ruin.admin.info.structure", "&7Structure : &f{0} &7({1})");
		m.put("ruin.admin.info.box", "&7Boîte : &f{0} {1} {2} &7→ &f{3} {4} {5}");
		m.put("ruin.admin.info.markers", "&7Boss : &f{0} &7| sbires : &f{1} &7| entrée : &f{2}");
		m.put("ruin.admin.info.state", "{0} &7| victoires : &e{1}");
		m.put("ruin.admin.info.fight", "&cCombat : {0} &c{1}/{2} ❤ &7| sbires : {3} | spectateurs : {4}");
		m.put("ruin.admin.locate.none", "&cAucune ruine '{0}' trouvée.");
		m.put("ruin.admin.map", "&a{0} &adonné à {1}.");
		m.put("ruin.admin.incantation", "&a{0} parchemin(s) d'incantation donné(s) à {1}.");
		m.put("ruin.admin.info.ritual", "&5Rituel d'incantation : {0} % &7| participants : {1}");
		m.put("ruin.admin.info.threat", "&7Menace : {0} &7(enregistrée : {1}, max {2}) &7| résurrection si abattu en moins de &e{3}");
		m.put("ruin.admin.info.resurrection", "&7Résurrection : {0}");
		m.put("ruin.admin.resurrection.window", "&epossible encore {0}");
		m.put("ruin.admin.resurrection.closed", "&7fenêtre fermée");
		m.put("ruin.admin.resurrection.rising", "&4en cours");
		m.put("ruin.admin.resurrection.risen", "&4ressuscité (enragé)");
		m.put("ruin.admin.resurrection.never", "&7jamais");
		m.put("ruin.admin.threat", "&a{0}&a : menace réglée sur {1}&a (appliquée au prochain réveil du gardien).");
		m.put("ruin.admin.info.guards", "&7Gardiens de salle : &f{0}&7 en poste, &f{1}&7 tué(s) &8| &7postes : &f{2} &8({3})");
		m.put("ruin.admin.info.guards.markers", "marqueurs");
		m.put("ruin.admin.info.guards.fallback", "postes de secours");
		m.put("ruin.admin.info.group", "&7Groupe : &e{0}&7 joueur(s) &8(&7vie x{1}, dégâts x{2}&8)");
		m.put("ruin.admin.guards.posted", "&a{0} gardien(s) posté(s) dans la &f{1}&a.");
		m.put("ruin.admin.guards.none", "&cAucun gardien posté (désactivés pour cette ruine, aucun poste libre ou zone non chargée).");
		m.put("ruin.admin.guards.cleared", "&aGardiens de la &f{0}&a retirés : de nouveaux viendront à la prochaine approche.");
		m.put("ruin.admin.guards.nothing", "&7Aucun gardien en poste dans la &f{0}&7.");
		m.put("ruin.admin.ability.unknown", "&cCapacité ou style inconnu '{0}' ({1}).");
		m.put("ruin.admin.ability.failed", "&cCapacité '{0}' impossible : aucun combat en cours, aucun joueur en survie à portée ou condition non remplie.");
		m.put("ruin.admin.ability.used", "&aCapacité '{0}' utilisée.");
		m.put("ruin.admin.research.finished", "&aRecherche de {0} terminée : {1}");
		m.put("ruin.admin.research.found", "ruine trouvée");
		m.put("ruin.admin.research.nothing", "rien trouvé (remboursement)");
		Lang.addDefaults(m);
	}
}
