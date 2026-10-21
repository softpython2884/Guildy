package dev.guildy.alchemy;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Safe;

/**
 * Alchemy: the potions and elixirs brewed by the alchemist villagers (alchemy.json5: brewing steps, default stocks,
 * elixirs) and the magic boosts of the elixirs (implements the core MagicBoost hook read by the spell scrolls).
 */
public final class AlchemyModule {
	private static ConfigHandle<AlchemyConfig> config;

	private AlchemyModule() {
	}

	public static void init() {
		config = ConfigManager.register("alchemy.json5", AlchemyConfig.class, AlchemyConfig::new);
		AlchemyLang.register();
		MagicBoosts.init();
		AlchemyCommands.register();
		// Item, potion and effect ids are checked once the registries are ready.
		Lifecycle.STARTED.add(server -> Safe.run("alchemy.check", Brews::warnUnknown));
		Lifecycle.RELOADED.add(() -> Safe.run("alchemy.check", Brews::warnUnknown));
	}

	public static AlchemyConfig config() {
		return config == null ? DEFAULTS : config.get();
	}

	/** Defaults before the module is initialized (unit tests of other modules). */
	private static final AlchemyConfig DEFAULTS = new AlchemyConfig();
}
