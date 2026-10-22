package dev.guildy.unique;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Worn look of the unique armors: which uniques get an equipment asset, and the asset paths. */
class UniqueEquipmentTest {
	/** Armor slot of a vanilla armor item id ("minecraft:diamond_helmet" -> head), null for anything else. */
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
	void assetOnlyForItsOwnSlot() {
		assertEquals("couronne_du_roi_de_givre", UniqueEquipment.assetFor("couronne_du_roi_de_givre", "head"));
		assertEquals("plastron_du_serpent", UniqueEquipment.assetFor("plastron_du_serpent", "chest"));
		assertEquals("bottes_de_zephyr", UniqueEquipment.assetFor("bottes_de_zephyr", "feet"));
		assertEquals("bottes_du_coursier", UniqueEquipment.assetFor(" Bottes_du_Coursier ", "feet"), "normalized like the config");
		assertNull(UniqueEquipment.assetFor("couronne_du_roi_de_givre", "feet"), "a crown drawn on boots would show nothing");
		assertEquals("jambieres_hiver_eternel", UniqueEquipment.assetFor("jambieres_hiver_eternel", "legs"), "leggings of a set");
		assertNull(UniqueEquipment.assetFor("jambieres_hiver_eternel", "feet"));
		assertEquals("masque_guerrier_jaguar", UniqueEquipment.assetFor("masque_guerrier_jaguar", "head"));
		assertNull(UniqueEquipment.assetFor("plastron_du_serpent", "mainhand"));
		assertNull(UniqueEquipment.assetFor("foudrelame", "mainhand"), "weapons keep their equippable (none)");
		assertNull(UniqueEquipment.assetFor("", "head"), "no model: vanilla look");
		assertNull(UniqueEquipment.assetFor(null, "head"));
		assertNull(UniqueEquipment.assetFor("bottes_de_zephyr", null));
	}

	@Test
	void everyDefaultArmorHasItsWornLook() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.rebuild(id -> true);
		Map<String, String> armors = new HashMap<>();
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			String slot = slotOf(def.item);
			String asset = slot == null ? null : UniqueEquipment.assetFor(def.model, slot);
			if (slot != null) {
				assertEquals(def.id, asset, def.id + " (" + def.item + "): its own equipment asset");
				armors.put(def.id, slot);
			} else {
				assertNull(UniqueEquipment.assetFor(def.model, "head"), def.id + ": not an armor");
				assertNull(UniqueEquipment.assetFor(def.model, "feet"), def.id + ": not an armor");
			}
		}
		assertEquals(UniqueEquipment.ASSETS, armors, "one asset per default armor piece, nothing else");
	}

	@Test
	void paths() {
		assertEquals("humanoid", UniqueEquipment.layer("head"));
		assertEquals("humanoid", UniqueEquipment.layer("chest"));
		assertEquals("humanoid", UniqueEquipment.layer("feet"));
		assertEquals("humanoid_leggings", UniqueEquipment.layer("legs"));
		assertEquals("assets/guildy/equipment/bottes_de_zephyr.json", UniqueEquipment.definitionPath("bottes_de_zephyr"));
		assertEquals("assets/guildy/textures/entity/equipment/humanoid/couronne_du_roi_de_givre.png",
				UniqueEquipment.texturePath("couronne_du_roi_de_givre", "head"));
		assertEquals("assets/guildy/textures/entity/equipment/humanoid_leggings/x.png", UniqueEquipment.texturePath("x", "legs"));
		assertEquals("assets/guildy/textures/entity/equipment/humanoid_leggings/greves_brasier_des_ames.png",
				UniqueEquipment.texturePath("greves_brasier_des_ames", UniqueEquipment.ASSETS.get("greves_brasier_des_ames")));
	}
}
