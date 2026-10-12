package dev.guildy.test;

import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.ItemClassifier;
import dev.guildy.villager.PlantingRules;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerConfig;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import dev.guildy.villager.WorkZone;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Planting between harvests: a lumberjack takes saplings from a storage chest and plants them on the free natural
 * ground of its forest zone with the configured spacing, never outside the zone nor near another guild's land; a farmer
 * sows empty farmland and tills hydrated soil of its field zone, never a path, a block next to a player's block, dry
 * soil, outside the zone or in another guild's land.
 */
public class PlantingGameTests {

	@GameTest(maxTicks = 4800)
	public void lumberjackPlantsStoredSaplingsWithSpacing(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		VillagerConfig config = VillagerModule.config();
		// Exact sapling count: no sapling picked up from the ground (a planted one could grow and drop more).
		config.lumberjack.pickUpSaplings = false;
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Pousses");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		TestSupport.useOnTop(recruit.leader(), level, ground.offset(36, 0, 0), ZoneGameTests.fanion(guild, "FOREST"));
		helper.assertTrue(guild.villagers.zones.size() == 1, "forest zone created by planting its fanion");
		WorkZone zone = guild.villagers.zones.getFirst();
		int[] bounds = zone.bounds();
		int y = ground.getY();
		// Grass from the zone's west part to 10 blocks south of it, partly in the chunk of another guild (zone's south-east chunk).
		int minX = bounds[0] + 2;
		int maxX = bounds[2] - 10;
		int minZ = bounds[3] - 19;
		int maxZ = bounds[3] + 10;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}
		ServerPlayer neighbour = TestSupport.survivor(helper, recruit.deposit().above(2));
		Guild rival = GuildManager.create(TestSupport.uniqueName("Voisin"), neighbour);
		long rivalChunk = ChunkKey.ofBlock(bounds[2], bounds[3]);
		GuildManager.claim(rival, rivalChunk);
		int rivalX = ChunkKey.x(rivalChunk) << 4;
		int rivalZ = ChunkKey.z(rivalChunk) << 4;
		helper.assertTrue(rivalX <= maxX, "part of the grass lies in the other guild's land");

		// Saplings in a "Graines et pousses" chest; birch saplings misfiled in a "Bois" chest are not taken.
		BlockPos saplingChest = ground.offset(4, 1, 3);
		BlockPos woodChest = ground.offset(4, 1, 6);
		chest(level, guild, saplingChest, ItemClassifier.categoryOf(new ItemStack(Items.OAK_SAPLING)), new ItemStack(Items.OAK_SAPLING, 10));
		chest(level, guild, woodChest, ItemClassifier.categoryOf(new ItemStack(Items.OAK_LOG)), new ItemStack(Items.BIRCH_SAPLING, 5));
		VillagerRecord record = recruit.record();
		record.zoneId = zone.id;
		ZoneGameTests.setTask(recruit, Task.LUMBERJACK);

		int spacing = config.lumberjack.plantSpacing;
		int margin = PlantingRules.FOREIGN_MARGIN;
		helper.succeedWhen(() -> {
			List<BlockPos> planted = new ArrayList<>();
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockState state = level.getBlockState(new BlockPos(x, y + 1, z));
					// A sapling may grow into a tree meanwhile: its trunk stays on the same column.
					if (state.is(BlockTags.SAPLINGS) || state.is(BlockTags.LOGS)) {
						planted.add(new BlockPos(x, y + 1, z));
					}
				}
			}
			for (BlockPos pos : planted) {
				if (!zone.contains(pos)) {
					helper.fail("sapling planted outside the zone at " + pos.toShortString());
				}
				if (pos.getX() >= rivalX - margin && pos.getX() <= rivalX + 15 + margin && pos.getZ() >= rivalZ - margin
						&& pos.getZ() <= rivalZ + 15 + margin) {
					helper.fail("sapling planted in or next to the other guild's land at " + pos.toShortString());
				}
			}
			for (int i = 0; i < planted.size(); i++) {
				for (int j = i + 1; j < planted.size(); j++) {
					BlockPos a = planted.get(i);
					BlockPos b = planted.get(j);
					if (PlantingRules.distance(a.getX(), a.getZ(), b.getX(), b.getZ()) <= spacing) {
						helper.fail("saplings closer than " + spacing + " blocks: " + a.toShortString() + " / " + b.toShortString());
					}
				}
			}
			int inChest = count(level, saplingChest, Items.OAK_SAPLING);
			int inBag = carried(record, Items.OAK_SAPLING);
			helper.assertTrue(inChest == 0, "waiting for the saplings to be taken from the storage chest (" + inChest + " left)");
			helper.assertTrue(record.stats.planted >= 10, "waiting for the 10 saplings to be planted (" + record.stats.planted + ")");
			helper.assertTrue(inChest + inBag + record.stats.planted == 10,
					"saplings neither lost nor duplicated (chest " + inChest + ", bag " + inBag + ", planted " + record.stats.planted + ")");
			helper.assertTrue(planted.size() == record.stats.planted, "planted saplings in the world: " + planted.size());
			helper.assertTrue(count(level, woodChest, Items.BIRCH_SAPLING) == 5, "saplings of a chest of another category are not taken");
			config.lumberjack.pickUpSaplings = true;
			GuildManager.disband(guild);
			GuildManager.disband(rival);
		});
	}

	@GameTest(maxTicks = 4800)
	public void farmerSowsFarmlandAndTillsHydratedSoil(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Semis");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		TestSupport.useOnTop(recruit.leader(), level, ground.offset(36, 0, 0), ZoneGameTests.fanion(guild, "FARM"));
		helper.assertTrue(guild.villagers.zones.size() == 1, "field zone created by planting its fanion");
		WorkZone zone = guild.villagers.zones.getFirst();
		int[] bounds = zone.bounds();
		int y = ground.getY();
		int wx = bounds[0] + 24;
		int wz = bounds[3] - 11;
		level.setBlockAndUpdate(new BlockPos(wx, y, wz), Blocks.WATER.defaultBlockState());
		List<BlockPos> farmland = new ArrayList<>();
		List<BlockPos> soil = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			BlockPos field = new BlockPos(wx + dx, y, wz + 2);
			level.setBlockAndUpdate(field, Blocks.FARMLAND.defaultBlockState());
			farmland.add(field);
			BlockPos dirt = new BlockPos(wx + dx, y, wz - 2);
			level.setBlockAndUpdate(dirt, (dx % 2 == 0 ? Blocks.DIRT : Blocks.GRASS_BLOCK).defaultBlockState());
			soil.add(dirt);
		}
		// Too far from the water, next to a player's block, a path: never tilled.
		BlockPos dry = new BlockPos(wx, y, wz + 7);
		level.setBlockAndUpdate(dry, Blocks.DIRT.defaultBlockState());
		BlockPos besidePlanks = new BlockPos(wx + 4, y, wz);
		level.setBlockAndUpdate(besidePlanks, Blocks.DIRT.defaultBlockState());
		level.setBlockAndUpdate(besidePlanks.east(), Blocks.OAK_PLANKS.defaultBlockState());
		BlockPos path = new BlockPos(wx - 4, y, wz);
		level.setBlockAndUpdate(path, Blocks.DIRT_PATH.defaultBlockState());
		// Farmland outside the zone (south) and in another guild's chunk (zone's north-east chunk): never sown.
		BlockPos outside = new BlockPos(wx, y, bounds[3] + 3);
		level.setBlockAndUpdate(outside, Blocks.FARMLAND.defaultBlockState());
		ServerPlayer neighbour = TestSupport.survivor(helper, recruit.deposit().above(2));
		Guild rival = GuildManager.create(TestSupport.uniqueName("Voisin"), neighbour);
		long rivalChunk = ChunkKey.ofBlock(bounds[2], bounds[1]);
		GuildManager.claim(rival, rivalChunk);
		BlockPos foreign = new BlockPos((ChunkKey.x(rivalChunk) << 4) + 6, y, (ChunkKey.z(rivalChunk) << 4) + 6);
		level.setBlockAndUpdate(foreign, Blocks.FARMLAND.defaultBlockState());
		level.setBlockAndUpdate(foreign.east(2), Blocks.WATER.defaultBlockState());

		Container deposit = (Container) level.getBlockEntity(recruit.deposit());
		deposit.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 16));
		VillagerRecord record = recruit.record();
		record.zoneId = zone.id;
		ZoneGameTests.setTask(recruit, Task.FARMER);

		helper.succeedWhen(() -> {
			if (!level.getBlockState(outside.above()).isAir() || !level.getBlockState(foreign.above()).isAir()) {
				helper.fail("sowed outside the zone or in another guild's land");
			}
			if (!level.getBlockState(dry).is(Blocks.DIRT) || !level.getBlockState(besidePlanks).is(Blocks.DIRT)
					|| !level.getBlockState(path).is(Blocks.DIRT_PATH)) {
				helper.fail("tilled dry soil, soil next to planks or a path");
			}
			for (BlockPos field : farmland) {
				helper.assertTrue(level.getBlockState(field.above()).is(Blocks.WHEAT), "waiting for the empty farmland to be sown at " + field.toShortString());
			}
			for (BlockPos dirt : soil) {
				helper.assertTrue(level.getBlockState(dirt).is(Blocks.FARMLAND) && level.getBlockState(dirt.above()).is(Blocks.WHEAT),
						"waiting for the hydrated soil to be tilled and sown at " + dirt.toShortString());
			}
			int inChest = count(level, recruit.deposit(), Items.WHEAT_SEEDS);
			int inBag = carried(record, Items.WHEAT_SEEDS);
			helper.assertTrue(record.stats.sown == 10, "10 crops sown (" + record.stats.sown + ")");
			helper.assertTrue(inChest + inBag + record.stats.sown == 16,
					"seeds neither lost nor duplicated (chest " + inChest + ", bag " + inBag + ", sown " + record.stats.sown + ")");
			GuildManager.disband(guild);
			GuildManager.disband(rival);
		});
	}

	// ------------------------------------------------------------------ helpers

	private static void chest(ServerLevel level, Guild guild, BlockPos pos, String category, ItemStack content) {
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		((Container) level.getBlockEntity(pos)).setItem(0, content);
		guild.villagers.chests.add(new StorageChest(pos, StorageChest.Role.CATEGORY, category));
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

	private static int carried(VillagerRecord record, Item item) {
		int total = 0;
		for (ItemStack stack : record.carried) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
