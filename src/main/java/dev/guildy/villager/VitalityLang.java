package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the villagers' survival features: health line of the menus, statuses of the evening / night
 * shelter ("villager.status.night.*") and the base equipment of the guards ("villager.gui.guard.gear.*").
 */
final class VitalityLang {
	private VitalityLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// health
		m.put("villager.gui.lore.health", "&7Vie : &c{0}/{1} ❤");
		// evening and night
		m.put("villager.status.night.going_home", "&9Rentre se coucher ({0} {1} {2})");
		m.put("villager.status.night.home", "&9À l'abri, attend la nuit");
		m.put("villager.status.night.sheltered", "&9Mis à l'abri pour la nuit");
		m.put("villager.status.night.no_shelter", "&cAucun abri pour la nuit (ni lit, ni bureau d'accueil)");
		m.put("villager.status.night.unloaded", "&eLoin de son lit (zone non chargée)");
		// guards
		m.put("villager.status.guard.outclassed", "&7Rapporte au stockage l'équipement que son équipement de base surpasse");
		m.put("villager.gui.guard.gear", "&7Équipement de base (niveau de guilde &f{0}&7) :");
		m.put("villager.gui.guard.gear.sword", "&8· &7Épée : ");
		m.put("villager.gui.guard.gear.ranged", "&8· &7Tir : ");
		m.put("villager.gui.guard.gear.armor", "&8· &7Armure : ");
		m.put("villager.gui.guard.gear.no_armor", "&8aucune");
		m.put("villager.gui.guard.gear.next", "&8Meilleur équipement de base au niveau {0}.");
		m.put("villager.gui.guard.gear.none", "&8Pas d'équipement de base à ce niveau de guilde.");
		m.put("villager.gui.guard.gear.off", "&8(équipement de base désactivé sur ce serveur)");
		Lang.addDefaults(m);
	}
}
