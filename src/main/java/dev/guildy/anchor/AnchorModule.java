package dev.guildy.anchor;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.guildy.command.GuildCommand;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Anchor flags (config.json5 → anchors): banners planted in the territory that keep the chunks around them active as if
 * a player stood there — chunk tickets ({@link AnchorTickets}), mob spawning and despawning, spawners within reach and
 * no server pause (mixins of {@code dev.guildy.anchor.mixin}, fed by {@link AnchorPoints}). Menu: territory menu →
 * "Drapeaux d'ancrage", {@code /guild ancrage}; server overview: {@code /guild admin ancrages}.
 */
public final class AnchorModule {
	private AnchorModule() {
	}

	public static void init() {
		AnchorLang.register();
		AnchorTickets.init();
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)
					|| player.isSpectator()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!AnchorFlags.isAnchorFlag(stack)) {
				return InteractionResult.PASS;
			}
			return Safe.get("anchor.plant", () -> AnchorFlags.plant(serverPlayer, serverLevel, hit, stack), InteractionResult.FAIL);
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				Safe.get("anchor.break", () -> !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)
						|| AnchorFlags.onBreak(serverPlayer, serverLevel, pos, state), true));
		GuildEvents.DISBANDED.add(guild -> Safe.run("anchor.disband", () -> AnchorFlags.onDisband(guild)));
		GuildCommand.addSubcommands((root, context) -> {
			root.then(menuCommand("ancrage"));
			root.then(menuCommand("ancrages"));
		});
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("ancrages")
				.executes(ctx -> Safe.get("anchor.admin", () -> report(ctx.getSource()), 0))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> menuCommand(String name) {
		return Commands.literal(name).executes(AnchorModule::openMenu);
	}

	private static int openMenu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = GuildCommand.player(ctx);
		Safe.run("anchor.command", () -> {
			Guild guild = GuildManager.of(player);
			if (guild == null) {
				Msg.send(player, "error.no_guild");
			} else if (!GuildyServer.config().anchors.enabled) {
				Msg.send(player, "anchor.error.disabled");
			} else {
				AnchorMenu.open(player, guild);
			}
		});
		return 1;
	}

	/** /guild admin ancrages: every anchor of the server with its state. */
	private static int report(CommandSourceStack source) {
		AnchorTickets.refresh();
		GuildyConfig.Anchors config = GuildyServer.config().anchors;
		int total = 0;
		for (Guild guild : GuildManager.all()) {
			total += guild.anchors.flags.size();
		}
		int active = AnchorTickets.activeCount();
		Component header = Msg.prefixed(Lang.get("anchor.admin.header", total, active, active * AnchorTickets.chunksPerAnchor(),
				Lang.raw(config.enabled ? "anchor.admin.on" : "anchor.admin.off"), Lang.raw(config.mobSpawning ? "anchor.admin.on" : "anchor.admin.off")));
		source.sendSuccess(() -> header, false);
		for (Guild guild : GuildManager.all()) {
			for (ChunkAnchor anchor : guild.anchors.flags) {
				AnchorTickets.State state = AnchorTickets.state(guild, anchor);
				Component line = Lang.get("anchor.admin.line", guild.name, anchor.pos.getX(), anchor.pos.getY(), anchor.pos.getZ(),
						anchor.dimension, Lang.raw(state.langKey()), anchor.placedBy);
				source.sendSuccess(() -> line, false);
			}
		}
		if (total == 0) {
			source.sendSuccess(() -> Lang.get("anchor.admin.none"), false);
		}
		MinecraftServer server = source.getServer();
		if (server instanceof DedicatedServer dedicated && dedicated.pauseWhenEmptySeconds() > 0 && !config.keepServerAwake && active > 0) {
			source.sendSuccess(() -> Lang.get("anchor.admin.pause", dedicated.pauseWhenEmptySeconds()), false);
		}
		return Math.max(1, total);
	}
}
