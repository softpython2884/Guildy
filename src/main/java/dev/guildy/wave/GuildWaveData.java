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
}
