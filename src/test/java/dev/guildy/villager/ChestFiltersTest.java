package dev.guildy.villager;

import dev.guildy.villager.ChestFilters.LabelPart;
import dev.guildy.villager.ChestFilters.Outcome;
import dev.guildy.villager.ChestFilters.PartKind;
import dev.guildy.villager.ChestFilters.Proposal;
import dev.guildy.villager.ChestFilters.Seen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Filter chests: families checked on every item id of Minecraft 26.3, inference from chest contents, scan proposals, names. */
class ChestFiltersTest {
	private static final List<String> ITEMS = new ArrayList<>();

	@BeforeAll
	static void loadItems() throws IOException {
		try (InputStream in = ChestFiltersTest.class.getResourceAsStream("items-26.3.txt")) {
			assertNotNull(in, "item list resource");
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				String id = line.trim();
				if (!id.isEmpty() && !id.startsWith("#")) {
					ITEMS.add("minecraft:" + id);
				}
			}
		}
		assertTrue(ITEMS.size() > 1500, "the whole item registry");
	}

	/** Paths of every item of the registry the filter accepts, sorted. */
	private static Set<String> accepted(ChestFilter filter) {
		Set<String> accepted = new TreeSet<>();
		for (String id : ITEMS) {
			if (filter.accepts(id)) {
				accepted.add(id.substring("minecraft:".length()));
			}
		}
		return accepted;
	}

	private static ChestFilter material(String family) {
		return ChestFilter.family(ChestFilter.Mode.MATERIAL, family, "");
	}

	private static ChestFilter type(String family) {
		return ChestFilter.family(ChestFilter.Mode.TYPE, family, "");
	}

	private static Set<String> set(String... ids) {
		return new TreeSet<>(List.of(ids));
	}

	// ================================================================ families on the real registry

	@Test
	void stoneFamiliesHoldTheirBlocksAndShapes() {
		assertEquals(set("diorite", "diorite_slab", "diorite_stairs", "diorite_wall", "polished_diorite", "polished_diorite_slab",
				"polished_diorite_stairs"), accepted(material("diorite")));
		assertEquals(set("granite", "granite_slab", "granite_stairs", "granite_wall", "polished_granite", "polished_granite_slab",
				"polished_granite_stairs"), accepted(material("granite")));
		assertEquals(set("andesite", "andesite_slab", "andesite_stairs", "andesite_wall", "polished_andesite", "polished_andesite_slab",
				"polished_andesite_stairs"), accepted(material("andesite")));
		Set<String> deepslate = accepted(material("deepslate"));
		assertEquals(21, deepslate.size(), deepslate.toString());
		assertTrue(deepslate.containsAll(set("deepslate", "cobbled_deepslate", "cobbled_deepslate_wall", "polished_deepslate", "deepslate_bricks",
				"cracked_deepslate_tiles", "deepslate_tile_stairs", "chiseled_deepslate")));
		assertFalse(deepslate.contains("deepslate_iron_ore"), "ores keep their own chests");
		assertFalse(deepslate.contains("reinforced_deepslate"));
		Set<String> stone = accepted(material("stone"));
		assertTrue(stone.containsAll(set("stone", "smooth_stone", "stone_bricks", "mossy_stone_brick_wall", "stone_button", "stone_pressure_plate")));
		assertFalse(stone.contains("stone_sword") || stone.contains("stone_pickaxe"), "tools are not stone blocks");
		assertFalse(stone.contains("end_stone") || stone.contains("cobblestone") || stone.contains("redstone"));
		assertEquals(set("cobblestone", "cobblestone_slab", "cobblestone_stairs", "cobblestone_wall", "infested_cobblestone", "mossy_cobblestone",
				"mossy_cobblestone_slab", "mossy_cobblestone_stairs", "mossy_cobblestone_wall"), accepted(material("cobblestone")));
		assertEquals(set("brick", "bricks", "brick_slab", "brick_stairs", "brick_wall"), accepted(material("brick")),
				"the brick family is not every *_bricks block");
	}

	@Test
	void sandstoneAndPrismarineKeepTheirVariantsApart() {
		Set<String> sandstone = accepted(material("sandstone"));
		assertEquals(set("sandstone", "chiseled_sandstone", "cut_sandstone", "cut_sandstone_slab", "smooth_sandstone", "smooth_sandstone_slab",
				"smooth_sandstone_stairs", "sandstone_slab", "sandstone_stairs", "sandstone_wall"), sandstone);
		assertTrue(accepted(material("red_sandstone")).containsAll(set("red_sandstone", "cut_red_sandstone", "smooth_red_sandstone_stairs")));
		assertEquals(set("prismarine", "prismarine_bricks", "prismarine_brick_slab", "prismarine_brick_stairs", "prismarine_crystals",
				"prismarine_shard", "prismarine_slab", "prismarine_stairs", "prismarine_wall"), accepted(material("prismarine")));
		assertTrue(accepted(material("dark_prismarine")).contains("dark_prismarine_slab"));
	}

	@Test
	void woodSetsStayApartFromDarkAndPaleOak() {
		Set<String> oak = accepted(material("oak"));
		assertEquals(set("oak_button", "oak_door", "oak_fence", "oak_fence_gate", "oak_hanging_sign", "oak_log", "oak_planks", "oak_pressure_plate",
				"oak_shelf", "oak_sign", "oak_slab", "oak_stairs", "oak_trapdoor", "oak_wood", "stripped_oak_log", "stripped_oak_wood"), oak);
		assertFalse(oak.contains("oak_boat") || oak.contains("oak_sapling") || oak.contains("oak_leaves"), "boats, saplings, leaves keep their chests");
		Set<String> spruce = accepted(material("spruce"));
		assertEquals(16, spruce.size(), spruce.toString());
		assertTrue(spruce.contains("stripped_spruce_wood") && spruce.contains("spruce_hanging_sign"));
		assertTrue(accepted(material("dark_oak")).contains("dark_oak_planks"));
		assertFalse(accepted(material("dark_oak")).contains("oak_planks"));
	}

	@Test
	void copperFamilyHoldsEveryOxidationAndWaxedState() {
		Set<String> copper = accepted(material("copper"));
		assertTrue(copper.containsAll(set("copper_block", "cut_copper", "exposed_copper", "weathered_cut_copper_slab", "oxidized_copper",
				"waxed_copper_block", "waxed_oxidized_cut_copper_stairs", "waxed_weathered_copper_bulb", "copper_ingot", "raw_copper",
				"copper_nugget", "copper_ore", "copper_door", "exposed_copper_trapdoor", "oxidized_copper_grate", "copper_lantern",
				"waxed_exposed_copper_golem_statue")), copper.toString());
		assertFalse(copper.contains("deepslate_copper_ore"));
		assertFalse(copper.contains("copper_sword") || copper.contains("copper_helmet") || copper.contains("copper_horse_armor"),
				"copper tools and armor go to their chests");
		assertFalse(copper.contains("lightning_rod"));
	}

	@Test
	void coloursByColourOrByType() {
		Set<String> white = accepted(material("white"));
		assertTrue(white.containsAll(set("white_wool", "white_carpet", "white_concrete", "white_concrete_powder", "white_terracotta",
				"white_glazed_terracotta", "white_stained_glass", "white_stained_glass_pane", "white_bed", "white_banner", "white_dye",
				"white_shulker_box", "white_candle", "white_wool_slab")), white.toString());
		assertFalse(white.contains("white_tulip"), "a flower is no white block");
		Set<String> gray = accepted(material("gray"));
		assertTrue(gray.contains("gray_wool"));
		assertFalse(gray.contains("light_gray_wool"), "light gray is another colour");
		assertTrue(accepted(material("light_blue")).contains("light_blue_concrete"));
		Set<String> wool = accepted(type("wool"));
		assertEquals(48, wool.size(), "16 colours of wool, wool slabs and wool stairs: " + wool);
		assertFalse(wool.contains("white_carpet"));
		Set<String> concrete = accepted(type("concrete"));
		assertTrue(concrete.contains("light_gray_concrete") && concrete.contains("red_concrete_powder"));
		assertFalse(concrete.contains("white_terracotta"));
	}

	@Test
	void seedsAndWheat() {
		assertEquals(set("wheat", "wheat_seeds"), accepted(material("wheat")));
		assertEquals(set("beetroot_seeds", "melon_seeds", "pumpkin_seeds", "torchflower_seeds", "wheat_seeds"), accepted(type("seeds")));
		assertFalse(accepted(material("wheat")).contains("hay_block"));
		assertTrue(accepted(type("slab")).containsAll(set("oak_slab", "diorite_slab", "red_wool_slab")));
	}

	@Test
	void metals() {
		assertEquals(set("iron_bars", "iron_block", "iron_chain", "iron_door", "iron_ingot", "iron_nugget", "iron_ore", "iron_trapdoor", "raw_iron",
				"raw_iron_block"), accepted(material("iron")));
		assertEquals(set("gold_block", "gold_ingot", "gold_nugget", "gold_ore", "raw_gold", "raw_gold_block"), accepted(material("gold")));
		Set<String> ingots = accepted(type("ingot"));
		assertTrue(ingots.containsAll(set("iron_ingot", "gold_ingot", "copper_ingot", "netherite_ingot")));
	}

	// ================================================================ inference

	private static ChestFilter infer(String... ids) {
		return ChestFilters.infer(List.of(ids), 9);
	}

	@Test
	void familiesAreInferredFromChestContents() {
		ChestFilter diorite = infer("diorite", "polished_diorite", "diorite_slab", "diorite_stairs", "diorite_wall");
		assertNotNull(diorite);
		assertEquals(ChestFilter.Mode.MATERIAL, diorite.mode);
		assertEquals("diorite", diorite.family);
		assertEquals("minecraft:diorite", diorite.icon, "the first item seen is the icon");
		assertEquals("material:granite", infer("polished_granite", "granite").key());
		assertEquals("material:oak", infer("oak_log", "oak_planks", "stripped_oak_log", "oak_stairs").key());
		assertEquals("material:copper", infer("copper_block", "exposed_copper", "waxed_oxidized_cut_copper_slab", "copper_ingot", "raw_copper").key());
		assertEquals("material:deepslate", infer("cobbled_deepslate", "deepslate_tiles", "polished_deepslate_wall").key());
		assertEquals("material:white", infer("white_wool", "white_carpet", "white_concrete").key(), "by colour");
		assertEquals("type:wool", infer("white_wool", "orange_wool", "light_blue_wool").key(), "by type");
		assertEquals("type:concrete", infer("red_concrete", "blue_concrete").key());
		assertEquals("material:wheat", infer("wheat", "wheat_seeds").key());
		assertEquals("type:seeds", infer("wheat_seeds", "beetroot_seeds", "melon_seeds").key());
		assertEquals("type:ingot", infer("iron_ingot", "gold_ingot").key());
		assertEquals("type:shulker_box", infer("white_shulker_box", "red_shulker_box").key(), "the longest common type words");
		assertEquals("type:sword", infer("iron_sword", "diamond_sword").key());
	}

	@Test
	void noFamilyGivesTheExactItems() {
		// The server owner's screenshot chest: wheat, sugar cane and seeds have no common family.
		ChestFilter farm = infer("wheat", "sugar_cane", "wheat_seeds");
		assertEquals(ChestFilter.Mode.ITEMS, farm.mode);
		assertEquals(List.of("minecraft:wheat", "minecraft:sugar_cane", "minecraft:wheat_seeds"), farm.items);
		assertTrue(farm.accepts("minecraft:sugar_cane") && !farm.accepts("minecraft:beetroot_seeds"));
		assertEquals(ChestFilter.Mode.ITEMS, infer("sandstone", "red_sandstone").mode, "sandstone and red sandstone are two families");
		assertEquals(ChestFilter.Mode.ITEMS, infer("oak_planks", "dark_oak_log").mode, "oak and dark oak are two families");
		assertEquals("type:log", infer("oak_log", "dark_oak_log").key(), "two kinds of logs: every log");
		assertEquals(ChestFilter.Mode.ITEMS, infer("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots").mode);
		// One kind of item: exactly that item (a family needs two different items).
		ChestFilter single = infer("diorite");
		assertEquals(List.of("minecraft:diorite"), single.items);
		assertFalse(single.accepts("polished_diorite"));
		// Too many different items without a family: no filter.
		assertNull(ChestFilters.infer(List.of("stone", "oak_planks", "iron_ingot", "bread", "bone", "string", "redstone", "diamond", "apple", "torch"), 9));
		assertNull(ChestFilters.infer(List.of(), 9));
	}

	@Test
	void idsAreNormalized() {
		ChestFilter a = ChestFilter.items(List.of("Minecraft:Granite", "diorite", "diorite"));
		ChestFilter b = ChestFilter.items(List.of("minecraft:diorite", "minecraft:granite"));
		assertEquals(a.key(), b.key(), "same items in any order = same filter");
		assertEquals(2, a.items.size());
		assertTrue(a.accepts("granite") && a.accepts("minecraft:granite"));
		assertTrue(material("diorite").accepts("othermod:diorite_tiles"), "a family works for every namespace");
		assertEquals("stone_brick", ChestFilters.normalizeFamily("Stone Bricks"));
		ChestFilter broken = new ChestFilter();
		assertFalse(broken.sanitize(), "a filter without items accepts nothing");
	}

	// ================================================================ scan proposals

	@Test
	void scanProposesAFilterACategoryOrNothing() {
		assertEquals(Outcome.EMPTY, ChestFilters.propose(List.of(), 9, 75, "Divers").outcome());
		Proposal granite = ChestFilters.propose(List.of(new Seen("minecraft:granite", "Pierre", 10), new Seen("minecraft:polished_granite", "Pierre", 3)),
				9, 75, "Divers");
		assertEquals(Outcome.FILTER, granite.outcome());
		assertEquals("material:granite", granite.filter().key());
		// 10 different foods and one tool: too mixed for a filter, mostly "Nourriture".
		List<Seen> food = new ArrayList<>();
		for (String id : List.of("bread", "apple", "carrot", "potato", "beef", "cookie", "melon_slice", "pumpkin_pie", "cake", "sweet_berries")) {
			food.add(new Seen("minecraft:" + id, "Nourriture", 2));
		}
		food.add(new Seen("minecraft:shears", "Outils", 1));
		Proposal category = ChestFilters.propose(food, 9, 75, "Divers");
		assertEquals(Outcome.CATEGORY, category.outcome());
		assertEquals("Nourriture", category.category());
		// Half food, half tools: left as it is.
		List<Seen> mixed = new ArrayList<>(food.subList(0, 6));
		for (String id : List.of("shears", "bucket", "compass", "clock", "spyglass", "brush")) {
			mixed.add(new Seen("minecraft:" + id, "Outils", 2));
		}
		assertEquals(Outcome.MIXED, ChestFilters.propose(mixed, 9, 75, "Divers").outcome());
		// Mostly items without category: never a "Divers" chest from the scan.
		List<Seen> misc = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			misc.add(new Seen("mod:thing_" + i, "Divers", 1));
		}
		assertEquals(Outcome.MIXED, ChestFilters.propose(misc, 9, 75, "Divers").outcome());
	}

	@Test
	void scanProposesTheMostPreciseCategoryReachingTheShare() {
		List<String> books = List.of("Livres et enchantements");
		// Ten kinds of enchanted books (different ids of a mod) only: the sub-category.
		List<Seen> enchanted = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			enchanted.add(new Seen("mod:tome_" + i, "Livres enchantés", 2, books));
		}
		assertEquals("Livres enchantés", ChestFilters.propose(enchanted, 9, 75, "Divers").category());
		// Half enchanted books, half paper and books: no sub-category reaches 75 %, their parent does.
		List<Seen> mixed = new ArrayList<>(enchanted.subList(0, 5));
		for (int i = 0; i < 5; i++) {
			mixed.add(new Seen("mod:page_" + i, "Livres et papier", 2, books));
		}
		Proposal parent = ChestFilters.propose(mixed, 9, 75, "Divers");
		assertEquals(Outcome.CATEGORY, parent.outcome());
		assertEquals("Livres et enchantements", parent.category());
	}

	// ================================================================ names

	@Test
	void namesUseFamilyTextsThenItemNames() {
		Set<String> lang = Set.of("villager.storage.family.oak", "villager.storage.family.wheat", "villager.storage.family.wool");
		Set<String> items = new TreeSet<>(ITEMS);
		assertEquals(List.of(new LabelPart(PartKind.ITEM, "minecraft:diorite", 0)),
				ChestFilters.label(material("diorite"), lang::contains, items::contains), "a family named like an item: that item's name");
		assertEquals(List.of(new LabelPart(PartKind.LANG, "villager.storage.family.oak", 0)),
				ChestFilters.label(material("oak"), lang::contains, items::contains), "no 'oak' item: the French family name");
		assertEquals(PartKind.LANG, ChestFilters.label(material("wheat"), lang::contains, items::contains).getFirst().kind(),
				"a family text wins over the item name (Blé et graines)");
		assertEquals(List.of(new LabelPart(PartKind.TEXT, "Mystery stone", 0)),
				ChestFilters.label(material("mystery_stone"), lang::contains, items::contains));
		List<LabelPart> two = ChestFilters.label(ChestFilter.items(List.of("wheat", "sugar_cane")), lang::contains, items::contains);
		assertEquals(List.of(new LabelPart(PartKind.ITEM, "minecraft:wheat", 0), new LabelPart(PartKind.LANG, "villager.storage.filter.name.and", 0),
				new LabelPart(PartKind.ITEM, "minecraft:sugar_cane", 0)), two);
		List<LabelPart> three = ChestFilters.label(ChestFilter.items(List.of("wheat", "sugar_cane", "wheat_seeds")), lang::contains, items::contains);
		assertEquals(5, three.size());
		assertEquals("villager.storage.filter.name.comma", three.get(1).value());
		List<LabelPart> five = ChestFilters.label(ChestFilter.items(List.of("a", "b", "c", "d", "e")), lang::contains, items::contains);
		assertEquals(new LabelPart(PartKind.LANG, "villager.storage.filter.name.others", 3), five.getLast(), "A, B et 3 autres");
		assertEquals("Dark oak", ChestFilters.pretty("dark_oak"));
	}
}
