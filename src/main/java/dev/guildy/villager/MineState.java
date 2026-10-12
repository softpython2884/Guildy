package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Real excavation of a MINE zone (persisted in {@link WorkZone#mine}, shared by all the miners of the zone). The layout
 * itself is never stored: it is rebuilt from the fanion position, the chosen orientation and the staircase width
 * ({@link MineLayout}); only the choices, the progress (stairs, levels, pending veins) and the statistics are. Work
 * reservations between miners are transient ({@link Mines}).
 */
public class MineState {
	/** Data format: 0 = a mine planned before levels existed (one gallery level, 1-wide stairs), 2 = levels. */
	public static final int FORMAT = 2;
	/** Pending veins kept at most (the oldest ones are forgotten). */
	public static final int MAX_VEINS = 64;

	public MineMode mode = MineMode.REAL;
	/** Target level wished by the players (feet Y), null = default of the objective. */
	public @Nullable Integer targetY;
	/** Staircase orientation (0..7) chosen for {@link #origin}, -1 = to choose. */
	public int orientation = -1;
	/** Fanion position the staircase was planned for. */
	public @Nullable BlockPos origin;
	/** Side of the spiral (inner lane) when it was planned. */
	public int stairSide;
	/** Staircase width chosen with the orientation: 2 = two lanes, 1 = one lane, 0 = planned before levels (1 lane). */
	public int stairWidth;
	/** Inner lane steps dug (prefix). */
	public int stairsDone;
	/** First stair step that could not be dug (-1 = none): no level below it. */
	public int stairsBlockedAt = -1;
	/** Orientations given up (stairs blocked right under the surface). */
	public List<Integer> rejected = new ArrayList<>();
	/** Outer lane steps widened (bit set, 64 steps per value). */
	public List<Long> widened = new ArrayList<>();
	/** Deepest level opened for now (null = the target level): deeper ones open once every level above is finished. */
	public @Nullable Integer bottomY;
	/** Level of a mine planned before levels existed (kept as one of its levels), null otherwise. */
	public @Nullable Integer levelY;
	/** Identity of the gallery layout the level progress refers to. */
	public String layoutKey = "";
	/** Progress of each level. */
	public List<Level> levels = new ArrayList<>();
	/** Veins seen from the tunnels but out of reach: followed later by any miner (approach tunnels). */
	public List<Vein> veins = new ArrayList<>();
	/** Levels finished (feet Y), kept for the statistics. */
	public List<Integer> finishedLevels = new ArrayList<>();
	/** Before levels existed: finished segments of {@link #levelY} (cleared by the migration). */
	public List<Integer> done = new ArrayList<>();
	/** Before levels existed: segments of {@link #levelY} given up (cleared by the migration). */
	public List<Integer> abandoned = new ArrayList<>();
	/** Nothing left to dig (or no way down): waits for a new depth or a reset. */
	public boolean exhausted;
	public long blocksMined;
	public long oresFound;
	/** Items of the "objectifs prioritaires" found. */
	public long wantedFound;
	/** Deepest feet level dug, null = nothing yet. */
	public @Nullable Integer deepestY;
	/** Lang key of the last problem met ("" = none) and where. */
	public String issue = "";
	public @Nullable BlockPos issuePos;
	/** Bumped by a reset: the miners start over. */
	public int epoch;
	public int format;
	/**
	 * Quarry: columns (x, z as {@code BlockPos.asLong(x, 0, z)}) left as natural support columns under a non-natural
	 * block (never dug on any bench), at most {@link #MAX_SUPPORT_COLUMNS}.
	 */
	public List<Long> supportColumns = new ArrayList<>();
	/** Quarry: bumped when a column is given up (the benches are planned again around it). */
	public int quarryRevision;
	/** Quarry: blocks dug by the benches. */
	public long quarried;

	/** Support columns kept at most (beyond, a column given up under a building is only skipped on its bench). */
	public static final int MAX_SUPPORT_COLUMNS = 4096;

	/** Progress of one level (or of one bench of a quarry). */
	public static class Level {
		public int y;
		/** Finished segments. */
		public List<Integer> done = new ArrayList<>();
		/** Segments given up (lava, building...). */
		public List<Integer> abandoned = new ArrayList<>();
		public boolean finished;
		public long ores;
		public long wanted;
		/** Quarry bench: columns dug (bit set over the quarry square, see {@link Quarry#column}). */
		public List<Long> cleared = new ArrayList<>();
		/** Quarry bench: columns given up on this bench (water above, cave-in...). */
		public List<Long> skipped = new ArrayList<>();
		/** Quarry bench: blocks dug. */
		public long quarried;

		public Level() {
		}

		Level(int y) {
			this.y = y;
		}
	}

	/** A vein to follow: {@code seed} = its first block, {@code stand} = the mine node it was seen from. */
	public static class Vein {
		public BlockPos seed;
		public BlockPos stand;
		/** A wanted block (else an ore). */
		public boolean wanted;

		public Vein() {
		}

		Vein(BlockPos seed, BlockPos stand, boolean wanted) {
			this.seed = seed.immutable();
			this.stand = stand.immutable();
			this.wanted = wanted;
		}
	}

	public MineState() {
	}

	public MineState(MineMode mode) {
		this.mode = mode;
		this.format = FORMAT;
	}

	/** Forgets the staircase and the levels (fanion moved, reset): the next miner plans again. Statistics are kept. */
	void resetPlan() {
		orientation = -1;
		origin = null;
		stairSide = 0;
		stairWidth = 0;
		stairsDone = 0;
		stairsBlockedAt = -1;
		widened.clear();
		levelY = null;
		resetLevels();
		veins.clear();
		format = FORMAT;
	}

	/**
	 * Depth or objective changed: the levels are chosen again (the staircase is kept; levels on the same grid keep their
	 * progress, see {@link Mines#plan}).
	 */
	void resetLevel() {
		bottomY = null;
		exhausted = false;
	}

	/** Every level starts over (other galleries): dug cells are walked through quickly. */
	void resetLevels() {
		layoutKey = "";
		levels.clear();
		finishedLevels.clear();
		done.clear();
		abandoned.clear();
		bottomY = null;
		exhausted = false;
		supportColumns.clear();
		quarryRevision++;
	}

	/** Quarry: the column stays as a natural support column (false when it already was, or when the list is full). */
	boolean addSupport(int x, int z) {
		long packed = BlockPos.asLong(x, 0, z);
		if (supportColumns.contains(packed) || supportColumns.size() >= MAX_SUPPORT_COLUMNS) {
			return false;
		}
		supportColumns.add(packed);
		return true;
	}

	@Nullable Level level(int y) {
		for (Level level : levels) {
			if (level.y == y) {
				return level;
			}
		}
		return null;
	}

	Level levelOrCreate(int y) {
		Level level = level(y);
		if (level == null) {
			level = new Level(y);
			levels.add(level);
		}
		return level;
	}

	boolean widened(int step) {
		int word = step >> 6;
		return step >= 0 && word < widened.size() && (widened.get(word) & (1L << (step & 63))) != 0;
	}

	void widen(int step) {
		if (step < 0) {
			return;
		}
		int word = step >> 6;
		while (widened.size() <= word) {
			widened.add(0L);
		}
		widened.set(word, widened.get(word) | (1L << (step & 63)));
	}

	void issue(String key, @Nullable BlockPos pos) {
		issue = key;
		issuePos = pos == null ? null : pos.immutable();
	}

	/** Fixes nulls / out-of-range values after deserialization; migrates mines planned before levels existed. */
	void sanitize() {
		if (mode == null) {
			mode = MineMode.REAL;
		}
		orientation = orientation < 0 || orientation >= MineLayout.ORIENTATIONS ? -1 : orientation;
		stairWidth = Math.max(0, Math.min(2, stairWidth));
		stairsDone = Math.max(0, stairsDone);
		stairsBlockedAt = Math.max(-1, stairsBlockedAt);
		rejected = rejected == null ? new ArrayList<>() : new ArrayList<>(rejected);
		rejected.removeIf(o -> o == null || o < 0 || o >= MineLayout.ORIENTATIONS);
		widened = widened == null ? new ArrayList<>() : new ArrayList<>(widened);
		widened.replaceAll(word -> word == null ? 0L : word);
		if (layoutKey == null) {
			layoutKey = "";
		}
		levels = levels == null ? new ArrayList<>() : new ArrayList<>(levels);
		levels.removeIf(level -> level == null);
		for (Level level : levels) {
			level.done = level.done == null ? new ArrayList<>() : new ArrayList<>(level.done);
			level.done.removeIf(id -> id == null);
			level.abandoned = level.abandoned == null ? new ArrayList<>() : new ArrayList<>(level.abandoned);
			level.abandoned.removeIf(id -> id == null);
			level.ores = Math.max(0, level.ores);
			level.wanted = Math.max(0, level.wanted);
			level.cleared = level.cleared == null ? new ArrayList<>() : new ArrayList<>(level.cleared);
			level.cleared.replaceAll(word -> word == null ? 0L : word);
			level.skipped = level.skipped == null ? new ArrayList<>() : new ArrayList<>(level.skipped);
			level.skipped.replaceAll(word -> word == null ? 0L : word);
			level.quarried = Math.max(0, level.quarried);
		}
		supportColumns = supportColumns == null ? new ArrayList<>() : new ArrayList<>(supportColumns);
		supportColumns.removeIf(column -> column == null);
		while (supportColumns.size() > MAX_SUPPORT_COLUMNS) {
			supportColumns.removeLast();
		}
		quarried = Math.max(0, quarried);
		veins = veins == null ? new ArrayList<>() : new ArrayList<>(veins);
		veins.removeIf(vein -> vein == null || vein.seed == null || vein.stand == null);
		while (veins.size() > MAX_VEINS) {
			veins.removeFirst();
		}
		done = done == null ? new ArrayList<>() : new ArrayList<>(done);
		done.removeIf(id -> id == null);
		abandoned = abandoned == null ? new ArrayList<>() : new ArrayList<>(abandoned);
		abandoned.removeIf(id -> id == null);
		finishedLevels = finishedLevels == null ? new ArrayList<>() : new ArrayList<>(finishedLevels);
		finishedLevels.removeIf(y -> y == null);
		blocksMined = Math.max(0, blocksMined);
		oresFound = Math.max(0, oresFound);
		wantedFound = Math.max(0, wantedFound);
		if (issue == null) {
			issue = "";
		}
		if (format < FORMAT) {
			migrate();
		}
	}

	/**
	 * A mine planned before levels existed: its 1-wide staircase stays (width 0: same cells, same old galleries) and its
	 * gallery level becomes one of its levels (finished ones stay finished); the new levels are added on the way down.
	 */
	void migrate() {
		if (orientation < 0) {
			// Nothing planned yet: planned like a new mine.
			levelY = null;
			stairWidth = 0;
		} else {
			stairWidth = 0;
			for (Integer y : finishedLevels) {
				levelOrCreate(y).finished = true;
			}
			if (levelY != null && exhausted) {
				levelOrCreate(levelY).finished = true;
			}
		}
		layoutKey = "";
		done.clear();
		abandoned.clear();
		bottomY = null;
		exhausted = false;
		format = FORMAT;
	}
}
