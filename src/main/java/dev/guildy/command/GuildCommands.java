package dev.guildy.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildRole;
import dev.guildy.gui.GuildMenus;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Player subcommands of /guild (create, invite, join, leave, kick, roles, disband, info, list, treasury, banner)
 * and the guild admin subcommands ({@link GuildAdminCommands}).
 */
public final class GuildCommands {
	private GuildCommands() {
	}

	public static void init() {
		CommandLang.register();
		GuildCommand.addSubcommands(GuildCommands::register);
		GuildCommand.addAdminSubcommands(GuildAdminCommands::register);
	}

	@FunctionalInterface
	interface PlayerCommand {
		boolean run(ServerPlayer player) throws CommandSyntaxException;
	}

	@FunctionalInterface
	interface SourceCommand {
		boolean run(CommandSourceStack source) throws CommandSyntaxException;
	}

	/** Runs a player-only subcommand: unexpected errors are logged and reported, never propagated. */
	static int run(CommandContext<CommandSourceStack> ctx, String name, PlayerCommand command) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		try {
			return command.run(player) ? 1 : 0;
		} catch (CommandSyntaxException e) {
			throw e;
		} catch (Throwable t) {
			Safe.log("cmd." + name, t);
			Msg.send(player, "error.internal");
			return 0;
		}
	}

	/** Runs a subcommand usable from the console too. */
	static int runSource(CommandContext<CommandSourceStack> ctx, String name, SourceCommand command) throws CommandSyntaxException {
		try {
			return command.run(ctx.getSource()) ? 1 : 0;
		} catch (CommandSyntaxException e) {
			throw e;
		} catch (Throwable t) {
			Safe.log("cmd." + name, t);
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("error.internal")));
			return 0;
		}
	}

	private static void register(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
		root.then(Commands.literal("menu").executes(ctx -> run(ctx, "menu", player -> {
			GuildMenus.open(player);
			return true;
		})));
		root.then(Commands.literal("help").executes(ctx -> run(ctx, "help", player -> {
			GuildChat.help(player);
			return true;
		})));

		root.then(Commands.literal("create")
				.then(Commands.argument("name", StringArgumentType.greedyString())
						.executes(ctx -> run(ctx, "create", player ->
								GuildActions.create(player, StringArgumentType.getString(ctx, "name"))))));

		root.then(Commands.literal("invite")
				.then(Commands.argument("player", StringArgumentType.word()).suggests(GuildArguments.INVITABLE_PLAYERS)
						.executes(ctx -> run(ctx, "invite", player -> {
							String targetName = StringArgumentType.getString(ctx, "player");
							ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
							if (target == null) {
								Msg.send(player, "guild.player.unknown", targetName);
								return false;
							}
							return GuildActions.invite(player, target);
						}))));

		for (String literal : List.of("join", "accept")) {
			root.then(Commands.literal(literal)
					.executes(ctx -> run(ctx, "join", player -> GuildActions.join(player, null)))
					.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GuildArguments.INVITING_GUILDS)
							.executes(ctx -> run(ctx, "join", player ->
									withGuild(player, ctx, guild -> GuildActions.join(player, guild))))));
		}
		root.then(Commands.literal("deny")
				.executes(ctx -> run(ctx, "deny", player -> GuildActions.deny(player, null)))
				.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GuildArguments.INVITING_GUILDS)
						.executes(ctx -> run(ctx, "deny", player ->
								withGuild(player, ctx, guild -> GuildActions.deny(player, guild))))));

		root.then(Commands.literal("leave")
				.executes(ctx -> run(ctx, "leave", player -> {
					// A lone leader leaving disbands the guild: ask first.
					if (GuildActions.leaveDisbands(player) && GuildActions.leaveDenial(player) == null) {
						GuildActions.confirmLeave(player, () -> {
						}, () -> Msg.send(player, "guild.cancelled"));
						return true;
					}
					return GuildActions.leave(player);
				}))
				.then(Commands.literal("confirm").executes(ctx -> run(ctx, "leave", GuildActions::leave))));

		root.then(Commands.literal("kick")
				.then(Commands.argument("member", StringArgumentType.word()).suggests(GuildArguments.members(GuildRules::canKick))
						.executes(ctx -> run(ctx, "kick", player ->
								withMember(player, ctx, id -> GuildActions.kick(player, id))))));
		root.then(Commands.literal("promote")
				.then(Commands.argument("member", StringArgumentType.word())
						.suggests(GuildArguments.members((self, role) -> self == GuildRole.LEADER && GuildRules.promoted(role) != null))
						.executes(ctx -> run(ctx, "promote", player ->
								withMember(player, ctx, id -> GuildActions.promote(player, id))))));
		root.then(Commands.literal("demote")
				.then(Commands.argument("member", StringArgumentType.word())
						.suggests(GuildArguments.members((self, role) -> self == GuildRole.LEADER && GuildRules.demoted(role) != null))
						.executes(ctx -> run(ctx, "demote", player ->
								withMember(player, ctx, id -> GuildActions.demote(player, id))))));
		root.then(Commands.literal("transfer")
				.then(Commands.argument("member", StringArgumentType.word())
						.suggests(GuildArguments.members((self, role) -> self == GuildRole.LEADER))
						.executes(ctx -> run(ctx, "transfer", player -> withMember(player, ctx, id -> {
							GuildActions.confirmTransfer(player, id, () -> {
							}, () -> Msg.send(player, "guild.cancelled"));
							return true;
						})))
						.then(Commands.literal("confirm").executes(ctx -> run(ctx, "transfer", player ->
								withMember(player, ctx, id -> GuildActions.transfer(player, id)))))));

		root.then(Commands.literal("disband")
				.executes(ctx -> run(ctx, "disband", player -> {
					GuildActions.confirmDisband(player, () -> Msg.send(player, "guild.cancelled"));
					return true;
				}))
				.then(Commands.literal("confirm").executes(ctx -> run(ctx, "disband", GuildActions::disband))));

		root.then(Commands.literal("info")
				.executes(ctx -> run(ctx, "info", player -> {
					Guild guild = GuildManager.of(player);
					if (guild == null) {
						Msg.send(player, "error.no_guild");
						return false;
					}
					GuildChat.info(player::sendSystemMessage, guild, true);
					return true;
				}))
				.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GuildArguments.ALL_GUILDS)
						.executes(ctx -> runSource(ctx, "info", source -> {
							String name = StringArgumentType.getString(ctx, "guild");
							Guild guild = GuildArguments.guild(name);
							if (guild == null) {
								source.sendFailure(Msg.prefixed(Lang.get("error.unknown_guild", name)));
								return false;
							}
							ServerPlayer viewer = source.getPlayer();
							// Treasury and heart position stay private to members (and the console).
							GuildChat.info(source::sendSystemMessage, guild, viewer == null || guild.isMember(viewer.getUUID()));
							return true;
						}))));

		root.then(Commands.literal("list")
				.executes(ctx -> runSource(ctx, "list", source -> {
					GuildChat.list(source::sendSystemMessage, 1);
					return true;
				}))
				.then(Commands.argument("page", IntegerArgumentType.integer(1))
						.executes(ctx -> runSource(ctx, "list", source -> {
							GuildChat.list(source::sendSystemMessage, IntegerArgumentType.getInteger(ctx, "page"));
							return true;
						}))));

		root.then(Commands.literal("deposit")
				.executes(ctx -> run(ctx, "deposit", player -> GuildActions.deposit(player, 0)))
				.then(Commands.literal("all").executes(ctx -> run(ctx, "deposit", player -> GuildActions.deposit(player, 0))))
				.then(Commands.argument("amount", IntegerArgumentType.integer(1))
						.executes(ctx -> run(ctx, "deposit", player ->
								GuildActions.deposit(player, IntegerArgumentType.getInteger(ctx, "amount"))))));
		root.then(Commands.literal("withdraw")
				.then(Commands.argument("amount", IntegerArgumentType.integer(1))
						.executes(ctx -> run(ctx, "withdraw", player ->
								GuildActions.withdraw(player, IntegerArgumentType.getInteger(ctx, "amount"))))));

		root.then(Commands.literal("banner").executes(ctx -> run(ctx, "banner", player -> {
			if (GuildManager.of(player) == null) {
				Msg.send(player, "error.no_guild");
				return false;
			}
			GuildMenus.openBanner(player);
			return true;
		})).then(Commands.literal("adopt").executes(ctx -> run(ctx, "banner adopt", GuildActions::adoptBanner))));
	}

	/** Resolves the "guild" argument; reports unknown names. */
	private static boolean withGuild(ServerPlayer player, CommandContext<CommandSourceStack> ctx, Predicate<Guild> action) {
		String name = StringArgumentType.getString(ctx, "guild");
		Guild guild = GuildArguments.guild(name);
		if (guild == null) {
			Msg.send(player, "error.unknown_guild", name);
			return false;
		}
		return action.test(guild);
	}

	/** Resolves the "member" argument against the player's guild (offline members by stored name). */
	private static boolean withMember(ServerPlayer player, CommandContext<CommandSourceStack> ctx, Predicate<UUID> action) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			Msg.send(player, "error.no_guild");
			return false;
		}
		String name = StringArgumentType.getString(ctx, "member");
		UUID id = GuildActions.findMember(guild, name);
		if (id == null) {
			Msg.send(player, "guild.member.unknown", name);
			return false;
		}
		return action.test(id);
	}
}
