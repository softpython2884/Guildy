package dev.guildy.wave;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * /guild waves (menu) and /guild admin wave start|stop|status|setnight|truce|schedule (guild names may contain spaces).
 */
final class WaveCommands {
	/** Guild names (greedy argument: spaces allowed). */
	private static final SuggestionProvider<CommandSourceStack> GUILD_NAMES = (ctx, builder) -> {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (Guild guild : GuildManager.all()) {
			if (guild.name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
				builder.suggest(guild.name);
			}
		}
		return builder.buildFuture();
	};

	/** "<guild name> <night>": suggests names, then the current night step once a full name is typed. */
	private static final SuggestionProvider<CommandSourceStack> GUILD_AND_NIGHT = (ctx, builder) -> {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (Guild guild : GuildManager.all()) {
			String name = guild.name.toLowerCase(Locale.ROOT);
			if (name.startsWith(remaining)) {
				builder.suggest(guild.name);
			} else if (remaining.startsWith(name + " ")) {
				builder.suggest(guild.name + " " + guild.waves.night);
			}
		}
		return builder.buildFuture();
	};

	private WaveCommands() {
	}

	static void register() {
		GuildCommand.addSubcommands((root, context) -> root.then(Commands.literal("waves").executes(WaveCommands::openMenu)));
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("wave")
				.then(Commands.literal("start")
						.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GUILD_NAMES).executes(WaveCommands::start)))
				.then(Commands.literal("stop")
						.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GUILD_NAMES).executes(WaveCommands::stop)))
				.then(Commands.literal("status")
						.then(Commands.argument("guild", StringArgumentType.greedyString()).suggests(GUILD_NAMES).executes(WaveCommands::status)))
				.then(Commands.literal("setnight")
						.then(Commands.argument("args", StringArgumentType.greedyString()).suggests(GUILD_AND_NIGHT).executes(WaveCommands::setNight)))
				.then(Commands.literal("truce")
						.then(Commands.argument("args", StringArgumentType.greedyString()).suggests(GUILD_AND_NIGHT).executes(WaveCommands::truce)))
				.then(Commands.literal("schedule")
						.then(Commands.argument("args", StringArgumentType.greedyString()).suggests(GUILD_AND_NIGHT).executes(WaveCommands::schedule)))));
	}

	private static int openMenu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = GuildCommand.player(ctx);
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			Msg.send(player, "error.no_guild");
			return 0;
		}
		WaveMenu.open(player, guild);
		return 1;
	}

	private static int start(CommandContext<CommandSourceStack> ctx) {
		Guild guild = guildArg(ctx, StringArgumentType.getString(ctx, "guild"));
		if (guild == null) {
			return 0;
		}
		if (WaveManager.isActive(guild)) {
			return fail(ctx, "wave.cmd.already", guild.name);
		}
		if (!guild.isFounded()) {
			return fail(ctx, "wave.cmd.notfounded", guild.name);
		}
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return fail(ctx, "error.internal");
		}
		int nearby = WaveManager.nearbyMembers(level, guild);
		WaveSession session = WaveManager.startWave(level, guild, Math.max(1, nearby), true, level.getOverworldClockTime());
		if (session == null) {
			return fail(ctx, "wave.cmd.startfailed");
		}
		success(ctx, Lang.get("wave.cmd.started", guild.name, Lang.colorize(session.theme().name), session.total()));
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) {
		Guild guild = guildArg(ctx, StringArgumentType.getString(ctx, "guild"));
		if (guild == null) {
			return 0;
		}
		if (!WaveManager.stop(guild, "wave.reason.admin")) {
			return fail(ctx, "wave.cmd.none", guild.name);
		}
		success(ctx, Lang.get("wave.cmd.stopped", guild.name));
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		Guild guild = guildArg(ctx, StringArgumentType.getString(ctx, "guild"));
		if (guild == null) {
			return 0;
		}
		WaveSession session = WaveManager.session(guild);
		ServerLevel level = GuildyServer.claimLevel();
		if (session != null && level != null) {
			success(ctx, session.statusLine(level));
		} else {
			long day = WaveFormula.dayIndex(WaveManager.clock());
			success(ctx, Lang.get("wave.cmd.status.idle", guild.name, guild.waves.night,
					WaveSession.formatDifficulty(WaveManager.estimateDifficulty(guild)), guild.waves.lastWaveDay, day));
		}
		return 1;
	}

	private static int setNight(CommandContext<CommandSourceStack> ctx) {
		String raw = StringArgumentType.getString(ctx, "args").trim();
		int space = raw.lastIndexOf(' ');
		if (space <= 0) {
			return fail(ctx, "wave.cmd.usage.setnight");
		}
		int night;
		try {
			night = Integer.parseInt(raw.substring(space + 1).trim());
		} catch (NumberFormatException e) {
			return fail(ctx, "wave.cmd.usage.setnight");
		}
		if (night < 0 || night > 100_000) {
			return fail(ctx, "wave.cmd.usage.setnight");
		}
		Guild guild = guildArg(ctx, raw.substring(0, space));
		if (guild == null) {
			return 0;
		}
		guild.waves.night = night;
		GuildManager.markDirty();
		success(ctx, Lang.get("wave.cmd.night", guild.name, night));
		return 1;
	}

	/** Sets the truce nights left (0 ends the truce now). */
	private static int truce(CommandContext<CommandSourceStack> ctx) {
		String raw = StringArgumentType.getString(ctx, "args").trim();
		int space = raw.lastIndexOf(' ');
		if (space <= 0) {
			return fail(ctx, "wave.cmd.usage.truce");
		}
		int nights;
		try {
			nights = Integer.parseInt(raw.substring(space + 1).trim());
		} catch (NumberFormatException e) {
			return fail(ctx, "wave.cmd.usage.truce");
		}
		if (nights < 0 || nights > 30) {
			return fail(ctx, "wave.cmd.usage.truce");
		}
		Guild guild = guildArg(ctx, raw.substring(0, space));
		if (guild == null) {
			return 0;
		}
		guild.waves.truceNights = nights;
		guild.waves.truceDay = -1;
		GuildManager.markDirty();
		success(ctx, Lang.get("wave.cmd.truce", guild.name, nights));
		return 1;
	}

	/** Sets the night of the next horde (0 = tonight). */
	private static int schedule(CommandContext<CommandSourceStack> ctx) {
		String raw = StringArgumentType.getString(ctx, "args").trim();
		int space = raw.lastIndexOf(' ');
		if (space <= 0) {
			return fail(ctx, "wave.cmd.usage.schedule");
		}
		int nights;
		try {
			nights = Integer.parseInt(raw.substring(space + 1).trim());
		} catch (NumberFormatException e) {
			return fail(ctx, "wave.cmd.usage.schedule");
		}
		if (nights < 0 || nights > 30) {
			return fail(ctx, "wave.cmd.usage.schedule");
		}
		Guild guild = guildArg(ctx, raw.substring(0, space));
		if (guild == null) {
			return 0;
		}
		guild.waves.nextWaveDay = WaveFormula.dayIndex(WaveManager.clock()) + nights;
		GuildManager.markDirty();
		success(ctx, Lang.get("wave.cmd.schedule", guild.name, nights));
		return 1;
	}

	private static Guild guildArg(CommandContext<CommandSourceStack> ctx, String name) {
		Guild guild = GuildManager.byName(name.trim());
		if (guild == null) {
			fail(ctx, "error.unknown_guild", name.trim());
		}
		return guild;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
		ctx.getSource().sendFailure(Msg.prefixed(Lang.get(key, args)));
		return 0;
	}

	private static void success(CommandContext<CommandSourceStack> ctx, Component message) {
		ctx.getSource().sendSuccess(() -> Msg.prefixed(message), true);
	}
}
