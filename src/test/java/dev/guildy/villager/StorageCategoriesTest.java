package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageCategoriesTest {
	private static StorageCategories defaults() {
		VillagerConfig config = new VillagerConfig();
		config.validate();
		return new StorageCategories(StorageDefaults.rules(config.storage.categories), StorageDefaults.parents(config.storage.categories),
				config.storage.fallbackCategory);
	}

	/** Facts from "#tag" (size after '=' if given), "@property" and plain words ignored. */
	private static StorageCategories.Facts facts(String id, String... facts) {
		Map<String, Integer> tags = new LinkedHashMap<>();
		Set<String> properties = new HashSet<>();
		for (String fact : facts) {
			if (fact.startsWith("#")) {
				String[] parts = fact.substring(1).split("=");
				tags.put(StorageCategories.normalize(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
			} else if (fact.startsWith("@")) {
				properties.add(StorageCategories.property(fact));
			}
		}
		return new StorageCategories.Facts(id, tags, properties);
	}

	private static Map<String, List<String>> rules(Object... nameAndRules) {
		Map<String, List<String>> map = new LinkedHashMap<>();
		for (int i = 0; i < nameAndRules.length; i += 2) {
			map.put((String) nameAndRules[i], List.of((String[]) nameAndRules[i + 1]));
		}
		return map;
	}

	// ------------------------------------------------------------------ rule engine

	@Test
	void exactIdBeatsTagPatternPropertyAndTab() {
		StorageCategories categories = new StorageCategories(rules(
				"Tab", new String[]{"@tab:combat"},
				"Property", new String[]{"@weapon"},
				"Pattern", new String[]{"*_sword"},
				"Tag", new String[]{"#minecraft:swords"},
				"Id", new String[]{"minecraft:diamond_sword"}), "Divers");
		StorageCategories.Facts sword = facts("minecraft:diamond_sword", "#minecraft:swords", "@weapon", "@tab:combat");
		assertEquals("Id", categories.categoryOf(sword));
		assertEquals(StorageCategories.Kind.ID, categories.match(sword).kind());
		assertEquals("Tag", categories.categoryOf(facts("minecraft:iron_sword", "#minecraft:swords", "@weapon", "@tab:combat")));
		assertEquals("Pattern", categories.categoryOf(facts("minecraft:iron_sword", "@weapon", "@tab:combat")));
		assertEquals("Property", categories.categoryOf(facts("minecraft:iron_axe", "@weapon", "@tab:combat")));
		assertEquals("Tab", categories.categoryOf(facts("minecraft:snowball", "@tab:combat")));
		StorageCategories.Match none = categories.match(facts("minecraft:stone"));
		assertEquals("Divers", none.category());
		assertEquals(StorageCategories.Kind.FALLBACK, none.kind());
	}

	@Test
	void smallestTagWinsThenConfigOrder() {
		StorageCategories categories = new StorageCategories(rules(
				"Tools", new String[]{"#c:tools"},
				"Swords", new String[]{"#minecraft:swords"},
				"Other", new String[]{"#minecraft:swords"}), "Divers");
		assertEquals("Swords", categories.categoryOf(facts("minecraft:iron_sword", "#c:tools=40", "#minecraft:swords=7")));
		// Unknown sizes: config order.
		assertEquals("Tools", categories.categoryOf(facts("minecraft:iron_sword", "#c:tools", "#minecraft:swords")));
		// The same tag twice: the first category keeps it.
		assertEquals("Swords", categories.categoryOf(facts("minecraft:iron_sword", "#minecraft:swords=7")));
	}

	@Test
	void longestPatternWins() {
		StorageCategories categories = new StorageCategories(rules(
				"Stone", new String[]{"*stone_brick*"},
				"Nether", new String[]{"*polished_blackstone*"},
				"Ores", new String[]{"*_ore"}), "Divers");
		assertEquals("Nether", categories.categoryOf(facts("minecraft:polished_blackstone_bricks")));
		assertEquals("Stone", categories.categoryOf(facts("minecraft:mossy_stone_bricks")));
		assertEquals("Ores", categories.categoryOf(facts("othermod:ruby_ore")));
		assertEquals("Divers", categories.categoryOf(facts("minecraft:ore_block")));
	}

	@Test
	void patternWithNamespaceOnlyMatchesItsMod() {
		StorageCategories categories = new StorageCategories(rules(
				"Vanilla", new String[]{"minecraft:*_ingot"},
				"All", new String[]{"*_nugget"}), "Divers");
		assertEquals("Vanilla", categories.categoryOf(facts("minecraft:iron_ingot")));
		assertEquals("Divers", categories.categoryOf(facts("othermod:tin_ingot")));
		assertEquals("All", categories.categoryOf(facts("othermod:tin_nugget")));
	}

	@Test
	void namespaceIsOptionalAndCaseIgnored() {
		StorageCategories categories = new StorageCategories(rules(
				"Plantes", new String[]{"#Saplings", "Wheat_Seeds", "@FOOD", "@Tab:Natural_Blocks"}), "Autres");
		assertEquals("Plantes", categories.categoryOf("minecraft:wheat_seeds", List.of()));
		assertEquals("Plantes", categories.categoryOf("wheat_seeds", List.of()));
		assertEquals("Plantes", categories.categoryOf("minecraft:oak_sapling", List.of("saplings")));
		assertEquals("Plantes", categories.categoryOf(facts("minecraft:apple", "@food")));
		assertEquals("Plantes", categories.categoryOf(facts("minecraft:fern", "@tab:minecraft:natural_blocks")));
		assertEquals("Autres", categories.categoryOf("minecraft:stone", List.of()));
	}

	@Test
	void namesFallbackAndCanonicalSpelling() {
		StorageCategories categories = new StorageCategories(rules(
				"Bois", new String[]{"#minecraft:logs"},
				"divers", new String[]{"minecraft:dirt"},
				"bois", new String[]{"minecraft:stick"}), "Divers");
		// The fallback and a duplicate (case ignored) are not categories.
		assertEquals(List.of("Bois"), categories.names());
		assertEquals("Bois", categories.canonical("BOIS"));
		assertEquals("Divers", categories.canonical("divers"));
		assertNull(categories.canonical("Pierre"));
		assertEquals(-1, categories.indexOf("Divers"));
		assertEquals("Divers", categories.categoryOf("minecraft:dirt", List.of()));
	}

	@Test
	void problemsReportUnusableRules() {
		assertEquals(List.of(), StorageCategories.problems(List.of("#minecraft:logs", "*_ore", "@food", "@tab:combat", "stone")));
		assertEquals(3, StorageCategories.problems(List.of("@edible", "@tab:", "**")).size());
	}

	// ------------------------------------------------------------------ default categories

	@Test
	void defaultCategoriesAreComplete() {
		VillagerConfig config = new VillagerConfig();
		config.validate();
		assertEquals(List.of("Bois", "Pierre", "Terre, sable et gravier", "Blocs de construction", "Laine et tapis", "Verre",
				"Béton et terre cuite", "Mobilier et décoration", "Éclairage", "Minerais et lingots", "Redstone", "Outils", "Armes",
				"Armures", "Livres et enchantements", "Livres enchantés", "Parchemins et grimoires", "Livres et papier",
				"Potions et alchimie", "Potions", "Nourriture", "Cultures", "Graines et pousses", "Plantes et fleurs",
				"Butin de monstres", "Nether", "End", "Colorants", "Équipement enchanté", "Objets rares", "Ingrédients d'alchimie"),
				new ArrayList<>(config.storage.categories.keySet()));
		assertEquals("Divers", config.storage.fallbackCategory);
		assertEquals(StorageDefaults.V7_NAMES, defaults().roots(), "the top-level categories are those of Guildy 1.11");
		assertEquals(List.of("Équipement enchanté", "Objets rares", "Ingrédients d'alchimie"), defaults().transversals());
		// An item id is listed once among the rules that compete together: the top-level categories, the sub-categories
		// of one parent, one transversal category.
		Map<String, Set<String>> idsByScope = new java.util.HashMap<>();
		for (Map.Entry<String, StorageCategory> entry : config.storage.categories.entrySet()) {
			StorageCategory category = entry.getValue();
			assertFalse(category.icon.isBlank(), entry.getKey() + " has an icon");
			assertFalse(category.description.isBlank(), entry.getKey() + " has examples");
			assertEquals(List.of(), StorageCategories.problems(category.rules), entry.getKey() + " rules");
			String scope = category.isTransversal() ? "*" + entry.getKey() : category.parent;
			Set<String> ids = idsByScope.computeIfAbsent(scope, s -> new HashSet<>());
			for (String rule : category.rules) {
				if (!rule.startsWith("#") && !rule.startsWith("@") && rule.indexOf('*') < 0) {
					assertTrue(ids.add(StorageCategories.normalize(rule)), rule + " listed in two categories of the same level");
				}
			}
		}
	}

	// ------------------------------------------------------------------ sub-categories and transversal categories

	@Test
	void subCategoriesRefineTheirParent() {
		StorageCategories c = defaults();
		StorageCategories.Resolution book = c.resolve(facts("minecraft:enchanted_book", "#minecraft:bookshelf_books=5", "@enchanted_book",
				"@tab:tools_and_utilities"));
		assertEquals("Livres enchantés", book.category());
		assertEquals(List.of("Livres enchantés", "Livres et enchantements"), book.chain());
		assertEquals(List.of("Livres et papier", "Livres et enchantements"), c.chain(facts("minecraft:paper", "@tab:ingredients")));
		assertEquals(List.of("Livres et enchantements"), c.chain(facts("minecraft:bookshelf", "@block", "@tab:functional_blocks")));
		assertEquals(List.of("Potions", "Potions et alchimie"), c.chain(facts("minecraft:potion", "@potion", "@tab:food_and_drinks")));
		assertEquals(List.of("Cultures", "Nourriture"), c.chain(facts("minecraft:carrot", "#c:crops=8", "@food")));
		assertEquals(List.of("Laine et tapis", "Blocs de construction"), c.chain(facts("minecraft:red_wool", "#minecraft:wool=16", "@block")));
		assertEquals(List.of("Verre", "Blocs de construction"), c.chain(facts("minecraft:red_stained_glass", "@block", "@tab:colored_blocks")));
		assertEquals(List.of("Éclairage", "Mobilier et décoration"), c.chain(facts("minecraft:lantern", "#minecraft:lanterns=10", "@block")));
		// Nothing of the parent's rules is lost: the top-level choice is the one of Guildy 1.11.
		assertEquals(List.of("Pierre"), c.chain(facts("minecraft:cobblestone", "#c:cobblestones=4", "@block")));
		assertEquals(List.of(), c.chain(facts("othermod:thing")));
		assertEquals("Divers", c.categoryOf(facts("othermod:thing")));
	}

	@Test
	void transversalCategoriesComeFirstAndKeepTheUsualCategory() {
		StorageCategories c = defaults();
		assertEquals(List.of("Équipement enchanté", "Armes"),
				c.chain(facts("minecraft:diamond_sword", "#minecraft:swords=7", "@weapon", "@enchanted", "@tab:combat")));
		assertEquals("Armes", c.categoryOf(facts("minecraft:diamond_sword", "#minecraft:swords=7", "@weapon", "@enchanted")),
				"the category of an item is never a transversal one");
		assertEquals(List.of("Ingrédients d'alchimie", "Butin de monstres"), c.chain(facts("minecraft:ghast_tear", "@tab:ingredients")));
		assertEquals(List.of("Ingrédients d'alchimie", "Potions et alchimie"), c.chain(facts("minecraft:blaze_powder", "#minecraft:brewing_fuel=1")));
		assertEquals(List.of("Objets rares", "Mobilier et décoration"),
				c.chain(facts("minecraft:music_disc_cat", "#c:music_discs=20", "@music_disc")));
		// Several transversal categories: the most precise rule first (an item id before a property).
		assertEquals(List.of("Objets rares", "Équipement enchanté", "Armures"), c.chain(facts("minecraft:elytra", "@armor", "@enchanted")));
		// Plain gear: no transversal category.
		assertEquals(List.of("Armes"), c.chain(facts("minecraft:iron_sword", "#minecraft:swords=7", "@weapon")));
	}

	@Test
	void magicItemsChooseTheirTopLevelCategoryAsEnchantedBooks() {
		StorageCategories c = defaults();
		StorageCategories.Facts book = facts("minecraft:enchanted_book", "@enchanted_book");
		StorageCategories.Facts scroll = facts("guildy:scroll", "@magic");
		StorageCategories.Resolution resolution = c.resolve(book, scroll);
		assertEquals(List.of("Parchemins et grimoires", "Livres et enchantements"), resolution.chain());
		// Without the magic sub-category: the books category itself, never the enchanted books one.
		Map<String, List<String>> rules = new LinkedHashMap<>();
		rules.put("Livres", List.of("minecraft:enchanted_book"));
		rules.put("Livres enchantés", List.of("minecraft:enchanted_book", "@enchanted_book"));
		StorageCategories plain = new StorageCategories(rules, Map.of("Livres enchantés", "Livres"), "Divers");
		assertEquals(List.of("Livres"), plain.resolve(book, scroll).chain());
		assertEquals(List.of("Livres enchantés", "Livres"), plain.chain(book));
	}

	@Test
	void subCategoryRulesNeverTakeItemsOfAnotherCategory() {
		Map<String, List<String>> rules = new LinkedHashMap<>();
		rules.put("Bois", List.of("#minecraft:logs"));
		rules.put("Pierre", List.of("minecraft:cobblestone"));
		rules.put("Bûches", List.of("#minecraft:logs", "minecraft:cobblestone"));
		StorageCategories c = new StorageCategories(rules, Map.of("Bûches", "Bois"), "Divers");
		assertEquals(List.of("Pierre"), c.chain(facts("minecraft:cobblestone")));
		assertEquals(List.of("Bûches", "Bois"), c.chain(facts("minecraft:oak_log", "#minecraft:logs=48")));
		assertEquals(List.of("Bois", "Bûches", "Pierre"), c.menuOrder());
		assertEquals(List.of("Bûches"), c.children("bois"));
		assertEquals("Bois", c.parent("Bûches"));
		assertEquals(1, c.depth("Bûches"));
		assertEquals(List.of("Bûches", "Bois"), c.ancestors("bûches"));
		assertEquals("Bois", c.root("Bûches"));
	}

	@Test
	void badParentsMakeTopLevelCategories() {
		Map<String, List<String>> rules = new LinkedHashMap<>();
		rules.put("A", List.of("minecraft:a"));
		rules.put("B", List.of("minecraft:b"));
		rules.put("C", List.of("minecraft:c"));
		rules.put("T", List.of("minecraft:t"));
		rules.put("D", List.of("minecraft:d"));
		Map<String, String> parents = new LinkedHashMap<>();
		parents.put("A", "B");
		parents.put("B", "A");
		parents.put("C", "Inconnue");
		parents.put("T", "*");
		parents.put("D", "T");
		StorageCategories c = new StorageCategories(rules, parents, "Divers");
		// A cycle is broken at its first category, an unknown parent and a transversal parent make top-level categories.
		assertEquals(List.of("A", "C", "D"), c.roots());
		assertEquals("A", c.parent("B"));
		assertEquals(List.of("T"), c.transversals());
		assertEquals(List.of("A"), c.chain(facts("minecraft:a")));
		assertEquals(List.of("T"), c.chain(facts("minecraft:t")), "only a transversal rule: no usual category");
		assertEquals("Divers", c.categoryOf(facts("minecraft:t")));
	}

	@Test
	void defaultsSettleTheOverlaps() {
		StorageCategories c = defaults();
		// Mob loot wins over food, Nether and End.
		assertEquals("Butin de monstres", c.categoryOf(facts("minecraft:skeleton_skull", "#minecraft:skulls=7", "@block", "@tab:functional_blocks")));
		assertEquals("Butin de monstres", c.categoryOf(facts("minecraft:player_head", "#minecraft:skulls=7", "@block")));
		assertEquals("Butin de monstres", c.categoryOf(facts("minecraft:leather", "#c:leathers=1", "@tab:ingredients")));
		assertEquals("Butin de monstres", c.categoryOf(facts("minecraft:rotten_flesh", "#minecraft:meat=11", "#c:foods=70", "@food")));
		assertEquals("Butin de monstres", c.categoryOf(facts("minecraft:blaze_rod", "#c:rods/blaze=1", "@tab:ingredients")));
		assertEquals("Butin de monstres", c.categoryOf(facts("minecraft:shulker_shell", "@tab:ingredients")));
		// Arrows stay weapons even with a potion; swords are weapons, axes tools.
		assertEquals("Armes", c.categoryOf(facts("minecraft:tipped_arrow", "#minecraft:arrows=3", "@potion", "@tab:combat")));
		assertEquals("Armes", c.categoryOf(facts("minecraft:diamond_sword", "#minecraft:swords=7", "#c:tools/melee_weapon=30", "@weapon")));
		assertEquals("Outils", c.categoryOf(facts("minecraft:diamond_axe", "#minecraft:axes=7", "#c:tools/melee_weapon=30", "@tool")));
		// Themes: chorus in End, carved pumpkin is furniture (worn on the head but no armor), Nether wood is wood.
		assertEquals("End", c.categoryOf(facts("minecraft:chorus_fruit", "@food", "@tab:food_and_drinks")));
		assertEquals("Mobilier et décoration", c.categoryOf(facts("minecraft:carved_pumpkin", "@block", "@tab:natural_blocks")));
		assertEquals("Bois", c.categoryOf(facts("minecraft:crimson_stem", "#minecraft:logs=48")));
		assertEquals("Graines et pousses", c.categoryOf(facts("minecraft:mangrove_propagule", "#minecraft:saplings=12", "#minecraft:flowers=31")));
		assertEquals("Plantes et fleurs", c.categoryOf(facts("minecraft:cherry_leaves", "#minecraft:leaves=14", "#minecraft:flowers=31")));
		// Ores before the stone they sit in; stairs follow their material.
		assertEquals("Minerais et lingots", c.categoryOf(facts("minecraft:deepslate_iron_ore", "#minecraft:ores=18", "#c:ores=20")));
		assertEquals("Minerais et lingots", c.categoryOf(facts("minecraft:nether_quartz_ore", "#minecraft:ores=18")));
		assertEquals("Nether", c.categoryOf(facts("minecraft:polished_blackstone_bricks", "@tab:building_blocks")));
		assertEquals("End", c.categoryOf(facts("minecraft:end_stone_bricks", "@tab:building_blocks")));
		assertEquals("Pierre", c.categoryOf(facts("minecraft:cobblestone_stairs", "#minecraft:stairs=97", "@tab:building_blocks")));
		assertEquals("Bois", c.categoryOf(facts("minecraft:oak_slab", "#minecraft:wooden_slabs=13", "#minecraft:slabs=101")));
		// Wool slabs: building blocks, refined into the wool sub-category.
		assertEquals(List.of("Laine et tapis", "Blocs de construction"), c.chain(facts("minecraft:white_wool_slab", "#minecraft:wool_slabs=16")));
		assertEquals("Redstone", c.categoryOf(facts("minecraft:polished_blackstone_pressure_plate", "@tab:building_blocks")));
		assertEquals("Nourriture", c.categoryOf(facts("minecraft:milk_bucket", "#c:buckets=12", "@tab:food_and_drinks")));
		// Modded items: tags, conventions and creative tabs.
		assertEquals("Outils", c.categoryOf(facts("othermod:ruby_pickaxe", "#minecraft:pickaxes=8", "@tool")));
		assertEquals("Minerais et lingots", c.categoryOf(facts("othermod:ruby", "#c:gems=9", "@tab:ingredients")));
		assertEquals("Redstone", c.categoryOf(facts("othermod:widget", "@block", "@tab:redstone_blocks")));
		assertEquals("Divers", c.categoryOf(facts("othermod:thing")));
	}

	@Test
	void legacyChestCategoriesAreRenamed() {
		StorageCategories c = defaults();
		assertEquals("Pierre", StorageDefaults.legacyRename("Pierre & terre", c));
		assertEquals("Minerais et lingots", StorageDefaults.legacyRename("minerais & lingots", c));
		assertEquals("Nourriture", StorageDefaults.legacyRename("Cultures & nourriture", c));
		assertEquals("Outils", StorageDefaults.legacyRename("Outils & armes", c));
		assertNull(StorageDefaults.legacyRename("Bois", c));
		assertNull(StorageDefaults.legacyRename("Mes trésors", c));
		// Still a category (customised set kept): nothing renamed.
		StorageCategories kept = new StorageCategories(StorageDefaults.legacyCategories(), "Divers");
		assertNull(StorageDefaults.legacyRename("Pierre & terre", kept));
	}
}
