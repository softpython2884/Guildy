package dev.guildy.upgrade;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults for the upgrade tree (keys "upgrade.*" and the main menu button "menu.main.upgrades.*").
 */
final class UpgradeLang {
	private UpgradeLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// main menu button
		m.put("menu.main.upgrades.name", "&5&lArbre d'évolution");
		m.put("menu.main.upgrades.lore", "&7Améliorations permanentes de la guilde.\n&7Débloquées : &a{0}&7/&f{1}\n\n&e» Cliquez pour ouvrir");
		// menu
		m.put("upgrade.gui.title", "Arbre d'évolution");
		m.put("upgrade.gui.title_paged", "Arbre d'évolution ({0}/{1})");
		m.put("upgrade.gui.none", "&cAucune amélioration n'est configurée.");
		m.put("upgrade.gui.help.name", "&e&lComment ça marche ?");
		m.put("upgrade.gui.help.lore", "&7Chaque branche se débloque de gauche à\n&7droite : un palier demande le précédent.\n\n"
				+ "&7Coût : &6or du trésor&7 (rôle autorisé à\n&7dépenser le trésor) + &fmatériaux&7 pris dans\n"
				+ "&7votre inventaire (objets simples : ni\n&7renommés, ni enchantés, ni abîmés).\n\n"
				+ "&7Les bonus sont &apermanents&7 et profitent\n&7à toute la guilde.");
		m.put("upgrade.gui.info.name", "&5&lArbre d'évolution");
		m.put("upgrade.gui.info.lore", "&7Trésor : &6{0} or\n&7Niveau de guilde : &e{1}\n&7Améliorations : &a{2}&7/&f{3}");
		m.put("upgrade.gui.info.bonuses", "&6Bonus actifs :");
		m.put("upgrade.gui.info.bonus", "&8- &a{0}");
		m.put("upgrade.gui.info.none", "&8Aucun bonus pour l'instant.");
		m.put("upgrade.gui.branch.name", "&6&l{0}");
		m.put("upgrade.gui.branch.lore", "&7Améliorations débloquées : &a{0}&7/&f{1}");
		m.put("upgrade.gui.branch.empty", "&8Aucune amélioration dans cette branche.");
		m.put("upgrade.gui.node.name.unlocked", "&a&l{0} &a✔");
		m.put("upgrade.gui.node.name.available", "&e&l{0}");
		m.put("upgrade.gui.node.name.locked", "&7{0}");
		m.put("upgrade.gui.node.header", "&8{0} · palier {1}");
		m.put("upgrade.gui.node.effects", "&6Effets :");
		m.put("upgrade.gui.node.effect", "&8- &a{0}");
		m.put("upgrade.gui.node.no_effect", "&8- aucun");
		m.put("upgrade.gui.node.cost", "&6Coût :");
		m.put("upgrade.gui.node.cost.gold", "&8- &e{0} or &7(trésor : &a{1}&7)");
		m.put("upgrade.gui.node.cost.gold.missing", "&8- &e{0} or &7(trésor : &c{1}&7)");
		m.put("upgrade.gui.node.cost.item", "&8- &f{0} x {1} &7(vous : &a{2}&7)");
		m.put("upgrade.gui.node.cost.item.missing", "&8- &f{0} x {1} &7(vous : &c{2}&7)");
		m.put("upgrade.gui.node.cost.free", "&8- &aGratuit");
		m.put("upgrade.gui.node.conditions", "&6Conditions :");
		m.put("upgrade.gui.node.level", "&8- &aGuilde niveau {0} ✔");
		m.put("upgrade.gui.node.level.missing", "&8- &cGuilde niveau {0} &7(actuel : {1})");
		m.put("upgrade.gui.node.require", "&8- &a{0} ✔");
		m.put("upgrade.gui.node.require.missing", "&8- &c{0} ✘");
		m.put("upgrade.gui.node.state.unlocked", "&a&l✔ Débloqué");
		m.put("upgrade.gui.node.state.available", "&e» Cliquez pour débloquer");
		m.put("upgrade.gui.node.state.unaffordable", "&cDisponible, mais ressources insuffisantes");
		m.put("upgrade.gui.node.state.permission", "&8Disponible : votre rôle ne permet pas\n&8de dépenser le trésor.");
		m.put("upgrade.gui.node.state.locked", "&c✘ Verrouillé");
		m.put("upgrade.confirm.title", "Débloquer l'amélioration ?");
		m.put("upgrade.confirm.question", "&7Débloquer &d{0}&7 pour :");
		m.put("upgrade.confirm.gold", "&8- &6{0} or &7du trésor");
		m.put("upgrade.confirm.item", "&8- &f{0} x {1} &7de votre inventaire");
		// unlocking
		m.put("upgrade.unlocked", "&e{0}&a a débloqué l'amélioration &d{1}&a : &f{2}");
		m.put("upgrade.unlocked.title", "&d&lAmélioration débloquée");
		m.put("upgrade.unlocked.subtitle", "&f{0}");
		m.put("upgrade.error.unknown", "&cAmélioration introuvable : &e{0}");
		m.put("upgrade.error.already", "&7Amélioration déjà débloquée : &f{0}");
		m.put("upgrade.error.level", "&cIl faut une guilde de niveau &e{0}&c (niveau actuel : &e{1}&c).");
		m.put("upgrade.error.requires", "&cDébloquez d'abord : &e{0}&c.");
		m.put("upgrade.error.gold", "&cIl faut &e{0} or&c dans le trésor (il contient &e{1} or&c).");
		m.put("upgrade.error.items", "&cIl vous manque : {0}");
		m.put("upgrade.error.items.entry", "&e{0} x {1}");
		m.put("upgrade.error.creative", "&cImpossible de payer des matériaux en mode créatif.");
		m.put("upgrade.error.payment", "&cLe paiement a échoué : rien n'a été pris.");
		// admin
		m.put("upgrade.admin.unknown", "&cAmélioration inconnue : &e{0}");
		m.put("upgrade.admin.nothing", "&7Aucun changement pour &e{0}&7.");
		m.put("upgrade.admin.unlocked", "&aAmélioration(s) débloquée(s) pour &e{0}&a : &f{1}");
		m.put("upgrade.admin.locked", "&aAmélioration(s) retirée(s) à &e{0}&a : &f{1}");
		m.put("upgrade.admin.notify.unlocked", "&dUn administrateur a débloqué : &f{0}");
		m.put("upgrade.admin.notify.locked", "&cUn administrateur a retiré : &f{0}");
		// effects ({0} = value, or roman level for the potion effects)
		m.put("upgrade.effect." + UpgradeEffects.HEART_INTEGRITY_PCT, "+{0} % d'intégrité du cœur");
		m.put("upgrade.effect." + UpgradeEffects.GUARD_DAMAGE_PCT, "+{0} % de dégâts des gardes");
		m.put("upgrade.effect." + UpgradeEffects.WAVE_REGEN, "Régénération {0} dans le territoire pendant les hordes");
		m.put("upgrade.effect." + UpgradeEffects.DEFEAT_LOSS_REDUCTION_PCT, "-{0} % d'or perdu en cas de défaite");
		m.put("upgrade.effect." + UpgradeEffects.VILLAGER_LOYALTY_PCT, "-{0} % de départs de villageois après une défaite");
		m.put("upgrade.effect." + UpgradeEffects.QUEST_REWARD_PCT, "+{0} % d'XP et d'or des quêtes (hors or des livraisons)");
		m.put("upgrade.effect." + UpgradeEffects.WAVE_REWARD_PCT, "+{0} % d'or et d'XP des victoires contre les hordes");
		m.put("upgrade.effect." + UpgradeEffects.FLAG_DISCOUNT_PCT, "-{0} % sur le prix des drapeaux d'extension");
		m.put("upgrade.effect." + UpgradeEffects.EXTRA_CHUNKS, "+{0} chunks de territoire maximum");
		m.put("upgrade.effect." + UpgradeEffects.EXTRA_VILLAGERS, "+{0} villageois maximum");
		m.put("upgrade.effect." + UpgradeEffects.WORK_SPEED_PCT, "-{0} % de temps de travail des villageois");
		m.put("upgrade.effect." + UpgradeEffects.TERRITORY_HASTE, "Célérité {0} dans le territoire");
		Lang.addDefaults(m);
	}
}
