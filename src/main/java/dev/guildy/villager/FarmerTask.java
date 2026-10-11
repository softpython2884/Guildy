package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Harvests the mature crops of the villager's field zone (or, without zone, around itself). Wheat, carrots, potatoes,
 * beetroots and other crops are replanted at age 0 with one seed of their own drops; "any crop" fields also give
 * melons and pumpkins (stem kept) and sugar cane (base kept). Incremental nearest-first scan of the surface (+ a band
 * around the fanion height for covered fields), loaded chunks only, never in another guild's territory.
 */
final class FarmerTask extends TaskHandler {
	private enum Phase {
		SEARCH,
		GO_CROP,
		HARVEST,
		DEPOSIT,
		BLOCKED
	}

	enum Kind {
		NONE,
		/** Mature crop block: harvested and replanted. */
		CROP,
		/** Melon / pumpkin attached to a stem: picked, the stem stays. */
		FRUIT,
		/** Second segment of a sugar cane: everything above the base is cut. */
		CANE
	}

	private static final int MAX_QUEUE = 96;
	private static final int CHUNKS_PER_STEP = 1;
	private static final int MAX_CANE = 16;
	private static final double HARVEST_REACH_SQ = 3.0 * 3.0;
	private static final ItemStack HOE = new ItemStack(Items.IRON_HOE);

	private Phase phase = Phase.SEARCH;
	private ArrayDeque<BlockPos> queue = new ArrayDeque<>();
	private @Nullable BlockPos target;
	private @Nullable WorkZone zone;
	private String areaKey = "";
	private WorkArea.@Nullable Scan scan;
	private long nextScan;
	private long retryAt;
	private final DepositFlow deposit = new DepositFlow();

	@Override
	void tick(WorkContext ctx) {
		zone = Zones.assignedZone(ctx.guild, ctx.record);
		String key = areaKey(zone);
		if (!key.equals(areaKey)) {
			areaKey = key;
			scan = null;
			queue.clear();
			target = null;
			nextScan = 0;
			if (phase == Phase.GO_CROP || phase == Phase.HARVEST) {
				phase = Phase.SEARCH;
			}
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

	private ZoneObjective objective() {
		return zone != null ? zone.objective : ZoneObjective.ANY_CROP;
	}

	private void search(WorkContext ctx) {
		if (ctx.mustReturn()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		WorkZone current = zone;
		if (current != null && !ctx.ticking(current.pos)) {
			scan = null;
			queue.clear();
			idle(ctx, "villager.status.zone_unloaded", current.name);
			return;
		}
		target = pollValid(ctx);
		if (target == null) {
			if (scan == null && ctx.now >= nextScan) {
				BlockPos from = ctx.villager.blockPosition();
				WorkArea area = current != null ? WorkArea.of(current) : WorkArea.auto(ctx.guild, from, ctx.config);
				scan = new WorkArea.Scan(area, from);
				nextScan = ctx.now + ctx.workTicks(ctx.config.work.scanIntervalSeconds);
			}
			WorkArea.Scan running = scan;
			if (running != null) {
				for (int i = 0; i < CHUNKS_PER_STEP && queue.size() < MAX_QUEUE; i++) {
					LevelChunk chunk = running.next(ctx);
					if (chunk == null) {
						break;
					}
					if (!collect(ctx, running.area, chunk)) {
						// Big field: the chunk is read again once the queue is harvested.
						running.retry();
						break;
					}
				}
				if (running.done()) {
					scan = null;
				}
				target = pollValid(ctx);
			}
		}
		if (target == null) {
			if (scan != null) {
				ctx.status("villager.status.farmer.searching");
			} else if (current != null) {
				idle(ctx, "villager.status.farmer.waiting_zone", current.name, Lang.raw(current.objective.langKey()));
			} else {
				idle(ctx, "villager.status.farmer.waiting_auto", ctx.config.zones.autoSearchRadius);
			}
			return;
		}
		phase = Phase.GO_CROP;
		goCrop(ctx);
	}

	/** Nothing to harvest: bring the crops home, else wait. */
	private void idle(WorkContext ctx, String status, Object... args) {
		if (!ctx.record.carried.isEmpty()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
		} else {
			ctx.status(status, args);
		}
	}

	private void goCrop(WorkContext ctx) {
		BlockPos current = target;
		if (current == null || !ctx.loaded(current) || detect(ctx.level, current, ctx.level.getBlockState(current), objective()) == Kind.NONE) {
			phase = Phase.SEARCH;
			return;
		}
		switch (Movement.moveTo(ctx, current, 2.0, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				target = null;
				phase = Phase.SEARCH;
			}
			case MOVING -> {
				if (zone != null) {
					ctx.status("villager.status.farmer.going_zone", zone.name);
				} else {
					ctx.status("villager.status.farmer.going_auto", current.getX(), current.getY(), current.getZ());
				}
			}
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
		// Neighbouring ripe crops within reach, same second.
		Iterator<BlockPos> it = queue.iterator();
		while (it.hasNext() && done < budget && !ctx.mustReturn()) {
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
		phase = ctx.mustReturn() ? Phase.DEPOSIT : Phase.SEARCH;
	}

	/** Harvests one ripe plant (real drops). @return true when something was harvested */
	private boolean harvestOne(WorkContext ctx, BlockPos pos) {
		ServerLevel level = ctx.level;
		if (!ctx.loaded(pos) || ctx.foreign(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		Kind kind = detect(level, pos, state, objective());
		if (kind == Kind.NONE) {
			return false;
		}
		List<ItemStack> drops = new ArrayList<>();
		Movement.hold(ctx, pos);
		WorldUtil.breakEffect(level, pos, state);
		switch (kind) {
			case CROP -> {
				CropBlock crop = (CropBlock) state.getBlock();
				drops.addAll(Block.getDrops(state, level, pos, null, ctx.villager, HOE));
				Item seed = crop.asItem();
				boolean replant = false;
				for (ItemStack drop : drops) {
					if (!drop.isEmpty() && drop.is(seed)) {
						drop.shrink(1);
						replant = true;
						break;
					}
				}
				if (replant) {
					level.setBlockAndUpdate(pos, crop.getStateForAge(0));
				} else {
					level.removeBlock(pos, false);
				}
			}
			case FRUIT -> {
				drops.addAll(Block.getDrops(state, level, pos, null, ctx.villager, HOE));
				level.removeBlock(pos, false);
			}
			case CANE -> {
				// Cut from the top down so no segment pops off as a loose item; the base stays and grows again.
				int height = 0;
				while (height < MAX_CANE && level.getBlockState(pos.above(height)).is(Blocks.SUGAR_CANE)) {
					height++;
				}
				for (int i = height - 1; i >= 0; i--) {
					level.removeBlock(pos.above(i), false);
				}
				drops.add(new ItemStack(Items.SUGAR_CANE, height));
			}
			default -> {
			}
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
			case DONE -> {
				if (Missions.complete(ctx)) {
					return;
				}
				phase = Phase.SEARCH;
			}
			case NO_CHEST, ALL_FULL -> {
				ctx.status(DepositFlow.blockedStatus(state));
				if (ctx.mustReturn()) {
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
		ZoneObjective objective = objective();
		while (!queue.isEmpty() && checks++ < 16) {
			BlockPos pos = queue.poll();
			if (ctx.loaded(pos) && !ctx.foreign(pos) && detect(ctx.level, pos, ctx.level.getBlockState(pos), objective) != Kind.NONE) {
				return pos;
			}
		}
		return null;
	}

	/**
	 * Ripe plants of one chunk (columns inside the area): surface first, plus the band around the reference height.
	 *
	 * @return false when they did not all fit in the queue
	 */
	private boolean collect(WorkContext ctx, WorkArea area, LevelChunk chunk) {
		ZoneObjective objective = objective();
		int vertical = ctx.config.farmer.verticalRange;
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<BlockPos> found = new ArrayList<>();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				if (!area.contains(x, z)) {
					continue;
				}
				// Top motion-blocking block: farmland under a crop, the ground under sugar cane, a melon itself.
				int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				for (int y = surface; y <= surface + 2; y++) {
					check(ctx.level, chunk, pos.set(x, y, z), objective, found);
				}
				for (int y = area.refY - vertical; y <= area.refY + vertical; y++) {
					if (y < surface || y > surface + 2) {
						check(ctx.level, chunk, pos.set(x, y, z), objective, found);
					}
				}
			}
		}
		BlockPos villager = ctx.villager.blockPosition();
		found.sort((a, b) -> Double.compare(a.distSqr(villager), b.distSqr(villager)));
		for (BlockPos plant : found) {
			if (queue.size() >= MAX_QUEUE) {
				return false;
			}
			queue.add(plant);
		}
		return true;
	}

	private static void check(ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, ZoneObjective objective, List<BlockPos> found) {
		BlockState state = chunk.getBlockState(pos);
		if (!state.isAir() && detect(level, pos, state, objective) != Kind.NONE) {
			found.add(pos.immutable());
		}
	}

	/** What can be harvested at {@code pos} for this objective. */
	static Kind detect(ServerLevel level, BlockPos pos, BlockState state, ZoneObjective objective) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			return !(crop instanceof TorchflowerCropBlock) && crop.isMaxAge(state)
					&& objective.acceptsCrop(BuiltInRegistries.BLOCK.getKey(block).toString()) ? Kind.CROP : Kind.NONE;
		}
		if (!objective.acceptsExtras()) {
			return Kind.NONE;
		}
		if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
			return hasAttachedStem(level, pos) ? Kind.FRUIT : Kind.NONE;
		}
		if (state.is(Blocks.SUGAR_CANE) && level.isLoaded(pos.below(2))) {
			return level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE) && !level.getBlockState(pos.below(2)).is(Blocks.SUGAR_CANE)
					? Kind.CANE : Kind.NONE;
		}
		return Kind.NONE;
	}

	/** A grown melon / pumpkin: a stem next to it points at it (decorative pumpkins are left alone). */
	private static boolean hasAttachedStem(ServerLevel level, BlockPos fruit) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = fruit.relative(direction);
			if (!level.isLoaded(side)) {
				continue;
			}
			BlockState stem = level.getBlockState(side);
			if (stem.getBlock() instanceof AttachedStemBlock && stem.getValue(AttachedStemBlock.FACING) == direction.getOpposite()) {
				return true;
			}
		}
		return false;
	}
}
