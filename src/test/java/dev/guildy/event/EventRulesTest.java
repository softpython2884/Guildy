package dev.guildy.event;

import dev.guildy.event.EventRules.RaidStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventRulesTest {
	private static final int DUSK = 12000;

	@Test
	void rollWindows() {
		assertTrue(EventRules.inWindow(1000, 1000, 1000));
		assertTrue(EventRules.inWindow(1999, 1000, 1000));
		assertFalse(EventRules.inWindow(2000, 1000, 1000));
		assertFalse(EventRules.inWindow(999, 1000, 1000));
	}

	@Test
	void cooldowns() {
		assertTrue(EventRules.cooldownOver(10, 7, 3));
		assertFalse(EventRules.cooldownOver(10, 8, 3));
		assertTrue(EventRules.cooldownOver(10, 10, 0), "no cooldown");
		assertTrue(EventRules.cooldownOver(0, Long.MIN_VALUE / 2, 4), "never happened");
	}

	@Test
	void bloodNightNeedsAnExperiencedGuildAndFourDays() {
		assertFalse(EventRules.bloodNightEligible(2, 3, 20, -1000, 4), "no guild at night step 3");
		assertTrue(EventRules.bloodNightEligible(3, 3, 20, -1000, 4));
		assertFalse(EventRules.bloodNightEligible(9, 3, 20, 17, 4), "last one 3 days ago");
		assertTrue(EventRules.bloodNightEligible(9, 3, 21, 17, 4));
	}

	@Test
	void guildEventsNeedLevelCooldownAndBase() {
		assertTrue(EventRules.guildEligible(true, true, 2, 2, 10, -5, 3));
		assertFalse(EventRules.guildEligible(false, true, 2, 2, 10, -5, 3), "disabled");
		assertFalse(EventRules.guildEligible(true, false, 2, 2, 10, -5, 3), "no heart");
		assertFalse(EventRules.guildEligible(true, true, 1, 2, 10, -5, 3), "level too low");
		assertFalse(EventRules.guildEligible(true, true, 5, 2, 10, 8, 3), "cooldown");
	}

	@Test
	void targetNightIsTonightUntilDawn() {
		int nightEnd = 23000;
		assertEquals(5, EventRules.targetNight(5 * 24000 + 1000, nightEnd), "morning: tonight");
		assertEquals(5, EventRules.targetNight(5 * 24000 + 15000, nightEnd), "during the night: this night");
		assertEquals(6, EventRules.targetNight(5 * 24000 + 23500, nightEnd), "after dawn: the next night");
	}

	@Test
	void raidWaitsForADefenderThenWarnsThenAttacks() {
		long day = 3;
		// Rolled, nobody home.
		assertEquals(RaidStep.WAIT, EventRules.raidStep(day, day, 2000, DUSK, 0, 100, 0, false));
		// A member comes home: warning, countdown starts.
		assertEquals(RaidStep.WARN, EventRules.raidStep(day, day, 2000, DUSK, 0, 100, 1, false));
		// Countdown running.
		assertEquals(RaidStep.WAIT, EventRules.raidStep(day, day, 2100, DUSK, 2500, 2400, 1, false));
		// Countdown over but nobody home: waits.
		assertEquals(RaidStep.WAIT, EventRules.raidStep(day, day, 3000, DUSK, 2500, 2600, 0, false));
		// Countdown over, defender present.
		assertEquals(RaidStep.ATTACK, EventRules.raidStep(day, day, 3000, DUSK, 2500, 2600, 2, false));
		// Another wave running: waits.
		assertEquals(RaidStep.WAIT, EventRules.raidStep(day, day, 3000, DUSK, 2500, 2600, 2, true));
		// Dusk or another day: the pillagers give up.
		assertEquals(RaidStep.DROP, EventRules.raidStep(day, day, DUSK, DUSK, 2500, 2600, 2, false));
		assertEquals(RaidStep.DROP, EventRules.raidStep(day, day + 1, 1000, DUSK, 0, 2600, 2, false));
	}

	@Test
	void raidsEndBeforeTheNightHorde() {
		assertEquals(600, EventRules.raidDuration(600, 1000, 13000), "morning: full duration");
		assertEquals(100, EventRules.raidDuration(600, 11000, 13000), "late: cut at nightfall");
		assertEquals(60, EventRules.raidDuration(600, 12990, 13000), "at least one minute");
		assertEquals(600, EventRules.raidDuration(600, 15000, 13000), "admin raid at night: full duration");
	}
}
