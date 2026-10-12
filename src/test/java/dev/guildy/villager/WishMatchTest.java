package dev.guildy.villager;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishMatchTest {

	@Test
	void woodOfNaturalLogsOnly() {
		assertEquals("oak", WishMatch.wood("minecraft:oak_log"));
		assertEquals("dark_oak", WishMatch.wood("minecraft:dark_oak_log"));
		assertEquals("crimson", WishMatch.wood("minecraft:crimson_stem"));
		assertNull(WishMatch.wood("minecraft:stripped_oak_log"));
		assertNull(WishMatch.wood("minecraft:oak_planks"));
	}

	@Test
	void logsGiveTheirWoodFamily() {
		assertTrue(WishMatch.fromLogs("minecraft:oak_log", "minecraft:oak_log"));
		assertTrue(WishMatch.fromLogs("minecraft:oak_log", "minecraft:oak_planks"));
		assertTrue(WishMatch.fromLogs("minecraft:oak_log", "minecraft:stripped_oak_wood"));
		assertTrue(WishMatch.fromLogs("minecraft:birch_log", "minecraft:birch_wood"));
		// No mix-up between oak and dark oak / pale oak.
		assertFalse(WishMatch.fromLogs("minecraft:oak_log", "minecraft:dark_oak_log"));
		assertFalse(WishMatch.fromLogs("minecraft:dark_oak_log", "minecraft:oak_planks"));
		assertFalse(WishMatch.fromLogs("minecraft:pale_oak_log", "minecraft:oak_log"));
		assertFalse(WishMatch.fromLogs("minecraft:oak_log", "minecraft:diorite"));
		assertFalse(WishMatch.fromLogs("minecraft:oak_log", "othermod:oak_log"));
	}

	@Test
	void leavesDropSaplingsSticksAndApples() {
		assertEquals("minecraft:oak_sapling", WishMatch.saplingOf("minecraft:oak_leaves"));
		assertEquals("minecraft:poplar_sapling", WishMatch.saplingOf("minecraft:red_poplar_leaves"));
		assertEquals("minecraft:azalea", WishMatch.saplingOf("minecraft:azalea_leaves"));
		assertEquals("minecraft:flowering_azalea", WishMatch.saplingOf("minecraft:flowering_azalea_leaves"));
		assertEquals("", WishMatch.saplingOf("minecraft:mangrove_leaves"));
		assertTrue(WishMatch.fromLeaves("minecraft:oak_leaves", "minecraft:apple"));
		assertTrue(WishMatch.fromLeaves("minecraft:dark_oak_leaves", "minecraft:apple"));
		assertFalse(WishMatch.fromLeaves("minecraft:birch_leaves", "minecraft:apple"));
		assertTrue(WishMatch.fromLeaves("minecraft:mangrove_leaves", "minecraft:stick"));
		assertTrue(WishMatch.fromLeaves("minecraft:cherry_leaves", "minecraft:cherry_sapling"));
		assertFalse(WishMatch.fromLeaves("minecraft:cherry_leaves", "minecraft:oak_sapling"));
		assertFalse(WishMatch.fromLeaves("minecraft:oak_log", "minecraft:stick"));
	}

	@Test
	void treesWhoseLeavesMayGiveTheItem() {
		assertTrue(WishMatch.leavesMayGive("minecraft:oak_log", "minecraft:apple"));
		assertTrue(WishMatch.leavesMayGive("minecraft:dark_oak_log", "minecraft:apple"));
		assertFalse(WishMatch.leavesMayGive("minecraft:spruce_log", "minecraft:apple"));
		assertTrue(WishMatch.leavesMayGive("minecraft:spruce_log", "minecraft:spruce_sapling"));
		assertTrue(WishMatch.leavesMayGive("minecraft:poplar_log", "minecraft:poplar_sapling"));
		// Azalea trees grow on oak logs.
		assertTrue(WishMatch.leavesMayGive("minecraft:oak_log", "minecraft:flowering_azalea"));
		assertTrue(WishMatch.leavesMayGive("minecraft:birch_log", "minecraft:stick"));
		assertFalse(WishMatch.leavesMayGive("minecraft:birch_log", "minecraft:oak_log"));
	}

	@Test
	void cropsGiveTheirHarvestAndSeeds() {
		assertTrue(WishMatch.fromCrop("minecraft:wheat", "minecraft:wheat_seeds", "minecraft:wheat"));
		assertTrue(WishMatch.fromCrop("minecraft:wheat", "minecraft:wheat_seeds", "minecraft:wheat_seeds"));
		assertTrue(WishMatch.fromCrop("minecraft:beetroots", "minecraft:beetroot_seeds", "minecraft:beetroot"));
		assertTrue(WishMatch.fromCrop("minecraft:potatoes", "minecraft:potato", "minecraft:poisonous_potato"));
		assertTrue(WishMatch.fromCrop("minecraft:carrots", "minecraft:carrot", "minecraft:carrot"));
		assertTrue(WishMatch.fromCrop("minecraft:melon", "minecraft:melon", "minecraft:melon_slice"));
		assertTrue(WishMatch.fromCrop("minecraft:pumpkin", "minecraft:pumpkin", "minecraft:pumpkin"));
		assertTrue(WishMatch.fromCrop("minecraft:sugar_cane", "minecraft:sugar_cane", "minecraft:sugar_cane"));
		assertFalse(WishMatch.fromCrop("minecraft:wheat", "minecraft:wheat_seeds", "minecraft:carrot"));
		assertFalse(WishMatch.fromCrop("minecraft:carrots", "minecraft:carrot", "minecraft:potato"));
		for (String item : List.of("minecraft:wheat", "minecraft:carrot", "minecraft:beetroot_seeds", "minecraft:melon_slice")) {
			assertTrue(WishMatch.FARM_ITEMS.contains(item), item);
		}
	}

	@Test
	void editingKeepsTheListShortAndWithoutDuplicates() {
		List<String> list = new ArrayList<>();
		assertTrue(WishMatch.add(list, "minecraft:diorite", 3));
		assertFalse(WishMatch.add(list, "minecraft:diorite", 3), "no duplicate");
		assertFalse(WishMatch.add(list, " ", 3), "no blank id");
		assertTrue(WishMatch.add(list, "minecraft:granite", 3));
		assertTrue(WishMatch.add(list, "minecraft:andesite", 3));
		assertFalse(WishMatch.add(list, "minecraft:tuff", 3), "full");
		assertEquals(List.of("minecraft:diorite", "minecraft:granite", "minecraft:andesite"), list);
		assertTrue(WishMatch.remove(list, "minecraft:granite"));
		assertFalse(WishMatch.remove(list, "minecraft:granite"));
		assertTrue(WishMatch.add(list, "minecraft:tuff", 3));
		assertEquals(List.of("minecraft:diorite", "minecraft:andesite", "minecraft:tuff"), list);
	}

	@Test
	void summaryMergesOwnEntriesFirst() {
		List<String> own = List.of("minecraft:diorite", "minecraft:granite");
		List<String> zone = List.of("minecraft:granite", "minecraft:coal", "minecraft:raw_iron");
		assertEquals(List.of("minecraft:diorite", "minecraft:granite", "minecraft:coal", "minecraft:raw_iron"), WishMatch.merged(own, zone));
		assertEquals(List.of("minecraft:diorite", "minecraft:granite", "minecraft:coal"), WishMatch.head(own, zone, 3));
		assertEquals(List.of("minecraft:coal"), WishMatch.head(List.of(), List.of("minecraft:coal"), 3));
	}

	@Test
	void configMigratesTheOldTreeSizeLimitOnly() {
		VillagerConfig old = Json5.fromString("{ \"configVersion\": 1, \"lumberjack\": { \"maxLogsPerTree\": 80 } }", VillagerConfig.class);
		old.validate();
		assertEquals(160, old.lumberjack.maxLogsPerTree);
		assertEquals(VillagerConfig.CURRENT_VERSION, old.configVersion);

		VillagerConfig custom = Json5.fromString("{ \"configVersion\": 1, \"lumberjack\": { \"maxLogsPerTree\": 100 } }", VillagerConfig.class);
		custom.validate();
		assertEquals(100, custom.lumberjack.maxLogsPerTree);

		VillagerConfig migrated = Json5.fromString("{ \"configVersion\": 5, \"lumberjack\": { \"maxLogsPerTree\": 80 } }", VillagerConfig.class);
		migrated.validate();
		assertEquals(80, migrated.lumberjack.maxLogsPerTree, "an admin's choice made after the migration is kept");

		VillagerConfig fresh = new VillagerConfig();
		fresh.validate();
		assertEquals(160, fresh.lumberjack.maxLogsPerTree);
		assertEquals(16, fresh.zones.wishlistMargin);

		VillagerConfig bounded = Json5.fromString("{ \"zones\": { \"wishlistMargin\": 500 } }", VillagerConfig.class);
		bounded.validate();
		assertEquals(64, bounded.zones.wishlistMargin);
	}
}
