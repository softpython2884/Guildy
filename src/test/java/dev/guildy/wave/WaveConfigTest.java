package dev.guildy.wave;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveConfigTest {

	@Test
	void writeThenReadKeepsEverything(@TempDir Path dir) throws Exception {
		WaveConfig original = new WaveConfig();
		original.validate();
		Path file = dir.resolve("waves.json5");
		Json5.write(file, original);
		String text = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(text.contains("// Guildy - hordes nocturnes"), "header comment");
		assertTrue(text.contains("Créatures du Nether"), "accents kept");

		WaveConfig loaded = Json5.read(file, WaveConfig.class);
		loaded.validate();
		assertEquals(original.themes.size(), loaded.themes.size());
		for (int i = 0; i < original.themes.size(); i++) {
			WaveConfig.Theme a = original.themes.get(i);
			WaveConfig.Theme b = loaded.themes.get(i);
			assertEquals(a.id, b.id);
			assertEquals(a.name, b.name);
			assertEquals(a.mobs.size(), b.mobs.size());
			assertEquals(a.bosses.size(), b.bosses.size());
			assertEquals(a.forceBoss, b.forceBoss);
		}
		assertEquals(original.timing.nightStart, loaded.timing.nightStart);
		assertEquals(original.rewards.items.size(), loaded.rewards.items.size());
		assertEquals(original.mobs.armorTiers.size(), loaded.mobs.armorTiers.size());
	}

	@Test
	void missingKeysKeepDefaults() {
		WaveConfig loaded = Json5.fromString("{ // partial file\n enabled: false, timing: { nightStart: 14000 } }", WaveConfig.class);
		loaded.validate();
		assertFalse(loaded.enabled);
		assertEquals(14000, loaded.timing.nightStart);
		assertEquals(23000, loaded.timing.nightEnd);
		assertEquals(new WaveConfig().themes.size(), loaded.themes.size());
	}

	@Test
	void validateClampsNonsense() {
		WaveConfig cfg = new WaveConfig();
		cfg.timing.nightStart = 20000;
		cfg.timing.nightEnd = 1000;
		cfg.timing.lateStartUntil = 50;
		cfg.difficulty.max = -5;
		cfg.difficulty.eliteMax = 3;
		cfg.spawning.groupSizeMin = 10;
		cfg.spawning.groupSizeMax = 2;
		cfg.defeat.treasuryLossPercent = 400;
		cfg.themes = new ArrayList<>();
		cfg.rewards.items = null;
		cfg.validate();
		assertTrue(cfg.timing.nightEnd > cfg.timing.nightStart);
		assertTrue(cfg.timing.lateStartUntil > cfg.timing.nightStart && cfg.timing.lateStartUntil <= cfg.timing.nightEnd);
		assertEquals(cfg.difficulty.base, cfg.difficulty.max);
		assertEquals(1.0, cfg.difficulty.eliteMax);
		assertTrue(cfg.spawning.groupSizeMax >= cfg.spawning.groupSizeMin);
		assertEquals(100, cfg.defeat.treasuryLossPercent);
		assertFalse(cfg.themes.isEmpty(), "empty theme list falls back to the defaults");
		assertTrue(cfg.rewards.items.isEmpty());
	}

	@Test
	void invalidThemeEntriesAreCleaned() {
		WaveConfig cfg = new WaveConfig();
		WaveConfig.Theme theme = WaveConfig.Theme.of("", null, -3, -1, null);
		theme.mobs.add(null);
		theme.mobs.add(WaveConfig.MobEntry.of("  MINECRAFT:Zombie ", -1, -2, -4));
		theme.mobs.add(WaveConfig.MobEntry.of(" ", 1, 0, 0));
		theme.bosses = null;
		cfg.themes = new ArrayList<>();
		cfg.themes.add(theme);
		cfg.validate();
		WaveConfig.Theme cleaned = cfg.themes.get(0);
		assertFalse(cleaned.id.isBlank());
		assertEquals(cleaned.id, cleaned.name);
		assertEquals(0, cleaned.weight);
		assertEquals(0, cleaned.minNight);
		assertEquals("RED", cleaned.barColor);
		assertEquals(1, cleaned.mobs.size());
		assertEquals("minecraft:zombie", cleaned.mobs.get(0).entity);
		assertEquals(0, cleaned.mobs.get(0).weight);
		assertEquals(0, cleaned.mobs.get(0).maxCount);
		assertTrue(cleaned.bosses.isEmpty());
	}
}
