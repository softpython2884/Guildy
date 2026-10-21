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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertTrue(VillagerConfig.CURRENT_VERSION >= 3, "step 3 is part of the current format");
	}

	@Test
	void customisedCategoriesAreKept() {
		VillagerConfig config = new VillagerConfig();
		config.configVersion = 1;
		config.storage.categories = legacyAsCategories();
		config.storage.categories.get("Bois").rules.add("minecraft:ladder");
		config.validate();
		assertEquals(withTransversals(StorageDefaults.legacyCategories().keySet()), new ArrayList<>(config.storage.categories.keySet()));
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
		// Step 3 is not run again; step 8 adds the transversal categories (no parent of the new sub-categories is there).
		assertEquals(withTransversals(StorageDefaults.legacyCategories().keySet()), new ArrayList<>(config.storage.categories.keySet()));
	}

	/** The names followed by the three default transversal categories (added to every older file). */
	private static List<String> withTransversals(java.util.Collection<String> names) {
		List<String> list = new ArrayList<>(names);
		list.addAll(List.of("Équipement enchanté", "Objets rares", "Ingrédients d'alchimie"));
		return list;
	}

	/** A villagers.json5 of Guildy 1.11 (configVersion 7): the 19 categories of that version. */
	private static VillagerConfig version7() {
		VillagerConfig config = new VillagerConfig();
		config.configVersion = 7;
		LinkedHashMap<String, StorageCategory> categories = new LinkedHashMap<>();
		for (Map.Entry<String, StorageCategory> entry : StorageDefaults.categories().entrySet()) {
			if (StorageDefaults.V7_NAMES.contains(entry.getKey())) {
				categories.put(entry.getKey(), entry.getValue().copy());
			}
		}
		config.storage.categories = categories;
		config.storage.knownCategories = new ArrayList<>();
		config.storage.maxChests = 48;
		config.storage.maxFilterChests = 256;
		config.storage.maxRooms = 4;
		config.storage.maxRoomChests = 192;
		config.storage.maxRoomSize = 32;
		config.storage.scanMaxChunks = 8;
		config.storage.scanMaxContainers = 1024;
		return config;
	}

	@Test
	void version7GetsTheNewCategoriesInPlace() {
		VillagerConfig config = version7();
		config.validate();
		assertEquals(new ArrayList<>(StorageDefaults.categories().keySet()), new ArrayList<>(config.storage.categories.keySet()),
				"sub-categories after their parent, transversal categories at the end: the order of the defaults");
		assertEquals("Livres et enchantements", config.storage.categories.get("Livres enchantés").parent);
		assertEquals("*", config.storage.categories.get("Objets rares").parent);
		assertEquals(new ArrayList<>(StorageDefaults.categories().keySet()), config.storage.knownCategories);
		assertEquals(VillagerConfig.CURRENT_VERSION, config.configVersion);
	}

	@Test
	void removedCategoriesDoNotComeBack() {
		VillagerConfig config = version7();
		config.validate();
		config.storage.categories.remove("Verre");
		config.storage.categories.remove("Objets rares");
		config.validate();
		assertFalse(config.storage.categories.containsKey("Verre"));
		assertFalse(config.storage.categories.containsKey("Objets rares"));
		// A sub-category whose parent the admin removed is not added either.
		VillagerConfig custom = version7();
		custom.storage.categories.remove("Livres et enchantements");
		custom.validate();
		assertFalse(custom.storage.categories.containsKey("Livres enchantés"));
		assertTrue(custom.storage.categories.containsKey("Laine et tapis"));
	}

	@Test
	void untouchedLimitsBecomeUnlimited() {
		VillagerConfig config = version7();
		config.validate();
		assertEquals(0, config.storage.maxChests);
		assertEquals(0, config.storage.maxFilterChests);
		assertEquals(0, config.storage.maxRooms);
		assertEquals(0, config.storage.maxRoomChests);
		assertEquals(64, config.storage.maxRoomSize);
		assertEquals(16, config.storage.scanMaxChunks);
		assertEquals(4096, config.storage.scanMaxContainers);
		assertTrue(config.storage.filterChests);
		assertTrue(config.storage.chestRooms);
		// Already current: never migrated again.
		config.storage.maxChests = 48;
		config.validate();
		assertEquals(48, config.storage.maxChests);
	}

	@Test
	void editedLimitsAreKeptAndOldZeroDisables() {
		VillagerConfig config = version7();
		config.storage.maxChests = 100;
		config.storage.maxFilterChests = 0;
		config.storage.maxRooms = 0;
		config.storage.maxRoomChests = 500;
		config.storage.maxRoomSize = 40;
		config.validate();
		assertEquals(100, config.storage.maxChests);
		assertEquals(500, config.storage.maxRoomChests);
		assertEquals(40, config.storage.maxRoomSize);
		assertFalse(config.storage.filterChests, "maxFilterChests = 0 meant: no filter chests");
		assertFalse(config.storage.chestRooms, "maxRooms = 0 meant: no chest rooms");
		VillagerConfig rooms = version7();
		rooms.storage.maxRoomChests = 0;
		rooms.validate();
		assertFalse(rooms.storage.chestRooms, "maxRoomChests = 0 meant: no room chest at all");
	}

	@Test
	void newFilesAndFilesWithoutTheKeysAreUnlimited() {
		VillagerConfig fresh = new VillagerConfig();
		fresh.validate();
		assertTrue(fresh.storage.filterChests && fresh.storage.chestRooms, "a new file: everything enabled");
		assertEquals(0, fresh.storage.maxFilterChests);
		assertEquals(0, fresh.storage.maxRooms);
		assertEquals(0, fresh.storage.maxRoomChests);
		// An older file written before the chest rooms existed (no key): not "disabled".
		VillagerConfig old = Json5.fromString("{ \"configVersion\": 3, \"storage\": { \"maxChests\": 48 } }", VillagerConfig.class);
		old.validate();
		assertTrue(old.storage.filterChests && old.storage.chestRooms);
		assertEquals(0, old.storage.maxChests);
		assertEquals(0, old.storage.maxRooms);
		// An explicit 0 in a version 8 file: unlimited.
		VillagerConfig current = Json5.fromString("{ \"configVersion\": 8, \"storage\": { \"maxRooms\": 0, \"maxFilterChests\": 0 } }", VillagerConfig.class);
		current.validate();
		assertTrue(current.storage.filterChests && current.storage.chestRooms);
	}

	@Test
	void unknownParentBecomesTopLevel() {
		VillagerConfig config = new VillagerConfig();
		config.storage.categories.put("Mes livres", new StorageCategory("minecraft:book", "", "Inconnue", List.of("minecraft:book")));
		config.storage.categories.put("Mes bûches", new StorageCategory("minecraft:oak_log", "", "bois", List.of("#minecraft:logs")));
		config.validate();
		assertEquals("", config.storage.categories.get("Mes livres").parent);
		assertEquals("Bois", config.storage.categories.get("Mes bûches").parent, "parent spelled like its category");
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
		assertEquals(withTransversals(List.of("Bois", "Gemmes")), new ArrayList<>(config.storage.categories.keySet()));
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
			assertEquals(expected.parent, actual.parent);
		}
		assertTrue(text.contains("\"parent\": \"Livres et enchantements\""));
		assertEquals(config.storage.knownCategories, read.storage.knownCategories);
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
