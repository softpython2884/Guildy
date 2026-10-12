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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Harvests the mature crops of the villager's field zone or, without zone, around the territory (disc centred on the
 * nearest point of the territory, see {@link WorkAnchor}: it never drifts away field after field). Wheat, carrots,
 * potatoes, beetroots and other crops are replanted at age 0 with one seed of their own drops; "any crop" fields also
 * give melons and pumpkins (stem kept) and sugar cane (base kept). Incremental nearest-first scan of the surface (+ a
 * band around the fanion height for covered fields), resumed where it stopped, loaded chunks only, never in another
 * guild's territory. Crops giving a wanted item ("objectifs prioritaires") come first, even up to
 * {@code zones.wishlistMargin} blocks outside the area. When nothing is ripe it sows empty farmland (and tills
 * hydrated soil in field zones), see {@link CropSower}.
 */
final class FarmerTask extends TaskHandler {
	private enum Phase {
		SEARCH,
		GO_CROP,
		HARVEST,
		/** Nothing ripe: a sowing round (empty farmland, tilling in field zones, see {@link CropSower}). */
		PLANT,
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
	/** With wanted items the whole area is read before ordinary crops are taken: it is read faster. */
	private static final int WISH_CHUNKS_PER_STEP = 3;
	private static final int MAX_CANE = 16;
	private static final double HARVEST_REACH_SQ = 3.0 * 3.0;
	/** Nothing loaded in the area: look again soon (a player may come back). */
	private static final long UNLOADED_RETRY_TICKS = 3 * 20L;
	private static final ItemStack HOE = new ItemStack(Items.IRON_HOE);

	private Phase phase = Phase.SEARCH;
	/** Ripe plants giving a wanted item (harvested first, also in the margin). */
	private final List<BlockPos> wanted = new ArrayList<>();
	/** Other ripe plants of the area. */
	private final List<BlockPos> queue = new ArrayList<>();
	private @Nullable BlockPos target;
	private boolean targetWanted;
	private @Nullable WorkZone zone;
	private WorkWishes wishes = WorkWishes.NONE;
	private String areaKey = "";
	private WorkArea.@Nullable Scan scan;
	/** Area of the last search (inside / margin checks). */
	private @Nullable WorkArea area;
	/** Automatic mode: centre of the current search (plants nearest to it first); null = nearest to the villager. */
	private @Nullable BlockPos anchor;
	private int lastRead = -1;
	private int lastUnloaded;
	private long nextScan;
	private long retryAt;
	/** Where the task started: centre of the automatic search of a guild without territory. */
	private @Nullable BlockPos home;
	private final DepositFlow deposit = new DepositFlow();
	private final CropSower sower = new CropSower();
	/** No deposit chest usable: sowing goes on meanwhile, the deposit is tried again at this tick. */
	private long depositRetryAt;

	@Override
	void tick(WorkContext ctx) {
		if (home == null) {
			home = ctx.villager.blockPosition();
		}
		zone = Zones.assignedZone(ctx.guild, ctx.record);
		String key = areaKey(zone) + "|" + WorkWishes.keyOf(ctx.record, zone);
		if (!key.equals(areaKey)) {
			areaKey = key;
			wishes = WorkWishes.of(ctx.record, zone);
			scan = null;
			area = null;
			anchor = null;
			wanted.clear();
			queue.clear();
			target = null;
			lastRead = -1;
			nextScan = 0;
			sower.reset();
			if (phase == Phase.GO_CROP || phase == Phase.HARVEST || phase == Phase.PLANT) {
				phase = Phase.SEARCH;
			}
		}
		switch (phase) {
			case SEARCH -> search(ctx);
			case GO_CROP -> goCrop(ctx);
			case HARVEST -> harvest(ctx);
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

	private ZoneObjective objective() {
		return zone != null ? zone.objective : ZoneObjective.ANY_CROP;
	}

	private void search(WorkContext ctx) {
		if (ctx.mustReturn()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		target = pollValid(ctx);
		if (target == null) {
			if (scan == null && wanted.isEmpty() && queue.isEmpty() && ctx.now >= nextScan) {
				startScan(ctx);
			}
			WorkArea.Scan running = scan;
			if (running != null) {
				int chunks = wishes.isEmpty() ? CHUNKS_PER_STEP : WISH_CHUNKS_PER_STEP;
				for (int i = 0; i < chunks && wanted.size() < MAX_QUEUE; i++) {
					LevelChunk chunk = running.next(ctx);
					if (chunk == null) {
						break;
					}
					int stop = collect(ctx, running.area, chunk, running.startColumn());
					if (stop >= 0) {
						// Big field: the rest of this chunk is read once the queue is harvested.
						running.resumeAt(stop);
						break;
					}
				}
				if (running.done()) {
					lastRead = running.readChunks();
					lastUnloaded = running.unloadedChunks();
					if (lastRead == 0) {
						nextScan = Math.min(nextScan, ctx.now + UNLOADED_RETRY_TICKS);
					}
					scan = null;
				}
				target = pollValid(ctx);
			}
		}
		if (target == null) {
			WorkZone current = zone;
			if (scan != null || !wanted.isEmpty() || !queue.isEmpty()) {
				ctx.status(wishes.isEmpty() ? "villager.status.farmer.searching" : "villager.work.farmer.searching_wanted");
			} else if (lastRead == 0 && lastUnloaded > 0) {
				if (current != null) {
					idle(ctx, "villager.status.zone_unloaded", current.name);
				} else {
					idle(ctx, "villager.work.area_unloaded");
				}
			} else if (current != null) {
				idle(ctx, "villager.status.farmer.waiting_zone", current.name, Lang.raw(current.objective.langKey()));
			} else {
				idle(ctx, "villager.work.farmer.waiting_auto", ctx.config.zones.autoSearchRadius);
			}
			return;
		}
		phase = Phase.GO_CROP;
		goCrop(ctx);
	}

	private void startScan(WorkContext ctx) {
		BlockPos villager = ctx.villager.blockPosition();
		int margin = wishes.isEmpty() ? 0 : ctx.config.zones.wishlistMargin;
		WorkZone current = zone;
		WorkArea next;
		if (current != null) {
			next = WorkArea.of(current, margin);
			anchor = null;
		} else {
			next = WorkArea.anchored(ctx.guild, villager, home != null ? home : villager, ctx.config, margin);
			anchor = next.center();
		}
		area = next;
		scan = new WorkArea.Scan(next, anchor != null ? anchor : villager);
		nextScan = ctx.now + ctx.workTicks(ctx.config.work.scanIntervalSeconds);
	}

	/** Nothing to harvest: bring the crops home, else sow, else wait. */
	private void idle(WorkContext ctx, String status, Object... args) {
		boolean carrying = Planting.hasDepositable(ctx, sower.keep(ctx, zone));
		if (carrying && ctx.now >= depositRetryAt) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
		} else if (home != null && sower.ready(ctx, zone)) {
			phase = Phase.PLANT;
			plantStep(ctx);
		} else if (carrying) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
		} else {
			Planting.idleStatus(ctx, sower, status, args);
		}
	}

	/** One step of the sowing round; a full bag goes home first, the end of the round goes back to the search. */
	private void plantStep(WorkContext ctx) {
		if (ctx.mustReturn()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		BlockPos start = home != null ? home : ctx.villager.blockPosition();
		if (sower.tick(ctx, zone, start) == Planter.Result.DONE) {
			phase = Phase.SEARCH;
		}
	}

	private void goCrop(WorkContext ctx) {
		BlockPos current = target;
		if (current == null || !ctx.loaded(current) || kindAt(ctx, current, targetWanted) == Kind.NONE) {
			phase = Phase.SEARCH;
			return;
		}
		switch (Movement.moveTo(ctx, current, 2.0, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				target = null;
				phase = Phase.SEARCH;
			}
			case MOVING -> {
				if (targetWanted) {
					ctx.status("villager.work.farmer.going_wanted", current.getX(), current.getY(), current.getZ());
				} else if (zone != null) {
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
		if (target != null && harvestOne(ctx, target, targetWanted)) {
			done++;
		}
		target = null;
		// Neighbouring ripe plants within reach, same second (wanted ones first).
		done += harvestAround(ctx, wanted, true, budget - done);
		if (wishes.isEmpty() || (scan == null && wanted.isEmpty())) {
			done += harvestAround(ctx, queue, false, budget - done);
		}
		if (done > 0) {
			WorldUtil.swing(ctx.villager);
			ctx.status("villager.status.farmer.harvesting");
		}
		phase = ctx.mustReturn() ? Phase.DEPOSIT : Phase.SEARCH;
	}

	private int harvestAround(WorkContext ctx, List<BlockPos> list, boolean wantedOnly, int budget) {
		int done = 0;
		Iterator<BlockPos> it = list.iterator();
		while (it.hasNext() && done < budget && !ctx.mustReturn()) {
			BlockPos pos = it.next();
			if (ctx.villager.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > HARVEST_REACH_SQ) {
				continue;
			}
			it.remove();
			if (harvestOne(ctx, pos, wantedOnly)) {
				done++;
			}
		}
		return done;
	}

	/** Harvests one ripe plant (real drops). @return true when something was harvested */
	private boolean harvestOne(WorkContext ctx, BlockPos pos, boolean wantedOnly) {
		ServerLevel level = ctx.level;
		if (!ctx.loaded(pos) || ctx.foreign(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		Kind kind = kindAt(ctx, pos, wantedOnly);
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
		// One stack of seeds to sow stays in the bag.
		DepositFlow.State state = deposit.tick(ctx, sower.keep(ctx, zone));
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
					depositRetryAt = nextScan;
				}
			}
		}
	}

	/**
	 * Next plant to harvest, nearest first (to the anchor in automatic mode): wanted ones first; ordinary ones right away
	 * without wanted items, otherwise once the whole area was read and no wanted plant is left.
	 */
	private @Nullable BlockPos pollValid(WorkContext ctx) {
		int[] checks = {0};
		BlockPos found = pollNearest(ctx, wanted, true, checks);
		if (found != null) {
			targetWanted = true;
			return found;
		}
		if (!wishes.isEmpty() && (scan != null || !wanted.isEmpty())) {
			return null;
		}
		found = pollNearest(ctx, queue, false, checks);
		targetWanted = false;
		return found;
	}

	private @Nullable BlockPos pollNearest(WorkContext ctx, List<BlockPos> list, boolean wantedOnly, int[] checks) {
		while (!list.isEmpty() && checks[0]++ < 16) {
			int best = 0;
			double bestDist = Double.MAX_VALUE;
			BlockPos reference = anchor;
			for (int i = 0; i < list.size(); i++) {
				BlockPos pos = list.get(i);
				double dist = reference != null ? pos.distSqr(reference) : ctx.distSq(pos);
				if (dist < bestDist) {
					bestDist = dist;
					best = i;
				}
			}
			BlockPos pos = list.remove(best);
			if (ctx.loaded(pos) && !ctx.foreign(pos) && kindAt(ctx, pos, wantedOnly) != Kind.NONE) {
				return pos;
			}
		}
		return null;
	}

	/**
	 * What can be harvested at {@code pos}: a plant giving a wanted item (whatever the zone objective, also in the
	 * margin), else (unless {@code wantedOnly}) a plant of the objective inside the area.
	 */
	private Kind kindAt(WorkContext ctx, BlockPos pos, boolean wantedOnly) {
		BlockState state = ctx.level.getBlockState(pos);
		Kind any = detect(ctx.level, pos, state, ZoneObjective.ANY_CROP);
		if (any == Kind.NONE) {
			return Kind.NONE;
		}
		if (wishes.cropWanted(state.getBlock())) {
			return any;
		}
		WorkArea current = area;
		if (wantedOnly || (current != null && !current.contains(pos))) {
			return Kind.NONE;
		}
		return detect(ctx.level, pos, state, objective());
	}

	/**
	 * Ripe plants of the chunk's columns (inside the area, or its margin for wanted plants), from column {@code start}:
	 * surface first, plus the band around the reference height.
	 *
	 * @return the column where reading stopped because the lists are full, -1 when the chunk is done
	 */
	private int collect(WorkContext ctx, WorkArea area, LevelChunk chunk, int start) {
		boolean wishing = !wishes.isEmpty();
		int vertical = ctx.config.farmer.verticalRange;
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<BlockPos> found = new ArrayList<>();
		for (int index = Math.max(0, start); index < 256; index++) {
			int x = x0 + (index >> 4);
			int z = z0 + (index & 15);
			boolean core = area.contains(x, z);
			if (!core && (!wishing || !area.reaches(x, z))) {
				continue;
			}
			found.clear();
			// Top motion-blocking block: farmland under a crop, the ground under sugar cane, a melon itself.
			int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			for (int y = surface; y <= surface + 2; y++) {
				check(ctx.level, chunk, pos.set(x, y, z), found);
			}
			for (int y = area.refY - vertical; y <= area.refY + vertical; y++) {
				if (y < surface || y > surface + 2) {
					check(ctx.level, chunk, pos.set(x, y, z), found);
				}
			}
			for (BlockPos plant : found) {
				BlockState state = chunk.getBlockState(plant);
				if (wishing && wishes.cropWanted(state.getBlock())) {
					if (wanted.size() >= MAX_QUEUE) {
						return index;
					}
					if (!wanted.contains(plant)) {
						wanted.add(plant);
					}
				} else if (core && detect(ctx.level, plant, state, objective()) != Kind.NONE) {
					if (queue.size() < MAX_QUEUE) {
						if (!queue.contains(plant)) {
							queue.add(plant);
						}
					} else if (!wishing) {
						return index;
					}
					// With wanted items, ordinary plants beyond the queue are found again by the next search.
				}
			}
		}
		return -1;
	}

	/** Any ripe plant (the objective is checked by the caller). */
	private static void check(ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, List<BlockPos> found) {
		BlockState state = chunk.getBlockState(pos);
		if (!state.isAir() && detect(level, pos, state, ZoneObjective.ANY_CROP) != Kind.NONE) {
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
