package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Real excavation of a MINE zone (persisted in {@link WorkZone#mine}, shared by all the miners of the zone). The layout
 * itself is never stored: it is rebuilt from the fanion position, the chosen orientation and the level
 * ({@link MineLayout}); only the choices, the progress and the statistics are. Block reservations between miners are
 * transient ({@link Mines}).
 */
public class MineState {
	public MineMode mode = MineMode.REAL;
	/** Gallery level wished by the players (feet Y), null = default of the objective. */
	public @Nullable Integer targetY;
	/** Staircase orientation (0..7) chosen for {@link #origin}, -1 = to choose. */
	public int orientation = -1;
	/** Fanion position the staircase was planned for. */
	public @Nullable BlockPos origin;
	/** Side of the spiral when it was planned. */
	public int stairSide;
	/** Stair steps dug (prefix). */
	public int stairsDone;
	/** First stair step that could not be dug (-1 = none): no gallery below it. */
	public int stairsBlockedAt = -1;
	/** Orientations given up (stairs blocked right under the surface). */
	public List<Integer> rejected = new ArrayList<>();
	/** Gallery level being worked (feet Y), null = to choose. */
	public @Nullable Integer levelY;
	/** Identity of the gallery layout {@link #done} / {@link #abandoned} refer to. */
	public String layoutKey = "";
	/** Finished gallery segments of the level. */
	public List<Integer> done = new ArrayList<>();
	/** Gallery segments given up (lava, building...). */
	public List<Integer> abandoned = new ArrayList<>();
	/** Levels whose galleries are finished. */
	public List<Integer> finishedLevels = new ArrayList<>();
	/** Nothing left to dig (or no way down): waits for a new depth or a reset. */
	public boolean exhausted;
	public long blocksMined;
	public long oresFound;
	/** Deepest feet level dug, null = nothing yet. */
	public @Nullable Integer deepestY;
	/** Lang key of the last problem met ("" = none) and where. */
	public String issue = "";
	public @Nullable BlockPos issuePos;
	/** Bumped by a reset: the miners start over. */
	public int epoch;

	public MineState() {
	}

	public MineState(MineMode mode) {
		this.mode = mode;
	}

	/** Forgets the staircase and the galleries (fanion moved, reset): the next miner plans again. Statistics are kept. */
	void resetPlan() {
		orientation = -1;
		origin = null;
		stairSide = 0;
		stairsDone = 0;
		stairsBlockedAt = -1;
		resetLevel();
	}

	/** Depth or objective changed: the gallery level is chosen again (the staircase is kept). */
	void resetLevel() {
		levelY = null;
		layoutKey = "";
		done.clear();
		abandoned.clear();
		finishedLevels.clear();
		exhausted = false;
	}

	void issue(String key, @Nullable BlockPos pos) {
		issue = key;
		issuePos = pos == null ? null : pos.immutable();
	}

	/** Fixes nulls / out-of-range values after deserialization. */
	void sanitize() {
		if (mode == null) {
			mode = MineMode.REAL;
		}
		orientation = orientation < 0 || orientation >= MineLayout.ORIENTATIONS ? -1 : orientation;
		stairsDone = Math.max(0, stairsDone);
		stairsBlockedAt = Math.max(-1, stairsBlockedAt);
		rejected = rejected == null ? new ArrayList<>() : new ArrayList<>(rejected);
		rejected.removeIf(o -> o == null || o < 0 || o >= MineLayout.ORIENTATIONS);
		if (layoutKey == null) {
			layoutKey = "";
		}
		done = done == null ? new ArrayList<>() : new ArrayList<>(done);
		done.removeIf(id -> id == null);
		abandoned = abandoned == null ? new ArrayList<>() : new ArrayList<>(abandoned);
		abandoned.removeIf(id -> id == null);
		finishedLevels = finishedLevels == null ? new ArrayList<>() : new ArrayList<>(finishedLevels);
		finishedLevels.removeIf(y -> y == null);
		blocksMined = Math.max(0, blocksMined);
		oresFound = Math.max(0, oresFound);
		if (issue == null) {
			issue = "";
		}
	}
}
