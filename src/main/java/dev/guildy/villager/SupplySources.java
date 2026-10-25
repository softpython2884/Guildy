package dev.guildy.villager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure item → natural source knowledge of the gatherers (unit tested): built from the drops of the natural blocks (what
 * a block gives when mined with a non-enchanted tool of the right kind, averaged over a few rolls: stone → cobblestone,
 * grass → dirt, clay → 4 clay balls, gravel → gravel and sometimes flint) and from the natural tree logs, then corrected by
 * the overrides of supply.json5 ("sources": item → blocks, {@code tree:<log>} for a tree, an empty list = never gathered).
 * A block giving less than {@link #MIN_YIELD} of an item per block is not a source of it.
 */
public final class SupplySources {
	/** Below this average yield per block, a block is not counted as a source (a rare bonus drop). */
	public static final double MIN_YIELD = 0.05;
	/** Prefix of a tree source in the overrides. */
	public static final String TREE = "tree:";

	public enum Method {
		/** Natural block dug in a pit. */
		DIG,
		/** Natural tree cut whole. */
		TREE
	}

	/** A block giving {@code perBlock} units of the item on average. */
	public record Source(String block, Method method, double perBlock) {
	}

	private final Map<String, List<Source>> byItem;

	/**
	 * @param drops    natural block id → item id → average units per block mined
	 * @param trees    natural log block id → its item id
	 * @param overrides item id → block ids ({@link #TREE} prefix for logs; empty = never gathered)
	 * @param never    item ids never gathered
	 */
	public SupplySources(Map<String, Map<String, Double>> drops, Map<String, String> trees, Map<String, List<String>> overrides, Set<String> never) {
		Map<String, List<Source>> index = new LinkedHashMap<>();
		drops.forEach((block, items) -> items.forEach((item, perBlock) -> {
			if (perBlock != null && perBlock >= MIN_YIELD) {
				index.computeIfAbsent(item, k -> new ArrayList<>()).add(new Source(block, Method.DIG, perBlock));
			}
		}));
		trees.forEach((log, item) -> index.computeIfAbsent(item, k -> new ArrayList<>()).add(new Source(log, Method.TREE, 1.0)));
		overrides.forEach((item, blocks) -> {
			List<Source> sources = new ArrayList<>();
			for (String block : blocks) {
				if (block.startsWith(TREE)) {
					String log = block.substring(TREE.length());
					if (trees.containsKey(log)) {
						sources.add(new Source(log, Method.TREE, 1.0));
					}
				} else if (drops.containsKey(block)) {
					double perBlock = drops.get(block).getOrDefault(item, 1.0);
					sources.add(new Source(block, Method.DIG, Math.max(MIN_YIELD, perBlock)));
				}
			}
			index.put(item, sources);
		});
		for (String item : never) {
			index.remove(item);
		}
		Map<String, List<Source>> frozen = new LinkedHashMap<>();
		index.forEach((item, sources) -> {
			if (!sources.isEmpty()) {
				List<Source> sorted = new ArrayList<>(sources);
				sorted.sort((a, b) -> Double.compare(b.perBlock(), a.perBlock()));
				frozen.put(item, Collections.unmodifiableList(sorted));
			}
		});
		this.byItem = Collections.unmodifiableMap(frozen);
	}

	/** Natural sources of an item, best yield first (empty: not gatherable). */
	public List<Source> sources(String item) {
		return byItem.getOrDefault(item, List.of());
	}

	public boolean gatherable(String item) {
		return byItem.containsKey(item);
	}

	/** Every gatherable item. */
	public Set<String> items() {
		return byItem.keySet();
	}

	/** Gatherable by the given method only (pits, or trees). */
	public Set<String> items(Method method) {
		Set<String> items = new LinkedHashSet<>();
		byItem.forEach((item, sources) -> {
			for (Source source : sources) {
				if (source.method() == method) {
					items.add(item);
					break;
				}
			}
		});
		return items;
	}

	/** Blocks that are a source of the item by this method. */
	public Set<String> blocks(String item, Method method) {
		Set<String> blocks = new LinkedHashSet<>();
		for (Source source : sources(item)) {
			if (source.method() == method) {
				blocks.add(source.block());
			}
		}
		return blocks;
	}

	/** Average units per block of the best source of this method (1 when unknown). */
	public double yield(String item, Method method) {
		for (Source source : sources(item)) {
			if (source.method() == method) {
				return source.perBlock();
			}
		}
		return 1.0;
	}
}
