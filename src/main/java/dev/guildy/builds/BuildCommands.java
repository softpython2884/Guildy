package dev.guildy.builds;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * /guild chantier (alias /guild chantiers; /guild build stays the constructions menu): the sites menu; {@code list} in
 * the chat; {@code confirm} / {@code cancel} for the placement in progress (same as the adjust menu).
 */
final class BuildCommands {
	private BuildCommands() {
	}

	static void init() {
		GuildCommand.addSubcommands((root, context) -> {
			root.then(tree("chantier"));
			root.then(tree("chantiers"));
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
		return Commands.literal(name)
				.executes(ctx -> run(ctx, "build.command.menu", (player, guild) -> BuildMenus.openList(player, guild)))
				.then(Commands.literal("list").executes(ctx -> run(ctx, "build.command.list", BuildCommands::list)))
				.then(Commands.literal("litematica").executes(ctx -> run(ctx, "build.command.litematica",
						(player, guild) -> BuildMenus.openShared(player, guild, 0))))
				.then(Commands.literal("confirm").executes(ctx -> run(ctx, "build.command.confirm", (player, guild) -> {
					if (BuildMenus.allowed(player, guild)) {
						BuildProject project = BuildPlacement.confirm(player, guild);
						if (project != null) {
							BuildMenus.openDetail(player, guild, project.id);
						}
					}
				})))
				.then(Commands.literal("cancel").executes(ctx -> run(ctx, "build.command.cancel", (player, guild) -> {
					if (BuildPreview.session(player) == null) {
						Msg.send(player, "build.preview.none");
						return;
					}
					BuildPreview.end(player);
					BuildPlacement.removePlans(player);
					Msg.send(player, "build.preview.cancelled");
				})));
	}

	private interface Action {
		void run(ServerPlayer player, Guild guild);
	}

	private static int run(CommandContext<CommandSourceStack> ctx, String context, Action action) throws CommandSyntaxException {
		ServerPlayer player = GuildCommand.player(ctx);
		Safe.run(context, () -> {
			Guild guild = GuildManager.of(player);
			if (guild == null) {
				Msg.send(player, "error.no_guild");
			} else {
				action.run(player, guild);
			}
		});
		return 1;
	}

	private static void list(ServerPlayer player, Guild guild) {
		if (guild.builds.projects.isEmpty()) {
			Msg.send(player, "build.command.list.empty");
			return;
		}
		Msg.send(player, "build.command.list.header", guild.builds.activeCount(), BuildModule.config().maxActive(guild.level));
		for (BuildProject project : guild.builds.ordered()) {
			BuildSite site = BuildSites.site(guild, project);
			int percent = project.finished ? 100 : site != null && site.scanned() ? site.counts().percent() : project.percent();
			int[] box = project.box();
			Msg.sendRaw(player, Lang.get("build.command.list.line", project.name, percent,
					Lang.raw(BuildMenus.statusKey(site), BuildMenus.statusArgs(site)), box[0], box[1], box[2]));
		}
	}
}
