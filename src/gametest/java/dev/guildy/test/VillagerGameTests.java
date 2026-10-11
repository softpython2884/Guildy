package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.Goal;
import dev.guildy.villager.MineMode;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerConfig;
import dev.guildy.villager.VillagerModule;
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

	/** Expedition: the villager leaves (removed from the world), comes back with loot, empties it; FILL ends the mission. */
	@GameTest(maxTicks = 2400)
	public void expeditionLeavesAndComesBack(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		VillagerConfig.Expedition exploration = VillagerModule.config().expeditions.exploration;
		exploration.durationSeconds = 3;
		exploration.lossChance = 0;
		exploration.rolls = 4;
		exploration.treasureMapChance = 0;
		Recruit recruit = recruit(helper, "Expe");
		recruit.record.goal = Goal.FILL;
		setTask(recruit, Task.EXPLORER);

		boolean[] left = {false};
		helper.succeedWhen(() -> {
			if (recruit.record.isAway()) {
				left[0] = true;
			}
			helper.assertTrue(left[0], "waiting for the villager to leave on its expedition");
			helper.assertTrue(!recruit.record.isAway() && recruit.record.task == Task.IDLE, "waiting for the return and the end of the mission");
			helper.assertTrue(level.getEntity(recruit.record.uuid) != null, "the very same villager (UUID) is back");
			helper.assertTrue(count(level, recruit.deposit) > 0 && recruit.record.carried.isEmpty(), "loot deposited in the deposit chest");
			helper.assertTrue(recruit.record.stats.expeditions == 1, "expedition counted");
			GuildManager.disband(recruit.guild);
		});
	}

	/** Miner in a simulated mine zone (fanion planted) with a quota of 5 items: brings them back, then stops (task IDLE). */
	@GameTest(maxTicks = 2400)
	public void minerStopsAfterQuota(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		VillagerModule.config().miner.tripSeconds = 1;
		Recruit recruit = recruit(helper, "Mine");
		TestSupport.useOnTop(recruit.leader, level, recruit.ground.offset(2, 0, 2), ZoneGameTests.fanion(recruit.guild, "MINE"));
		helper.assertTrue(recruit.guild.villagers.zones.size() == 1, "mine zone created by planting its fanion");
		recruit.guild.villagers.zones.getFirst().mine().mode = MineMode.SIMULATED;
		recruit.record.zoneId = recruit.guild.villagers.zones.getFirst().id;
		recruit.record.goal = Goal.QUOTA;
		recruit.record.goalAmount = 5;
		recruit.record.goalItem = "";
		setTask(recruit, Task.MINER);

		helper.succeedWhen(() -> {
			helper.assertTrue(recruit.record.task == Task.IDLE, "waiting for the miner to finish its quota");
			helper.assertTrue(count(level, recruit.deposit) >= 5, "at least 5 items deposited");
			GuildManager.disband(recruit.guild);
		});
	}

	private record Recruit(Guild guild, VillagerRecord record, ServerPlayer leader, BlockPos ground, BlockPos deposit) {
	}

	/** Founds a guild, recruits one villager and registers a deposit chest. */
	private static Recruit recruit(GameTestHelper helper, String prefix) {
		ServerLevel level = helper.getLevel();
		TestSupport.console(level, "time set 1000");
		BlockPos ground = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, prefix);
		TestSupport.console(level, "guild admin villager spawn " + guild.name);
		level.getServer().getCommands().performPrefixedCommand(leader.createCommandSourceStack(), "guild villagers accept");
		helper.assertTrue(guild.villagers.members.size() == 1, "candidate accepted as guild villager");
		BlockPos deposit = ground.offset(4, 1, 0);
		level.setBlockAndUpdate(deposit, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(new StorageChest(deposit, StorageChest.Role.DEPOSIT, ""));
		return new Recruit(guild, guild.villagers.members.values().iterator().next(), leader, ground, deposit);
	}

	private static void setTask(Recruit recruit, Task task) throws ReflectiveOperationException {
		Method setTask = Class.forName("dev.guildy.villager.VillagerManager")
				.getDeclaredMethod("setTask", Guild.class, VillagerRecord.class, Task.class, ServerPlayer.class);
		setTask.setAccessible(true);
		Goal goal = recruit.record.goal;
		setTask.invoke(null, recruit.guild, recruit.record, task, recruit.leader);
		recruit.record.goal = goal;
	}

	private static int count(ServerLevel level, BlockPos chest) {
		Container container = (Container) level.getBlockEntity(chest);
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			total += container.getItem(i).getCount();
		}
		return total;
	}
}
