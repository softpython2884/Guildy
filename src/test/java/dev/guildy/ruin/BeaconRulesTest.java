package dev.guildy.ruin;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Beacon sanctuaries: the levels of a pyramid (vanilla scan) and the blocks they protect. */
class BeaconRulesTest {
	/** A beacon at (0, 100, 0) on a full pyramid of {@code levels} levels; {@code holes} are missing blocks. */
	private static BeaconRules.Base pyramid(int levels, Set<String> holes) {
		return (x, y, z) -> {
			int step = 100 - y;
			return step >= 1 && step <= levels && Math.abs(x) <= step && Math.abs(z) <= step && !holes.contains(x + "," + y + "," + z);
		};
	}

	@Test
	void levelsOfAPyramid() {
		assertEquals(4, BeaconRules.levels(0, 100, 0, -64, pyramid(4, Set.of())), "full pyramid");
		assertEquals(4, BeaconRules.levels(0, 100, 0, -64, pyramid(6, Set.of())), "never more than four levels");
		assertEquals(2, BeaconRules.levels(0, 100, 0, -64, pyramid(2, Set.of())));
		assertEquals(0, BeaconRules.levels(0, 100, 0, -64, pyramid(0, Set.of())), "a lone beacon");
		assertEquals(1, BeaconRules.levels(0, 100, 0, -64, pyramid(4, Set.of("2,98,-2"))), "a corner missing at level 2: one level");
		assertEquals(0, BeaconRules.levels(0, 100, 0, -64, pyramid(4, Set.of("0,99,0"))), "the block under it missing");
		assertEquals(2, BeaconRules.levels(0, 100, 0, 98, pyramid(4, Set.of())), "nothing below the bottom of the world");
	}

	@Test
	void protectedBlocks() {
		int levels = 2;
		Set<String> inside = new HashSet<>();
		for (int y = 90; y <= 101; y++) {
			for (int x = -6; x <= 6; x++) {
				for (int z = -6; z <= 6; z++) {
					if (BeaconRules.protects(0, 100, 0, levels, x, y, z)) {
						inside.add(x + "," + y + "," + z);
					}
				}
			}
		}
		assertEquals(1 + BeaconRules.baseBlocks(levels), inside.size(), "the beacon and its 9 + 25 base blocks");
		assertTrue(inside.contains("0,100,0"), "the beacon");
		assertTrue(inside.contains("1,99,-1") && inside.contains("-2,98,2"), "corners of both levels");
		assertFalse(inside.contains("2,99,0"), "outside level 1");
		assertFalse(inside.contains("0,97,0"), "level 3 does not count");
		assertFalse(inside.contains("0,101,0"), "the glass above is not protected");
		assertFalse(BeaconRules.protects(0, 100, 0, 0, 0, 99, 0), "no complete level: only the beacon");
		assertTrue(BeaconRules.protects(0, 100, 0, 0, 0, 100, 0));
		assertFalse(BeaconRules.protects(0, 100, 0, 9, 0, 95, 0), "never below four levels");
	}

	@Test
	void baseBlocks() {
		assertEquals(0, BeaconRules.baseBlocks(0));
		assertEquals(9, BeaconRules.baseBlocks(1));
		assertEquals(34, BeaconRules.baseBlocks(2));
		assertEquals(83, BeaconRules.baseBlocks(3));
		assertEquals(164, BeaconRules.baseBlocks(4));
		assertEquals(164, BeaconRules.baseBlocks(7));
	}
}
