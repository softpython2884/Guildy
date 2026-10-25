package dev.guildy.test;

import com.google.gson.Gson;
import dev.guildy.builds.BuildModule;
import dev.guildy.builds.BuildPlacement;
import dev.guildy.builds.BuildProject;
import dev.guildy.builds.BuildSite;
import dev.guildy.builds.BuildSites;
import dev.guildy.builds.GuildBuildData;
import dev.guildy.builds.PlacementTransform;
import dev.guildy.builds.SchematicLibrary;
import dev.guildy.core.GuildyItems;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildStorage;
import dev.guildy.data.JsonAdapters;
import dev.guildy.villager.BuilderData;
import dev.guildy.villager.Procurement;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerRecord;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construction sites built by builder villagers from a schematic written in the schematics folder (Sponge v3): a small
 * house from the storage, a room dug into a hill (inside hollowed, terrain around and above intact), blocks placed by a
 * player counted and skipped, obstacles reported and never broken, missing materials posted to {@link Procurement},
 * materials taken from an unregistered barrel with the "all containers" option, foundations down to the ground, a site
 * saved and reloaded from its copy, and the placement through the plan item (preview, confirmed inside the territory,
 * refused outside).
 */
public class BuildGameTests {
	/** Schematic text of a cell (null = air). */
	interface StateAt {
		@Nullable String at(int x, int y, int z);
	}

	// ================================================================ helpers

	/** Writes a Sponge v3 schematic into config/guildy/schematics/tests/ and returns its key in the list. */
	static String schematic(String prefix, int w, int h, int l, StateAt states) throws IOException {
		return schematic(prefix, w, h, l, states, new net.minecraft.nbt.ListTag());
	}

	/** Same, with Sponge v3 block entities ({Pos, Id, Data}). */
	static String schematic(String prefix, int w, int h, int l, StateAt states, net.minecraft.nbt.ListTag blockEntities) throws IOException {
		Map<String, Integer> palette = new LinkedHashMap<>();
		palette.put("minecraft:air", 0);
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		for (int y = 0; y < h; y++) {
			for (int z = 0; z < l; z++) {
				for (int x = 0; x < w; x++) {
					String state = states.at(x, y, z);
					int index = palette.computeIfAbsent(state == null ? "minecraft:air" : state, k -> palette.size());
					int v = index;
					while ((v & ~0x7F) != 0) {
						data.write((v & 0x7F) | 0x80);
						v >>>= 7;
					}
					data.write(v);
				}
			}
		}
		CompoundTag paletteTag = new CompoundTag();
		palette.forEach(paletteTag::putInt);
		CompoundTag blocks = new CompoundTag();
		blocks.put("Palette", paletteTag);
		blocks.putByteArray("Data", data.toByteArray());
		blocks.put("BlockEntities", blockEntities);
		CompoundTag inner = new CompoundTag();
		inner.putInt("Version", 3);
		inner.putShort("Width", (short) w);
		inner.putShort("Height", (short) h);
		inner.putShort("Length", (short) l);
		inner.put("Blocks", blocks);
		CompoundTag root = new CompoundTag();
		root.put("Schematic", inner);
		String name = TestSupport.uniqueName(prefix) + ".schem";
		Path dir = SchematicLibrary.configDir().resolve("tests");
		Files.createDirectories(dir);
		NbtIo.writeCompressed(root, dir.resolve(name));
		return "config:tests/" + name;
	}

	/** Starts a site of that schematic at {@code origin} (no placement checks); the project once created. */
	static BuildProject[] start(GameTestHelper helper, Guild guild, String key, BlockPos origin) {
		BuildProject[] holder = new BuildProject[1];
		BuildModule.createSite(guild, key, origin, PlacementTransform.Turn.NONE, PlacementTransform.Flip.NONE, true,
				project -> holder[0] = project, error -> helper.fail("site not created: " + error));
		return holder;
	}

	static void fast() {
		BuildModule.config().builder.blocksPerSecond = 8.0;
	}

	/** Builds only (never gathers): the scenario tests the building side whatever the gathering can do. */
	static void buildOnly(ZoneGameTests.Recruit recruit) {
		BuilderData data = new BuilderData();
		data.role = "build";
		recruit.record().builder = data;
	}

	static void fill(Container container, ItemStack... stacks) {
		int slot = 0;
		for (ItemStack stack : stacks) {
			container.setItem(slot++, stack);
		}
	}

	static int count(Container container, Item item) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	static int carried(VillagerRecord record, Item item) {
		int total = 0;
		for (ItemStack stack : record.carried) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** The builder's status line and where it stands (failure messages). */
	static String status(ServerLevel level, VillagerRecord record) {
		try {
			Object runtime = Class.forName("dev.guildy.villager.TaskEngine").getDeclaredMethod("peek", java.util.UUID.class);
			java.lang.reflect.Method peek = (java.lang.reflect.Method) runtime;
			peek.setAccessible(true);
			Object rt = peek.invoke(null, record.uuid);
			if (rt == null) {
				return "no runtime";
			}
			java.lang.reflect.Field key = rt.getClass().getDeclaredField("statusKey");
			java.lang.reflect.Field args = rt.getClass().getDeclaredField("statusArgs");
			key.setAccessible(true);
			args.setAccessible(true);
			String text = dev.guildy.core.Lang.raw((String) key.get(rt), (Object[]) args.get(rt));
			net.minecraft.world.entity.Entity entity = level.getEntity(record.uuid);
			return text + " @ " + (entity == null ? "?" : entity.blockPosition().toShortString()) + " bag " + record.carried;
		} catch (ReflectiveOperationException e) {
			return e.toString();
		}
	}

	static @Nullable BuildSite site(Guild guild, BuildProject @Nullable [] holder) {
		return holder == null || holder[0] == null ? null : BuildSites.peek(guild, holder[0]);
	}

	private static String house(int x, int y, int z) {
		boolean ring = x == 0 || z == 0 || x == 4 || z == 4;
		if (y == 0 || y == 3) {
			return "minecraft:oak_planks";
		}
		if (x == 2 && z == 0) {
			return "minecraft:oak_door[facing=north,half=" + (y == 1 ? "lower" : "upper") + ",hinge=left,open=false,powered=false]";
		}
		if (ring) {
			return "minecraft:cobblestone";
		}
		if (x == 1 && y == 2 && z == 2) {
			return "minecraft:wall_torch[facing=east]";
		}
		return null;
	}

	// ================================================================ scenarios

	@GameTest(environment = "guildy-test:day", maxTicks = 4800)
	public void builderBuildsAHouseWithTheStorageMaterials(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		fast();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Maison");
		Guild guild = recruit.guild();
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		fill(chest, new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_DOOR, 1),
				new ItemStack(Items.TORCH, 2));
		String key = schematic("maison", 5, 4, 5, BuildGameTests::house);
		BlockPos origin = recruit.ground().offset(8, 1, 6);
		BuildProject[] holder = start(helper, guild, key, origin);
		ZoneGameTests.setTask(recruit, Task.BUILDER);
		helper.succeedWhen(() -> {
			BuildProject project = holder[0];
			helper.assertTrue(project != null, "waiting for the site");
			int planks = count(chest, Items.OAK_PLANKS) + carried(recruit.record(), Items.OAK_PLANKS);
			if (planks < 64 - 50) {
				helper.fail("planks lost: " + planks);
			}
			helper.assertTrue(project.finished, "waiting for the house (" + project.done + "/" + project.total + ")");
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					helper.assertTrue(level.getBlockState(origin.offset(x, 0, z)).is(Blocks.OAK_PLANKS), "floor");
					helper.assertTrue(level.getBlockState(origin.offset(x, 3, z)).is(Blocks.OAK_PLANKS), "roof");
				}
			}
			helper.assertTrue(level.getBlockState(origin.offset(0, 1, 3)).is(Blocks.COBBLESTONE), "wall");
			BlockState door = level.getBlockState(origin.offset(2, 1, 0));
			helper.assertTrue(door.is(Blocks.OAK_DOOR) && door.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
					&& door.getValue(DoorBlock.FACING) == Direction.NORTH, "door lower half: " + door);
			helper.assertTrue(level.getBlockState(origin.offset(2, 2, 0)).is(Blocks.OAK_DOOR), "door upper half placed with it");
			BlockState torch = level.getBlockState(origin.offset(1, 2, 2));
			helper.assertTrue(torch.is(Blocks.WALL_TORCH) && torch.getValue(WallTorchBlock.FACING) == Direction.EAST, "wall torch: " + torch);
			helper.assertTrue(level.getBlockState(origin.offset(2, 1, 2)).isAir(), "inside: air");
			helper.assertTrue(planks == 64 - 50, "exactly the planks of the plan used (" + planks + " left)");
			helper.assertTrue(project.placed >= 50 + 30 + 2, "blocks placed counted: " + project.placed);
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 7200)
	public void builderHollowsARoomInAHillAndLeavesTheTerrainAround(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		fast();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Colline");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		BlockPos origin = ground.offset(8, 1, 6);
		// A hill of stone covering the box (7 × 5 × 7), 2 blocks around and 1 above it.
		for (int x = -2; x <= 8; x++) {
			for (int z = -2; z <= 8; z++) {
				for (int y = 0; y <= 5; y++) {
					level.setBlock(origin.offset(x, y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		fill(chest, new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_PLANKS, 64));
		// Room of planks in the middle of the box (x, z 1..5), its interior air; the rest of the box is air too.
		String key = schematic("colline", 7, 5, 7, (x, y, z) -> {
			if (x < 1 || x > 5 || z < 1 || z > 5) {
				return null;
			}
			boolean ring = x == 1 || x == 5 || z == 1 || z == 5;
			return y == 0 || y == 4 || ring ? "minecraft:oak_planks" : null;
		});
		BuildProject[] holder = start(helper, guild, key, origin);
		ZoneGameTests.setTask(recruit, Task.BUILDER);
		helper.succeedWhen(() -> {
			BuildProject project = holder[0];
			helper.assertTrue(project != null, "waiting for the site");
			BuildSite site = BuildSites.peek(guild, project);
			helper.assertTrue(project.finished, "waiting for the room (" + project.done + "/" + project.total + ") "
					+ (site == null ? "" : site.counts().toString().replaceAll("\\[I@[0-9a-f]+", "")) + " · " + status(level, recruit.record()));
			for (int x = 2; x <= 4; x++) {
				for (int z = 2; z <= 4; z++) {
					for (int y = 1; y <= 3; y++) {
						helper.assertTrue(level.getBlockState(origin.offset(x, y, z)).isAir(), "interior dug out at " + x + " " + y + " " + z);
					}
				}
			}
			helper.assertTrue(level.getBlockState(origin.offset(1, 2, 3)).is(Blocks.OAK_PLANKS), "wall replaced the stone");
			helper.assertTrue(level.getBlockState(origin.offset(3, 4, 3)).is(Blocks.OAK_PLANKS), "roof");
			// The terrain of the box around the room, above the box and around it: untouched.
			helper.assertTrue(level.getBlockState(origin.offset(0, 2, 0)).is(Blocks.STONE), "box corner kept");
			helper.assertTrue(level.getBlockState(origin.offset(6, 3, 3)).is(Blocks.STONE), "box side kept");
			helper.assertTrue(level.getBlockState(origin.offset(3, 5, 3)).is(Blocks.STONE), "above the roof kept");
			helper.assertTrue(level.getBlockState(origin.offset(-1, 2, 3)).is(Blocks.STONE), "outside kept");
			helper.assertTrue(project.cleared >= 27, "interior cells dug: " + project.cleared);
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void blocksPlacedByAPlayerAreCountedAndSkipped(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		fast();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Aide");
		Guild guild = recruit.guild();
		BlockPos origin = recruit.ground().offset(8, 1, 6);
		// A player already laid 4 of the 9 bricks of the floor.
		BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
		level.setBlockAndUpdate(origin, bricks);
		level.setBlockAndUpdate(origin.offset(2, 0, 0), bricks);
		level.setBlockAndUpdate(origin.offset(0, 0, 2), bricks);
		level.setBlockAndUpdate(origin.offset(1, 0, 1), bricks);
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		fill(chest, new ItemStack(Items.STONE_BRICKS, 5));
		String key = schematic("aide", 3, 1, 3, (x, y, z) -> "minecraft:stone_bricks");
		BuildProject[] holder = start(helper, guild, key, origin);
		boolean[] early = {false};
		helper.succeedWhen(() -> {
			BuildProject project = holder[0];
			helper.assertTrue(project != null, "waiting for the site");
			BuildSite site = BuildSites.peek(guild, project);
			if (!early[0]) {
				helper.assertTrue(site != null && site.scanned(), "waiting for the first scan");
				helper.assertTrue(site.counts().done() == 4 && site.counts().total() == 9, "4 of 9 cells already done: " + site.counts());
				early[0] = true;
				try {
					ZoneGameTests.setTask(recruit, Task.BUILDER);
				} catch (ReflectiveOperationException e) {
					helper.fail("setTask: " + e);
				}
			}
			helper.assertTrue(project.finished, "waiting for the floor");
			for (int x = 0; x < 3; x++) {
				for (int z = 0; z < 3; z++) {
					helper.assertTrue(level.getBlockState(origin.offset(x, 0, z)).is(Blocks.STONE_BRICKS), "floor");
				}
			}
			helper.assertTrue(project.placed == 5, "only the 5 missing bricks placed: " + project.placed);
			helper.assertTrue(count(chest, Items.STONE_BRICKS) + carried(recruit.record(), Items.STONE_BRICKS) == 0, "5 bricks used");
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void obstaclesAreReportedAndNeverBroken(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		fast();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Obstacle");
		Guild guild = recruit.guild();
		BlockPos origin = recruit.ground().offset(8, 1, 6);
		level.setBlockAndUpdate(origin, Blocks.OAK_PLANKS.defaultBlockState());
		BlockPos chestPos = origin.offset(1, 0, 0);
		level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
		Container obstacle = (Container) level.getBlockEntity(chestPos);
		obstacle.setItem(0, new ItemStack(Items.DIAMOND, 3));
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		fill(chest, new ItemStack(Items.STONE_BRICKS, 4));
		String key = schematic("obstacle", 4, 1, 1, (x, y, z) -> "minecraft:stone_bricks");
		BuildProject[] holder = start(helper, guild, key, origin);
		ZoneGameTests.setTask(recruit, Task.BUILDER);
		boolean[] replacing = {false};
		helper.succeedWhen(() -> {
			BuildProject project = holder[0];
			helper.assertTrue(project != null, "waiting for the site");
			BuildSite site = BuildSites.peek(guild, project);
			helper.assertTrue(site != null && site.scanned(), "waiting for the scan");
			if (project.finished) {
				helper.fail("finished with an obstacle on a cell");
			}
			helper.assertTrue(level.getBlockState(chestPos).is(Blocks.CHEST) && count(obstacle, Items.DIAMOND) == 3, "the chest is never broken");
			helper.assertTrue(level.getBlockState(origin.offset(2, 0, 0)).is(Blocks.STONE_BRICKS)
					&& level.getBlockState(origin.offset(3, 0, 0)).is(Blocks.STONE_BRICKS), "the free cells are built");
			if (!replacing[0]) {
				helper.assertTrue(level.getBlockState(origin).is(Blocks.OAK_PLANKS), "the player's planks stay");
				helper.assertTrue(site.counts().blocked() == 2 && site.counts().blockedAt().contains(origin)
						&& site.counts().blockedAt().contains(chestPos), "two obstacles listed: " + site.counts().blockedAt());
				// Option "replace the built blocks": the planks go, never the chest.
				project.replaceBuilt = true;
				replacing[0] = true;
			}
			helper.assertTrue(level.getBlockState(origin).is(Blocks.STONE_BRICKS), "waiting for the planks to be replaced");
			helper.assertTrue(site.counts().blocked() == 1 && site.counts().blockedAt().contains(chestPos), "the chest is the last obstacle");
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void missingMaterialsArePostedForTheGatherers(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		fast();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Verre");
		Guild guild = recruit.guild();
		BlockPos origin = recruit.ground().offset(8, 1, 6);
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		fill(chest, new ItemStack(Items.GLASS, 4));
		String key = schematic("verre", 10, 1, 1, (x, y, z) -> "minecraft:glass");
		BuildProject[] holder = start(helper, guild, key, origin);
		buildOnly(recruit);
		ZoneGameTests.setTask(recruit, Task.BUILDER);
		helper.succeedWhen(() -> {
			BuildProject project = holder[0];
			helper.assertTrue(project != null, "waiting for the site");
			Map<Item, Integer> wanted = Procurement.wanted(guild);
			helper.assertTrue(wanted.getOrDefault(Items.GLASS, 0) == 6, "6 glass wanted: " + wanted);
			int placed = 0;
			for (int x = 0; x < 10; x++) {
				if (level.getBlockState(origin.offset(x, 0, 0)).is(Blocks.GLASS)) {
					placed++;
				}
			}
			helper.assertTrue(placed == 4, "the 4 glass of the storage placed: " + placed);
			helper.assertTrue(!project.finished, "not finished without the missing glass");
			GuildManager.disband(guild);
			helper.assertTrue(Procurement.wanted(guild).isEmpty(), "a disbanded guild wants nothing");
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void allChestsAndBarrelsOfTheTerritory(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		fast();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Tonneau");
		Guild guild = recruit.guild();
		BlockPos origin = recruit.ground().offset(8, 1, 6);
		// An unregistered barrel of the territory holds the bricks.
		BlockPos barrelPos = recruit.ground().offset(-4, 1, 3);
		level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
		Container barrel = (Container) level.getBlockEntity(barrelPos);
		barrel.setItem(0, new ItemStack(Items.BRICKS, 3));
		String key = schematic("tonneau", 3, 1, 1, (x, y, z) -> "minecraft:bricks");
		BuildProject[] holder = start(helper, guild, key, origin);
		buildOnly(recruit);
		ZoneGameTests.setTask(recruit, Task.BUILDER);
		boolean[] all = {false};
		helper.succeedWhen(() -> {
			BuildProject project = holder[0];
			helper.assertTrue(project != null, "waiting for the site");
			BuildSite site = BuildSites.peek(guild, project);
			if (!all[0]) {
				// Sorted storage only (default): the bricks are missing.
				helper.assertTrue(Procurement.wanted(guild).getOrDefault(Items.BRICKS, 0) == 3, "3 bricks missing from the sorted storage");
				helper.assertTrue(count(barrel, Items.BRICKS) == 3, "the barrel is not a source yet");
				project.sources = BuildProject.Sources.ALL.id();
				site.invalidateStock();
				all[0] = true;
			}
			helper.assertTrue(project.finished, "waiting for the wall with the barrel's bricks");
			helper.assertTrue(count(barrel, Items.BRICKS) == 0, "the barrel gave its bricks");
			for (int x = 0; x < 3; x++) {
				helper.assertTrue(level.getBlockState(origin.offset(x, 0, 0)).is(Blocks.BRICKS), "bricks");
			}
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void foundationsFillDownToTheGround(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		fast();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Pilotis");
		Guild guild = recruit.guild();
		// A deck 3 blocks above the stone platform.
		BlockPos origin = recruit.ground().offset(8, 4, 6);
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		fill(chest, new ItemStack(Items.OAK_PLANKS, 12));
		String key = schematic("pilotis", 3, 1, 1, (x, y, z) -> "minecraft:oak_planks");
		BuildProject[] holder = start(helper, guild, key, origin);
		boolean[] started = {false};
		helper.succeedWhen(() -> {
			BuildProject project = holder[0];
			helper.assertTrue(project != null, "waiting for the site");
			if (!started[0]) {
				project.foundations = true;
				BuildSites.reload(guild, project);
				started[0] = true;
				try {
					ZoneGameTests.setTask(recruit, Task.BUILDER);
				} catch (ReflectiveOperationException e) {
					helper.fail("setTask: " + e);
				}
			}
			helper.assertTrue(project.finished, "waiting for the deck and its pillars (" + project.done + "/" + project.total + ") · "
					+ status(level, recruit.record()));
			for (int x = 0; x < 3; x++) {
				for (int dy = 0; dy >= -3; dy--) {
					helper.assertTrue(level.getBlockState(origin.offset(x, dy, 0)).is(Blocks.OAK_PLANKS), "deck and pillar at " + x + " " + dy);
				}
				helper.assertTrue(level.getBlockState(origin.offset(x, -4, 0)).is(Blocks.STONE), "the ground stays");
			}
			helper.assertTrue(project.placed == 12, "3 deck blocks and 9 foundation blocks: " + project.placed);
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 1200)
	public void siteSurvivesASaveAndAReload(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Sauvegarde");
		Guild guild = recruit.guild();
		BlockPos origin = recruit.ground().offset(8, 1, 6);
		String key = schematic("sauvegarde", 4, 2, 3, (x, y, z) -> y == 0 ? "minecraft:oak_planks" : x == 1 ? "minecraft:oak_fence" : null);
		BuildProject[] holder = start(helper, guild, key, origin);
		boolean[] reloaded = {false};
		helper.succeedWhen(() -> {
			BuildProject project = holder[0];
			helper.assertTrue(project != null, "waiting for the site");
			Path copy = GuildStorage.dir(level.getServer()).resolve("builds").resolve(project.file);
			helper.assertTrue(Files.isRegularFile(copy), "copy in the world folder: " + copy);
			if (!reloaded[0]) {
				project.priority = 3;
				project.interior = "box";
				Gson gson = JsonAdapters.createGson();
				GuildBuildData back = gson.fromJson(gson.toJson(guild.builds), GuildBuildData.class);
				back.sanitize();
				BuildProject read = back.get(project.id);
				helper.assertTrue(read != null && read.name.equals(project.name) && read.file.equals(project.file)
						&& read.transform().equals(project.transform()) && read.priority == 3 && read.interior.equals("box")
						&& read.sizeX == 4 && read.sizeY == 2 && read.sizeZ == 3, "project saved and read back");
				helper.assertTrue(back.nextId == guild.builds.nextId, "next id saved");
				BuildSites.reload(guild, project);
				reloaded[0] = true;
			}
			BuildSite site = BuildSites.peek(guild, project);
			helper.assertTrue(site != null && site.ready() && site.scanned(), "site loaded again from its copy");
			helper.assertTrue(site.plan().data.sizeX == 4 && site.counts().total() == 24, "same plan, box mode (12 planks, 3 fences, 9 air cells): " + site.counts());
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 1200)
	public void planItemPlacesAPreviewAndConfirmsInsideTheTerritory(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Plan");
		Guild guild = recruit.guild();
		String key = schematic("plan", 3, 2, 3, (x, y, z) -> y == 0 ? "minecraft:cobblestone" : null);
		BlockPos ground = recruit.ground();
		int[] step = {0};
		helper.succeedWhen(() -> {
			if (step[0] == 0) {
				TestSupport.useOnTop(recruit.leader(), level, ground.offset(6, 0, 6), plan(guild, key));
				step[0] = 1;
			}
			if (step[0] == 1) {
				helper.assertTrue(BuildPlacement.previewing(recruit.leader()), "waiting for the preview");
				ZoneGameTests.command(level, recruit.leader(), "guild chantier confirm");
				step[0] = 2;
			}
			if (step[0] == 2) {
				helper.assertTrue(guild.builds.projects.size() == 1, "site created by the confirmation");
				BuildProject project = guild.builds.projects.getFirst();
				int[] box = project.box();
				// Looking south: front edge centred on the clicked block, one block above it.
				helper.assertTrue(box[0] == ground.getX() + 5 && box[1] == ground.getY() + 1 && box[2] == ground.getZ() + 6
						&& box[3] == ground.getX() + 7 && box[5] == ground.getZ() + 8, "box in front of the player");
				helper.assertTrue(!BuildPlacement.previewing(recruit.leader()), "the preview ended");
				// Far outside the territory: refused.
				TestSupport.useOnTop(recruit.leader(), level, ground.offset(160, 0, 0), plan(guild, key));
				step[0] = 3;
			}
			if (step[0] == 3) {
				helper.assertTrue(BuildPlacement.previewing(recruit.leader()), "waiting for the second preview");
				ZoneGameTests.command(level, recruit.leader(), "guild chantier confirm");
				step[0] = 4;
			}
			helper.assertTrue(guild.builds.projects.size() == 1, "no site outside the territory");
			boolean refused = TestSupport.chat(recruit.leader()).stream().anyMatch(line -> line.contains("Placement impossible"));
			helper.assertTrue(refused, "refusal message");
			ZoneGameTests.command(level, recruit.leader(), "guild chantier cancel");
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void signTextsAreCopiedNeverContainerContents(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		fast();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Panneau");
		Guild guild = recruit.guild();
		BlockPos origin = recruit.ground().offset(8, 1, 6);
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		fill(chest, new ItemStack(Items.STONE_BRICKS, 2), new ItemStack(Items.OAK_SIGN, 1), new ItemStack(Items.CHEST, 1));
		net.minecraft.nbt.ListTag entities = new net.minecraft.nbt.ListTag();
		// The sign on top of a brick, with its text.
		CompoundTag sign = new CompoundTag();
		sign.put("Pos", new net.minecraft.nbt.IntArrayTag(new int[] {0, 1, 0}));
		sign.putString("Id", "minecraft:sign");
		CompoundTag front = new CompoundTag();
		net.minecraft.nbt.ListTag messages = new net.minecraft.nbt.ListTag();
		messages.add(net.minecraft.nbt.StringTag.valueOf("Bienvenue"));
		for (int i = 0; i < 3; i++) {
			messages.add(net.minecraft.nbt.StringTag.valueOf(""));
		}
		front.put("messages", messages);
		CompoundTag signData = new CompoundTag();
		signData.put("front_text", front);
		sign.put("Data", signData);
		entities.add(sign);
		// A chest full of diamonds in the schematic: the chest is built empty.
		CompoundTag loot = new CompoundTag();
		loot.put("Pos", new net.minecraft.nbt.IntArrayTag(new int[] {1, 1, 0}));
		loot.putString("Id", "minecraft:chest");
		CompoundTag lootData = new CompoundTag();
		net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
		CompoundTag diamond = new CompoundTag();
		diamond.putByte("Slot", (byte) 0);
		diamond.putString("id", "minecraft:diamond");
		diamond.putInt("count", 64);
		items.add(diamond);
		lootData.put("Items", items);
		loot.put("Data", lootData);
		entities.add(loot);
		String key = schematic("panneau", 2, 2, 1, (x, y, z) -> y == 0 ? "minecraft:stone_bricks"
				: x == 0 ? "minecraft:oak_sign[rotation=0,waterlogged=false]" : "minecraft:chest[facing=north,type=single,waterlogged=false]", entities);
		BuildProject[] holder = start(helper, guild, key, origin);
		ZoneGameTests.setTask(recruit, Task.BUILDER);
		helper.succeedWhen(() -> {
			BuildProject project = holder[0];
			helper.assertTrue(project != null, "waiting for the site");
			helper.assertTrue(project.finished, "waiting for the sign (" + project.done + "/" + project.total + ") · " + status(level, recruit.record()));
			helper.assertTrue(level.getBlockEntity(origin.above()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity placed
					&& placed.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).getFirst().getString()
					.equals("Bienvenue"), "sign text copied");
			Container built = (Container) level.getBlockEntity(origin.offset(1, 1, 0));
			helper.assertTrue(built != null && built.isEmpty(), "the chest is built empty");
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 1200)
	public void menusOfASiteOpen(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Menus");
		Guild guild = recruit.guild();
		BlockPos origin = recruit.ground().offset(8, 1, 6);
		// An obstacle, a missing material, a block nobody can build.
		level.setBlockAndUpdate(origin, Blocks.OAK_PLANKS.defaultBlockState());
		String key = schematic("menus", 3, 2, 2, (x, y, z) -> y == 1 ? (x == 2 && z == 0 ? "minecraft:barrier" : null)
				: x == 1 ? "minecraft:glass" : "minecraft:stone_bricks");
		BuildProject[] holder = start(helper, guild, key, origin);
		helper.succeedWhen(() -> {
			BuildProject project = holder[0];
			helper.assertTrue(project != null, "waiting for the site");
			BuildSite site = BuildSites.peek(guild, project);
			helper.assertTrue(site != null && site.scanned(), "waiting for the scan");
			net.minecraft.server.level.ServerPlayer player = recruit.leader();
			dev.guildy.builds.BuildMenus.openList(player, guild);
			helper.assertTrue(player.containerMenu != player.inventoryMenu, "list open");
			dev.guildy.builds.BuildMenus.openDetail(player, guild, project.id);
			dev.guildy.builds.BuildMenus.openMaterials(player, guild, project.id, 0);
			dev.guildy.builds.BuildMenus.openObstacles(player, guild, project.id);
			dev.guildy.builds.BuildMenus.openPicker(player, guild, 0);
			helper.assertTrue(player.containerMenu != player.inventoryMenu, "menus open");
			// The villager menus with a builder: list (its lines, the "Chantiers" button), its detail (site and role buttons).
			try {
				ZoneGameTests.setTask(recruit, Task.BUILDER);
				dev.guildy.villager.VillagerGui.openList(player, guild);
				java.lang.reflect.Method detail = dev.guildy.villager.VillagerGui.class.getDeclaredMethod("openDetail",
						net.minecraft.server.level.ServerPlayer.class, Guild.class, java.util.UUID.class);
				detail.setAccessible(true);
				detail.invoke(null, player, guild, recruit.record().uuid);
			} catch (ReflectiveOperationException e) {
				helper.fail("villager menus: " + e);
			}
			helper.assertTrue(site.counts().blocked() == 1 && site.counts().notBuildable() == 1, "obstacle and barrier counted: " + site.counts());
			helper.assertTrue(site.missing(0).getOrDefault(Items.GLASS, 0) == 2, "glass missing");
			ZoneGameTests.command(level, player, "guild chantier list");
			boolean listed = TestSupport.chat(player).stream().anyMatch(line -> line.contains(project.name));
			helper.assertTrue(listed, "/guild chantier list shows the site");
			player.closeContainer();
			GuildManager.disband(guild);
		});
	}

	private static ItemStack plan(Guild guild, String key) {
		CompoundTag extra = new CompoundTag();
		extra.putString("schematic", key);
		extra.putString("guild", guild.id.toString());
		return GuildyItems.tag(new ItemStack(Items.PAPER), BuildPlacement.KIND_BUILD_PLAN, extra);
	}
}
