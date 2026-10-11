package dev.guildy.claim;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * Territory: flag planting, claims, protection, enter/leave display, border visualisation.
 */
public final class ClaimModule {
	private ClaimModule() {
	}

	public static void init() {
		ClaimLang.register();
		ProtectionHandler.register();
		TerritoryNotifier.register();
		BorderVisualizer.register();
		HomeTeleports.register();
		ClaimCommands.register();

		Lifecycle.STARTED.add(server -> {
			ClaimGuard.refresh();
			HeartIndex.markDirty();
		});
		Lifecycle.RELOADED.add(ClaimGuard::refresh);
		GuildEvents.TERRITORY_CHANGED.add(guild -> {
			HeartIndex.markDirty();
			TerritoryNotifier.refreshSilently();
		});
		GuildEvents.DISBANDED.add(guild -> {
			removeHeartBanner(guild);
			HeartIndex.markDirty();
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				Safe.run("claim.disconnect", () -> forget(handler.getPlayer().getUUID())));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Safe.run("claim.stopped", ClaimModule::clear));
	}

	/** A disbanded guild's heart banner disappears (no drop), like when the territory is abandoned. */
	private static void removeHeartBanner(Guild guild) {
		BlockPos heart = guild.heart;
		ServerLevel level = GuildyServer.claimLevel();
		if (heart != null && level != null && level.isLoaded(heart)
				&& level.getBlockState(heart).getBlock() instanceof AbstractBannerBlock) {
			level.setBlock(heart, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	private static void forget(UUID player) {
		ClaimGuard.forget(player);
		ProtectionHandler.forget(player);
		TerritoryNotifier.forget(player);
		BorderVisualizer.forget(player);
		HomeTeleports.forget(player);
	}

	private static void clear() {
		ClaimGuard.clear();
		ProtectionHandler.clear();
		TerritoryNotifier.clear();
		BorderVisualizer.clear();
		HomeTeleports.clear();
		HeartIndex.clear();
	}
}
