package dev.guildy.alchemy;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the alchemy module: elixir lore, magic boost messages and bar, French potion names for the villager
 * statuses (the server only knows the English ones), admin commands.
 */
final class AlchemyLang {
	private AlchemyLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// elixir items
		m.put("alchemy.elixir.lore.story", "&7{0}");
		m.put("alchemy.elixir.lore.power", "&d✦ Sorts +{0} % &8(dégâts, soins, durées)");
		m.put("alchemy.elixir.lore.cooldown", "&b✦ Recharges des sorts -{0} %");
		m.put("alchemy.elixir.lore.channel", "&b✦ Incantations -{0} %");
		m.put("alchemy.elixir.lore.food", "&a✦ Faim du grimoire -{0} %");
		m.put("alchemy.elixir.lore.duration", "&8Bonus magique pendant {0} (à boire)");
		m.put("alchemy.elixir.lore.footer", "&8Élixir de Guildy · préparé par un alchimiste");
		// magic boosts
		m.put("alchemy.boost.start", "&d{0}&7 fait effet : &f{1}&7 de magie renforcée.");
		m.put("alchemy.boost.end", "&8L'effet de {0}&8 se dissipe.");
		m.put("alchemy.boost.bar", "{0} &f{1}");
		// potion names (statuses): "Potion de soin", "Potion jetable de force II"
		m.put("alchemy.form.potion", "Potion {0}");
		m.put("alchemy.form.splash", "Potion jetable {0}");
		m.put("alchemy.form.lingering", "Potion persistante {0}");
		m.put("alchemy.form.elixir", "Élixir {0}");
		m.put("alchemy.potion.strong", " II");
		m.put("alchemy.potion.long", " (longue)");
		m.put("alchemy.potion.water", "d'eau");
		m.put("alchemy.potion.awkward", "étrange");
		m.put("alchemy.potion.mundane", "banale");
		m.put("alchemy.potion.thick", "épaisse");
		m.put("alchemy.potion.healing", "de soin");
		m.put("alchemy.potion.harming", "de dégâts");
		m.put("alchemy.potion.poison", "de poison");
		m.put("alchemy.potion.regeneration", "de régénération");
		m.put("alchemy.potion.strength", "de force");
		m.put("alchemy.potion.weakness", "de faiblesse");
		m.put("alchemy.potion.swiftness", "de rapidité");
		m.put("alchemy.potion.slowness", "de lenteur");
		m.put("alchemy.potion.leaping", "de saut");
		m.put("alchemy.potion.fire_resistance", "de résistance au feu");
		m.put("alchemy.potion.night_vision", "de vision nocturne");
		m.put("alchemy.potion.invisibility", "d'invisibilité");
		m.put("alchemy.potion.water_breathing", "de respiration aquatique");
		m.put("alchemy.potion.turtle_master", "du maître tortue");
		m.put("alchemy.potion.slow_falling", "de chute lente");
		m.put("alchemy.potion.luck", "de chance");
		m.put("alchemy.potion.wind_charged", "de charge de vent");
		m.put("alchemy.potion.weaving", "de tissage");
		m.put("alchemy.potion.oozing", "de suintement");
		m.put("alchemy.potion.infested", "d'infestation");
		// admin
		m.put("alchemy.admin.list.header", "&6Élixirs ({0}) :");
		m.put("alchemy.admin.list.line", "&e{0} &7- {1} &8(base {2} + {3}){4}");
		m.put("alchemy.admin.list.disabled", " &c[désactivé]");
		m.put("alchemy.admin.given", "&a{0} x {1} donné(s) à {2}.");
		m.put("alchemy.admin.unknown", "&cÉlixir inconnu : {0}");
		m.put("alchemy.admin.boosts", "&7Bonus magiques de {0} : &f{1}");
		m.put("alchemy.admin.boosts.none", "&7{0} n'a aucun bonus magique.");
		m.put("alchemy.admin.boosts.cleared", "&aBonus magiques de {0} retirés.");
		Lang.addDefaults(m);
	}
}
