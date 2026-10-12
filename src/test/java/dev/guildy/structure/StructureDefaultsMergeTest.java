package dev.guildy.structure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Existing structures.json5 files receive the default structures added by mod updates (trade hub, village sign) exactly once. */
class StructureDefaultsMergeTest {

	private static List<String> ids(List<StructureDefinition> definitions) {
		List<String> ids = new ArrayList<>();
		for (StructureDefinition def : definitions) {
			ids.add(def.id);
		}
		return ids;
	}

	/** A file written before the trade hub and the village sign existed. */
	private static List<StructureDefinition> legacyFile() {
		List<StructureDefinition> list = new ArrayList<>(StructureConfig.defaults());
		list.removeIf(def -> def.id.equals("trade_hub") || def.id.equals("village_sign"));
		return list;
	}

	@Test
	void oldFilesGetTheNewDefaultsOnce() {
		List<StructureDefinition> definitions = legacyFile();
		List<String> known = new ArrayList<>();
		assertEquals(List.of("trade_hub", "village_sign"), StructureConfig.mergeNewDefaults(definitions, known));
		assertEquals(List.of("quest_board", "shop", "reception", "trade_hub", "village_sign"), ids(definitions));
		assertTrue(known.containsAll(List.of("quest_board", "shop", "reception", "trade_hub", "village_sign")));
		// Removed by the admin afterwards: never added back.
		definitions.removeIf(def -> def.id.equals("trade_hub"));
		assertTrue(StructureConfig.mergeNewDefaults(definitions, known).isEmpty());
		assertEquals(4, definitions.size());
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
		assertEquals(List.of("quest_board", "shop", "trade_hub", "village_sign"), ids(definitions));
	}

	@Test
	void freshFilesAreUnchanged() {
		List<StructureDefinition> definitions = new ArrayList<>(StructureConfig.defaults());
		List<String> known = new ArrayList<>();
		assertTrue(StructureConfig.mergeNewDefaults(definitions, known).isEmpty());
		assertEquals(5, definitions.size());
		assertEquals(5, known.size());
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
}
