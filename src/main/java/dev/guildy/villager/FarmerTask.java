package dev.guildy.villager;

import dev.guildy.data.ChunkKey;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Harvests mature crops (wheat, carrots, potatoes, beetroots and other CropBlocks) around the work position, inside the
 * territory. Real drops; the crop is replanted at age 0 using one seed taken from its own drops.
 */
final class FarmerTask extends TaskHandler {
	private enum Phase {
		SEARCH,
		GO_CROP,
		HARVEST,
		DEPOSIT,
		BLOCKED
	}

	private static final int MAX_QUEUE = 96;
	private static final double HARVEST_REACH_SQ = 3.0 * 3.0;
	private static final ItemStack HOE = new ItemStack(Items.IRON_HOE);

	private Phase phase = Phase.SEARCH;
	private ArrayDeque<BlockPos> queue = new ArrayDeque<>();
	private @Nullable BlockPos target;
	private long nextScan;
	private long retryAt;
	private final DepositFlow deposit = new DepositFlow();

	@Override
	void tick(WorkContext ctx) {
		if (!checkWorkPos(ctx)) {
			return;
		}
		switch (phase) {
			case SEARCH -> search(ctx);
			case GO_CROP -> goCrop(ctx);
			case HARVEST -> harvest(ctx);
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
		if (ctx.carriedFull()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		target = pollValid(ctx);
		if (target == null && ctx.now >= nextScan) {
			nextScan = ctx.now + ctx.workTicks(ctx.config.work.scanIntervalSeconds);
			if (!ctx.loaded(ctx.record.workPos)) {
				ctx.status("villager.status.workpos_unloaded");
				return;
			}
			queue = scan(ctx);
			target = pollValid(ctx);
		}
		if (target == null) {
			if (!ctx.record.carried.isEmpty()) {
				phase = Phase.DEPOSIT;
				depositStep(ctx);
			} else {
				ctx.status("villager.status.farmer.waiting");
			}
			return;
		}
		phase = Phase.GO_CROP;
		goCrop(ctx);
	}

	private void goCrop(WorkContext ctx) {
		if (target == null || !isMature(ctx.level.getBlockState(target))) {
			phase = Phase.SEARCH;
			return;
		}
		switch (Movement.moveTo(ctx, target, 2.0, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				target = null;
				phase = Phase.SEARCH;
			}
			case MOVING -> ctx.status("villager.status.farmer.going");
			case ARRIVED -> {
				phase = Phase.HARVEST;
				harvest(ctx);
			}
		}
	}

	private void harvest(WorkContext ctx) {
		int budget = (int) Math.max(1, Math.round(ctx.config.farmer.harvestPerSecond * Upgrades.workRate(ctx.guild)));
		int done = 0;
		if (target != null && harvestOne(ctx, target)) {
			done++;
		}
		target = null;
		// Neighbouring mature crops within reach, same second.
		Iterator<BlockPos> it = queue.iterator();
		while (it.hasNext() && done < budget && !ctx.carriedFull()) {
			BlockPos pos = it.next();
			if (ctx.villager.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > HARVEST_REACH_SQ) {
				continue;
			}
			it.remove();
			if (harvestOne(ctx, pos)) {
				done++;
			}
		}
		if (done > 0) {
			WorldUtil.swing(ctx.villager);
			ctx.status("villager.status.farmer.harvesting");
		}
		phase = ctx.carriedFull() ? Phase.DEPOSIT : Phase.SEARCH;
	}

	/** Harvests one mature crop: real drops, replanted at age 0 with one of its own seeds. */
	private boolean harvestOne(WorkContext ctx, BlockPos pos) {
		ServerLevel level = ctx.level;
		if (!ctx.loaded(pos) || !ctx.inTerritory(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (!isMature(state)) {
			return false;
		}
		CropBlock crop = (CropBlock) state.getBlock();
		List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null, ctx.villager, HOE));
		Item seed = crop.asItem();
		boolean replant = false;
		for (ItemStack drop : drops) {
			if (!drop.isEmpty() && drop.is(seed)) {
				drop.shrink(1);
				replant = true;
				break;
			}
		}
		Movement.hold(ctx, pos);
		WorldUtil.breakEffect(level, pos, state);
		if (replant) {
			level.setBlockAndUpdate(pos, crop.getStateForAge(0));
		} else {
			level.removeBlock(pos, false);
		}
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			ItemStack leftover = ctx.addCarried(drop);
			if (!leftover.isEmpty()) {
				ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, leftover);
				entity.setDefaultPickUpDelay();
				level.addFreshEntity(entity);
			}
		}
		ctx.record.stats.crops++;
		GuildManager.markDirty();
		return true;
	}

	private void depositStep(WorkContext ctx) {
		DepositFlow.State state = deposit.tick(ctx);
		switch (state) {
			case WORKING -> {
			}
			case DONE -> phase = Phase.SEARCH;
			case NO_CHEST, ALL_FULL -> {
				ctx.status(DepositFlow.blockedStatus(state));
				if (ctx.carriedFull()) {
					phase = Phase.BLOCKED;
					retryAt = ctx.now + WorkContext.ticks(10);
				} else {
					phase = Phase.SEARCH;
					// Nothing else to do until crops grow: don't retry the deposit every second.
					nextScan = Math.max(nextScan, ctx.now + ctx.workTicks(ctx.config.work.scanIntervalSeconds));
				}
			}
		}
	}

	private @Nullable BlockPos pollValid(WorkContext ctx) {
		int checks = 0;
		while (!queue.isEmpty() && checks++ < 16) {
			BlockPos pos = queue.poll();
			if (ctx.loaded(pos) && ctx.inTerritory(pos) && isMature(ctx.level.getBlockState(pos))) {
				return pos;
			}
		}
		return null;
	}

	/** Bounded scan of the farm area: (2r+1)² columns × (2v+1) heights, loaded guild chunks only. */
	private static ArrayDeque<BlockPos> scan(WorkContext ctx) {
		BlockPos zone = ctx.record.workPos;
		int radius = ctx.config.farmer.radius;
		int vertical = ctx.config.farmer.verticalRange;
		ServerLevel level = ctx.level;
		Map<Long, Boolean> chunkOk = new HashMap<>();
		List<BlockPos> found = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int x = zone.getX() + dx;
				int z = zone.getZ() + dz;
				boolean ok = chunkOk.computeIfAbsent(ChunkKey.ofBlock(x, z),
						k -> level.hasChunk(ChunkKey.x(k), ChunkKey.z(k)) && GuildManager.atChunk(k) == ctx.guild);
				if (!ok) {
					continue;
				}
				for (int dy = -vertical; dy <= vertical; dy++) {
					pos.set(x, zone.getY() + dy, z);
					if (isMature(level.getBlockState(pos))) {
						found.add(pos.immutable());
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(ctx::distSq));
		ArrayDeque<BlockPos> result = new ArrayDeque<>();
		for (int i = 0; i < found.size() && i < MAX_QUEUE; i++) {
			result.add(found.get(i));
		}
		return result;
	}

	private static boolean isMature(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && !(crop instanceof TorchflowerCropBlock) && crop.isMaxAge(state);
	}
}
