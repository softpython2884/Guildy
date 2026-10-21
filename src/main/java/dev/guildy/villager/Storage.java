package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
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
 * routing of items between deposit / filter / category / fallback chests. Lookups go through the guild's
 * {@link StorageIndex}: their cost depends on the chests of the item's tiers, not on the number of chests.
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
			cachedCategories = new StorageCategories(StorageDefaults.rules(config.storage.categories),
					StorageDefaults.parents(config.storage.categories), config.storage.fallbackCategory);
			cachedFor = config;
		}
		return cachedCategories;
	}

	/**
	 * Storage category of a stack: its deepest category of the tree (ids, tags, patterns, item properties, creative
	 * tabs: see {@link ItemClassifier}), transversal categories aside.
	 */
	static String categoryOf(ItemStack stack) {
		return ItemClassifier.categoryOf(stack);
	}

	/**
	 * Routing chain of a stack, most specific first: the transversal categories it matches, its deepest category... its
	 * top-level category (empty: only the fallback).
	 */
	static List<String> chain(ItemStack stack) {
		return ItemClassifier.chain(stack);
	}

	/** Index of the guild's registered containers. */
	static StorageIndex index(Guild guild) {
		return StorageIndex.of(guild.villagers);
	}

	/**
	 * A registered container changed role, category, filter, room or position (additions and removals are seen by
	 * themselves): the index is rebuilt at its next use.
	 */
	static void changed(Guild guild) {
		guild.villagers.storageChanged();
	}

	/** True when {@code count} reached a limit of villagers.json5 ({@code max} 0 = unlimited). */
	static boolean limitReached(int count, int max) {
		return max > 0 && count >= max;
	}

	/** Namespaced id of the item of a stack ("minecraft:diorite"). */
	static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	/**
	 * Category a CATEGORY chest really receives: its category, or the fallback when that category no longer exists
	 * (removed or renamed in villagers.json5). "" for free chests and filter chests.
	 */
	static String effectiveCategory(StorageChest chest) {
		if (chest.isFree() || chest.role == StorageChest.Role.FILTER) {
			return "";
		}
		String canonical = categories().canonical(chest.category);
		return canonical == null ? categories().fallback() : canonical;
	}

	static boolean isFallback(StorageChest chest) {
		return chest.role == StorageChest.Role.CATEGORY && effectiveCategory(chest).equalsIgnoreCase(categories().fallback());
	}

	/** True when the filter of a FILTER chest accepts the stack (by item id, components never matter). */
	static boolean accepts(StorageChest chest, ItemStack stack) {
		return chest.isFilter() && !stack.isEmpty() && chest.filter.accepts(itemId(stack));
	}

	/**
	 * Same kind of destination: both deposit chests, filter chests of the same filter, or category chests of the same
	 * (effective) category.
	 */
	static boolean sameDestination(StorageChest a, StorageChest b) {
		if (a.role != b.role) {
			return false;
		}
		if (a.isDeposit()) {
			return true;
		}
		if (a.role == StorageChest.Role.FILTER) {
			return a.isFilter() && b.isFilter() && a.filter.key().equals(b.filter.key());
		}
		return effectiveCategory(a).equalsIgnoreCase(effectiveCategory(b));
	}

	/** Display name of a chest role ("Dépôt", the filter's name or the effective category). */
	static String roleName(StorageChest chest) {
		if (chest.isFree()) {
			return Lang.raw("villager.storage.role.reserve");
		}
		if (chest.isFilter()) {
			return FilterNames.text(chest.filter);
		}
		return chest.isDeposit() ? Lang.raw("villager.storage.role.deposit") : effectiveCategory(chest);
	}

	/**
	 * May the chest give this stack to a worker looking for it (crafts, plantations)? Deposit and fallback chests,
	 * filter chests accepting it, and the chests of the categories of its routing chain (its category, the parent
	 * categories, a transversal category that takes it).
	 */
	static boolean gives(StorageChest chest, ItemStack stack) {
		if (chest.isDeposit() || isFallback(chest)) {
			return true;
		}
		if (chest.role == StorageChest.Role.FILTER) {
			return accepts(chest, stack);
		}
		if (chest.isFree()) {
			return false;
		}
		String category = effectiveCategory(chest);
		for (String link : chain(stack)) {
			if (link.equalsIgnoreCase(category)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ routing

	/** The chest as the routing sees it ({@link StorageRouting}). */
	static StorageRouting.Target target(StorageChest chest) {
		if (chest.isDeposit()) {
			return StorageRouting.Target.deposit();
		}
		if (chest.isFree() || (chest.role == StorageChest.Role.FILTER && !chest.isFilter())) {
			return StorageRouting.Target.reserve();
		}
		if (chest.isFilter()) {
			return StorageRouting.Target.filter(chest.filter);
		}
		return isFallback(chest) ? StorageRouting.Target.fallback() : StorageRouting.Target.category(effectiveCategory(chest));
	}

	/** Tier of a chest for a stack ({@link StorageRouting#tier}). */
	static int tier(StorageChest chest, ItemStack stack) {
		return StorageRouting.tier(target(chest), itemId(stack), chain(stack));
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
			// The entity search of the vanilla openers only when the container counts an opener (cheap check first).
			if (mayBeOpen(level, half, part) && !part.getEntitiesWithContainerOpen().isEmpty()) {
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

	/**
	 * False when the container surely has no vanilla opener: a chest counting none, a closed barrel (its OPEN state is
	 * set by the openers counter). Other containers (shulker boxes...) are checked the slow way.
	 */
	private static boolean mayBeOpen(ServerLevel level, BlockPos pos, Container part) {
		if (part instanceof ChestBlockEntity) {
			return ChestBlockEntity.getOpenCount(level, pos) > 0;
		}
		if (part instanceof BarrelBlockEntity) {
			BlockState state = level.getBlockState(pos);
			return !state.hasProperty(BarrelBlock.OPEN) || state.getValue(BarrelBlock.OPEN);
		}
		return true;
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

	/** True when the container can accept at least one item of this kind (stops at the first free place). */
	static boolean hasSpaceFor(Container container, ItemStack stack) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack current = container.getItem(i);
			if (current.isEmpty()) {
				if (container.getMaxStackSize(stack) > 0 && canPlace(container, i, stack)) {
					return true;
				}
			} else if (ItemStack.isSameItemSameComponents(current, stack)
					&& Math.min(container.getMaxStackSize(current), current.getMaxStackSize()) > current.getCount() && canPlace(container, i, stack)) {
				return true;
			}
		}
		return false;
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
		return !index(guild).deposits().isEmpty();
	}

	/** Nearest usable deposit chest (not excluded) able to take the first carried stack. */
	static @Nullable StorageChest nearestDeposit(WorkContext ctx, Set<BlockPos> excluded) {
		ItemStack first = ctx.record.carried.isEmpty() ? ItemStack.EMPTY : ctx.record.carried.getFirst();
		StorageChest best = null;
		double bestDist = Double.MAX_VALUE;
		for (StorageChest chest : index(ctx.guild).deposits()) {
			if (excluded.contains(chest.pos) || !usable(ctx.level, ctx.guild, chest)) {
				continue;
			}
			Container container = container(ctx.level, chest.pos);
			if (container == null || (!first.isEmpty() && !hasSpaceFor(container, first))) {
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
	 * Destination of a stack for the sorter ({@link StorageRouting}): a filter chest accepting it (exact items, then
	 * material, then type), else a chest of its category, else a fallback ("Divers") chest, else (if
	 * {@code allowDeposit}) a deposit chest. Only usable chests with free space, not excluded. Nearest first within a
	 * tier.
	 */
	static @Nullable StorageChest route(WorkContext ctx, ItemStack stack, Set<BlockPos> excluded, boolean allowDeposit) {
		return route(ctx.level, ctx.guild, ctx.villager.position(), stack, excluded, allowDeposit);
	}

	/** Same as {@link #route(WorkContext, ItemStack, Set, boolean)}, nearest to {@code from}. */
	static @Nullable StorageChest route(ServerLevel level, Guild guild, Vec3 from, ItemStack stack, Set<BlockPos> excluded, boolean allowDeposit) {
		return route(level, guild, from, stack, excluded, allowDeposit ? StorageRouting.DEPOSIT_TIER : StorageRouting.FALLBACK_TIER);
	}

	/**
	 * Best usable chest with room for the stack among the tiers up to {@code maxTier}, nearest first, or null. Only the
	 * chests of the item's tiers are read ({@link StorageRouting.Index}), one tier at a time.
	 */
	static @Nullable StorageChest route(ServerLevel level, Guild guild, Vec3 from, ItemStack stack, Set<BlockPos> excluded, int maxTier) {
		if (stack.isEmpty()) {
			return null;
		}
		StorageIndex index = index(guild);
		StorageRouting.Cursor cursor = index.routing().cursor(itemId(stack), chain(stack), maxTier, from.x, from.y, from.z);
		for (int target = cursor.next(); target >= 0; target = cursor.next()) {
			StorageChest chest = index.chest(target);
			if (excluded.contains(chest.pos) || !usable(level, guild, chest)) {
				continue;
			}
			Container container = container(level, chest.pos);
			if (container != null && hasSpaceFor(container, stack)) {
				return chest;
			}
		}
		return null;
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
		for (StorageChest chest : index(ctx.guild).sources()) {
			if (excluded.contains(chest.pos) || !usable(ctx.level, ctx.guild, chest)) {
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
		StorageIndex index = index(guild);
		StorageChest chest = index.at(pos);
		if (chest == null && level.isLoaded(pos)) {
			BlockPos other = otherHalf(level, pos);
			if (other != null) {
				chest = index.at(other);
			}
		}
		return chest;
	}
}
