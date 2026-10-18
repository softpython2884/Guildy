package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.ChestFilter;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Filter chests and the in-chest column layout (environment {@code guildy-test:day}): the sorter fills a filter chest
 * by columns before the category chest (the server owner's wheat / sugar cane / seeds chest), the chat scan proposes
 * diorite / granite / wheat filters and a food category chest for a small room and registers them, and a filter chest
 * loses the items it refuses (to their category chest, else to the deposit) while it takes its own items from the
 * category chest.
 */
public class FilterGameTests {
	private static final String FOOD = "Nourriture";
	private static final String STONE = "Pierre";

	/** Slot of a chest at (column, row), 0-based (rows of 9). */
	private static int slot(int column, int row) {
		return row * 9 + column;
	}

	/**
	 * A double chest filtered on wheat, sugar cane and wheat seeds, holding scattered seeds and some wheat; the deposit
	 * brings the rest of the wheat and the cane, a "Nourriture" chest stands next to the deposit. The sorter puts every
	 * item into the filter chest (before the category chest) and lays it out by columns, each kind at the top of a fresh
	 * column, in the creative order: cane in column 1, seeds in columns 2 to 6, wheat in columns 7 to 9.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 3000)
	public void sorterFillsAFilterChestByColumns(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Colonne");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		BlockPos left = ground.offset(-3, 1, 6);
		BlockPos right = left.west();
		level.setBlockAndUpdate(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		level.setBlockAndUpdate(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		BlockPos food = recruit.deposit().offset(0, 0, 2);
		level.setBlockAndUpdate(food, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(StorageChest.filtered(left, ChestFilter.items(List.of("minecraft:wheat", "minecraft:sugar_cane", "minecraft:wheat_seeds"))));
		guild.villagers.chests.add(new StorageChest(food, StorageChest.Role.CATEGORY, FOOD));
		Container chest = combined(level, left);
		helper.assertTrue(chest.getContainerSize() == 54, "double chest");
		Random random = new Random(11);
		for (int i = 0; i < 27; i++) {
			put(chest, random, new ItemStack(Items.WHEAT_SEEDS, 64));
		}
		for (int i = 0; i < 3; i++) {
			put(chest, random, new ItemStack(Items.WHEAT, 64));
		}
		Container deposit = (Container) level.getBlockEntity(recruit.deposit());
		for (int i = 0; i < 10; i++) {
			deposit.setItem(i, new ItemStack(Items.WHEAT, 64));
		}
		deposit.setItem(10, new ItemStack(Items.WHEAT, 38));
		deposit.setItem(11, new ItemStack(Items.SUGAR_CANE, 20));
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.succeedWhen(() -> {
			Map<String, Integer> totals = totals(chest);
			for (ItemStack stack : recruit.record().carried) {
				totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
			}
			totals(deposit).forEach((id, count) -> totals.merge(id, count, Integer::sum));
			totals(single(level, food)).forEach((id, count) -> totals.merge(id, count, Integer::sum));
			helper.assertTrue(totals.equals(Map.of("wheat", 13 * 64 + 38, "wheat_seeds", 27 * 64, "sugar_cane", 20)), "items lost or duplicated: " + totals);
			helper.assertTrue(stacks(deposit) == 0, "waiting for the deposit to be emptied");
			helper.assertTrue(stacks(single(level, food)) == 0, "the filter chest comes before the 'Nourriture' chest");
			expect(helper, chest, 0, 0, Items.SUGAR_CANE, 20);
			for (int column = 1; column <= 4; column++) {
				for (int row = 0; row < 6; row++) {
					expect(helper, chest, column, row, Items.WHEAT_SEEDS, 64);
				}
			}
			for (int row = 0; row < 6; row++) {
				expect(helper, chest, 5, row, row < 3 ? Items.WHEAT_SEEDS : null, 64);
				expect(helper, chest, 6, row, Items.WHEAT, 64);
				expect(helper, chest, 7, row, Items.WHEAT, 64);
				expect(helper, chest, 0, row, row == 0 ? Items.SUGAR_CANE : null, 20);
			}
			expect(helper, chest, 8, 0, Items.WHEAT, 64);
			expect(helper, chest, 8, 1, Items.WHEAT, 38);
			for (int row = 2; row < 6; row++) {
				expect(helper, chest, 8, row, null, 0);
			}
			GuildManager.disband(guild);
		});
	}

	/**
	 * A small room: diorite blocks, granite blocks (the chest renamed by a player), wheat and seeds, ten foods, a mix of
	 * everything and an empty chest. The chat scan proposes the diorite / granite / wheat families and the food
	 * category, leaves the mixed and empty chests alone and skips the registered deposit; [Appliquer] registers them, the
	 * new chests take the filter's name except the one the player named.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 400)
	public void scanProposesFiltersAndApplyRegistersThem(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Scan");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		ServerPlayer leader = recruit.leader();
		BlockPos[] chests = new BlockPos[6];
		for (int i = 0; i < chests.length; i++) {
			chests[i] = ground.offset(-6 + 2 * i, 1, 6);
			level.setBlockAndUpdate(chests[i], Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
		}
		fill(level, chests[0], new ItemStack(Items.DIORITE, 64), new ItemStack(Items.POLISHED_DIORITE, 32), new ItemStack(Items.DIORITE_SLAB, 16),
				new ItemStack(Items.DIORITE_STAIRS, 8), new ItemStack(Items.DIORITE_WALL, 4));
		fill(level, chests[1], new ItemStack(Items.GRANITE, 64), new ItemStack(Items.POLISHED_GRANITE, 64), new ItemStack(Items.GRANITE_WALL, 10));
		fill(level, chests[2], new ItemStack(Items.WHEAT, 64), new ItemStack(Items.WHEAT, 64), new ItemStack(Items.WHEAT_SEEDS, 30));
		fill(level, chests[3], new ItemStack(Items.STONE, 5), new ItemStack(Items.OAK_PLANKS, 5), new ItemStack(Items.IRON_INGOT, 5),
				new ItemStack(Items.BREAD, 5), new ItemStack(Items.BONE, 5), new ItemStack(Items.STRING, 5), new ItemStack(Items.REDSTONE, 5),
				new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.APPLE, 5), new ItemStack(Items.TORCH, 5));
		fill(level, chests[4], new ItemStack(Items.BREAD, 5), new ItemStack(Items.COOKED_BEEF, 5), new ItemStack(Items.COOKED_PORKCHOP, 5),
				new ItemStack(Items.COOKED_CHICKEN, 5), new ItemStack(Items.COOKED_MUTTON, 5), new ItemStack(Items.COOKIE, 5), new ItemStack(Items.COOKED_COD, 5),
				new ItemStack(Items.PUMPKIN_PIE, 5), new ItemStack(Items.BAKED_POTATO, 5), new ItemStack(Items.COOKED_SALMON, 5));
		// The granite chest bears a name given by a player: it is never replaced.
		Method setName = Class.forName("dev.guildy.villager.StorageLabels").getDeclaredMethod("setName", ServerLevel.class, BlockPos.class, Component.class);
		setName.setAccessible(true);
		setName.invoke(null, level, chests[1], Component.literal("Mon granite"));
		int registered = guild.villagers.chests.size();
		ZoneGameTests.command(level, leader, "guild stockage scan 1");
		Method ready = Class.forName("dev.guildy.villager.ChestScan").getDeclaredMethod("ready", java.util.UUID.class);
		ready.setAccessible(true);
		boolean[] applied = {false};
		helper.succeedWhen(() -> {
			if (!applied[0]) {
				helper.assertTrue(invoke(ready, leader.getUUID()), "waiting for the scan preview");
				helper.assertTrue(guild.villagers.chests.size() == registered, "the preview registers nothing");
				List<String> chat = TestSupport.chat(leader).stream().map(line -> line.replaceAll("§.", "")).toList();
				helper.assertTrue(chat.stream().anyMatch(line -> line.contains("Familles : 3") && line.contains("Catégories : 1")
						&& line.contains("Trop mélangés : 1") && line.contains("Vides : 1") && line.contains("Déjà enregistrés : 1")),
						"preview counts: " + chat);
				ZoneGameTests.command(level, leader, "guild villagers storage scan apply");
				applied[0] = true;
			}
			StorageChest diorite = guild.villagers.chestAt(chests[0]);
			StorageChest granite = guild.villagers.chestAt(chests[1]);
			StorageChest wheat = guild.villagers.chestAt(chests[2]);
			StorageChest food = guild.villagers.chestAt(chests[4]);
			helper.assertTrue(diorite != null && diorite.isFilter() && "material:diorite".equals(diorite.filter.key()), "diorite filter chest");
			helper.assertTrue(granite != null && granite.isFilter() && "material:granite".equals(granite.filter.key()), "granite filter chest");
			helper.assertTrue(wheat != null && wheat.isFilter() && "material:wheat".equals(wheat.filter.key()), "wheat and seeds filter chest");
			helper.assertTrue(food != null && food.role == StorageChest.Role.CATEGORY && FOOD.equals(food.category), "food category chest");
			helper.assertTrue(guild.villagers.chestAt(chests[3]) == null && guild.villagers.chestAt(chests[5]) == null,
					"mixed and empty chests are left alone");
			helper.assertTrue(guild.villagers.chests.size() == registered + 4, "four containers registered");
			helper.assertTrue("Diorite".equals(name(level, chests[0])), "named after its family (now " + name(level, chests[0]) + ")");
			helper.assertTrue("Blé et graines".equals(name(level, chests[2])), "family name from lang.json (now " + name(level, chests[2]) + ")");
			helper.assertTrue("Mon granite".equals(name(level, chests[1])), "a player's name is kept (now " + name(level, chests[1]) + ")");
			helper.assertTrue(FOOD.equals(name(level, chests[4])), "the category chest is named after its category");
			// The registered deposit was skipped, not turned into anything.
			helper.assertTrue(guild.villagers.chestAt(recruit.deposit()).isDeposit(), "the deposit stays a deposit");
			GuildManager.disband(guild);
		});
	}

	/**
	 * A diorite filter chest holding cobblestone and bones, a "Pierre" chest holding polished diorite: the sorter takes
	 * the cobblestone to the "Pierre" chest, the bones (no other chest takes them) to the deposit, and the polished
	 * diorite into the filter chest, which ends merged by columns.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 3000)
	public void filterChestLosesTheItemsItRefuses(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Refus");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		BlockPos filter = ground.offset(-4, 1, 5);
		BlockPos stone = ground.offset(5, 1, 5);
		for (BlockPos pos : List.of(filter, stone)) {
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
		}
		guild.villagers.chests.add(StorageChest.filtered(filter, ChestFilter.family(ChestFilter.Mode.MATERIAL, "diorite", "minecraft:diorite")));
		guild.villagers.chests.add(new StorageChest(stone, StorageChest.Role.CATEGORY, STONE));
		Container filterChest = single(level, filter);
		filterChest.setItem(4, new ItemStack(Items.DIORITE, 40));
		filterChest.setItem(7, new ItemStack(Items.COBBLESTONE, 32));
		filterChest.setItem(13, new ItemStack(Items.DIORITE, 24));
		filterChest.setItem(20, new ItemStack(Items.BONE, 5));
		filterChest.setItem(22, new ItemStack(Items.POLISHED_DIORITE, 10));
		Container stoneChest = single(level, stone);
		stoneChest.setItem(0, new ItemStack(Items.POLISHED_DIORITE, 20));
		stoneChest.setItem(1, new ItemStack(Items.STONE, 12));
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.succeedWhen(() -> {
			Map<String, Integer> totals = totals(filterChest);
			totals(stoneChest).forEach((id, count) -> totals.merge(id, count, Integer::sum));
			totals(single(level, recruit.deposit())).forEach((id, count) -> totals.merge(id, count, Integer::sum));
			for (ItemStack stack : recruit.record().carried) {
				totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
			}
			helper.assertTrue(totals.equals(Map.of("diorite", 64, "polished_diorite", 30, "cobblestone", 32, "bone", 5, "stone", 12)),
					"items lost or duplicated: " + totals);
			helper.assertTrue(count(stoneChest, Items.COBBLESTONE) == 32, "waiting for the cobblestone in the 'Pierre' chest");
			helper.assertTrue(count(single(level, recruit.deposit()), Items.BONE) == 5, "waiting for the bones in the deposit (no other chest takes them)");
			helper.assertTrue(count(filterChest, Items.POLISHED_DIORITE) == 30, "waiting for the polished diorite in the filter chest");
			helper.assertTrue(count(stoneChest, Items.STONE) == 12 && count(stoneChest, Items.POLISHED_DIORITE) == 0, "the stone stays in its chest");
			// Merged by columns: diorite at the top of column 1, polished diorite at the top of column 2, nothing else.
			helper.assertTrue(filterChest.getItem(slot(0, 0)).is(Items.DIORITE) && filterChest.getItem(slot(0, 0)).getCount() == 64
					&& filterChest.getItem(slot(1, 0)).is(Items.POLISHED_DIORITE) && filterChest.getItem(slot(1, 0)).getCount() == 30
					&& stacks(filterChest) == 2, "waiting for the filter chest to be merged by columns");
			GuildManager.disband(guild);
		});
	}

	/**
	 * "/guild stockage filtre ici" on the chest the player looks at: a granite filter from its contents, named after
	 * it; "filter clear" unregisters it (name removed); "rangement lignes" sets the guild's in-chest layout.
	 */
	@GameTest(maxTicks = 40)
	public void filterCommandsUseTheChestYouLookAt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 6);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Regard");
		BlockPos pos = ground.offset(0, 1, -4);
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
		fill(level, pos, new ItemStack(Items.GRANITE, 64), new ItemStack(Items.POLISHED_GRANITE, 20));
		// Standing 2.5 blocks south of the chest, looking north and down at it.
		leader.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5, java.util.Set.of(), 180, 35, true);
		ZoneGameTests.command(level, leader, "guild stockage filtre ici");
		StorageChest chest = guild.villagers.chestAt(pos);
		helper.assertTrue(chest != null && chest.isFilter() && "material:granite".equals(chest.filter.key()),
				"the chest looked at becomes a granite filter chest (chat: " + TestSupport.chat(leader) + ")");
		helper.assertTrue("Granite".equals(name(level, pos)), "named after its filter (now " + name(level, pos) + ")");
		ZoneGameTests.command(level, leader, "guild villagers storage filter clear");
		helper.assertTrue(guild.villagers.chestAt(pos) == null && name(level, pos) == null, "unregistered, name removed");
		ZoneGameTests.command(level, leader, "guild stockage rangement lignes");
		helper.assertTrue("rows".equals(guild.villagers.chestLayout), "layout by rows (now '" + guild.villagers.chestLayout + "')");
		ZoneGameTests.command(level, leader, "guild stockage rangement colonnes");
		helper.assertTrue("columns".equals(guild.villagers.chestLayout), "layout by columns");
		GuildManager.disband(guild);
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	private static boolean invoke(Method method, Object argument) {
		try {
			return (boolean) method.invoke(null, argument);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The container at {@code pos} with the other half of a double chest. */
	private static Container combined(ServerLevel level, BlockPos pos) {
		var state = level.getBlockState(pos);
		Container container = ChestBlock.getContainer((ChestBlock) state.getBlock(), state, level, pos, true);
		return container == null ? single(level, pos) : container;
	}

	private static Container single(ServerLevel level, BlockPos pos) {
		return (Container) level.getBlockEntity(pos);
	}

	private static void put(Container container, Random random, ItemStack stack) {
		while (true) {
			int slot = random.nextInt(container.getContainerSize());
			if (container.getItem(slot).isEmpty()) {
				container.setItem(slot, stack);
				return;
			}
		}
	}

	private static void fill(ServerLevel level, BlockPos pos, ItemStack... stacks) {
		Container container = single(level, pos);
		for (int i = 0; i < stacks.length; i++) {
			container.setItem(i, stacks[i]);
		}
	}

	/** The slot at (column, row) holds {@code count} of {@code item} (null: empty). */
	private static void expect(GameTestHelper helper, Container chest, int column, int row, Item item, int count) {
		ItemStack stack = chest.getItem(slot(column, row));
		boolean ok = item == null ? stack.isEmpty() : stack.is(item) && stack.getCount() == count;
		helper.assertTrue(ok, "waiting for column " + (column + 1) + ", row " + (row + 1) + " to hold " + (item == null ? "nothing" : count + " " + item)
				+ " (now " + stack + ")");
	}

	private static Map<String, Integer> totals(Container container) {
		Map<String, Integer> totals = new TreeMap<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
			}
		}
		return totals;
	}

	private static int stacks(Container container) {
		int stacks = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (!container.getItem(i).isEmpty()) {
				stacks++;
			}
		}
		return stacks;
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

	private static String name(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof BaseContainerBlockEntity container && container.getCustomName() != null
				? container.getCustomName().getString() : null;
	}
}
