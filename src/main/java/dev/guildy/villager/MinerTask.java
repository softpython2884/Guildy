package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated mining (design decision 13): the villager walks to its work position, "mines" there for the trip duration
 * (swing + block-break particles/sounds on a nearby block), gets loot from the depth table of workPos.y, then
 * brings it back to a deposit chest. No block is ever broken.
 */
final class MinerTask extends TaskHandler {
	private enum Phase {
		GO_WORK,
		MINING,
		DEPOSIT,
		BLOCKED
	}

	private Phase phase = Phase.GO_WORK;
	private int elapsed;
	private long nextEffect;
	private long retryAt;
	private final DepositFlow deposit = new DepositFlow();

	@Override
	void tick(WorkContext ctx) {
		if (!checkWorkPos(ctx)) {
			return;
		}
		switch (phase) {
			case GO_WORK -> goWork(ctx);
			case MINING -> mine(ctx);
			case DEPOSIT -> depositStep(ctx);
			case BLOCKED -> {
				if (ctx.now >= retryAt) {
					phase = Phase.DEPOSIT;
					depositStep(ctx);
				}
			}
		}
	}

	private void goWork(WorkContext ctx) {
		if (ctx.mustReturn()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		BlockPos zone = ctx.record.workPos;
		switch (Movement.moveTo(ctx, zone, 2.0, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> ctx.status("villager.status.workpos_unloaded");
			case MOVING -> ctx.status("villager.status.going_work");
			case ARRIVED -> {
				phase = Phase.MINING;
				mine(ctx);
			}
		}
	}

	private void mine(WorkContext ctx) {
		BlockPos zone = ctx.record.workPos;
		if (!Movement.near(ctx, zone, 4.0, 3.0)) {
			// Wandered away (pushed, vanilla behaviour...): go back, the trip progress is kept.
			phase = Phase.GO_WORK;
			goWork(ctx);
			return;
		}
		Movement.hold(ctx, null);
		elapsed++;
		int trip = Upgrades.workSeconds(ctx.guild, ctx.config.miner.tripSeconds);
		if (ctx.now >= nextEffect) {
			nextEffect = ctx.now + WorkContext.ticks(ctx.config.miner.effectIntervalSeconds);
			miningEffect(ctx);
		}
		if (elapsed < trip) {
			ctx.status("villager.status.miner.mining", trip - elapsed);
			return;
		}
		elapsed = 0;
		VillagerConfig.DepthTable table = DepthLoot.tableFor(ctx.config.miner.depthTables, zone.getY());
		int produced = 0;
		for (DepthLoot.Drop drop : DepthLoot.roll(table, ctx.config.miner.rollsPerTrip, ThreadLocalRandom.current())) {
			Item item = ItemUtil.item(drop.item());
			if (item == null) {
				continue;
			}
			int count = drop.count();
			while (count > 0) {
				ItemStack stack = new ItemStack(item, Math.min(count, new ItemStack(item).getMaxStackSize()));
				count -= stack.getCount();
				ItemStack leftover = ctx.addCarried(stack);
				produced += stack.getCount() - leftover.getCount();
			}
		}
		ctx.record.stats.mined += produced;
		GuildManager.markDirty();
		WorldUtil.happy(ctx.level, ctx.villager);
		phase = Phase.DEPOSIT;
		depositStep(ctx);
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
				phase = Phase.GO_WORK;
			}
			case NO_CHEST, ALL_FULL -> {
				if (ctx.mustReturn()) {
					phase = Phase.BLOCKED;
					retryAt = ctx.now + WorkContext.ticks(10);
					ctx.status(DepositFlow.blockedStatus(state));
				} else {
					// Keep the loot (up to capacity) and go on mining.
					phase = Phase.GO_WORK;
					ctx.status(DepositFlow.blockedStatus(state));
				}
			}
		}
	}

	/** Swing + break particles/sound on a solid block next to the villager. */
	private static void miningEffect(WorkContext ctx) {
		BlockPos feet = ctx.villager.blockPosition();
		List<BlockPos> candidates = new ArrayList<>();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			candidates.add(feet.relative(direction));
			candidates.add(feet.above().relative(direction));
		}
		candidates.add(feet.below());
		java.util.Collections.shuffle(candidates, ThreadLocalRandom.current());
		BlockPos chosen = null;
		BlockState chosenState = null;
		for (BlockPos pos : candidates) {
			BlockState state = ctx.level.getBlockState(pos);
			if (!state.isAir() && !state.getCollisionShape(ctx.level, pos).isEmpty()) {
				chosen = pos;
				chosenState = state;
				break;
			}
		}
		if (chosen == null) {
			chosen = feet.below();
			chosenState = ctx.level.getBlockState(chosen);
		}
		Movement.hold(ctx, chosen);
		WorldUtil.swing(ctx.villager);
		if (!chosenState.isAir()) {
			WorldUtil.breakEffect(ctx.level, chosen, chosenState);
		}
	}
}
