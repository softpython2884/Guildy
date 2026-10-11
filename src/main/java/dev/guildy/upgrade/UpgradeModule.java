package dev.guildy.upgrade;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.Hooks;
import dev.guildy.core.Safe;
import org.jspecify.annotations.Nullable;

/**
 * Guild upgrade tree ("Arbre d'evolution"): permanent bonuses bought with treasury gold and materials.
 * Config: config/guildy/upgrades.json5. Bonus API for the other modules: {@link Upgrades}.
 */
public final class UpgradeModule {
	private static @Nullable ConfigHandle<UpgradeConfig> config;

	private UpgradeModule() {
	}

	public static void init() {
		config = ConfigManager.register("upgrades.json5", UpgradeConfig.class, UpgradeConfig::new);
		UpgradeLang.register();
		UpgradeBuffs.init();
		UpgradeCommands.register();
		Hooks.openUpgrades = (player, guild) -> Safe.run("upgrade.gui", () -> UpgradeMenu.open(player, guild));
	}

	/** Loaded config, or null before {@link #init()} (unit tests). */
	static @Nullable UpgradeConfig config() {
		return config == null ? null : config.get();
	}
}
