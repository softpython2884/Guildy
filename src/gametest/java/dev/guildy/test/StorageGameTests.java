package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.ItemClassifier;
import dev.guildy.villager.StorageCategories;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Storage categories with the real items (tags, components, creative tabs): representative items, coverage of the
 * whole item registry (dump in run/storage-categories.tsv), the sorter filing mob loot, and the storage menus.
 */
public class StorageGameTests {
	private static final Logger LOGGER = LoggerFactory.getLogger("GuildyTest");

	private static final String WOOD = "Bois";
	private static final String STONE = "Pierre";
	private static final String SOIL = "Terre, sable et gravier";
	private static final String BUILDING = "Blocs de construction";
	private static final String FURNITURE = "Mobilier et décoration";
	private static final String ORES = "Minerais et lingots";
	private static final String REDSTONE = "Redstone";
	private static final String TOOLS = "Outils";
	private static final String WEAPONS = "Armes";
	private static final String ARMOR = "Armures";
	private static final String BOOKS = "Livres et enchantements";
	private static final String POTIONS = "Potions et alchimie";
	private static final String FOOD = "Nourriture";
	private static final String SEEDS = "Graines et pousses";
	private static final String PLANTS = "Plantes et fleurs";
	private static final String LOOT = "Butin de monstres";
	private static final String NETHER = "Nether";
	private static final String END = "End";
	private static final String DYES = "Colorants";

	/** Items no survival player can get (creative / commands only), left out of the coverage figure. */
	private static final Set<String> NOT_SURVIVAL = Set.of(
			"air", "command_block", "chain_command_block", "repeating_command_block", "command_block_minecart", "jigsaw",
			"structure_block", "structure_void", "barrier", "debug_stick", "test_instance_block", "test_block", "light",
			"bedrock", "budding_amethyst", "reinforced_deepslate", "end_portal_frame", "farmland", "dirt_path", "frogspawn",
			"chorus_plant", "petrified_oak_slab", "player_head", "spawner", "trial_spawner", "vault", "knowledge_book",
			"suspicious_sand", "suspicious_gravel", "infested_stone", "infested_cobblestone", "infested_stone_bricks",
			"infested_mossy_stone_bricks", "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks", "infested_deepslate");

	private static Map<String, String> expectations() {
		Map<String, String> e = new LinkedHashMap<>();
		// Mob loot (the server owner's examples first: skulls and leather).
		for (String id : List.of("skeleton_skull", "wither_skeleton_skull", "player_head", "zombie_head", "creeper_head", "piglin_head",
				"dragon_head", "leather", "feather", "rabbit_hide", "rabbit_foot", "ink_sac", "glow_ink_sac", "bone", "bone_meal",
				"bone_block", "string", "cobweb", "gunpowder", "ender_pearl", "blaze_rod", "breeze_rod", "ghast_tear", "shulker_shell",
				"nautilus_shell", "heart_of_the_sea", "rotten_flesh", "spider_eye", "slime_ball", "magma_cream", "phantom_membrane",
				"turtle_scute", "armadillo_scute", "totem_of_undying", "nether_star", "prismarine_shard", "prismarine_crystals",
				"echo_shard", "trial_key", "heavy_core", "honeycomb", "turtle_egg", "sniffer_egg", "dried_ghast", "sponge",
				"zombie_spawn_egg", "spawner")) {
			e.put(id, LOOT);
		}
		for (String id : List.of("oak_log", "stripped_birch_wood", "cherry_planks", "oak_slab", "crimson_stem", "warped_planks",
				"stick", "bowl", "oak_sign", "oak_door", "oak_fence_gate", "bamboo_mosaic", "spruce_trapdoor", "pale_oak_hanging_sign")) {
			e.put(id, WOOD);
		}
		for (String id : List.of("stone", "cobbled_deepslate", "tuff_bricks", "cobblestone_stairs", "mossy_stone_brick_wall",
				"sandstone_slab", "cinnabar", "sulfur", "sulfur_spike", "calcite", "pointed_dripstone", "smooth_stone", "red_sandstone",
				"granite", "andesite_wall", "deepslate_tiles", "infested_stone")) {
			e.put(id, STONE);
		}
		for (String id : List.of("dirt", "coarse_dirt", "sand", "gravel", "clay_ball", "snowball", "ice", "podzol", "mud", "grass_block")) {
			e.put(id, SOIL);
		}
		for (String id : List.of("white_wool", "white_concrete", "glass", "white_wool_slab", "cut_copper_stairs", "oxidized_copper",
				"bricks", "brick", "terracotta", "white_glazed_terracotta", "white_carpet", "glass_pane", "red_stained_glass_pane",
				"prismarine", "iron_bars", "packed_mud", "mud_bricks", "resin_clump", "copper_door")) {
			e.put(id, BUILDING);
		}
		for (String id : List.of("carved_pumpkin", "jack_o_lantern", "copper_chest", "lantern", "torch", "crafting_table", "chest",
				"barrel", "red_bed", "white_banner", "music_disc_cat", "flower_pot", "skull_banner_pattern", "angler_pottery_sherd",
				"scaffolding", "ladder", "anvil", "painting", "item_frame", "bell", "campfire", "sea_lantern", "candle", "oak_shelf",
				"white_cushion", "straw_bed", "ochre_froglight", "lodestone", "beacon", "copper_torch", "copper_lantern", "iron_chain",
				"copper_golem_statue", "decorated_pot", "armor_stand", "jukebox", "furnace", "smoker", "composter", "loom")) {
			e.put(id, FURNITURE);
		}
		for (String id : List.of("iron_ore", "raw_iron", "iron_ingot", "diamond", "emerald", "netherite_scrap", "coal", "nether_quartz_ore",
				"deepslate_diamond_ore", "ancient_debris", "copper_block", "lapis_lazuli", "gold_nugget", "amethyst_shard", "flint",
				"raw_gold_block", "amethyst_cluster", "charcoal", "netherite_ingot", "nether_gold_ore")) {
			e.put(id, ORES);
		}
		for (String id : List.of("redstone", "repeater", "piston", "hopper", "rail", "tnt", "copper_bulb", "oak_button", "oak_pressure_plate",
				"polished_blackstone_pressure_plate", "iron_door", "minecart", "sculk_sensor", "sculk", "observer", "daylight_detector",
				"lightning_rod", "target", "slime_block", "honey_block", "trapped_chest", "note_block", "lever", "redstone_torch",
				"powered_rail", "tnt_minecart", "stone_button", "polished_blackstone_button")) {
			e.put(id, REDSTONE);
		}
		for (String id : List.of("iron_pickaxe", "shears", "fishing_rod", "spyglass", "bucket", "netherite_axe", "golden_hoe", "oak_boat",
				"white_bundle", "compass", "filled_map", "water_bucket", "firework_rocket", "lead", "name_tag", "saddle", "brush", "clock",
				"flint_and_steel", "goat_horn", "white_harness", "abandoned_camp_map", "sulfur_cube_bucket", "axolotl_bucket")) {
			e.put(id, TOOLS);
		}
		for (String id : List.of("diamond_sword", "bow", "arrow", "trident", "mace", "tipped_arrow", "spectral_arrow", "wooden_sword",
				"wind_charge", "crossbow", "netherite_spear")) {
			e.put(id, WEAPONS);
		}
		for (String id : List.of("elytra", "iron_helmet", "shield", "iron_horse_armor", "wolf_armor", "turtle_helmet",
				"diamond_nautilus_armor", "netherite_upgrade_smithing_template", "leather_boots")) {
			e.put(id, ARMOR);
		}
		for (String id : List.of("enchanted_book", "book", "experience_bottle", "writable_book", "paper", "bookshelf", "lectern",
				"enchanting_table")) {
			e.put(id, BOOKS);
		}
		for (String id : List.of("potion", "splash_potion", "lingering_potion", "glass_bottle", "blaze_powder", "brewing_stand", "cauldron",
				"fermented_spider_eye", "dragon_breath", "glistering_melon_slice")) {
			e.put(id, POTIONS);
		}
		for (String id : List.of("bread", "cooked_beef", "golden_carrot", "milk_bucket", "cake", "egg", "honey_bottle", "sweet_berries",
				"carrot", "pumpkin", "wheat", "sugar", "dried_kelp", "hay_block", "melon", "poisonous_potato", "suspicious_stew",
				"enchanted_golden_apple", "pufferfish", "cod")) {
			e.put(id, FOOD);
		}
		for (String id : List.of("wheat_seeds", "oak_sapling", "mangrove_propagule", "azalea", "cocoa_beans", "pitcher_pod",
				"torchflower_seeds", "cherry_sapling")) {
			e.put(id, SEEDS);
		}
		for (String id : List.of("poppy", "sugar_cane", "bamboo", "cactus", "kelp", "oak_leaves", "cherry_leaves", "brown_mushroom",
				"brain_coral_block", "moss_block", "lily_pad", "short_grass", "vine", "glow_lichen", "sunflower", "wither_rose",
				"spore_blossom", "pale_hanging_moss", "leaf_litter", "golden_dandelion", "red_shrub", "shelf_mushroom", "mushroom_stem")) {
			e.put(id, PLANTS);
		}
		for (String id : List.of("netherrack", "nether_bricks", "quartz", "soul_sand", "crimson_fungus", "nether_wart",
				"polished_blackstone_bricks", "glowstone_dust", "glowstone", "magma_block", "obsidian", "basalt", "quartz_block",
				"blackstone", "red_nether_brick_slab", "nether_brick_fence", "crimson_nylium", "weeping_vines", "shroomlight",
				"respawn_anchor", "crying_obsidian", "soul_soil", "nether_brick")) {
			e.put(id, NETHER);
		}
		for (String id : List.of("end_stone", "purpur_block", "chorus_fruit", "end_stone_bricks", "shulker_box", "red_shulker_box",
				"ender_eye", "ender_chest", "end_rod", "dragon_egg", "popped_chorus_fruit", "chorus_flower", "purpur_stairs", "end_crystal")) {
			e.put(id, END);
		}
		for (String id : List.of("red_dye", "white_dye", "black_dye")) {
			e.put(id, DYES);
		}
		return e;
	}

	@GameTest(maxTicks = 20)
	public void representativeItemsHaveTheirCategory(GameTestHelper helper) {
		ItemClassifier.invalidate();
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, String> entry : expectations().entrySet()) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(entry.getKey()));
			if (item == Items.AIR) {
				wrong.add(entry.getKey() + " (unknown item)");
				continue;
			}
			StorageCategories.Match match = ItemClassifier.match(new ItemStack(item));
			if (!match.category().equals(entry.getValue())) {
				wrong.add(entry.getKey() + " -> " + match.category() + " (" + match.kind() + " " + match.rule() + "), expected " + entry.getValue());
			}
		}
		if (!wrong.isEmpty()) {
			LOGGER.info("[storage] misclassified items:\n  {}", String.join("\n  ", wrong));
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " misclassified item(s): " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Every registry item gets a category; at least 95 % of the survival items get a specific one (not the fallback).
	 * Writes the full list (id, category, rule) and the leftovers to run/storage-categories.tsv.
	 */
	@GameTest(maxTicks = 20)
	public void coversTheItemRegistry(GameTestHelper helper) throws IOException {
		ItemClassifier.invalidate();
		String fallback = VillagerModule.config().storage.fallbackCategory;
		Map<String, Integer> perCategory = new TreeMap<>();
		List<String> leftovers = new ArrayList<>();
		List<String> lines = new ArrayList<>();
		int survival = 0;
		int specific = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			ItemStack stack = new ItemStack(item);
			StorageCategories.Match match = ItemClassifier.match(stack);
			StorageCategories.Facts facts = ItemClassifier.facts(stack);
			lines.add(id + "\t" + match.category() + "\t" + match.kind() + "\t" + match.rule() + "\t"
					+ String.join(",", new TreeSet<>(facts.tags().keySet())) + "\t" + String.join(",", new TreeSet<>(facts.properties())));
			boolean isSurvival = "minecraft".equals(id.getNamespace()) && !NOT_SURVIVAL.contains(id.getPath()) && !(item instanceof SpawnEggItem);
			if (!isSurvival) {
				continue;
			}
			survival++;
			perCategory.merge(match.category(), 1, Integer::sum);
			if (match.category().equals(fallback)) {
				leftovers.add(id.getPath());
			} else {
				specific++;
			}
		}
		double coverage = 100.0 * specific / Math.max(1, survival);
		String summary = String.format(java.util.Locale.ROOT, "%d/%d survival items in a specific category (%.1f %%)", specific, survival, coverage);
		lines.addFirst("# " + summary + " ; leftovers in '" + fallback + "': " + String.join(" ", leftovers));
		lines.add(1, "# per category: " + perCategory);
		Path dump = helper.getLevel().getServer().getServerDirectory().resolve("storage-categories.tsv");
		Files.write(dump, lines, StandardCharsets.UTF_8);
		LOGGER.info("[storage] {} ; per category: {} ; leftovers: {}", summary, perCategory, leftovers);
		helper.assertTrue(coverage >= 95.0, "storage coverage too low: " + summary + " ; leftovers: " + leftovers);
		helper.succeed();
	}

	/**
	 * The sorter files a skeleton skull and leather from the deposit chest into the "Butin de monstres" chest (oak logs
	 * into "Bois"), and the registered chests are named after their role.
	 */
	@GameTest(maxTicks = 2400)
	public void sorterStoresMobLootInItsChest(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		TestSupport.console(level, "time set 1000");
		BlockPos ground = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Stor");
		TestSupport.console(level, "guild admin villager spawn " + guild.name);
		level.getServer().getCommands().performPrefixedCommand(leader.createCommandSourceStack(), "guild villagers accept");
		helper.assertTrue(guild.villagers.members.size() == 1, "candidate accepted as guild villager");
		VillagerRecord record = guild.villagers.members.values().iterator().next();

		BlockPos deposit = ground.offset(4, 1, 0);
		BlockPos loot = ground.offset(-4, 1, 0);
		BlockPos wood = ground.offset(0, 1, 4);
		for (BlockPos pos : List.of(deposit, loot, wood)) {
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		}
		Container depositChest = (Container) level.getBlockEntity(deposit);
		depositChest.setItem(0, new ItemStack(Items.SKELETON_SKULL, 2));
		depositChest.setItem(1, new ItemStack(Items.LEATHER, 10));
		depositChest.setItem(2, new ItemStack(Items.OAK_LOG, 8));
		guild.villagers.chests.add(new StorageChest(deposit, StorageChest.Role.DEPOSIT, ""));
		guild.villagers.chests.add(new StorageChest(loot, StorageChest.Role.CATEGORY, LOOT));
		guild.villagers.chests.add(new StorageChest(wood, StorageChest.Role.CATEGORY, WOOD));

		Method setTask = Class.forName("dev.guildy.villager.VillagerManager")
				.getDeclaredMethod("setTask", Guild.class, VillagerRecord.class, Task.class, ServerPlayer.class);
		setTask.setAccessible(true);
		setTask.invoke(null, guild, record, Task.SORTER, leader);

		helper.succeedWhen(() -> {
			helper.assertTrue(count(level, loot, Items.SKELETON_SKULL) == 2, "waiting for the skulls in the 'Butin de monstres' chest");
			helper.assertTrue(count(level, loot, Items.LEATHER) == 10, "waiting for the leather in the 'Butin de monstres' chest");
			helper.assertTrue(count(level, wood, Items.OAK_LOG) == 8, "waiting for the logs in the 'Bois' chest");
			helper.assertTrue(count(level, deposit, null) == 0, "deposit chest emptied");
			helper.assertTrue(LOOT.equals(name(level, loot)), "loot chest named '" + LOOT + "' (now " + name(level, loot) + ")");
			helper.assertTrue("Dépôt".equals(name(level, deposit)), "deposit chest named 'Dépôt' (now " + name(level, deposit) + ")");
			GuildManager.disband(guild);
			helper.assertTrue(name(level, loot) == null, "names removed when the guild is disbanded");
		});
	}

	/** Storage menu, categories menu and role picker open as real containers. */
	@GameTest(maxTicks = 40)
	public void storageMenusOpen(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 4);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Menu");
		BlockPos chest = ground.offset(2, 1, 0);
		level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(new StorageChest(chest, StorageChest.Role.CATEGORY, "Catégorie supprimée"));
		Class<?> gui = Class.forName("dev.guildy.villager.VillagerGui");
		Method storage = gui.getDeclaredMethod("openStorage", ServerPlayer.class, Guild.class, int.class);
		Method picker = gui.getDeclaredMethod("openRolePicker", ServerPlayer.class, Guild.class, BlockPos.class);
		Method categories = Class.forName("dev.guildy.villager.StorageGui").getDeclaredMethod("openCategories", ServerPlayer.class, Guild.class, int.class);
		storage.setAccessible(true);
		picker.setAccessible(true);
		categories.setAccessible(true);
		leader.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.LEATHER));
		for (Runnable open : List.<Runnable>of(
				() -> invoke(storage, leader, guild, 0),
				() -> invoke(picker, leader, guild, chest),
				() -> invoke(categories, leader, guild, 0))) {
			leader.closeContainer();
			open.run();
			helper.assertTrue(leader.containerMenu != leader.inventoryMenu, "storage menu should be open");
		}
		GuildManager.disband(guild);
		helper.succeed();
	}

	/** Chests saved with the older default categories follow their new equivalent; unknown categories are kept. */
	@GameTest(maxTicks = 20)
	public void olderChestCategoriesAreMigrated(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 4);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Migr");
		StorageChest stone = new StorageChest(ground.offset(1, 1, 0), StorageChest.Role.CATEGORY, "Pierre & terre");
		StorageChest tools = new StorageChest(ground.offset(2, 1, 0), StorageChest.Role.CATEGORY, "Outils & armes");
		StorageChest custom = new StorageChest(ground.offset(3, 1, 0), StorageChest.Role.CATEGORY, "Mes trésors");
		guild.villagers.chests.addAll(List.of(stone, tools, custom));
		Method migrate = Class.forName("dev.guildy.villager.StorageSetup").getDeclaredMethod("migrateChestCategories");
		migrate.setAccessible(true);
		migrate.invoke(null);
		helper.assertTrue(STONE.equals(stone.category), "'Pierre & terre' became 'Pierre' (now " + stone.category + ")");
		helper.assertTrue(TOOLS.equals(tools.category), "'Outils & armes' became 'Outils' (now " + tools.category + ")");
		helper.assertTrue("Mes trésors".equals(custom.category), "an unknown category is kept (it acts as the fallback)");
		GuildManager.disband(guild);
		helper.succeed();
	}

	private static void invoke(Method method, Object... args) {
		try {
			method.invoke(null, args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int count(ServerLevel level, BlockPos pos, Item item) {
		Container container = (Container) level.getBlockEntity(pos);
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && (item == null || stack.is(item))) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static String name(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof BaseContainerBlockEntity container && container.getCustomName() != null
				? container.getCustomName().getString() : null;
	}
}
