package dev.guildy.villager;

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
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real tree cutting inside the territory. A bounded scan (every scanIntervalSeconds at most, one heightmap lookup per
 * column + a short trunk walk) finds natural trees around the work position; a tree is only accepted when all its logs
 * are inside the territory and it carries natural (non-persistent) leaves, so player buildings are never cut.
 * One log per second, top-down; saplings are replanted at the trunk base.
 */
final class LumberjackTask extends TaskHandler {
	private enum Phase {
		SEARCH,
		GO_TREE,
		CUT,
		DEPOSIT,
		BLOCKED
	}

	/** Max trees validated (BFS) per scan. */
	private static final int MAX_VALIDATIONS = 2;
	private static final int MAX_TRUNK_HEIGHT = 40;
	private static final int MAX_TREE_SPREAD = 6;
	private static final int MIN_NATURAL_LEAVES = 3;
	private static final ItemStack AXE = new ItemStack(Items.IRON_AXE);

	private record Tree(BlockPos base, Block log, ArrayDeque<BlockPos> logs, List<BlockPos> plantSpots, @Nullable Block sapling) {
	}

	private Phase phase = Phase.SEARCH;
	private @Nullable Tree tree;
	private long nextScan;
	private long retryAt;
	/** Extra logs earned by the work speed upgrade (fractional rate accumulated across steps). */
	private double bonusCuts;
	private final DepositFlow deposit = new DepositFlow();

	@Override
	void tick(WorkContext ctx) {
		if (!checkWorkPos(ctx)) {
			return;
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
		if (ctx.now < nextScan) {
			if (!ctx.record.carried.isEmpty()) {
				phase = Phase.DEPOSIT;
				depositStep(ctx);
			} else {
				ctx.status("villager.status.lumberjack.no_tree");
			}
			return;
		}
		nextScan = ctx.now + ctx.workTicks(ctx.config.work.scanIntervalSeconds);
		BlockPos zone = ctx.record.workPos;
		if (!ctx.loaded(zone)) {
			ctx.status("villager.status.workpos_unloaded");
			return;
		}
		tree = findTree(ctx, zone);
		if (tree == null) {
			if (!ctx.record.carried.isEmpty()) {
				phase = Phase.DEPOSIT;
				depositStep(ctx);
			} else {
				ctx.status("villager.status.lumberjack.no_tree");
			}
			return;
		}
		phase = Phase.GO_TREE;
		goTree(ctx);
	}

	private void goTree(WorkContext ctx) {
		if (tree == null) {
			phase = Phase.SEARCH;
			return;
		}
		switch (Movement.moveTo(ctx, tree.base, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				tree = null;
				phase = Phase.SEARCH;
			}
			case MOVING -> ctx.status("villager.status.lumberjack.going");
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
		ctx.status("villager.status.lumberjack.cutting", current.logs.size());
	}

	private void finishTree(WorkContext ctx, Tree current) {
		if (ctx.config.lumberjack.replant && current.sapling != null) {
			BlockState sapling = current.sapling.defaultBlockState();
			for (BlockPos spot : current.plantSpots) {
				if (ctx.loaded(spot) && ctx.inTerritory(spot) && ctx.level.getBlockState(spot).isAir() && sapling.canSurvive(ctx.level, spot)) {
					ctx.level.setBlockAndUpdate(spot, sapling);
					ctx.level.playSound(null, spot, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8f, 1.0f);
				}
			}
		}
		tree = null;
		nextScan = ctx.now;
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

	/** Next log still standing (top-down), skipping logs already gone or out of the territory. */
	private static @Nullable BlockPos nextLog(WorkContext ctx, Tree current) {
		while (!current.logs.isEmpty()) {
			BlockPos pos = current.logs.poll();
			if (ctx.loaded(pos) && ctx.inTerritory(pos) && ctx.level.getBlockState(pos).is(current.log)) {
				return pos;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ tree search

	private static @Nullable Tree findTree(WorkContext ctx, BlockPos zone) {
		ServerLevel level = ctx.level;
		int radius = ctx.config.lumberjack.radius;
		Map<Long, Boolean> chunkOk = new HashMap<>();
		List<BlockPos> bases = new ArrayList<>();
		Set<BlockPos> seenBases = new HashSet<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int x = zone.getX() + dx;
				int z = zone.getZ() + dz;
				long key = ChunkKey.ofBlock(x, z);
				// Only loaded chunks owned by the guild (cached per chunk).
				boolean ok = chunkOk.computeIfAbsent(key, k -> level.hasChunk(ChunkKey.x(k), ChunkKey.z(k)) && GuildManager.atChunk(k) == ctx.guild);
				if (!ok) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				pos.set(x, top, z);
				if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
					continue;
				}
				int y = top;
				int steps = 0;
				while (steps < MAX_TRUNK_HEIGHT && level.getBlockState(pos.set(x, y - 1, z)).is(BlockTags.LOGS)) {
					y--;
					steps++;
				}
				BlockPos base = new BlockPos(x, y, z);
				if (isSoil(level.getBlockState(base.below())) && seenBases.add(base)) {
					bases.add(base);
				}
			}
		}
		if (bases.isEmpty()) {
			return null;
		}
		bases.sort(Comparator.comparingDouble(ctx::distSq));
		int validations = 0;
		Set<BlockPos> covered = new HashSet<>();
		for (BlockPos base : bases) {
			if (covered.contains(base)) {
				continue;
			}
			if (validations++ >= MAX_VALIDATIONS) {
				break;
			}
			Tree tree = validate(ctx, base, covered);
			if (tree != null) {
				return tree;
			}
		}
		return null;
	}

	/** Collects the connected logs of the tree (same block, bounded) and checks it is a natural tree in the territory. */
	private static @Nullable Tree validate(WorkContext ctx, BlockPos base, Set<BlockPos> covered) {
		ServerLevel level = ctx.level;
		Block logBlock = level.getBlockState(base).getBlock();
		int maxLogs = ctx.config.lumberjack.maxLogsPerTree;
		Set<BlockPos> logs = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(base);
		logs.add(base);
		int naturalLeaves = 0;
		boolean valid = true;
		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();
			if (!ctx.inTerritory(current)) {
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

	private static boolean isNaturalLeaves(BlockState state) {
		return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
	}

	private static boolean isSoil(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.NYLIUM) || state.is(Blocks.MUD) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
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
