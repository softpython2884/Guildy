package dev.guildy.ruin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dread form of the guardians (Menace VII and more): tier, multipliers, effective health, damage, rewards, geometry. */
class DreadRulesTest {
	private static final double EPS = 1.0E-9;

	@Test
	void theDreadFormStartsAtThreatSeven() {
		RuinConfig.EscalationSettings e = new RuinConfig.EscalationSettings();
		assertEquals(7, e.dreadThreshold);
		assertSame(DreadRules.Stats.NONE, DreadRules.stats(e, 1));
		assertSame(DreadRules.Stats.NONE, DreadRules.stats(e, 6), "Menace VI: nothing of it");
		DreadRules.Stats seven = DreadRules.stats(e, 7);
		assertTrue(seven.active());
		assertEquals(0, seven.steps());
		assertEquals(2.5, seven.health(), EPS, "x2.5 health at VII");
		assertEquals(1.5, seven.damage(), EPS, "+50 % damage at VII");
		assertEquals(0.8, seven.cooldown(), EPS, "faster rhythm");
		DreadRules.Stats ten = DreadRules.stats(e, 10);
		assertEquals(3, ten.steps());
		assertEquals(4.0, ten.health(), EPS, "x4 at X");
		assertEquals(2.25, ten.damage(), EPS, "+125 % damage at X");
		assertEquals(0.65, ten.cooldown(), EPS);
		assertTrue(ten.gold() > seven.gold() && ten.xp() > seven.xp(), "more gold and XP higher");
		assertEquals(1, ten.extraLootRolls());

		DreadRules.Stats fifty = DreadRules.stats(e, 50);
		assertEquals(e.dread.maxHealthMultiplier, fifty.health(), EPS, "health multiplier capped");
		assertEquals(e.dread.maxDamageMultiplier, fifty.damage(), EPS, "damage multiplier capped");
		assertEquals(e.dread.minCooldownFactor, fifty.cooldown(), EPS, "cooldowns never below the floor");

		e.dreadThreshold = 0;
		assertSame(DreadRules.Stats.NONE, DreadRules.stats(e, 10), "0 = never");
		e.dreadThreshold = 7;
		e.dread.enabled = false;
		assertSame(DreadRules.Stats.NONE, DreadRules.stats(e, 10), "dread disabled");
		e.dread.enabled = true;
		e.enabled = false;
		assertSame(DreadRules.Stats.NONE, DreadRules.stats(e, 10), "escalation disabled: always Menace I");
		assertFalse(DreadRules.active(true, true, 7, 6));
		assertTrue(DreadRules.active(true, true, 3, 3));
	}

	@Test
	void multipliersAndCooldowns() {
		assertEquals(2.5, DreadRules.multiplier(2.5, 0.5, 8, 0), EPS);
		assertEquals(3.5, DreadRules.multiplier(2.5, 0.5, 8, 2), EPS);
		assertEquals(8, DreadRules.multiplier(2.5, 0.5, 8, 40), EPS);
		assertEquals(1, DreadRules.multiplier(0.2, 0, 8, 3), EPS, "never under 1");
		assertEquals(0.8, DreadRules.cooldown(0.8, 0.05, 0.5, 0), EPS);
		assertEquals(0.5, DreadRules.cooldown(0.8, 0.05, 0.5, 30), EPS);
		assertEquals(0, DreadRules.steps(5, 7));
		assertEquals(3, DreadRules.steps(10, 7));
	}

	@Test
	void intendedHealthBeyondTheVanillaCap() {
		// The cursed Archimage at Menace X, alone: 260 x 2.8 x 3 = 2184 intended (the game would keep 1024).
		assertEquals(2184, DreadRules.intendedHealth(260, 2.8, 1.0, 3.0, 1.0), 1.0E-6);
		assertEquals(1.0, DreadRules.vitality(800, 800), EPS, "under the cap: no factor");
		assertEquals(1.0, DreadRules.vitality(1024.00001, 1024), EPS, "a rounding difference is no factor");
		assertEquals(2184 / 1024.0, DreadRules.vitality(2184, 1024), EPS);
		// 3000 intended health: 3000 / 1024 = 2.9296875 (exact), every hit divided by it.
		double v = DreadRules.vitality(3000, 1024);
		assertEquals(2.9296875, v, 0.0);
		assertEquals(1000.0F, DreadRules.toGuardian(2929.6875F, v), 0.0F, "2929.6875 raw = 1000 real");
		assertEquals(24.0F, DreadRules.toGuardian(70.3125F, v), 0.0F, "the last 70.3125 raw = the last 24 real: 3000 in all");
		assertEquals(5.0F, DreadRules.toGuardian(5.0F, 1.0), 0.0F, "no factor, no change");
		assertEquals(3000, DreadRules.shown(1024, v));
		assertEquals(0, DreadRules.shown(0, v));
		assertEquals(30, DreadRules.shown(10.24, v), "rounded up");
	}

	@Test
	void effectiveHealthCapsPerPlayer() {
		assertEquals(5000, DreadRules.effectiveHealth(5000, 1, 8000), EPS);
		assertEquals(8000, DreadRules.effectiveHealth(9576, 1, 8000), EPS, "solo cap");
		assertEquals(21280, DreadRules.effectiveHealth(21280, 4, 8000), EPS, "a group of 4: up to 32000");
		assertEquals(32000, DreadRules.effectiveHealth(63840, 4, 8000), EPS);
		assertEquals(63840, DreadRules.effectiveHealth(63840, 4, 0), EPS, "0 = no cap");
		assertEquals(10000, DreadRules.clampHealth(1320, 1, 10000, 16000), EPS, "a floor per player");
		assertEquals(64000, DreadRules.clampHealth(127680, 4, 10000, 16000), EPS);
	}

	@Test
	void moreDamageOnThePlayersButNoOneShotFromTheBonus() {
		assertEquals(6.0F, DreadRules.toPlayer(4, 1.5, 0.8, 20), 1.0E-6F, "+50 % after armour");
		assertEquals(16.0F, DreadRules.toPlayer(10, 2.25, 0.8, 20), 1.0E-6F, "the bonus stops at 80 % of 20 health");
		assertEquals(18.0F, DreadRules.toPlayer(18, 2.25, 0.8, 20), 1.0E-6F, "a hit already stronger stays as it is");
		assertEquals(22.5F, DreadRules.toPlayer(10, 2.25, 0, 20), 1.0E-6F, "0 = no limit");
		assertEquals(10.0F, DreadRules.toPlayer(10, 1.0, 0.8, 20), 0.0F);
		assertEquals(0.0F, DreadRules.toPlayer(0, 2, 0.8, 20), 0.0F);
	}

	@Test
	void rewardsGrowButStayRolls() {
		RuinConfig.EscalationSettings e = new RuinConfig.EscalationSettings();
		ThreatRules.Rewards base = ThreatRules.rewards(e, new RuinConfig.ResurrectionSettings(), 7, false);
		ThreatRules.Rewards dread = DreadRules.rewards(base, DreadRules.stats(e, 7));
		assertEquals(base.gold() * 1.5, dread.gold(), EPS);
		assertEquals(base.xp() * 1.5, dread.xp(), EPS);
		assertEquals(base.extraLootRolls() + 1, dread.extraLootRolls());
		assertTrue(dread.extraScrollChance() > base.extraScrollChance() && dread.extraScrollChance() <= 1);
		assertSame(base, DreadRules.rewards(base, DreadRules.Stats.NONE), "nothing below VII");
	}

	@Test
	void abilitiesInRotationByPhase() {
		long[] ready = {100, 0, 0};
		int[] phases = {1, 1, 3};
		assertEquals(1, DreadRules.pick(50, ready, phases, 1, 0), "the first ready one in rotation");
		assertEquals(1, DreadRules.pick(50, ready, phases, 1, 2), "phase 3 locked in phase 1: skipped");
		assertEquals(2, DreadRules.pick(50, ready, phases, 3, 2), "unlocked in phase 3");
		assertEquals(0, DreadRules.pick(100, ready, phases, 1, 0));
		assertEquals(-1, DreadRules.pick(10, new long[] {20, 30}, new int[] {1, 1}, 1, 0), "none ready");
		assertEquals(DreadRules.MIN_TELEGRAPH_TICKS, DreadRules.telegraphTicks(0.1), "always a fair dodge window");
		assertEquals(25, DreadRules.telegraphTicks(1.25));
		assertEquals(20, DreadRules.cooldownTicks(0.2, 1), "a second at least");
		assertEquals(160, DreadRules.cooldownTicks(10, 0.8));
	}

	@Test
	void geometryOfTheTelegraphedAttacks() {
		assertEquals(1.0, DreadRules.distanceToSegment(5, 1, 0, 0, 10, 0), EPS);
		assertEquals(5.0, DreadRules.distanceToSegment(13, 4, 0, 0, 10, 0), EPS, "beyond the end: to the end");
		assertEquals(0.5, DreadRules.along(5, 1, 0, 0, 10, 0), EPS);
		assertEquals(1.0, DreadRules.distanceToSegment3(5, 1, 0, 0, 0, 0, 10, 0, 0), EPS);
		assertTrue(DreadRules.inCone(5, 1, 1, 0, Math.toRadians(40), 10), "in front");
		assertFalse(DreadRules.inCone(-5, 0, 1, 0, Math.toRadians(40), 10), "behind");
		assertFalse(DreadRules.inCone(3, 5, 1, 0, Math.toRadians(40), 10), "to the side");
		assertFalse(DreadRules.inCone(11, 0, 1, 0, Math.toRadians(40), 10), "too far");
		assertTrue(DreadRules.inCone(0.3, -0.3, 1, 0, Math.toRadians(40), 10), "right under its jaws");
		double[] gaps = DreadRules.gaps(3, 0);
		assertEquals(3, gaps.length);
		assertEquals(Math.PI * 2 / 3, gaps[1], EPS);
		assertTrue(DreadRules.inGap(0.2, gaps, Math.toRadians(24)));
		assertTrue(DreadRules.inGap(Math.PI * 2 - 0.2, gaps, Math.toRadians(24)), "around the turn");
		assertFalse(DreadRules.inGap(Math.PI / 3, gaps, Math.toRadians(24)));
		assertEquals(Math.PI, DreadRules.angleBetween(0, Math.PI), EPS);
		assertEquals(0.2, DreadRules.angleBetween(0.1, -0.1), EPS);
		assertTrue(DreadRules.swept(5.0, 5.2, 0.45));
		assertFalse(DreadRules.swept(3.0, 5.2, 0.45), "inside the ring, not on its front");
		assertEquals(0, DreadRules.pull(8, 7, 0.1), EPS, "out of the well");
		assertTrue(DreadRules.pull(1, 7, 0.1) > DreadRules.pull(6, 7, 0.1), "stronger near the center");
		assertEquals(5, DreadRules.closing(5, 1.4, 0), EPS);
		assertEquals(1.4, DreadRules.closing(5, 1.4, 1), EPS);
	}

	@Test
	void homingMissilesTurnSlowly() {
		double[] dir = {1, 0, 0};
		double[] toTarget = {0, 0, 1};
		double[] next = DreadRules.steer(dir, toTarget, 0.1);
		double turned = Math.acos(next[0]);
		assertEquals(0.1, turned, 1.0E-6, "never more than its turn per tick");
		assertEquals(1.0, Math.sqrt(next[0] * next[0] + next[1] * next[1] + next[2] * next[2]), 1.0E-9);
		assertArrayEquals(new double[] {0, 0, 1}, DreadRules.steer(new double[] {0.05, 0, 1}, toTarget, 0.5), 1.0E-9, "close enough: on target");
	}
}
