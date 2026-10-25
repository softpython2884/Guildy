package dev.guildy.test;

import dev.guildy.core.Lang;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.MineLayout;
import dev.guildy.villager.MineRules;
import dev.guildy.villager.MineState;
import dev.guildy.villager.PitPlan;
import dev.guildy.villager.Procurement;
import dev.guildy.villager.Supply;
import dev.guildy.villager.SupplyPit;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerConfig;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import dev.guildy.villager.WorkZone;
import dev.guildy.villager.ZoneType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builders fetching the materials of the construction sites ({@code Procurement.want}), with no construction project:
 * planks and stairs crafted from logs of the storage at a crafting table; glass smelted from sand with coal at a furnace
 * (lit for the show, its inventory untouched); stone brick stairs cut at the stonecutter (1 stone each, cheaper than the
 * crafting table); concrete hardened in a water source; sand dug outside the claims in a tidy pit with a ramp (another
 * guild's land untouched); cobblestone dug in a real mine zone, away from its staircase and galleries; logs from a forest
 * zone (whole trees, replanted, a tree outside the zone left alone); bricks smelted from clay balls then crafted into
 * blocks; logs gathered first, then crafted into planks (two trips); two builders sharing two wants; an item nobody can
 * obtain and a missing workstation give a reason in the builder's status. Delivered items lower the wants.
 */
public class SupplyGameTests {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("GuildyTest");
	private static final int ORIENTATION = 5;
	private static final int WIDTH = 2;

	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void buildersCraftStairsFromLogsAtACraftingTable(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Escalier");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		level.setBlockAndUpdate(ground.offset(-5, 1, 4), Blocks.CRAFTING_TABLE.defaultBlockState());
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 8));
		want(helper, recruit, "test:stairs", Items.OAK_STAIRS, 16);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);

		helper.succeedWhen(() -> {
			int logs = count(chest, Items.OAK_LOG) + carried(record, Items.OAK_LOG);
			int planks = count(chest, Items.OAK_PLANKS) + carried(record, Items.OAK_PLANKS);
			int stairs = count(chest, Items.OAK_STAIRS) + carried(record, Items.OAK_STAIRS);
			// In halves of a plank: a log = 8, a plank = 2, a stair = 3 (6 planks for 4 stairs).
			if (logs * 8 + planks * 2 + stairs * 3 > 64) {
				helper.fail("wood duplicated: " + logs + " logs, " + planks + " planks, " + stairs + " stairs");
			}
			helper.assertTrue(count(chest, Items.OAK_STAIRS) >= 16, "waiting for 16 stairs in the storage (" + stairs + ", status "
					+ status(level, record) + ")");
			helper.assertTrue(count(chest, Items.OAK_LOG) == 2 && planks == 0, "6 logs used, nothing wasted (" + logs + " logs, " + planks + " planks)");
			helper.assertTrue(!Procurement.wanted(guild).containsKey(Items.OAK_STAIRS), "the delivered stairs lowered the want");
			done(guild, "test:stairs");
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void buildersSmeltGlassFromSandWithCoal(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Verrerie");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		BlockPos furnace = recruit.ground().offset(-5, 1, -4);
		level.setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		chest.setItem(0, new ItemStack(Items.SAND, 16));
		chest.setItem(1, new ItemStack(Items.COAL, 2));
		want(helper, recruit, "test:glass", Items.GLASS, 16);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);
		boolean[] lit = {false};

		helper.succeedWhen(() -> {
			BlockState state = level.getBlockState(furnace);
			if (state.getValue(BlockStateProperties.LIT)) {
				lit[0] = true;
			}
			AbstractFurnaceBlockEntity entity = (AbstractFurnaceBlockEntity) level.getBlockEntity(furnace);
			for (int i = 0; i < 3; i++) {
				if (!entity.getItem(i).isEmpty()) {
					helper.fail("the furnace's own inventory was touched: " + entity.getItem(i));
				}
			}
			int glass = count(chest, Items.GLASS) + carried(record, Items.GLASS);
			int sand = count(chest, Items.SAND) + carried(record, Items.SAND);
			if (glass + sand > 16) {
				helper.fail("glass duplicated: " + glass + " glass, " + sand + " sand");
			}
			helper.assertTrue(count(chest, Items.GLASS) >= 16, "waiting for 16 glass (" + glass + ", status " + status(level, record) + ")");
			helper.assertTrue(sand == 0 && count(chest, Items.COAL) + carried(record, Items.COAL) == 0, "2 coal for 16 glass");
			helper.assertTrue(lit[0], "the furnace was lit while smelting");
			helper.assertTrue(!state.getValue(BlockStateProperties.LIT), "and put out afterwards");
			done(guild, "test:glass");
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void buildersCutStairsAtTheStonecutter(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Taille");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		level.setBlockAndUpdate(recruit.ground().offset(-5, 1, 4), Blocks.CRAFTING_TABLE.defaultBlockState());
		level.setBlockAndUpdate(recruit.ground().offset(-5, 1, -3), Blocks.STONECUTTER.defaultBlockState());
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		chest.setItem(0, new ItemStack(Items.STONE, 8));
		want(helper, recruit, "test:cut", Items.STONE_BRICK_STAIRS, 8);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);

		helper.succeedWhen(() -> {
			int stairs = count(chest, Items.STONE_BRICK_STAIRS);
			helper.assertTrue(stairs >= 8, "waiting for 8 stone brick stairs (" + stairs + ", status " + status(level, record) + ")");
			// The crafting table would need 12 stones (6 bricks for 4 stairs): the stonecutter's 1 stone per stair was chosen.
			helper.assertTrue(count(chest, Items.STONE) == 0 && count(chest, Items.STONE_BRICKS) == 0, "one stone per stair");
			done(guild, "test:cut");
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void buildersHardenConcreteInWater(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Beton");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		BlockPos water = recruit.ground().offset(-6, 0, 6);
		level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		chest.setItem(0, new ItemStack(Items.CONCRETE_POWDER.pick(DyeColor.WHITE), 8));
		want(helper, recruit, "test:concrete", Items.CONCRETE.pick(DyeColor.WHITE), 8);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);
		double[] nearest = {Double.MAX_VALUE};

		helper.succeedWhen(() -> {
			net.minecraft.world.entity.Entity villager = level.getEntity(record.uuid);
			if (villager != null) {
				nearest[0] = Math.min(nearest[0], villager.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(water)));
			}
			int concrete = count(chest, Items.CONCRETE.pick(DyeColor.WHITE));
			helper.assertTrue(concrete >= 8, "waiting for 8 white concrete (" + concrete + ", status " + status(level, record) + ")");
			helper.assertTrue(count(chest, Items.CONCRETE_POWDER.pick(DyeColor.WHITE)) == 0, "all the powder hardened");
			helper.assertTrue(nearest[0] < 4.5, "it went to the water source (" + nearest[0] + ")");
			helper.assertTrue(level.getBlockState(water).is(Blocks.WATER), "the water source stays");
			done(guild, "test:concrete");
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 4800)
	public void buildersDigSandOutsideTheClaimsInATidyPit(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Sabliere");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		int cx = ground.getX() >> 4;
		int cz = ground.getZ() >> 4;
		int g = ground.getY();
		// Sand under the ground east of the territory (chunks cx+2..cx+3, cz-1..cz+1), 4 blocks deep.
		int x0 = (cx + 2) << 4;
		int x1 = ((cx + 3) << 4) + 15;
		int z0 = (cz - 1) << 4;
		int z1 = ((cz + 1) << 4) + 15;
		fill(level, x0, g - 3, z0, x1, g, z1, Blocks.SAND.defaultBlockState());
		// The nearest of these chunks belongs to another guild: never dug.
		ServerPlayer neighbour = TestSupport.player(helper, ground.offset(0, 0, 12));
		Guild other = GuildManager.create(TestSupport.uniqueName("Voisin"), neighbour);
		long otherChunk = ChunkKey.of(cx + 2, cz);
		GuildManager.claim(other, otherChunk);
		want(helper, recruit, "test:sand", Items.SAND, 24);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);

		helper.succeedWhen(() -> {
			for (int x = (cx + 2) << 4; x < ((cx + 3) << 4); x++) {
				for (int z = cz << 4; z < (cz + 1) << 4; z++) {
					for (int y = g - 3; y <= g; y++) {
						if (!level.getBlockState(new BlockPos(x, y, z)).is(Blocks.SAND)) {
							helper.fail("the other guild's land was dug at " + x + " " + y + " " + z);
						}
					}
				}
			}
			int sand = count((Container) level.getBlockEntity(recruit.deposit()), Items.SAND);
			helper.assertTrue(sand >= 24, "waiting for 24 sand in the storage (" + sand + ", status " + status(level, record) + ")");
			List<SupplyPit> pits = new ArrayList<>(guild.villagers.pits);
			helper.assertTrue(pits.size() == 1 && pits.getFirst().zoneId < 0, "one pit outside the territory: " + pits.size());
			SupplyPit pit = pits.getFirst();
			for (int x = pit.x - 1; x <= pit.x + pit.side; x++) {
				for (int z = pit.z - 1; z <= pit.z + pit.side; z++) {
					helper.assertTrue(GuildManager.atChunk(ChunkKey.ofBlock(x, z)) == null, "the pit and its rim are in no claim");
				}
			}
			// Tidy: every hole of the sand area is inside the pit; the ramp's entry step was not dug.
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					for (int y = g - 3; y <= g; y++) {
						boolean inPit = x >= pit.x && x < pit.x + pit.side && z >= pit.z && z < pit.z + pit.side;
						if (!inPit && level.getBlockState(new BlockPos(x, y, z)).isAir()) {
							helper.fail("a hole outside the pit at " + x + " " + y + " " + z);
						}
					}
				}
			}
			BlockPos entry = rampStep(pit, 0);
			helper.assertTrue(!level.getBlockState(entry.below()).isAir(), "the ramp's entry stays at ground level");
			helper.assertTrue(pit.dug >= 24, "blocks counted for the pit (" + pit.dug + ")");
			done(guild, "test:sand");
			GuildManager.disband(other);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 4800)
	public void buildersDigInAMineZoneAwayFromItsLayout(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Carreau");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		int cx = ground.getX() >> 4;
		int cz = ground.getZ() >> 4;
		VillagerConfig.RealMining real = VillagerModule.config().realMining;
		BlockPos fanion = new BlockPos(((cx + 1) << 4) + 8, ground.getY() + 1, (cz << 4) + 8);
		// Radius 2 (5 x 5 chunks): room for a pit beside the staircase and the galleries of its levels.
		WorkZone zone = new WorkZone(guild.villagers.nextZoneId++, ZoneType.MINE, "Mine des tests", fanion, 2, System.currentTimeMillis());
		guild.villagers.zones.add(zone);
		MineState mine = zone.mine();
		int side = MineLayout.clampSide(real.stairSide);
		helper.assertTrue(MineLayout.fits(fanion, ORIENTATION, side, WIDTH, zone.bounds()), "the staircase fits the zone");
		mine.origin = fanion;
		mine.stairSide = side;
		mine.orientation = ORIENTATION;
		mine.stairWidth = WIDTH;
		mine.format = MineState.FORMAT;
		want(helper, recruit, "test:cobble", Items.COBBLESTONE, 20);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);
		MineLayout.Settings settings = new MineLayout.Settings(real.stairSide, WIDTH, real.mainTunnelLength, real.branchSpacing, real.branchLength,
				real.roomWidth, real.roomLength, real.roomHeight, real.roomGap);
		MineLayout layout = MineLayout.build(fanion, ORIENTATION, MineRules.minLevel(level.getMinY()), zone.bounds(), false, settings);

		helper.succeedWhen(() -> {
			int cobble = count((Container) level.getBlockEntity(recruit.deposit()), Items.COBBLESTONE);
			helper.assertTrue(cobble >= 20, "waiting for 20 cobblestone (" + cobble + ", status " + status(level, record) + ")");
			helper.assertTrue(guild.villagers.pits.size() == 1, "one pit");
			SupplyPit pit = guild.villagers.pits.getFirst();
			helper.assertTrue(pit.zoneId == zone.id, "dug in the mine zone, not outside");
			int[] bounds = zone.bounds();
			helper.assertTrue(pit.x >= bounds[0] && pit.z >= bounds[1] && pit.x + pit.side - 1 <= bounds[2] && pit.z + pit.side - 1 <= bounds[3],
					"inside the zone's square");
			int margin = Supply.config().gathering.mineMargin;
			for (int x = pit.x; x < pit.x + pit.side; x++) {
				for (int z = pit.z; z < pit.z + pit.side; z++) {
					for (int dx = -margin; dx <= margin; dx++) {
						for (int dz = -margin; dz <= margin; dz++) {
							helper.assertTrue(!layout.inStairColumns(x + dx, z + dz), "the pit keeps away from the staircase");
						}
					}
					for (int y = pit.top - pit.depth - 1; y <= pit.top + 1; y++) {
						MineLayout.Level built = layout.level(y);
						if (built == null) {
							continue;
						}
						for (MineLayout.Segment segment : built.segments()) {
							for (MineLayout.Cell cell : segment.cells()) {
								// Only the levels the mine plans count; any level's galleries kept at least the margin away is stricter.
								int distance = Math.max(Math.abs(cell.feet().getX() - x), Math.abs(cell.feet().getZ() - z));
								if (distance <= margin && planned(level, zone, y)) {
									helper.fail("the pit is " + distance + " blocks from a gallery of level " + y);
								}
							}
						}
					}
				}
			}
			done(guild, "test:cobble");
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void buildersCutWholeTreesInAForestZone(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Futaie");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		int cx = ground.getX() >> 4;
		int cz = ground.getZ() >> 4;
		BlockPos center = new BlockPos((cx << 4) + 8, ground.getY() + 1, ((cz + 1) << 4) + 8);
		WorkZone zone = new WorkZone(guild.villagers.nextZoneId++, ZoneType.FOREST, "Futaie des tests", center, 0, System.currentTimeMillis());
		guild.villagers.zones.add(zone);
		List<BlockPos> first = tree(level, center.offset(-4, 0, -3), 4);
		List<BlockPos> second = tree(level, center.offset(4, 0, 3), 4);
		// In the territory but outside the zone: never cut.
		List<BlockPos> spared = tree(level, ground.offset(-12, 1, -12), 4);
		want(helper, recruit, "test:logs", Items.OAK_LOG, 6);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);

		helper.succeedWhen(() -> {
			for (BlockPos log : spared) {
				if (!level.getBlockState(log).is(Blocks.OAK_LOG)) {
					helper.fail("a tree outside the forest zone was cut");
				}
			}
			int logs = count((Container) level.getBlockEntity(recruit.deposit()), Items.OAK_LOG);
			helper.assertTrue(logs >= 6, "waiting for 6 logs (" + logs + ", status " + status(level, record) + ")");
			for (List<BlockPos> tree : List.of(first, second)) {
				boolean cut = !level.getBlockState(tree.getFirst()).is(Blocks.OAK_LOG);
				if (cut) {
					for (BlockPos log : tree.subList(1, tree.size())) {
						helper.assertTrue(!level.getBlockState(log).is(Blocks.OAK_LOG), "a tree is cut whole, never half");
					}
					helper.assertTrue(level.getBlockState(tree.getFirst()).is(Blocks.OAK_SAPLING), "a sapling replanted at the base");
				}
			}
			helper.assertTrue(logs == 8, "both trees cut whole: 8 logs (" + logs + ")");
			done(guild, "test:logs");
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void anUnobtainableItemGivesAReason(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Diamant");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		// A crafting table: the block would be crafted from diamonds, which no site gives.
		level.setBlockAndUpdate(recruit.ground().offset(-5, 1, 4), Blocks.CRAFTING_TABLE.defaultBlockState());
		want(helper, recruit, "test:diamond", Items.DIAMOND_BLOCK, 12);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);

		String[] last = {""};
		helper.succeedWhen(() -> {
			String status = status(level, record);
			if (!status.equals(last[0])) {
				LOGGER.info("[GuildyTest] diamond builder: {}", status);
				last[0] = status;
			}
			helper.assertTrue(status.contains("Il manque 12 Bloc de diamant") && status.contains("à déposer au stockage"),
					"waiting for the reason in the status (" + status + ")");
			helper.assertTrue(Procurement.wanted(guild).getOrDefault(Items.DIAMOND_BLOCK, 0) == 12, "still wanted");
			helper.assertTrue(record.carried.isEmpty(), "nothing fetched");
			done(guild, "test:diamond");
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 1200)
	public void aMissingWorkstationGivesAReason(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Sansfour");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		chest.setItem(0, new ItemStack(Items.SAND, 8));
		chest.setItem(1, new ItemStack(Items.COAL, 1));
		want(helper, recruit, "test:nofurnace", Items.GLASS, 8);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);

		helper.succeedWhen(() -> {
			String status = status(level, record);
			helper.assertTrue(status.contains("Aucun four dans le territoire pour cuire Verre"), "waiting for the reason (" + status + ")");
			helper.assertTrue(count(chest, Items.SAND) == 8 && record.carried.isEmpty(), "nothing taken without a furnace");
			done(guild, "test:nofurnace");
		});
	}

	/** Clay balls of the storage smelted into bricks, then 4 bricks crafted by hand into each bricks block. */
	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void buildersChainSmeltingAndCrafting(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Briqueterie");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		level.setBlockAndUpdate(recruit.ground().offset(-5, 1, -4), Blocks.FURNACE.defaultBlockState());
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		chest.setItem(0, new ItemStack(Items.CLAY_BALL, 16));
		chest.setItem(1, new ItemStack(Items.COAL, 2));
		want(helper, recruit, "test:bricks", Items.BRICKS, 4);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);

		helper.succeedWhen(() -> {
			int bricks = count(chest, Items.BRICKS);
			helper.assertTrue(bricks >= 4, "waiting for 4 bricks blocks (" + bricks + ", status " + status(level, record) + ")");
			helper.assertTrue(count(chest, Items.CLAY_BALL) == 0 && count(chest, Items.BRICK) == 0 && count(chest, Items.COAL) == 0,
					"16 clay balls -> 16 bricks -> 4 blocks, 2 coal burnt");
			done(guild, "test:bricks");
		});
	}

	/** No log in the storage: a tree of the forest zone is cut first (logs to the storage), then the planks are crafted. */
	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void buildersGatherLogsThenCraftPlanks(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Scierie");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		BlockPos center = new BlockPos(((ground.getX() >> 4) << 4) + 8, ground.getY() + 1, (((ground.getZ() >> 4) + 1) << 4) + 8);
		guild.villagers.zones.add(new WorkZone(guild.villagers.nextZoneId++, ZoneType.FOREST, "Scierie des tests", center, 0,
				System.currentTimeMillis()));
		tree(level, center.offset(-3, 0, 2), 5);
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		want(helper, recruit, "test:planks", Items.OAK_PLANKS, 12);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);

		helper.succeedWhen(() -> {
			int planks = count(chest, Items.OAK_PLANKS);
			helper.assertTrue(planks >= 12, "waiting for 12 planks (" + planks + ", status " + status(level, record) + ")");
			int logs = count(chest, Items.OAK_LOG) + carried(record, Items.OAK_LOG);
			// 5 logs cut: 3 made 12 planks, 2 left in the storage.
			helper.assertTrue(planks + 4 * logs == 20, "no wood lost (" + planks + " planks, " + logs + " logs)");
			done(guild, "test:planks");
		});
	}

	/**
	 * The guild allows any chest and barrel of the territory: logs of a barrel outside the storage are crafted into planks,
	 * which go to the storage.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void buildersTakeIngredientsFromAnyContainerWhenAllowed(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Tonneaux");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		java.util.function.Predicate<Guild> before = Supply.allContainers;
		Supply.allContainers = before.or(g -> g == guild);
		BlockPos barrelPos = recruit.ground().offset(-3, 1, -6);
		level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
		Container barrel = (Container) level.getBlockEntity(barrelPos);
		barrel.setItem(4, new ItemStack(Items.OAK_LOG, 4));
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		want(helper, recruit, "test:barrel", Items.OAK_PLANKS, 16);
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BUILDER);

		helper.succeedWhen(() -> {
			int planks = count(chest, Items.OAK_PLANKS);
			helper.assertTrue(planks >= 16, "waiting for 16 planks from the barrel's logs (" + planks + ", status " + status(level, record) + ")");
			helper.assertTrue(count(barrel, Items.OAK_LOG) == 0, "the barrel's logs were used");
			Supply.allContainers = before;
			done(guild, "test:barrel");
		});
	}

	/** Two builders and two wants: each reserves its own and both are delivered. */
	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void twoBuildersShareTheWork(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Equipe");
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		while (dev.guildy.villager.VillagerManager.maxVillagers(guild) < 2 && guild.level < 500) {
			guild.level++;
		}
		TestSupport.console(level, "guild admin villager spawn " + guild.name);
		level.getServer().getCommands().performPrefixedCommand(recruit.leader().createCommandSourceStack(), "guild villagers accept");
		List<VillagerRecord> builders = new ArrayList<>(guild.villagers.members.values());
		helper.assertTrue(builders.size() == 2, "two villagers");
		level.setBlockAndUpdate(recruit.ground().offset(-5, 1, 4), Blocks.CRAFTING_TABLE.defaultBlockState());
		level.setBlockAndUpdate(recruit.ground().offset(-5, 1, -4), Blocks.FURNACE.defaultBlockState());
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 6));
		chest.setItem(1, new ItemStack(Items.SAND, 16));
		chest.setItem(2, new ItemStack(Items.COAL, 2));
		want(helper, recruit, "test:team.stairs", Items.OAK_STAIRS, 16);
		want(helper, recruit, "test:team.glass", Items.GLASS, 16);
		for (VillagerRecord builder : builders) {
			ZoneGameTests.setTask(new ZoneGameTests.Recruit(guild, builder, recruit.leader(), recruit.ground(), recruit.deposit()), Task.BUILDER);
		}
		boolean[] together = {false};

		helper.succeedWhen(() -> {
			Map<Item, Integer> fetching = Procurement.fetching(guild);
			if (fetching.containsKey(Items.OAK_STAIRS) && fetching.containsKey(Items.GLASS)) {
				together[0] = true;
			}
			if (fetching.getOrDefault(Items.GLASS, 0) > 16 || fetching.getOrDefault(Items.OAK_STAIRS, 0) > 16) {
				helper.fail("the same want chased twice: " + fetching);
			}
			int stairs = count(chest, Items.OAK_STAIRS);
			int glass = count(chest, Items.GLASS);
			helper.assertTrue(stairs >= 16 && glass >= 16, "waiting for 16 stairs and 16 glass (" + stairs + ", " + glass + ")");
			helper.assertTrue(together[0], "both builders worked at the same time, each on its own want");
			Procurement.clear(guild, "test:team.stairs");
			done(guild, "test:team.glass");
		});
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Posts a want and, like a construction site, posts its shortfall again every 20 seconds (a want is forgotten after
	 * 60 s): the total minus what the storage already holds.
	 */
	private static void want(GameTestHelper helper, ZoneGameTests.Recruit recruit, String requester, Item item, int total) {
		Guild guild = recruit.guild();
		Container chest = (Container) helper.getLevel().getBlockEntity(recruit.deposit());
		Procurement.want(guild, requester, Map.of(item, total));
		int[] ticks = {0};
		helper.onEachTick(() -> {
			if (++ticks[0] % 400 == 0 && GuildManager.get(guild.id) != null) {
				int missing = total - count(chest, item);
				Procurement.want(guild, requester, missing > 0 ? Map.of(item, missing) : Map.of());
			}
		});
	}

	private static void done(Guild guild, String requester) {
		Procurement.clear(guild, requester);
		GuildManager.disband(guild);
	}

	/** The level is one the mine plans (its levels, or deeper ones on their grid). */
	private static boolean planned(ServerLevel level, WorkZone zone, int y) {
		try {
			Class<?> mines = Class.forName("dev.guildy.villager.Mines");
			Method levels = mines.getDeclaredMethod("levels", ServerLevel.class, WorkZone.class, MineState.class, VillagerConfig.RealMining.class);
			levels.setAccessible(true);
			MineRules.LevelPlan plan = (MineRules.LevelPlan) levels.invoke(null, level, zone, zone.mine(), VillagerModule.config().realMining);
			return plan.levels().contains(y);
		} catch (ReflectiveOperationException e) {
			return true;
		}
	}

	/** Feet of ramp step i of a pit. */
	private static BlockPos rampStep(SupplyPit pit, int i) {
		return new PitPlan(pit.x, pit.z, pit.side, pit.top, pit.depth, pit.entry, pit.clockwise).stepFeet(i);
	}

	private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** Oak trunk with natural leaves around its top, on grass; logs bottom-up. */
	private static List<BlockPos> tree(ServerLevel level, BlockPos base, int height) {
		level.setBlockAndUpdate(base.below(), Blocks.GRASS_BLOCK.defaultBlockState());
		List<BlockPos> logs = new ArrayList<>();
		for (int i = 0; i < height; i++) {
			BlockPos log = base.above(i);
			level.setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
			logs.add(log);
		}
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1).setValue(LeavesBlock.PERSISTENT, false);
		BlockPos top = logs.getLast();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			level.setBlockAndUpdate(top.relative(direction), leaves);
		}
		level.setBlockAndUpdate(top.above(), leaves);
		return logs;
	}

	private static int count(Container container, Item item) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int carried(VillagerRecord record, Item item) {
		int total = 0;
		for (ItemStack stack : record.carried) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static String status(ServerLevel level, VillagerRecord record) {
		try {
			Class<?> gui = Class.forName("dev.guildy.villager.VillagerGui");
			Method statusText = gui.getDeclaredMethod("statusText", VillagerRecord.class, net.minecraft.world.entity.npc.villager.Villager.class);
			statusText.setAccessible(true);
			net.minecraft.world.entity.Entity entity = level.getEntity(record.uuid);
			return Lang.strip(String.valueOf(statusText.invoke(null, record,
					entity instanceof net.minecraft.world.entity.npc.villager.Villager villager ? villager : null)));
		} catch (ReflectiveOperationException e) {
			return "?";
		}
	}
}
