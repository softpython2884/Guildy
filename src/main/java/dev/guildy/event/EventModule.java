package dev.guildy.event;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.Safe;
import dev.guildy.wave.NightModifiers;
import dev.guildy.wave.WaveApi;

import java.util.List;

/**
 * Random events: blood night (server-wide), pillager raid, merchant caravan and book merchant (per guild).
 * Config: config/guildy/events.json5. Hooks into the wave module through {@link WaveApi} (night modifiers, event waves,
 * menu lines) and into the trade module for the visiting merchants. Must be initialized after both.
 */
public final class EventModule {
	private static final EventConfig DEFAULTS = new EventConfig();
	private static ConfigHandle<EventConfig> config;

	private EventModule() {
	}

	public static void init() {
		config = ConfigManager.register("events.json5", EventConfig.class, EventConfig::new);
		EventLang.register();
		EventManager.init();
		EventCommands.register();
		WaveApi.nightModifiers = (guild, clock) -> Safe.get("event.modifiers", () -> EventManager.nightModifiers(guild, clock), NightModifiers.NONE);
		WaveApi.eventLore = guild -> Safe.get("event.lore", () -> EventManager.lore(guild), List.of());
	}

	public static EventConfig config() {
		return config != null ? config.get() : DEFAULTS;
	}
}
