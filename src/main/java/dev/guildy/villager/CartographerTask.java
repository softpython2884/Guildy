package dev.guildy.villager;

import dev.guildy.ruin.Cartography;
import dev.guildy.ruin.GuildRuinData;
import dev.guildy.ruin.RuinRules;
import dev.guildy.structure.BuiltStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;

/**
 * CARTOGRAPHER: walks to the "Atelier du cartographe" and studies maps in front of its cartography table while the guild
 * has a research running (one second of work per second, see {@link Cartography#work}). Paused at night like the other
 * workers (TaskEngine / NightShelter). Without an atelier or a research it waits near the atelier.
 */
final class CartographerTask extends TaskHandler {
	private static final long RETRY_TICKS = 10 * 20L;
	private static final double IDLE_DISTANCE = 8.0;

	private final DepositFlow deposit = new DepositFlow();
	private long retryAt;
	private int workSteps;

	@Override
	void tick(WorkContext ctx) {
		if (!ctx.record.carried.isEmpty()) {
			// Items of a former job: back to a deposit chest first.
			DepositFlow.State state = deposit.tick(ctx);
			if (state == DepositFlow.State.NO_CHEST || state == DepositFlow.State.ALL_FULL) {
				ctx.status(DepositFlow.blockedStatus(state));
			}
			return;
		}
		BuiltStructure atelier = Cartography.atelier(ctx.guild);
		if (atelier == null) {
			ctx.status("villager.status.cartographer.no_atelier");
			return;
		}
		if (ctx.now < retryAt) {
			return;
		}
		BlockPos spot = Cartography.workSpot(ctx.level, atelier);
		GuildRuinData.Research research = Cartography.research(ctx.guild);
		boolean running = research != null && research.status == GuildRuinData.Research.Status.RUNNING;
		if (!running && ctx.distSq(spot) <= IDLE_DISTANCE * IDLE_DISTANCE) {
			ctx.status("villager.status.cartographer.waiting");
			return;
		}
		switch (Movement.moveTo(ctx, spot, 1.6, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				ctx.status("villager.status.cartographer.walking");
				retryAt = ctx.now + RETRY_TICKS;
			}
			case MOVING -> ctx.status("villager.status.cartographer.walking");
			case ARRIVED -> {
				if (!running) {
					ctx.status("villager.status.cartographer.waiting");
					return;
				}
				work(ctx, atelier, research);
			}
		}
	}

	private void work(WorkContext ctx, BuiltStructure atelier, GuildRuinData.Research research) {
		BlockPos table = Cartography.table(ctx.level, atelier);
		Movement.hold(ctx, table);
		WorldUtil.swing(ctx.villager);
		ctx.level.sendParticles(ParticleTypes.ENCHANT, table.getX() + 0.5, table.getY() + 1.3, table.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.4);
		if (workSteps++ % 4 == 0) {
			WorldUtil.playSound(ctx.level, ctx.villager, workSteps % 8 == 1 ? SoundEvents.VILLAGER_WORK_CARTOGRAPHER : SoundEvents.BOOK_PAGE_TURN, 0.8f, 1.0f);
		}
		if (Cartography.work(ctx.level, ctx.guild, ctx.record.uuid)) {
			ctx.status("villager.status.cartographer.working", RuinRules.percent(research.progress, research.required));
			if (research.status != GuildRuinData.Research.Status.RUNNING) {
				WorldUtil.happy(ctx.level, ctx.villager);
			}
		}
	}

	@Override
	void stop(WorkContext ctx) {
		deposit.reset();
	}
}
