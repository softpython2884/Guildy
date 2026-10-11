package dev.guildy.villager;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Pure geometry of a real mine (no world access: unit tested). From the fanion, a 1-wide spiral staircase goes down
 * one block per step around a square pillar, with 3 blocks of headroom per step so that the vanilla ground navigation
 * walks it both ways (it cannot climb ladders). At the gallery level a 2-high main tunnel leaves the spiral outwards,
 * with side branches every few blocks, or (stone) quarry rooms along it. Everything stays inside the zone square.
 * <p>
 * The layout also gives the walking graph, a tree rooted at the fanion (node 0): stairs, then the main tunnel, branches
 * hanging off it; and the work segments shared by the miners (a segment is dug in order, each cell from its stand node).
 */
public final class MineLayout {
	/** 4 first directions × 2 turning senses. */
	public static final int ORIENTATIONS = 8;
	public static final int STAIR_HEIGHT = 3;
	public static final int TUNNEL_HEIGHT = 2;
	public static final int MIN_SIDE = 3;
	public static final int MAX_SIDE = 6;
	/** Node segment index of the stairs (gallery segments have ids from 1). */
	public static final int STAIRS = 0;
	/** Horizontal directions: north, east, south, west. */
	private static final int[] DX = {0, 1, 0, -1};
	private static final int[] DZ = {-1, 0, 1, 0};

	public enum Kind {
		MAIN,
		BRANCH,
		ROOM
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

	/** Dimensions (villagers.json5, realMining). */
	public record Settings(int stairSide, int mainLength, int branchSpacing, int branchLength, int roomWidth, int roomLength,
			int roomHeight, int roomGap) {
	}

	/**
	 * Work unit of one miner: cells dug in order, cell i from node {@code stands[i]}; available once segment
	 * {@code after} is done (-1: right after the stairs). {@code ordinal}: 1-based rank among the segments of its kind.
	 */
	public record Segment(int id, Kind kind, List<Cell> cells, int[] stands, int after, int ordinal) {
	}

	private final BlockPos origin;
	private final int orientation;
	private final int side;
	private final int levelY;
	private final List<BlockPos> nodes = new ArrayList<>();
	private final List<Integer> parentList = new ArrayList<>();
	/** Segment of each node (-1 entrance, {@link #STAIRS}, gallery id) and cell index inside it. */
	private final List<Integer> nodeSegment = new ArrayList<>();
	private final List<Integer> nodeCell = new ArrayList<>();
	private final List<Cell> stairs = new ArrayList<>();
	private final List<Segment> segments = new ArrayList<>();
	private final Map<Integer, Segment> byId = new HashMap<>();
	/** Blocks and floors of every cell (+ one more loop of stairs): veins never touch them. */
	private final Set<Long> structure = new HashSet<>();
	private int[] parents = new int[0];
	private int mainDir = -1;
	private int mainLength;
	private int mainCount;
	private int galleryCount;

	private MineLayout(BlockPos origin, int orientation, int levelY, int[] bounds, boolean rooms, Settings settings) {
		this.origin = origin.immutable();
		this.orientation = Math.floorMod(orientation, ORIENTATIONS);
		this.side = clampSide(settings.stairSide());
		this.levelY = Math.min(levelY, origin.getY());
		addNode(this.origin, -1, -1, -1);
		structure.add(this.origin.asLong());
		structure.add(this.origin.below().asLong());
		int steps = this.origin.getY() - this.levelY;
		for (int k = 0; k < steps; k++) {
			Cell cell = stairCell(this.origin, this.orientation, side, k);
			stairs.add(cell);
			addNode(cell.feet(), k, STAIRS, k);
			addStructure(cell);
		}
		for (int k = steps; k < steps + perimeter(side); k++) {
			addStructure(stairCell(this.origin, this.orientation, side, k));
		}
		buildGalleries(bounds, rooms, settings, steps);
		parents = new int[parentList.size()];
		for (int i = 0; i < parents.length; i++) {
			parents[i] = parentList.get(i);
		}
	}

	/**
	 * Layout of the mine of a fanion at {@code origin} (feet position of the banner) with galleries at {@code levelY}
	 * (feet height, at most the fanion's), inside {@code bounds} = {minX, minZ, maxX, maxZ}.
	 *
	 * @param rooms quarry rooms instead of side branches
	 */
	public static MineLayout build(BlockPos origin, int orientation, int levelY, int[] bounds, boolean rooms, Settings settings) {
		return new MineLayout(origin, orientation, levelY, bounds, rooms, settings);
	}

	// ------------------------------------------------------------------ spiral

	public static int clampSide(int side) {
		return Math.max(MIN_SIDE, Math.min(MAX_SIDE, side));
	}

	/** Steps of one turn of the spiral. */
	public static int perimeter(int side) {
		return 4 * (clampSide(side) - 1);
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

	/**
	 * Stair step {@code k} (0 = the column next to the fanion): feet one block lower per step, 3 blocks high. The steps
	 * walk the border of a side×side square that starts next to the fanion, so one turn of the spiral goes down
	 * {@link #perimeter} blocks and the next turn passes under the previous one with plenty of rock between them.
	 */
	public static Cell stairCell(BlockPos origin, int orientation, int side, int k) {
		int[] local = stairLocal(side, k);
		int d0 = firstDirection(orientation);
		int d1 = sideDirection(orientation);
		int x = origin.getX() + DX[d0] + local[0] * DX[d0] + local[1] * DX[d1];
		int z = origin.getZ() + DZ[d0] + local[0] * DZ[d0] + local[1] * DZ[d1];
		return new Cell(new BlockPos(x, origin.getY() - 1 - k, z), STAIR_HEIGHT);
	}

	/** Position {a, b} of step k in the square (a along the first direction, b along the second). */
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

	/** Columns {x, z} of the whole square (steps and pillar). */
	public static List<int[]> footprint(BlockPos origin, int orientation, int side) {
		int s = clampSide(side);
		int d0 = firstDirection(orientation);
		int d1 = sideDirection(orientation);
		List<int[]> columns = new ArrayList<>(s * s);
		for (int a = 0; a < s; a++) {
			for (int b = 0; b < s; b++) {
				columns.add(new int[]{origin.getX() + DX[d0] * (1 + a) + DX[d1] * b, origin.getZ() + DZ[d0] * (1 + a) + DZ[d1] * b});
			}
		}
		return columns;
	}

	/** The spiral of this orientation stays inside the bounds. */
	public static boolean fits(BlockPos origin, int orientation, int side, int[] bounds) {
		for (int[] column : footprint(origin, orientation, side)) {
			if (!inside(column[0], column[1], bounds)) {
				return false;
			}
		}
		return true;
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

	// ------------------------------------------------------------------ galleries

	private void buildGalleries(int[] bounds, boolean rooms, Settings settings, int steps) {
		BlockPos anchor = steps > 0 ? stairs.getLast().feet() : origin;
		List<Integer> candidates = steps > 0 ? outward(orientation, side, steps - 1) : List.of(0, 1, 2, 3);
		int maxMain = Math.max(0, settings.mainLength());
		int best = -1;
		for (int direction : candidates) {
			int length = available(anchor.getX(), anchor.getZ(), direction, maxMain, bounds);
			if (length > best) {
				best = length;
				mainDir = direction;
			}
		}
		mainLength = Math.max(0, best);
		int roomLength = Math.max(1, settings.roomLength());
		int roomPeriod = roomLength + Math.max(1, settings.roomGap());
		int roomHeight = Math.max(TUNNEL_HEIGHT, settings.roomHeight());
		int[] mainNodes = new int[mainLength + 1];
		mainNodes[0] = steps;
		List<Cell> mainCells = new ArrayList<>(mainLength + 1);
		mainCells.add(null);
		for (int j = 1; j <= mainLength; j++) {
			boolean roomTrunk = rooms && j >= 2 && (j - 2) % roomPeriod < roomLength;
			Cell cell = new Cell(new BlockPos(anchor.getX() + DX[mainDir] * j, levelY, anchor.getZ() + DZ[mainDir] * j),
					roomTrunk ? roomHeight : TUNNEL_HEIGHT);
			mainCells.add(cell);
			mainNodes[j] = nodes.size();
			// The segment is not known yet: fixed below when the segments are cut.
			addNode(cell.feet(), mainNodes[j - 1], -1, -1);
			addStructure(cell);
		}
		if (rooms) {
			cutRooms(bounds, settings, mainCells, mainNodes, roomLength, roomPeriod, roomHeight);
		} else {
			cutBranches(bounds, settings, mainCells, mainNodes);
		}
	}

	private void cutBranches(int[] bounds, Settings settings, List<Cell> mainCells, int[] mainNodes) {
		int spacing = Math.max(2, settings.branchSpacing());
		int prevMain = -1;
		for (int start = 1; start <= mainLength; start += spacing) {
			int end = Math.min(start + spacing - 1, mainLength);
			Segment main = mainSegment(mainCells, mainNodes, start, end, prevMain);
			prevMain = main.id();
			if (end - start + 1 < spacing) {
				break;
			}
			BlockPos junction = mainCells.get(end).feet();
			for (int sign : new int[]{-1, 1}) {
				int direction = turn(mainDir, sign);
				int length = available(junction.getX(), junction.getZ(), direction, Math.max(0, settings.branchLength()), bounds);
				if (length <= 0) {
					continue;
				}
				int id = segments.size() + 1;
				List<Cell> cells = new ArrayList<>(length);
				int[] stands = new int[length];
				int previous = mainNodes[end];
				for (int t = 1; t <= length; t++) {
					Cell cell = new Cell(new BlockPos(junction.getX() + DX[direction] * t, levelY, junction.getZ() + DZ[direction] * t), TUNNEL_HEIGHT);
					cells.add(cell);
					stands[t - 1] = previous;
					previous = addNode(cell.feet(), previous, id, t - 1);
					addStructure(cell);
				}
				add(new Segment(id, Kind.BRANCH, List.copyOf(cells), stands, main.id(), ++galleryCount));
			}
		}
	}

	private void cutRooms(int[] bounds, Settings settings, List<Cell> mainCells, int[] mainNodes, int roomLength, int roomPeriod,
			int roomHeight) {
		int half = Math.max(0, (settings.roomWidth() - 1) / 2);
		int prevMain = -1;
		int j = 1;
		for (int room = 0; j <= mainLength; room++) {
			int roomStart = 2 + room * roomPeriod;
			int end = roomStart > mainLength ? mainLength : Math.min(roomStart + roomLength - 1, mainLength);
			Segment main = mainSegment(mainCells, mainNodes, j, end, prevMain);
			prevMain = main.id();
			j = end + 1;
			if (roomStart > mainLength || half == 0) {
				continue;
			}
			int id = segments.size() + 1;
			List<Cell> cells = new ArrayList<>();
			List<Integer> stands = new ArrayList<>();
			for (int jj = roomStart; jj <= end; jj++) {
				BlockPos trunk = mainCells.get(jj).feet();
				for (int t = 1; t <= half; t++) {
					for (int sign : new int[]{-1, 1}) {
						int direction = turn(mainDir, sign);
						if (available(trunk.getX(), trunk.getZ(), direction, t, bounds) < t) {
							continue;
						}
						Cell cell = new Cell(new BlockPos(trunk.getX() + DX[direction] * t, levelY, trunk.getZ() + DZ[direction] * t), roomHeight);
						cells.add(cell);
						stands.add(mainNodes[jj]);
						addStructure(cell);
					}
				}
			}
			if (!cells.isEmpty()) {
				int[] standArray = stands.stream().mapToInt(Integer::intValue).toArray();
				add(new Segment(id, Kind.ROOM, List.copyOf(cells), standArray, main.id(), ++galleryCount));
			}
		}
	}

	private Segment mainSegment(List<Cell> mainCells, int[] mainNodes, int start, int end, int after) {
		int id = segments.size() + 1;
		List<Cell> cells = new ArrayList<>(end - start + 1);
		int[] stands = new int[end - start + 1];
		for (int j = start; j <= end; j++) {
			cells.add(mainCells.get(j));
			stands[j - start] = mainNodes[j - 1];
			nodeSegment.set(mainNodes[j], id);
			nodeCell.set(mainNodes[j], j - start);
		}
		Segment segment = new Segment(id, Kind.MAIN, List.copyOf(cells), stands, after, ++mainCount);
		add(segment);
		return segment;
	}

	private void add(Segment segment) {
		segments.add(segment);
		byId.put(segment.id(), segment);
	}

	private int addNode(BlockPos pos, int parent, int segment, int cell) {
		nodes.add(pos);
		parentList.add(parent);
		nodeSegment.add(segment);
		nodeCell.add(cell);
		return nodes.size() - 1;
	}

	private void addStructure(Cell cell) {
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

	public int levelY() {
		return levelY;
	}

	/** Stair steps down to the gallery level (feet of the last one = level). */
	public List<Cell> stairs() {
		return stairs;
	}

	/** Gallery segments in work order (ids 1..n). */
	public List<Segment> segments() {
		return segments;
	}

	public Segment segment(int id) {
		return byId.get(id);
	}

	/** Branches / quarry rooms of the level. */
	public int galleryCount() {
		return galleryCount;
	}

	public int mainLength() {
		return mainLength;
	}

	/** Direction (0 north, 1 east, 2 south, 3 west) of the main tunnel, -1 if none. */
	public int mainDirection() {
		return mainDir;
	}

	public int nodeCount() {
		return nodes.size();
	}

	public BlockPos node(int index) {
		return nodes.get(index);
	}

	public int parent(int index) {
		return parents[index];
	}

	/** Segment of a node: -1 for the entrance (the fanion), {@link #STAIRS}, or a gallery segment id. */
	public int nodeSegment(int index) {
		return nodeSegment.get(index);
	}

	/** Cell index of a node inside its segment (step index for the stairs). */
	public int nodeCell(int index) {
		return nodeCell.get(index);
	}

	/** Node standing on stair step k. */
	public static int stairNode(int k) {
		return k + 1;
	}

	public boolean inStructure(BlockPos pos) {
		return structure.contains(pos.asLong());
	}

	public Set<Long> structure() {
		return structure;
	}

	/**
	 * Nodes walked from {@code from} (excluded) to {@code to} (included) through the tree: up to the common ancestor,
	 * then down. Empty when already there.
	 */
	public List<Integer> path(int from, int to) {
		List<Integer> result = new ArrayList<>();
		if (from == to) {
			return result;
		}
		List<Integer> chain = new ArrayList<>();
		Map<Integer, Integer> rank = new HashMap<>();
		for (int node = to; node >= 0; node = parents[node]) {
			rank.put(node, chain.size());
			chain.add(node);
		}
		int current = from;
		while (!rank.containsKey(current)) {
			current = parents[current];
			if (current < 0) {
				return result;
			}
			result.add(current);
		}
		for (int i = rank.get(current) - 1; i >= 0; i--) {
			result.add(chain.get(i));
		}
		return result;
	}

	/** Walkable node nearest to (x, y, z) (feet), within {@code maxDistance}; -1 if none. */
	public int nearestNode(double x, double y, double z, double maxDistance, IntPredicate walkable) {
		int best = -1;
		double bestSq = maxDistance * maxDistance;
		for (int i = 0; i < nodes.size(); i++) {
			BlockPos pos = nodes.get(i);
			double dx = pos.getX() + 0.5 - x;
			double dy = pos.getY() - y;
			double dz = pos.getZ() + 0.5 - z;
			double dist = dx * dx + dy * dy + dz * dz;
			// The predicate may read the world: only for candidates closer than the best one.
			if (dist <= bestSq && walkable.test(i)) {
				bestSq = dist;
				best = i;
			}
		}
		return best;
	}
}
