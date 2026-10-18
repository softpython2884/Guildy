package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.ChestRoom;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Tidying of the chest groups by a sorter (environment {@code guildy-test:day}: the clock is frozen, the night shelter
 * never stops the sorter): a wall of armor chests becomes perfectly merged and ordered in reading order, a misplaced
 * stone stack leaves an armor chest for the stone chest, a chest open by a player is never touched, and a chest room
 * gets its categories by columns and receives the deposit.
 */
public class TidyGameTests {
	private static final String ARMOR = "Armures";
	private static final String STONE = "Pierre";

	/**
	 * 2 columns × 4 chests facing south (reading order: the west column top to bottom, then the east one), shuffled
	 * armor and half stacks of smithing templates, the guild's in-chest layout set to rows (the old reading order):
	 * after one reorganisation everything is merged, packed from the first chest and in the creative inventory order
	 * (shield, leather, iron helmet / chestplate / leggings / boots, enchanted after plain, diamond, templates last), with
	 * the very same items.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void armorWallIsMergedAndOrderedInReadingOrder(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Mur");
		Guild guild = recruit.guild();
		guild.villagers.chestLayout = "rows";
		BlockPos ground = recruit.ground();
		List<BlockPos> wall = new ArrayList<>();
		for (int column = 0; column < 2; column++) {
			for (int row = 0; row < 4; row++) {
				BlockPos pos = ground.offset(-3 + column, 4 - row, 6);
				level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
				wall.add(pos);
			}
		}
		// Registered in a scrambled order: the reading order never depends on it.
		List<BlockPos> registration = new ArrayList<>(wall);
		Collections.shuffle(registration, new Random(7));
		for (BlockPos pos : registration) {
			guild.villagers.chests.add(new StorageChest(pos, StorageChest.Role.CATEGORY, ARMOR));
		}
		ItemStack enchanted = new ItemStack(Items.IRON_HELMET);
		enchanted.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 2);
		ItemStack damaged = new ItemStack(Items.DIAMOND_CHESTPLATE);
		damaged.setDamageValue(100);
		List<ItemStack> items = new ArrayList<>();
		for (Item item : List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS)) {
			for (int i = 0; i < 12; i++) {
				items.add(new ItemStack(item));
			}
		}
		for (int i = 0; i < 6; i++) {
			items.add(new ItemStack(Items.DIAMOND_HELMET));
		}
		for (int i = 0; i < 4; i++) {
			items.add(new ItemStack(Items.LEATHER_BOOTS));
		}
		for (int i = 0; i < 3; i++) {
			items.add(new ItemStack(Items.SHIELD));
		}
		items.add(enchanted);
		items.add(damaged);
		for (int count : new int[]{10, 20, 15, 30}) {
			items.add(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, count));
		}
		Random random = new Random(42);
		for (ItemStack stack : items) {
			while (true) {
				Container chest = (Container) level.getBlockEntity(wall.get(random.nextInt(wall.size())));
				int slot = random.nextInt(chest.getContainerSize());
				if (chest.getItem(slot).isEmpty()) {
					chest.setItem(slot, stack);
					break;
				}
			}
		}
		Map<String, Integer> expected = totals(level, wall);
		ZoneGameTests.setTask(recruit, Task.SORTER);
		List<Predicate<ItemStack>> order = List.of(
				s -> s.is(Items.SHIELD),
				s -> s.is(Items.LEATHER_BOOTS),
				s -> s.is(Items.IRON_HELMET) && !s.isEnchanted(),
				s -> s.is(Items.IRON_HELMET) && s.isEnchanted(),
				s -> s.is(Items.IRON_CHESTPLATE),
				s -> s.is(Items.IRON_LEGGINGS),
				s -> s.is(Items.IRON_BOOTS),
				s -> s.is(Items.DIAMOND_HELMET),
				s -> s.is(Items.DIAMOND_CHESTPLATE),
				s -> s.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
		helper.succeedWhen(() -> {
			Map<String, Integer> now = totals(level, wall);
			helper.assertTrue(now.equals(expected), "items lost or duplicated: " + now + " instead of " + expected);
			List<ItemStack> sequence = readingOrder(helper, level, wall);
			assertMerged(helper, sequence);
			int last = -1;
			for (ItemStack stack : sequence) {
				int rank = -1;
				for (int i = 0; i < order.size() && rank < 0; i++) {
					rank = order.get(i).test(stack) ? i : -1;
				}
				helper.assertTrue(rank >= last, "waiting for the canonical order (" + stack + " after rank " + last + ")");
				last = rank;
			}
			// 65 stacks after merging: chests 1 and 2 (west column, top) full, chest 3 holds the last 11 stacks.
			helper.assertTrue(used(level, wall.get(0)) == 27 && used(level, wall.get(1)) == 27 && used(level, wall.get(2)) == 11,
					"chests filled in reading order");
			ItemStack enchantedNow = find(level, wall, s -> s.is(Items.IRON_HELMET) && s.isEnchanted());
			helper.assertTrue(enchantedNow != null && enchantedNow.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size() == 1,
					"the enchantment is kept");
			ItemStack damagedNow = find(level, wall, s -> s.is(Items.DIAMOND_CHESTPLATE));
			helper.assertTrue(damagedNow != null && damagedNow.getDamageValue() == 100, "the damage is kept");
			GuildManager.disband(guild);
		});
	}

	/** A stone stack in an armor chest leaves it for the stone chest; the armor stays. */
	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void misplacedStoneLeavesTheArmorChest(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Egare");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		BlockPos armor = ground.offset(-4, 1, 5);
		BlockPos stone = ground.offset(5, 1, 5);
		for (BlockPos pos : List.of(armor, stone)) {
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		}
		guild.villagers.chests.add(new StorageChest(armor, StorageChest.Role.CATEGORY, ARMOR));
		guild.villagers.chests.add(new StorageChest(stone, StorageChest.Role.CATEGORY, STONE));
		Container armorChest = (Container) level.getBlockEntity(armor);
		armorChest.setItem(0, new ItemStack(Items.IRON_HELMET));
		armorChest.setItem(4, new ItemStack(Items.COBBLESTONE, 32));
		armorChest.setItem(9, new ItemStack(Items.IRON_BOOTS));
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.succeedWhen(() -> {
			int carried = 0;
			for (ItemStack stack : recruit.record().carried) {
				carried += stack.is(Items.COBBLESTONE) ? stack.getCount() : 0;
			}
			int inArmor = count(level, armor, Items.COBBLESTONE);
			int inStone = count(level, stone, Items.COBBLESTONE);
			helper.assertTrue(inArmor + inStone + carried == 32, "cobblestone lost or duplicated");
			helper.assertTrue(inStone == 32, "waiting for the stone in the 'Pierre' chest (now " + inStone + ")");
			helper.assertTrue(inArmor == 0, "no stone left in the armor chest");
			helper.assertTrue(count(level, armor, Items.IRON_HELMET) == 1 && count(level, armor, Items.IRON_BOOTS) == 1, "the armor stays");
			GuildManager.disband(guild);
		});
	}

	/**
	 * Two armor chests, one open by a player: only the other one is reorganised while the menu stays open (the open one
	 * keeps its exact contents); once closed, the whole group is tidied.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 3000)
	public void chestOpenByAPlayerIsNotTouched(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Ouvert");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		BlockPos open = ground.offset(2, 1, 2);
		BlockPos other = ground.offset(-2, 1, 2);
		for (BlockPos pos : List.of(open, other)) {
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
			guild.villagers.chests.add(new StorageChest(pos, StorageChest.Role.CATEGORY, ARMOR));
		}
		Container openChest = (Container) level.getBlockEntity(open);
		openChest.setItem(3, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 10));
		openChest.setItem(7, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 10));
		openChest.setItem(12, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 10));
		openChest.setItem(20, new ItemStack(Items.IRON_HELMET));
		Container otherChest = (Container) level.getBlockEntity(other);
		otherChest.setItem(5, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 5));
		otherChest.setItem(15, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 7));
		otherChest.setItem(2, new ItemStack(Items.IRON_BOOTS));
		otherChest.setItem(25, new ItemStack(Items.SHIELD));
		ServerPlayer leader = recruit.leader();
		leader.teleportTo(level, open.getX() + 0.5, open.getY(), open.getZ() + 1.5, java.util.Set.of(), 0, 0, true);
		leader.openMenu(level.getBlockState(open).getMenuProvider(level, open));
		helper.assertTrue(leader.containerMenu != leader.inventoryMenu, "the chest menu is open");
		List<ItemStack> snapshot = contents(level, open);
		Map<String, Integer> expected = totals(level, List.of(open, other));
		ZoneGameTests.setTask(recruit, Task.SORTER);
		boolean[] touched = {false};
		long[] otherSortedAt = {-1};
		boolean[] closed = {false};
		helper.succeedWhen(() -> {
			if (!closed[0] && !sameContents(snapshot, contents(level, open))) {
				touched[0] = true;
			}
			helper.assertTrue(!touched[0], "the chest open by a player was modified");
			helper.assertTrue(totals(level, List.of(open, other)).equals(expected), "items lost or duplicated");
			if (!closed[0]) {
				helper.assertTrue(leader.containerMenu != leader.inventoryMenu, "the menu must stay open during the first phase");
				// The other chest: its own items merged and in order (shield, iron boots, then the 12 templates).
				helper.assertTrue(count(level, other, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 12 && stacks(level, other) == 3,
						"waiting for the other chest to be reorganised");
				helper.assertTrue(otherChest.getItem(0).is(Items.SHIELD), "the shield first in the other chest");
				if (otherSortedAt[0] < 0) {
					otherSortedAt[0] = helper.getTick();
				}
				// Give the sorter 10 more seconds to (not) touch the open chest, then close it.
				helper.assertTrue(helper.getTick() - otherSortedAt[0] >= 200, "watching the open chest");
				leader.closeContainer();
				closed[0] = true;
			}
			helper.assertTrue(stacks(level, open) == 0, "waiting for the whole group to be tidied once the chest is closed");
			helper.assertTrue(count(level, other, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 42
					&& otherChest.getItem(1).is(Items.IRON_HELMET), "the group is merged into the first chest");
			GuildManager.disband(guild);
		});
	}

	/**
	 * Two armor chests 12 blocks apart (out of reach of one standing spot): the items of the second one are carried into
	 * the first one (first in reading order), merged and ordered there by columns (each kind at the top of its own
	 * column: shield, the two helmets, boots, templates), without losing anything.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 3000)
	public void distantChestsOfAGroupAreFilledByCarrying(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Loin");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		BlockPos first = ground.offset(-6, 1, 6);
		BlockPos second = ground.offset(6, 1, 6);
		for (BlockPos pos : List.of(second, first)) {
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
			guild.villagers.chests.add(new StorageChest(pos, StorageChest.Role.CATEGORY, ARMOR));
		}
		Container far = (Container) level.getBlockEntity(second);
		far.setItem(0, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 20));
		far.setItem(4, new ItemStack(Items.IRON_BOOTS));
		far.setItem(9, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 20));
		far.setItem(13, new ItemStack(Items.IRON_HELMET));
		far.setItem(20, new ItemStack(Items.IRON_HELMET));
		((Container) level.getBlockEntity(first)).setItem(26, new ItemStack(Items.SHIELD));
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.succeedWhen(() -> {
			int templates = count(level, first, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) + count(level, second, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
			int helmets = count(level, first, Items.IRON_HELMET) + count(level, second, Items.IRON_HELMET);
			for (ItemStack stack : recruit.record().carried) {
				templates += stack.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) ? stack.getCount() : 0;
				helmets += stack.is(Items.IRON_HELMET) ? stack.getCount() : 0;
			}
			helper.assertTrue(templates == 40 && helmets == 2, "items lost or duplicated (templates " + templates + ", helmets " + helmets + ")");
			helper.assertTrue(stacks(level, second) == 0, "waiting for the far chest to be emptied into the first one");
			Container near = (Container) level.getBlockEntity(first);
			// By columns (slot = row * 9 + column): column 1 shield, column 2 both helmets, column 3 boots, column 4 templates.
			helper.assertTrue(near.getItem(0).is(Items.SHIELD) && near.getItem(1).is(Items.IRON_HELMET) && near.getItem(10).is(Items.IRON_HELMET)
					&& near.getItem(2).is(Items.IRON_BOOTS) && near.getItem(3).getCount() == 40 && stacks(level, first) == 5,
					"waiting for the first chest to be merged and ordered by columns");
			GuildManager.disband(guild);
		});
	}

	/**
	 * A chest room of 3 columns × 2 chests (created by clicking two corners): the columns get Bois, Pierre, Divers from
	 * what the deposit holds, the deposit is sorted into them, and each column is filled from its top chest.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void chestRoomGetsColumnsAndReceivesTheDeposit(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Salle");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		BlockPos[][] room = new BlockPos[3][2];
		for (int column = 0; column < 3; column++) {
			for (int row = 0; row < 2; row++) {
				BlockPos pos = ground.offset(-6 + column, 2 - row, 6);
				level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
				room[column][row] = pos;
			}
		}
		Container deposit = (Container) level.getBlockEntity(recruit.deposit());
		for (int i = 0; i < 3; i++) {
			deposit.setItem(i, new ItemStack(Items.OAK_LOG, 64));
		}
		deposit.setItem(5, new ItemStack(Items.COBBLESTONE, 64));
		deposit.setItem(8, new ItemStack(Items.COBBLESTONE, 64));
		ServerPlayer leader = recruit.leader();
		Method start = Class.forName("dev.guildy.villager.VillagerInteractions")
				.getDeclaredMethod("startRoomSelection", ServerPlayer.class, Guild.class);
		start.setAccessible(true);
		start.invoke(null, leader, guild);
		TestSupport.useOnTop(leader, level, room[0][0], ItemStack.EMPTY);
		TestSupport.useOnTop(leader, level, room[2][1], ItemStack.EMPTY);
		leader.closeContainer();
		helper.assertTrue(guild.villagers.rooms.size() == 1, "chest room created");
		ChestRoom chestRoom = guild.villagers.rooms.getFirst();
		String[] columns = {"Bois", STONE, "Divers"};
		for (int column = 0; column < 3; column++) {
			for (int row = 0; row < 2; row++) {
				StorageChest chest = guild.villagers.chestAt(room[column][row]);
				helper.assertTrue(chest != null && chest.auto && chest.room == chestRoom.id, "automatic chest of the room at column " + column);
				helper.assertTrue(columns[column].equals(chest.category), "column " + column + " assigned to " + columns[column]
						+ " (now '" + chest.category + "')");
				helper.assertTrue(columns[column].equals(name(level, room[column][row])), "chest named after its category");
			}
		}
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.succeedWhen(() -> {
			int logs = count(level, room[0][0], Items.OAK_LOG) + count(level, room[0][1], Items.OAK_LOG);
			int cobble = count(level, room[1][0], Items.COBBLESTONE) + count(level, room[1][1], Items.COBBLESTONE);
			helper.assertTrue(logs == 192 && cobble == 128, "waiting for the deposit in the room (logs " + logs + ", cobblestone " + cobble + ")");
			helper.assertTrue(stacks(level, recruit.deposit()) == 0, "deposit emptied");
			helper.assertTrue(count(level, room[0][0], Items.OAK_LOG) == 192 && count(level, room[1][0], Items.COBBLESTONE) == 128,
					"waiting for each column to be filled from its top chest");
			GuildManager.disband(guild);
		});
	}

	/**
	 * A room of 4 columns × 3 chests whose chests hold a random mix of logs, cobblestone and iron ingots: the columns get
	 * Bois, Pierre, Minerais et lingots and Divers, every misplaced stack is taken to its column, and each column ends
	 * merged and packed from its top chest, with the very same items.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 9000)
	public void mixedChestRoomEndsTidy(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Melange");
		Guild guild = recruit.guild();
		BlockPos ground = recruit.ground();
		List<List<BlockPos>> columns = new ArrayList<>();
		List<BlockPos> all = new ArrayList<>();
		for (int column = 0; column < 4; column++) {
			List<BlockPos> chests = new ArrayList<>();
			for (int row = 0; row < 3; row++) {
				BlockPos pos = ground.offset(-8 + column, 3 - row, 6);
				level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
				chests.add(pos);
				all.add(pos);
			}
			columns.add(chests);
		}
		Random random = new Random(99);
		Item[] kinds = {Items.OAK_LOG, Items.COBBLESTONE, Items.IRON_INGOT};
		for (int i = 0; i < 48; i++) {
			Container chest = (Container) level.getBlockEntity(all.get(random.nextInt(all.size())));
			int slot = random.nextInt(chest.getContainerSize());
			if (chest.getItem(slot).isEmpty()) {
				chest.setItem(slot, new ItemStack(kinds[i % 3], 1 + random.nextInt(64)));
			}
		}
		Map<String, Integer> expected = totals(level, all);
		ServerPlayer leader = recruit.leader();
		Method start = Class.forName("dev.guildy.villager.VillagerInteractions")
				.getDeclaredMethod("startRoomSelection", ServerPlayer.class, Guild.class);
		start.setAccessible(true);
		start.invoke(null, leader, guild);
		TestSupport.useOnTop(leader, level, all.getFirst(), ItemStack.EMPTY);
		TestSupport.useOnTop(leader, level, all.getLast(), ItemStack.EMPTY);
		leader.closeContainer();
		helper.assertTrue(guild.villagers.rooms.size() == 1, "chest room created");
		String[] categories = {"Bois", STONE, "Minerais et lingots", "Divers"};
		for (int column = 0; column < 4; column++) {
			StorageChest chest = guild.villagers.chestAt(columns.get(column).getFirst());
			helper.assertTrue(chest != null && categories[column].equals(chest.category),
					"column " + column + " assigned to " + categories[column] + " (now '" + (chest == null ? null : chest.category) + "')");
		}
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.succeedWhen(() -> {
			Map<String, Integer> now = totals(level, all);
			for (ItemStack stack : recruit.record().carried) {
				now.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
			helper.assertTrue(now.equals(expected), "items lost or duplicated: " + now + " instead of " + expected);
			helper.assertTrue(recruit.record().carried.isEmpty(), "waiting for the sorter to put everything away");
			for (int column = 0; column < 3; column++) {
				Item kind = kinds[column];
				for (BlockPos pos : columns.get(column)) {
					for (ItemStack stack : contents(level, pos)) {
						helper.assertTrue(stack.isEmpty() || stack.is(kind), "waiting for column " + column + " to hold only " + kind);
					}
				}
				// One kind per column of chests: packed by columns inside the chests (the default layout).
				List<ItemStack> sequence = packed(helper, level, columns.get(column), true);
				assertMerged(helper, sequence);
			}
			GuildManager.disband(guild);
		});
	}

	// ------------------------------------------------------------------ helpers

	/** Items per kind (id, enchanted, damaged) in the chests. */
	private static Map<String, Integer> totals(ServerLevel level, List<BlockPos> chests) {
		Map<String, Integer> totals = new TreeMap<>();
		for (BlockPos pos : chests) {
			for (ItemStack stack : contents(level, pos)) {
				if (!stack.isEmpty()) {
					String key = BuiltInRegistries.ITEM.getKey(stack.getItem()) + (stack.isEnchanted() ? "+enchanted" : "")
							+ (stack.isDamaged() ? "+damaged" : "");
					totals.merge(key, stack.getCount(), Integer::sum);
				}
			}
		}
		return totals;
	}

	/** Non-empty stacks in reading order; fails while an empty slot comes before a stack (not packed). */
	private static List<ItemStack> readingOrder(GameTestHelper helper, ServerLevel level, List<BlockPos> chests) {
		return packed(helper, level, chests, false);
	}

	/**
	 * Non-empty stacks of the chests in slot order ({@code columns}: each column of 9-slot rows top to bottom, then the
	 * next column); fails while an empty slot comes before a stack (not packed).
	 */
	static List<ItemStack> packed(GameTestHelper helper, ServerLevel level, List<BlockPos> chests, boolean columns) {
		List<ItemStack> sequence = new ArrayList<>();
		boolean gap = false;
		for (BlockPos pos : chests) {
			List<ItemStack> slots = contents(level, pos);
			for (int i = 0; i < slots.size(); i++) {
				int rows = slots.size() / 9;
				ItemStack stack = columns && rows > 0 ? slots.get((i % rows) * 9 + i / rows) : slots.get(i);
				if (stack.isEmpty()) {
					gap = true;
				} else {
					helper.assertTrue(!gap, columns ? "waiting for the chests to be packed by columns" : "waiting for the chests to be packed in reading order");
					sequence.add(stack);
				}
			}
		}
		return sequence;
	}

	/** At most one partial stack of each kind. */
	static void assertMerged(GameTestHelper helper, List<ItemStack> sequence) {
		for (int i = 0; i < sequence.size(); i++) {
			ItemStack a = sequence.get(i);
			if (a.getCount() >= a.getMaxStackSize()) {
				continue;
			}
			for (int j = i + 1; j < sequence.size(); j++) {
				ItemStack b = sequence.get(j);
				helper.assertTrue(b.getCount() >= b.getMaxStackSize() || !ItemStack.isSameItemSameComponents(a, b),
						"waiting for the stacks to be merged (" + a + " / " + b + ")");
			}
		}
	}

	private static List<ItemStack> contents(ServerLevel level, BlockPos pos) {
		Container container = (Container) level.getBlockEntity(pos);
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			stacks.add(container.getItem(i).copy());
		}
		return stacks;
	}

	private static boolean sameContents(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.matches(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}

	private static ItemStack find(ServerLevel level, List<BlockPos> chests, Predicate<ItemStack> filter) {
		for (BlockPos pos : chests) {
			for (ItemStack stack : contents(level, pos)) {
				if (!stack.isEmpty() && filter.test(stack)) {
					return stack;
				}
			}
		}
		return null;
	}

	private static int count(ServerLevel level, BlockPos pos, Item item) {
		int total = 0;
		for (ItemStack stack : contents(level, pos)) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int stacks(ServerLevel level, BlockPos pos) {
		int stacks = 0;
		for (ItemStack stack : contents(level, pos)) {
			if (!stack.isEmpty()) {
				stacks++;
			}
		}
		return stacks;
	}

	private static int used(ServerLevel level, BlockPos pos) {
		return stacks(level, pos);
	}

	private static String name(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof BaseContainerBlockEntity container && container.getCustomName() != null
				? container.getCustomName().getString() : null;
	}
}
