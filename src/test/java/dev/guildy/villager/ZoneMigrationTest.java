package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ZoneMigrationTest {
	private static final Function<ZoneType, String> NAMES = Map.of(ZoneType.MINE, "Mine", ZoneType.FOREST, "Coupe de bois",
			ZoneType.FARM, "Champs", ZoneType.PATROL, "Patrouille")::get;

	private static VillagerRecord record(GuildVillagerData data, Task task, BlockPos workPos) {
		VillagerRecord record = new VillagerRecord(UUID.randomUUID(), task.id());
		record.task = task;
		record.workPos = workPos;
		data.members.put(record.uuid, record);
		return record;
	}

	@Test
	void workPositionsBecomeZonesOfTheTaskType() {
		GuildVillagerData data = new GuildVillagerData();
		BlockPos shared = new BlockPos(10, -20, 10);
		VillagerRecord minerA = record(data, Task.MINER, shared);
		VillagerRecord minerB = record(data, Task.MINER, shared);
		VillagerRecord lumberjack = record(data, Task.LUMBERJACK, shared);
		VillagerRecord guard = record(data, Task.GUARD, new BlockPos(0, 70, 0));
		VillagerRecord sorter = record(data, Task.SORTER, new BlockPos(5, 5, 5));
		VillagerRecord idle = new VillagerRecord(UUID.randomUUID(), "idle");
		data.members.put(idle.uuid, idle);
		data.sanitize(3, NAMES, "Parcours");

		int created = data.migrateLegacyWorkPositions(NAMES, 1234L);

		assertEquals(3, created, "same type + same position share one zone; tasks without zones get none");
		assertEquals(3, data.zones.size());
		WorkZone mine = data.zone(minerA.zoneId);
		assertNotNull(mine);
		assertEquals(mine.id, minerB.zoneId);
		assertEquals(ZoneType.MINE, mine.type);
		assertEquals(shared, mine.pos);
		assertEquals(0, mine.radius);
		assertEquals("Mine 1", mine.name);
		assertEquals(ZoneObjective.MIXED, mine.objective);
		assertEquals(1234L, mine.createdAt);
		assertFalse(mine.fanion, "migrated zones have no fanion");
		WorkZone forest = data.zone(lumberjack.zoneId);
		assertNotNull(forest);
		assertNotEquals(mine.id, forest.id);
		assertEquals(ZoneType.FOREST, forest.type);
		assertEquals("Coupe de bois 1", forest.name);
		assertEquals(ZoneObjective.ANY_WOOD, forest.objective);
		WorkZone patrol = data.zone(guard.zoneId);
		assertNotNull(patrol);
		assertEquals(ZoneType.PATROL, patrol.type);
		assertEquals(-1, sorter.zoneId);
		assertEquals(-1, idle.zoneId);
		for (VillagerRecord record : data.members.values()) {
			assertNull(record.workPos, "the legacy field is cleared");
		}
		assertEquals(4, data.nextZoneId);
		// Nothing left to migrate.
		assertEquals(0, data.migrateLegacyWorkPositions(NAMES, 5678L));
		assertEquals(3, data.zones.size());
	}

	@Test
	void migratedNamesAvoidExistingOnes() {
		GuildVillagerData data = new GuildVillagerData();
		data.zones.add(new WorkZone(7, ZoneType.MINE, "Mine 1", new BlockPos(1, 2, 3), 1, 0L));
		VillagerRecord miner = record(data, Task.MINER, new BlockPos(100, 12, 100));
		data.sanitize(3, NAMES, "Parcours");
		assertEquals(8, data.nextZoneId, "next id after the highest existing one");
		data.migrateLegacyWorkPositions(NAMES, 0L);
		WorkZone zone = data.zone(miner.zoneId);
		assertNotNull(zone);
		assertEquals(8, zone.id);
		assertEquals("Mine 2", zone.name);
	}

	@Test
	void sanitizeRepairsZonesRoutesAndReferences() {
		GuildVillagerData data = new GuildVillagerData();
		WorkZone first = new WorkZone(3, ZoneType.FOREST, "", new BlockPos(0, 64, 0), 9, 0L);
		first.objective = ZoneObjective.ORES;
		WorkZone duplicate = new WorkZone(3, ZoneType.FARM, "Champs", new BlockPos(16, 64, 0), 1, 0L);
		WorkZone broken = new WorkZone(4, ZoneType.MINE, "Mine", null, 1, 0L);
		data.zones.add(first);
		data.zones.add(duplicate);
		data.zones.add(broken);
		data.zones.add(null);
		data.routes.add(new PatrolRoute(2, "", List.of(new BlockPos(0, 64, 0), new BlockPos(8, 64, 0)), 0L));
		data.routes.add(new PatrolRoute(5, "Vide", List.of(), 0L));
		VillagerRecord lost = new VillagerRecord(UUID.randomUUID(), "lost");
		lost.zoneId = 42;
		lost.routeId = 5;
		data.members.put(lost.uuid, lost);

		data.sanitize(3, NAMES, "Parcours");

		assertEquals(2, data.zones.size(), "zones without position are dropped");
		assertNotEquals(first.id, duplicate.id, "duplicate ids are renumbered");
		assertEquals(3, first.radius, "radius clamped to the max");
		assertEquals(ZoneObjective.ANY_WOOD, first.objective, "objective of another type replaced by the default");
		assertEquals("Coupe de bois 1", first.name, "blank names repaired with the type base name");
		assertEquals(1, data.routes.size(), "routes without waypoint are dropped");
		assertEquals("Parcours 1", data.routes.getFirst().name);
		assertEquals(-1, lost.zoneId);
		assertEquals(-1, lost.routeId);
		assertEquals(6, data.nextRouteId);
	}
}
