package dev.guildy.unique;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /guild admin unique list | give <player> <id>} (the admin node is already permission-gated).
 */
final class UniqueCommands {
	private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.uniques", UniqueCommands::ids, List.of()), builder);

	private UniqueCommands() {
	}

	static void register() {
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("unique")
				.then(Commands.literal("list").executes(UniqueCommands::list))
				.then(Commands.literal("give")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
										.executes(UniqueCommands::give))))));
	}

	private static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (UniqueConfig.UniqueDef def : UniqueModule.config().usable()) {
			ids.add(def.id);
		}
		return ids;
	}

	private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
		UniqueConfig.UniqueDef def = UniqueModule.config().item(id);
		ItemStack stack = def == null ? ItemStack.EMPTY
				: Safe.get("unique.admin.give", () -> UniqueItems.create(def, GuildyServer.registryAccess()), ItemStack.EMPTY);
		if (stack.isEmpty()) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("unique.admin.unknown", id)));
			return 0;
		}
		Component name = stack.getDisplayName();
		ItemUtil.giveOrDrop(target, stack);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(TextUtil.lang("unique.admin.give", name, target.getName().getString())), true);
		return 1;
	}

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
			String powerName = power == null ? Lang.raw("unique.admin.list.no_power") : Lang.raw(power.langKey() + ".name");
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
		return parts.isEmpty() ? Lang.raw("unique.admin.list.no_drop") : String.join(", ", parts);
	}

	private static String percent(double chance) {
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
