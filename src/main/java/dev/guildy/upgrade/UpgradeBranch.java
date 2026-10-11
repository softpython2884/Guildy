package dev.guildy.upgrade;

/**
 * One branch of the upgrade tree as written in upgrades.json5 (a row of the menu).
 */
public class UpgradeBranch {
	public String id = "";
	public String name = "";
	/** Menu icon (item id). */
	public String icon = UpgradeTree.DEFAULT_BRANCH_ICON;

	public UpgradeBranch() {
	}

	public UpgradeBranch(String id, String name, String icon) {
		this.id = id;
		this.name = name;
		this.icon = icon;
	}
}
