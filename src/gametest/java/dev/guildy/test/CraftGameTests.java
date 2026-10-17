package dev.guildy.test;

import dev.guildy.core.Hooks;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.AlchemistConfig;
import dev.guildy.villager.AlchemyRules;
import dev.guildy.villager.BlacksmithConfig;
import dev.guildy.villager.CraftData;
import dev.guildy.villager.ItemClassifier;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;

/**
 * Blacksmith and alchemist: a blacksmith repairs a damaged chestplate on an armor stand at the nearby anvil (the item
 * never leaves the stand, an unbreakable item and a stand too far from any anvil are left alone, the anvil wears like
 * in vanilla); an alchemist turns books of a storage chest into vanilla enchanted books at a table with 15
 * bookshelves; both jobs are refused in the menu below their guild level.
 */
public class CraftGameTests {

	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void blacksmithRepairsArmorOnAStandAtTheNearestAnvil(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		BlacksmithConfig cfg = VillagerModule.config().blacksmith;
		int seconds = cfg.secondsPerRepair;
		double chance = cfg.anvilDamageChance;
		cfg.secondsPerRepair = 1;
		cfg.anvilDamageChance = 0.0;
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Forge");
		Guild guild = recruit.guild();
		guild.level = Math.max(guild.level, cfg.minGuildLevel);
		BlockPos ground = recruit.ground();

		BlockPos anvil = ground.offset(-6, 1, 6);
		level.setBlockAndUpdate(anvil, Blocks.ANVIL.defaultBlockState());
		ArmorStand stand = new ArmorStand(level, ground.getX() - 8 + 0.5, ground.getY() + 1, ground.getZ() + 6 + 0.5);
		ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
		int max = chestplate.getMaxDamage();
		chestplate.setDamageValue(max - 10);
		stand.setItemSlot(EquipmentSlot.CHEST, chestplate);
		// Unbreakable (like Guildy's unique weapons): never touched even if it has a damage value.
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.setDamageValue(100);
		sword.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		stand.setItemSlot(EquipmentSlot.MAINHAND, sword);
		level.addFreshEntity(stand);
		// More damaged, but 20 blocks from the only anvil: skipped.
		ArmorStand far = new ArmorStand(level, ground.getX() + 14 + 0.5, ground.getY() + 1, ground.getZ() + 6 + 0.5);
		ItemStack boots = new ItemStack(Items.IRON_BOOTS);
		boots.setDamageValue(boots.getMaxDamage() - 2);
		far.setItemSlot(EquipmentSlot.FEET, boots);
		level.addFreshEntity(far);
		int farDamage = boots.getMaxDamage() - 2;

		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BLACKSMITH);
		int start = max - 10;
		int step = (int) Math.ceil(max * cfg.repairPercent / 100.0);
		boolean[] wearing = {false};
		helper.succeedWhen(() -> {
			ItemStack worn = stand.getItemBySlot(EquipmentSlot.CHEST);
			if (!stand.isAlive() || !worn.is(Items.IRON_CHESTPLATE)) {
				helper.fail("the chestplate left the armor stand");
			}
			if (stand.getItemBySlot(EquipmentSlot.MAINHAND).getDamageValue() != 100) {
				helper.fail("an unbreakable item was repaired");
			}
			if (far.getItemBySlot(EquipmentSlot.FEET).getDamageValue() != farDamage) {
				helper.fail("a stand 20 blocks away from any anvil was repaired");
			}
			if (!wearing[0]) {
				helper.assertTrue(worn.getDamageValue() <= start - 2 * step,
						"waiting for two repair steps (damage " + worn.getDamageValue() + ", start " + start + ")");
				helper.assertTrue(level.getBlockState(anvil).is(Blocks.ANVIL), "no anvil wear with a chance of 0");
				// Now every anvil use wears the anvil one stage.
				cfg.anvilDamageChance = 1.0;
				wearing[0] = true;
			}
			helper.assertTrue(level.getBlockState(anvil).is(Blocks.CHIPPED_ANVIL), "waiting for the anvil to become chipped");
			CraftData data = record.craft;
			helper.assertTrue(data != null && data.durability >= 2L * step && data.repairSteps >= 3, "repair statistics counted");
			helper.assertTrue(worn.getDamageValue() < start - 2 * step, "durability still going up");
			cfg.secondsPerRepair = seconds;
			cfg.anvilDamageChance = chance;
			stand.discard();
			far.discard();
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 3600)
	public void alchemistEnchantsBooksOfTheStorage(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		AlchemistConfig cfg = VillagerModule.config().alchemist;
		int seconds = cfg.secondsPerLevel;
		cfg.secondsPerLevel = 1;
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Alchimie");
		Guild guild = recruit.guild();
		guild.level = Math.max(guild.level, cfg.minGuildLevel);
		BlockPos ground = recruit.ground();

		BlockPos table = ground.offset(-8, 1, -8);
		level.setBlockAndUpdate(table, Blocks.ENCHANTING_TABLE.defaultBlockState());
		int placed = 0;
		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			// 15 of the 16 lower ring spots: a gap to walk in.
			if (offset.getY() == 0 && placed < 15) {
				level.setBlockAndUpdate(table.offset(offset), Blocks.BOOKSHELF.defaultBlockState());
				placed++;
			}
		}
		int shelves = 0;
		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			if (EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
				shelves++;
			}
		}
		helper.assertTrue(shelves == 15, "15 valid bookshelves around the table, got " + shelves);
		helper.assertTrue(AlchemyRules.minLevel(shelves) == 30 && AlchemyRules.maxLevel(shelves) == 30, "15 bookshelves: level 30");

		// Plain books in a registered "Livres et enchantements" chest; the enchanted books go back to that category.
		BlockPos bookChest = ground.offset(4, 1, 4);
		level.setBlockAndUpdate(bookChest, Blocks.CHEST.defaultBlockState());
		String books = ItemClassifier.categoryOf(new ItemStack(Items.BOOK));
		helper.assertTrue(books.equals(ItemClassifier.categoryOf(new ItemStack(Items.ENCHANTED_BOOK))), "books and enchanted books share a category");
		guild.villagers.chests.add(new StorageChest(bookChest, StorageChest.Role.CATEGORY, books));
		Container chest = (Container) level.getBlockEntity(bookChest);
		chest.setItem(0, new ItemStack(Items.BOOK, 3));

		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.ALCHEMIST);
		helper.succeedWhen(() -> {
			int plain = count(chest, Items.BOOK) + carried(record, Items.BOOK);
			int enchanted = count(chest, Items.ENCHANTED_BOOK) + carried(record, Items.ENCHANTED_BOOK);
			CraftData data = record.craft;
			long made = data == null ? 0 : data.books;
			if (plain + made != 3 || enchanted != made) {
				helper.fail("books lost or duplicated: plain " + plain + ", enchanted " + enchanted + ", made " + made);
			}
			helper.assertTrue(count(chest, Items.ENCHANTED_BOOK) >= 1, "waiting for an enchanted book in the storage chest (made " + made + ")");
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack stack = chest.getItem(i);
				if (!stack.is(Items.ENCHANTED_BOOK)) {
					continue;
				}
				ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
				helper.assertTrue(!stored.isEmpty(), "the enchanted book has stored enchantments");
				for (Holder<Enchantment> holder : stored.keySet()) {
					helper.assertTrue(holder.is(EnchantmentTags.IN_ENCHANTING_TABLE), "only enchanting table enchantments: " + holder.getRegisteredName());
					helper.assertTrue(holder.getRegisteredName().startsWith("minecraft:"), "never a Guildy enchantment: " + holder.getRegisteredName());
				}
			}
			helper.assertTrue(data != null && !data.bestEnchant.isEmpty() && data.booksToday >= 1, "alchemist statistics counted");
			cfg.secondsPerLevel = seconds;
			GuildManager.disband(guild);
		});
	}

	@GameTest(maxTicks = 200)
	public void craftJobsNeedTheirGuildLevel(GameTestHelper helper) {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Niveau");
		Guild guild = recruit.guild();
		ServerPlayer leader = recruit.leader();
		VillagerRecord record = recruit.record();
		int smithLevel = VillagerModule.config().blacksmith.minGuildLevel;
		int alchemistLevel = VillagerModule.config().alchemist.minGuildLevel;
		helper.assertTrue(smithLevel > 1 && alchemistLevel > smithLevel, "default requirements: blacksmith then alchemist");
		guild.level = 1;

		openDetail(helper, leader, guild);
		int smith = slotOf(leader, Items.ANVIL);
		int alchemist = slotOf(leader, Items.ENCHANTING_TABLE);
		helper.assertTrue(smith >= 0 && alchemist >= 0, "both jobs listed in the villager menu");
		helper.assertTrue(lore(leader, smith).contains("Niveau de guilde " + smithLevel + " requis"), "requirement shown: " + lore(leader, smith));
		click(helper, leader, smith);
		helper.assertTrue(record.task == Task.IDLE, "blacksmith refused at guild level 1");

		guild.level = smithLevel;
		openDetail(helper, leader, guild);
		click(helper, leader, smith);
		helper.assertTrue(record.task == Task.BLACKSMITH, "blacksmith accepted at guild level " + smithLevel);
		click(helper, leader, slotOf(leader, Items.ENCHANTING_TABLE));
		helper.assertTrue(record.task == Task.BLACKSMITH, "alchemist refused at guild level " + smithLevel);

		guild.level = alchemistLevel;
		openDetail(helper, leader, guild);
		click(helper, leader, slotOf(leader, Items.ENCHANTING_TABLE));
		helper.assertTrue(record.task == Task.ALCHEMIST, "alchemist accepted at guild level " + alchemistLevel);
		leader.closeContainer();
		GuildManager.disband(guild);
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	/** Villager list → the (only) villager's menu, through the real buttons. */
	private static void openDetail(GameTestHelper helper, ServerPlayer player, Guild guild) {
		Hooks.openVillagers.accept(player, guild);
		click(helper, player, 0);
	}

	private static void click(GameTestHelper helper, ServerPlayer player, int slot) {
		helper.assertTrue(player.containerMenu instanceof SlotBasedWrapperMenu, "a menu is open");
		((SlotBasedWrapperMenu) player.containerMenu).getBackingGui().click(slot, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
	}

	private static int slotOf(ServerPlayer player, Item item) {
		for (int slot = 0; slot < 45; slot++) {
			if (player.containerMenu.getSlot(slot).getItem().is(item)) {
				return slot;
			}
		}
		return -1;
	}

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
