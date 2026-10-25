package dev.guildy.villager;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.Lifecycle;
import dev.guildy.data.Guild;

import java.util.function.Predicate;

/**
 * Supply of the construction sites by the builders (role "Chercher les matériaux", see {@link GatherWork}):
 * supply.json5, lang, and the shared state cleared when the server stops or the configs are reloaded.
 */
public final class Supply {
	private static final SupplyConfig DEFAULTS = new SupplyConfig();
	private static ConfigHandle<SupplyConfig> config;

	/**
	 * Whether the builders of a guild also take ingredients from every chest and barrel of its territory, not only from
	 * the registered storage (default: supply.json5, crafting.allContainers). The construction sites may plug their own
	 * per-guild choice in here.
	 */
	public static Predicate<Guild> allContainers = guild -> config().crafting.allContainers;

	private Supply() {
	}

	static void init() {
		config = ConfigManager.register("supply.json5", SupplyConfig.class, SupplyConfig::new);
		SupplyLang.register();
		Lifecycle.STOPPING.add(server -> {
			SupplyGuild.clear();
			SupplyBook.clear();
			Procurement.reset();
		});
		Lifecycle.RELOADED.add(() -> {
			SupplyBook.clear();
			SupplyGuild.invalidateAll();
		});
	}

	public static SupplyConfig config() {
		return config == null ? DEFAULTS : config.get();
	}
}
