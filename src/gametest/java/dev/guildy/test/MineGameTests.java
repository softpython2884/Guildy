package dev.guildy.test;

import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.Goal;
import dev.guildy.villager.MineLayout;
import dev.guildy.villager.MineState;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerConfig;
import dev.guildy.villager.VillagerManager;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real mines, in a block of stone built on the flat test world: the 2-wide spiral staircase of a MINE zone is dug from
 * its fanion; a planks block buried in its way is never broken (the stairs stop above it, a level is dug there); the
 * villager walks the spiral down and back up; a water source next to a step is sealed; several miners work at the same
 * time on different jobs; an ore in a gallery floor is taken; a vein crossing the zone border is followed outside but
 * stops at another guild's land; a wanted diorite cluster is mined and deposited. Mined stone ends in the deposit chest.
 */
public class MineGameTests {
	private static final Logger LOGGER = LoggerFactory.getLogger("GuildyTest");
	/** Staircase planned by the scenes: east first, then north (fits the fanion's chunk, 2 lanes). */
	private static final int ORIENTATION = 5;
	private static final int WIDTH = 2;

	private record Scene(ServerLevel level, Guild guild, ServerPlayer leader, List<VillagerRecord> miners, BlockPos deposit, WorkZone zone,
			MineState mine, MineLayout layout) {
		MineLayout.Cell step(int k) {
			return layout.stairs().get(k);
		}
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 4800)
	public void realMinerDigsStairsAndNeverBreaksPlanks(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Galerie", 12, 1, 9, 0);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		int fanionY = scene.zone().pos.getY();
		// A planks block buried in the 5th step: never broken, the stairs stop above it.
		BlockPos planks = scene.step(4).feet().above();
		level.setBlockAndUpdate(planks, Blocks.OAK_PLANKS.defaultBlockState());
		// Iron ore in the pillar next to the 2nd step: seen from the stairs, mined as a vein.
		BlockPos ore = beside(scene, scene.step(1).feet().above());
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
			MineLayout.Level stop = scene.layout().level(fanionY - 4);
			BlockPos landing = stop.segments().getFirst().cells().get(1).feet();
			helper.assertTrue(level.getBlockState(landing).isAir(), "a level where the stairs stop");
			int cobblestone = count(level, scene.deposit(), Items.COBBLESTONE);
			helper.assertTrue(cobblestone >= 15, "waiting for the mined stone in the deposit chest (" + cobblestone + ")");
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
	@GameTest(environment = "guildy-test:day", maxTicks = 6000)
	public void realMinerWalksTheSpiralAndSealsWater(GameTestHelper helper) throws Exception {
		int steps = 15;
		Scene scene = scene(helper, "Colimacon", 20, 1, steps, 0);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		// Water source in the pillar next to the 8th step, outside the staircase.
		BlockPos waterPos = beside(scene, scene.step(7).feet());
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
				for (MineLayout.Cell cell : scene.layout().step(k)) {
					for (int i = 0; i < cell.height(); i++) {
						helper.assertTrue(level.getBlockState(cell.feet().above(i)).getFluidState().isEmpty(), "no water in the stairs");
					}
				}
			}
			helper.assertTrue(mine.deepestY != null && mine.deepestY <= scene.zone().pos.getY() - steps, "down to the lowest level");
			helper.assertTrue(miner.task == Task.IDLE, "waiting for the miner to bring its quota back up");
			int cobblestone = count(level, scene.deposit(), Items.COBBLESTONE);
			helper.assertTrue(cobblestone >= 40, "mined stone deposited (" + cobblestone + ")");
			LOGGER.info("[GuildyTest] spiral miner fallback teleports: {}", teleports[0]);
			GuildManager.disband(scene.guild());
		});
	}

	/**
	 * Five miners in one mine with several levels: the work is split in small jobs (stairs, widening, landings,
	 * galleries of every level the stairs reached), so at least four of them dig at the same time.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 4000)
	public void severalMinersWorkAtOnce(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Equipe", 30, 5, 25, 1);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		for (VillagerRecord miner : scene.miners()) {
			startMining(scene, miner, 1);
			miner.goal = Goal.CONTINUOUS;
		}
		Map<VillagerRecord, Long> lastMined = new HashMap<>();
		Map<VillagerRecord, Long> lastChange = new HashMap<>();
		long[] tick = {0};
		int[] best = {0};
		int[] teleports = {0};
		Map<VillagerRecord, double[][]> positions = new HashMap<>();
		helper.succeedWhen(() -> {
			tick[0]++;
			int busy = 0;
			for (VillagerRecord miner : scene.miners()) {
				teleports[0] += jumped(level, miner, positions.computeIfAbsent(miner, m -> new double[][]{null}));
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
			helper.assertTrue(best[0] >= 4, "waiting for 4 miners digging within the same 10 s (" + busy + " now, levels "
					+ mine.levels.size() + ", stairs " + mine.stairsDone + ")");
			helper.assertTrue(mine.levels.size() >= 2, "several levels worked at once (" + mine.levels.size() + ")");
			openExploitation(scene);
			LOGGER.info("[GuildyTest] five miners: {} busy at once after {} ticks, {} blocks, fallback teleports (all miners): {}", best[0], tick[0],
					mine.blocksMined, teleports[0]);
			for (VillagerRecord miner : scene.miners()) {
				LOGGER.info("[GuildyTest] miner {}: {}", miner.name, status(level, miner));
			}
			GuildManager.disband(scene.guild());
		});
	}

	/**
	 * The maximum of a zone: 20 miners in one mine (3×3 chunks, ores and a wanted block here and there). Once the first
	 * levels are open, at least 15 of them dig within the same 10 seconds, and the 2-wide staircase keeps the fallback
	 * teleports rare.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 7000)
	public void twentyMinersShareOneMine(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Vingt", 40, 20, 33, 1);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		scene.zone().wishlist.add("minecraft:diorite");
		java.util.Random random = new java.util.Random(7);
		int[] bounds = scene.zone().bounds();
		int bottom = scene.zone().pos.getY() - 40;
		for (int i = 0; i < 120; i++) {
			BlockPos center = new BlockPos(bounds[0] + random.nextInt(bounds[2] - bounds[0] + 1), bottom + 2 + random.nextInt(30),
					bounds[1] + random.nextInt(bounds[3] - bounds[1] + 1));
			BlockState ore = switch (i % 3) {
				case 0 -> Blocks.IRON_ORE.defaultBlockState();
				case 1 -> Blocks.COAL_ORE.defaultBlockState();
				default -> Blocks.DIORITE.defaultBlockState();
			};
			for (int j = 0; j < 8; j++) {
				BlockPos pos = center.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
				if (!scene.layout().inStairColumns(pos.getX(), pos.getZ())) {
					level.setBlock(pos, ore, Block.UPDATE_CLIENTS);
				}
			}
		}
		for (VillagerRecord miner : scene.miners()) {
			startMining(scene, miner, 1);
			miner.goal = Goal.CONTINUOUS;
		}
		Map<VillagerRecord, Long> lastMined = new HashMap<>();
		Map<VillagerRecord, Long> lastChange = new HashMap<>();
		Map<VillagerRecord, double[][]> positions = new HashMap<>();
		long[] tick = {0};
		int[] best = {0};
		int[] teleports = {0};
		helper.succeedWhen(() -> {
			tick[0]++;
			int busy = 0;
			for (VillagerRecord miner : scene.miners()) {
				teleports[0] += jumped(level, miner, positions.computeIfAbsent(miner, m -> new double[][]{null}));
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
			helper.assertTrue(best[0] >= 15, "waiting for 15 of the 20 miners digging within the same 10 s (" + busy + " now, best "
					+ best[0] + ", stairs " + mine.stairsDone + ", levels " + mine.levels.size() + ")");
			helper.assertTrue(teleports[0] <= 10, "the staircase is shared without jams (" + teleports[0] + " fallback teleports)");
			LOGGER.info("[GuildyTest] twenty miners: {} busy at once after {} ticks, {} blocks, {} ores, {} wanted, {} levels, "
					+ "fallback teleports: {}", best[0], tick[0], mine.blocksMined, mine.oresFound, mine.wantedFound, mine.levels.size(), teleports[0]);
			helper.assertTrue(refuseAssign(scene, new VillagerRecord(java.util.UUID.randomUUID(), "Extra")),
					"a 21st miner is refused by the zone menu");
			GuildManager.disband(scene.guild());
		});
	}

	/**
	 * A mine planned before levels existed (1-wide staircase dug down to its gallery level, old galleries): it keeps its
	 * staircase (never widened) and its level, and gets a level on the way down, worked right away.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 4000)
	public void minePlannedBeforeLevelsKeepsItsStairs(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Ancienne", 20, 2, 14, 1);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		WorkZone zone = scene.zone();
		int fanionY = zone.pos.getY();
		// The old staircase: 1-wide spiral, east then south, 14 steps dug, galleries at Y - 14 (2 segments done).
		int oldOrientation = 1;
		int side = MineLayout.clampSide(VillagerModule.config().realMining.stairSide);
		helper.assertTrue(MineLayout.fits(zone.pos, oldOrientation, side, zone.bounds()), "the old staircase fits");
		for (int k = 0; k < 14; k++) {
			MineLayout.Cell cell = MineLayout.stairCell(zone.pos, oldOrientation, side, k);
			for (int i = 0; i < cell.height(); i++) {
				level.setBlock(cell.feet().above(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		mine.orientation = oldOrientation;
		mine.stairWidth = 0;
		mine.format = 0;
		mine.stairsDone = 14;
		mine.levelY = fanionY - 14;
		mine.layoutKey = "old";
		mine.done.add(1);
		mine.done.add(2);
		for (VillagerRecord miner : scene.miners()) {
			startMining(scene, miner, 1);
			miner.goal = Goal.CONTINUOUS;
		}
		MineLayout.Settings legacy = new MineLayout.Settings(side, 0, VillagerModule.config().realMining.mainTunnelLength,
				VillagerModule.config().realMining.branchSpacing, VillagerModule.config().realMining.branchLength, 5, 5, 3, 2);
		MineLayout old = MineLayout.build(zone.pos, oldOrientation, level.getMinY() + 5, zone.bounds(), false, legacy);
		BlockPos upper = old.level(fanionY - 8).segments().getFirst().cells().getFirst().feet();
		BlockPos lower = old.level(fanionY - 14).segments().getFirst().cells().getFirst().feet();
		// Where an outer lane would be (step 5 is on the east side of the old square), the rock stays.
		BlockPos pillarSide = old.stairs().get(5).feet().above().east();
		helper.succeedWhen(() -> {
			helper.assertTrue(mine.format == MineState.FORMAT && mine.stairWidth == 0, "migrated, still 1-wide");
			helper.assertTrue(mine.stairsDone == 14, "the old staircase is kept (" + mine.stairsDone + ")");
			helper.assertTrue(level.getBlockState(upper).isAir(), "waiting for the new level on the way down (Y " + (fanionY - 8) + ")");
			helper.assertTrue(level.getBlockState(lower).isAir(), "waiting for the old level (Y " + (fanionY - 14) + ")");
			helper.assertTrue(!level.getBlockState(pillarSide).isAir(), "the staircase is never widened");
			GuildManager.disband(scene.guild());
		});
	}

	/** Continuous work: bag full in the galleries → up the spiral to the chest (filler reserve kept) → back to the front. */
	@GameTest(environment = "guildy-test:day", maxTicks = 6000)
	public void realMinerGoesBackToTheFrontAfterDepositing(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Retour", 20, 1, 15, 0);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		VillagerRecord miner = scene.miners().getFirst();
		startMining(scene, miner, 1);
		miner.goal = Goal.CONTINUOUS;
		// 7 stacks already in the bag (9 max): it is full once 64 stone blocks are mined, around the first level.
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

	/** An ore in the floor of a gallery is taken (and the floor filled again), not left in place. */
	@GameTest(environment = "guildy-test:day", maxTicks = 4000)
	public void galleryFloorOreIsMined(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Sol", 14, 1, 9, 0);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		int levelY = scene.zone().pos.getY() - 9;
		MineLayout.Level galleries = scene.layout().level(levelY);
		MineLayout.Segment main = galleries.segments().stream().filter(s -> s.kind() == MineLayout.Kind.MAIN).findFirst().orElseThrow();
		BlockPos floor = main.cells().get(1).floor();
		level.setBlockAndUpdate(floor, Blocks.IRON_ORE.defaultBlockState());
		VillagerRecord miner = scene.miners().getFirst();
		startMining(scene, miner, 1);
		miner.goal = Goal.CONTINUOUS;
		helper.succeedWhen(() -> {
			BlockState state = level.getBlockState(floor);
			helper.assertTrue(!state.is(Blocks.IRON_ORE), "waiting for the ore of the gallery floor to be mined");
			helper.assertTrue(state.isFaceSturdy(level, floor, net.minecraft.core.Direction.UP), "the floor is filled again");
			helper.assertTrue(mine.oresFound >= 1 && (count(miner.carried, Items.RAW_IRON) + count(level, scene.deposit(), Items.RAW_IRON)) >= 1,
					"raw iron collected");
			GuildManager.disband(scene.guild());
		});
	}

	/**
	 * A vein seen from the end of a gallery runs out of the zone (north), then west into another guild's land: it is
	 * followed outside the zone (an approach tunnel when it gets out of reach) but stops at the other guild's border.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 6000)
	public void veinCrossingTheBorderStopsAtAnotherGuild(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Filon", 14, 1, 9, 0);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		WorkZone zone = scene.zone();
		int levelY = zone.pos.getY() - 9;
		int[] bounds = zone.bounds();
		MineLayout.Level galleries = scene.layout().level(levelY);
		helper.assertTrue(galleries.mainDirection() == 0, "the main tunnel of the level goes north");
		BlockPos end = galleries.segments().stream().filter(s -> s.kind() == MineLayout.Kind.MAIN).reduce((a, b) -> b).orElseThrow()
				.cells().getLast().feet();
		helper.assertTrue(end.getZ() == bounds[1], "the main tunnel ends at the zone border");
		// The vein: 3 blocks north out of the zone, then west to the other guild's chunk and into it.
		List<BlockPos> inside = new ArrayList<>();
		List<BlockPos> foreign = new ArrayList<>();
		for (int dz = 1; dz <= 3; dz++) {
			inside.add(end.north(dz));
		}
		BlockPos corner = end.north(3);
		for (int x = corner.getX() - 1; x >= bounds[0] - 3; x--) {
			BlockPos pos = new BlockPos(x, levelY, corner.getZ());
			(x >= bounds[0] ? inside : foreign).add(pos);
		}
		for (BlockPos pos : inside) {
			level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
		}
		for (BlockPos pos : foreign) {
			level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
		}
		// The founding claim covers 3×3 chunks: the chunk north-west of the zone goes to another guild.
		ServerPlayer neighbour = TestSupport.survivor(helper, scene.deposit().above(2));
		Guild rival = GuildManager.create(TestSupport.uniqueName("Voisin"), neighbour);
		long chunk = ChunkKey.ofBlock(bounds[0] - 1, corner.getZ());
		GuildManager.unclaim(scene.guild(), chunk);
		GuildManager.claim(rival, chunk);
		helper.assertTrue(GuildManager.at(level, foreign.getFirst()) == rival && GuildManager.at(level, inside.getLast()) != rival,
				"the vein runs into the other guild's land");
		VillagerRecord miner = scene.miners().getFirst();
		startMining(scene, miner, 1);
		miner.goal = Goal.CONTINUOUS;
		helper.succeedWhen(() -> {
			int left = 0;
			for (BlockPos pos : inside) {
				left += level.getBlockState(pos).is(Blocks.IRON_ORE) ? 1 : 0;
			}
			helper.assertTrue(left == 0, "waiting for the vein outside the zone (" + left + "/" + inside.size() + " left, pending "
					+ mine.veins.size() + ")");
			for (BlockPos pos : foreign) {
				helper.assertTrue(level.getBlockState(pos).is(Blocks.IRON_ORE), "never in the other guild's land");
				helper.assertTrue(!level.getBlockState(pos.south()).isAir() || pos.getX() >= bounds[0], "no tunnel in the other guild's land");
			}
			helper.assertTrue(mine.oresFound >= inside.size(), "ores counted");
			GuildManager.disband(scene.guild());
			GuildManager.disband(rival);
		});
	}

	/** "Objectifs prioritaires": with the ores objective, a wanted diorite cluster is still mined, kept and deposited. */
	@GameTest(environment = "guildy-test:day", maxTicks = 4000)
	public void wantedDioriteClusterIsMinedAndDeposited(GameTestHelper helper) throws Exception {
		Scene scene = scene(helper, "Diorite", 14, 1, 9, 0);
		ServerLevel level = scene.level();
		MineState mine = scene.mine();
		WorkZone zone = scene.zone();
		zone.objective = ZoneObjective.ORES;
		zone.wishlist.add("minecraft:diorite");
		int levelY = zone.pos.getY() - 9;
		MineLayout.Level galleries = scene.layout().level(levelY);
		MineLayout.Segment main = galleries.segments().stream().filter(s -> s.kind() == MineLayout.Kind.MAIN).findFirst().orElseThrow();
		BlockPos wall = main.cells().get(1).feet();
		// 2 × 2 × 2 diorite beside the main tunnel (east of it), away from the other galleries.
		int east = galleries.mainDirection() == 0 || galleries.mainDirection() == 2 ? 1 : 0;
		int south = 1 - east;
		List<BlockPos> diorite = new ArrayList<>();
		for (int a = 1; a <= 2; a++) {
			for (int b = 0; b <= 1; b++) {
				for (int y = 0; y <= 1; y++) {
					BlockPos pos = wall.offset(east * a + south * b, y, south * a + east * b);
					if (!scene.layout().inStructure(pos)) {
						diorite.add(pos);
					}
				}
			}
		}
		for (BlockPos pos : diorite) {
			level.setBlockAndUpdate(pos, Blocks.DIORITE.defaultBlockState());
		}
		VillagerRecord miner = scene.miners().getFirst();
		startMining(scene, miner, diorite.size());
		miner.goalItem = "minecraft:diorite";
		helper.succeedWhen(() -> {
			for (BlockPos pos : diorite) {
				helper.assertTrue(!level.getBlockState(pos).is(Blocks.DIORITE), "waiting for the diorite cluster to be mined");
			}
			int deposited = count(level, scene.deposit(), Items.DIORITE);
			helper.assertTrue(deposited >= diorite.size(), "waiting for the diorite in the deposit chest (" + deposited + "/" + diorite.size() + ")");
			helper.assertTrue(miner.task == Task.IDLE, "quota of diorite reached");
			helper.assertTrue(mine.wantedFound >= diorite.size(), "counted as objectifs prioritaires");
			GuildManager.disband(scene.guild());
		});
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * A block of stone {@code height} high (the test world is flat), a guild founded on top with {@code miners} villagers
	 * and a deposit chest, and a mine zone planted on the stone: square = the fanion's chunk ({@code radius} 0) or more,
	 * target level {@code depth} blocks under the fanion, 2-wide staircase planned east then north.
	 */
	private static Scene scene(GameTestHelper helper, String prefix, int height, int miners, int depth, int radius) throws Exception {
		ServerLevel level = helper.getLevel();
		VillagerConfig.RealMining real = VillagerModule.config().realMining;
		real.ticksPerBlock = 5;
		TestSupport.console(level, "time set 1000");
		BlockPos base = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, base, 3);
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		int margin = 16 * radius + 4;
		int x0 = (base.getX() & ~15) - margin;
		int x1 = (base.getX() & ~15) + 15 + margin;
		int z0 = ((base.getZ() + 4) & ~15) - margin;
		int z1 = ((base.getZ() + 4) & ~15) + 15 + margin;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int dy = 0; dy < height + 5; dy++) {
					level.setBlock(new BlockPos(x, base.getY() + dy, z), dy < height ? stone : air, Block.UPDATE_CLIENTS);
				}
			}
		}
		BlockPos top = base.above(height - 1);
		ServerPlayer leader = TestSupport.player(helper, top);
		Guild guild = TestSupport.foundGuild(helper, leader, top, prefix);
		// Room for every miner (villagers allowed per guild level).
		while (VillagerManager.maxVillagers(guild) < miners && guild.level < 500) {
			guild.level++;
		}
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
		zone.radius = radius;
		int side = MineLayout.clampSide(real.stairSide);
		helper.assertTrue(MineLayout.fits(zone.pos, ORIENTATION, side, WIDTH, zone.bounds()), "the staircase fits the zone");
		MineState mine = zone.mine();
		mine.targetY = zone.pos.getY() - depth;
		mine.origin = zone.pos;
		mine.stairSide = side;
		mine.orientation = ORIENTATION;
		mine.stairWidth = WIDTH;
		mine.format = MineState.FORMAT;
		leader.closeContainer();
		MineLayout.Settings settings = new MineLayout.Settings(real.stairSide, WIDTH, real.mainTunnelLength, real.branchSpacing,
				real.branchLength, real.roomWidth, real.roomLength, real.roomHeight, real.roomGap);
		MineLayout layout = MineLayout.build(zone.pos, ORIENTATION, level.getMinY() + 5, zone.bounds(), zone.objective == ZoneObjective.STONE,
				settings);
		return new Scene(level, guild, leader, records, deposit, zone, mine, layout);
	}

	/** A rock block next to {@code block} (horizontally) that is no part of the mine (the pillar, mostly). */
	private static BlockPos beside(Scene scene, BlockPos block) {
		for (BlockPos candidate : new BlockPos[]{block.north(), block.south(), block.east(), block.west()}) {
			if (!scene.layout().inStructure(candidate) && !candidate.equals(scene.zone().pos)) {
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

	/** The "Exploitation" menu of the zone opens (levels, miners, wishlist). */
	private static void openExploitation(Scene scene) {
		try {
			Method open = Class.forName("dev.guildy.villager.MineGui")
					.getDeclaredMethod("open", ServerPlayer.class, Guild.class, int.class, int.class);
			open.setAccessible(true);
			open.invoke(null, scene.leader(), scene.guild(), scene.zone().id, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		if (scene.leader().containerMenu == scene.leader().inventoryMenu) {
			throw new IllegalStateException("the exploitation menu should be open");
		}
		scene.leader().closeContainer();
	}

	/** The zone menu refuses to add the villager to the scene's mine (full). */
	private static boolean refuseAssign(Scene scene, VillagerRecord record) {
		try {
			Method refuse = Class.forName("dev.guildy.villager.Mines")
					.getDeclaredMethod("refuseAssign", ServerPlayer.class, Guild.class, WorkZone.class, VillagerRecord.class);
			refuse.setAccessible(true);
			return (boolean) refuse.invoke(null, scene.leader(), scene.guild(), scene.zone(), record);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Status line of a villager, as its menu shows it. */
	private static String status(ServerLevel level, VillagerRecord record) {
		try {
			Class<?> gui = Class.forName("dev.guildy.villager.VillagerGui");
			Method statusText = gui.getDeclaredMethod("statusText", VillagerRecord.class, net.minecraft.world.entity.npc.villager.Villager.class);
			statusText.setAccessible(true);
			Entity entity = level.getEntity(record.uuid);
			return String.valueOf(statusText.invoke(null, record, entity instanceof net.minecraft.world.entity.npc.villager.Villager villager
					? villager : null));
		} catch (ReflectiveOperationException e) {
			return "?";
		}
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

	private static int count(List<ItemStack> stacks, Item item) {
		int total = 0;
		for (ItemStack stack : stacks) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
