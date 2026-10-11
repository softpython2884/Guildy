package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneObjectiveTest {

	@Test
	void typesMatchTasks() {
		for (ZoneType type : ZoneType.values()) {
			assertEquals(type, ZoneType.forTask(type.task()));
			assertEquals(type, type.task().zoneType());
		}
		assertNull(ZoneType.forTask(Task.SORTER));
		assertNull(ZoneType.forTask(Task.EXPLORER));
		assertNull(ZoneType.forTask(Task.IDLE));
		assertFalse(ZoneType.MINE.usesRadius());
		assertTrue(ZoneType.FOREST.usesRadius());
		assertEquals(ZoneType.FARM, ZoneType.parse(" farm ", ZoneType.MINE));
		assertEquals(ZoneType.MINE, ZoneType.parse("nope", ZoneType.MINE));
	}

	@Test
	void eachTypeOffersItsObjectives() {
		assertEquals(List.of(ZoneObjective.MIXED, ZoneObjective.ORES, ZoneObjective.STONE), ZoneObjective.forType(ZoneType.MINE));
		List<ZoneObjective> woods = ZoneObjective.forType(ZoneType.FOREST);
		assertEquals(ZoneObjective.ANY_WOOD, woods.getFirst());
		assertEquals(11, woods.size());
		assertEquals(5, ZoneObjective.forType(ZoneType.FARM).size());
		assertEquals(List.of(ZoneObjective.PATROL), ZoneObjective.forType(ZoneType.PATROL));
		for (ZoneType type : ZoneType.values()) {
			assertEquals(type, ZoneObjective.defaultFor(type).type());
			assertTrue(ZoneObjective.forType(type).contains(ZoneObjective.defaultFor(type)));
		}
	}

	@Test
	void woodObjectivesMatchTheirLogsOnly() {
		assertTrue(ZoneObjective.OAK.acceptsLog("minecraft:oak_log"));
		assertFalse(ZoneObjective.OAK.acceptsLog("minecraft:dark_oak_log"));
		assertFalse(ZoneObjective.OAK.acceptsLog("minecraft:pale_oak_log"));
		assertTrue(ZoneObjective.DARK_OAK.acceptsLog("minecraft:dark_oak_log"));
		assertTrue(ZoneObjective.CHERRY.acceptsLog("minecraft:cherry_log"));
		assertTrue(ZoneObjective.POPLAR.acceptsLog("minecraft:poplar_log"));
		assertFalse(ZoneObjective.BIRCH.acceptsLog("minecraft:spruce_log"));
		assertTrue(ZoneObjective.ANY_WOOD.acceptsLog("minecraft:mangrove_log"));
		assertTrue(ZoneObjective.ANY_WOOD.acceptsLog("minecraft:crimson_stem"));
		// Other kinds of zones never cut trees.
		assertFalse(ZoneObjective.ANY_CROP.acceptsLog("minecraft:oak_log"));
		assertFalse(ZoneObjective.MIXED.acceptsLog("minecraft:oak_log"));
	}

	@Test
	void cropObjectivesMatchTheirCropsOnly() {
		assertTrue(ZoneObjective.WHEAT.acceptsCrop("minecraft:wheat"));
		assertFalse(ZoneObjective.WHEAT.acceptsCrop("minecraft:carrots"));
		assertTrue(ZoneObjective.CARROTS.acceptsCrop("minecraft:carrots"));
		assertTrue(ZoneObjective.POTATOES.acceptsCrop("minecraft:potatoes"));
		assertTrue(ZoneObjective.BEETROOTS.acceptsCrop("minecraft:beetroots"));
		assertTrue(ZoneObjective.ANY_CROP.acceptsCrop("minecraft:wheat"));
		assertTrue(ZoneObjective.ANY_CROP.acceptsCrop("minecraft:beetroots"));
		assertFalse(ZoneObjective.ANY_WOOD.acceptsCrop("minecraft:wheat"));
		// Melons, pumpkins and sugar cane: "any crop" fields only.
		assertTrue(ZoneObjective.ANY_CROP.acceptsExtras());
		assertFalse(ZoneObjective.WHEAT.acceptsExtras());
		assertFalse(ZoneObjective.ANY_WOOD.acceptsExtras());
	}

	@Test
	void invalidObjectivesFallBackToTheTypeDefault() {
		assertEquals(ZoneObjective.MIXED, ZoneObjective.validFor(ZoneType.MINE, ZoneObjective.OAK));
		assertEquals(ZoneObjective.ANY_WOOD, ZoneObjective.validFor(ZoneType.FOREST, null));
		assertEquals(ZoneObjective.CARROTS, ZoneObjective.validFor(ZoneType.FARM, ZoneObjective.CARROTS));
		assertEquals(ZoneObjective.PATROL, ZoneObjective.validFor(ZoneType.PATROL, ZoneObjective.STONE));
	}

	@Test
	void minerTablesPerObjective() {
		VillagerConfig config = new VillagerConfig();
		config.validate();
		assertEquals(config.miner.depthTables, config.miner.tables(ZoneObjective.MIXED));
		assertEquals(config.miner.oreTables, config.miner.tables(ZoneObjective.ORES));
		assertEquals(config.miner.stoneTables, config.miner.tables(ZoneObjective.STONE));
		assertEquals(config.miner.oreRollsPerTrip, config.miner.rolls(ZoneObjective.ORES));
		// Ores only: no stone; diamonds only deep down.
		for (VillagerConfig.DepthTable table : config.miner.oreTables) {
			for (VillagerConfig.LootEntry entry : table.entries) {
				assertFalse(entry.item.contains("cobble") || entry.item.equals("minecraft:gravel") || entry.item.equals("minecraft:tuff"), entry.item);
				if (entry.item.equals("minecraft:diamond")) {
					assertTrue(table.maxY <= -24, "diamonds at maxY " + table.maxY);
				}
			}
		}
		// Stone only: no ores.
		for (VillagerConfig.DepthTable table : config.miner.stoneTables) {
			for (VillagerConfig.LootEntry entry : table.entries) {
				assertFalse(entry.item.contains("raw_") || entry.item.contains("diamond") || entry.item.contains("coal"), entry.item);
			}
		}
		// Cobbled deepslate only in the deepest stone table.
		assertTrue(DepthLoot.tableFor(config.miner.stoneTables, -40).entries.stream().anyMatch(e -> e.item.equals("minecraft:cobbled_deepslate")));
		assertFalse(DepthLoot.tableFor(config.miner.stoneTables, 70).entries.stream().anyMatch(e -> e.item.equals("minecraft:cobbled_deepslate")));
	}

	@Test
	void zoneAndRouteSettingsAreBounded() {
		VillagerConfig config = new VillagerConfig();
		config.zones.maxZones = 500;
		config.zones.maxRadiusChunks = 50;
		config.zones.defaultRadiusChunks = 9;
		config.zones.maxZoneDistance = 1;
		config.routes.maxRoutes = -3;
		config.routes.maxWaypoints = 1;
		config.miner.oreTables = null;
		config.validate();
		assertEquals(27, config.zones.maxZones);
		assertEquals(5, config.zones.maxRadiusChunks);
		assertEquals(5, config.zones.defaultRadiusChunks);
		assertEquals(16, config.zones.maxZoneDistance);
		assertEquals(0, config.routes.maxRoutes);
		assertEquals(2, config.routes.maxWaypoints);
		assertFalse(config.miner.oreTables.isEmpty());
		VillagerConfig defaults = new VillagerConfig();
		defaults.validate();
		assertEquals(12, defaults.zones.maxZones);
		assertEquals(3, defaults.zones.maxRadiusChunks);
		assertEquals(1, defaults.zones.defaultRadiusChunks);
		assertEquals(192, defaults.zones.maxZoneDistance);
		assertEquals(48, defaults.zones.autoSearchRadius);
		assertEquals(6, defaults.routes.maxRoutes);
		assertEquals(16, defaults.routes.maxWaypoints);
		assertEquals(16, defaults.routes.leash);
	}
}
