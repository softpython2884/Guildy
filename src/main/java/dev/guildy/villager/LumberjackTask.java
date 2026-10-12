package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real tree cutting in the villager's forest zone or, without zone, around the territory: the automatic search is a
 * disc centred on the nearest point of the territory (on itself while inside it, see {@link WorkAnchor}), so tree after
 * tree it never drifts away. An incremental nearest-first scan (a few loaded chunks per second, one heightmap lookup
 * per column + a short trunk walk, resumed at the column where it stopped) collects tree bases; a tree is only accepted
 * when it carries natural (non-persistent) leaves, is made of the zone's wood, and has no log in another guild's
 * territory, so player buildings are never cut. Rejected trees are not looked at again for a few minutes.
 * <p>
 * "Objectifs prioritaires" ({@link Wishlist}): trees giving a wanted item (their logs, or leaf drops such as saplings,
 * apples and sticks) come first, even up to {@code zones.wishlistMargin} blocks outside the area; for wanted leaf drops
 * the canopy is harvested before the trunk. One log per second, top-down; saplings are replanted at the trunk base.
 * With nothing left to cut it plants new saplings in its forest ({@link SaplingPlanter}).
 */
final class LumberjackTask extends TaskHandler {
	private enum Phase {
		SEARCH,
		GO_TREE,
		LEAVES,
		CUT,
		/** Nothing to cut: a planting round (new saplings, see {@link SaplingPlanter}). */
		PLANT,
		DEPOSIT,
		BLOCKED
	}

	/** Trees validated (bounded flood fill) per step. */
	private static final int MAX_VALIDATIONS = 2;
	private static final int CHUNKS_PER_STEP = 2;
	/** With wanted items the whole area is read before ordinary trees are taken: it is read faster. */
	private static final int WISH_CHUNKS_PER_STEP = 4;
	private static final int MAX_PENDING = 48;
	private static final int MAX_TRUNK_HEIGHT = 40;
	private static final int MAX_TREE_SPREAD = 6;
	private static final int MIN_NATURAL_LEAVES = 3;
	/** Canopy harvested for wanted leaf drops: at most this many leaves per tree, a few per second. */
	private static final int MAX_LEAVES = 96;
	private static final int LEAVES_PER_STEP = 6;
	/** A rejected tree (not natural, too big, partly another guild's) is not examined again for 5 minutes. */
	private static final long REJECT_TICKS = 5 * 60 * 20L;
	private static final int MAX_REJECTED = 2048;
	/** Nothing loaded in the area: look again soon (a player may come back). */
	private static final long UNLOADED_RETRY_TICKS = 3 * 20L;
	private static final ItemStack AXE = new ItemStack(Items.IRON_AXE);

	/** A trunk base found by the scan; {@code core}: inside the area (not only in the margin of wanted items). */
	private record Candidate(BlockPos base, boolean core) {
	}

	private record Tree(BlockPos base, Block log, ArrayDeque<BlockPos> logs, ArrayDeque<BlockPos> leaves, List<BlockPos> plantSpots,
			@Nullable Block sapling, boolean wanted) {
	}

	/** Outcome of the last complete scan, for the idle status. */
	private record ScanResult(int read, int unloaded) {
	}

	private Phase phase = Phase.SEARCH;
	private @Nullable Tree tree;
	private @Nullable WorkZone zone;
	private WorkWishes wishes = WorkWishes.NONE;
	private String areaKey = "";
	private WorkArea.@Nullable Scan scan;
	/** Automatic mode: centre of the current search (candidates nearest to it first); null = nearest to the villager. */
	private @Nullable BlockPos anchor;
	/** Bases of trees that may give a wanted item (examined first). */
	private final List<Candidate> wanted = new ArrayList<>();
	/** Other bases found inside the area. */
	private final List<Candidate> normal = new ArrayList<>();
	/** Column (x, z) → tick until which its tree is ignored. */
	private final Map<Long, Long> rejected = new HashMap<>();
	/** Base column of a tree rejected for its size → same expiry (counted by every search, for the status). */
	private final Map<Long, Long> bigBases = new HashMap<>();
	/** Trees too big to be cut seen by the current search. */
	private int bigTrees;
	private @Nullable ScanResult lastScan;
	private long nextScan;
	private long retryAt;
	/** Extra logs earned by the work speed upgrade (fractional rate accumulated across steps). */
	private double bonusCuts;
	/** Where the task started: centre of the automatic search of a guild without territory. */
	private @Nullable BlockPos home;
	private final DepositFlow deposit = new DepositFlow();
	private final SaplingPlanter planter = new SaplingPlanter();
	/** No deposit chest usable: planting goes on meanwhile, the deposit is tried again at this tick. */
	private long depositRetryAt;

	@Override
	void tick(WorkContext ctx) {
		if (home == null) {
			home = ctx.villager.blockPosition();
		}
		zone = Zones.assignedZone(ctx.guild, ctx.record);
		String wishKey = WorkWishes.keyOf(ctx.record, zone);
		String key = areaKey(zone) + "|" + wishKey;
		if (!key.equals(areaKey)) {
			areaKey = key;
			wishes = WorkWishes.of(ctx.record, zone);
			resetSearch();
			planter.reset();
			nextScan = 0;
			if (phase == Phase.GO_TREE || phase == Phase.LEAVES || phase == Phase.CUT || phase == Phase.PLANT) {
				tree = null;
				phase = Phase.SEARCH;
			}
		}
		switch (phase) {
			case SEARCH -> search(ctx);
			case GO_TREE -> goTree(ctx);
			case LEAVES -> harvestLeaves(ctx);
			case CUT -> {
				cut(ctx);
				bonusCuts += Upgrades.workRate(ctx.guild) - 1;
				while (bonusCuts >= 1 && phase == Phase.CUT) {
					bonusCuts -= 1;
					cut(ctx);
				}
				if (phase != Phase.CUT) {
					bonusCuts = 0;
				}
			}
			case PLANT -> plantStep(ctx);
			case DEPOSIT -> depositStep(ctx);
			case BLOCKED -> {
				if (ctx.now >= retryAt) {
					phase = Phase.DEPOSIT;
					depositStep(ctx);
				}
			}
		}
	}

	private void resetSearch() {
		scan = null;
		anchor = null;
		wanted.clear();
		normal.clear();
		lastScan = null;
		bigTrees = 0;
	}

	// ------------------------------------------------------------------ search

	private void search(WorkContext ctx) {
		if (ctx.mustReturn()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		if (scan == null && wanted.isEmpty() && normal.isEmpty()) {
			if (ctx.now < nextScan) {
				idleAfterScan(ctx);
				return;
			}
			startScan(ctx);
		}
		int[] budget = {MAX_VALIDATIONS};
		Tree found = validateNext(ctx, budget);
		WorkArea.Scan running = scan;
		if (found == null && running != null) {
			int chunks = wishes.isEmpty() ? CHUNKS_PER_STEP : WISH_CHUNKS_PER_STEP;
			for (int i = 0; i < chunks && wanted.size() < MAX_PENDING; i++) {
				LevelChunk chunk = running.next(ctx);
				if (chunk == null) {
					break;
				}
				int stop = collectBases(ctx, running.area, chunk, running.startColumn());
				if (stop >= 0) {
					// Lists full (dense forest): the rest of this chunk is read once some bases are handled.
					running.resumeAt(stop);
					break;
				}
			}
			if (running.done()) {
				lastScan = new ScanResult(running.readChunks(), running.unloadedChunks());
				if (running.readChunks() == 0) {
					nextScan = Math.min(nextScan, ctx.now + UNLOADED_RETRY_TICKS);
				}
				scan = null;
			}
			found = validateNext(ctx, budget);
		}
		if (found != null) {
			tree = found;
			phase = Phase.GO_TREE;
			goTree(ctx);
			return;
		}
		if (scan == null && wanted.isEmpty() && normal.isEmpty()) {
			idleAfterScan(ctx);
		} else {
			searchingStatus(ctx);
		}
	}

	private void startScan(WorkContext ctx) {
		BlockPos villager = ctx.villager.blockPosition();
		int margin = wishes.isEmpty() ? 0 : ctx.config.zones.wishlistMargin;
		WorkZone current = zone;
		WorkArea area;
		if (current != null) {
			area = WorkArea.of(current, margin);
			anchor = null;
		} else {
			area = WorkArea.anchored(ctx.guild, villager, home != null ? home : villager, ctx.config, margin);
			anchor = area.center();
		}
		scan = new WorkArea.Scan(area, anchor != null ? anchor : villager);
		bigTrees = 0;
		nextScan = ctx.now + ctx.workTicks(ctx.config.work.scanIntervalSeconds);
	}

	private void searchingStatus(WorkContext ctx) {
		WorkArea.Scan running = scan;
		int position = running == null ? 1 : running.position();
		int total = running == null ? 1 : running.total();
		WorkZone current = zone;
		if (!wishes.isEmpty() && (running != null || !wanted.isEmpty())) {
			ctx.status("villager.work.lumberjack.searching_wanted", position, total);
		} else if (current != null) {
			ctx.status("villager.work.lumberjack.searching_zone", current.name, position, total);
		} else {
			ctx.status("villager.work.lumberjack.searching_auto", position, total);
		}
	}

	/** Nothing to cut (last scan): bring the logs home, else say why. */
	private void idleAfterScan(WorkContext ctx) {
		WorkZone current = zone;
		ScanResult result = lastScan;
		int radius = ctx.config.zones.autoSearchRadius;
		if (result != null && result.read() == 0 && result.unloaded() > 0) {
			if (current != null) {
				idle(ctx, "villager.status.zone_unloaded", current.name);
			} else {
				idle(ctx, "villager.work.area_unloaded");
			}
		} else if (bigTrees > 0) {
			int max = ctx.config.lumberjack.maxLogsPerTree;
			if (current != null) {
				idle(ctx, "villager.work.lumberjack.no_tree_zone_big", current.name, Lang.raw(current.objective.langKey()), bigTrees, max);
			} else {
				idle(ctx, "villager.work.lumberjack.no_tree_auto_big", radius, bigTrees, max);
			}
		} else if (current != null) {
			idle(ctx, "villager.status.lumberjack.no_tree_zone", current.name, Lang.raw(current.objective.langKey()));
		} else {
			idle(ctx, "villager.work.lumberjack.no_tree_auto", radius);
		}
	}

	private void idle(WorkContext ctx, String status, Object... args) {
		boolean carrying = Planting.hasDepositable(ctx, planter.keep(ctx, zone));
		if (carrying && ctx.now >= depositRetryAt) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
		} else if (home != null && planter.ready(ctx, zone)) {
			// Nothing to cut: plant new saplings until the next search.
			phase = Phase.PLANT;
			plantStep(ctx);
		} else if (carrying) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
		} else {
			Planting.idleStatus(ctx, planter, status, args);
		}
	}

	/** One step of the planting round; a full bag goes home first, the end of the round goes back to the search. */
	private void plantStep(WorkContext ctx) {
		if (ctx.mustReturn()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		BlockPos start = home != null ? home : ctx.villager.blockPosition();
		if (planter.tick(ctx, zone, start) == Planter.Result.DONE) {
			phase = Phase.SEARCH;
		}
	}

	// ------------------------------------------------------------------ cutting

	private void goTree(WorkContext ctx) {
		Tree current = tree;
		if (current == null) {
			phase = Phase.SEARCH;
			return;
		}
		switch (Movement.moveTo(ctx, current.base, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				tree = null;
				phase = Phase.SEARCH;
			}
			case MOVING -> {
				if (current.wanted) {
					ctx.status("villager.work.lumberjack.going_wanted", current.base.getX(), current.base.getY(), current.base.getZ());
				} else if (zone != null) {
					ctx.status("villager.status.lumberjack.going_zone", zone.name);
				} else {
					ctx.status("villager.status.lumberjack.going_auto", current.base.getX(), current.base.getY(), current.base.getZ());
				}
			}
			case ARRIVED -> {
				if (current.leaves.isEmpty()) {
					phase = Phase.CUT;
					cut(ctx);
				} else {
					phase = Phase.LEAVES;
					harvestLeaves(ctx);
				}
			}
		}
	}

	/** Wanted leaf drops (saplings, apples, sticks): the canopy is taken before the trunk, so it does not decay first. */
	private void harvestLeaves(WorkContext ctx) {
		Tree current = tree;
		if (current == null) {
			phase = Phase.SEARCH;
			return;
		}
		if (!Movement.near(ctx, current.base, 5.0, 6.0)) {
			phase = Phase.GO_TREE;
			goTree(ctx);
			return;
		}
		if (ctx.mustReturn()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		ServerLevel level = ctx.level;
		int budget = (int) Math.max(1, Math.round(LEAVES_PER_STEP * Upgrades.workRate(ctx.guild)));
		int done = 0;
		while (done < budget && !current.leaves.isEmpty() && !ctx.mustReturn()) {
			BlockPos pos = current.leaves.poll();
			if (!ctx.loaded(pos) || ctx.foreign(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!isNaturalLeaves(state)) {
				continue;
			}
			List<ItemStack> drops = Block.getDrops(state, level, pos, null, ctx.villager, ItemStack.EMPTY);
			level.destroyBlock(pos, false, ctx.villager);
			carry(ctx, drops);
			done++;
		}
		if (done > 0) {
			Movement.hold(ctx, current.base.above(3));
			WorldUtil.swing(ctx.villager);
		}
		if (current.leaves.isEmpty()) {
			phase = Phase.CUT;
		}
		ctx.status("villager.work.lumberjack.leaves", current.leaves.size());
	}

	private void cut(WorkContext ctx) {
		Tree current = tree;
		if (current == null) {
			phase = Phase.SEARCH;
			return;
		}
		if (!Movement.near(ctx, current.base, 5.0, 6.0)) {
			phase = Phase.GO_TREE;
			goTree(ctx);
			return;
		}
		if (ctx.mustReturn()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		BlockPos log = nextLog(ctx, current);
		if (log == null) {
			finishTree(ctx, current);
			return;
		}
		ServerLevel level = ctx.level;
		BlockState state = level.getBlockState(log);
		List<ItemStack> drops = Block.getDrops(state, level, log, null, ctx.villager, AXE);
		Movement.hold(ctx, log);
		WorldUtil.swing(ctx.villager);
		level.destroyBlock(log, false, ctx.villager);
		carry(ctx, drops);
		if (ctx.config.lumberjack.pickUpSaplings) {
			// Saplings of the foliage decaying around: planted later between cuts.
			Planting.pickUp(ctx, 3.0, Planting::isSapling);
		}
		ctx.record.stats.logs++;
		GuildManager.markDirty();
		if (zone != null) {
			ctx.status("villager.status.lumberjack.cutting", current.logs.size());
		} else {
			ctx.status("villager.status.lumberjack.cutting_auto", current.base.getX(), current.base.getY(), current.base.getZ(), current.logs.size());
		}
	}

	/** Into the bag; what does not fit falls at the villager's feet. */
	private static void carry(WorkContext ctx, List<ItemStack> drops) {
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			ItemStack leftover = ctx.addCarried(drop);
			if (!leftover.isEmpty()) {
				ItemEntity entity = new ItemEntity(ctx.level, ctx.villager.getX(), ctx.villager.getY() + 0.5, ctx.villager.getZ(), leftover);
				entity.setDefaultPickUpDelay();
				ctx.level.addFreshEntity(entity);
			}
		}
	}

	private void finishTree(WorkContext ctx, Tree current) {
		if (ctx.config.lumberjack.replant && current.sapling != null) {
			BlockState sapling = current.sapling.defaultBlockState();
			for (BlockPos spot : current.plantSpots) {
				if (ctx.loaded(spot) && !ctx.foreign(spot) && ctx.level.getBlockState(spot).isAir() && sapling.canSurvive(ctx.level, spot)) {
					ctx.level.setBlockAndUpdate(spot, sapling);
					ctx.level.playSound(null, spot, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8f, 1.0f);
				}
			}
		}
		tree = null;
		if (scan == null && wanted.isEmpty() && normal.isEmpty()) {
			// Every tree found by the last search is done: look again right away (new trees around, skipped ones).
			nextScan = ctx.now;
		}
		phase = ctx.mustReturn() ? Phase.DEPOSIT : Phase.SEARCH;
		searchingStatus(ctx);
	}

	private void depositStep(WorkContext ctx) {
		// One stack of saplings to plant stays in the bag.
		DepositFlow.State state = deposit.tick(ctx, planter.keep(ctx, zone));
		switch (state) {
			case WORKING -> {
			}
			case DONE -> {
				if (Missions.complete(ctx)) {
					return;
				}
				phase = tree != null ? Phase.GO_TREE : Phase.SEARCH;
			}
			case NO_CHEST, ALL_FULL -> {
				ctx.status(DepositFlow.blockedStatus(state));
				if (ctx.mustReturn()) {
					phase = Phase.BLOCKED;
					retryAt = ctx.now + WorkContext.ticks(10);
				} else {
					phase = tree != null ? Phase.GO_TREE : Phase.SEARCH;
					depositRetryAt = ctx.now + ctx.workTicks(ctx.config.work.scanIntervalSeconds);
				}
			}
		}
	}

	/** Next log still standing (top-down), skipping logs already gone or in another guild's territory. */
	private static @Nullable BlockPos nextLog(WorkContext ctx, Tree current) {
		while (!current.logs.isEmpty()) {
			BlockPos pos = current.logs.poll();
			if (ctx.loaded(pos) && !ctx.foreign(pos) && ctx.level.getBlockState(pos).is(current.log)) {
				return pos;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ tree search

	private ZoneObjective objective() {
		return zone != null ? zone.objective : ZoneObjective.ANY_WOOD;
	}

	/**
	 * Trunk bases of the chunk's columns inside the area (or its margin, for wanted trees), from column {@code start}:
	 * top block (no leaves) is a log standing on soil.
	 *
	 * @return the column where reading stopped because the lists are full, -1 when the chunk is done
	 */
	private int collectBases(WorkContext ctx, WorkArea area, LevelChunk chunk, int start) {
		ZoneObjective objective = objective();
		boolean wishing = !wishes.isEmpty();
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int index = Math.max(0, start); index < 256; index++) {
			int x = x0 + (index >> 4);
			int z = z0 + (index & 15);
			boolean core = area.contains(x, z);
			if (!core && (!wishing || !area.reaches(x, z))) {
				continue;
			}
			if (isRejected(x, z, ctx.now)) {
				if (bigBases.containsKey(ChunkKey.of(x, z))) {
					bigTrees++;
				}
				continue;
			}
			int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockState state = chunk.getBlockState(pos.set(x, top, z));
			if (!state.is(BlockTags.LOGS)) {
				continue;
			}
			Block log = state.getBlock();
			boolean want = wishing && (wishes.logWanted(log) || wishes.leavesMayGive(log));
			if (!want && (!core || !objective.acceptsLog(blockId(log)))) {
				continue;
			}
			int y = top;
			int steps = 0;
			while (steps < MAX_TRUNK_HEIGHT && chunk.getBlockState(pos.set(x, y - 1, z)).is(log)) {
				y--;
				steps++;
			}
			BlockPos base = new BlockPos(x, y, z);
			if (!isSoil(chunk.getBlockState(base.below()))) {
				continue;
			}
			Candidate candidate = new Candidate(base, core);
			if (want) {
				if (wanted.size() >= MAX_PENDING) {
					return index;
				}
				addCandidate(wanted, candidate);
			} else if (normal.size() < MAX_PENDING) {
				addCandidate(normal, candidate);
			} else if (!wishing) {
				return index;
			}
			// With wanted items, ordinary trees beyond the list are found again by the next search.
		}
		return -1;
	}

	private static void addCandidate(List<Candidate> list, Candidate candidate) {
		for (Candidate other : list) {
			if (other.base.equals(candidate.base)) {
				return;
			}
		}
		list.add(candidate);
	}

	/**
	 * The next tree to cut: trees that may give a wanted item first (nearest first); ordinary trees right away without
	 * wanted items, otherwise only once the whole area was read and no wanted tree is left.
	 */
	private @Nullable Tree validateNext(WorkContext ctx, int[] budget) {
		Tree found = validateFrom(ctx, wanted, budget, true);
		if (found != null || !wanted.isEmpty() || (!wishes.isEmpty() && scan != null)) {
			return found;
		}
		return validateFrom(ctx, normal, budget, false);
	}

	private @Nullable Tree validateFrom(WorkContext ctx, List<Candidate> list, int[] budget, boolean wantedList) {
		if (list.isEmpty() || budget[0] <= 0) {
			return null;
		}
		BlockPos reference = anchor;
		if (reference != null) {
			list.sort(Comparator.comparingDouble(candidate -> candidate.base.distSqr(reference)));
		} else {
			list.sort(Comparator.comparingDouble(candidate -> ctx.distSq(candidate.base)));
		}
		Set<BlockPos> covered = new HashSet<>();
		Tree found = null;
		while (found == null && !list.isEmpty() && budget[0] > 0) {
			Candidate candidate = list.removeFirst();
			if (covered.contains(candidate.base)) {
				continue;
			}
			budget[0]--;
			found = validate(ctx, candidate, covered, wantedList);
		}
		// Other bases of the same tree (large trunks) or of rejected trees are done.
		Iterator<Candidate> it = list.iterator();
		while (it.hasNext()) {
			if (covered.contains(it.next().base)) {
				it.remove();
			}
		}
		return found;
	}

	/**
	 * Collects the connected logs of the tree (same block, bounded) and checks it is a natural tree we may cut: from the
	 * wanted list, a tree that turns out to give nothing wanted waits in the ordinary list (inside the area) or is
	 * skipped (margin).
	 */
	private @Nullable Tree validate(WorkContext ctx, Candidate candidate, Set<BlockPos> covered, boolean wantedList) {
		ServerLevel level = ctx.level;
		BlockPos base = candidate.base;
		if (!ctx.loaded(base)) {
			return null;
		}
		BlockState baseState = level.getBlockState(base);
		Block logBlock = baseState.getBlock();
		if (!baseState.is(BlockTags.LOGS)) {
			return null;
		}
		boolean logWanted = wishes.logWanted(logBlock);
		boolean leafMaybe = wishes.leavesMayGive(logBlock);
		boolean ordinary = candidate.core && objective().acceptsLog(blockId(logBlock));
		if (!logWanted && !leafMaybe && !ordinary) {
			return null;
		}
		int maxLogs = ctx.config.lumberjack.maxLogsPerTree;
		Set<BlockPos> logs = new HashSet<>();
		Set<BlockPos> leafSeeds = new HashSet<>();
		Set<Block> leafTypes = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(base);
		logs.add(base);
		int naturalLeaves = 0;
		int topY = base.getY();
		boolean valid = true;
		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();
			topY = Math.max(topY, current.getY());
			if (ctx.foreign(current)) {
				valid = false;
			}
			for (Direction direction : Direction.values()) {
				BlockPos side = current.relative(direction);
				if (!ctx.loaded(side)) {
					continue;
				}
				BlockState sideState = level.getBlockState(side);
				if (isNaturalLeaves(sideState)) {
					naturalLeaves++;
					leafTypes.add(sideState.getBlock());
					if (leafSeeds.size() < MAX_LEAVES) {
						leafSeeds.add(side);
					}
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos next = current.offset(dx, dy, dz);
						if (logs.contains(next) || Math.abs(next.getX() - base.getX()) > MAX_TREE_SPREAD
								|| Math.abs(next.getZ() - base.getZ()) > MAX_TREE_SPREAD || next.getY() - base.getY() > MAX_TRUNK_HEIGHT
								|| !ctx.loaded(next) || !level.getBlockState(next).is(logBlock)) {
							continue;
						}
						if (logs.size() >= maxLogs) {
							covered.addAll(logs);
							reject(logs, ctx.now);
							bigBases.put(ChunkKey.of(base.getX(), base.getZ()), ctx.now + REJECT_TICKS);
							bigTrees++;
							return null;
						}
						logs.add(next);
						queue.add(next);
					}
				}
			}
		}
		covered.addAll(logs);
		if (!valid || naturalLeaves < MIN_NATURAL_LEAVES) {
			reject(logs, ctx.now);
			return null;
		}
		boolean leavesWanted = false;
		if (leafMaybe) {
			for (Block leaves : leafTypes) {
				if (wishes.leavesWanted(leaves)) {
					leavesWanted = true;
					break;
				}
			}
		}
		boolean want = logWanted || leavesWanted;
		if (!want) {
			if (ordinary && wantedList) {
				// Its leaves give nothing wanted (an azalea tree on oak logs...): an ordinary tree, after the wanted ones.
				if (normal.size() < MAX_PENDING) {
					addCandidate(normal, new Candidate(base, true));
				}
				return null;
			}
			if (!ordinary) {
				return null;
			}
		}
		List<BlockPos> sorted = new ArrayList<>(logs);
		sorted.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		List<BlockPos> plantSpots = new ArrayList<>();
		for (BlockPos log : logs) {
			if (log.getY() == base.getY() && isSoil(level.getBlockState(log.below()))) {
				plantSpots.add(log);
			}
		}
		ArrayDeque<BlockPos> canopy = leavesWanted ? canopy(ctx, leafSeeds, base, topY) : new ArrayDeque<>();
		return new Tree(base, logBlock, new ArrayDeque<>(sorted), canopy, plantSpots, saplingFor(logBlock), want);
	}

	/** Natural leaves of the tree (spreading from those touching its logs), bounded, top-down. */
	private static ArrayDeque<BlockPos> canopy(WorkContext ctx, Set<BlockPos> seeds, BlockPos base, int topY) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(seeds);
		Set<BlockPos> seen = new HashSet<>(seeds);
		List<BlockPos> found = new ArrayList<>();
		int spread = MAX_TREE_SPREAD + 2;
		while (!queue.isEmpty() && found.size() < MAX_LEAVES) {
			BlockPos pos = queue.poll();
			if (ctx.foreign(pos)) {
				continue;
			}
			found.add(pos);
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				if (seen.size() >= MAX_LEAVES * 4 || Math.abs(next.getX() - base.getX()) > spread || Math.abs(next.getZ() - base.getZ()) > spread
						|| next.getY() < base.getY() || next.getY() > topY + 3 || !seen.add(next)) {
					continue;
				}
				if (ctx.loaded(next) && isNaturalLeaves(ctx.level.getBlockState(next))) {
					queue.add(next);
				}
			}
		}
		found.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		return new ArrayDeque<>(found);
	}

	private boolean isRejected(int x, int z, long now) {
		long key = ChunkKey.of(x, z);
		Long until = rejected.get(key);
		if (until == null) {
			return false;
		}
		if (until <= now) {
			rejected.remove(key);
			bigBases.remove(key);
			return false;
		}
		return true;
	}

	/** The columns of a rejected tree are skipped by the next searches for a while. */
	private void reject(Set<BlockPos> logs, long now) {
		if (rejected.size() >= MAX_REJECTED) {
			rejected.values().removeIf(until -> until <= now);
			bigBases.values().removeIf(until -> until <= now);
			if (rejected.size() >= MAX_REJECTED) {
				rejected.clear();
				bigBases.clear();
			}
		}
		for (BlockPos log : logs) {
			rejected.put(ChunkKey.of(log.getX(), log.getZ()), now + REJECT_TICKS);
		}
	}

	private static String blockId(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).toString();
	}

	private static boolean isNaturalLeaves(BlockState state) {
		return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
	}

	/** Ground a natural tree grows on. Since 26.x #dirt no longer holds grass, podzol, moss or mud: #substrate_overworld does. */
	private static boolean isSoil(BlockState state) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.NYLIUM) || state.is(Blocks.MANGROVE_ROOTS);
	}

	/** oak_log → oak_sapling, mangrove_log → mangrove_propagule, crimson_stem → crimson_fungus. */
	static @Nullable Block saplingFor(Block log) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(log);
		String path = id.getPath();
		List<String> candidates = new ArrayList<>();
		if (path.endsWith("_log")) {
			String wood = path.substring(0, path.length() - 4);
			candidates.add(wood + "_sapling");
			candidates.add(wood + "_propagule");
		} else if (path.endsWith("_stem")) {
			candidates.add(path.substring(0, path.length() - 5) + "_fungus");
		}
		for (String candidate : candidates) {
			Identifier saplingId = Identifier.fromNamespaceAndPath(id.getNamespace(), candidate);
			if (BuiltInRegistries.BLOCK.containsKey(saplingId)) {
				Block block = BuiltInRegistries.BLOCK.getValue(saplingId);
				if (block != null && block != Blocks.AIR) {
					return block;
				}
			}
		}
		return null;
	}
}
