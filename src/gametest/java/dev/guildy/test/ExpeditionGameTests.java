package dev.guildy.test;

import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.ExpeditionDestination;
import dev.guildy.villager.Goal;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerConfig;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Expedition destinations: the desert is chosen through the real menus (villager list → villager → "Destination" →
 * desert), kept for the whole trip, and the explorer comes back with loot of the desert table only.
 */
public class ExpeditionGameTests {

	@GameTest(maxTicks = 2400)
	public void expeditionBringsLootOfTheChosenDestination(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		VillagerConfig.Expeditions settings = VillagerModule.config().expeditions;
		ExpeditionDestination desert = settings.destinations.stream().filter(d -> d.id.equals("desert")).findFirst().orElseThrow();
		desert.lossChance = 0;
		desert.treasureMapChance = 0;
		desert.uniqueChanceMultiplier = 0;
		Set<String> desertItems = desert.loot.stream().map(e -> e.item).collect(Collectors.toSet());

		TestSupport.console(level, "time set 1000");
		BlockPos ground = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Dest");
		TestSupport.console(level, "guild admin villager spawn " + guild.name);
		level.getServer().getCommands().performPrefixedCommand(leader.createCommandSourceStack(), "guild villagers accept");
		helper.assertTrue(guild.villagers.members.size() == 1, "candidate accepted as guild villager");
		VillagerRecord record = guild.villagers.members.values().iterator().next();
		BlockPos deposit = ground.offset(4, 1, 0);
		level.setBlockAndUpdate(deposit, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(new StorageChest(deposit, StorageChest.Role.DEPOSIT, ""));
		record.wishlist.add("minecraft:cactus");
		setTask(guild, record, Task.EXPLORER, leader);
		record.goal = Goal.FILL;

		// The menus: villager list → its menu → "Destination" (instead of the work zone) → the desert (cactus icon).
		Hooks.openVillagers.accept(leader, guild);
		click(helper, leader, 0);
		helper.assertTrue(lore(leader, 19).contains("Destination"), "destination button in the villager menu: " + lore(leader, 19));
		click(helper, leader, 19);
		int desertSlot = -1;
		for (int slot = 0; slot < 45; slot++) {
			if (leader.containerMenu.getSlot(slot).getItem().is(Items.CACTUS)) {
				desertSlot = slot;
			}
		}
		helper.assertTrue(desertSlot > 0, "the desert is listed after the default destination");
		helper.assertTrue(lore(leader, desertSlot).contains("Favorise"), "the wished cactus is favoured: " + lore(leader, desertSlot));
		boolean locked = false;
		for (int slot = 0; slot < 45; slot++) {
			locked |= leader.containerMenu.getSlot(slot).getItem().is(Items.DYE.pick(DyeColor.GRAY)) && lore(leader, slot).contains("Verrouill");
		}
		helper.assertTrue(locked, "level 1 guild: higher destinations greyed with the reason");
		click(helper, leader, desertSlot);
		helper.assertTrue("desert".equals(record.expeditionDestination), "desert chosen, got '" + record.expeditionDestination + "'");
		helper.assertTrue(leader.containerMenu.getSlot(19).getItem().is(Items.CACTUS), "back to the villager menu, desert shown");
		leader.closeContainer();

		boolean[] left = {false};
		helper.succeedWhen(() -> {
			if (record.isAway()) {
				left[0] = true;
				helper.assertTrue("desert".equals(record.awayDestination), "the trip goes to the desert, got " + record.awayDestination);
				// Back right now (the trip itself lasts minutes).
				record.returnAt = Math.min(record.returnAt, level.getGameTime());
			}
			helper.assertTrue(left[0], "waiting for the villager to leave for the desert");
			helper.assertTrue(!record.isAway() && record.task == Task.IDLE, "waiting for the return and the end of the mission");
			helper.assertTrue(record.awayDestination == null, "trip over");
			helper.assertTrue("desert".equals(record.expeditionDestination), "choice kept for the next trips");
			helper.assertTrue(record.stats.expeditions == 1, "expedition counted");
			Container chest = (Container) level.getBlockEntity(deposit);
			int items = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack stack = chest.getItem(i);
				if (!stack.isEmpty()) {
					String id = ItemUtil.id(stack.getItem());
					helper.assertTrue(desertItems.contains(id), "only desert loot, got " + id);
					items += stack.getCount();
				}
			}
			helper.assertTrue(items > 0 && record.carried.isEmpty(), "desert loot deposited in the deposit chest");
			GuildManager.disband(guild);
		});
	}

	/** A left click on a slot of the open menu, as the SGUI packet handler does. */
	private static void click(GameTestHelper helper, ServerPlayer player, int slot) {
		helper.assertTrue(player.containerMenu instanceof SlotBasedWrapperMenu, "a menu is open");
		((SlotBasedWrapperMenu) player.containerMenu).getBackingGui().click(slot, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
	}

	/** Name and lore of a slot of the open menu, as plain text. */
	private static String lore(ServerPlayer player, int slot) {
		ItemStack stack = player.containerMenu.getSlot(slot).getItem();
		StringBuilder text = new StringBuilder(stack.getHoverName().getString());
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				text.append(" | ").append(line.getString());
			}
		}
		return text.toString();
	}

	private static void setTask(Guild guild, VillagerRecord record, Task task, ServerPlayer actor) throws ReflectiveOperationException {
		Method setTask = Class.forName("dev.guildy.villager.VillagerManager")
				.getDeclaredMethod("setTask", Guild.class, VillagerRecord.class, Task.class, ServerPlayer.class);
		setTask.setAccessible(true);
		setTask.invoke(null, guild, record, task, actor);
	}
}
