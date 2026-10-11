package dev.guildy.structure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureDefinitionTest {

	private static StructureDefinition board() {
		StructureDefinition def = new StructureDefinition();
		def.id = "quest_board";
		def.name = "Tableau";
		def.rows = new ArrayList<>(List.of("LBL", "LPL"));
		Map<String, String> key = new LinkedHashMap<>();
		key.put("L", "#minecraft:logs");
		key.put("B", "minecraft:bookshelf");
		key.put("P", "#minecraft:planks");
		def.key = key;
		def.signCells = new ArrayList<>(List.of(new int[] {0, 0}, new int[] {1, 0}, new int[] {2, 0},
				new int[] {0, 1}, new int[] {1, 1}, new int[] {2, 1}));
		return def;
	}

	@Test
	void validDefinition() {
		StructureDefinition def = board();
		assertTrue(def.check().isEmpty());
		assertNotNull(def.pattern());
		assertEquals(3, def.pattern().width());
		assertEquals(2, def.pattern().height());
		assertEquals(Set.of('L', 'B', 'P'), def.pattern().keys());
		assertEquals(6, def.signCount());
	}

	@Test
	void defaultsArePureValid() {
		List<StructureDefinition> defaults = StructureConfig.defaults();
		Set<String> ids = new HashSet<>();
		for (StructureDefinition def : defaults) {
			assertTrue(def.check().isEmpty(), def.id + " " + def.check());
			assertTrue(ids.add(def.id));
			assertTrue(def.signCount() >= 1, def.id);
		}
		assertTrue(ids.containsAll(Set.of("quest_board", "shop", "reception")));
		StructureDefinition board = defaults.stream().filter(d -> d.id.equals("quest_board")).findFirst().orElseThrow();
		// 3 x 2 board, every cell signed: 6 signs = 6 quests.
		assertEquals(6, board.signCount());
		assertEquals(6, board.pattern().blockCount());
	}

	@Test
	void signCellErrors() {
		StructureDefinition outside = board();
		outside.signCells.add(new int[] {3, 0});
		assertFalse(outside.check().isEmpty());
		assertNull(outside.pattern());

		StructureDefinition wildcard = board();
		wildcard.rows = new ArrayList<>(List.of("L.L", "LPL"));
		wildcard.signCells = new ArrayList<>(List.of(new int[] {1, 0}));
		assertFalse(wildcard.check().isEmpty());

		StructureDefinition duplicate = board();
		duplicate.signCells.add(new int[] {0, 0});
		assertFalse(duplicate.check().isEmpty());
	}

	@Test
	void keyAndSizeErrors() {
		StructureDefinition missingKey = board();
		missingKey.key.remove("B");
		assertFalse(missingKey.check().isEmpty());

		StructureDefinition tooLarge = board();
		tooLarge.rows = new ArrayList<>(List.of("LLLLLLLLLL", "LLLLLLLLLL"));
		tooLarge.signCells = new ArrayList<>();
		assertFalse(tooLarge.check().isEmpty());

		StructureDefinition badId = board();
		badId.id = "Quest Board";
		assertFalse(badId.check().isEmpty());

		StructureDefinition clamped = board();
		clamped.minLevel = -3;
		clamped.unlockCost = -10;
		clamped.maxPerGuild = 0;
		assertTrue(clamped.check().isEmpty());
		assertEquals(1, clamped.minLevel);
		assertEquals(0, clamped.unlockCost);
		assertEquals(1, clamped.maxPerGuild);
	}

	@Test
	void patternParsing() {
		StructurePattern pattern = StructurePattern.parse(List.of("AB", "A"));
		assertEquals(2, pattern.width());
		assertTrue(pattern.isWildcard(1, 1));
		assertEquals(List.of("AB", "A."), pattern.rows());
		// Tabs / spaces (re-indented config files) are wildcards.
		assertTrue(StructurePattern.parse(List.of("A\tA")).isWildcard(1, 0));
		assertThrows(IllegalArgumentException.class, () -> StructurePattern.parse(List.of()));
		assertThrows(IllegalArgumentException.class, () -> StructurePattern.parse(List.of("...")));
	}
}
