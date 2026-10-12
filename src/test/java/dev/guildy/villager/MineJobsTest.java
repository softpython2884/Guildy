package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineJobsTest {
	private static final MineLayout.Settings WIDE = new MineLayout.Settings(4, 2, 32, 3, 12, 5, 5, 3, 2);
	private static final BlockPos FANION = new BlockPos(8, 70, 8);
	/** 3×3 chunks around the fanion. */
	private static final int[] BOUNDS = ZoneMath.squareBounds(FANION.getX(), FANION.getZ(), 1);
	private static final List<Integer> LEVELS = List.of(64, 58, 52, 46, 40, 34, 28, 22, 16);

	private static MineLayout layout() {
		int orientation = 0;
		while (!MineLayout.fits(FANION, orientation, 4, 2, BOUNDS)) {
			orientation++;
		}
		return MineLayout.build(FANION, orientation, -59, BOUNDS, false, WIDE);
	}

	private static int stairsNeeded(MineLayout layout) {
		return layout.stepAt(LEVELS.getLast()) + 1;
	}

	@Test
	void stairsFirstThenTheLevelsTheyReach() {
		MineLayout layout = layout();
		MineState state = new MineState(MineMode.REAL);
		List<MineJobs.Candidate> open = MineJobs.available(layout, state, LEVELS, stairsNeeded(layout));
		assertEquals(1, open.size(), "nothing but the stairs at first");
		assertEquals(MineJobs.Type.STAIRS, open.getFirst().job().type());
		assertEquals(MineLayout.ENTRANCE, open.getFirst().stand());
		// The stairs reached the first level (64 = step 5): its landing opens, the stairs go on.
		state.stairsDone = 6;
		open = MineJobs.available(layout, state, LEVELS, stairsNeeded(layout));
		assertTrue(open.stream().anyMatch(c -> c.job().type() == MineJobs.Type.STAIRS));
		MineJobs.Candidate landing = open.stream().filter(c -> c.job().type() == MineJobs.Type.SEGMENT).findFirst().orElseThrow();
		assertEquals(64, landing.job().levelY());
		assertEquals(MineLayout.Kind.LANDING, layout.level(64).segment(landing.job().id()).kind());
		assertEquals(2, open.size(), "the main tunnel waits for the landing");
		// Landing done: the first stretch of main tunnel; a stretch done: its two branches.
		MineState.Level progress = state.levelOrCreate(64);
		progress.done.add(landing.job().id());
		MineLayout.Level level = layout.level(64);
		MineLayout.Segment firstMain = level.segments().stream().filter(s -> s.kind() == MineLayout.Kind.MAIN).findFirst().orElseThrow();
		open = MineJobs.available(layout, state, LEVELS, stairsNeeded(layout));
		assertTrue(open.stream().anyMatch(c -> c.job().equals(MineJobs.Job.segment(64, firstMain.id()))));
		progress.done.add(firstMain.id());
		open = MineJobs.available(layout, state, LEVELS, stairsNeeded(layout));
		long branches = open.stream().filter(c -> c.job().type() == MineJobs.Type.SEGMENT
				&& level.segment(c.job().id()).kind() == MineLayout.Kind.BRANCH).count();
		assertEquals(2, branches);
		// Every step dug: no stairs job any more; widening sections of the outer lane are open.
		state.stairsDone = stairsNeeded(layout);
		open = MineJobs.available(layout, state, LEVELS, stairsNeeded(layout));
		assertFalse(open.stream().anyMatch(c -> c.job().type() == MineJobs.Type.STAIRS));
		long sections = open.stream().filter(c -> c.job().type() == MineJobs.Type.WIDEN).count();
		assertEquals((stairsNeeded(layout) + MineJobs.WIDEN_SECTION - 1) / MineJobs.WIDEN_SECTION, sections);
		for (int k = 0; k < MineJobs.WIDEN_SECTION; k++) {
			state.widen(k);
		}
		assertTrue(MineJobs.widenSteps(layout, state, 0).isEmpty());
		assertEquals(MineJobs.WIDEN_SECTION, MineJobs.widenSteps(layout, state, 1).size());
	}

	@Test
	void twentyMinersWorkAtOnceOnDifferentJobs() {
		MineLayout layout = layout();
		MineState state = new MineState(MineMode.REAL);
		state.stairsDone = 30;
		// Three levels reached, their landings and first main stretches dug.
		for (int y : List.of(64, 58, 52)) {
			MineLayout.Level level = layout.level(y);
			MineState.Level progress = state.levelOrCreate(y);
			int mains = 0;
			for (MineLayout.Segment segment : level.segments()) {
				if (segment.kind() == MineLayout.Kind.LANDING || (segment.kind() == MineLayout.Kind.MAIN && mains++ < 3)) {
					progress.done.add(segment.id());
				}
			}
		}
		state.veins.add(new MineState.Vein(new BlockPos(30, 40, 8), layout.node(MineLayout.stairNode(29)), false));
		List<MineJobs.Candidate> open = MineJobs.available(layout, state, LEVELS, stairsNeeded(layout));
		assertTrue(open.size() >= 20, "plenty of small jobs (" + open.size() + ")");
		// 20 miners spread over the mine pick one after the other, each the nearest job nobody holds.
		Map<String, Integer> holders = new HashMap<>();
		List<Integer> positions = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			positions.add(i % 2 == 0 ? MineLayout.ENTRANCE : MineLayout.stairNode(i));
		}
		for (int miner = 0; miner < 20; miner++) {
			MineJobs.Candidate pick = MineJobs.nearest(layout, open, positions.get(miner), job -> holders.containsKey(job.key()));
			assertNotNull(pick, "miner " + miner + " never waits while work is open");
			holders.put(pick.job().key(), miner);
		}
		assertEquals(20, holders.size(), "20 different jobs");
		Set<Integer> levels = new HashSet<>();
		for (String key : holders.keySet()) {
			for (MineJobs.Candidate candidate : open) {
				if (candidate.job().key().equals(key) && candidate.job().type() == MineJobs.Type.SEGMENT) {
					levels.add(candidate.job().levelY());
				}
			}
		}
		assertTrue(levels.size() >= 3, "the miners spread over the levels " + levels);
		assertTrue(holders.containsKey("stairs"), "someone keeps digging the stairs down");
		assertTrue(holders.containsKey(MineJobs.Job.vein(state.veins.getFirst()).key()), "the pending vein is followed");
		// A miner at a level takes the work right there.
		int junction = layout.nodeAt(layout.level(58).segments().stream().filter(s -> s.kind() == MineLayout.Kind.BRANCH)
				.findFirst().orElseThrow().cells().getFirst().feet());
		MineJobs.Candidate near = MineJobs.nearest(layout, open, junction, job -> job.type() != MineJobs.Type.SEGMENT);
		assertEquals(58, near.job().levelY());
		// The stair front is preferred while it is free (somebody must keep it going down).
		assertEquals(MineJobs.Type.STAIRS, MineJobs.nearest(layout, open, junction, job -> false).job().type());
		// Everything taken: nothing left for a 21st miner.
		Set<String> all = new HashSet<>();
		open.forEach(c -> all.add(c.job().key()));
		assertNull(MineJobs.nearest(layout, open, MineLayout.ENTRANCE, job -> all.contains(job.key())));
	}

	@Test
	void levelFinishedWhenEverySegmentIsDoneOrCutOff() {
		MineLayout layout = layout();
		MineLayout.Level level = layout.level(46);
		MineState.Level progress = new MineState.Level();
		assertFalse(MineJobs.levelFinished(level, progress));
		assertFalse(MineJobs.levelFinished(level, null));
		// The landing given up: everything after it is cut off, the level is over.
		MineLayout.Segment landing = level.segments().getFirst();
		progress.abandoned.add(landing.id());
		assertTrue(MineJobs.levelFinished(level, progress));
		for (MineLayout.Segment segment : level.segments()) {
			if (segment != landing) {
				assertEquals(-1, MineJobs.way(level, progress, segment));
			}
		}
		progress.abandoned.clear();
		for (MineLayout.Segment segment : level.segments()) {
			progress.done.add(segment.id());
		}
		assertTrue(MineJobs.levelFinished(level, progress));
	}

	@Test
	void legacyMinesKeepTheirStairsAndLevel() {
		MineState state = new MineState();
		state.orientation = 3;
		state.stairsDone = 120;
		state.levelY = -51;
		state.layoutKey = "abc";
		state.done.add(4);
		state.finishedLevels.add(-43);
		state.sanitize();
		assertEquals(MineState.FORMAT, state.format);
		assertEquals(0, state.stairWidth, "the 1-wide spiral stays");
		assertEquals(120, state.stairsDone);
		assertEquals(-51, state.levelY, "its level stays one of its levels");
		assertTrue(state.level(-43).finished);
		assertTrue(state.done.isEmpty());
		assertEquals("", state.layoutKey);
		// A fresh mine is planned with the configured width.
		MineState fresh = new MineState();
		fresh.sanitize();
		assertEquals(-1, fresh.orientation);
		assertEquals(MineState.FORMAT, fresh.format);
	}
}
