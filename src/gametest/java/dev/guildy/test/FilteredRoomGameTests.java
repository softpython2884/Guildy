package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.ChestRoom;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtered chest rooms (environment {@code guildy-test:day}): every automatic chest of the room receives the kinds of
 * items it already holds, an empty one nothing; a kind put in by hand is accepted from then on; the tidying only merges
 * and lays out each chest, it never takes a kind out of one.
 */
public class FilteredRoomGameTests {
	private static final String FALLBACK = "Divers";
	private static final String STONE = "Pierre";

	/** A room of {@code count} chests in a row (filled by {@code fill} before it is created), turned into a filtered room. */
	private static ChestRoom filteredRoom(GameTestHelper helper, ZoneGameTests.Recruit recruit, List<BlockPos> chests) throws Exception {
		ServerLevel level = helper.getLevel();
		Guild guild = recruit.guild();
		ServerPlayer leader = recruit.leader();
		Method start = Class.forName("dev.guildy.villager.VillagerInteractions").getDeclaredMethod("startRoomSelection", ServerPlayer.class, Guild.class);
		start.setAccessible(true);
		start.invoke(null, leader, guild);
		TestSupport.useOnTop(leader, level, chests.getFirst(), ItemStack.EMPTY);
		TestSupport.useOnTop(leader, level, chests.getLast(), ItemStack.EMPTY);
		leader.closeContainer();
		helper.assertTrue(guild.villagers.rooms.size() == 1, "chest room created");
		ChestRoom room = guild.villagers.rooms.getFirst();
		Method setFiltered = Class.forName("dev.guildy.villager.ChestRooms").getDeclaredMethod("setFiltered", ServerLevel.class, Guild.class,
				ChestRoom.class, boolean.class);
		setFiltered.setAccessible(true);
		int converted = (int) setFiltered.invoke(null, level, guild, room, true);
		helper.assertTrue(room.filtered() && converted == chests.size(), "room turned into a filtered room (" + converted + " chests)");
		for (BlockPos pos : chests) {
			StorageChest chest = guild.villagers.chestAt(pos);
			helper.assertTrue(chest != null && chest.auto && chest.room == room.id && chest.role == StorageChest.Role.CONTENT,
					"filtered-room chest at " + pos.toShortString());
		}
		return room;
	}

	/** Chests in a row, facing south. */
	private static List<BlockPos> row(ServerLevel level, BlockPos ground, int count) {
		List<BlockPos> chests = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			BlockPos pos = ground.offset(-6 + i, 1, 6);
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
			chests.add(pos);
		}
		return chests;
	}

	private static BlockPos fallbackChest(ServerLevel level, Guild guild, BlockPos ground) {
		BlockPos misc = ground.offset(5, 1, -3);
		level.setBlockAndUpdate(misc, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(new StorageChest(misc, StorageChest.Role.CATEGORY, FALLBACK));
		return misc;
	}

	/**
	 * A filtered room of 3 chests: diorite + granite, wheat, and an empty one. The sorter puts the deposited diorite and
	 * granite into the first chest, the wheat into the second, never anything into the empty one, and the logs (no chest
	 * of the room holds any) into "Divers".
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 3000)
	public void sorterFillsEachChestWithWhatItHolds(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Filtree");
		Guild guild = recruit.guild();
		List<BlockPos> chests = row(level, recruit.ground(), 3);
		Container stones = container(level, chests.get(0));
		Container wheat = container(level, chests.get(1));
		Container empty = container(level, chests.get(2));
		stones.setItem(3, new ItemStack(Items.DIORITE, 20));
		stones.setItem(12, new ItemStack(Items.GRANITE, 10));
		wheat.setItem(0, new ItemStack(Items.WHEAT, 5));
		BlockPos misc = fallbackChest(level, guild, recruit.ground());
		filteredRoom(helper, recruit, chests);
		Container deposit = container(level, recruit.deposit());
		deposit.setItem(0, new ItemStack(Items.DIORITE, 64));
		deposit.setItem(1, new ItemStack(Items.WHEAT, 32));
		deposit.setItem(2, new ItemStack(Items.OAK_LOG, 16));
		deposit.setItem(3, new ItemStack(Items.GRANITE, 8));
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.onEachTick(() -> {
			if (!empty.isEmpty()) {
				helper.fail("the empty chest of the filtered room received " + empty.getItem(0));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(count(deposit, null) == 0, "waiting for the deposit to be emptied");
			helper.assertTrue(count(stones, Items.DIORITE) == 84 && count(stones, Items.GRANITE) == 18,
					"waiting for diorite and granite in their chest (diorite " + count(stones, Items.DIORITE) + ", granite " + count(stones, Items.GRANITE) + ")");
			helper.assertTrue(count(wheat, Items.WHEAT) == 37, "waiting for the wheat in its chest (" + count(wheat, Items.WHEAT) + ")");
			helper.assertTrue(count(stones, null) == 102 && count(wheat, null) == 37, "nothing else in the filtered chests");
			Container fallback = container(level, misc);
			helper.assertTrue(count(fallback, Items.OAK_LOG) == 16 && count(fallback, null) == 16, "waiting for the logs in 'Divers'");
			helper.assertTrue(recruit.record().carried.isEmpty(), "nothing left in the sorter's bag");
			GuildManager.disband(guild);
		});
	}

	/**
	 * A filtered room of 2 chests (wheat, empty): cobblestone first goes to "Divers" (no chest of the room holds any).
	 * Once a player puts one cobblestone into the empty chest, the deposited cobblestone goes there, and the tidying
	 * brings the cobblestone of "Divers" too (a chest holding an item comes before the fallback).
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 4000)
	public void aKindPutInByHandIsAcceptedFromThenOn(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Ajout");
		Guild guild = recruit.guild();
		List<BlockPos> chests = row(level, recruit.ground(), 2);
		Container wheat = container(level, chests.get(0));
		Container empty = container(level, chests.get(1));
		wheat.setItem(0, new ItemStack(Items.WHEAT, 5));
		BlockPos misc = fallbackChest(level, guild, recruit.ground());
		filteredRoom(helper, recruit, chests);
		Container deposit = container(level, recruit.deposit());
		deposit.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		ZoneGameTests.setTask(recruit, Task.SORTER);
		Container fallback = container(level, misc);
		boolean[] added = {false};
		helper.succeedWhen(() -> {
			if (!added[0]) {
				helper.assertTrue(count(deposit, null) == 0 && count(fallback, Items.COBBLESTONE) == 16,
						"waiting for the cobblestone in 'Divers' (no chest of the room holds any)");
				helper.assertTrue(empty.isEmpty(), "the empty chest received nothing");
				// A player puts one cobblestone into the empty chest, then more cobblestone arrives in the deposit.
				empty.setItem(4, new ItemStack(Items.COBBLESTONE, 1));
				empty.setChanged();
				deposit.setItem(0, new ItemStack(Items.COBBLESTONE, 20));
				added[0] = true;
			}
			helper.assertTrue(count(deposit, null) == 0, "waiting for the second deposit to be emptied");
			helper.assertTrue(count(empty, Items.COBBLESTONE) == 37 && count(fallback, Items.COBBLESTONE) == 0,
					"waiting for all the cobblestone in the chest that now holds some (" + count(empty, Items.COBBLESTONE) + ", 'Divers' "
							+ count(fallback, Items.COBBLESTONE) + ")");
			helper.assertTrue(count(wheat, null) == 5 && count(wheat, Items.WHEAT) == 5, "the wheat chest received no cobblestone");
			GuildManager.disband(guild);
		});
	}

	/**
	 * A filtered room whose first chest holds scattered diorite, one stick and one bone, the second some granite; a
	 * "Pierre" chest holds diorite. The tidying brings the diorite of "Pierre" into the room and merges each chest by
	 * columns, but never takes the stick, the bone (nor any last item of a kind) out of a chest of the room.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 4000)
	public void tidyingNeverTakesAKindOutOfAFilteredChest(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Garde");
		Guild guild = recruit.guild();
		List<BlockPos> chests = row(level, recruit.ground(), 2);
		Container mixed = container(level, chests.get(0));
		Container granite = container(level, chests.get(1));
		mixed.setItem(4, new ItemStack(Items.DIORITE, 10));
		mixed.setItem(8, new ItemStack(Items.STICK, 1));
		mixed.setItem(13, new ItemStack(Items.DIORITE, 20));
		mixed.setItem(20, new ItemStack(Items.BONE, 1));
		mixed.setItem(22, new ItemStack(Items.DIORITE, 5));
		granite.setItem(5, new ItemStack(Items.GRANITE, 30));
		BlockPos misc = fallbackChest(level, guild, recruit.ground());
		BlockPos stonePos = recruit.ground().offset(5, 1, 3);
		level.setBlockAndUpdate(stonePos, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(new StorageChest(stonePos, StorageChest.Role.CATEGORY, STONE));
		Container stone = container(level, stonePos);
		stone.setItem(0, new ItemStack(Items.DIORITE, 12));
		stone.setItem(1, new ItemStack(Items.STONE, 7));
		filteredRoom(helper, recruit, chests);
		ZoneGameTests.setTask(recruit, Task.SORTER);
		helper.onEachTick(() -> {
			if (count(mixed, Items.STICK) != 1 || count(mixed, Items.BONE) != 1 || count(mixed, Items.DIORITE) < 35 || count(granite, Items.GRANITE) != 30) {
				helper.fail("a kind left a chest of the filtered room (stick " + count(mixed, Items.STICK) + ", bone " + count(mixed, Items.BONE)
						+ ", diorite " + count(mixed, Items.DIORITE) + ", granite " + count(granite, Items.GRANITE) + ")");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(count(stone, Items.DIORITE) == 0 && count(mixed, Items.DIORITE) == 47,
					"waiting for the diorite of 'Pierre' in the filtered chest (" + count(mixed, Items.DIORITE) + ")");
			helper.assertTrue(count(stone, Items.STONE) == 7, "the stone stays in 'Pierre'");
			helper.assertTrue(mixed.getItem(0).is(Items.DIORITE) && mixed.getItem(0).getCount() == 47 && used(mixed) == 3,
					"waiting for the filtered chest to be merged (slot 1: " + mixed.getItem(0) + ", " + used(mixed) + " slots used)");
			helper.assertTrue(granite.getItem(0).is(Items.GRANITE) && granite.getItem(0).getCount() == 30 && used(granite) == 1,
					"waiting for the granite at the top of the first column (" + granite.getItem(0) + ")");
			helper.assertTrue(count(container(level, misc), null) == 0, "nothing went to 'Divers'");
			helper.assertTrue(recruit.record().carried.isEmpty(), "nothing left in the sorter's bag");
			GuildManager.disband(guild);
		});
	}

	// ------------------------------------------------------------------ helpers

	private static Container container(ServerLevel level, BlockPos pos) {
		return (Container) level.getBlockEntity(pos);
	}

	/** Items of that kind in the container (null: every item). */
	private static int count(Container container, Item item) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && (item == null || stack.is(item))) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int used(Container container) {
		int used = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			used += container.getItem(i).isEmpty() ? 0 : 1;
		}
		return used;
	}
}
