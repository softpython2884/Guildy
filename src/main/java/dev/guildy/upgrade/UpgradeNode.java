package dev.guildy.upgrade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One upgrade as written in upgrades.json5. Validated into an immutable {@link UpgradeTree.Node}.
 */
public class UpgradeNode {
	public String id = "";
	public String branch = "";
	/** Position in its branch (1 = first), one node per tier and branch. */
	public int tier = 1;
	public String name = "";
	/** Lore lines ('&' colors). */
	public List<String> description = new ArrayList<>();
	public String icon = UpgradeTree.DEFAULT_NODE_ICON;
	/** Guild level required. */
	public int minLevel = 1;
	/** Node ids to unlock first. */
	public List<String> requires = new ArrayList<>();
	/** Treasury gold. */
	public int costGold = 0;
	/** Plain items taken from the inventory of the player who unlocks it. */
	public List<ItemCost> costItems = new ArrayList<>();
	/** Effect key ({@link UpgradeEffects}) to value. */
	public Map<String, Double> effects = new LinkedHashMap<>();

	public UpgradeNode() {
	}

	public static class ItemCost {
		public String item = "";
		public int count = 1;

		public ItemCost() {
		}

		public ItemCost(String item, int count) {
			this.item = item;
			this.count = count;
		}
	}
}
