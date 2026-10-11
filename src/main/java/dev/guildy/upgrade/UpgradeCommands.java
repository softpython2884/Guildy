package dev.guildy.upgrade;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.structure.GuildArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * {@code /guild upgrades} (menu) and {@code /guild admin upgrade unlock|lock <guild> <id>} (guild names with spaces in quotes).
 */
final class UpgradeCommands {
	/** Every node id of the tree. */
	private static final SuggestionProvider<CommandSourceStack> NODE_IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.upgrades", UpgradeCommands::nodeIds, List.of()), builder);

	/** Node ids unlocked by the guild typed before (all ids when it is unknown). */
	private static final SuggestionProvider<CommandSourceStack> UNLOCKED_IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.upgrades", () -> unlockedIds(ctx), List.of()), builder);

	private UpgradeCommands() {
	}

	static void register() {
		GuildCommand.addSubcommands((root, context) -> root.then(Commands.literal("upgrades").executes(ctx -> {
			ServerPlayer player = GuildCommand.player(ctx);
			Guild guild = GuildManager.of(player);
			if (guild == null) {
				Msg.send(player, "error.no_guild");
				return 0;
			}
			Hooks.openUpgrades.accept(player, guild);
			return 1;
		})));

		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("upgrade")
				.then(Commands.literal("unlock")
						.then(GuildArgument.guild("guild")
								.then(Commands.argument("id", StringArgumentType.word()).suggests(NODE_IDS)
										.executes(ctx -> change(ctx, true)))))
				.then(Commands.literal("lock")
						.then(GuildArgument.guild("guild")
								.then(Commands.argument("id", StringArgumentType.word()).suggests(UNLOCKED_IDS)
										.executes(ctx -> change(ctx, false)))))));
	}

	private static int change(CommandContext<CommandSourceStack> ctx, boolean unlock) {
		Guild guild = GuildArgument.resolve(ctx, "guild");
		if (guild == null) {
			return 0;
		}
		String id = StringArgumentType.getString(ctx, "id");
		boolean known = Upgrades.tree().node(id) != null;
		if (!known && (unlock || !guild.upgrades.unlocked.contains(id))) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("upgrade.admin.unknown", id)));
			return 0;
		}
		BiFunction<Guild, String, List<String>> action = unlock ? UpgradeService::adminUnlock : UpgradeService::adminLock;
		List<String> changed = Safe.get("upgrade.admin", () -> action.apply(guild, id), List.of());
		if (changed.isEmpty()) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("upgrade.admin.nothing", guild.name)));
			return 0;
		}
		String key = unlock ? "upgrade.admin.unlocked" : "upgrade.admin.locked";
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get(key, guild.name, String.join(", ", changed))), true);
		return changed.size();
	}

	private static List<String> nodeIds() {
		List<String> ids = new ArrayList<>();
		for (UpgradeTree.Node node : Upgrades.tree().nodes()) {
			ids.add(node.id());
		}
		return ids;
	}

	private static List<String> unlockedIds(CommandContext<CommandSourceStack> ctx) {
		Guild guild;
		try {
			guild = GuildManager.byName(StringArgumentType.getString(ctx, "guild"));
		} catch (IllegalArgumentException e) {
			guild = null;
		}
		return guild == null ? nodeIds() : new ArrayList<>(guild.upgrades.unlocked);
	}
}
