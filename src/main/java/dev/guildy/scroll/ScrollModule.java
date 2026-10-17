package dev.guildy.scroll;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ScrollDrops;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.random.RandomGenerator;

/**
 * Spell scrolls ("parchemins de sort"): single-use vanilla items (custom data + components) defined in
 * config/guildy/scrolls.json5, cast with a vanilla channel animation, twelve spells with long animated particle shows (PvE
 * only, no block ever changed), drop tables per source and the admin commands. Implements the core {@link ScrollDrops} hook
 * used by the ruin bosses, the hordes, the quests, the merchants and the expeditions.
 */
public final class ScrollModule {
	private static final ScrollConfig DEFAULTS = defaults();
	private static @Nullable ConfigHandle<ScrollConfig> config;

	private ScrollModule() {
	}

	public static void init() {
		ConfigHandle<ScrollConfig> handle = ConfigManager.register("scrolls.json5", ScrollConfig.class, ScrollConfig::new);
		config = handle;
		handle.onReload(ScrollItems::clearSignatures);
		ScrollLang.register();
		ScrollCommands.register();
		ScrollCasting.register();
		EffectRunner.init();
		ScrollTracker.init();
		ScrollDrops.roller = ScrollModule::roll;
		ScrollDrops.factory = ScrollModule::create;
	}

	/** Loaded config (validated defaults before the first load / in unit tests). */
	public static ScrollConfig config() {
		ConfigHandle<ScrollConfig> handle = config;
		return handle != null ? handle.get() : DEFAULTS;
	}

	private static ScrollConfig defaults() {
		ScrollConfig cfg = new ScrollConfig();
		cfg.rebuild(id -> true);
		return cfg;
	}

	/** {@link ScrollDrops.Roller}: scrolls of the source's table, or empty. */
	static ItemStack roll(String source, RandomGenerator random) {
		ScrollConfig cfg = config();
		if (!cfg.dropsEnabled || GuildyServer.server() == null) {
			return ItemStack.EMPTY;
		}
		ScrollRolls.Drop drop = ScrollRolls.roll(cfg.table(source), id -> cfg.scroll(id) != null, random);
		ScrollConfig.ScrollDef def = drop == null ? null : cfg.scroll(drop.id());
		return def == null ? ItemStack.EMPTY : ScrollItems.create(def, drop.count());
	}

	/** {@link ScrollDrops.Factory}: scrolls of this id, or empty. */
	static ItemStack create(String id, int count) {
		ScrollConfig.ScrollDef def = config().scroll(id);
		return def == null ? ItemStack.EMPTY : ScrollItems.create(def, Math.max(1, count));
	}

	/**
	 * Gold price of a scroll stack sold by a trade hub merchant of this tier (scrolls.json5: price of its rarity, minimum hub
	 * tier); 0 when it must not be sold there (or the stack is no scroll).
	 */
	public static int tradePrice(ItemStack stack, int tier) {
		ScrollConfig.ScrollDef def = ScrollItems.defOf(stack);
		return def == null ? 0 : config().tradePrice(def.rarity(), tier) * Math.max(1, stack.getCount());
	}
}
