package dev.guildy.wave;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HordeTargetsConfigTest {

	@Test
	void hordesLeaveTheVillagersAloneByDefault() {
		assertFalse(new WaveConfig().mobs.targetVillagers);
		WaveConfig old = Json5.fromString("{ \"configVersion\": 2, \"mobs\": { \"followRange\": 40 } }", WaveConfig.class);
		old.validate();
		assertFalse(old.mobs.targetVillagers, "files written before the setting: villagers spared");
	}

	@Test
	void vanillaTargetingCanBeRestored() {
		WaveConfig cfg = Json5.fromString("{ \"mobs\": { \"targetVillagers\": true } }", WaveConfig.class);
		cfg.validate();
		assertTrue(cfg.mobs.targetVillagers);
	}
}
