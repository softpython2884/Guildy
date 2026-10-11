package dev.guildy.upgrade;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-guild upgrade tree progress (persisted inside the guild).
 */
public class GuildUpgradeData {
	/** Unlocked node ids. Ids missing from upgrades.json5 are kept (they count again if the node comes back). */
	public Set<String> unlocked = new LinkedHashSet<>();

	/** Called after deserialization. */
	public void fixNulls() {
		if (unlocked == null) {
			unlocked = new LinkedHashSet<>();
		}
		unlocked.removeIf(id -> id == null || id.isBlank());
	}
}
