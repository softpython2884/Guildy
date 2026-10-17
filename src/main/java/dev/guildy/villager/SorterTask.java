package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * "Ranger la base": empties DEPOSIT chests into the matching CATEGORY chests (nearest chest of the category with room),
 * leftovers into a fallback ("Divers") chest, else back into a deposit chest. Only items that have a destination
 * are taken, so nothing loops forever.
 * <p>
 * With nothing to deliver, the sorter tidies the chest groups ({@link Tidier}): misplaced items out, sections within
 * reach reorganised in one atomic step (lids opened, arm swing, sounds), stacks carried between distant sections. It
 * also keeps the chest rooms planned ({@link ChestRooms#maybePlan}). The deposits are still checked every
 * {@code intervalSeconds}.
 */
final class SorterTask extends TaskHandler {
	private enum Phase {
		WAIT,
		PICKUP,
		DELIVER,
		TIDY
	}

	private static final long FULL_RETRY_TICKS = 30 * 20L;
	/** Retry delay when a chest to tidy is open by a player. */
	private static final long BUSY_RETRY_TICKS = 3 * 20L;
	/** Walk reach to a standing spot / chest. */
	private static final double REACH = 2.5;

	private Phase phase = Phase.WAIT;
	private long nextCheck;
	private long nextTidy;
	private @Nullable BlockPos pickup;
	private @Nullable BlockPos deliverTo;
	/** Tidying job being walked to / done. */
	private Tidier.@Nullable Job job;
	/** ARRANGE: lids already opened (the rearrangement happens at the next step). */
	private boolean opened;
	/** CARRY: destination section of the carried items. */
	private Tidier.@Nullable Job delivery;
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
			case TIDY -> tidyStep(ctx);
		}
	}

	@Override
	void stop(WorkContext ctx) {
		Tidier.release(ctx.villager.getUUID());
	}

	private void waitStep(WorkContext ctx) {
		if (!ctx.record.carried.isEmpty()) {
			phase = Phase.DELIVER;
			deliverStep(ctx);
			return;
		}
		if (ctx.now >= nextCheck) {
			nextCheck = ctx.now + ctx.workTicks(ctx.config.sorter.intervalSeconds);
			StorageChest source = Storage.findSortSource(ctx, full);
			if (source != null) {
				pickup = source.pos;
				phase = Phase.PICKUP;
				pickupStep(ctx);
				return;
			}
		}
		ChestRooms.maybePlan(ctx.level, ctx.guild, ctx.now);
		if (!ctx.config.sorter.tidy) {
			ctx.status(Storage.hasDeposit(ctx.guild) ? "villager.status.sorter.waiting" : "villager.status.no_deposit");
			return;
		}
		if (ctx.now < nextTidy) {
			return;
		}
		Tidier.Next next = Tidier.next(ctx);
		if (next.job() != null) {
			job = next.job();
			opened = false;
			phase = Phase.TIDY;
			tidyStep(ctx);
			return;
		}
		if (next.busyCategory() != null) {
			nextTidy = ctx.now + BUSY_RETRY_TICKS;
			ctx.status("villager.status.sorter.busy", next.busyCategory());
		} else if (next.allTidy()) {
			nextTidy = ctx.now + ctx.config.sorter.tidyIntervalSeconds * 20L;
			ctx.status(Storage.hasDeposit(ctx.guild) || !ctx.guild.villagers.chests.isEmpty() ? "villager.status.sorter.all_tidy"
					: "villager.status.no_deposit");
		} else {
			ctx.status("villager.status.sorter.checking");
		}
	}

	private void pickupStep(WorkContext ctx) {
		StorageChest chest = pickup == null ? null : ctx.guild.villagers.chestAt(pickup);
		if (chest == null || !Storage.isSortSource(chest)) {
			phase = Phase.WAIT;
			return;
		}
		Movement.Result result = Movement.moveTo(ctx, pickup, REACH, ctx.config.sorter.walkTimeoutSeconds);
		if (result == Movement.Result.UNREACHABLE) {
			full.add(pickup);
			phase = Phase.WAIT;
			return;
		}
		if (result == Movement.Result.MOVING) {
			ctx.status("villager.status.sorter.going_pickup");
			return;
		}
		if (Storage.inUse(ctx.level, pickup)) {
			// A player looks into it: another deposit, or this one later.
			full.add(pickup);
			pickup = null;
			phase = Phase.WAIT;
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
			delivery = null;
			nextCheck = Math.min(nextCheck, ctx.now + 20);
			nextTidy = ctx.now;
			Tidier.release(ctx.villager.getUUID());
			return;
		}
		if (delivery != null) {
			// Carried stacks of a group: to the section that misses them first.
			Tidier.Job carry = delivery;
			Tidier.lease(ctx.guild, carry.groupKey(), ctx.villager.getUUID(), ctx.now, ctx.config.sorter.leaseSeconds);
			BlockPos target = carry.destination().getFirst();
			Movement.Result result = Movement.moveTo(ctx, target, REACH, ctx.config.sorter.walkTimeoutSeconds);
			if (result == Movement.Result.MOVING) {
				ctx.status("villager.status.sorter.tidy_chest", carry.category(), carry.index(), carry.total());
				return;
			}
			delivery = null;
			if (result == Movement.Result.ARRIVED) {
				int put = Tidier.deliver(ctx, carry);
				if (put > 0) {
					Movement.hold(ctx, target);
					WorldUtil.swing(ctx.villager);
					WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.BUNDLE_INSERT, 0.8f, 1.0f);
					ctx.record.stats.tidied += put;
				}
			}
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
				nextCheck = ctx.now + ctx.workTicks(ctx.config.sorter.intervalSeconds);
				return;
			}
		}
		BlockPos target = deliverTo;
		Movement.Result result = Movement.moveTo(ctx, target, REACH, ctx.config.sorter.walkTimeoutSeconds);
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
		if (container != null && here != null && !Storage.inUse(ctx.level, target)) {
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
			Tidier.touched(ctx.guild, here);
		} else {
			full.add(target);
		}
		deliverTo = null;
	}

	// ================================================================ tidying

	private void tidyStep(WorkContext ctx) {
		Tidier.Job current = job;
		if (current == null) {
			phase = Phase.WAIT;
			return;
		}
		if (!Tidier.lease(ctx.guild, current.groupKey(), ctx.villager.getUUID(), ctx.now, ctx.config.sorter.leaseSeconds)) {
			finishJob(ctx, false);
			return;
		}
		Movement.Result result = Movement.moveTo(ctx, current.walkTo(), REACH, ctx.config.sorter.walkTimeoutSeconds);
		if (result == Movement.Result.UNREACHABLE) {
			finishJob(ctx, false);
			return;
		}
		if (result == Movement.Result.MOVING) {
			ctx.status(current.kind() == Tidier.Kind.TAKE_OUT ? "villager.status.sorter.going_take_out" : "villager.status.sorter.going_tidy",
					current.category());
			return;
		}
		BlockPos lookAt = current.chests().getFirst();
		Movement.hold(ctx, lookAt);
		if (Tidier.anyInUse(ctx.level, current.chests())) {
			ctx.status("villager.status.sorter.busy", current.category());
			finishJob(ctx, true);
			nextTidy = ctx.now + BUSY_RETRY_TICKS;
			return;
		}
		switch (current.kind()) {
			case TAKE_OUT -> {
				int taken = Tidier.takeOut(ctx, current, ctx.config.sorter.stacksPerTrip);
				ctx.status("villager.status.sorter.taking_out", current.category(), current.index(), current.total());
				if (taken > 0) {
					WorldUtil.swing(ctx.villager);
					WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.ITEM_PICKUP, 0.6f, 0.9f);
					if (ctx.config.sorter.animation) {
						StorageAnimation.openThenClose(ctx.level, current.chests(), 20);
					}
				}
				finishJob(ctx, true);
			}
			case CARRY -> {
				int taken = Tidier.carryPickup(ctx, current, ctx.config.sorter.stacksPerTrip);
				ctx.status("villager.status.sorter.tidy_chest", current.category(), current.index(), current.total());
				if (taken > 0) {
					WorldUtil.swing(ctx.villager);
					WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.ITEM_PICKUP, 0.6f, 0.9f);
					if (ctx.config.sorter.animation) {
						StorageAnimation.openThenClose(ctx.level, current.chests(), 20);
					}
					delivery = current;
					job = null;
					phase = Phase.DELIVER;
					return;
				}
				finishJob(ctx, true);
			}
			case ARRANGE -> arrangeStep(ctx, current);
		}
	}

	/** Lids open first (animation), the section is reorganised one step later, in one go. */
	private void arrangeStep(WorkContext ctx, Tidier.Job current) {
		String status = current.chests().size() == 1 ? "villager.status.sorter.tidy_chest"
				: StorageGroups.mode(ctx.guild) == ChestOrder.Mode.COLUMNS ? "villager.status.sorter.arranging.columns"
				: "villager.status.sorter.arranging.rows";
		ctx.status(status, current.category(), current.index(), current.total());
		if (ctx.config.sorter.animation && !opened) {
			opened = true;
			StorageAnimation.openThenClose(ctx.level, current.chests(), 30);
			WorldUtil.swing(ctx.villager);
			return;
		}
		int moved = Tidier.arrange(ctx.level, ctx.guild, current);
		if (moved > 0) {
			WorldUtil.swing(ctx.villager);
			WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.BUNDLE_INSERT, 0.8f, 1.0f);
			ctx.record.stats.tidied += moved;
		}
		finishJob(ctx, true);
	}

	/** Job done (or abandoned): back to WAIT; {@code soon}: look for the next work at the next step. */
	private void finishJob(WorkContext ctx, boolean soon) {
		job = null;
		opened = false;
		phase = ctx.record.carried.isEmpty() ? Phase.WAIT : Phase.DELIVER;
		if (ctx.record.carried.isEmpty()) {
			Tidier.release(ctx.villager.getUUID());
		}
		if (soon) {
			nextTidy = Math.min(nextTidy, ctx.now);
		} else {
			nextTidy = ctx.now + BUSY_RETRY_TICKS;
		}
	}
}
