package dev.guildy.wave;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaveConfigMigrationTest {

	@Test
	void oldInsideSpawnDefaultMovesOutside() {
		WaveConfig config = Json5.fromString("{ \"spawning\": { \"edgeOffsetMin\": -3 } }", WaveConfig.class);
		config.validate();
		assertEquals(1, config.spawning.edgeOffsetMin);
		assertEquals(WaveConfig.CURRENT_VERSION, config.configVersion);
	}

	@Test
	void adminChoiceIsKept() {
		WaveConfig config = Json5.fromString("{ \"spawning\": { \"edgeOffsetMin\": -1 } }", WaveConfig.class);
		config.validate();
		assertEquals(-1, config.spawning.edgeOffsetMin);
	}
}
