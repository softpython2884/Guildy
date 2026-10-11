package dev.guildy.event;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * {@code /guild admin event list | start <event> [guild] | stop <event> [guild]} (guild names may contain spaces).
 */
final class EventCommands {
	private static final SuggestionProvider<CommandSourceStack> EVENTS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(EventType.ids(), builder);

	private static final SuggestionProvider<CommandSourceStack> GUILD_NAMES = (ctx, builder) -> {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (Guild guild : GuildManager.all()) {
			if (guild.name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
				builder.suggest(guild.name);
			}
		}
		return builder.buildFuture();
	};

	private EventCommands() {
	}

	static void register() {
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("event")
				.then(Commands.literal("list").executes(EventCommands::list))
				.then(Commands.literal("start")
						.then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS)
								.executes(ctx -> run(ctx, true, null))
								.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GUILD_NAMES)
										.executes(ctx -> run(ctx, true, StringArgumentType.getString(ctx, "guild"))))))
				.then(Commands.literal("stop")
						.then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS)
								.executes(ctx -> run(ctx, false, null))
								.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GUILD_NAMES)
										.executes(ctx -> run(ctx, false, StringArgumentType.getString(ctx, "guild"))))))));
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		for (Component line : Safe.get("event.admin.list", EventManager::list, java.util.List.<Component>of())) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int run(CommandContext<CommandSourceStack> ctx, boolean start, @Nullable String guildName) {
		String id = StringArgumentType.getString(ctx, "event");
		EventType type = EventType.byId(id);
		if (type == null) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("event.admin.unknown", id, String.join(", ", EventType.ids()))));
			return 0;
		}
		Guild guild = null;
		if (guildName != null && !type.global) {
			guild = GuildManager.byName(guildName.trim());
			if (guild == null) {
				ctx.getSource().sendFailure(Msg.prefixed(Lang.get("error.unknown_guild", guildName.trim())));
				return 0;
			}
		}
		Guild target = guild;
		EventManager.AdminResult result = Safe.get("event.admin", () -> start ? EventManager.start(type, target) : EventManager.stop(type, target),
				new EventManager.AdminResult(false, Lang.get("error.internal")));
		if (result.success()) {
			ctx.getSource().sendSuccess(() -> Msg.prefixed(result.message()), true);
			return 1;
		}
		ctx.getSource().sendFailure(Msg.prefixed(result.message()));
		return 0;
	}
}
