package dev.guildy.wave;

import java.util.random.RandomGenerator;

/**
 * Nights between two hordes of a guild (pure, tested): after each night horde the next one is drawn
 * {@code minDaysBetweenHordes}..{@code maxDaysBetweenHordes} days later. A horde that could not start on its night
 * (nobody home) stays due: it comes the first following night with a defender.
 */
final class WaveSchedule {
	private WaveSchedule() {
	}

	/** Day from which the horde after one started on {@code day} may come. */
	static long next(WaveConfig.Timing timing, long day, RandomGenerator random) {
		int min = Math.max(1, timing.minDaysBetweenHordes);
		int max = Math.max(min, timing.maxDaysBetweenHordes);
		return day + min + (max > min ? random.nextInt(max - min + 1) : 0);
	}

	/** A horde may start tonight (never scheduled, or its day has come). */
	static boolean due(GuildWaveData data, long day) {
		return data.nextWaveDay < 0 || day >= data.nextWaveDay;
	}

	/** Nights before the next horde (0 = tonight). */
	static int nightsUntil(GuildWaveData data, long day) {
		return data.nextWaveDay < 0 ? 0 : (int) Math.max(0, Math.min(Integer.MAX_VALUE, data.nextWaveDay - day));
	}
}
