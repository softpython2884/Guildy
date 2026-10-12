package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure rules of the "objectifs prioritaires" (unit tested, block / item ids only): which trees and crops give a wanted
 * item, and the list editing helpers used by the wishlist menu.
 */
public final class WishMatch {
	/** Items a farmer can bring back from vanilla crops (relevance hint of the wishlist menu). */
	public static final Set<String> FARM_ITEMS = Set.of("minecraft:wheat", "minecraft:wheat_seeds", "minecraft:carrot",
			"minecraft:potato", "minecraft:poisonous_potato", "minecraft:beetroot", "minecraft:beetroot_seeds", "minecraft:melon_slice",
			"minecraft:melon", "minecraft:pumpkin", "minecraft:sugar_cane");

	private WishMatch() {
	}

	// ------------------------------------------------------------------ trees

	/** Wood of a natural log block: "minecraft:oak_log" → "oak", "minecraft:crimson_stem" → "crimson"; null otherwise. */
	public static @Nullable String wood(String logId) {
		String path = path(logId);
		if (path.startsWith("stripped_")) {
			return null;
		}
		if (path.endsWith("_log")) {
			return path.substring(0, path.length() - 4);
		}
		if (path.endsWith("_stem")) {
			return path.substring(0, path.length() - 5);
		}
		return null;
	}

	/** Cutting these logs gives the item: the log itself, its wood block, the stripped variants or the planks (crafted). */
	public static boolean fromLogs(String logId, String itemId) {
		String wood = wood(logId);
		if (wood == null || !namespace(logId).equals(namespace(itemId))) {
			return false;
		}
		String item = path(itemId);
		return item.equals(wood + "_log") || item.equals(wood + "_wood") || item.equals("stripped_" + wood + "_log")
				|| item.equals("stripped_" + wood + "_wood") || item.equals(wood + "_planks") || item.equals(wood + "_stem")
				|| item.equals(wood + "_hyphae") || item.equals("stripped_" + wood + "_stem") || item.equals("stripped_" + wood + "_hyphae");
	}

	/** Sapling (or equivalent) dropped by these leaves, "" when they drop none (mangrove leaves only drop sticks). */
	public static String saplingOf(String leavesId) {
		String path = path(leavesId);
		if (!path.endsWith("_leaves")) {
			return "";
		}
		String kind = path.substring(0, path.length() - 7);
		String ns = namespace(leavesId);
		return switch (kind) {
			case "mangrove" -> "";
			case "azalea" -> ns + ":azalea";
			case "flowering_azalea" -> ns + ":flowering_azalea";
			default -> kind.endsWith("_poplar") ? ns + ":poplar_sapling" : ns + ":" + kind + "_sapling";
		};
	}

	/** Breaking these leaves may drop the item: their sapling, sticks, apples (oak and dark oak leaves). */
	public static boolean fromLeaves(String leavesId, String itemId) {
		if (!path(leavesId).endsWith("_leaves")) {
			return false;
		}
		if (itemId.equals("minecraft:stick")) {
			return true;
		}
		if (itemId.equals("minecraft:apple")) {
			return leavesId.equals("minecraft:oak_leaves") || leavesId.equals("minecraft:dark_oak_leaves");
		}
		String sapling = saplingOf(leavesId);
		return !sapling.isEmpty() && sapling.equals(itemId);
	}

	/**
	 * Before looking at its leaves: may a tree made of these logs give the item through its leaves? (azalea trees have
	 * oak logs, poplars have red / orange / yellow leaves.)
	 */
	public static boolean leavesMayGive(String logId, String itemId) {
		String wood = wood(logId);
		if (wood == null) {
			return false;
		}
		if (itemId.equals("minecraft:stick")) {
			return true;
		}
		if (itemId.equals("minecraft:apple")) {
			return logId.equals("minecraft:oak_log") || logId.equals("minecraft:dark_oak_log");
		}
		String ns = namespace(logId);
		if (wood.equals("oak") && (itemId.equals(ns + ":azalea") || itemId.equals(ns + ":flowering_azalea"))) {
			return true;
		}
		return itemId.equals(ns + ":" + wood + "_sapling");
	}

	// ------------------------------------------------------------------ crops

	/**
	 * A ripe plant of this block gives the item: its seed item ({@code seedId}, the item that plants it), or its harvest
	 * (wheat, carrots, potatoes, beetroots, melon slices, pumpkins, sugar cane).
	 */
	public static boolean fromCrop(String blockId, @Nullable String seedId, String itemId) {
		if (itemId.equals(seedId)) {
			return true;
		}
		if (!namespace(blockId).equals(namespace(itemId))) {
			return false;
		}
		String block = path(blockId);
		String item = path(itemId);
		return switch (block) {
			case "wheat" -> item.equals("wheat") || item.equals("wheat_seeds");
			case "carrots" -> item.equals("carrot");
			case "potatoes" -> item.equals("potato") || item.equals("poisonous_potato");
			case "beetroots" -> item.equals("beetroot") || item.equals("beetroot_seeds");
			case "melon" -> item.equals("melon_slice") || item.equals("melon");
			default -> item.equals(block) || (block.endsWith("s") && item.equals(block.substring(0, block.length() - 1)));
		};
	}

	// ------------------------------------------------------------------ list editing

	/**
	 * Adds an id at the end of the list (no duplicate, at most {@code max} entries).
	 *
	 * @return false when the list is full, the id already listed or blank
	 */
	public static boolean add(List<String> list, String id, int max) {
		if (id == null || id.isBlank() || list.contains(id) || list.size() >= max) {
			return false;
		}
		list.add(id);
		return true;
	}

	/** Removes the id. @return false when it was not listed */
	public static boolean remove(List<String> list, String id) {
		return list.remove(id);
	}

	/** The first {@code shown} entries of a merged list (own entries first, no duplicate), for a summary line. */
	public static List<String> head(List<String> own, List<String> shared, int shown) {
		List<String> merged = merged(own, shared);
		return merged.size() <= shown ? merged : new ArrayList<>(merged.subList(0, shown));
	}

	/** Own entries then the shared ones not already listed. */
	public static List<String> merged(List<String> own, List<String> shared) {
		List<String> merged = new ArrayList<>(own);
		for (String id : shared) {
			if (!merged.contains(id)) {
				merged.add(id);
			}
		}
		return merged;
	}

	static String namespace(String id) {
		int colon = id.indexOf(':');
		return colon < 0 ? "minecraft" : id.substring(0, colon);
	}

	static String path(String id) {
		int colon = id.indexOf(':');
		return colon < 0 ? id : id.substring(colon + 1);
	}
}
