package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * "Ranger la base": empties DEPOSIT chests into the matching CATEGORY chests (first chest of the category with room),
 * leftovers into a fallback ("Divers") chest, else back into a deposit chest. Only items that have a destination
 * are taken, so nothing loops forever.
 */
final class SorterTask extends TaskHandler {
	private enum Phase {
		WAIT,
		PICKUP,
		DELIVER
	}

	private static final long FULL_RETRY_TICKS = 30 * 20L;

	private Phase phase = Phase.WAIT;
	private long nextCheck;
	private @Nullable BlockPos pickup;
	private @Nullable BlockPos deliverTo;
	/** Chests found full / unreachable during this period (retried every 30 s). */
	private final Set<BlockPos> full = new HashSet<>();
	private long fullResetAt;

	@Override
	void tick(WorkContext ctx) {
		if (ctx.now >= fullResetAt) {
			full.clear();
			fullResetAt = ctx.now + FULL_RETRY_TICKS;
		}
		switch (phase) {
			case WAIT -> waitStep(ctx);
			case PICKUP -> pickupStep(ctx);
			case DELIVER -> deliverStep(ctx);
		}
	}

	private void waitStep(WorkContext ctx) {
		if (ctx.now < nextCheck) {
			if (ctx.record.carried.isEmpty()) {
				ctx.status("villager.status.sorter.waiting");
			}
			return;
		}
		nextCheck = ctx.now + WorkContext.ticks(ctx.config.sorter.intervalSeconds);
		if (!ctx.record.carried.isEmpty()) {
			phase = Phase.DELIVER;
			deliverStep(ctx);
			return;
		}
		if (!Storage.hasDeposit(ctx.guild)) {
			ctx.status("villager.status.no_deposit");
			return;
		}
		StorageChest source = Storage.findSortSource(ctx, full);
		if (source == null) {
			ctx.status("villager.status.sorter.waiting");
			return;
		}
		pickup = source.pos;
		phase = Phase.PICKUP;
		pickupStep(ctx);
	}

	private void pickupStep(WorkContext ctx) {
		StorageChest chest = pickup == null ? null : ctx.guild.villagers.chestAt(pickup);
		if (chest == null || !chest.isDeposit()) {
			phase = Phase.WAIT;
			return;
		}
		Movement.Result result = Movement.moveTo(ctx, pickup, 2.5, ctx.config.sorter.walkTimeoutSeconds);
		if (result == Movement.Result.UNREACHABLE) {
			full.add(pickup);
			phase = Phase.WAIT;
			return;
		}
		if (result == Movement.Result.MOVING) {
			ctx.status("villager.status.sorter.going_pickup");
			return;
		}
		Container container = Storage.container(ctx.level, pickup);
		int taken = container == null ? 0 : Storage.takeMovable(ctx, container, ctx.config.sorter.stacksPerTrip, full);
		BlockPos source = pickup;
		pickup = null;
		if (taken <= 0) {
			phase = Phase.WAIT;
			return;
		}
		Movement.hold(ctx, source);
		WorldUtil.swing(ctx.villager);
		WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.ITEM_PICKUP, 0.6f, 0.9f);
		phase = Phase.DELIVER;
		deliverTo = null;
		ctx.status("villager.status.sorter.delivering", ctx.record.carried.size());
	}

	private void deliverStep(WorkContext ctx) {
		if (ctx.record.carried.isEmpty()) {
			phase = Phase.WAIT;
			nextCheck = ctx.now + 20;
			ctx.status("villager.status.sorter.waiting");
			return;
		}
		if (deliverTo == null || ctx.guild.villagers.chestAt(deliverTo) == null) {
			deliverTo = null;
			for (var stack : ctx.record.carried) {
				StorageChest chest = Storage.route(ctx, stack, full, true);
				if (chest != null) {
					deliverTo = chest.pos;
					break;
				}
			}
			if (deliverTo == null) {
				// Nowhere to put anything: keep the items and retry later.
				ctx.status("villager.status.sorter.no_destination");
				phase = Phase.WAIT;
				nextCheck = ctx.now + WorkContext.ticks(ctx.config.sorter.intervalSeconds);
				return;
			}
		}
		BlockPos target = deliverTo;
		Movement.Result result = Movement.moveTo(ctx, target, 2.5, ctx.config.sorter.walkTimeoutSeconds);
		if (result == Movement.Result.UNREACHABLE) {
			full.add(target);
			deliverTo = null;
			return;
		}
		if (result == Movement.Result.MOVING) {
			ctx.status("villager.status.sorter.delivering", ctx.record.carried.size());
			return;
		}
		Container container = Storage.container(ctx.level, target);
		StorageChest here = ctx.guild.villagers.chestAt(target);
		int moved = 0;
		if (container != null && here != null) {
			// Insert every stack whose best destination is this kind of chest (same category / fallback / deposit).
			moved = Storage.insertCarried(ctx, container, stack -> {
				StorageChest route = Storage.route(ctx, stack, full, true);
				return route != null && Storage.sameDestination(route, here);
			});
		}
		if (moved > 0) {
			Movement.hold(ctx, target);
			WorldUtil.swing(ctx.villager);
			WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.BUNDLE_INSERT, 0.8f, 1.0f);
			ctx.record.stats.sorted += moved;
		} else {
			full.add(target);
		}
		deliverTo = null;
	}
}
