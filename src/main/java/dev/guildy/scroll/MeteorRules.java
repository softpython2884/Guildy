package dev.guildy.scroll;

import java.util.List;

/**
 * Aim of the meteor shower (pure, unit-tested). Two meteors out of three are called down on a monster of the area and
 * follow it during their fall (moving monsters are no longer missed); the third one falls at random in the area. A called
 * meteor goes to the monster that the fewest meteors were called on (the nearest to the aimed point first), skipping the
 * monsters already doomed by the meteors still falling on them: the escort falls first, then everything goes to the boss.
 */
public final class MeteorRules {
	private MeteorRules() {
	}

	/**
	 * A monster that may be aimed at.
	 *
	 * @param health   health left (absorption included)
	 * @param pending  damage of the meteors still falling on it
	 * @param aimed    meteors already called on it during this cast
	 * @param distance distance (blocks) from the aimed point to its hitbox
	 */
	public record Candidate(double health, double pending, int aimed, double distance) {
	}

	/** True when the meteor of this index (0 = the first) is called down on a monster, false when it falls at random. */
	public static boolean aimed(int index) {
		return index % 3 != 2;
	}

	/** Index of the monster the meteor is called on, or -1 (none left to aim at: the meteor falls at random). */
	public static int pick(List<Candidate> candidates) {
		int best = -1;
		for (int i = 0; i < candidates.size(); i++) {
			Candidate c = candidates.get(i);
			if (c == null || !(c.health() > 0) || c.pending() >= c.health()) {
				continue;
			}
			if (best < 0) {
				best = i;
				continue;
			}
			Candidate b = candidates.get(best);
			if (c.aimed() < b.aimed() || c.aimed() == b.aimed() && c.distance() < b.distance()) {
				best = i;
			}
		}
		return best;
	}
}
