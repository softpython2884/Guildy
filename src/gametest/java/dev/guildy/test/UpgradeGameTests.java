package dev.guildy.test;

import dev.guildy.claim.ClaimService;
import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildRole;
import dev.guildy.upgrade.UpgradeEffects;
import dev.guildy.upgrade.UpgradeService;
import dev.guildy.upgrade.UpgradeTree;
import dev.guildy.upgrade.Upgrades;
import dev.guildy.villager.VillagerManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Upgrade tree scenario: treasury-paid unlock (extra chunks), refusals (level, role, missing materials), materials taken
 * from the clicking player's inventory, bonuses read by other modules (flag price, villager limit), admin lock, menu.
 */
public class UpgradeGameTests {

	@GameTest(maxTicks = 100)
	public void unlockAndApplyUpgrades(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 4);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Upg");
		UpgradeTree tree = Upgrades.tree();
		helper.assertTrue(tree.nodes().size() == 12, "default tree loaded, got " + tree.nodes().size() + " nodes");
		UpgradeTree.Node housing = tree.node("logements");
		helper.assertTrue(housing != null && housing.costItems().size() == 1, "bed cost item resolved in the registry");

		// Treasury-only node, unlocked by the (creative) leader.
		GuildManager.addTreasury(guild, 100);
		int baseChunks = guild.maxChunks();
		helper.assertTrue(UpgradeService.unlock(leader, guild, "frontieres_elargies") == null, "Frontières élargies unlocked");
		helper.assertTrue(guild.maxChunks() == baseChunks + 4, "max chunks +4, got " + guild.maxChunks() + " (base " + baseChunks + ")");
		helper.assertTrue(guild.treasury == 70, "30 gold paid, treasury " + guild.treasury);
		helper.assertTrue(UpgradeService.unlock(leader, guild, "frontieres_elargies") != null, "cannot unlock twice");
		helper.assertTrue(UpgradeService.unlock(leader, guild, "logements") != null && guild.treasury == 70, "level 2 node refused at level 1");

		// Materials come from the inventory of the player who unlocks (survival officer).
		ServerPlayer officer = TestSupport.survivor(helper, ground.east(2));
		GuildManager.addMember(guild, officer.getUUID(), officer.getName().getString());
		helper.assertTrue(UpgradeService.unlock(officer, guild, "coeur_renforce") != null, "a member cannot spend the treasury");
		GuildManager.setRole(guild, officer.getUUID(), GuildRole.OFFICER);
		officer.getInventory().add(new ItemStack(Items.IRON_INGOT, 5));
		helper.assertTrue(UpgradeService.unlock(officer, guild, "coeur_renforce") != null, "5 iron ingots are not enough");
		helper.assertTrue(guild.treasury == 70 && ItemUtil.count(officer, Items.IRON_INGOT) == 5, "nothing taken on refusal");
		officer.getInventory().add(new ItemStack(Items.IRON_INGOT, 5));
		helper.assertTrue(UpgradeService.unlock(officer, guild, "coeur_renforce") == null, "Cœur renforcé unlocked");
		helper.assertTrue(ItemUtil.count(officer, Items.IRON_INGOT) == 2, "8 iron ingots taken, " + ItemUtil.count(officer, Items.IRON_INGOT) + " left");
		helper.assertTrue(guild.treasury == 40, "treasury " + guild.treasury);
		helper.assertTrue(Upgrades.value(guild, UpgradeEffects.HEART_INTEGRITY_PCT) == 25, "heart bonus active");

		// Bonuses read by other modules.
		int flagPrice = ClaimService.extensionFlagPrice(guild);
		int villagers = VillagerManager.maxVillagers(guild);
		UpgradeService.adminUnlock(guild, "cartographes");
		helper.assertTrue(guild.upgrades.unlocked.containsAll(java.util.List.of("primes_quetes", "butin_guerre", "cartographes")),
				"admin unlock adds the missing requirements");
		int discounted = ClaimService.extensionFlagPrice(guild);
		helper.assertTrue(discounted == (int) Math.ceil(flagPrice * 0.7 - 1e-9), "flag price -30 %: " + flagPrice + " -> " + discounted);
		UpgradeService.adminUnlock(guild, "logements");
		helper.assertTrue(VillagerManager.maxVillagers(guild) == villagers + 2, "villager limit +2");

		// Menu: one row per branch, panes turn green after an unlocked node, unlocked nodes glow.
		leader.closeContainer();
		Hooks.openUpgrades.accept(leader, guild);
		helper.assertTrue(leader.containerMenu != leader.inventoryMenu, "upgrade menu should be open");
		Item lime = Items.STAINED_GLASS_PANE.pick(DyeColor.LIME);
		Item white = Items.STAINED_GLASS_PANE.pick(DyeColor.WHITE);
		assertSlots(helper, leader, 9, Items.SHIELD, lime, Items.HEART_OF_THE_SEA, lime, Items.IRON_SWORD, white, Items.BANNER.pick(DyeColor.RED),
				white, Items.TOTEM_OF_UNDYING);
		assertSlots(helper, leader, 18, Items.GOLD_INGOT, lime, Items.WRITABLE_BOOK, lime, Items.CHEST, lime, Items.CARTOGRAPHY_TABLE,
				lime, Items.GOLD_BLOCK);
		assertSlots(helper, leader, 27, Items.MAP, lime, Items.OAK_FENCE, lime, Items.BED.pick(DyeColor.RED), lime, Items.IRON_PICKAXE,
				white, Items.BEACON);
		helper.assertTrue(glows(leader, 11) && !glows(leader, 13), "unlocked nodes glow, locked ones do not");
		helper.assertTrue(leader.containerMenu.getSlot(4).getItem().is(Items.ENCHANTING_TABLE), "info card");
		leader.closeContainer();

		// Admin lock through the command (removes the dependent node too).
		TestSupport.console(level, "guild admin upgrade lock " + guild.name + " frontieres_elargies");
		helper.assertFalse(guild.upgrades.unlocked.contains("frontieres_elargies") || guild.upgrades.unlocked.contains("logements"),
				"lock removes the node and its dependents");
		helper.assertTrue(guild.maxChunks() == baseChunks, "max chunks back to " + baseChunks);

		GuildManager.disband(guild);
		helper.succeed();
	}

	/** Items of 9 consecutive menu slots starting at {@code first}. */
	private static void assertSlots(GameTestHelper helper, ServerPlayer player, int first, Item... items) {
		for (int i = 0; i < items.length; i++) {
			ItemStack stack = player.containerMenu.getSlot(first + i).getItem();
			helper.assertTrue(stack.is(items[i]), "slot " + (first + i) + ": expected " + items[i] + ", got " + stack);
		}
	}

	private static boolean glows(ServerPlayer player, int slot) {
		return Boolean.TRUE.equals(player.containerMenu.getSlot(slot).getItem().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
	}
}
