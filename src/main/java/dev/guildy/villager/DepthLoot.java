package dev.guildy.villager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Pure miner loot logic (no Minecraft classes: unit tested): table selection by depth and weighted rolls.
 */
public final class DepthLoot {
	private DepthLoot() {
	}

	public record Drop(String item, int count) {
	}

	/**
	 * Table used at height {@code y}: the first table (sorted by maxY ascending) with {@code y <= maxY};
	 * above every table, the highest one. Null when there is no table.
	 */
	public static VillagerConfig.DepthTable tableFor(List<VillagerConfig.DepthTable> tables, int y) {
		VillagerConfig.DepthTable best = null;
		VillagerConfig.DepthTable highest = null;
		for (VillagerConfig.DepthTable table : tables) {
			if (table == null || table.entries == null || table.entries.isEmpty()) {
				continue;
			}
			if (y <= table.maxY && (best == null || table.maxY < best.maxY)) {
				best = table;
			}
			if (highest == null || table.maxY > highest.maxY) {
				highest = table;
			}
		}
		return best != null ? best : highest;
	}

	/** Rolls {@code rolls} weighted entries; drops of the same item are merged (insertion order kept). */
	public static List<Drop> roll(VillagerConfig.DepthTable table, int rolls, RandomGenerator random) {
		List<Drop> result = new ArrayList<>();
		if (table == null || table.entries == null || rolls <= 0) {
			return result;
		}
		int totalWeight = 0;
		for (VillagerConfig.LootEntry entry : table.entries) {
			if (entry != null && entry.weight > 0) {
				totalWeight += entry.weight;
			}
		}
		if (totalWeight <= 0) {
			return result;
		}
		Map<String, Integer> merged = new LinkedHashMap<>();
		for (int i = 0; i < rolls; i++) {
			int pick = random.nextInt(totalWeight);
			for (VillagerConfig.LootEntry entry : table.entries) {
				if (entry == null || entry.weight <= 0) {
					continue;
				}
				pick -= entry.weight;
				if (pick < 0) {
					int min = Math.max(1, entry.min);
					int max = Math.max(min, entry.max);
					int count = min + random.nextInt(max - min + 1);
					merged.merge(entry.item, count, Integer::sum);
					break;
				}
			}
		}
		for (Map.Entry<String, Integer> entry : merged.entrySet()) {
			result.add(new Drop(entry.getKey(), entry.getValue()));
		}
		return result;
	}
}
