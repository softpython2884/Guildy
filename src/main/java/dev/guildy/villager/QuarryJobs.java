package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure work sharing of a quarry (unit tested): which benches are worked right now, the opening and the strips open on
 * them, bench progress. A strip is open when the cell it starts from is dug (and the way to it), and when the bench
 * above has nothing left to dig over it nor any miner to walk across it: every column of the bench above above the
 * strip has its whole sub-tree dug. Progress is kept per column ({@link MineState.Level#cleared}).
 */
public final class QuarryJobs {
	/** Benches worked at the same time (the others are not even planned). */
	public static final int WINDOW = 2;
	private static final int PRIORITY_OPENING = 16;
	private static final int PRIORITY_UPPER = 4;

	private QuarryJobs() {
	}

	/** The column is dug on that bench. */
	public static boolean cleared(MineState.@Nullable Level progress, int column) {
		return progress != null && Quarry.bit(progress.cleared, column);
	}

	/** Benches worked now: the first {@link #WINDOW} unfinished ones, highest first. */
	public static List<Integer> window(List<Integer> benches, MineState state) {
		List<Integer> window = new ArrayList<>(WINDOW);
		for (int y : benches) {
			MineState.Level progress = state.level(y);
			if (progress != null && progress.finished) {
				continue;
			}
			window.add(y);
			if (window.size() >= WINDOW) {
				break;
			}
		}
		return window;
	}

	/** The stairs reached the way onto the bench (its opening's step is dug). */
	public static boolean reached(MineLayout layout, MineState state, int y) {
		MineLayout.Level level = layout.level(y);
		Quarry.Bench bench = level == null ? null : level.bench();
		if (bench == null || bench.opening() == null) {
			return MineJobs.reached(layout, state, y);
		}
		return bench.opening().step() < state.stairsDone;
	}

	/** First cell of a strip not dug yet ({@code columns.length} when the strip is done). */
	public static int firstOpen(Quarry.Strip strip, MineState.@Nullable Level progress) {
		int[] columns = strip.columns();
		for (int i = 0; i < columns.length; i++) {
			if (!cleared(progress, columns[i])) {
				return i;
			}
		}
		return columns.length;
	}

	/** Columns dug and reachable through dug cells from the opening (each one walked to through the tree). */
	public static boolean[] reach(Quarry.Bench bench, MineState.@Nullable Level progress, int columns) {
		boolean[] reach = new boolean[columns];
		for (int c : bench.order()) {
			int parent = bench.parent(c);
			reach[c] = cleared(progress, c) && (parent == Quarry.ROOT || (parent >= 0 && reach[parent]));
		}
		return reach;
	}

	/**
	 * Columns of the bench whose sub-tree still has a cell to dig (miners still work or walk over them); null when nothing
	 * is left to dig on the bench.
	 */
	public static boolean @Nullable [] pending(Quarry.Bench bench, MineState.@Nullable Level progress, int columns) {
		if (finished(bench, progress)) {
			return null;
		}
		boolean[] pending = new boolean[columns];
		int[] order = bench.order();
		for (int i = order.length - 1; i >= 0; i--) {
			int c = order[i];
			pending[c] |= !cleared(progress, c);
			int parent = bench.parent(c);
			if (pending[c] && parent >= 0) {
				pending[parent] = true;
			}
		}
		return pending;
	}

	/** Every cell of the bench is dug (a bench without way in has nothing to dig). */
	public static boolean finished(Quarry.Bench bench, MineState.@Nullable Level progress) {
		if (bench.opening() == null) {
			return true;
		}
		if (progress == null || Quarry.count(progress.cleared) < bench.size()) {
			// Fewer columns dug than planned: cheap answer (checked every step).
			return false;
		}
		for (int c : bench.order()) {
			if (!cleared(progress, c)) {
				return false;
			}
		}
		return true;
	}

	/** Strips done / planned on the bench (the opening counts as one). */
	public static int[] strips(Quarry.Bench bench, MineState.@Nullable Level progress) {
		if (bench.opening() == null) {
			return new int[]{0, 0};
		}
		int done = cleared(progress, bench.root()) ? 1 : 0;
		for (Quarry.Strip strip : bench.strips()) {
			if (firstOpen(strip, progress) >= strip.columns().length) {
				done++;
			}
		}
		return new int[]{done, bench.strips().size() + 1};
	}

	/** Lease key of a strip (stable when the bench is planned again: its first column). */
	static String key(int y, Quarry.@Nullable Strip strip) {
		return strip == null ? "q:" + y + ":opening" : "q:" + y + ":" + strip.columns()[0];
	}

	/**
	 * Adds the open jobs of the benches of {@code window}: the opening of a bench whose first cell is not dug yet (once
	 * the stairs reach its step), else its open strips.
	 */
	public static void addCandidates(List<MineJobs.Candidate> list, MineLayout layout, MineState state, List<Integer> window) {
		Quarry quarry = layout.quarry();
		if (quarry == null) {
			return;
		}
		int columns = quarry.columns();
		for (int index = 0; index < window.size(); index++) {
			int y = window.get(index);
			MineLayout.Level level = layout.level(y);
			Quarry.Bench bench = level == null ? null : level.bench();
			MineState.Level progress = state.level(y);
			if (bench == null || bench.opening() == null || (progress != null && progress.finished)
					|| bench.opening().step() >= state.stairsDone) {
				continue;
			}
			boolean[] above = above(layout, quarry, state, y, columns);
			int bonus = index == 0 ? PRIORITY_UPPER : 0;
			int root = bench.root();
			if (!cleared(progress, root)) {
				if (above == null || !above[root]) {
					list.add(new MineJobs.Candidate(MineJobs.Job.strip(y, 1, key(y, null)), MineLayout.stairNode(bench.opening().step()),
							PRIORITY_OPENING + bonus));
				}
				continue;
			}
			boolean[] reach = reach(bench, progress, columns);
			List<Quarry.Strip> strips = bench.strips();
			for (int i = 0; i < strips.size(); i++) {
				Quarry.Strip strip = strips.get(i);
				int first = firstOpen(strip, progress);
				int[] cells = strip.columns();
				if (first >= cells.length) {
					continue;
				}
				int from = first == 0 ? strip.parent() : cells[first - 1];
				if (from < 0 || !reach[from] || !freeBelow(above, cells, first, progress)) {
					continue;
				}
				MineLayout.Segment segment = level.segment(i + 2);
				if (segment == null) {
					continue;
				}
				list.add(new MineJobs.Candidate(MineJobs.Job.strip(y, i + 2, key(y, strip)), segment.stands()[first], bonus));
			}
		}
	}

	/** No cell left to dig of the strip lies under a part of the bench above still worked or walked. */
	private static boolean freeBelow(boolean @Nullable [] above, int[] cells, int first, MineState.@Nullable Level progress) {
		if (above == null) {
			return true;
		}
		for (int i = first; i < cells.length; i++) {
			if (!cleared(progress, cells[i]) && above[cells[i]]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Columns of the bench above the one at {@code y} still worked or walked ({@link #pending}); null when it is
	 * finished or does not exist (first bench). A bench above not planned (out of the window) blocks everything.
	 */
	private static boolean @Nullable [] above(MineLayout layout, Quarry quarry, MineState state, int y, int columns) {
		int upper = y + quarry.benchHeight();
		if (!quarry.isBench(upper)) {
			return null;
		}
		MineState.Level progress = state.level(upper);
		if (progress != null && progress.finished) {
			return null;
		}
		MineLayout.Level level = layout.level(upper);
		if (level == null || level.bench() == null) {
			boolean[] all = new boolean[columns];
			java.util.Arrays.fill(all, true);
			return all;
		}
		return pending(level.bench(), progress, columns);
	}
}
