package dev.guildy.builds;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What a block state of a schematic costs to build (pure, unit tested: block ids and properties as text, the item
 * registry behind {@link ItemLookup}). A player would place the same: a double slab = 2 slabs, 3 candles = 3 candles,
 * a door, a bed or a tall flower = one item (the other half is free, placed with it), crops = their seeds, redstone
 * wire = redstone, a wall torch / sign / banner / head / coral fan = the item of the standing one, a potted plant = a
 * flower pot + the plant, a waterlogged block = + water. Water sources are free by default (builds.json5); a lava
 * source costs a lava bucket (the empty bucket goes back to the storage). Flowing fluids, piston heads and the upper
 * halves are never work of their own; command blocks, barriers, bedrock, portals, spawners, fire... are "non
 * constructible". Anything else: the item of the same id, else the block's own item (server side, {@link Requirement#pick}).
 */
public final class MaterialRules {
	/** What kind of work a cell is. */
	public enum Kind {
		/** Air: nothing to place (it may be dug out, see {@link InteriorRules}). */
		AIR,
		/** Not part of the schematic. */
		VOID,
		/** A block to place. */
		BUILD,
		/** A water or lava source. */
		FLUID,
		/** Comes by itself: upper half of a door / bed head (placed with the other half), flowing fluid, piston head. */
		AUTO,
		/** Cannot be built in survival (no item, or forbidden). */
		NOT_BUILDABLE
	}

	/**
	 * Cost of a cell.
	 *
	 * @param items   items used (item id → count)
	 * @param returns items given back after placing (empty buckets)
	 * @param pick    the cost is "the block's own item", resolved server side (no item of the same id)
	 * @param reason  why it is not buildable / automatic ("" otherwise): a short code shown in the menus
	 */
	public record Requirement(Kind kind, Map<String, Integer> items, Map<String, Integer> returns, boolean pick, String reason) {
		public static final Requirement AIR = new Requirement(Kind.AIR, Map.of(), Map.of(), false, "");
		public static final Requirement VOID = new Requirement(Kind.VOID, Map.of(), Map.of(), false, "");

		static Requirement notBuildable(String reason) {
			return new Requirement(Kind.NOT_BUILDABLE, Map.of(), Map.of(), false, reason);
		}

		static Requirement auto(String reason) {
			return new Requirement(Kind.AUTO, Map.of(), Map.of(), false, reason);
		}
	}

	/** Item registry access ("minecraft:stone" exists?). */
	public interface ItemLookup {
		boolean exists(String itemId);
	}

	private static final Set<String> NOT_BUILDABLE = Set.of(
			"barrier", "bedrock", "command_block", "chain_command_block", "repeating_command_block", "structure_block", "jigsaw",
			"spawner", "trial_spawner", "vault", "nether_portal", "end_portal", "end_gateway", "fire", "soul_fire",
			"end_portal_frame", "reinforced_deepslate", "budding_amethyst", "light", "moving_piston", "bubble_column", "frosted_ice",
			"petrified_oak_slab", "test_block", "test_instance_block", "chorus_plant");
	private static final Map<String, String> ITEM_OF = Map.ofEntries(
			Map.entry("wheat", "wheat_seeds"),
			Map.entry("carrots", "carrot"),
			Map.entry("potatoes", "potato"),
			Map.entry("beetroots", "beetroot_seeds"),
			Map.entry("melon_stem", "melon_seeds"),
			Map.entry("attached_melon_stem", "melon_seeds"),
			Map.entry("pumpkin_stem", "pumpkin_seeds"),
			Map.entry("attached_pumpkin_stem", "pumpkin_seeds"),
			Map.entry("cocoa", "cocoa_beans"),
			Map.entry("sweet_berry_bush", "sweet_berries"),
			Map.entry("torchflower_crop", "torchflower_seeds"),
			Map.entry("pitcher_crop", "pitcher_pod"),
			Map.entry("cave_vines", "glow_berries"),
			Map.entry("cave_vines_plant", "glow_berries"),
			Map.entry("kelp_plant", "kelp"),
			Map.entry("twisting_vines_plant", "twisting_vines"),
			Map.entry("weeping_vines_plant", "weeping_vines"),
			Map.entry("bamboo_sapling", "bamboo"),
			Map.entry("big_dripleaf_stem", "big_dripleaf"),
			Map.entry("tripwire", "string"),
			Map.entry("redstone_wire", "redstone"),
			Map.entry("tall_seagrass", "seagrass"),
			// A player lays dirt, then tills it / makes a path.
			Map.entry("farmland", "dirt"),
			Map.entry("dirt_path", "dirt"),
			Map.entry("water_cauldron", "cauldron"),
			Map.entry("lava_cauldron", "cauldron"),
			Map.entry("powder_snow_cauldron", "cauldron"),
			Map.entry("potted_azalea_bush", "azalea"),
			Map.entry("potted_flowering_azalea_bush", "flowering_azalea"));
	/** Properties counting the items of a block (snow layers, candles, sea pickles...). */
	private static final Map<String, String> COUNT_PROPERTY = Map.of(
			"snow", "layers",
			"sea_pickle", "pickles",
			"turtle_egg", "eggs",
			"pink_petals", "flower_amount",
			"wildflowers", "flower_amount",
			"leaf_litter", "segment_amount");
	private static final Set<String> FACES = Set.of("north", "south", "east", "west", "up", "down");
	private static final Set<String> MULTIFACE = Set.of("glow_lichen", "sculk_vein", "resin_clump", "vine");

	private MaterialRules() {
	}

	/** Cost of a state ({@code waterFree}: water sources and waterlogging cost nothing). */
	public static Requirement of(BlockStateString state, ItemLookup lookup, boolean waterFree) {
		if (state.isStructureVoid()) {
			return Requirement.VOID;
		}
		if (state.isAir()) {
			return Requirement.AIR;
		}
		String ns = state.id().substring(0, state.id().indexOf(':') + 1);
		String path = state.path();
		if (path.equals("water") || path.equals("lava")) {
			String level = state.prop("level");
			if (level != null && !level.equals("0")) {
				return Requirement.auto("flowing");
			}
			if (path.equals("water")) {
				return waterFree ? new Requirement(Kind.FLUID, Map.of(), Map.of(), false, "")
						: new Requirement(Kind.FLUID, Map.of("minecraft:water_bucket", 1), Map.of("minecraft:bucket", 1), false, "");
			}
			return new Requirement(Kind.FLUID, Map.of("minecraft:lava_bucket", 1), Map.of("minecraft:bucket", 1), false, "");
		}
		if (state.isMinecraft() && (NOT_BUILDABLE.contains(path) || path.startsWith("infested_"))) {
			return Requirement.notBuildable(path);
		}
		if (path.equals("piston_head")) {
			return Requirement.auto("piston_head");
		}
		if ("upper".equals(state.prop("half")) || "head".equals(state.prop("part"))) {
			return Requirement.auto("paired");
		}
		Map<String, Integer> items = new LinkedHashMap<>();
		Map<String, Integer> returns = new LinkedHashMap<>();
		boolean pick = false;
		if (path.startsWith("potted_")) {
			add(items, "minecraft:flower_pot", 1);
			String plant = ITEM_OF.getOrDefault(path, path.substring("potted_".length()));
			add(items, ns + plant, 1);
		} else if (path.endsWith("candle_cake")) {
			add(items, "minecraft:cake", 1);
			add(items, ns + (path.equals("candle_cake") ? "candle" : path.substring(0, path.length() - "_cake".length())), 1);
		} else if (path.equals("powder_snow")) {
			add(items, "minecraft:powder_snow_bucket", 1);
			add(returns, "minecraft:bucket", 1);
		} else {
			String itemPath = state.isMinecraft() ? ITEM_OF.get(path) : null;
			if (itemPath == null) {
				itemPath = withoutWall(path);
			}
			String itemId = ns + itemPath;
			if (!lookup.exists(itemId)) {
				itemId = state.id();
				pick = !lookup.exists(itemId);
			}
			if (!pick) {
				add(items, itemId, count(state, path));
			}
		}
		if ("true".equals(state.prop("waterlogged")) && !waterFree) {
			add(items, "minecraft:water_bucket", 1);
			add(returns, "minecraft:bucket", 1);
		}
		return new Requirement(Kind.BUILD, items, returns, pick, "");
	}

	/** "oak_wall_sign" → "oak_sign", "wall_torch" → "torch", "dead_brain_coral_wall_fan" → "dead_brain_coral_fan". */
	static String withoutWall(String path) {
		if (path.startsWith("wall_")) {
			return path.substring("wall_".length());
		}
		int index = path.indexOf("_wall_");
		if (index >= 0) {
			return path.substring(0, index) + "_" + path.substring(index + "_wall_".length());
		}
		return path;
	}

	/** Items placed in the cell: 2 for a double slab, the candles, layers, pickles, eggs, petals or faces. */
	static int count(BlockStateString state, String path) {
		if (path.endsWith("_slab") && "double".equals(state.prop("type"))) {
			return 2;
		}
		String property = COUNT_PROPERTY.get(path);
		if (property == null && path.endsWith("candle")) {
			property = "candles";
		}
		if (property != null) {
			return Math.max(1, parse(state.prop(property), 1));
		}
		if (MULTIFACE.contains(path)) {
			int faces = 0;
			for (Map.Entry<String, String> entry : state.props().entrySet()) {
				if (FACES.contains(entry.getKey()) && "true".equals(entry.getValue())) {
					faces++;
				}
			}
			return Math.max(1, faces);
		}
		return 1;
	}

	private static int parse(@Nullable String text, int fallback) {
		if (text == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static void add(Map<String, Integer> map, String id, int count) {
		map.merge(id, count, Integer::sum);
	}
}
