package dev.guildy.core;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.GuildyConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Static access to the running server and the main config.
 */
public final class GuildyServer {
	private static @Nullable MinecraftServer server;
	private static ConfigHandle<GuildyConfig> config;

	private GuildyServer() {
	}

	public static void setServer(@Nullable MinecraftServer value) {
		server = value;
	}

	public static void setConfigHandle(ConfigHandle<GuildyConfig> handle) {
		config = handle;
	}

	public static GuildyConfig config() {
		return config.get();
	}

	public static @Nullable MinecraftServer server() {
		return server;
	}

	public static boolean isRunning() {
		return server != null;
	}

	public static RegistryAccess registryAccess() {
		if (server == null) {
			throw new IllegalStateException("Server not running");
		}
		return server.registryAccess();
	}

	/** Server tick counter (0 when no server). */
	public static int tick() {
		return server == null ? 0 : server.getTickCount();
	}

	/** The dimension where territories live (config territory.dimension), or null if missing. */
	public static @Nullable ServerLevel claimLevel() {
		if (server == null) {
			return null;
		}
		ServerLevel level = server.getLevel(claimDimension());
		return level != null ? level : server.overworld();
	}

	public static ResourceKey<Level> claimDimension() {
		Identifier id = Identifier.parse(config().territory.dimension);
		return ResourceKey.create(Registries.DIMENSION, id);
	}

	public static boolean isClaimLevel(Level level) {
		return level.dimension().equals(claimDimension());
	}

	public static @Nullable ServerPlayer player(UUID uuid) {
		return server == null ? null : server.getPlayerList().getPlayer(uuid);
	}

	public static List<ServerPlayer> players() {
		return server == null ? List.of() : server.getPlayerList().getPlayers();
	}
}
