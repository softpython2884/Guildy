package dev.guildy.villager;

import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;

/**
 * Supplies of the alchemist (plain books, lapis): in its bag, or in the guild's registered storage chests. A chest
 * gives an item when it is a deposit chest, a chest of the item's own category ("Livres et enchantements",
 * "Minerais et lingots"...) or a fallback ("Divers") chest. Items taken from a chest really leave it: what fits in the
 * bag is counted first, then exactly that is removed and added (no duplication, nothing lost).
 */
final class CraftStock {
	private CraftStock() {
	}

	/** Unmodified stack of the item (a renamed or enchanted book is never used up). */
	static boolean plain(ItemStack stack, Item item) {
		return !stack.isEmpty() && stack.is(item) && stack.getComponentsPatch().isEmpty();
	}

	static int countInBag(WorkContext ctx, Item item) {
		int count = 0;
		for (ItemStack stack : ctx.record.carried) {
			if (plain(stack, item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Removes {@code count} plain items from the bag (all or nothing). */
	static boolean takeFromBag(WorkContext ctx, Item item, int count) {
		if (count <= 0) {
			return true;
		}
		if (countInBag(ctx, item) < count) {
			return false;
		}
		int left = count;
		Iterator<ItemStack> it = ctx.record.carried.iterator();
		while (it.hasNext() && left > 0) {
			ItemStack stack = it.next();
			if (!plain(stack, item)) {
				continue;
			}
			int take = Math.min(left, stack.getCount());
			stack.shrink(take);
			left -= take;
			if (stack.isEmpty()) {
				it.remove();
			}
		}
		GuildManager.markDirty();
		return true;
	}

	/** May the chest give this stack? Deposit chests, the stack's own category and fallback chests. */
	static boolean gives(StorageChest chest, ItemStack stack) {
		return chest.isDeposit() || Storage.isFallback(chest) || Storage.effectiveCategory(chest).equalsIgnoreCase(Storage.categoryOf(stack));
	}

	/** Plain items of that kind the chest may give. */
	static int available(Container container, StorageChest chest, Item item) {
		int count = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (plain(stack, item) && gives(chest, stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Nearest usable registered chest (not excluded) that gives at least one plain item of that kind. */
	static @Nullable BlockPos nearestWith(WorkContext ctx, Item item, Set<BlockPos> excluded) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (StorageChest chest : ctx.guild.villagers.chests) {
			if (excluded.contains(chest.pos) || !Storage.usable(ctx.level, ctx.guild, chest)) {
				continue;
			}
			Container container = Storage.container(ctx.level, chest.pos);
			if (container == null || available(container, chest, item) <= 0) {
				continue;
			}
			double dist = ctx.distSq(chest.pos);
			if (dist < bestDist) {
				bestDist = dist;
				best = chest.pos;
			}
		}
		return best;
	}

	/** Room left in the bag for plain items of that kind (merging into its stacks, then new stacks). */
	static int room(WorkContext ctx, Item item) {
		int max = new ItemStack(item).getMaxStackSize();
		int room = 0;
		for (ItemStack stack : ctx.record.carried) {
			if (plain(stack, item)) {
				room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
			}
		}
		return room + Math.max(0, ctx.config.work.carryStacks - ctx.record.carried.size()) * max;
	}

	/**
	 * Moves up to {@code wanted} plain items from the registered chest at {@code pos} into the bag.
	 *
	 * @return items moved
	 */
	static int fetch(WorkContext ctx, BlockPos pos, Item item, int wanted) {
		StorageChest chest = Storage.registered(ctx.level, ctx.guild, pos);
		Container container = chest == null || !Storage.usable(ctx.level, ctx.guild, chest) ? null : Storage.container(ctx.level, pos);
		if (container == null) {
			return 0;
		}
		int amount = Math.min(Math.min(wanted, room(ctx, item)), available(container, chest, item));
		if (amount <= 0) {
			return 0;
		}
		int left = amount;
		for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
			ItemStack stack = container.getItem(i);
			if (!plain(stack, item) || !gives(chest, stack)) {
				continue;
			}
			left -= container.removeItem(i, Math.min(left, stack.getCount())).getCount();
		}
		int moved = amount - left;
		container.setChanged();
		addToBag(ctx, item, moved);
		GuildManager.markDirty();
		return moved;
	}

	/** Adds supplies taken from storage to the bag (not a collection: missions do not count them). */
	private static void addToBag(WorkContext ctx, Item item, int amount) {
		int left = amount;
		for (ItemStack stack : ctx.record.carried) {
			if (left <= 0) {
				break;
			}
			if (plain(stack, item) && stack.getCount() < stack.getMaxStackSize()) {
				int add = Math.min(left, stack.getMaxStackSize() - stack.getCount());
				stack.grow(add);
				left -= add;
			}
		}
		int max = new ItemStack(item).getMaxStackSize();
		while (left > 0) {
			int add = Math.min(left, max);
			ctx.record.carried.add(new ItemStack(item, add));
			left -= add;
		}
	}
}
