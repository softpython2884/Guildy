package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Pure rules of the planting done by lumberjacks (new saplings in their forest) and farmers (sowing empty farmland,
 * tilling hydrated soil): spot selection with spacing and density, ground checks, species / seed preferences and the
 * choice of the item to plant (bag or storage chest). Item and block ids only, no Minecraft classes (unit tested).
 */
public final class PlantingRules {
	/** Saplings only grow with at least this light level (sky or block light, see SaplingBlock). */
	public static final int SAPLING_MIN_LIGHT = 9;
	/** Vanilla hydration range of farmland (blocks, horizontally). */
	public static final int HYDRATION_RANGE = 4;
	/** No sapling closer than this (blocks) to another guild's land: the grown tree must stay out of it. */
	public static final int FOREIGN_MARGIN = 4;
	/** The four vanilla crops a farmer harvests and sows (seed items). */
	public static final Set<String> SEEDS = Set.of("minecraft:wheat_seeds", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot_seeds");

	private PlantingRules() {
	}

	/** Ground under a planting spot. */
	public enum Ground {
		/** Grass block or dirt: a sapling grows there, a hoe turns it into farmland. */
		TILLABLE,
		/** Other natural soil (podzol, mycelium, coarse / rooted dirt, moss, mud): saplings only. */
		SOIL,
		FARMLAND,
		/** Dirt path: never planted nor tilled. */
		PATH,
		OTHER
	}

	/**
	 * A sapling spot as seen from the world: ground, the spot block itself free (air or a small plant), free blocks
	 * above it, light, only natural blocks around (no player building), another guild's land nearby.
	 */
	public record Column(Ground ground, boolean spotFree, int headroom, int light, boolean naturalAround, boolean foreignNear) {
	}

	/** A sapling may be planted at this column. */
	public static boolean saplingSpot(Column column, int headroomNeeded) {
		return (column.ground == Ground.TILLABLE || column.ground == Ground.SOIL) && column.spotFree && column.headroom >= headroomNeeded
				&& column.light >= SAPLING_MIN_LIGHT && column.naturalAround && !column.foreignNear;
	}

	/**
	 * A farmer may till this block: grass block or dirt (never a path), nothing on top, water within the vanilla
	 * hydration range and only natural blocks, farmland, water, fences or torches around.
	 */
	public static boolean tillable(Ground ground, boolean airAbove, boolean hydrated, boolean neighboursOk) {
		return ground == Ground.TILLABLE && airAbove && hydrated && neighboursOk;
	}

	// ------------------------------------------------------------------ spot selection

	/** Lazy test of a column of the chunk (local coordinates 0..15), run only once the cheap spacing test passed. */
	@FunctionalInterface
	public interface SpotTest {
		boolean test(int x, int z);
	}

	/**
	 * Picks sapling spots in one chunk, nearest to the reference first.
	 *
	 * @param occupied     grid around the chunk, indexed [x + pad][z + pad] (local x, z from -pad to 15 + pad): true where a
	 *                     log or a sapling stands (or the column is unknown: not loaded)
	 * @param pad          cells around the chunk in the grid, at least {@code spacing}
	 * @param spacing      no log nor sapling at this distance or less (Chebyshev, blocks) from a new sapling
	 * @param size         1 = one sapling, 2 = a 2×2 square (dark oak, pale oak): the spot is its north-west corner
	 * @param existing     trees already in the chunk (trunk bases and saplings)
	 * @param maxPerChunk  density cap: trees per chunk, new ones included
	 * @param limit        spots wanted at most
	 * @param refX         local reference (the villager), candidates nearest first
	 * @param open         ground / room / light test of one column
	 * @return local {x, z} spots (north-west corners for squares)
	 */
	public static List<int[]> pickSpots(boolean[][] occupied, int pad, int spacing, int size, int existing, int maxPerChunk, int limit,
			int refX, int refZ, SpotTest open) {
		List<int[]> picked = new ArrayList<>();
		int room = Math.min(limit, maxPerChunk - existing);
		int side = Math.max(1, Math.min(2, size));
		if (room <= 0 || spacing < 0 || pad < spacing || occupied.length < 16 + 2 * pad) {
			return picked;
		}
		boolean[][] taken = new boolean[occupied.length][];
		for (int i = 0; i < occupied.length; i++) {
			taken[i] = occupied[i].clone();
		}
		List<int[]> candidates = new ArrayList<>();
		for (int x = 0; x <= 16 - side; x++) {
			for (int z = 0; z <= 16 - side; z++) {
				candidates.add(new int[]{x, z});
			}
		}
		candidates.sort(Comparator.<int[]>comparingLong(c -> distSq(c[0], c[1], refX, refZ)).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
		for (int[] candidate : candidates) {
			if (picked.size() >= room) {
				break;
			}
			int x = candidate[0];
			int z = candidate[1];
			if (!clear(taken, pad, x - spacing, z - spacing, x + side - 1 + spacing, z + side - 1 + spacing) || !open(open, x, z, side)) {
				continue;
			}
			for (int dx = 0; dx < side; dx++) {
				for (int dz = 0; dz < side; dz++) {
					taken[x + dx + pad][z + dz + pad] = true;
				}
			}
			picked.add(candidate);
		}
		return picked;
	}

	/** True when no cell of the box (local coordinates, inclusive) is occupied. */
	private static boolean clear(boolean[][] grid, int pad, int minX, int minZ, int maxX, int maxZ) {
		for (int x = minX; x <= maxX; x++) {
			boolean[] row = grid[x + pad];
			for (int z = minZ; z <= maxZ; z++) {
				if (row[z + pad]) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean open(SpotTest test, int x, int z, int side) {
		for (int dx = 0; dx < side; dx++) {
			for (int dz = 0; dz < side; dz++) {
				if (!test.test(x + dx, z + dz)) {
					return false;
				}
			}
		}
		return true;
	}

	private static long distSq(int x, int z, int refX, int refZ) {
		long dx = x - refX;
		long dz = z - refZ;
		return dx * dx + dz * dz;
	}

	/** Chebyshev distance (blocks) between two columns. */
	public static int distance(int x1, int z1, int x2, int z2) {
		return Math.max(Math.abs(x1 - x2), Math.abs(z1 - z2));
	}

	// ------------------------------------------------------------------ species and seeds

	/** Saplings that only grow as a 2×2 square: planted by four, or not at all. */
	public static boolean needsSquare(String saplingId) {
		return saplingId.equals("minecraft:dark_oak_sapling") || saplingId.equals("minecraft:pale_oak_sapling");
	}

	/** Sapling of a natural log block: "minecraft:oak_log" → "minecraft:oak_sapling", mangrove → propagule; "" otherwise. */
	public static String saplingOfLog(String logId) {
		String path = WishMatch.path(logId);
		if (path.startsWith("stripped_") || !path.endsWith("_log")) {
			return "";
		}
		return saplingOfWood(WishMatch.namespace(logId), path.substring(0, path.length() - 4));
	}

	/**
	 * Sapling named by a wanted item: the sapling itself (azaleas and propagules too), or the sapling of the wood of a
	 * log, wood block or planks; "" for anything else.
	 */
	public static String saplingOfItem(String itemId) {
		String ns = WishMatch.namespace(itemId);
		String path = WishMatch.path(itemId);
		if (path.endsWith("_sapling") || path.endsWith("_propagule") || path.equals("azalea") || path.equals("flowering_azalea")) {
			return ns + ":" + path;
		}
		if (path.startsWith("stripped_")) {
			path = path.substring("stripped_".length());
		}
		for (String suffix : List.of("_log", "_wood", "_planks")) {
			if (path.endsWith(suffix)) {
				return saplingOfWood(ns, path.substring(0, path.length() - suffix.length()));
			}
		}
		return "";
	}

	private static String saplingOfWood(String ns, String wood) {
		if (wood.isEmpty()) {
			return "";
		}
		return wood.equals("mangrove") ? ns + ":mangrove_propagule" : ns + ":" + wood + "_sapling";
	}

	/**
	 * Lumberjack: species to plant first. The zone's species (when it cuts only one), then the species of the wanted
	 * items (logs, wood, planks or saplings), without duplicates.
	 *
	 * @param objectiveLog log block of the zone objective ("" = any tree)
	 */
	public static List<String> saplingPreferences(String objectiveLog, List<String> wishIds) {
		List<String> list = new ArrayList<>();
		addUnique(list, objectiveLog.isEmpty() ? "" : saplingOfLog(objectiveLog));
		for (String id : wishIds) {
			addUnique(list, saplingOfItem(id));
		}
		return list;
	}

	/** Seed item of a vanilla crop block: wheat → wheat seeds, carrots → carrot...; "" otherwise. */
	public static String seedOfCrop(String cropBlockId) {
		return switch (cropBlockId) {
			case "minecraft:wheat" -> "minecraft:wheat_seeds";
			case "minecraft:carrots" -> "minecraft:carrot";
			case "minecraft:potatoes" -> "minecraft:potato";
			case "minecraft:beetroots" -> "minecraft:beetroot_seeds";
			default -> "";
		};
	}

	/** Seed item named by a wanted item (the seed itself or the harvest); "" for anything else. */
	public static String seedOfItem(String itemId) {
		return switch (itemId) {
			case "minecraft:wheat", "minecraft:wheat_seeds" -> "minecraft:wheat_seeds";
			case "minecraft:carrot" -> "minecraft:carrot";
			case "minecraft:potato", "minecraft:poisonous_potato" -> "minecraft:potato";
			case "minecraft:beetroot", "minecraft:beetroot_seeds" -> "minecraft:beetroot_seeds";
			default -> "";
		};
	}

	/**
	 * Farmer: seeds to sow first. The wanted crops, then the zone's crop (when it grows only one), without duplicates.
	 *
	 * @param objectiveCrop crop block of the zone objective ("" = any crop)
	 */
	public static List<String> seedPreferences(String objectiveCrop, List<String> wishIds) {
		List<String> list = new ArrayList<>();
		for (String id : wishIds) {
			addUnique(list, seedOfItem(id));
		}
		addUnique(list, objectiveCrop.isEmpty() ? "" : seedOfCrop(objectiveCrop));
		return list;
	}

	private static void addUnique(List<String> list, String id) {
		if (!id.isEmpty() && !list.contains(id)) {
			list.add(id);
		}
	}

	// ------------------------------------------------------------------ source

	/**
	 * What to plant and where to take it.
	 *
	 * @param chest index of the storage chest in the list given to {@link #choose}, -1 = the villager's bag
	 */
	public record Choice(int chest, String item) {
		public boolean inBag() {
			return chest < 0;
		}
	}

	/**
	 * Chooses the item to plant. Preferred items first, in order (from the bag, else from the nearest chest holding
	 * enough); then, when {@code anyAllowed}, whatever the bag holds (bag order), then whatever the chests hold (nearest
	 * chest first). An item is usable from one source only when it holds at least {@code minCount} of it (4 for the
	 * saplings of 2×2 trees).
	 *
	 * @param bag    item id → count in the villager's bag, in bag order
	 * @param chests per storage chest the villager may take from (nearest first): item id → count, in slot order
	 * @return null when nothing can be planted
	 */
	public static @Nullable Choice choose(List<String> preferred, boolean anyAllowed, Map<String, Integer> bag, List<Map<String, Integer>> chests,
			Predicate<String> plantable, ToIntFunction<String> minCount) {
		for (String item : preferred) {
			if (!plantable.test(item)) {
				continue;
			}
			int min = Math.max(1, minCount.applyAsInt(item));
			if (bag.getOrDefault(item, 0) >= min) {
				return new Choice(-1, item);
			}
			for (int i = 0; i < chests.size(); i++) {
				if (chests.get(i).getOrDefault(item, 0) >= min) {
					return new Choice(i, item);
				}
			}
		}
		if (!anyAllowed) {
			return null;
		}
		for (Map.Entry<String, Integer> entry : bag.entrySet()) {
			if (plantable.test(entry.getKey()) && entry.getValue() >= Math.max(1, minCount.applyAsInt(entry.getKey()))) {
				return new Choice(-1, entry.getKey());
			}
		}
		for (int i = 0; i < chests.size(); i++) {
			for (Map.Entry<String, Integer> entry : chests.get(i).entrySet()) {
				if (plantable.test(entry.getKey()) && entry.getValue() >= Math.max(1, minCount.applyAsInt(entry.getKey()))) {
					return new Choice(i, entry.getKey());
				}
			}
		}
		return null;
	}

	/** Items taken from a chest in one trip: whole squares for 2×2 species, never more than the chest holds. */
	public static int fetchAmount(int wanted, int available, boolean square) {
		int amount = Math.max(0, Math.min(wanted, available));
		return square ? amount - amount % 4 : amount;
	}
}
