package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepthLootTest {
	private static List<VillagerConfig.DepthTable> tables() {
		List<VillagerConfig.DepthTable> tables = new ArrayList<>();
		tables.add(new VillagerConfig.DepthTable(64, List.of(new VillagerConfig.LootEntry("minecraft:coal", 1, 2, 1))));
		tables.add(new VillagerConfig.DepthTable(-24, List.of(new VillagerConfig.LootEntry("minecraft:diamond", 1, 1, 1))));
		tables.add(new VillagerConfig.DepthTable(16, List.of(new VillagerConfig.LootEntry("minecraft:raw_iron", 1, 3, 1))));
		return tables;
	}

	@Test
	void tableSelectionByDepth() {
		List<VillagerConfig.DepthTable> tables = tables();
		assertEquals(-24, DepthLoot.tableFor(tables, -60).maxY);
		assertEquals(-24, DepthLoot.tableFor(tables, -24).maxY);
		assertEquals(16, DepthLoot.tableFor(tables, -23).maxY);
		assertEquals(16, DepthLoot.tableFor(tables, 16).maxY);
		assertEquals(64, DepthLoot.tableFor(tables, 40).maxY);
		// Above every table: the highest one.
		assertEquals(64, DepthLoot.tableFor(tables, 200).maxY);
		assertNull(DepthLoot.tableFor(List.of(), 0));
	}

	@Test
	void rollsAreDeterministicWithSeed() {
		VillagerConfig.DepthTable table = DepthLoot.tableFor(new VillagerConfig().miner.depthTables, -40);
		List<DepthLoot.Drop> first = DepthLoot.roll(table, 5, new Random(42));
		List<DepthLoot.Drop> second = DepthLoot.roll(table, 5, new Random(42));
		assertEquals(first, second);
		assertFalse(first.isEmpty());
	}

	@Test
	void countsStayWithinBoundsAndItemsBelongToTable() {
		VillagerConfig.DepthTable table = DepthLoot.tableFor(new VillagerConfig().miner.depthTables, 10);
		Set<String> allowed = new HashSet<>();
		int maxPerRoll = 0;
		for (VillagerConfig.LootEntry entry : table.entries) {
			allowed.add(entry.item);
			maxPerRoll = Math.max(maxPerRoll, entry.max);
		}
		Random random = new Random(7);
		for (int i = 0; i < 200; i++) {
			int total = 0;
			for (DepthLoot.Drop drop : DepthLoot.roll(table, 5, random)) {
				assertTrue(allowed.contains(drop.item()), drop.item());
				assertTrue(drop.count() >= 1);
				total += drop.count();
			}
			assertTrue(total >= 5 && total <= 5 * maxPerRoll, "total " + total);
		}
	}

	@Test
	void diamondsOnlyDeep() {
		List<VillagerConfig.DepthTable> defaults = new VillagerConfig().miner.depthTables;
		Random random = new Random(1234);
		for (int y : new int[]{100, 60, 10}) {
			VillagerConfig.DepthTable table = DepthLoot.tableFor(defaults, y);
			for (int i = 0; i < 500; i++) {
				for (DepthLoot.Drop drop : DepthLoot.roll(table, 5, random)) {
					assertFalse(drop.item().equals("minecraft:diamond"), "diamond at y=" + y);
				}
			}
		}
		VillagerConfig.DepthTable deep = DepthLoot.tableFor(defaults, -50);
		assertNotNull(deep);
		int diamonds = 0;
		int rolls = 2000;
		for (int i = 0; i < rolls; i++) {
			for (DepthLoot.Drop drop : DepthLoot.roll(deep, 1, random)) {
				if (drop.item().equals("minecraft:diamond")) {
					diamonds++;
				}
			}
		}
		assertTrue(diamonds > 0, "diamonds should drop deep");
		assertTrue(diamonds < rolls / 10, "diamonds should stay rare: " + diamonds);
	}

	@Test
	void sameItemIsMerged() {
		VillagerConfig.DepthTable table = new VillagerConfig.DepthTable(0, List.of(new VillagerConfig.LootEntry("minecraft:coal", 2, 2, 1)));
		List<DepthLoot.Drop> drops = DepthLoot.roll(table, 4, new Random(3));
		assertEquals(List.of(new DepthLoot.Drop("minecraft:coal", 8)), drops);
	}

	@Test
	void validateSortsAndCleansTables() {
		VillagerConfig config = new VillagerConfig();
		config.miner.depthTables = new ArrayList<>(tables());
		config.miner.depthTables.add(new VillagerConfig.DepthTable(30, List.of(new VillagerConfig.LootEntry("minecraft:stone", 5, 1, 0))));
		config.validate();
		List<VillagerConfig.DepthTable> sorted = config.miner.depthTables;
		assertEquals(3, sorted.size(), "table with only zero-weight entries is dropped");
		assertEquals(-24, sorted.get(0).maxY);
		assertEquals(16, sorted.get(1).maxY);
		assertEquals(64, sorted.get(2).maxY);
	}
}
