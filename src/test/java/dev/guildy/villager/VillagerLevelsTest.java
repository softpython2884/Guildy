package dev.guildy.villager;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Villager formulas at guild levels past 10: unchanged up to 10, slower and bounded past it. */
class VillagerLevelsTest {
	private static final double EPS = 1e-9;

	private static VillagerConfig config() {
		VillagerConfig config = new VillagerConfig();
		config.validate();
		return config;
	}

	@Test
	void villagerPlacesStopGrowingPerLevelPastTen() {
		VillagerConfig config = config();
		assertEquals(2, config.maxVillagers(1));
		assertEquals(11, config.maxVillagers(10), "unchanged up to level 10");
		assertEquals(11, config.maxVillagers(100), "past 10 the places come from the level milestones (config.json5)");
		config.arrivals.maxVillagersPerLevelAbove10 = 0.1;
		assertEquals(11 + 9, config.maxVillagers(100), "0.1 = one every 10 levels");
	}

	@Test
	void guardDamagePastTenIsSlowAndCapped() {
		VillagerConfig config = config();
		assertEquals(4.0, config.guardDamage(1), EPS);
		assertEquals(8.5, config.guardDamage(10), EPS, "unchanged up to level 10");
		assertEquals(9.0, config.guardDamage(20), EPS);
		assertEquals(13.0, config.guardDamage(100), EPS);
		assertEquals(13.0, config.guardDamage(500), EPS, "capped");
	}

	@Test
	void villagerHealthPastTenIsSlowAndCapped() {
		VitalityConfig vitality = config().vitality;
		assertEquals(36.0, vitality.bonusHealth(10, false), EPS, "unchanged up to level 10");
		assertEquals(36.0 + 45.0, vitality.bonusHealth(100, false), EPS);
		assertEquals(10.0 + 81.0 * 1.5, vitality.bonusHealth(100, true), EPS);
		assertEquals(vitality.maxBonus, vitality.bonusHealth(500, true), EPS, "maxBonus still caps the whole bonus");
		for (int level = 2; level <= 500; level++) {
			assertTrue(vitality.bonusHealth(level, false) >= vitality.bonusHealth(level - 1, false), "never decreases: " + level);
		}
	}

	@Test
	void guardEquipmentKeepsImprovingPastTen() {
		GuardGearConfig gear = config().guardGear;
		assertEquals("minecraft:diamond", gear.forLevel(19).armor);
		assertEquals(3, gear.forLevel(20).sharpness);
		assertEquals("minecraft:netherite", gear.forLevel(30).armor);
		assertEquals(1, gear.forLevel(50).multishot);
		assertEquals(gear.forLevel(50), gear.forLevel(500), "the last step above the table");
	}

	@Test
	void oldUntouchedGuardTableReceivesTheHighSteps() {
		VillagerConfig old = Json5.fromString("{ \"configVersion\": 6 }", VillagerConfig.class);
		old.guardGear.levels = GuardGearConfig.classicLevels();
		old.validate();
		assertEquals(GuardGearConfig.defaultLevels().size(), old.guardGear.levels.size());
		assertEquals(50, old.guardGear.levels.getLast().level);

		VillagerConfig edited = Json5.fromString("{ \"configVersion\": 6 }", VillagerConfig.class);
		List<GuardGearConfig.GearLevel> custom = new ArrayList<>(GuardGearConfig.classicLevels());
		custom.getFirst().sword = "minecraft:stone_sword";
		edited.guardGear.levels = custom;
		edited.validate();
		assertEquals(10, edited.guardGear.levels.size(), "an edited table is kept as it is");

		VillagerConfig current = Json5.fromString("{ \"configVersion\": " + VillagerConfig.CURRENT_VERSION + " }", VillagerConfig.class);
		current.guardGear.levels = GuardGearConfig.classicLevels();
		current.validate();
		assertEquals(10, current.guardGear.levels.size(), "an admin who removed the high steps keeps that choice");
	}
}
