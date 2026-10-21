package dev.guildy.alchemy;

import dev.guildy.scroll.Spell;
import dev.guildy.scroll.SpellPower;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Magic boosts of the elixirs: sums and caps, food cost, channel and cooldown, refresh, and their effect on a cast. */
class MagicBoostRulesTest {
	private static final double EPS = 1.0E-9;
	private static final MagicBoostRules.Boost ARCANE = new MagicBoostRules.Boost(0.25, 0, 0, 0);
	private static final MagicBoostRules.Boost SIGHT = new MagicBoostRules.Boost(0, 0.3, 0.3, 0);
	private static final MagicBoostRules.Boost FULL = new MagicBoostRules.Boost(0, 0, 0, 0.5);

	@Test
	void bonusesAddUpWithinTheirCaps() {
		MagicBoostRules.Caps caps = MagicBoostRules.Caps.DEFAULT;
		assertEquals(1.0, MagicBoostRules.powerMultiplier(List.of(), caps), "no elixir: unchanged");
		assertEquals(1.25, MagicBoostRules.powerMultiplier(List.of(ARCANE, SIGHT), caps), EPS);
		assertEquals(1.5, MagicBoostRules.powerMultiplier(List.of(ARCANE, ARCANE), caps), EPS, "two elixirs add up");
		assertEquals(2.0, MagicBoostRules.powerMultiplier(List.of(ARCANE, ARCANE, ARCANE, ARCANE, ARCANE), caps), EPS, "capped at +100 %");
		assertEquals(0.7, MagicBoostRules.cooldownMultiplier(List.of(SIGHT), caps), EPS);
		assertEquals(0.25, MagicBoostRules.cooldownMultiplier(List.of(SIGHT, SIGHT, SIGHT), caps), EPS, "never below -75 %");
		assertEquals(0.7, MagicBoostRules.channelMultiplier(List.of(SIGHT, ARCANE), caps), EPS);
		assertEquals(0.5, MagicBoostRules.foodMultiplier(List.of(FULL), caps), EPS);
		assertEquals(0.0, MagicBoostRules.foodMultiplier(List.of(FULL, FULL, FULL), caps), EPS, "free at most");
		MagicBoostRules.Caps strict = new MagicBoostRules.Caps(0.1, 0.1, 0, 0);
		assertEquals(1.1, MagicBoostRules.powerMultiplier(List.of(ARCANE), strict), EPS);
		assertEquals(1.0, MagicBoostRules.channelMultiplier(List.of(SIGHT), strict), EPS, "cap 0: no reduction");
		assertTrue(new MagicBoostRules.Boost(Double.NaN, -1, 7, 0).channel() == 1.0, "fractions bounded");
		assertTrue(!MagicBoostRules.Boost.NONE.any() && ARCANE.any());
	}

	@Test
	void foodChannelAndCooldown() {
		assertEquals(2, MagicBoostRules.foodCost(3, 0.5), "rounded up");
		assertEquals(2, MagicBoostRules.foodCost(4, 0.5));
		assertEquals(1, MagicBoostRules.foodCost(1, 0.5), "a cast is free only when fully free");
		assertEquals(0, MagicBoostRules.foodCost(3, 0.0));
		assertEquals(3, MagicBoostRules.foodCost(3, 1.0));
		assertEquals(0, MagicBoostRules.foodCost(0, 0.5));
		assertEquals(28, MagicBoostRules.channelTicks(40, 0.7));
		assertEquals(MagicBoostRules.MIN_CHANNEL_TICKS, MagicBoostRules.channelTicks(40, 0.01), "the incantation stays visible");
		assertEquals(3, MagicBoostRules.channelTicks(3, 0.5), "already short: unchanged");
		assertEquals(40, MagicBoostRules.channelTicks(40, 1.5), "never longer");
		assertEquals(420, MagicBoostRules.cooldownTicks(30, 0.7));
		assertEquals(1, MagicBoostRules.cooldownTicks(0, 0.7), "at least one tick");
		assertEquals(600, MagicBoostRules.cooldownTicks(30, Double.NaN));
	}

	@Test
	void drinkingAgainRefreshes() {
		assertEquals(6000, MagicBoostRules.refresh(1200, 6000));
		assertEquals(6000, MagicBoostRules.refresh(6000, 1200), "never shorter");
		assertEquals(6000, MagicBoostRules.refresh(-5, 6000));
		assertEquals("5:00", MagicBoostRules.clock(6000));
		assertEquals("4:05", MagicBoostRules.clock(4881), "rounded up to the second");
		assertEquals("0:00", MagicBoostRules.clock(0));
		assertEquals(25, MagicBoostRules.percent(0.25));
	}

	@Test
	void anArcaneElixirAmplifiesDamageHealsAndDurationsOnly() {
		Spell meteors = Spell.METEOR_SHOWER;
		Spell.Param damage = meteors.param("damage");
		Spell.Param radius = meteors.param("radius");
		Spell.Param count = meteors.param("count");
		SpellPower scroll = SpellPower.NONE.boosted(1.25, meteors);
		assertEquals(damage.defaultValue() * 1.25, scroll.apply(damage, damage.defaultValue()), EPS, "+25 % damage");
		assertEquals(radius.defaultValue(), scroll.apply(radius, radius.defaultValue()), EPS, "radius unchanged");
		assertEquals(count.defaultValue(), scroll.apply(count, count.defaultValue()), EPS, "meteor count unchanged");
		assertSame(SpellPower.NONE, SpellPower.NONE.boosted(1.0, meteors), "no boost: the same power");
		assertSame(SpellPower.NONE, SpellPower.NONE.boosted(Double.NaN, meteors));
		// On top of a grimoire, and of a cursed override of the damage.
		SpellPower grimoire = SpellPower.of(1.5).boosted(1.25, meteors);
		assertEquals(damage.defaultValue() * 1.5 * 1.25, grimoire.apply(damage, damage.defaultValue()), EPS);
		SpellPower cursed = new SpellPower(2.0, 1.2, 1.5, 1, Map.of("damage", 3.0, "radius", 1.4), true).boosted(1.25, meteors);
		assertEquals(3.75, cursed.multiplier(damage), EPS, "a damage override is boosted too");
		assertEquals(1.4, cursed.multiplier(radius), EPS, "a radius override is not");
		assertTrue(cursed.cursed());
		// Still bounded like any amplified value.
		double huge = SpellPower.of(5).boosted(2.0, meteors).apply(damage, damage.max());
		assertTrue(huge <= damage.max() * SpellPower.POWER_CEILING + EPS);
	}
}
