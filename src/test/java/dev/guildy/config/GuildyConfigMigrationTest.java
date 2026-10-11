package dev.guildy.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
