package dev.guildy.claim;

import dev.guildy.data.Guild;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Territory operations used by commands and menus. Methods returning a String return null on success,
 * otherwise the lang key of the error (already-formatted messages are sent by the caller with Msg.send).
 */
public final class ClaimService {
	private ClaimService() {
	}

	/** Claims {@code chunkKey} for the guild, consuming one extension flag from the player's inventory. */
	public static @Nullable String claimWithFlagFromInventory(ServerPlayer player, Guild guild, long chunkKey) {
		return "error.unavailable";
	}

	/** Unclaims a chunk (never the heart chunk; keeps the territory connected) and refunds an extension flag. */
	public static @Nullable String unclaim(ServerPlayer player, Guild guild, long chunkKey) {
		return "error.unavailable";
	}

	/** Leader only: removes all claims and the heart, gives the founding flag back. Caller asks for confirmation. */
	public static @Nullable String abandonTerritory(ServerPlayer player, Guild guild) {
		return "error.unavailable";
	}

	/** Shows the territory borders with particles for a few seconds. */
	public static void showBorders(ServerPlayer player, Guild guild) {
	}

	public static @Nullable String setHome(ServerPlayer player, Guild guild) {
		return "error.unavailable";
	}

	/** Teleports to the guild home (cooldown + warm-up handled here). */
	public static void teleportHome(ServerPlayer player, Guild guild) {
	}

	/** Gives the founding flag again if the guild has no heart yet and the player doesn't already carry one. */
	public static @Nullable String giveFoundingFlag(ServerPlayer player, Guild guild) {
		return "error.unavailable";
	}

	/** Current price (treasury gold) of the next extension flag. */
	public static int extensionFlagPrice(Guild guild) {
		return 0;
	}

	/** Buys an extension flag with treasury gold (TREASURY permission) and gives it to the player. */
	public static @Nullable String buyExtensionFlag(ServerPlayer player, Guild guild) {
		return "error.unavailable";
	}

	/** True when the player is an admin in protection-bypass mode. */
	public static boolean hasBypass(ServerPlayer player) {
		return false;
	}
}
