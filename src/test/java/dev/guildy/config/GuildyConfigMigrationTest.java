package dev.guildy.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildyConfigMigrationTest {

	@Test
	void oldUntouchedDefaultIsMigrated() {
		GuildyConfig config = Json5.fromString("{ \"territory\": { \"borderDisplaySeconds\": 15 } }", GuildyConfig.class);
		config.validate();
		assertEquals(45, config.territory.borderDisplaySeconds);
		assertEquals(GuildyConfig.CURRENT_VERSION, config.configVersion);
	}

	@Test
	void adminValueIsKept() {
		GuildyConfig config = Json5.fromString("{ \"territory\": { \"borderDisplaySeconds\": 20 } }", GuildyConfig.class);
		config.validate();
		assertEquals(20, config.territory.borderDisplaySeconds);
	}

	@Test
	void currentFilesAreNotMigratedAgain() {
		GuildyConfig config = Json5.fromString("{ \"configVersion\": 2, \"territory\": { \"borderDisplaySeconds\": 15 } }", GuildyConfig.class);
		config.validate();
		assertEquals(15, config.territory.borderDisplaySeconds);
	}

	@Test
	void oldLevelSectionGetsTheNewKeys() {
		// A version 2 file: the old 9 thresholds (edited) and no other level key.
		GuildyConfig config = Json5.fromString("{ \"configVersion\": 2, \"levels\": { \"xpThresholds\": [100, 250, 450, 700, 1000, 1400, 1900, 2500, 3000] } }",
				GuildyConfig.class);
		config.validate();
		assertEquals(GuildyConfig.CURRENT_VERSION, config.configVersion);
		assertEquals(100, config.maxLevel(), "levels up to 100 by default");
		assertEquals(3000, config.xpForLevel(10), "the edited thresholds are kept");
		assertEquals(3000 + 1340, config.xpForLevel(11), "the formula continues from the last listed level");
		assertTrue(config.levels.rewards.enabled);
		assertFalse(config.levels.ranks.isEmpty());
	}

	@Test
	void longEditedListsKeepAllTheirLevels() {
		StringBuilder list = new StringBuilder();
		for (int i = 1; i <= 149; i++) {
			list.append(i > 1 ? "," : "").append(i * 100);
		}
		GuildyConfig config = Json5.fromString("{ \"configVersion\": 2, \"levels\": { \"xpThresholds\": [" + list + "] } }", GuildyConfig.class);
		config.validate();
		assertEquals(150, config.maxLevel(), "a list of 149 thresholds still reaches level 150");
		assertEquals(14_900, config.xpForLevel(150));
		assertEquals(1_500, config.xpForLevel(16), "explicit thresholds past level 10 are kept");

		// Already migrated: the admin's maximum is respected.
		GuildyConfig current = Json5.fromString("{ \"configVersion\": 3, \"levels\": { \"maxLevel\": 40, \"xpThresholds\": [" + list + "] } }",
				GuildyConfig.class);
		current.validate();
		assertEquals(40, current.maxLevel());
	}

	@Test
	void maxLevelIsBounded() {
		GuildyConfig low = Json5.fromString("{ \"configVersion\": 3, \"levels\": { \"maxLevel\": 3 } }", GuildyConfig.class);
		low.validate();
		assertEquals(10, low.maxLevel());
		GuildyConfig high = Json5.fromString("{ \"configVersion\": 3, \"levels\": { \"maxLevel\": 9000, \"xpStepBase\": 0, \"xpStepGrowth\": -4 } }",
				GuildyConfig.class);
		high.validate();
		assertEquals(500, high.maxLevel());
		assertEquals(1, high.levels.xpStepBase);
		assertEquals(0, high.levels.xpStepGrowth);
		assertTrue(high.xpForLevel(500) > high.xpForLevel(499), "still increasing");
	}
}
