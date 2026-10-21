package dev.guildy.scroll;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fléau and Chaînes spectrales: the extra damage of a cursed monster (bounded, never stacked, a share on bosses), how long the
 * chains hold, and the amplified values of the new spells (scroll, grimoire, cursed grimoire) within their bounds.
 */
class DebuffRulesTest {
	private static final double EPS = 1.0E-9;

	@Test
	void vulnerabilityIsBoundedAndSharedWithBosses() {
		assertEquals(25.0, DebuffRules.vulnerability(25, false, 50), EPS);
		assertEquals(12.5, DebuffRules.vulnerability(25, true, 50), EPS, "a boss gets half of it");
		assertEquals(0.0, DebuffRules.vulnerability(25, true, 0), EPS, "bossShare 0: bosses are immune");
		assertEquals(25.0, DebuffRules.vulnerability(25, true, 100), EPS);
		assertEquals(25.0, DebuffRules.vulnerability(25, true, 250), EPS, "the share never exceeds the full effect");
		assertEquals(DebuffRules.MAX_VULNERABILITY, DebuffRules.vulnerability(1000, false, 50), EPS, "never above the cap");
		assertEquals(0.0, DebuffRules.vulnerability(-10, false, 50), EPS);
		assertEquals(0.0, DebuffRules.vulnerability(Double.NaN, false, 50), EPS);
		assertEquals(0.0, DebuffRules.vulnerability(25, true, Double.NaN), EPS);
	}

	@Test
	void multiplierStaysBetweenOneAndTwo() {
		assertEquals(1.25, DebuffRules.multiplier(25), EPS);
		assertEquals(1.0, DebuffRules.multiplier(0), EPS);
		assertEquals(1.0, DebuffRules.multiplier(-50), EPS);
		assertEquals(2.0, DebuffRules.multiplier(500), EPS, "at most twice the damage");
		assertEquals(1.0, DebuffRules.multiplier(Double.NaN), EPS);
		assertEquals(1.0, DebuffRules.multiplier(Double.POSITIVE_INFINITY), EPS, "an infinite value is no value");
	}

	@Test
	void cursesNeverAddUp() {
		assertEquals(25.0, DebuffRules.stack(25, 12.5), EPS, "the strongest counts");
		assertEquals(35.0, DebuffRules.stack(25, 35), EPS);
		assertEquals(25.0, DebuffRules.stack(25, 25), EPS, "two equal curses: no more than one");
		assertEquals(DebuffRules.MAX_VULNERABILITY, DebuffRules.stack(90, 400), EPS);
		assertEquals(0.0, DebuffRules.stack(Double.NaN, -3), EPS);
	}

	@Test
	void chainsHoldBossesForTheirShare() {
		assertEquals(100, DebuffRules.holdTicks(100, false, 50));
		assertEquals(50, DebuffRules.holdTicks(100, true, 50), "a boss is held half the time");
		assertEquals(1, DebuffRules.holdTicks(100, true, 0), "never less than a tick");
		assertEquals(100, DebuffRules.holdTicks(100, true, 400), "never longer than the full duration");
		assertEquals(1, DebuffRules.holdTicks(0, false, 50));
		assertEquals(1, DebuffRules.holdTicks(-20, true, 50));
	}

	@Test
	void newSpellsAreAmplifiedWithinBounds() {
		ScrollConfig cfg = new ScrollConfig();
		assertTrue(cfg.rebuild(id -> true).isEmpty());
		// Fléau: +25 % from a scroll or a grimoire (percentages ignore the grimoire's power), +35 % from the cursed grimoire.
		Spell.Param vulnerability = Spell.SCOURGE.param("vulnerability");
		assertEquals(25.0, vulnerability.defaultValue(), EPS);
		assertEquals(25.0, SpellPower.of(cfg.grimoire.powerMultiplier).apply(vulnerability, 25), EPS);
		assertEquals(25.0, SpellPower.of(3.0).apply(vulnerability, 25), EPS, "a grimoire's power never changes a percentage");
		assertEquals(35.0, cfg.cursedGrimoire.power(Spell.SCOURGE).apply(vulnerability, 25), EPS);
		assertEquals(17.5, cfg.cursedGrimoire.power(Spell.SCOURGE).apply(Spell.SCOURGE.param("duration"), 10), EPS);
		assertEquals(2, cfg.cursedGrimoire.power(Spell.SCOURGE).apply(Spell.SCOURGE.param("weaknessLevel"), 1), EPS);
		SpellPower huge = new SpellPower(5, 3, 5, 3, Map.of("vulnerability", 5.0, "bossShare", 5.0, "crossDimension", 5.0), true);
		assertEquals(DebuffRules.MAX_VULNERABILITY, huge.apply(vulnerability, 60), EPS, "never above +100 %");
		assertEquals(100.0, huge.apply(Spell.SCOURGE.param("bossShare"), 50), EPS, "a share never above 100 %");
		assertEquals(3, huge.apply(Spell.SCOURGE.param("weaknessLevel"), 1), EPS, "Faiblesse III at most");
		assertEquals(5, huge.apply(Spell.SCOURGE.param("slowLevel"), 2), EPS);
		// Chaînes spectrales: 5 monsters, held 65 % of the time when they are bosses.
		assertEquals(5, cfg.cursedGrimoire.power(Spell.SPECTRAL_CHAINS).apply(Spell.SPECTRAL_CHAINS.param("count"), 3), EPS, "4.5 -> 5");
		assertEquals(65.0, cfg.cursedGrimoire.power(Spell.SPECTRAL_CHAINS).apply(Spell.SPECTRAL_CHAINS.param("bossShare"), 50), EPS);
		assertEquals(8, huge.apply(Spell.SPECTRAL_CHAINS.param("count"), 3), EPS, "8 chained monsters at most");
		assertEquals(75.0, huge.apply(Spell.SPECTRAL_CHAINS.param("duration"), 15), EPS, "durations: 5x the maximum at most");
		// Ralliement: 12 members, a longer call; the cross-dimension flag never changes.
		SpellPower rally = cfg.cursedGrimoire.power(Spell.RALLY);
		assertEquals(12, rally.apply(Spell.RALLY.param("maxAllies"), 8), EPS);
		assertEquals(52.5, rally.apply(Spell.RALLY.param("acceptSeconds"), 30), EPS);
		assertEquals(0, huge.apply(Spell.RALLY.param("crossDimension"), 0), EPS, "a flag is never turned on");
		assertEquals(20, huge.apply(Spell.RALLY.param("maxAllies"), 8), EPS, "20 members at most");
		assertEquals(6144.0, huge.apply(Spell.RALLY.param("range"), 4096), EPS, "range: 1.5x the maximum at most");
		// Permutation, Bannière de guerre, Hâte du vent.
		assertEquals(43.2, cfg.cursedGrimoire.power(Spell.SWAP).apply(Spell.SWAP.param("range"), 32), EPS);
		assertEquals(96.0, huge.apply(Spell.SWAP.param("range"), 64), EPS);
		assertEquals(3, cfg.cursedGrimoire.power(Spell.WAR_BANNER).apply(Spell.WAR_BANNER.param("strengthLevel"), 2), EPS);
		assertEquals(4, huge.apply(Spell.WAR_BANNER.param("strengthLevel"), 2), EPS, "Force IV at most");
		assertEquals(4, huge.apply(Spell.TAILWIND.param("speedLevel"), 2), EPS, "Vitesse IV at most");
		assertEquals(1, huge.apply(Spell.TAILWIND.param("noFall"), 1), EPS);
	}

	@Test
	void percentagesAreShownAsSuch() {
		assertEquals("25 %", Spell.SCOURGE.param("vulnerability").format(25));
		assertEquals("50 %", Spell.SPECTRAL_CHAINS.param("bossShare").format(50));
		assertTrue(Spell.SCOURGE.param("vulnerability").integer(), "whole percentages");
		assertEquals(30.0, Spell.SCOURGE.param("vulnerability").clamp(29.6), EPS);
		assertEquals(DebuffRules.MAX_VULNERABILITY, Spell.SCOURGE.param("vulnerability").clamp(900), EPS);
	}
}
