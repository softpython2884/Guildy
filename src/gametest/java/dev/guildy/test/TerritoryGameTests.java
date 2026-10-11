package dev.guildy.test;

import dev.guildy.claim.ClaimService;
import dev.guildy.core.GuildyItems;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * Territory scenario: founding flag, 3x3 claim, protection, extension rules, unclaim refund, abandon.
 */
public class TerritoryGameTests {

	@GameTest(maxTicks = 100)
	public void flagsClaimsAndProtection(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Terr");

		BlockPos heart = ground.above();
		helper.assertTrue(heart.equals(guild.heart), "heart is the planted banner position");
		helper.assertTrue(level.getBlockState(heart).getBlock() instanceof AbstractBannerBlock, "banner block placed");
		int founding = (2 * 1 + 1) * (2 * 1 + 1);
		helper.assertTrue(guild.claimCount() == founding, "founding claims a 3x3 square, got " + guild.claimCount());

		// Protection: visitors cannot break, members can, nobody breaks the heart.
		ServerPlayer visitor = TestSupport.player(helper, ground.east(3));
		BlockPos inside = ground.east(3);
		level.setBlockAndUpdate(inside, Blocks.STONE.defaultBlockState());
		boolean visitorBreak = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, visitor, inside, level.getBlockState(inside), null);
		boolean memberBreak = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, leader, inside, level.getBlockState(inside), null);
		boolean heartBreak = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, leader, heart, level.getBlockState(heart), null);
		helper.assertFalse(visitorBreak, "visitor must not break blocks in a claim");
		helper.assertTrue(memberBreak, "member can break blocks in own claim");
		helper.assertFalse(heartBreak, "heart is unbreakable");

		// Extension flag in an adjacent chunk (east of the 3x3) is accepted.
		long heartChunk = ChunkKey.ofBlock(heart.getX(), heart.getZ());
		BlockPos east = TestSupport.pad(level, ((ChunkKey.x(heartChunk) + 2) << 4) + 8, (ChunkKey.z(heartChunk) << 4) + 8);
		TestSupport.useOnTop(leader, level, east, GuildyItems.extensionFlag(guild));
		long eastChunk = ChunkKey.ofBlock(east.getX(), east.getZ());
		helper.assertTrue(guild.owns(eastChunk) && guild.claimCount() == founding + 1, "adjacent extension claimed");

		// A diagonal chunk not touching the territory is refused.
		BlockPos diagonal = TestSupport.pad(level, ((ChunkKey.x(heartChunk) + 3) << 4) + 8, ((ChunkKey.z(heartChunk) + 3) << 4) + 8);
		TestSupport.useOnTop(leader, level, diagonal, GuildyItems.extensionFlag(guild));
		helper.assertFalse(guild.owns(ChunkKey.ofBlock(diagonal.getX(), diagonal.getZ())), "disconnected chunk refused");

		// Unclaiming the heart chunk is refused, the extension chunk is released with a refunded flag.
		helper.assertTrue(ClaimService.unclaim(leader, guild, heartChunk) != null, "heart chunk cannot be unclaimed");
		leader.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertTrue(ClaimService.unclaim(leader, guild, eastChunk) == null && !guild.owns(eastChunk), "edge chunk unclaimed");
		helper.assertTrue(leader.getInventory().contains(s -> GuildyItems.is(s, GuildyItems.KIND_EXTENSION_FLAG)), "flag refunded");

		// Abandon: no claims, no heart, banner removed, founding flag returned.
		helper.assertTrue(ClaimService.abandonTerritory(leader, guild) == null, "leader can abandon");
		helper.assertTrue(guild.claimCount() == 0 && guild.heart == null, "territory cleared");
		helper.assertFalse(level.getBlockState(heart).getBlock() instanceof AbstractBannerBlock, "heart banner removed");
		helper.assertTrue(leader.getInventory().contains(s -> GuildyItems.is(s, GuildyItems.KIND_FOUNDING_FLAG)), "founding flag returned");

		GuildManager.disband(guild);
		helper.succeed();
	}

	/** Two guilds: a second base too close to the first is refused, a farther one is accepted. */
	@GameTest(maxTicks = 100)
	public void neighbourGuildsKeepTheirDistance(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level);
		ServerPlayer first = TestSupport.player(helper, ground);
		Guild a = TestSupport.foundGuild(helper, first, ground, "Near");

		// founding radius 1 + min distance 3: a heart 4 chunks away puts both 3x3 squares 2 chunks apart -> refused.
		BlockPos tooClose = TestSupport.pad(level, ground.getX() + 4 * 16, ground.getZ());
		ServerPlayer second = TestSupport.player(helper, tooClose);
		Guild b = GuildManager.create(TestSupport.uniqueName("Far"), second);
		TestSupport.useOnTop(second, level, tooClose, GuildyItems.foundingFlag(b));
		helper.assertFalse(b.isFounded(), "a base 4 chunks away must be refused");

		BlockPos farEnough = TestSupport.pad(level, ground.getX() + 6 * 16, ground.getZ());
		TestSupport.useOnTop(second, level, farEnough, GuildyItems.foundingFlag(b));
		helper.assertTrue(b.isFounded(), "a base 6 chunks away is accepted");
		helper.assertTrue(a.claims().stream().noneMatch(b::owns), "no shared chunk");

		GuildManager.disband(a);
		GuildManager.disband(b);
		helper.succeed();
	}
}
