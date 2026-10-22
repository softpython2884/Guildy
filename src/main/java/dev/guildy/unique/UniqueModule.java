package dev.guildy.unique;

import dev.guildy.Guildy;
import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.ScrollDrops;
import dev.guildy.core.UniqueDrops;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Unique weapons and tools ("armes uniques"): data-driven guildy enchantments (data/guildy/enchantment), unique items
 * defined in config/guildy/uniques.json5 (drop tables per source, Java powers with cooldowns), optional custom models
 * (Polymer or a hosted resource pack), the registry of the copies (serials, holders, uniqueness per guild or server, guild
 * collections) and the admin commands. Implements the core {@link UniqueDrops} hook used by the hordes, quests, merchants,
 * expeditions and ruins.
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
		SetBonuses.init();
		UniqueRegistry.init();
		UniqueDamage.init();
		UniqueTracker.init();
		UniqueDrops.install(UniqueModule::roll, UniqueModule::roll, UniqueModule::offer, UniqueModule::create);
		UniqueDrops.damageLeader = UniqueDamage::leader;
		Hooks.openUniques = (player, guild) -> Safe.run("unique.gui", () -> UniqueCollectionMenu.open(player, guild));
		Hooks.uniqueCollectionLore = UniqueCollectionMenu::summary;
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

	/** {@link UniqueDrops.Roller}: a roll without recipient (only "server" mode excludes weapons). */
	static ItemStack roll(String source, RandomGenerator random) {
		return roll(source, random, UniqueDrops.Recipient.NONE);
	}

	/**
	 * {@link UniqueDrops.RecipientRoller}: a unique of the source's table the recipient may receive, or a fallback scroll. A
	 * source may carry a context ({@code ruin_boss@grand_temple_maya,cursed}, see {@link UniqueDrops#withContext}): the
	 * favourite weapons of that context weigh more ({@link UniqueConfig#effectiveTable}); the registry, the announcement and
	 * the fallback scroll use the plain source.
	 */
	static ItemStack roll(String source, RandomGenerator random, UniqueDrops.Recipient recipient) {
		UniqueConfig cfg = config();
		UniqueConfig.DropTable table = cfg.effectiveTable(source);
		if (!cfg.dropsEnabled || table == null) {
			return ItemStack.EMPTY;
		}
		return rollWith(cfg, table, UniqueConfig.baseSource(source), random, recipient, cfg.mode(), cfg.fallbackToScroll);
	}

	/**
	 * A roll in a given table under a given uniqueness mode (public for the GameTests): the created copy is registered for
	 * the recipient and marked for the server announcement (table "announce"). When the chance passed but the recipient
	 * may receive none of the items, a spell scroll of the same source ({@code fallback}), else nothing.
	 */
	public static ItemStack rollWith(UniqueConfig cfg, UniqueConfig.DropTable table, String source, RandomGenerator random,
			UniqueDrops.Recipient recipient, UniquenessMode mode, boolean fallback) {
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return ItemStack.EMPTY;
		}
		String key = source.trim().toLowerCase(Locale.ROOT);
		Set<String> excluded = UniqueRegistry.excluded(mode, recipient);
		UniqueRolls.Outcome outcome = UniqueRolls.roll(table, id -> cfg.item(id) != null, excluded, random);
		if (outcome.kind() == UniqueRolls.Kind.EXHAUSTED) {
			Guildy.LOGGER.debug("[uniques] '{}' roll: every weapon already held ({} mode){}", key, mode.id(), fallback ? ", scroll instead" : "");
			// The weapon's chance already passed: the replacement scroll is guaranteed (no second chance roll).
			ItemStack scroll = fallback ? ScrollDrops.pick(key, random) : ItemStack.EMPTY;
			ServerPlayer player = recipient.player() == null ? null : GuildyServer.player(recipient.player());
			if (!scroll.isEmpty() && player != null) {
				Msg.send(player, "unique.fallback.scroll");
			}
			return scroll;
		}
		UniqueConfig.UniqueDef def = outcome.kind() == UniqueRolls.Kind.ITEM ? cfg.item(outcome.id()) : null;
		if (def == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = UniqueItems.create(def, server.registryAccess());
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		if (table.announce) {
			UniqueItems.markAnnounce(stack, key);
		}
		UniqueRegistry.issue(stack, def.id, key, recipient);
		return stack;
	}

	/**
	 * {@link UniqueDrops.Offerer}: the unique of a merchant offer, never one the recipient may not receive (held by its
	 * guild, or anywhere in "server" mode). Not registered: the stack keeps its source and gets its serial when a player
	 * carries it.
	 */
	static ItemStack offer(String source, @Nullable String id, RandomGenerator random, UniqueDrops.Recipient recipient) {
		UniqueConfig cfg = config();
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return ItemStack.EMPTY;
		}
		String key = UniqueConfig.baseSource(source);
		Set<String> excluded = UniqueRegistry.excluded(cfg.mode(), recipient);
		UniqueConfig.UniqueDef def;
		UniqueConfig.DropTable table = null;
		if (id == null || id.isBlank() || "random".equals(id)) {
			table = cfg.effectiveTable(source);
			if (!cfg.dropsEnabled || table == null) {
				return ItemStack.EMPTY;
			}
			UniqueRolls.Outcome outcome = UniqueRolls.roll(table, item -> cfg.item(item) != null, excluded, random);
			def = outcome.kind() == UniqueRolls.Kind.ITEM ? cfg.item(outcome.id()) : null;
		} else {
			def = cfg.item(id);
			if (def != null && excluded.contains(def.id)) {
				def = null;
			}
		}
		if (def == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = UniqueItems.create(def, server.registryAccess());
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		if (table != null && table.announce) {
			UniqueItems.markAnnounce(stack, key);
		}
		UniqueItems.markPending(stack, key);
		return stack;
	}

	/** {@link UniqueDrops.Factory}: the unique of this id (no announcement, registered when first seen), or empty. */
	static ItemStack create(String id) {
		UniqueConfig.UniqueDef def = config().item(id);
		MinecraftServer server = GuildyServer.server();
		return def == null || server == null ? ItemStack.EMPTY : UniqueItems.create(def, server.registryAccess());
	}
}
