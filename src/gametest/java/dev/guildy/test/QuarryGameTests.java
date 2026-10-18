package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.Goal;
import dev.guildy.villager.MineLayout;
import dev.guildy.villager.MineState;
import dev.guildy.villager.Quarry;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerConfig;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import dev.guildy.villager.WorkZone;
import dev.guildy.villager.ZoneObjective;
import dev.guildy.villager.ZoneType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quarries ("Pierre et variantes" of a real mine), in a block of stone: miners dig a one-chunk quarry by benches of 3
 * blocks, all at the same time; everything natural of the square goes but the stair tower (shell and openings) and the
 * access causeway; a natural tree on the surface is cleared; a plank on the surface keeps its natural support column; no
 * miner ever takes damage (no fall from a bench). A stone mine dug before the quarries (staircase, old tunnels, a
 * cobblestone floor patch) becomes a quarry: its old tunnels are just air, the hole they made in the tower is patched.
 */
public class QuarryGameTests {
	private static final Logger LOGGER = LoggerFactory.getLogger("GuildyTest");
	/** Staircase east then south: the tower (8×8 with its shell) in the middle of the fanion's chunk. */
	private static final int ORIENTATION = 1;
	private static final int WIDTH = 2;
	private static final int BENCHES = 2;

	private record Scene(ServerLevel level, Guild guild, ServerPlayer leader, List<VillagerRecord> miners, List<BlockPos> chests, WorkZone zone,
			MineState mine, Quarry quarry, int x0, int z0, int top) {
		/** Feet height of the lowest bench checked. */
		int lowest() {
			return quarry.benchY(BENCHES - 1);
		}
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 16000)
	public void minersDigQuarryBenchesAroundTheirTower(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Carriere", 4);
		ServerLevel level = scene.level();
		int x0 = scene.x0();
		int z0 = scene.z0();
		int top = scene.top();
		Quarry quarry = scene.quarry();
		// A plank on the surface (a player's block): its column stays, down to the bottom.
		BlockPos plank = new BlockPos(x0 + 13, top + 1, z0 + 13);
		helper.assertTrue(quarry.inQuarry(plank.getX(), plank.getZ()), "the plank stands on the quarry");
		level.setBlockAndUpdate(plank, Blocks.OAK_PLANKS.defaultBlockState());
		// A natural tree on the surface: cleared, its column dug.
		BlockPos trunk = new BlockPos(x0 + 13, top, z0 + 1);
		helper.assertTrue(quarry.inQuarry(trunk.getX(), trunk.getZ()), "the tree stands on the quarry");
		level.setBlockAndUpdate(trunk, Blocks.GRASS_BLOCK.defaultBlockState());
		for (int dy = 1; dy <= 4; dy++) {
			level.setBlockAndUpdate(trunk.above(dy), Blocks.OAK_LOG.defaultBlockState());
		}
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
		level.setBlockAndUpdate(trunk.above(5), leaves);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					level.setBlockAndUpdate(trunk.offset(dx, 4, dz), leaves);
				}
			}
		}
		start(scene);
		Map<VillagerRecord, Long> lastMined = new HashMap<>();
		Map<VillagerRecord, Long> lastChange = new HashMap<>();
		long[] tick = {0};
		int[] best = {0};
		float[] health = {1.0f};
		helper.succeedWhen(() -> {
			tick[0]++;
			int busy = 0;
			for (VillagerRecord miner : scene.miners()) {
				long mined = miner.stats.mined;
				Long before = lastMined.put(miner, mined);
				if (before != null && mined > before) {
					lastChange.put(miner, tick[0]);
				}
				Long changed = lastChange.get(miner);
				if (changed != null && tick[0] - changed <= 200) {
					busy++;
				}
			}
			best[0] = Math.max(best[0], busy);
			health[0] = Math.min(health[0], health(scene));
			helper.assertTrue(health[0] >= 1.0f, "no miner ever takes damage (no fall from a bench)");
			assertDug(helper, scene, plank.getX(), plank.getZ());
			helper.assertTrue(best[0] >= 3, "several miners dig at the same time (best " + best[0] + ")");
			helper.assertTrue(level.getBlockState(plank).is(Blocks.OAK_PLANKS), "the plank is never broken");
			for (int y = scene.lowest(); y <= top; y++) {
				BlockPos support = new BlockPos(plank.getX(), y, plank.getZ());
				helper.assertTrue(level.getBlockState(support).is(Blocks.STONE), "a natural support column under the plank (" + support + ")");
			}
			helper.assertTrue(scene.mine().supportColumns.contains(BlockPos.asLong(plank.getX(), 0, plank.getZ())),
					"the plank's column is kept as a support");
			for (int dy = 1; dy <= 5; dy++) {
				helper.assertTrue(!level.getBlockState(trunk.above(dy)).is(Blocks.OAK_LOG), "the trunk of the natural tree is cleared");
			}
			assertTower(helper, scene);
			int cobblestone = stone(scene);
			helper.assertTrue(cobblestone > 0, "quarried stone kept");
			LOGGER.info("[GuildyTest] quarry: {} blocks quarried after {} ticks, {} miners busy at once, {} cobblestone kept, "
					+ "{} support column(s)", scene.mine().quarried, tick[0], best[0], cobblestone, scene.mine().supportColumns.size());
			GuildManager.disband(scene.guild());
		});
	}

	/**
	 * A stone mine dug before the quarries: its 2-wide staircase, an old tunnel crossing the tower's shell (with a cobblestone
	 * floor patch) and an old quarry room, level progress of the old galleries. It becomes a quarry: the old progress is
	 * dropped, the tunnels are just air, the patch keeps its column, the hole in the shell is patched.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 16000)
	public void stoneMineDugBeforeBecomesAQuarry(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Ancienne", 3);
		ServerLevel level = scene.level();
		int x0 = scene.x0();
		int z0 = scene.z0();
		int top = scene.top();
		MineState mine = scene.mine();
		WorkZone zone = scene.zone();
		BlockState air = Blocks.AIR.defaultBlockState();
		int side = MineLayout.clampSide(VillagerModule.config().realMining.stairSide);
		int steps = 9;
		for (int k = 0; k < steps; k++) {
			for (MineLayout.Cell cell : MineLayout.stepCells(zone.pos, ORIENTATION, side, WIDTH, k)) {
				for (int i = 0; i < cell.height(); i++) {
					level.setBlock(cell.feet().above(i), air, Block.UPDATE_CLIENTS);
				}
			}
		}
		mine.stairsDone = steps;
		mine.widened = new ArrayList<>(List.of((1L << steps) - 1));
		MineState.Level old = new MineState.Level();
		old.y = top - 4;
		old.done = new ArrayList<>(List.of(1, 2, 3));
		mine.levels.add(old);
		mine.layoutKey = "old-stone-mine";
		// An old tunnel from the south side of the stairs, through the shell, 2 high; a cobblestone patch in its floor.
		for (int z = z0 + 10; z <= z0 + 15; z++) {
			level.setBlock(new BlockPos(x0 + 6, top - 4, z), air, Block.UPDATE_CLIENTS);
			level.setBlock(new BlockPos(x0 + 6, top - 3, z), air, Block.UPDATE_CLIENTS);
		}
		BlockPos patch = new BlockPos(x0 + 6, top - 5, z0 + 13);
		level.setBlockAndUpdate(patch, Blocks.COBBLESTONE.defaultBlockState());
		// An old quarry room.
		for (int x = x0 + 12; x <= x0 + 14; x++) {
			for (int z = z0 + 12; z <= z0 + 14; z++) {
				for (int y = top - 5; y <= top - 3; y++) {
					level.setBlock(new BlockPos(x, y, z), air, Block.UPDATE_CLIENTS);
				}
			}
		}
		start(scene);
		float[] health = {1.0f};
		helper.succeedWhen(() -> {
			health[0] = Math.min(health[0], health(scene));
			helper.assertTrue(health[0] >= 1.0f, "no miner ever takes damage");
			for (MineState.Level level0 : mine.levels) {
				helper.assertTrue(level0.done.isEmpty(), "the progress of the old galleries is dropped");
			}
			assertDug(helper, scene, patch.getX(), patch.getZ());
			helper.assertTrue(level.getBlockState(patch).is(Blocks.COBBLESTONE), "the cobblestone patch is never broken");
			helper.assertTrue(mine.supportColumns.contains(BlockPos.asLong(patch.getX(), 0, patch.getZ())), "its column is kept");
			for (int y = top - 4; y <= top - 3; y++) {
				helper.assertTrue(!level.getBlockState(new BlockPos(x0 + 6, y, z0 + 10)).isAir(), "the old hole in the tower's shell is patched");
			}
			assertTower(helper, scene);
			LOGGER.info("[GuildyTest] old stone mine as a quarry: {} blocks quarried, stairs {}", mine.quarried, mine.stairsDone);
			GuildManager.disband(scene.guild());
		});
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * A block of stone 14 high around the chunk of the mine (the test world is flat), a guild founded west of it with
	 * {@code count} villagers and two deposit chests, a mine zone of the chunk (radius 0) planted on the stone, objective
	 * "Pierre et variantes" two benches deep, 2-wide staircase planned east then south.
	 */
	private static Scene scene(GameTestHelper helper, String prefix, int count) {
		ServerLevel level = helper.getLevel();
		VillagerConfig.RealMining real = VillagerModule.config().realMining;
		real.ticksPerBlock = 5;
		TestSupport.console(level, "time set 1000");
		BlockPos base = TestSupport.region(level, 4);
		TestSupport.forceChunks(level, base, 3);
		int height = 14;
		int x0 = base.getX() & ~15;
		int z0 = base.getZ() & ~15;
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = x0 - 24; x <= x0 + 19; x++) {
			for (int z = z0 - 4; z <= z0 + 19; z++) {
				for (int dy = 0; dy < height + 8; dy++) {
					level.setBlock(new BlockPos(x, base.getY() + dy, z), dy < height ? stone : air, Block.UPDATE_CLIENTS);
				}
			}
		}
		int top = base.getY() + height - 1;
		BlockPos founding = new BlockPos(x0 - 12, top, z0 + 8);
		ServerPlayer leader = TestSupport.player(helper, founding);
		Guild guild = TestSupport.foundGuild(helper, leader, founding, prefix);
		while (VillagerModule.config().maxVillagers(guild.level) < count) {
			guild.level++;
		}
		for (int i = 0; i < count; i++) {
			TestSupport.console(level, "guild admin villager spawn " + guild.name);
			level.getServer().getCommands().performPrefixedCommand(leader.createCommandSourceStack(), "guild villagers accept");
		}
		List<VillagerRecord> miners = new ArrayList<>(guild.villagers.members.values());
		helper.assertTrue(miners.size() == count, "candidates accepted as guild villagers");
		List<BlockPos> chests = List.of(new BlockPos(x0 - 4, top + 1, z0 + 5), new BlockPos(x0 - 4, top + 1, z0 + 6));
		for (BlockPos chest : chests) {
			level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
			guild.villagers.chests.add(new StorageChest(chest, StorageChest.Role.DEPOSIT, ""));
		}
		// The fanion near the north-west corner of the chunk: room all around the tower.
		BlockPos fanionGround = new BlockPos(x0 + 3, top, z0 + 4);
		TestSupport.useOnTop(leader, level, fanionGround, ZoneGameTests.fanion(guild, "MINE"));
		helper.assertTrue(guild.villagers.zones.size() == 1, "mine zone created by planting its fanion");
		WorkZone zone = guild.villagers.zones.getFirst();
		helper.assertTrue(zone.type == ZoneType.MINE && zone.isRealMine(), "a new mine digs for real");
		zone.radius = 0;
		zone.objective = ZoneObjective.STONE;
		int side = MineLayout.clampSide(real.stairSide);
		helper.assertTrue(MineLayout.fits(zone.pos, ORIENTATION, side, WIDTH, zone.bounds()), "the staircase fits the zone");
		MineState mine = zone.mine();
		mine.targetY = zone.pos.getY() - real.quarryBenchHeight * BENCHES;
		mine.origin = zone.pos;
		mine.stairSide = side;
		mine.orientation = ORIENTATION;
		mine.stairWidth = WIDTH;
		mine.format = MineState.FORMAT;
		leader.closeContainer();
		Quarry quarry = new Quarry(zone.pos, ORIENTATION, side, WIDTH, zone.bounds(), new Quarry.Settings(real.quarryBenchHeight,
				real.quarryMargin, real.quarryAccessWidth, real.quarryStripLength, real.quarryClearAbove, real.torchInterval));
		return new Scene(level, guild, leader, miners, chests, zone, mine, quarry, x0, z0, top);
	}

	/** Every miner to the mine, continuous work. */
	private static void start(Scene scene) throws ReflectiveOperationException {
		Method setTask = Class.forName("dev.guildy.villager.VillagerManager")
				.getDeclaredMethod("setTask", Guild.class, VillagerRecord.class, Task.class, ServerPlayer.class);
		setTask.setAccessible(true);
		for (VillagerRecord miner : scene.miners()) {
			miner.zoneId = scene.zone().id;
			setTask.invoke(null, scene.guild(), miner, Task.MINER, scene.leader());
			miner.goal = Goal.CONTINUOUS;
		}
	}

	/** Lowest health ratio of the miners now; the day goes on (the miners would go up before the night). */
	private static float health(Scene scene) {
		if (Math.floorMod(scene.level().getOverworldClockTime(), 24000L) > 8000) {
			TestSupport.console(scene.level(), "time set 1000");
		}
		float lowest = 1.0f;
		for (VillagerRecord miner : scene.miners()) {
			if (scene.level().getEntity(miner.uuid) instanceof LivingEntity villager) {
				lowest = Math.min(lowest, villager.getHealth() / villager.getMaxHealth());
			}
		}
		return lowest;
	}

	/** Every block of the quarry columns, from the lowest bench checked to the ground, is dug (floor torches aside). */
	private static void assertDug(GameTestHelper helper, Scene scene, int keptX, int keptZ) {
		int left = 0;
		BlockPos sample = null;
		for (int x = scene.x0(); x <= scene.x0() + 15; x++) {
			for (int z = scene.z0(); z <= scene.z0() + 15; z++) {
				if (!scene.quarry().inQuarry(x, z) || (x == keptX && z == keptZ)) {
					continue;
				}
				for (int y = scene.lowest(); y <= scene.top(); y++) {
					BlockState state = scene.level().getBlockState(new BlockPos(x, y, z));
					if (!state.isAir() && !state.is(Blocks.TORCH)) {
						left++;
						sample = new BlockPos(x, y, z);
					}
				}
			}
		}
		helper.assertTrue(left == 0, "waiting for the benches to be dug (" + left + " blocks left, e.g. " + sample + ", "
				+ scene.mine().quarried + " quarried, stairs " + scene.mine().stairsDone + ")");
	}

	/** The tower stands: its shell is rock but for the openings onto the benches; the causeway too. */
	private static void assertTower(GameTestHelper helper, Scene scene) {
		Quarry quarry = scene.quarry();
		for (int x = scene.x0() - 1; x <= scene.x0() + 16; x++) {
			for (int z = scene.z0() - 1; z <= scene.z0() + 16; z++) {
				boolean shell = quarry.inShell(x, z);
				boolean causeway = quarry.inCauseway(x, z) && !quarry.inTower(x, z) && quarry.inSquare(x, z);
				if (!shell && !causeway) {
					continue;
				}
				for (int y = scene.lowest(); y <= scene.top(); y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (shell && quarry.openingWall(pos, 64)) {
						continue;
					}
					helper.assertTrue(!scene.level().getBlockState(pos).isAir(), (shell ? "the tower's shell" : "the causeway") + " stays (" + pos + ")");
				}
			}
		}
	}

	/** Cobblestone kept: in the deposit chests and in the miners' bags. */
	private static int stone(Scene scene) {
		int total = 0;
		for (BlockPos chest : scene.chests()) {
			total += count(scene.level(), chest, Items.COBBLESTONE);
		}
		for (VillagerRecord miner : scene.miners()) {
			for (ItemStack stack : miner.carried) {
				total += stack.is(Items.COBBLESTONE) ? stack.getCount() : 0;
			}
		}
		return total;
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
