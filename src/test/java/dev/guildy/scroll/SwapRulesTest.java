package dev.guildy.scroll;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Permutation never sends anyone into a block, lava, fire or above the void: the caster's landing (the target's spot or the
 * ground right under it) and the target's (the caster's spot) are both checked, the caster's first.
 */
class SwapRulesTest {
	private static SwapRules.Spot safe() {
		return new SwapRules.Spot(true, true, false, false, 0);
	}

	@Test
	void aSafeSpotOnTheGround() {
		assertEquals(SwapRules.Verdict.OK, SwapRules.forCaster(safe()));
		assertEquals(SwapRules.Verdict.OK, SwapRules.check(safe(), safe()));
		assertNull(SwapRules.failureKey(SwapRules.Verdict.OK));
	}

	@Test
	void theCasterNeverLandsSomewhereDangerous() {
		assertEquals(SwapRules.Verdict.UNLOADED, SwapRules.forCaster(new SwapRules.Spot(false, true, false, false, 0)));
		assertEquals(SwapRules.Verdict.BLOCKED, SwapRules.forCaster(new SwapRules.Spot(true, false, false, false, 0)), "into a wall");
		assertEquals(SwapRules.Verdict.LAVA, SwapRules.forCaster(new SwapRules.Spot(true, true, true, false, 0)));
		assertEquals(SwapRules.Verdict.HAZARD, SwapRules.forCaster(new SwapRules.Spot(true, true, false, true, 0)), "fire, magma, cactus...");
		assertEquals(SwapRules.Verdict.FALL, SwapRules.forCaster(new SwapRules.Spot(true, true, false, false, -1)), "the void");
		// A floating target: the caster lands on the ground under it, never after a fall that hurts.
		for (int drop = 0; drop <= SwapRules.MAX_DROP; drop++) {
			assertEquals(SwapRules.Verdict.OK, SwapRules.forCaster(new SwapRules.Spot(true, true, false, false, drop)), "drop " + drop);
		}
		assertEquals(SwapRules.Verdict.FALL, SwapRules.forCaster(new SwapRules.Spot(true, true, false, false, SwapRules.MAX_DROP + 1)));
		assertEquals(3, SwapRules.MAX_DROP, "no fall damage under 4 blocks");
		// The first problem wins: room before lava before hazards before the fall.
		assertEquals(SwapRules.Verdict.BLOCKED, SwapRules.forCaster(new SwapRules.Spot(true, false, true, true, -1)));
		assertEquals(SwapRules.Verdict.LAVA, SwapRules.forCaster(new SwapRules.Spot(true, true, true, true, -1)));
	}

	@Test
	void theTargetOnlyNeedsRoom() {
		// The caster stood there: lava, hazards and the ground do not matter, the hitbox does (a big monster, a small spot).
		assertEquals(SwapRules.Verdict.OK, SwapRules.forTarget(new SwapRules.Spot(true, true, true, true, -1)));
		assertEquals(SwapRules.Verdict.BLOCKED, SwapRules.forTarget(new SwapRules.Spot(true, false, false, false, 0)));
		assertEquals(SwapRules.Verdict.UNLOADED, SwapRules.forTarget(new SwapRules.Spot(false, true, false, false, 0)));
		assertEquals(SwapRules.Verdict.BLOCKED, SwapRules.check(safe(), new SwapRules.Spot(true, false, false, false, 0)));
		assertEquals(SwapRules.Verdict.LAVA, SwapRules.check(new SwapRules.Spot(true, true, true, false, 0), new SwapRules.Spot(true, false, false, false, 0)),
				"the caster's destination first");
	}

	@Test
	void everyRefusalHasAMessage() {
		for (SwapRules.Verdict verdict : SwapRules.Verdict.values()) {
			if (verdict != SwapRules.Verdict.OK) {
				assertNotNull(SwapRules.failureKey(verdict), verdict.name());
			}
		}
		assertEquals("scroll.fail.swap_fall", SwapRules.failureKey(SwapRules.Verdict.FALL));
		assertEquals("scroll.fail.swap_hazard", SwapRules.failureKey(SwapRules.Verdict.LAVA));
	}
}
