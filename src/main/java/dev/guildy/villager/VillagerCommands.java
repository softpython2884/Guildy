package dev.guildy.villager;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * /guild villagers [accept|refuse|zones], /guild villagers route new|finish|undo|cancel (patrol route drawing, also
 * the clickable chat buttons) and /guild admin villager spawn &lt;guild&gt; (name with spaces, suggested).
 */
final class VillagerCommands {
	private static final SuggestionProvider<CommandSourceStack> GUILD_NAMES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(GuildManager.all().stream().map(guild -> guild.name), builder);
	/** Names of the candidates waiting for the player's guild. */
	private static final SuggestionProvider<CommandSourceStack> CANDIDATE_NAMES = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		Guild guild = player == null ? null : GuildManager.of(player);
		return SharedSuggestionProvider.suggest(guild == null ? java.util.stream.Stream.<String>empty()
				: VillagerManager.candidates(guild).stream().map(VillagerManager.Candidate::name), builder);
	};

	private VillagerCommands() {
	}

	static void init() {
		GuildCommand.addSubcommands((root, context) -> root.then(Commands.literal("villagers")
				.executes(ctx -> {
					ServerPlayer player = GuildCommand.player(ctx);
					Safe.run("villager.command.menu", () -> {
						Guild guild = GuildManager.of(player);
						if (guild == null) {
							Msg.send(player, "error.no_guild");
						} else {
							VillagerGui.openList(player, guild);
						}
					});
					return 1;
				})
				.then(Commands.literal("accept").executes(ctx -> {
					ServerPlayer player = GuildCommand.player(ctx);
					Safe.run("villager.command.accept", () -> VillagerManager.accept(player));
					return 1;
				}).then(Commands.argument("candidate", StringArgumentType.greedyString()).suggests(CANDIDATE_NAMES).executes(ctx -> {
					ServerPlayer player = GuildCommand.player(ctx);
					String name = StringArgumentType.getString(ctx, "candidate");
					Safe.run("villager.command.accept", () -> VillagerManager.accept(player, name));
					return 1;
				})))
				.then(Commands.literal("refuse").executes(ctx -> {
					ServerPlayer player = GuildCommand.player(ctx);
					Safe.run("villager.command.refuse", () -> VillagerManager.refuse(player));
					return 1;
				}).then(Commands.argument("candidate", StringArgumentType.greedyString()).suggests(CANDIDATE_NAMES).executes(ctx -> {
					ServerPlayer player = GuildCommand.player(ctx);
					String name = StringArgumentType.getString(ctx, "candidate");
					Safe.run("villager.command.refuse", () -> VillagerManager.refuse(player, name));
					return 1;
				})))
				.then(Commands.literal("zones").executes(ctx -> {
					ServerPlayer player = GuildCommand.player(ctx);
					Safe.run("villager.command.zones", () -> {
						Guild guild = GuildManager.of(player);
						if (guild == null) {
							Msg.send(player, "error.no_guild");
						} else {
							ZoneGui.openZones(player, guild);
						}
					});
					return 1;
				}))
				.then(Commands.literal("route")
						.then(Commands.literal("new").executes(ctx -> {
							ServerPlayer player = GuildCommand.player(ctx);
							Safe.run("villager.command.route.new", () -> newRoute(player));
							return 1;
						}))
						.then(Commands.literal("finish").executes(ctx -> {
							ServerPlayer player = GuildCommand.player(ctx);
							Safe.run("villager.command.route.finish", () -> RouteSelection.finish(player));
							return 1;
						}))
						.then(Commands.literal("undo").executes(ctx -> {
							ServerPlayer player = GuildCommand.player(ctx);
							Safe.run("villager.command.route.undo", () -> RouteSelection.undo(player));
							return 1;
						}))
						.then(Commands.literal("cancel").executes(ctx -> {
							ServerPlayer player = GuildCommand.player(ctx);
							Safe.run("villager.command.route.cancel", () -> RouteSelection.cancel(player));
							return 1;
						})))));

		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("villager")
				.then(Commands.literal("spawn")
						.then(Commands.argument("guild", StringArgumentType.greedyString())
								.suggests(GUILD_NAMES)
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "guild").trim();
									CommandSourceStack source = ctx.getSource();
									return Safe.get("villager.command.spawn", () -> spawn(source, name), 0);
								})))));
	}

	/** Starts drawing a patrol route (same checks as the menu button). */
	private static void newRoute(ServerPlayer player) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			Msg.send(player, "error.no_guild");
			return;
		}
		if (!guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(player, "error.no_permission");
			return;
		}
		String error = Zones.canCreateRoute(guild);
		if (error != null) {
			Msg.send(player, error, VillagerModule.config().routes.maxRoutes);
			return;
		}
		RouteSelection.start(player, guild, null);
	}

	private static int spawn(CommandSourceStack source, String name) {
		Guild guild = GuildManager.byName(name);
		if (guild == null) {
			source.sendFailure(Msg.prefixed(Lang.get("error.unknown_guild", name)));
			return 0;
		}
		String error = VillagerManager.forceCandidate(guild);
		if (error != null) {
			source.sendFailure(Msg.prefixed(Lang.get(error)));
			return 0;
		}
		VillagerManager.Candidate candidate = VillagerManager.lastCandidate(guild);
		String candidateName = candidate == null ? "?" : candidate.name();
		source.sendSuccess(() -> Msg.prefixed(Lang.get("villager.admin.spawned", candidateName, guild.name)), true);
		return 1;
	}
}
