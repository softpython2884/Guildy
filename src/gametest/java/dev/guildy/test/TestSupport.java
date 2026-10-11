package dev.guildy.test;

import dev.guildy.core.GuildyItems;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helpers for scenario GameTests. Each scenario gets its own far-away region so concurrently running tests never
 * interfere through claims or distance rules.
 */
@SuppressWarnings("removal")
final class TestSupport {
	private static final AtomicInteger REGIONS = new AtomicInteger();

	private TestSupport() {
	}

	/** A fresh ground position far from spawn and from other scenarios (x = 40000 + n * 4096). */
	static BlockPos region(ServerLevel level) {
		int n = REGIONS.getAndIncrement();
		BlockPos pos = new BlockPos(40000 + n * 4096, 100, 8);
		level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		return pos;
	}

	/** Connected mock player (creative) standing at {@code pos}. */
	static ServerPlayer player(GameTestHelper helper, BlockPos pos) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.teleportTo(helper.getLevel(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, Set.of(), 0, 0, true);
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
