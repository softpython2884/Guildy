package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.Goal;
import dev.guildy.villager.MineLayout;
import dev.guildy.villager.MineState;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import dev.guildy.villager.WorkZone;
import dev.guildy.villager.ZoneType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Real mines, in a block of stone built on the flat test world: the spiral staircase of a MINE zone is dug from its
 * fanion; a planks block buried in its way is never broken (the stairs stop above it, the galleries start there); the
 * villager walks the spiral down and back up through every corner; a water source next to a step is sealed; two miners
 * share the mine (stairs handed over, galleries split) without deadlock. Mined stone ends in the deposit chest.
 */
public class MineGameTests {
	private static final Logger LOGGER = LoggerFactory.getLogger("GuildyTest");
	/** Staircase planned by the scenes: east first, then south (fits the fanion's chunk). */
	private static final int ORIENTATION = 1;

	private record Scene(ServerLevel level, Guild guild, ServerPlayer leader, List<VillagerRecord> miners, BlockPos deposit, WorkZone zone,
			MineState mine, int side) {
		MineLayout.Cell step(int k) {
			return MineLayout.stairCell(zone.pos, ORIENTATION, side, k);
		}
	}

	@GameTest(maxTicks = 4800)
	public void realMinerDigsStairsAndNeverBreaksPlanks(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Galerie", 10, 1, 6);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		// A planks block buried in the 5th step: never broken, the stairs stop above it.
		BlockPos planks = scene.step(4).feet().above();
		level.setBlockAndUpdate(planks, Blocks.OAK_PLANKS.defaultBlockState());
		// Iron ore in the wall of the 2nd step: seen from the stairs, mined as a vein.
		BlockPos ore = outsideStairs(scene, scene.step(1).feet().above());
		level.setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
		VillagerRecord miner = scene.miners().getFirst();
		startMining(scene, miner, 20);

		helper.succeedWhen(() -> {
			helper.assertTrue(level.getBlockState(planks).is(Blocks.OAK_PLANKS), "the planks block is never broken");
			helper.assertTrue(level.getBlockState(ore).isAir() && mine.oresFound >= 1, "waiting for the ore next to the stairs to be mined");
			for (int k = 0; k < 4; k++) {
				BlockPos feet = scene.step(k).feet();
				helper.assertTrue(level.getBlockState(feet).getCollisionShape(level, feet).isEmpty(), "waiting for stair step " + k);
			}
			helper.assertTrue(mine.stairsBlockedAt == 4, "the stairs stop above the planks (" + mine.stairsBlockedAt + ")");
			helper.assertTrue(mine.levelY != null && mine.levelY == scene.zone().pos.getY() - 4, "galleries where the stairs stop");
			int cobblestone = count(level, scene.deposit(), Items.COBBLESTONE);
			helper.assertTrue(cobblestone >= 19, "waiting for the mined stone in the deposit chest (" + cobblestone + ")");
			helper.assertTrue(count(level, scene.deposit(), Items.RAW_IRON) >= 1, "real drops: raw iron from the ore");
			helper.assertTrue(miner.task == Task.IDLE, "quota reached: the miner stops");
			helper.assertTrue(mine.blocksMined >= 20, "blocks counted for the zone");
			GuildManager.disband(scene.guild());
		});
	}

	/**
	 * A deeper spiral (15 steps, every corner, one turn under the first steps): the miner walks it down and back up to the
	 * deposit chest, and seals a water source found next to a step with a block of its bag.
	 */
	@GameTest(maxTicks = 6000)
	public void realMinerWalksTheSpiralAndSealsWater(GameTestHelper helper) throws Exception {
		int steps = 15;
		Scene scene = scene(helper, "Colimacon", 20, 1, steps);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		// Water source in the rock next to the 8th step, outside the staircase.
		BlockPos waterPos = outsideStairs(scene, scene.step(7).feet());
		level.setBlockAndUpdate(waterPos, Blocks.WATER.defaultBlockState());
		VillagerRecord miner = scene.miners().getFirst();
		startMining(scene, miner, 45);

		int[] teleports = {0};
		double[][] last = {null};
		helper.succeedWhen(() -> {
			teleports[0] += jumped(level, miner, last);
			helper.assertTrue(mine.stairsDone == steps, "waiting for the whole staircase (" + mine.stairsDone + "/" + steps + ")");
			helper.assertTrue(level.getBlockState(waterPos).getFluidState().isEmpty() && !level.getBlockState(waterPos).isAir(),
					"the water next to the stairs is sealed with a block");
			for (int k = 0; k < steps; k++) {
				MineLayout.Cell cell = scene.step(k);
				for (int i = 0; i < cell.height(); i++) {
					helper.assertTrue(level.getBlockState(cell.feet().above(i)).getFluidState().isEmpty(), "no water in the stairs");
				}
			}
			helper.assertTrue(mine.deepestY != null && mine.deepestY == scene.zone().pos.getY() - steps, "down to the gallery level");
			helper.assertTrue(miner.task == Task.IDLE, "waiting for the miner to bring its quota back up");
			int cobblestone = count(level, scene.deposit(), Items.COBBLESTONE);
			helper.assertTrue(cobblestone >= 40, "mined stone deposited (" + cobblestone + ")");
			LOGGER.info("[GuildyTest] spiral miner fallback teleports: {}", teleports[0]);
			GuildManager.disband(scene.guild());
		});
	}

	/** Two miners: one digs the stairs while the other waits, the stairs change hands, the galleries are shared. */
	@GameTest(maxTicks = 6000)
	public void twoMinersShareTheMine(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Duo", 10, 2, 6);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		for (VillagerRecord miner : scene.miners()) {
			startMining(scene, miner, 14);
		}
		int[] teleports = {0};
		double[][] last = {null};
		helper.succeedWhen(() -> {
			teleports[0] += jumped(level, scene.miners().getFirst(), last);
			helper.assertTrue(mine.stairsDone == 6, "waiting for the staircase (" + mine.stairsDone + "/6)");
			for (VillagerRecord miner : scene.miners()) {
				helper.assertTrue(miner.task == Task.IDLE, "waiting for both miners to bring their quota (" + miner.name + ")");
			}
			int cobblestone = count(level, scene.deposit(), Items.COBBLESTONE);
			helper.assertTrue(cobblestone >= 28, "stone of both miners deposited (" + cobblestone + ")");
			helper.assertTrue(mine.blocksMined >= 28, "blocks counted for the zone (" + mine.blocksMined + ")");
			LOGGER.info("[GuildyTest] two miners fallback teleports (first miner): {}", teleports[0]);
			GuildManager.disband(scene.guild());
		});
	}

	/** Continuous work: bag full in the galleries → up the spiral to the chest (filler reserve kept) → back to the front. */
	@GameTest(maxTicks = 6000)
	public void realMinerGoesBackToTheFrontAfterDepositing(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Retour", 20, 1, 15);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		VillagerRecord miner = scene.miners().getFirst();
		startMining(scene, miner, 1);
		miner.goal = Goal.CONTINUOUS;
		// 7 stacks already in the bag (9 max): it is full once 64 stone blocks are mined, around the gallery level.
		for (int i = 0; i < 7; i++) {
			miner.carried.add(new ItemStack(Items.FLINT, 64));
		}
		long[] minedAtDeposit = {-1};
		helper.succeedWhen(() -> {
			int flint = count(level, scene.deposit(), Items.FLINT);
			if (minedAtDeposit[0] < 0 && flint == 7 * 64) {
				minedAtDeposit[0] = mine.blocksMined;
			}
			helper.assertTrue(minedAtDeposit[0] >= 64, "waiting for the full bag in the deposit chest (" + flint + " flint)");
			helper.assertTrue(count(level, scene.deposit(), Items.COBBLESTONE) > 0, "stone deposited too");
			int reserve = 0;
			for (ItemStack stack : miner.carried) {
				reserve += stack.is(Items.COBBLESTONE) ? stack.getCount() : 0;
			}
			helper.assertTrue(mine.blocksMined >= minedAtDeposit[0] + 10, "waiting for the miner to dig again after its trip");
			helper.assertTrue(reserve > 0 && miner.task == Task.MINER, "a filler reserve stays in the bag, still at work");
			GuildManager.disband(scene.guild());
		});
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * A block of stone {@code height} high (the test world is flat), a guild founded on top with {@code miners} villagers
	 * and a deposit chest, and a mine zone planted on the stone: square = the fanion's chunk, galleries {@code depth}
	 * blocks under the fanion, staircase planned east then south.
	 */
	private static Scene scene(GameTestHelper helper, String prefix, int height, int miners, int depth) throws Exception {
		ServerLevel level = helper.getLevel();
		VillagerModule.config().realMining.ticksPerBlock = 5;
		TestSupport.console(level, "time set 1000");
		BlockPos base = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, base, 3);
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int dx = -4; dx <= 19; dx++) {
			for (int dz = -12; dz <= 11; dz++) {
				for (int dy = 0; dy < height + 5; dy++) {
					level.setBlock(base.offset(dx, dy, dz), dy < height ? stone : air, Block.UPDATE_CLIENTS);
				}
			}
		}
		BlockPos top = base.above(height - 1);
		ServerPlayer leader = TestSupport.player(helper, top);
		Guild guild = TestSupport.foundGuild(helper, leader, top, prefix);
		List<VillagerRecord> records = new ArrayList<>();
		for (int i = 0; i < miners; i++) {
			TestSupport.console(level, "guild admin villager spawn " + guild.name);
			level.getServer().getCommands().performPrefixedCommand(leader.createCommandSourceStack(), "guild villagers accept");
		}
		records.addAll(guild.villagers.members.values());
		helper.assertTrue(records.size() == miners, "candidates accepted as guild villagers");
		BlockPos deposit = top.offset(4, 1, 0);
		level.setBlockAndUpdate(deposit, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(new StorageChest(deposit, StorageChest.Role.DEPOSIT, ""));

		TestSupport.useOnTop(leader, level, top.offset(8, 0, 4), ZoneGameTests.fanion(guild, "MINE"));
		helper.assertTrue(guild.villagers.zones.size() == 1, "mine zone created by planting its fanion");
		WorkZone zone = guild.villagers.zones.getFirst();
		helper.assertTrue(zone.type == ZoneType.MINE && zone.isRealMine(), "a new mine digs for real");
		zone.radius = 0;
		int side = MineLayout.clampSide(VillagerModule.config().realMining.stairSide);
		helper.assertTrue(MineLayout.fits(zone.pos, ORIENTATION, side, zone.bounds()), "the staircase fits the fanion's chunk");
		MineState mine = zone.mine();
		mine.targetY = zone.pos.getY() - depth;
		mine.origin = zone.pos;
		mine.stairSide = side;
		mine.orientation = ORIENTATION;
		leader.closeContainer();
		return new Scene(level, guild, leader, records, deposit, zone, mine, side);
	}

	/** A rock block next to {@code block} (horizontally) outside the staircase square. */
	private static BlockPos outsideStairs(Scene scene, BlockPos block) {
		for (BlockPos candidate : new BlockPos[]{block.north(), block.south(), block.east(), block.west()}) {
			boolean stairColumn = false;
			for (int[] column : MineLayout.footprint(scene.zone().pos, ORIENTATION, scene.side())) {
				stairColumn |= column[0] == candidate.getX() && column[1] == candidate.getZ();
			}
			if (!stairColumn && !candidate.equals(scene.zone().pos) && !candidate.equals(scene.zone().pos.below())) {
				return candidate;
			}
		}
		throw new IllegalStateException("no rock next to " + block);
	}

	/** Assigns the miner to the scene's mine with a quota mission of {@code amount} items. */
	private static void startMining(Scene scene, VillagerRecord miner, int amount) throws ReflectiveOperationException {
		miner.zoneId = scene.zone().id;
		miner.goal = Goal.QUOTA;
		miner.goalAmount = amount;
		miner.goalItem = "";
		Method setTask = Class.forName("dev.guildy.villager.VillagerManager")
				.getDeclaredMethod("setTask", Guild.class, VillagerRecord.class, Task.class, ServerPlayer.class);
		setTask.setAccessible(true);
		setTask.invoke(null, scene.guild(), miner, Task.MINER, scene.leader());
		miner.goal = Goal.QUOTA;
	}

	/** 1 when the villager moved more than 3 blocks since the last tick (a fallback teleport). */
	private static int jumped(ServerLevel level, VillagerRecord record, double[][] last) {
		Entity villager = level.getEntity(record.uuid);
		if (villager == null) {
			return 0;
		}
		double[] previous = last[0];
		last[0] = new double[]{villager.getX(), villager.getY(), villager.getZ()};
		return previous != null && villager.position().distanceToSqr(previous[0], previous[1], previous[2]) > 9.0 ? 1 : 0;
	}

	private static int count(ServerLevel level, BlockPos chest, Item item) {
		Container container = (Container) level.getBlockEntity(chest);
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
