package dev.guildy.ruin;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.GuildEvents;
import dev.guildy.structure.StructureBehavior;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Ruins: generated ruins guarded by bosses (Overworld and Nether), indexed from their data markers, awakened by an
 * incantation ritual, and the cartographer's research that locates them. Config: config/guildy/ruins.json5; data:
 * {@code <world>/guildy/ruins.json}. Entry points: {@link RuinIndex} (known ruins), {@link Incantations} (incantation
 * scrolls and rituals), {@link Encounters} (fights), {@link Cartography} (research), {@link RuinCommands}
 * ({@code /guild admin ruin ...}).
 */
public final class RuinModule {
	private static final RuinConfig DEFAULTS = new RuinConfig();
	private static final int AUTOSAVE_TICKS = 5 * 60 * 20;
	private static ConfigHandle<RuinConfig> config;
	private static long lastSave;

	private RuinModule() {
	}

	public static void init() {
		config = ConfigManager.register("ruins.json5", RuinConfig.class, RuinConfig::new);
		RuinLang.register();
		CursedLang.register();
		DreadLang.register();
		RuinFightStats.init();
		Encounters.init();
		Incantations.init();
		RoomGuards.init();
		Sanctuaries.init();
		Statues.init();
		CursedFights.init();
		DreadFights.init();
		Felines.init();
		RuinCommands.register();
		CursedCommands.register();
		StructureBehavior.register(Cartography.STRUCTURE_ID, new CartographerBehavior());

		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> Safe.run("ruin.chunk", () -> RuinIndex.onChunkLoad(level, chunk)));
		Scheduler.every("ruin.index", 1, 0, () -> {
			MinecraftServer server = GuildyServer.server();
			if (server != null) {
				RuinIndex.processPending(server);
				RuinFx.tick(server);
			}
		});
		Scheduler.every("ruin.autosave", 200, 17, () -> {
			MinecraftServer server = GuildyServer.server();
			if (server != null && Scheduler.ticks() - lastSave >= AUTOSAVE_TICKS) {
				lastSave = Scheduler.ticks();
				RuinIndex.save(server, false);
			}
		});
		GuildEvents.DISBANDED.add(guild -> Safe.run("ruin.disband", () -> Cartography.onGuildRemoved(guild.id)));
		Lifecycle.STARTED.add(server -> {
			RuinIndex.load(server);
			lastSave = Scheduler.ticks();
		});
		Lifecycle.STOPPING.add(server -> {
			Encounters.stopAll();
			RuinIndex.processPending(server);
			RuinIndex.save(server, true);
		});
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> Safe.run("ruin.save", () -> RuinIndex.save(server, false)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RuinIndex.clear();
			Cartography.clear();
		});
	}

	public static RuinConfig config() {
		return config != null ? config.get() : DEFAULTS;
	}
}
