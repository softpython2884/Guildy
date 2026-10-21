package dev.guildy.scroll;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ralliement: who is called, and the acceptance window of [Rejoindre] (nobody moves without clicking it in time).
 */
class RallyRulesTest {
	private static final long NOW = 1000;
	private static final long EXPIRES = NOW + 600;

	/** A valid click (called member, open call, room left, same dimension, awake). */
	private static RallyRules.Click click() {
		return new RallyRules.Click(NOW, EXPIRES, true, true, true, false, 2, 8, true, false, true);
	}

	@Test
	void whoIsCalled() {
		assertTrue(RallyRules.called(false, true, true, true, 120, 512, false), "a member 120 blocks away");
		assertFalse(RallyRules.called(true, true, true, true, 120, 512, false), "never the caster");
		assertFalse(RallyRules.called(false, false, true, true, 120, 512, false), "never someone of another guild");
		assertFalse(RallyRules.called(false, true, false, true, 120, 512, false), "dead, asleep or spectating");
		assertFalse(RallyRules.called(false, true, true, true, 600, 512, false), "out of range");
		assertFalse(RallyRules.called(false, true, true, true, 5, 512, false), "already at the rally point");
		assertTrue(RallyRules.called(false, true, true, true, RallyRules.NEAR + 0.1, 512, false));
		assertFalse(RallyRules.called(false, true, true, false, 0, 512, false), "another dimension");
		assertTrue(RallyRules.called(false, true, true, false, 0, 512, true), "another dimension when the call crosses them");
	}

	@Test
	void aClickInTimeJoins() {
		assertEquals(RallyRules.Verdict.OK, RallyRules.verdict(click()));
		assertNull(RallyRules.failureKey(RallyRules.Verdict.OK));
		// The last second of the window still counts, the next tick no more.
		RallyRules.Click last = new RallyRules.Click(EXPIRES, EXPIRES, true, true, true, false, 2, 8, true, false, true);
		assertEquals(RallyRules.Verdict.OK, RallyRules.verdict(last));
		RallyRules.Click late = new RallyRules.Click(EXPIRES + 1, EXPIRES, true, true, true, false, 2, 8, true, false, true);
		assertEquals(RallyRules.Verdict.EXPIRED, RallyRules.verdict(late));
	}

	@Test
	void everyRefusal() {
		assertEquals(RallyRules.Verdict.NOT_INVITED, RallyRules.verdict(new RallyRules.Click(NOW, EXPIRES, true, false, true, false, 2, 8, true, false, true)),
				"not called (another guild, came online later)");
		assertEquals(RallyRules.Verdict.NOT_INVITED, RallyRules.verdict(new RallyRules.Click(NOW, EXPIRES, true, true, false, false, 2, 8, true, false, true)),
				"left the guild since");
		assertEquals(RallyRules.Verdict.CLOSED, RallyRules.verdict(new RallyRules.Click(NOW, EXPIRES, false, true, true, false, 2, 8, true, false, true)),
				"the caster logged out");
		assertEquals(RallyRules.Verdict.ALREADY_JOINED, RallyRules.verdict(new RallyRules.Click(NOW, EXPIRES, true, true, true, true, 2, 8, true, false, true)));
		assertEquals(RallyRules.Verdict.FULL, RallyRules.verdict(new RallyRules.Click(NOW, EXPIRES, true, true, true, false, 8, 8, true, false, true)));
		assertEquals(RallyRules.Verdict.WRONG_DIMENSION,
				RallyRules.verdict(new RallyRules.Click(NOW, EXPIRES, true, true, true, false, 2, 8, false, false, true)));
		assertEquals(RallyRules.Verdict.OK, RallyRules.verdict(new RallyRules.Click(NOW, EXPIRES, true, true, true, false, 2, 8, false, true, true)),
				"another dimension is fine when the call crosses them");
		assertEquals(RallyRules.Verdict.UNAVAILABLE, RallyRules.verdict(new RallyRules.Click(NOW, EXPIRES, true, true, true, false, 2, 8, true, false, false)));
		// The order: a stranger is told it is not for them, even after the call expired.
		assertEquals(RallyRules.Verdict.NOT_INVITED,
				RallyRules.verdict(new RallyRules.Click(EXPIRES + 100, EXPIRES, false, false, true, false, 8, 8, false, false, false)));
		assertEquals(RallyRules.Verdict.EXPIRED, RallyRules.verdict(new RallyRules.Click(EXPIRES + 1, EXPIRES, true, true, true, true, 8, 8, true, false, true)),
				"expired before already joined or full");
		for (RallyRules.Verdict verdict : RallyRules.Verdict.values()) {
			if (verdict != RallyRules.Verdict.OK) {
				assertNotNull(RallyRules.failureKey(verdict), verdict.name());
			}
		}
	}

	@Test
	void secondsLeft() {
		assertEquals(30, RallyRules.secondsLeft(NOW, NOW + 600));
		assertEquals(1, RallyRules.secondsLeft(NOW, NOW + 1), "a last tick still shows one second");
		assertEquals(0, RallyRules.secondsLeft(NOW, NOW));
		assertEquals(0, RallyRules.secondsLeft(NOW + 50, NOW));
	}
}
