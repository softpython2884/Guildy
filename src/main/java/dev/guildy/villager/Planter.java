package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A planting round of a gathering villager, run between harvests (when its task has nothing to cut / harvest): choose
 * the item (preferred species / crops first, from the bag, else from a storage chest), find spots with an incremental
 * nearest-first scan of the work area (a few loaded chunks per step, never another guild's), fetch the item from the
 * chest if needed, then walk from spot to spot and plant a few per step. The round ends after {@code roundSize}
 * plants (the task then looks for work again), or when no item or no spot is left (then it waits {@code pauseSeconds}
 * and the idle status says why). Subclasses: {@link SaplingPlanter} (lumberjack), {@link CropSower} (farmer).
 */
abstract class Planter {
	enum Result {
		/** Call again next step. */
		WORKING,
		/** Round over: back to the task's own work. */
		DONE
	}

	private enum Stop {
		ROUND_DONE,
		NO_SOURCE,
		NO_SPOT,
		UNLOADED,
		DISABLED
	}

	/**
	 * Where to plant: the block the plant goes in (north-west sapling of a 2×2 square, {@code size} 2), and whether
	 * the ground below must be tilled first.
	 */
	record Spot(BlockPos pos, int size, boolean till) {
	}

	record Settings(int roundSize, int perStep, boolean fromStorage, int fetchAmount, int pauseSeconds) {
	}

	private static final int CHUNKS_PER_STEP = 2;
	private static final double REACH = 2.5;
	/** Other spots planted in the same step when this close (blocks, squared). */
	private static final double WORK_REACH_SQ = 3.2 * 3.2;
	/** Extra items (bone meal) taken from a chest in one trip. */
	private static final int EXTRA_FETCH = 16;
	/** Nothing loaded around: look again soon. */
	private static final long UNLOADED_PAUSE_TICKS = 5 * 20L;

	private boolean active;
	private int done;
	private @Nullable WorkArea area;
	private WorkArea.@Nullable Scan scan;
	private final List<Spot> pending = new ArrayList<>();
	private @Nullable Spot target;
	/** Item planted this round, null = to choose. */
	private @Nullable Item item;
	/** Chest to fetch {@link #fetchItem} from, null = nothing to fetch. */
	private @Nullable BlockPos fetchChest;
	private @Nullable Item fetchItem;
	private boolean extraChecked;
	private final Set<BlockPos> failedChests = new HashSet<>();
	private long nextRoundAt;
	private @Nullable String note;
	/** Zone of the current step (null = automatic mode). */
	protected @Nullable WorkZone zone;

	// ------------------------------------------------------------------ hooks

	/** Planting enabled for this villager here (config, zone or automatic mode). */
	abstract boolean enabled(WorkContext ctx, @Nullable WorkZone zone);

	abstract Settings settings(VillagerConfig config);

	/** The item can be planted by this job (saplings / crop seeds). */
	abstract boolean plantable(Item item);

	/** Items planted at once (4 saplings for a 2×2 tree). */
	int minCount(Item item) {
		return 1;
	}

	/** Item ids to plant first, in order. */
	abstract List<String> preferences(WorkContext ctx, @Nullable WorkZone zone);

	/** Items other than the preferred ones may be planted (zone without a single species / crop, automatic mode). */
	abstract boolean anyAllowed(@Nullable WorkZone zone);

	/** Adds spots of the chunk (at most {@code limit}, none already pending) for this item. */
	abstract void collect(WorkContext ctx, WorkArea area, LevelChunk chunk, Item item, List<Spot> pending, int limit);

	/** Checks the spot again and plants there (consuming the item). @return true when something was done */
	abstract boolean plant(WorkContext ctx, Spot spot, Item item);

	/** Second item to bring from the storage once per round (bone meal), null = none. */
	@Nullable Item extraItem(WorkContext ctx) {
		return null;
	}

	/** Called at every step of a round. */
	void onStep(WorkContext ctx) {
	}

	abstract void searching(WorkContext ctx, Item item, int position, int total);

	abstract void going(WorkContext ctx, Spot spot, Item item, int done, int round);

	abstract void working(WorkContext ctx, Spot spot, Item item, int done, int round);

	abstract void fetching(WorkContext ctx, Item item);

	/** Lang key of the idle note when nothing is left to plant. */
	abstract String noSourceNote();

	/** Lang key of the idle note when no spot is free. */
	abstract String noSpotNote();

	// ------------------------------------------------------------------ state

	/** May a round run now: planting enabled here and not waiting after a round that found nothing. */
	final boolean ready(WorkContext ctx, @Nullable WorkZone zone) {
		return enabled(ctx, zone) && (active || ctx.now >= nextRoundAt);
	}

	/** Note for the idle status (nothing left to plant, no free spot), null when none. */
	final @Nullable String note() {
		return note;
	}

	/** The work area or the wishes changed: everything starts over. */
	final void reset() {
		clearRound();
		nextRoundAt = 0;
		note = null;
	}

	/**
	 * Stacks staying in the bag when it is emptied at the deposit chest: one stack of the item to plant (the most
	 * wanted, else the biggest) and one of the extra item, never a wanted item ("objectifs prioritaires") nor the item of
	 * a quota mission (those are brought back), nothing once the mission goal is reached or with a tiny bag.
	 */
	final Predicate<ItemStack> keep(WorkContext ctx, @Nullable WorkZone zone) {
		if (!enabled(ctx, zone) || ctx.record.goalReached || ctx.config.work.carryStacks < 3) {
			return stack -> false;
		}
		Set<String> reserved = new HashSet<>(wishIds(ctx.record, zone));
		String quota = PlantStock.reserved(ctx.record);
		if (quota != null) {
			reserved.add(quota);
		}
		List<String> preferred = preferences(ctx, zone);
		boolean any = anyAllowed(zone);
		ItemStack best = null;
		int bestRank = Integer.MAX_VALUE;
		for (ItemStack stack : ctx.record.carried) {
			if (!Planting.plain(stack) || !plantable(stack.getItem())) {
				continue;
			}
			String id = ItemUtil.id(stack.getItem());
			if (reserved.contains(id)) {
				continue;
			}
			int rank = preferred.indexOf(id);
			if (rank < 0) {
				if (!any) {
					continue;
				}
				rank = preferred.size();
			}
			if (best == null || rank < bestRank || (rank == bestRank && stack.getCount() > best.getCount())) {
				best = stack;
				bestRank = rank;
			}
		}
		Item extra = extraItem(ctx);
		ItemStack extraStack = extra != null && ctx.config.work.carryStacks >= 5 && !reserved.contains(ItemUtil.id(extra))
				? PlantStock.bagStack(ctx, extra) : null;
		ItemStack keptPlant = best;
		if (keptPlant == null && extraStack == null) {
			return stack -> false;
		}
		return stack -> stack == keptPlant || stack == extraStack;
	}

	/** Wanted item ids of the villager and its zone. */
	static List<String> wishIds(VillagerRecord record, @Nullable WorkZone zone) {
		return WishMatch.merged(record.wishlist, zone == null ? List.of() : zone.wishlist);
	}

	// ------------------------------------------------------------------ round

	/** One step of the round. {@code home}: where the task started (automatic area of a guild without territory). */
	final Result tick(WorkContext ctx, @Nullable WorkZone zone, BlockPos home) {
		this.zone = zone;
		if (!enabled(ctx, zone)) {
			finish(ctx, Stop.DISABLED, 0);
			return Result.DONE;
		}
		Settings settings = settings(ctx.config);
		if (!active) {
			start(ctx, zone, home);
		}
		onStep(ctx);
		if (fetchChest == null && (item == null || PlantStock.countInBag(ctx, item) < minCount(item))) {
			if (!choose(ctx, zone, settings)) {
				finish(ctx, Stop.NO_SOURCE, settings.pauseSeconds);
				return Result.DONE;
			}
		}
		Item current = item;
		if (current == null) {
			finish(ctx, Stop.NO_SOURCE, settings.pauseSeconds);
			return Result.DONE;
		}
		if (pending.isEmpty()) {
			WorkArea.Scan running = scan;
			if (running == null || running.done()) {
				return endScan(ctx, running, settings);
			}
			int room = Math.max(1, settings.roundSize - done);
			for (int i = 0; i < CHUNKS_PER_STEP && pending.size() < room; i++) {
				LevelChunk chunk = running.next(ctx);
				if (chunk == null) {
					break;
				}
				collect(ctx, running.area, chunk, current, pending, room - pending.size());
			}
			if (pending.isEmpty()) {
				if (running.done()) {
					return endScan(ctx, running, settings);
				}
				searching(ctx, current, running.position(), running.total());
				return Result.WORKING;
			}
		}
		if (fetchChest != null) {
			return fetchStep(ctx, settings);
		}
		if (!extraChecked) {
			extraChecked = true;
			Item extra = extraItem(ctx);
			if (extra != null && settings.fromStorage && PlantStock.bagStack(ctx, extra) == null) {
				List<PlantStock.ChestView> chests = PlantStock.chests(ctx, candidate -> candidate == extra, failedChests);
				if (!chests.isEmpty()) {
					fetchChest = chests.getFirst().pos();
					fetchItem = extra;
					return fetchStep(ctx, settings);
				}
			}
		}
		return plantStep(ctx, settings, current);
	}

	private void start(WorkContext ctx, @Nullable WorkZone zone, BlockPos home) {
		clearRound();
		active = true;
		BlockPos villager = ctx.villager.blockPosition();
		WorkArea next = zone != null ? WorkArea.of(zone) : WorkArea.anchored(ctx.guild, villager, home, ctx.config, 0);
		area = next;
		scan = new WorkArea.Scan(next, villager);
	}

	private Result endScan(WorkContext ctx, WorkArea.@Nullable Scan running, Settings settings) {
		if (done > 0) {
			finish(ctx, Stop.ROUND_DONE, 0);
		} else if (running != null && running.readChunks() == 0) {
			finish(ctx, Stop.UNLOADED, 0);
		} else {
			finish(ctx, Stop.NO_SPOT, settings.pauseSeconds);
		}
		return Result.DONE;
	}

	/** Picks the item to plant (bag first for each preferred item, then the storage). @return false when none */
	private boolean choose(WorkContext ctx, @Nullable WorkZone zone, Settings settings) {
		Map<String, Integer> bag = PlantStock.bag(ctx, this::plantable);
		List<PlantStock.ChestView> chests = settings.fromStorage ? PlantStock.chests(ctx, this::plantable, failedChests) : List.of();
		List<Map<String, Integer>> contents = new ArrayList<>();
		for (PlantStock.ChestView chest : chests) {
			contents.add(chest.items());
		}
		PlantingRules.Choice choice = PlantingRules.choose(preferences(ctx, zone), anyAllowed(zone), bag, contents, id -> {
			Item candidate = ItemUtil.item(id);
			return candidate != null && plantable(candidate);
		}, id -> {
			Item candidate = ItemUtil.item(id);
			return candidate == null ? 1 : minCount(candidate);
		});
		Item chosen = choice == null ? null : ItemUtil.item(choice.item());
		if (chosen == null) {
			item = null;
			return false;
		}
		Item previous = item;
		if (previous != null && minCount(previous) != minCount(chosen)) {
			// Spots of single saplings do not fit 2×2 squares (and back): look again.
			pending.clear();
			target = null;
			WorkArea current = area;
			if (current != null) {
				scan = new WorkArea.Scan(current, ctx.villager.blockPosition());
			}
		}
		item = chosen;
		if (choice.inBag()) {
			fetchChest = null;
			fetchItem = null;
		} else {
			fetchChest = chests.get(choice.chest()).pos();
			fetchItem = chosen;
		}
		return true;
	}

	private Result fetchStep(WorkContext ctx, Settings settings) {
		BlockPos pos = fetchChest;
		Item wanted = fetchItem;
		if (pos == null || wanted == null) {
			fetchChest = null;
			fetchItem = null;
			return Result.WORKING;
		}
		boolean main = wanted == item;
		switch (Movement.moveTo(ctx, pos, REACH, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				failedChests.add(pos);
				clearFetch(main);
				return Result.WORKING;
			}
			case MOVING -> {
				fetching(ctx, wanted);
				return Result.WORKING;
			}
			default -> {
			}
		}
		int moved = PlantStock.fetch(ctx, pos, wanted, main ? settings.fetchAmount : EXTRA_FETCH, main && minCount(wanted) >= 4);
		fetchChest = null;
		fetchItem = null;
		if (moved <= 0) {
			failedChests.add(pos);
			if (main) {
				item = null;
			}
		} else {
			Movement.hold(ctx, pos);
			WorldUtil.swing(ctx.villager);
			WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 1.0f);
			fetching(ctx, wanted);
		}
		return Result.WORKING;
	}

	private void clearFetch(boolean main) {
		fetchChest = null;
		fetchItem = null;
		if (main) {
			item = null;
		}
	}

	private Result plantStep(WorkContext ctx, Settings settings, Item current) {
		Spot spot = target;
		if (spot == null || !pending.contains(spot)) {
			spot = nearest(ctx);
			target = spot;
		}
		if (spot == null) {
			return Result.WORKING;
		}
		switch (Movement.moveTo(ctx, spot.pos(), REACH, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				pending.remove(spot);
				target = null;
				return Result.WORKING;
			}
			case MOVING -> {
				going(ctx, spot, current, done, settings.roundSize);
				return Result.WORKING;
			}
			default -> {
			}
		}
		int budget = (int) Math.max(1, Math.round(settings.perStep * Upgrades.workRate(ctx.guild)));
		int planted = 0;
		target = null;
		pending.remove(spot);
		if (plant(ctx, spot, current)) {
			planted++;
			done++;
		}
		Iterator<Spot> it = pending.iterator();
		while (it.hasNext() && planted < budget && done < settings.roundSize) {
			Spot other = it.next();
			BlockPos pos = other.pos();
			if (ctx.villager.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > WORK_REACH_SQ) {
				continue;
			}
			if (PlantStock.countInBag(ctx, current) < minCount(current)) {
				break;
			}
			it.remove();
			if (plant(ctx, other, current)) {
				planted++;
				done++;
			}
		}
		if (planted > 0) {
			Movement.hold(ctx, spot.pos());
			WorldUtil.swing(ctx.villager);
			working(ctx, spot, current, done, settings.roundSize);
		}
		if (done >= settings.roundSize) {
			finish(ctx, Stop.ROUND_DONE, 0);
			return Result.DONE;
		}
		return Result.WORKING;
	}

	/** Next spot: plain spots before the ones to till, nearest first. */
	private @Nullable Spot nearest(WorkContext ctx) {
		Spot best = null;
		double bestDist = Double.MAX_VALUE;
		for (Spot spot : pending) {
			double dist = ctx.distSq(spot.pos());
			if (best == null || (best.till() && !spot.till()) || (best.till() == spot.till() && dist < bestDist)) {
				best = spot;
				bestDist = dist;
			}
		}
		return best;
	}

	/** A pending spot covers this column (its 2×2 square included). */
	final boolean isPending(List<Spot> spots, int x, int z) {
		for (Spot spot : spots) {
			int dx = x - spot.pos().getX();
			int dz = z - spot.pos().getZ();
			if (dx >= 0 && dz >= 0 && dx < spot.size() && dz < spot.size()) {
				return true;
			}
		}
		return false;
	}

	private void finish(WorkContext ctx, Stop stop, int pauseSeconds) {
		clearRound();
		nextRoundAt = ctx.now + switch (stop) {
			case ROUND_DONE, DISABLED -> 0L;
			case UNLOADED -> UNLOADED_PAUSE_TICKS;
			case NO_SOURCE, NO_SPOT -> WorkContext.ticks(pauseSeconds);
		};
		note = switch (stop) {
			case NO_SOURCE -> noSourceNote();
			case NO_SPOT -> noSpotNote();
			default -> null;
		};
	}

	private void clearRound() {
		active = false;
		done = 0;
		area = null;
		scan = null;
		pending.clear();
		target = null;
		item = null;
		fetchChest = null;
		fetchItem = null;
		extraChecked = false;
		failedChests.clear();
	}
}
