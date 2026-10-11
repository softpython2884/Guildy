package dev.guildy.claim;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Safe;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Fast ownership / permission checks shared by the protection events and the world mixins
 * (hot paths: no allocation besides chunk-key math, claim dimension cached).
 */
public final class ClaimGuard {
	private static final Set<UUID> BYPASS = new HashSet<>();
	private static @Nullable ResourceKey<Level> claimDimension;

	private ClaimGuard() {
	}

	/** Re-reads the claim dimension from the config (server start, reload). */
	public static void refresh() {
		claimDimension = Safe.get("claim.dimension", GuildyServer::claimDimension, null);
	}

	public static boolean isClaimLevel(Level level) {
		ResourceKey<Level> key = claimDimension;
		if (key == null) {
			refresh();
			key = claimDimension;
			if (key == null) {
				return false;
			}
		}
		return level.dimension() == key || level.dimension().equals(key);
	}

	/** Guild owning the chunk containing this block (claim dimension only). */
	public static @Nullable Guild ownerAt(Level level, BlockPos pos) {
		return isClaimLevel(level) ? GuildManager.atChunk(ChunkKey.ofBlock(pos.getX(), pos.getZ())) : null;
	}

	// ---------------------------------------------------------------- permissions

	public static boolean hasBypass(ServerPlayer player) {
		return BYPASS.contains(player.getUUID());
	}

	/** Toggles the admin bypass. @return the new state */
	public static boolean toggleBypass(ServerPlayer player) {
		if (!BYPASS.remove(player.getUUID())) {
			BYPASS.add(player.getUUID());
			return true;
		}
		return false;
	}

	public static void forget(UUID player) {
		BYPASS.remove(player);
	}

	public static void clear() {
		BYPASS.clear();
	}

	/** True when the player may do {@code permission} in the owner's territory (owner null = wilderness). */
	public static boolean allowed(ServerPlayer player, @Nullable Guild owner, GuildPermission permission) {
		return owner == null || hasBypass(player) || owner.can(player.getUUID(), permission);
	}

	/** True when the player may do {@code permission} at this position. */
	public static boolean allowedAt(ServerPlayer player, Level level, BlockPos pos, GuildPermission permission) {
		return allowed(player, ownerAt(level, pos), permission);
	}

	// ---------------------------------------------------------------- spawn

	/** Chunk key of the world spawn when it lies in the claim dimension, else null. */
	public static @Nullable Long spawnChunk() {
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return null;
		}
		LevelData.RespawnData data = server.getRespawnData();
		ResourceKey<Level> dimension = claimDimension;
		if (dimension == null) {
			refresh();
			dimension = claimDimension;
		}
		if (dimension == null || !dimension.equals(data.dimension())) {
			return null;
		}
		BlockPos pos = data.pos();
		return ChunkKey.ofBlock(pos.getX(), pos.getZ());
	}
}
