package dev.guildy.structure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Existing structures.json5 files receive the default structures added by mod updates (trade hub, village sign,
 * cartographer's atelier) exactly once.
 */
class StructureDefaultsMergeTest {

	private static List<String> ids(List<StructureDefinition> definitions) {
		List<String> ids = new ArrayList<>();
		for (StructureDefinition def : definitions) {
			ids.add(def.id);
		}
		return ids;
	}

	/** A file written before the trade hub, the village sign and the cartographer's atelier existed. */
	private static List<StructureDefinition> legacyFile() {
		List<StructureDefinition> list = new ArrayList<>(StructureConfig.defaults());
		list.removeIf(def -> def.id.equals("trade_hub") || def.id.equals("village_sign") || def.id.equals("cartographer"));
		return list;
	}

	@Test
	void oldFilesGetTheNewDefaultsOnce() {
		List<StructureDefinition> definitions = legacyFile();
		List<String> known = new ArrayList<>();
		assertEquals(List.of("trade_hub", "village_sign", "cartographer"), StructureConfig.mergeNewDefaults(definitions, known));
		assertEquals(List.of("quest_board", "shop", "reception", "trade_hub", "village_sign", "cartographer"), ids(definitions));
		assertTrue(known.containsAll(List.of("quest_board", "shop", "reception", "trade_hub", "village_sign", "cartographer")));
		// Removed by the admin afterwards: never added back.
		definitions.removeIf(def -> def.id.equals("trade_hub"));
		assertTrue(StructureConfig.mergeNewDefaults(definitions, known).isEmpty());
		assertEquals(5, definitions.size());
	}

	@Test
	void filesThatAlreadyKnewTheTradeHubGetTheVillageSign() {
		List<StructureDefinition> definitions = new ArrayList<>(StructureConfig.defaults());
		definitions.removeIf(def -> def.id.equals("village_sign"));
		List<String> known = new ArrayList<>(List.of("quest_board", "shop", "reception", "trade_hub"));
		assertEquals(List.of("village_sign"), StructureConfig.mergeNewDefaults(definitions, known));
	}

	@Test
	void originalDefaultsRemovedOnPurposeStayRemoved() {
		List<StructureDefinition> definitions = legacyFile();
		definitions.removeIf(def -> def.id.equals("reception"));
		StructureConfig.mergeNewDefaults(definitions, new ArrayList<>());
		assertEquals(List.of("quest_board", "shop", "trade_hub", "village_sign", "cartographer"), ids(definitions));
	}

	@Test
	void freshFilesAreUnchanged() {
		List<StructureDefinition> definitions = new ArrayList<>(StructureConfig.defaults());
		List<String> known = new ArrayList<>();
		assertTrue(StructureConfig.mergeNewDefaults(definitions, known).isEmpty());
		assertEquals(StructureConfig.defaults().size(), definitions.size());
		assertEquals(StructureConfig.defaults().size(), known.size());
	}

	@Test
	void villageSignIsOneAnyBlockWithASign() {
		StructureDefinition sign = null;
		for (StructureDefinition def : StructureConfig.defaults()) {
			if (def.id.equals("village_sign")) {
				sign = def;
			}
		}
		assertTrue(sign != null);
		assertTrue(sign.check().isEmpty(), sign.check().toString());
		assertEquals(1, sign.pattern().width());
		assertEquals(1, sign.pattern().height());
		assertEquals("*", sign.keyFor(sign.pattern().at(0, 0)));
		assertEquals(1, sign.signCount());
		assertEquals(0, sign.unlockCost);
		assertEquals(1, sign.minLevel);
	}

	@Test
	void tradeHubIsAValidWall() {
		StructureDefinition hub = null;
		for (StructureDefinition def : StructureConfig.defaults()) {
			if (def.id.equals("trade_hub")) {
				hub = def;
			}
		}
		assertTrue(hub != null);
		assertTrue(hub.check().isEmpty(), hub.check().toString());
		assertEquals(3, hub.pattern().width());
		assertEquals(2, hub.pattern().height());
		assertEquals('E', hub.pattern().at(1, 0), "the sign hangs on the emerald block");
		assertEquals(2, hub.minLevel);
		assertEquals(60, hub.unlockCost);
	}

	@Test
	void filesThatKnewTheVillageSignGetTheCartographer() {
		List<StructureDefinition> definitions = new ArrayList<>(StructureConfig.defaults());
		definitions.removeIf(def -> def.id.equals("cartographer"));
		List<String> known = new ArrayList<>(List.of("quest_board", "shop", "reception", "trade_hub", "village_sign"));
		assertEquals(List.of("cartographer"), StructureConfig.mergeNewDefaults(definitions, known));
	}

	@Test
	void cartographerAtelierIsAValidWall() {
		StructureDefinition atelier = null;
		for (StructureDefinition def : StructureConfig.defaults()) {
			if (def.id.equals("cartographer")) {
				atelier = def;
			}
		}
		assertTrue(atelier != null);
		assertTrue(atelier.check().isEmpty(), atelier.check().toString());
		assertEquals(3, atelier.pattern().width());
		assertEquals(2, atelier.pattern().height());
		assertEquals('C', atelier.pattern().at(1, 1), "the cartography table is in the middle of the bottom row");
		assertEquals("minecraft:cartography_table", atelier.keyFor('C'));
		assertEquals(1, atelier.signCount());
		assertEquals(5, atelier.minLevel);
		assertEquals(80, atelier.unlockCost);
	}
}
