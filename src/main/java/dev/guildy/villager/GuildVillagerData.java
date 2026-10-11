package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
	/** Assigned storage chests (deposit + sorted categories). */
	public List<StorageChest> chests = new ArrayList<>();
	/** Villagers that left the guild while their chunk was unloaded: despawned when they load. */
	public Set<UUID> leaving = new LinkedHashSet<>();
	/** Work zones (fanions). */
	public List<WorkZone> zones = new ArrayList<>();
	/** Guard patrol routes. */
	public List<PatrolRoute> routes = new ArrayList<>();
	/** Next zone / route ids (never reused, so a stale reference never points to a newer zone). */
	public int nextZoneId = 1;
	public int nextRouteId = 1;
	/** Fanions of deleted / moved zones whose chunk was not loaded: removed (without drop) once loaded. */
	public List<StaleFanion> staleFanions = new ArrayList<>();

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
		for (StorageChest chest : chests) {
			if (pos.equals(chest.pos)) {
				return chest;
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
		if (chests == null) {
			chests = new ArrayList<>();
		}
		chests.removeIf(chest -> chest == null || chest.pos == null);
		for (StorageChest chest : chests) {
			chest.sanitize();
		}
		if (leaving == null) {
			leaving = new LinkedHashSet<>();
		}
		leaving.remove(null);

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
