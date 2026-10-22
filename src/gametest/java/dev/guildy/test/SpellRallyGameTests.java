package dev.guildy.test;

import dev.guildy.core.ScrollDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildRole;
import dev.guildy.scroll.GrimoireItems;
import dev.guildy.scroll.GrimoireRules;
import dev.guildy.scroll.ScrollItems;
import dev.guildy.scroll.ScrollModule;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ralliement as a guild uses it: every online member is called wherever they are (2000 blocks away, in the Nether), a click
 * on [Rejoindre] brings them next to the caster, across dimensions; when nobody can be called, the refusal says why (nobody
 * else online, already next to the caster, asleep...) and the scroll stays intact.
 */
public class SpellRallyGameTests {
	private static final String ENV = "guildy-test:day";
	private static final String RALLY = "ralliement";

	/** Right click with {@code count} rally scrolls in the main hand; returns the stack in hand. */
	static ItemStack rightClick(GameTestHelper helper, ServerPlayer player, int count, boolean expectStart) {
		ItemStack stack = ScrollDrops.create(RALLY, count);
		helper.assertTrue(ScrollItems.isScroll(stack) && stack.getCount() == count, "rally scroll created");
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND);
		if (expectStart) {
			helper.assertTrue(result instanceof InteractionResult.Success && player.isUsingItem(), "incantation started, got " + result);
		} else {
			helper.assertTrue(result == InteractionResult.FAIL && !player.isUsingItem(), "refused, got " + result);
		}
		return player.getMainHandItem();
	}

	/** Casts the rally (2 scrolls in hand): exactly one consumed. */
	static void cast(GameTestHelper helper, ServerPlayer caster) {
		ItemStack stack = rightClick(helper, caster, 2, true);
		for (int i = 0; i < 400 && caster.isUsingItem(); i++) {
			caster.doTick();
		}
		helper.assertTrue(stack.getCount() == 1, "rally cast: one scroll consumed, " + stack.getCount() + " left");
	}

	/** A stone floor with room above at (x, y, z) of {@code level}; returns the floor block. */
	static BlockPos floor(ServerLevel level, int x, int y, int z) {
		level.getChunk(x >> 4, z >> 4);
		BlockPos ground = new BlockPos(x, y, z);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(ground.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int dy = 1; dy <= 3; dy++) {
					level.setBlock(ground.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		return ground;
	}

	@GameTest(environment = ENV, maxTicks = 300)
	public void rallyCallsMembersFarAwayAndInTheNether(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 6);
		TestSupport.forceChunks(level, ground, 2);
		// The leader founds the guild, then travels 2000 blocks away in the Overworld.
		ServerPlayer wanderer = TestSupport.survivor(helper, ground, TestSupport.uniqueName("loin"));
		Guild guild = TestSupport.foundGuild(helper, wanderer, ground.west(4), "Cor");
		BlockPos far = TestSupport.pad(level, ground.getX() + 2000, ground.getZ());
		wanderer.teleportTo(level, far.getX() + 0.5, far.getY() + 1, far.getZ() + 0.5, Set.of(), 0, 0, true);
		// A plain member (not the leader) sounds the rally.
		ServerPlayer caster = TestSupport.survivor(helper, ground, TestSupport.uniqueName("cor"));
		GuildManager.addMember(guild, caster.getUUID(), caster.getName().getString());
		helper.assertTrue(guild.role(caster.getUUID()) == GuildRole.MEMBER && GuildManager.of(caster) == guild, "a plain member of the guild");
		// Another member in the Nether.
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "the Nether exists");
		BlockPos hell = floor(nether, ground.getX() / 8, 70, ground.getZ() / 8);
		ServerPlayer miner = TestSupport.survivor(helper, ground.north(3), TestSupport.uniqueName("nether"));
		miner.teleportTo(nether, hell.getX() + 0.5, hell.getY() + 1, hell.getZ() + 0.5, Set.of(), 0, 0, true);
		helper.assertTrue(miner.level() == nether, "a member in the Nether");
		GuildManager.addMember(guild, miner.getUUID(), miner.getName().getString());

		cast(helper, caster);
		String farCommand = ScrollModule.pendingRally(wanderer.getUUID());
		String netherCommand = ScrollModule.pendingRally(miner.getUUID());
		helper.assertTrue(farCommand != null, "the member 2000 blocks away received [Rejoindre]");
		helper.assertTrue(netherCommand != null, "the member in the Nether received [Rejoindre]");
		helper.assertTrue(miner.level() == nether && wanderer.position().distanceTo(Vec3.atCenterOf(far)) < 3, "nobody moved before clicking");

		// The Nether member clicks: next to the caster, in the Overworld.
		TestSupport.console(level, "execute as " + miner.getStringUUID() + " run " + netherCommand);
		helper.assertTrue(miner.level() == level, "the Nether member came back to the Overworld");
		helper.assertTrue(miner.position().distanceTo(caster.position()) < 3.5, "next to the caster: " + miner.position() + " / " + caster.position());
		// The far member clicks too.
		TestSupport.console(level, "execute as " + wanderer.getStringUUID() + " run " + farCommand);
		helper.assertTrue(wanderer.position().distanceTo(caster.position()) < 3.5, "the far member joined: " + wanderer.position());
		helper.assertTrue(ScrollModule.pendingRally(miner.getUUID()) == null && ScrollModule.pendingRally(wanderer.getUUID()) == null,
				"both joined: nothing pending");
		helper.runAfterDelay(5, () -> {
			GuildManager.disband(guild);
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 300)
	public void rallyRefusalSaysWhyNobodyIsCalled(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 6);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer caster = TestSupport.survivor(helper, ground, TestSupport.uniqueName("seul"));
		Guild guild = TestSupport.foundGuild(helper, caster, ground.west(4), "Echo");
		caster.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), 0, 0, true);
		// A member who is offline: nobody else online.
		GuildManager.addMember(guild, UUID.randomUUID(), "Absent");
		String alone = ScrollModule.rallyRefusal(caster);
		helper.assertTrue(alone != null && alone.contains("Aucun autre membre") && alone.contains("en ligne"), "nobody else online: " + alone);
		ItemStack hand = rightClick(helper, caster, 2, false);
		helper.assertTrue(hand.getCount() == 2, "refused: the scroll is intact");

		// A member right next to the caster: already there.
		ServerPlayer neighbour = TestSupport.survivor(helper, ground.east(2), TestSupport.uniqueName("voisin"));
		GuildManager.addMember(guild, neighbour.getUUID(), neighbour.getName().getString());
		String near = ScrollModule.rallyRefusal(caster);
		helper.assertTrue(near != null && near.contains("(1)") && near.contains("moins de 8 blocs"), "already next to the caster: " + near);
		hand = rightClick(helper, caster, 2, false);
		helper.assertTrue(hand.getCount() == 2, "refused again: the scroll is intact");

		// Plus a member asleep 40 blocks away: both reasons, with their numbers.
		BlockPos bed = TestSupport.pad(level, ground.getX() + 40, ground.getZ());
		ServerPlayer sleeper = TestSupport.survivor(helper, bed, TestSupport.uniqueName("dort"));
		GuildManager.addMember(guild, sleeper.getUUID(), sleeper.getName().getString());
		// Asleep where they stand (no bed needed: the mock player is not ticked, nothing wakes them up).
		sleeper.setSleepingPos(bed.above());
		helper.assertTrue(sleeper.isSleeping(), "the member sleeps");
		String mixed = ScrollModule.rallyRefusal(caster);
		helper.assertTrue(mixed != null && mixed.contains("1 déjà à moins de 8 blocs") && mixed.contains("1 endormi"), "both reasons: " + mixed);

		// The sleeper wakes up: the neighbour (a plain member) sounds the rally from a grimoire page; only the far member is called
		// (the leader stands next to the neighbour).
		sleeper.stopSleeping();
		helper.assertTrue(ScrollModule.rallyRefusal(caster) == null, "someone to call");
		ItemStack grimoire = GrimoireItems.create(new GrimoireRules.Pages(List.of(RALLY), 0));
		neighbour.setItemInHand(InteractionHand.MAIN_HAND, grimoire);
		neighbour.getFoodData().setFoodLevel(20);
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(neighbour, level, InteractionHand.MAIN_HAND);
		helper.assertTrue(result instanceof InteractionResult.Success && neighbour.isUsingItem(), "grimoire incantation started, got " + result);
		for (int i = 0; i < 400 && neighbour.isUsingItem(); i++) {
			neighbour.doTick();
		}
		helper.assertTrue(neighbour.getCooldowns().isOnCooldown(grimoire), "the rally sounded from the grimoire");
		helper.assertTrue(ScrollModule.pendingRally(sleeper.getUUID()) != null, "the far member is called");
		helper.assertTrue(ScrollModule.pendingRally(caster.getUUID()) == null, "not the leader already there");
		helper.runAfterDelay(5, () -> {
			GuildManager.disband(guild);
			helper.succeed();
		});
	}
}
