package dev.guildy.wave;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveTruceTest {

	private static WaveConfig.Truce truce() {
		return new WaveConfig.Truce(); // 2 defeats in a row -> 3 nights
	}

	@Test
	void twoDefeatsInARowStartATruce() {
		GuildWaveData data = new GuildWaveData();
		assertFalse(WaveTruce.onDefeat(truce(), data));
		assertTrue(WaveTruce.onDefeat(truce(), data));
		assertEquals(3, data.truceNights);
		assertEquals(0, data.defeatsInRow);
	}

	@Test
	void aVictoryResetsTheDefeatCount() {
		GuildWaveData data = new GuildWaveData();
		WaveTruce.onDefeat(truce(), data);
		WaveTruce.onVictory(data);
		assertFalse(WaveTruce.onDefeat(truce(), data));
		assertEquals(0, data.truceNights);
	}

	@Test
	void eachNightCountsOnceAndTheLastNightStaysSpared() {
		GuildWaveData data = new GuildWaveData();
		data.truceNights = 3;
		WaveConfig.Truce cfg = truce();
		for (long day = 10; day < 13; day++) {
			assertEquals(WaveTruce.Night.SPARED_NOW, WaveTruce.spareTonight(cfg, data, day));
			// later start attempts of the same night (every few seconds) stay spared without counting again
			assertEquals(WaveTruce.Night.SPARED, WaveTruce.spareTonight(cfg, data, day));
			assertEquals(WaveTruce.Night.SPARED, WaveTruce.spareTonight(cfg, data, day));
			assertTrue(WaveTruce.active(cfg, data, day));
		}
		assertEquals(0, data.truceNights);
		assertEquals(WaveTruce.Night.NONE, WaveTruce.spareTonight(cfg, data, 13));
		assertFalse(WaveTruce.active(cfg, data, 13));
	}

	@Test
	void nightsLeftIncludeTheNightBeingSpared() {
		GuildWaveData data = new GuildWaveData();
		data.truceNights = 2;
		assertEquals(2, WaveTruce.nightsLeft(data, 5));
		WaveTruce.spareTonight(truce(), data, 5);
		assertEquals(2, WaveTruce.nightsLeft(data, 5));
		assertEquals(1, WaveTruce.nightsLeft(data, 6));
	}

	@Test
	void disabledTruceNeverSparesNorStarts() {
		WaveConfig.Truce cfg = truce();
		cfg.enabled = false;
		GuildWaveData data = new GuildWaveData();
		assertFalse(WaveTruce.onDefeat(cfg, data));
		assertFalse(WaveTruce.onDefeat(cfg, data));
		data.truceNights = 2;
		assertEquals(WaveTruce.Night.NONE, WaveTruce.spareTonight(cfg, data, 1));
		assertFalse(WaveTruce.active(cfg, data, 1));
	}

	@Test
	void configIsBoundedAndMissingSectionGetsDefaults() {
		WaveConfig config = Json5.fromString("{ \"truce\": { \"defeatsInARow\": 0, \"nights\": 99 } }", WaveConfig.class);
		config.validate();
		assertEquals(1, config.truce.defeatsInARow);
		assertEquals(30, config.truce.nights);
		WaveConfig empty = Json5.fromString("{ \"truce\": null }", WaveConfig.class);
		empty.validate();
		assertEquals(2, empty.truce.defeatsInARow);
		assertEquals(3, empty.truce.nights);
	}
}
