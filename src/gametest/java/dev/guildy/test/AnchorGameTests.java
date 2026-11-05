package dev.guildy.test;

import dev.guildy.anchor.AnchorFlags;
import dev.guildy.anchor.AnchorTickets;
import dev.guildy.anchor.ChunkAnchor;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractBannerBlock;

import java.util.Set;

/**
 * Anchor flags (config.json5 → anchors): planted in the territory, the banner keeps its chunk square entity-ticking with
 * every player 2048 blocks away, the chunks count as near a player for mob spawning and a mob next to it does not
 * despawn; broken by the guild, the flag comes back and the chunks stop.
 */
public class AnchorGameTests {

	@GameTest(maxTicks = 1200)
	public void anchorKeepsChunksActiveLikeAPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Ancre");

		// Outside the 3 x 3 founding claim: refused.
		BlockPos far = TestSupport.pad(level, ground.getX() + 80, ground.getZ());
		helper.assertTrue(TestSupport.useOnTop(leader, level, far, AnchorFlags.create(guild)) == InteractionResult.FAIL,
				"an anchor flag is refused outside the territory");
		helper.assertTrue(guild.anchors.flags.isEmpty(), "no anchor outside the territory");

		// One chunk east of the heart chunk (still claimed): planted, active.
		BlockPos pad = TestSupport.pad(level, ground.getX() + 16, ground.getZ());
		helper.assertTrue(TestSupport.useOnTop(leader, level, pad, AnchorFlags.create(guild)) == InteractionResult.SUCCESS,
				"the anchor flag is planted in the territory");
		helper.assertTrue(guild.anchors.flags.size() == 1, "one anchor");
		ChunkAnchor anchor = guild.anchors.flags.getFirst();
		BlockPos banner = pad.above();
		helper.assertTrue(anchor.pos.equals(banner) && level.getBlockState(banner).getBlock() instanceof AbstractBannerBlock,
				"the banner stands on the pad");
		helper.assertTrue(AnchorTickets.state(guild, anchor) == AnchorTickets.State.ACTIVE, "the anchor is active");
		helper.assertTrue(AnchorTickets.tickets() >= 1, "a ticket is held");
		// Only one flag at level 1 (default): a second one is refused.
		BlockPos second = TestSupport.pad(level, ground.getX() - 16, ground.getZ());
		helper.assertTrue(TestSupport.useOnTop(leader, level, second, AnchorFlags.create(guild)) == InteractionResult.FAIL,
				"a second anchor flag is refused at level 1");

		// Everybody far away: 2 chunks east of the anchor chunk (radius 2, outside the claim) must keep ticking.
		leader.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 2048.5, Set.of(), 0, 0, true);
		BlockPos edge = new BlockPos(banner.getX() + 32, banner.getY(), banner.getZ());
		ChunkPos anchorChunk = new ChunkPos(anchor.chunkX(), anchor.chunkZ());
		boolean despawnMatters = level.getDifficulty() != Difficulty.PEACEFUL;
		Husk[] husk = {null};
		int[] phase = {0};
		long[] since = {0};
		helper.succeedWhen(() -> {
			if (phase[0] == 0) {
				helper.assertTrue(level.isPositionEntityTicking(banner) && level.isPositionEntityTicking(edge),
						"waiting for the anchor's chunks to be entity-ticking");
				helper.assertTrue(level.anyPlayerCloseEnoughForSpawning(anchorChunk), "the anchor chunk counts as near a player for spawning");
				if (despawnMatters) {
					// 128+ blocks from every player: vanilla would remove it at once; 6 blocks from the anchor it stays.
					Husk mob = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
					helper.assertTrue(mob != null, "husk created");
					mob.snapTo(banner.getX() + 6.5, banner.getY(), banner.getZ() + 0.5, 0, 0);
					level.addFreshEntity(mob);
					husk[0] = mob;
				}
				since[0] = helper.getTick();
				phase[0] = 1;
			}
			if (phase[0] == 1) {
				helper.assertTrue(helper.getTick() - since[0] >= 60, "watching the husk for 3 seconds");
				if (husk[0] != null) {
					helper.assertTrue(husk[0].isAlive() && !husk[0].isRemoved(), "a mob next to an anchor does not despawn");
					husk[0].discard();
				}
				// The guild takes it down: the flag comes back, the banner and the ticket go.
				leader.getInventory().clearContent();
				leader.gameMode.destroyBlock(banner);
				helper.assertTrue(guild.anchors.flags.isEmpty(), "the anchor is removed when its banner is broken");
				helper.assertTrue(!(level.getBlockState(banner).getBlock() instanceof AbstractBannerBlock), "the banner is gone");
				helper.assertTrue(carriesAnchorFlag(leader), "the flag goes back to the member who broke it");
				phase[0] = 2;
			}
			helper.assertTrue(!level.isPositionEntityTicking(edge), "waiting for the anchor's chunks to stop");
			helper.assertTrue(!level.anyPlayerCloseEnoughForSpawning(anchorChunk), "no spawning reach without the anchor");
			GuildManager.disband(guild);
		});
	}

	/** anchors.enabled = false: planted anchors stay but hold nothing; true again: they hold their chunks again. */
	@GameTest(maxTicks = 600)
	public void disabledAnchorsHoldNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Ancre");
		BlockPos pad = TestSupport.pad(level, ground.getX(), ground.getZ() + 16);
		helper.assertTrue(TestSupport.useOnTop(leader, level, pad, AnchorFlags.create(guild)) == InteractionResult.SUCCESS,
				"the anchor flag is planted");
		ChunkAnchor anchor = guild.anchors.flags.getFirst();
		GuildyConfig.Anchors config = GuildyServer.config().anchors;
		config.enabled = false;
		try {
			AnchorTickets.refresh();
			helper.assertTrue(AnchorTickets.state(guild, anchor) == AnchorTickets.State.DISABLED, "disabled on the server");
			helper.assertTrue(guild.anchors.flags.size() == 1, "the planted anchor is kept");
			helper.assertTrue(TestSupport.useOnTop(leader, level, TestSupport.pad(level, ground.getX() + 5, ground.getZ() + 5),
					AnchorFlags.create(guild)) == InteractionResult.FAIL, "no planting while disabled");
		} finally {
			config.enabled = true;
			AnchorTickets.refresh();
		}
		helper.assertTrue(AnchorTickets.state(guild, anchor) == AnchorTickets.State.ACTIVE, "active again");
		GuildManager.disband(guild);
		helper.assertTrue(!(level.getBlockState(anchor.pos).getBlock() instanceof AbstractBannerBlock),
				"a disbanded guild's anchor banner disappears");
		helper.succeed();
	}

	private static boolean carriesAnchorFlag(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (GuildyItems.is(inventory.getItem(i), GuildyItems.KIND_ANCHOR_FLAG)) {
				return true;
			}
		}
		return false;
	}
}
