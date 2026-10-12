package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.ChunkLoadingConfig;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import dev.guildy.villager.WorkChunkLoader;
import dev.guildy.villager.WorkZone;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chunk loading of working villagers (villagers.json5 → chunkLoading): a farmer whose field zone lies 7 chunks from the
 * base (outside the base's forced chunks and their loaded rings), its only member online but 2048 blocks away.
 */
public class ChunkLoadGameTests {

	/**
	 * Mode "never": the field is not loaded, nothing is harvested. Mode "online": the field is kept active, the farmer
	 * walks there and harvests. Back to "never": every ticket is released, the field stops ticking (the farmer freezes
	 * on its way back). "online" again: the farmer, reloaded from its last known position, brings the wheat to the
	 * deposit chest of the base.
	 */
	@GameTest(maxTicks = 7200)
	public void farmerKeepsWorkingFarFromPlayers(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ChunkLoadingConfig config = VillagerModule.config().chunkLoading;
		String previousMode = config.mode;
		config.mode = "never";
		WorkChunkLoader.refresh();

		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Charge");
		Guild guild = recruit.guild();
		VillagerRecord record = recruit.record();
		BlockPos ground = recruit.ground();
		// 112 blocks = 7 chunks east: the base forces 3 chunks around it (loaded up to 5), the field is beyond.
		BlockPos field = TestSupport.pad(level, ground.getX() + 112, ground.getZ());
		TestSupport.useOnTop(recruit.leader(), level, field, ZoneGameTests.fanion(guild, "FARM"));
		helper.assertTrue(guild.villagers.zones.size() == 1, "field zone created by planting its fanion");
		WorkZone zone = guild.villagers.zones.getFirst();
		List<BlockPos> wheat = crops(level, field.offset(3, 0, 3));
		helper.assertTrue(zone.contains(wheat.getFirst()), "the wheat is in the zone");
		record.zoneId = zone.id;
		ZoneGameTests.setTask(recruit, Task.FARMER);
		// The only member stays online, far from the base and the field.
		ServerPlayer leader = recruit.leader();
		leader.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 2048.5, Set.of(), 0, 0, true);

		int[] phase = {0};
		long[] since = {helper.getTick()};
		helper.succeedWhen(() -> {
			if (leader.position().distanceToSqr(Vec3.atCenterOf(field)) < 1000.0 * 1000.0) {
				helper.fail("the member must stay far from the field");
			}
			if (phase[0] == 0) {
				if (!allRipe(level, wheat)) {
					helper.fail("mode never: the field out of the players' reach must not be worked");
				}
				helper.assertTrue(helper.getTick() - since[0] >= 400, "mode never: watching the farmer for 20 seconds");
				if (level.isPositionEntityTicking(wheat.getFirst()) || WorkChunkLoader.chunks(guild) != 0) {
					helper.fail("mode never: nothing may be kept active");
				}
				config.mode = "online";
				WorkChunkLoader.refresh();
				phase[0] = 1;
			}
			if (phase[0] == 1) {
				helper.assertTrue(WorkChunkLoader.chunks(guild) > 0, "mode online: waiting for chunks kept active for the guild");
				helper.assertTrue(!anyRipe(level, wheat) && record.stats.crops >= 3,
						"mode online: waiting for the far wheat to be harvested (" + record.stats.crops + ")");
				config.mode = "never";
				WorkChunkLoader.refresh();
				if (WorkChunkLoader.tickets() != 0 || WorkChunkLoader.chunks(guild) != 0) {
					helper.fail("mode never: every ticket is released at once");
				}
				phase[0] = 2;
			}
			if (phase[0] == 2) {
				helper.assertTrue(!level.isPositionEntityTicking(wheat.getFirst()), "mode never: waiting for the field to stop ticking");
				Entity villager = level.getEntity(record.uuid);
				helper.assertTrue(villager == null || !level.isPositionEntityTicking(villager.blockPosition()),
						"mode never: waiting for the farmer to stop ticking");
				config.mode = "online";
				WorkChunkLoader.refresh();
				phase[0] = 3;
			}
			helper.assertTrue(count(level, recruit.deposit()) >= 3, "mode online: waiting for the wheat in the deposit chest of the base");
			helper.assertTrue(WorkChunkLoader.kept(record.uuid), "the farmer's chunks are kept active");
			// The admin report runs (written to the server log).
			TestSupport.console(level, "guild admin villager chunks");
			config.mode = previousMode;
			WorkChunkLoader.refresh();
			GuildManager.disband(guild);
		});
	}

	/** Three ripe wheat crops on farmland in a row along x from {@code soil} (farmland level). Returns the crops. */
	private static List<BlockPos> crops(ServerLevel level, BlockPos soil) {
		CropBlock wheat = (CropBlock) Blocks.WHEAT;
		List<BlockPos> plants = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			BlockPos pos = soil.offset(i, 0, 0);
			level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
			level.setBlockAndUpdate(pos.above(), wheat.getStateForAge(wheat.getMaxAge()));
			plants.add(pos.above());
		}
		return plants;
	}

	private static boolean anyRipe(ServerLevel level, List<BlockPos> plants) {
		for (BlockPos pos : plants) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
				return true;
			}
		}
		return false;
	}

	private static boolean allRipe(ServerLevel level, List<BlockPos> plants) {
		for (BlockPos pos : plants) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
				return false;
			}
		}
		return true;
	}

	/** Wheat in the chest. */
	private static int count(ServerLevel level, BlockPos chest) {
		Container container = (Container) level.getBlockEntity(chest);
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(Items.WHEAT)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
