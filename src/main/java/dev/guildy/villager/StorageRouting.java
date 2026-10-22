package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure destination priority of the sorters (no Minecraft classes: unit tested). For an item, every registered chest has
 * a tier, the lower the better:
 * <ol start="0">
 * <li>a filter chest listing the item (exact items), or a chest of a filtered room that already holds this kind of item
 * ({@link Kind#CONTENT}: its filter is its own contents, {@link ContentKeys});</li>
 * <li>a filter chest of its material family ("diorite");</li>
 * <li>a filter chest of its type family ("wool");</li>
 * <li>a chest of the first category of its routing chain ({@link StorageCategories.Resolution#chain}: a transversal
 * category it matches, else its deepest category), then {@code 4, 5...} the next categories of the chain (its parent
 * category... its top-level category);</li>
 * <li>{@link #FALLBACK_TIER}: a fallback ("Divers") chest;</li>
 * <li>{@link #DEPOSIT_TIER}: a deposit chest.</li>
 * </ol>
 * Filter chests that do not accept the item, filtered-room chests that do not hold it (an empty one receives nothing),
 * category chests of a category outside its chain and free chests of the chest rooms are no destination ({@link #NONE}). The sorter picks the best tier with room, the nearest chest first.
 * <p>
 * {@link Index} answers that question without looking at every chest: chests are grouped by category, filter and kind
 * once, and only the groups of the item's tiers are read (nearest first, one tier at a time); the filtered-room chests
 * holding the item come from a live index of their contents ({@link ContentHolders}).
 */
public final class StorageRouting {
	public static final int NONE = Integer.MAX_VALUE;
	/** Tier of a filtered-room chest holding the item: the tier of a filter chest listing it. */
	public static final int CONTENT_TIER = 0;
	public static final int CATEGORY_TIER = 3;
	/** Longest routing chain followed (transversal categories + sub-categories + top-level category). */
	public static final int MAX_CHAIN = 8;
	public static final int FALLBACK_TIER = CATEGORY_TIER + MAX_CHAIN;
	public static final int DEPOSIT_TIER = FALLBACK_TIER + 1;

	/** What a registered chest is for the routing. */
	public enum Kind {
		FILTER,
		CATEGORY,
		FALLBACK,
		DEPOSIT,
		/** Free chest of a chest room (reserve): receives nothing. */
		RESERVE,
		/** Chest of a filtered room: receives the kinds of items it already holds ({@link Target#contents}). */
		CONTENT
	}

	/**
	 * A registered chest: its kind, its filter (FILTER), its effective category (CATEGORY) and the kinds of items it holds
	 * (CONTENT: a live read-only view, {@link ContentIndex#kinds}).
	 */
	public record Target(Kind kind, @Nullable ChestFilter filter, String category, @Nullable Set<String> contents) {
		public Target(Kind kind, @Nullable ChestFilter filter, String category) {
			this(kind, filter, category, null);
		}

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

		public static Target reserve() {
			return new Target(Kind.RESERVE, null, "");
		}

		/** Chest of a filtered room holding these kinds of items ({@link ContentKeys} keys). */
		public static Target content(Set<String> kinds) {
			return new Target(Kind.CONTENT, null, "", kinds);
		}
	}

	/** Filtered-room chests currently holding a kind of item (indices of the targets of an {@link Index}). */
	@FunctionalInterface
	public interface ContentHolders {
		ContentHolders NONE = key -> new int[0];

		int[] holders(String key);
	}

	private StorageRouting() {
	}

	/** Tier of the chest for an item of this id whose routing chain is just {@code itemCategory}. */
	public static int tier(Target target, String itemId, String itemCategory) {
		return tier(target, itemId, List.of(itemCategory));
	}

	/** Tier of the chest for an item of this id and routing chain ({@link #NONE}: never a destination); kind = item id. */
	public static int tier(Target target, String itemId, List<String> chain) {
		return tier(target, itemId, itemId, chain);
	}

	/**
	 * Tier of the chest for an item of this id, kind ({@link ContentKeys} key: what a filtered-room chest must hold) and
	 * routing chain ({@link #NONE}: never a destination).
	 */
	public static int tier(Target target, String itemId, String contentKey, List<String> chain) {
		return switch (target.kind()) {
			case FILTER -> target.filter() != null && target.filter().accepts(itemId) ? ChestFilters.specificity(target.filter()) : NONE;
			case CONTENT -> target.contents() != null && target.contents().contains(contentKey) ? CONTENT_TIER : NONE;
			case CATEGORY -> {
				int index = indexOf(chain, target.category());
				yield index < 0 || index >= MAX_CHAIN ? NONE : CATEGORY_TIER + index;
			}
			case FALLBACK -> FALLBACK_TIER;
			case DEPOSIT -> DEPOSIT_TIER;
			case RESERVE -> NONE;
		};
	}

	private static int indexOf(List<String> chain, String category) {
		for (int i = 0; i < chain.size(); i++) {
			if (chain.get(i).equalsIgnoreCase(category)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Indices of the targets that may receive the item, best first: tier, then distance ({@code distSq}, same order
	 * as the targets); tiers above {@code maxTier} left out.
	 */
	public static List<Integer> candidates(List<Target> targets, double[] distSq, String itemId, String itemCategory, int maxTier) {
		return candidates(targets, distSq, itemId, List.of(itemCategory), maxTier);
	}

	/** Same as {@link #candidates(List, double[], String, String, int)} with a routing chain. */
	public static List<Integer> candidates(List<Target> targets, double[] distSq, String itemId, List<String> chain, int maxTier) {
		return candidates(targets, distSq, itemId, itemId, chain, maxTier);
	}

	/** Same as {@link #candidates(List, double[], String, List, int)} with the item's kind for the filtered rooms. */
	public static List<Integer> candidates(List<Target> targets, double[] distSq, String itemId, String contentKey, List<String> chain,
			int maxTier) {
		List<int[]> ranked = new ArrayList<>();
		for (int i = 0; i < targets.size(); i++) {
			int tier = tier(targets.get(i), itemId, contentKey, chain);
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

	// ================================================================ index

	/** Chests of one tier for an item (indices of the targets of the {@link Index}). */
	public record Bucket(int tier, int[] members) {
	}

	/**
	 * The registered chests of a guild grouped once (by category, filter, fallback, deposit) with their block positions,
	 * so that finding a destination only reads the chests of the item's tiers. Built for one state of the chest list;
	 * filter answers are remembered per item id. The filtered-room chests holding an item are asked to
	 * {@link ContentHolders} at each question (their contents change all the time).
	 */
	public static final class Index {
		private static final int MEMO_LIMIT = 4096;
		private final List<Target> targets;
		private final int[] xs;
		private final int[] ys;
		private final int[] zs;
		private final Map<String, int[]> categories = new HashMap<>();
		private final int[] fallbacks;
		private final int[] deposits;
		/** Filter chests by filter key: the filter and its chests. */
		private final List<ChestFilter> filters = new ArrayList<>();
		private final List<int[]> filterMembers = new ArrayList<>();
		private final Map<String, List<Bucket>> filterMemo = new HashMap<>();
		private final ContentHolders content;

		/**
		 * @param targets the chests as the routing sees them
		 * @param xs      block x of each target (same order), likewise {@code ys}, {@code zs}
		 */
		public Index(List<Target> targets, int[] xs, int[] ys, int[] zs) {
			this(targets, xs, ys, zs, ContentHolders.NONE);
		}

		/** Same, with the live index of the filtered-room chests ({@code content}: target indices holding a kind). */
		public Index(List<Target> targets, int[] xs, int[] ys, int[] zs, ContentHolders content) {
			this.content = content;
			this.targets = List.copyOf(targets);
			this.xs = xs.clone();
			this.ys = ys.clone();
			this.zs = zs.clone();
			Map<String, List<Integer>> byCategory = new LinkedHashMap<>();
			Map<String, List<Integer>> byFilter = new LinkedHashMap<>();
			Map<String, ChestFilter> filterOf = new HashMap<>();
			List<Integer> fallbackList = new ArrayList<>();
			List<Integer> depositList = new ArrayList<>();
			for (int i = 0; i < targets.size(); i++) {
				Target target = targets.get(i);
				switch (target.kind()) {
					case CATEGORY -> byCategory.computeIfAbsent(target.category().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(i);
					case FILTER -> {
						if (target.filter() != null) {
							String key = target.filter().key();
							filterOf.putIfAbsent(key, target.filter());
							byFilter.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
						}
					}
					case FALLBACK -> fallbackList.add(i);
					case DEPOSIT -> depositList.add(i);
					case RESERVE, CONTENT -> {
					}
				}
			}
			for (Map.Entry<String, List<Integer>> entry : byCategory.entrySet()) {
				categories.put(entry.getKey(), toArray(entry.getValue()));
			}
			for (Map.Entry<String, List<Integer>> entry : byFilter.entrySet()) {
				filters.add(filterOf.get(entry.getKey()));
				filterMembers.add(toArray(entry.getValue()));
			}
			fallbacks = toArray(fallbackList);
			deposits = toArray(depositList);
		}

		public int size() {
			return targets.size();
		}

		public Target target(int index) {
			return targets.get(index);
		}

		/** Number of CATEGORY chests of a category (case ignored). */
		public int categoryCount(String category) {
			int[] members = categories.get(category.toLowerCase(Locale.ROOT));
			return members == null ? 0 : members.length;
		}

		/** CATEGORY chests of a category (case ignored), indices of the targets. */
		public int[] categoryMembers(String category) {
			int[] members = categories.get(category.toLowerCase(Locale.ROOT));
			return members == null ? new int[0] : members.clone();
		}

		public int fallbackCount() {
			return fallbacks.length;
		}

		public int depositCount() {
			return deposits.length;
		}

		/**
		 * The chests that may receive the item, grouped by tier (best first; tiers above {@code maxTier} left out). With
		 * {@code maxTier} = {@link #DEPOSIT_TIER} these are also every chest that may give the item to a worker.
		 */
		public List<Bucket> buckets(String itemId, List<String> chain, int maxTier) {
			return buckets(itemId, itemId, chain, maxTier);
		}

		/**
		 * Same as {@link #buckets(String, List, int)} for an item of this kind ({@link ContentKeys}): the filtered-room
		 * chests holding it share the first tier with the filter chests listing it (nearest first among them).
		 */
		public List<Bucket> buckets(String itemId, String contentKey, List<String> chain, int maxTier) {
			List<Bucket> buckets = new ArrayList<>(chain.size() + 5);
			int[] held = CONTENT_TIER <= maxTier ? contentMembers(contentKey) : new int[0];
			for (Bucket bucket : filterBuckets(itemId)) {
				if (bucket.tier() > maxTier) {
					continue;
				}
				if (held.length > 0 && bucket.tier() == CONTENT_TIER) {
					bucket = new Bucket(CONTENT_TIER, union(bucket.members(), held));
					held = new int[0];
				}
				buckets.add(bucket);
			}
			if (held.length > 0) {
				buckets.addFirst(new Bucket(CONTENT_TIER, held));
			}
			for (int i = 0; i < chain.size() && i < MAX_CHAIN; i++) {
				int tier = CATEGORY_TIER + i;
				int[] members = categories.get(chain.get(i).toLowerCase(Locale.ROOT));
				if (tier <= maxTier && members != null) {
					buckets.add(new Bucket(tier, members));
				}
			}
			if (FALLBACK_TIER <= maxTier && fallbacks.length > 0) {
				buckets.add(new Bucket(FALLBACK_TIER, fallbacks));
			}
			if (DEPOSIT_TIER <= maxTier && deposits.length > 0) {
				buckets.add(new Bucket(DEPOSIT_TIER, deposits));
			}
			return buckets;
		}

		/** Filter chests accepting the item, one bucket per tier (exact, material, type), remembered per item id. */
		private List<Bucket> filterBuckets(String itemId) {
			if (filters.isEmpty()) {
				return List.of();
			}
			List<Bucket> known = filterMemo.get(itemId);
			if (known != null) {
				return known;
			}
			Map<Integer, List<int[]>> byTier = new HashMap<>();
			for (int f = 0; f < filters.size(); f++) {
				ChestFilter filter = filters.get(f);
				if (filter.accepts(itemId)) {
					byTier.computeIfAbsent(ChestFilters.specificity(filter), t -> new ArrayList<>()).add(filterMembers.get(f));
				}
			}
			List<Bucket> buckets = new ArrayList<>(byTier.size());
			List<Integer> tiers = new ArrayList<>(byTier.keySet());
			tiers.sort(null);
			for (int tier : tiers) {
				List<int[]> parts = byTier.get(tier);
				int total = 0;
				for (int[] part : parts) {
					total += part.length;
				}
				int[] members = new int[total];
				int at = 0;
				for (int[] part : parts) {
					System.arraycopy(part, 0, members, at, part.length);
					at += part.length;
				}
				Arrays.sort(members);
				buckets.add(new Bucket(tier, members));
			}
			List<Bucket> result = List.copyOf(buckets);
			if (filterMemo.size() >= MEMO_LIMIT) {
				filterMemo.clear();
			}
			filterMemo.put(itemId, result);
			return result;
		}

		/** Filtered-room targets holding the kind (sorted, each once; other targets the provider names are left out). */
		private int[] contentMembers(String contentKey) {
			int[] held = content.holders(contentKey);
			if (held.length == 0) {
				return held;
			}
			int[] members = new int[held.length];
			int count = 0;
			for (int member : held) {
				if (member >= 0 && member < targets.size() && targets.get(member).kind() == Kind.CONTENT) {
					members[count++] = member;
				}
			}
			members = Arrays.copyOf(members, count);
			Arrays.sort(members);
			int distinct = 0;
			for (int i = 0; i < members.length; i++) {
				if (i == 0 || members[i] != members[i - 1]) {
					members[distinct++] = members[i];
				}
			}
			return Arrays.copyOf(members, distinct);
		}

		private static int[] union(int[] a, int[] b) {
			int[] all = new int[a.length + b.length];
			System.arraycopy(a, 0, all, 0, a.length);
			System.arraycopy(b, 0, all, a.length, b.length);
			Arrays.sort(all);
			return all;
		}

		/** Best tier of the item among the targets {@code outside} accepts (NONE when none, tiers above maxTier ignored). */
		public int bestTier(String itemId, List<String> chain, int maxTier, java.util.function.IntPredicate outside) {
			return bestTier(itemId, itemId, chain, maxTier, outside);
		}

		/** Same as {@link #bestTier(String, List, int, java.util.function.IntPredicate)} for an item of this kind. */
		public int bestTier(String itemId, String contentKey, List<String> chain, int maxTier, java.util.function.IntPredicate outside) {
			for (Bucket bucket : buckets(itemId, contentKey, chain, maxTier)) {
				for (int member : bucket.members()) {
					if (outside.test(member)) {
						return bucket.tier();
					}
				}
			}
			return NONE;
		}

		/** Destinations of the item best first: tier, then distance to (x, y, z), then registration order; lazily sorted. */
		public Cursor cursor(String itemId, List<String> chain, int maxTier, double x, double y, double z) {
			return cursor(itemId, itemId, chain, maxTier, x, y, z);
		}

		/** Same as {@link #cursor(String, List, int, double, double, double)} for an item of this kind. */
		public Cursor cursor(String itemId, String contentKey, List<String> chain, int maxTier, double x, double y, double z) {
			return new Cursor(this, buckets(itemId, contentKey, chain, maxTier), x, y, z);
		}

		double distSq(int index, double x, double y, double z) {
			double dx = xs[index] + 0.5 - x;
			double dy = ys[index] - y;
			double dz = zs[index] + 0.5 - z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	/** Walks the destinations of an item tier by tier, each tier sorted by distance only when reached. */
	public static final class Cursor {
		private final Index index;
		private final List<Bucket> buckets;
		private final double x;
		private final double y;
		private final double z;
		private int bucket = -1;
		private int[] order = new int[0];
		private int position;
		private int tier = NONE;

		Cursor(Index index, List<Bucket> buckets, double x, double y, double z) {
			this.index = index;
			this.buckets = buckets;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		/** Next target index, -1 when none is left. */
		public int next() {
			while (position >= order.length) {
				bucket++;
				if (bucket >= buckets.size()) {
					return -1;
				}
				Bucket current = buckets.get(bucket);
				tier = current.tier();
				order = sorted(current.members());
				position = 0;
			}
			return order[position++];
		}

		/** Tier of the target last returned by {@link #next()}. */
		public int tier() {
			return tier;
		}

		private int[] sorted(int[] members) {
			if (members.length <= 1) {
				return members;
			}
			// Distance as float bits (monotonic for positive floats) in the high half, member index in the low half: one
			// primitive sort gives distance then registration order.
			long[] keys = new long[members.length];
			for (int i = 0; i < members.length; i++) {
				float d = (float) index.distSq(members[i], x, y, z);
				keys[i] = ((long) Float.floatToIntBits(d) << 32) | (members[i] & 0xffffffffL);
			}
			Arrays.sort(keys);
			int[] sorted = new int[members.length];
			for (int i = 0; i < keys.length; i++) {
				sorted[i] = (int) keys[i];
			}
			return sorted;
		}
	}

	private static int[] toArray(List<Integer> list) {
		int[] array = new int[list.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = list.get(i);
		}
		return array;
	}
}
