package dev.guildy.trade;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.structure.GuildArgument;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /guild trade} (trade hub menu) and {@code /guild admin trade tier <guild> <tier>}.
 * Merchant visits are forced with {@code /guild admin event start merchant <guild>}.
 */
final class TradeCommands {
	private TradeCommands() {
	}

	static void register() {
		GuildCommand.addSubcommands((root, context) -> root.then(Commands.literal("trade").executes(ctx -> {
			ServerPlayer player = GuildCommand.player(ctx);
			Guild guild = GuildManager.of(player);
			if (guild == null) {
				Msg.send(player, "error.no_guild");
				return 0;
			}
			TradeHubMenu.open(player, guild);
			return 1;
		})));

		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("trade")
				.then(Commands.literal("tier")
						.then(GuildArgument.guild("guild")
								.then(Commands.argument("tier", IntegerArgumentType.integer(1, 10)).executes(ctx -> {
									Guild guild = GuildArgument.resolve(ctx, "guild");
									if (guild == null) {
										return 0;
									}
									int tier = TradeHub.setTier(guild, IntegerArgumentType.getInteger(ctx, "tier"));
									ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("trade.admin.tier", guild.name, TradeHub.roman(tier))), true);
									return 1;
								}))))));
	}
}
