package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardRulesTest {

	// ------------------------------------------------------------------ roles

	@Test
	void autoRolesAlternateForAnEvenShare() {
		int swords = 0;
		int bows = 0;
		StringBuilder order = new StringBuilder();
		for (int i = 0; i < 6; i++) {
			GuardRole role = GuardRules.pickRole(swords, bows, 0.5);
			order.append(role == GuardRole.BOW ? 'B' : 'S');
			if (role == GuardRole.BOW) {
				bows++;
			} else {
				swords++;
			}
		}
		assertEquals("SBSBSB", order.toString());
		assertEquals(3, swords);
		assertEquals(3, bows);
	}

	@Test
	void autoRoleFillsTheMissingRole() {
		// Two archers died: the next guards become archers until the balance is back.
		assertEquals(GuardRole.BOW, GuardRules.pickRole(3, 1, 0.5));
		assertEquals(GuardRole.SWORD, GuardRules.pickRole(1, 3, 0.5));
		assertEquals(GuardRole.SWORD, GuardRules.pickRole(2, 2, 0.5));
	}

	@Test
	void autoRoleFollowsTheConfiguredShare() {
		for (int s = 0; s < 6; s++) {
			for (int b = 0; b < 6; b++) {
				assertEquals(GuardRole.SWORD, GuardRules.pickRole(s, b, 0.0), "share 0: only swordsmen");
				assertEquals(GuardRole.BOW, GuardRules.pickRole(s, b, 1.0), "share 1: only archers");
			}
		}
		// One archer for three guards.
		int swords = 0;
		int bows = 0;
		for (int i = 0; i < 12; i++) {
			if (GuardRules.pickRole(swords, bows, 1.0 / 3.0) == GuardRole.BOW) {
				bows++;
			} else {
				swords++;
			}
		}
		assertEquals(8, swords);
		assertEquals(4, bows);
	}

	@Test
	void roleSettingCycles() {
		GuardRules.Setting first = GuardRules.nextSetting(true, GuardRole.BOW);
		assertFalse(first.auto());
		assertEquals(GuardRole.SWORD, first.forced());
		GuardRules.Setting second = GuardRules.nextSetting(false, GuardRole.SWORD);
		assertFalse(second.auto());
		assertEquals(GuardRole.BOW, second.forced());
		GuardRules.Setting third = GuardRules.nextSetting(false, GuardRole.BOW);
		assertTrue(third.auto());
		assertNull(third.forced());
	}

	// ------------------------------------------------------------------ equipment

	@Test
	void moreArmorWinsThenToughness() {
		GuardRules.Piece iron = new GuardRules.Piece(6, 0, 1.0);
		GuardRules.Piece diamond = new GuardRules.Piece(8, 2, 1.0);
		GuardRules.Piece wornDiamond = new GuardRules.Piece(8, 2, 0.1);
		GuardRules.Piece netherite = new GuardRules.Piece(8, 3, 1.0);
		assertTrue(GuardRules.better(iron, GuardRules.Piece.NONE), "any armor beats an empty slot");
		assertTrue(GuardRules.better(diamond, iron));
		assertFalse(GuardRules.better(iron, diamond));
		assertTrue(GuardRules.better(wornDiamond, iron), "armor points first, even worn");
		assertTrue(GuardRules.better(netherite, diamond), "same armor: toughness decides");
		assertFalse(GuardRules.better(diamond, netherite));
	}

	@Test
	void freshPieceOnlyWhenMuchLessWorn() {
		GuardRules.Piece worn = new GuardRules.Piece(6, 0, 0.2);
		GuardRules.Piece slightlyWorn = new GuardRules.Piece(6, 0, 0.9);
		GuardRules.Piece fresh = new GuardRules.Piece(6, 0, 1.0);
		assertTrue(GuardRules.better(fresh, worn));
		assertFalse(GuardRules.better(fresh, slightlyWorn), "no trip for a few durability points");
		assertFalse(GuardRules.better(fresh, fresh));
	}

	@Test
	void uselessPiecesAreNeverWorn() {
		GuardRules.Piece elytra = new GuardRules.Piece(0, 0, 1.0);
		assertFalse(elytra.useful());
		assertFalse(GuardRules.better(elytra, GuardRules.Piece.NONE));
	}

	@Test
	void bestPieceOrdering() {
		GuardRules.Piece a = new GuardRules.Piece(5, 0, 1.0);
		GuardRules.Piece b = new GuardRules.Piece(5, 1, 0.5);
		GuardRules.Piece c = new GuardRules.Piece(5, 1, 0.8);
		assertTrue(GuardRules.compare(b, a) > 0);
		assertTrue(GuardRules.compare(c, b) > 0);
		assertEquals(0, GuardRules.compare(c, new GuardRules.Piece(5, 1, 0.8)));
	}

	@Test
	void weaponScores() {
		double iron = GuardRules.swordScore(6, 0);
		assertTrue(GuardRules.betterWeapon(GuardRules.swordScore(7, 0), iron), "diamond sword");
		assertTrue(GuardRules.betterWeapon(GuardRules.swordScore(6, 1), iron), "sharpness I iron sword");
		assertFalse(GuardRules.betterWeapon(GuardRules.swordScore(5, 0), iron), "stone sword is worse");
		assertFalse(GuardRules.betterWeapon(iron, iron));
		double plainBow = GuardRules.bowScore(0, 0, 0);
		assertFalse(GuardRules.betterWeapon(plainBow, plainBow), "a plain bow is not worth the trip");
		assertTrue(GuardRules.betterWeapon(GuardRules.bowScore(1, 0, 0), plainBow));
		assertTrue(GuardRules.betterWeapon(GuardRules.bowScore(0, 1, 0), plainBow));
		assertEquals(1.0, GuardRules.swordBonus(7, 6), 1e-9);
		assertEquals(0.0, GuardRules.swordBonus(4, 6), 1e-9);
	}

	// ------------------------------------------------------------------ combat

	@Test
	void archerKeepsItsDistance() {
		assertEquals(GuardRules.ArcherMove.APPROACH, GuardRules.archerMove(20, true, 5, 16));
		assertEquals(GuardRules.ArcherMove.APPROACH, GuardRules.archerMove(10, false, 5, 16), "no line of sight");
		assertEquals(GuardRules.ArcherMove.BACK_OFF, GuardRules.archerMove(3, true, 5, 16));
		assertEquals(GuardRules.ArcherMove.HOLD, GuardRules.archerMove(10, true, 5, 16));
		assertEquals(GuardRules.ArcherMove.HOLD, GuardRules.archerMove(16, true, 5, 16));
	}

	@Test
	void arrowDamageDoesNotDependOnSpeed() {
		for (double speed : new double[]{0.5, 1.2, 1.6, 3.0}) {
			assertEquals(4.5, GuardRules.arrowBaseDamage(4.5, speed) * speed, 1e-9);
		}
		assertEquals(0.0, GuardRules.arrowBaseDamage(-3, 1.6), 1e-9);
	}

	// ------------------------------------------------------------------ retreat & healing

	@Test
	void retreatAndResumeThresholds() {
		assertFalse(GuardRules.mustRetreat(20, 20, 0.4));
		assertFalse(GuardRules.mustRetreat(8, 20, 0.4), "exactly 40 %: still fighting");
		assertTrue(GuardRules.mustRetreat(7.5, 20, 0.4));
		assertFalse(GuardRules.mustRetreat(1, 20, 0.0), "ratio 0 = never retreats");
		assertFalse(GuardRules.canResume(17.5, 20, 0.9));
		assertTrue(GuardRules.canResume(18, 20, 0.9));
		assertTrue(GuardRules.canResume(20, 20, 1.0));
	}

	@Test
	void healingRates() {
		assertEquals(1.0, GuardRules.healPerSecond(true, true, false, 0.5, 1.0, 0.1), 1e-9, "at its bed");
		assertEquals(0.5, GuardRules.healPerSecond(true, false, true, 0.5, 1.0, 0.1), 1e-9, "retreating, away from a bed");
		assertEquals(0.1, GuardRules.healPerSecond(false, false, true, 0.5, 1.0, 0.1), 1e-9, "passive, out of combat");
		assertEquals(0.0, GuardRules.healPerSecond(false, false, false, 0.5, 1.0, 0.1), 1e-9, "fighting: no regeneration");
		assertEquals(0.5, GuardRules.healPerSecond(true, true, false, 0.5, 0.2, 0.1), 1e-9, "a bed never heals slower");
	}

	@Test
	void healthDisplay() {
		assertEquals(12, GuardRules.displayHealth(12.0));
		assertEquals(13, GuardRules.displayHealth(12.25));
		assertEquals(1, GuardRules.displayHealth(0.3));
		assertEquals(0, GuardRules.displayHealth(0));
	}
}
