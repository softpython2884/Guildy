package dev.guildy.villager;

import dev.guildy.core.Lang;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Real tree cutting in the villager's forest zone (or, without zone, around itself). An incremental nearest-first scan
 * (a couple of loaded chunks per second, one heightmap lookup per column + a short trunk walk) collects tree bases; a
 * tree is only accepted when it carries natural (non-persistent) leaves, is made of the zone's wood, and has no log in
 * another guild's territory, so player buildings are never cut. One log per second, top-down; saplings are replanted
 * at the trunk base.
 */
final class LumberjackTask extends TaskHandler {
	private enum Phase {
		SEARCH,
		GO_TREE,
		CUT,
		DEPOSIT,
		BLOCKED
	}

	/** Trees validated (bounded flood fill) per step. */
	private static final int MAX_VALIDATIONS = 2;
	private static final int CHUNKS_PER_STEP = 2;
	private static final int MAX_PENDING = 48;
	private static final int MAX_TRUNK_HEIGHT = 40;
	private static final int MAX_TREE_SPREAD = 6;
	private static final int MIN_NATURAL_LEAVES = 3;
	private static final ItemStack AXE = new ItemStack(Items.IRON_AXE);

	private record Tree(BlockPos base, Block log, ArrayDeque<BlockPos> logs, List<BlockPos> plantSpots, @Nullable Block sapling) {
	}

	private Phase phase = Phase.SEARCH;
	private @Nullable Tree tree;
	private @Nullable WorkZone zone;
	private String areaKey = "";
	private WorkArea.@Nullable Scan scan;
	/** Tree bases found by the scan, not validated yet. */
	private final List<BlockPos> pending = new ArrayList<>();
	private long nextScan;
	private long retryAt;
	/** Extra logs earned by the work speed upgrade (fractional rate accumulated across steps). */
	private double bonusCuts;
	private final DepositFlow deposit = new DepositFlow();

	@Override
	void tick(WorkContext ctx) {
		zone = Zones.assignedZone(ctx.guild, ctx.record);
		String key = areaKey(zone);
		if (!key.equals(areaKey)) {
			areaKey = key;
			scan = null;
			pending.clear();
			nextScan = 0;
			if (phase == Phase.GO_TREE || phase == Phase.CUT) {
				tree = null;
				phase = Phase.SEARCH;
			}
		}
		switch (phase) {
			case SEARCH -> search(ctx);
			case GO_TREE -> goTree(ctx);
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
			case DEPOSIT -> depositStep(ctx);
			case BLOCKED -> {
				if (ctx.now >= retryAt) {
					phase = Phase.DEPOSIT;
					depositStep(ctx);
				}
			}
		}
	}

	private void search(WorkContext ctx) {
		if (ctx.mustReturn()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		WorkZone current = zone;
		if (current != null && !ctx.ticking(current.pos)) {
			// Far zone without any player around: nothing can be cut there right now.
			scan = null;
			pending.clear();
			idle(ctx, "villager.status.zone_unloaded", current.name);
			return;
		}
		if (scan == null && pending.isEmpty()) {
			if (ctx.now < nextScan) {
				idleNoTree(ctx);
				return;
			}
			BlockPos from = ctx.villager.blockPosition();
			WorkArea area = current != null ? WorkArea.of(current) : WorkArea.auto(ctx.guild, from, ctx.config);
			scan = new WorkArea.Scan(area, from);
			nextScan = ctx.now + ctx.workTicks(ctx.config.work.scanIntervalSeconds);
		}
		int[] budget = {MAX_VALIDATIONS};
		Tree found = validatePending(ctx, budget);
		WorkArea.Scan running = scan;
		if (found == null && running != null) {
			for (int i = 0; i < CHUNKS_PER_STEP && pending.size() < MAX_PENDING; i++) {
				LevelChunk chunk = running.next(ctx);
				if (chunk == null) {
					break;
				}
				if (!collectBases(running.area, chunk)) {
					// Dense forest: the rest of this chunk is read again once some bases are handled.
					running.retry();
					break;
				}
			}
			if (running.done()) {
				scan = null;
			}
			found = validatePending(ctx, budget);
		}
		if (found != null) {
			tree = found;
			phase = Phase.GO_TREE;
			goTree(ctx);
			return;
		}
		if (scan == null && pending.isEmpty()) {
			idleNoTree(ctx);
		} else if (current != null) {
			ctx.status("villager.status.lumberjack.searching_zone", current.name);
		} else {
			ctx.status("villager.status.lumberjack.searching");
		}
	}

	/** Nothing to cut: bring the logs home, else wait. */
	private void idleNoTree(WorkContext ctx) {
		WorkZone current = zone;
		if (current != null) {
			idle(ctx, "villager.status.lumberjack.no_tree_zone", current.name, Lang.raw(current.objective.langKey()));
		} else {
			idle(ctx, "villager.status.lumberjack.no_tree_auto", ctx.config.zones.autoSearchRadius);
		}
	}

	private void idle(WorkContext ctx, String status, Object... args) {
		if (!ctx.record.carried.isEmpty()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
		} else {
			ctx.status(status, args);
		}
	}

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
				if (zone != null) {
					ctx.status("villager.status.lumberjack.going_zone", zone.name);
				} else {
					ctx.status("villager.status.lumberjack.going_auto", current.base.getX(), current.base.getY(), current.base.getZ());
				}
			}
			case ARRIVED -> {
				phase = Phase.CUT;
				cut(ctx);
			}
		}
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
		for (ItemStack drop : drops) {
			ItemStack leftover = ctx.addCarried(drop);
			if (!leftover.isEmpty()) {
				ItemEntity entity = new ItemEntity(level, ctx.villager.getX(), ctx.villager.getY() + 0.5, ctx.villager.getZ(), leftover);
				entity.setDefaultPickUpDelay();
				level.addFreshEntity(entity);
			}
		}
		ctx.record.stats.logs++;
		GuildManager.markDirty();
		if (zone != null) {
			ctx.status("villager.status.lumberjack.cutting", current.logs.size());
		} else {
			ctx.status("villager.status.lumberjack.cutting_auto", current.base.getX(), current.base.getY(), current.base.getZ(), current.logs.size());
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
		if (scan == null && pending.isEmpty()) {
			// Every tree found by the last search is done: look again right away (new trees around, skipped ones).
			nextScan = ctx.now;
		}
		phase = ctx.mustReturn() ? Phase.DEPOSIT : Phase.SEARCH;
		ctx.status("villager.status.lumberjack.searching");
	}

	private void depositStep(WorkContext ctx) {
		DepositFlow.State state = deposit.tick(ctx);
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
	 * Trunk bases of the chunk's columns inside the area: top block (no leaves) is a matching log standing on soil.
	 *
	 * @return false when the pending list filled up before the end of the chunk
	 */
	private boolean collectBases(WorkArea area, LevelChunk chunk) {
		ZoneObjective objective = objective();
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				if (!area.contains(x, z)) {
					continue;
				}
				if (pending.size() >= MAX_PENDING) {
					return false;
				}
				int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockState state = chunk.getBlockState(pos.set(x, top, z));
				if (!state.is(BlockTags.LOGS) || !objective.acceptsLog(blockId(state.getBlock()))) {
					continue;
				}
				int y = top;
				int steps = 0;
				while (steps < MAX_TRUNK_HEIGHT && chunk.getBlockState(pos.set(x, y - 1, z)).is(state.getBlock())) {
					y--;
					steps++;
				}
				BlockPos base = new BlockPos(x, y, z);
				if (isSoil(chunk.getBlockState(base.below())) && !pending.contains(base)) {
					pending.add(base);
				}
			}
		}
		return true;
	}

	/** Validates pending bases nearest first (within the step budget). */
	private @Nullable Tree validatePending(WorkContext ctx, int[] budget) {
		if (pending.isEmpty()) {
			return null;
		}
		pending.sort(Comparator.comparingDouble(ctx::distSq));
		Set<BlockPos> covered = new HashSet<>();
		Tree found = null;
		while (found == null && !pending.isEmpty() && budget[0] > 0) {
			BlockPos base = pending.removeFirst();
			if (covered.contains(base)) {
				continue;
			}
			budget[0]--;
			found = validate(ctx, base, covered);
		}
		// Other bases of the same tree (large trunks) or of rejected trees are done.
		pending.removeIf(covered::contains);
		return found;
	}

	/** Collects the connected logs of the tree (same block, bounded) and checks it is a natural tree we may cut. */
	private @Nullable Tree validate(WorkContext ctx, BlockPos base, Set<BlockPos> covered) {
		ServerLevel level = ctx.level;
		if (!ctx.loaded(base)) {
			return null;
		}
		BlockState baseState = level.getBlockState(base);
		Block logBlock = baseState.getBlock();
		if (!baseState.is(BlockTags.LOGS) || !objective().acceptsLog(blockId(logBlock))) {
			return null;
		}
		int maxLogs = ctx.config.lumberjack.maxLogsPerTree;
		Set<BlockPos> logs = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(base);
		logs.add(base);
		int naturalLeaves = 0;
		boolean valid = true;
		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();
			if (ctx.foreign(current)) {
				valid = false;
			}
			for (Direction direction : Direction.values()) {
				BlockPos side = current.relative(direction);
				if (ctx.loaded(side) && isNaturalLeaves(level.getBlockState(side))) {
					naturalLeaves++;
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
			return null;
		}
		List<BlockPos> sorted = new ArrayList<>(logs);
		sorted.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		List<BlockPos> plantSpots = new ArrayList<>();
		for (BlockPos log : logs) {
			if (log.getY() == base.getY() && isSoil(level.getBlockState(log.below()))) {
				plantSpots.add(log);
			}
		}
		return new Tree(base, logBlock, new ArrayDeque<>(sorted), plantSpots, saplingFor(logBlock));
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
