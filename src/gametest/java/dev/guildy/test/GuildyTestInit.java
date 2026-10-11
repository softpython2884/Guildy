package dev.guildy.test;

import dev.guildy.data.GuildStorage;
import dev.guildy.event.EventModule;
import dev.guildy.trade.TradeModule;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The GameTest world is reused between runs: start every run with empty Guildy data. Random events and natural merchant
 * visits are switched off (scenarios force them with the admin commands): tests stay deterministic.
 */
public class GuildyTestInit implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Path dir = GuildStorage.dir(server);
			for (String file : new String[]{"guilds.json", "guilds.json.bak", "events.json"}) {
				try {
					Files.deleteIfExists(dir.resolve(file));
				} catch (IOException e) {
					throw new IllegalStateException("Cannot reset test guild data", e);
				}
			}
			EventModule.config().randomEvents = false;
			TradeModule.config().visits.enabled = false;
		});
	}
}
