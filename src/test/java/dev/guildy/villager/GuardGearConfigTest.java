package dev.guildy.villager;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardGearConfigTest {
	private static final List<String> SWORDS = List.of("minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:iron_sword",
			"minecraft:diamond_sword", "minecraft:netherite_sword");
	private static final List<String> ARMORS = List.of("", "minecraft:leather", "minecraft:chainmail", "minecraft:iron", "minecraft:diamond");

	@Test
	void defaultTableImprovesWithTheGuildLevel() {
		GuardGearConfig gear = new GuardGearConfig();
		gear.validate();
		GuardGearConfig.GearLevel previous = null;
		for (int level = 1; level <= 10; level++) {
			GuardGearConfig.GearLevel step = gear.forLevel(level);
			assertNotNull(step, "a step for level " + level);
			assertTrue(SWORDS.contains(step.sword), step.sword);
			assertTrue(ARMORS.contains(step.armor), step.armor);
			if (previous != null) {
				assertTrue(SWORDS.indexOf(step.sword) >= SWORDS.indexOf(previous.sword), "the sword never gets worse at level " + level);
				assertTrue(ARMORS.indexOf(step.armor) >= ARMORS.indexOf(previous.armor), "the armor never gets worse at level " + level);
			}
			previous = step;
		}
	}

	@Test
	void defaultTableMilestones() {
		GuardGearConfig gear = new GuardGearConfig();
		gear.validate();
		GuardGearConfig.GearLevel first = gear.forLevel(1);
		assertEquals("minecraft:wooden_sword", first.sword);
		assertEquals("minecraft:bow", first.ranged);
		assertEquals("", first.armor, "no armor at level 1");
		assertEquals(2, gear.forLevel(5).power, "bow with Power II at level 5");
		assertEquals("minecraft:iron_sword", gear.forLevel(5).sword);
		assertTrue(gear.forLevel(6).crossbow(), "archers switch to a crossbow at level 6");
		assertTrue(gear.forLevel(6).quickCharge > 0);
		assertEquals("minecraft:iron", gear.forLevel(6).armor);
		GuardGearConfig.GearLevel top = gear.forLevel(10);
		assertEquals("minecraft:netherite_sword", top.sword);
		assertEquals("minecraft:diamond", top.armor);
		assertTrue(top.crossbow() && top.piercing > 0);
		assertEquals(top, gear.forLevel(19), "between two steps: the lower one");
	}

	@Test
	void lookupPicksTheHighestReachedStep() {
		GuardGearConfig gear = new GuardGearConfig();
		gear.levels = new ArrayList<>(List.of(step(7, "minecraft:diamond_sword"), step(3, "minecraft:iron_sword")));
		gear.validate();
		assertNull(gear.forLevel(2), "below every step: no base equipment");
		assertEquals("minecraft:iron_sword", gear.forLevel(3).sword);
		assertEquals("minecraft:iron_sword", gear.forLevel(6).sword);
		assertEquals("minecraft:diamond_sword", gear.forLevel(7).sword);
		assertEquals(3, gear.levels.getFirst().level, "sorted by level");
	}

	@Test
	void disabledMeansNoBaseEquipment() {
		GuardGearConfig gear = new GuardGearConfig();
		gear.enabled = false;
		gear.validate();
		assertNull(gear.forLevel(10));
	}

	@Test
	void validateCleansTheTable() {
		GuardGearConfig gear = new GuardGearConfig();
		GuardGearConfig.GearLevel broken = step(-4, "  minecraft:iron_sword ");
		broken.ranged = null;
		broken.armor = null;
		broken.sharpness = 50;
		broken.multishot = 3;
		gear.levels = new ArrayList<>();
		gear.levels.add(null);
		gear.levels.add(broken);
		gear.crossbowDamageFactor = Double.NaN;
		gear.quickChargeTicks = 500;
		gear.validate();
		assertEquals(1, gear.levels.size(), "null entries removed");
		GuardGearConfig.GearLevel fixed = gear.levels.getFirst();
		assertEquals(1, fixed.level);
		assertEquals("minecraft:iron_sword", fixed.sword);
		assertEquals("minecraft:bow", fixed.ranged);
		assertEquals("", fixed.armor);
		assertEquals(5, fixed.sharpness);
		assertEquals(1, fixed.multishot);
		assertEquals(1.0, gear.crossbowDamageFactor, 1e-9);
		assertEquals(20, gear.quickChargeTicks);

		GuardGearConfig missing = new GuardGearConfig();
		missing.levels = null;
		missing.validate();
		assertEquals(GuardGearConfig.defaultLevels().size(), missing.levels.size(), "missing table: the defaults");
	}

	@Test
	void quickChargeShortensTheCrossbowDelay() {
		GuardGearConfig gear = new GuardGearConfig();
		assertEquals(30, gear.crossbowCooldown(30, 0));
		assertEquals(15, gear.crossbowCooldown(30, 3));
		assertEquals(5, gear.crossbowCooldown(10, 5), "never below 5 ticks");
	}

	@Test
	void crossbowScoresRankLikeTheirEnchantments() {
		double plain = GuardRules.crossbowScore(0, 0, 0);
		assertEquals(GuardRules.bowScore(1, 0, 0), plain, 1e-9, "a plain crossbow is worth a Power I bow");
		assertTrue(GuardRules.betterWeapon(GuardRules.crossbowScore(1, 0, 0), plain), "Quick Charge");
		assertTrue(GuardRules.betterWeapon(GuardRules.crossbowScore(1, 1, 0), GuardRules.crossbowScore(1, 0, 0)), "Piercing");
		assertTrue(GuardRules.betterWeapon(GuardRules.crossbowScore(0, 0, 1), plain), "Multishot");
		assertFalse(GuardRules.betterWeapon(plain, GuardRules.bowScore(2, 0, 0)), "a Power II bow beats a plain crossbow");
	}

	@Test
	void woodenSwordIsTheDamageBaseline() {
		// Compile-time constant: inlined, GuardEquipment itself is not loaded.
		assertEquals(0.0, GuardRules.swordBonus(4.0, GuardEquipment.BASELINE_SWORD_DAMAGE), 1e-9);
		assertEquals(2.0, GuardRules.swordBonus(6.0, GuardEquipment.BASELINE_SWORD_DAMAGE), 1e-9, "iron sword");
		assertEquals(4.0, GuardRules.swordBonus(8.0, GuardEquipment.BASELINE_SWORD_DAMAGE), 1e-9, "netherite sword");
	}

	@Test
	void newSectionsSurviveAWriteAndARead(@TempDir Path dir) throws Exception {
		VillagerConfig config = new VillagerConfig();
		config.guardGear.levels.getFirst().sword = "minecraft:stone_sword";
		config.guardGear.levels.getLast().multishot = 1;
		config.vitality.healthPerLevel = 6.0;
		config.night.homeTime = 10500;
		config.validate();
		Path file = dir.resolve("villagers.json5");
		Json5.write(file, config);
		String text = Files.readString(file);
		assertTrue(text.contains("\"guardGear\"") && text.contains("\"vitality\"") && text.contains("\"night\""), "sections written");
		assertTrue(text.contains("// Equipement de base des gardes"), "commented");
		VillagerConfig read = Json5.read(file, VillagerConfig.class);
		read.validate();
		assertEquals("minecraft:stone_sword", read.guardGear.levels.getFirst().sword);
		assertEquals(1, read.guardGear.levels.getLast().multishot);
		assertEquals(GuardGearConfig.defaultLevels().size(), read.guardGear.levels.size());
		assertEquals(6.0, read.vitality.healthPerLevel, 1e-9);
		assertEquals(10500, read.night.homeTime);
	}

	@Test
	void olderFilesGetTheNewSectionsWithoutMigration() {
		VillagerConfig old = Json5.fromString("{ \"configVersion\": 6, \"guard\": { \"damage\": 5.0, \"passiveRegenPerSecond\": 0.3 } }",
				VillagerConfig.class);
		old.validate();
		assertEquals(5.0, old.guard.damage, 1e-9, "existing values untouched");
		assertEquals(0.3, old.guard.passiveRegenPerSecond, 1e-9, "guards keep their own regeneration setting");
		assertEquals(new VitalityConfig().healthPerLevel, old.vitality.healthPerLevel, 1e-9);
		assertEquals(new NightConfig().homeTime, old.night.homeTime);
		assertEquals("minecraft:netherite_sword", old.guardGear.forLevel(10).sword);
		VillagerConfig nulls = Json5.fromString("{ \"guardGear\": null, \"vitality\": null, \"night\": null }", VillagerConfig.class);
		nulls.validate();
		assertNotNull(nulls.guardGear.forLevel(1));
		assertNotNull(nulls.vitality);
		assertNotNull(nulls.night);
	}

	private static GuardGearConfig.GearLevel step(int level, String sword) {
		GuardGearConfig.GearLevel step = new GuardGearConfig.GearLevel();
		step.level = level;
		step.sword = sword;
		return step;
	}
}
