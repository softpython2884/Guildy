package dev.guildy.villager;

import net.minecraft.core.BlockPos;

/**
 * EXPLORER / NETHER: empty the bag, walk to the departure point (reception or heart; a nether portal of the territory
 * for the Nether), then leave — the villager is away (see {@link Expeditions}) and comes back later with loot. Back
 * home, the loot is deposited, then the mission goal decides: leave again (continuous), or end the mission.
 */
final class ExpeditionTask extends TaskHandler {
	private static final long RETRY_TICKS = 10 * 20L;

	private final DepositFlow deposit = new DepositFlow();
	private long retryAt;

	@Override
	void tick(WorkContext ctx) {
		if (!ctx.record.carried.isEmpty()) {
			DepositFlow.State state = deposit.tick(ctx);
			switch (state) {
				case WORKING -> {
				}
				case DONE -> Missions.complete(ctx);
				case NO_CHEST, ALL_FULL -> ctx.status(DepositFlow.blockedStatus(state));
			}
			return;
		}
		if (Missions.complete(ctx) || ctx.now < retryAt) {
			return;
		}
		Task type = ctx.record.task;
		BlockPos departure = Expeditions.departurePoint(ctx.level, ctx.guild, type, ctx.villager.blockPosition());
		if (departure == null) {
			ctx.status(type == Task.NETHER ? "villager.status.no_portal" : "villager.status.no_departure");
			retryAt = ctx.now + RETRY_TICKS;
			return;
		}
		// Stop next to a portal, never inside (vanilla would send the villager to the real Nether).
		double reach = type == Task.NETHER ? 2.5 : 2.0;
		switch (Movement.moveTo(ctx, departure, reach, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				ctx.status("villager.status.departure_unloaded");
				retryAt = ctx.now + RETRY_TICKS;
			}
			case MOVING -> ctx.status("villager.status.going_departure");
			case ARRIVED -> Expeditions.depart(ctx, type, departure);
		}
	}

	@Override
	void stop(WorkContext ctx) {
		deposit.reset();
	}
}
