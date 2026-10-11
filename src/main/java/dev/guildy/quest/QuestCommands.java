package dev.guildy.quest;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.structure.GuildArgument;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /guild quests} and {@code /guild admin quest reroll|complete}.
 */
final class QuestCommands {
	private QuestCommands() {
	}

	static void register() {
		GuildCommand.addSubcommands((node, context) -> node.then(Commands.literal("quests").executes(ctx -> {
			ServerPlayer player = GuildCommand.player(ctx);
			Guild guild = GuildManager.of(player);
			if (guild == null) {
				Msg.send(player, "error.no_guild");
				return 0;
			}
			Hooks.openQuests.accept(player, guild);
			return 1;
		})));

		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("quest")
				.then(Commands.literal("reroll")
						.then(GuildArgument.guild("guild").executes(ctx -> {
							Guild guild = GuildArgument.resolve(ctx, "guild");
							if (guild == null) {
								return 0;
							}
							int removed = QuestManager.reroll(guild);
							boolean board = QuestManager.hasBoard(guild);
							ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get(board ? "quest.admin.rerolled" : "quest.admin.rerolled_noboard",
									guild.name, removed)), true);
							return 1;
						})))
				.then(Commands.literal("complete")
						.then(GuildArgument.guild("guild")
								.then(Commands.argument("slot", IntegerArgumentType.integer(1, GuildQuestData.SLOTS)).executes(ctx -> {
									Guild guild = GuildArgument.resolve(ctx, "guild");
									if (guild == null) {
										return 0;
									}
									int slot = IntegerArgumentType.getInteger(ctx, "slot");
									if (!QuestManager.forceComplete(guild, slot - 1)) {
										ctx.getSource().sendFailure(Msg.prefixed(Lang.get("quest.admin.no_quest", slot, guild.name)));
										return 0;
									}
									ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("quest.admin.completed", slot, guild.name)), true);
									return 1;
								}))))
				.then(Commands.literal("map")
						.then(GuildArgument.guild("guild").executes(ctx -> {
							ServerPlayer player = GuildCommand.player(ctx);
							Guild guild = GuildArgument.resolve(ctx, "guild");
							if (guild == null) {
								return 0;
							}
							TreasureMaps.giveMap(player, guild);
							return 1;
						})))));
	}
}
