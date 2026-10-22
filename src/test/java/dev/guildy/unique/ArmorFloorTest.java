package dev.guildy.unique;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protection floor of the unique armors: every unique worn in an armor slot reaches at least the netherite piece of that
 * slot (armor, toughness, knockback resistance), higher values kept, nothing added to the other uniques.
 */
class ArmorFloorTest {
	private static final double EPSILON = 1e-9;

	/** Vanilla armor of the base items (ArmorMaterials of 26.3: defense per type, toughness, knockback resistance). */
	private static ArmorFloor.Stats vanilla(String item) {
		String material = item.substring("minecraft:".length(), item.lastIndexOf('_'));
		String type = item.substring(item.lastIndexOf('_') + 1);
		Map<String, int[]> defense = Map.of(
				"leather", new int[] {1, 3, 2, 1},
				"golden", new int[] {2, 5, 3, 1},
				"diamond", new int[] {3, 8, 6, 3},
				"netherite", new int[] {3, 8, 6, 3});
		int index = List.of("helmet", "chestplate", "leggings", "boots").indexOf(type);
		double toughness = switch (material) {
			case "diamond" -> 2;
			case "netherite" -> 3;
			default -> 0;
		};
		return new ArmorFloor.Stats(defense.get(material)[index], toughness, "netherite".equals(material) ? 0.1 : 0);
	}

	private static String slotOf(String item) {
		if (item.endsWith("_helmet")) {
			return "head";
		}
		if (item.endsWith("_chestplate")) {
			return "chest";
		}
		if (item.endsWith("_leggings")) {
			return "legs";
		}
		return item.endsWith("_boots") ? "feet" : null;
	}

	@Test
	void netheriteFloorPerSlot() {
		assertEquals(new ArmorFloor.Stats(3, 3, 0.1), ArmorFloor.floor("netherite", "head"));
		assertEquals(new ArmorFloor.Stats(8, 3, 0.1), ArmorFloor.floor("netherite", "chest"));
		assertEquals(new ArmorFloor.Stats(6, 3, 0.1), ArmorFloor.floor("netherite", "legs"));
		assertEquals(new ArmorFloor.Stats(3, 3, 0.1), ArmorFloor.floor("netherite", "feet"));
		assertNull(ArmorFloor.floor("netherite", "mainhand"), "not an armor slot");
		assertNull(ArmorFloor.floor("netherite", null));
		assertNull(ArmorFloor.floor("none", "feet"), "no floor");
		assertEquals("netherite", ArmorFloor.parse(" Netherite "));
		assertEquals("none", ArmorFloor.parse("NONE"));
		assertNull(ArmorFloor.parse("diamond"));
		assertNull(ArmorFloor.parse(null));
	}

	@Test
	void onlyWhatIsMissingIsAdded() {
		ArmorFloor.Stats floor = ArmorFloor.floor("netherite", "feet");
		assertEquals(new ArmorFloor.Stats(2, 3, 0.1), ArmorFloor.missing(floor, new ArmorFloor.Stats(1, 0, 0)), "leather boots");
		assertTrue(ArmorFloor.missing(floor, new ArmorFloor.Stats(3, 3, 0.1)).isZero(), "netherite boots: nothing");
		assertEquals(new ArmorFloor.Stats(0, 1, 0.1), ArmorFloor.missing(floor, new ArmorFloor.Stats(5, 2, 0)), "a higher armor is kept");
		assertTrue(ArmorFloor.missing(floor, new ArmorFloor.Stats(10, 9, 0.5)).isZero(), "above the floor: nothing");
		assertTrue(ArmorFloor.missing(null, new ArmorFloor.Stats(0, 0, 0)).isZero(), "no floor: nothing");
		assertEquals(0.1, ArmorFloor.missing(floor, new ArmorFloor.Stats(3, 3, 0)).knockback(), EPSILON);
		assertTrue(new ArmorFloor.Stats(3, 3, 0.1).atLeast(floor));
		assertFalse(new ArmorFloor.Stats(3, 2.9, 0.1).atLeast(floor));
	}

	@Test
	void attributesOfTheDefinitionCountForTheirSlot() {
		List<UniqueConfig.AttributeEntry> attributes = List.of(
				new UniqueConfig.AttributeEntry("minecraft:armor", 2, "add_value", "feet"),
				new UniqueConfig.AttributeEntry("minecraft:armor_toughness", 1, "add_value", "armor"),
				new UniqueConfig.AttributeEntry("minecraft:knockback_resistance", 0.05, "add_value", "any"),
				new UniqueConfig.AttributeEntry("minecraft:armor", 5, "add_value", "head"),
				new UniqueConfig.AttributeEntry("minecraft:armor", 0.5, "add_multiplied_base", "feet"),
				new UniqueConfig.AttributeEntry("minecraft:movement_speed", 0.2, "add_multiplied_base", "feet"));
		ArmorFloor.Stats total = ArmorFloor.withAttributes(new ArmorFloor.Stats(1, 0, 0), attributes, "feet");
		assertEquals(new ArmorFloor.Stats(3, 1, 0.05), total, "another slot's and multiplied attributes do not count");
	}

	@Test
	void everyDefaultUniqueArmorReachesNetherite() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.rebuild(id -> true);
		assertEquals("netherite", cfg.armorFloor, "netherite floor by default");
		int armors = 0;
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			String slot = slotOf(def.item);
			if (slot == null) {
				continue;
			}
			armors++;
			ArmorFloor.Stats own = ArmorFloor.withAttributes(vanilla(def.item), def.attributes, slot);
			ArmorFloor.Stats floor = ArmorFloor.floor(cfg.armorFloor, slot);
			ArmorFloor.Stats total = own.plus(ArmorFloor.missing(floor, own));
			assertTrue(total.atLeast(floor), def.id + " protects at least like netherite: " + total);
		}
		assertEquals(16, armors, "4 unique armors + 12 set pieces");
		// Coursier boots (leather): far below, completed; Zéphyr (netherite) and the serpent's chestplate: already there.
		UniqueConfig.UniqueDef coursier = cfg.item("bottes_du_coursier");
		ArmorFloor.Stats coursierOwn = ArmorFloor.withAttributes(vanilla(coursier.item), coursier.attributes, "feet");
		assertEquals(new ArmorFloor.Stats(2, 3, 0.1), ArmorFloor.missing(ArmorFloor.floor("netherite", "feet"), coursierOwn));
		UniqueConfig.UniqueDef zephyr = cfg.item("bottes_de_zephyr");
		assertTrue(ArmorFloor.missing(ArmorFloor.floor("netherite", "feet"),
				ArmorFloor.withAttributes(vanilla(zephyr.item), zephyr.attributes, "feet")).isZero());
		UniqueConfig.UniqueDef serpent = cfg.item("plastron_du_serpent");
		assertTrue(ArmorFloor.missing(ArmorFloor.floor("netherite", "chest"),
				ArmorFloor.withAttributes(vanilla(serpent.item), serpent.attributes, "chest")).isZero(), "+5 armor, +3 toughness, 0.1 knockback");
		UniqueConfig.UniqueDef crown = cfg.item("couronne_du_roi_de_givre");
		assertEquals(new ArmorFloor.Stats(0, 1, 0.1), ArmorFloor.missing(ArmorFloor.floor("netherite", "head"),
				ArmorFloor.withAttributes(vanilla(crown.item), crown.attributes, "head")), "diamond helmet: toughness and knockback");
		UniqueConfig.UniqueDef greaves = cfg.item("greves_brasier_des_ames");
		assertTrue(ArmorFloor.missing(ArmorFloor.floor("netherite", "legs"),
				ArmorFloor.withAttributes(vanilla(greaves.item), greaves.attributes, "legs")).isZero(), "netherite greaves: already above");
	}

	@Test
	void modifierIdsPerSlotAndStat() {
		Set<String> ids = new HashSet<>();
		for (String slot : List.of("head", "chest", "legs", "feet")) {
			for (String stat : List.of("armor", "toughness", "knockback")) {
				String path = ArmorFloor.modifierPath(slot, stat);
				assertTrue(ids.add(path), "unique id " + path);
				assertTrue(ArmorFloor.isFloorModifier(path));
				assertFalse(path.startsWith("set_") || path.startsWith("rune_"), "never a set or rune modifier");
			}
		}
		assertEquals("unique_floor_feet_toughness", ArmorFloor.modifierPath("feet", "toughness"));
		assertFalse(ArmorFloor.isFloorModifier("set_hiver_eternel_2_0"));
	}

	@Test
	void configModeIsValidatedAndMigrated() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.armorFloor = "Diamond";
		List<String> warnings = cfg.rebuild(id -> true);
		assertEquals("netherite", cfg.armorFloor);
		assertTrue(warnings.stream().anyMatch(w -> w.contains("armorFloor")), "reported: " + warnings);
		UniqueConfig none = new UniqueConfig();
		none.armorFloor = " NONE ";
		assertTrue(none.rebuild(id -> true).isEmpty());
		assertEquals("none", none.armorFloor);
		UniqueConfig old = new UniqueConfig();
		old.configVersion = 4;
		old.armorFloor = null;
		old.rebuild(id -> true);
		assertEquals("netherite", old.armorFloor, "a format 4 file (no armorFloor) gets the netherite floor");
		assertNotNull(ArmorFloor.floor(old.armorFloor, "head"));
	}
}
