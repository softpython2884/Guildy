package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerRecord;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;

/**
 * Villager scenario: forced candidate, acceptance by a member command, then the sorter moves logs from the
 * deposit chest to the "Bois" category chest.
 */
public class VillagerGameTests {

	@GameTest(maxTicks = 2400)
	public void candidateAcceptedAndSorterWorks(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		TestSupport.console(level, "time set 1000");
		BlockPos ground = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Vill");

		TestSupport.console(level, "guild admin villager spawn " + guild.name);
		level.getServer().getCommands().performPrefixedCommand(leader.createCommandSourceStack(), "guild villagers accept");
		helper.assertTrue(guild.villagers.members.size() == 1, "candidate accepted as guild villager");
		VillagerRecord record = guild.villagers.members.values().iterator().next();

		BlockPos deposit = ground.offset(4, 1, 0);
		BlockPos wood = ground.offset(-4, 1, 0);
		level.setBlockAndUpdate(deposit, Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(wood, Blocks.CHEST.defaultBlockState());
		((Container) level.getBlockEntity(deposit)).setItem(0, new ItemStack(Items.OAK_LOG, 16));
		guild.villagers.chests.add(new StorageChest(deposit, StorageChest.Role.DEPOSIT, ""));
		guild.villagers.chests.add(new StorageChest(wood, StorageChest.Role.CATEGORY, "Bois"));

		Method setTask = Class.forName("dev.guildy.villager.VillagerManager")
				.getDeclaredMethod("setTask", Guild.class, VillagerRecord.class, Task.class, ServerPlayer.class);
		setTask.setAccessible(true);
		setTask.invoke(null, guild, record, Task.SORTER, leader);

		helper.succeedWhen(() -> {
			Container container = (Container) level.getBlockEntity(wood);
			int logs = 0;
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (stack.is(Items.OAK_LOG)) {
					logs += stack.getCount();
				}
			}
			helper.assertTrue(logs == 16, "waiting for the sorter to move 16 logs into the 'Bois' chest (now " + logs + ")");
			GuildManager.disband(guild);
		});
	}
}
