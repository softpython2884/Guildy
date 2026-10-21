package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Items to plant: in the villager's bag, or in the guild's storage chests. A chest is used for an item when it is a
 * deposit chest, a chest of the item's own category ("Graines et pousses", "Nourriture"...) or a fallback ("Divers")
 * chest. Items taken from a chest really leave it (counted first, then moved: no duplication). Only plain stacks are
 * planted, and never the item of a "Rapporter une quantité" mission (it is brought back).
 */
final class PlantStock {
	private PlantStock() {
	}

	/** A storage chest seen by the planter: plantable items it may give (item id → count, slot order). */
	record ChestView(BlockPos pos, Map<String, Integer> items) {
	}

	/** Item of a quota mission, never planted from the bag (null without such a mission). */
	static @Nullable String reserved(VillagerRecord record) {
		return record.goal == Goal.QUOTA && !record.goalItem.isEmpty() ? record.goalItem : null;
	}

	/** Plantable items of the bag: item id → count, in bag order (the quota mission item is left out). */
	static Map<String, Integer> bag(WorkContext ctx, Predicate<Item> plantable) {
		Map<String, Integer> items = new LinkedHashMap<>();
		String reserved = reserved(ctx.record);
		for (ItemStack stack : ctx.record.carried) {
			if (!Planting.plain(stack) || !plantable.test(stack.getItem())) {
				continue;
			}
			String id = ItemUtil.id(stack.getItem());
			if (!id.equals(reserved)) {
				items.merge(id, stack.getCount(), Integer::sum);
			}
		}
		return items;
	}

	static int countInBag(WorkContext ctx, Item item) {
		if (ItemUtil.id(item).equals(reserved(ctx.record))) {
			return 0;
		}
		int count = 0;
		for (ItemStack stack : ctx.record.carried) {
			if (Planting.plain(stack) && stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Removes {@code count} items from the bag (all or nothing). */
	static boolean takeFromBag(WorkContext ctx, Item item, int count) {
		if (countInBag(ctx, item) < count) {
			return false;
		}
		int left = count;
		Iterator<ItemStack> it = ctx.record.carried.iterator();
		while (it.hasNext() && left > 0) {
			ItemStack stack = it.next();
			if (!Planting.plain(stack) || !stack.is(item)) {
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

	/** First plain bag stack of the item, or null. */
	static @Nullable ItemStack bagStack(WorkContext ctx, Item item) {
		for (ItemStack stack : ctx.record.carried) {
			if (Planting.plain(stack) && stack.is(item)) {
				return stack;
			}
		}
		return null;
	}

	/** May the chest give this stack? Deposit chests, filter chests accepting it, its own category and fallback chests. */
	static boolean gives(StorageChest chest, ItemStack stack) {
		return Storage.gives(chest, stack);
	}

	/**
	 * Usable storage chests (loaded, in the territory) holding plantable items they may give, nearest first. Both
	 * halves of a double chest are read once.
	 */
	static List<ChestView> chests(WorkContext ctx, Predicate<Item> plantable, Set<BlockPos> excluded) {
		// Only the chests that may give one of the plantable items are read (their givers in the storage index).
		List<ItemStack> wanted = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (plantable.test(item)) {
				wanted.add(new ItemStack(item));
			}
		}
		List<StorageChest> usable = new ArrayList<>();
		for (StorageChest chest : Storage.index(ctx.guild).givers(wanted)) {
			if (!excluded.contains(chest.pos) && Storage.usable(ctx.level, ctx.guild, chest)) {
				usable.add(chest);
			}
		}
		usable.sort(Comparator.comparingDouble(chest -> ctx.distSq(chest.pos)));
		List<ChestView> views = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (StorageChest chest : usable) {
			if (!seen.add(chest.pos)) {
				continue;
			}
			BlockPos other = Storage.otherHalf(ctx.level, chest.pos);
			if (other != null) {
				seen.add(other);
			}
			Container container = Storage.container(ctx.level, chest.pos);
			if (container == null) {
				continue;
			}
			Map<String, Integer> items = new LinkedHashMap<>();
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (Planting.plain(stack) && plantable.test(stack.getItem()) && gives(chest, stack)) {
					items.merge(ItemUtil.id(stack.getItem()), stack.getCount(), Integer::sum);
				}
			}
			if (!items.isEmpty()) {
				views.add(new ChestView(chest.pos, items));
			}
		}
		return views;
	}

	/** Room left in the bag for this item (merging into its stacks, then new stacks). */
	static int room(WorkContext ctx, Item item) {
		int max = new ItemStack(item).getMaxStackSize();
		int room = 0;
		for (ItemStack stack : ctx.record.carried) {
			if (Planting.plain(stack) && stack.is(item)) {
				room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
			}
		}
		return room + Math.max(0, ctx.config.work.carryStacks - ctx.record.carried.size()) * max;
	}

	/**
	 * Moves up to {@code wanted} plain items from the chest into the bag (whole squares of four for 2×2 species): what
	 * fits is counted first, then exactly that is removed from the chest and added to the bag.
	 *
	 * @return items moved
	 */
	static int fetch(WorkContext ctx, BlockPos pos, Item item, int wanted, boolean square) {
		StorageChest chest = Storage.registered(ctx.level, ctx.guild, pos);
		Container container = chest == null || !Storage.usable(ctx.level, ctx.guild, chest) ? null : Storage.container(ctx.level, pos);
		if (container == null) {
			return 0;
		}
		int available = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (Planting.plain(stack) && stack.is(item) && gives(chest, stack)) {
				available += stack.getCount();
			}
		}
		int amount = PlantingRules.fetchAmount(Math.min(wanted, room(ctx, item)), available, square);
		if (amount <= 0) {
			return 0;
		}
		int left = amount;
		for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
			ItemStack stack = container.getItem(i);
			if (!Planting.plain(stack) || !stack.is(item) || !gives(chest, stack)) {
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

	/** Adds items taken from storage to the bag (not a collection: missions do not count them). */
	static void addToBag(WorkContext ctx, Item item, int amount) {
		int left = amount;
		for (ItemStack stack : ctx.record.carried) {
			if (left <= 0) {
				break;
			}
			if (Planting.plain(stack) && stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
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
