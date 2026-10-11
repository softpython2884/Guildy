package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared "bring the carried items to a deposit chest" sub-state machine (miner, lumberjack, farmer).
 */
final class DepositFlow {
	enum State {
		/** Walking / inserting, call again next second. */
		WORKING,
		/** Carried list is empty. */
		DONE,
		/** The guild has no deposit chest. */
		NO_CHEST,
		/** Deposit chests exist but none is usable or has room. */
		ALL_FULL
	}

	private static final long FULL_RETRY_TICKS = 30 * 20L;

	private @Nullable BlockPos target;
	private final Set<BlockPos> full = new HashSet<>();
	private long fullResetAt;

	State tick(WorkContext ctx) {
		if (ctx.record.carried.isEmpty()) {
			target = null;
			return State.DONE;
		}
		if (ctx.now >= fullResetAt) {
			full.clear();
			fullResetAt = ctx.now + FULL_RETRY_TICKS;
		}
		if (target == null || !isDeposit(ctx, target)) {
			StorageChest chest = Storage.nearestDeposit(ctx, full);
			if (chest == null) {
				target = null;
				return Storage.hasDeposit(ctx.guild) ? State.ALL_FULL : State.NO_CHEST;
			}
			target = chest.pos;
		}
		switch (Movement.moveTo(ctx, target, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				full.add(target);
				target = null;
				return State.WORKING;
			}
			case MOVING -> {
				ctx.status("villager.status.going_deposit");
				return State.WORKING;
			}
			default -> {
			}
		}
		Container container = Storage.container(ctx.level, target);
		int moved = container == null ? 0 : Storage.insertCarried(ctx, container, stack -> true);
		if (moved > 0) {
			Movement.hold(ctx, target);
			WorldUtil.swing(ctx.villager);
			WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.BUNDLE_INSERT, 0.8f, 1.0f);
			ctx.status("villager.status.deposited", moved);
		}
		if (!ctx.record.carried.isEmpty()) {
			full.add(target);
		}
		target = null;
		return ctx.record.carried.isEmpty() ? State.DONE : State.WORKING;
	}

	void reset() {
		target = null;
	}

	private static boolean isDeposit(WorkContext ctx, BlockPos pos) {
		StorageChest chest = ctx.guild.villagers.chestAt(pos);
		return chest != null && chest.isDeposit();
	}

	/** Status key for a blocked deposit. */
	static String blockedStatus(State state) {
		return state == State.NO_CHEST ? "villager.status.no_deposit" : "villager.status.deposit_full";
	}
}
