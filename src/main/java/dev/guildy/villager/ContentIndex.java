package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the chests of the filtered rooms of a guild hold (runtime only, pure: chests are plain {@code long} ids, kinds are
 * {@link ContentKeys} keys; unit tested): the kinds of each chest (its filter) and, for each kind, the chests holding it,
 * so that the sorter finds the chests of an item without reading any other chest. Updated when a chest is read again
 * ({@link RoomContents}); an empty chest holds no kind, so it is the destination of nothing.
 */
public final class ContentIndex {
	private static final long[] NONE = new long[0];

	/** Kinds per chest: the set objects stay the same (live views are handed out), only their contents change. */
	private final Map<Long, Set<String>> kinds = new HashMap<>();
	private final Map<Long, Set<String>> views = new HashMap<>();
	private final Map<String, Set<Long>> holders = new HashMap<>();
	/** Chests read at least once. */
	private final Set<Long> known = new HashSet<>();
	/** Identity rules the keys were made with (null: none yet). */
	private ContentKeys.@Nullable Rules rules;

	/** Read-only live view of the kinds the chest holds (empty for an unknown or empty chest). */
	public Set<String> kinds(long chest) {
		Set<String> view = views.get(chest);
		if (view == null) {
			Set<String> set = kinds.computeIfAbsent(chest, c -> new HashSet<>());
			view = Collections.unmodifiableSet(set);
			views.put(chest, view);
		}
		return view;
	}

	/** True when the chest holds this kind. */
	public boolean holds(long chest, String key) {
		Set<String> set = kinds.get(chest);
		return set != null && set.contains(key);
	}

	/** True when the chest was read at least once since it was added. */
	public boolean known(long chest) {
		return known.contains(chest);
	}

	/**
	 * The chest holds exactly these kinds now.
	 *
	 * @return true when its kinds changed
	 */
	public boolean update(long chest, Collection<String> now) {
		known.add(chest);
		Set<String> set = kinds.computeIfAbsent(chest, c -> new HashSet<>());
		if (set.size() == now.size() && set.containsAll(now)) {
			return false;
		}
		for (String key : set) {
			if (!now.contains(key)) {
				Set<Long> chests = holders.get(key);
				if (chests != null) {
					chests.remove(chest);
					if (chests.isEmpty()) {
						holders.remove(key);
					}
				}
			}
		}
		for (String key : now) {
			if (!set.contains(key)) {
				holders.computeIfAbsent(key, k -> new HashSet<>()).add(chest);
			}
		}
		set.clear();
		set.addAll(now);
		return true;
	}

	/** The chest is no filtered-room chest any more. */
	public void remove(long chest) {
		Set<String> set = kinds.remove(chest);
		views.remove(chest);
		known.remove(chest);
		if (set == null) {
			return;
		}
		for (String key : set) {
			Set<Long> chests = holders.get(key);
			if (chests != null) {
				chests.remove(chest);
				if (chests.isEmpty()) {
					holders.remove(key);
				}
			}
		}
		set.clear();
	}

	/** Forgets every chest outside {@code chests}. */
	public void retain(Set<Long> chests) {
		List<Long> gone = new ArrayList<>();
		for (Long chest : kinds.keySet()) {
			if (!chests.contains(chest)) {
				gone.add(chest);
			}
		}
		for (Long chest : gone) {
			remove(chest);
		}
	}

	/** Chests holding this kind (a copy). */
	public long[] holders(String key) {
		Set<Long> chests = holders.get(key);
		if (chests == null || chests.isEmpty()) {
			return NONE;
		}
		long[] result = new long[chests.size()];
		int i = 0;
		for (long chest : chests) {
			result[i++] = chest;
		}
		return result;
	}

	/** Number of chests holding this kind. */
	public int holderCount(String key) {
		Set<Long> chests = holders.get(key);
		return chests == null ? 0 : chests.size();
	}

	/** Chests known to the index (read at least once or asked for). */
	public int size() {
		return kinds.size();
	}

	/**
	 * Keys are made with these rules: when they differ from the rules of the known keys (configuration changed), every
	 * chest is forgotten (read again later).
	 *
	 * @return true when the index was cleared
	 */
	public boolean useRules(ContentKeys.Rules now) {
		if (now.equals(rules)) {
			return false;
		}
		boolean had = !kinds.isEmpty();
		rules = now;
		for (Set<String> set : kinds.values()) {
			set.clear();
		}
		holders.clear();
		known.clear();
		return had;
	}
}
