package dev.guildy.villager;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyConfigTest {
	@Test
	void boundsAreKept() {
		SupplyConfig config = new SupplyConfig();
		config.gathering.pitSide = 4;
		config.gathering.pitMaxDepth = 50;
		config.gathering.radius = 5;
		config.crafting.maxDepth = 0;
		config.smelting.ticksPerItem = 0;
		config.validate();
		assertEquals(4 * 3 - 1, config.gathering.pitMaxDepth, "the ramp needs one border column per level");
		assertEquals(16, config.gathering.radius);
		assertEquals(1, config.crafting.maxDepth);
		assertEquals(1, config.smelting.ticksPerItem);
		assertEquals(1, config.configVersion);
	}

	@Test
	void missingSectionsAndListsAreRestored() {
		SupplyConfig config = Json5.fromString("{ crafting: null, gathering: { pitSide: 8 }, sources: { \"minecraft:flint\": null, \" \": [] },"
				+ " neverFetch: [\"minecraft:diamond\", \"minecraft:diamond\", \"\"] }", SupplyConfig.class);
		config.validate();
		assertNotNull(config.crafting);
		assertNotNull(config.transforms);
		assertEquals(8, config.gathering.pitSide);
		assertEquals(10, config.gathering.pitMaxDepth, "defaults kept for the missing keys");
		assertEquals(List.of(), config.sources.get("minecraft:flint"), "a null list becomes empty (never gathered)");
		assertEquals(1, config.sources.size(), "blank items dropped");
		assertEquals(List.of("minecraft:diamond"), config.neverFetch);
	}

	@Test
	void defaultFuelsPreferCoal() {
		SupplyConfig config = new SupplyConfig();
		assertEquals("minecraft:coal", config.smelting.fuels.getFirst());
		assertTrue(config.smelting.fuels.contains("#minecraft:logs_that_burn"));
		List<String> copy = new ArrayList<>(config.smelting.fuels);
		config.smelting.fuels = new ArrayList<>(Arrays.asList("minecraft:coal", null, " minecraft:charcoal "));
		config.validate();
		assertEquals(List.of("minecraft:coal", "minecraft:charcoal"), config.smelting.fuels);
		assertTrue(copy.size() > 2);
		assertTrue(new LinkedHashMap<>(config.sources).isEmpty());
	}
}
