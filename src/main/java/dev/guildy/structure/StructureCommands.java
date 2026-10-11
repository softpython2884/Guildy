package dev.guildy.structure;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /guild build} and {@code /guild admin structure ...}.
 */
final class StructureCommands {
	private StructureCommands() {
	}

	static void register() {
		GuildCommand.addSubcommands((node, context) -> node.then(Commands.literal("build").executes(ctx -> {
			ServerPlayer player = GuildCommand.player(ctx);
			Guild guild = GuildManager.of(player);
			if (guild == null) {
				Msg.send(player, "error.no_guild");
				return 0;
			}
			Hooks.openStructures.accept(player, guild);
			return 1;
		})));

		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("structure")
				.then(Commands.literal("unlock")
						.then(GuildArgument.guild("guild")
								.then(Commands.argument("id", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(typeIds(), builder))
										.executes(ctx -> {
											Guild guild = GuildArgument.resolve(ctx, "guild");
											if (guild == null) {
												return 0;
											}
											String id = StringArgumentType.getString(ctx, "id");
											StructureType type = StructureManager.type(id);
											if (type == null) {
												ctx.getSource().sendFailure(Msg.prefixed(Lang.get("structure.admin.unknown", id)));
												return 0;
											}
											boolean added = StructureManager.adminUnlock(guild, type);
											ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get(
													added ? "structure.admin.unlocked" : "structure.admin.already", type.plainName(), guild.name)), true);
											return 1;
										}))))
				.then(Commands.literal("check")
						.then(GuildArgument.guild("guild").executes(ctx -> {
							Guild guild = GuildArgument.resolve(ctx, "guild");
							if (guild == null) {
								return 0;
							}
							int before = guild.structures.built.size();
							var level = dev.guildy.core.GuildyServer.claimLevel();
							if (level != null) {
								StructureManager.checkGuild(level, guild);
							}
							int removed = before - guild.structures.built.size();
							ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("structure.admin.checked", guild.name,
									guild.structures.built.size(), removed)), false);
							return 1;
						})))));
	}

	private static List<String> typeIds() {
		List<String> ids = new ArrayList<>();
		for (StructureType type : StructureManager.types()) {
			ids.add(type.id());
		}
		return ids;
	}
}
