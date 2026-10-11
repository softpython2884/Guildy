package dev.guildy.event;

import dev.guildy.wave.WaveFormula;

/**
 * Pure event rules (unit-tested): roll windows, eligibility, cooldowns, target night of a blood night, raid steps.
 */
public final class EventRules {
	/** What a pending pillager raid does this second. */
	public enum RaidStep {
		/** Nothing yet (no defender near the heart, a wave is running, countdown not over). */
		WAIT,
		/** A defender is near the heart: warn the guild and start the countdown. */
		WARN,
		/** Countdown over and a defender is present: attack. */
		ATTACK,
		/** Dusk (or another day): the pillagers give up. */
		DROP
	}

	private EventRules() {
	}

	/** True when {@code timeOfDay} is in [start, start + window). */
	public static boolean inWindow(long timeOfDay, int start, int window) {
		return timeOfDay >= start && timeOfDay < start + Math.max(1, window);
	}

	/** At least {@code days} days since {@code lastDay} (always true when {@code days <= 0}: no cooldown). */
	public static boolean cooldownOver(long day, long lastDay, int days) {
		return days <= 0 || day - lastDay >= days;
	}

	/** A blood night can be rolled: a guild reached {@code minNightStep} and the last one is old enough. */
	public static boolean bloodNightEligible(int highestNightStep, int minNightStep, long day, long lastBloodNight, int minDaysBetween) {
		return highestNightStep >= minNightStep && cooldownOver(day, lastBloodNight, minDaysBetween);
	}

	/** A per-guild day event can be rolled for this guild. */
	public static boolean guildEligible(boolean enabled, boolean founded, int level, int minLevel, long day, long lastDay, int cooldownDays) {
		return enabled && founded && level >= minLevel && cooldownOver(day, lastDay, cooldownDays);
	}

	/** Day index of the night an event started now concerns: tonight until dawn, else the next night. */
	public static long targetNight(long clock, int nightEnd) {
		long day = WaveFormula.dayIndex(clock);
		return WaveFormula.timeOfDay(clock) < nightEnd ? day : day + 1;
	}

	/**
	 * Duration (seconds) of a raid starting at {@code timeOfDay}: the configured maximum, cut so that a raid started by day
	 * is over at nightfall (the night horde of the guild can only start once it is over), at least 60 s.
	 */
	public static int raidDuration(int maxSeconds, long timeOfDay, int nightStart) {
		if (timeOfDay >= nightStart) {
			return Math.max(60, maxSeconds);
		}
		long untilNight = (nightStart - timeOfDay) / 20;
		return (int) Math.max(60, Math.min(maxSeconds, untilNight));
	}

	/**
	 * Next step of a pending raid.
	 *
	 * @param raidDay   day the raid was rolled for
	 * @param raidAt    game time of the attack once warned (0 = not warned yet)
	 * @param defenders members near the heart
	 */
	public static RaidStep raidStep(long raidDay, long day, long timeOfDay, int duskTime, long raidAt, long gameTime, int defenders,
			boolean waveActive) {
		if (raidDay != day || timeOfDay >= duskTime) {
			return RaidStep.DROP;
		}
		if (waveActive || defenders <= 0) {
			return RaidStep.WAIT;
		}
		if (raidAt <= 0) {
			return RaidStep.WARN;
		}
		return gameTime >= raidAt ? RaidStep.ATTACK : RaidStep.WAIT;
	}
}
