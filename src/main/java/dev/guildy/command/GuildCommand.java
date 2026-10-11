package dev.guildy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.GuildManager;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Root of {@code /guild} (alias {@code /gld}). Modules contribute their subcommands through
 * {@link #addSubcommands} / {@link #addAdminSubcommands} during mod init.
 */
public final class GuildCommand {
	public static final String ADMIN_PERMISSION = "guildy.admin";

	@FunctionalInterface
	public interface Provider {
		void register(LiteralArgumentBuilder<CommandSourceStack> node, CommandBuildContext context);
	}

	private static final List<Provider> PROVIDERS = new ArrayList<>();
	private static final List<Provider> ADMIN_PROVIDERS = new ArrayList<>();

	private GuildCommand() {
	}

	/** Adds children to /guild. */
	public static void addSubcommands(Provider provider) {
		PROVIDERS.add(provider);
	}

	/** Adds children to /guild admin (already permission-gated by guildy.admin). */
	public static void addAdminSubcommands(Provider provider) {
		ADMIN_PROVIDERS.add(provider);
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("guild").executes(GuildCommand::openMenu);
		for (Provider provider : PROVIDERS) {
			provider.register(root, context);
		}

		LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin")
				.requires(Permissions.require(ADMIN_PERMISSION, PermissionLevel.byId(GuildyServer.config().general.adminOpLevel)));
		admin.then(Commands.literal("reload").executes(ctx -> {
			List<String> errors = ConfigManager.loadAll();
			String langError = Lang.load();
			if (langError != null) {
				errors.add(langError);
			}
			for (Runnable hook : Lifecycle.RELOADED) {
				Safe.run("lifecycle.reloaded", hook);
			}
			if (errors.isEmpty()) {
				ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("admin.reloaded")), true);
			} else {
				ctx.getSource().sendFailure(Msg.prefixed(Lang.get("admin.reload_errors", String.join(", ", errors))));
			}
			return 1;
		}));
		admin.then(Commands.literal("save").executes(ctx -> {
			GuildManager.save(ctx.getSource().getServer(), true);
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("admin.saved")), true);
			return 1;
		}));
		for (Provider provider : ADMIN_PROVIDERS) {
			provider.register(admin, context);
		}
		root.then(admin);

		LiteralCommandNode<CommandSourceStack> node = dispatcher.register(root);
		dispatcher.register(Commands.literal("gld").executes(GuildCommand::openMenu).redirect(node));
	}

	private static int openMenu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Hooks.openMainMenu.accept(player);
		return 1;
	}

	/** Shortcut used by subcommands. */
	public static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}
}
