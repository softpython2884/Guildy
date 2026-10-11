package dev.guildy.quest;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults for quest texts (keys "quest.*").
 */
final class QuestLang {
	private QuestLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// lifecycle
		m.put("quest.new", "&7Nouvelle quête au tableau : &e{0}");
		m.put("quest.expired", "&cLa quête &e{0}&c a expiré.");
		m.put("quest.progress.actionbar", "&e{0} &7: &f{1}&7/&f{2}");
		m.put("quest.completed", "&a&lQuête terminée : &e{0}&a ! &7Récupérez la récompense au tableau de quêtes (&e/guild quests&7).");
		m.put("quest.completed.title", "&a&lQuête terminée !");
		m.put("quest.completed.subtitle", "&e{0}");
		m.put("quest.claimed", "&e{0}&a a récupéré la récompense de &e{1}&a : &6{2} or&a au trésor, &b{3} XP&a de guilde.");
		m.put("quest.abandoned", "&e{0}&7 a abandonné la quête &e{1}&7.");
		m.put("quest.abandon.completed", "&cCette quête est terminée : récupérez plutôt sa récompense.");
		m.put("quest.claim.gone", "&cCette quête n'est plus disponible.");
		m.put("quest.claim.not_done", "&cCette quête n'est pas encore terminée.");
		m.put("quest.deliver.creative", "&cImpossible de livrer en mode créatif.");
		m.put("quest.deliver.unavailable", "&cCette quête n'accepte pas de livraison.");
		m.put("quest.deliver.none", "&cVous n'avez rien à livrer (&f{0}&c, objets non modifiés uniquement).");
		m.put("quest.deliver.done", "&aVous avez livré &e{0} x {1}&a pour la quête &e{2}&a.");
		// treasure maps
		m.put("quest.treasure.received", "&6Vous avez reçu une carte au trésor (rang {0}) ! &7Allez à l'endroit indiqué, carte en main.");
		m.put("quest.treasure.none", "&7Aucun lieu de trésor valide : &e{0} or&7 ont été versés au trésor à la place.");
		m.put("quest.treasure.near", "&6Trésor à &e{0}&6 blocs...");
		m.put("quest.treasure.blocked", "&cLe trésor est enseveli sous des blocs : dégagez la surface au point indiqué.");
		m.put("quest.treasure.foreign", "&cCe trésor se trouve dans le territoire d'une autre guilde.");
		m.put("quest.treasure.title", "&6&lTrésor découvert !");
		m.put("quest.treasure.subtitle", "&eUn coffre est sorti de terre");
		m.put("quest.treasure.found", "&6Trésor déterré en &e{0} {1} {2}&6 !");
		// board signs (max ~15 characters per line)
		m.put("quest.sign.empty", "&8Nouvelle quête");
		m.put("quest.sign.empty.wait", "&8dans {0} min");
		m.put("quest.sign.empty.soon", "&8bientôt...");
		m.put("quest.sign.done", "&2&lTerminée !");
		m.put("quest.sign.done.reward", "&8Récompense");
		m.put("quest.sign.done.claim", "&8à récupérer");
		m.put("quest.sign.type.gather", "&2&lRécolte");
		m.put("quest.sign.type.kill", "&4&lChasse");
		m.put("quest.sign.type.deliver", "&6&lLivraison");
		m.put("quest.sign.type.survive", "&5&lDéfense");
		m.put("quest.sign.type.explore", "&3&lExploration");
		m.put("quest.sign.progress", "&1{0}/{1}");
		m.put("quest.sign.reward", "&6{0} or");
		m.put("quest.target.survive", "Vagues nocturnes");
		m.put("quest.target.explore", "X {0} Z {1}");
		// menus
		m.put("quest.gui.title", "Quêtes de la guilde");
		m.put("quest.gui.click", "&eClic : détails");
		m.put("quest.gui.info.name", "&e&lTableau de quêtes");
		m.put("quest.gui.info.lore", "&7Les quêtes sont communes à toute la guilde :\n&7chaque membre fait progresser la même quête.\n&7Une fois terminée, n'importe quel membre\n&7peut récupérer la récompense.\n\n&7Quêtes accomplies : &e{0}\n&7Quêtes affichées : &e{1}&7/6");
		m.put("quest.gui.noboard.name", "&c&lAucun tableau de quêtes");
		m.put("quest.gui.noboard.lore", "&7Votre guilde doit bâtir un &eTableau de quêtes\n&7(mur de 3 x 2 blocs) pour recevoir des quêtes.\n&7Récupérez son plan dans le menu des constructions.\n\n&eClic : ouvrir les constructions");
		m.put("quest.gui.empty.name", "&7Panneau {0} : libre");
		m.put("quest.gui.empty.wait", "&7Nouvelle quête dans &e{0}");
		m.put("quest.gui.empty.soon", "&7Nouvelle quête dans quelques instants");
		m.put("quest.gui.empty.noboard", "&7Construisez un tableau de quêtes");
		m.put("quest.gui.detail.title", "Quête : {0}");
		m.put("quest.gui.quest.name", "&e&l{0}");
		m.put("quest.gui.quest.done", "&a&l{0} &2(terminée)");
		m.put("quest.gui.type.gather", "&2Récolte");
		m.put("quest.gui.type.kill", "&4Chasse");
		m.put("quest.gui.type.deliver", "&6Livraison");
		m.put("quest.gui.type.survive", "&5Défense");
		m.put("quest.gui.type.explore", "&3Exploration");
		m.put("quest.gui.objective.gather", "&7Objectif : récolter &f{1} x {0}");
		m.put("quest.gui.objective.kill", "&7Objectif : éliminer &f{1} x {0}");
		m.put("quest.gui.objective.deliver", "&7Objectif : livrer &f{1} x {0}");
		m.put("quest.gui.objective.survive", "&7Objectif : remporter &f{1}&7 vague(s) nocturne(s)");
		m.put("quest.gui.objective.explore", "&7Objectif : se rendre en &f{0}");
		m.put("quest.gui.progress", "&7Progression : {0} &f{1}&7/&f{2}");
		m.put("quest.gui.explore.distance", "&7Distance actuelle : &e{0}&7 blocs");
		m.put("quest.gui.rewards", "&6Récompenses :");
		m.put("quest.gui.reward.gold", "&8- &e{0} or &7(trésor de guilde)");
		m.put("quest.gui.reward.xp", "&8- &b{0} XP &7de guilde");
		m.put("quest.gui.reward.item", "&8- &f{0} x {1}");
		m.put("quest.gui.reward.flag", "&8- &e{0} drapeau(x) d'extension");
		m.put("quest.gui.reward.map", "&8- &6Carte au trésor");
		m.put("quest.gui.completed", "&a&lTerminée ! &aRécompense à récupérer.");
		m.put("quest.gui.expires", "&7Expire dans : &e{0}");
		m.put("quest.gui.contributions", "&e&lContributions");
		m.put("quest.gui.contribution", "&f{0} &8: &e{1}");
		m.put("quest.gui.contributions.none", "&7Personne n'a encore contribué.");
		m.put("quest.gui.deliver.name", "&6&lLivrer");
		m.put("quest.gui.deliver.have", "&7Vous avez : &e{0} x {1}");
		m.put("quest.gui.deliver.missing", "&7Il manque encore : &e{0}");
		m.put("quest.gui.deliver.lore", "&7Prend les objets de votre inventaire\n&7(livraison partielle possible).\n&8Les objets renommés, enchantés ou abîmés\n&8ne sont jamais pris.");
		m.put("quest.gui.claim.name", "&a&lRécupérer la récompense");
		m.put("quest.gui.abandon.name", "&c&lAbandonner");
		m.put("quest.gui.abandon.lore", "&7Retire la quête du tableau ;\n&7une autre la remplacera plus tard.");
		m.put("quest.gui.abandon.confirm_title", "Abandonner la quête ?");
		m.put("quest.gui.abandon.confirm", "&7Abandonner &e{0}&7 ? La progression sera perdue.");
		m.put("quest.gui.need_permission", "&cVotre rôle ne permet pas cette action.");
		// admin
		m.put("quest.admin.rerolled", "&aQuêtes de &e{0}&a renouvelées ({1} retirée(s)).");
		m.put("quest.admin.rerolled_noboard", "&e{0}&7 n'a pas de tableau de quêtes : {1} quête(s) retirée(s), aucune nouvelle.");
		m.put("quest.admin.no_quest", "&cAucune quête active sur le panneau {0} de &e{1}&c.");
		m.put("quest.admin.completed", "&aQuête du panneau {0} de &e{1}&a terminée.");
		Lang.addDefaults(m);
	}
}
