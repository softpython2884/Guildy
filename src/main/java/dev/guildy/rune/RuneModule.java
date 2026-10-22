package dev.guildy.rune;

import dev.guildy.Guildy;
import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Safe;
import org.jspecify.annotations.Nullable;

/**
 * Runes inscribed by the blacksmith villagers on the armor of the guild's armor stands (runes.json5): stat buffs, small
 * powers, two mythic powers for netherite chestplates; removed at the grindstone (rune.mixin). The blacksmith task calls
 * {@link RuneForge}.
 */
public final class RuneModule {
	private static final RuneConfig DEFAULTS = defaults();
	private static @Nullable ConfigHandle<RuneConfig> config;

	private RuneModule() {
	}

	public static void init() {
		config = ConfigManager.register("runes.json5", RuneConfig.class, RuneConfig::new);
		RuneLang.register();
		RunePowers.register();
		RuneCommands.register();
		// Attribute ids are checked once the registries are ready.
		Lifecycle.STARTED.add(server -> Safe.run("rune.check", RuneModule::warnUnknown));
		Lifecycle.RELOADED.add(() -> Safe.run("rune.check", RuneModule::warnUnknown));
		Lifecycle.STOPPING.add(server -> RunePowers.clear());
	}

	/** Loaded config (validated defaults before the first load / in unit tests). */
	public static RuneConfig config() {
		ConfigHandle<RuneConfig> handle = config;
		return handle != null ? handle.get() : DEFAULTS;
	}

	private static RuneConfig defaults() {
		RuneConfig cfg = new RuneConfig();
		cfg.clean();
		return cfg;
	}

	/** Unknown attribute ids of the runes (logged once per load). */
	private static void warnUnknown() {
		for (RuneConfig.RuneDef def : config().runes) {
			for (RuneConfig.AttributeDef attribute : def.attributes) {
				if (RuneItems.attribute(attribute.attributeId()).isEmpty()) {
					Guildy.LOGGER.warn("[runes.json5] runes.{}: unknown attribute '{}' (skipped when inscribed)", def.id, attribute.attribute);
				}
			}
		}
		String material = config().inscription.materialId();
		if (!material.isEmpty() && RuneForge.material() == null) {
			Guildy.LOGGER.warn("[runes.json5] inscription.material: unknown item '{}': runes are free", material);
		}
	}
}
