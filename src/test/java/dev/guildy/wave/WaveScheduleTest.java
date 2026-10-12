package dev.guildy.wave;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.SplittableRandom;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveScheduleTest {

	@Test
	void nextHordeComesOneToThreeDaysLater() {
		WaveConfig.Timing timing = new WaveConfig.Timing(); // 1..3 days
		SplittableRandom random = new SplittableRandom(42);
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < 300; i++) {
			long next = WaveSchedule.next(timing, 100, random);
			assertTrue(next >= 101 && next <= 103, "got " + next);
			seen.add(next);
		}
		assertEquals(Set.of(101L, 102L, 103L), seen);
	}

	@Test
	void sameMinAndMaxIsAFixedInterval() {
		WaveConfig.Timing timing = new WaveConfig.Timing();
		timing.minDaysBetweenHordes = 1;
		timing.maxDaysBetweenHordes = 1;
		assertEquals(11, WaveSchedule.next(timing, 10, new SplittableRandom(1)));
	}

	@Test
	void aMissedHordeStaysDue() {
		GuildWaveData data = new GuildWaveData();
		assertTrue(WaveSchedule.due(data, 5), "never scheduled: tonight");
		data.nextWaveDay = 7;
		assertFalse(WaveSchedule.due(data, 6));
		assertEquals(1, WaveSchedule.nightsUntil(data, 6));
		assertTrue(WaveSchedule.due(data, 7));
		assertTrue(WaveSchedule.due(data, 9), "nobody was home on day 7: still due");
		assertEquals(0, WaveSchedule.nightsUntil(data, 9));
	}

	@Test
	void intervalIsBounded() {
		WaveConfig config = Json5.fromString("{ \"timing\": { \"minDaysBetweenHordes\": 0, \"maxDaysBetweenHordes\": 99 } }", WaveConfig.class);
		config.validate();
		assertEquals(1, config.timing.minDaysBetweenHordes);
		assertEquals(30, config.timing.maxDaysBetweenHordes);
		WaveConfig inverted = Json5.fromString("{ \"timing\": { \"minDaysBetweenHordes\": 5, \"maxDaysBetweenHordes\": 2 } }", WaveConfig.class);
		inverted.validate();
		assertEquals(5, inverted.timing.maxDaysBetweenHordes);
	}
}
