package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlantingRulesTest {
	private static final int SPACING = 4;
	private static final int PAD = SPACING;

	private static boolean[][] grid() {
		return new boolean[16 + 2 * PAD][16 + 2 * PAD];
	}

	private static void occupy(boolean[][] grid, int x, int z) {
		grid[x + PAD][z + PAD] = true;
	}

	private static void assertSpaced(List<int[]> spots, int size, int spacing) {
		for (int i = 0; i < spots.size(); i++) {
			for (int j = i + 1; j < spots.size(); j++) {
				int[] a = spots.get(i);
				int[] b = spots.get(j);
				int gapX = Math.max(0, Math.max(a[0], b[0]) - Math.min(a[0], b[0]) - (size - 1));
				int gapZ = Math.max(0, Math.max(a[1], b[1]) - Math.min(a[1], b[1]) - (size - 1));
				assertTrue(Math.max(gapX, gapZ) > spacing, "spots too close: " + a[0] + "," + a[1] + " / " + b[0] + "," + b[1]);
			}
		}
	}

	// ------------------------------------------------------------------ spots

	@Test
	void spotsKeepTheSpacingBetweenNewSaplings() {
		List<int[]> spots = PlantingRules.pickSpots(grid(), PAD, SPACING, 1, 0, 64, 64, 0, 0, (x, z) -> true);
		// Every 5 blocks (4 free blocks between two trunks): 4 × 4 in a chunk.
		assertEquals(16, spots.size());
		assertSpaced(spots, 1, SPACING);
		assertEquals(0, spots.getFirst()[0]);
		assertEquals(0, spots.getFirst()[1], "nearest to the reference first");
	}

	@Test
	void noSpotNearAnExistingTreeEvenInTheNextChunk() {
		boolean[][] grid = grid();
		occupy(grid, 8, 8);
		// A log just over the chunk border (west).
		occupy(grid, -2, 2);
		List<int[]> spots = PlantingRules.pickSpots(grid, PAD, SPACING, 1, 0, 64, 64, 8, 8, (x, z) -> true);
		assertFalse(spots.isEmpty());
		for (int[] spot : spots) {
			assertTrue(PlantingRules.distance(spot[0], spot[1], 8, 8) > SPACING, "too close to the tree of the chunk");
			assertTrue(PlantingRules.distance(spot[0], spot[1], -2, 2) > SPACING, "too close to the tree of the next chunk");
		}
		assertSpaced(spots, 1, SPACING);
	}

	@Test
	void densityCapCountsExistingTrees() {
		assertEquals(2, PlantingRules.pickSpots(grid(), PAD, SPACING, 1, 6, 8, 64, 0, 0, (x, z) -> true).size());
		assertTrue(PlantingRules.pickSpots(grid(), PAD, SPACING, 1, 8, 8, 64, 0, 0, (x, z) -> true).isEmpty());
		assertTrue(PlantingRules.pickSpots(grid(), PAD, SPACING, 1, 9, 8, 64, 0, 0, (x, z) -> true).isEmpty());
		assertEquals(3, PlantingRules.pickSpots(grid(), PAD, SPACING, 1, 0, 8, 3, 0, 0, (x, z) -> true).size(), "round limit");
	}

	@Test
	void onlyOpenColumnsArePicked() {
		// Natural ground on the east half only (x >= 8).
		List<int[]> spots = PlantingRules.pickSpots(grid(), PAD, SPACING, 1, 0, 64, 64, 0, 0, (x, z) -> x >= 8);
		assertFalse(spots.isEmpty());
		for (int[] spot : spots) {
			assertTrue(spot[0] >= 8);
		}
		assertSpaced(spots, 1, SPACING);
		assertTrue(PlantingRules.pickSpots(grid(), PAD, SPACING, 1, 0, 64, 64, 0, 0, (x, z) -> false).isEmpty());
	}

	@Test
	void squaresForTwoByTwoTrees() {
		boolean[][] grid = grid();
		// The column (1, 1) is closed: no square may cover it.
		List<int[]> spots = PlantingRules.pickSpots(grid, PAD, SPACING, 2, 0, 64, 64, 0, 0, (x, z) -> !(x == 1 && z == 1));
		assertFalse(spots.isEmpty());
		for (int[] spot : spots) {
			assertTrue(spot[0] <= 14 && spot[1] <= 14, "the square stays in the chunk");
			assertFalse(spot[0] <= 1 && spot[0] + 1 >= 1 && spot[1] <= 1 && spot[1] + 1 >= 1, "square over the closed column");
		}
		assertSpaced(spots, 2, SPACING);
	}

	@Test
	void groundRoomLightBuildingsAndOtherGuilds() {
		PlantingRules.Column good = new PlantingRules.Column(PlantingRules.Ground.TILLABLE, true, 6, 15, true, false);
		assertTrue(PlantingRules.saplingSpot(good, 6));
		assertTrue(PlantingRules.saplingSpot(new PlantingRules.Column(PlantingRules.Ground.SOIL, true, 6, 15, true, false), 6), "podzol, moss, mud");
		assertFalse(PlantingRules.saplingSpot(new PlantingRules.Column(PlantingRules.Ground.FARMLAND, true, 6, 15, true, false), 6), "farmland");
		assertFalse(PlantingRules.saplingSpot(new PlantingRules.Column(PlantingRules.Ground.PATH, true, 6, 15, true, false), 6), "path");
		assertFalse(PlantingRules.saplingSpot(new PlantingRules.Column(PlantingRules.Ground.OTHER, true, 6, 15, true, false), 6), "stone, sand");
		assertFalse(PlantingRules.saplingSpot(new PlantingRules.Column(PlantingRules.Ground.TILLABLE, false, 6, 15, true, false), 6), "spot taken");
		assertFalse(PlantingRules.saplingSpot(new PlantingRules.Column(PlantingRules.Ground.TILLABLE, true, 5, 15, true, false), 6), "no room");
		assertFalse(PlantingRules.saplingSpot(new PlantingRules.Column(PlantingRules.Ground.TILLABLE, true, 6, 8, true, false), 6), "too dark");
		assertTrue(PlantingRules.saplingSpot(new PlantingRules.Column(PlantingRules.Ground.TILLABLE, true, 6, 9, true, false), 6));
		assertFalse(PlantingRules.saplingSpot(new PlantingRules.Column(PlantingRules.Ground.TILLABLE, true, 6, 15, false, false), 6), "building");
		assertFalse(PlantingRules.saplingSpot(new PlantingRules.Column(PlantingRules.Ground.TILLABLE, true, 6, 15, true, true), 6), "other guild");
	}

	@Test
	void tillingNeedsGrassOrDirtWaterAndAFieldAround() {
		assertTrue(PlantingRules.tillable(PlantingRules.Ground.TILLABLE, true, true, true));
		assertFalse(PlantingRules.tillable(PlantingRules.Ground.PATH, true, true, true), "never a path");
		assertFalse(PlantingRules.tillable(PlantingRules.Ground.SOIL, true, true, true), "podzol / mud are not tilled");
		assertFalse(PlantingRules.tillable(PlantingRules.Ground.FARMLAND, true, true, true));
		assertFalse(PlantingRules.tillable(PlantingRules.Ground.TILLABLE, false, true, true), "something on top");
		assertFalse(PlantingRules.tillable(PlantingRules.Ground.TILLABLE, true, false, true), "no water within 4 blocks");
		assertFalse(PlantingRules.tillable(PlantingRules.Ground.TILLABLE, true, true, false), "next to a player's block");
	}

	// ------------------------------------------------------------------ species and seeds

	@Test
	void saplingsOfLogsAndWantedItems() {
		assertEquals("minecraft:oak_sapling", PlantingRules.saplingOfLog("minecraft:oak_log"));
		assertEquals("minecraft:mangrove_propagule", PlantingRules.saplingOfLog("minecraft:mangrove_log"));
		assertEquals("minecraft:pale_oak_sapling", PlantingRules.saplingOfLog("minecraft:pale_oak_log"));
		assertEquals("", PlantingRules.saplingOfLog("minecraft:crimson_stem"));
		assertEquals("", PlantingRules.saplingOfLog("minecraft:stripped_oak_log"));
		assertEquals("minecraft:cherry_sapling", PlantingRules.saplingOfItem("minecraft:stripped_cherry_wood"));
		assertEquals("minecraft:birch_sapling", PlantingRules.saplingOfItem("minecraft:birch_planks"));
		assertEquals("minecraft:azalea", PlantingRules.saplingOfItem("minecraft:azalea"));
		assertEquals("", PlantingRules.saplingOfItem("minecraft:stick"));
		assertEquals("", PlantingRules.saplingOfItem("minecraft:apple"));
		assertTrue(PlantingRules.needsSquare("minecraft:dark_oak_sapling"));
		assertTrue(PlantingRules.needsSquare("minecraft:pale_oak_sapling"));
		assertFalse(PlantingRules.needsSquare("minecraft:spruce_sapling"));
	}

	@Test
	void zoneSpeciesFirstThenWantedOnes() {
		assertEquals(List.of("minecraft:birch_sapling", "minecraft:oak_sapling", "minecraft:dark_oak_sapling"),
				PlantingRules.saplingPreferences("minecraft:birch_log",
						List.of("minecraft:oak_planks", "minecraft:stick", "minecraft:dark_oak_sapling", "minecraft:birch_log")));
		assertEquals(List.of("minecraft:spruce_sapling"), PlantingRules.saplingPreferences("", List.of("minecraft:spruce_log")));
		assertTrue(PlantingRules.saplingPreferences("", List.of()).isEmpty());
	}

	@Test
	void wantedCropsFirstThenTheZoneCrop() {
		assertEquals(List.of("minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:carrot"),
				PlantingRules.seedPreferences("minecraft:carrots", List.of("minecraft:wheat", "minecraft:beetroot", "minecraft:diorite")));
		assertEquals(List.of("minecraft:potato"), PlantingRules.seedPreferences("minecraft:potatoes", List.of("minecraft:poisonous_potato")));
		assertEquals("minecraft:beetroot_seeds", PlantingRules.seedOfCrop("minecraft:beetroots"));
		assertEquals("", PlantingRules.seedOfCrop("minecraft:melon"));
		assertTrue(PlantingRules.SEEDS.contains(PlantingRules.seedOfItem("minecraft:carrot")));
	}

	// ------------------------------------------------------------------ source

	private static final Set<String> PLANTABLE = Set.of("minecraft:oak_sapling", "minecraft:birch_sapling", "minecraft:dark_oak_sapling");

	private static PlantingRules.Choice choose(List<String> preferred, boolean any, Map<String, Integer> bag, List<Map<String, Integer>> chests) {
		return PlantingRules.choose(preferred, any, bag, chests, PLANTABLE::contains, id -> PlantingRules.needsSquare(id) ? 4 : 1);
	}

	private static Map<String, Integer> items(Object... pairs) {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	@Test
	void preferredItemFromTheBagThenFromTheNearestChest() {
		PlantingRules.Choice choice = choose(List.of("minecraft:birch_sapling"), true, items("minecraft:oak_sapling", 5, "minecraft:birch_sapling", 2),
				List.of(items("minecraft:birch_sapling", 30)));
		assertTrue(choice.inBag());
		assertEquals("minecraft:birch_sapling", choice.item());
		// Not in the bag: the second chest holds it, before the oak saplings of the bag.
		choice = choose(List.of("minecraft:birch_sapling"), true, items("minecraft:oak_sapling", 5),
				List.of(items("minecraft:oak_sapling", 3), items("minecraft:birch_sapling", 1)));
		assertEquals(1, choice.chest());
		assertEquals("minecraft:birch_sapling", choice.item());
	}

	@Test
	void anythingElseOnlyWhenAllowedBagFirst() {
		Map<String, Integer> bag = items("minecraft:oak_sapling", 5);
		List<Map<String, Integer>> chests = List.of(items("minecraft:birch_sapling", 8));
		PlantingRules.Choice choice = choose(List.of(), true, bag, chests);
		assertTrue(choice.inBag());
		assertEquals("minecraft:oak_sapling", choice.item());
		choice = choose(List.of(), true, items(), chests);
		assertEquals(0, choice.chest());
		assertEquals("minecraft:birch_sapling", choice.item());
		// A zone of one species: nothing else is planted.
		assertNull(choose(List.of("minecraft:spruce_sapling"), false, bag, chests));
		// Unknown / not plantable items are skipped.
		assertNull(choose(List.of(), true, items("minecraft:stick", 64), List.of(items("minecraft:dirt", 64))));
		assertNull(choose(List.of(), true, items(), List.of()));
	}

	@Test
	void twoByTwoSpeciesNeedFourSaplingsInOnePlace() {
		PlantingRules.Choice choice = choose(List.of("minecraft:dark_oak_sapling"), true, items("minecraft:dark_oak_sapling", 3),
				List.of(items("minecraft:dark_oak_sapling", 2), items("minecraft:dark_oak_sapling", 4)));
		assertEquals(1, choice.chest(), "the bag (3) and the first chest (2) hold too few");
		assertNull(choose(List.of("minecraft:dark_oak_sapling"), false, items("minecraft:dark_oak_sapling", 3), List.of()));
		assertEquals(10, PlantingRules.fetchAmount(16, 10, false));
		assertEquals(8, PlantingRules.fetchAmount(16, 10, true), "whole squares");
		assertEquals(0, PlantingRules.fetchAmount(2, 10, true), "no room for a square");
		assertEquals(0, PlantingRules.fetchAmount(16, 3, true));
		assertEquals(4, PlantingRules.fetchAmount(16, 4, true));
	}
}
