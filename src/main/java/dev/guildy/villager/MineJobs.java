package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure work sharing of a real mine (unit tested): the small jobs open right now (the stair front, widening sections of
 * a 2-wide staircase, the segments of every level the stairs reached, pending veins) and the choice of the nearest free
 * one for a miner. Leases (who works where) are kept by {@link Mines}; a job is identified by its {@link Job#key}.
 */
public final class MineJobs {
	/** Stair steps per widening job. */
	public static final int WIDEN_SECTION = 8;
	// Preference (in walking nodes) given to some jobs over the plain nearest one.
	private static final int PRIORITY_STAIRS = 30;
	private static final int PRIORITY_VEIN = 20;
	private static final int PRIORITY_LANDING = 16;
	private static final int PRIORITY_MAIN = 10;
	private static final int PRIORITY_WIDEN = -6;

	private MineJobs() {
	}

	public enum Type {
		/** The front of the inner lane of the stairs. */
		STAIRS,
		/** Outer lane cells of a section of the stairs. */
		WIDEN,
		/** A segment of a level (landing, main tunnel, branch, quarry room). */
		SEGMENT,
		/** A pending vein. */
		VEIN,
		/** Quarry: the opening onto a bench (id 1) or one of its strips (id = strip index + 2). */
		STRIP
	}

	/**
	 * One job: {@code levelY} = level of a segment (or of a vein, for display), {@code id} = segment id / widening
	 * section, {@code vein} = the pending vein.
	 */
	public record Job(Type type, int levelY, int id, MineState.@Nullable Vein vein, String key) {
		public static Job stairs() {
			return new Job(Type.STAIRS, 0, 0, null, "stairs");
		}

		public static Job widen(int section) {
			return new Job(Type.WIDEN, 0, section, null, "widen:" + section);
		}

		public static Job segment(int levelY, int id) {
			return new Job(Type.SEGMENT, levelY, id, null, levelY + ":" + id);
		}

		/** Quarry: the opening onto the bench at {@code benchY} (id 1) or a strip (id = strip index + 2). */
		public static Job strip(int benchY, int id, String key) {
			return new Job(Type.STRIP, benchY, id, null, key);
		}

		public static Job vein(MineState.Vein vein) {
			return new Job(Type.VEIN, vein.stand.getY(), 0, vein, "vein:" + vein.seed.asLong());
		}
	}

	/** An open job, the node to walk to and its preference. */
	public record Candidate(Job job, int stand, int priority) {
	}

	/** The stairs reached level y (its landing step is dug, or galleries right at the fanion's height). */
	public static boolean reached(MineLayout layout, MineState state, int y) {
		int step = layout.stepAt(y);
		return step < state.stairsDone;
	}

	/** Steps of widening section {@code section} still to widen, within the dug inner lane (in order). */
	public static List<Integer> widenSteps(MineLayout layout, MineState state, int section) {
		List<Integer> steps = new ArrayList<>();
		if (!MineLayout.wide(layout.width())) {
			return steps;
		}
		int first = section * WIDEN_SECTION;
		int last = Math.min(first + WIDEN_SECTION, Math.min(state.stairsDone, layout.stairs().size())) - 1;
		for (int k = first; k <= last; k++) {
			if (!state.widened(k) && !layout.outer(k).isEmpty()) {
				steps.add(k);
			}
		}
		return steps;
	}

	/**
	 * Every job open right now: the stairs while they are short of {@code stairsNeeded} steps, widening sections the
	 * inner lane went past, the segments of the reached levels whose way is dug, the pending veins.
	 */
	public static List<Candidate> available(MineLayout layout, MineState state, List<Integer> levels, int stairsNeeded) {
		List<Candidate> list = new ArrayList<>();
		int needed = Math.min(stairsNeeded, layout.stairs().size());
		if (state.stairsDone < needed && (state.stairsBlockedAt < 0 || state.stairsDone < state.stairsBlockedAt)) {
			list.add(new Candidate(Job.stairs(), MineLayout.stairStand(state.stairsDone), PRIORITY_STAIRS));
		}
		if (MineLayout.wide(layout.width())) {
			int sections = (Math.min(state.stairsDone, layout.stairs().size()) + WIDEN_SECTION - 1) / WIDEN_SECTION;
			for (int section = 0; section < sections; section++) {
				int last = section * WIDEN_SECTION + WIDEN_SECTION - 1;
				// A section is worked once the inner lane went past it (or stopped in it for now).
				boolean stopped = state.stairsDone >= needed || (state.stairsBlockedAt >= 0 && state.stairsDone >= state.stairsBlockedAt);
				boolean passed = last < state.stairsDone || stopped;
				List<Integer> steps = passed ? widenSteps(layout, state, section) : List.of();
				if (!steps.isEmpty()) {
					list.add(new Candidate(Job.widen(section), MineLayout.stairNode(steps.getFirst()), PRIORITY_WIDEN));
				}
			}
		}
		if (layout.quarry() != null) {
			QuarryJobs.addCandidates(list, layout, state, QuarryJobs.window(levels, state));
		}
		for (int y : layout.quarry() != null ? List.<Integer>of() : levels) {
			if (!reached(layout, state, y)) {
				continue;
			}
			MineLayout.Level level = layout.level(y);
			MineState.Level progress = state.level(y);
			if (level == null || (progress != null && progress.finished)) {
				continue;
			}
			for (MineLayout.Segment segment : level.segments()) {
				if (progress != null && (progress.done.contains(segment.id()) || progress.abandoned.contains(segment.id()))) {
					continue;
				}
				if (way(level, progress, segment) != 0) {
					continue;
				}
				list.add(new Candidate(Job.segment(y, segment.id()), segment.stands()[0], priority(segment.kind())));
			}
		}
		for (MineState.Vein vein : state.veins) {
			int stand = layout.nodeAt(vein.stand);
			if (stand >= 0) {
				list.add(new Candidate(Job.vein(vein), stand, PRIORITY_VEIN));
			}
		}
		return list;
	}

	private static int priority(MineLayout.Kind kind) {
		return switch (kind) {
			case LANDING -> PRIORITY_LANDING;
			case MAIN -> PRIORITY_MAIN;
			default -> 0;
		};
	}

	/** 0: the way to the segment is dug; 1: later (the way is being dug); -1: never (the way was given up). */
	public static int way(MineLayout.Level level, MineState.@Nullable Level progress, MineLayout.Segment segment) {
		int after = segment.after();
		int result = 0;
		while (after >= 0) {
			if (progress != null && progress.abandoned.contains(after)) {
				return -1;
			}
			if (progress == null || !progress.done.contains(after)) {
				result = 1;
			}
			MineLayout.Segment previous = level.segment(after);
			after = previous == null ? -1 : previous.after();
		}
		return result;
	}

	/** Every segment of the level is finished, given up or cut off by a segment given up. */
	public static boolean levelFinished(MineLayout.Level level, MineState.@Nullable Level progress) {
		for (MineLayout.Segment segment : level.segments()) {
			if (progress != null && (progress.done.contains(segment.id()) || progress.abandoned.contains(segment.id()))) {
				continue;
			}
			if (way(level, progress, segment) < 0) {
				continue;
			}
			return false;
		}
		return true;
	}

	/**
	 * The open job a miner standing at node {@code from} should take: the nearest one along the walking graph, minus its
	 * preference, among those {@code taken} says nobody else works on. Null when there is none.
	 */
	public static @Nullable Candidate nearest(MineLayout layout, List<Candidate> candidates, int from, Predicate<Job> taken) {
		Candidate best = null;
		int bestScore = Integer.MAX_VALUE;
		for (Candidate candidate : candidates) {
			if (taken.test(candidate.job())) {
				continue;
			}
			int score = layout.distance(from, candidate.stand()) - candidate.priority();
			if (score < bestScore) {
				bestScore = score;
				best = candidate;
			}
		}
		return best;
	}
}
