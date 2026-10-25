package dev.guildy.builds;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A builder's bag ({@code VillagerRecord.carried}): materials are plain stacks only (no renamed or enchanted block is
 * ever built with); taking a cost is all or nothing (nothing is lost, nothing duplicated).
 */
public final class Bags {
	private Bags() {
	}

	public static boolean plain(ItemStack stack) {
		return !stack.isEmpty() && stack.getComponentsPatch().isEmpty();
	}

	/** Plain items of the bag by item. */
	public static Map<Item, Integer> contents(List<ItemStack> bag) {
		Map<Item, Integer> map = new HashMap<>();
		for (ItemStack stack : bag) {
			if (plain(stack)) {
				map.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return map;
	}

	public static boolean covers(Map<Item, Integer> contents, Map<Item, Integer> cost) {
		for (Map.Entry<Item, Integer> entry : cost.entrySet()) {
			if (contents.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
				return false;
			}
		}
		return true;
	}

	/** Removes a cost from the bag, all or nothing. */
	public static boolean take(List<ItemStack> bag, Map<Item, Integer> cost) {
		if (cost.isEmpty()) {
			return true;
		}
		if (!covers(contents(bag), cost)) {
			return false;
		}
		for (Map.Entry<Item, Integer> entry : cost.entrySet()) {
			int left = entry.getValue();
			Iterator<ItemStack> it = bag.iterator();
			while (it.hasNext() && left > 0) {
				ItemStack stack = it.next();
				if (!plain(stack) || !stack.is(entry.getKey())) {
					continue;
				}
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
				if (stack.isEmpty()) {
					it.remove();
				}
			}
		}
		return true;
	}

	/**
	 * Adds a stack to the bag (merging, then new stacks up to {@code capacity} stacks).
	 *
	 * @return what did not fit (empty when everything went in)
	 */
	public static ItemStack add(List<ItemStack> bag, ItemStack input, int capacity) {
		ItemStack stack = input.copy();
		for (ItemStack carried : bag) {
			if (stack.isEmpty()) {
				break;
			}
			if (ItemStack.isSameItemSameComponents(carried, stack) && carried.getCount() < carried.getMaxStackSize()) {
				int move = Math.min(stack.getCount(), carried.getMaxStackSize() - carried.getCount());
				carried.grow(move);
				stack.shrink(move);
			}
		}
		while (!stack.isEmpty() && bag.size() < capacity) {
			bag.add(stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
		}
		return stack;
	}

	/** Free stack places of the bag. */
	public static int freeStacks(List<ItemStack> bag, int capacity) {
		return Math.max(0, capacity - bag.size());
	}

	/** Room for plain items of that kind (merging, then new stacks). */
	public static int room(List<ItemStack> bag, Item item, int capacity) {
		int max = new ItemStack(item).getMaxStackSize();
		int room = 0;
		for (ItemStack stack : bag) {
			if (plain(stack) && stack.is(item)) {
				room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
			}
		}
		return room + freeStacks(bag, capacity) * max;
	}
}
