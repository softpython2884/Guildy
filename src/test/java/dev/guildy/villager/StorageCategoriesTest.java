package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageCategoriesTest {
	private static StorageCategories defaults() {
		VillagerConfig config = new VillagerConfig();
		return new StorageCategories(config.storage.categories, config.storage.fallbackCategory);
	}

	@Test
	void itemIdMatchesCategory() {
		StorageCategories categories = defaults();
		assertEquals("Minerais & lingots", categories.categoryOf("minecraft:diamond", List.of()));
		assertEquals("Cultures & nourriture", categories.categoryOf("minecraft:wheat", List.of()));
		assertEquals("Butin de monstres", categories.categoryOf("minecraft:rotten_flesh", List.of()));
		assertEquals("Pierre & terre", categories.categoryOf("minecraft:cobblestone", List.of()));
	}

	@Test
	void itemTagMatchesCategory() {
		StorageCategories categories = defaults();
		assertEquals("Bois", categories.categoryOf("minecraft:oak_log", List.of("minecraft:logs", "minecraft:logs_that_burn")));
		assertEquals("Outils & armes", categories.categoryOf("minecraft:diamond_sword", List.of("minecraft:swords")));
		assertEquals("Minerais & lingots", categories.categoryOf("minecraft:charcoal", List.of("minecraft:coals")));
	}

	@Test
	void unknownItemGoesToFallback() {
		StorageCategories categories = defaults();
		assertEquals("Divers", categories.categoryOf("minecraft:feather", List.of()));
		assertEquals("Divers", categories.categoryOf("othermod:gizmo", List.of("othermod:gadgets")));
		assertEquals("Divers", categories.fallback());
	}

	@Test
	void namespaceIsOptionalAndCaseIgnored() {
		Map<String, List<String>> config = new LinkedHashMap<>();
		config.put("Plantes", List.of("#Saplings", "Wheat_Seeds"));
		StorageCategories categories = new StorageCategories(config, "Autres");
		assertEquals("Plantes", categories.categoryOf("minecraft:wheat_seeds", List.of()));
		assertEquals("Plantes", categories.categoryOf("wheat_seeds", List.of()));
		assertEquals("Plantes", categories.categoryOf("minecraft:oak_sapling", List.of("saplings")));
		assertEquals("Autres", categories.categoryOf("minecraft:stone", List.of()));
	}

	@Test
	void firstCategoryInConfigOrderWins() {
		Map<String, List<String>> config = new LinkedHashMap<>();
		config.put("A", List.of("#minecraft:logs"));
		config.put("B", List.of("minecraft:oak_log"));
		StorageCategories categories = new StorageCategories(config, "Divers");
		assertEquals("A", categories.categoryOf("minecraft:oak_log", List.of("minecraft:logs")));
		// Without the tag, only B matches.
		assertEquals("B", categories.categoryOf("minecraft:oak_log", List.of()));
		assertEquals(List.of("A", "B"), categories.names());
	}

	@Test
	void validateRemovesFallbackFromExplicitCategories() {
		VillagerConfig config = new VillagerConfig();
		config.storage.categories.put("Divers", List.of("minecraft:dirt"));
		config.validate();
		StorageCategories categories = new StorageCategories(config.storage.categories, config.storage.fallbackCategory);
		assertEquals(List.of("Bois", "Pierre & terre", "Minerais & lingots", "Cultures & nourriture", "Butin de monstres", "Outils & armes"),
				categories.names());
	}
}
