package dev.guildy.villager;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.Hooks;
import dev.guildy.structure.StructureBehavior;

/**
 * Guild villagers: arrivals, acceptance, tasks, work zones (fanions) and patrol routes, storage sorting.
 */
public final class VillagerModule {
	private static ConfigHandle<VillagerConfig> config;

	private VillagerModule() {
	}

	public static void init() {
		config = ConfigManager.register("villagers.json5", VillagerConfig.class, VillagerConfig::new);
		VillagerLang.register();
		VillagerManager.init();
		VillagerInteractions.init();
		ZoneVisualizer.register();
		VillagerCommands.init();
		StructureBehavior.register(VillagerManager.RECEPTION, new ReceptionBehavior());
		Hooks.openVillagers = VillagerGui::openList;
	}

	public static VillagerConfig config() {
		return config.get();
	}
}
