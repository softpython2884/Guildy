package dev.guildy.structure;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternMatcherTest {
	/** A wall seen from the front: (x = right, y = up) → block character. */
	private static final class FakeWall {
		private final Map<Long, Character> blocks = new HashMap<>();

		/** Builds rows (top to bottom) with the bottom-left block at (x0, y0). */
		FakeWall put(int x0, int y0, String... rows) {
			for (int r = 0; r < rows.length; r++) {
				int y = y0 + rows.length - 1 - r;
				for (int c = 0; c < rows[r].length(); c++) {
					char ch = rows[r].charAt(c);
					if (ch != ' ') {
						blocks.put(key(x0 + c, y), ch);
					}
				}
			}
			return this;
		}

		FakeWall set(int x, int y, char c) {
			blocks.put(key(x, y), c);
			return this;
		}

		PatternMatcher.CellTester clickedAt(int x, int y) {
			return (right, up, k) -> {
				Character found = blocks.get(key(x + right, y + up));
				return found != null && found == k;
			};
		}

		private static long key(int x, int y) {
			return ((long) x << 32) | (y & 0xFFFFFFFFL);
		}
	}

	private static final StructurePattern BOARD = StructurePattern.parse(List.of("LBL", "LPL"));

	@Test
	void everyCellCanBeClicked() {
		FakeWall wall = new FakeWall().put(10, 64, "LBL", "LPL");
		for (int col = 0; col < 3; col++) {
			for (int row = 0; row < 2; row++) {
				int x = 10 + col;
				int y = 64 + (1 - row);
				PatternMatcher.Result result = PatternMatcher.match(BOARD, wall.clickedAt(x, y));
				assertTrue(result.found(), "click on cell " + col + "," + row);
				assertEquals(1, result.matches().size());
				PatternMatcher.Placement placement = result.matches().get(0);
				// The origin (bottom-left) is always at (10, 64).
				assertEquals(10, x + placement.originRight());
				assertEquals(64, y + placement.originUp(BOARD.height()));
				assertEquals(6, result.bestMatched());
				assertTrue(result.bestMismatches().isEmpty());
			}
		}
	}

	@Test
	void wrongBlockIsReported() {
		FakeWall wall = new FakeWall().put(0, 0, "LBL", "LPL").set(2, 0, 'X');
		PatternMatcher.Result result = PatternMatcher.match(BOARD, wall.clickedAt(1, 1));
		assertFalse(result.found());
		assertNotNull(result.best());
		assertEquals(1, result.bestMismatches().size());
		PatternMatcher.Mismatch mismatch = result.bestMismatches().get(0);
		assertEquals(2, mismatch.col());
		assertEquals(1, mismatch.row());
		assertEquals('L', mismatch.key());
		// Offsets from the clicked bookshelf (1, 1) to the wrong block (2, 0).
		assertEquals(1, mismatch.right());
		assertEquals(-1, mismatch.up());
	}

	@Test
	void clickedBlockOutsideThePatternKeys() {
		FakeWall wall = new FakeWall().put(0, 0, "XXX", "XXX");
		PatternMatcher.Result result = PatternMatcher.match(BOARD, wall.clickedAt(0, 0));
		assertFalse(result.found());
		assertNull(result.best());
	}

	@Test
	void wildcardsAcceptAnything() {
		StructurePattern pattern = StructurePattern.parse(List.of("L.L", "LLL"));
		FakeWall wall = new FakeWall().put(0, 0, "LZL", "LLL");
		assertTrue(PatternMatcher.match(pattern, wall.clickedAt(0, 0)).found());
		FakeWall empty = new FakeWall().put(0, 0, "L L", "LLL");
		assertTrue(PatternMatcher.match(pattern, empty.clickedAt(2, 1)).found());
		assertEquals(5, pattern.blockCount());
	}

	@Test
	void repeatedBlocksGiveSeveralPlacements() {
		StructurePattern pattern = StructurePattern.parse(List.of("LL"));
		FakeWall wall = new FakeWall().put(0, 0, "LLL");
		PatternMatcher.Result result = PatternMatcher.match(pattern, wall.clickedAt(1, 0));
		assertEquals(2, result.matches().size());
	}

	@Test
	void partialStructureKeepsBestCandidate() {
		// Only the bottom row exists: the best placement matches 3 cells, 3 mismatches.
		FakeWall wall = new FakeWall().put(0, 0, "LPL");
		PatternMatcher.Result result = PatternMatcher.match(BOARD, wall.clickedAt(1, 0));
		assertFalse(result.found());
		assertEquals(3, result.bestMatched());
		assertEquals(3, result.bestMismatches().size());
		assertEquals(0, result.bestMismatches().get(0).row());
	}

	@Test
	void checkKnownPlacement() {
		FakeWall wall = new FakeWall().put(5, 5, "LBL", "LPL");
		// Anchor = bottom-left cell, clicked = origin.
		PatternMatcher.Placement origin = new PatternMatcher.Placement(0, BOARD.height() - 1);
		assertTrue(PatternMatcher.check(BOARD, origin, wall.clickedAt(5, 5)).isEmpty());
		wall.set(6, 6, 'P');
		List<PatternMatcher.Mismatch> mismatches = PatternMatcher.check(BOARD, origin, wall.clickedAt(5, 5));
		assertEquals(1, mismatches.size());
		assertEquals('B', mismatches.get(0).key());
	}
}
