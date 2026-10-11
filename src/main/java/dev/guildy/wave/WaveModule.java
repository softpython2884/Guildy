package dev.guildy.wave;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.Hooks;
import dev.guildy.core.Safe;

import java.util.List;

/**
 * Night hordes: every night a themed horde attacks the base of each guild whose members are home.
 * Config: config/guildy/waves.json5. Entry points: {@link WaveManager} (runtime), {@link WaveMenu} (GUI),
 * {@link WaveCommands} (/guild waves, /guild admin wave ...).
 */
public final class WaveModule {
	private static final WaveConfig DEFAULTS = new WaveConfig();
	private static ConfigHandle<WaveConfig> config;

	private WaveModule() {
	}

	public static void init() {
		config = ConfigManager.register("waves.json5", WaveConfig.class, WaveConfig::new);
		WaveLang.register();
		WaveManager.init();
		WaveCommands.register();
		Hooks.isWaveActive = guild -> guild != null && WaveManager.isActive(guild);
		Hooks.waveStatusLore = guild -> Safe.get("wave.lore", () -> WaveManager.statusLore(guild), List.of());
		Hooks.openWaves = (player, guild) -> Safe.run("wave.gui", () -> WaveMenu.open(player, guild));
	}

	public static WaveConfig config() {
		return config != null ? config.get() : DEFAULTS;
	}
}
