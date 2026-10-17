package dev.guildy.ruin;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cursed guardians: phases and transitions, multipliers, attacks by phase, pentagram damage rules, rewards, settings. */
class CursedRulesTest {
	private static final double EPS = 1.0E-9;

	// ================================================================== phases

	@Test
	void phasesStartUnderTheirThresholds() {
		assertEquals(1.0, CursedRules.threshold(1, 0.66, 0.33), EPS);
		assertEquals(0.66, CursedRules.threshold(2, 0.66, 0.33), EPS);
		assertEquals(0.33, CursedRules.threshold(3, 0.66, 0.33), EPS);
		assertEquals(0.0, CursedRules.threshold(4, 0.66, 0.33), EPS, "nothing after the last phase");
		assertEquals(0.5, CursedRules.threshold(3, 0.5, 0.7), EPS, "a phase 3 threshold above phase 2 is capped");

		assertEquals(1, CursedRules.next(1, 0.9, 0.66, 0.33));
		assertEquals(1, CursedRules.next(1, 0.66, 0.66, 0.33), "exactly at the threshold: still phase 1");
		assertEquals(2, CursedRules.next(1, 0.659, 0.66, 0.33));
		assertEquals(2, CursedRules.next(1, 0.01, 0.66, 0.33), "a huge hit never skips phase 2");
		assertEquals(2, CursedRules.next(2, 0.5, 0.66, 0.33));
		assertEquals(3, CursedRules.next(2, 0.2, 0.66, 0.33));
		assertEquals(3, CursedRules.next(3, 0.01, 0.66, 0.33), "the last phase stays");
		assertEquals(2, CursedRules.next(0, 0.5, 0.66, 0.33), "a bad phase counts as the first");
	}

	@Test
	void aBigHitStopsAtTheNextThreshold() {
		assertEquals(0.66, CursedRules.floorRatio(1, 0.1, 0.66, 0.33), EPS, "the overflow is lost");
		assertEquals(0.8, CursedRules.floorRatio(1, 0.8, 0.66, 0.33), EPS, "above the threshold: untouched");
		assertEquals(0.33, CursedRules.floorRatio(2, -0.5, 0.66, 0.33), EPS, "even a killing blow");
		assertEquals(0.05, CursedRules.floorRatio(3, 0.05, 0.66, 0.33), EPS, "the last phase keeps nothing");
		assertFalse(CursedRules.diesIn(1));
		assertFalse(CursedRules.diesIn(2));
		assertTrue(CursedRules.diesIn(3), "only killed for good in phase 3");
	}

	@Test
	void transitionsRiseHoverAndSlam() {
		assertEquals(0.0, CursedRules.lift(0, 3), EPS, "starts on the floor");
		assertEquals(3.0, CursedRules.lift(CursedRules.RISE_END, 3), 1.0E-6, "risen at the end of the rise");
		for (double p = CursedRules.RISE_END; p < CursedRules.SLAM; p += 0.01) {
			double h = CursedRules.lift(p, 3);
			assertTrue(h >= 2.5 && h <= 3.6, "hovers around its height at " + p + ": " + h);
		}
		assertEquals(0.0, CursedRules.lift(CursedRules.SLAM, 3), EPS, "slams down");
		assertEquals(0.0, CursedRules.lift(1.0, 3), EPS);
		assertEquals(0.0, CursedRules.lift(0.5, 0), EPS, "no room: stays on the floor");
		assertTrue(CursedRules.CIRCLE_START < CursedRules.CIRCLE_DRAWN && CursedRules.CHAINS_START < CursedRules.CHAINS_END
				&& CursedRules.CHAINS_END < CursedRules.SLAM && CursedRules.SLAM < CursedRules.MINIONS && CursedRules.MINIONS < 1.0, "timeline in order");
		assertEquals(3.0, CursedRules.maxLift(12, 2.0, 3.0), EPS);
		assertEquals(1.2, CursedRules.maxLift(3.5, 2.0, 3.0), 1.0E-9, "limited by the ceiling");
		assertEquals(0.0, CursedRules.maxLift(2, 2.0, 3.0), EPS);
	}

	// ================================================================== multipliers

	@Test
	void theCursedGuardianIsMuchStronger() {
		CursedSettings s = new CursedSettings();
		s.fix(id -> true, new ArrayList<>());
		CursedRules.Stats stats = CursedRules.stats(s.guardian);
		assertTrue(stats.health() >= 3.0, "at least 3x health: " + stats.health());
		assertTrue(stats.damage() >= 2.0, "at least 2x damage: " + stats.damage());
		assertTrue(stats.armor() > 0 && stats.toughness() > 0 && stats.scale() > 1 && stats.speed() > 0, "armor, size, speed");
		assertTrue(stats.cooldown() < 1.0, "faster abilities");

		assertEquals(2.0, CursedRules.abilityDamage(s, 1), EPS, "phase 1: the damage multiplier");
		assertEquals(2.5, CursedRules.abilityDamage(s, 2), EPS, "+25 % in phase 2");
		assertEquals(3.0, CursedRules.abilityDamage(s, 3), EPS, "+50 % in phase 3");
		assertEquals(0.6, CursedRules.abilityCooldown(s, 1), EPS);
		assertEquals(0.6 * 0.85, CursedRules.abilityCooldown(s, 2), EPS);
		assertEquals(0.6 * 0.85 * 0.85, CursedRules.abilityCooldown(s, 3), EPS);
		assertTrue(CursedRules.abilityCooldown(s, 3) >= 0.15, "never instant");
		assertEquals(1.0, CursedRules.phaseDamage(1, 0.25), EPS);
		assertEquals(1.5, CursedRules.phaseDamage(9, 0.25), EPS, "phases capped at 3");
		assertEquals(0.2, CursedRules.phaseCooldown(3, 0.05), EPS, "cooldown floor");

		assertEquals(1.3, CursedRules.scale(1.3, 12, 2.0), EPS);
		assertEquals(1.35, CursedRules.scale(2.0, 3.0, 2.0), EPS, "limited by the free blocks above the spawn");
		assertEquals(1.0, CursedRules.scale(1.3, 1.0, 2.0), EPS, "never smaller");
		assertEquals(7, CursedRules.escort(3, 1.5, 2, 20), "3 x 1.5 + 2, rounded");
		assertEquals(6, CursedRules.escort(10, 1.5, 2, 6), "within the escort cap");
		assertEquals(0, CursedRules.escort(-3, -1, -1, 5));
	}

	@Test
	void attacksUnlockWithThePhases() {
		assertTrue(CursedRules.unlocked(1).isEmpty(), "phase 1: its normal abilities only (faster)");
		List<CursedRules.Attack> two = CursedRules.unlocked(2);
		assertEquals(List.of(CursedRules.Attack.PENTAGRAMS, CursedRules.Attack.CHAINS, CursedRules.Attack.METEORS), two, "3 new attacks in phase 2");
		List<CursedRules.Attack> three = CursedRules.unlocked(3);
		assertTrue(three.containsAll(two), "phase 3 keeps them");
		assertTrue(three.size() - two.size() >= 3, "and adds at least 3");
		assertEquals(Set.of(CursedRules.Attack.values()), Set.copyOf(three), "every attack in phase 3");
		for (CursedRules.Attack attack : CursedRules.Attack.values()) {
			assertEquals(attack, CursedRules.Attack.byId(attack.id()), "admin id of " + attack);
		}
		assertEquals(null, CursedRules.Attack.byId("nope"));
		assertEquals(300, CursedRules.cooldownTicks(15, 1.0));
		assertEquals(20, CursedRules.cooldownTicks(0.1, 0.1), "at least one second");
		assertEquals(3, CursedRules.pentagrams(3, 2));
		assertEquals(4, CursedRules.pentagrams(3, 3), "one more pentagram in phase 3");
		assertEquals(1, CursedRules.pentagrams(0, 2));
	}

	// ================================================================== pentagrams and damage

	@Test
	void pentagramsDrainThoseWhoStandInThem() {
		assertTrue(CursedRules.inside(0, 0, 0, 2.5), "center");
		assertTrue(CursedRules.inside(2.0, 0.5, 1.4, 2.5), "near the edge");
		assertFalse(CursedRules.inside(2.0, 0, 1.6, 2.5), "just outside");
		assertTrue(CursedRules.inside(0, 1.9, 0, 2.5), "jumping in it");
		assertFalse(CursedRules.inside(0, 3.0, 0, 2.5), "far above");
		assertFalse(CursedRules.inside(0, -1.0, 0, 2.5), "on the floor below");

		int telegraph = CursedRules.TELEGRAPH_TICKS;
		assertFalse(CursedRules.pulse(0, telegraph, 160), "telegraphed: no damage during the warning");
		assertFalse(CursedRules.pulse(telegraph - 1, telegraph, 160));
		assertTrue(CursedRules.pulse(telegraph, telegraph, 160), "first pulse when it ignites");
		assertFalse(CursedRules.pulse(telegraph + 1, telegraph, 160));
		assertTrue(CursedRules.pulse(telegraph + CursedRules.PULSE_TICKS, telegraph, 160), "every half second");
		assertFalse(CursedRules.pulse(telegraph + 160, telegraph, 160), "burnt out");
		int pulses = 0;
		for (int age = 0; age < 400; age++) {
			if (CursedRules.pulse(age, telegraph, 160)) {
				pulses++;
			}
		}
		assertEquals(16, pulses, "8 s of pulses every 0.5 s");

		assertEquals(3.0, CursedRules.heal(1.5, 2.0), 1.0E-6, "the guardian heals twice the drained health");
		assertEquals(0.0, CursedRules.heal(-4, 2.0), EPS);
		assertEquals(0.0, CursedRules.heal(4, -1), EPS);

		assertEquals(8.0, CursedRules.impact(8, 0, 2.5), EPS, "meteor: full damage at the center");
		assertEquals(8.0, CursedRules.impact(8, 1.25, 2.5), EPS, "full in the inner half");
		assertEquals(3.2, CursedRules.impact(8, 2.5, 2.5), 1.0E-9, "40 % at the edge");
		assertEquals(0.0, CursedRules.impact(8, 2.6, 2.5), EPS, "none outside");
		assertTrue(CursedRules.onFront(5.0, 5.0) && CursedRules.onFront(4.0, 5.0) && !CursedRules.onFront(2.0, 5.0) && !CursedRules.onFront(6.0, 5.0),
				"the nova hits on its front only");
	}

	// ================================================================== rewards

	@Test
	void cursedRewardsAreHuge() {
		CursedSettings.RewardSettings r = new CursedSettings.RewardSettings();
		ThreatRules.Rewards rewards = CursedRules.rewards(ThreatRules.Rewards.NONE, r);
		assertEquals(3.0, rewards.gold(), EPS, "gold x3");
		assertEquals(3.0, rewards.xp(), EPS, "XP x3");
		assertEquals(r.extraLootRolls, rewards.extraLootRolls(), "several loot table rolls");
		assertEquals(r.extraUniqueRolls, rewards.bonusUniqueRolls(), "several unique attempts");
		assertEquals(r.extraScrollRolls, rewards.bonusScrollRolls(), "several scroll rolls");
		ThreatRules.Rewards threat = new ThreatRules.Rewards(1.45, 1.3, 1, 0.15, 0.24, 1, 1);
		ThreatRules.Rewards both = CursedRules.rewards(threat, r);
		assertEquals(1.45 * 3, both.gold(), EPS, "on top of the threat level");
		assertEquals(1.3 * 3, both.xp(), EPS);
		assertEquals(1 + r.extraLootRolls, both.extraLootRolls());
		assertEquals(0.15, both.extraUniqueChance(), EPS);
		assertEquals(1 + r.extraUniqueRolls, both.bonusUniqueRolls());
		ThreatRules.Rewards huge = CursedRules.rewards(new ThreatRules.Rewards(1, 1, 50, 0, 0, 50, 50), r);
		assertTrue(huge.extraLootRolls() <= 20 && huge.bonusUniqueRolls() <= 15 && huge.bonusScrollRolls() <= 15, "rolls bounded");
		assertEquals(3.0, CursedRules.rewards(null, r).gold(), EPS);

		assertEquals(2, CursedRules.cappedRolls(6, 2, i -> true), "never more than the cap");
		assertEquals(0, CursedRules.cappedRolls(6, 2, i -> false));
		assertEquals(1, CursedRules.cappedRolls(6, 2, i -> i == 4));
		assertEquals(3, CursedRules.cappedRolls(3, 9, i -> true), "never more than the attempts");
		assertEquals(0, CursedRules.cappedRolls(-1, 2, i -> true));
		int[] calls = {0};
		CursedRules.cappedRolls(10, 2, i -> {
			calls[0]++;
			return true;
		});
		assertEquals(2, calls[0], "stops rolling once the cap is reached");

		assertEquals(0.0, CursedRules.atLeastOne(0, 0.5), EPS);
		assertEquals(1 - Math.pow(0.88, 6), CursedRules.atLeastOne(6, 0.12), 1.0E-12, "6 attempts at 12 %");
		assertTrue(CursedRules.atLeastOne(6, 0.12) > 0.5, "more likely than not");

		assertEquals(3, CursedRules.nextThreat(1, 2, 10), "the threat rises by 2");
		assertEquals(10, CursedRules.nextThreat(9, 2, 10), "never above the cap");
		assertEquals(4, CursedRules.nextThreat(4, 0, 10), "gain 0: unchanged");
		assertEquals(5, CursedRules.nextThreat(12, 1, 5), "a lowered cap applies");
	}

	// ================================================================== settings

	@Test
	void settingsAreBounded() {
		CursedSettings s = new CursedSettings();
		List<String> warnings = new ArrayList<>();
		s.fix(id -> true, warnings);
		assertTrue(warnings.isEmpty(), "defaults: " + warnings);
		assertEquals(6.0, s.ritual.drainHealth, EPS, "heavier blood price: 3 hearts");
		assertTrue(s.phases.phase3Below < s.phases.phase2Below);

		s.ritual.item = "minecraft:nope";
		s.ritual.minHealth = 0;
		s.ritual.maxStack = 999;
		s.guardian.healthMultiplier = 0.2;
		s.guardian.scale = 9;
		s.phases.phase2Below = 0.3;
		s.phases.phase3Below = 0.9;
		s.phases.transitionSeconds = 0;
		s.attacks.pentagramCount = 99;
		s.attacks.particleBudget = 1;
		s.attacks.pentagramDamage = Double.NaN;
		s.rewards.maxUniques = 0;
		s.rewards.threatGain = 40;
		s.sources.tradePrice = 0;
		s.sources.tradeChance = 3;
		s.guardian = null;
		warnings.clear();
		s.fix(id -> !id.equals("minecraft:nope"), warnings);
		assertEquals(1, warnings.size(), "unknown base item: " + warnings);
		assertEquals(CursedSettings.DEFAULT_ITEM, s.ritual.item);
		assertEquals(1.0, s.ritual.minHealth, EPS, "the ritual never kills");
		assertEquals(64, s.ritual.maxStack);
		assertEquals(3.0, s.guardian.healthMultiplier, EPS, "missing section: defaults");
		assertTrue(s.phases.phase3Below < s.phases.phase2Below, "phase 3 under phase 2: " + s.phases.phase3Below);
		assertEquals(2.0, s.phases.transitionSeconds, EPS);
		assertEquals(8, s.attacks.pentagramCount);
		assertEquals(100, s.attacks.particleBudget);
		assertEquals(0.0, s.attacks.pentagramDamage, EPS);
		assertEquals(1, s.rewards.maxUniques);
		assertEquals(5, s.rewards.threatGain);
		assertEquals(1, s.sources.tradePrice);
		assertEquals(1.0, s.sources.tradeChance, EPS);
	}

	@Test
	void cursedSectionInRuinsJson5(@TempDir Path dir) throws Exception {
		RuinConfig written = new RuinConfig();
		assertTrue(written.rebuild(id -> true, id -> true, id -> true).isEmpty());
		written.cursed.guardian.healthMultiplier = 4;
		written.cursed.sources.tradePrice = 300;
		Path file = dir.resolve("ruins.json5");
		Json5.write(file, written);
		String text = java.nio.file.Files.readString(file);
		assertTrue(text.contains("\"cursed\"") && text.contains("\"pentagramRadius\"") && text.contains("// Gardiens maudits"), "section written with comments");
		RuinConfig read = Json5.read(file, RuinConfig.class);
		assertTrue(read.rebuild(id -> true, id -> true, id -> true).isEmpty());
		assertEquals(4.0, read.cursed.guardian.healthMultiplier, EPS);
		assertEquals(300, read.cursed.sources.tradePrice);
		// An older ruins.json5 without the section: defaults, no migration.
		RuinConfig old = Json5.fromString("{\"configVersion\": 1, \"bossesEnabled\": true}", RuinConfig.class);
		assertTrue(old.rebuild(id -> true, id -> true, id -> true).isEmpty());
		assertTrue(old.cursed.enabled && old.cursed.guardian.healthMultiplier >= 3);
	}
}
