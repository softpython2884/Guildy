package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure destination priority of the sorters (no Minecraft classes: unit tested). For an item, every registered chest has
 * a tier, the lower the better:
 * <ol start="0">
 * <li>a filter chest listing the item (exact items);</li>
 * <li>a filter chest of its material family ("diorite");</li>
 * <li>a filter chest of its type family ("wool");</li>
 * <li>a chest of its storage category;</li>
 * <li>a fallback ("Divers") chest;</li>
 * <li>a deposit chest.</li>
 * </ol>
 * Filter chests that do not accept the item, category chests of another category and free chests of the chest rooms
 * are no destination ({@link #NONE}). The sorter picks the best tier with room, the nearest chest first.
 */
public final class StorageRouting {
	public static final int NONE = Integer.MAX_VALUE;
	public static final int CATEGORY_TIER = 3;
	public static final int FALLBACK_TIER = 4;
	public static final int DEPOSIT_TIER = 5;

	/** What a registered chest is for the routing. */
	public enum Kind {
		FILTER,
		CATEGORY,
		FALLBACK,
		DEPOSIT,
		/** Free chest of a chest room (reserve): receives nothing. */
		RESERVE
	}

	/** A registered chest: its kind, its filter (FILTER) and its effective category (CATEGORY). */
	public record Target(Kind kind, @Nullable ChestFilter filter, String category) {
		public static Target filter(ChestFilter filter) {
			return new Target(Kind.FILTER, filter, "");
		}

		public static Target category(String category) {
			return new Target(Kind.CATEGORY, null, category);
		}

		public static Target fallback() {
			return new Target(Kind.FALLBACK, null, "");
		}

		public static Target deposit() {
			return new Target(Kind.DEPOSIT, null, "");
		}
	}

	private StorageRouting() {
	}

	/** Tier of the chest for an item of this id and storage category ({@link #NONE}: never a destination). */
	public static int tier(Target target, String itemId, String itemCategory) {
		return switch (target.kind()) {
			case FILTER -> target.filter() != null && target.filter().accepts(itemId) ? ChestFilters.specificity(target.filter()) : NONE;
			case CATEGORY -> target.category().equalsIgnoreCase(itemCategory) ? CATEGORY_TIER : NONE;
			case FALLBACK -> FALLBACK_TIER;
			case DEPOSIT -> DEPOSIT_TIER;
			case RESERVE -> NONE;
		};
	}

	/**
	 * Indices of the targets that may receive the item, best first: tier, then distance ({@code distSq}, same order
	 * as the targets); tiers above {@code maxTier} left out.
	 */
	public static List<Integer> candidates(List<Target> targets, double[] distSq, String itemId, String itemCategory, int maxTier) {
		List<int[]> ranked = new ArrayList<>();
		for (int i = 0; i < targets.size(); i++) {
			int tier = tier(targets.get(i), itemId, itemCategory);
			if (tier <= maxTier) {
				ranked.add(new int[]{tier, i});
			}
		}
		ranked.sort(Comparator.<int[]>comparingInt(r -> r[0]).thenComparingDouble(r -> distSq[r[1]]).thenComparingInt(r -> r[1]));
		List<Integer> order = new ArrayList<>(ranked.size());
		for (int[] r : ranked) {
			order.add(r[1]);
		}
		return order;
	}
}
