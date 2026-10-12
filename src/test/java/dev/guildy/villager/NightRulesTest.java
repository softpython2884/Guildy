package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NightRulesTest {

	@Test
	void workersGoHomeBeforeTheVanillaRestAndTheHorde() {
		NightConfig night = new NightConfig();
		night.validate();
		assertTrue(night.homeTime < 12000, "home before the vanilla rest time");
		assertTrue(night.shelterTime > night.homeTime && night.shelterTime < 13000, "sheltered before the horde (13000)");
	}

	@Test
	void phasesOfTheDay() {
		assertEquals(NightRules.Phase.DAY, NightRules.phase(0, 11000, 12500));
		assertEquals(NightRules.Phase.DAY, NightRules.phase(10999, 11000, 12500));
		assertEquals(NightRules.Phase.EVENING, NightRules.phase(11000, 11000, 12500));
		assertEquals(NightRules.Phase.EVENING, NightRules.phase(12499, 11000, 12500));
		assertEquals(NightRules.Phase.NIGHT, NightRules.phase(12500, 11000, 12500));
		assertEquals(NightRules.Phase.NIGHT, NightRules.phase(23999, 11000, 12500));
		assertEquals(NightRules.Phase.DAY, NightRules.phase(24000, 11000, 12500), "next morning");
		assertEquals(NightRules.Phase.EVENING, NightRules.phase(5 * 24000L + 11500, 11000, 12500), "any day of the world");
	}

	@Test
	void disabledSteps() {
		assertEquals(NightRules.Phase.DAY, NightRules.phase(18000, -1, -1), "both off: vanilla nights");
		assertEquals(NightRules.Phase.DAY, NightRules.phase(11500, -1, 12500), "no evening walk");
		assertEquals(NightRules.Phase.NIGHT, NightRules.phase(12600, -1, 12500));
		assertEquals(NightRules.Phase.EVENING, NightRules.phase(18000, 11000, -1), "no teleport: walks home all night");
	}

	@Test
	void teleportOnlyAtNightfallOrDuringAHorde() {
		assertFalse(NightRules.mayTeleport(NightRules.Phase.EVENING, false, true));
		assertTrue(NightRules.mayTeleport(NightRules.Phase.NIGHT, false, true));
		assertTrue(NightRules.mayTeleport(NightRules.Phase.DAY, true, true), "event horde by day");
		assertFalse(NightRules.mayTeleport(NightRules.Phase.EVENING, true, false), "horde shelter off");
	}

	@Test
	void validateKeepsTheOrderAndBounds() {
		NightConfig night = new NightConfig();
		night.homeTime = 13000;
		night.shelterTime = 12000;
		night.speedLevel = 9;
		night.safeRadius = 0;
		night.validate();
		assertEquals(12000, night.homeTime, "never after the nightfall shelter");
		assertEquals(5, night.speedLevel);
		assertEquals(2, night.safeRadius);
		night.homeTime = -7;
		night.shelterTime = 99999;
		night.validate();
		assertEquals(-1, night.homeTime);
		assertEquals(23999, night.shelterTime);
	}
}
