package dev.guildy.villager;

import dev.guildy.Guildy;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chest rooms ("Salle des coffres"): creation from two corner containers (every storage block of the box inside the
 * territory becomes an automatic chest; containers already registered stay as they are and join the room), removal,
 * and the planning of their categories ({@link RoomPlanner}: lines = columns or rows of the guild's order). Planning
 * runs at creation, when the order changes, and every {@code roomPlanSeconds} while a sorter works; a chest whose
 * category changes is renamed and the sorters move its items. Runtime state (empty since, last plan) is not saved.
 */
final class ChestRooms {
	/** Chest position → scheduler tick since which it is empty. */
	private static final Map<BlockPos, Long> EMPTY_SINCE = new HashMap<>();
	private static final Map<UUID, Long> LAST_PLAN = new HashMap<>();
	/** "guild:room" → categories the last plan could not place (room full). */
	private static final Map<String, List<String>> UNPLACED = new HashMap<>();

	private ChestRooms() {
	}

	static void clear() {
		EMPTY_SINCE.clear();
		LAST_PLAN.clear();
		UNPLACED.clear();
	}

	static void forget(Guild guild) {
		LAST_PLAN.remove(guild.id);
		UNPLACED.keySet().removeIf(key -> key.startsWith(guild.id + ":"));
	}

	/** Categories the last plan of the room could not place (empty when none). */
	static List<String> unplaced(Guild guild, ChestRoom room) {
		return UNPLACED.getOrDefault(guild.id + ":" + room.id, List.of());
	}

	static List<StorageChest> chests(Guild guild, ChestRoom room) {
		List<StorageChest> chests = new ArrayList<>();
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.room == room.id && chest.pos != null) {
				chests.add(chest);
			}
		}
		return chests;
	}

	/** The room's chests without a second entry for the other half of a double chest (planning reads each once). */
	private static List<StorageChest> distinctChests(ServerLevel level, Guild guild, ChestRoom room) {
		List<StorageChest> chests = new ArrayList<>();
		Set<BlockPos> covered = new HashSet<>();
		for (StorageChest chest : chests(guild, room)) {
			if (covered.contains(chest.pos)) {
				continue;
			}
			covered.addAll(level.isLoaded(chest.pos) ? Storage.halves(level, chest.pos) : List.of(chest.pos));
			chests.add(chest);
		}
		return chests;
	}

	/** Number of containers registered by the chest rooms of the guild (their budget: storage.maxRoomChests). */
	static int roomChestCount(Guild guild) {
		int count = 0;
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.room > 0 && chest.role != StorageChest.Role.FILTER) {
				count++;
			}
		}
		return count;
	}

	// ================================================================ creation / removal

	/**
	 * Creates a room between two corners (messages to the player); null when refused.
	 */
	static @Nullable ChestRoom create(ServerPlayer player, ServerLevel level, Guild guild, BlockPos a, BlockPos b) {
		VillagerConfig.Storage config = VillagerModule.config().storage;
		if (guild.villagers.rooms.size() >= config.maxRooms) {
			Msg.send(player, "villager.storage.room.limit", config.maxRooms);
			return null;
		}
		int sizeX = Math.abs(a.getX() - b.getX()) + 1;
		int sizeY = Math.abs(a.getY() - b.getY()) + 1;
		int sizeZ = Math.abs(a.getZ() - b.getZ()) + 1;
		if (Math.max(sizeX, Math.max(sizeY, sizeZ)) > config.maxRoomSize) {
			Msg.send(player, "villager.storage.room.too_big", sizeX, sizeY, sizeZ, config.maxRoomSize);
			return null;
		}
		if (roomChestCount(guild) >= config.maxRoomChests) {
			Msg.send(player, "villager.storage.room.chest_limit", config.maxRoomChests);
			return null;
		}
		ChestRoom room = new ChestRoom(guild.villagers.nextRoomId, nextName(guild), a, b);
		int added = scan(level, guild, room);
		if (added < 0) {
			Msg.send(player, "villager.storage.room.unloaded");
			return null;
		}
		if (added == 0 && chests(guild, room).isEmpty()) {
			Msg.send(player, "villager.storage.room.empty");
			return null;
		}
		guild.villagers.nextRoomId++;
		guild.villagers.rooms.add(room);
		plan(level, guild, room, true, Scheduler.ticks());
		for (StorageChest chest : chests(guild, room)) {
			StorageLabels.sync(level, guild, chest);
		}
		Tidier.invalidate(guild);
		GuildManager.markDirty();
		int lines = lines(level, guild, room);
		Msg.send(player, "villager.storage.room.created", room.name, chests(guild, room).size(), lines,
				Lang.raw(StorageGroups.mode(guild) == ChestOrder.Mode.COLUMNS ? "villager.storage.order.columns.word" : "villager.storage.order.rows.word"));
		if (roomChestCount(guild) >= config.maxRoomChests) {
			Msg.send(player, "villager.storage.room.chest_limit", config.maxRoomChests);
		}
		return room;
	}

	/**
	 * Registers the storage blocks of the room's box that are not registered yet (automatic, free), and attaches the
	 * registered category chests of the box to the room.
	 *
	 * @return number of containers added, -1 when part of the box is not loaded (nothing done)
	 */
	static int scan(ServerLevel level, Guild guild, ChestRoom room) {
		VillagerConfig.Storage config = VillagerModule.config().storage;
		for (int x = room.min.getX() >> 4; x <= room.max.getX() >> 4; x++) {
			for (int z = room.min.getZ() >> 4; z <= room.max.getZ() >> 4; z++) {
				if (!level.hasChunk(x, z)) {
					return -1;
				}
			}
		}
		int budget = config.maxRoomChests - roomChestCount(guild);
		int added = 0;
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos pos : BlockPos.betweenClosed(room.min, room.max)) {
			BlockPos at = pos.immutable();
			if (seen.contains(at) || !Storage.isStorageBlock(level.getBlockEntity(at)) || GuildManager.at(level, at) != guild) {
				continue;
			}
			seen.add(at);
			BlockPos other = Storage.otherHalf(level, at);
			if (other != null) {
				seen.add(other.immutable());
			}
			StorageChest existing = Storage.registered(level, guild, at);
			if (existing != null) {
				if (existing.room == 0 && !existing.isDeposit()) {
					existing.room = room.id;
				}
				continue;
			}
			if (budget <= 0) {
				continue;
			}
			StorageChest chest = new StorageChest(at, StorageChest.Role.CATEGORY, "");
			chest.auto = true;
			chest.room = room.id;
			guild.villagers.chests.add(chest);
			budget--;
			added++;
		}
		if (added > 0) {
			GuildManager.markDirty();
		}
		return added;
	}

	/** Unregisters the room: its automatic chests leave the storage (their items stay inside), the others stay. */
	static void remove(ServerLevel level, Guild guild, ChestRoom room) {
		List<StorageChest> removed = new ArrayList<>();
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.room != room.id) {
				continue;
			}
			if (chest.auto) {
				removed.add(chest);
			} else {
				chest.room = 0;
			}
		}
		for (StorageChest chest : removed) {
			StorageLabels.clear(level, chest);
			EMPTY_SINCE.remove(chest.pos);
		}
		guild.villagers.chests.removeAll(removed);
		guild.villagers.rooms.remove(room);
		UNPLACED.remove(guild.id + ":" + room.id);
		Tidier.invalidate(guild);
		GuildManager.markDirty();
	}

	private static String nextName(Guild guild) {
		Set<String> names = new HashSet<>();
		for (ChestRoom room : guild.villagers.rooms) {
			names.add(room.name);
		}
		return ZoneMath.nextName(Lang.strip(Lang.raw("villager.storage.room.base")), names);
	}

	// ================================================================ planning

	/** Plans the rooms of the guild when due (called by the sorters). */
	static void maybePlan(ServerLevel level, Guild guild, long now) {
		if (guild.villagers.rooms.isEmpty()) {
			return;
		}
		Long last = LAST_PLAN.get(guild.id);
		if (last != null && now - last < VillagerModule.config().storage.roomPlanSeconds * 20L) {
			return;
		}
		LAST_PLAN.put(guild.id, now);
		for (ChestRoom room : List.copyOf(guild.villagers.rooms)) {
			plan(level, guild, room, false, now);
		}
	}

	/** The order of the guild changed: every room is planned again from scratch. */
	static void replanAll(ServerLevel level, Guild guild) {
		long now = Scheduler.ticks();
		for (ChestRoom room : List.copyOf(guild.villagers.rooms)) {
			plan(level, guild, room, true, now);
		}
		Tidier.invalidate(guild);
	}

	/** Number of lines (columns or rows) holding automatic chests in the room. */
	static int lines(ServerLevel level, Guild guild, ChestRoom room) {
		List<StorageChest> chests = distinctChests(level, guild, room);
		ChestOrder.Result result = StorageGroups.order(level, chests, StorageGroups.mode(guild));
		int lines = 0;
		for (List<Integer> line : result.lines()) {
			for (int id : line) {
				if (chests.get(id).auto) {
					lines++;
					break;
				}
			}
		}
		return lines;
	}

	/**
	 * Plans one room: new categories of its automatic chests (renamed at once).
	 *
	 * @return number of chests whose category changed, -1 when a chest of the room is not loaded (nothing done)
	 */
	static int plan(ServerLevel level, Guild guild, ChestRoom room, boolean fresh, long now) {
		VillagerConfig.Storage config = VillagerModule.config().storage;
		List<StorageChest> chests = distinctChests(level, guild, room);
		if (chests.isEmpty()) {
			return 0;
		}
		List<Container> containers = new ArrayList<>(chests.size());
		for (StorageChest chest : chests) {
			Container container = level.isLoaded(chest.pos) ? Storage.container(level, chest.pos) : null;
			if (container == null) {
				return -1;
			}
			containers.add(container);
		}
		long idleTicks = config.roomIdleMinutes * 60L * 20L;
		List<RoomPlanner.RoomChest> roomChests = new ArrayList<>(chests.size());
		Map<Integer, String> current = new HashMap<>();
		Set<Integer> idle = new HashSet<>();
		for (int id = 0; id < chests.size(); id++) {
			StorageChest chest = chests.get(id);
			Container container = containers.get(id);
			roomChests.add(new RoomPlanner.RoomChest(StorageGroups.unit(level, id, chest.pos), container.getContainerSize(), chest.auto));
			if (!chest.auto) {
				continue;
			}
			current.put(id, chest.category);
			if (container.isEmpty()) {
				long since = EMPTY_SINCE.computeIfAbsent(chest.pos, pos -> now);
				if (now - since >= idleTicks) {
					idle.add(id);
				}
			} else {
				EMPTY_SINCE.remove(chest.pos);
			}
		}
		StorageCategories categories = Storage.categories();
		RoomPlanner.Settings settings = new RoomPlanner.Settings(config.roomHeadroomPercent, config.roomGrowFreeSlots,
				config.roomNewCategoryStacks, config.roomMaxShift);
		RoomPlanner.Result result = RoomPlanner.planRoom(roomChests, StorageGroups.mode(guild), current, categories.names(),
				categories.fallback(), demand(level, guild, chests, containers), idle, settings, fresh);
		UNPLACED.put(guild.id + ":" + room.id, List.copyOf(result.unplaced()));
		for (int id : result.changed()) {
			StorageChest chest = chests.get(id);
			chest.category = result.assignment().getOrDefault(id, RoomPlanner.FREE);
			StorageLabels.sync(level, guild, chest);
		}
		if (!result.changed().isEmpty()) {
			Tidier.invalidate(guild);
			GuildManager.markDirty();
			Guildy.LOGGER.debug("Storage: room '{}' of guild {}: {} chest(s) reassigned", room.name, guild.name, result.changed().size());
		}
		return result.changed().size();
	}

	/**
	 * Stacks held per category: the items of the room (except those already in a manual chest of their category) and
	 * the items waiting in the deposits; each distinct stack counts its merged number of stacks.
	 */
	private static Map<String, Integer> demand(ServerLevel level, Guild guild, List<StorageChest> chests, List<Container> containers) {
		Object2LongOpenCustomHashMap<ItemStack> totals = new Object2LongOpenCustomHashMap<>(StorageSort.SAME_ITEM);
		for (int i = 0; i < chests.size(); i++) {
			StorageChest chest = chests.get(i);
			boolean manual = !chest.auto && !chest.isDeposit();
			String category = manual ? Storage.effectiveCategory(chest) : "";
			Container container = containers.get(i);
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (stack.isEmpty() || (manual && (chest.role == StorageChest.Role.FILTER ? Storage.accepts(chest, stack) : Storage.categoryOf(stack).equalsIgnoreCase(category)))) {
					continue;
				}
				totals.addTo(stack.copyWithCount(1), stack.getCount());
			}
		}
		for (StorageChest chest : guild.villagers.chests) {
			if (!chest.isDeposit() || !Storage.usable(level, guild, chest)) {
				continue;
			}
			Container container = Storage.container(level, chest.pos);
			if (container == null) {
				continue;
			}
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (!stack.isEmpty()) {
					totals.addTo(stack.copyWithCount(1), stack.getCount());
				}
			}
		}
		Map<String, Integer> demand = new LinkedHashMap<>();
		for (Object2LongMap.Entry<ItemStack> entry : totals.object2LongEntrySet()) {
			ItemStack stack = entry.getKey();
			int max = Math.max(1, stack.getMaxStackSize());
			long stacks = (entry.getLongValue() + max - 1) / max;
			demand.merge(Storage.categoryOf(stack), (int) Math.min(Integer.MAX_VALUE, stacks), Integer::sum);
		}
		return demand;
	}
}
