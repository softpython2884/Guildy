package dev.guildy.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-guild random events state (persisted inside the guild).
 */
public class GuildEventData {
	/** Overworld day index of the last morning roll (at most one roll per day). */
	public long lastRollDay = -1;
	/** Event id -> day index of its last occurrence (cooldowns). */
	public Map<String, Long> lastDay = new LinkedHashMap<>();
	/** Pillager raid waiting to attack: day index (-1 = none). Dropped at dusk. */
	public long raidDay = -1;
	/** Game time of the attack once the members were warned (0 = not warned yet). */
	public long raidAt;
	/** Raids started (statistics). */
	public int raids;

	public long last(String eventId) {
		Long day = lastDay.get(eventId);
		return day == null ? Long.MIN_VALUE / 2 : day;
	}

	public boolean raidPending() {
		return raidDay >= 0;
	}

	public void clearRaid() {
		raidDay = -1;
		raidAt = 0;
	}

	/** Called after deserialization. */
	public void fixNulls() {
		if (lastDay == null) {
			lastDay = new LinkedHashMap<>();
		}
		lastDay.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
	}
}
