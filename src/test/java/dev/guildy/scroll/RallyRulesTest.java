package dev.guildy.scroll;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ralliement: who is called (every online member anywhere by default), why nobody is (with the numbers), and the acceptance
 * window of [Rejoindre] (nobody moves without clicking it in time).
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
	void theDefaultsCallEveryOnlineMemberAnywhere() {
		double range = Spell.RALLY.param("range").defaultValue();
		boolean cross = Spell.RALLY.param("crossDimension").defaultValue() > 0;
		assertEquals(0.0, range, "range 0 = every distance");
		assertTrue(cross, "the other dimensions too");
		assertTrue(RallyRules.unlimited(range));
		assertFalse(RallyRules.unlimited(512));
		// The member 2000 blocks away and the one in the Nether: both called (they were not with 512 / same dimension).
		assertEquals(RallyRules.Reason.CALLED, RallyRules.reason(false, true, true, true, 2000, range, cross));
		assertEquals(RallyRules.Reason.CALLED, RallyRules.reason(false, true, true, false, Double.MAX_VALUE, range, cross));
		assertEquals(RallyRules.Reason.FAR, RallyRules.reason(false, true, true, true, 2000, 512, false), "the old defaults: too far");
		assertEquals(RallyRules.Reason.OTHER_DIMENSION, RallyRules.reason(false, true, true, false, 0, 512, false), "the old defaults: Nether");
		// Never the caster, nobody offline, asleep or already here.
		assertEquals(RallyRules.Reason.CASTER, RallyRules.reason(true, true, true, true, 0, range, cross));
		assertEquals(RallyRules.Reason.OFFLINE, RallyRules.reason(false, false, false, false, 0, range, cross));
		assertEquals(RallyRules.Reason.UNAVAILABLE, RallyRules.reason(false, true, false, false, 0, range, cross), "asleep in the Nether");
		assertEquals(RallyRules.Reason.NEAR, RallyRules.reason(false, true, true, true, 3, range, cross));
		assertEquals(RallyRules.Reason.NEAR, RallyRules.reason(false, true, true, true, RallyRules.NEAR, range, cross));
		assertEquals(RallyRules.Reason.CALLED, RallyRules.reason(false, true, true, true, RallyRules.NEAR + 0.01, range, cross));
	}

	@Test
	void nobodyCalledSaysWhy() {
		// Someone called: no refusal.
		assertNull(RallyRules.refusal(RallyRules.census(List.of(RallyRules.Reason.CASTER, RallyRules.Reason.CALLED, RallyRules.Reason.NEAR)), 0));
		// Alone online (the others offline, or nobody else in the guild).
		assertEquals("scroll.fail.rally.alone", RallyRules.refusal(RallyRules.census(List.of(RallyRules.Reason.CASTER)), 0).key());
		assertEquals("scroll.fail.rally.alone",
				RallyRules.refusal(RallyRules.census(List.of(RallyRules.Reason.CASTER, RallyRules.Reason.OFFLINE, RallyRules.Reason.OFFLINE)), 0).key());
		// One reason for everyone: its own message with the number of members.
		RallyRules.Refusal near = RallyRules.refusal(RallyRules.census(List.of(RallyRules.Reason.CASTER, RallyRules.Reason.NEAR,
				RallyRules.Reason.NEAR, RallyRules.Reason.OFFLINE)), 0);
		assertEquals("scroll.fail.rally.near", near.key());
		assertEquals(List.of(2, 8), near.args(), "2 members within 8 blocks");
		assertEquals("scroll.fail.rally.unavailable",
				RallyRules.refusal(RallyRules.census(List.of(RallyRules.Reason.UNAVAILABLE)), 0).key());
		RallyRules.Refusal far = RallyRules.refusal(RallyRules.census(List.of(RallyRules.Reason.FAR, RallyRules.Reason.FAR, RallyRules.Reason.FAR)), 300);
		assertEquals("scroll.fail.rally.far", far.key());
		assertEquals(List.of(3, 300), far.args(), "3 members beyond the admin's 300 blocks");
		assertEquals("scroll.fail.rally.dimension", RallyRules.refusal(RallyRules.census(List.of(RallyRules.Reason.OTHER_DIMENSION)), 0).key());
		// Several reasons: every one with its number.
		Map<RallyRules.Reason, Integer> mixed = RallyRules.census(List.of(RallyRules.Reason.NEAR, RallyRules.Reason.UNAVAILABLE,
				RallyRules.Reason.UNAVAILABLE, RallyRules.Reason.OFFLINE));
		assertEquals("scroll.fail.rally.mixed", RallyRules.refusal(mixed, 0).key());
		List<RallyRules.Refusal> parts = RallyRules.parts(mixed, 0);
		assertEquals(2, parts.size());
		assertEquals("scroll.rally.reason.near", parts.get(0).key());
		assertEquals(List.of(1, 8), parts.get(0).args());
		assertEquals("scroll.rally.reason.unavailable", parts.get(1).key());
		assertEquals(List.of(2), parts.get(1).args());
		assertEquals(0, RallyRules.census(List.of()).get(RallyRules.Reason.CALLED));
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
