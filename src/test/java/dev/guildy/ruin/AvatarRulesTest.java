package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.DreadAbilityDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Avatar: who summons it, its threat, health, damage, abilities by phase, rewards. */
class AvatarRulesTest {
	private static final double EPS = 1.0E-9;

	@Test
	void onlyACursedRitualWithTheStarSummonsIt() {
		assertTrue(AvatarRules.summons(true, true, true, true));
		assertFalse(AvatarRules.summons(true, true, false, true), "a normal ritual with a star: no Avatar");
		assertFalse(AvatarRules.summons(true, true, true, false), "a cursed ritual without the star: the cursed guardian");
		assertFalse(AvatarRules.summons(false, true, true, true), "Avatar disabled");
		assertFalse(AvatarRules.summons(true, false, true, true), "curse disabled");
	}

	@Test
	void itFightsAtLeastAtThreatTenWithItsOwnHealthAndDamage() {
		AvatarSettings a = new AvatarSettings();
		assertEquals(10, AvatarRules.threat(1, a.minThreat), "at least Menace X");
		assertEquals(14, AvatarRules.threat(14, a.minThreat), "a higher threat stays");
		// The crypt's lord, alone: 220 x 2.8 (X) x 3 (curse) x 4 (dread X) = 7392, x2 = 14784 (between 10000 and 16000).
		double intended = DreadRules.intendedHealth(220, 2.8, 1.0, 3.0, 4.0);
		assertEquals(14784, AvatarRules.health(intended, 1, a), 1.0E-6);
		assertEquals(10000, AvatarRules.health(1000, 1, a), EPS, "never under 10000 per player");
		assertEquals(64000, AvatarRules.health(127680, 4, a), EPS, "never above 16000 per player");
		RuinConfig.EscalationSettings e = new RuinConfig.EscalationSettings();
		double dreadX = DreadRules.stats(e, 10).damage();
		assertEquals(dreadX * a.damageMultiplier, AvatarRules.playerDamage(dreadX, a), EPS);
		assertTrue(AvatarRules.playerDamage(dreadX, a) > dreadX, "harder than the dread form of Menace X");
		// Its health stays well above the cursed guardian of Menace X (capped at 8000 per player).
		double cursedX = DreadRules.effectiveHealth(DreadRules.intendedHealth(760, 2.8, 1.0, 3.0, 4.0), 1, e.dread.healthCapPerPlayer);
		assertTrue(AvatarRules.health(DreadRules.intendedHealth(760, 2.8, 1.0, 3.0, 4.0), 1, a) > cursedX);
	}

	@Test
	void itsThemeAmplifiedThenTheNetherPhaseAfterPhase() {
		AvatarSettings a = new AvatarSettings();
		List<DreadAbilityDef> theme = RuinDefaults.cryptDread();
		List<DreadAbilityDef> all = AvatarRules.abilities(theme, a.attacks, a);
		assertEquals(theme.size() + a.attacks.size(), all.size());
		DreadAbilityDef graves = all.getFirst();
		assertEquals("grasping_graves", graves.type);
		assertEquals(theme.getFirst().damage * a.themeDamageMultiplier, graves.damage, EPS, "its theme amplified");
		assertEquals(theme.getFirst().count + a.themeCountBonus, graves.count);
		assertEquals(1, graves.phase, "from the first phase");
		assertEquals(6, theme.getFirst().damage, EPS, "the ruin's own definition untouched");
		boolean rifts = false;
		for (DreadAbilityDef def : all) {
			if (def.type.equals("nether_rifts")) {
				rifts = true;
				assertEquals(2, def.phase, "the rifts open from phase II");
			}
			if (def.type.equals("collapsing_ring")) {
				assertEquals(3, def.phase);
			}
		}
		assertTrue(rifts);
		assertEquals(1, AvatarRules.unlockPhase(1, false));
		assertEquals(2, AvatarRules.unlockPhase(2, false));
		assertEquals(3, AvatarRules.unlockPhase(2, true), "every attack in its final form");
		assertEquals(1.0, AvatarRules.cooldownFactor(false, a), EPS);
		assertEquals(a.finalFormCooldownFactor, AvatarRules.cooldownFactor(true, a), EPS);
	}

	@Test
	void hugeRewardsOnTopOfTheCurse() {
		AvatarSettings a = new AvatarSettings();
		CursedSettings c = new CursedSettings();
		ThreatRules.Rewards threat = ThreatRules.rewards(new RuinConfig.EscalationSettings(), new RuinConfig.ResurrectionSettings(), 10, false);
		ThreatRules.Rewards cursed = CursedRules.rewards(threat, c.rewards);
		ThreatRules.Rewards avatar = AvatarRules.rewards(cursed, a.rewards);
		assertEquals(cursed.gold() * a.rewards.goldMultiplier, avatar.gold(), EPS);
		assertEquals(cursed.xp() * a.rewards.xpMultiplier, avatar.xp(), EPS);
		assertEquals(cursed.extraLootRolls() + a.rewards.extraLootRolls, avatar.extraLootRolls());
		assertEquals(cursed.bonusUniqueRolls() + a.rewards.extraUniqueRolls, avatar.bonusUniqueRolls());
		assertEquals(cursed.bonusScrollRolls() + a.rewards.extraScrollRolls, avatar.bonusScrollRolls());
		assertTrue(a.rewards.maxUniques > c.rewards.maxUniques, "more unique weapons than a cursed guardian");
	}
}
