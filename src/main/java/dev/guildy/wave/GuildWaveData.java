package dev.guildy.wave;

/**
 * Per-guild wave progression (persisted inside the guild).
 */
public class GuildWaveData {
	/** Difficulty step: +1 per victory, reduced on defeat (anti death-spiral). */
	public int night;
	public int victories;
	public int defeats;
	public int streak;
	public int bestStreak;
	/** Lang key of the last result (e.g. "wave.result.victory"), empty if none. */
	public String lastResult = "";
	/** Overworld day index of the last wave start (prevents two waves in the same night). */
	public long lastWaveDay = -1;
	/** Defeats in a row (night hordes and event waves), reset by a victory: triggers the truce. */
	public int defeatsInRow;
	/** Nights of truce still to come (no horde, no raid), see {@link WaveTruce}. */
	public int truceNights;
	/** Overworld day index of the last night spared by the truce (-1 = none). */
	public long truceDay = -1;

	// Details of the last wave (shown in the waves menu).
	/** Theme display name ('&' codes) of the last wave. */
	public String lastTheme = "";
	public int lastKills;
	public int lastTotal;
	/** Epoch millis of the end of the last wave (0 = never). */
	public long lastAt;
	/** Treasury gold won (victory) or lost (defeat, positive amount) by the last wave. */
	public long lastGold;
	/** Guild XP won by the last wave. */
	public long lastXp;

	// Lifetime statistics.
	public int totalKills;
	public int bossesKilled;
}
