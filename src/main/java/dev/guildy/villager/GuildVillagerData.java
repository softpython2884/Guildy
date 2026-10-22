package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Per-guild villagers, storage setup, work zones and patrol routes (persisted inside the guild).
 */
public class GuildVillagerData {
	/** Accepted guild villagers by entity UUID. */
	public Map<UUID, VillagerRecord> members = new LinkedHashMap<>();
	/**
	 * Assigned storage chests (deposit + sorted categories). A {@link ChestList}: its modification counter tells the
	 * {@link StorageIndex} when to rebuild (Gson reads a plain list, converted at the first use).
	 */
	public List<StorageChest> chests = new ChestList();
	/** Villagers that left the guild while their chunk was unloaded: despawned when they load. */
	public Set<UUID> leaving = new LinkedHashSet<>();
	/** Work zones (fanions). */
	public List<WorkZone> zones = new ArrayList<>();
	/** Guard patrol routes. */
	public List<PatrolRoute> routes = new ArrayList<>();
	/** Next zone / route ids (never reused, so a stale reference never points to a newer zone). */
	public int nextZoneId = 1;
	public int nextRouteId = 1;
	/** Potion stock the alchemists keep (product of alchemy.json5 -> bottles); absent = the default of alchemy.json5. */
	public Map<String, Integer> potionTargets = new LinkedHashMap<>();
	/** Fanions of deleted / moved zones whose chunk was not loaded: removed (without drop) once loaded. */
	public List<StaleFanion> staleFanions = new ArrayList<>();
	/** Chest rooms (automatic chests). */
	public List<ChestRoom> rooms = new ArrayList<>();
	public int nextRoomId = 1;
	/** Storage order of the chest groups: "columns" or "rows" ("" = the default of villagers.json5). */
	public String storageOrder = "";
	/** Layout inside each chest: "columns" or "rows" ("" = the default of villagers.json5, storage.defaultChestLayout). */
	public String chestLayout = "";
	/** Index of the registered containers (not saved), see {@link StorageIndex}. */
	transient @Nullable StorageIndex storageIndex;
	/** Bumped when a registered container changes role, category, filter, room or position ({@link Storage#changed}). */
	transient int storageVersion;
	/** What the chests of the filtered rooms hold (not saved), see {@link RoomContents}. */
	transient @Nullable ContentIndex contentIndex;

	/** The chest list, counting its modifications. */
	public static final class ChestList extends ArrayList<StorageChest> {
		public ChestList() {
		}

		public ChestList(Collection<StorageChest> chests) {
			super(chests);
		}

		/** Structural modifications so far (additions, removals...). */
		int modifications() {
			return modCount;
		}
	}

	/** {@link #chests} as a {@link ChestList} (a plain list read by Gson or set by hand is converted once). */
	ChestList chestList() {
		if (chests instanceof ChestList list) {
			return list;
		}
		ChestList list = new ChestList(chests == null ? List.of() : chests);
		chests = list;
		return list;
	}

	/** A registered container changed (role, category, filter, room, position): the storage index is rebuilt. */
	public void storageChanged() {
		storageVersion++;
	}

	/**
	 * Contents of the filtered-room chests (runtime only, created at the first use; forgotten when the identity rules of
	 * villagers.json5 change, the chests are then read again).
	 */
	ContentIndex contents() {
		ContentIndex index = contentIndex;
		if (index == null) {
			index = new ContentIndex();
			contentIndex = index;
		}
		index.useRules(RoomContents.rules());
		return index;
	}

	/** A fanion block to remove later; only removed if the banner there still carries {@link #name}. */
	public static class StaleFanion {
		public BlockPos pos;
		public String name = "";

		public StaleFanion() {
		}

		public StaleFanion(BlockPos pos, String name) {
			this.pos = pos;
			this.name = name;
		}
	}

	public @Nullable VillagerRecord get(UUID uuid) {
		return members.get(uuid);
	}

	public @Nullable StorageChest chestAt(BlockPos pos) {
		return StorageIndex.of(this).at(pos);
	}

	public @Nullable ChestRoom room(int id) {
		if (id <= 0) {
			return null;
		}
		for (ChestRoom room : rooms) {
			if (room.id == id) {
				return room;
			}
		}
		return null;
	}

	public @Nullable WorkZone zone(int id) {
		if (id < 0) {
			return null;
		}
		for (WorkZone zone : zones) {
			if (zone.id == id) {
				return zone;
			}
		}
		return null;
	}

	/** Zone whose planted fanion stands at {@code pos} (zones without fanion never match). */
	public @Nullable WorkZone fanionZoneAt(BlockPos pos) {
		for (WorkZone zone : zones) {
			if (zone.fanion && pos.equals(zone.pos)) {
				return zone;
			}
		}
		return null;
	}

	public @Nullable PatrolRoute route(int id) {
		if (id < 0) {
			return null;
		}
		for (PatrolRoute route : routes) {
			if (route.id == id) {
				return route;
			}
		}
		return null;
	}

	public Set<String> zoneNames() {
		Set<String> names = new HashSet<>();
		for (WorkZone zone : zones) {
			names.add(zone.name);
		}
		return names;
	}

	public Set<String> routeNames() {
		Set<String> names = new HashSet<>();
		for (PatrolRoute route : routes) {
			names.add(route.name);
		}
		return names;
	}

	/**
	 * Fixes nulls after deserialization, unique ids, names, and drops references to missing zones / routes.
	 *
	 * @param zoneBaseNames base of a default zone name ("Mine" → "Mine 1"), {@code routeBaseName} the same for routes
	 */
	public void sanitize(int maxZoneRadius, Function<ZoneType, String> zoneBaseNames, String routeBaseName) {
		if (members == null) {
			members = new LinkedHashMap<>();
		}
		members.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
		for (Map.Entry<UUID, VillagerRecord> entry : members.entrySet()) {
			entry.getValue().sanitize(entry.getKey());
		}
		if (potionTargets == null) {
			potionTargets = new LinkedHashMap<>();
		}
		potionTargets.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0);
		chestList();
		chests.removeIf(chest -> chest == null || chest.pos == null);
		for (StorageChest chest : chests) {
			chest.sanitize();
		}
		if (leaving == null) {
			leaving = new LinkedHashSet<>();
		}
		leaving.remove(null);

		if (rooms == null) {
			rooms = new ArrayList<>();
		}
		rooms.removeIf(room -> room == null || room.min == null || room.max == null);
		int maxRoom = 0;
		for (ChestRoom room : rooms) {
			maxRoom = Math.max(maxRoom, room.id);
		}
		nextRoomId = Math.max(Math.max(1, nextRoomId), maxRoom + 1);
		Set<Integer> roomIds = new HashSet<>();
		for (ChestRoom room : rooms) {
			if (room.id <= 0 || !roomIds.add(room.id)) {
				room.id = nextRoomId++;
				roomIds.add(room.id);
			}
			room.sanitize();
		}
		// Room gone (edited data): its free and filtered chests leave the storage, the others stay as manual chests of their
		// category.
		chests.removeIf(chest -> chest.room > 0 && room(chest.room) == null && (chest.isFree() || chest.isContent()));
		for (StorageChest chest : chests) {
			if (chest.room > 0 && room(chest.room) == null) {
				chest.room = 0;
				chest.auto = false;
			}
		}
		// The automatic chests follow the mode of their room: filtered by their contents, or planned categories.
		chests.removeIf(chest -> chest.isContent() && room(chest.room) == null);
		for (StorageChest chest : chests) {
			ChestRoom room = room(chest.room);
			if (room == null || !chest.auto) {
				continue;
			}
			if (room.filtered() && !chest.isContent()) {
				chest.role = StorageChest.Role.CONTENT;
				chest.category = "";
			} else if (!room.filtered() && chest.isContent()) {
				chest.role = StorageChest.Role.CATEGORY;
				chest.category = "";
			}
		}
		if (storageOrder == null) {
			storageOrder = "";
		}
		if (chestLayout == null) {
			chestLayout = "";
		}

		if (zones == null) {
			zones = new ArrayList<>();
		}
		zones.removeIf(zone -> zone == null || zone.pos == null);
		int maxZone = 0;
		for (WorkZone zone : zones) {
			maxZone = Math.max(maxZone, zone.id);
		}
		nextZoneId = Math.max(Math.max(1, nextZoneId), maxZone + 1);
		Set<Integer> zoneIds = new HashSet<>();
		for (WorkZone zone : zones) {
			if (zone.id <= 0 || !zoneIds.add(zone.id)) {
				zone.id = nextZoneId++;
				zoneIds.add(zone.id);
			}
			zone.sanitize(maxZoneRadius);
		}
		for (WorkZone zone : zones) {
			if (zone.name.isBlank()) {
				zone.name = ZoneMath.nextName(zoneBaseNames.apply(zone.type), zoneNames());
			}
		}

		if (routes == null) {
			routes = new ArrayList<>();
		}
		routes.removeIf(route -> route == null);
		int maxRoute = 0;
		for (PatrolRoute route : routes) {
			maxRoute = Math.max(maxRoute, route.id);
		}
		nextRouteId = Math.max(Math.max(1, nextRouteId), maxRoute + 1);
		Set<Integer> routeIds = new HashSet<>();
		for (PatrolRoute route : routes) {
			if (route.id <= 0 || !routeIds.add(route.id)) {
				route.id = nextRouteId++;
				routeIds.add(route.id);
			}
			route.sanitize();
		}
		routes.removeIf(route -> route.points.isEmpty());
		for (PatrolRoute route : routes) {
			if (route.name.isBlank()) {
				route.name = ZoneMath.nextName(routeBaseName, routeNames());
			}
		}

		if (staleFanions == null) {
			staleFanions = new ArrayList<>();
		}
		staleFanions.removeIf(stale -> stale == null || stale.pos == null);
		for (StaleFanion stale : staleFanions) {
			if (stale.name == null) {
				stale.name = "";
			}
		}

		for (VillagerRecord record : members.values()) {
			if (zone(record.zoneId) == null) {
				record.zoneId = -1;
			}
			if (route(record.routeId) == null) {
				record.routeId = -1;
			}
		}
	}

	/**
	 * Legacy data: each per-villager work position becomes a zone of the type matching the villager's task (radius 0;
	 * villagers sharing the same type and position share one zone), assigned to that villager. Work positions of
	 * tasks without zones are dropped.
	 *
	 * @param baseNames base of the default zone names ("Mine" → "Mine 1")
	 * @return number of zones created
	 */
	public int migrateLegacyWorkPositions(Function<ZoneType, String> baseNames, long now) {
		int created = 0;
		for (VillagerRecord record : members.values()) {
			BlockPos pos = record.workPos;
			if (pos == null) {
				continue;
			}
			record.workPos = null;
			ZoneType type = ZoneType.forTask(record.task);
			if (type == null) {
				continue;
			}
			WorkZone zone = null;
			for (WorkZone existing : zones) {
				if (existing.type == type && existing.pos.equals(pos)) {
					zone = existing;
					break;
				}
			}
			if (zone == null) {
				zone = new WorkZone(nextZoneId++, type, ZoneMath.nextName(baseNames.apply(type), zoneNames()), pos.immutable(), 0, now);
				zones.add(zone);
				created++;
			}
			record.zoneId = zone.id;
			record.routeId = -1;
		}
		return created;
	}
}
