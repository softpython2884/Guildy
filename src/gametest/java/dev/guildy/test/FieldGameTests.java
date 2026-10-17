package dev.guildy.test;

import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.FieldState;
import dev.guildy.villager.ItemClassifier;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerRecord;
import dev.guildy.villager.WorkZone;
import dev.guildy.villager.ZoneOption;
import dev.guildy.villager.ZonePermissions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Field works allowed by the "Autorisations du fanion" of a field zone (environment {@code guildy-test:day}: the clock
 * is frozen, the farmers never go home for the night): "Créer des champs" turns a grass plateau into fields (water
 * holes, tilled soil, crops sown with seeds of a storage chest), never outside the zone nor in another guild's land;
 * "Entretenir un champ" tills and sows again trampled farmland without tilling anything new; with "Labourer" off the
 * water holes only hydrate the existing farmland and nothing is tilled; "Aplanir" fills a pit and shaves a bump, "Poudre
 * d'os" uses the storage's bone meal on the crops.
 */
public class FieldGameTests {
	private static final int PLATFORM = 72;

	@GameTest(environment = "guildy-test:day", maxTicks = 8000)
	public void createFieldsOnGrassPlateau(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = recruit(helper, "Champs");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		int y = ground.getY();
		WorkZone zone = fieldZone(recruit);
		ZonePermissions.apply(zone, ZonePermissions.Preset.CREATE);
		int[] bounds = zone.bounds();
		List<BlockPos> plateau = plateau(level, zone, y);
		// Flowers on the plateau (cleared before tilling) and coarse dirt (tilled in two hoe strokes).
		BlockPos flower = plateau.get(40);
		level.setBlockAndUpdate(flower.above(), Blocks.POPPY.defaultBlockState());
		BlockPos coarse = plateau.get(70);
		level.setBlockAndUpdate(coarse, Blocks.COARSE_DIRT.defaultBlockState());

		// Another guild's chunk in the zone (north-east corner) and a spot just outside the zone, both with hydrated grass.
		ServerPlayer neighbour = TestSupport.survivor(helper, recruit.deposit().above(2));
		Guild rival = GuildManager.create(TestSupport.uniqueName("Voisin"), neighbour);
		long rivalChunk = ChunkKey.ofBlock(bounds[2], bounds[1]);
		GuildManager.claim(rival, rivalChunk);
		BlockPos rivalWater = new BlockPos((ChunkKey.x(rivalChunk) << 4) + 8, y, (ChunkKey.z(rivalChunk) << 4) + 8);
		List<BlockPos> rivalGrass = pond(level, rivalWater);
		BlockPos outsideWater = new BlockPos(bounds[2] + 4, y, (bounds[1] + bounds[3]) / 2);
		List<BlockPos> outsideGrass = pond(level, outsideWater);
		helper.assertTrue(!zone.contains(outsideWater) && !zone.contains(outsideWater.west(2)), "the second pond lies outside the zone");

		// Seeds in a "Graines et pousses" storage chest.
		BlockPos seedChest = ground.offset(4, 1, 3);
		level.setBlockAndUpdate(seedChest, Blocks.CHEST.defaultBlockState());
		((Container) level.getBlockEntity(seedChest)).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 16));
		guild.villagers.chests.add(new StorageChest(seedChest, StorageChest.Role.CATEGORY, ItemClassifier.categoryOf(new ItemStack(Items.WHEAT_SEEDS))));
		VillagerRecord record = recruit.record();
		record.zoneId = zone.id;
		ZoneGameTests.setTask(recruit, Task.FARMER);

		helper.succeedWhen(() -> {
			for (BlockPos pos : rivalGrass) {
				if (!level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
					helper.fail("worked in another guild's land at " + pos.toShortString());
				}
			}
			for (BlockPos pos : outsideGrass) {
				if (!level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
					helper.fail("worked outside the zone at " + pos.toShortString());
				}
			}
			FieldState state = zone.field();
			for (BlockPos hole : state.holes) {
				if (!zone.contains(hole) || GuildManager.at(level, hole) == rival) {
					helper.fail("water hole outside the zone or in another guild's land at " + hole.toShortString());
				}
			}
			int water = 0;
			int farmland = 0;
			int sown = 0;
			for (BlockPos pos : plateau) {
				BlockState block = level.getBlockState(pos);
				if (block.is(Blocks.WATER)) {
					water++;
				} else if (block.is(Blocks.FARMLAND)) {
					farmland++;
					if (level.getBlockState(pos.above()).getBlock() instanceof CropBlock) {
						sown++;
					}
				}
			}
			helper.assertTrue(water >= 3 && state.holes.size() == water, "waiting for the water holes (" + water + ", remembered " + state.holes.size() + ")");
			helper.assertTrue(farmland >= 100, "waiting for the plateau to be tilled (" + farmland + " farmland)");
			helper.assertTrue(level.getBlockState(flower.above()).isAir() && level.getBlockState(flower).is(Blocks.FARMLAND),
					"waiting for the flower to be cleared and its soil tilled");
			helper.assertTrue(level.getBlockState(coarse).is(Blocks.FARMLAND), "waiting for the coarse dirt to be tilled");
			int inChest = count(level, seedChest, Items.WHEAT_SEEDS);
			int inBag = carried(record, Items.WHEAT_SEEDS);
			helper.assertTrue(record.stats.sown == 16 && sown == 16, "waiting for the 16 seeds to be sown (" + record.stats.sown + ")");
			helper.assertTrue(inChest == 0 && inBag == 0, "seeds taken from the storage chest (chest " + inChest + ", bag " + inBag + ")");
			helper.assertTrue(record.stats.tilled >= farmland && record.stats.waterHoles >= water, "stats counted");
			GuildManager.disband(guild);
			GuildManager.disband(rival);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 4800)
	public void maintainedFieldRetillsTrampledFarmland(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = recruit(helper, "Entretien");
		Guild guild = recruit.guild();
		int y = recruit.ground().getY();
		WorkZone zone = fieldZone(recruit);
		ZonePermissions.apply(zone, ZonePermissions.Preset.MAINTAIN);
		helper.assertTrue(!ZonePermissions.allows(zone, ZoneOption.TILL) && ZonePermissions.allows(zone, ZoneOption.RETILL), "maintain preset");
		// A field around a water source: wheat on the farmland within 3 blocks, hydrated grass on the ring at 4 blocks.
		BlockPos water = new BlockPos((zone.pos.getX() & ~15) + 11, y, (zone.pos.getZ() & ~15) + 8);
		List<BlockPos> farmland = new ArrayList<>();
		List<BlockPos> grass = new ArrayList<>();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				BlockPos pos = water.offset(dx, 0, dz);
				level.setBlockAndUpdate(pos.below(2), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
				int ring = Math.max(Math.abs(dx), Math.abs(dz));
				if (ring == 0) {
					level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
				} else if (ring == 4) {
					level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
					grass.add(pos);
				} else {
					level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
					level.setBlockAndUpdate(pos.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(1));
					farmland.add(pos);
				}
			}
		}
		Container deposit = (Container) level.getBlockEntity(recruit.deposit());
		deposit.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 8));
		// Trampled through the vanilla conversion (the wheat on them pops off).
		List<BlockPos> trampled = List.of(water.offset(2, 0, 0), water.offset(-2, 0, 1), water.offset(0, 0, 3), water.offset(1, 0, -2));
		FarmlandBlock block = (FarmlandBlock) Blocks.FARMLAND;
		for (BlockPos pos : trampled) {
			block.turnToBaseBlock(null, level.getBlockState(pos), level, pos);
		}
		for (BlockPos pos : trampled) {
			helper.assertTrue(level.getBlockState(pos).is(Blocks.DIRT) && level.getBlockState(pos.above()).isAir(), "trampled to dirt");
		}
		helper.assertTrue(zone.field().trampled.size() == 4, "the zone remembers the 4 trampled spots (" + zone.field().trampled.size() + ")");
		helper.assertTrue("minecraft:wheat_seeds".equals(zone.field().trampled.getFirst().seed), "with the crop that grew there");
		// Farmland outside the zone is not remembered.
		BlockPos away = recruit.ground().offset(-20, 0, 0);
		level.setBlockAndUpdate(away, Blocks.FARMLAND.defaultBlockState());
		block.turnToBaseBlock(null, level.getBlockState(away), level, away);
		helper.assertTrue(zone.field().trampled.size() == 4, "farmland outside field zones is not remembered");

		VillagerRecord record = recruit.record();
		record.zoneId = zone.id;
		ZoneGameTests.setTask(recruit, Task.FARMER);
		helper.succeedWhen(() -> {
			for (BlockPos pos : grass) {
				if (!level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
					helper.fail("tilled new soil while \"Labourer\" is off at " + pos.toShortString());
				}
			}
			helper.assertTrue(zone.field().holes.isEmpty(), "no water hole without \"Créer des points d'eau\"");
			for (BlockPos pos : trampled) {
				helper.assertTrue(level.getBlockState(pos).is(Blocks.FARMLAND) && level.getBlockState(pos.above()).is(Blocks.WHEAT),
						"waiting for the trampled spot to be tilled and sown again at " + pos.toShortString());
			}
			helper.assertTrue(zone.field().trampled.isEmpty(), "memory emptied");
			helper.assertTrue(record.stats.tilled >= 4 && record.stats.sown >= 4, "stats counted");
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 6000)
	public void withoutTillingWaterHolesOnlyHydrateTheFarmland(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = recruit(helper, "Irrigation");
		Guild guild = recruit.guild();
		WorkZone zone = fieldZone(recruit);
		ZonePermissions.apply(zone, ZonePermissions.Preset.CREATE);
		ZonePermissions.set(zone, ZoneOption.TILL, false);
		int y = recruit.ground().getY();
		List<BlockPos> grass = plateau(level, zone, y);
		// A dry field of young wheat in the plateau (the crops keep the farmland), grass all around it.
		int x0 = zone.pos.getX() & ~15;
		int z0 = zone.pos.getZ() & ~15;
		List<BlockPos> farmland = new ArrayList<>();
		for (int x = x0 + 8; x <= x0 + 14; x++) {
			for (int z = z0 + 1; z <= z0 + 7; z++) {
				BlockPos pos = new BlockPos(x, y, z);
				level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
				level.setBlockAndUpdate(pos.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(0));
				farmland.add(pos);
				grass.remove(pos);
			}
		}
		VillagerRecord record = recruit.record();
		record.zoneId = zone.id;
		ZoneGameTests.setTask(recruit, Task.FARMER);
		long[] hydratedAt = {-1};
		helper.succeedWhen(() -> {
			for (BlockPos pos : grass) {
				if (level.getBlockState(pos).is(Blocks.FARMLAND)) {
					helper.fail("tilled while \"Labourer\" is off at " + pos.toShortString());
				}
			}
			helper.assertTrue(!zone.field().holes.isEmpty(), "waiting for water holes");
			for (BlockPos pos : farmland) {
				helper.assertTrue(hydrated(level, pos), "waiting for the farmland to be hydrated at " + pos.toShortString());
			}
			for (BlockPos hole : zone.field().holes) {
				helper.assertTrue(level.getBlockState(hole).is(Blocks.WATER) && grass.contains(hole), "holes dug in the grass around the field");
			}
			if (hydratedAt[0] < 0) {
				hydratedAt[0] = helper.getTick();
			}
			// A few more rounds of work: still nothing tilled.
			helper.assertTrue(helper.getTick() - hydratedAt[0] >= 600, "watching a while longer");
			helper.assertTrue(record.stats.tilled == 0, "nothing tilled");
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 8000)
	public void levellingAndBoneMeal(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = recruit(helper, "Aplanir");
		Guild guild = recruit.guild();
		WorkZone zone = fieldZone(recruit);
		ZonePermissions.apply(zone, ZonePermissions.Preset.CREATE);
		ZonePermissions.set(zone, ZoneOption.LEVEL, true);
		ZonePermissions.set(zone, ZoneOption.BONEMEAL, true);
		int y = recruit.ground().getY();
		// A grass patch of the fanion's chunk (local x 7..13, z 2..14) with a 1-block pit and a 1-block bump.
		int x0 = zone.pos.getX() & ~15;
		int z0 = zone.pos.getZ() & ~15;
		for (int x = x0 + 7; x <= x0 + 13; x++) {
			for (int z = z0 + 2; z <= z0 + 14; z++) {
				BlockPos pos = new BlockPos(x, y, z);
				level.setBlockAndUpdate(pos.below(2), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
				level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}
		BlockPos pit = new BlockPos(x0 + 10, y, z0 + 5);
		level.setBlockAndUpdate(pit, Blocks.AIR.defaultBlockState());
		BlockPos bump = new BlockPos(x0 + 10, y + 1, z0 + 11);
		level.setBlockAndUpdate(bump.below(), Blocks.DIRT.defaultBlockState());
		level.setBlockAndUpdate(bump, Blocks.GRASS_BLOCK.defaultBlockState());
		Container deposit = (Container) level.getBlockEntity(recruit.deposit());
		deposit.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 8));
		deposit.setItem(1, new ItemStack(Items.BONE_MEAL, 8));
		VillagerRecord record = recruit.record();
		record.zoneId = zone.id;
		ZoneGameTests.setTask(recruit, Task.FARMER);
		helper.succeedWhen(() -> {
			helper.assertTrue(level.getBlockState(pit).is(Blocks.FARMLAND), "waiting for the pit to be filled and tilled");
			helper.assertTrue(level.getBlockState(bump).isAir() && level.getBlockState(bump.below()).is(Blocks.FARMLAND),
					"waiting for the bump to be shaved and tilled");
			int meal = count(level, recruit.deposit(), Items.BONE_MEAL) + carried(record, Items.BONE_MEAL);
			helper.assertTrue(meal < 8, "waiting for bone meal to be used (" + meal + " left)");
			boolean grown = false;
			for (int x = x0 + 7; x <= x0 + 13 && !grown; x++) {
				for (int z = z0 + 2; z <= z0 + 14 && !grown; z++) {
					BlockState crop = level.getBlockState(new BlockPos(x, y + 1, z));
					grown = crop.getBlock() instanceof CropBlock cropBlock && cropBlock.getAge(crop) >= 2;
				}
			}
			helper.assertTrue(grown, "waiting for a crop grown by bone meal");
			GuildManager.disband(guild);
		});
	}

	/** Vanilla rule of FarmlandBlock.isNearWater. */
	private static boolean hydrated(ServerLevel level, BlockPos farmland) {
		for (BlockPos pos : BlockPos.betweenClosed(farmland.offset(-4, 0, -4), farmland.offset(4, 1, 4))) {
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ helpers

	/** Like {@link ZoneGameTests#recruit} with a stone platform reaching beyond the field zone of radius 1 at +36. */
	private static ZoneGameTests.Recruit recruit(GameTestHelper helper, String prefix) {
		ServerLevel level = helper.getLevel();
		TestSupport.console(level, "time set 1000");
		BlockPos ground = TestSupport.region(level, PLATFORM);
		TestSupport.forceChunks(level, ground, 5);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, prefix);
		TestSupport.console(level, "guild admin villager spawn " + guild.name);
		ZoneGameTests.command(level, leader, "guild villagers accept");
		helper.assertTrue(guild.villagers.members.size() == 1, "candidate accepted as guild villager");
		BlockPos deposit = ground.offset(4, 1, 0);
		level.setBlockAndUpdate(deposit, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(new StorageChest(deposit, StorageChest.Role.DEPOSIT, ""));
		return new ZoneGameTests.Recruit(guild, guild.villagers.members.values().iterator().next(), leader, ground, deposit);
	}

	/** Plants a field fanion 36 blocks east of the heart (radius 1: 3×3 chunks of the stone platform). */
	private static WorkZone fieldZone(ZoneGameTests.Recruit recruit) {
		ServerLevel level = (ServerLevel) recruit.leader().level();
		TestSupport.useOnTop(recruit.leader(), level, recruit.ground().offset(36, 0, 0), ZoneGameTests.fanion(recruit.guild(), "FARM"));
		if (recruit.guild().villagers.zones.size() != 1) {
			throw new IllegalStateException("field zone not created");
		}
		return recruit.guild().villagers.zones.getFirst();
	}

	/** Grass over dirt over stone on the whole chunk of the fanion (but the fanion's column): the plateau's grass blocks. */
	private static List<BlockPos> plateau(ServerLevel level, WorkZone zone, int y) {
		int x0 = zone.pos.getX() & ~15;
		int z0 = zone.pos.getZ() & ~15;
		List<BlockPos> grass = new ArrayList<>();
		for (int x = x0; x < x0 + 16; x++) {
			for (int z = z0; z < z0 + 16; z++) {
				if (x == zone.pos.getX() && z == zone.pos.getZ()) {
					continue;
				}
				BlockPos pos = new BlockPos(x, y, z);
				level.setBlock(pos.below(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				grass.add(pos);
			}
		}
		return grass;
	}

	/** A water source with grass around it (hydrated, tillable); returns the grass blocks. */
	private static List<BlockPos> pond(ServerLevel level, BlockPos water) {
		List<BlockPos> grass = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos pos = water.offset(dx, 0, dz);
				level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
				if (dx == 0 && dz == 0) {
					level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
				} else {
					level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
					grass.add(pos);
				}
			}
		}
		return grass;
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
