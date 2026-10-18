package dev.guildy.test;

import com.mojang.authlib.GameProfile;
import dev.guildy.core.GuildyItems;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helpers for scenario GameTests. Each scenario gets its own far-away region so concurrently running tests never
 * interfere through claims or distance rules.
 */
@SuppressWarnings("removal")
final class TestSupport {
	private static final Logger LOGGER = LoggerFactory.getLogger("GuildyTest");
	private static final AtomicInteger REGIONS = new AtomicInteger();
	/** Chat received by the mock players (see {@link #chat}). */
	private static final Map<UUID, List<String>> CHAT = new HashMap<>();
	/** Random per run: the test world is reused, so avoid the blocks left by previous runs. */
	private static final int BASE_X = 40000 + (int) (Math.random() * 400) * 65536;

	private TestSupport() {
	}

	/**
	 * A fresh ground position far from spawn and from other scenarios (x = 40000 + n * 4096, y = 64), with a stone
	 * platform of the given radius (the test world is empty out there).
	 */
	static BlockPos region(ServerLevel level, int platformRadius) {
		int n = REGIONS.getAndIncrement();
		BlockPos center = pad(level, BASE_X + n * 4096, 8);
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int dx = -platformRadius; dx <= platformRadius; dx++) {
			for (int dz = -platformRadius; dz <= platformRadius; dz++) {
				BlockPos pos = center.offset(dx, 0, dz);
				level.setBlock(pos, stone, Block.UPDATE_CLIENTS);
				for (int dy = 1; dy <= 4; dy++) {
					level.setBlock(pos.above(dy), air, Block.UPDATE_CLIENTS);
				}
			}
		}
		return center;
	}

	/** Stone block at the real surface of (x, z) with free space above; returns the stone position. */
	static BlockPos pad(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		int firstFree = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		BlockPos ground = new BlockPos(x, firstFree - 1, z);
		level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(ground.above(2), Blocks.AIR.defaultBlockState());
		return ground;
	}

	static BlockPos region(ServerLevel level) {
		return region(level, 0);
	}

	/** Force-loads (entity ticking) the chunks around {@code center}, like a player standing there would. */
	static void forceChunks(ServerLevel level, BlockPos center, int radiusChunks) {
		int cx = center.getX() >> 4;
		int cz = center.getZ() >> 4;
		for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
			for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
				level.setChunkForced(cx + dx, cz + dz, true);
			}
		}
	}

	/** Runs a /guild command as the server console (all permissions). */
	static void console(ServerLevel level, String command) {
		var server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	/** Connected mock player (creative) standing at {@code pos}. */
	static ServerPlayer player(GameTestHelper helper, BlockPos pos) {
		return connected(helper, pos, GameType.CREATIVE, "test-creative");
	}

	/** Connected mock player in SURVIVAL mode. */
	static ServerPlayer survivor(GameTestHelper helper, BlockPos pos) {
		return connected(helper, pos, GameType.SURVIVAL, "test-survivor");
	}

	/** Connected mock player in SURVIVAL mode with its own name (at most 16 characters). */
	static ServerPlayer survivor(GameTestHelper helper, BlockPos pos, String name) {
		return connected(helper, pos, GameType.SURVIVAL, name);
	}

	/** Chat messages received by a mock player (plain text, oldest first, the last 200). */
	static List<String> chat(ServerPlayer player) {
		synchronized (CHAT) {
			List<String> lines = CHAT.get(player.getUUID());
			return lines == null ? List.of() : new ArrayList<>(lines);
		}
	}

	private static void rememberChat(UUID player, String line) {
		synchronized (CHAT) {
			List<String> lines = CHAT.computeIfAbsent(player, uuid -> new ArrayList<>());
			if (lines.size() >= 200) {
				lines.removeFirst();
			}
			lines.add(line);
			if (CHAT.size() > 256) {
				CHAT.keySet().removeIf(uuid -> !uuid.equals(player));
			}
		}
	}

	/** Mock player with an embedded connection; its chat messages are written to the log (handy to debug tests). */
	private static ServerPlayer connected(GameTestHelper helper, BlockPos pos, GameType mode, String name) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				LOGGER.info("[{}] {}", name, message.getString());
				if (!overlay) {
					rememberChat(getUUID(), message.getString());
				}
				super.sendSystemMessage(message, overlay);
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(mode);
		player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, Set.of(), 0, 0, true);
		return player;
	}

	static String uniqueName(String prefix) {
		return prefix + (System.nanoTime() % 1_000_000);
	}

	/** Right-clicks the top face of {@code ground} with {@code stack} in the main hand, through the real Fabric event. */
	static InteractionResult useOnTop(ServerPlayer player, ServerLevel level, BlockPos ground, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
	}

	/** Creates a guild and plants its founding flag on {@code ground} (heart = ground.above()). */
	static Guild foundGuild(GameTestHelper helper, ServerPlayer leader, BlockPos ground, String prefix) {
		Guild guild = GuildManager.create(uniqueName(prefix), leader);
		useOnTop(leader, helper.getLevel(), ground, GuildyItems.foundingFlag(guild));
		helper.assertTrue(guild.isFounded(), "guild should be founded after planting the founding flag");
		return guild;
	}
}
