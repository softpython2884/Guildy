package dev.guildy.command;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults for guild management chat messages (guild., treasury., banner., cmd. keys).
 */
final class CommandLang {
	private CommandLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("guild.cancelled", "&7Action annulée.");
		m.put("guild.leader_only", "&cSeul le chef de la guilde peut faire cela.");
		m.put("guild.full", "&cLa guilde est complète (&e{0}&c membres maximum).");
		m.put("guild.member.unknown", "&cAucun membre nommé &e{0}&c dans votre guilde.");
		m.put("guild.player.unknown", "&cJoueur introuvable ou hors ligne : &e{0}");
		m.put("guild.role.self", "&cVous ne pouvez pas modifier votre propre rôle.");

		// creation
		m.put("guild.created.title", "&6&l{0}");
		m.put("guild.created.subtitle", "&eGuilde créée ! Plantez le drapeau fondateur.");
		m.put("guild.created.explain", "&aLa guilde &e{0}&a est créée, vous en êtes le chef !\n"
				+ "&7➤ &fPlantez le &6drapeau fondateur&f (dans votre inventaire) là où vous voulez bâtir : "
				+ "il devient le &ccœur&f de votre base et revendique les chunks autour.\n"
				+ "&7➤ &fChaque nuit, une &chorde&f attaque le cœur : défendez-le jusqu'à l'aube pour gagner de l'&6or&f et de l'&bXP&f.\n"
				+ "&7➤ &fRecrutez vos amis avec &e/guild invite <joueur>&f. Menu de la guilde : &e/guild");
		m.put("guild.created.broadcast", "&7Une nouvelle guilde est née : &e{0}&7 (chef : &f{1}&7).");

		// invitations
		m.put("guild.invite.self", "&cVous ne pouvez pas vous inviter vous-même.");
		m.put("guild.invite.already_member", "&e{0}&c fait déjà partie de votre guilde.");
		m.put("guild.invite.target_in_guild", "&e{0}&c fait déjà partie d'une autre guilde.");
		m.put("guild.invite.sent", "&aInvitation envoyée à &e{0}&a (valable {1} s).");
		m.put("guild.invite.notify", "&e{0}&7 a invité &e{1}&7 à rejoindre la guilde.");
		m.put("guild.invite.received", "&e{1}&f vous invite à rejoindre la guilde &6{0}&f ! &7(expire dans {2} s)");
		m.put("guild.invite.accept", "&a&l[Rejoindre]");
		m.put("guild.invite.accept.hover", "&aRejoindre la guilde");
		m.put("guild.invite.deny", "&c&l[Refuser]");
		m.put("guild.invite.deny.hover", "&cRefuser l'invitation");
		m.put("guild.join.no_invite", "&cVous n'avez aucune invitation en attente.");
		m.put("guild.join.not_invited", "&cVous n'avez pas (ou plus) d'invitation de la guilde &e{0}&c.");
		m.put("guild.join.choose", "&7Vous avez plusieurs invitations, choisissez :");
		m.put("guild.join.option", "&e[{0}]");
		m.put("guild.join.success", "&aBienvenue dans la guilde &e{0}&a ! &7(/guild pour ouvrir le menu)");
		m.put("guild.join.notify", "&e{0}&a a rejoint la guilde !");
		m.put("guild.deny.success", "&7Invitation de la guilde &e{0}&7 refusée.");
		m.put("guild.deny.notify", "&e{0}&7 a refusé l'invitation à rejoindre la guilde.");

		// leaving & roles
		m.put("guild.leave.leader", "&cVous êtes le chef : transférez d'abord la direction (&e/guild transfer <membre>&c) "
				+ "ou dissolvez la guilde (&e/guild disband&c).");
		m.put("guild.leave.success", "&7Vous avez quitté la guilde &e{0}&7.");
		m.put("guild.leave.notify", "&e{0}&7 a quitté la guilde.");
		m.put("guild.leave.confirm.title", "&cQuitter la guilde ?");
		m.put("guild.leave.confirm.lore", "&7Vous quitterez la guilde &e{0}&7.\n&7Il faudra une nouvelle invitation pour revenir.");
		m.put("guild.leave.alone.title", "&cQuitter et dissoudre ?");
		m.put("guild.leave.alone.lore", "&7Vous êtes le dernier membre :\n&7la guilde &e{0}&7 sera &cdissoute&7.\n&7Le trésor (&6{1} or&7) sera perdu.");
		m.put("guild.kick.self", "&cVous ne pouvez pas vous exclure vous-même (&e/guild leave&c).");
		m.put("guild.kick.higher", "&cVous ne pouvez pas exclure un membre de rang égal ou supérieur au vôtre.");
		m.put("guild.kick.notify", "&e{0}&7 a été exclu de la guilde par &e{1}&7.");
		m.put("guild.kick.target", "&cVous avez été exclu de la guilde &e{0}&c par &e{1}&c.");
		m.put("guild.kick.confirm.title", "&cExclure {0} ?");
		m.put("guild.kick.confirm.lore", "&e{0}&7 sera retiré de la guilde.");
		m.put("guild.promote.max", "&e{0}&c est déjà officier. Pour lui confier la guilde : &e/guild transfer {0}");
		m.put("guild.demote.min", "&e{0}&c est déjà simple membre.");
		m.put("guild.promote.notify", "&e{0}&a a été promu &e{1}&a par &e{2}&a.");
		m.put("guild.demote.notify", "&e{0}&7 a été rétrogradé &e{1}&7 par &e{2}&7.");
		m.put("guild.transfer.self", "&cVous êtes déjà le chef de la guilde.");
		m.put("guild.transfer.notify", "&6{1}&e est le nouveau chef de la guilde (passation par &6{0}&e) !");
		m.put("guild.transfer.confirm.title", "&6Transférer la direction ?");
		m.put("guild.transfer.confirm.lore", "&e{0}&7 deviendra le chef de la guilde.\n&7Vous deviendrez officier.");
		m.put("guild.disband.confirm.title", "&4Dissoudre la guilde ?");
		m.put("guild.disband.confirm.lore", "&7La guilde &e{0}&7 sera &cdéfinitivement&7 dissoute :\n"
				+ "&7membres exclus, territoire libéré,\n&7trésor (&6{1} or&7) perdu.");
		m.put("guild.disband.notify", "&cLa guilde &e{0}&c a été dissoute par &e{1}&c.");
		m.put("guild.disband.admin_notify", "&cLa guilde &e{0}&c a été dissoute par un administrateur.");

		// info
		m.put("guild.info.header", "&6&l━━━━━ &e&l{0} &6&l━━━━━");
		m.put("guild.info.level", "&7Niveau : &e{0} &8(&b{1}&8)");
		m.put("guild.info.xp", "{0}/{1} XP");
		m.put("guild.info.xp_max", "{0} XP, niveau max");
		m.put("guild.info.leader", "&7Chef : &6{0}");
		m.put("guild.info.members", "&7Membres (&f{0}&7) : {1}");
		m.put("guild.info.territory", "&7Territoire : &f{0}&7/&f{1}&7 chunks");
		m.put("guild.info.heart", "&7Cœur : &f{0}, {1}, {2}");
		m.put("guild.info.heart_founded", "&7Cœur : &abase fondée");
		m.put("guild.info.heart_none", "&7Cœur : &cbase non fondée");
		m.put("guild.info.treasury", "&7Trésor : &6{0} or");
		m.put("guild.info.waves", "&7Nuit : &f{0} &8| &7Victoires : &a{1} &8| &7Défaites : &c{2}");
		m.put("guild.info.created", "&7Créée le : &f{0}");

		// list
		m.put("guild.list.header", "&6&lGuildes &7({0}) &8- &7page {1}/{2}");
		m.put("guild.list.entry", "&e#{0} &f{1} &7- niveau &e{2} &8| &7{3} membre(s)");
		m.put("guild.list.entry.hover", "&7Cliquez pour afficher les informations");
		m.put("guild.list.empty", "&7Aucune guilde pour le moment. Créez la vôtre : &e/guild create <nom>");
		m.put("guild.list.prev", "&e[« Précédente]");
		m.put("guild.list.next", "&e[Suivante »]");
		m.put("guild.list.page.hover", "&7Changer de page");

		m.put("guild.version", "&6Guildy &e{0} &7(Minecraft {1})");

		// help
		m.put("guild.help.header", "&6&l━━━━━ &e&lGuildy : comment jouer &6&l━━━━━");
		m.put("guild.help.lines", "&e1. &fCréez une guilde, ou rejoignez-en une sur invitation.\n"
				+ "&e2. &fPlantez le &6drapeau fondateur&f : il devient le &ccœur&f de votre base et revendique les chunks autour.\n"
				+ "&e3. &fChaque nuit, une &chorde&f attaque le cœur : tenez jusqu'à l'aube ! Les nuits sont de plus en plus dures.\n"
				+ "&e4. &fVictoires = &6or&f, butin et &bXP&f de guilde. Déposez l'or au &6trésor&f commun.\n"
				+ "&e5. &fAgrandissez le territoire avec des &edrapeaux d'extension&f (menu Territoire).\n"
				+ "&e6. &fRecrutez des &avillageois&f : ils travaillent pour la guilde.\n"
				+ "&e7. &fBâtissez le &btableau de quêtes&f et la &bboutique&f, accomplissez des quêtes pour progresser.");
		m.put("guild.help.commands", "&7Commandes : ");
		m.put("guild.help.button.menu", "&a[Menu]");
		m.put("guild.help.button.menu.hover", "&7/guild : ouvrir le menu de guilde");
		m.put("guild.help.button.create", "&a[Créer]");
		m.put("guild.help.button.create.hover", "&7/guild create <nom>");
		m.put("guild.help.button.invite", "&a[Inviter]");
		m.put("guild.help.button.invite.hover", "&7/guild invite <joueur>");
		m.put("guild.help.button.info", "&a[Infos]");
		m.put("guild.help.button.info.hover", "&7/guild info : résumé de votre guilde");
		m.put("guild.help.button.list", "&a[Classement]");
		m.put("guild.help.button.list.hover", "&7/guild list : toutes les guildes");
		m.put("guild.help.button.deposit", "&a[Déposer l'or]");
		m.put("guild.help.button.deposit.hover", "&7/guild deposit : tout votre or va au trésor");

		// treasury
		m.put("treasury.deposit.nothing", "&cVous n'avez pas d'or à déposer.");
		m.put("treasury.deposit.success", "&aVous avez déposé &6{0} or&a. Trésor : &6{1} or&a.");
		m.put("treasury.withdraw.success", "&aVous avez retiré &6{0} or&a. Trésor : &6{1} or&a.");
		m.put("treasury.withdraw.notify", "&e{0}&7 a retiré &6{1} or&7 du trésor.");

		// banner
		m.put("banner.saved", "&aLa nouvelle bannière de la guilde est enregistrée !");
		m.put("banner.adopt.done", "&aLa bannière que vous tenez est désormais celle de la guilde ! Le cœur et les drapeaux suivront ce dessin.");
		m.put("banner.adopt.no_banner", "&cTenez en main la bannière à adopter (tissez-la au métier à tisser avec vos colorants).");
		m.put("banner.adopt.name", "&a&lAdopter la bannière en main");
		m.put("banner.adopt.lore", "&7La bannière que vous tenez devient celle de\n&7la guilde : cœur de la base, drapeaux,\n"
				+ "&7bannières décoratives et cartes.\n\n&eClic : adopter");
		m.put("banner.adopt.lore.empty", "&7Prenez en main une bannière tissée au\n&7métier à tisser, puis cliquez ici.\n\n"
				+ "&cAucune bannière en main.");
		m.put("banner.loom.name", "&6&lCréer le drapeau au métier à tisser");
		m.put("banner.loom.lore", "&71. Fabriquez une bannière de la couleur de fond voulue\n"
				+ "&72. Au &fmétier à tisser&7, ajoutez des motifs avec vos &fcolorants\n"
				+ "&7   (le drapeau fondateur peut lui-même y être tissé)\n"
				+ "&73. Tenez-la en main et cliquez sur &a« Adopter »\n\n"
				+ "&7Le drapeau planté devient le cœur de la base\n"
				+ "&7et apparaît sur les &fcartes&7 avec le nom de la guilde.");
		m.put("banner.saved.notify", "&e{0}&7 a modifié la bannière de la guilde.");
		m.put("banner.decorative.bought", "&aBannière décorative obtenue pour &6{0} or&a (payé par le trésor).");
		m.put("banner.max_layers", "&cUne bannière ne peut pas avoir plus de {0} motifs.");

		// admin
		m.put("cmd.admin.setlevel", "&aNiveau de &e{0}&a fixé à &e{1}&a.");
		m.put("cmd.admin.addxp", "&a+{1} XP pour &e{0}&a (niveau {2}, {3} XP).");
		m.put("cmd.admin.addgold", "&aTrésor de &e{0}&a : {1} → &6{2} or&a.");
		m.put("cmd.admin.disband", "&aLa guilde &e{0}&a a été dissoute.");
		m.put("cmd.admin.forcejoin", "&e{0}&a a été ajouté à la guilde &e{1}&a.");
		m.put("cmd.admin.forcejoin.same", "&e{0}&c fait déjà partie de &e{1}&c.");
		m.put("cmd.admin.forcejoin.leader", "&e{0}&c dirige la guilde &e{1}&c : transférez la direction ou dissolvez-la d'abord.");
		m.put("cmd.admin.forcejoin.moved", "&e{0}&7 a été retiré de la guilde &e{1}&7 par un administrateur.");
		m.put("cmd.admin.forcejoin.target", "&aUn administrateur vous a ajouté à la guilde &e{0}&a.");
		Lang.addDefaults(m);
	}
}
