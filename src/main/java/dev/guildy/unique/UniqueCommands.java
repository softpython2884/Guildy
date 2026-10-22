package dev.guildy.unique;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.structure.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /guild uniques} (collection of the guild) and {@code /guild admin unique list | give <player> <id> |
 * registry [id] | free <id> [guild]} (the admin node is already permission-gated).
 */
final class UniqueCommands {
	private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.uniques", UniqueCommands::ids, List.of()), builder);
	/** Configured ids and ids of the registry (a removed weapon may still have copies). */
	private static final SuggestionProvider<CommandSourceStack> KNOWN_IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.uniques", UniqueCommands::knownIds, List.of()), builder);
	private static final SuggestionProvider<CommandSourceStack> GUILDS = (ctx, builder) -> SharedSuggestionProvider.suggest(
			Safe.get("suggest.guilds", () -> GuildManager.all().stream().map(g -> g.name).toList(), List.of()), builder);
	private static final int MAX_LINES = 40;

	private UniqueCommands() {
	}

	static void register() {
		GuildCommand.addSubcommands((root, context) -> root.then(Commands.literal("uniques").executes(ctx -> {
			ServerPlayer player = GuildCommand.player(ctx);
			Guild guild = GuildManager.of(player);
			if (guild == null) {
				Msg.send(player, "error.no_guild");
				return 0;
			}
			Hooks.openUniques.accept(player, guild);
			return 1;
		})));
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("unique")
				.then(Commands.literal("list").executes(UniqueCommands::list))
				.then(Commands.literal("give")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
										.executes(UniqueCommands::give))))
				.then(Commands.literal("registry")
						.executes(ctx -> Safe.get("unique.admin.registry", () -> registry(ctx), 0))
						.then(Commands.argument("id", StringArgumentType.word()).suggests(KNOWN_IDS)
								.executes(ctx -> Safe.get("unique.admin.registry", () -> registryOf(ctx, StringArgumentType.getString(ctx, "id")), 0))))
				.then(Commands.literal("free")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(KNOWN_IDS)
								.executes(ctx -> free(ctx, null))
								.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GUILDS)
										.executes(ctx -> free(ctx, StringArgumentType.getString(ctx, "guild"))))))));
	}

	private static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (UniqueConfig.UniqueDef def : UniqueModule.config().usable()) {
			ids.add(def.id);
		}
		return ids;
	}

	private static List<String> knownIds() {
		Set<String> ids = new LinkedHashSet<>(ids());
		ids.addAll(UniqueRegistry.ledger().ids());
		return new ArrayList<>(ids);
	}

	// ================================================================== give

	private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
		UniqueConfig cfg = UniqueModule.config();
		UniqueConfig.UniqueDef def = cfg.item(id);
		ItemStack stack = def == null ? ItemStack.EMPTY
				: Safe.get("unique.admin.give", () -> UniqueItems.create(def, GuildyServer.registryAccess()), ItemStack.EMPTY);
		if (stack.isEmpty()) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("unique.admin.unknown", id)));
			return 0;
		}
		// Admin gifts bypass the uniqueness rule, but say so when they create a duplicate.
		UniqueDrops.Recipient recipient = UniqueDrops.Recipient.of(target);
		UniquenessMode mode = cfg.mode();
		boolean duplicate = mode != UniquenessMode.NONE && UniqueRegistry.excluded(mode, recipient).contains(def.id);
		UniqueRegistry.issue(stack, def.id, "admin", recipient);
		Component name = stack.getDisplayName();
		ItemUtil.giveOrDrop(target, stack);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(TextUtil.lang("unique.admin.give", name, target.getName().getString())), true);
		if (duplicate) {
			Guild guild = GuildManager.of(target);
			Component warning = mode == UniquenessMode.SERVER ? TextUtil.lang("unique.admin.give.duplicate_server", name)
					: TextUtil.lang("unique.admin.give.duplicate_guild", name, guild == null ? target.getName().getString() : guild.name);
			ctx.getSource().sendSuccess(() -> Msg.prefixed(warning), false);
		}
		return 1;
	}

	// ================================================================== registry

	/** Summary per weapon (click = its copies), then the duplicates. */
	private static int registry(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		UniqueConfig cfg = UniqueModule.config();
		UniqueLedger ledger = UniqueRegistry.ledger();
		int active = 0;
		for (UniqueLedger.Copy copy : ledger.copies()) {
			if (copy.active()) {
				active++;
			}
		}
		int total = ledger.copies().size();
		int activeCount = active;
		source.sendSuccess(() -> Lang.get("unique.admin.registry.header", Lang.raw("unique.mode." + cfg.mode().id()), total, activeCount), false);
		for (String id : knownIds()) {
			List<UniqueLedger.Copy> copies = ledger.copiesOf(id);
			int alive = 0;
			for (UniqueLedger.Copy copy : copies) {
				if (copy.active()) {
					alive++;
				}
			}
			int lost = copies.size() - alive;
			MutableComponent name = displayName(cfg, id).copy().withStyle(style -> style
					.withClickEvent(new ClickEvent.RunCommand("/guild admin unique registry " + id))
					.withHoverEvent(new HoverEvent.ShowText(Lang.get("unique.admin.registry.hover"))));
			Component line = TextUtil.lang("unique.admin.registry.entry", name, id, alive, lost);
			source.sendSuccess(() -> line, false);
		}
		List<UniqueLedger.Duplicate> duplicates = ledger.duplicates();
		if (!duplicates.isEmpty()) {
			source.sendSuccess(() -> Lang.get("unique.admin.registry.duplicates", duplicates.size()), false);
			long now = System.currentTimeMillis();
			int shown = 0;
			for (int i = duplicates.size() - 1; i >= 0 && shown < MAX_LINES / 2; i--, shown++) {
				UniqueLedger.Duplicate duplicate = duplicates.get(i);
				Component line = TextUtil.lang("unique.admin.registry.duplicate", duplicate.serial, displayName(cfg, duplicate.id), duplicate.first,
						duplicate.second, duplicate.count, UniqueCollectionMenu.ago(now, duplicate.lastSeen));
				source.sendSuccess(() -> line, false);
			}
		}
		return total;
	}

	/** Copies of one weapon: serial, state, holder, sightings (click an active copy = prepare the free command). */
	private static int registryOf(CommandContext<CommandSourceStack> ctx, String rawId) {
		CommandSourceStack source = ctx.getSource();
		String id = rawId.toLowerCase(Locale.ROOT);
		UniqueConfig cfg = UniqueModule.config();
		List<UniqueLedger.Copy> copies = UniqueRegistry.ledger().copiesOf(id);
		if (copies.isEmpty() && cfg.item(id) == null) {
			source.sendFailure(Msg.prefixed(Lang.get("unique.admin.unknown", id)));
			return 0;
		}
		Component name = displayName(cfg, id);
		source.sendSuccess(() -> TextUtil.lang("unique.admin.registry.copies", name, copies.size()), false);
		long now = System.currentTimeMillis();
		int from = Math.max(0, copies.size() - MAX_LINES);
		for (int i = from; i < copies.size(); i++) {
			UniqueLedger.Copy copy = copies.get(i);
			String sourceName = Lang.raw(UniqueCollectionMenu.sourceKey(copy.source));
			Component hover = Lang.get("unique.admin.registry.copy.hover", sourceName, UniqueCollectionMenu.ago(now, copy.issuedAt),
					copy.player == null ? "-" : copy.player.toString());
			MutableComponent line;
			if (copy.active()) {
				Guild guild = copy.guild == null ? null : GuildManager.get(copy.guild);
				String command = "/guild admin unique free " + id + (guild == null ? "" : " " + guild.name);
				line = Lang.get("unique.admin.registry.copy.active", copy.serial, UniqueRegistry.holder(copy), UniqueCollectionMenu.ago(now, copy.lastSeen),
						sourceName);
				Component freeHover = Component.empty().append(hover).append("\n").append(Lang.get("unique.admin.registry.copy.free_hint"));
				line.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(command)).withHoverEvent(new HoverEvent.ShowText(freeHover)));
			} else {
				line = Lang.get("unique.admin.registry.copy.lost", copy.serial, Lang.raw(lostKey(copy.lostReason)),
						UniqueCollectionMenu.ago(now, copy.lostAt), UniqueRegistry.holder(copy));
				line.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
			}
			Component out = line;
			source.sendSuccess(() -> out, false);
		}
		if (from > 0) {
			source.sendSuccess(() -> Lang.get("unique.admin.registry.more", from), false);
		}
		return copies.size();
	}

	private static String lostKey(String reason) {
		String key = "unique.lost." + (reason == null || reason.isEmpty() ? "unknown" : reason);
		return Lang.has(key) ? key : "unique.lost.unknown";
	}

	/** "Foudrelame" (colored) for a configured id, the raw id otherwise. */
	private static Component displayName(UniqueConfig cfg, String id) {
		UniqueConfig.UniqueDef def = cfg.item(id);
		return def == null ? Component.literal(id) : UniqueItems.name(def);
	}

	// ================================================================== free

	private static int free(CommandContext<CommandSourceStack> ctx, String guildName) {
		CommandSourceStack source = ctx.getSource();
		String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
		Guild guild = null;
		if (guildName != null) {
			guild = GuildManager.byName(guildName.trim());
			if (guild == null) {
				source.sendFailure(Msg.prefixed(Lang.get("unique.admin.free.no_guild", guildName)));
				return 0;
			}
		}
		Component name = displayName(UniqueModule.config(), id);
		List<UniqueLedger.Copy> freed = UniqueRegistry.ledger().free(id, guild == null ? null : guild.id, "admin", System.currentTimeMillis());
		if (freed.isEmpty()) {
			String where = guild == null ? "" : Lang.raw("unique.admin.free.of_guild", guild.name);
			source.sendFailure(Msg.prefixed(TextUtil.lang("unique.admin.free.none", name, where)));
			return 0;
		}
		List<String> serials = new ArrayList<>();
		for (UniqueLedger.Copy copy : freed) {
			serials.add("#" + copy.serial);
		}
		source.sendSuccess(() -> Msg.prefixed(TextUtil.lang("unique.admin.free.done", freed.size(), name, String.join(", ", serials))), true);
		return freed.size();
	}

	// ================================================================== list

	private static int list(CommandContext<CommandSourceStack> ctx) {
		return Safe.get("unique.admin.list", () -> listUniques(ctx), 0);
	}

	private static int listUniques(CommandContext<CommandSourceStack> ctx) {
		UniqueConfig cfg = UniqueModule.config();
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Lang.get("unique.admin.list.header", cfg.usable().size()), false);
		if (!cfg.dropsEnabled) {
			source.sendSuccess(() -> Lang.get("unique.admin.list.disabled"), false);
		}
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			UniquePower power = def.power();
			UniqueConfig.SetDef set = cfg.set(def.set);
			String powerName = power != null ? Lang.raw(power.langKey() + ".name")
					: set != null ? Lang.colorize(set.name) : Lang.raw("unique.admin.list.no_power");
			Component hover = Lang.get("unique.admin.list.hover", powerName, drops(cfg, def.id));
			MutableComponent name = UniqueItems.name(def).copy().withStyle(style -> style
					.withClickEvent(new ClickEvent.SuggestCommand("/guild admin unique give " + playerName(source) + " " + def.id))
					.withHoverEvent(new HoverEvent.ShowText(hover)));
			Component line = TextUtil.lang("unique.admin.list.entry", name, def.id, UniqueItems.rarityName(def.rarity()), def.item);
			source.sendSuccess(() -> line, false);
		}
		List<String> tables = new ArrayList<>();
		for (Map.Entry<String, UniqueConfig.DropTable> entry : cfg.drops.entrySet()) {
			tables.add(entry.getKey() + " " + percent(entry.getValue().chance) + " %");
		}
		source.sendSuccess(() -> Lang.get("unique.admin.list.tables", String.join(", ", tables)), false);
		String models = Lang.raw(UniqueVisuals.enabled() ? "unique.admin.models.on" : "unique.admin.models.off")
				+ (UniqueVisuals.polymerPresent() ? " (Polymer)" : "");
		source.sendSuccess(() -> Lang.get("unique.admin.list.models", models), false);
		source.sendSuccess(() -> Lang.get("unique.admin.list.mode", Lang.raw("unique.mode." + cfg.mode().id())), false);
		return cfg.usable().size();
	}

	/** "boss 0,4 %, blood_boss 2,5 %" (chance that one roll of the source gives this item). */
	private static String drops(UniqueConfig cfg, String id) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, UniqueConfig.DropTable> entry : cfg.drops.entrySet()) {
			double chance = UniqueRolls.probability(entry.getValue(), id, key -> cfg.item(key) != null);
			if (chance > 0) {
				parts.add(entry.getKey() + " " + percent(chance) + " %");
			}
		}
		for (Map.Entry<String, Map<String, Double>> entry : cfg.favorites.entrySet()) {
			if (entry.getValue().getOrDefault(id, 0.0) > 0) {
				double chance = UniqueRolls.probability(cfg.effectiveTable(entry.getKey()), id, key -> cfg.item(key) != null);
				parts.add(entry.getKey() + " " + percent(chance) + " %");
			}
		}
		return parts.isEmpty() ? Lang.raw("unique.admin.list.no_drop") : String.join(", ", parts);
	}

	static String percent(double chance) {
		double pct = chance * 100;
		if (pct >= 10) {
			return String.valueOf(Math.round(pct));
		}
		return String.format(Locale.FRANCE, pct >= 1 ? "%.1f" : "%.2f", pct);
	}

	private static String playerName(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player == null ? "<joueur>" : player.getName().getString();
	}
}
