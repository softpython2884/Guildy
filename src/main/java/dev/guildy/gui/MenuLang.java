package dev.guildy.gui;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults for the guild menus (menu., members., treasury., banner. keys).
 */
final class MenuLang {
	private MenuLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// main menu
		m.put("menu.main.title", "Guilde : {0}");
		m.put("menu.main.info.name", "&6&l{0}");
		m.put("menu.main.info.role", "&7Votre rôle : &e{0}");
		m.put("menu.main.info.level", "&7Niveau : &e{0}");
		m.put("menu.main.info.xp", "&7XP : &b{0}&7/&b{1}");
		m.put("menu.main.info.xp_max", "&7XP : &b{0} &7(niveau maximum)");
		m.put("menu.main.info.bar", "{0} &7{1}%");
		m.put("menu.main.info.members", "&7Membres : &a{0} en ligne &7/ &f{1}");
		m.put("menu.main.info.chunks", "&7Territoire : &f{0}&7/&f{1} &7chunks");
		m.put("menu.main.info.treasury", "&7Trésor : &6{0} or");
		m.put("menu.main.info.heart", "&7Cœur : &f{0}, {1}, {2}");
		m.put("menu.main.info.heart_none", "&7Cœur : &cbase non fondée");
		m.put("menu.main.info.night", "&7Nuit : &f{0}");
		m.put("menu.main.info.record", "&7Victoires : &a{0} &8| &7Défaites : &c{1}");
		m.put("menu.main.info.wave_active", "&c&l⚔ Vague en cours !");
		m.put("menu.main.xpbar", "&bVers le niveau {0} : &f{1}&7/&f{2} XP &7({3}%)");
		m.put("menu.main.xpbar.max", "&6Niveau maximum atteint ({0}) !");
		m.put("menu.main.members.name", "&e&lMembres");
		m.put("menu.main.members.lore", "&7{0} membre(s).\n&7Rôles, exclusions, invitations.\n\n&e» Cliquez pour ouvrir");
		m.put("menu.main.territory.name", "&a&lTerritoire");
		m.put("menu.main.territory.lore", "&7{0}/{1} chunks revendiqués.\n&7Drapeaux, frontières, ralliement.\n\n&e» Cliquez pour ouvrir");
		m.put("menu.main.treasury.name", "&6&lTrésor");
		m.put("menu.main.treasury.lore", "&7Solde : &6{0} or\n&7Déposer et retirer de l'or.\n\n&e» Cliquez pour ouvrir");
		m.put("menu.main.banner.name", "&f&lBannière");
		m.put("menu.main.banner.lore", "&7Personnaliser la bannière de la guilde\n&7(drapeaux, menus, bannières décoratives).\n\n&e» Cliquez pour ouvrir");
		m.put("menu.main.structures.name", "&b&lConstructions");
		m.put("menu.main.structures.lore", "&7Tableau de quêtes, boutique, accueil...\n&7Plans et déblocages.\n\n&e» Cliquez pour ouvrir");
		m.put("menu.main.quests.name", "&d&lQuêtes");
		m.put("menu.main.quests.lore", "&7Quêtes en cours de la guilde.\n\n&e» Cliquez pour ouvrir");
		m.put("menu.main.villagers.name", "&2&lVillageois");
		m.put("menu.main.villagers.lore", "&7Villageois de la guilde et leurs tâches.\n\n&e» Cliquez pour ouvrir");
		m.put("menu.main.waves.name", "&c&lVagues");
		m.put("menu.main.waves.lore", "&7Hordes nocturnes : statistiques.\n\n&e» Cliquez pour ouvrir");
		m.put("menu.main.help.name", "&e&lAide");
		m.put("menu.main.help.lore", "&7Comment jouer ? Les étapes clés\n&7et les commandes (dans le chat).");
		m.put("menu.main.leave.name", "&c&lQuitter la guilde");
		m.put("menu.main.leave.lore", "&7Quitter définitivement la guilde\n&7(confirmation demandée).");
		m.put("menu.main.disband.name", "&4&lDissoudre la guilde");
		m.put("menu.main.disband.lore", "&7Supprime la guilde, son territoire\n&7et son trésor (confirmation demandée).\n"
				+ "&8Pour partir sans dissoudre : transférez\n&8la direction (menu Membres).");

		// no guild
		m.put("menu.noguild.title", "Guildes");
		m.put("menu.noguild.info.name", "&6&lVous n'avez pas de guilde");
		m.put("menu.noguild.info.lore", "&7Créez votre propre guilde\n&7ou acceptez une invitation.");
		m.put("menu.noguild.create.name", "&a&lCréer une guilde");
		m.put("menu.noguild.create.lore", "&7Choisissez un nom ({0} à {1} caractères).\n&7Vous recevrez le &6drapeau fondateur&7.\n\n&e» Cliquez pour choisir le nom");
		m.put("menu.noguild.create.input", "Nom de la guilde");
		m.put("menu.noguild.list.name", "&e&lGuildes existantes");
		m.put("menu.noguild.list.lore", "&7Afficher le classement dans le chat.");
		m.put("menu.noguild.invites.none.name", "&7Aucune invitation");
		m.put("menu.noguild.invites.none.lore", "&8Un officier d'une guilde peut vous inviter :\n&8/guild invite <joueur>");
		m.put("menu.noguild.invite.name", "&6Invitation : &e{0}");
		m.put("menu.noguild.invite.lore", "&7Niveau {0} &8| &7{1} membre(s)\n\n&aClic gauche : rejoindre\n&cClic droit : refuser");

		// members
		m.put("members.title", "Membres ({0}/{1})");
		m.put("members.title_unlimited", "Membres ({0})");
		m.put("members.head.name.leader", "&6&l{0}");
		m.put("members.head.name.officer", "&e{0}");
		m.put("members.head.name.member", "&f{0}");
		m.put("members.head.role", "&7Rôle : &e{0}");
		m.put("members.head.online", "&a● En ligne");
		m.put("members.head.offline", "&8● Hors ligne");
		m.put("members.head.joined", "&7Membre depuis le &f{0}");
		m.put("members.head.you", "&b(vous)");
		m.put("members.head.manage", "&e» Cliquez pour gérer");
		m.put("members.invite.name", "&a&lInviter un joueur");
		m.put("members.invite.lore", "&7Membres : &f{0}&7/&f{1}\n&7Choisir parmi les joueurs en ligne\n&7qui n'ont pas de guilde.");
		m.put("members.manage.title", "Gérer {0}");
		m.put("members.action.promote.name", "&a&lPromouvoir");
		m.put("members.action.promote.lore", "&7{0} devient &eOfficier&7.");
		m.put("members.action.demote.name", "&e&lRétrograder");
		m.put("members.action.demote.lore", "&7{0} redevient &fMembre&7.");
		m.put("members.action.kick.name", "&c&lExclure");
		m.put("members.action.kick.lore", "&7Retirer {0} de la guilde\n&7(confirmation demandée).");
		m.put("members.action.transfer.name", "&6&lTransférer la direction");
		m.put("members.action.transfer.lore", "&7{0} devient le chef de la guilde,\n&7vous devenez officier\n&7(confirmation demandée).");
		m.put("members.invite_menu.title", "Inviter un joueur");
		m.put("members.invite_menu.info.name", "&a&lJoueurs sans guilde");
		m.put("members.invite_menu.info.lore", "&7Cliquez sur un joueur pour l'inviter.\n&7L'invitation expire après {0} s.");
		m.put("members.invite_menu.none.name", "&7Aucun joueur disponible");
		m.put("members.invite_menu.none.lore", "&8Tous les joueurs en ligne ont\n&8déjà une guilde.");
		m.put("members.invite_menu.player.name", "&f{0}");
		m.put("members.invite_menu.player.invited", "&a✔ Déjà invité");
		m.put("members.invite_menu.player.lore", "&e» Cliquez pour inviter");

		// territory
		m.put("menu.territory.title", "Territoire");
		m.put("menu.territory.info.name", "&a&lTerritoire de {0}");
		m.put("menu.territory.info.chunks", "&7Chunks : &f{0}&7/&f{1}");
		m.put("menu.territory.info.heart", "&7Cœur : &f{0}, {1}, {2}");
		m.put("menu.territory.info.heart_none", "&7Cœur : &cbase non fondée\n&8Plantez le drapeau fondateur pour fonder la base.");
		m.put("menu.territory.info.home", "&7Ralliement : &f{0}, {1}, {2}");
		m.put("menu.territory.info.home_none", "&7Ralliement : &8aucun");
		m.put("menu.territory.info.flag_price", "&7Prochain drapeau d'extension : &6{0} or");
		m.put("menu.territory.chunk.name", "&e&lChunk actuel &7[{0}, {1}]");
		m.put("menu.territory.chunk.own", "&aAppartient à votre guilde");
		m.put("menu.territory.chunk.heart", "&c❤ Chunk du cœur");
		m.put("menu.territory.chunk.border", "&7Chunk frontalier");
		m.put("menu.territory.chunk.other", "&cAppartient à la guilde &e{0}");
		m.put("menu.territory.chunk.free", "&7Libre");
		m.put("menu.territory.chunk.wrong_dimension", "&8Pas de territoire dans cette dimension.");
		m.put("menu.territory.claim.name", "&a&lRevendiquer ce chunk");
		m.put("menu.territory.claim.lore", "&7Utilise un &edrapeau d'extension&7 de votre\n&7inventaire (vous en avez &e{0}&7).\n&7Le chunk doit toucher le territoire.");
		m.put("menu.territory.unclaim.name", "&c&lLibérer ce chunk");
		m.put("menu.territory.unclaim.lore", "&7Rend le chunk libre et vous redonne\n&7un drapeau d'extension.\n&8Impossible pour le chunk du cœur.");
		m.put("menu.territory.borders.name", "&b&lAfficher les frontières");
		m.put("menu.territory.borders.lore", "&7Particules le long des limites\n&7pendant {0} s.");
		m.put("menu.territory.buy_flag.name", "&e&lAcheter un drapeau d'extension");
		m.put("menu.territory.buy_flag.lore", "&7Prix : &6{0} or &7(payé par le trésor)\n&7Trésor : &6{1} or\n&8Le prix augmente à chaque achat.");
		m.put("menu.territory.founding_flag.name", "&6&lRécupérer le drapeau fondateur");
		m.put("menu.territory.founding_flag.lore", "&7Perdu le drapeau fondateur ?\n&7Obtenez-en un nouveau tant que\n&7la base n'est pas fondée.");
		m.put("menu.territory.abandon.name", "&4&lAbandonner le territoire");
		m.put("menu.territory.abandon.lore", "&7Libère tous les chunks et retire le cœur.\n&7Le drapeau fondateur vous est rendu\n&7pour refonder ailleurs.");
		m.put("menu.territory.abandon.confirm.title", "&4Abandonner le territoire ?");
		m.put("menu.territory.abandon.confirm.lore", "&7Les {0} chunks seront libérés et le cœur\n&7retiré. Vous récupérerez le drapeau\n&7fondateur pour refonder ailleurs.");
		m.put("menu.territory.sethome.name", "&d&lDéfinir le point de ralliement");
		m.put("menu.territory.sethome.lore", "&7À votre position actuelle\n&7(dans le territoire).");
		m.put("menu.territory.home.name", "&d&lSe téléporter au ralliement");
		m.put("menu.territory.home.lore", "&7Position : &f{0}, {1}, {2}");
		m.put("menu.territory.home.lore_none", "&8Aucun point de ralliement défini.");
		m.put("menu.territory.not_founded", "&cLa base n'est pas encore fondée (plantez le drapeau fondateur).");
		m.put("menu.territory.wrong_dimension", "&cLes territoires n'existent pas dans cette dimension.");
		m.put("menu.territory.already_own", "&cCe chunk appartient déjà à votre guilde.");
		m.put("menu.territory.other_owner", "&cCe chunk appartient à la guilde &e{0}&c.");
		m.put("menu.territory.max_chunks", "&cLimite de chunks atteinte ({0}) : montez de niveau !");
		m.put("menu.territory.no_flag", "&cIl vous faut un drapeau d'extension dans l'inventaire.");
		m.put("menu.territory.not_own", "&cVous n'êtes pas dans un chunk de votre guilde.");
		m.put("menu.territory.heart_chunk", "&cLe chunk du cœur ne peut pas être libéré.");
		m.put("menu.territory.already_founded", "&cLa base est déjà fondée.");
		m.put("menu.territory.no_home", "&cAucun point de ralliement n'est défini.");

		// treasury
		m.put("treasury.title", "Trésor de la guilde");
		m.put("treasury.balance.name", "&6&lTrésor : {0} or");
		m.put("treasury.balance.lore", "&7Votre or : &6{0}\n&8(lingots, blocs d'or = 9)");
		m.put("treasury.balance.no_withdraw", "&8Votre rôle ne permet pas de retirer.");
		m.put("treasury.deposit.name", "&a&lDéposer {0} or");
		m.put("treasury.deposit.lore", "&7De votre inventaire vers le trésor.");
		m.put("treasury.withdraw.name", "&e&lRetirer {0} or");
		m.put("treasury.withdraw.lore", "&7Du trésor vers votre inventaire.");
		m.put("treasury.deposit_all.name", "&a&lDéposer tout mon or");
		m.put("treasury.deposit_all.lore", "&7Vous avez &6{0} or&7 sur vous.");
		m.put("treasury.deposit_all.lore_nuggets", "&7Vous avez &6{0} or&7 sur vous\n&7+ &6{1} or&7 en pépites (9 = 1 or).");

		// banner editor
		m.put("banner.title", "Bannière de la guilde");
		m.put("banner.preview.name", "&f&lAperçu");
		m.put("banner.preview.base", "&7Couleur de fond : ");
		m.put("banner.preview.layers", "&7Motifs : &f{0}&7/&f{1}");
		m.put("banner.preview.saved", "&a✔ Bannière enregistrée");
		m.put("banner.preview.unsaved", "&e⚠ Modifications non enregistrées");
		m.put("banner.base.pick", "&7Couleur de fond\n&e» Cliquez pour appliquer");
		m.put("banner.base.current", "&a✔ Couleur de fond actuelle");
		m.put("banner.layer.lore", "&7Motif n°{0}\n&cClic droit : retirer ce motif");
		m.put("banner.layer.empty", "&8Emplacement de motif n°{0}");
		m.put("banner.add.name", "&a&lAjouter un motif");
		m.put("banner.add.lore", "&7Choisir un motif, puis sa couleur.\n&7Motifs : &f{0}&7/&f{1}");
		m.put("banner.remove_last.name", "&c&lRetirer le dernier motif");
		m.put("banner.remove_last.lore", "&7Annule le dernier motif ajouté.");
		m.put("banner.remove_last.empty", "&cAucun motif à retirer.");
		m.put("banner.reset.name", "&e&lRéinitialiser");
		m.put("banner.reset.lore", "&7Repartir d'une bannière blanche unie.");
		m.put("banner.revert.name", "&7&lAnnuler les modifications");
		m.put("banner.revert.lore", "&7Revenir à la bannière enregistrée.");
		m.put("banner.revert.nothing", "&cAucune modification à annuler.");
		m.put("banner.save.name", "&a&lEnregistrer");
		m.put("banner.save.lore", "&7Devient la bannière officielle de la guilde\n&7(nouveaux drapeaux, menus...).");
		m.put("banner.decorative.name", "&6&lObtenir une bannière décorative");
		m.put("banner.decorative.lore", "&7Prix : &6{0} or &7(payé par le trésor)\n&7Aux couleurs de la bannière &benregistrée&7.");
		m.put("banner.patterns.title", "Choisir un motif");
		m.put("banner.patterns.title_paged", "Choisir un motif ({0}/{1})");
		m.put("banner.pattern.lore", "&8{0}\n&e» Cliquez pour choisir la couleur");
		m.put("banner.colors.title", "Choisir la couleur du motif");
		m.put("banner.color.lore", "&e» Cliquez pour ajouter ce motif");
		Lang.addDefaults(m);
	}
}
