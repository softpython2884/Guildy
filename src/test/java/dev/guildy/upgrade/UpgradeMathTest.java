package dev.guildy.upgrade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeMathTest {

	@Test
	void boostRoundsToTheNearestInteger() {
		assertEquals(125L, UpgradeMath.boost(100L, 25));
		assertEquals(8L, UpgradeMath.boost(7L, 20), "8.4 -> 8");
		assertEquals(18L, UpgradeMath.boost(14L, 25), "17.5 -> 18");
		assertEquals(150, UpgradeMath.boost(100, 50.0));
		assertEquals(0L, UpgradeMath.boost(0L, 50));
		assertEquals(10L, UpgradeMath.boost(10L, -30), "negative bonus ignored");
		assertEquals(10L, UpgradeMath.boost(10L, Double.NaN));
		assertEquals(6.0, UpgradeMath.boost(4.0, 50), 1e-9);
		assertEquals(Integer.MAX_VALUE, UpgradeMath.boost(Integer.MAX_VALUE, 100));
	}

	@Test
	void reduceRoundsDownAndNeverGoesNegative() {
		assertEquals(12L, UpgradeMath.reduce(25L, 50), "12.5 -> 12 (in the guild's favour)");
		assertEquals(0L, UpgradeMath.reduce(25L, 150));
		assertEquals(25L, UpgradeMath.reduce(25L, -10));
		assertEquals(0L, UpgradeMath.reduce(0L, 50));
		assertEquals(0L, UpgradeMath.reduce(-3L, 0));
		assertEquals(7L, UpgradeMath.reduce(10L, 30), "no floating point surprise");
		assertEquals(0.1, UpgradeMath.reduce(0.2, 50), 1e-9);
		assertEquals(0.0, UpgradeMath.reduce(0.2, 100), 1e-9);
		assertEquals(12.5, UpgradeMath.reduce(25.0, 50), 1e-9);
	}

	@Test
	void discountRoundsUpAndKeepsAtLeastOneGold() {
		assertEquals(6, UpgradeMath.discount(8, 30), "5.6 -> 6");
		assertEquals(7, UpgradeMath.discount(10, 30), "exactly 7, not 8");
		assertEquals(1, UpgradeMath.discount(1, 30));
		assertEquals(1, UpgradeMath.discount(64, 100), "never free");
		assertEquals(0, UpgradeMath.discount(0, 30), "free stays free");
		assertEquals(20, UpgradeMath.discount(20, 0));
		for (int price = 1; price <= 200; price++) {
			int discounted = UpgradeMath.discount(price, 30);
			assertTrue(discounted >= 1 && discounted <= price, "price " + price);
			assertTrue(discounted >= price * 0.7 - 1e-9 && discounted < price * 0.7 + 1, "rounded up: price " + price);
		}
	}

	@Test
	void workDurationsAreCappedAt75Percent() {
		assertEquals(84, UpgradeMath.workSeconds(120, 30));
		assertEquals(7, UpgradeMath.workSeconds(10, 30));
		assertEquals(30, UpgradeMath.workSeconds(120, 90), "capped at -75 %");
		assertEquals(120, UpgradeMath.workSeconds(120, 0));
		assertEquals(1, UpgradeMath.workSeconds(1, 75));
		assertEquals(1, UpgradeMath.workSeconds(0, 30));
		assertEquals(1.0, UpgradeMath.workRate(0), 1e-9);
		assertEquals(1 / 0.7, UpgradeMath.workRate(30), 1e-9);
		assertEquals(4.0, UpgradeMath.workRate(75), 1e-9);
		assertEquals(4.0, UpgradeMath.workRate(500), 1e-9);
	}

	@Test
	void deliveryGoldIsNeverBoosted() {
		assertEquals(12L, UpgradeMath.questGold(10, 20, false));
		assertEquals(10L, UpgradeMath.questGold(10, 20, true));
		assertEquals(10L, UpgradeMath.questGold(10, 0, false));
	}

	@Test
	void effectsFormatting() {
		assertEquals("25", UpgradeEffects.format(25.0));
		assertEquals("12,5", UpgradeEffects.format(12.5));
		assertEquals("0", UpgradeEffects.format(0));
		assertEquals("I", UpgradeEffects.displayValue(UpgradeEffects.TERRITORY_HASTE, 1));
		assertEquals("II", UpgradeEffects.displayValue(UpgradeEffects.WAVE_REGEN, 2.7));
		assertEquals("30", UpgradeEffects.displayValue(UpgradeEffects.WORK_SPEED_PCT, 30));
		assertEquals("12", UpgradeEffects.roman(12));
		assertEquals(75, UpgradeEffects.effective(UpgradeEffects.WORK_SPEED_PCT, 120));
		assertEquals(100, UpgradeEffects.effective(UpgradeEffects.VILLAGER_LOYALTY_PCT, 200));
		assertEquals(UpgradeEffects.MAX_EFFECT_LEVEL, UpgradeEffects.effective(UpgradeEffects.TERRITORY_HASTE, 9));
		assertEquals(1, UpgradeEffects.effective(UpgradeEffects.WAVE_REGEN, 1.5));
		assertEquals(40, UpgradeEffects.effective(UpgradeEffects.EXTRA_CHUNKS, 40));
		assertEquals(0, UpgradeEffects.effective(UpgradeEffects.EXTRA_CHUNKS, -3));
		assertTrue(UpgradeEffects.isKnown(UpgradeEffects.EXTRA_CHUNKS));
		assertFalse(UpgradeEffects.isKnown("teleportation"));
		assertEquals(12, UpgradeEffects.ALL.size());
	}
}
