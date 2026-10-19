package dev.guildy.fly;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * Guild flight: {@code /guild fly [on|off]} (permission guildy.fly, granted by default), flight inside the own territory,
 * never during a horde ({@link GuildFly}, rules in {@link FlyRules}, settings in the {@code fly} section of config.json5).
 */
public final class FlyModule {
	private FlyModule() {
	}

	public static void init() {
		FlyLang.register();
		GuildFly.register();
		// A plain child of /guild (top-level shortcuts can copy it).
		GuildCommand.addSubcommands((root, context) -> root.then(Commands.literal("fly")
				.requires(Permissions.require(GuildFly.PERMISSION, true))
				.executes(ctx -> run(ctx, GuildFly::toggle))
				.then(Commands.literal("on").executes(ctx -> run(ctx, player -> GuildFly.setPreference(player, true))))
				.then(Commands.literal("off").executes(ctx -> run(ctx, player -> GuildFly.setPreference(player, false))))));
	}

	private static int run(CommandContext<CommandSourceStack> ctx, Predicate<ServerPlayer> action) throws CommandSyntaxException {
		ServerPlayer player = GuildCommand.player(ctx);
		Boolean done = Safe.get("cmd.fly", () -> action.test(player), null);
		if (done == null) {
			Msg.send(player, "error.internal");
			return 0;
		}
		return done ? 1 : 0;
	}
}
