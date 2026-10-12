package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the planting done by lumberjacks (saplings) and farmers (sowing, tilling): statuses, idle notes,
 * item names used in the statuses and stats lines.
 */
final class PlantingLang {
	private PlantingLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// lumberjack
		m.put("villager.plant.searching_forest", "&7Cherche où planter {0} &8({1}/{2})");
		m.put("villager.plant.going", "&eVa planter {0} &8({1}/{2})");
		m.put("villager.plant.planting", "&aPlante {0} ({1}/{2})");
		m.put("villager.plant.fetch_saplings", "&eVa chercher des pousses au stockage");
		m.put("villager.plant.fetch_bone_meal", "&eVa chercher de la poudre d'os au stockage");
		// farmer
		m.put("villager.plant.searching_field", "&7Cherche des terres à semer &8({0}/{1})");
		m.put("villager.plant.going_sow", "&eVa semer {0} &8({1}/{2})");
		m.put("villager.plant.going_till", "&eVa labourer pour semer {0} &8({1}/{2})");
		m.put("villager.plant.sowing", "&aSème {0} ({1}/{2})");
		m.put("villager.plant.tilling", "&aLaboure et sème {0} ({1}/{2})");
		m.put("villager.plant.fetch_seeds", "&eVa chercher des graines au stockage");
		// idle notes (after the task's own idle status)
		m.put("villager.plant.idle", "{0} &8· {1}");
		m.put("villager.plant.note.no_saplings", "plus de pousses à planter (sac et stockage vides)");
		m.put("villager.plant.note.no_room_forest", "aucune place libre pour une pousse");
		m.put("villager.plant.note.no_seeds", "plus de graines à semer (sac et stockage vides)");
		m.put("villager.plant.note.no_room_field", "aucune terre à semer");
		// names in the statuses
		m.put("villager.plant.name.saplings", "des pousses");
		m.put("villager.plant.name.seeds", "des graines");
		m.put("villager.plant.name.oak_sapling", "des pousses de chêne");
		m.put("villager.plant.name.spruce_sapling", "des pousses de sapin");
		m.put("villager.plant.name.birch_sapling", "des pousses de bouleau");
		m.put("villager.plant.name.jungle_sapling", "des pousses d'acajou");
		m.put("villager.plant.name.acacia_sapling", "des pousses d'acacia");
		m.put("villager.plant.name.dark_oak_sapling", "des pousses de chêne noir");
		m.put("villager.plant.name.pale_oak_sapling", "des pousses de chêne pâle");
		m.put("villager.plant.name.cherry_sapling", "des pousses de cerisier");
		m.put("villager.plant.name.poplar_sapling", "des pousses de peuplier");
		m.put("villager.plant.name.mangrove_propagule", "des propagules de palétuvier");
		m.put("villager.plant.name.azalea", "des azalées");
		m.put("villager.plant.name.flowering_azalea", "des azalées fleuries");
		m.put("villager.plant.name.wheat_seeds", "du blé");
		m.put("villager.plant.name.carrot", "des carottes");
		m.put("villager.plant.name.potato", "des pommes de terre");
		m.put("villager.plant.name.beetroot_seeds", "des betteraves");
		// stats
		m.put("villager.gui.lore.stat.planted", "&8Pousses plantées : {0}");
		m.put("villager.gui.lore.stat.sown", "&8Semis : {0}");
		Lang.addDefaults(m);
	}
}
