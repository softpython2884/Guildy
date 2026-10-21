package dev.guildy.alchemy;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /guild admin elixir list | give <player> <id> [count] | boosts <player> [clear]} (the admin node is already
 * permission-gated).
 */
final class AlchemyCommands {
	private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.elixirs", AlchemyCommands::ids, List.of()), builder);

	private AlchemyCommands() {
	}

	static void register() {
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("elixir")
				.then(Commands.literal("list").executes(AlchemyCommands::list))
				.then(Commands.literal("give")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
										.executes(ctx -> give(ctx, 1))
										.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
												.executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
				.then(Commands.literal("boosts")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> boosts(ctx, false))
								.then(Commands.literal("clear").executes(ctx -> boosts(ctx, true)))))));
	}

	private static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (AlchemyConfig.ElixirDef def : AlchemyModule.config().elixirs) {
			ids.add(def.id);
		}
		return ids;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		AlchemyConfig config = AlchemyModule.config();
		ctx.getSource().sendSuccess(() -> Lang.get("alchemy.admin.list.header", config.elixirs.size()), false);
		for (AlchemyConfig.ElixirDef def : config.elixirs) {
			String disabled = def.enabled ? "" : Lang.raw("alchemy.admin.list.disabled");
			ctx.getSource().sendSuccess(() -> Lang.get("alchemy.admin.list.line", def.id, Lang.colorize(def.name), def.base, def.reagent, disabled),
					false);
		}
		return config.elixirs.size();
	}

	private static int give(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
		AlchemyConfig.ElixirDef def = AlchemyModule.config().elixir(id);
		if (def == null) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("alchemy.admin.unknown", id)));
			return 0;
		}
		for (int i = 0; i < count; i++) {
			ItemStack stack = Safe.get("alchemy.admin.give", () -> Elixirs.create(def), ItemStack.EMPTY);
			if (!stack.isEmpty()) {
				ItemUtil.giveOrDrop(target, stack);
			}
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("alchemy.admin.given", count, Lang.colorize(def.name),
				target.getName().getString())), true);
		return count;
	}

	private static int boosts(CommandContext<CommandSourceStack> ctx, boolean clear) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		String name = target.getName().getString();
		if (clear) {
			MagicBoosts.clear(target);
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("alchemy.admin.boosts.cleared", name)), true);
			return 1;
		}
		String text = MagicBoosts.describe(target);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(text == null ? Lang.get("alchemy.admin.boosts.none", name)
				: Lang.get("alchemy.admin.boosts", name, text)), false);
		return text == null ? 0 : 1;
	}
}
