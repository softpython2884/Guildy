package dev.guildy.villager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Default storage categories of villagers.json5, their validation and the migration of the older default set (pure:
 * no Minecraft classes, unit tested).
 * <p>
 * Overlaps are settled by the rule kinds (see {@link StorageCategories}): mob drops are listed by id in "Butin de
 * monstres" (so rotten flesh, spider eyes, blaze rods, shulker shells... never reach "Nourriture", "Nether" or
 * "End"), and the generic rules (item properties, creative tabs) only catch what no precise rule took.
 * <p>
 * Since configVersion 8 the defaults also hold sub-categories (they only refine their parent: "Livres enchantés" takes
 * the enchanted books of "Livres et enchantements") and transversal categories (they take their items from every
 * category: "Équipement enchanté", "Objets rares", "Ingrédients d'alchimie"). Older files receive the default categories
 * they do not know yet once ({@code storage.knownCategories}).
 */
public final class StorageDefaults {
	/** Category names of the older default set (before configVersion 3) → their new equivalent. */
	public static final Map<String, String> LEGACY_NAMES = Map.of(
			"Pierre & terre", "Pierre",
			"Minerais & lingots", "Minerais et lingots",
			"Cultures & nourriture", "Nourriture",
			"Outils & armes", "Outils");
	public static final String DEFAULT_FALLBACK = "Divers";
	/** Default categories of the files written before configVersion 8 (Guildy 1.11 and older). */
	public static final List<String> V7_NAMES = List.of("Bois", "Pierre", "Terre, sable et gravier", "Blocs de construction",
			"Mobilier et décoration", "Minerais et lingots", "Redstone", "Outils", "Armes", "Armures", "Livres et enchantements",
			"Potions et alchimie", "Nourriture", "Graines et pousses", "Plantes et fleurs", "Butin de monstres", "Nether", "End",
			"Colorants");
	/** Version of villagers.json5 that added the sub-categories and the transversal categories. */
	public static final int HIERARCHY_VERSION = 8;

	private StorageDefaults() {
	}

	/** The default category set (config order = menu order). */
	public static LinkedHashMap<String, StorageCategory> categories() {
		LinkedHashMap<String, StorageCategory> map = new LinkedHashMap<>();
		add(map, "Bois", "minecraft:oak_log",
				"Bûches, bois, planches, escaliers et dalles en bois, barrières, portillons, portes, trappes, panneaux, bâtons, bols",
				"#minecraft:logs", "#minecraft:planks", "#minecraft:wooden_slabs", "#minecraft:wooden_stairs",
				"#minecraft:wooden_fences", "#minecraft:fence_gates", "#minecraft:wooden_doors", "#minecraft:wooden_trapdoors",
				"#minecraft:signs", "#minecraft:hanging_signs", "#minecraft:bamboo_blocks", "#c:natural_logs", "#c:natural_woods",
				"#c:stripped_logs", "#c:stripped_woods", "#c:fences/wooden", "#c:fence_gates/wooden",
				"minecraft:stick", "minecraft:bowl", "minecraft:petrified_oak_slab", "bamboo_mosaic*");
		add(map, "Pierre", "minecraft:cobblestone",
				"Pierre, roche, pierre des abîmes, andésite, diorite, granite, tuf, calcite, grès, cinabre, soufre, pierre taillée et briques de pierre",
				"minecraft:stone", "minecraft:stone_stairs", "minecraft:stone_slab", "minecraft:bedrock", "minecraft:infested_stone",
				"#minecraft:stone_bricks", "#c:stones", "#c:cobblestones",
				"*cobblestone*", "*stone_brick*", "smooth_stone*", "*granite*", "*diorite*", "*andesite*", "*deepslate*", "*tuff*",
				"*calcite*", "*dripstone*", "*sandstone*", "*cinnabar*", "*sulfur*", "infested_*");
		add(map, "Terre, sable et gravier", "minecraft:dirt",
				"Terre, herbe, podzol, mycélium, boue, argile, sable, gravier, neige, glace",
				"#minecraft:dirt", "#minecraft:grass_blocks", "#minecraft:sand", "#minecraft:mud", "#c:sands", "#c:gravels",
				"minecraft:gravel", "minecraft:suspicious_gravel", "minecraft:clay", "minecraft:clay_ball", "minecraft:farmland",
				"minecraft:dirt_path", "minecraft:snow", "minecraft:snow_block", "minecraft:snowball", "minecraft:ice",
				"minecraft:packed_ice", "minecraft:blue_ice");
		add(map, "Blocs de construction", "minecraft:bricks",
				"Briques, verre, vitres, béton, terre cuite, laine, tapis, cuivre, prismarine, résine, barreaux, dalles, escaliers et murets",
				"#minecraft:terracotta", "#minecraft:glazed_terracotta", "#minecraft:concrete", "#minecraft:concrete_powders",
				"#minecraft:concrete_slabs", "#minecraft:concrete_stairs", "#minecraft:wool", "#minecraft:wool_slabs",
				"#minecraft:wool_stairs", "#minecraft:wool_carpets", "#minecraft:copper", "#minecraft:bars", "#c:glass_blocks",
				"#c:glass_panes", "#c:concretes", "#c:glazed_terracottas",
				"minecraft:packed_mud", "minecraft:honeycomb_block", "minecraft:resin_block",
				"brick*", "*mud_brick*", "*prismarine*", "*resin*", "*glass", "*glass_pane", "*cut_copper*", "*chiseled_copper*",
				"*copper_grate*", "*copper_door*", "*copper_trapdoor*",
				"@tab:building_blocks", "@tab:colored_blocks");
		sub(map, "Laine et tapis", "minecraft:white_wool", "Blocs de construction",
				"Laine, tapis, dalles et escaliers en laine des 16 couleurs",
				"#minecraft:wool", "#minecraft:wool_carpets", "#minecraft:wool_slabs", "#minecraft:wool_stairs");
		sub(map, "Verre", "minecraft:glass", "Blocs de construction",
				"Verre, verre teinté et vitres de toutes les couleurs",
				"#c:glass_blocks", "#c:glass_panes", "*glass", "*glass_pane");
		sub(map, "Béton et terre cuite", "minecraft:white_concrete", "Blocs de construction",
				"Béton, béton en poudre, terre cuite et terre cuite émaillée",
				"#minecraft:terracotta", "#minecraft:glazed_terracotta", "#minecraft:concrete", "#minecraft:concrete_powders",
				"#minecraft:concrete_slabs", "#minecraft:concrete_stairs", "#c:concretes", "#c:glazed_terracottas");
		add(map, "Mobilier et décoration", "minecraft:lantern",
				"Établis, fours, coffres, tonneaux, enclumes, lits, torches, lanternes, bougies, chaînes, échelles, tableaux, cadres, pots, bannières, tessons, disques",
				"#minecraft:beds", "#minecraft:candles", "#minecraft:banners", "#minecraft:lanterns", "#minecraft:chains",
				"#minecraft:wooden_shelves", "#minecraft:copper_chests", "#minecraft:copper_golem_statues", "#minecraft:anvil",
				"#minecraft:cushions", "#minecraft:decorated_pot_sherds", "#c:chests", "#c:barrels", "#c:froglights",
				"#c:music_discs", "#c:player_workstations/crafting_tables", "#c:player_workstations/furnaces", "#c:villager_job_sites",
				"minecraft:crafting_table", "minecraft:furnace", "minecraft:smoker", "minecraft:blast_furnace", "minecraft:stonecutter",
				"minecraft:cartography_table", "minecraft:fletching_table", "minecraft:smithing_table", "minecraft:grindstone",
				"minecraft:loom", "minecraft:composter", "minecraft:chest", "minecraft:barrel", "minecraft:campfire",
				"minecraft:soul_campfire", "minecraft:torch", "minecraft:soul_torch", "minecraft:copper_torch", "minecraft:sea_lantern",
				"minecraft:jack_o_lantern", "minecraft:carved_pumpkin", "minecraft:ladder", "minecraft:scaffolding", "minecraft:bell",
				"minecraft:beacon", "minecraft:conduit", "minecraft:lodestone", "minecraft:jukebox", "minecraft:disc_fragment_5",
				"minecraft:flower_pot", "minecraft:decorated_pot", "minecraft:painting", "minecraft:item_frame",
				"minecraft:glow_item_frame", "minecraft:armor_stand", "minecraft:bee_nest", "minecraft:beehive", "minecraft:straw_bed",
				"*_banner_pattern", "*_pottery_sherd", "*froglight",
				"@music_disc", "@banner_pattern", "@tab:functional_blocks");
		sub(map, "Éclairage", "minecraft:lantern", "Mobilier et décoration",
				"Torches, lanternes, bougies, feux de camp, lanternes aquatiques, citrouilles-lanternes, champilampes",
				"#minecraft:candles", "#minecraft:lanterns", "#c:froglights",
				"minecraft:torch", "minecraft:soul_torch", "minecraft:copper_torch", "minecraft:sea_lantern",
				"minecraft:jack_o_lantern", "minecraft:campfire", "minecraft:soul_campfire", "*froglight");
		add(map, "Minerais et lingots", "minecraft:iron_ingot",
				"Minerais, minerais bruts, lingots, pépites, diamants, émeraudes, lapis, charbon, cuivre, netherite, améthyste, blocs de minerai, silex",
				"#minecraft:ores", "#minecraft:coals", "#minecraft:metal_nuggets", "#c:ores", "#c:ingots", "#c:nuggets",
				"#c:raw_materials", "#c:gems", "#c:storage_blocks",
				"minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper", "minecraft:iron_ingot", "minecraft:gold_ingot",
				"minecraft:copper_ingot", "minecraft:netherite_ingot", "minecraft:netherite_scrap", "minecraft:ancient_debris",
				"minecraft:diamond", "minecraft:emerald", "minecraft:lapis_lazuli", "minecraft:flint", "minecraft:coal_block",
				"minecraft:iron_block", "minecraft:gold_block", "minecraft:diamond_block", "minecraft:emerald_block",
				"minecraft:lapis_block", "minecraft:netherite_block", "minecraft:raw_iron_block", "minecraft:raw_gold_block",
				"minecraft:raw_copper_block", "minecraft:copper_block", "minecraft:waxed_copper_block",
				"*amethyst*", "*_ore", "@tab:ingredients");
		add(map, "Redstone", "minecraft:redstone",
				"Poudre et blocs de redstone, torches, répéteurs, comparateurs, pistons, leviers, boutons, plaques de pression, trémies, observateurs, rails, wagonnets, TNT",
				"#minecraft:buttons", "#minecraft:wooden_pressure_plates", "#minecraft:rails", "#minecraft:lightning_rods",
				"#c:dusts/redstone",
				"minecraft:redstone", "minecraft:redstone_block", "minecraft:redstone_torch", "minecraft:redstone_lamp",
				"minecraft:repeater", "minecraft:comparator", "minecraft:observer", "minecraft:piston", "minecraft:sticky_piston",
				"minecraft:dispenser", "minecraft:dropper", "minecraft:crafter", "minecraft:hopper", "minecraft:lever",
				"minecraft:tripwire_hook", "minecraft:daylight_detector", "minecraft:target", "minecraft:note_block",
				"minecraft:trapped_chest", "minecraft:tnt", "minecraft:slime_block", "minecraft:honey_block", "minecraft:iron_door",
				"minecraft:iron_trapdoor", "minecraft:stone_pressure_plate", "minecraft:polished_blackstone_pressure_plate",
				"minecraft:light_weighted_pressure_plate", "minecraft:heavy_weighted_pressure_plate", "minecraft:sculk_sensor",
				"minecraft:calibrated_sculk_sensor",
				"*minecart", "*copper_bulb", "*sculk*", "@tab:redstone_blocks");
		add(map, "Outils", "minecraft:iron_pickaxe",
				"Pioches, haches, pelles, houes, cisailles, briquets, cannes à pêche, seaux, boussoles, horloges, cartes, longues-vues, laisses, selles, bateaux, sacs, fusées",
				"#minecraft:pickaxes", "#minecraft:axes", "#minecraft:shovels", "#minecraft:hoes", "#minecraft:boats",
				"#minecraft:bundles", "#minecraft:harnesses", "#minecraft:compasses", "#c:tools/mining_tool", "#c:tools/fishing_rod",
				"#c:tools/brush", "#c:tools/igniter", "#c:tools/shear", "#c:tools/wrench", "#c:buckets",
				"minecraft:shears", "minecraft:flint_and_steel", "minecraft:fire_charge", "minecraft:fishing_rod",
				"minecraft:carrot_on_a_stick", "minecraft:warped_fungus_on_a_stick", "minecraft:brush", "minecraft:spyglass",
				"minecraft:clock", "minecraft:map", "minecraft:lead", "minecraft:name_tag", "minecraft:saddle", "minecraft:bucket",
				"minecraft:goat_horn", "minecraft:firework_rocket", "minecraft:firework_star",
				"*_bucket", "*_map", "@tool", "@tab:tools_and_utilities");
		sub(map, "Cartes et boussoles", "minecraft:filled_map", "Outils",
				"Cartes vierges et remplies, cartes au trésor, boussoles, boussoles de récupération, horloges, longues-vues",
				"minecraft:map", "minecraft:filled_map", "*_map", "#minecraft:compasses", "minecraft:compass",
				"minecraft:recovery_compass", "minecraft:clock", "minecraft:spyglass");
		add(map, "Armes", "minecraft:iron_sword",
				"Épées, lances, arcs, arbalètes, tridents, masses, flèches, charges de vent",
				"#minecraft:swords", "#minecraft:spears", "#minecraft:arrows", "#c:tools/melee_weapon", "#c:tools/ranged_weapon",
				"#c:tools/trident", "#c:tools/mace",
				"minecraft:bow", "minecraft:crossbow", "minecraft:trident", "minecraft:mace", "minecraft:wind_charge",
				"@weapon", "@tab:combat");
		add(map, "Armures", "minecraft:iron_chestplate",
				"Casques, plastrons, jambières, bottes, boucliers, élytres, armures de cheval, de loup et de nautile, modèles de forge",
				"#minecraft:head_armor", "#minecraft:chest_armor", "#minecraft:leg_armor", "#minecraft:foot_armor", "#c:armors",
				"#c:tools/shield",
				"minecraft:shield", "minecraft:elytra", "minecraft:wolf_armor",
				"*horse_armor", "*nautilus_armor", "*_smithing_template", "@armor", "@smithing_template");
		add(map, "Livres et enchantements", "minecraft:enchanted_book",
				"Livres enchantés, livres, livres et plumes, papier, fioles d'expérience, tables d'enchantement, bibliothèques, pupitres",
				"#minecraft:bookshelf_books", "#minecraft:lectern_books", "#c:bookshelves",
				"minecraft:book", "minecraft:writable_book", "minecraft:written_book", "minecraft:knowledge_book",
				"minecraft:enchanted_book", "minecraft:paper", "minecraft:experience_bottle", "minecraft:enchanting_table",
				"minecraft:bookshelf", "minecraft:chiseled_bookshelf", "minecraft:lectern",
				"@enchanted_book");
		sub(map, "Livres enchantés", "minecraft:enchanted_book", "Livres et enchantements",
				"Livres enchantés seulement : le bâtiment des enchantements",
				"minecraft:enchanted_book", "@enchanted_book");
		sub(map, "Parchemins et grimoires", "minecraft:knowledge_book", "Livres et enchantements",
				"Parchemins de sort, grimoires et parchemins d'incantation de Guildy",
				"@magic");
		sub(map, "Livres et papier", "minecraft:book", "Livres et enchantements",
				"Livres, livres et plumes, livres écrits, papier",
				"minecraft:book", "minecraft:writable_book", "minecraft:written_book", "minecraft:knowledge_book", "minecraft:paper");
		add(map, "Potions et alchimie", "minecraft:potion",
				"Potions, potions jetables et persistantes, fioles, alambics, chaudrons, poudre de blaze, œil d'araignée fermenté, melon scintillant, souffle de dragon",
				"#minecraft:brewing_potion_inputs", "#c:potions",
				"minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:glass_bottle",
				"minecraft:brewing_stand", "minecraft:cauldron", "minecraft:blaze_powder", "minecraft:fermented_spider_eye",
				"minecraft:glistering_melon_slice", "minecraft:dragon_breath", "minecraft:ominous_bottle",
				"@potion");
		sub(map, "Potions", "minecraft:potion", "Potions et alchimie",
				"Potions, potions jetables et persistantes, fioles sinistres",
				"minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:ominous_bottle",
				"#c:potions", "@potion");
		add(map, "Nourriture", "minecraft:bread",
				"Viandes, poissons, pain, fruits, légumes, gâteaux, tartes, soupes, œufs, lait, sucre, blé, citrouilles, pastèques",
				"#minecraft:eggs", "#minecraft:fishes", "#minecraft:meat", "#c:foods", "#c:crops",
				"minecraft:cake", "minecraft:milk_bucket", "minecraft:sugar", "minecraft:wheat", "minecraft:hay_block",
				"minecraft:pumpkin", "minecraft:melon", "minecraft:dried_kelp_block",
				"@food", "@tab:food_and_drinks");
		sub(map, "Cultures", "minecraft:wheat", "Nourriture",
				"Récoltes des champs : blé, carottes, pommes de terre, betteraves, pastèques, citrouilles, baies, bottes de foin",
				"minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:poisonous_potato", "minecraft:beetroot",
				"minecraft:melon_slice", "minecraft:melon", "minecraft:pumpkin", "minecraft:hay_block", "minecraft:sweet_berries",
				"minecraft:glow_berries", "#c:crops");
		add(map, "Graines et pousses", "minecraft:wheat_seeds",
				"Graines, pousses d'arbres, propagules, azalées, fèves de cacao, cosses de sarracénie",
				"#minecraft:saplings", "#c:seeds",
				"minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:melon_seeds", "minecraft:pumpkin_seeds",
				"minecraft:torchflower_seeds", "minecraft:pitcher_pod", "minecraft:cocoa_beans", "minecraft:mangrove_propagule",
				"*_seeds", "*_sapling");
		add(map, "Plantes et fleurs", "minecraft:poppy",
				"Fleurs, feuilles, herbes, fougères, buissons, lianes, mousses, champignons, coraux, algues, cactus, canne à sucre, bambou, nénuphars",
				"#minecraft:flowers", "#minecraft:small_flowers", "#minecraft:leaves", "#minecraft:moss_blocks", "#minecraft:mushrooms",
				"#c:flowers", "#c:mushrooms",
				"minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern", "minecraft:short_dry_grass",
				"minecraft:tall_dry_grass", "minecraft:bush", "minecraft:red_shrub", "minecraft:dead_bush", "minecraft:firefly_bush",
				"minecraft:leaf_litter", "minecraft:vine", "minecraft:glow_lichen", "minecraft:hanging_roots",
				"minecraft:mangrove_roots", "minecraft:lily_pad", "minecraft:seagrass", "minecraft:sea_pickle", "minecraft:kelp",
				"minecraft:bamboo", "minecraft:sugar_cane", "minecraft:cactus", "minecraft:big_dripleaf", "minecraft:small_dripleaf",
				"minecraft:moss_carpet", "minecraft:pale_moss_carpet", "minecraft:pale_hanging_moss", "minecraft:brown_mushroom_block",
				"minecraft:red_mushroom_block", "minecraft:mushroom_stem",
				"*coral*", "*_leaves", "@tab:natural_blocks");
		add(map, "Butin de monstres", "minecraft:skeleton_skull",
				"Os, ficelle, poudre à canon, chair putréfiée, yeux d'araignée, slime, perles de l'Ender, bâtons de blaze, larmes de ghast, membranes, cuir, plumes, peaux de lapin, poches d'encre, têtes et crânes, écailles, carapaces de shulker, coquilles de nautile, totems",
				"#minecraft:skulls", "#c:bones", "#c:feathers", "#c:leathers", "#c:strings", "#c:gunpowders", "#c:slime_balls",
				"#c:ender_pearls", "#c:nether_stars", "#c:rods/blaze", "#c:rods/breeze",
				"minecraft:rotten_flesh", "minecraft:bone", "minecraft:bone_meal", "minecraft:bone_block", "minecraft:string",
				"minecraft:cobweb", "minecraft:spider_eye", "minecraft:gunpowder", "minecraft:ender_pearl", "minecraft:slime_ball",
				"minecraft:magma_cream", "minecraft:phantom_membrane", "minecraft:blaze_rod", "minecraft:breeze_rod",
				"minecraft:ghast_tear", "minecraft:leather", "minecraft:feather", "minecraft:rabbit_hide", "minecraft:rabbit_foot",
				"minecraft:ink_sac", "minecraft:glow_ink_sac", "minecraft:turtle_scute", "minecraft:armadillo_scute",
				"minecraft:shulker_shell", "minecraft:nautilus_shell", "minecraft:heart_of_the_sea", "minecraft:prismarine_shard",
				"minecraft:prismarine_crystals", "minecraft:totem_of_undying", "minecraft:nether_star", "minecraft:echo_shard",
				"minecraft:trial_key", "minecraft:ominous_trial_key", "minecraft:heavy_core", "minecraft:honeycomb",
				"minecraft:turtle_egg", "minecraft:sniffer_egg", "minecraft:dried_ghast", "minecraft:frogspawn", "minecraft:sponge",
				"minecraft:wet_sponge", "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault", "minecraft:creaking_heart",
				"*_spawn_egg", "@spawn_egg", "@tab:spawn_eggs");
		add(map, "Nether", "minecraft:netherrack",
				"Netherrack, briques du Nether, quartz, sable et terre des âmes, basalte, roche noire, verrues, pierre lumineuse, champignons et racines du Nether, obsidienne, magma",
				"#minecraft:soul_fire_base_blocks", "#minecraft:wart_blocks", "#c:netherracks", "#c:obsidians",
				"minecraft:netherrack", "minecraft:quartz", "minecraft:nether_wart", "minecraft:glowstone", "minecraft:glowstone_dust",
				"minecraft:shroomlight", "minecraft:magma_block", "minecraft:obsidian", "minecraft:crying_obsidian",
				"minecraft:respawn_anchor", "minecraft:crimson_nylium", "minecraft:warped_nylium", "minecraft:crimson_fungus",
				"minecraft:warped_fungus", "minecraft:crimson_roots", "minecraft:warped_roots", "minecraft:nether_sprouts",
				"minecraft:weeping_vines", "minecraft:twisting_vines", "minecraft:gilded_blackstone",
				"*nether_brick*", "*blackstone*", "*polished_blackstone*", "*basalt*", "*quartz*");
		add(map, "End", "minecraft:end_stone",
				"Pierre de l'End, purpur, chorus, barres de l'End, yeux de l'Ender, cristaux de l'End, coffres de l'Ender, boîtes de Shulker, œuf de dragon",
				"#minecraft:shulker_boxes", "#c:end_stones", "#c:shulker_boxes",
				"minecraft:end_stone", "minecraft:end_stone_bricks", "minecraft:end_stone_brick_slab",
				"minecraft:end_stone_brick_stairs", "minecraft:end_stone_brick_wall", "minecraft:chorus_fruit",
				"minecraft:popped_chorus_fruit", "minecraft:chorus_plant", "minecraft:chorus_flower", "minecraft:end_rod",
				"minecraft:end_crystal", "minecraft:ender_eye", "minecraft:ender_chest", "minecraft:dragon_egg",
				"minecraft:end_portal_frame",
				"purpur_*");
		add(map, "Colorants", "minecraft:red_dye",
				"Colorants des 16 couleurs",
				"#minecraft:dyes", "#c:dyes", "@dye");
		sub(map, "Équipement enchanté", "minecraft:diamond_sword", StorageCategory.TRANSVERSAL,
				"Outils, armes et armures enchantés (pris dans toutes les catégories)",
				"@enchanted");
		sub(map, "Objets rares", "minecraft:nether_star", StorageCategory.TRANSVERSAL,
				"Disques, têtes et crânes, totems, étoiles du Nether, cœurs de la mer, élytres, œuf de dragon, modèles de forge, pommes dorées enchantées",
				"@music_disc", "#c:music_discs", "minecraft:disc_fragment_5", "#minecraft:skulls", "minecraft:totem_of_undying",
				"minecraft:nether_star", "minecraft:heart_of_the_sea", "minecraft:dragon_egg", "minecraft:elytra",
				"minecraft:heavy_core", "minecraft:enchanted_golden_apple", "minecraft:echo_shard", "minecraft:sniffer_egg",
				"minecraft:beacon", "minecraft:conduit", "*_smithing_template", "@smithing_template");
		sub(map, "Ingrédients d'alchimie", "minecraft:brewing_stand", StorageCategory.TRANSVERSAL,
				"Poudre de blaze, verrues du Nether, larmes de ghast, crème de magma, yeux d'araignée, melon scintillant, pattes de lapin, membranes, souffle de dragon, fioles",
				"minecraft:blaze_powder", "minecraft:nether_wart", "minecraft:ghast_tear", "minecraft:magma_cream",
				"minecraft:fermented_spider_eye", "minecraft:spider_eye", "minecraft:glistering_melon_slice", "minecraft:rabbit_foot",
				"minecraft:phantom_membrane", "minecraft:dragon_breath", "minecraft:glass_bottle");
		return map;
	}

	private static void add(Map<String, StorageCategory> map, String name, String icon, String description, String... rules) {
		map.put(name, new StorageCategory(icon, description, List.of(rules)));
	}

	/** A sub-category ({@code parent} = its parent's name) or a transversal category ({@code parent} = "*"). */
	private static void sub(Map<String, StorageCategory> map, String name, String icon, String parent, String description, String... rules) {
		map.put(name, new StorageCategory(icon, description, parent, List.of(rules)));
	}

	/** The default set of Guildy 1.1 (before configVersion 3): replaced by {@link #categories()} when untouched. */
	public static LinkedHashMap<String, List<String>> legacyCategories() {
		LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
		map.put("Bois", List.of(
				"#minecraft:logs", "#minecraft:planks", "#minecraft:wooden_slabs", "#minecraft:wooden_stairs",
				"#minecraft:wooden_fences", "#minecraft:saplings", "#minecraft:leaves", "minecraft:stick"));
		map.put("Pierre & terre", List.of(
				"#minecraft:stone_crafting_materials", "#minecraft:stone_tool_materials", "#minecraft:dirt", "#minecraft:grass_blocks",
				"#minecraft:mud", "#minecraft:moss_blocks", "#minecraft:sand",
				"#minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:stone", "minecraft:cobbled_deepslate",
				"minecraft:deepslate", "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
				"minecraft:calcite", "minecraft:gravel", "minecraft:clay_ball", "minecraft:netherrack"));
		map.put("Minerais & lingots", List.of(
				"#minecraft:coals", "#minecraft:iron_ores", "#minecraft:gold_ores", "#minecraft:copper_ores",
				"#minecraft:coal_ores", "#minecraft:diamond_ores", "#minecraft:emerald_ores", "#minecraft:lapis_ores",
				"#minecraft:redstone_ores", "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper",
				"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot", "minecraft:iron_nugget",
				"minecraft:gold_nugget", "minecraft:diamond", "minecraft:emerald", "minecraft:lapis_lazuli",
				"minecraft:redstone", "minecraft:quartz", "minecraft:amethyst_shard", "minecraft:flint"));
		map.put("Cultures & nourriture", List.of(
				"#minecraft:villager_plantable_seeds", "#minecraft:meat", "#minecraft:fishes", "minecraft:wheat",
				"minecraft:carrot", "minecraft:potato", "minecraft:beetroot", "minecraft:bread", "minecraft:apple",
				"minecraft:melon_slice", "minecraft:pumpkin", "minecraft:sugar_cane", "minecraft:egg"));
		map.put("Butin de monstres", List.of(
				"minecraft:rotten_flesh", "minecraft:bone", "minecraft:bone_meal", "minecraft:string",
				"minecraft:spider_eye", "minecraft:gunpowder", "minecraft:ender_pearl", "minecraft:slime_ball",
				"minecraft:phantom_membrane", "minecraft:blaze_rod", "minecraft:ghast_tear", "minecraft:magma_cream"));
		map.put("Outils & armes", List.of(
				"#minecraft:swords", "#minecraft:axes", "#minecraft:pickaxes", "#minecraft:shovels", "#minecraft:hoes",
				"#minecraft:spears", "#minecraft:head_armor", "#minecraft:chest_armor", "#minecraft:leg_armor",
				"#minecraft:foot_armor", "#minecraft:arrows", "minecraft:bow", "minecraft:crossbow", "minecraft:shield",
				"minecraft:trident", "minecraft:mace", "minecraft:fishing_rod", "minecraft:shears",
				"minecraft:flint_and_steel"));
		return map;
	}

	/** True when the categories are exactly the older default set (same names, same rules in any order). */
	public static boolean isLegacyDefault(Map<String, StorageCategory> categories) {
		Map<String, List<String>> legacy = legacyCategories();
		if (categories == null || categories.size() != legacy.size()) {
			return false;
		}
		for (Map.Entry<String, List<String>> entry : legacy.entrySet()) {
			StorageCategory current = categories.get(entry.getKey());
			if (current == null || current.rules == null || !normalized(current.rules).equals(normalized(entry.getValue()))
					|| (current.icon != null && !current.icon.isBlank()) || (current.description != null && !current.description.isBlank())) {
				return false;
			}
		}
		return true;
	}

	private static Set<String> normalized(List<String> rules) {
		Set<String> set = new HashSet<>();
		for (String rule : rules) {
			if (rule != null && !rule.isBlank()) {
				String trimmed = rule.trim();
				set.add(trimmed.startsWith("#") ? "#" + StorageCategories.normalize(trimmed) : StorageCategories.normalize(trimmed));
			}
		}
		return set;
	}

	/**
	 * Validates {@code storage.categories} / {@code fallbackCategory}; step 3 of the villagers.json5 migration
	 * ({@code configVersion < 3}): the older default set, when untouched, is replaced by the new one (a customised set
	 * is kept as it is).
	 */
	public static void validate(VillagerConfig.Storage storage, int configVersion) {
		validate(storage, configVersion, new ArrayList<>());
	}

	/**
	 * Same as {@link #validate(VillagerConfig.Storage, int)}; {@code notes} receives what was changed or fixed (default
	 * categories added, unknown parents), for the log.
	 */
	public static void validate(VillagerConfig.Storage storage, int configVersion, List<String> notes) {
		if (storage.categories == null) {
			storage.categories = categories();
		}
		if (configVersion < 3 && isLegacyDefault(storage.categories)) {
			storage.categories = categories();
		}
		if (storage.fallbackCategory == null || storage.fallbackCategory.isBlank()) {
			storage.fallbackCategory = DEFAULT_FALLBACK;
		}
		storage.fallbackCategory = storage.fallbackCategory.trim();
		LinkedHashMap<String, StorageCategory> clean = new LinkedHashMap<>();
		Set<String> seen = new HashSet<>();
		for (Map.Entry<String, StorageCategory> entry : storage.categories.entrySet()) {
			String name = entry.getKey() == null ? "" : entry.getKey().trim();
			StorageCategory category = entry.getValue();
			if (name.isEmpty() || category == null || name.equalsIgnoreCase(storage.fallbackCategory)
					|| !seen.add(name.toLowerCase(java.util.Locale.ROOT))) {
				continue;
			}
			category.sanitize();
			clean.put(name, category);
		}
		storage.categories = clean;
		addNewDefaults(storage, configVersion, notes);
		fixParents(storage, notes);
	}

	/**
	 * Adds once the default categories the file does not know yet ({@code storage.knownCategories}; files older than
	 * {@link #HIERARCHY_VERSION} know {@link #V7_NAMES}): a sub-category only when its parent exists (placed after its
	 * parent's sub-categories), a transversal category at the end. A category the admin removed after it was added
	 * stays removed (its name is known).
	 */
	static void addNewDefaults(VillagerConfig.Storage storage, int configVersion, List<String> notes) {
		LinkedHashMap<String, StorageCategory> defaults = categories();
		Set<String> known = new HashSet<>();
		boolean empty = storage.knownCategories == null || storage.knownCategories.isEmpty();
		if (empty && configVersion < HIERARCHY_VERSION) {
			for (String name : V7_NAMES) {
				known.add(name.toLowerCase(Locale.ROOT));
			}
		} else if (empty) {
			for (String name : defaults.keySet()) {
				known.add(name.toLowerCase(Locale.ROOT));
			}
		} else {
			for (String name : storage.knownCategories) {
				if (name != null) {
					known.add(name.trim().toLowerCase(Locale.ROOT));
				}
			}
		}
		for (Map.Entry<String, StorageCategory> entry : defaults.entrySet()) {
			String name = entry.getKey();
			if (known.contains(name.toLowerCase(Locale.ROOT)) || find(storage.categories, name) != null
					|| name.equalsIgnoreCase(storage.fallbackCategory)) {
				continue;
			}
			StorageCategory category = entry.getValue().copy();
			if (category.isChild()) {
				String parent = find(storage.categories, category.parent);
				if (parent == null) {
					// Parent removed or renamed by the admin: the sub-category would have nothing to refine.
					continue;
				}
				category.parent = parent;
				storage.categories = insertAfterSubtree(storage.categories, parent, name, category);
			} else {
				storage.categories.put(name, category);
			}
			notes.add("storage category '" + name + "' added");
		}
		List<String> names = new ArrayList<>(defaults.keySet());
		if (!empty) {
			for (String name : storage.knownCategories) {
				if (name != null && !name.isBlank() && names.stream().noneMatch(n -> n.equalsIgnoreCase(name.trim()))) {
					names.add(name.trim());
				}
			}
		}
		storage.knownCategories = names;
	}

	/** Spelling of a category name in the map (case ignored), null if absent. */
	static String find(Map<String, StorageCategory> categories, String name) {
		if (name == null) {
			return null;
		}
		for (String key : categories.keySet()) {
			if (key.equalsIgnoreCase(name.trim())) {
				return key;
			}
		}
		return null;
	}

	/** Copy of the map with the category inserted after {@code parent} and the categories below it. */
	private static LinkedHashMap<String, StorageCategory> insertAfterSubtree(LinkedHashMap<String, StorageCategory> categories,
			String parent, String name, StorageCategory category) {
		Set<String> subtree = new HashSet<>();
		subtree.add(parent.toLowerCase(Locale.ROOT));
		boolean grew = true;
		while (grew) {
			grew = false;
			for (Map.Entry<String, StorageCategory> entry : categories.entrySet()) {
				String key = entry.getKey().toLowerCase(Locale.ROOT);
				String of = entry.getValue().parent == null ? "" : entry.getValue().parent.trim().toLowerCase(Locale.ROOT);
				if (!subtree.contains(key) && subtree.contains(of)) {
					subtree.add(key);
					grew = true;
				}
			}
		}
		String last = null;
		for (String key : categories.keySet()) {
			if (subtree.contains(key.toLowerCase(Locale.ROOT))) {
				last = key;
			}
		}
		LinkedHashMap<String, StorageCategory> result = new LinkedHashMap<>();
		for (Map.Entry<String, StorageCategory> entry : categories.entrySet()) {
			result.put(entry.getKey(), entry.getValue());
			if (entry.getKey().equals(last)) {
				result.put(name, category);
			}
		}
		if (!result.containsKey(name)) {
			result.put(name, category);
		}
		return result;
	}

	/**
	 * Parents spelled like their category; an unknown parent, a transversal category or the category itself as parent
	 * makes the category top-level (noted). Cycles are broken by {@link StorageCategories}.
	 */
	private static void fixParents(VillagerConfig.Storage storage, List<String> notes) {
		for (Map.Entry<String, StorageCategory> entry : storage.categories.entrySet()) {
			StorageCategory category = entry.getValue();
			if (!category.isChild()) {
				continue;
			}
			String parent = find(storage.categories, category.parent);
			StorageCategory parentCategory = parent == null ? null : storage.categories.get(parent);
			if (parentCategory == null || parent.equals(entry.getKey()) || parentCategory.isTransversal()) {
				notes.add("storage category '" + entry.getKey() + "': unknown parent '" + category.parent + "', used as a top-level category");
				category.parent = "";
			} else {
				category.parent = parent;
			}
		}
	}

	/** Name → parent ("" top-level, "*" transversal), as used by {@link StorageCategories}. */
	public static LinkedHashMap<String, String> parents(Map<String, StorageCategory> categories) {
		LinkedHashMap<String, String> parents = new LinkedHashMap<>();
		for (Map.Entry<String, StorageCategory> entry : categories.entrySet()) {
			if (entry.getKey() != null && entry.getValue() != null) {
				parents.put(entry.getKey(), entry.getValue().parent == null ? "" : entry.getValue().parent);
			}
		}
		return parents;
	}

	/** Name → rules, as used by {@link StorageCategories}. */
	public static LinkedHashMap<String, List<String>> rules(Map<String, StorageCategory> categories) {
		LinkedHashMap<String, List<String>> rules = new LinkedHashMap<>();
		for (Map.Entry<String, StorageCategory> entry : categories.entrySet()) {
			if (entry.getKey() != null && entry.getValue() != null) {
				rules.put(entry.getKey(), entry.getValue().rules == null ? List.of() : new ArrayList<>(entry.getValue().rules));
			}
		}
		return rules;
	}

	/**
	 * New name of a chest category saved with the older default set ("Pierre & terre" → "Pierre"), when that name is no
	 * longer a category but its replacement is; null otherwise.
	 */
	public static String legacyRename(String category, StorageCategories categories) {
		if (category == null || categories.canonical(category) != null) {
			return null;
		}
		for (Map.Entry<String, String> entry : LEGACY_NAMES.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(category.trim())) {
				return categories.canonical(entry.getValue());
			}
		}
		return null;
	}
}
