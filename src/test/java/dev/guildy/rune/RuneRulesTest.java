package dev.guildy.rune;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rune draws: weighted tiers and runes, slot pools, the netherite-chestplate-only rule of the mythic runes, caps. */
class RuneRulesTest {
	private static final String NETHERITE_CHEST = "minecraft:netherite_chestplate";
	private static final String DIAMOND_CHEST = "minecraft:diamond_chestplate";

	private static RuneConfig config() {
		RuneConfig config = new RuneConfig();
		assertTrue(config.clean().isEmpty(), "the default runes are valid");
		return config;
	}

	@Test
	void tierIsDrawnByWeight() {
		Map<RuneTier, Integer> weights = new EnumMap<>(RuneTier.class);
		weights.put(RuneTier.COMMON, 80);
		weights.put(RuneTier.RARE, 18);
		weights.put(RuneTier.MYTHIC, 2);
		assertSame(RuneTier.COMMON, RuneRules.pickTier(weights, 0.0));
		assertSame(RuneTier.COMMON, RuneRules.pickTier(weights, 0.799));
		assertSame(RuneTier.RARE, RuneRules.pickTier(weights, 0.80));
		assertSame(RuneTier.RARE, RuneRules.pickTier(weights, 0.979));
		assertSame(RuneTier.MYTHIC, RuneRules.pickTier(weights, 0.98));
		assertSame(RuneTier.MYTHIC, RuneRules.pickTier(weights, 0.99999));
		assertSame(RuneTier.MYTHIC, RuneRules.pickTier(weights, 1.5), "rolls are clamped below 1");
		assertSame(RuneTier.COMMON, RuneRules.pickTier(weights, -3), "and above 0");
		// A tier without weight is never drawn.
		weights.put(RuneTier.RARE, 0);
		assertSame(RuneTier.COMMON, RuneRules.pickTier(weights, 0.97), "80 common + 2 mythic: 0.97 * 82 = 79.5 is common");
		assertSame(RuneTier.MYTHIC, RuneRules.pickTier(weights, 0.98), "0.98 * 82 = 80.4 is mythic");
		assertNull(RuneRules.pickTier(new EnumMap<>(RuneTier.class), 0.5), "no weight, no tier");
	}

	@Test
	void runeIsDrawnByWeightInsideItsTier() {
		RuneConfig.RuneDef a = new RuneConfig.RuneDef("a", "A", "ᚠ", RuneTier.COMMON, 3, "head");
		RuneConfig.RuneDef b = new RuneConfig.RuneDef("b", "B", "ᚢ", RuneTier.COMMON, 0, "head");
		RuneConfig.RuneDef c = new RuneConfig.RuneDef("c", "C", "ᚦ", RuneTier.COMMON, 1, "head");
		List<RuneConfig.RuneDef> pool = List.of(a, b, c);
		assertSame(a, RuneRules.pickWeighted(pool, 0.0));
		assertSame(a, RuneRules.pickWeighted(pool, 0.74));
		assertSame(c, RuneRules.pickWeighted(pool, 0.75));
		assertSame(c, RuneRules.pickWeighted(pool, 0.999));
		assertNull(RuneRules.pickWeighted(List.of(b), 0.5), "only zero weights");
		assertNull(RuneRules.pickWeighted(List.of(), 0.5));
	}

	@Test
	void slotPoolsFitThePiece() {
		RuneConfig config = config();
		for (String slot : RuneConfig.SLOTS) {
			List<RuneConfig.RuneDef> pool = RuneRules.candidates(config.runes, slot, "minecraft:iron_" + piece(slot), config.mythicItems);
			long commons = pool.stream().filter(def -> def.tier() == RuneTier.COMMON).count();
			long rares = pool.stream().filter(def -> def.tier() == RuneTier.RARE).count();
			assertTrue(commons >= 4, slot + ": several common runes (" + commons + ")");
			assertTrue(rares >= 1, slot + ": at least one rare rune (" + rares + ")");
			for (RuneConfig.RuneDef def : pool) {
				assertTrue(def.allows(slot), def.id + " allowed on " + slot);
				assertTrue(def.tier() != RuneTier.MYTHIC, "no mythic rune on iron " + slot);
			}
		}
		Set<String> feet = ids(RuneRules.candidates(config.runes, "feet", "minecraft:iron_boots", config.mythicItems));
		assertTrue(feet.containsAll(Set.of("celerite", "plume", "ondine", "rebond", "rempart")));
		assertFalse(feet.contains("bastion"), "the +2 armor rune is for the chest and legs");
		assertFalse(feet.contains("trefle"), "the luck rune is for the head");
		Set<String> head = ids(RuneRules.candidates(config.runes, "head", "minecraft:iron_helmet", config.mythicItems));
		assertTrue(head.containsAll(Set.of("trefle", "abysses", "mineur", "lueur")));
		assertFalse(head.contains("celerite"), "the speed rune is for the boots");
		// Every default rune goes somewhere.
		Set<String> placed = new HashSet<>();
		for (String slot : RuneConfig.SLOTS) {
			placed.addAll(ids(RuneRules.candidates(config.runes, slot, slot.equals("chest") ? NETHERITE_CHEST : "minecraft:iron_" + piece(slot),
					config.mythicItems)));
		}
		assertEquals(ids(config.runes), placed);
	}

	@Test
	void mythicRunesOnlyOnNetheriteChestplates() {
		RuneConfig config = config();
		Set<String> netherite = ids(RuneRules.candidates(config.runes, "chest", NETHERITE_CHEST, config.mythicItems));
		assertTrue(netherite.containsAll(Set.of("phenix", "dragon")), "both mythic runes on a netherite chestplate");
		for (String item : List.of(DIAMOND_CHEST, "minecraft:iron_chestplate", "minecraft:elytra", "minecraft:leather_chestplate")) {
			for (RuneConfig.RuneDef def : RuneRules.candidates(config.runes, "chest", item, config.mythicItems)) {
				assertTrue(def.tier() != RuneTier.MYTHIC, def.id + " is never on " + item);
			}
		}
		for (String slot : List.of("head", "legs", "feet")) {
			for (RuneConfig.RuneDef def : RuneRules.candidates(config.runes, slot, "minecraft:netherite_" + piece(slot), config.mythicItems)) {
				assertTrue(def.tier() != RuneTier.MYTHIC, def.id + " is never on netherite " + slot);
			}
		}
		// Whatever the rolls, a diamond chestplate never draws a mythic rune; a netherite chestplate does at the top rolls.
		int mythic = 0;
		for (int i = 0; i < 1000; i++) {
			double roll = i / 1000.0;
			RuneConfig.RuneDef diamond = RuneRules.pick(config, "chest", DIAMOND_CHEST, roll, roll);
			assertNotNull(diamond);
			assertTrue(diamond.tier() != RuneTier.MYTHIC, "diamond chestplate drew " + diamond.id);
			RuneConfig.RuneDef nether = RuneRules.pick(config, "chest", NETHERITE_CHEST, roll, 0.5);
			if (nether.tier() == RuneTier.MYTHIC) {
				mythic++;
			}
		}
		assertEquals(20, mythic, "2 % of the tier rolls give a mythic rune on a netherite chestplate");
		assertEquals("phenix", RuneRules.pick(config, "chest", NETHERITE_CHEST, 0.99, 0.2).id);
		assertEquals("dragon", RuneRules.pick(config, "chest", NETHERITE_CHEST, 0.99, 0.8).id);
		// Tier chances: the mythic weight is dropped for other pieces.
		Map<RuneTier, Double> diamond = RuneRules.tierChances(config, "chest", DIAMOND_CHEST);
		assertEquals(0.0, diamond.get(RuneTier.MYTHIC));
		assertEquals(80 / 98.0, diamond.get(RuneTier.COMMON), 1e-9);
		Map<RuneTier, Double> nether = RuneRules.tierChances(config, "chest", NETHERITE_CHEST);
		assertEquals(0.02, nether.get(RuneTier.MYTHIC), 1e-9);
		// Another mythic item may be configured, but a mythic rune still only goes on the chest slot.
		config.mythicItems.add(DIAMOND_CHEST);
		assertTrue(ids(RuneRules.candidates(config.runes, "chest", DIAMOND_CHEST, config.mythicItems)).contains("dragon"));
		RuneConfig.RuneDef phoenix = config.rune("phenix");
		assertFalse(RuneRules.eligible(phoenix, "head", NETHERITE_CHEST, config.mythicItems));
	}

	@Test
	void disabledOrWeightlessRunesAreNeverDrawn() {
		RuneConfig config = config();
		config.rune("rempart").enabled = false;
		config.rune("acier").weight = 0;
		Set<String> feet = ids(RuneRules.candidates(config.runes, "feet", "minecraft:iron_boots", config.mythicItems));
		assertFalse(feet.contains("rempart"));
		assertFalse(feet.contains("acier"));
		config.tiers.rare = 0;
		for (int i = 0; i < 100; i++) {
			assertSame(RuneTier.COMMON, RuneRules.pick(config, "feet", "minecraft:iron_boots", i / 100.0, 0.5).tier(), "no rare tier");
		}
	}

	@Test
	void chanceCapAndInspections() {
		assertTrue(RuneRules.succeeds(0.0, 0.15));
		assertTrue(RuneRules.succeeds(0.149, 0.15));
		assertFalse(RuneRules.succeeds(0.15, 0.15));
		assertTrue(RuneRules.succeeds(0.9999, 1.0), "chance 1: always");
		assertFalse(RuneRules.succeeds(0.0, 0.0), "chance 0: never");
		// Daily cap per blacksmith.
		assertEquals(2, RuneRules.madeToday(5, 2, 5));
		assertEquals(0, RuneRules.madeToday(4, 2, 5), "a new day starts at 0");
		assertFalse(RuneRules.underCap(5, 2, 5, 2));
		assertTrue(RuneRules.underCap(4, 2, 5, 2));
		assertFalse(RuneRules.underCap(-1, 0, 5, 0), "cap 0: never");
		// Inspections of a stand.
		assertTrue(RuneRules.inspectionDue(-1, 100, 18000), "never inspected");
		assertFalse(RuneRules.inspectionDue(100, 17999, 18000));
		assertTrue(RuneRules.inspectionDue(100, 18100, 18000));
		assertEquals(0, RuneRules.day(23999));
		assertEquals(1, RuneRules.day(24000));
	}

	private static Set<String> ids(List<RuneConfig.RuneDef> defs) {
		Set<String> ids = new HashSet<>();
		for (RuneConfig.RuneDef def : defs) {
			ids.add(def.id);
		}
		return ids;
	}

	private static String piece(String slot) {
		return switch (slot) {
			case "head" -> "helmet";
			case "chest" -> "chestplate";
			case "legs" -> "leggings";
			default -> "boots";
		};
	}
}
