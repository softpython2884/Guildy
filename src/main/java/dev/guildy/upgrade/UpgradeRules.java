package dev.guildy.upgrade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Pure availability rules of the upgrade tree (unit-tested): node state and why it cannot be unlocked.
 */
public final class UpgradeRules {
	public enum State {
		UNLOCKED,
		/** Level and requirements met (costs not checked). */
		AVAILABLE,
		LOCKED
	}

	/** First reason preventing an unlock, in the order players fix them. */
	public enum Problem {
		NONE,
		ALREADY_UNLOCKED,
		LEVEL,
		REQUIREMENTS,
		GOLD,
		ITEMS
	}

	private UpgradeRules() {
	}

	public static State state(UpgradeTree.Node node, Collection<String> unlocked, int level) {
		if (unlocked.contains(node.id())) {
			return State.UNLOCKED;
		}
		return level >= node.minLevel() && missingRequirements(node, unlocked).isEmpty() ? State.AVAILABLE : State.LOCKED;
	}

	/** Required node ids not unlocked yet. */
	public static List<String> missingRequirements(UpgradeTree.Node node, Collection<String> unlocked) {
		List<String> missing = new ArrayList<>();
		for (String requirement : node.requires()) {
			if (!unlocked.contains(requirement)) {
				missing.add(requirement);
			}
		}
		return missing;
	}

	/**
	 * Cost items the player lacks.
	 *
	 * @param owned plain items owned by the player, by item id
	 * @return item id to missing count (cost order), empty when everything is there
	 */
	public static Map<String, Integer> missingItems(UpgradeTree.Node node, ToIntFunction<String> owned) {
		Map<String, Integer> missing = new LinkedHashMap<>();
		for (UpgradeTree.Cost cost : node.costItems()) {
			int have = Math.max(0, owned.applyAsInt(cost.item()));
			if (have < cost.count()) {
				missing.put(cost.item(), cost.count() - have);
			}
		}
		return missing;
	}

	public static Problem check(UpgradeTree.Node node, Collection<String> unlocked, int level, long treasury, ToIntFunction<String> owned) {
		if (unlocked.contains(node.id())) {
			return Problem.ALREADY_UNLOCKED;
		}
		if (level < node.minLevel()) {
			return Problem.LEVEL;
		}
		if (!missingRequirements(node, unlocked).isEmpty()) {
			return Problem.REQUIREMENTS;
		}
		if (treasury < node.costGold()) {
			return Problem.GOLD;
		}
		if (!missingItems(node, owned).isEmpty()) {
			return Problem.ITEMS;
		}
		return Problem.NONE;
	}
}
