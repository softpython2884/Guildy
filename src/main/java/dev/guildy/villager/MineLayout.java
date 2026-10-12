package dev.guildy.villager;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * Pure geometry of a real mine (no world access: unit tested).
 * <p>
 * <b>Staircase.</b> From the fanion, a spiral goes down one block per step around a square pillar, with 3 blocks of
 * headroom per step so that the vanilla ground navigation walks it both ways (it cannot climb ladders). Its inner lane
 * is a 1-wide spiral (the walking path, dug first); a 2-wide staircase adds an outer lane beside it (each step then has
 * 2 cells, 4 at the corners: room for many miners to pass each other), dug apart. Mines planned before levels existed
 * keep their 1-wide spiral and their old galleries (width 0).
 * <p>
 * <b>Levels.</b> A level is the set of galleries at the height of one stair step: a small landing next to the
 * staircase, then a 2-high main tunnel leaving the spiral outwards, with side branches every few blocks (or quarry
 * rooms). Levels are built on demand, everything stays inside the zone square.
 * <p>
 * <b>Walking graph.</b> A tree rooted at the fanion ({@link #ENTRANCE}): the stairs (inner lane), then for each level
 * its landing, main tunnel and branches. Work segments: cells dug in order, cell i from node {@code stands[i]}.
 * <p>
 * <b>Quarry.</b> With quarry settings ({@link #quarry(BlockPos, int, int, int[], Settings, Quarry.Settings, Quarry.Facts)})
 * the levels are the benches of a {@link Quarry}: an opening segment (outer lane cell, opening through the shell, first
 * bench cell) then its strips, every bench cell a node of the walking graph.
 */
public final class MineLayout {
	/** 4 first directions × 2 turning senses. */
	public static final int ORIENTATIONS = 8;
	public static final int STAIR_HEIGHT = 3;
	public static final int TUNNEL_HEIGHT = 2;
	public static final int MIN_SIDE = 3;
	public static final int MAX_SIDE = 6;
	/** Node of the fanion (root of the walking graph). */
	public static final int ENTRANCE = 0;
	/** {@link #nodeLevel} of the entrance and of the stair nodes. */
	public static final int STAIRS_LEVEL = Integer.MIN_VALUE;
	/** Highest cell of any level (quarry rooms): a level at y holds blocks y - 1 .. y + MAX_CELL_HEIGHT - 1. */
	public static final int MAX_CELL_HEIGHT = 5;
	/** Horizontal directions: north, east, south, west. */
	private static final int[] DX = {0, 1, 0, -1};
	private static final int[] DZ = {-1, 0, 1, 0};

	public enum Kind {
		LANDING,
		MAIN,
		BRANCH,
		ROOM,
		/** Quarry: from the stairs onto a bench (outer lane cell, opening through the shell, first bench cell). */
		OPENING,
		/** Quarry: a straight run of bench cells. */
		STRIP
	}

	/** Column of blocks a villager walks in (or a quarry column): {@code height} blocks from {@code feet} up. */
	public record Cell(BlockPos feet, int height) {
		public BlockPos floor() {
			return feet.below();
		}

		public BlockPos top() {
			return feet.above(height - 1);
		}
	}

	/**
	 * Dimensions (villagers.json5, realMining). {@code stairWidth}: 2 = two-block staircase, 1 = one block, 0 = one
	 * block with the galleries of mines planned before levels existed (no landing, main tunnel right off the stairs).
	 */
	public record Settings(int stairSide, int stairWidth, int mainLength, int branchSpacing, int branchLength, int roomWidth,
			int roomLength, int roomHeight, int roomGap) {
	}

	/**
	 * Work unit of one miner: cells dug in order, cell i from node {@code stands[i]}; available once segment
	 * {@code after} of the same level is done (-1: as soon as the stairs reach the level). {@code ordinal}: 1-based
	 * rank among the segments of its kind (branches and rooms share one count).
	 */
	public record Segment(int id, Kind kind, List<Cell> cells, int[] stands, int after, int ordinal) {
	}

	/** Galleries of one level (feet height {@link #y}), dug from the stair step at that height. */
	public static final class Level {
		private final int y;
		private final int step;
		private int mainDir = -1;
		private int mainLength;
		private int galleryCount;
		private final List<Segment> segments = new ArrayList<>();
		private final Map<Integer, Segment> byId = new HashMap<>();
		private final LongOpenHashSet structure = new LongOpenHashSet();
		private final IntArrayList nodes = new IntArrayList();
		/** Quarry bench: its plan (null for gallery levels). */
		private Quarry.@Nullable Bench bench;
		/** Quarry bench: nodes off the bench grid (opening through the shell). */
		private final IntArrayList extra = new IntArrayList();

		private Level(int y, int step) {
			this.y = y;
			this.step = step;
		}

		public int y() {
			return y;
		}

		/** Plan of a quarry bench, null for gallery levels. */
		public Quarry.@Nullable Bench bench() {
			return bench;
		}

		/** Stair step at this height (-1: galleries at the fanion's own height). */
		public int step() {
			return step;
		}

		/** Segments in work order (ids 1..n). */
		public List<Segment> segments() {
			return segments;
		}

		public @Nullable Segment segment(int id) {
			return byId.get(id);
		}

		/** Branches / quarry rooms. */
		public int galleryCount() {
			return galleryCount;
		}

		/** Direction (0 north, 1 east, 2 south, 3 west) of the main tunnel, -1 if none. */
		public int mainDirection() {
			return mainDir;
		}

		/** Farthest main tunnel cell from the staircase (blocks). */
		public int mainLength() {
			return mainLength;
		}

		public int cellCount() {
			int total = 0;
			for (Segment segment : segments) {
				total += segment.cells().size();
			}
			return total;
		}

		public boolean contains(BlockPos pos) {
			return structure.contains(pos.asLong());
		}
	}

	private final BlockPos origin;
	private final int orientation;
	private final int side;
	private final int width;
	private final int[] bounds;
	private final boolean rooms;
	private final Settings settings;
	/** Inner lane: step k (the walking path). */
	private final List<Cell> stairs = new ArrayList<>();
	/** Outer lane cells of step k (2-wide staircases). */
	private final List<List<Cell>> outer = new ArrayList<>();
	private final LongOpenHashSet stairStructure = new LongOpenHashSet();
	private final TreeMap<Integer, Level> levels = new TreeMap<>();
	// walking graph
	private final List<BlockPos> nodes = new ArrayList<>();
	private final IntArrayList parents = new IntArrayList();
	private final IntArrayList depths = new IntArrayList();
	private final IntArrayList nodeLevels = new IntArrayList();
	private final IntArrayList nodeSegments = new IntArrayList();
	private final IntArrayList nodeCells = new IntArrayList();
	private final Long2IntOpenHashMap nodeByPos = new Long2IntOpenHashMap();
	/** Quarry of the mine (benches instead of galleries), null for galleries. */
	private final @Nullable Quarry quarry;
	private final Quarry.Facts facts;
	private final int lowestY;

	private MineLayout(BlockPos origin, int orientation, int lowestY, int[] bounds, boolean rooms, Settings settings,
			Quarry.@Nullable Settings quarrySettings, Quarry.Facts facts) {
		this.origin = origin.immutable();
		this.orientation = Math.floorMod(orientation, ORIENTATIONS);
		this.side = clampSide(settings.stairSide());
		this.width = settings.stairWidth();
		this.bounds = bounds.clone();
		this.rooms = rooms;
		this.settings = settings;
		this.quarry = quarrySettings == null ? null : new Quarry(this.origin, this.orientation, side, width, bounds, quarrySettings);
		this.facts = facts;
		this.lowestY = lowestY;
		nodeByPos.defaultReturnValue(-1);
		addNode(this.origin, -1, STAIRS_LEVEL, -1, -1);
		stairStructure.add(this.origin.asLong());
		stairStructure.add(this.origin.below().asLong());
		int steps = Math.max(0, this.origin.getY() - Math.min(lowestY, this.origin.getY()));
		for (int k = 0; k < steps; k++) {
			Cell cell = stairCell(this.origin, this.orientation, side, width, k);
			stairs.add(cell);
			addNode(cell.feet(), k, STAIRS_LEVEL, -1, k);
			addStructure(stairStructure, cell);
			List<Cell> lane = outerCells(this.origin, this.orientation, side, width, k);
			outer.add(lane);
			for (Cell extra : lane) {
				addStructure(stairStructure, extra);
			}
		}
	}

	/**
	 * Layout of the mine of a fanion at {@code origin} (feet position of the banner): stairs down to {@code lowestY}
	 * (feet height of the deepest possible step), levels inside {@code bounds} = {minX, minZ, maxX, maxZ}.
	 *
	 * @param rooms quarry rooms instead of side branches
	 */
	public static MineLayout build(BlockPos origin, int orientation, int lowestY, int[] bounds, boolean rooms, Settings settings) {
		return new MineLayout(origin, orientation, lowestY, bounds, rooms, settings, null, Quarry.Facts.NONE);
	}

	/**
	 * Layout of a quarry: the same staircase, benches instead of galleries ({@link Quarry}); {@code facts}: what the
	 * miners learnt (support columns, skipped columns, benches being worked), read when a bench is planned.
	 */
	public static MineLayout quarry(BlockPos origin, int orientation, int lowestY, int[] bounds, Settings settings,
			Quarry.Settings quarry, Quarry.Facts facts) {
		return new MineLayout(origin, orientation, lowestY, bounds, false, settings, quarry, facts);
	}

	// ------------------------------------------------------------------ spiral

	public static int clampSide(int side) {
		return Math.max(MIN_SIDE, Math.min(MAX_SIDE, side));
	}

	/** Steps of one turn of the spiral. */
	public static int perimeter(int side) {
		return 4 * (clampSide(side) - 1);
	}

	/** Two-lane staircase. */
	public static boolean wide(int width) {
		return width >= 2;
	}

	/** Side of the dug square (the outer lane of a 2-wide staircase surrounds the inner spiral). */
	public static int footprintSide(int side, int width) {
		return clampSide(side) + (wide(width) ? 2 : 0);
	}

	/** First direction (0 north, 1 east, 2 south, 3 west) of an orientation. */
	public static int firstDirection(int orientation) {
		return Math.floorMod(orientation, ORIENTATIONS) & 3;
	}

	/** Direction of the second side of the square: a right turn for orientations 0-3, a left turn for 4-7. */
	public static int sideDirection(int orientation) {
		return turn(firstDirection(orientation), Math.floorMod(orientation, ORIENTATIONS) < 4 ? 1 : -1);
	}

	static int turn(int direction, int quarters) {
		return Math.floorMod(direction + quarters, 4);
	}

	static int dx(int direction) {
		return DX[direction];
	}

	static int dz(int direction) {
		return DZ[direction];
	}

	/** Column (u, v) of the square: u along the first direction (0 = next to the fanion), v along the second one. */
	private static BlockPos column(BlockPos origin, int orientation, int u, int v, int y) {
		int d0 = firstDirection(orientation);
		int d1 = sideDirection(orientation);
		return new BlockPos(origin.getX() + DX[d0] * (1 + u) + DX[d1] * v, y, origin.getZ() + DZ[d0] * (1 + u) + DZ[d1] * v);
	}

	/** Inner lane step {@code k} of a 1-wide staircase (mines planned before wide staircases). */
	public static Cell stairCell(BlockPos origin, int orientation, int side, int k) {
		return stairCell(origin, orientation, side, 1, k);
	}

	/**
	 * Inner lane step {@code k} (0 = the step next to the fanion): feet one block lower per step, 3 blocks high. The
	 * steps walk the border of a side×side square, so one turn of the spiral goes down {@link #perimeter} blocks and the
	 * next turn passes under the previous one with plenty of rock between them. The inner lane of a 2-wide staircase is
	 * the same spiral moved one block away from the fanion (its outer lane passes between them).
	 */
	public static Cell stairCell(BlockPos origin, int orientation, int side, int width, int k) {
		int[] local = stairLocal(side, k);
		int shift = wide(width) ? 1 : 0;
		return new Cell(column(origin, orientation, local[0] + shift, local[1] + shift, origin.getY() - 1 - k), STAIR_HEIGHT);
	}

	/**
	 * Outer lane cells of step {@code k} (2-wide staircases, empty otherwise): the cells next to the inner step on its
	 * outer side(s), at the same height; at a corner both sides and the diagonal (a 2×2 landing of the turn).
	 */
	public static List<Cell> outerCells(BlockPos origin, int orientation, int side, int width, int k) {
		if (!wide(width)) {
			return List.of();
		}
		BlockPos inner = stairCell(origin, orientation, side, width, k).feet();
		List<Integer> directions = outward(orientation, side, k);
		List<Cell> cells = new ArrayList<>(3);
		for (int direction : directions) {
			cells.add(new Cell(inner.offset(DX[direction], 0, DZ[direction]), STAIR_HEIGHT));
		}
		if (directions.size() == 2) {
			int a = directions.get(0);
			int b = directions.get(1);
			cells.add(new Cell(inner.offset(DX[a] + DX[b], 0, DZ[a] + DZ[b]), STAIR_HEIGHT));
		}
		return cells;
	}

	/** Every cell of step {@code k}: the inner one first. */
	public static List<Cell> stepCells(BlockPos origin, int orientation, int side, int width, int k) {
		List<Cell> cells = new ArrayList<>(4);
		cells.add(stairCell(origin, orientation, side, width, k));
		cells.addAll(outerCells(origin, orientation, side, width, k));
		return cells;
	}

	/** Position {a, b} of step k in the inner square (a along the first direction, b along the second). */
	static int[] stairLocal(int side, int k) {
		int s = clampSide(side) - 1;
		int p = Math.floorMod(k, 4 * s);
		int leg = p / s;
		int offset = p % s;
		return switch (leg) {
			case 0 -> new int[]{offset, 0};
			case 1 -> new int[]{s, offset};
			case 2 -> new int[]{s - offset, s};
			default -> new int[]{0, s - offset};
		};
	}

	/** Columns {x, z} of the whole dug square (steps of both lanes and pillar). */
	public static List<int[]> footprint(BlockPos origin, int orientation, int side, int width) {
		int s = footprintSide(side, width);
		List<int[]> columns = new ArrayList<>(s * s);
		for (int u = 0; u < s; u++) {
			for (int v = 0; v < s; v++) {
				BlockPos pos = column(origin, orientation, u, v, 0);
				columns.add(new int[]{pos.getX(), pos.getZ()});
			}
		}
		return columns;
	}

	/** Columns of a 1-wide staircase. */
	public static List<int[]> footprint(BlockPos origin, int orientation, int side) {
		return footprint(origin, orientation, side, 1);
	}

	/** The staircase of this orientation stays inside the bounds. */
	public static boolean fits(BlockPos origin, int orientation, int side, int width, int[] bounds) {
		for (int[] column : footprint(origin, orientation, side, width)) {
			if (!inside(column[0], column[1], bounds)) {
				return false;
			}
		}
		return true;
	}

	public static boolean fits(BlockPos origin, int orientation, int side, int[] bounds) {
		return fits(origin, orientation, side, 1, bounds);
	}

	static boolean inside(int x, int z, int[] bounds) {
		return x >= bounds[0] && x <= bounds[2] && z >= bounds[1] && z <= bounds[3];
	}

	/** Blocks (at most {@code max}) one can go from (x, z) in a direction without leaving the bounds. */
	static int available(int x, int z, int direction, int max, int[] bounds) {
		int length = 0;
		while (length < max && inside(x + DX[direction] * (length + 1), z + DZ[direction] * (length + 1), bounds)) {
			length++;
		}
		return length;
	}

	/** Directions leading out of the square from step k (two at a corner). */
	static List<Integer> outward(int orientation, int side, int k) {
		int s = clampSide(side) - 1;
		int[] local = stairLocal(side, k);
		int d0 = firstDirection(orientation);
		int d1 = sideDirection(orientation);
		List<Integer> directions = new ArrayList<>(2);
		if (local[0] == s) {
			directions.add(d0);
		}
		if (local[1] == s) {
			directions.add(d1);
		}
		if (local[0] == 0) {
			directions.add(turn(d0, 2));
		}
		if (local[1] == 0) {
			directions.add(turn(d1, 2));
		}
		return directions;
	}

	// ------------------------------------------------------------------ levels

	/**
	 * Galleries at feet height {@code y} (built on first use), or null when no stair step stands at that height (above
	 * the first step, below the deepest one). {@code y} = the fanion's own height: galleries right from the fanion
	 * (a fanion planted just above the bedrock).
	 */
	public @Nullable Level level(int y) {
		Level level = levels.get(y);
		if (level != null) {
			return level;
		}
		int step = origin.getY() - 1 - y;
		if (step < -1 || step >= stairs.size()) {
			return null;
		}
		Quarry plan = quarry;
		if (plan != null && (!plan.isBench(y) || !facts.open(y))) {
			return null;
		}
		level = new Level(y, step);
		levels.put(y, level);
		if (plan != null) {
			buildBench(level, plan);
		} else {
			buildLevel(level);
		}
		return level;
	}

	/** Quarry: the opening segment (id 1), then one segment per strip (id = strip index + 2), every cell a node. */
	private void buildBench(Level level, Quarry plan) {
		int y = level.y;
		Quarry.Bench bench = plan.bench(y, stairs.size() - 1, facts);
		level.bench = bench;
		Quarry.Opening opening = bench.opening();
		if (opening == null) {
			return;
		}
		List<Cell> way = new ArrayList<>(3);
		IntArrayList wayStands = new IntArrayList(3);
		int parent = stairNode(opening.step());
		Cell outer = opening.outer();
		if (outer != null) {
			way.add(outer);
			wayStands.add(parent);
			parent = addNode(outer.feet(), parent, y, 1, way.size() - 1);
			level.extra.add(parent);
		}
		way.add(opening.wall());
		wayStands.add(parent);
		parent = addNode(opening.wall().feet(), parent, y, 1, way.size() - 1);
		level.extra.add(parent);
		way.add(opening.entry());
		wayStands.add(parent);
		int entryNode = addNode(opening.entry().feet(), parent, y, 1, way.size() - 1);
		level.nodes.add(entryNode);
		add(level, new Segment(1, Kind.OPENING, List.copyOf(way), wayStands.toIntArray(), -1, 1));
		int[] nodeOf = new int[plan.columns()];
		java.util.Arrays.fill(nodeOf, -1);
		nodeOf[bench.root()] = entryNode;
		int height = plan.benchHeight();
		List<Quarry.Strip> strips = bench.strips();
		for (int i = 0; i < strips.size(); i++) {
			Quarry.Strip strip = strips.get(i);
			int id = i + 2;
			int[] columns = strip.columns();
			List<Cell> cells = new ArrayList<>(columns.length);
			int[] stands = new int[columns.length];
			int previous = nodeOf[strip.parent()];
			for (int j = 0; j < columns.length; j++) {
				int c = columns[j];
				Cell cell = new Cell(new BlockPos(plan.columnX(c), y, plan.columnZ(c)), height);
				cells.add(cell);
				stands[j] = previous;
				previous = addNode(cell.feet(), previous, y, id, j);
				nodeOf[c] = previous;
				level.nodes.add(previous);
			}
			int parentStrip = bench.stripOf(strip.parent());
			add(level, new Segment(id, Kind.STRIP, List.copyOf(cells), stands, parentStrip < 0 ? 1 : parentStrip + 2, i + 1));
		}
	}

	/** Levels built so far. */
	public Collection<Level> builtLevels() {
		return levels.values();
	}

	private void buildLevel(Level level) {
		int y = level.y;
		BlockPos anchor = level.step < 0 ? origin : stairs.get(level.step).feet();
		int anchorNode = level.step < 0 ? ENTRANCE : stairNode(level.step);
		List<Integer> candidates = level.step < 0 ? List.of(0, 1, 2, 3) : outward(orientation, side, level.step);
		boolean landing = settings.stairWidth() > 0;
		// Cells of the staircase square next to the anchor (outer lane of a 2-wide staircase): the landing starts after.
		int first = level.step >= 0 && wide(width) ? 2 : 1;
		int maxMain = Math.max(0, settings.mainLength());
		int best = -1;
		for (int direction : candidates) {
			int length = available(anchor.getX(), anchor.getZ(), direction, maxMain, bounds);
			if (length > best) {
				best = length;
				level.mainDir = direction;
			}
		}
		int reach = Math.max(0, best);
		level.mainLength = reach;
		if (level.mainDir < 0 || reach < first) {
			return;
		}
		int dir = level.mainDir;
		int parent = anchorNode;
		int mainStart = 1;
		int previous = -1;
		if (landing) {
			int id = level.segments.size() + 1;
			List<Cell> cells = new ArrayList<>();
			IntArrayList stands = new IntArrayList();
			if (first == 2) {
				// The outer lane cell beside the landing step: the way in, dug even before the staircase is widened.
				Cell entry = new Cell(at(anchor, dir, 1, y), STAIR_HEIGHT);
				cells.add(entry);
				stands.add(parent);
				parent = addNode(entry.feet(), parent, y, id, 0);
				level.nodes.add(parent);
				addStructure(level.structure, entry);
			}
			int last = Math.min(first + 1, reach);
			for (int j = first; j <= last; j++) {
				Cell center = new Cell(at(anchor, dir, j, y), TUNNEL_HEIGHT);
				cells.add(center);
				stands.add(parent);
				int centerNode = addNode(center.feet(), parent, y, id, cells.size() - 1);
				level.nodes.add(centerNode);
				addStructure(level.structure, center);
				for (int sign : new int[]{-1, 1}) {
					int across = turn(dir, sign);
					BlockPos pos = center.feet().offset(DX[across], 0, DZ[across]);
					if (!inside(pos.getX(), pos.getZ(), bounds)) {
						continue;
					}
					Cell cell = new Cell(pos, TUNNEL_HEIGHT);
					cells.add(cell);
					stands.add(centerNode);
					level.nodes.add(addNode(pos, centerNode, y, id, cells.size() - 1));
					addStructure(level.structure, cell);
				}
				parent = centerNode;
			}
			add(level, new Segment(id, Kind.LANDING, List.copyOf(cells), stands.toIntArray(), -1, 1));
			previous = id;
			mainStart = last + 1;
		}
		if (mainStart > reach) {
			return;
		}
		int roomLength = Math.max(1, settings.roomLength());
		int roomPeriod = roomLength + Math.max(1, settings.roomGap());
		int roomHeight = Math.max(TUNNEL_HEIGHT, Math.min(MAX_CELL_HEIGHT, settings.roomHeight()));
		// Quarry rooms leave one corridor cell after the landing (legacy galleries: from the 2nd cell).
		int roomBase = landing ? mainStart + 1 : 2;
		Cell[] mainCells = new Cell[reach + 1];
		int[] mainNodes = new int[reach + 1];
		mainNodes[mainStart - 1] = parent;
		for (int j = mainStart; j <= reach; j++) {
			boolean trunk = rooms && j >= roomBase && (j - roomBase) % roomPeriod < roomLength;
			Cell cell = new Cell(at(anchor, dir, j, y), trunk ? roomHeight : TUNNEL_HEIGHT);
			mainCells[j] = cell;
			// The segment is not known yet: set when the segments are cut.
			mainNodes[j] = addNode(cell.feet(), mainNodes[j - 1], y, -1, -1);
			level.nodes.add(mainNodes[j]);
			addStructure(level.structure, cell);
		}
		if (rooms) {
			cutRooms(level, mainCells, mainNodes, mainStart, reach, roomBase, roomLength, roomPeriod, roomHeight, previous);
		} else {
			cutBranches(level, mainCells, mainNodes, mainStart, reach, previous);
		}
	}

	private static BlockPos at(BlockPos anchor, int direction, int distance, int y) {
		return new BlockPos(anchor.getX() + DX[direction] * distance, y, anchor.getZ() + DZ[direction] * distance);
	}

	private void cutBranches(Level level, Cell[] mainCells, int[] mainNodes, int mainStart, int reach, int previous) {
		int spacing = Math.max(2, settings.branchSpacing());
		int prevMain = previous;
		for (int start = mainStart; start <= reach; start += spacing) {
			int end = Math.min(start + spacing - 1, reach);
			Segment main = mainSegment(level, mainCells, mainNodes, start, end, prevMain);
			prevMain = main.id();
			if (end - start + 1 < spacing) {
				break;
			}
			BlockPos junction = mainCells[end].feet();
			for (int sign : new int[]{-1, 1}) {
				int direction = turn(level.mainDir, sign);
				int length = available(junction.getX(), junction.getZ(), direction, Math.max(0, settings.branchLength()), bounds);
				if (length <= 0) {
					continue;
				}
				int id = level.segments.size() + 1;
				List<Cell> cells = new ArrayList<>(length);
				int[] stands = new int[length];
				int node = mainNodes[end];
				for (int t = 1; t <= length; t++) {
					Cell cell = new Cell(at(junction, direction, t, level.y), TUNNEL_HEIGHT);
					cells.add(cell);
					stands[t - 1] = node;
					node = addNode(cell.feet(), node, level.y, id, t - 1);
					level.nodes.add(node);
					addStructure(level.structure, cell);
				}
				add(level, new Segment(id, Kind.BRANCH, List.copyOf(cells), stands, main.id(), ++level.galleryCount));
			}
		}
	}

	private void cutRooms(Level level, Cell[] mainCells, int[] mainNodes, int mainStart, int reach, int roomBase, int roomLength,
			int roomPeriod, int roomHeight, int previous) {
		int half = Math.max(0, (settings.roomWidth() - 1) / 2);
		int prevMain = previous;
		int j = mainStart;
		for (int room = 0; j <= reach; room++) {
			int roomStart = roomBase + room * roomPeriod;
			int end = roomStart > reach ? reach : Math.min(roomStart + roomLength - 1, reach);
			if (end < j) {
				continue;
			}
			Segment main = mainSegment(level, mainCells, mainNodes, j, end, prevMain);
			prevMain = main.id();
			j = end + 1;
			if (roomStart > reach || half == 0) {
				continue;
			}
			int id = level.segments.size() + 1;
			List<Cell> cells = new ArrayList<>();
			IntArrayList stands = new IntArrayList();
			for (int jj = Math.max(roomStart, mainStart); jj <= end; jj++) {
				BlockPos trunk = mainCells[jj].feet();
				for (int t = 1; t <= half; t++) {
					for (int sign : new int[]{-1, 1}) {
						int direction = turn(level.mainDir, sign);
						if (available(trunk.getX(), trunk.getZ(), direction, t, bounds) < t) {
							continue;
						}
						Cell cell = new Cell(at(trunk, direction, t, level.y), roomHeight);
						cells.add(cell);
						stands.add(mainNodes[jj]);
						addStructure(level.structure, cell);
					}
				}
			}
			if (!cells.isEmpty()) {
				add(level, new Segment(id, Kind.ROOM, List.copyOf(cells), stands.toIntArray(), main.id(), ++level.galleryCount));
			}
		}
	}

	private Segment mainSegment(Level level, Cell[] mainCells, int[] mainNodes, int start, int end, int after) {
		int id = level.segments.size() + 1;
		List<Cell> cells = new ArrayList<>(end - start + 1);
		int[] stands = new int[end - start + 1];
		for (int j = start; j <= end; j++) {
			cells.add(mainCells[j]);
			stands[j - start] = mainNodes[j - 1];
			nodeSegments.set(mainNodes[j], id);
			nodeCells.set(mainNodes[j], j - start);
		}
		int ordinal = 0;
		for (Segment segment : level.segments) {
			if (segment.kind() == Kind.MAIN) {
				ordinal++;
			}
		}
		Segment segment = new Segment(id, Kind.MAIN, List.copyOf(cells), stands, after, ordinal + 1);
		add(level, segment);
		return segment;
	}

	private static void add(Level level, Segment segment) {
		level.segments.add(segment);
		level.byId.put(segment.id(), segment);
	}

	private int addNode(BlockPos pos, int parent, int level, int segment, int cell) {
		int index = nodes.size();
		nodes.add(pos);
		parents.add(parent);
		depths.add(parent < 0 ? 0 : depths.getInt(parent) + 1);
		nodeLevels.add(level);
		nodeSegments.add(segment);
		nodeCells.add(cell);
		nodeByPos.put(pos.asLong(), index);
		return index;
	}

	private static void addStructure(LongOpenHashSet structure, Cell cell) {
		for (int i = 0; i < cell.height(); i++) {
			structure.add(cell.feet().above(i).asLong());
		}
		structure.add(cell.floor().asLong());
	}

	// ------------------------------------------------------------------ queries

	public BlockPos origin() {
		return origin;
	}

	public int orientation() {
		return orientation;
	}

	public int side() {
		return side;
	}

	/** Staircase width (0 / 1: one lane, 2: two lanes). */
	public int width() {
		return width;
	}

	public int[] bounds() {
		return bounds.clone();
	}

	public boolean rooms() {
		return rooms;
	}

	/** Quarry of the mine (benches instead of galleries), null for galleries. */
	public @Nullable Quarry quarry() {
		return quarry;
	}

	public Settings settings() {
		return settings;
	}

	/** Inner lane steps (feet of step k = fanion height - 1 - k). */
	public List<Cell> stairs() {
		return stairs;
	}

	/** Outer lane cells of step k (empty for a 1-wide staircase). */
	public List<Cell> outer(int k) {
		return k >= 0 && k < outer.size() ? outer.get(k) : List.of();
	}

	/** Every cell of step k, the inner one first. */
	public List<Cell> step(int k) {
		List<Cell> cells = new ArrayList<>(4);
		cells.add(stairs.get(k));
		cells.addAll(outer(k));
		return cells;
	}

	/** Step whose feet stand at height y (-1 for the fanion's own height). */
	public int stepAt(int y) {
		return origin.getY() - 1 - y;
	}

	/** Node standing on stair step k. */
	public static int stairNode(int k) {
		return k + 1;
	}

	/** Node to stand on to dig stair step k: the previous step (the fanion for the first one). */
	public static int stairStand(int k) {
		return k;
	}

	public int nodeCount() {
		return nodes.size();
	}

	public BlockPos node(int index) {
		return nodes.get(index);
	}

	public int parent(int index) {
		return parents.getInt(index);
	}

	public int depth(int index) {
		return depths.getInt(index);
	}

	/** Level (feet height) of a node, {@link #STAIRS_LEVEL} for the entrance and the stairs. */
	public int nodeLevel(int index) {
		return nodeLevels.getInt(index);
	}

	/** Segment of a node: -1 for the entrance and the stairs, else a segment id of its level. */
	public int nodeSegment(int index) {
		return nodeSegments.getInt(index);
	}

	/** Cell index of a node inside its segment (step index for the stairs). */
	public int nodeCell(int index) {
		return nodeCells.getInt(index);
	}

	/** Node whose feet are at {@code feet} (stairs and built levels), -1 if none. */
	public int nodeAt(BlockPos feet) {
		return nodeByPos.get(feet.asLong());
	}

	/** Part of a staircase step, a built level's cells, or their floors: veins never take them. */
	public boolean inStructure(BlockPos pos) {
		return inStructure(pos.asLong(), pos.getY());
	}

	public boolean inStructure(long packed, int y) {
		if (stairStructure.contains(packed)) {
			return true;
		}
		Quarry plan = quarry;
		if (plan != null) {
			// A quarry: veins are followed outside the zone only (the benches dig everything inside, the tower,
			// the causeway and the margin stay).
			return plan.inZone(BlockPos.getX(packed), BlockPos.getZ(packed));
		}
		for (Level level : levels.subMap(y - MAX_CELL_HEIGHT + 1, true, y + 1, true).values()) {
			if (level.structure.contains(packed)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Will be dug by the mine (stairs, galleries, quarry benches): torches never hang there. Unlike {@link #inStructure},
	 * the quarry's tower, causeway and margin are not.
	 */
	public boolean digVolume(BlockPos pos) {
		Quarry plan = quarry;
		if (plan == null) {
			return inStructure(pos);
		}
		if (stairStructure.contains(pos.asLong())) {
			return true;
		}
		return plan.inQuarry(pos.getX(), pos.getZ()) && pos.getY() >= lowestY - 1
				&& pos.getY() <= plan.ground() + plan.settings().clearAbove();
	}

	/** In the columns of the staircase square, or the fanion's: approach tunnels never go there. */
	public boolean inStairColumns(int x, int z) {
		if (x == origin.getX() && z == origin.getZ()) {
			return true;
		}
		int d0 = firstDirection(orientation);
		int d1 = sideDirection(orientation);
		int rx = x - origin.getX();
		int rz = z - origin.getZ();
		// Back to (u, v): u + 1 along d0, v along d1.
		int along = rx * DX[d0] + rz * DZ[d0];
		int sideways = rx * DX[d1] + rz * DZ[d1];
		int s = footprintSide(side, width);
		return along >= 1 && along <= s && sideways >= 0 && sideways < s;
	}

	/**
	 * Nodes walked from {@code from} (excluded) to {@code to} (included) through the tree: up to the common ancestor,
	 * then down. Empty when already there.
	 */
	public List<Integer> path(int from, int to) {
		List<Integer> result = new ArrayList<>();
		if (from == to || from < 0 || to < 0) {
			return result;
		}
		List<Integer> down = new ArrayList<>();
		int a = from;
		int b = to;
		while (depth(a) > depth(b)) {
			a = parent(a);
			result.add(a);
		}
		while (depth(b) > depth(a)) {
			down.add(b);
			b = parent(b);
		}
		while (a != b) {
			a = parent(a);
			result.add(a);
			down.add(b);
			b = parent(b);
		}
		for (int i = down.size() - 1; i >= 0; i--) {
			result.add(down.get(i));
		}
		return result;
	}

	/** Nodes walked between two nodes of the tree. */
	public int distance(int from, int to) {
		if (from < 0 || to < 0) {
			return Integer.MAX_VALUE / 4;
		}
		int a = from;
		int b = to;
		int steps = 0;
		while (depth(a) > depth(b)) {
			a = parent(a);
			steps++;
		}
		while (depth(b) > depth(a)) {
			b = parent(b);
			steps++;
		}
		while (a != b) {
			a = parent(a);
			b = parent(b);
			steps += 2;
		}
		return steps;
	}

	/** Walkable node nearest to (x, y, z) (feet), within {@code maxDistance}; -1 if none. */
	public int nearestNode(double x, double y, double z, double maxDistance, IntPredicate walkable) {
		int best = -1;
		double bestSq = maxDistance * maxDistance;
		int span = (int) Math.ceil(maxDistance) + 1;
		int center = origin.getY() - 1 - (int) Math.floor(y);
		int from = Math.max(-1, center - span);
		int to = Math.min(stairs.size() - 1, center + span);
		for (int k = from; k <= to; k++) {
			int node = k < 0 ? ENTRANCE : stairNode(k);
			double dist = distSq(node, x, y, z);
			// The predicate may read the world: only for candidates closer than the best one.
			if (dist <= bestSq && walkable.test(node)) {
				bestSq = dist;
				best = node;
			}
		}
		int floorY = (int) Math.floor(y);
		for (Level level : levels.subMap(floorY - span, true, floorY + span, true).values()) {
			if (level.bench != null) {
				// A bench holds thousands of nodes: only those of the columns around.
				int r = (int) Math.ceil(maxDistance);
				int bx = (int) Math.floor(x);
				int bz = (int) Math.floor(z);
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						int node = nodeByPos.get(BlockPos.asLong(bx + dx, level.y, bz + dz));
						if (node < 0 || nodeLevels.getInt(node) != level.y) {
							continue;
						}
						double dist = distSq(node, x, y, z);
						if (dist <= bestSq && walkable.test(node)) {
							bestSq = dist;
							best = node;
						}
					}
				}
				for (int i = 0; i < level.extra.size(); i++) {
					int node = level.extra.getInt(i);
					double dist = distSq(node, x, y, z);
					if (dist <= bestSq && walkable.test(node)) {
						bestSq = dist;
						best = node;
					}
				}
				continue;
			}
			for (int i = 0; i < level.nodes.size(); i++) {
				int node = level.nodes.getInt(i);
				double dist = distSq(node, x, y, z);
				if (dist <= bestSq && walkable.test(node)) {
					bestSq = dist;
					best = node;
				}
			}
		}
		return best;
	}

	private double distSq(int node, double x, double y, double z) {
		BlockPos pos = nodes.get(node);
		double dx = pos.getX() + 0.5 - x;
		double dy = pos.getY() - y;
		double dz = pos.getZ() + 0.5 - z;
		return dx * dx + dy * dy + dz * dz;
	}
}
