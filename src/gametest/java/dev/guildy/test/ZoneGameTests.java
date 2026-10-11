package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.PatrolRoute;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerConfig;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import dev.guildy.villager.WorkZone;
import dev.guildy.villager.ZoneGui;
import dev.guildy.villager.ZoneObjective;
import dev.guildy.villager.ZoneType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Work zones and patrol routes: a lumberjack working a forest zone planted outside the territory, a guard walking a
 * route drawn by right-clicks, fanion placement rules / removal, and the zone menus.
 */
public class ZoneGameTests {

	/** Forest zone outside the territory with a small natural tree: the lumberjack cuts it and brings the logs home. */
	@GameTest(maxTicks = 3600)
	public void lumberjackCutsTreeInForestZoneOutsideTerritory(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		Recruit recruit = recruit(helper, "Foret");
		// The founding square is 3x3 chunks and the heart sits at local x = 0 of its chunk: +36 is two chunks away.
		BlockPos fanionGround = recruit.ground.offset(36, 0, 0);
		List<BlockPos> logs = buildTree(level, recruit.ground.offset(40, 1, 3), 4);
		TestSupport.useOnTop(recruit.leader, level, fanionGround, fanion(recruit.guild, "FOREST"));
		helper.assertTrue(recruit.guild.villagers.zones.size() == 1, "forest zone created by planting its fanion");
		WorkZone zone = recruit.guild.villagers.zones.getFirst();
		helper.assertTrue(zone.type == ZoneType.FOREST && zone.pos.equals(fanionGround.above()), "zone at the fanion");
		helper.assertTrue(GuildManager.at(level, zone.pos) == null, "the zone lies outside the territory");
		helper.assertTrue(level.getBlockState(zone.pos).getBlock() instanceof AbstractBannerBlock, "fanion banner planted");
		recruit.record.zoneId = zone.id;
		setTask(recruit, Task.LUMBERJACK);
		helper.succeedWhen(() -> {
			for (BlockPos log : logs) {
				helper.assertTrue(!level.getBlockState(log).is(Blocks.OAK_LOG), "waiting for the lumberjack to cut the tree");
			}
			int deposited = count(level, recruit.deposit, Items.OAK_LOG);
			helper.assertTrue(deposited >= logs.size(), "waiting for the logs in the deposit chest (" + deposited + ")");
			helper.assertTrue(level.getBlockState(logs.getFirst()).is(Blocks.OAK_SAPLING), "a sapling is replanted");
			GuildManager.disband(recruit.guild);
		});
	}

	/** A route drawn with right-clicks (then [Terminer]); the guard assigned to it walks both waypoints. */
	@GameTest(maxTicks = 2400)
	public void guardWalksItsPatrolRoute(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		VillagerModule.config().routes.pauseSeconds = 1;
		Recruit recruit = recruit(helper, "Ronde");
		BlockPos a = recruit.ground.offset(-8, 0, 6);
		BlockPos b = recruit.ground.offset(8, 0, 6);
		command(level, recruit.leader, "guild villagers route new");
		TestSupport.useOnTop(recruit.leader, level, a, ItemStack.EMPTY);
		helper.runAfterDelay(10, () -> TestSupport.useOnTop(recruit.leader, level, b, ItemStack.EMPTY));
		helper.runAfterDelay(20, () -> command(level, recruit.leader, "guild villagers route finish"));
		boolean[] assigned = {false};
		boolean[] seen = {false, false};
		helper.succeedWhen(() -> {
			helper.assertTrue(recruit.guild.villagers.routes.size() == 1, "waiting for the route to be saved");
			PatrolRoute route = recruit.guild.villagers.routes.getFirst();
			helper.assertTrue(route.points.equals(List.of(a.above(), b.above())), "waypoints = clicked blocks (+1): " + route.points);
			if (!assigned[0]) {
				assigned[0] = true;
				recruit.record.routeId = route.id;
				try {
					setTask(recruit, Task.GUARD);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			}
			Entity villager = level.getEntity(recruit.record.uuid);
			if (villager != null) {
				seen[0] |= villager.distanceToSqr(Vec3.atBottomCenterOf(a.above())) < 2.5 * 2.5;
				seen[1] |= villager.distanceToSqr(Vec3.atBottomCenterOf(b.above())) < 2.5 * 2.5;
			}
			helper.assertTrue(seen[0] && seen[1], "waiting for the guard to walk both waypoints");
			GuildManager.disband(recruit.guild);
		});
	}

	/** Fanion rules (distance, other guild), removal by a stranger refused, by the leader deletes the zone; menus open. */
	@GameTest(maxTicks = 200)
	public void fanionRulesRemovalAndMenus(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 4);
		TestSupport.forceChunks(level, ground, 3);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Fanion");

		// Too far from the heart (default 192 blocks): refused.
		BlockPos far = TestSupport.pad(level, ground.getX() + 250, ground.getZ());
		TestSupport.useOnTop(leader, level, far, fanion(guild, "FARM"));
		helper.assertTrue(guild.villagers.zones.isEmpty(), "fanion refused too far from the heart");

		BlockPos outside = ground.offset(36, 0, 0);
		TestSupport.useOnTop(leader, level, outside, fanion(guild, "MINE"));
		helper.assertTrue(guild.villagers.zones.size() == 1, "mine zone created");
		WorkZone zone = guild.villagers.zones.getFirst();
		BlockPos banner = outside.above();
		helper.assertTrue(zone.pos.equals(banner) && zone.type == ZoneType.MINE && zone.objective == ZoneObjective.MIXED && zone.fanion,
				"mine zone settings");
		helper.assertTrue(level.getBlockState(banner).getBlock() instanceof AbstractBannerBlock, "fanion banner planted");

		// Menus.
		List<Consumer<ServerPlayer>> menus = new ArrayList<>();
		menus.add(p -> ZoneGui.openZones(p, guild));
		menus.add(p -> ZoneGui.openZone(p, guild, zone.id));
		for (Consumer<ServerPlayer> menu : menus) {
			leader.closeContainer();
			menu.accept(leader);
			helper.assertTrue(leader.containerMenu != leader.inventoryMenu, "zone menu should be open");
		}
		leader.closeContainer();

		// Someone else cannot remove the fanion.
		ServerPlayer stranger = TestSupport.survivor(helper, outside.offset(1, 1, 0));
		BlockState state = level.getBlockState(banner);
		boolean broken = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, stranger, banner, state, level.getBlockEntity(banner));
		helper.assertTrue(!broken && guild.villagers.zones.size() == 1, "a stranger cannot remove the fanion");
		// The leader removes it: the zone is deleted and the banner disappears without drop.
		broken = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, leader, banner, state, level.getBlockEntity(banner));
		helper.assertTrue(!broken, "vanilla break replaced (no drop)");
		helper.assertTrue(guild.villagers.zones.isEmpty(), "zone deleted with its fanion");
		helper.assertTrue(level.getBlockState(banner).isAir(), "fanion removed");

		// Every default loot item / icon exists in this version.
		VillagerConfig config = VillagerModule.config();
		List<VillagerConfig.DepthTable> tables = new ArrayList<>(config.miner.depthTables);
		tables.addAll(config.miner.oreTables);
		tables.addAll(config.miner.stoneTables);
		for (VillagerConfig.DepthTable table : tables) {
			for (VillagerConfig.LootEntry entry : table.entries) {
				helper.assertTrue(ItemUtil.item(entry.item) != null, "unknown loot item " + entry.item);
			}
		}
		for (ZoneObjective objective : ZoneObjective.values()) {
			helper.assertTrue(ItemUtil.item(objective.icon()) != null, "unknown icon " + objective.icon());
		}
		GuildManager.disband(guild);
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	record Recruit(Guild guild, VillagerRecord record, ServerPlayer leader, BlockPos ground, BlockPos deposit) {
	}

	/** Founds a guild, recruits one villager and registers a deposit chest. */
	static Recruit recruit(GameTestHelper helper, String prefix) {
		ServerLevel level = helper.getLevel();
		TestSupport.console(level, "time set 1000");
		BlockPos ground = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, ground, 3);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, prefix);
		TestSupport.console(level, "guild admin villager spawn " + guild.name);
		command(level, leader, "guild villagers accept");
		helper.assertTrue(guild.villagers.members.size() == 1, "candidate accepted as guild villager");
		BlockPos deposit = ground.offset(4, 1, 0);
		level.setBlockAndUpdate(deposit, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(new StorageChest(deposit, StorageChest.Role.DEPOSIT, ""));
		return new Recruit(guild, guild.villagers.members.values().iterator().next(), leader, ground, deposit);
	}

	/** A zone fanion item as the villager menu gives it (Guildy custom data). */
	static ItemStack fanion(Guild guild, String type) {
		ItemStack stack = new ItemStack(Items.BANNER.pick(DyeColor.GREEN));
		CompoundTag extra = new CompoundTag();
		extra.putString("type", type);
		extra.putString("guild", guild.id.toString());
		return GuildyItems.tag(stack, GuildyItems.KIND_ZONE_FLAG, extra);
	}

	/** Oak trunk of {@code height} logs with natural (non-persistent) leaves around its top; returns the logs bottom-up. */
	private static List<BlockPos> buildTree(ServerLevel level, BlockPos base, int height) {
		// The test world is not grass everywhere: trees only grow (and are only cut) on soil.
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

	static void setTask(Recruit recruit, Task task) throws ReflectiveOperationException {
		Method setTask = Class.forName("dev.guildy.villager.VillagerManager")
				.getDeclaredMethod("setTask", Guild.class, VillagerRecord.class, Task.class, ServerPlayer.class);
		setTask.setAccessible(true);
		setTask.invoke(null, recruit.guild, recruit.record, task, recruit.leader);
	}

	static void command(ServerLevel level, ServerPlayer player, String command) {
		level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static int count(ServerLevel level, BlockPos chest, net.minecraft.world.item.Item item) {
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
