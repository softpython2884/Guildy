package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import dev.guildy.villager.WishlistMenu;
import dev.guildy.villager.Wishlist;
import dev.guildy.villager.WorkZone;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import eu.pb4.sgui.api.gui.SlotBasedGui;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gathering work: a forest zone lumberjack takes the wanted tree of the zone margin first and never goes further; an
 * automatic lumberjack stays around the territory instead of drifting tree after tree; a farmer harvests wanted crops
 * first; "Appeler ici" brings a villager whose chunk is not loaded; the "objectifs prioritaires" editor.
 */
public class WorkGameTests {

	/**
	 * Forest zone with "birch logs" as objective: the birch 8 blocks outside the zone (margin) is cut before the oak
	 * inside the zone; a spruce beyond the margin is never touched and the villager never goes that far.
	 */
	@GameTest(maxTicks = 4800)
	public void zoneLumberjackTakesWantedTreeFirstAndStaysNearItsZone(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Coupe");
		BlockPos ground = recruit.ground();
		TestSupport.useOnTop(recruit.leader(), level, ground.offset(36, 0, 0), ZoneGameTests.fanion(recruit.guild(), "FOREST"));
		helper.assertTrue(recruit.guild().villagers.zones.size() == 1, "forest zone created by planting its fanion");
		WorkZone zone = recruit.guild().villagers.zones.getFirst();
		int[] bounds = zone.bounds();
		int margin = VillagerModule.config().zones.wishlistMargin;
		int y = ground.getY() + 1;
		List<BlockPos> oak = tree(level, new BlockPos(ground.getX() + 44, y, bounds[1] + 27), 4, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		List<BlockPos> birch = tree(level, new BlockPos(ground.getX() + 44, y, bounds[3] + 8), 4, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
		List<BlockPos> spruce = tree(level, new BlockPos(ground.getX() + 44, y, bounds[3] + margin + 16), 4, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES);
		helper.assertTrue(zone.contains(oak.getFirst()) && !zone.contains(birch.getFirst()), "oak inside the zone, birch in its margin");
		zone.wishlist.add("minecraft:birch_log");
		recruit.record().zoneId = zone.id;
		ZoneGameTests.setTask(recruit, Task.LUMBERJACK);

		int maxZ = bounds[3] + margin + 8;
		boolean[] birchFirst = {false, false};
		double[] farthest = {Double.NEGATIVE_INFINITY};
		helper.succeedWhen(() -> {
			Entity villager = level.getEntity(recruit.record().uuid);
			if (villager != null) {
				farthest[0] = Math.max(farthest[0], villager.getZ());
			}
			if (farthest[0] > maxZ) {
				helper.fail("the lumberjack went beyond its zone and margin: z = " + farthest[0] + " (max " + maxZ + ")");
			}
			if (!birchFirst[0] && gone(level, birch, Blocks.BIRCH_LOG)) {
				birchFirst[0] = true;
				birchFirst[1] = standing(level, oak, Blocks.OAK_LOG);
			}
			helper.assertTrue(birchFirst[0], "waiting for the wanted birch (zone margin) to be cut");
			helper.assertTrue(birchFirst[1], "the wanted birch must be cut before the ordinary oak");
			helper.assertTrue(gone(level, oak, Blocks.OAK_LOG), "waiting for the oak of the zone to be cut");
			int birchLogs = count(level, recruit.deposit(), Items.BIRCH_LOG);
			int oakLogs = count(level, recruit.deposit(), Items.OAK_LOG);
			helper.assertTrue(birchLogs >= 4 && oakLogs >= 4, "waiting for the logs in the deposit chest (" + birchLogs + " birch, " + oakLogs + " oak)");
			helper.assertTrue(standing(level, spruce, Blocks.SPRUCE_LOG), "the spruce beyond the margin must stay");
			GuildManager.disband(recruit.guild());
		});
	}

	/**
	 * No zone: the lumberjack cuts the tree 9 blocks outside the territory, but not the next one 45 blocks further (54
	 * blocks from the territory, beyond the 48-block automatic radius), nor the one after: it never drifts away.
	 */
	@GameTest(maxTicks = 4800)
	public void autoLumberjackNeverDriftsAwayFromTheTerritory(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Auto");
		BlockPos ground = recruit.ground();
		int cx = ground.getX() >> 4;
		int cz = ground.getZ() >> 4;
		forceRect(level, cx + 3, cx + 9, cz - 1, cz + 1, true);
		// Founding square: 3x3 chunks around the heart chunk, so its east edge is 31 blocks east of the heart column.
		int edge = (cx << 4) + 31;
		int radius = VillagerModule.config().zones.autoSearchRadius;
		int y = ground.getY() + 1;
		int z = ground.getZ() + 3;
		List<BlockPos> near = tree(level, new BlockPos(edge + 9, y, z), 4, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		List<BlockPos> next = tree(level, new BlockPos(edge + 54, y, z), 4, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		List<BlockPos> far = tree(level, new BlockPos(edge + 99, y, z), 4, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		ZoneGameTests.setTask(recruit, Task.LUMBERJACK);

		long[] cutAt = {-1};
		double[] maxX = {Double.NEGATIVE_INFINITY};
		helper.succeedWhen(() -> {
			Entity villager = level.getEntity(recruit.record().uuid);
			if (villager != null) {
				maxX[0] = Math.max(maxX[0], villager.getX());
			}
			if (maxX[0] > edge + radius) {
				helper.fail("the automatic lumberjack went " + (maxX[0] - edge) + " blocks from the territory (max " + radius + ")");
			}
			if (!standing(level, next, Blocks.OAK_LOG) || !standing(level, far, Blocks.OAK_LOG)) {
				helper.fail("a tree beyond the automatic radius around the territory was cut");
			}
			helper.assertTrue(gone(level, near, Blocks.OAK_LOG), "waiting for the tree near the territory to be cut");
			helper.assertTrue(count(level, recruit.deposit(), Items.OAK_LOG) >= 4, "waiting for its logs in the deposit chest");
			if (cutAt[0] < 0) {
				cutAt[0] = level.getGameTime();
			}
			// Then 30 more seconds: several searches, the further trees stay.
			helper.assertTrue(level.getGameTime() - cutAt[0] >= 600, "watching the lumberjack for 30 seconds");
			forceRect(level, cx + 3, cx + 9, cz - 1, cz + 1, false);
			GuildManager.disband(recruit.guild());
		});
	}

	/** Field zone with "carrots" as objective: the carrots of the zone margin come before the wheat of the zone. */
	@GameTest(maxTicks = 4800)
	public void farmerHarvestsWantedCropsFirst(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Champ");
		BlockPos ground = recruit.ground();
		TestSupport.useOnTop(recruit.leader(), level, ground.offset(36, 0, 0), ZoneGameTests.fanion(recruit.guild(), "FARM"));
		helper.assertTrue(recruit.guild().villagers.zones.size() == 1, "field zone created by planting its fanion");
		WorkZone zone = recruit.guild().villagers.zones.getFirst();
		int[] bounds = zone.bounds();
		int margin = VillagerModule.config().zones.wishlistMargin;
		List<BlockPos> wheat = crops(level, new BlockPos(ground.getX() + 44, ground.getY(), bounds[1] + 27), (CropBlock) Blocks.WHEAT);
		List<BlockPos> carrots = crops(level, new BlockPos(ground.getX() + 44, ground.getY(), bounds[3] + 8), (CropBlock) Blocks.CARROTS);
		List<BlockPos> potatoes = crops(level, new BlockPos(ground.getX() + 44, ground.getY(), bounds[3] + margin + 14), (CropBlock) Blocks.POTATOES);
		zone.wishlist.add("minecraft:carrot");
		recruit.record().zoneId = zone.id;
		ZoneGameTests.setTask(recruit, Task.FARMER);

		boolean[] carrotsFirst = {false, false};
		helper.succeedWhen(() -> {
			if (!carrotsFirst[0] && !anyRipe(level, carrots)) {
				carrotsFirst[0] = true;
				carrotsFirst[1] = allRipe(level, wheat);
			}
			helper.assertTrue(carrotsFirst[0], "waiting for the wanted carrots (zone margin) to be harvested");
			helper.assertTrue(carrotsFirst[1], "the wanted carrots must come before the wheat of the zone");
			helper.assertTrue(!anyRipe(level, wheat), "waiting for the wheat of the zone");
			helper.assertTrue(allRipe(level, potatoes), "the potatoes beyond the margin must stay");
			helper.assertTrue(level.getBlockState(carrots.getFirst()).is(Blocks.CARROTS), "carrots replanted");
			GuildManager.disband(recruit.guild());
		});
	}

	/**
	 * "Appeler ici" for a villager 800 blocks away whose chunk is unloaded (entities saved to disk): it is loaded for a
	 * moment and the villager comes. A villager that is not at its last known position: the call fails, nothing stays
	 * loaded.
	 */
	@GameTest(maxTicks = 2400)
	public void callBringsAVillagerWhoseChunkIsNotLoaded(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Appel");
		VillagerRecord record = recruit.record();
		BlockPos far = TestSupport.pad(level, recruit.ground().getX() + 800, recruit.ground().getZ());
		ChunkPos farChunk = ChunkPos.containing(far);
		level.setChunkForced(farChunk.x(), farChunk.z(), true);
		helper.runAfterDelay(20, () -> {
			Entity villager = level.getEntity(record.uuid);
			helper.assertTrue(villager != null, "the recruited villager is loaded");
			villager.teleportTo(far.getX() + 0.5, far.getY() + 1, far.getZ() + 0.5);
		});
		helper.runAfterDelay(60, () -> {
			Entity villager = level.getEntity(record.uuid);
			helper.assertTrue(villager != null && villager.distanceToSqr(Vec3.atBottomCenterOf(far.above())) < 64, "villager moved far away");
			record.lastPos = villager.blockPosition();
			level.setChunkForced(farChunk.x(), farChunk.z(), false);
		});
		Class<?>[] summonTypes = {ServerPlayer.class, Guild.class, VillagerRecord.class};
		VillagerRecord ghost = new VillagerRecord(java.util.UUID.randomUUID(), "Fantome");
		int[] phase = {0};
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 60, "setting up");
			if (phase[0] == 0) {
				helper.assertTrue(level.getEntity(record.uuid) == null && !level.areEntitiesLoaded(farChunk.pack()),
						"waiting for the far chunk to unload (entities saved)");
				Object result = call("summon", summonTypes, recruit.leader(), recruit.guild(), record);
				if (!"PENDING".equals(String.valueOf(result))) {
					helper.fail("an unloaded villager is called by loading its chunk (got " + result + ")");
				}
				phase[0] = 1;
			}
			if (phase[0] == 1) {
				Entity villager = level.getEntity(record.uuid);
				helper.assertTrue(villager != null && villager.distanceTo(recruit.leader()) < 4, "waiting for the called villager next to the player");
				helper.assertTrue(((Number) call("pendingCalls", new Class<?>[0])).intValue() == 0, "the call is over (chunks released)");
				helper.assertTrue(record.lastPos != null && record.lastPos.closerThan(recruit.leader().blockPosition(), 4), "last position updated");
				// A villager missing from its last known position.
				ghost.lastPos = TestSupport.pad(level, recruit.ground().getX() + 800, recruit.ground().getZ() + 400).above();
				recruit.guild().villagers.members.put(ghost.uuid, ghost);
				Object result = call("summon", summonTypes, recruit.leader(), recruit.guild(), ghost);
				if (!"PENDING".equals(String.valueOf(result))) {
					helper.fail("a villager out of reach is looked for at its last position (got " + result + ")");
				}
				phase[0] = 2;
			}
			helper.assertTrue(((Number) call("pendingCalls", new Class<?>[0])).intValue() == 0, "waiting for the call of the missing villager to fail");
			recruit.guild().villagers.members.remove(ghost.uuid);
			GuildManager.disband(recruit.guild());
		});
	}

	/** Editor: cursor item on a free slot and shift-click in the inventory add the type (the items stay), clicks remove. */
	@GameTest(maxTicks = 200)
	public void wishlistEditorAddsAndRemovesEntries(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Liste");
		ServerPlayer leader = recruit.leader();
		Guild guild = recruit.guild();
		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.LUMBERJACK);

		WishlistMenu.openVillager(leader, guild, record.uuid);
		SlotBasedGui gui = backing(helper, leader);
		// A free slot clicked with logs on the cursor: the type is listed, the logs stay on the cursor.
		leader.containerMenu.setCarried(new ItemStack(Items.BIRCH_LOG, 5));
		gui.click(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
		helper.assertTrue(record.wishlist.equals(List.of("minecraft:birch_log")), "cursor item listed: " + record.wishlist);
		helper.assertTrue(leader.containerMenu.getCarried().is(Items.BIRCH_LOG) && leader.containerMenu.getCarried().getCount() == 5,
				"the cursor item is not taken");
		leader.containerMenu.setCarried(ItemStack.EMPTY);
		// Shift-click on an inventory item: listed, never moved.
		leader.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));
		int index = inventorySlot(helper, leader, 0);
		boolean vanilla = gui.onAnyClick(index, ClickType.MOUSE_LEFT_SHIFT, ContainerInput.QUICK_MOVE);
		helper.assertTrue(!vanilla, "the shift-click is not handed to vanilla (no quick move)");
		helper.assertTrue(record.wishlist.equals(List.of("minecraft:birch_log", "minecraft:apple")), "inventory item listed: " + record.wishlist);
		helper.assertTrue(leader.getInventory().getItem(0).is(Items.APPLE) && leader.getInventory().getItem(0).getCount() == 3,
				"the inventory item stays where it was");
		// The same type again: no duplicate.
		gui.onAnyClick(index, ClickType.MOUSE_LEFT_SHIFT, ContainerInput.QUICK_MOVE);
		helper.assertTrue(record.wishlist.size() == 2, "no duplicate");
		// A click on an entry removes it.
		gui.click(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
		helper.assertTrue(record.wishlist.equals(List.of("minecraft:apple")), "entry removed: " + record.wishlist);
		// At most Wishlist.MAX entries: the free slots fill up, then a shift-clicked item is refused.
		Item[] items = {Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_SAPLING, Items.STICK, Items.OAK_SAPLING, Items.JUNGLE_LOG,
				Items.ACACIA_LOG, Items.CHERRY_LOG};
		for (Item item : items) {
			leader.containerMenu.setCarried(new ItemStack(item));
			gui.click(record.wishlist.size(), ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
		}
		leader.containerMenu.setCarried(ItemStack.EMPTY);
		helper.assertTrue(record.wishlist.size() == Wishlist.MAX, "list full: " + record.wishlist);
		leader.getInventory().setItem(1, new ItemStack(Items.DARK_OAK_LOG));
		gui.onAnyClick(inventorySlot(helper, leader, 1), ClickType.MOUSE_LEFT_SHIFT, ContainerInput.QUICK_MOVE);
		helper.assertTrue(record.wishlist.size() == Wishlist.MAX && !record.wishlist.contains("minecraft:dark_oak_log"),
				"list capped at " + Wishlist.MAX);
		// "Vider".
		gui.click(15, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
		helper.assertTrue(record.wishlist.isEmpty(), "list emptied");
		leader.closeContainer();

		// Zone editor: its entries are shared by the villagers of the zone (merged after their own).
		TestSupport.useOnTop(leader, level, recruit.ground().offset(36, 0, 0), ZoneGameTests.fanion(guild, "FOREST"));
		WorkZone zone = guild.villagers.zones.getFirst();
		record.zoneId = zone.id;
		record.wishlist.add("minecraft:oak_log");
		WishlistMenu.openZone(leader, guild, zone.id, null);
		gui = backing(helper, leader);
		leader.containerMenu.setCarried(new ItemStack(Items.APPLE));
		gui.click(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
		leader.containerMenu.setCarried(ItemStack.EMPTY);
		helper.assertTrue(zone.wishlist.equals(List.of("minecraft:apple")), "zone entry listed: " + zone.wishlist);
		helper.assertTrue(Wishlist.items(record, zone).equals(Set.of(Items.OAK_LOG, Items.APPLE))
				&& Wishlist.items(record, zone).iterator().next() == Items.OAK_LOG, "villager entries first, then the zone's");
		leader.closeContainer();
		// The villager menu (with the "Objectifs prioritaires" button) and the zone menu still open.
		call("openDetail", new Class<?>[]{ServerPlayer.class, Guild.class, java.util.UUID.class}, leader, guild, record.uuid);
		helper.assertTrue(leader.containerMenu != leader.inventoryMenu, "villager menu open");
		leader.closeContainer();
		GuildManager.disband(guild);
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	/** Calls a package-private static method of the villager module (VillagerManager.summon, VillagerCalls.pending, VillagerGui.openDetail). */
	private static Object call(String method, Class<?>[] types, Object... args) {
		try {
			String owner = switch (method) {
				case "pendingCalls" -> "dev.guildy.villager.VillagerCalls";
				case "openDetail" -> "dev.guildy.villager.VillagerGui";
				default -> "dev.guildy.villager.VillagerManager";
			};
			var target = Class.forName(owner).getDeclaredMethod("pendingCalls".equals(method) ? "pending" : method, types);
			target.setAccessible(true);
			return target.invoke(null, args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static SlotBasedGui backing(GameTestHelper helper, ServerPlayer player) {
		if (!(player.containerMenu instanceof SlotBasedWrapperMenu menu)) {
			throw helper.assertionException("the wishlist editor should be open");
		}
		return menu.getBackingGui();
	}

	/** Menu index of a slot of the player's own inventory. */
	private static int inventorySlot(GameTestHelper helper, ServerPlayer player, int inventoryIndex) {
		for (Slot slot : player.containerMenu.slots) {
			if (slot.container == player.getInventory() && slot.getContainerSlot() == inventoryIndex) {
				return slot.index;
			}
		}
		throw helper.assertionException("inventory slot " + inventoryIndex + " not found in the menu");
	}

	private static void forceRect(ServerLevel level, int minCx, int maxCx, int minCz, int maxCz, boolean forced) {
		for (int x = minCx; x <= maxCx; x++) {
			for (int z = minCz; z <= maxCz; z++) {
				level.setChunkForced(x, z, forced);
			}
		}
	}

	/** Natural tree: trunk on grass, non-persistent leaves around its top. Returns the logs bottom-up. */
	private static List<BlockPos> tree(ServerLevel level, BlockPos base, int height, Block log, Block leavesBlock) {
		level.setBlockAndUpdate(base.below(), Blocks.GRASS_BLOCK.defaultBlockState());
		List<BlockPos> logs = new ArrayList<>();
		for (int i = 0; i < height; i++) {
			BlockPos pos = base.above(i);
			level.setBlockAndUpdate(pos, log.defaultBlockState());
			logs.add(pos);
		}
		BlockState leaves = leavesBlock.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1).setValue(LeavesBlock.PERSISTENT, false);
		BlockPos top = logs.getLast();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			level.setBlockAndUpdate(top.relative(direction), leaves);
		}
		level.setBlockAndUpdate(top.above(), leaves);
		return logs;
	}

	/** Three ripe crops on farmland in a row along x, starting at {@code ground} (farmland level). Returns the crops. */
	private static List<BlockPos> crops(ServerLevel level, BlockPos ground, CropBlock crop) {
		List<BlockPos> plants = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			BlockPos soil = ground.offset(i, 0, 0);
			level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState());
			level.setBlockAndUpdate(soil.above(), crop.getStateForAge(crop.getMaxAge()));
			plants.add(soil.above());
		}
		return plants;
	}

	private static boolean anyRipe(ServerLevel level, List<BlockPos> plants) {
		for (BlockPos pos : plants) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
				return true;
			}
		}
		return false;
	}

	private static boolean allRipe(ServerLevel level, List<BlockPos> plants) {
		for (BlockPos pos : plants) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
				return false;
			}
		}
		return true;
	}

	private static boolean gone(ServerLevel level, List<BlockPos> logs, Block log) {
		for (BlockPos pos : logs) {
			if (level.getBlockState(pos).is(log)) {
				return false;
			}
		}
		return true;
	}

	private static boolean standing(ServerLevel level, List<BlockPos> logs, Block log) {
		for (BlockPos pos : logs) {
			if (!level.getBlockState(pos).is(log)) {
				return false;
			}
		}
		return true;
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
