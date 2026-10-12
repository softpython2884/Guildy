package dev.guildy.villager;

/**
 * Pure time rules of the villagers' evening (no Minecraft classes, unit tested). The day of Minecraft starts at 0
 * (sunrise), the vanilla villagers go to bed at 12000, the hordes start at 13000; the night window ends at 24000.
 */
final class NightRules {
	/** Ticks in a Minecraft day. */
	static final long DAY_TICKS = 24000L;

	private NightRules() {
	}

	enum Phase {
		/** Workers work. */
		DAY,
		/** {@code homeTime} reached: workers stop and walk home (speed boost). */
		EVENING,
		/** {@code shelterTime} reached (nightfall): a worker still outside is brought next to its bed. */
		NIGHT
	}

	/**
	 * Phase of the day for the workers. A negative {@code homeTime} disables the evening walk, a negative
	 * {@code shelterTime} disables the nightfall shelter (both negative = always {@link Phase#DAY}).
	 */
	static Phase phase(long clockTime, int homeTime, int shelterTime) {
		long time = Math.floorMod(clockTime, DAY_TICKS);
		if (shelterTime >= 0 && time >= shelterTime) {
			return Phase.NIGHT;
		}
		if (homeTime >= 0 && time >= homeTime) {
			return Phase.EVENING;
		}
		return Phase.DAY;
	}

	/** Shelter (teleport) allowed now: nightfall reached, or a horde in progress (when sheltering on hordes is on). */
	static boolean mayTeleport(Phase phase, boolean horde, boolean shelterOnHorde) {
		return phase == Phase.NIGHT || horde && shelterOnHorde;
	}
}
