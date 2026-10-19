package dev.guildy.fly;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the guild flight (fly. keys, the territory menu button and the /guild help line).
 */
final class FlyLang {
	private FlyLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("fly.on", "&a✈ Vol de guilde activé &7: sautez deux fois pour décoller (dans le territoire de la guilde).");
		m.put("fly.on.waiting", "&a✈ Vol de guilde activé&7, indisponible pour l'instant : {0}");
		m.put("fly.off", "&7✈ Vol de guilde désactivé.");
		m.put("fly.refused", "&cVol de guilde impossible : {0}");
		m.put("fly.available", "&a✈ Vol disponible &7(sautez deux fois pour décoller)");
		m.put("fly.border", "&e⚠ Bord du territoire : le vol s'arrête au-delà !");

		// Flight taken back (action bar).
		m.put("fly.stopped.outside", "&c✈ Vol coupé : hors du territoire");
		m.put("fly.stopped.horde", "&c&l✈ Vol coupé : la horde arrive !");
		m.put("fly.stopped.combat", "&c✈ Vol coupé : blessé par un monstre ({0} s)");
		m.put("fly.stopped.no_guild", "&c✈ Vol coupé : vous n'êtes plus dans une guilde");
		m.put("fly.stopped.role", "&c✈ Vol coupé : rôle {0} minimum");
		m.put("fly.stopped.level", "&c✈ Vol coupé : niveau de guilde {0} requis");
		m.put("fly.stopped.permission", "&c✈ Vol coupé : permission retirée");
		m.put("fly.stopped.disabled", "&c✈ Vol coupé : vol de guilde désactivé sur le serveur");
		m.put("fly.horde.chat", "&cLa horde arrive : vol de guilde coupé jusqu'à la fin de l'attaque.");

		// Why flight is unavailable (menu, status, refusals).
		m.put("fly.reason.disabled", "le vol de guilde est désactivé sur ce serveur");
		m.put("fly.reason.game_mode", "sans effet en créatif et en spectateur");
		m.put("fly.reason.no_guild", "vous n'avez pas de guilde");
		m.put("fly.reason.role", "rôle {0} minimum");
		m.put("fly.reason.level", "niveau de guilde {0} requis");
		m.put("fly.reason.permission", "permission manquante (guildy.fly)");
		m.put("fly.reason.horde", "une horde attaque la guilde");
		m.put("fly.reason.outside", "hors du territoire de la guilde");
		m.put("fly.reason.combat", "blessé par un monstre (encore {0} s)");

		// Territory menu button.
		m.put("menu.territory.fly.name.on", "&b&lVol de guilde : &a&lactivé");
		m.put("menu.territory.fly.name.off", "&b&lVol de guilde : &7&ldésactivé");
		m.put("menu.territory.fly.lore", "&7Volez dans le territoire de la guilde\n&7(sautez deux fois pour décoller).\n"
				+ "&7Jamais pendant une horde ; si le vol\n&7s'arrête en l'air, la chute est sans danger.");
		m.put("menu.territory.fly.state.ok", "&aEn ce moment : vol possible ici");
		m.put("menu.territory.fly.state.blocked", "&cEn ce moment : {0}");
		m.put("menu.territory.fly.click.on", "&eClic : activer");
		m.put("menu.territory.fly.click.off", "&eClic : désactiver");

		// /guild help
		m.put("guild.help.fly", "&b✈ /guild fly &7: voler dans le territoire de votre guilde (jamais pendant une horde).");
		m.put("guild.help.fly.hover", "&7Activer / désactiver le vol de guilde");
		Lang.addDefaults(m);
	}
}
