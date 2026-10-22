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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
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
 * runs at creation, when the order changes, and while a sorter works: one room every {@code roomPlanSeconds} / number of
 * rooms (each room every {@code roomPlanSeconds}, never all rooms in the same tick); a chest whose category changes is
 * renamed and the sorters move its items. A room reserved to some categories ({@link ChestRoom#categories}) plans only
 * those (each keeps at least one line). A filtered room ({@link #setFiltered}) is never planned: its automatic chests
 * receive what they already hold ({@link RoomContents}). Runtime state (empty since, last plan) is not saved.
 */
final class ChestRooms {
	/** Chest position → scheduler tick since which it is empty. */
	private static final Map<BlockPos, Long> EMPTY_SINCE = new HashMap<>();
	private static final Map<UUID, Long> LAST_PLAN = new HashMap<>();
	/** Guild → index of the next room to plan (round robin). */
	private static final Map<UUID, Integer> NEXT_ROOM = new HashMap<>();
	/** "guild:room" → categories the last plan could not place (room full). */
	private static final Map<String, List<String>> UNPLACED = new HashMap<>();

	private ChestRooms() {
	}

	static void clear() {
		EMPTY_SINCE.clear();
		LAST_PLAN.clear();
		NEXT_ROOM.clear();
		UNPLACED.clear();
	}

	static void forget(Guild guild) {
		LAST_PLAN.remove(guild.id);
		NEXT_ROOM.remove(guild.id);
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
		return Storage.index(guild).roomChestCount();
	}


	// ================================================================ creation / removal

	/**
	 * Creates a room between two corners (messages to the player); null when refused.
	 */
	static @Nullable ChestRoom create(ServerPlayer player, ServerLevel level, Guild guild, BlockPos a, BlockPos b) {
		VillagerConfig.Storage config = VillagerModule.config().storage;
		if (!config.chestRooms) {
			Msg.send(player, "villager.storage.room.disabled");
			return null;
		}
		if (Storage.limitReached(guild.villagers.rooms.size(), config.maxRooms)) {
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
		if (Storage.limitReached(roomChestCount(guild), config.maxRoomChests)) {
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
		if (Storage.limitReached(roomChestCount(guild), config.maxRoomChests)) {
			Msg.send(player, "villager.storage.room.chest_limit", config.maxRoomChests);
		}
		return room;
	}

	/**
	 * Registers the storage blocks of the room's box that are not registered yet (automatic, free), and attaches the
	 * registered category chests of the box to the room. Reads the block entities of the box's chunks (never every block
	 * of the box: a 64 × 64 × 64 room is a few chunks).
	 *
	 * @return number of containers added, -1 when part of the box is not loaded (nothing done)
	 */
	static int scan(ServerLevel level, Guild guild, ChestRoom room) {
		VillagerConfig.Storage config = VillagerModule.config().storage;
		List<BlockPos> positions = new ArrayList<>();
		for (int x = room.min.getX() >> 4; x <= room.max.getX() >> 4; x++) {
			for (int z = room.min.getZ() >> 4; z <= room.max.getZ() >> 4; z++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
				if (chunk == null) {
					return -1;
				}
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					if (room.contains(entry.getKey()) && Storage.isStorageBlock(entry.getValue())) {
						positions.add(entry.getKey().immutable());
					}
				}
			}
		}
		// Reading order of the scan does not matter for the planning; a stable order keeps the registrations stable.
		positions.sort(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
		int budget = config.maxRoomChests <= 0 ? Integer.MAX_VALUE : config.maxRoomChests - roomChestCount(guild);
		int added = 0;
		boolean attached = false;
		Set<BlockPos> seen = new HashSet<>();
		List<StorageChest> fresh = new ArrayList<>();
		for (BlockPos at : positions) {
			if (seen.contains(at) || GuildManager.at(level, at) != guild) {
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
					attached = true;
				}
				continue;
			}
			if (budget <= 0) {
				continue;
			}
			StorageChest chest = new StorageChest(at, room.filtered() ? StorageChest.Role.CONTENT : StorageChest.Role.CATEGORY, "");
			chest.auto = true;
			chest.room = room.id;
			// Added together at the end: the index (lookups above) is not rebuilt for every new chest.
			fresh.add(chest);
			budget--;
			added++;
		}
		guild.villagers.chests.addAll(fresh);
		if (attached) {
			Storage.changed(guild);
		}
		if (added > 0 || attached) {
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

	/**
	 * Turns a room into a filtered room (each automatic chest then receives the kinds of items it holds now; nothing is
	 * moved) or back into an automatic room (its automatic chests go back to the reserve and the room is planned again
	 * from scratch: the sorters then move the items to their categories). Manual chests keep their role.
	 *
	 * @return number of automatic chests converted
	 */
	static int setFiltered(ServerLevel level, Guild guild, ChestRoom room, boolean filtered) {
		if (room.filtered() == filtered) {
			return 0;
		}
		room.mode = filtered ? ChestRoom.Mode.FILTERED : null;
		int converted = 0;
		for (StorageChest chest : chests(guild, room)) {
			if (!chest.auto) {
				continue;
			}
			chest.role = filtered ? StorageChest.Role.CONTENT : StorageChest.Role.CATEGORY;
			chest.category = "";
			chest.filter = null;
			EMPTY_SINCE.remove(chest.pos);
			converted++;
		}
		UNPLACED.remove(guild.id + ":" + room.id);
		Storage.changed(guild);
		if (filtered) {
			RoomContents.refreshRoom(level, guild, room);
		} else {
			plan(level, guild, room, true, Scheduler.ticks());
		}
		for (StorageChest chest : chests(guild, room)) {
			StorageLabels.sync(level, guild, chest);
		}
		Tidier.invalidate(guild);
		GuildManager.markDirty();
		return converted;
	}

	private static String nextName(Guild guild) {
		Set<String> names = new HashSet<>();
		for (ChestRoom room : guild.villagers.rooms) {
			names.add(room.name);
		}
		return ZoneMath.nextName(Lang.strip(Lang.raw("villager.storage.room.base")), names);
	}

	// ================================================================ planning

	/**
	 * Plans the next room of the guild when due (called by the sorters): one room per call, the rooms in turn, so that
	 * each room is planned every {@code roomPlanSeconds} and a guild with many big rooms never reads them all in one tick.
	 */
	static void maybePlan(ServerLevel level, Guild guild, long now) {
		List<ChestRoom> rooms = guild.villagers.rooms;
		if (rooms.isEmpty()) {
			return;
		}
		Long last = LAST_PLAN.get(guild.id);
		long period = Math.max(1L, VillagerModule.config().storage.roomPlanSeconds * 20L / rooms.size());
		if (last != null && now - last < period) {
			return;
		}
		LAST_PLAN.put(guild.id, now);
		int next = Math.floorMod(NEXT_ROOM.getOrDefault(guild.id, 0), rooms.size());
		NEXT_ROOM.put(guild.id, next + 1);
		plan(level, guild, rooms.get(next), false, now);
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
		if (room.filtered()) {
			// Nothing to plan: each chest receives what it already holds.
			return 0;
		}
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
		Theme theme = theme(room, categories);
		RoomPlanner.Result result = RoomPlanner.planRoom(roomChests, StorageGroups.mode(guild), current, theme.order(),
				theme.fallback(), demand(level, guild, chests, containers, theme), idle, settings, fresh, theme.keep());
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
	 * Categories a room plans: an ordinary room = the top-level categories in config order and the fallback (kept);
	 * a room reserved to some categories = those (menu order), each kept, the fallback only when chosen.
	 */
	record Theme(List<String> order, String fallback, Set<String> keep) {
		/** Category of the room for an item: the first of its chain the room plans, else the fallback (or null). */
		@Nullable String placeOf(List<String> chain) {
			for (String link : chain) {
				for (String category : order) {
					if (category.equalsIgnoreCase(link)) {
						return category;
					}
				}
			}
			return fallback.isBlank() ? null : fallback;
		}
	}

	static Theme theme(ChestRoom room, StorageCategories categories) {
		if (!room.themed()) {
			return new Theme(categories.roots(), categories.fallback(), Set.of(categories.fallback()));
		}
		Set<String> chosen = new java.util.LinkedHashSet<>();
		boolean fallback = false;
		for (String name : room.categories) {
			String canonical = categories.canonical(name);
			if (canonical == null) {
				continue;
			}
			if (canonical.equals(categories.fallback())) {
				fallback = true;
			} else {
				chosen.add(canonical);
			}
		}
		if (chosen.isEmpty() && !fallback) {
			// Every chosen category was removed from villagers.json5: an ordinary room again.
			return new Theme(categories.roots(), categories.fallback(), Set.of(categories.fallback()));
		}
		List<String> order = new ArrayList<>();
		for (String name : categories.menuOrder()) {
			if (chosen.contains(name)) {
				order.add(name);
			}
		}
		Set<String> keep = new java.util.LinkedHashSet<>(order);
		if (fallback) {
			keep.add(categories.fallback());
		}
		return new Theme(order, fallback ? categories.fallback() : "", keep);
	}

	/** Display names of the categories a room is reserved to (empty for an ordinary room). */
	static List<String> themeNames(ChestRoom room) {
		if (!room.themed()) {
			return List.of();
		}
		Theme theme = theme(room, Storage.categories());
		List<String> names = new ArrayList<>(theme.order());
		if (!theme.fallback().isBlank()) {
			names.add(theme.fallback());
		}
		return names;
	}

	/**
	 * Stacks held per category of the room: the items of the room (except those already in a manual chest that takes
	 * them) and the items waiting in the deposits, each counted in the room's category of its chain
	 * ({@link Theme#placeOf}; items no category of a reserved room takes are not counted); each distinct stack counts
	 * its merged number of stacks.
	 */
	private static Map<String, Integer> demand(ServerLevel level, Guild guild, List<StorageChest> chests, List<Container> containers, Theme theme) {
		Object2LongOpenCustomHashMap<ItemStack> totals = new Object2LongOpenCustomHashMap<>(StorageSort.SAME_ITEM);
		for (int i = 0; i < chests.size(); i++) {
			StorageChest chest = chests.get(i);
			boolean manual = !chest.auto && !chest.isDeposit();
			Container container = containers.get(i);
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				// Items already in a manual chest that takes them stay there (a manual fallback chest keeps the items of no category).
				boolean stays = manual && (Storage.isFallback(chest) ? Storage.chain(stack).isEmpty() : Storage.gives(chest, stack));
				if (stack.isEmpty() || stays) {
					continue;
				}
				totals.addTo(stack.copyWithCount(1), stack.getCount());
			}
		}
		for (StorageChest chest : Storage.index(guild).deposits()) {
			if (!Storage.usable(level, guild, chest)) {
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
			String place = theme.placeOf(Storage.chain(stack));
			if (place != null) {
				demand.merge(place, (int) Math.min(Integer.MAX_VALUE, stacks), Integer::sum);
			}
		}
		return demand;
	}
}
