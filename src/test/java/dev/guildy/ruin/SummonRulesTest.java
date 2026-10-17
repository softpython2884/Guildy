package dev.guildy.ruin;

import dev.guildy.ruin.SummonRules.Outcome;
import dev.guildy.ruin.SummonRules.Refusal;
import dev.guildy.ruin.SummonRules.Situation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Incantation ritual: mode, where a scroll may be read, participants, blood price, timeline and cancellation. */
class SummonRulesTest {
	/** A player 5 blocks from the marker of a dormant, loaded ruin, nothing else running. */
	private static Situation ok() {
		return new Situation(true, false, true, true, RuinState.DORMANT, 0, false, false, 25, 16, 0, 8, true);
	}

	@Test
	void modes() {
		assertEquals(SummonRules.MODE_SCROLL, SummonRules.mode(null));
		assertEquals(SummonRules.MODE_SCROLL, SummonRules.mode(" "));
		assertEquals(SummonRules.MODE_SCROLL, SummonRules.mode(" Scroll "));
		assertEquals(SummonRules.MODE_PROXIMITY, SummonRules.mode("PROXIMITY"));
		assertNull(SummonRules.mode("telepathy"));
		assertTrue(SummonRules.proximityAwakens("proximity"));
		assertFalse(SummonRules.proximityAwakens("scroll"));
		assertFalse(SummonRules.proximityAwakens("telepathy"), "unknown mode = scroll");
		assertFalse(SummonRules.proximityAwakens(new RuinConfig().summon.mode), "default: the guardian waits for a scroll");
	}

	@Test
	void validRitual() {
		assertEquals(Refusal.NONE, SummonRules.check(ok()));
		// Exactly at the arena's edge.
		assertEquals(Refusal.NONE, SummonRules.check(new Situation(true, false, true, true, RuinState.DORMANT, 0, false, false, 256, 16, 7, 8, true)));
	}

	@Test
	void everyRefusal() {
		assertEquals(Refusal.NOT_IN_RUIN, SummonRules.check(new Situation(true, false, false, true, RuinState.DORMANT, 0, false, false, 25, 16, 0, 8, true)));
		assertEquals(Refusal.WRONG_DIMENSION,
				SummonRules.check(new Situation(true, false, true, false, RuinState.DORMANT, 0, false, false, 25, 16, 0, 8, true)));
		assertEquals(Refusal.DISABLED, SummonRules.check(new Situation(false, false, true, true, RuinState.DORMANT, 0, false, false, 25, 16, 0, 8, true)));
		assertEquals(Refusal.PEACEFUL, SummonRules.check(new Situation(true, true, true, true, RuinState.DORMANT, 0, false, false, 25, 16, 0, 8, true)));
		assertEquals(Refusal.CLEARED, SummonRules.check(new Situation(true, false, true, true, RuinState.CLEARED, 3, false, false, 25, 16, 0, 8, true)));
		assertEquals(Refusal.CLEARED_FOREVER,
				SummonRules.check(new Situation(true, false, true, true, RuinState.CLEARED, -1, false, false, 25, 16, 0, 8, true)));
		assertEquals(Refusal.AWAKE, SummonRules.check(new Situation(true, false, true, true, RuinState.AWAKE, 0, false, false, 25, 16, 0, 8, true)));
		assertEquals(Refusal.AWAKE, SummonRules.check(new Situation(true, false, true, true, RuinState.DORMANT, 0, true, false, 25, 16, 0, 8, true)),
				"a fight running (state not updated yet) counts as awake");
		assertEquals(Refusal.RITUAL, SummonRules.check(new Situation(true, false, true, true, RuinState.DORMANT, 0, false, true, 25, 16, 0, 8, true)));
		assertEquals(Refusal.TOO_FAR, SummonRules.check(new Situation(true, false, true, true, RuinState.DORMANT, 0, false, false, 257, 16, 0, 8, true)));
		assertEquals(Refusal.BUSY, SummonRules.check(new Situation(true, false, true, true, RuinState.DORMANT, 0, false, false, 25, 16, 8, 8, true)));
		assertEquals(Refusal.NOT_LOADED, SummonRules.check(new Situation(true, false, true, true, RuinState.DORMANT, 0, false, false, 25, 16, 0, 8, false)));
	}

	@Test
	void refusalOrder() {
		// Outside any ruin, nothing else matters.
		assertEquals(Refusal.NOT_IN_RUIN, SummonRules.check(new Situation(false, true, false, false, RuinState.CLEARED, 2, true, true, 9999, 16, 99, 8, false)));
		// A defeated ruin says when it comes back, even from far away.
		assertEquals(Refusal.CLEARED, SummonRules.check(new Situation(true, false, true, true, RuinState.CLEARED, 2, false, false, 9999, 16, 0, 8, true)));
		// Awake beats "too far" (the fight is already there).
		assertEquals(Refusal.AWAKE, SummonRules.check(new Situation(true, false, true, true, RuinState.AWAKE, 0, true, false, 9999, 16, 0, 8, true)));
	}

	@Test
	void refusalLangKeys() {
		Set<String> keys = new HashSet<>();
		for (Refusal refusal : Refusal.values()) {
			assertTrue(refusal.langKey().startsWith("ruin.summon.fail."));
			keys.add(refusal.langKey());
		}
		assertEquals(Refusal.values().length, keys.size(), "one text per refusal");
		assertEquals("ruin.summon.fail.too_far", Refusal.TOO_FAR.langKey());
	}

	@Test
	void participants() {
		assertTrue(SummonRules.participant(true, false, true, 100, 16));
		assertTrue(SummonRules.participant(true, false, true, 256, 16), "at the edge");
		assertFalse(SummonRules.participant(true, false, true, 257, 16), "left the arena");
		assertFalse(SummonRules.participant(false, false, true, 1, 16), "dead");
		assertFalse(SummonRules.participant(true, true, true, 1, 16), "spectator");
		assertFalse(SummonRules.participant(true, false, false, 1, 16), "other dimension");
	}

	@Test
	void drainNeverBelowTheMinimum() {
		assertEquals(16.0F, SummonRules.drain(20.0F, 4, 4), 1.0E-6);
		assertEquals(4.0F, SummonRules.drain(6.0F, 4, 4), 1.0E-6, "stops at the minimum");
		assertEquals(4.0F, SummonRules.drain(4.0F, 4, 4), 1.0E-6, "already at the minimum: nothing taken");
		assertEquals(2.5F, SummonRules.drain(2.5F, 4, 4), 1.0E-6, "below the minimum: never lower, never healed");
		assertEquals(20.0F, SummonRules.drain(20.0F, 4, 0), 1.0E-6, "no price");
		assertEquals(20.0F, SummonRules.drain(20.0F, 4, -3), 1.0E-6, "negative amount ignored");
		// Paid in many heartbeats: the sum never goes below the minimum either.
		float health = 7.0F;
		for (int i = 0; i < 20; i++) {
			health = SummonRules.drain(health, 4, 0.5);
			assertTrue(health >= 4.0F - 1.0E-6, "beat " + i + ": " + health);
		}
		assertEquals(4.0F, health, 1.0E-6);
	}

	@Test
	void drainIsSpreadOverTheRitual() {
		double total = 4;
		assertEquals(0, SummonRules.drainTarget(0, total), 1.0E-9);
		assertEquals(0, SummonRules.drainTarget(SummonRules.DRAIN_START, total), 1.0E-9);
		assertEquals(total, SummonRules.drainTarget(SummonRules.DRAIN_END, total), 1.0E-9);
		assertEquals(total, SummonRules.drainTarget(1, total), 1.0E-9);
		double previous = 0;
		for (int i = 0; i <= 100; i++) {
			double target = SummonRules.drainTarget(i / 100.0, total);
			assertTrue(target >= previous - 1.0E-9 && target <= total + 1.0E-9, "monotonic and bounded at " + i);
			previous = target;
		}
		double mid = SummonRules.drainTarget((SummonRules.DRAIN_START + SummonRules.DRAIN_END) / 2, total);
		assertEquals(total / 2, mid, 1.0E-9, "half the price at half the drain phase");
		assertEquals(0, SummonRules.drainTarget(0.5, -2), 1.0E-9, "a negative price takes nothing");
		// Heartbeat increments add up exactly to the price.
		double paid = 0;
		double done = 0;
		for (int age = 0; age <= 140; age += 9) {
			double target = SummonRules.drainTarget(age / 140.0, total);
			paid += target - done;
			done = target;
		}
		paid += total - done;
		assertEquals(total, paid, 1.0E-9);
	}

	@Test
	void ritualTimeline() {
		int duration = SummonRules.ticks(7.0);
		assertEquals(140, duration);
		assertEquals(1, SummonRules.ticks(0));
		assertEquals(Outcome.CONTINUE, SummonRules.step(1, duration, 1, 0, SummonRules.GRACE_TICKS, true));
		assertEquals(Outcome.CONTINUE, SummonRules.step(duration - 1, duration, 3, 0, SummonRules.GRACE_TICKS, true));
		assertEquals(Outcome.CLIMAX, SummonRules.step(duration, duration, 1, 0, SummonRules.GRACE_TICKS, true));
		assertEquals(0, SummonRules.phase(0.2, 0.45, 1.0), 1.0E-9);
		assertEquals(0.5, SummonRules.phase(0.725, 0.45, 1.0), 1.0E-9);
		assertEquals(1, SummonRules.phase(1.2, 0.45, 1.0), 1.0E-9);
		assertEquals(1, SummonRules.phase(0.5, 0.5, 0.5), 1.0E-9, "empty phase: done once reached");
		assertTrue(SummonRules.heartbeatTicks(0) > SummonRules.heartbeatTicks(0.5));
		assertTrue(SummonRules.heartbeatTicks(0.5) > SummonRules.heartbeatTicks(1));
		assertTrue(SummonRules.heartbeatTicks(5) >= 6 && SummonRules.heartbeatTicks(-1) <= 20, "bounded");
		assertTrue(SummonRules.DRAIN_START < SummonRules.DRAIN_END && SummonRules.DRAIN_END < 1);
		assertTrue(SummonRules.COLUMN_START < SummonRules.TREMOR_START && SummonRules.TREMOR_START < 1);
	}

	@Test
	void cancelConditions() {
		int grace = SummonRules.GRACE_TICKS;
		// Everyone left (or died, or disconnected): a short grace, then cancelled.
		assertEquals(Outcome.CONTINUE, SummonRules.step(50, 140, 0, 1, grace, true));
		assertEquals(Outcome.CONTINUE, SummonRules.step(50, 140, 0, grace - 1, grace, true));
		assertEquals(Outcome.CANCEL, SummonRules.step(50, 140, 0, grace, grace, true));
		// Nobody at the climax: no guardian for an empty arena.
		assertEquals(Outcome.CONTINUE, SummonRules.step(140, 140, 0, 3, grace, true));
		assertEquals(Outcome.CANCEL, SummonRules.step(160, 140, 0, grace, grace, true));
		// Someone came back in time: the ritual goes on.
		assertEquals(Outcome.CLIMAX, SummonRules.step(150, 140, 1, 0, grace, true));
		// Awakened by other means (admin) meanwhile.
		assertEquals(Outcome.CANCEL, SummonRules.step(10, 140, 4, 0, grace, false));
		assertEquals(Outcome.CANCEL, SummonRules.step(140, 140, 4, 0, grace, false));
	}

	@Test
	void themeColors() {
		Set<Integer> colors = new HashSet<>();
		for (String name : List.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE")) {
			int color = SummonRules.themeColor(name);
			assertTrue(color > 0 && color <= 0xFFFFFF, name);
			colors.add(color);
		}
		assertEquals(7, colors.size(), "one colour per boss bar colour");
		assertEquals(SummonRules.themeColor("RED"), SummonRules.themeColor("nonsense"));
		assertEquals(SummonRules.themeColor("RED"), SummonRules.themeColor(null));
		assertEquals(SummonRules.themeColor("PURPLE"), SummonRules.themeColor(" purple "));
	}
}
