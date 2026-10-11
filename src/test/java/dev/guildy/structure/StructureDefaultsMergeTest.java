package dev.guildy.structure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Existing structures.json5 files receive the default structures added by mod updates (trade hub) exactly once. */
class StructureDefaultsMergeTest {

	private static List<String> ids(List<StructureDefinition> definitions) {
		List<String> ids = new ArrayList<>();
		for (StructureDefinition def : definitions) {
			ids.add(def.id);
		}
		return ids;
	}

	private static List<StructureDefinition> legacyFile() {
		List<StructureDefinition> list = new ArrayList<>(StructureConfig.defaults());
		list.removeIf(def -> def.id.equals("trade_hub"));
		return list;
	}

	@Test
	void oldFilesGetTheTradeHubOnce() {
		List<StructureDefinition> definitions = legacyFile();
		List<String> known = new ArrayList<>();
		assertEquals(List.of("trade_hub"), StructureConfig.mergeNewDefaults(definitions, known));
		assertEquals(List.of("quest_board", "shop", "reception", "trade_hub"), ids(definitions));
		assertTrue(known.containsAll(List.of("quest_board", "shop", "reception", "trade_hub")));
		// Removed by the admin afterwards: never added back.
		definitions.removeIf(def -> def.id.equals("trade_hub"));
		assertTrue(StructureConfig.mergeNewDefaults(definitions, known).isEmpty());
		assertEquals(3, definitions.size());
	}

	@Test
	void originalDefaultsRemovedOnPurposeStayRemoved() {
		List<StructureDefinition> definitions = legacyFile();
		definitions.removeIf(def -> def.id.equals("reception"));
		StructureConfig.mergeNewDefaults(definitions, new ArrayList<>());
		assertEquals(List.of("quest_board", "shop", "trade_hub"), ids(definitions));
	}

	@Test
	void freshFilesAreUnchanged() {
		List<StructureDefinition> definitions = new ArrayList<>(StructureConfig.defaults());
		List<String> known = new ArrayList<>();
		assertTrue(StructureConfig.mergeNewDefaults(definitions, known).isEmpty());
		assertEquals(4, definitions.size());
		assertEquals(4, known.size());
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
