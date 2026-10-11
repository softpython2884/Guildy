package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * Real excavation by one miner in a REAL mine zone (zone side: {@link Mines}, geometry: {@link MineLayout}, rules:
 * {@link MineRules}). Each step (once per second):
 * <ul>
 * <li>a work segment shared with the other miners of the zone is taken (the stairs first, then the galleries in order;
 * leases so that two miners never dig the same blocks);</li>
 * <li>the miner walks to it through the mine's own tunnels: waypoints a few nodes ahead along the walking graph (the
 * vanilla navigation only ever plans short paths), a watchdog teleports it to its spot when it makes no progress;</li>
 * <li>cells are dug in order: their blocks top-down when the rules allow it (fluids next to a block are sealed with a
 * filler block of the bag first, else the gallery is given up), a settle step (falling sand / gravel is dug again),
 * the floor (holes filled), the ore veins seen from the cell, a wall torch every few blocks.</li>
 * </ul>
 * Real drops (iron pickaxe) go to the bag ("ores" objective: ores and a reserve of filler blocks only). A full bag, a
 * reached mission goal or the evening: back up through the tunnels to the deposit chest, then back to work.
 */
final class RealMiner {
	private static final int NO_JOB = -1;
	/** Waypoints this many nodes ahead along the route: the vanilla navigation only plans short paths. */
	private static final int LOOKAHEAD = 5;
	/** Cells checked per step without walking (already dug ones). */
	private static final int MAX_CELLS_PER_STEP = 16;
	/** Blocks dug again in one cell (falling sand / gravel) before giving up. */
	private static final int MAX_REDIGS = 24;
	private static final double MAX_BLOCKS_PER_STEP = 4.0;
	private static final int VEIN_REACH = 4;
	private static final double ON_ROUTE = 1.6;
	private static final double NEAR_ROUTE = 8.0;
	/** Close enough to the stand node to dig the next cell. */
	private static final double STAND_REACH = 1.5;
	/** Walking progress off the route counts the distance left above this (any node on the route is better). */
	private static final int OFF_ROUTE = 100_000;
	private static final long FULL_RETRY_TICKS = 10 * 20L;

	private enum Phase {
		WORK,
		DEPOSIT,
		BLOCKED
	}

	private enum CellStep {
		/** Cell finished: next one. */
		DONE,
		/** Work started or waiting for the budget / for falling blocks: same cell next step. */
		PENDING,
		/** Work needed: walk to the stand node first. */
		WALK,
		/** Not loaded: wait. */
		WAIT,
		/** Segment given up (or stairs blocked). */
		FAILED
	}

	final WorkZone zone;
	private Phase phase = Phase.WORK;
	/** Layout of the last plan: lets the miner walk out of the mine when it has nothing to dig any more. */
	private @Nullable MineLayout route;
	/** {@link MineLayout#STAIRS}, a gallery segment id, or {@link #NO_JOB}. */
	private int job = NO_JOB;
	private String jobKey = "";
	private int cursor;
	private int redigs;
	/** Ore blocks found around the current cell (null = not searched yet). */
	private @Nullable List<BlockPos> veins;
	private double budget;
	private int torchCredits;
	private long retryAt;
	private int walkDest = -1;
	private int walkBest;
	private long walkSince;
	private boolean tripEnded;
	/** Filler stack kept in the bag at the deposit chest (identity). */
	private @Nullable ItemStack reserve;
	private final DepositFlow deposit = new DepositFlow();

	RealMiner(WorkZone zone) {
		this.zone = zone;
	}

	/**
	 * One step.
	 *
	 * @return true when a trip ended (bag emptied at the deposit chest): an automatic miner may choose its mine again
	 */
	boolean tick(WorkContext ctx) {
		tripEnded = false;
		if (phase == Phase.DEPOSIT) {
			depositStep(ctx);
			return tripEnded;
		}
		if (phase == Phase.BLOCKED) {
			if (ctx.now >= retryAt) {
				startDeposit(ctx);
			}
			return tripEnded;
		}
		Mines.Plan plan = Mines.plan(ctx, zone);
		if (plan != null) {
			route = plan.layout();
		}
		boolean evening = evening(ctx);
		if (plan == null || ctx.mustReturn() || evening) {
			leave(ctx, plan, evening);
			return tripEnded;
		}
		work(ctx, plan);
		return tripEnded;
	}

	/** Task changed / villager gone: reservations released. */
	void stop(WorkContext ctx) {
		Mines.release(ctx.record.uuid);
		deposit.reset();
	}

	// ================================================================ leaving the mine

	/**
	 * Bag full, goal reached, evening or nothing to dig: up through the tunnels to the fanion, then to the deposit chest
	 * (or wait at the surface).
	 */
	private void leave(WorkContext ctx, Mines.@Nullable Plan plan, boolean evening) {
		if (job != NO_JOB) {
			// Keep the job (its dug cells are walkable) but let the other miners take it meanwhile.
			Mines.release(ctx.record.uuid);
		}
		if (plan == null && !zone.mine().exhausted && !ctx.mustReturn()) {
			// Terrain not ready yet (status set by the plan): wait where it is.
			return;
		}
		MineLayout layout = route;
		Villager villager = ctx.villager;
		if (layout != null && inMine(ctx, layout)) {
			if (!walkTo(ctx, layout, 0, walkable(ctx, layout, plan))) {
				int y = villager.getBlockY();
				if (ctx.mustReturn()) {
					ctx.status("villager.status.mine.going_up", ctx.record.carriedCount(), y);
				} else if (evening) {
					ctx.status("villager.status.mine.evening", y);
				} else {
					ctx.status("villager.status.mine.leaving", zone.name, y);
				}
				return;
			}
		}
		if ((ctx.mustReturn() || evening || plan == null) && worthDeposit(ctx)) {
			startDeposit(ctx);
			return;
		}
		if (evening && plan != null) {
			ctx.status("villager.status.mine.night_wait", zone.name);
		}
		// Nothing to dig: the status set by the plan stays (exhausted / blocked).
	}

	/** Something to bring to the chest beyond the filler reserve (everything when the mission ends or the mine is over). */
	private boolean worthDeposit(WorkContext ctx) {
		List<ItemStack> carried = ctx.record.carried;
		if (carried.isEmpty()) {
			return false;
		}
		if (ctx.record.goalReached || zone.mine().exhausted) {
			return true;
		}
		for (ItemStack stack : carried) {
			if (!MineWorld.isFiller(stack)) {
				return true;
			}
		}
		return MineWorld.fillerCount(ctx.record) > ctx.config.realMining.fillerReserve || carried.size() > 1;
	}

	/**
	 * Off to the deposit chest. A stack of filler blocks (at most {@code fillerReserve}) stays in the bag to seal leaks
	 * and fill holes on the way back down; everything goes when the mission ends or the mine is over.
	 */
	private void startDeposit(WorkContext ctx) {
		phase = Phase.DEPOSIT;
		reserve = null;
		int keep = ctx.config.realMining.fillerReserve;
		List<ItemStack> carried = ctx.record.carried;
		if (keep > 0 && !ctx.record.goalReached && !zone.mine().exhausted) {
			for (ItemStack stack : carried) {
				if (MineWorld.isFiller(stack)) {
					reserve = stack;
					break;
				}
			}
		}
		ItemStack kept = reserve;
		if (kept != null) {
			// Last in the bag (the deposit chest is chosen for the first stack), the surplus deposited.
			carried.remove(kept);
			if (kept.getCount() > keep) {
				carried.add(kept.split(kept.getCount() - keep));
			}
			carried.add(kept);
			GuildManager.markDirty();
		}
		depositStep(ctx);
	}

	private void depositStep(WorkContext ctx) {
		ItemStack kept = reserve;
		DepositFlow.State state = deposit.tick(ctx, stack -> stack == kept);
		switch (state) {
			case WORKING -> {
			}
			case DONE -> {
				tripEnded = true;
				if (Missions.complete(ctx)) {
					return;
				}
				phase = Phase.WORK;
			}
			case NO_CHEST, ALL_FULL -> {
				ctx.status(DepositFlow.blockedStatus(state));
				if (ctx.mustReturn()) {
					phase = Phase.BLOCKED;
					retryAt = ctx.now + FULL_RETRY_TICKS;
				} else {
					phase = Phase.WORK;
				}
			}
		}
	}

	/** Time to come back up before the night (the night itself pauses the task). */
	private static boolean evening(WorkContext ctx) {
		int at = ctx.config.realMining.surfaceAtTime;
		return at >= 0 && Math.floorMod(ctx.level.getOverworldClockTime(), 24000L) >= at;
	}

	/** Somewhere in the tunnels (on a route node below the fanion, or underground inside the zone). */
	private boolean inMine(WorkContext ctx, MineLayout layout) {
		Villager villager = ctx.villager;
		int node = layout.nearestNode(villager.getX(), villager.getY(), villager.getZ(), ON_ROUTE, n -> true);
		if (node >= 0) {
			return node > 0;
		}
		return villager.getY() < zone.pos.getY() - 2 && zone.containsWithMargin(villager.getX(), villager.getZ(), 2);
	}

	// ================================================================ work

	private void work(WorkContext ctx, Mines.Plan plan) {
		phase = Phase.WORK;
		if (!jobValid(ctx, plan) && !pickJob(ctx, plan)) {
			return;
		}
		VillagerConfig.RealMining real = ctx.config.realMining;
		double rate = Math.min(MAX_BLOCKS_PER_STEP, 20.0 / real.ticksPerBlock * Upgrades.workRate(ctx.guild));
		budget = Math.min(budget + rate, Math.max(1.0, rate));
		MineWorld world = new MineWorld(ctx.level, ctx.guild);
		MineLayout layout = plan.layout();
		for (int checked = 0; checked < MAX_CELLS_PER_STEP; checked++) {
			if (cursor >= jobSize(plan)) {
				finishJob(ctx, plan);
				return;
			}
			MineLayout.Cell cell = jobCell(plan, cursor);
			BlockPos stand = layout.node(jobStand(plan, cursor));
			if (!ctx.ticking(cell.feet()) || !ctx.ticking(stand)) {
				ctx.status("villager.status.zone_unloaded", zone.name);
				return;
			}
			switch (digCell(ctx, plan, world, cell, atStand(ctx, stand))) {
				case DONE -> completeCell(plan, cell);
				case PENDING -> {
					// While following a vein, its own status stays.
					if (veins == null || veins.isEmpty()) {
						workStatus(ctx, plan, cell);
					}
					return;
				}
				case WALK -> {
					if (!walkTo(ctx, layout, jobStand(plan, cursor), walkable(ctx, layout, plan))) {
						ctx.status("villager.status.mine.going_down", zone.name, ctx.villager.getBlockY(), cell.feet().getY());
					}
					return;
				}
				case WAIT -> {
					ctx.status("villager.status.zone_unloaded", zone.name);
					return;
				}
				case FAILED -> {
					return;
				}
			}
		}
		workStatus(ctx, plan, jobCell(plan, Math.min(cursor, jobSize(plan) - 1)));
	}

	/** Advances the current cell as far as the budget allows. */
	private CellStep digCell(WorkContext ctx, Mines.Plan plan, MineWorld world, MineLayout.Cell cell, boolean near) {
		// 1) The blocks of the cell, top-down.
		boolean settle = false;
		for (int i = cell.height() - 1; i >= 0; i--) {
			BlockPos pos = cell.feet().above(i);
			MineRules.Check check = MineRules.dig(world, pos.getX(), pos.getY(), pos.getZ());
			switch (check.verdict()) {
				case CLEAR -> {
				}
				case WAIT -> {
					return CellStep.WAIT;
				}
				case DIG -> {
					if (!near) {
						return CellStep.WALK;
					}
					if (budget < 1.0) {
						Movement.hold(ctx, pos);
						return CellStep.PENDING;
					}
					if (redigs >= cell.height() + MAX_REDIGS) {
						fail(ctx, plan, Mines.issueKey(MineRules.Verdict.CAVE_IN), pos);
						return CellStep.FAILED;
					}
					if (world.decorated(pos)) {
						fail(ctx, plan, Mines.issueKey(MineRules.Verdict.BUILDING), pos);
						return CellStep.FAILED;
					}
					if (!seal(ctx, world, check.seal())) {
						fail(ctx, plan, "villager.mine.issue.no_filler", pos);
						return CellStep.FAILED;
					}
					settle |= world.at(pos.getX(), pos.getY() + 1, pos.getZ()) == MineBlock.FALLING;
					Movement.hold(ctx, pos);
					collect(ctx, plan, world, pos);
					budget -= 1.0;
					redigs++;
				}
				default -> {
					fail(ctx, plan, Mines.issueKey(check.verdict()), pos);
					return CellStep.FAILED;
				}
			}
		}
		if (settle) {
			// Sand or gravel above comes down a moment later: look again next step (dug again if it fell in).
			return CellStep.PENDING;
		}
		// 2) The floor: holes filled, unsafe natural blocks (magma) replaced.
		BlockPos floor = cell.floor();
		MineRules.FloorCheck floorCheck = MineRules.floor(world, floor.getX(), floor.getY(), floor.getZ());
		switch (floorCheck.fix()) {
			case OK -> {
			}
			case WAIT -> {
				return CellStep.WAIT;
			}
			case BLOCKED -> {
				fail(ctx, plan, Mines.issueKey(floorCheck.reason()), floor);
				return CellStep.FAILED;
			}
			case FILL, REPLACE -> {
				if (!near) {
					return CellStep.WALK;
				}
				if (budget < 1.0) {
					return CellStep.PENDING;
				}
				if (floorCheck.fix() == MineRules.FloorFix.REPLACE) {
					MineRules.Check check = MineRules.dig(world, floor.getX(), floor.getY(), floor.getZ());
					if (check.verdict() == MineRules.Verdict.DIG && world.decorated(floor)) {
						check = new MineRules.Check(MineRules.Verdict.BUILDING, List.of());
					}
					if (check.verdict() != MineRules.Verdict.DIG || !seal(ctx, world, check.seal())) {
						fail(ctx, plan, check.verdict() == MineRules.Verdict.DIG ? "villager.mine.issue.no_filler" : Mines.issueKey(check.verdict()), floor);
						return CellStep.FAILED;
					}
					collect(ctx, plan, world, floor);
				}
				BlockState filler = MineWorld.takeFiller(ctx.record);
				if (filler == null && ctx.config.realMining.freeFloorBlocks) {
					filler = Blocks.COBBLESTONE.defaultBlockState();
				}
				if (filler == null) {
					fail(ctx, plan, "villager.mine.issue.no_filler", floor);
					return CellStep.FAILED;
				}
				Movement.hold(ctx, floor);
				WorldUtil.swing(ctx.villager);
				world.place(floor, filler);
				budget -= 1.0;
				return CellStep.PENDING;
			}
		}
		// 3) Ore veins seen from the cell.
		if (zone.objective != ZoneObjective.STONE && ctx.config.realMining.maxVeinBlocks > 0) {
			CellStep veinStep = digVeins(ctx, plan, world, cell, near);
			if (veinStep != CellStep.DONE) {
				return veinStep;
			}
		}
		// 4) Light.
		torch(ctx, plan, world, cell);
		return CellStep.DONE;
	}

	private CellStep digVeins(WorkContext ctx, Mines.Plan plan, MineWorld world, MineLayout.Cell cell, boolean near) {
		UUID me = ctx.record.uuid;
		if (veins == null) {
			List<BlockPos> found = new ArrayList<>();
			for (BlockPos ore : MineRules.vein(world, cell, plan.layout().structure(), ctx.config.realMining.maxVeinBlocks, VEIN_REACH)) {
				// Inside the zone square only; another miner may already follow the same vein from its own tunnel.
				if (zone.contains(ore) && Mines.lease(Mines.leaseKey(ctx.guild, zone, "b" + ore.asLong()), me, ctx.now)) {
					found.add(ore);
				}
			}
			veins = found;
		}
		List<BlockPos> pending = veins;
		while (!pending.isEmpty()) {
			BlockPos ore = pending.getFirst();
			MineRules.Verdict verdict = MineRules.digVein(world, ore.getX(), ore.getY(), ore.getZ());
			if (verdict == MineRules.Verdict.WAIT) {
				return CellStep.WAIT;
			}
			if (verdict != MineRules.Verdict.DIG || world.decorated(ore)) {
				// Next to a fluid, under a building or a falling block, carrying an item frame...: left in place.
				pending.removeFirst();
				continue;
			}
			if (!near) {
				return CellStep.WALK;
			}
			if (budget < 1.0) {
				ctx.status("villager.status.mine.vein", pending.size(), cell.feet().getY());
				Movement.hold(ctx, ore);
				return CellStep.PENDING;
			}
			Movement.hold(ctx, ore);
			collect(ctx, plan, world, ore);
			budget -= 1.0;
			pending.removeFirst();
			ctx.status("villager.status.mine.vein", pending.size(), cell.feet().getY());
		}
		return CellStep.DONE;
	}

	/** Seals the fluids next to a block with filler blocks of the bag; false when the bag has not enough. */
	private static boolean seal(WorkContext ctx, MineWorld world, List<BlockPos> fluids) {
		if (fluids.isEmpty()) {
			return true;
		}
		if (MineWorld.fillerCount(ctx.record) < fluids.size()) {
			return false;
		}
		for (BlockPos pos : fluids) {
			BlockState filler = MineWorld.takeFiller(ctx.record);
			if (filler == null) {
				return false;
			}
			world.place(pos, filler);
		}
		WorldUtil.swing(ctx.villager);
		return true;
	}

	/** Breaks a block: drops into the bag (the "ores" objective keeps ores and a reserve of filler blocks only). */
	private void collect(WorkContext ctx, Mines.Plan plan, MineWorld world, BlockPos pos) {
		MineBlock kind = world.at(pos.getX(), pos.getY(), pos.getZ());
		List<ItemStack> drops = world.breakBlock(pos, ctx.villager);
		WorldUtil.swing(ctx.villager);
		boolean rubble = zone.objective == ZoneObjective.ORES && kind != MineBlock.ORE;
		int reserve = rubble ? ctx.config.realMining.fillerReserve - MineWorld.fillerCount(ctx.record) : 0;
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			ItemStack kept = drop;
			if (rubble) {
				if (reserve <= 0 || !MineWorld.isFiller(drop)) {
					continue;
				}
				kept = drop.copyWithCount(Math.min(reserve, drop.getCount()));
				reserve -= kept.getCount();
			}
			ItemStack leftover = ctx.addCarried(kept);
			ctx.record.stats.mined += kept.getCount() - leftover.getCount();
			if (!leftover.isEmpty()) {
				Villager villager = ctx.villager;
				ItemEntity entity = new ItemEntity(ctx.level, villager.getX(), villager.getY() + 0.5, villager.getZ(), leftover);
				entity.setDefaultPickUpDelay();
				ctx.level.addFreshEntity(entity);
			}
		}
		MineState state = plan.state();
		state.blocksMined++;
		if (kind == MineBlock.ORE) {
			state.oresFound++;
		}
		GuildManager.markDirty();
	}

	/** Wall torch every {@code torchInterval} blocks, hung on rock that is not to be dug (coal of the bag if any). */
	private void torch(WorkContext ctx, Mines.Plan plan, MineWorld world, MineLayout.Cell cell) {
		VillagerConfig.RealMining real = ctx.config.realMining;
		int interval = real.torchInterval;
		if (interval <= 0) {
			return;
		}
		BlockPos feet = cell.feet();
		boolean stairs = job == MineLayout.STAIRS;
		int index = stairs ? cursor + 1 : feet.getX() + feet.getZ();
		if (Math.floorMod(index, interval) != 0) {
			return;
		}
		BlockPos at = feet.above(stairs ? 2 : 1);
		if (!ctx.level.getBlockState(at).isAir()) {
			return;
		}
		if (real.torchesUseCoal && torchCredits <= 0
				&& (MineWorld.take(ctx.record, Items.COAL) || MineWorld.take(ctx.record, Items.CHARCOAL))) {
			torchCredits = 4;
			MineWorld.take(ctx.record, Items.STICK);
		}
		boolean paid = torchCredits > 0;
		if (!paid && !real.freeTorches) {
			return;
		}
		MineLayout layout = plan.layout();
		if (world.placeTorch(at, wall -> !layout.inStructure(wall) && world.at(wall.getX(), wall.getY(), wall.getZ()) != MineBlock.ORE)
				&& paid) {
			torchCredits--;
		}
	}

	private void completeCell(Mines.Plan plan, MineLayout.Cell cell) {
		MineState state = plan.state();
		int y = cell.feet().getY();
		if (state.deepestY == null || y < state.deepestY) {
			state.deepestY = y;
		}
		if (job == MineLayout.STAIRS && state.stairsDone == cursor) {
			state.stairsDone = cursor + 1;
		}
		cursor++;
		redigs = 0;
		veins = null;
		GuildManager.markDirty();
	}

	private void workStatus(WorkContext ctx, Mines.Plan plan, MineLayout.Cell cell) {
		int level = plan.levelY();
		if (job == MineLayout.STAIRS) {
			ctx.status("villager.status.mine.stairs", cell.feet().getY(), level);
			return;
		}
		MineLayout.Segment segment = plan.layout().segment(job);
		if (segment == null) {
			return;
		}
		switch (segment.kind()) {
			case MAIN -> ctx.status("villager.status.mine.main", cursor + 1, segment.cells().size(), level);
			case BRANCH -> ctx.status("villager.status.mine.branch", segment.ordinal(), plan.layout().galleryCount(), level);
			case ROOM -> ctx.status("villager.status.mine.room", segment.ordinal(), plan.layout().galleryCount(), level);
		}
	}

	/** Gives up the current segment (lava, building...), or stops the stairs there. */
	private void fail(WorkContext ctx, Mines.Plan plan, String issue, BlockPos pos) {
		MineState state = plan.state();
		if (job == MineLayout.STAIRS) {
			Mines.stairsBlocked(ctx, zone, plan, cursor, issue, pos);
		} else {
			state.issue(issue, pos);
			if (!state.abandoned.contains(job)) {
				state.abandoned.add(job);
			}
			GuildManager.markDirty();
		}
		ctx.status("villager.status.mine.blocked", Lang.raw(issue));
		dropJob(ctx);
	}

	// ================================================================ jobs

	private boolean jobValid(WorkContext ctx, Mines.Plan plan) {
		if (job == NO_JOB) {
			return false;
		}
		MineState state = plan.state();
		boolean valid;
		if (!jobKey.equals(state.layoutKey + "#" + plan.levelY())) {
			valid = false;
		} else if (job == MineLayout.STAIRS) {
			valid = state.stairsDone < plan.stairsNeeded();
			cursor = Math.max(cursor, state.stairsDone);
		} else {
			valid = plan.layout().segment(job) != null && !state.done.contains(job) && !state.abandoned.contains(job);
		}
		if (valid && Mines.lease(leaseKey(ctx, plan, job), ctx.record.uuid, ctx.now)) {
			return true;
		}
		dropJob(ctx);
		return false;
	}

	/** Takes the first free work: the stairs, else the first gallery segment whose way is dug. */
	private boolean pickJob(WorkContext ctx, Mines.Plan plan) {
		MineState state = plan.state();
		UUID me = ctx.record.uuid;
		if (state.stairsDone < plan.stairsNeeded()) {
			String key = leaseKey(ctx, plan, MineLayout.STAIRS);
			if (!Mines.lease(key, me, ctx.now)) {
				UUID holder = Mines.holder(key, ctx.now);
				VillagerRecord other = holder == null ? null : ctx.guild.villagers.get(holder);
				ctx.status("villager.status.mine.waiting_stairs", other == null ? "?" : other.name);
				return false;
			}
			start(plan, MineLayout.STAIRS, state.stairsDone);
			return true;
		}
		boolean others = false;
		for (MineLayout.Segment segment : plan.layout().segments()) {
			if (state.done.contains(segment.id()) || state.abandoned.contains(segment.id())) {
				continue;
			}
			int reach = reachable(state, plan.layout(), segment);
			if (reach < 0) {
				continue;
			}
			if (reach > 0 || !Mines.lease(leaseKey(ctx, plan, segment.id()), me, ctx.now)) {
				others = true;
				continue;
			}
			start(plan, segment.id(), 0);
			return true;
		}
		if (others) {
			ctx.status("villager.status.mine.waiting_galleries", zone.name);
			return false;
		}
		// Every gallery of the level is done (or given up).
		Mines.finishLevel(ctx, zone, plan);
		ctx.status("villager.status.mine.planning", zone.name);
		return false;
	}

	/** 0: its way is dug; 1: later (the way is being dug); -1: never (the way was given up). */
	private static int reachable(MineState state, MineLayout layout, MineLayout.Segment segment) {
		int after = segment.after();
		int result = 0;
		while (after >= 0) {
			if (state.abandoned.contains(after)) {
				return -1;
			}
			if (!state.done.contains(after)) {
				result = 1;
			}
			MineLayout.Segment previous = layout.segment(after);
			after = previous == null ? -1 : previous.after();
		}
		return result;
	}

	private void start(Mines.Plan plan, int segment, int from) {
		job = segment;
		jobKey = plan.state().layoutKey + "#" + plan.levelY();
		cursor = from;
		redigs = 0;
		veins = null;
		walkDest = -1;
	}

	private void finishJob(WorkContext ctx, Mines.Plan plan) {
		MineState state = plan.state();
		if (job != MineLayout.STAIRS && !state.done.contains(job)) {
			state.done.add(job);
			GuildManager.markDirty();
		}
		dropJob(ctx);
	}

	private void dropJob(WorkContext ctx) {
		Mines.release(ctx.record.uuid);
		job = NO_JOB;
		jobKey = "";
		cursor = 0;
		redigs = 0;
		veins = null;
	}

	private String leaseKey(WorkContext ctx, Mines.Plan plan, int segment) {
		return Mines.leaseKey(ctx.guild, zone, segment == MineLayout.STAIRS ? "stairs" : plan.levelY() + ":" + segment);
	}

	private int jobSize(Mines.Plan plan) {
		if (job == MineLayout.STAIRS) {
			return plan.stairsNeeded();
		}
		MineLayout.Segment segment = plan.layout().segment(job);
		return segment == null ? 0 : segment.cells().size();
	}

	private MineLayout.Cell jobCell(Mines.Plan plan, int index) {
		return job == MineLayout.STAIRS ? plan.layout().stairs().get(index) : plan.layout().segment(job).cells().get(index);
	}

	/** Node to stand on to dig cell {@code index}: the previous step / cell (the fanion for the first step). */
	private int jobStand(Mines.Plan plan, int index) {
		return job == MineLayout.STAIRS ? index : plan.layout().segment(job).stands()[index];
	}

	// ================================================================ walking

	private static boolean atStand(WorkContext ctx, BlockPos stand) {
		return stand.equals(ctx.runtime.remoteTarget) || ctx.distSq(stand) <= STAND_REACH * STAND_REACH;
	}

	/** Nodes the miner may walk through: the fanion, the dug stairs, finished segments, any cell that is open. */
	private IntPredicate walkable(WorkContext ctx, MineLayout layout, Mines.@Nullable Plan plan) {
		MineState state = zone.mine();
		MineWorld world = new MineWorld(ctx.level, ctx.guild);
		boolean current = plan != null && plan.layout() == layout;
		return node -> {
			int segment = layout.nodeSegment(node);
			if (segment < 0) {
				return true;
			}
			int cell = layout.nodeCell(node);
			if (current && segment == MineLayout.STAIRS && cell < state.stairsDone) {
				return true;
			}
			if (current && segment != MineLayout.STAIRS && (state.done.contains(segment) || (segment == job && cell < cursor))) {
				return true;
			}
			BlockPos feet = layout.node(node);
			return world.at(feet.getX(), feet.getY(), feet.getZ()).passable()
					&& world.at(feet.getX(), feet.getY() + 1, feet.getZ()).passable();
		};
	}

	/**
	 * Walks along the mine's walking graph toward node {@code dest}: waypoint a few nodes ahead (short vanilla paths),
	 * straight to the nearest node when off the route. A watchdog teleports the miner to its destination when it makes
	 * no progress for a while (or lets it work from where it stands).
	 *
	 * @return true when the miner stands at the destination
	 */
	private boolean walkTo(WorkContext ctx, MineLayout layout, int dest, IntPredicate walkable) {
		BlockPos target = layout.node(dest);
		if (atStand(ctx, target)) {
			walkDest = -1;
			return true;
		}
		Villager villager = ctx.villager;
		int current = layout.nearestNode(villager.getX(), villager.getY(), villager.getZ(), ON_ROUTE, walkable);
		BlockPos waypoint;
		int remaining;
		if (current >= 0) {
			List<Integer> path = layout.path(current, dest);
			waypoint = path.isEmpty() ? target : layout.node(path.get(Math.min(LOOKAHEAD, path.size()) - 1));
			remaining = path.size();
		} else {
			// Off the route (coming back from the deposit chest...): first to the nearest open node, else to the fanion.
			int near = layout.nearestNode(villager.getX(), villager.getY(), villager.getZ(), NEAR_ROUTE, walkable);
			waypoint = layout.node(near >= 0 ? near : 0);
			remaining = OFF_ROUTE + (int) Math.sqrt(ctx.distSq(waypoint));
		}
		if (dest != walkDest) {
			walkDest = dest;
			walkBest = remaining;
			walkSince = ctx.now;
		} else if (remaining < walkBest) {
			walkBest = remaining;
			walkSince = ctx.now;
		} else if (ctx.now - walkSince > WorkContext.ticks(ctx.config.work.stuckSeconds * 2)) {
			Movement.teleportNear(ctx, target);
			ctx.runtime.resetMovement();
			walkDest = -1;
			if (!atStand(ctx, target)) {
				// Even the teleport could not put it there (spot in the air, blocked...): it works from where it stands.
				ctx.runtime.remoteTarget = target;
			}
			return true;
		}
		Movement.Result result = Movement.moveTo(ctx, waypoint, waypoint.equals(target) ? STAND_REACH : ON_ROUTE,
				ctx.config.work.walkTimeoutSeconds);
		return result == Movement.Result.ARRIVED && waypoint.equals(target);
	}
}
