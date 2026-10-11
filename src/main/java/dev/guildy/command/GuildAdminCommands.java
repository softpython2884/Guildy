package dev.guildy.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildRole;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * /guild admin setlevel | addxp | addgold | disband | info | forcejoin (the "admin" node is already permission-gated).
 * Guild names: quoted strings ("Les Loups") when followed by another argument, free text when last.
 */
final class GuildAdminCommands {
	private GuildAdminCommands() {
	}

	@FunctionalInterface
	private interface GuildCommandBody {
		boolean run(CommandSourceStack source, Guild guild) throws CommandSyntaxException;
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> admin, CommandBuildContext context) {
		admin.then(Commands.literal("setlevel")
				.then(Commands.argument("guild", StringArgumentType.string()).suggests(GuildArguments.ALL_GUILDS_QUOTED)
						.then(Commands.argument("level", IntegerArgumentType.integer(1))
								.executes(ctx -> withGuild(ctx, "admin.setlevel", (source, guild) -> {
									GuildManager.setLevel(guild, IntegerArgumentType.getInteger(ctx, "level"));
									success(source, "cmd.admin.setlevel", guild.name, guild.level);
									return true;
								})))));

		admin.then(Commands.literal("addxp")
				.then(Commands.argument("guild", StringArgumentType.string()).suggests(GuildArguments.ALL_GUILDS_QUOTED)
						.then(Commands.argument("amount", IntegerArgumentType.integer(1))
								.executes(ctx -> withGuild(ctx, "admin.addxp", (source, guild) -> {
									int amount = IntegerArgumentType.getInteger(ctx, "amount");
									GuildManager.addXp(guild, amount);
									success(source, "cmd.admin.addxp", guild.name, amount, guild.level, guild.xp);
									return true;
								})))));

		admin.then(Commands.literal("addgold")
				.then(Commands.argument("guild", StringArgumentType.string()).suggests(GuildArguments.ALL_GUILDS_QUOTED)
						.then(Commands.argument("amount", IntegerArgumentType.integer())
								.executes(ctx -> withGuild(ctx, "admin.addgold", (source, guild) -> {
									long amount = IntegerArgumentType.getInteger(ctx, "amount");
									long before = guild.treasury;
									if (amount >= 0) {
										GuildManager.addTreasury(guild, amount);
									} else {
										// Negative = remove, clamped at 0.
										GuildManager.takeTreasury(guild, before - GuildRules.clampTreasury(before, amount));
									}
									success(source, "cmd.admin.addgold", guild.name, before, guild.treasury);
									return true;
								})))));

		admin.then(Commands.literal("disband")
				.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GuildArguments.ALL_GUILDS)
						.executes(ctx -> withGuild(ctx, "admin.disband", (source, guild) -> {
							String name = guild.name;
							GuildActions.disbandNow(guild, "guild.disband.admin_notify", name);
							success(source, "cmd.admin.disband", name);
							return true;
						}))));

		admin.then(Commands.literal("info")
				.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GuildArguments.ALL_GUILDS)
						.executes(ctx -> withGuild(ctx, "admin.info", (source, guild) -> {
							GuildChat.info(source::sendSystemMessage, guild, true);
							return true;
						}))));

		admin.then(Commands.literal("forcejoin")
				.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GuildArguments.ALL_GUILDS)
								.executes(ctx -> withGuild(ctx, "admin.forcejoin", (source, guild) ->
										forceJoin(source, EntityArgument.getPlayer(ctx, "player"), guild))))));
	}

	/** Moves a player into a guild (ignores invitations and the member limit). */
	private static boolean forceJoin(CommandSourceStack source, ServerPlayer target, Guild guild) {
		String name = GuildActions.name(target);
		Guild current = GuildManager.of(target);
		if (current == guild) {
			failure(source, "cmd.admin.forcejoin.same", name, guild.name);
			return false;
		}
		if (current != null) {
			if (current.role(target.getUUID()) == GuildRole.LEADER) {
				failure(source, "cmd.admin.forcejoin.leader", name, current.name);
				return false;
			}
			GuildManager.removeMember(current, target.getUUID());
			GuildActions.notifyGuild(current, null, "cmd.admin.forcejoin.moved", name, current.name);
		}
		GuildManager.addMember(guild, target.getUUID(), name);
		GuildActions.notifyGuild(guild, target.getUUID(), "guild.join.notify", name);
		Msg.send(target, "cmd.admin.forcejoin.target", guild.name);
		success(source, "cmd.admin.forcejoin", name, guild.name);
		return true;
	}

	private static int withGuild(CommandContext<CommandSourceStack> ctx, String name, GuildCommandBody body) throws CommandSyntaxException {
		return GuildCommands.runSource(ctx, name, source -> {
			String input = StringArgumentType.getString(ctx, "guild");
			Guild guild = GuildArguments.guild(input);
			if (guild == null) {
				failure(source, "error.unknown_guild", input);
				return false;
			}
			return body.run(source, guild);
		});
	}

	private static void success(CommandSourceStack source, String key, Object... args) {
		source.sendSuccess(() -> Msg.prefixed(Lang.get(key, args)), true);
	}

	private static void failure(CommandSourceStack source, String key, Object... args) {
		source.sendFailure(Msg.prefixed(Lang.get(key, args)));
	}
}
