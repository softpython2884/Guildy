package dev.guildy.claim;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * /guild claim | unclaim | show | sethome | home | flag [buy]
 * /guild admin bypass | giveflag <player> extension [count] | giveflag <player> founding <guild> | tp <guild>
 */
final class ClaimCommands {
	private ClaimCommands() {
	}

	/** Guild names (with spaces) for greedy string arguments. */
	private static final SuggestionProvider<CommandSourceStack> GUILD_NAMES = ClaimCommands::suggestGuilds;

	static void register() {
		GuildCommand.addSubcommands((root, context) -> {
			root.then(Commands.literal("claim").executes(ctx -> withGuild(ctx, (player, guild) -> {
				if (!ClaimGuard.isClaimLevel(player.level())) {
					return "claim.error.wrong_dimension";
				}
				return ClaimService.claimWithFlagFromInventory(player, guild, chunkOf(player));
			})));
			root.then(Commands.literal("unclaim").executes(ctx -> withGuild(ctx, (player, guild) -> {
				if (!ClaimGuard.isClaimLevel(player.level())) {
					return "claim.error.not_owned";
				}
				return ClaimService.unclaim(player, guild, chunkOf(player));
			})));
			root.then(Commands.literal("show").executes(ctx -> withGuild(ctx, (player, guild) -> {
				ClaimService.showBorders(player, guild);
				return null;
			})));
			root.then(Commands.literal("sethome").executes(ctx -> withGuild(ctx, ClaimService::setHome)));
			root.then(Commands.literal("home").executes(ctx -> withGuild(ctx, (player, guild) -> {
				ClaimService.teleportHome(player, guild);
				return null;
			})));
			root.then(Commands.literal("flag")
					.executes(ctx -> withGuild(ctx, ClaimCommands::flag))
					.then(Commands.literal("buy").executes(ctx -> withGuild(ctx, ClaimService::buyExtensionFlag))));
		});

		GuildCommand.addAdminSubcommands((admin, context) -> {
			admin.then(Commands.literal("bypass").executes(ClaimCommands::toggleBypass));
			admin.then(Commands.literal("giveflag")
					.then(Commands.argument("player", EntityArgument.player())
							.then(Commands.literal("extension")
									.executes(ctx -> giveExtension(ctx, 1))
									.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
											.executes(ctx -> giveExtension(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
							.then(Commands.literal("founding")
									.then(Commands.argument("guild", StringArgumentType.greedyString())
											.suggests(GUILD_NAMES)
											.executes(ClaimCommands::giveFounding)))));
			admin.then(Commands.literal("tp")
					.then(Commands.argument("guild", StringArgumentType.greedyString())
							.suggests(GUILD_NAMES)
							.executes(ClaimCommands::teleportToHeart)));
		});
	}

	private static long chunkOf(ServerPlayer player) {
		return ChunkKey.ofBlock(player.getBlockX(), player.getBlockZ());
	}

	/** Runs an action for the player's guild; a returned lang key is sent as an error. */
	private static int withGuild(CommandContext<CommandSourceStack> ctx, BiFunction<ServerPlayer, Guild, String> action)
			throws CommandSyntaxException {
		ServerPlayer player = GuildCommand.player(ctx);
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			Msg.send(player, "error.no_guild");
			return 0;
		}
		String error = Safe.get("command.claim", () -> action.apply(player, guild), "error.internal");
		if (error != null) {
			Msg.send(player, error);
			return 0;
		}
		return 1;
	}

	/** Founding flag while the base is not founded, otherwise extension flag price with a [Buy] button. */
	private static String flag(ServerPlayer player, Guild guild) {
		if (!guild.isFounded()) {
			return ClaimService.giveFoundingFlag(player, guild);
		}
		MutableComponent line = Lang.get("claim.flag.info", ClaimService.extensionFlagPrice(guild), guild.treasury)
				.append(Msg.button("claim.flag.buy", "/guild flag buy", "claim.flag.buy.hover"));
		Msg.sendRaw(player, line);
		Msg.send(player, "claim.info.usage");
		return null;
	}

	// ---------------------------------------------------------------- admin

	private static int toggleBypass(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = GuildCommand.player(ctx);
		boolean enabled = ClaimGuard.toggleBypass(player);
		Msg.send(player, enabled ? "protect.bypass.on" : "protect.bypass.off");
		return 1;
	}

	private static int giveExtension(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		Guild guild = GuildManager.of(target);
		for (int i = 0; i < count; i++) {
			ItemUtil.giveOrDrop(target, GuildyItems.extensionFlag(guild));
		}
		Component message = Msg.prefixed(Lang.get("claim.admin.gave_extension", count, target.getName().getString()));
		ctx.getSource().sendSuccess(() -> message, true);
		return count;
	}

	private static int giveFounding(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		Guild guild = guildArgument(ctx);
		if (guild == null) {
			return 0;
		}
		ItemUtil.giveOrDrop(target, GuildyItems.foundingFlag(guild));
		Component message = Msg.prefixed(Lang.get("claim.admin.gave_founding", guild.name, target.getName().getString()));
		ctx.getSource().sendSuccess(() -> message, true);
		return 1;
	}

	private static int teleportToHeart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = GuildCommand.player(ctx);
		Guild guild = guildArgument(ctx);
		if (guild == null) {
			return 0;
		}
		ServerLevel level = GuildyServer.claimLevel();
		if (guild.heart == null || level == null) {
			Msg.send(player, "claim.admin.no_heart", guild.name);
			return 0;
		}
		Vec3 target = HomeTeleports.nearHeart(level, guild.heart);
		HomeTeleports.moveTo(player, level, target, player.getYRot());
		Msg.send(player, "claim.admin.tp", guild.name);
		return 1;
	}

	private static @Nullable Guild guildArgument(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "guild").trim();
		Guild guild = GuildManager.byName(name);
		if (guild == null) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("error.unknown_guild", name)));
		}
		return guild;
	}

	private static CompletableFuture<Suggestions> suggestGuilds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (Guild guild : GuildManager.all()) {
			if (guild.name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
				builder.suggest(guild.name);
			}
		}
		return builder.buildFuture();
	}
}
