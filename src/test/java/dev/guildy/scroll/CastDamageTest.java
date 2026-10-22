package dev.guildy.scroll;

import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.ThreatRules;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one cast may do to one target: diminishing returns of repeated hits (continuous spells weigh a fraction of a hit per
 * tick), the per-cast cap against bosses (none against ordinary monsters), a fresh tally per cast; and, with the real numbers
 * of the spells and of the cursed Archimage déchu at threat X, what one cast does to a single boss before and after.
 */
class CastDamageTest {
	private static final double EPS = 1.0E-9;

	@Test
	void eachFurtherHitDealsLess() {
		assertEquals(1.0, CastDamage.factor(0.75, 0.2, 0), EPS, "the first hit is whole");
		assertEquals(0.75, CastDamage.factor(0.75, 0.2, 1), EPS);
		assertEquals(0.5625, CastDamage.factor(0.75, 0.2, 2), EPS);
		assertEquals(0.2, CastDamage.factor(0.75, 0.2, 10), EPS, "never below the floor");
		assertEquals(1.0, CastDamage.factor(1.0, 0.2, 10), EPS, "decay 1 = no diminishing return");
		assertEquals(0.0, CastDamage.factor(0.0, 0.0, 1), EPS);
		CastDamage cast = new CastDamage(0.75, 0.2, 0.04);
		UUID zombie = UUID.randomUUID();
		assertEquals(28.0, cast.take(zombie, false, 20, 28, 1), EPS);
		assertEquals(21.0, cast.take(zombie, false, 20, 28, 1), EPS);
		assertEquals(15.75, cast.take(zombie, false, 20, 28, 1), EPS);
		assertEquals(3.0, cast.hits(zombie), EPS);
		assertEquals(64.75, cast.dealt(zombie), EPS);
		// Another target of the same cast starts whole.
		assertEquals(28.0, cast.take(UUID.randomUUID(), false, 20, 28, 1), EPS);
		// A new cast starts whole on the same target.
		assertEquals(28.0, new CastDamage(0.75, 0.2, 0.04).take(zombie, false, 20, 28, 1), EPS);
	}

	@Test
	void continuousSpellsWeighAFractionOfAHitPerTick() {
		assertEquals(0.2, CastDamage.continuous(20), EPS, "a blizzard or aegis pulse (1 s)");
		assertEquals(0.04, CastDamage.continuous(4), EPS, "a tick of the arcane ray (0.2 s)");
		CastDamage cast = new CastDamage(0.75, 0.2, 1);
		UUID husk = UUID.randomUUID();
		double total = 0;
		for (int pulse = 0; pulse < 9; pulse++) {
			total += cast.take(husk, false, 1024, 7, CastDamage.continuous(20));
		}
		// The 9 pulses of a blizzard scroll: 63 before, about 50 after (−20 %), against −80 % for 9 meteors.
		assertTrue(total > 48 && total < 53, "blizzard scroll on one monster: " + total);
		assertEquals(1.8, cast.hits(husk), 1.0E-6, "9 pulses = 1.8 hits");
	}

	@Test
	void aBossLosesAtMostTheCapOfItsMaxHealthToOneCast() {
		CastDamage cast = new CastDamage(0.75, 0.2, 0.07);
		UUID boss = UUID.randomUUID();
		assertEquals(49.0, cast.take(boss, true, 1024, 49, 1), EPS, "under the cap: whole");
		double room = 0.07 * 1024 - 49;
		assertEquals(room, cast.room(boss, true, 1024), 1.0E-6);
		assertEquals(room, cast.take(boss, true, 1024, 49, 1), 1.0E-6, "the second meteor stops at the cap (36.75 > 22.68)");
		assertEquals(0.0, cast.take(boss, true, 1024, 49, 1), EPS, "nothing more from this cast");
		assertEquals(0.07 * 1024, cast.dealt(boss), 1.0E-6);
		// The same hits on an ordinary monster: no cap, only the diminishing return.
		UUID tough = UUID.randomUUID();
		cast.take(tough, false, 1024, 49, 1);
		cast.take(tough, false, 1024, 49, 1);
		assertEquals(49 * 0.5625, cast.take(tough, false, 1024, 49, 1), EPS);
		assertEquals(Double.POSITIVE_INFINITY, cast.room(tough, false, 1024));
		// A cap of 1 (or more) is no cap; a new cast has its whole cap again.
		assertEquals(Double.POSITIVE_INFINITY, CastDamage.room(1.0, 1024, 5000));
		assertEquals(0.07 * 1024, new CastDamage(0.75, 0.2, 0.07).room(boss, true, 1024), 1.0E-6);
		// What the meteors still falling would deal, nothing recorded.
		CastDamage fresh = new CastDamage(0.75, 0.2, 0.07);
		assertEquals(49 + 0.07 * 1024 - 49, fresh.expected(boss, true, 1024, 49, 5), 1.0E-6);
		assertEquals(49 + 36.75 + 27.5625, fresh.expected(tough, false, 1024, 49, 3), 1.0E-6);
		assertEquals(0.0, fresh.dealt(boss), EPS);
	}

	@Test
	void failuresCarryTheirArguments() {
		String failure = SpellFailure.of("scroll.fail.rally.near", 2, 8);
		assertEquals("scroll.fail.rally.near", SpellFailure.key(failure));
		assertArrayEquals(new Object[] {"2", "8"}, SpellFailure.args(failure));
		String grimoire = SpellFailure.rekey(failure, "grimoire.fail.rally.near");
		assertEquals("grimoire.fail.rally.near", SpellFailure.key(grimoire));
		assertArrayEquals(new Object[] {"2", "8"}, SpellFailure.args(grimoire));
		assertEquals("scroll.fail.no_target", SpellFailure.of("scroll.fail.no_target"), "a plain key stays a plain key");
		assertEquals(0, SpellFailure.args("scroll.fail.no_target").length);
		assertEquals("grimoire.fail.no_target", SpellFailure.rekey("scroll.fail.no_target", "grimoire.fail.no_target"));
	}

	// ================================================================== the real numbers

	/** Max health of the cursed Archimage déchu at threat X: 260 x 2.8 x 3 = 2184, held at 1024 by the vanilla attribute. */
	static double cursedArchmageThreatX() {
		RuinConfig ruins = new RuinConfig();
		RuinConfig.RuinDef def = ruins.ruins.get("tour_du_mage");
		double threat = ThreatRules.multiplier(ruins.escalation.healthPerLevel, 10);
		return Math.min(1024, def.boss.health * threat * ruins.cursed.guardian.healthMultiplier);
	}

	/** One meteor shower whose 2 meteors out of 3 fall on the boss (the others missing it), through a cast's tally. */
	private static double[] meteorShower(ScrollConfig.ScrollDef def, SpellPower power, double cap, double maxHealth) {
		Spell spell = def.spell();
		int count = (int) Math.round(power.apply(spell.param("count"), def.param("count")));
		double damage = power.apply(spell.param("damage"), def.param("damage"));
		CastDamage cast = new CastDamage(0.75, 0.2, cap);
		UUID boss = UUID.randomUUID();
		double before = 0;
		for (int i = 0; i < count; i++) {
			if (MeteorRules.aimed(i)) {
				before += damage;
				cast.take(boss, true, maxHealth, damage, 1);
			}
		}
		return new double[] {before, cast.dealt(boss)};
	}

	@Test
	void oneMeteorShowerOnTheCursedArchmageAtThreatX() {
		ScrollConfig cfg = new ScrollConfig();
		cfg.rebuild(id -> true);
		ScrollConfig.ScrollDef def = cfg.scroll("pluie_de_meteores");
		double max = cursedArchmageThreatX();
		assertEquals(1024, max, 1.0E-6, "the vanilla max health attribute holds it at 1024");
		double[] cursed = meteorShower(def, cfg.cursedGrimoire.power(Spell.METEOR_SHOWER), cfg.cursedGrimoire.bossCastCap, max);
		double[] scroll = meteorShower(def, SpellPower.NONE, cfg.limits.bossCastCap, max);
		// Before: 17 homing meteors of 49 = 833 HP (81 % of its health) for the cursed grimoire, 7 x 28 = 196 (19 %) for a scroll.
		assertEquals(17 * 49.0, cursed[0], 1.0E-6);
		assertEquals(7 * 28.0, scroll[0], 1.0E-6);
		// After: 7 % and 4 % of its max health.
		assertEquals(0.07, cursed[1] / max, 1.0E-6);
		assertEquals(0.04, scroll[1] / max, 1.0E-6);
		assertTrue(4 * cursed[1] / max < 0.34, "4 cursed showers leave it in its first phase");
	}
}
