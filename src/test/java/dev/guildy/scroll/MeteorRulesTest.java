package dev.guildy.scroll;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aim of the meteor shower: two meteors out of three called on a monster, spread over the escort, then on the boss. */
class MeteorRulesTest {
	private static MeteorRules.Candidate c(double health, double pending, int aimed, double distance) {
		return new MeteorRules.Candidate(health, pending, aimed, distance);
	}

	@Test
	void twoMeteorsOutOfThreeAreCalled() {
		int called = 0;
		for (int i = 0; i < 10; i++) {
			called += MeteorRules.aimed(i) ? 1 : 0;
		}
		assertEquals(7, called, "7 of the 10 default meteors");
		assertTrue(MeteorRules.aimed(0), "the first one always");
		assertFalse(MeteorRules.aimed(2), "the third one falls at random");
	}

	@Test
	void theFewestAimedThenTheNearest() {
		assertEquals(-1, MeteorRules.pick(List.of()), "nobody: at random");
		assertEquals(1, MeteorRules.pick(List.of(c(650, 0, 0, 2), c(28, 0, 0, 1))), "nearest first");
		assertEquals(1, MeteorRules.pick(List.of(c(650, 28, 1, 0), c(28, 0, 0, 3))), "the escort mob nobody aimed at yet");
		assertEquals(0, MeteorRules.pick(List.of(c(650, 28, 1, 0), c(28, 28, 1, 3))), "a doomed mob is skipped");
		assertEquals(-1, MeteorRules.pick(List.of(c(20, 28, 1, 0), c(0, 0, 0, 1))), "every mob doomed or dead: at random");
	}

	@Test
	void anEscortFallsFirstThenEverythingGoesToTheBoss() {
		// Boss of 650 health points at the aimed point, three escort mobs of 28 around it; meteors of 28 that land before the
		// next ones are called (worst case: nothing dies before the end).
		List<double[]> mobs = new ArrayList<>(List.of(new double[] {650, 0, 0, 0}, new double[] {28, 0, 0, 2}, new double[] {28, 0, 0, 3},
				new double[] {28, 0, 0, 4}));
		int[] calls = new int[4];
		for (int i = 0; i < 10; i++) {
			if (!MeteorRules.aimed(i)) {
				continue;
			}
			List<MeteorRules.Candidate> candidates = new ArrayList<>();
			for (double[] mob : mobs) {
				candidates.add(c(mob[0], mob[1], (int) mob[2], mob[3]));
			}
			int pick = MeteorRules.pick(candidates);
			assertTrue(pick >= 0, "always a target: the boss is never doomed");
			mobs.get(pick)[1] += 28;
			mobs.get(pick)[2]++;
			calls[pick]++;
		}
		assertEquals(1, calls[1], "one meteor is enough for each escort mob");
		assertEquals(1, calls[2]);
		assertEquals(1, calls[3]);
		assertEquals(4, calls[0], "the four other called meteors on the boss");
	}
}
