package dev.guildy.villager;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** villagers.json5 storage section: format, round trip and migration step 3 of the older default categories. */
class StorageConfigTest {
	private static LinkedHashMap<String, StorageCategory> legacyAsCategories() {
		LinkedHashMap<String, StorageCategory> map = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : StorageDefaults.legacyCategories().entrySet()) {
			StorageCategory category = new StorageCategory();
			category.rules = new ArrayList<>(entry.getValue());
			map.put(entry.getKey(), category);
		}
		return map;
	}

	@Test
	void untouchedOlderDefaultsAreReplaced() {
		VillagerConfig config = new VillagerConfig();
		config.configVersion = 1;
		config.storage.categories = legacyAsCategories();
		config.validate();
		assertEquals(new ArrayList<>(StorageDefaults.categories().keySet()), new ArrayList<>(config.storage.categories.keySet()));
		assertEquals("minecraft:skeleton_skull", config.storage.categories.get("Butin de monstres").icon);
		assertEquals(VillagerConfig.CURRENT_VERSION, config.configVersion);
		assertEquals(3, VillagerConfig.CURRENT_VERSION);
	}

	@Test
	void customisedCategoriesAreKept() {
		VillagerConfig config = new VillagerConfig();
		config.configVersion = 1;
		config.storage.categories = legacyAsCategories();
		config.storage.categories.get("Bois").rules.add("minecraft:ladder");
		config.validate();
		assertEquals(new ArrayList<>(StorageDefaults.legacyCategories().keySet()), new ArrayList<>(config.storage.categories.keySet()));
		assertTrue(config.storage.categories.get("Bois").rules.contains("minecraft:ladder"));
		// No icon configured: the first item rule.
		assertEquals("minecraft:stick", config.storage.categories.get("Bois").iconOrGuess());
	}

	@Test
	void currentFilesAreNotMigratedAgain() {
		VillagerConfig config = new VillagerConfig();
		config.configVersion = 3;
		config.storage.categories = legacyAsCategories();
		config.validate();
		assertEquals(new ArrayList<>(StorageDefaults.legacyCategories().keySet()), new ArrayList<>(config.storage.categories.keySet()));
	}

	@Test
	void validateDropsFallbackDuplicatesAndBlankRules() {
		VillagerConfig config = new VillagerConfig();
		config.storage.categories.put("divers", new StorageCategory("minecraft:barrel", "", List.of("minecraft:dirt")));
		config.storage.categories.put("BOIS", new StorageCategory("", "", List.of("minecraft:stick")));
		config.storage.categories.put(" ", new StorageCategory());
		config.storage.categories.get("Pierre").rules.add("  ");
		config.storage.categories.get("Pierre").rules.add("minecraft:stone");
		config.validate();
		assertTrue(config.storage.categories.keySet().stream().noneMatch(name -> name.equalsIgnoreCase("divers") || name.equals("BOIS") || name.isBlank()));
		List<String> stone = config.storage.categories.get("Pierre").rules;
		assertEquals(stone.size(), stone.stream().distinct().count());
		assertTrue(stone.stream().noneMatch(String::isBlank));
	}

	@Test
	void olderListFormatIsRead() {
		VillagerConfig config = Json5.fromString("{ \"configVersion\": 3, \"storage\": { \"categories\": {"
				+ " \"Bois\": [\"#minecraft:logs\", \"minecraft:stick\"],"
				+ " \"Gemmes\": { \"icon\": \"minecraft:diamond\", \"description\": \"Pierres précieuses\", \"rules\": [\"minecraft:diamond\"], \"extra\": 1 } } } }",
				VillagerConfig.class);
		config.validate();
		assertEquals(List.of("Bois", "Gemmes"), new ArrayList<>(config.storage.categories.keySet()));
		assertEquals(List.of("#minecraft:logs", "minecraft:stick"), config.storage.categories.get("Bois").rules);
		assertEquals("", config.storage.categories.get("Bois").icon);
		assertEquals("Pierres précieuses", config.storage.categories.get("Gemmes").description);
	}

	@Test
	void writtenFileReadsBackTheSame(@TempDir Path dir) throws Exception {
		VillagerConfig config = new VillagerConfig();
		config.validate();
		Path file = dir.resolve("villagers.json5");
		Json5.write(file, config);
		String text = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(text.contains("\"icon\": \"minecraft:oak_log\""));
		assertTrue(text.contains("// Categories de rangement"));
		VillagerConfig read = Json5.read(file, VillagerConfig.class);
		read.validate();
		assertEquals(config.storage.categories.keySet(), read.storage.categories.keySet());
		for (String name : config.storage.categories.keySet()) {
			StorageCategory expected = config.storage.categories.get(name);
			StorageCategory actual = read.storage.categories.get(name);
			assertEquals(expected.icon, actual.icon);
			assertEquals(expected.description, actual.description);
			assertEquals(expected.rules, actual.rules);
		}
	}

	/** The villagers.json5 shipped with Guildy 1.1 (docs/default-config) is migrated to the new categories. */
	@Test
	void shippedOlderFileIsMigrated() throws Exception {
		Path shipped = Path.of("docs", "default-config", "villagers.json5");
		if (!Files.exists(shipped)) {
			return;
		}
		VillagerConfig config = Json5.read(shipped, VillagerConfig.class);
		config.validate();
		assertEquals(new ArrayList<>(StorageDefaults.categories().keySet()), new ArrayList<>(config.storage.categories.keySet()));
		assertEquals(VillagerConfig.CURRENT_VERSION, config.configVersion);
	}
}
