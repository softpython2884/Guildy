package dev.guildy.shop;

import dev.guildy.core.GuildyItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Counting / removing "plain" items from a player's inventory for trades and deliveries: main inventory + off hand
 * only (never armor), never special Guildy items, never modified stacks (named, enchanted, damaged, filled...),
 * so a player cannot lose a precious item by mistake.
 */
public final class PlainItems {
	private PlainItems() {
	}

	/** True for an unmodified, non-special stack. */
	public static boolean isPlain(ItemStack stack) {
		return !stack.isEmpty() && !GuildyItems.isSpecial(stack) && stack.getComponentsPatch().isEmpty();
	}

	public static int count(ServerPlayer player, Predicate<ItemStack> filter) {
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int slot : slots()) {
			ItemStack stack = inventory.getItem(slot);
			if (isPlain(stack) && filter.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/**
	 * Removes exactly {@code amount} matching items. Checks first: nothing is removed when the player has fewer.
	 *
	 * @return true when removed
	 */
	public static boolean remove(ServerPlayer player, Predicate<ItemStack> filter, int amount) {
		if (amount <= 0) {
			return true;
		}
		if (count(player, filter) < amount) {
			return false;
		}
		Inventory inventory = player.getInventory();
		int remaining = amount;
		for (int slot : slots()) {
			if (remaining <= 0) {
				break;
			}
			ItemStack stack = inventory.getItem(slot);
			if (isPlain(stack) && filter.test(stack)) {
				int n = Math.min(remaining, stack.getCount());
				stack.shrink(n);
				if (stack.isEmpty()) {
					inventory.setItem(slot, ItemStack.EMPTY);
				}
				remaining -= n;
			}
		}
		inventory.setChanged();
		player.containerMenu.broadcastChanges();
		return true;
	}

	private static int[] slots() {
		int[] slots = new int[Inventory.INVENTORY_SIZE + 1];
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			slots[i] = i;
		}
		slots[Inventory.INVENTORY_SIZE] = Inventory.SLOT_OFFHAND;
		return slots;
	}
}
