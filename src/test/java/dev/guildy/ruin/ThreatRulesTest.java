package dev.guildy.ruin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Threat levels of the ruins (scaling, cap, rewards) and the resurrection of guardians killed too fast. */
class ThreatRulesTest {
	private static final double EPS = 1.0E-9;

	@Test
	void levelsStartAtOneAndClimbToTheCap() {
		assertEquals(1, ThreatRules.stored(0, 0), "new ruin: Menace I");
		assertEquals(4, ThreatRules.stored(0, 3), "older save: 1 + its past victories");
		assertEquals(2, ThreatRules.stored(2, 9), "a stored level wins over the victories");
		assertEquals(1, ThreatRules.level(0, 0, 10, true));
		assertEquals(10, ThreatRules.level(25, 0, 10, true), "capped by maxLevel");
		assertEquals(1, ThreatRules.level(7, 6, 10, false), "escalation disabled: always I");
		assertEquals(ThreatRules.MAX_LEVEL, ThreatRules.level(500, 0, 9999, true), "hard cap");
		assertEquals(2, ThreatRules.next(1, 10));
		assertEquals(10, ThreatRules.next(10, 10), "never above the cap");
		assertEquals(5, ThreatRules.next(12, 5), "a lowered cap applies at once");
		assertEquals(1, ThreatRules.next(0, 0));
	}

	@Test
	void statisticsScaleWithTheLevel() {
		RuinConfig.EscalationSettings e = new RuinConfig.EscalationSettings();
		assertSame(ThreatRules.Scaling.NONE, ThreatRules.scaling(e, 1), "level I = the configured guardian");
		ThreatRules.Scaling three = ThreatRules.scaling(e, 3);
		assertEquals(3, three.level());
		assertEquals(1.4, three.health(), EPS, "+20 % health per level");
		assertEquals(1.2, three.damage(), EPS, "+10 % damage per level");
		assertEquals(2.0, three.armor(), EPS, "+1 armor point per level");
		assertEquals(1, three.extraMinions(), "+1 minion every 2 levels");
		assertEquals(0.9, three.cooldownFactor(), EPS, "-5 % cooldowns per level");
		ThreatRules.Scaling ten = ThreatRules.scaling(e, 10);
		assertTrue(ten.health() > three.health() && ten.damage() > three.damage() && ten.armor() > three.armor());
		assertEquals(4, ten.extraMinions());
		assertEquals(e.minCooldownFactor, ten.cooldownFactor(), EPS, "cooldowns never below the floor");
		e.enabled = false;
		assertSame(ThreatRules.Scaling.NONE, ThreatRules.scaling(e, 8), "disabled: no scaling");

		assertEquals(0, ThreatRules.extraMinions(9, 0), "0 = never");
		assertEquals(0, ThreatRules.extraMinions(2, 2));
		assertEquals(1, ThreatRules.extraMinions(3, 2));
		assertEquals(1.0, ThreatRules.cooldownFactor(0.05, 1, 0.5), EPS);
		assertEquals(0.5, ThreatRules.cooldownFactor(0.2, 9, 0.5), EPS);
	}

	@Test
	void enrageComesEarlierButStaysBounded() {
		assertEquals(0.3, ThreatRules.enrageThreshold(0.3, 0.02, 1, 0.5), EPS);
		assertEquals(0.36, ThreatRules.enrageThreshold(0.3, 0.02, 4, 0.5), EPS);
		assertEquals(0.5, ThreatRules.enrageThreshold(0.3, 0.02, 40, 0.5), EPS, "capped");
		assertEquals(0.8, ThreatRules.enrageThreshold(0.8, 0.02, 5, 0.5), EPS, "a configured threshold above the cap is kept");
	}

	@Test
	void rewardsGrowWithTheLevelAndTheRisenForm() {
		RuinConfig.EscalationSettings e = new RuinConfig.EscalationSettings();
		RuinConfig.ResurrectionSettings r = new RuinConfig.ResurrectionSettings();
		ThreatRules.Rewards base = ThreatRules.rewards(e, r, 1, false);
		assertEquals(1.0, base.gold(), EPS);
		assertEquals(1.0, base.xp(), EPS);
		assertEquals(0, base.extraLootRolls());
		assertEquals(0.0, base.extraUniqueChance(), EPS, "no extra roll at Menace I");
		assertEquals(0, base.bonusUniqueRolls());

		ThreatRules.Rewards five = ThreatRules.rewards(e, r, 5, false);
		assertEquals(1.6, five.gold(), EPS, "+15 % gold per level");
		assertEquals(1.6, five.xp(), EPS);
		assertEquals(0.2, five.extraUniqueChance(), EPS, "5 % per level");
		assertEquals(0.32, five.extraScrollChance(), EPS);
		assertEquals(1.0, ThreatRules.extraRollChance(0.5, 9), EPS, "never above 100 %");

		ThreatRules.Rewards risen = ThreatRules.rewards(e, r, 5, true);
		assertEquals(1.6 * 1.5, risen.gold(), EPS, "risen form: +50 % gold on top");
		assertEquals(1.6 * 1.5, risen.xp(), EPS);
		assertEquals(r.bonusLootRolls, risen.extraLootRolls());
		assertEquals(r.bonusUniqueRolls, risen.bonusUniqueRolls());
		assertEquals(r.bonusScrollRolls, risen.bonusScrollRolls());
		assertTrue(ThreatRules.rewards(e, r, 1, true).gold() > base.gold(), "a risen guardian pays more even at Menace I");

		assertEquals(16, ThreatRules.scaled(10, 1.6));
		assertEquals(0, ThreatRules.scaled(-4, 2));
		assertEquals(3, ThreatRules.scaled(2, 1.5), "rounded");
	}

	@Test
	void theFastKillWindow() {
		assertEquals(45, ThreatRules.fastKillSeconds(-1, 45, 15, 5, 1, 1), EPS, "tier 1, Menace I: the base window");
		assertEquals(45 + 30 + 10, ThreatRules.fastKillSeconds(-1, 45, 15, 5, 3, 3), EPS, "+15 s per tier, +5 s per level");
		assertEquals(20, ThreatRules.fastKillSeconds(20, 45, 15, 5, 5, 9), EPS, "the ruin's own window");
		assertEquals(0, ThreatRules.fastKillSeconds(0, 45, 15, 5, 5, 9), EPS, "0 = this guardian never rises");
		long window = ThreatRules.windowTicks(45);
		assertEquals(900, window);
		assertTrue(ThreatRules.resurrects(true, false, 0, window));
		assertTrue(ThreatRules.resurrects(true, false, 899, window), "killed just inside the window");
		assertFalse(ThreatRules.resurrects(true, false, 900, window), "window over: it dies");
		assertFalse(ThreatRules.resurrects(true, true, 10, window), "it rises only once per fight");
		assertFalse(ThreatRules.resurrects(false, false, 10, window), "disabled");
		assertFalse(ThreatRules.resurrects(true, false, 10, 0), "no window");

		assertEquals(45, ThreatRules.secondsLeft(0, window));
		assertEquals(32, ThreatRules.secondsLeft(900 - 640, window));
		assertEquals(1, ThreatRules.secondsLeft(899, window), "rounded up");
		assertEquals(0, ThreatRules.secondsLeft(900, window));
		assertEquals(0, ThreatRules.secondsLeft(5000, window));
		assertEquals("0:32", ThreatRules.clock(32));
		assertEquals("1:05", ThreatRules.clock(65));
		assertEquals("0:00", ThreatRules.clock(-3));
	}

	@Test
	void theBodySinksThenLifts() {
		assertEquals(0, ThreatRules.bodyOffset(0, 0.8, 1.5), EPS);
		assertEquals(-0.8, ThreatRules.bodyOffset(ThreatRules.SINK_END, 0.8, 1.5), EPS, "sunk");
		assertTrue(ThreatRules.bodyOffset(ThreatRules.SINK_END / 2, 0.8, 1.5) < 0, "sinking");
		assertEquals(1.5, ThreatRules.bodyOffset(ThreatRules.LIFT_END, 0.8, 1.5), EPS, "lifted");
		assertEquals(1.5, ThreatRules.bodyOffset(1, 0.8, 1.5), EPS, "floats until it lands");
		double previous = -1;
		for (int i = 20; i <= 80; i += 5) {
			double y = ThreatRules.bodyOffset(i / 100.0, 0.8, 1.5);
			assertTrue(y >= previous, "lifts steadily at " + i + " %");
			previous = y;
		}
		assertEquals(1.0F, ThreatRules.risingHealth(0, 300, 1), 1.0E-4, "1 health while it sinks");
		assertEquals(300F, ThreatRules.risingHealth(1, 300, 1), 1.0E-3, "full health once back");
		assertEquals(150F, ThreatRules.risingHealth(1, 300, 0.5), 1.0E-3, "a configured part of its health");
		assertTrue(ThreatRules.risingHealth(0.6, 300, 1) > ThreatRules.risingHealth(0.4, 300, 1), "the bar fills up");
	}

	@Test
	void labels() {
		assertEquals("I", ThreatRules.roman(1));
		assertEquals("IV", ThreatRules.roman(4));
		assertEquals("IX", ThreatRules.roman(9));
		assertEquals("X", ThreatRules.roman(10));
		assertEquals("XLIX", ThreatRules.roman(49));
		assertEquals("I", ThreatRules.roman(0));
		assertEquals("&a", ThreatRules.color(1, 10));
		assertEquals("&4&l", ThreatRules.color(10, 10));
		assertFalse(ThreatRules.color(5, 10).equals(ThreatRules.color(1, 10)));
	}

	@Test
	void abilityDamageIsScaledOnACopy() {
		RuinConfig.AbilityDef ability = new RuinConfig.AbilityDef();
		ability.type = "fire_ring";
		ability.damage = 6;
		ability.style = "jade";
		assertSame(ability, ability.scaled(1.0));
		RuinConfig.AbilityDef scaled = ability.scaled(1.5);
		assertEquals(9, scaled.damage, EPS);
		assertEquals(6, ability.damage, EPS, "the configured ability is untouched");
		assertEquals("jade", scaled.style);
		assertEquals(ability.cooldownSeconds, scaled.cooldownSeconds, EPS);
	}
}
