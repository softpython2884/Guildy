package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.structure.BlockKey;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * "Panneau du village": validated on any full block, its managed sign shows the guild name and level.
 */
public class VillageSignGameTests {

	@GameTest(maxTicks = 300)
	public void villageSignOnAnyFullBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 10);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Signville");

		BlockKey any = BlockKey.parse(BlockKey.ANY_FULL_BLOCK);
		helper.assertTrue(any != null && any.isAny(), "'*' key parsed");
		helper.assertTrue(any.test(Blocks.MOSSY_COBBLESTONE.defaultBlockState()), "any full block matches mossy cobblestone");
		helper.assertFalse(any.test(Blocks.AIR.defaultBlockState()), "air never matches");
		helper.assertFalse(any.test(Blocks.OAK_SLAB.defaultBlockState()), "a slab is not a full block");

		BlockPos block = ground.offset(3, 1, 0);
		level.setBlockAndUpdate(block, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
		leader.setItemInHand(InteractionHand.MAIN_HAND, GuildyItems.plan("village_sign", Component.literal("village_sign"), List.of()));
		Vec3 hit = Vec3.atCenterOf(block).relative(Direction.NORTH, 0.5);
		UseBlockCallback.EVENT.invoker().interact(leader, level, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.NORTH, block, false));
		helper.assertTrue(StructureManager.isBuilt(guild, "village_sign"), "village sign validated");
		BuiltStructure built = StructureManager.builtOf(guild, "village_sign").getFirst();
		helper.assertTrue(built.signs.size() == 1, "one managed sign, got " + built.signs.size());

		helper.succeedWhen(() -> {
			SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(built.signs.getFirst());
			helper.assertTrue(sign != null, "sign placed");
			List<Component> lines = sign.getText(SignTextSlot.FRONT).getMessages(false);
			helper.assertTrue(lines.get(0).getString().contains("Signville"), "first line shows the guild name, got '" + lines.get(0).getString() + "'");
			helper.assertTrue(lines.get(1).getString().contains(String.valueOf(guild.level)), "second line shows the level");
			GuildManager.disband(guild);
		});
	}
}
