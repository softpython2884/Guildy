package dev.guildy.unique;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.UniqueDrops;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.random.RandomGenerator;

/**
 * Unique weapons and tools ("armes uniques"): data-driven guildy enchantments (data/guildy/enchantment), unique items
 * defined in config/guildy/uniques.json5 (drop tables per source, Java powers with cooldowns), optional custom models
 * (Polymer or a hosted resource pack) and the admin commands. Implements the core {@link UniqueDrops} hook used by the
 * hordes, quests, merchants and expeditions.
 */
public final class UniqueModule {
	private static final UniqueConfig DEFAULTS = defaults();
	private static @Nullable ConfigHandle<UniqueConfig> config;

	private UniqueModule() {
	}

	public static void init() {
		ConfigHandle<UniqueConfig> handle = ConfigManager.register("uniques.json5", UniqueConfig.class, UniqueConfig::new);
		config = handle;
		handle.onReload(() -> UniqueVisuals.onConfigLoaded(handle.get()));
		UniqueLang.register();
		UniqueVisuals.init();
		UniqueCommands.register();
		UniquePowers.register();
		UniqueTracker.init();
		UniqueDrops.roller = UniqueModule::roll;
		UniqueDrops.factory = UniqueModule::create;
	}

	/** Loaded config (validated defaults before the first load / in unit tests). */
	public static UniqueConfig config() {
		ConfigHandle<UniqueConfig> handle = config;
		return handle != null ? handle.get() : DEFAULTS;
	}

	private static UniqueConfig defaults() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.rebuild(id -> true);
		return cfg;
	}

	/** {@link UniqueDrops.Roller}: a unique of the source's table (marked for the announcement), or empty. */
	static ItemStack roll(String source, RandomGenerator random) {
		UniqueConfig cfg = config();
		MinecraftServer server = GuildyServer.server();
		UniqueConfig.DropTable table = cfg.table(source);
		if (!cfg.dropsEnabled || server == null || table == null) {
			return ItemStack.EMPTY;
		}
		String id = UniqueRolls.roll(table, key -> cfg.item(key) != null, random);
		UniqueConfig.UniqueDef def = cfg.item(id);
		if (def == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = UniqueItems.create(def, server.registryAccess());
		if (!stack.isEmpty() && table.announce) {
			UniqueItems.markAnnounce(stack, source.trim().toLowerCase(java.util.Locale.ROOT));
		}
		return stack;
	}

	/** {@link UniqueDrops.Factory}: the unique of this id (no announcement), or empty. */
	static ItemStack create(String id) {
		UniqueConfig.UniqueDef def = config().item(id);
		MinecraftServer server = GuildyServer.server();
		return def == null || server == null ? ItemStack.EMPTY : UniqueItems.create(def, server.registryAccess());
	}
}
