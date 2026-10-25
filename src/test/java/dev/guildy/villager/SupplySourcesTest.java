package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplySourcesTest {
	private static Map<String, Map<String, Double>> drops() {
		Map<String, Map<String, Double>> drops = new LinkedHashMap<>();
		drops.put("minecraft:stone", Map.of("minecraft:cobblestone", 1.0));
		drops.put("minecraft:grass_block", Map.of("minecraft:dirt", 1.0));
		drops.put("minecraft:dirt", Map.of("minecraft:dirt", 1.0));
		drops.put("minecraft:clay", Map.of("minecraft:clay_ball", 4.0));
		drops.put("minecraft:gravel", Map.of("minecraft:gravel", 0.9, "minecraft:flint", 0.1));
		drops.put("minecraft:sand", Map.of("minecraft:sand", 1.0, "minecraft:rare", 0.01));
		return drops;
	}

	private static Map<String, String> trees() {
		return Map.of("minecraft:oak_log", "minecraft:oak_log");
	}

	@Test
	void sourcesComeFromTheDrops() {
		SupplySources sources = new SupplySources(drops(), trees(), Map.of(), Set.of());
		assertEquals(List.of("minecraft:stone"), sources.sources("minecraft:cobblestone").stream().map(SupplySources.Source::block).toList(),
				"stone mined without silk touch gives cobblestone");
		assertFalse(sources.gatherable("minecraft:stone"), "stone itself is never a drop");
		assertEquals(4.0, sources.yield("minecraft:clay_ball", SupplySources.Method.DIG));
		assertTrue(sources.gatherable("minecraft:flint"), "a 10 % drop counts");
		assertFalse(sources.gatherable("minecraft:rare"), "a 1 % drop does not");
		assertEquals(Set.of("minecraft:grass_block", "minecraft:dirt"), sources.blocks("minecraft:dirt", SupplySources.Method.DIG));
	}

	@Test
	void treesGiveTheirLogs() {
		SupplySources sources = new SupplySources(drops(), trees(), Map.of(), Set.of());
		assertEquals(Set.of("minecraft:oak_log"), sources.blocks("minecraft:oak_log", SupplySources.Method.TREE));
		assertTrue(sources.items(SupplySources.Method.TREE).contains("minecraft:oak_log"));
		assertFalse(sources.items(SupplySources.Method.TREE).contains("minecraft:dirt"));
	}

	@Test
	void bestYieldFirst() {
		Map<String, Map<String, Double>> drops = drops();
		drops.put("minecraft:coarse", Map.of("minecraft:gravel", 2.0));
		SupplySources sources = new SupplySources(drops, trees(), Map.of(), Set.of());
		assertEquals("minecraft:coarse", sources.sources("minecraft:gravel").getFirst().block());
	}

	@Test
	void overridesReplaceOrRemove() {
		Map<String, List<String>> overrides = new LinkedHashMap<>();
		overrides.put("minecraft:flint", List.of("minecraft:gravel"));
		overrides.put("minecraft:dirt", List.of());
		overrides.put("minecraft:stick", List.of("tree:minecraft:oak_log", "minecraft:unknown"));
		SupplySources sources = new SupplySources(drops(), trees(), overrides, Set.of("minecraft:cobblestone"));
		assertEquals(0.1, sources.yield("minecraft:flint", SupplySources.Method.DIG), 1e-9, "the drop rate of the block is kept");
		assertFalse(sources.gatherable("minecraft:dirt"), "an empty list: never gathered");
		assertEquals(Set.of("minecraft:oak_log"), sources.blocks("minecraft:stick", SupplySources.Method.TREE), "unknown blocks ignored");
		assertFalse(sources.gatherable("minecraft:cobblestone"), "never fetched");
	}
}
