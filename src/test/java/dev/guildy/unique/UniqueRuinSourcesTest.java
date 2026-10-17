package dev.guildy.unique;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** uniques.json5 format 2: drop tables of the ruin bosses, added once to older files. */
class UniqueRuinSourcesTest {

	@Test
	void ruinBossesHaveTheirOwnTables() {
		UniqueConfig cfg = new UniqueConfig();
		assertTrue(cfg.rebuild(id -> true).isEmpty());
		assertEquals(2, cfg.configVersion);
		for (String source : UniqueConfig.RUIN_SOURCES) {
			assertTrue(UniqueConfig.SOURCES.contains(source), source + " is a known source");
			assertNotNull(cfg.table(source), source + " table");
		}
		assertTrue(cfg.table("ruin_boss").chance > cfg.table("boss").chance, "ruin bosses drop more often than horde bosses");
		assertTrue(cfg.table("nether_ruin_boss").chance > cfg.table("ruin_boss").chance, "Nether ruins are richer");
	}

	@Test
	void olderFilesReceiveTheRuinTablesOnce() {
		UniqueConfig old = Json5.fromString("{ \"configVersion\": 1, \"drops\": { \"boss\": { \"chance\": 0.5, \"items\": { \"foudrelame\": 1 } } } }",
				UniqueConfig.class);
		old.rebuild(id -> true);
		assertEquals(2, old.configVersion);
		assertEquals(0.5, old.table("boss").chance, "existing tables untouched");
		assertNotNull(old.table("ruin_boss"), "ruin table added");
		assertNotNull(old.table("nether_ruin_boss"), "Nether ruin table added");

		UniqueConfig custom = Json5.fromString("{ \"configVersion\": 1, \"drops\": { \"ruin_boss\": { \"chance\": 0.9, \"items\": { \"foudrelame\": 1 } } } }",
				UniqueConfig.class);
		custom.rebuild(id -> true);
		assertEquals(0.9, custom.table("ruin_boss").chance, "an admin's own ruin table is kept");

		UniqueConfig current = Json5.fromString("{ \"configVersion\": 2, \"drops\": { \"boss\": { \"chance\": 0.1, \"items\": { \"foudrelame\": 1 } } } }",
				UniqueConfig.class);
		current.rebuild(id -> true);
		assertFalse(current.drops.containsKey("ruin_boss"), "removed after the migration: never added back");
	}
}
