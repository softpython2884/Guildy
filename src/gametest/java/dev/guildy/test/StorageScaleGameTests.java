package dev.guildy.test;

import dev.guildy.Guildy;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.ChestRoom;
import dev.guildy.villager.ItemClassifier;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.StorageRouting;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerConfig;
import dev.guildy.villager.VillagerModule;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Storage at scale and specialised buildings (environment {@code guildy-test:day}: the sorter is never sent home):
 * <ul>
 * <li>500+ registered chests of 20 categories (a 128-chest stone group among them): the lookups stay cheap (index,
 * groups, 1 000 destinations, the audit of the big group are timed and logged) and the sorter puts the deposit away into
 * chests of the right categories;</li>
 * <li>the books building: "Livres enchantés" chests get the enchanted books and nothing else, paper and bookshelves go
 * to "Livres et enchantements", the rest to "Divers";</li>
 * <li>a chest room reserved to "Livres enchantés" receives only enchanted books.</li>
 * </ul>
 */
public class StorageScaleGameTests {
	private static final String ENCHANTED = "Livres enchantés";
	private static final String BOOKS = "Livres et enchantements";
	private static final String FALLBACK = "Divers";

	/** 4 walls on each side of the ground, 32 chests wide and 2 high: 512 single chests. */
	@GameTest(environment = "guildy-test:day", maxTicks = 4800)
	public void sorterStaysCheapWithFiveHundredChests(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Entrepot");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		List<BlockPos> walls = new ArrayList<>();
		for (int side : new int[]{1, -1}) {
			for (int wall = 0; wall < 4; wall++) {
				int dz = side * (5 + wall * 3);
				for (int dx = -16; dx < 16; dx++) {
					for (int dy = 1; dy <= 2; dy++) {
						BlockPos pos = ground.offset(dx, dy, dz);
						if (GuildManager.at(level, pos) != guild) {
							continue;
						}
						level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, side > 0 ? Direction.NORTH : Direction.SOUTH));
						walls.add(pos);
					}
				}
			}
		}
		helper.assertTrue(walls.size() >= 500, "at least 500 chests in the territory, got " + walls.size());
		List<String> categories = List.of("Bois", "Terre, sable et gravier", "Blocs de construction", "Mobilier et décoration",
				"Minerais et lingots", "Redstone", "Outils", "Armes", "Armures", BOOKS, "Potions et alchimie", "Nourriture",
				"Graines et pousses", "Plantes et fleurs", "Butin de monstres", "Nether", "End", "Colorants", FALLBACK);
		List<BlockPos> order = new ArrayList<>(walls);
		Collections.shuffle(order, new Random(5));
		Map<BlockPos, String> categoryAt = new HashMap<>();
		for (int i = 0; i < order.size(); i++) {
			// A quarter of the chests: one big stone group; the others in turn.
			String category = i % 4 == 0 ? "Pierre" : categories.get(i % categories.size());
			categoryAt.put(order.get(i), category);
			guild.villagers.chests.add(new StorageChest(order.get(i), StorageChest.Role.CATEGORY, category));
		}

		// ---- timings (logged for the release notes; budgets generous for slow machines)
		Class<?> storage = Class.forName("dev.guildy.villager.Storage");
		Method index = storage.getDeclaredMethod("index", Guild.class);
		Method route = storage.getDeclaredMethod("route", ServerLevel.class, Guild.class, Vec3.class, ItemStack.class, Set.class, int.class);
		Method groups = Class.forName("dev.guildy.villager.StorageGroups").getDeclaredMethod("groups", ServerLevel.class, Guild.class);
		Class<?> groupClass = Class.forName("dev.guildy.villager.StorageGroups$Group");
		Method groupChests = groupClass.getDeclaredMethod("chests");
		Method audit = Class.forName("dev.guildy.villager.Tidier").getDeclaredMethod("audit", ServerLevel.class, Guild.class, groupClass);
		for (Method method : List.of(index, route, groups, groupChests, audit)) {
			method.setAccessible(true);
		}
		guild.villagers.storageChanged();
		long t0 = System.nanoTime();
		index.invoke(null, guild);
		long indexNanos = System.nanoTime() - t0;
		t0 = System.nanoTime();
		List<?> list = (List<?>) groups.invoke(null, level, guild);
		long groupsNanos = System.nanoTime() - t0;
		t0 = System.nanoTime();
		groups.invoke(null, level, guild);
		long groupsCachedNanos = System.nanoTime() - t0;
		List<ItemStack> samples = List.of(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.OAK_LOG), new ItemStack(Items.IRON_INGOT),
				new ItemStack(Items.BREAD), new ItemStack(Items.BONE), book(level, Enchantments.SHARPNESS, 1), new ItemStack(Items.DIAMOND_SWORD),
				new ItemStack(Items.REDSTONE));
		Vec3 from = Vec3.atCenterOf(ground.above());
		int found = 0;
		t0 = System.nanoTime();
		for (int i = 0; i < 1000; i++) {
			found += route.invoke(null, level, guild, from, samples.get(i % samples.size()), Set.of(), StorageRouting.DEPOSIT_TIER) != null ? 1 : 0;
		}
		long routeNanos = System.nanoTime() - t0;
		Object stoneGroup = null;
		int biggest = 0;
		for (Object group : list) {
			int size = ((List<?>) groupChests.invoke(group)).size();
			if (size > biggest) {
				biggest = size;
				stoneGroup = group;
			}
		}
		// The audit of the big group, every chest full of mixed stone stacks (the worst case of a tidy check), then emptied.
		List<?> stoneChests = (List<?>) groupChests.invoke(stoneGroup);
		List<Item> stones = List.of(Items.COBBLESTONE, Items.STONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.COBBLED_DEEPSLATE,
				Items.TUFF, Items.CALCITE, Items.SMOOTH_STONE, Items.STONE_BRICKS);
		Random random = new Random(9);
		for (Object chest : stoneChests) {
			Container container = (Container) level.getBlockEntity(((StorageChest) chest).pos);
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				container.setItem(slot, new ItemStack(stones.get(random.nextInt(stones.size())), 1 + random.nextInt(64)));
			}
		}
		long auditNanos = Long.MAX_VALUE;
		long auditTotal = 0;
		for (int i = 0; i < 5; i++) {
			t0 = System.nanoTime();
			audit.invoke(null, level, guild, stoneGroup);
			long took = System.nanoTime() - t0;
			auditNanos = Math.min(auditNanos, took);
			auditTotal += took;
		}
		// A group bigger than storage.maxGroupChests is tidied in parts: 128 stone chests with a maximum of 64 = 2 parts.
		VillagerConfig.Storage storageConfig = VillagerModule.config().storage;
		int maxGroup = storageConfig.maxGroupChests;
		storageConfig.maxGroupChests = 64;
		guild.villagers.storageChanged();
		List<?> parted = (List<?>) groups.invoke(null, level, guild);
		Method key = groupClass.getDeclaredMethod("key");
		key.setAccessible(true);
		int stoneParts = 0;
		long partNanos = Long.MAX_VALUE;
		for (Object group : parted) {
			String name = (String) key.invoke(group);
			if (name.startsWith("0:pierre")) {
				stoneParts++;
				helper.assertTrue(((List<?>) groupChests.invoke(group)).size() <= 64, "a part holds at most 64 chests");
				t0 = System.nanoTime();
				audit.invoke(null, level, guild, group);
				partNanos = Math.min(partNanos, System.nanoTime() - t0);
			}
		}
		storageConfig.maxGroupChests = maxGroup;
		guild.villagers.storageChanged();
		helper.assertTrue(stoneParts == 2, "the stone group is cut into 2 parts, got " + stoneParts);
		Guildy.LOGGER.info("[GuildyTest] storage scale: audit of a 64-chest part {} ms", ms(partNanos));
		for (Object chest : stoneChests) {
			((Container) level.getBlockEntity(((StorageChest) chest).pos)).clearContent();
		}
		Guildy.LOGGER.info("[GuildyTest] storage scale: {} chests, {} groups; index {} ms, groups {} ms (cached {} ms), 1000 routes {} ms ({} us each), "
						+ "audit of a full {}-chest group {} ms (best of 5, average {} ms)", walls.size(), list.size(), ms(indexNanos), ms(groupsNanos),
				ms(groupsCachedNanos), ms(routeNanos), routeNanos / 1000 / 1000, biggest, ms(auditNanos), ms(auditTotal / 5));
		helper.assertTrue(found == 1000, "every sample has a destination");
		helper.assertTrue(biggest >= 120, "the stone group holds a quarter of the chests, got " + biggest);
		helper.assertTrue(routeNanos < 1_000_000_000L, "1000 destinations in less than a second, took " + ms(routeNanos) + " ms");
		helper.assertTrue(indexNanos < 200_000_000L, "index built in less than 200 ms, took " + ms(indexNanos) + " ms");

		// ---- the sorter puts the deposit away into chests of the right categories
		Container deposit = (Container) level.getBlockEntity(recruit.deposit());
		List<ItemStack> items = List.of(new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_LOG, 32),
				new ItemStack(Items.IRON_INGOT, 20), new ItemStack(Items.BREAD, 16), new ItemStack(Items.BONE, 30), new ItemStack(Items.REDSTONE, 40),
				book(level, Enchantments.SHARPNESS, 3), book(level, Enchantments.EFFICIENCY, 2));
		Map<Item, Integer> expected = new HashMap<>();
		for (int i = 0; i < items.size(); i++) {
			deposit.setItem(i, items.get(i).copy());
			expected.merge(items.get(i).getItem(), items.get(i).getCount(), Integer::sum);
		}
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.succeedWhen(() -> {
			helper.assertTrue(stacks(deposit) == 0, "waiting for the deposit to be emptied (" + stacks(deposit) + " stacks left)");
			helper.assertTrue(recruit.record().carried.isEmpty(), "waiting for the sorter to deliver everything");
			Map<Item, Integer> placed = new HashMap<>();
			for (BlockPos pos : walls) {
				Container chest = (Container) level.getBlockEntity(pos);
				String category = categoryAt.get(pos);
				for (int slot = 0; slot < chest.getContainerSize(); slot++) {
					ItemStack stack = chest.getItem(slot);
					if (stack.isEmpty()) {
						continue;
					}
					boolean right = category.equals(FALLBACK) || ItemClassifier.chain(stack).stream().anyMatch(c -> c.equalsIgnoreCase(category));
					helper.assertTrue(right, BuiltInRegistries.ITEM.getKey(stack.getItem()) + " in a '" + category + "' chest");
					placed.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
			helper.assertTrue(placed.equals(expected), "items lost or duplicated: " + placed + " instead of " + expected);
			GuildManager.disband(guild);
		});
	}

	/**
	 * The books building: two "Livres enchantés" chests far away, a "Livres et enchantements" chest and a "Divers" chest
	 * near the deposit. The enchanted books go to the building (never to the nearer chests), paper and bookshelves to
	 * "Livres et enchantements" (no "Livres et papier" chest), cobblestone and bread to "Divers": the building receives
	 * nothing else.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void booksBuildingGetsOnlyEnchantedBooks(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Livres");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		BlockPos buildingA = ground.offset(-8, 1, 8);
		BlockPos buildingB = ground.offset(-7, 1, 8);
		BlockPos books = ground.offset(5, 1, 2);
		BlockPos misc = ground.offset(5, 1, -2);
		for (BlockPos pos : List.of(buildingA, buildingB, books, misc)) {
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		}
		guild.villagers.chests.add(new StorageChest(buildingA, StorageChest.Role.CATEGORY, ENCHANTED));
		guild.villagers.chests.add(new StorageChest(buildingB, StorageChest.Role.CATEGORY, ENCHANTED));
		guild.villagers.chests.add(new StorageChest(books, StorageChest.Role.CATEGORY, BOOKS));
		guild.villagers.chests.add(new StorageChest(misc, StorageChest.Role.CATEGORY, FALLBACK));
		helper.assertTrue(ItemClassifier.chain(book(level, Enchantments.SHARPNESS, 1)).equals(List.of(ENCHANTED, BOOKS)),
				"an enchanted book: its sub-category, then the books");
		Container deposit = (Container) level.getBlockEntity(recruit.deposit());
		deposit.setItem(0, book(level, Enchantments.SHARPNESS, 1));
		deposit.setItem(1, book(level, Enchantments.EFFICIENCY, 2));
		deposit.setItem(2, book(level, Enchantments.UNBREAKING, 3));
		deposit.setItem(3, new ItemStack(Items.PAPER, 20));
		deposit.setItem(4, new ItemStack(Items.BOOKSHELF, 4));
		deposit.setItem(5, new ItemStack(Items.COBBLESTONE, 32));
		deposit.setItem(6, new ItemStack(Items.BREAD, 10));
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.succeedWhen(() -> {
			int enchanted = count(level, buildingA, Items.ENCHANTED_BOOK) + count(level, buildingB, Items.ENCHANTED_BOOK);
			helper.assertTrue(enchanted == 3, "waiting for the 3 enchanted books in the building (now " + enchanted + ")");
			helper.assertTrue(count(level, buildingA, null) + count(level, buildingB, null) == 3, "nothing else in the books building");
			helper.assertTrue(count(level, books, Items.PAPER) == 20 && count(level, books, Items.BOOKSHELF) == 4,
					"waiting for paper and bookshelves in 'Livres et enchantements'");
			helper.assertTrue(count(level, books, Items.ENCHANTED_BOOK) == 0, "no enchanted book in the nearer books chest");
			helper.assertTrue(count(level, misc, Items.COBBLESTONE) == 32 && count(level, misc, Items.BREAD) == 10, "waiting for the rest in 'Divers'");
			helper.assertTrue(stacks(deposit) == 0, "deposit emptied");
			GuildManager.disband(guild);
		});
	}

	/**
	 * A chest room of 3 columns × 2 chests reserved to "Livres enchantés": one column gets the category (the others stay
	 * in reserve), it receives the enchanted books, and the logs and cobblestone of the deposit never enter the room.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 3000)
	public void reservedRoomTakesOnlyItsCategories(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Reservee");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		List<BlockPos> room = new ArrayList<>();
		for (int column = 0; column < 3; column++) {
			for (int row = 0; row < 2; row++) {
				BlockPos pos = ground.offset(-6 + column, 2 - row, 6);
				level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
				room.add(pos);
			}
		}
		BlockPos misc = ground.offset(5, 1, -3);
		level.setBlockAndUpdate(misc, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(new StorageChest(misc, StorageChest.Role.CATEGORY, FALLBACK));
		Container deposit = (Container) level.getBlockEntity(recruit.deposit());
		deposit.setItem(0, book(level, Enchantments.SHARPNESS, 2));
		deposit.setItem(1, book(level, Enchantments.PROTECTION, 1));
		deposit.setItem(2, book(level, Enchantments.MENDING, 1));
		deposit.setItem(3, new ItemStack(Items.OAK_LOG, 64));
		deposit.setItem(4, new ItemStack(Items.COBBLESTONE, 64));
		ServerPlayer leader = recruit.leader();
		Method start = Class.forName("dev.guildy.villager.VillagerInteractions").getDeclaredMethod("startRoomSelection", ServerPlayer.class, Guild.class);
		start.setAccessible(true);
		start.invoke(null, leader, guild);
		TestSupport.useOnTop(leader, level, room.getFirst(), ItemStack.EMPTY);
		TestSupport.useOnTop(leader, level, room.getLast(), ItemStack.EMPTY);
		leader.closeContainer();
		helper.assertTrue(guild.villagers.rooms.size() == 1, "chest room created");
		ChestRoom chestRoom = guild.villagers.rooms.getFirst();
		chestRoom.categories = new ArrayList<>(List.of(ENCHANTED));
		Method plan = Class.forName("dev.guildy.villager.ChestRooms").getDeclaredMethod("plan", ServerLevel.class, Guild.class, ChestRoom.class,
				boolean.class, long.class);
		plan.setAccessible(true);
		plan.invoke(null, level, guild, chestRoom, false, level.getGameTime());
		int reserved = 0;
		for (BlockPos pos : room) {
			StorageChest chest = guild.villagers.chestAt(pos);
			helper.assertTrue(chest != null && chest.auto && chest.room == chestRoom.id, "automatic chest of the room");
			helper.assertTrue(chest.category.isEmpty() || chest.category.equals(ENCHANTED), "only the reserved category or the reserve, got '"
					+ chest.category + "'");
			reserved += chest.category.equals(ENCHANTED) ? 1 : 0;
		}
		helper.assertTrue(reserved == 2, "one column (2 chests) for the enchanted books, got " + reserved);
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.succeedWhen(() -> {
			int enchanted = 0;
			int others = 0;
			for (BlockPos pos : room) {
				enchanted += count(level, pos, Items.ENCHANTED_BOOK);
				others += count(level, pos, null) - count(level, pos, Items.ENCHANTED_BOOK);
			}
			helper.assertTrue(others == 0, "nothing but enchanted books in the reserved room");
			helper.assertTrue(enchanted == 3, "waiting for the 3 enchanted books in the room (now " + enchanted + ")");
			helper.assertTrue(count(level, misc, Items.OAK_LOG) == 64 && count(level, misc, Items.COBBLESTONE) == 64,
					"waiting for the logs and cobblestone in 'Divers'");
			GuildManager.disband(guild);
		});
	}

	// ------------------------------------------------------------------ helpers

	static ItemStack book(ServerLevel level, ResourceKey<Enchantment> enchantment, int lvl) {
		return EnchantmentHelper.createBook(new EnchantmentInstance(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), lvl));
	}

	private static String ms(long nanos) {
		return String.format(Locale.ROOT, "%.2f", nanos / 1e6);
	}

	private static int stacks(Container container) {
		int stacks = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			stacks += container.getItem(i).isEmpty() ? 0 : 1;
		}
		return stacks;
	}

	/** Items of that kind in the container (null: every item). */
	private static int count(ServerLevel level, BlockPos pos, Item item) {
		Container container = (Container) level.getBlockEntity(pos);
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && (item == null || stack.is(item))) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
