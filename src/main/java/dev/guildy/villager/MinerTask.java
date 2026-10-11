package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Miner. Its mine: the MINE zone it is assigned to; without zone the nearest mine of the guild where a player is around,
 * else the nearest exposed natural stone, else the heart.
 * <ul>
 * <li>Mine in "real" mode: real excavation, see {@link RealMiner} (staircase and galleries really dug).</li>
 * <li>Mine in "simulated" mode and automatic stone spots (design decision 13): the villager "mines" at the spot for the
 * trip duration (swing + block-break particles/sounds on a nearby block), gets loot from the tables of the objective at
 * the spot's depth, then brings it back to a deposit chest. No block is ever broken.</li>
 * </ul>
 */
final class MinerTask extends TaskHandler {
	private enum Phase {
		GO_WORK,
		MINING,
		DEPOSIT,
		BLOCKED
	}

	/** Where and how the villager mines; {@code key} changes when the site does. {@code auto}: picked by itself. */
	private record Site(BlockPos pos, int depth, ZoneObjective objective, @Nullable WorkZone zone, String key, boolean auto) {
	}

	private static final int STONE_CHUNKS_PER_STEP = 4;
	/** An automatic stone spot is kept that long before looking for a closer one. */
	private static final long AUTO_SPOT_TICKS = 5 * 60 * 20L;

	private Phase phase = Phase.GO_WORK;
	private int elapsed;
	private long nextEffect;
	private long retryAt;
	private @Nullable Site site;
	/** Automatic mode: pick the site again (after each trip only, so a trip is never interrupted by a closer mine). */
	private boolean reevaluate = true;
	private WorkArea.@Nullable Scan stoneScan;
	private @Nullable BlockPos stoneFound;
	private @Nullable Site autoSite;
	private long autoSiteUntil;
	private final DepositFlow deposit = new DepositFlow();
	/** Real excavation of the current site's zone (real mines only). */
	private @Nullable RealMiner real;

	@Override
	void tick(WorkContext ctx) {
		if (phase == Phase.DEPOSIT) {
			depositStep(ctx);
			return;
		}
		if (phase == Phase.BLOCKED) {
			if (ctx.now >= retryAt) {
				phase = Phase.DEPOSIT;
				depositStep(ctx);
			}
			return;
		}
		Site wanted = resolveSite(ctx);
		if (wanted == null) {
			return;
		}
		if (site == null || !site.key().equals(wanted.key())) {
			elapsed = 0;
			phase = Phase.GO_WORK;
			stopReal(ctx);
		}
		site = wanted;
		WorkZone zone = wanted.zone();
		if (zone != null && zone.isRealMine()) {
			if (real == null || real.zone != zone) {
				stopReal(ctx);
				real = new RealMiner(zone);
			}
			if (real.tick(ctx)) {
				// Trip over (bag emptied): an automatic miner may pick a closer mine for the next one.
				reevaluate = true;
			}
			return;
		}
		stopReal(ctx);
		switch (phase) {
			case GO_WORK -> goWork(ctx, wanted);
			case MINING -> mine(ctx, wanted);
			default -> {
			}
		}
	}

	@Override
	void stop(WorkContext ctx) {
		stopReal(ctx);
	}

	private void stopReal(WorkContext ctx) {
		if (real != null) {
			real.stop(ctx);
			real = null;
		}
	}

	// ------------------------------------------------------------------ site

	/** The mining site, or null (status set) while there is none yet. */
	private @Nullable Site resolveSite(WorkContext ctx) {
		WorkZone zone = Zones.assignedZone(ctx.guild, ctx.record);
		if (zone != null) {
			if (!ctx.ticking(zone.pos)) {
				waitOrDeposit(ctx, "villager.status.zone_unloaded", zone.name);
				return null;
			}
			return zoneSite(zone, false);
		}
		// Automatic mode: the chosen site is kept until the trip is over (unless it vanished).
		Site current = site;
		if (!reevaluate && current != null && current.auto() && stillValid(ctx, current)) {
			return current;
		}
		WorkZone nearest = Zones.nearestActive(ctx.level, ctx.guild, ZoneType.MINE, ctx.villager.blockPosition());
		if (nearest != null) {
			reevaluate = false;
			return zoneSite(nearest, true);
		}
		if (autoSite != null && ctx.now < autoSiteUntil) {
			reevaluate = false;
			return autoSite;
		}
		Site found = searchStone(ctx);
		if (found != null) {
			reevaluate = false;
		}
		return found;
	}

	private static boolean stillValid(WorkContext ctx, Site current) {
		WorkZone zone = current.zone();
		return zone == null || (ctx.guild.villagers.zones.contains(zone) && ctx.ticking(zone.pos));
	}

	private static Site zoneSite(WorkZone zone, boolean auto) {
		return new Site(zone.pos, zone.pos.getY(), zone.objective, zone, zone.settingsKey(), auto);
	}

	/** Incremental search of the nearest exposed natural stone around the villager (else the heart). */
	private @Nullable Site searchStone(WorkContext ctx) {
		if (stoneScan == null) {
			BlockPos from = ctx.villager.blockPosition();
			stoneScan = new WorkArea.Scan(WorkArea.auto(ctx.guild, from, ctx.config), from);
			stoneFound = null;
		}
		WorkArea.Scan scan = stoneScan;
		for (int i = 0; i < STONE_CHUNKS_PER_STEP && stoneFound == null; i++) {
			LevelChunk chunk = scan.next(ctx);
			if (chunk == null) {
				break;
			}
			stoneFound = exposedStone(ctx, scan.area, chunk);
		}
		if (stoneFound == null && !scan.done()) {
			waitOrDeposit(ctx, "villager.status.miner.searching");
			return null;
		}
		stoneScan = null;
		BlockPos stone = stoneFound;
		Site found;
		if (stone != null) {
			found = new Site(stone.above(), stone.getY(), ZoneObjective.MIXED, null, "auto:" + stone.asLong(), true);
		} else if (ctx.guild.heart != null) {
			found = new Site(ctx.guild.heart, ctx.guild.heart.getY(), ZoneObjective.MIXED, null, "auto:heart", true);
		} else {
			waitOrDeposit(ctx, "villager.status.no_territory");
			return null;
		}
		autoSite = found;
		autoSiteUntil = ctx.now + AUTO_SPOT_TICKS;
		return found;
	}

	/** Nearest column of the chunk (inside the area) whose top block is natural stone open to the sky. */
	private static @Nullable BlockPos exposedStone(WorkContext ctx, WorkArea area, LevelChunk chunk) {
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				if (!area.contains(x, z)) {
					continue;
				}
				int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (!chunk.getBlockState(pos.set(x, top, z)).is(BlockTags.BASE_STONE_OVERWORLD)) {
					continue;
				}
				double dist = ctx.distSq(pos);
				if (dist < bestDist) {
					bestDist = dist;
					best = pos.immutable();
				}
			}
		}
		return best;
	}

	private void waitOrDeposit(WorkContext ctx, String status, Object... args) {
		if (!ctx.record.carried.isEmpty()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
		} else {
			ctx.status(status, args);
		}
	}

	// ------------------------------------------------------------------ work

	private void goWork(WorkContext ctx, Site current) {
		if (ctx.mustReturn()) {
			phase = Phase.DEPOSIT;
			depositStep(ctx);
			return;
		}
		switch (Movement.moveTo(ctx, current.pos(), 2.0, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				if (current.auto()) {
					reevaluate = true;
				}
				if (current.zone() != null) {
					ctx.status("villager.status.zone_unloaded", current.zone().name);
				} else {
					autoSite = null;
					ctx.status("villager.status.miner.searching");
				}
			}
			case MOVING -> {
				if (current.zone() != null) {
					ctx.status("villager.status.miner.going", current.zone().name);
				} else {
					ctx.status("villager.status.miner.going_auto", current.pos().getX(), current.pos().getY(), current.pos().getZ());
				}
			}
			case ARRIVED -> {
				phase = Phase.MINING;
				mine(ctx, current);
			}
		}
	}

	private void mine(WorkContext ctx, Site current) {
		if (!Movement.near(ctx, current.pos(), 4.0, 3.0)) {
			// Wandered away (pushed, vanilla behaviour...): go back, the trip progress is kept.
			phase = Phase.GO_WORK;
			goWork(ctx, current);
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
			if (current.zone() != null) {
				ctx.status("villager.status.miner.mining_zone", current.zone().name, trip - elapsed);
			} else {
				ctx.status("villager.status.miner.mining_auto", current.pos().getX(), current.pos().getY(), current.pos().getZ(), trip - elapsed);
			}
			return;
		}
		elapsed = 0;
		// Trip over: an automatic miner may pick a closer / newly available mine for the next one.
		reevaluate = true;
		VillagerConfig.DepthTable table = DepthLoot.tableFor(ctx.config.miner.tables(current.objective()), current.depth());
		int produced = 0;
		for (DepthLoot.Drop drop : DepthLoot.roll(table, ctx.config.miner.rolls(current.objective()), ThreadLocalRandom.current())) {
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
