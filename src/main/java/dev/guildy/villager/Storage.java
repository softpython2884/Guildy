package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Guild storage: container access (double chests combined), insertion with proper stacking, item categories and
 * routing of items between deposit / category / fallback chests.
 */
final class Storage {
	private static @Nullable StorageCategories cachedCategories;
	private static @Nullable VillagerConfig cachedFor;

	private Storage() {
	}

	// ------------------------------------------------------------------ categories

	static StorageCategories categories() {
		VillagerConfig config = VillagerModule.config();
		if (cachedCategories == null || cachedFor != config) {
			cachedCategories = new StorageCategories(StorageDefaults.rules(config.storage.categories), config.storage.fallbackCategory);
			cachedFor = config;
		}
		return cachedCategories;
	}

	/** Storage category of a stack (ids, tags, patterns, item properties, creative tabs: see {@link ItemClassifier}). */
	static String categoryOf(ItemStack stack) {
		return ItemClassifier.categoryOf(stack);
	}

	/**
	 * Category a CATEGORY chest really receives: its category, or the fallback when that category no longer exists
	 * (removed or renamed in villagers.json5).
	 */
	static String effectiveCategory(StorageChest chest) {
		if (chest.isFree()) {
			return "";
		}
		String canonical = categories().canonical(chest.category);
		return canonical == null ? categories().fallback() : canonical;
	}

	static boolean isFallback(StorageChest chest) {
		return chest.role == StorageChest.Role.CATEGORY && effectiveCategory(chest).equalsIgnoreCase(categories().fallback());
	}

	/** Same kind of destination: both deposit chests, or category chests of the same (effective) category. */
	static boolean sameDestination(StorageChest a, StorageChest b) {
		return a.role == b.role && (a.isDeposit() || effectiveCategory(a).equalsIgnoreCase(effectiveCategory(b)));
	}

	/** Display name of a chest role ("Dépôt" or the effective category). */
	static String roleName(StorageChest chest) {
		if (chest.isFree()) {
			return Lang.raw("villager.storage.role.reserve");
		}
		return chest.isDeposit() ? Lang.raw("villager.storage.role.deposit") : effectiveCategory(chest);
	}

	// ------------------------------------------------------------------ containers

	/** True for block entities usable as guild storage (chests, trapped chests, barrels, shulker boxes). */
	static boolean isStorageBlock(@Nullable BlockEntity blockEntity) {
		return blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity;
	}

	/** The container at {@code pos} (double chests combined), or null if not loaded / not a storage block. */
	static @Nullable Container container(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!isStorageBlock(blockEntity)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		if (blockEntity instanceof ChestBlockEntity && state.getBlock() instanceof ChestBlock chestBlock) {
			Container combined = ChestBlock.getContainer(chestBlock, state, level, pos, true);
			if (combined != null) {
				return combined;
			}
		}
		return (Container) blockEntity;
	}

	/** Other half of a double chest, or null. */
	static @Nullable BlockPos otherHalf(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			return ChestBlock.getConnectedBlockPos(pos, state);
		}
		return null;
	}

	/** The container and, for a double chest, its other half. */
	static List<BlockPos> halves(ServerLevel level, BlockPos pos) {
		List<BlockPos> halves = new ArrayList<>(2);
		halves.add(pos);
		BlockPos other = otherHalf(level, pos);
		if (other != null) {
			halves.add(other);
		}
		return halves;
	}

	/**
	 * True while someone uses the container: a player whose open menu shows it (either half of a double chest), or any
	 * vanilla container user (copper golems...). Sorters never touch such a container.
	 */
	static boolean inUse(ServerLevel level, BlockPos pos) {
		return inUse(level, pos, shownContainers(level));
	}

	/** Same as {@link #inUse(ServerLevel, BlockPos)} with the containers of the open menus already listed. */
	static boolean inUse(ServerLevel level, BlockPos pos, List<Container> shown) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		for (BlockPos half : halves(level, pos)) {
			if (!(level.getBlockEntity(half) instanceof Container part)) {
				continue;
			}
			if (!part.getEntitiesWithContainerOpen().isEmpty()) {
				return true;
			}
			for (Container container : shown) {
				if (container == part || (container instanceof CompoundContainer compound && compound.contains(part))) {
					return true;
				}
			}
		}
		return false;
	}

	/** Containers shown by the open menus of the players (their own inventories aside), each once. */
	static List<Container> shownContainers(ServerLevel level) {
		List<Container> shown = new ArrayList<>();
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			AbstractContainerMenu menu = player.containerMenu;
			if (menu == null || menu == player.inventoryMenu) {
				continue;
			}
			for (Slot slot : menu.slots) {
				Container container = slot.container;
				if (container != player.getInventory() && !containsSame(shown, container)) {
					shown.add(container);
				}
			}
		}
		return shown;
	}

	private static boolean containsSame(List<Container> list, Container container) {
		for (Container known : list) {
			if (known == container) {
				return true;
			}
		}
		return false;
	}

	static boolean canPlace(Container container, int slot, ItemStack stack) {
		if (!container.canPlaceItem(slot, stack)) {
			return false;
		}
		return !(container instanceof WorldlyContainer worldly) || worldly.canPlaceItemThroughFace(slot, stack, Direction.UP);
	}

	/** How many items of this kind the container can still accept. */
	static int spaceFor(Container container, ItemStack stack) {
		int space = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack current = container.getItem(i);
			if (current.isEmpty()) {
				if (canPlace(container, i, stack)) {
					space += container.getMaxStackSize(stack);
				}
			} else if (ItemStack.isSameItemSameComponents(current, stack) && canPlace(container, i, stack)) {
				space += Math.max(0, Math.min(container.getMaxStackSize(current), current.getMaxStackSize()) - current.getCount());
			}
		}
		return space;
	}

	/**
	 * Inserts a copy of {@code input}: first merges into matching stacks, then fills empty slots.
	 *
	 * @return the leftover (empty if everything fit)
	 */
	static ItemStack insert(Container container, ItemStack input) {
		ItemStack stack = input.copy();
		boolean changed = false;
		int size = container.getContainerSize();
		for (int i = 0; i < size && !stack.isEmpty(); i++) {
			ItemStack current = container.getItem(i);
			if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, stack) || !canPlace(container, i, stack)) {
				continue;
			}
			int limit = Math.min(container.getMaxStackSize(current), current.getMaxStackSize());
			int move = Math.min(stack.getCount(), limit - current.getCount());
			if (move > 0) {
				current.grow(move);
				stack.shrink(move);
				changed = true;
			}
		}
		for (int i = 0; i < size && !stack.isEmpty(); i++) {
			if (!container.getItem(i).isEmpty() || !canPlace(container, i, stack)) {
				continue;
			}
			int move = Math.min(stack.getCount(), container.getMaxStackSize(stack));
			if (move <= 0) {
				continue;
			}
			container.setItem(i, stack.split(move));
			changed = true;
		}
		if (changed) {
			container.setChanged();
		}
		return stack;
	}

	/**
	 * Moves carried items matching {@code filter} into the container.
	 *
	 * @return number of items moved
	 */
	static int insertCarried(WorkContext ctx, Container container, Predicate<ItemStack> filter) {
		int moved = 0;
		Iterator<ItemStack> it = ctx.record.carried.iterator();
		List<ItemStack> leftovers = new ArrayList<>();
		while (it.hasNext()) {
			ItemStack stack = it.next();
			if (!filter.test(stack)) {
				continue;
			}
			ItemStack leftover = insert(container, stack);
			moved += stack.getCount() - leftover.getCount();
			it.remove();
			if (!leftover.isEmpty()) {
				leftovers.add(leftover);
			}
		}
		ctx.record.carried.addAll(leftovers);
		if (moved > 0) {
			GuildManager.markDirty();
		}
		return moved;
	}

	// ------------------------------------------------------------------ chests of a guild

	/** Registered chest usable right now: loaded, inside the territory, still a storage block. */
	static boolean usable(ServerLevel level, Guild guild, StorageChest chest) {
		return chest.pos != null && level.isLoaded(chest.pos) && GuildManager.at(level, chest.pos) == guild
				&& isStorageBlock(level.getBlockEntity(chest.pos));
	}

	/** Containers the sorter empties: deposits, and free chests of the chest rooms (reserve: they keep nothing). */
	static boolean isSortSource(StorageChest chest) {
		return chest.isDeposit() || chest.isFree();
	}

	static boolean hasDeposit(Guild guild) {
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.isDeposit()) {
				return true;
			}
		}
		return false;
	}

	/** Nearest usable deposit chest (not excluded) able to take the first carried stack. */
	static @Nullable StorageChest nearestDeposit(WorkContext ctx, Set<BlockPos> excluded) {
		ItemStack first = ctx.record.carried.isEmpty() ? ItemStack.EMPTY : ctx.record.carried.getFirst();
		StorageChest best = null;
		double bestDist = Double.MAX_VALUE;
		for (StorageChest chest : ctx.guild.villagers.chests) {
			if (!chest.isDeposit() || excluded.contains(chest.pos) || !usable(ctx.level, ctx.guild, chest)) {
				continue;
			}
			Container container = container(ctx.level, chest.pos);
			if (container == null || (!first.isEmpty() && spaceFor(container, first) <= 0)) {
				continue;
			}
			double dist = ctx.distSq(chest.pos);
			if (dist < bestDist) {
				bestDist = dist;
				best = chest;
			}
		}
		return best;
	}

	/**
	 * Destination of a stack for the sorter: a chest of its category, else a fallback ("Divers") chest, else
	 * (if {@code allowDeposit}) a deposit chest. Only usable chests with free space, not excluded. Nearest first.
	 */
	static @Nullable StorageChest route(WorkContext ctx, ItemStack stack, Set<BlockPos> excluded, boolean allowDeposit) {
		return route(ctx.level, ctx.guild, ctx.villager.position(), stack, excluded, allowDeposit);
	}

	/** Same as {@link #route(WorkContext, ItemStack, Set, boolean)}, nearest to {@code from}. */
	static @Nullable StorageChest route(ServerLevel level, Guild guild, Vec3 from, ItemStack stack, Set<BlockPos> excluded, boolean allowDeposit) {
		String category = categoryOf(stack);
		boolean isFallback = category.equalsIgnoreCase(categories().fallback());
		if (!isFallback) {
			StorageChest chest = nearestWithSpace(level, guild, from, stack, excluded,
					c -> c.role == StorageChest.Role.CATEGORY && !c.isFree() && effectiveCategory(c).equalsIgnoreCase(category));
			if (chest != null) {
				return chest;
			}
		}
		StorageChest fallback = nearestWithSpace(level, guild, from, stack, excluded, Storage::isFallback);
		if (fallback != null || !allowDeposit) {
			return fallback;
		}
		return nearestWithSpace(level, guild, from, stack, excluded, StorageChest::isDeposit);
	}

	private static @Nullable StorageChest nearestWithSpace(ServerLevel level, Guild guild, Vec3 from, ItemStack stack, Set<BlockPos> excluded,
			Predicate<StorageChest> filter) {
		StorageChest best = null;
		double bestDist = Double.MAX_VALUE;
		for (StorageChest chest : guild.villagers.chests) {
			if (!filter.test(chest) || excluded.contains(chest.pos) || !usable(level, guild, chest)) {
				continue;
			}
			Container container = container(level, chest.pos);
			if (container == null || spaceFor(container, stack) <= 0) {
				continue;
			}
			double dist = from.distanceToSqr(Vec3.atBottomCenterOf(chest.pos));
			if (dist < bestDist) {
				bestDist = dist;
				best = chest;
			}
		}
		return best;
	}

	/** True if the stack has a sorted destination (category or fallback chest with room). Memoized per item for one scan. */
	private static boolean movable(WorkContext ctx, ItemStack stack, Set<BlockPos> excluded, Map<Item, Boolean> memo) {
		Boolean known = memo.get(stack.getItem());
		if (known == null) {
			known = route(ctx, stack, excluded, false) != null;
			memo.put(stack.getItem(), known);
		}
		return known;
	}

	/**
	 * A usable deposit chest (or free chest of a chest room, emptied the same way) holding at least one stack the sorter
	 * can put away, nearest first.
	 */
	static @Nullable StorageChest findSortSource(WorkContext ctx, Set<BlockPos> excluded) {
		StorageChest best = null;
		double bestDist = Double.MAX_VALUE;
		Map<Item, Boolean> memo = new HashMap<>();
		for (StorageChest chest : ctx.guild.villagers.chests) {
			if (!isSortSource(chest) || excluded.contains(chest.pos) || !usable(ctx.level, ctx.guild, chest)) {
				continue;
			}
			Container container = container(ctx.level, chest.pos);
			if (container == null || container.isEmpty()) {
				continue;
			}
			boolean movable = false;
			for (int i = 0; i < container.getContainerSize() && !movable; i++) {
				ItemStack stack = container.getItem(i);
				movable = !stack.isEmpty() && movable(ctx, stack, excluded, memo);
			}
			double dist = ctx.distSq(chest.pos);
			if (movable && dist < bestDist) {
				bestDist = dist;
				best = chest;
			}
		}
		return best;
	}

	/**
	 * Takes up to {@code maxStacks} stacks that have a destination (category or fallback chest) into the carried list.
	 *
	 * @return number of stacks taken
	 */
	static int takeMovable(WorkContext ctx, Container container, int maxStacks, Set<BlockPos> excluded) {
		int taken = 0;
		Map<Item, Boolean> memo = new HashMap<>();
		for (int i = 0; i < container.getContainerSize() && taken < maxStacks; i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty() || !movable(ctx, stack, excluded, memo)) {
				continue;
			}
			ctx.record.carried.add(stack.copy());
			container.setItem(i, ItemStack.EMPTY);
			taken++;
		}
		if (taken > 0) {
			container.setChanged();
			GuildManager.markDirty();
		}
		return taken;
	}

	/** Used / total slots, or null if unavailable. */
	static int @Nullable [] fill(ServerLevel level, BlockPos pos) {
		Container container = container(level, pos);
		if (container == null) {
			return null;
		}
		int used = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (!container.getItem(i).isEmpty()) {
				used++;
			}
		}
		return new int[]{used, container.getContainerSize()};
	}

	/** Drops registrations whose block (loaded) is no longer a storage container. */
	static void cleanup(ServerLevel level, Guild guild) {
		boolean removed = guild.villagers.chests.removeIf(chest -> level.isLoaded(chest.pos) && !isStorageBlock(level.getBlockEntity(chest.pos)));
		if (removed) {
			GuildManager.markDirty();
		}
	}

	/** Registered chest at pos, also matching the other half of a double chest. */
	static @Nullable StorageChest registered(ServerLevel level, Guild guild, BlockPos pos) {
		StorageChest chest = guild.villagers.chestAt(pos);
		if (chest == null) {
			BlockPos other = otherHalf(level, pos);
			if (other != null) {
				chest = guild.villagers.chestAt(other);
			}
		}
		return chest;
	}
}
