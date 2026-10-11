package dev.guildy.structure;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure pattern matching around a clicked block. Every non-wildcard cell is tried as the clicked one, so the player
 * can click any block of the structure. World access is abstracted by {@link CellTester}.
 */
public final class PatternMatcher {
	private PatternMatcher() {
	}

	@FunctionalInterface
	public interface CellTester {
		/**
		 * @param right offset from the clicked block along the structure's right axis (as seen from the front)
		 * @param up    vertical offset from the clicked block
		 * @param key   pattern character expected at that position
		 * @return true when the block there matches the key
		 */
		boolean test(int right, int up, char key);
	}

	/** Placement of the pattern: the clicked block is cell (anchorCol, anchorRow). */
	public record Placement(int anchorCol, int anchorRow) {
		/** Offset along the right axis of cell column {@code col} from the clicked block. */
		public int right(int col) {
			return col - anchorCol;
		}

		/** Vertical offset of cell row {@code row} from the clicked block (rows grow downwards). */
		public int up(int row) {
			return anchorRow - row;
		}

		/** Right offset of the origin (bottom-left cell) from the clicked block. */
		public int originRight() {
			return -anchorCol;
		}

		/** Vertical offset of the origin (bottom-left cell) from the clicked block. */
		public int originUp(int height) {
			return anchorRow - (height - 1);
		}
	}

	/** A cell that does not match, with its offsets from the clicked block. */
	public record Mismatch(int col, int row, int right, int up, char key) {
	}

	/**
	 * @param matches        placements where every cell matches (usually 0 or 1)
	 * @param best           the placement with the most matching cells (null when the clicked block fits no cell)
	 * @param bestMismatches non-matching cells of {@code best} (reading order)
	 * @param bestMatched    number of matching cells of {@code best}
	 */
	public record Result(List<Placement> matches, @Nullable Placement best, List<Mismatch> bestMismatches, int bestMatched) {
		public boolean found() {
			return !matches.isEmpty();
		}
	}

	public static Result match(StructurePattern pattern, CellTester tester) {
		List<Placement> matches = new ArrayList<>();
		Placement best = null;
		List<Mismatch> bestMismatches = List.of();
		int bestMatched = -1;
		for (int row = 0; row < pattern.height(); row++) {
			for (int col = 0; col < pattern.width(); col++) {
				if (pattern.isWildcard(col, row) || !tester.test(0, 0, pattern.at(col, row))) {
					continue;
				}
				Placement placement = new Placement(col, row);
				List<Mismatch> mismatches = new ArrayList<>();
				int matched = 0;
				for (int r = 0; r < pattern.height(); r++) {
					for (int c = 0; c < pattern.width(); c++) {
						if (pattern.isWildcard(c, r)) {
							continue;
						}
						char key = pattern.at(c, r);
						int right = placement.right(c);
						int up = placement.up(r);
						if (tester.test(right, up, key)) {
							matched++;
						} else {
							mismatches.add(new Mismatch(c, r, right, up, key));
						}
					}
				}
				if (mismatches.isEmpty()) {
					matches.add(placement);
				}
				if (matched > bestMatched) {
					best = placement;
					bestMismatches = mismatches;
					bestMatched = matched;
				}
			}
		}
		return new Result(matches, best, bestMismatches, Math.max(0, bestMatched));
	}

	/** Checks a known placement (integrity re-check): returns the mismatching cells (empty = intact). */
	public static List<Mismatch> check(StructurePattern pattern, Placement placement, CellTester tester) {
		List<Mismatch> mismatches = new ArrayList<>();
		for (int r = 0; r < pattern.height(); r++) {
			for (int c = 0; c < pattern.width(); c++) {
				if (pattern.isWildcard(c, r)) {
					continue;
				}
				char key = pattern.at(c, r);
				int right = placement.right(c);
				int up = placement.up(r);
				if (!tester.test(right, up, key)) {
					mismatches.add(new Mismatch(c, r, right, up, key));
				}
			}
		}
		return mismatches;
	}
}
