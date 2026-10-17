package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeRulesTest {

	@Test
	void repairStepGivesBackAPercentOfTheMaxDurability() {
		// Iron chestplate: 240 durability, 10 % = 24 per step.
		assertEquals(24, ForgeRules.repairAmount(240, 10));
		assertEquals(206, ForgeRules.damageAfterRepair(230, 240, 10));
		assertEquals(0, ForgeRules.damageAfterRepair(20, 240, 10), "never below 0");
		// Rounded up, at least one point.
		assertEquals(20, ForgeRules.repairAmount(195, 10), "19.5 → 20");
		assertEquals(1, ForgeRules.repairAmount(5, 1));
		assertEquals(0, ForgeRules.repairAmount(0, 10), "no durability: nothing to give back");
		// Out of range percentages are clamped.
		assertEquals(240, ForgeRules.repairAmount(240, 500));
		assertEquals(3, ForgeRules.repairAmount(240, 0), "at least 1 %");
	}

	@Test
	void stepsToRepair() {
		assertEquals(10, ForgeRules.stepsToRepair(230, 240, 10));
		assertEquals(1, ForgeRules.stepsToRepair(1, 240, 10));
		assertEquals(0, ForgeRules.stepsToRepair(0, 240, 10));
		assertEquals(1, ForgeRules.stepsToRepair(240, 240, 100));
	}

	@Test
	void durabilityShownInPercent() {
		assertEquals(100, ForgeRules.durabilityPercent(0, 240));
		assertEquals(99, ForgeRules.durabilityPercent(1, 240), "a damaged item never shows 100 %");
		assertEquals(4, ForgeRules.durabilityPercent(230, 240));
		assertEquals(0, ForgeRules.durabilityPercent(240, 240));
		assertEquals(1.0, ForgeRules.durabilityRatio(0, 0), "no durability = nothing to repair");
	}

	@Test
	void mostDamagedFirst() {
		// Lowest durability left first, not the biggest damage: 150/240 left beats 300/363 left.
		assertEquals(1, ForgeRules.mostDamaged(new int[]{63, 90, 0}, new int[]{363, 240, 0}));
		// Same ratio: the bigger damage.
		assertEquals(0, ForgeRules.mostDamaged(new int[]{200, 100}, new int[]{400, 200}));
		// Same ratio and damage: the first slot.
		assertEquals(0, ForgeRules.mostDamaged(new int[]{50, 50}, new int[]{100, 100}));
		// Empty slots / undamaged / unbreakable (max 0) items are skipped.
		assertEquals(-1, ForgeRules.mostDamaged(new int[]{0, 0, 0}, new int[]{240, 0, 100}));
		assertEquals(2, ForgeRules.mostDamaged(new int[]{0, 7, 1}, new int[]{240, 0, 100}));
		assertEquals(-1, ForgeRules.mostDamaged(new int[0], new int[0]));
	}

	@Test
	void anvilWearsInTheVanillaOrder() {
		assertEquals(ForgeRules.AnvilStage.CHIPPED, ForgeRules.AnvilStage.INTACT.next());
		assertEquals(ForgeRules.AnvilStage.DAMAGED, ForgeRules.AnvilStage.CHIPPED.next());
		assertEquals(ForgeRules.AnvilStage.BROKEN, ForgeRules.AnvilStage.DAMAGED.next());
		assertEquals(ForgeRules.AnvilStage.BROKEN, ForgeRules.AnvilStage.BROKEN.next());
		// Three wearing uses destroy a new anvil.
		ForgeRules.AnvilStage stage = ForgeRules.AnvilStage.INTACT;
		for (int i = 0; i < 3; i++) {
			stage = stage.next();
		}
		assertEquals(ForgeRules.AnvilStage.BROKEN, stage);
	}

	@Test
	void wearChanceLikeVanilla() {
		assertTrue(ForgeRules.wears(0.11, 0.12));
		assertFalse(ForgeRules.wears(0.12, 0.12));
		assertFalse(ForgeRules.wears(0.0, 0.0), "chance 0: never");
		assertTrue(ForgeRules.wears(0.9999, 1.0), "chance 1: always");
	}

	@Test
	void configDefaultsAndBounds() {
		BlacksmithConfig cfg = new BlacksmithConfig();
		cfg.validate();
		assertEquals(3, cfg.minGuildLevel);
		assertEquals(12, cfg.anvilRadius);
		assertEquals(8, cfg.secondsPerRepair);
		assertEquals(10, cfg.repairPercent);
		assertEquals(0.12, cfg.anvilDamageChance, 1e-9);
		cfg.minGuildLevel = 0;
		cfg.anvilRadius = 99;
		cfg.secondsPerRepair = 0;
		cfg.repairPercent = 1000;
		cfg.anvilDamageChance = Double.NaN;
		cfg.scanIntervalSeconds = 1;
		cfg.validate();
		assertEquals(1, cfg.minGuildLevel);
		assertEquals(32, cfg.anvilRadius);
		assertEquals(1, cfg.secondsPerRepair);
		assertEquals(100, cfg.repairPercent);
		assertEquals(0.12, cfg.anvilDamageChance, 1e-9);
		assertEquals(5, cfg.scanIntervalSeconds);
		cfg.anvilDamageChance = 3;
		cfg.validate();
		assertEquals(1.0, cfg.anvilDamageChance, 1e-9);
	}

	@Test
	void missingSectionsGetTheirDefaults() {
		VillagerConfig config = new VillagerConfig();
		config.blacksmith = null;
		config.alchemist = null;
		config.validate();
		assertEquals(3, config.blacksmith.minGuildLevel);
		assertEquals(5, config.alchemist.minGuildLevel);
	}
}
