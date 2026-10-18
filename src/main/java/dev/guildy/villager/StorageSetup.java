package dev.guildy.villager;

import dev.guildy.Guildy;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Wiring of the guild storage (categories, container names, "Afficher les coffres"): lang, events and the chest
 * category migration of older guild data.
 */
final class StorageSetup {
	private StorageSetup() {
	}

	static void init() {
		StorageLang.register();
		TidyLang.register();
		FilterLang.register();
		StorageLabels.init();
		StorageVisualizer.register();
		ChestScan.init();
		StorageCommands.init();
		Lifecycle.STARTED.add(server -> {
			ItemClassifier.invalidate();
			StorageVisualizer.clear();
			Tidier.clear();
			ChestRooms.clear();
			ChestScan.clear();
			migrateChestCategories();
		});
		Lifecycle.RELOADED.add(() -> {
			ItemClassifier.invalidate();
			migrateChestCategories();
		});
		// Tags (and so categories) may change with the data packs.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> Safe.run("villager.storage.reload", ItemClassifier::invalidate));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Safe.run("villager.storage.disconnect",
				() -> {
					StorageVisualizer.forget(handler.getPlayer().getUUID());
					ChestScan.forget(handler.getPlayer().getUUID());
				}));
		GuildEvents.DISBANDED.add(guild -> Safe.run("villager.storage.disband", () -> {
			StorageLabels.onDisband(guild);
			Tidier.forget(guild);
			ChestRooms.forget(guild);
		}));
	}

	/**
	 * Chests of the older default categories ("Pierre & terre", "Outils & armes"...) take the name of the new
	 * equivalent category once those names are gone (villagers.json5 migrated); chests of other removed categories are
	 * left as they are and act as fallback chests (their category may come back).
	 */
	static void migrateChestCategories() {
		StorageCategories categories = Storage.categories();
		int renamed = 0;
		for (Guild guild : GuildManager.all()) {
			for (StorageChest chest : guild.villagers.chests) {
				if (chest.role != StorageChest.Role.CATEGORY || chest.isFree()) {
					continue;
				}
				String replacement = StorageDefaults.legacyRename(chest.category, categories);
				if (replacement != null) {
					chest.category = replacement;
					renamed++;
				}
			}
		}
		if (renamed > 0) {
			Guildy.LOGGER.info("Storage: {} chest(s) moved from the older default categories to the new ones", renamed);
			GuildManager.markDirty();
		}
	}
}
