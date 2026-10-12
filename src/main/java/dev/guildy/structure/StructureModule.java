package dev.guildy.structure;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.Hooks;

/**
 * Data-driven multiblock structures (detection, managed signs, integrity).
 */
public final class StructureModule {
	private StructureModule() {
	}

	public static void init() {
		ConfigHandle<StructureConfig> config = ConfigManager.register("structures.json5", StructureConfig.class, StructureConfig::new);
		StructureLang.register();
		StructureManager.init(config);
		StructureCommands.register();
		StructureBehavior.register(VillageSignBehavior.ID, new VillageSignBehavior());
		Hooks.openStructures = StructureMenus::openList;
	}
}
