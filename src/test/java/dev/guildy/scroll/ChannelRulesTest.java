package dev.guildy.scroll;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Channel of the incantations: the grimoire reads a share of each scroll's channel (a minimum, never longer than the
 * scroll), the runic circle is complete before the spell fires whatever the channel's length, and scrolls.json5 files
 * written with the old 0.4 s grimoire channel are migrated (admin values kept).
 */
class ChannelRulesTest {
	@Test
	void grimoireChannelFollowsTheScroll() {
		// Defaults: at least 1 s, 60 % of the scroll, never longer than the scroll.
		assertEquals(1.0, ChannelRules.grimoireSeconds(1.25, 1.0, 0.6), 1.0E-9, "a short scroll: the minimum");
		assertEquals(1.0, ChannelRules.grimoireSeconds(1.5, 1.0, 0.6), 1.0E-9);
		assertEquals(1.2, ChannelRules.grimoireSeconds(2.0, 1.0, 0.6), 1.0E-9, "a long scroll stays longer");
		assertEquals(2.4, ChannelRules.grimoireSeconds(4.0, 1.0, 0.6), 1.0E-9, "the recall keeps a real channel");
		assertEquals(0.75, ChannelRules.grimoireSeconds(0.75, 1.0, 0.6), 1.0E-9, "never slower than its scroll");
		assertEquals(1.0, ChannelRules.grimoireSeconds(1.0, 1.0, 0.6), 1.0E-9);
		// Factor 0: always the minimum (within the scroll); factor 1: as long as the scroll.
		assertEquals(1.0, ChannelRules.grimoireSeconds(3.0, 1.0, 0.0), 1.0E-9);
		assertEquals(3.0, ChannelRules.grimoireSeconds(3.0, 1.0, 1.0), 1.0E-9);
		// Broken values never give a zero or negative channel.
		assertEquals(1.0, ChannelRules.grimoireSeconds(Double.NaN, 1.0, 0.6), 1.0E-9, "unknown scroll channel: the minimum");
		assertTrue(ChannelRules.grimoireSeconds(-2, 0, 0.6) > 0);
	}

	@Test
	void ticksLikeTheVanillaConsumable() {
		assertEquals(20, ChannelRules.ticks(1.0));
		assertEquals(24, ChannelRules.ticks(1.2));
		assertEquals(15, ChannelRules.ticks(0.75));
		assertEquals(8, ChannelRules.ticks(0.4));
		assertEquals(1, ChannelRules.ticks(0.01), "at least one tick");
	}

	@Test
	void theCircleIsCompleteBeforeTheSpellFires() {
		for (int total = 1; total <= 200; total++) {
			// The vanilla use completes after `total` ticks: the last frame drawn is at elapsed = total - 1.
			assertEquals(1.0, ChannelRules.drawProgress(total - 1, total), 1.0E-9, "complete at the end of a " + total + "-tick channel");
			int draw = ChannelRules.drawTicks(total);
			assertTrue(draw >= 1 && draw <= total, "drawn within the channel: " + draw + " / " + total);
			assertEquals(1.0, ChannelRules.drawProgress(draw - 1, total), 1.0E-9, "complete after its drawing ticks");
			double previous = 0;
			for (int elapsed = 0; elapsed < total; elapsed++) {
				double p = ChannelRules.drawProgress(elapsed, total);
				assertTrue(p > 0 && p <= 1 && p >= previous, "progress grows: " + elapsed + " / " + total);
				previous = p;
			}
		}
		// The drawing speed follows the channel: half-way through the drawing of a long channel, half the circle.
		assertEquals(0.5, ChannelRules.drawProgress(ChannelRules.drawTicks(80) / 2 - 1, 80), 0.02);
		assertTrue(ChannelRules.drawProgress(9, 20) < 0.7, "a 1 s channel is not drawn in a flash");
		assertTrue(ChannelRules.drawTicks(20) <= 18, "a 1 s channel shows the complete circle for its last ticks");
		assertEquals(0.0, ChannelRules.drawProgress(-3, 20));
	}

	@Test
	void grimoireChannelInTheConfig() {
		ScrollConfig cfg = new ScrollConfig();
		assertTrue(cfg.rebuild(id -> true).isEmpty());
		ScrollConfig.Grimoire g = cfg.grimoire;
		assertEquals(1.0, g.channelSeconds);
		assertEquals(0.6, g.channelFactor);
		assertEquals(20, g.channelTicks(), "no readable page: the minimum");
		assertEquals(20, g.channelTicks(null));
		for (ScrollConfig.ScrollDef def : cfg.usable()) {
			double seconds = g.channelSeconds(def);
			assertTrue(seconds <= def.channelSeconds + 1.0E-9, def.id + ": never longer than the scroll");
			assertTrue(seconds >= Math.min(def.channelSeconds, 1.0) - 1.0E-9, def.id + ": long enough to see the circle form");
			assertTrue(g.channelTicks(def) >= 15, def.id + ": at least 0.75 s (" + g.channelTicks(def) + " ticks)");
		}
		assertEquals(24, g.channelTicks(cfg.scroll("singularite")), "2 s scroll -> 1.2 s");
		assertEquals(20, g.channelTicks(cfg.scroll("seisme")), "1.25 s scroll -> 1 s");
		assertEquals(15, g.channelTicks(cfg.scroll("revelation")), "0.75 s scroll -> 0.75 s");

		ScrollConfig bad = new ScrollConfig();
		bad.grimoire.channelFactor = 5;
		bad.rebuild(id -> true);
		assertEquals(1.0, bad.grimoire.channelFactor);
		bad.grimoire.channelFactor = -1;
		bad.rebuild(id -> true);
		assertEquals(0.0, bad.grimoire.channelFactor);
	}

	@Test
	void oldGrimoireChannelIsMigrated(@TempDir Path dir) throws Exception {
		// Format 1 with the old default: the grimoire gets its new channel.
		Path old = dir.resolve("old.json5");
		Files.writeString(old, "{ \"configVersion\": 1, \"grimoire\": { \"channelSeconds\": 0.4, \"cooldownSeconds\": 3 } }");
		ScrollConfig migrated = Json5.read(old, ScrollConfig.class);
		assertTrue(migrated.rebuild(id -> true).isEmpty());
		assertEquals(1.0, migrated.grimoire.channelSeconds, "0.4 s (old default) -> 1 s");
		assertEquals(0.6, migrated.grimoire.channelFactor, "the new key gets its default");
		assertEquals(3.0, migrated.grimoire.cooldownSeconds, "cooldown unchanged");
		assertEquals(ScrollConfig.CURRENT_VERSION, migrated.configVersion);

		// An admin's own value is kept.
		Path custom = dir.resolve("custom.json5");
		Files.writeString(custom, "{ \"configVersion\": 1, \"grimoire\": { \"channelSeconds\": 0.7 } }");
		ScrollConfig kept = Json5.read(custom, ScrollConfig.class);
		kept.rebuild(id -> true);
		assertEquals(0.7, kept.grimoire.channelSeconds);

		// Already migrated: 0.4 is now an admin choice.
		Path current = dir.resolve("current.json5");
		Files.writeString(current, "{ \"configVersion\": 2, \"grimoire\": { \"channelSeconds\": 0.4 } }");
		ScrollConfig chosen = Json5.read(current, ScrollConfig.class);
		chosen.rebuild(id -> true);
		assertEquals(0.4, chosen.grimoire.channelSeconds);

		// Round trip: the migrated file keeps its values and its version.
		Path written = dir.resolve("written.json5");
		Json5.write(written, migrated);
		ScrollConfig again = Json5.read(written, ScrollConfig.class);
		again.rebuild(id -> true);
		assertEquals(1.0, again.grimoire.channelSeconds);
		assertEquals(migrated.grimoire.fingerprint(), again.grimoire.fingerprint());
	}
}
