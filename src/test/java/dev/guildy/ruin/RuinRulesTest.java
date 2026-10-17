package dev.guildy.ruin;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure rules of the ruins: encounter state machine, timings, escort size, XP split, research, naming. */
class RuinRulesTest {

	@Test
	void dormantRuinsAwakenOnlyWhenEnabledAndApproached() {
		assertEquals(RuinRules.Action.AWAKEN, RuinRules.decide(RuinState.DORMANT, true, true, 0, 60, false, false));
		assertEquals(RuinRules.Action.NONE, RuinRules.decide(RuinState.DORMANT, false, true, 0, 60, false, false), "bosses disabled / peaceful / limit");
		assertEquals(RuinRules.Action.NONE, RuinRules.decide(RuinState.DORMANT, true, false, 0, 60, false, false), "too far from the guardian");
	}

	@Test
	void awakeFightsResetWhenAloneAndCancelWhenUnloaded() {
		assertEquals(RuinRules.Action.NONE, RuinRules.decide(RuinState.AWAKE, true, false, 59, 60, true, false));
		assertEquals(RuinRules.Action.RESET, RuinRules.decide(RuinState.AWAKE, true, false, 60, 60, true, false));
		assertEquals(RuinRules.Action.CANCEL, RuinRules.decide(RuinState.AWAKE, true, false, 0, 60, false, false), "guardian unloaded");
		assertEquals(RuinRules.Action.CANCEL, RuinRules.decide(RuinState.AWAKE, true, false, 500, 60, false, false), "unload wins over reset");
		assertEquals(RuinRules.Action.RESET, RuinRules.decide(RuinState.AWAKE, true, false, 1, 0, true, false), "reset delay of at least 1 s");
	}

	@Test
	void defeatedGuardiansComeBackAfterTheirRespawnDays() {
		assertFalse(RuinRules.rearmed(10, 16, 7));
		assertTrue(RuinRules.rearmed(10, 17, 7));
		assertTrue(RuinRules.rearmed(10, 40, 7));
		assertFalse(RuinRules.rearmed(10, 5, 7), "clock moved backwards");
		assertFalse(RuinRules.rearmed(10, 1000, 0), "0 = never");
		assertEquals(RuinRules.Action.REARM, RuinRules.decide(RuinState.CLEARED, true, true, 0, 60, false, true));
		assertEquals(RuinRules.Action.NONE, RuinRules.decide(RuinState.CLEARED, true, true, 0, 60, false, false));

		assertEquals(5, RuinRules.daysLeft(10, 12, 7));
		assertEquals(0, RuinRules.daysLeft(10, 20, 7));
		assertEquals(7, RuinRules.daysLeft(10, 5, 7), "a clock moved backwards never makes the wait longer");
		assertEquals(-1, RuinRules.daysLeft(10, 12, 0));
	}

	@Test
	void escortGrowsWithPlayersUpToItsCap() {
		assertEquals(3, RuinRules.minionCount(3, 2, 8, 1));
		assertEquals(5, RuinRules.minionCount(3, 2, 8, 2));
		assertEquals(8, RuinRules.minionCount(3, 2, 8, 5));
		assertEquals(3, RuinRules.minionCount(3, 2, 8, 0), "at least the base escort");
		assertEquals(0, RuinRules.minionCount(3, 2, 0, 4));
		assertEquals(2, RuinRules.summonCount(3, 10, 12));
		assertEquals(0, RuinRules.summonCount(3, 12, 12));
		assertEquals(0, RuinRules.summonCount(3, 20, 12));
	}

	@Test
	void xpIsSplitBetweenTheGuildsOfTheParticipants() {
		Map<String, Integer> one = Map.of("A", 1);
		assertEquals(Map.of("A", 120L), RuinRules.splitXp(one, 120, 10));

		Map<String, Integer> two = new LinkedHashMap<>();
		two.put("A", 2);
		two.put("B", 1);
		Map<String, Long> split = RuinRules.splitXp(two, 100, 10);
		assertEquals(67L, split.get("A"), "2 of 3 participants, the rounding remainder to the largest group");
		assertEquals(33L, split.get("B"));
		assertEquals(100L, split.get("A") + split.get("B"), "nothing lost to rounding");

		Map<String, Integer> lopsided = new LinkedHashMap<>();
		lopsided.put("A", 9);
		lopsided.put("B", 1);
		Map<String, Long> floor = RuinRules.splitXp(lopsided, 50, 10);
		assertEquals(45L, floor.get("A"));
		assertEquals(10L, floor.get("B"), "minimum per guild");

		assertTrue(RuinRules.splitXp(new LinkedHashMap<String, Integer>(), 100, 10).isEmpty(), "nobody took part");
		assertEquals(Map.of("A", 0L), RuinRules.splitXp(one, 0, 10), "no XP to share");
	}

	@Test
	void researchProgress() {
		assertEquals(3, RuinRules.researchWorkers(5, 3));
		assertEquals(0, RuinRules.researchWorkers(0, 3));
		assertEquals(1, RuinRules.researchWorkers(2, 0), "at least one cartographer counts");
		assertEquals(50, RuinRules.percent(1.5, 3));
		assertEquals(100, RuinRules.percent(5, 3));
		assertEquals(100, RuinRules.percent(0, 0));
		assertEquals(0, RuinRules.percent(-2, 3));
		assertEquals(1, RuinRules.secondsLeft(1, 3, 2));
		assertEquals(0, RuinRules.secondsLeft(3, 3, 1));
		assertEquals(-1, RuinRules.secondsLeft(0, 3, 0), "paused");
		assertEquals("##--", RuinRules.bar(0.5, 4, '#', '-'));
		assertEquals("####", RuinRules.bar(2, 4, '#', '-'));
	}

	@Test
	void ruinIdsComeFromTheTemplatePath() {
		assertEquals("crypte_oubliee", RuinRules.ruinIdOf("ruin/crypte_oubliee"));
		assertEquals("tour_du_mage", RuinRules.ruinIdOf("ruin/tour_du_mage/sommet"));
		assertNull(RuinRules.ruinIdOf("ruins/crypte"));
		assertNull(RuinRules.ruinIdOf("ruin/"));
		assertNull(RuinRules.ruinIdOf("village/plains/houses"));
		assertNull(RuinRules.ruinIdOf(null));
		assertTrue(RuinRules.isRuinTemplate("ruin/x"));
		assertFalse(RuinRules.isRuinTemplate("trail_ruins/tower"));
	}

	@Test
	void dataMarkers() {
		assertEquals(RuinRules.Marker.BOSS, RuinRules.marker("guildy:boss"));
		assertEquals(RuinRules.Marker.MINION, RuinRules.marker(" guildy:minion "));
		assertEquals(RuinRules.Marker.ENTRANCE, RuinRules.marker("guildy:entrance"));
		assertNull(RuinRules.marker("chest"));
		assertNull(RuinRules.marker(""));
		assertNull(RuinRules.marker(null));
	}

	@Test
	void abilitiesRespectCooldownsAndHealthConditions() {
		long[] readyAt = {0, 100, 0};
		double[] healthBelow = {1.0, 1.0, 0.5};
		assertEquals(List.of(0), RuinRules.readyAbilities(50, readyAt, healthBelow, 0.8, 0));
		assertEquals(List.of(0, 2), RuinRules.readyAbilities(50, readyAt, healthBelow, 0.4, 0));
		assertEquals(List.of(2, 0), RuinRules.readyAbilities(50, readyAt, healthBelow, 0.4, 1), "rotated");
		assertEquals(List.of(0, 1, 2), RuinRules.readyAbilities(100, readyAt, healthBelow, 0.5, 0), "threshold included");
		assertTrue(RuinRules.readyAbilities(0, new long[0], new double[0], 1, 3).isEmpty());
		assertEquals(240, RuinRules.cooldownTicks(12, 1.0));
		assertEquals(120, RuinRules.cooldownTicks(12, 0.5), "enraged");
		assertEquals(20, RuinRules.cooldownTicks(0, 1.0), "at least one second");
	}
}
