package dev.guildy;

import dev.guildy.claim.ClaimModule;
import dev.guildy.command.GuildCommand;
import dev.guildy.command.GuildCommands;
import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.CoreLang;
import dev.guildy.core.FlightGrants;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.GuildManager;
import dev.guildy.event.EventModule;
import dev.guildy.fly.FlyModule;
import dev.guildy.gui.GuildMenus;
import dev.guildy.level.LevelLang;
import dev.guildy.quest.QuestModule;
import dev.guildy.scroll.ScrollModule;
import dev.guildy.ruin.RuinModule;
import dev.guildy.shop.ShopModule;
import dev.guildy.structure.StructureModule;
import dev.guildy.trade.TradeModule;
import dev.guildy.unique.UniqueModule;
import dev.guildy.upgrade.UpgradeModule;
import dev.guildy.villager.VillagerModule;
import dev.guildy.wave.WaveModule;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class Guildy implements ModInitializer {
	public static final String MOD_ID = "guildy";
	public static final Logger LOGGER = LoggerFactory.getLogger("Guildy");

	private static long lastAutosaveTick;

	@Override
	public void onInitialize() {
		ConfigHandle<GuildyConfig> config = ConfigManager.register("config.json5", GuildyConfig.class, GuildyConfig::new);
		GuildyServer.setConfigHandle(config);
		CoreLang.register();
		LevelLang.register();
		FlightGrants.init(); // shared owner of the survival flight (guild fly, double jump): before the modules' login hooks

		// Feature modules register their configs, lang defaults, events, commands and hooks here.
		ClaimModule.init();
		GuildCommands.init();
		GuildMenus.init();
		WaveModule.init();
		StructureModule.init();
		QuestModule.init();
		ShopModule.init();
		VillagerModule.init();
		UpgradeModule.init();
		TradeModule.init();
		EventModule.init(); // after the wave and trade modules (hooks into both)
		FlyModule.init(); // /guild fly: flight inside the own territory, never during a horde
		UniqueModule.init(); // unique weapons: implements the core UniqueDrops hook
		ScrollModule.init(); // spell scrolls: implements the core ScrollDrops hook
		RuinModule.init(); // ruins guarded by bosses + cartographer (uses the structure and villager modules)

		List<String> errors = ConfigManager.loadAll();
		String langError = Lang.load();
		if (langError != null) {
			errors.add(langError);
		}
		if (!errors.isEmpty()) {
			LOGGER.error("Config errors (defaults used where needed): {}", errors);
		}

		CommandRegistrationCallback.EVENT.register(GuildCommand::register);

		ServerLifecycleEvents.SERVER_STARTING.register(server -> GuildyServer.setServer(server));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			GuildManager.load(server);
			lastAutosaveTick = 0;
			Lifecycle.fire(Lifecycle.STARTED, server, "started");
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Lifecycle.fire(Lifecycle.STOPPING, server, "stopping");
			GuildManager.save(server, true);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Scheduler.clearDelayed();
			GuildManager.clear();
			GuildyServer.setServer(null);
		});
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> Safe.run("save", () -> GuildManager.save(server, false)));

		ServerTickEvents.END_SERVER_TICK.register(Scheduler::onServerTick);
		Scheduler.every("autosave", 200, 0, () -> {
			long interval = GuildyServer.config().general.autosaveMinutes * 60L * 20L;
			if (Scheduler.ticks() - lastAutosaveTick >= interval) {
				lastAutosaveTick = Scheduler.ticks();
				var server = GuildyServer.server();
				if (server != null) {
					GuildManager.save(server, false);
				}
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				Safe.run("join", () -> GuildManager.refreshName(handler.getPlayer())));

		LOGGER.info("Guildy initialized");
	}
}
