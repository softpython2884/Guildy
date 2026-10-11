package dev.guildy.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Gold = the currency. 1 gold = 1 gold ingot; a gold block counts as 9. All operations are check-then-act on the
 * server thread, so they are atomic from the player's point of view (no partial payments).
 */
public final class GoldUtil {
	private GoldUtil() {
	}

	/** Gold carried by the player (ingots + 9 x blocks). Nuggets are not counted. */
	public static int count(ServerPlayer player) {
		int total = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || GuildyItems.isSpecial(stack)) {
				continue;
			}
			if (stack.is(Items.GOLD_INGOT)) {
				total += stack.getCount();
			} else if (stack.is(Items.GOLD_BLOCK)) {
				total += stack.getCount() * 9;
			}
		}
		return total;
	}

	/**
	 * Takes {@code amount} gold from the player (ingots first, then blocks with change given back as ingots).
	 *
	 * @return false (and nothing removed) when the player cannot pay
	 */
	public static boolean take(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return true;
		}
		int ingots = ItemUtil.count(player, Items.GOLD_INGOT);
		int blocks = ItemUtil.count(player, Items.GOLD_BLOCK);
		if (ingots + blocks * 9 < amount) {
			return false;
		}
		int fromIngots = Math.min(ingots, amount);
		int rest = amount - fromIngots;
		int blocksNeeded = (rest + 8) / 9;
		int change = blocksNeeded * 9 - rest;
		if (!ItemUtil.remove(player, Items.GOLD_INGOT, fromIngots)) {
			return false;
		}
		if (blocksNeeded > 0 && !ItemUtil.remove(player, Items.GOLD_BLOCK, blocksNeeded)) {
			// Should never happen (checked above); refund the ingots to stay consistent.
			ItemUtil.giveOrDrop(player, Items.GOLD_INGOT, fromIngots);
			return false;
		}
		if (change > 0) {
			ItemUtil.giveOrDrop(player, Items.GOLD_INGOT, change);
		}
		return true;
	}

	/** Gives gold as ingots (overflow dropped at the player's feet). */
	public static void give(ServerPlayer player, long amount) {
		if (amount <= 0) {
			return;
		}
		ItemUtil.giveOrDrop(player, Items.GOLD_INGOT, (int) Math.min(Integer.MAX_VALUE, amount));
	}

	/**
	 * Removes all gold ingots/blocks (and nuggets by groups of 9 when allowed) from the inventory.
	 *
	 * @return amount of gold removed
	 */
	public static int takeAll(ServerPlayer player, boolean nuggets) {
		int ingots = ItemUtil.count(player, Items.GOLD_INGOT);
		int blocks = ItemUtil.count(player, Items.GOLD_BLOCK);
		int nuggetGroups = nuggets ? ItemUtil.count(player, Items.GOLD_NUGGET) / 9 : 0;
		ItemUtil.remove(player, Items.GOLD_INGOT, ingots);
		ItemUtil.remove(player, Items.GOLD_BLOCK, blocks);
		if (nuggetGroups > 0) {
			ItemUtil.remove(player, Items.GOLD_NUGGET, nuggetGroups * 9);
		}
		return ingots + blocks * 9 + nuggetGroups;
	}
}
