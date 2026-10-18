package dev.guildy.level;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/** French defaults of the guild levels: level-ups, rewards, ranks, progress lines of /guild info and of the menus. */
public final class LevelLang {
	private LevelLang() {
	}

	public static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// level-ups
		m.put("guild.levelup.multi", "&a&lLa guilde gagne {0} niveaux et passe au niveau {1} !");
		m.put("guild.levelup.milestone", "&d&l✦ Palier du niveau {0} atteint !");
		m.put("guild.levelup.rewards", "&7Récompenses : {0}");
		m.put("guild.levelup.flags_hint", "&7Drapeaux d'extension offerts en attente : &e{0}&7 (menu &eTerritoire&7 ou &e/guild flag buy&7, gratuits).");
		m.put("guild.levelup.rank", "&6&lNouveau rang : {0}&6&l !");
		m.put("guild.levelup.max", "&6&lNiveau maximum ({0}) atteint : la guilde est au sommet !");
		m.put("guild.levelup.next", "&7Prochain palier : &eniveau {0}");
		m.put("guild.levelup.subtitle.rank", "&eNiveau {0} &7- rang {1}");
		m.put("guild.milestone.broadcast", "&6✦ La guilde &e{0}&6 atteint le niveau &e{1}&6 !");
		m.put("guild.milestone.broadcast.rank", "&6✦ La guilde &e{0}&6 atteint le niveau &e{1}&6 et le rang {2}&6 !");
		m.put("level.reward.gold", "&6+{0} or");
		m.put("level.reward.flag", "&e+1 drapeau d'extension offert");
		m.put("level.reward.flags", "&e+{0} drapeaux d'extension offerts");
		m.put("level.reward.villager", "&a+1 place de villageois");
		m.put("level.reward.villagers", "&a+{0} places de villageois");
		m.put("level.reward.separator", "&7, ");
		// /guild info
		m.put("guild.info.level_of", "&7Niveau : &e{0}&7/&e{1} &8(&b{2}&8)");
		m.put("guild.info.rank", "&7Rang : {0}");
		m.put("guild.info.bar", "&7Progression : {0} &7{1}% &8(&b{2}&8/&b{3} XP&8)");
		m.put("guild.info.milestone", "&7Prochain palier : &eniveau {0} &8({1}&8)");
		m.put("guild.info.free_flags", "&7Drapeaux d'extension offerts : &e{0}");
		// main menu
		m.put("menu.main.info.level_of", "&7Niveau : &e{0}&7/&e{1}");
		m.put("menu.main.info.rank", "&7Rang : {0}");
		m.put("menu.main.info.next_rank", "&8Rang suivant : {0} &8au niveau {1}");
		m.put("menu.main.info.milestone", "&7Prochain palier : &eniveau {0}");
		m.put("menu.main.info.milestone.rewards", "&8  {0}");
		m.put("menu.main.info.free_flags", "&e{0} drapeau(x) d'extension offert(s) &7(menu Territoire)");
		// territory: free flags
		m.put("menu.territory.buy_flag.free.name", "&a&lPrendre un drapeau offert");
		m.put("menu.territory.buy_flag.free.lore", "&aGratuit &7: récompense de niveau\n&7Drapeaux offerts restants : &e{0}\n&8Ne fait pas monter le prix des drapeaux achetés.");
		m.put("claim.flag.free", "&aDrapeau d'extension offert (récompense de niveau) ! &7Restant(s) : &e{0}&7. Plantez-le à côté de votre territoire.");
		m.put("claim.flag.info.free", "&7Drapeaux d'extension offerts : &e{0} &7(récompenses de niveau, gratuits). ");
		m.put("claim.flag.take", "&a&l[Prendre]");
		m.put("claim.flag.take.hover", "&7Prendre un drapeau d'extension offert (gratuit)");
		// admin
		m.put("cmd.admin.setlevel.max", "&7Niveau maximum de la configuration : &e{0}&7 (levels.maxLevel dans config.json5).");
		m.put("cmd.admin.levels.header", "&6&lCourbe des niveaux &7(niveau maximum : &e{0}&7)");
		m.put("cmd.admin.levels.line", "&7Niveau &e{0}&7 : &b{1}&7 XP au total &8(+{2} pour ce niveau) {3}");
		Lang.addDefaults(m);
	}
}
