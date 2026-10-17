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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chest groups: the CATEGORY chests of one category inside one chest room (room 0 = the chests outside any room), in
 * reading order ({@link ChestOrder}, order setting of the guild). Deposits and free automatic chests belong to no group.
 */
final class StorageGroups {
	/** A group of chests, in reading order. {@code key} = "room:category" (lower case). */
	record Group(String key, String category, int room, List<StorageChest> chests) {
	}

	private StorageGroups() {
	}

	static String key(int room, String category) {
		return room + ":" + category.toLowerCase(Locale.ROOT);
	}

	/** Storage order of the guild (its setting, else the default of villagers.json5). */
	static ChestOrder.Mode mode(Guild guild) {
		ChestOrder.Mode mode = ChestOrder.Mode.parse(guild.villagers.storageOrder);
		if (mode == null) {
			mode = ChestOrder.Mode.parse(VillagerModule.config().storage.defaultOrder);
		}
		return mode == null ? ChestOrder.Mode.COLUMNS : mode;
	}

	/** Groups of the guild: categories in config order (fallback last), then rooms (outside rooms first). */
	static List<Group> groups(ServerLevel level, Guild guild) {
		Map<String, List<StorageChest>> byKey = new LinkedHashMap<>();
		Map<String, String> categoryOf = new LinkedHashMap<>();
		Map<String, Integer> roomOf = new LinkedHashMap<>();
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.pos == null || chest.isDeposit() || chest.isFree()) {
				continue;
			}
			String category = Storage.effectiveCategory(chest);
			String key = key(chest.room, category);
			byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(chest);
			categoryOf.putIfAbsent(key, category);
			roomOf.putIfAbsent(key, chest.room);
		}
		StorageCategories categories = Storage.categories();
		List<String> keys = new ArrayList<>(byKey.keySet());
		keys.sort(Comparator.comparingInt((String key) -> {
			int index = categories.indexOf(categoryOf.get(key));
			return index < 0 ? Integer.MAX_VALUE : index;
		}).thenComparingInt(roomOf::get));
		ChestOrder.Mode mode = mode(guild);
		List<Group> groups = new ArrayList<>();
		for (String key : keys) {
			groups.add(new Group(key, categoryOf.get(key), roomOf.get(key), ordered(level, byKey.get(key), mode)));
		}
		return groups;
	}

	static @Nullable Group group(ServerLevel level, Guild guild, String key) {
		for (Group group : groups(level, guild)) {
			if (group.key().equals(key)) {
				return group;
			}
		}
		return null;
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
