package dev.guildy.villager;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Pure geometry of a quarry: the "Pierre et variantes" objective of a real mine (realMining.stoneQuarry), unit tested.
 * <p>
 * <b>Tower.</b> The spiral staircase of the mine ({@link MineLayout}) is kept as a tower: its square (both lanes and the
 * pillar) plus a one-block shell of rock around it, never dug but for one opening per bench. The fanion stands on the
 * shell; an access causeway (rock left in place, {@code accessWidth} wide) joins it to the edge of the quarry so that
 * the miners (and the players) still walk in from the surface.
 * <p>
 * <b>Benches.</b> The rest of the zone square (minus {@code margin} blocks along its border) is dug in benches of
 * {@code benchHeight} blocks from the fanion's ground down: bench i has its feet at origin.y - H·(i + 1) and its top at
 * origin.y - 1 - H·i (bench 0 takes the topsoil). A bench is entered from the stair step at its height (or the step just
 * above / below) through a 2-high opening in the shell. From its first cell, straight rays cover every reachable cell (a
 * spine leaving the tower, ribs across it, and so on), cut into strips of at most {@code stripLength} cells: the jobs
 * shared by the miners. Each cell of a strip is dug from the previous one (the first from the cell the ray left).
 * <p>
 * <b>Columns.</b> Bench progress is kept per column of the quarry square ({@link #column}), so that a bench can be
 * planned again around a column given up: a support column left under a non-natural block (all benches), or a column
 * skipped on one bench (water above...).
 * <p>
 * <b>Stacks.</b> {@link #stack} tells what stands in and on a column before a bench cell is dug: vegetation and the
 * quarry's own floor torches to clear first, the natural overburden of the first bench, or why the column must stay.
 */
public final class Quarry {
	/** {@link Bench#parent} of a column out of the bench's tree. */
	public static final int NONE = -2;
	/** {@link Bench#parent} of the bench's first cell (entered through the opening). */
	public static final int ROOT = -1;
	private static final int[] DX = {0, 1, 0, -1};
	private static final int[] DZ = {-1, 0, 1, 0};
	private static final int BACK = 0;
	private static final int SIDE_NEG = 1;
	private static final int SIDE_POS = 2;

	/**
	 * Dimensions (villagers.json5, realMining): bench height, untouched blocks along the zone border, causeway width,
	 * strip length, natural overburden cleared above the fanion's ground by the first bench, floor torch spacing (0 = none).
	 */
	public record Settings(int benchHeight, int margin, int accessWidth, int stripLength, int clearAbove, int torchSpacing) {
	}

	/** What the miners learnt about the columns (persisted): read when a bench is planned. */
	public interface Facts {
		Facts NONE = new Facts() {
			@Override
			public boolean support(int x, int z) {
				return false;
			}

			@Override
			public boolean skipped(int benchY, int column) {
				return false;
			}

			@Override
			public boolean open(int benchY) {
				return true;
			}
		};

		/** Left as a natural support column under a non-natural block (never dug on any bench). */
		boolean support(int x, int z);

		/** Given up on that bench (water above, cave-in, opening that could not be dug...); {@link #column} index. */
		boolean skipped(int benchY, int column);

		/** The bench may be planned now (the few benches being worked): the others are never built. */
		boolean open(int benchY);
	}

	/**
	 * Way from the staircase onto a bench: the stair step it leaves from, the direction (outwards), the outer lane cell
	 * beside the step (2-wide staircases, else null), the opening through the shell and the first cell of the bench.
	 */
	public record Opening(int step, int direction, MineLayout.@Nullable Cell outer, MineLayout.Cell wall, MineLayout.Cell entry) {
	}

	/** Straight run of bench cells (columns, in digging order) dug from the cell of column {@code parent}. */
	public record Strip(int[] columns, int parent) {
	}

	/** One bench planned: its opening (null: no way in), the tree of its cells and its strips. */
	public static final class Bench {
		private final int y;
		private final @Nullable Opening opening;
		private final int root;
		private final int[] parent;
		private final int[] order;
		private final List<Strip> strips;
		private final int[] stripOf;

		private Bench(int y, @Nullable Opening opening, int root, int[] parent, int[] order, List<Strip> strips, int[] stripOf) {
			this.y = y;
			this.opening = opening;
			this.root = root;
			this.parent = parent;
			this.order = order;
			this.strips = strips;
			this.stripOf = stripOf;
		}

		/** Feet height of the bench cells. */
		public int y() {
			return y;
		}

		public @Nullable Opening opening() {
			return opening;
		}

		/** Column of the first cell (-1 without opening). */
		public int root() {
			return root;
		}

		/** Column the cell of {@code column} is dug from: {@link #ROOT} for the first cell, {@link #NONE} out of the tree. */
		public int parent(int column) {
			return parent[column];
		}

		/** Columns of the tree, each one after the column it is dug from (the first cell first). */
		public int[] order() {
			return order;
		}

		public int size() {
			return order.length;
		}

		public boolean contains(int column) {
			return column >= 0 && column < parent.length && parent[column] != NONE;
		}

		public List<Strip> strips() {
			return strips;
		}

		/** Strip index of a column, -1 for the first cell (part of the opening) or out of the tree. */
		public int stripOf(int column) {
			return column >= 0 && column < stripOf.length ? stripOf[column] : -1;
		}
	}

	private final BlockPos origin;
	private final int orientation;
	private final int side;
	private final int width;
	private final int footprint;
	private final int d0;
	private final int d1;
	private final int[] zone;
	private final int[] square;
	private final int sizeX;
	private final int sizeZ;
	private final Settings settings;
	private final int causeway;

	/**
	 * Quarry of the mine of a fanion at {@code origin} (staircase of that orientation, side and width, see
	 * {@link MineLayout}) in the zone square {@code zoneBounds} = {minX, minZ, maxX, maxZ}.
	 */
	public Quarry(BlockPos origin, int orientation, int side, int width, int[] zoneBounds, Settings settings) {
		this.origin = origin.immutable();
		this.orientation = Math.floorMod(orientation, MineLayout.ORIENTATIONS);
		this.side = MineLayout.clampSide(side);
		this.width = width;
		this.footprint = MineLayout.footprintSide(side, width);
		this.d0 = MineLayout.firstDirection(orientation);
		this.d1 = MineLayout.sideDirection(orientation);
		this.zone = zoneBounds.clone();
		int margin = Math.max(0, settings.margin());
		this.square = new int[]{zoneBounds[0] + margin, zoneBounds[1] + margin, zoneBounds[2] - margin, zoneBounds[3] - margin};
		this.sizeX = Math.max(0, square[2] - square[0] + 1);
		this.sizeZ = Math.max(0, square[3] - square[1] + 1);
		this.settings = settings;
		this.causeway = chooseCauseway();
	}

	public Settings settings() {
		return settings;
	}

	public BlockPos origin() {
		return origin;
	}

	/** Height of the fanion's ground: the top of the first bench. */
	public int ground() {
		return origin.getY() - 1;
	}

	public int benchHeight() {
		return Math.max(2, settings.benchHeight());
	}

	/** Quarry square {minX, minZ, maxX, maxZ} (the zone square minus the margin; may be empty). */
	public int[] square() {
		return square.clone();
	}

	// ------------------------------------------------------------------ columns

	/** Local coordinates {u, v} of a column: u along the first direction (0 = next to the fanion), v along the second. */
	private int[] local(int x, int z) {
		int rx = x - origin.getX();
		int rz = z - origin.getZ();
		return new int[]{rx * DX[d0] + rz * DZ[d0] - 1, rx * DX[d1] + rz * DZ[d1]};
	}

	/** Column of a staircase square (local u, v). */
	private int[] world(int u, int v) {
		return new int[]{origin.getX() + DX[d0] * (1 + u) + DX[d1] * v, origin.getZ() + DZ[d0] * (1 + u) + DZ[d1] * v};
	}

	/** Columns of the staircase square (both lanes and the pillar). */
	public boolean inFootprint(int x, int z) {
		int[] uv = local(x, z);
		return uv[0] >= 0 && uv[0] < footprint && uv[1] >= 0 && uv[1] < footprint;
	}

	/** The staircase square and its shell. */
	public boolean inTower(int x, int z) {
		int[] uv = local(x, z);
		return uv[0] >= -1 && uv[0] <= footprint && uv[1] >= -1 && uv[1] <= footprint;
	}

	/** The one-block ring of rock around the staircase square (the fanion stands on it). */
	public boolean inShell(int x, int z) {
		return inTower(x, z) && !inFootprint(x, z);
	}

	/** The access causeway between the fanion and the quarry edge (rock left in place). */
	public boolean inCauseway(int x, int z) {
		int[] uv = local(x, z);
		int u = uv[0];
		int v = uv[1];
		int w = Math.max(1, settings.accessWidth());
		return switch (causeway) {
			case SIDE_NEG -> v <= -1 && u >= -w && u <= -1;
			case SIDE_POS -> v >= 1 && u >= -w && u <= -1;
			default -> u <= -2 && v >= 0 && v < w;
		};
	}

	/** Shortest causeway to the quarry edge: straight back from the fanion, or along either side of the tower. */
	private int chooseCauseway() {
		int best = BACK;
		int bestLength = Integer.MAX_VALUE;
		for (int candidate : new int[]{BACK, SIDE_NEG, SIDE_POS}) {
			int length = 0;
			for (int t = 1; t < 1024; t++) {
				int[] uv = switch (candidate) {
					case SIDE_NEG -> new int[]{-1, -t};
					case SIDE_POS -> new int[]{-1, t};
					default -> new int[]{-1 - t, 0};
				};
				int[] xz = world(uv[0], uv[1]);
				if (!inSquare(xz[0], xz[1])) {
					break;
				}
				length++;
			}
			if (length < bestLength) {
				bestLength = length;
				best = candidate;
			}
		}
		return best;
	}

	public boolean inZone(int x, int z) {
		return x >= zone[0] && x <= zone[2] && z >= zone[1] && z <= zone[3];
	}

	/** In the quarry square (zone minus margin). */
	public boolean inSquare(int x, int z) {
		return x >= square[0] && x <= square[2] && z >= square[1] && z <= square[3];
	}

	/** A column the benches dig: in the quarry square, neither the tower nor the causeway. */
	public boolean inQuarry(int x, int z) {
		return inSquare(x, z) && !inTower(x, z) && !inCauseway(x, z);
	}

	/** Part of the zone never dug by the benches: tower, causeway, margin. */
	public boolean kept(int x, int z) {
		return inZone(x, z) && !inQuarry(x, z);
	}

	/** Columns of the quarry square. */
	public int columns() {
		return sizeX * sizeZ;
	}

	/** Index of a column of the quarry square, -1 outside. */
	public int column(int x, int z) {
		return inSquare(x, z) ? (x - square[0]) * sizeZ + (z - square[1]) : -1;
	}

	public int columnX(int column) {
		return square[0] + column / sizeZ;
	}

	public int columnZ(int column) {
		return square[1] + column % sizeZ;
	}

	/** Columns of the quarry square that the benches dig (tower and causeway excluded). */
	public int quarryColumns() {
		int count = 0;
		for (int c = 0; c < columns(); c++) {
			if (inQuarry(columnX(c), columnZ(c))) {
				count++;
			}
		}
		return count;
	}

	/** Floor torch spot: every {@code torchSpacing} blocks in both directions (none when 0). */
	public boolean torchSpot(int x, int z) {
		int n = settings.torchSpacing();
		return n > 0 && Math.floorMod(x - origin.getX(), n) == n / 2 && Math.floorMod(z - origin.getZ(), n) == n / 2;
	}

	// ------------------------------------------------------------------ benches

	/** Feet height of bench {@code index} (0 = the first one, under the fanion's ground). */
	public int benchY(int index) {
		return origin.getY() - benchHeight() * (index + 1);
	}

	public boolean isBench(int y) {
		int depth = origin.getY() - y;
		return depth >= benchHeight() && depth % benchHeight() == 0;
	}

	/** Index of the bench whose feet are at {@code y} (see {@link #isBench}). */
	public int benchIndex(int y) {
		return (origin.getY() - y) / benchHeight() - 1;
	}

	/**
	 * Benches (feet heights, highest first) from the first one down to {@code bottom} (the target level, or deeper when
	 * the quarry goes on), never under {@code lo} (bottom of the world). Empty when not even the first bench fits.
	 */
	public List<Integer> benches(int lo, int bottom) {
		return benches(origin.getY(), benchHeight(), lo, bottom);
	}

	/** {@link #benches(int, int)} of a fanion at height {@code originY}, benches {@code height} blocks high. */
	public static List<Integer> benches(int originY, int height, int lo, int bottom) {
		int h = Math.max(2, height);
		List<Integer> list = new ArrayList<>();
		for (int i = 0; i < 4096; i++) {
			int y = originY - h * (i + 1);
			if (y < lo || (i > 0 && y < bottom)) {
				break;
			}
			list.add(y);
		}
		return list;
	}

	/** Next bench under {@code lowest}, or null when the world (lo) ends there. */
	public @Nullable Integer deeper(int lowest, int lo) {
		int next = lowest - benchHeight();
		return next >= lo ? next : null;
	}

	/**
	 * Ways onto the bench at {@code benchY}, best first: from the stair step at its height, then the step above (a step
	 * down onto the bench), then the step below (a step up); each outwards from the step, as long as the first cell is a
	 * quarry column. {@code maxStep}: deepest stair step planned.
	 */
	public List<Opening> openings(int benchY, int maxStep) {
		List<Opening> list = new ArrayList<>(4);
		int k = origin.getY() - 1 - benchY;
		boolean wide = MineLayout.wide(width);
		int reach = wide ? 2 : 1;
		for (int step : new int[]{k, k - 1, k + 1}) {
			if (step < 0 || step > maxStep) {
				continue;
			}
			BlockPos inner = MineLayout.stairCell(origin, orientation, side, width, step).feet();
			for (int direction : MineLayout.outward(orientation, side, step)) {
				int dx = DX[direction];
				int dz = DZ[direction];
				BlockPos entry = new BlockPos(inner.getX() + dx * (reach + 1), benchY, inner.getZ() + dz * (reach + 1));
				if (!inQuarry(entry.getX(), entry.getZ())) {
					continue;
				}
				MineLayout.Cell outer = wide ? new MineLayout.Cell(inner.offset(dx, 0, dz), MineLayout.STAIR_HEIGHT) : null;
				// A step up onto the bench needs room for the jump.
				MineLayout.Cell wall = new MineLayout.Cell(inner.offset(dx * reach, 0, dz * reach), step > k ? 3 : 2);
				list.add(new Opening(step, direction, outer, wall, new MineLayout.Cell(entry, benchHeight())));
			}
		}
		return list;
	}

	/** The way onto the bench: the first opening whose first cell may be dug, or null. */
	public @Nullable Opening opening(int benchY, int maxStep, Facts facts) {
		for (Opening opening : openings(benchY, maxStep)) {
			BlockPos entry = opening.entry().feet();
			if (allowed(benchY, entry.getX(), entry.getZ(), facts)) {
				return opening;
			}
		}
		return null;
	}

	/** Part of the opening of one of the benches around {@code pos}: the shell is never patched there. */
	public boolean openingWall(BlockPos pos, int maxStep) {
		for (int index = Math.max(0, benchIndex(pos.getY() + 3) - 1); index <= benchIndex(pos.getY() - 2) + 1; index++) {
			for (Opening opening : openings(benchY(index), maxStep)) {
				MineLayout.Cell wall = opening.wall();
				BlockPos feet = wall.feet();
				if (feet.getX() == pos.getX() && feet.getZ() == pos.getZ() && pos.getY() >= feet.getY() && pos.getY() <= wall.top().getY()) {
					return true;
				}
			}
		}
		return false;
	}

	/** Of the first {@code count} benches, those with a way in (orientation choice: the more the better). */
	public int accessibleBenches(int count, int maxStep) {
		int accessible = 0;
		for (int i = 0; i < count; i++) {
			if (!openings(benchY(i), maxStep).isEmpty()) {
				accessible++;
			}
		}
		return accessible;
	}

	private boolean allowed(int benchY, int x, int z, Facts facts) {
		return inQuarry(x, z) && !facts.support(x, z) && !facts.skipped(benchY, column(x, z));
	}

	/**
	 * Plans the bench at {@code benchY}: its opening, then straight rays from its first cell (outwards first, then across,
	 * breadth first) over every column that may be dug, cut into strips of at most {@code stripLength} cells.
	 */
	public Bench bench(int benchY, int maxStep, Facts facts) {
		int n = columns();
		int[] parent = new int[n];
		Arrays.fill(parent, NONE);
		int[] stripOf = new int[n];
		Arrays.fill(stripOf, -1);
		Opening opening = opening(benchY, maxStep, facts);
		if (opening == null) {
			return new Bench(benchY, null, -1, parent, new int[0], List.of(), stripOf);
		}
		BlockPos entry = opening.entry().feet();
		int root = column(entry.getX(), entry.getZ());
		parent[root] = ROOT;
		IntArrayList order = new IntArrayList();
		order.add(root);
		List<Strip> strips = new ArrayList<>();
		int length = Math.max(1, settings.stripLength());
		ArrayDeque<int[]> rays = new ArrayDeque<>();
		int out = opening.direction();
		rays.add(new int[]{root, out});
		rays.add(new int[]{root, MineLayout.turn(out, 1)});
		rays.add(new int[]{root, MineLayout.turn(out, -1)});
		IntArrayList run = new IntArrayList();
		while (!rays.isEmpty()) {
			int[] ray = rays.poll();
			int from = ray[0];
			int direction = ray[1];
			int x = columnX(from);
			int z = columnZ(from);
			int previous = from;
			run.clear();
			while (true) {
				x += DX[direction];
				z += DZ[direction];
				int c = column(x, z);
				if (c < 0 || parent[c] != NONE || !allowed(benchY, x, z, facts)) {
					break;
				}
				parent[c] = previous;
				order.add(c);
				run.add(c);
				previous = c;
			}
			if (run.isEmpty()) {
				continue;
			}
			int start = 0;
			int stripParent = from;
			while (start < run.size()) {
				int end = Math.min(start + length, run.size());
				int[] columns = run.subList(start, end).toIntArray();
				for (int c : columns) {
					stripOf[c] = strips.size();
				}
				strips.add(new Strip(columns, stripParent));
				stripParent = columns[columns.length - 1];
				start = end;
			}
			for (int i = 0; i < run.size(); i++) {
				int c = run.getInt(i);
				rays.add(new int[]{c, MineLayout.turn(direction, 1)});
				rays.add(new int[]{c, MineLayout.turn(direction, -1)});
			}
		}
		return new Bench(benchY, opening, root, parent, order.toIntArray(), List.copyOf(strips), stripOf);
	}

	// ------------------------------------------------------------------ progress bits

	/** Bit {@code index} of a bit set stored as longs (persisted lists). */
	public static boolean bit(@Nullable List<Long> bits, int index) {
		if (bits == null || index < 0) {
			return false;
		}
		int word = index >> 6;
		if (word >= bits.size()) {
			return false;
		}
		Long value = bits.get(word);
		return value != null && (value & (1L << (index & 63))) != 0;
	}

	public static void setBit(List<Long> bits, int index) {
		if (index < 0) {
			return;
		}
		int word = index >> 6;
		while (bits.size() <= word) {
			bits.add(0L);
		}
		Long value = bits.get(word);
		bits.set(word, (value == null ? 0L : value) | (1L << (index & 63)));
	}

	public static int count(@Nullable List<Long> bits) {
		int total = 0;
		if (bits != null) {
			for (Long value : bits) {
				total += value == null ? 0 : Long.bitCount(value);
			}
		}
		return total;
	}

	// ------------------------------------------------------------------ stacks

	/** What a block of a column is, for {@link #stack}. */
	public enum Surface {
		AIR,
		/** Plant without collision (grass, flowers, vines...): removed without drops. */
		PLANT,
		/** Standing torch on a floor torch spot of the quarry (its own): taken back. */
		TORCH,
		/** Any plain torch (wall or standing): taken back when it is inside a cell being dug. */
		LIGHT,
		/** Natural leaves (never the persistent ones a player placed). */
		LEAVES,
		/** Upright log: part of a natural tree when its trunk ends in natural leaves. */
		LOG,
		/** Snow layers and the extra blocks of realMining.quarrySurfaceClear. */
		VEGETATION,
		NATURAL,
		/** Natural block that falls (sand, gravel). */
		FALLING,
		/** Water or lava (or a block holding water). */
		FLUID,
		/** Anything else: never broken, never left without its natural support column. */
		OTHER,
		UNLOADED
	}

	/** Read access to the world (or a fake grid in tests). */
	public interface SurfaceGrid {
		Surface surface(int x, int y, int z);

		/** Natural leaves around the top log of a trunk (trees whose trunk bends, like acacias). */
		boolean leavesNear(int x, int y, int z);
	}

	public enum StackVerdict {
		/** Dig the cell once the listed blocks are cleared. */
		OK,
		/** A non-natural block rests on the column: it stays as a natural support column, on every bench. */
		SUPPORT,
		/** A fluid would flow in: the column is skipped on this bench. */
		FLUID,
		/** Not loaded. */
		WAIT
	}

	/** {@code clear}: blocks to clear before the cell is dug (highest first); {@code at}: where the problem is. */
	public record Stack(StackVerdict verdict, List<BlockPos> clear, @Nullable BlockPos at) {
		static Stack of(StackVerdict verdict, int x, int y, int z) {
			return new Stack(verdict, List.of(), new BlockPos(x, y, z));
		}
	}

	/**
	 * What stands in and on the column (x, z) of a bench cell (blocks {@code feet} .. {@code top}), up to {@code maxScan}
	 * blocks above it, before the cell is dug. In the cell: natural blocks are dug as usual, vegetation and torches are
	 * cleared first, anything else keeps the column as a support. Above it, the blocks resting on the column (a chain of
	 * non-air blocks from its top): natural vegetation is cleared (leaves, natural trunks, snow, plants, the quarry's floor
	 * torch), natural blocks too up to {@code clearUpTo} (overburden of the first bench), else they are left (a natural
	 * block may lose its support: it stays or falls into the quarry). A non-natural block anywhere in the chain keeps the
	 * column as a support; a fluid resting on what would be removed (or on sand left above) keeps the column on this
	 * bench.
	 */
	public static Stack stack(SurfaceGrid grid, int x, int feet, int top, int z, int clearUpTo, int maxScan) {
		List<BlockPos> clear = new ArrayList<>();
		// The blocks below are removed (dug with the cell or cleared): this one would lose its support.
		boolean chain = true;
		boolean fallingLeft = false;
		boolean belowAir = false;
		int limit = top + Math.max(0, maxScan);
		for (int y = feet; y <= limit; y++) {
			Surface surface = grid.surface(x, y, z);
			boolean inCell = y <= top;
			if (!inCell && (surface == Surface.AIR || belowAir)) {
				break;
			}
			switch (surface) {
				case UNLOADED -> {
					return Stack.of(StackVerdict.WAIT, x, y, z);
				}
				case AIR -> {
				}
				case NATURAL, FALLING -> {
					if (!inCell) {
						if (chain && y <= clearUpTo) {
							clear.add(new BlockPos(x, y, z));
						} else {
							chain = false;
							fallingLeft |= surface == Surface.FALLING;
						}
					}
				}
				case PLANT, TORCH, LEAVES, VEGETATION -> {
					if (inCell || chain) {
						clear.add(new BlockPos(x, y, z));
					}
				}
				case LIGHT -> {
					if (!inCell) {
						return Stack.of(StackVerdict.SUPPORT, x, y, z);
					}
					clear.add(new BlockPos(x, y, z));
				}
				case LOG -> {
					int end = y;
					while (end < limit && grid.surface(x, end + 1, z) == Surface.LOG) {
						end++;
					}
					if (grid.surface(x, end + 1, z) != Surface.LEAVES && !grid.leavesNear(x, end, z)) {
						// A log pillar without leaves: built by a player.
						return Stack.of(StackVerdict.SUPPORT, x, y, z);
					}
					if (inCell || chain) {
						for (int log = y; log <= end; log++) {
							clear.add(new BlockPos(x, log, z));
						}
					}
					y = end;
				}
				case FLUID -> {
					if (inCell || chain || fallingLeft) {
						return Stack.of(StackVerdict.FLUID, x, y, z);
					}
					// Resting on natural rock left in place.
					clear.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
					return new Stack(StackVerdict.OK, clear, null);
				}
				default -> {
					return Stack.of(StackVerdict.SUPPORT, x, y, z);
				}
			}
			belowAir = surface == Surface.AIR;
		}
		clear.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
		return new Stack(StackVerdict.OK, clear, null);
	}
}
