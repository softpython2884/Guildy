package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Safe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The server's knowledge used by the gatherers, rebuilt when the recipes or the configs change: the recipe book of the
 * planner ({@link SupplyPlanner.Book}: shaped and shapeless crafting recipes and furnace recipes of the recipe manager,
 * special / dynamic recipes skipped, as are recipes whose result carries components or whose ingredients leave a remainder
 * such as a bucket), the vanilla burn time of the fuels (the furnace's own loot context), and the natural sources of the
 * items ({@link SupplySources}: the drops of every natural block of villagers.json5 mined with an iron tool of its kind,
 * sampled a few times, and the natural tree logs).
 */
final class SupplyBook implements SupplyPlanner.Book {
	private static final Logger LOGGER = LoggerFactory.getLogger("Guildy");
	/** Rolls of a block's loot table to average its drops. */
	private static final int DROP_ROLLS = 12;
	private static final ItemStack PICKAXE = new ItemStack(Items.IRON_PICKAXE);
	private static final ItemStack SHOVEL = new ItemStack(Items.IRON_SHOVEL);
	private static final ItemStack AXE = new ItemStack(Items.IRON_AXE);
	private static final ItemStack HOE = new ItemStack(Items.IRON_HOE);

	private static @Nullable SupplyBook cached;
	private static @Nullable RecipeManager cachedFor;
	private static @Nullable SupplyConfig cachedConfig;
	private static @Nullable SupplySources sources;
	private static @Nullable Object sourcesKey;

	private final Map<String, List<SupplyPlanner.Recipe>> byOutput;
	private final Map<String, Double> fuelSmelts = new HashMap<>();
	private final LootContext fuelContext;

	private SupplyBook(Map<String, List<SupplyPlanner.Recipe>> byOutput, LootContext fuelContext) {
		this.byOutput = byOutput;
		this.fuelContext = fuelContext;
	}

	/** Book of the server's current recipes. */
	static SupplyBook of(ServerLevel level) {
		MinecraftServer server = level.getServer();
		RecipeManager manager = server.getRecipeManager();
		SupplyConfig config = Supply.config();
		SupplyBook book = cached;
		if (book == null || cachedFor != manager || cachedConfig != config) {
			book = build(level, manager, config);
			cached = book;
			cachedFor = manager;
			cachedConfig = config;
		}
		return book;
	}

	static void clear() {
		cached = null;
		cachedFor = null;
		cachedConfig = null;
		sources = null;
		sourcesKey = null;
	}

	@Override
	public List<SupplyPlanner.Recipe> recipesFor(String item) {
		return byOutput.getOrDefault(item, List.of());
	}

	@Override
	public double smeltsPerFuel(String item) {
		Double known = fuelSmelts.get(item);
		if (known == null) {
			Item value = ItemUtil.item(item);
			known = value == null ? 0.0 : burnTicks(new ItemStack(value)) / 200.0;
			fuelSmelts.put(item, known);
		}
		return known;
	}

	@Override
	public int maxStack(String item) {
		Item value = ItemUtil.item(item);
		return value == null ? 64 : Math.max(1, value.getDefaultMaxStackSize());
	}

	/** Vanilla burn time (ticks) of a fuel stack, 0 when it is not a fuel. */
	int burnTicks(ItemStack stack) {
		if (stack.isEmpty() || !stack.has(DataComponents.COOKING_FUEL)) {
			return 0;
		}
		return Safe.get("supply.fuel", () -> ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, fuelContext, 0), 0);
	}

	/** Every recipe count (log). */
	int size() {
		int total = 0;
		for (List<SupplyPlanner.Recipe> recipes : byOutput.values()) {
			total += recipes.size();
		}
		return total;
	}

	// ------------------------------------------------------------------ recipes

	private static SupplyBook build(ServerLevel level, RecipeManager manager, SupplyConfig config) {
		FeatureFlagSet features = level.enabledFeatures();
		Set<String> ignored = new HashSet<>(config.crafting.ignoredRecipes);
		ignored.addAll(config.smelting.ignoredRecipes);
		Map<String, List<SupplyPlanner.Recipe>> byOutput = new LinkedHashMap<>();
		int skipped = 0;
		for (RecipeHolder<?> holder : manager.getRecipes()) {
			String id = holder.id().identifier().toString();
			if (ignored.contains(id) || ignored.contains(holder.id().identifier().getPath())) {
				continue;
			}
			SupplyPlanner.Recipe recipe = Safe.get("supply.recipe", () -> convert(id, holder.value(), features), null);
			if (recipe == null) {
				skipped++;
				continue;
			}
			byOutput.computeIfAbsent(recipe.output(), k -> new ArrayList<>()).add(recipe);
		}
		for (SupplyPlanner.Recipe recipe : transforms(config.transforms, features)) {
			byOutput.computeIfAbsent(recipe.output(), k -> new ArrayList<>()).add(recipe);
		}
		// Fewest input units per item made first (the stonecutter's 1:1 before the table's 6:4), then crafting first.
		for (List<SupplyPlanner.Recipe> recipes : byOutput.values()) {
			recipes.sort((a, b) -> {
				int byUnits = Double.compare(units(a) / (double) a.count(), units(b) / (double) b.count());
				return byUnits != 0 ? byUnits : Integer.compare(a.kind().ordinal(), b.kind().ordinal());
			});
		}
		FurnaceBlockEntity furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
		LootContext fuel = new LootContext.Builder(new LootParams.Builder(level)
				.withParameter(LootContextParams.BLOCK_STATE, furnace.getBlockState())
				.withParameter(LootContextParams.BLOCK_ENTITY, furnace)
				.withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
				.withParameter(LootContextParams.CONTAINER, furnace)
				.create(LootContextParamSets.CONTAINER_PROCESS)).create(Optional.empty());
		SupplyBook book = new SupplyBook(byOutput, fuel);
		LOGGER.debug("[Guildy] supply recipe book: {} recipes ({} skipped)", book.size(), skipped);
		return book;
	}

	private static int units(SupplyPlanner.Recipe recipe) {
		int total = 0;
		for (SupplyPlanner.Input input : recipe.inputs()) {
			total += input.count();
		}
		return total;
	}

	private static SupplyPlanner.@Nullable Recipe convert(String id, Recipe<?> recipe, FeatureFlagSet features) {
		if (recipe.isSpecial()) {
			return null;
		}
		if (recipe instanceof ShapedRecipe shaped) {
			List<Ingredient> ingredients = new ArrayList<>();
			for (Optional<Ingredient> slot : shaped.getIngredients()) {
				slot.ifPresent(ingredients::add);
			}
			boolean table = shaped.getWidth() > 2 || shaped.getHeight() > 2;
			return craft(id, shaped.assemble(CraftingInput.EMPTY), ingredients, SupplyPlanner.Kind.CRAFT, station(table), features);
		}
		if (recipe instanceof ShapelessRecipe shapeless) {
			List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
			return craft(id, shapeless.assemble(CraftingInput.EMPTY), ingredients, SupplyPlanner.Kind.CRAFT, station(ingredients.size() > 4), features);
		}
		if (recipe instanceof SmeltingRecipe smelting) {
			return craft(id, smelting.assemble(new SingleRecipeInput(ItemStack.EMPTY)), List.of(smelting.input()), SupplyPlanner.Kind.SMELT,
					SupplyPlanner.Station.FURNACE, features);
		}
		if (recipe instanceof StonecutterRecipe cutting) {
			return craft(id, cutting.assemble(new SingleRecipeInput(ItemStack.EMPTY)), List.of(cutting.input()), SupplyPlanner.Kind.CUT,
					SupplyPlanner.Station.STONECUTTER, features);
		}
		return null;
	}

	/** A crafting recipe larger than 2x2 needs a crafting table (all of them with {@code crafting.handCrafting} off). */
	private static SupplyPlanner.Station station(boolean table) {
		return table || !Supply.config().crafting.handCrafting ? SupplyPlanner.Station.TABLE : SupplyPlanner.Station.HAND;
	}

	private static SupplyPlanner.@Nullable Recipe craft(String id, ItemStack result, List<Ingredient> ingredients, SupplyPlanner.Kind kind,
			SupplyPlanner.Station station, FeatureFlagSet features) {
		if (result.isEmpty() || !result.getComponentsPatch().isEmpty() || ingredients.isEmpty() || !result.getItem().isEnabled(features)) {
			return null;
		}
		String output = ItemUtil.id(result.getItem());
		// Identical ingredients grouped: 6 × "oak planks" for the stairs.
		Map<List<String>, Integer> grouped = new LinkedHashMap<>();
		for (Ingredient ingredient : ingredients) {
			List<String> options = new ArrayList<>();
			for (Holder<Item> holder : ingredient.items().toList()) {
				Item item = holder.value();
				if (!item.isEnabled(features)) {
					continue;
				}
				if (item.getCraftingRemainder() != null) {
					// Buckets, bottles...: the remainder would have to be handled, those recipes are left to the players.
					return null;
				}
				String option = ItemUtil.id(item);
				if (option.equals(output)) {
					// Never "makes" an item from itself (dyeing, repairs...).
					return null;
				}
				options.add(option);
			}
			if (options.isEmpty()) {
				return null;
			}
			grouped.merge(options, 1, Integer::sum);
		}
		List<SupplyPlanner.Input> inputs = new ArrayList<>();
		grouped.forEach((options, count) -> inputs.add(new SupplyPlanner.Input(List.copyOf(options), count)));
		return new SupplyPlanner.Recipe(id, kind, output, result.getCount(), List.copyOf(inputs), station);
	}

	// ------------------------------------------------------------------ in-world transformations

	/** Prefix of the ids of the transformations ({@code guildy:transform/<tool>/<input>}). */
	static final String TRANSFORM = "guildy:transform/";

	/**
	 * Transformations that are not recipes (supply.json5 "transforms"): the axe strips logs, stems, woods and the bamboo
	 * block ("stripped_" variant of the same id), the shovel makes a dirt path of #turns_into_dirt_path, the hoe turns
	 * coarse dirt into dirt, water hardens concrete powder ("_concrete_powder" -> "_concrete") and turns dirt into mud.
	 */
	private static List<SupplyPlanner.Recipe> transforms(SupplyConfig.Transforms cfg, FeatureFlagSet features) {
		List<SupplyPlanner.Recipe> recipes = new ArrayList<>();
		if (!cfg.enabled) {
			return recipes;
		}
		for (Block block : BuiltInRegistries.BLOCK) {
			Item item = block.asItem();
			if (item == Items.AIR || !item.isEnabled(features)) {
				continue;
			}
			Identifier key = BuiltInRegistries.BLOCK.getKey(block);
			String path = key.getPath();
			if (cfg.stripping && !path.startsWith("stripped_") && (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem")
					|| path.endsWith("_hyphae") || path.equals("bamboo_block"))) {
				addTransform(recipes, "axe", item, Identifier.fromNamespaceAndPath(key.getNamespace(), "stripped_" + path), SupplyPlanner.Station.HAND,
						features);
			}
			if (cfg.dirtPaths && block.defaultBlockState().is(BlockTags.TURNS_INTO_DIRT_PATH)) {
				addTransform(recipes, "shovel", item, Identifier.withDefaultNamespace("dirt_path"), SupplyPlanner.Station.HAND, features);
			}
			if (cfg.concrete && block instanceof ConcretePowderBlock && path.endsWith("_concrete_powder")) {
				addTransform(recipes, "water", item, Identifier.fromNamespaceAndPath(key.getNamespace(),
						path.substring(0, path.length() - "_powder".length())), SupplyPlanner.Station.WATER, features);
			}
		}
		if (cfg.coarseDirt) {
			addTransform(recipes, "hoe", Items.COARSE_DIRT, Identifier.withDefaultNamespace("dirt"), SupplyPlanner.Station.HAND, features);
		}
		if (cfg.mud) {
			addTransform(recipes, "water", Items.DIRT, Identifier.withDefaultNamespace("mud"), SupplyPlanner.Station.WATER, features);
		}
		return recipes;
	}

	private static void addTransform(List<SupplyPlanner.Recipe> recipes, String tool, Item input, Identifier outputId, SupplyPlanner.Station station,
			FeatureFlagSet features) {
		if (!BuiltInRegistries.ITEM.containsKey(outputId)) {
			return;
		}
		Item output = BuiltInRegistries.ITEM.getValue(outputId);
		if (output == null || output == Items.AIR || output == input || !output.isEnabled(features)) {
			return;
		}
		String inputId = ItemUtil.id(input);
		recipes.add(new SupplyPlanner.Recipe(TRANSFORM + tool + "/" + BuiltInRegistries.ITEM.getKey(input).getPath(), SupplyPlanner.Kind.TRANSFORM,
				ItemUtil.id(output), 1, List.of(new SupplyPlanner.Input(List.of(inputId), 1)), station));
	}

	/** Tool of a transformation ("axe", "shovel", "hoe", "water"), "" for a real recipe. */
	static String transformTool(SupplyPlanner.Recipe recipe) {
		if (!recipe.id().startsWith(TRANSFORM)) {
			return "";
		}
		String rest = recipe.id().substring(TRANSFORM.length());
		int slash = rest.indexOf('/');
		return slash < 0 ? rest : rest.substring(0, slash);
	}

	// ------------------------------------------------------------------ natural sources

	/** Natural sources of the items (see {@link SupplySources}), rebuilt when villagers.json5 or supply.json5 change. */
	static SupplySources sources(ServerLevel level) {
		Object key = List.of(VillagerModule.config(), Supply.config());
		SupplySources current = sources;
		if (current == null || !key.equals(sourcesKey)) {
			current = Safe.get("supply.sources", () -> buildSources(level), null);
			if (current == null) {
				current = new SupplySources(Map.of(), Map.of(), Map.of(), Set.of());
			}
			sources = current;
			sourcesKey = key;
		}
		return current;
	}

	private static SupplySources buildSources(ServerLevel level) {
		Map<String, Map<String, Double>> drops = new LinkedHashMap<>();
		Map<String, String> trees = new LinkedHashMap<>();
		BlockPos origin = level.getRespawnData().pos();
		for (Block block : BuiltInRegistries.BLOCK) {
			BlockState state = block.defaultBlockState();
			if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
				continue;
			}
			String id = BuiltInRegistries.BLOCK.getKey(block).toString();
			if (state.is(BlockTags.OVERWORLD_NATURAL_LOGS) && LumberjackTask.saplingFor(block) != null && block.asItem() != Items.AIR) {
				trees.put(id, ItemUtil.id(block.asItem()));
				continue;
			}
			if (!MineWorld.natural(block)) {
				continue;
			}
			ItemStack tool = toolFor(state);
			Map<String, Double> sum = new LinkedHashMap<>();
			for (int i = 0; i < DROP_ROLLS; i++) {
				for (ItemStack drop : Block.getDrops(state, level, origin, null, null, tool)) {
					if (!drop.isEmpty() && drop.getComponentsPatch().isEmpty()) {
						sum.merge(ItemUtil.id(drop.getItem()), (double) drop.getCount(), Double::sum);
					}
				}
			}
			if (!sum.isEmpty()) {
				Map<String, Double> average = new LinkedHashMap<>();
				sum.forEach((item, total) -> average.put(item, total / DROP_ROLLS));
				drops.put(id, average);
			}
		}
		SupplyConfig config = Supply.config();
		Map<String, List<String>> overrides = new LinkedHashMap<>();
		config.sources.forEach((item, blocks) -> {
			String itemId = normalize(item);
			List<String> list = new ArrayList<>();
			for (String block : blocks) {
				list.add(block.startsWith(SupplySources.TREE) ? SupplySources.TREE + normalize(block.substring(SupplySources.TREE.length()))
						: normalize(block));
			}
			overrides.put(itemId, list);
		});
		Set<String> never = new LinkedHashSet<>(expandItems(config.neverFetch));
		return new SupplySources(drops, trees, overrides, never);
	}

	/** Virtual non-enchanted tool of the block's kind (iron: every natural block drops itself or its usual item). */
	static ItemStack toolFor(BlockState state) {
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return PICKAXE;
		}
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return SHOVEL;
		}
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return AXE;
		}
		if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
			return HOE;
		}
		return ItemStack.EMPTY;
	}

	/** Item ids of a list of ids and #tags (unknown entries ignored). */
	static Set<String> expandItems(List<String> values) {
		Set<String> items = new LinkedHashSet<>();
		for (String value : values) {
			if (value.startsWith("#")) {
				Identifier tag = parse(value.substring(1));
				if (tag == null) {
					continue;
				}
				TagKey<Item> key = TagKey.create(Registries.ITEM, tag);
				BuiltInRegistries.ITEM.getTagOrEmpty(key).forEach(holder -> items.add(ItemUtil.id(holder.value())));
			} else {
				Item item = ItemUtil.item(normalize(value));
				if (item != null && item != Items.AIR) {
					items.add(ItemUtil.id(item));
				}
			}
		}
		return items;
	}

	private static String normalize(String id) {
		String value = id.trim();
		return value.contains(":") ? value : "minecraft:" + value;
	}

	private static @Nullable Identifier parse(String id) {
		try {
			return Identifier.parse(normalize(id));
		} catch (RuntimeException e) {
			return null;
		}
	}
}
