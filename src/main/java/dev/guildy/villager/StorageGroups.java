package dev.guildy.villager;

import dev.guildy.data.Guild;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chest groups: the CATEGORY chests of one category, or the FILTER chests of one filter, inside one chest room (room 0
 * = the chests outside any room), in reading order ({@link ChestOrder}, order setting of the guild). Deposits and free
 * automatic chests belong to no group. A group bigger than {@code storage.maxGroupChests} is cut into consecutive parts
 * in reading order (keys "key", "key#2", "key#3"...): each part is audited and tidied on its own, so that the cost of a
 * tidying step never depends on the size of the storage.
 */
final class StorageGroups {
	/**
	 * A group of chests, in reading order. {@code key} = "room:category" (lower case) or "room:filter:" + filter key;
	 * {@code category} = the category, or the name of the filter (statuses, menus); {@code filter} null for category
	 * groups.
	 */
	record Group(String key, String category, int room, List<StorageChest> chests, @Nullable ChestFilter filter, int part, int parts) {
		boolean isFilter() {
			return filter != null;
		}
	}

	/** Separator of the part number in the key of a part of a big group. */
	static final String PART = "#";

	/** Key of the whole group of a part ("1:pierre#2" → "1:pierre"; other keys unchanged). */
	static String baseKey(String key) {
		int at = key.lastIndexOf(PART);
		if (at > 0 && at < key.length() - 1) {
			for (int i = at + 1; i < key.length(); i++) {
				if (!Character.isDigit(key.charAt(i))) {
					return key;
				}
			}
			return key.substring(0, at);
		}
		return key;
	}

	private StorageGroups() {
	}

	static String key(int room, String category) {
		return room + ":" + category.toLowerCase(Locale.ROOT);
	}

	static String filterKey(int room, ChestFilter filter) {
		return room + ":filter:" + filter.key();
	}

	/** Group key of a chest (deposits and free chests: a key no group has). */
	static String keyOf(StorageChest chest) {
		if (chest.isFilter()) {
			return filterKey(chest.room, chest.filter);
		}
		return key(chest.room, Storage.effectiveCategory(chest));
	}

	/** Layout inside the chests of the guild (its setting, else the default of villagers.json5): by columns or rows. */
	static StorageLayout.Fill fill(Guild guild) {
		StorageLayout.Fill fill = StorageLayout.Fill.parse(guild.villagers.chestLayout);
		if (fill == null) {
			fill = StorageLayout.Fill.parse(VillagerModule.config().storage.defaultChestLayout);
		}
		return fill == null ? StorageLayout.Fill.COLUMNS : fill;
	}

	/** Storage order of the guild (its setting, else the default of villagers.json5). */
	static ChestOrder.Mode mode(Guild guild) {
		ChestOrder.Mode mode = ChestOrder.Mode.parse(guild.villagers.storageOrder);
		if (mode == null) {
			mode = ChestOrder.Mode.parse(VillagerModule.config().storage.defaultOrder);
		}
		return mode == null ? ChestOrder.Mode.COLUMNS : mode;
	}

	/**
	 * Groups of the guild: filter groups first (by name), then categories in menu order (sub-categories after their
	 * parent, transversal categories after the others, fallback last); rooms outside first. Kept by the guild's
	 * {@link StorageIndex} (rebuilt with it).
	 */
	static List<Group> groups(ServerLevel level, Guild guild) {
		return StorageIndex.of(guild.villagers).groups(level, guild.villagers, mode(guild));
	}

	/** Computes the groups of these chests (reads the facing of the loaded containers). */
	static List<Group> build(ServerLevel level, List<StorageChest> chests, ChestOrder.Mode mode) {
		Map<String, List<StorageChest>> byKey = new LinkedHashMap<>();
		Map<String, String> categoryOf = new LinkedHashMap<>();
		Map<String, Integer> roomOf = new LinkedHashMap<>();
		Map<String, ChestFilter> filterOf = new LinkedHashMap<>();
		for (StorageChest chest : chests) {
			if (chest.pos == null || chest.isDeposit() || chest.isFree() || (chest.role == StorageChest.Role.FILTER && !chest.isFilter())) {
				continue;
			}
			String key = keyOf(chest);
			if (!byKey.containsKey(key)) {
				categoryOf.put(key, chest.isFilter() ? FilterNames.text(chest.filter) : Storage.effectiveCategory(chest));
				roomOf.put(key, chest.room);
				if (chest.isFilter()) {
					filterOf.put(key, chest.filter);
				}
			}
			byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(chest);
		}
		List<String> menu = Storage.categories().menuOrder();
		Map<String, Integer> rank = new HashMap<>();
		for (int i = 0; i < menu.size(); i++) {
			rank.put(menu.get(i).toLowerCase(Locale.ROOT), i);
		}
		List<String> keys = new ArrayList<>(byKey.keySet());
		keys.sort(Comparator.comparingInt((String key) -> {
			if (filterOf.containsKey(key)) {
				return -1;
			}
			return rank.getOrDefault(categoryOf.get(key).toLowerCase(Locale.ROOT), Integer.MAX_VALUE);
		}).thenComparing(key -> filterOf.containsKey(key) ? categoryOf.get(key).toLowerCase(Locale.ROOT) : "")
				.thenComparingInt(roomOf::get).thenComparing(key -> key));
		List<Group> groups = new ArrayList<>();
		int max = VillagerModule.config().storage.maxGroupChests;
		for (String key : keys) {
			List<StorageChest> ordered = ordered(level, byKey.get(key), mode);
			if (max <= 0 || ordered.size() <= max) {
				groups.add(new Group(key, categoryOf.get(key), roomOf.get(key), ordered, filterOf.get(key), 1, 1));
				continue;
			}
			// Parts of (nearly) equal size, consecutive in reading order.
			int parts = (ordered.size() + max - 1) / max;
			int size = (ordered.size() + parts - 1) / parts;
			for (int part = 0; part < parts; part++) {
				List<StorageChest> slice = List.copyOf(ordered.subList(part * size, Math.min(ordered.size(), (part + 1) * size)));
				groups.add(new Group(part == 0 ? key : key + PART + (part + 1), categoryOf.get(key), roomOf.get(key), slice, filterOf.get(key),
						part + 1, parts));
			}
		}
		return groups;
	}

	static @Nullable Group group(ServerLevel level, Guild guild, String key) {
		List<Group> groups = groups(level, guild);
		int position = StorageIndex.of(guild.villagers).groupPosition(key);
		return position < 0 || position >= groups.size() ? null : groups.get(position);
	}

	/** The chests in reading order (an unloaded chest keeps its place without facing). */
	static List<StorageChest> ordered(ServerLevel level, List<StorageChest> chests, ChestOrder.Mode mode) {
		ChestOrder.Result result = order(level, chests, mode);
		List<StorageChest> ordered = new ArrayList<>(chests.size());
		for (int id : result.order()) {
			ordered.add(chests.get(id));
		}
		return ordered;
	}

	/** Reading order and lines of the chests (ids = indices in {@code chests}). */
	static ChestOrder.Result order(ServerLevel level, List<StorageChest> chests, ChestOrder.Mode mode) {
		List<ChestOrder.Unit> units = new ArrayList<>(chests.size());
		for (int i = 0; i < chests.size(); i++) {
			units.add(unit(level, i, chests.get(i).pos));
		}
		return ChestOrder.order(units, mode);
	}

	/** Spatial unit of a container: its block, its facing (chest, barrel, shulker box) and the other half of a double chest. */
	static ChestOrder.Unit unit(ServerLevel level, int id, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return ChestOrder.Unit.single(id, pos.getX(), pos.getY(), pos.getZ(), null);
		}
		ChestOrder.Facing facing = facing(level.getBlockState(pos));
		BlockPos other = Storage.otherHalf(level, pos);
		return other == null ? ChestOrder.Unit.single(id, pos.getX(), pos.getY(), pos.getZ(), facing)
				: ChestOrder.Unit.pair(id, pos.getX(), pos.getY(), pos.getZ(), other.getX(), other.getZ(), facing);
	}

	static ChestOrder.@Nullable Facing facing(BlockState state) {
		Direction direction = null;
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			direction = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		} else if (state.hasProperty(BlockStateProperties.FACING)) {
			direction = state.getValue(BlockStateProperties.FACING);
		}
		return direction == null || !direction.getAxis().isHorizontal() ? null : ChestOrder.Facing.of(direction.getSerializedName());
	}
}
