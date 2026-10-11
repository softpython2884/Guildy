package dev.guildy.villager;

/**
 * Mission goal of a gathering task (miner, lumberjack, farmer) or an expedition. Persisted by name.
 */
public enum Goal {
	/** Works forever (deposits whenever the bag is full). */
	CONTINUOUS,
	/** Fills the bag once (one trip for an expedition), brings it back, then the mission ends. */
	FILL,
	/** Brings back {@code goalAmount} items (of {@code goalItem} if set), then the mission ends. */
	QUOTA;

	public String langKey() {
		return "villager.goal." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public Goal next() {
		Goal[] values = values();
		return values[(ordinal() + 1) % values.length];
	}
}
