package dev.guildy.builds;

import dev.guildy.claim.HeartIndex;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.villager.BuildStock;
import dev.guildy.villager.WorkZone;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime of one construction site (not saved: rebuilt from the world after a restart). The cells of the schematic
 * (and, with the foundations option, the columns under its bottom layer) each have a state, kept up to date by an
 * incremental scan (a budget of cells per tick shared by every site, loaded chunks only) and by the builders' own
 * actions: the world is the source of truth, a player who places a correct block by hand saves the builders that cell.
 * <p>
 * From the states: the actionable cells of each pass and layer (fronts, see {@link BuildPasses}), the materials still
 * needed per palette entry, the obstacles. Builders lease a parcel of a layer ({@link Job}, {@code builder.tileSize}
 * cells square) so that several of them spread over the site.
 */
public final class BuildSite {
	public static final byte UNKNOWN = 0;
	public static final byte DONE = 1;
	/** No work: void, air kept as terrain, the other half of a door, a flowing fluid, a cell that cannot be built. */
	public static final byte NONE = 2;
	/** A block to place, its materials available. */
	public static final byte PLACE = 3;
	/** Free work: dig out, fix the state. */
	public static final byte FREE = 4;
	/** A block to place, waiting for materials. */
	public static final byte NEED = 5;
	/** A block to place, waiting for a support, a free cell, its other half's cell. */
	public static final byte WAIT = 6;
	/** A cell to dig out kept for safety (falling block above, fluid around that cannot be plugged). */
	public static final byte HOLD = 7;
	/** Something not to be touched stands where a block goes. */
	public static final byte BLOCKED = 8;
	/** Something not to be touched stands in a cell to dig out: left there. */
	public static final byte LEFT = 9;
	/** The cell holds a block that cannot be built (no item, forbidden block, unknown block of the file). */
	public static final byte NOT_BUILDABLE = 10;

	private static final long LEASE_TICKS = 15 * 20L;
	private static final long BAG_FRESH_TICKS = 15 * 20L;
	private static final int SAMPLE = 54;
	/** Shortest time between two scans of the whole site. */
	private static final long MIN_CYCLE_TICKS = 10;
	/** Territory owners and protected positions read again at most this often. */
	private static final long CACHE_TICKS = 100;
	/** A claim looks at most at this many layers with work (and cells) once it has a candidate. */
	private static final int MAX_CLAIM_LAYERS = 4;
	private static final long MAX_CLAIM_CELLS = 200_000;

	/** A parcel leased by a builder: a square of {@code tileSize} columns of one layer. */
	public record Job(int layer, int tileX, int tileZ) {
		long key() {
			return ((long) layer << 42) | ((long) tileX << 21) | tileZ;
		}
	}

	private record Lease(UUID owner, long until) {
	}

	private record Bag(List<ItemStack> items, long seen) {
	}

	/** Totals of the last complete scan (or of the scan so far before the first one ends). */
	public record Counts(int done, int work, int place, int free, int need, int waiting, int hold, int blocked, int left,
			int notBuildable, int unknown, int[] passDone, int[] passTotal, List<BlockPos> blockedAt, List<BlockPos> leftAt,
			List<BlockPos> holdAt, List<BlockPos> waitAt) {
		static final Counts EMPTY = new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, new int[4], new int[4], List.of(), List.of(), List.of(), List.of());

		/** Cells that count for the progress (done + everything still to do, the left obstacles aside). */
		public int total() {
			return done + work;
		}

		public int percent() {
			return total() <= 0 ? 0 : (int) Math.min(100, Math.floor(done * 100.0 / total()));
		}
	}

	private final class Tally {
		int done;
		int place;
		int free;
		int need;
		int waiting;
		int hold;
		int blocked;
		int left;
		int notBuildable;
		int unknown;
		final int[] passDone = new int[4];
		final int[] passTotal = new int[4];
		final List<BlockPos> blockedAt = new ArrayList<>();
		final List<BlockPos> leftAt = new ArrayList<>();
		final List<BlockPos> holdAt = new ArrayList<>();
		final List<BlockPos> waitAt = new ArrayList<>();
		final int[][] act = new int[4][layers];
		final int[] remaining = new int[paletteSize()];
		int foundationRemaining;

		Counts counts() {
			int work = place + free + need + waiting + hold + blocked + unknown;
			return new Counts(done, work, place, free, need, waiting, hold, blocked, left, notBuildable, unknown, passDone.clone(),
					passTotal.clone(), List.copyOf(blockedAt), List.copyOf(leftAt), List.copyOf(holdAt), List.copyOf(waitAt));
		}
	}

	public final Guild guild;
	public final BuildProject project;
	private @Nullable BuildPlan plan;
	private @Nullable String error;
	private Object[] errorArgs = new Object[0];
	private boolean loading;

	// geometry (valid once the plan is set)
	private PlacementTransform transform;
	private int sx;
	private int sy;
	private int sz;
	private int boxCells;
	private int depth;
	private int layers;
	private int[] box = new int[6];
	private @Nullable BlockState[] targets = new BlockState[0];
	private BitSet clear = new BitSet();
	private @Nullable BlockState[] foundationTargets = new BlockState[0];
	private byte[] cells = new byte[0];

	// published
	private int[][] act = new int[4][0];
	private int[] remaining = new int[0];
	private int foundationRemaining;
	private Counts counts = Counts.EMPTY;
	private boolean scanned;
	private boolean completeAtPublish;
	private int cycles;

	// scan in progress
	private int cursor;
	private long nextCycleAt;
	private long cachesAt = -1;
	private @Nullable Tally tally;
	private int foundationColumn = -1;
	private boolean foundationGround;

	// leases, bags, stock
	private final Map<Long, Lease> leases = new HashMap<>();
	private final Map<UUID, Bag> bags = new HashMap<>();
	private Map<Item, Integer> stock = Map.of();
	private long stockAt = -1;
	private boolean[] affordable = new boolean[0];
	private boolean foundationAffordable;

	// caches
	private final LongOpenHashSet protectedPositions = new LongOpenHashSet();
	private final Long2ByteOpenHashMap owners = new Long2ByteOpenHashMap();

	// runtime notes
	long lastPostAt = -1;
	long lastWorkAt = -1;
	long finishedNoticeAt = -1;

	BuildSite(Guild guild, BuildProject project) {
		this.guild = guild;
		this.project = project;
		this.transform = project.transform();
	}

	// ================================================================ plan

	boolean loading() {
		return loading;
	}

	void setLoading() {
		loading = true;
	}

	void fail(String key, Object... args) {
		loading = false;
		error = key;
		errorArgs = args;
	}

	public @Nullable String error() {
		return error;
	}

	public Object[] errorArgs() {
		return errorArgs.clone();
	}

	public @Nullable BuildPlan plan() {
		return plan;
	}

	/** Sets (or resets after an option change) the plan: geometry, rotated palette, clear cells, fresh states. */
	void setPlan(BuildPlan plan) {
		this.plan = plan;
		this.loading = false;
		this.error = null;
		this.transform = project.transform();
		SchematicData data = plan.data;
		sx = data.sizeX;
		sy = data.sizeY;
		sz = data.sizeZ;
		boxCells = data.cells.length;
		depth = project.foundations ? BuildModule.config().foundations.maxDepth : 0;
		layers = sy + depth;
		box = transform.box(sx, sy, sz);
		Rotation rotation = rotation(transform.turn());
		Mirror mirror = mirror(transform.flip());
		targets = new BlockState[plan.paletteSize()];
		for (int p = 0; p < targets.length; p++) {
			BlockState state = plan.state(p);
			targets[p] = state == null ? null : persistent(state.mirror(mirror).rotate(rotation));
		}
		clear = plan.clearCells(project.interiorMode());
		foundationTargets = new BlockState[depth > 0 ? sx * sz : 0];
		for (int column = 0; column < foundationTargets.length; column++) {
			int x = column % sx;
			int z = column / sx;
			int index = data.index(x, 0, z);
			int p = data.cells[index];
			if (plan.kind(p) == MaterialRules.Kind.BUILD && plan.pass(p) == BuildPasses.Pass.SOLID && targets[p] != null) {
				foundationTargets[column] = foundation(targets[p]);
			}
		}
		cells = new byte[boxCells + foundationTargets.length * depth];
		act = new int[4][layers];
		remaining = new int[plan.paletteSize()];
		foundationRemaining = 0;
		affordable = new boolean[plan.paletteSize()];
		counts = Counts.EMPTY;
		scanned = false;
		completeAtPublish = false;
		cycles = 0;
		cursor = 0;
		nextCycleAt = 0;
		tally = null;
		leases.clear();
		stockAt = -1;
		refreshCaches();
	}

	private static BlockState foundation(BlockState above) {
		if (above.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !above.hasBlockEntity()
				&& !(above.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) && above.getBlock().asItem() != net.minecraft.world.item.Items.AIR) {
			return above.getBlock().defaultBlockState();
		}
		return SiteWorld.configuredFoundation();
	}

	/** Leaves placed by builders never decay. */
	private static BlockState persistent(BlockState state) {
		if (state.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) {
			return state.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
		}
		return state;
	}

	static Rotation rotation(PlacementTransform.Turn turn) {
		return switch (turn) {
			case CLOCKWISE_90 -> Rotation.CLOCKWISE_90;
			case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
			case COUNTERCLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
			case NONE -> Rotation.NONE;
		};
	}

	static Mirror mirror(PlacementTransform.Flip flip) {
		return switch (flip) {
			case LEFT_RIGHT -> Mirror.LEFT_RIGHT;
			case FRONT_BACK -> Mirror.FRONT_BACK;
			case NONE -> Mirror.NONE;
		};
	}

	public boolean ready() {
		return plan != null && error == null && !loading;
	}

	/** The plan is loaded and the whole site has been read once (fronts and materials are known). */
	public boolean scanned() {
		return ready() && scanned;
	}

	public int[] box() {
		return box.clone();
	}

	public int layers() {
		return layers;
	}

	public int sizeY() {
		return sy;
	}

	/** Layer index → height in the schematic (negative: foundations). */
	public int heightOf(int layer) {
		return layer - depth;
	}

	// ================================================================ cells

	public int cellCount() {
		return cells.length;
	}

	private int paletteSize() {
		return plan == null ? 0 : plan.paletteSize();
	}

	private boolean foundationCell(int cell) {
		return cell >= boxCells;
	}

	/** World position of a cell. */
	public BlockPos pos(int cell) {
		if (cell < boxCells) {
			SchematicData data = plan.data;
			int x = data.x(cell);
			int y = data.y(cell);
			int z = data.z(cell);
			return new BlockPos(transform.worldX(x, z), transform.worldY(y), transform.worldZ(x, z));
		}
		int v = cell - boxCells;
		int column = v / depth;
		int d = v % depth + 1;
		int x = column % sx;
		int z = column / sx;
		return new BlockPos(transform.worldX(x, z), transform.worldY(-d), transform.worldZ(x, z));
	}

	/** Layer index of a cell (foundations below the schematic's layer 0). */
	public int layer(int cell) {
		if (cell < boxCells) {
			return plan.data.y(cell) + depth;
		}
		int v = cell - boxCells;
		return depth - (v % depth + 1);
	}

	/** Target state of a cell (null: a cell to dig out, or no work). */
	public @Nullable BlockState target(int cell) {
		if (cell >= boxCells) {
			return foundationTargets[(cell - boxCells) / depth];
		}
		int p = plan.data.cells[cell];
		return clear.get(cell) ? null : targets[p];
	}

	public boolean isClear(int cell) {
		return cell < boxCells && clear.get(cell) && plan.kind(plan.data.cells[cell]) == MaterialRules.Kind.AIR;
	}

	/** Pass of a cell, null when the cell is no work. */
	public BuildPasses.@Nullable Pass pass(int cell) {
		if (cell >= boxCells) {
			return foundationTargets[(cell - boxCells) / depth] == null ? null : BuildPasses.Pass.SOLID;
		}
		int p = plan.data.cells[cell];
		MaterialRules.Kind kind = plan.kind(p);
		if (kind == MaterialRules.Kind.AIR) {
			return clear.get(cell) ? BuildPasses.Pass.CLEAR : null;
		}
		if (kind == MaterialRules.Kind.BUILD || kind == MaterialRules.Kind.FLUID) {
			return targets[p] == null ? null : plan.pass(p);
		}
		return null;
	}

	/** Materials of a cell (empty for a cell to dig out). */
	public Map<Item, Integer> cost(int cell) {
		if (cell >= boxCells) {
			BlockState state = foundationTargets[(cell - boxCells) / depth];
			return state == null ? Map.of() : Map.of(state.getBlock().asItem(), 1);
		}
		return clear.get(cell) ? Map.of() : plan.cost(plan.data.cells[cell]);
	}

	public Map<Item, Integer> returns(int cell) {
		return cell >= boxCells || clear.get(cell) ? Map.of() : plan.returns(plan.data.cells[cell]);
	}

	public @Nullable CompoundTag cosmetic(int cell) {
		return cell >= boxCells ? null : plan.cosmetic(cell);
	}

	public byte state(int cell) {
		return cells[cell];
	}

	/** The world position is in the box and a cell of work there (a plug never goes into the site's own work). */
	boolean worksAt(BlockPos pos) {
		if (plan == null || pos.getX() < box[0] || pos.getX() > box[3] || pos.getY() < box[1] || pos.getY() > box[4]
				|| pos.getZ() < box[2] || pos.getZ() > box[5]) {
			return false;
		}
		int[] local = transform.local(pos.getX(), pos.getZ());
		int y = pos.getY() - transform.oy();
		if (local[0] < 0 || local[1] < 0 || local[0] >= sx || local[1] >= sz || y < 0 || y >= sy) {
			return false;
		}
		return pass(plan.data.index(local[0], y, local[1])) != null;
	}

	/** Cell index of a world position in the box, -1 outside. */
	public int cellAt(BlockPos pos) {
		if (plan == null) {
			return -1;
		}
		int[] local = transform.local(pos.getX(), pos.getZ());
		int y = pos.getY() - transform.oy();
		if (local[0] < 0 || local[1] < 0 || local[0] >= sx || local[1] >= sz || y < 0 || y >= sy) {
			return -1;
		}
		return plan.data.index(local[0], y, local[1]);
	}

	// ================================================================ protection

	/** Another guild's land: never touched. */
	boolean foreign(ServerLevel level, BlockPos pos) {
		long chunk = ChunkKey.ofBlock(pos.getX(), pos.getZ());
		byte known = owners.getOrDefault(chunk, (byte) 0);
		if (known == 0) {
			Guild owner = GuildManager.at(level, pos);
			known = owner != null && owner != guild ? (byte) 2 : (byte) 1;
			owners.put(chunk, known);
		}
		return known == 2;
	}

	/** The heart and its support, a fanion, a block or managed sign of a guild construction. */
	boolean isProtected(ServerLevel level, BlockPos pos) {
		return protectedPositions.contains(pos.asLong()) || HeartIndex.heartOrSupport(level, pos) != null;
	}

	/** Territory and protected positions read again (each scan cycle). */
	void refreshCaches() {
		cachesAt = dev.guildy.core.Scheduler.ticks();
		owners.clear();
		protectedPositions.clear();
		int[] b = box;
		int minY = b[1] - depth - 1;
		for (Guild other : GuildManager.all()) {
			for (WorkZone zone : other.villagers.zones) {
				if (zone.fanion && zone.pos != null && inBox(zone.pos, b, minY)) {
					protectedPositions.add(zone.pos.asLong());
				}
			}
			for (BuiltStructure structure : other.structures.built) {
				if (structure.blocks != null) {
					for (BlockPos pos : structure.blocks) {
						if (inBox(pos, b, minY)) {
							protectedPositions.add(pos.asLong());
						}
					}
				}
				if (structure.signs != null) {
					for (BlockPos pos : structure.signs) {
						if (inBox(pos, b, minY)) {
							protectedPositions.add(pos.asLong());
						}
					}
				}
			}
		}
	}

	private static boolean inBox(BlockPos pos, int[] b, int minY) {
		return pos.getX() >= b[0] - 1 && pos.getX() <= b[3] + 1 && pos.getY() >= minY && pos.getY() <= b[4] + 1 && pos.getZ() >= b[2] - 1
				&& pos.getZ() <= b[5] + 1;
	}

	// ================================================================ scan

	/** Scans up to {@code budget} cells. @return cells read */
	int scan(ServerLevel level, int budget) {
		if (!ready() || project.finished) {
			return 0;
		}
		long now = dev.guildy.core.Scheduler.ticks();
		if (tally == null) {
			if (now < nextCycleAt) {
				// A small site is read in a tick: not more than once every few ticks.
				return 0;
			}
			tally = new Tally();
			foundationColumn = -1;
			if (cachesAt < 0 || now - cachesAt >= CACHE_TICKS) {
				refreshCaches();
			}
		}
		Tally t = tally;
		int read = 0;
		while (read < budget && cursor < cells.length) {
			scanCell(level, cursor, t);
			cursor++;
			read++;
		}
		if (cursor >= cells.length) {
			publish(t);
			cursor = 0;
			tally = null;
			cycles++;
			nextCycleAt = now + MIN_CYCLE_TICKS;
		}
		return read;
	}

	private void scanCell(ServerLevel level, int cell, Tally t) {
		BuildPasses.Pass pass = pass(cell);
		if (pass == null) {
			int p = cell < boxCells ? plan.data.cells[cell] : -1;
			if (p > 0 && plan.kind(p) == MaterialRules.Kind.NOT_BUILDABLE) {
				cells[cell] = NOT_BUILDABLE;
				t.notBuildable++;
			} else {
				cells[cell] = NONE;
			}
			return;
		}
		BlockPos pos = pos(cell);
		byte state;
		if (!level.isLoaded(pos)) {
			state = cells[cell];
			if (state == NONE) {
				state = UNKNOWN;
			}
		} else if (foundationCell(cell)) {
			state = classifyFoundation(level, cell, pos);
		} else {
			state = classify(level, cell, pos);
		}
		cells[cell] = state;
		tallyCell(cell, state, pass, pos, t);
	}

	private void tallyCell(int cell, byte state, BuildPasses.Pass pass, BlockPos pos, Tally t) {
		int passIndex = pass.ordinal();
		if (state != NONE && state != LEFT) {
			t.passTotal[passIndex]++;
		}
		switch (state) {
			case DONE -> {
				t.done++;
				t.passDone[passIndex]++;
			}
			case PLACE -> {
				t.place++;
				t.act[passIndex][layer(cell)]++;
			}
			case FREE -> {
				t.free++;
				t.act[passIndex][layer(cell)]++;
			}
			case NEED -> t.need++;
			case WAIT -> {
				t.waiting++;
				if (t.waitAt.size() < SAMPLE) {
					t.waitAt.add(pos);
				}
			}
			case HOLD -> {
				t.hold++;
				if (t.holdAt.size() < SAMPLE) {
					t.holdAt.add(pos);
				}
			}
			case BLOCKED -> {
				t.blocked++;
				if (t.blockedAt.size() < SAMPLE) {
					t.blockedAt.add(pos);
				}
			}
			case LEFT -> {
				t.left++;
				if (t.leftAt.size() < SAMPLE) {
					t.leftAt.add(pos);
				}
			}
			case UNKNOWN -> t.unknown++;
			default -> {
			}
		}
		if (state == PLACE || state == NEED || state == WAIT || (state == UNKNOWN && pass != BuildPasses.Pass.CLEAR)) {
			if (foundationCell(cell)) {
				t.foundationRemaining++;
			} else {
				t.remaining[plan.data.cells[cell]]++;
			}
		}
	}

	/** State of a cell of the schematic from the world. */
	private byte classify(ServerLevel level, int cell, BlockPos pos) {
		boolean clearCell = isClear(cell);
		BlockState target = clearCell ? null : target(cell);
		CellRules.World world = SiteWorld.classify(level, this, pos, target);
		CellRules.Action action = CellRules.decide(clearCell, world, project.replaceBuilt);
		return switch (action) {
			case DONE -> DONE;
			case UNKNOWN -> UNKNOWN;
			case BLOCKED -> BLOCKED;
			case LEFT -> LEFT;
			case FIX -> FREE;
			case CLEAR, CLEAR_BUILT -> clearState(level, pos);
			default -> placeState(level, cell, pos, target);
		};
	}

	private byte clearState(ServerLevel level, BlockPos pos) {
		if (SiteWorld.decorated(level, pos)) {
			return LEFT;
		}
		if (SiteWorld.wet(level, pos)) {
			return FREE;
		}
		SiteWorld.Ready ready = SiteWorld.readyToClear(level, this, pos);
		if (ready == SiteWorld.Ready.OK) {
			return FREE;
		}
		if (ready == SiteWorld.Ready.FLUID && BuildModule.config().terraform.plugOutsideFluids && SiteWorld.plugTarget(level, this, pos) != null) {
			return FREE;
		}
		return HOLD;
	}

	private byte placeState(ServerLevel level, int cell, BlockPos pos, @Nullable BlockState target) {
		if (target == null) {
			return NONE;
		}
		if (SiteWorld.decorated(level, pos)) {
			return BLOCKED;
		}
		SiteWorld.Ready ready = SiteWorld.readyToPlace(level, this, pos, target, null);
		if (ready != SiteWorld.Ready.OK && ready != SiteWorld.Ready.OCCUPIED) {
			return WAIT;
		}
		return affordable(cell) ? PLACE : NEED;
	}

	/** Foundation cell: placed while every cell above it in its column is air, a fluid or a placed foundation block. */
	private byte classifyFoundation(ServerLevel level, int cell, BlockPos pos) {
		int column = (cell - boxCells) / depth;
		if (column != foundationColumn) {
			foundationColumn = column;
			foundationGround = false;
		}
		if (foundationGround) {
			return NONE;
		}
		BlockState target = target(cell);
		if (target == null) {
			return NONE;
		}
		if (site().foreign(level, pos)) {
			foundationGround = true;
			return NONE;
		}
		BlockState world = level.getBlockState(pos);
		if (SiteWorld.matches(world, target)) {
			return DONE;
		}
		if (!world.isAir() && !world.canBeReplaced() && world.getFluidState().isEmpty()) {
			foundationGround = true;
			return NONE;
		}
		return affordable(cell) ? PLACE : NEED;
	}

	private BuildSite site() {
		return this;
	}

	private void publish(Tally t) {
		act = t.act;
		remaining = t.remaining;
		foundationRemaining = t.foundationRemaining;
		counts = t.counts();
		scanned = true;
		// Decided on a complete scan only (never on the live counts of the builders' actions).
		completeAtPublish = counts.unknown() == 0 && counts.place() == 0 && counts.free() == 0 && counts.need() == 0 && counts.blocked() == 0;
		project.done = counts.done();
		project.total = counts.total();
		GuildManager.markDirty();
	}

	/** The scan has read every cell since the last plan change. */
	public int cycles() {
		return cycles;
	}

	public Counts counts() {
		return counts;
	}

	/** Every cell is done (obstacles left in cells to dig out, cells kept for safety and blocks without support aside). */
	boolean complete() {
		return scanned && completeAtPublish;
	}

	/** What stays undone at the end (cells kept for safety, blocks without support, obstacles left in the interior). */
	public int leftovers() {
		return counts.hold() + counts.waiting() + counts.left();
	}

	// ================================================================ fronts and jobs

	public BuildPasses.Fronts fronts() {
		return BuildPasses.fronts(nonNegative(act[BuildPasses.Pass.SOLID.ordinal()]), nonNegative(act[BuildPasses.Pass.ATTACHED.ordinal()]),
				nonNegative(act[BuildPasses.Pass.CLEAR.ordinal()]), BuildModule.config().builder.layerWindow);
	}

	private static int[] nonNegative(int[] counts) {
		return counts;
	}

	/** Actionable cells right now (materials in the storage or the builders' bags, free work). */
	public int actionable() {
		int total = 0;
		for (int[] pass : act) {
			for (int count : pass) {
				total += Math.max(0, count);
			}
		}
		return total;
	}

	/**
	 * Leases the parcel of the nearest actionable cell for this builder (its bag counts: a block waiting for materials it
	 * carries is actionable for it). Null when there is nothing it can do.
	 */
	public @Nullable Job claim(UUID builder, BlockPos from, Map<Item, Integer> bag, long now, java.util.function.Predicate<Job> skip) {
		if (!scanned() || project.finished || project.paused) {
			return null;
		}
		expireLeases(now);
		BuildPasses.Fronts fronts = fronts();
		int tile = BuildModule.config().builder.tileSize;
		int bestCell = -1;
		double bestDist = Double.MAX_VALUE;
		boolean bagWork = !bag.isEmpty() && counts.need() > 0;
		BuildPasses.Pass[] passes = BuildPasses.Pass.values();
		boolean[] open = new boolean[passes.length];
		int[] found = new int[passes.length];
		int layersWithWork = 0;
		long checked = 0;
		for (int layer = 0; layer < layers; layer++) {
			if (bestCell >= 0 && (layersWithWork >= MAX_CLAIM_LAYERS || checked >= MAX_CLAIM_CELLS)) {
				// Enough candidates (a huge site with finishing work on many layers): the nearest of those.
				break;
			}
			boolean any = false;
			for (BuildPasses.Pass pass : passes) {
				open[pass.ordinal()] = fronts.allows(pass, layer)
						&& (act[pass.ordinal()][layer] > 0 || (bagWork && pass != BuildPasses.Pass.CLEAR));
				any |= open[pass.ordinal()];
				found[pass.ordinal()] = 0;
			}
			if (!any) {
				continue;
			}
			checked += (long) sx * sz;
			boolean candidate = false;
			for (int z = 0; z < sz; z++) {
				for (int x = 0; x < sx; x++) {
					int cell = cellOf(layer, x, z);
					if (cell < 0 || !workable(cell, bag)) {
						continue;
					}
					BuildPasses.Pass pass = pass(cell);
					if (pass == null || !open[pass.ordinal()]) {
						continue;
					}
					candidate = true;
					found[pass.ordinal()]++;
					Job job = jobOf(cell, tile);
					Lease lease = leases.get(job.key());
					if ((lease != null && !lease.owner().equals(builder)) || skip.test(job)) {
						continue;
					}
					double dist = pos(cell).distSqr(from);
					if (dist < bestDist) {
						bestDist = dist;
						bestCell = cell;
					}
				}
			}
			if (candidate) {
				layersWithWork++;
			}
			for (BuildPasses.Pass pass : passes) {
				if (open[pass.ordinal()] && found[pass.ordinal()] == 0 && act[pass.ordinal()][layer] > 0) {
					// Stale count (a player finished these cells, or they wait now): corrected until the next scan.
					act[pass.ordinal()][layer] = 0;
				}
			}
		}
		if (bestCell < 0) {
			return null;
		}
		Job job = jobOf(bestCell, tile);
		leases.put(job.key(), new Lease(builder, now + LEASE_TICKS));
		return job;
	}

	private Job jobOf(int cell, int tile) {
		int x;
		int z;
		if (cell < boxCells) {
			x = plan.data.x(cell);
			z = plan.data.z(cell);
		} else {
			int column = (cell - boxCells) / depth;
			x = column % sx;
			z = column / sx;
		}
		return new Job(layer(cell), x / tile, z / tile);
	}

	/** Actionable for a builder carrying {@code bag}. */
	private boolean workable(int cell, Map<Item, Integer> bag) {
		byte state = cells[cell];
		if (state == PLACE || state == FREE) {
			return true;
		}
		return state == NEED && !bag.isEmpty() && Bags.covers(bag, cost(cell));
	}

	/**
	 * Nearest actionable cell of the parcel still allowed by the fronts (cells {@code skip} refuses aside: tried a moment
	 * ago without success), -1 when the parcel is done.
	 */
	public int next(Job job, BlockPos from, Map<Item, Integer> bag, java.util.function.IntPredicate skip) {
		if (!ready()) {
			return -1;
		}
		BuildPasses.Fronts fronts = fronts();
		int tile = BuildModule.config().builder.tileSize;
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		int x0 = job.tileX() * tile;
		int z0 = job.tileZ() * tile;
		for (int z = z0; z < Math.min(sz, z0 + tile); z++) {
			for (int x = x0; x < Math.min(sx, x0 + tile); x++) {
				int cell = cellOf(job.layer(), x, z);
				if (cell < 0 || !workable(cell, bag) || skip.test(cell)) {
					continue;
				}
				BuildPasses.Pass pass = pass(cell);
				if (pass == null || !fronts.allows(pass, job.layer())) {
					continue;
				}
				double dist = pos(cell).distSqr(from);
				if (dist < bestDist) {
					bestDist = dist;
					best = cell;
				}
			}
		}
		return best;
	}

	/** The cell is still work of the parcel for that bag (fronts included): a builder walking to it keeps it. */
	public boolean stillWorkable(Job job, int cell, Map<Item, Integer> bag) {
		if (!ready() || cell < 0 || cell >= cells.length || layer(cell) != job.layer() || !workable(cell, bag)) {
			return false;
		}
		BuildPasses.Pass pass = pass(cell);
		if (pass == null || !fronts().allows(pass, job.layer())) {
			return false;
		}
		int tile = BuildModule.config().builder.tileSize;
		Job of = jobOf(cell, tile);
		return of.equals(job);
	}

	/** Cell of a layer and a column, -1 when there is none. */
	private int cellOf(int layer, int x, int z) {
		if (layer >= depth) {
			return plan.data.index(x, layer - depth, z);
		}
		if (foundationTargets.length == 0) {
			return -1;
		}
		int d = depth - layer;
		return boxCells + (z * sx + x) * depth + (d - 1);
	}

	/** Items still missing to finish the parcel (its cells waiting or to place, minus the bag). */
	public Map<Item, Integer> needs(Job job, Map<Item, Integer> bag) {
		Map<Item, Integer> needs = new LinkedHashMap<>();
		int tile = BuildModule.config().builder.tileSize;
		int x0 = job.tileX() * tile;
		int z0 = job.tileZ() * tile;
		BuildPasses.Fronts fronts = fronts();
		for (int z = z0; z < Math.min(sz, z0 + tile); z++) {
			for (int x = x0; x < Math.min(sx, x0 + tile); x++) {
				int cell = cellOf(job.layer(), x, z);
				if (cell < 0) {
					continue;
				}
				byte state = cells[cell];
				BuildPasses.Pass pass = pass(cell);
				if ((state == PLACE || state == NEED) && pass != null && fronts.allows(pass, job.layer())) {
					cost(cell).forEach((item, count) -> needs.merge(item, count, Integer::sum));
				}
			}
		}
		Map<Item, Integer> missing = new LinkedHashMap<>();
		needs.forEach((item, count) -> {
			int left = count - bag.getOrDefault(item, 0);
			if (left > 0) {
				missing.put(item, left);
			}
		});
		return missing;
	}

	public void renew(Job job, UUID builder, long now) {
		leases.put(job.key(), new Lease(builder, now + LEASE_TICKS));
	}

	public void release(UUID builder) {
		leases.values().removeIf(lease -> lease.owner().equals(builder));
		bags.remove(builder);
	}

	private void expireLeases(long now) {
		leases.values().removeIf(lease -> lease.until() < now);
	}

	/** Builders working here (leases held). */
	public int workers(long now) {
		java.util.Set<UUID> owners = new java.util.HashSet<>();
		for (Lease lease : leases.values()) {
			if (lease.until() >= now) {
				owners.add(lease.owner());
			}
		}
		return owners.size();
	}

	// ================================================================ state changes by the builders

	/** A builder finished the cell (or found it done): counted at once. */
	public void markDone(int cell) {
		byte before = cells[cell];
		if (before == DONE) {
			return;
		}
		leaveState(cell, before);
		cells[cell] = DONE;
		lastWorkAt = dev.guildy.core.Scheduler.ticks();
	}

	/** The builder found the cell in another state than scanned (waiting, blocked...). */
	public void markState(int cell, byte state) {
		byte before = cells[cell];
		if (before == state) {
			return;
		}
		leaveState(cell, before);
		cells[cell] = state;
	}

	private void leaveState(int cell, byte before) {
		BuildPasses.Pass pass = pass(cell);
		if (pass != null && (before == PLACE || before == FREE)) {
			int layer = layer(cell);
			act[pass.ordinal()][layer] = Math.max(0, act[pass.ordinal()][layer] - 1);
		}
		if (pass != null && pass != BuildPasses.Pass.CLEAR && (before == PLACE || before == NEED || before == WAIT || before == UNKNOWN)) {
			if (foundationCell(cell)) {
				foundationRemaining = Math.max(0, foundationRemaining - 1);
			} else {
				int p = plan.data.cells[cell];
				remaining[p] = Math.max(0, remaining[p] - 1);
			}
		}
		if (before == PLACE || before == FREE || before == NEED) {
			// Keeps the totals of the menu roughly right until the next scan.
			counts = new Counts(counts.done() + 1, Math.max(0, counts.work() - 1), counts.place() - (before == PLACE ? 1 : 0),
					counts.free() - (before == FREE ? 1 : 0), counts.need() - (before == NEED ? 1 : 0), counts.waiting(), counts.hold(),
					counts.blocked(), counts.left(), counts.notBuildable(), counts.unknown(), counts.passDone(), counts.passTotal(),
					counts.blockedAt(), counts.leftAt(), counts.holdAt(), counts.waitAt());
		}
	}

	// ================================================================ materials

	/** Remaining materials of every cell not done yet (item → count). */
	public Map<Item, Integer> remainingMaterials() {
		Map<Item, Integer> total = new LinkedHashMap<>();
		if (plan == null) {
			return total;
		}
		for (int p = 0; p < remaining.length; p++) {
			int count = remaining[p];
			if (count > 0) {
				plan.cost(p).forEach((item, n) -> total.merge(item, n * count, Integer::sum));
			}
		}
		if (foundationRemaining > 0 && foundationTargets.length > 0) {
			for (BlockState state : foundationTargets) {
				if (state != null) {
					total.merge(state.getBlock().asItem(), foundationRemaining, Integer::sum);
					break;
				}
			}
		}
		return total;
	}

	/** Every material of the plan (item → count), foundations aside. */
	public Map<Item, Integer> billOfMaterials() {
		return plan == null ? Map.of() : plan.bill();
	}

	/** Items in the material sources (last inventory). */
	public Map<Item, Integer> stock() {
		return stock;
	}

	/** Items carried by the builders of this site (recently seen). */
	public Map<Item, Integer> carried(long now) {
		Map<Item, Integer> total = new HashMap<>();
		Iterator<Map.Entry<UUID, Bag>> it = bags.entrySet().iterator();
		while (it.hasNext()) {
			Bag bag = it.next().getValue();
			if (now - bag.seen() > BAG_FRESH_TICKS) {
				it.remove();
				continue;
			}
			Bags.contents(bag.items()).forEach((item, count) -> total.merge(item, count, Integer::sum));
		}
		return total;
	}

	/** Missing: what remains to place minus the sources and the bags (item → count, positive only). */
	public Map<Item, Integer> missing(long now) {
		Map<Item, Integer> missing = new LinkedHashMap<>();
		Map<Item, Integer> carried = carried(now);
		remainingMaterials().forEach((item, count) -> {
			int left = count - stock.getOrDefault(item, 0) - carried.getOrDefault(item, 0);
			if (left > 0) {
				missing.put(item, left);
			}
		});
		return missing;
	}

	/** Remaining materials already in the sources (item → count): the gatherers never use them up as ingredients. */
	public Map<Item, Integer> countedOn(long now) {
		Map<Item, Integer> counted = new LinkedHashMap<>();
		Map<Item, Integer> carried = carried(now);
		remainingMaterials().forEach((item, count) -> {
			int fromStock = Math.min(stock.getOrDefault(item, 0), count - carried.getOrDefault(item, 0));
			if (fromStock > 0) {
				counted.put(item, fromStock);
			}
		});
		return counted;
	}

	/** A builder of this site shows its bag (counted as available for the site). */
	public void reportBag(UUID builder, List<ItemStack> bag, long now) {
		bags.put(builder, new Bag(bag, now));
	}

	/** Inventories the sources again when due. */
	void refreshStock(ServerLevel level, long now, boolean force) {
		if (!ready()) {
			return;
		}
		long interval = BuildModule.config().builder.stockSeconds * 20L;
		if (!force && stockAt >= 0 && now - stockAt < interval) {
			return;
		}
		stockAt = now;
		stock = BuildStock.available(level, guild, project.sourcesMode());
		Map<Item, Integer> pool = new HashMap<>(stock);
		carried(now).forEach((item, count) -> pool.merge(item, count, Integer::sum));
		for (int p = 0; p < affordable.length; p++) {
			affordable[p] = Bags.covers(pool, plan.cost(p));
		}
		foundationAffordable = false;
		for (BlockState state : foundationTargets) {
			if (state != null) {
				foundationAffordable = pool.getOrDefault(state.getBlock().asItem(), 0) > 0;
				break;
			}
		}
	}

	/** Forces a new inventory at the next step (a builder took the last items...). */
	public void invalidateStock() {
		stockAt = -1;
	}

	private boolean affordable(int cell) {
		if (foundationCell(cell)) {
			return foundationAffordable;
		}
		int p = plan.data.cells[cell];
		return plan.cost(p).isEmpty() || affordable[p];
	}

	// ================================================================ work

	/** What happened when a builder worked on a cell. */
	public enum Outcome {
		PLACED,
		CLEARED,
		FIXED,
		/** A fluid next to the cell was plugged (the cell itself next time). */
		PLUGGED,
		/** A wet cell to dig out was filled with dirt (dug out once dry around). */
		FILLED,
		/** Already done (by a player, a neighbour...). */
		ALREADY,
		/** Its materials are not in the bag. */
		NEED_ITEMS,
		/** Waits (support, other half, fluid around, falling block above...). */
		WAIT,
		/** The builder stands in the cell: it must step aside. */
		SELF_INSIDE,
		/** Never touched (obstacle, container, other guild). */
		BLOCKED,
		UNLOADED
	}

	/** Result of a work step: the outcome and what was dug out (to the bag). */
	public record WorkResult(Outcome outcome, List<ItemStack> drops) {
		static WorkResult of(Outcome outcome) {
			return new WorkResult(outcome, List.of());
		}
	}

	/**
	 * Works on a cell now (the world is read again first): places the target with the materials of the bag, digs the
	 * natural block out, fixes the state, plugs a fluid next to a cell to dig out. Updates the cell's state.
	 */
	public WorkResult work(ServerLevel level, int cell, Entity worker, List<ItemStack> bag) {
		if (!ready()) {
			return WorkResult.of(Outcome.WAIT);
		}
		BlockPos pos = pos(cell);
		if (!level.isLoaded(pos)) {
			return WorkResult.of(Outcome.UNLOADED);
		}
		if (foundationCell(cell)) {
			return workFoundation(level, cell, pos, worker, bag);
		}
		boolean clearCell = isClear(cell);
		BlockState target = clearCell ? null : target(cell);
		CellRules.World world = SiteWorld.classify(level, this, pos, target);
		CellRules.Action action = CellRules.decide(clearCell, world, project.replaceBuilt);
		switch (action) {
			case DONE -> {
				markDone(cell);
				return WorkResult.of(Outcome.ALREADY);
			}
			case UNKNOWN -> {
				return WorkResult.of(Outcome.UNLOADED);
			}
			case BLOCKED -> {
				markState(cell, BLOCKED);
				return WorkResult.of(Outcome.BLOCKED);
			}
			case LEFT -> {
				markState(cell, LEFT);
				return WorkResult.of(Outcome.BLOCKED);
			}
			case FIX -> {
				int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
				level.setBlock(pos, target, flags);
				BlockPos partner = SiteWorld.partnerPos(pos, target);
				if (partner != null && level.getBlockState(partner).is(target.getBlock())) {
					// The other half of a door / bed follows (hinge, facing...).
					level.setBlock(partner, SiteWorld.partnerState(target), flags);
				}
				markDone(cell);
				return WorkResult.of(Outcome.FIXED);
			}
			case CLEAR, CLEAR_BUILT -> {
				return clearWork(level, cell, pos, worker, bag);
			}
			default -> {
				return placeWork(level, cell, pos, target, worker, bag);
			}
		}
	}

	private WorkResult clearWork(ServerLevel level, int cell, BlockPos pos, Entity worker, List<ItemStack> bag) {
		if (SiteWorld.decorated(level, pos)) {
			markState(cell, LEFT);
			return WorkResult.of(Outcome.BLOCKED);
		}
		if (SiteWorld.wet(level, pos)) {
			boolean wetAround = false;
			for (net.minecraft.core.Direction direction : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.NORTH,
					net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST,
					net.minecraft.core.Direction.UP}) {
				if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
					wetAround = true;
					break;
				}
			}
			if (wetAround) {
				// A body of water: filled cell by cell (no source comes back), dug out once nothing wet touches it.
				SiteWorld.fillWet(level, pos);
				return WorkResult.of(Outcome.FILLED);
			}
			SiteWorld.clear(level, pos, worker);
			markDone(cell);
			project.cleared++;
			return WorkResult.of(Outcome.CLEARED);
		}
		SiteWorld.Ready ready = SiteWorld.readyToClear(level, this, pos);
		if (ready == SiteWorld.Ready.FLUID && BuildModule.config().terraform.plugOutsideFluids) {
			BlockPos plug = SiteWorld.plugTarget(level, this, pos);
			if (plug != null) {
				BlockState block = SiteWorld.takePlug(bag);
				if (block != null) {
					SiteWorld.plug(level, plug, block);
					return WorkResult.of(Outcome.PLUGGED);
				}
			}
		}
		if (ready != SiteWorld.Ready.OK) {
			markState(cell, HOLD);
			return WorkResult.of(Outcome.WAIT);
		}
		if (SiteWorld.selfInside(pos, worker)) {
			return WorkResult.of(Outcome.SELF_INSIDE);
		}
		List<ItemStack> drops = SiteWorld.clear(level, pos, worker);
		markDone(cell);
		project.cleared++;
		return new WorkResult(Outcome.CLEARED, BuildModule.config().terraform.keepDrops ? drops : List.of());
	}

	private WorkResult placeWork(ServerLevel level, int cell, BlockPos pos, @Nullable BlockState target, Entity worker, List<ItemStack> bag) {
		if (target == null) {
			markState(cell, NONE);
			return WorkResult.of(Outcome.BLOCKED);
		}
		if (SiteWorld.decorated(level, pos)) {
			markState(cell, BLOCKED);
			return WorkResult.of(Outcome.BLOCKED);
		}
		SiteWorld.Ready ready = SiteWorld.readyToPlace(level, this, pos, target, worker);
		if (ready != SiteWorld.Ready.OK) {
			if (ready != SiteWorld.Ready.OCCUPIED) {
				markState(cell, WAIT);
			}
			return WorkResult.of(Outcome.WAIT);
		}
		if (!target.getCollisionShape(level, pos).isEmpty() && SiteWorld.selfInside(pos, worker)) {
			return WorkResult.of(Outcome.SELF_INSIDE);
		}
		Map<Item, Integer> cost = cost(cell);
		if (!Bags.take(bag, cost)) {
			// Not obtainable right now (the builder fetches before working): waits for materials until the next scan.
			markState(cell, NEED);
			return WorkResult.of(Outcome.NEED_ITEMS);
		}
		List<ItemStack> drops = SiteWorld.place(level, pos, target, cosmetic(cell), worker);
		returns(cell).forEach((item, count) -> Bags.add(bag, new ItemStack(item, count), Integer.MAX_VALUE));
		markDone(cell);
		project.placed++;
		return new WorkResult(Outcome.PLACED, BuildModule.config().terraform.keepDrops ? drops : List.of());
	}

	private WorkResult workFoundation(ServerLevel level, int cell, BlockPos pos, Entity worker, List<ItemStack> bag) {
		BlockState target = target(cell);
		if (target == null || foreign(level, pos)) {
			markState(cell, NONE);
			return WorkResult.of(Outcome.BLOCKED);
		}
		BlockState world = level.getBlockState(pos);
		if (SiteWorld.matches(world, target)) {
			markDone(cell);
			return WorkResult.of(Outcome.ALREADY);
		}
		if (!world.isAir() && !world.canBeReplaced() && world.getFluidState().isEmpty()) {
			markState(cell, NONE);
			return WorkResult.of(Outcome.ALREADY);
		}
		if (SiteWorld.selfInside(pos, worker)) {
			return WorkResult.of(Outcome.SELF_INSIDE);
		}
		if (SiteWorld.occupied(level, pos, worker)) {
			return WorkResult.of(Outcome.WAIT);
		}
		if (!Bags.take(bag, cost(cell))) {
			return WorkResult.of(Outcome.NEED_ITEMS);
		}
		SiteWorld.place(level, pos, target, null, worker);
		markDone(cell);
		project.placed++;
		return WorkResult.of(Outcome.PLACED);
	}

	// ================================================================ cleanup

	void close() {
		leases.clear();
		bags.clear();
	}
}
