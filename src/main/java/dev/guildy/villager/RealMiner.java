package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

/**
 * Real excavation by one miner in a REAL mine zone (zone side: {@link Mines}, geometry: {@link MineLayout}, rules:
 * {@link MineRules}, work sharing: {@link MineJobs}). Each step (once per second):
 * <ul>
 * <li>the miner takes the nearest open job nobody else holds (stair front, widening of the stairs, a landing, a
 * segment of main tunnel, a branch or a quarry room of any level the stairs reached, a pending vein) and keeps it
 * leased while it works on it;</li>
 * <li>it walks there through the mine's own tunnels: waypoints a few nodes ahead along the walking graph (the vanilla
 * navigation only ever plans short paths), a watchdog teleports it to its spot when it makes no progress;</li>
 * <li>cells are dug in order: their blocks top-down when the rules allow it (fluids next to a block are sealed with a
 * filler block of the bag first, else the segment is given up), a settle step (falling sand / gravel is dug again),
 * the floor (holes filled; an ore or a wanted block in the floor is taken and the floor filled again), the veins seen
 * from the cell (ores and wanted blocks within reach right away, the rest becomes a pending vein followed later
 * through an approach tunnel, a little outside the zone too), a wall torch every few blocks.</li>
 * </ul>
 * Real drops (iron pickaxe) go to the bag ("ores" objective: ores, wanted items and a reserve of filler blocks only);
 * wanted items are never used as filler and push rubble out of a full bag. A full bag, a reached mission goal or the
 * evening: back up through the tunnels to the deposit chest, then back to work.
 */
final class RealMiner {
	/** Waypoints this many nodes ahead along the route: the vanilla navigation only plans short paths. */
	private static final int LOOKAHEAD = 5;
	/** Cells checked per step without walking (already dug ones). */
	private static final int MAX_CELLS_PER_STEP = 16;
	/** Blocks dug again in one cell (falling sand / gravel) before giving up. */
	private static final int MAX_REDIGS = 24;
	private static final double MAX_BLOCKS_PER_STEP = 4.0;
	/** Blocks a miner mines from where it stands (eye to block centre). */
	static final double VEIN_REACH = 4.5;
	/** Height above / below a cell where its veins are followed (farther: out of reach anyway). */
	private static final int VEIN_HEIGHT = 4;
	/** Approach tunnel cells beyond {@code veinMaxOutside} (the tunnel starts inside the zone). */
	private static final int TUNNEL_EXTRA = 8;
	private static final double ON_ROUTE = 1.6;
	private static final double NEAR_ROUTE = 8.0;
	/** Close enough to the stand node to dig the next cell. */
	private static final double STAND_REACH = 1.5;
	/** Walking progress off the route counts the distance left above this (any node on the route is better). */
	private static final int OFF_ROUTE = 100_000;
	private static final long FULL_RETRY_TICKS = 10 * 20L;
	/** Without work this long in a gallery: back to the surface (it may wait in a gallery, never on the stairs). */
	private static final long WAIT_IN_PLACE_TICKS = 30 * 20L;

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
		/** The cell cannot be dug ({@link #failIssue}). */
		FAILED
	}

	final WorkZone zone;
	private Phase phase = Phase.WORK;
	/** Layout of the last plan: lets the miner walk out of the mine when it has nothing to dig any more. */
	private @Nullable MineLayout route;
	private MineJobs.@Nullable Job job;
	private @Nullable MineLayout jobLayout;
	/** Cells of the current stairs / widening / segment job, the node to stand on for each, the stair step of each. */
	private List<MineLayout.Cell> cells = List.of();
	private int[] stands = new int[0];
	private int[] steps = new int[0];
	private int cursor;
	private int redigs;
	// ---- current cell
	/** Wall / ceiling veins of the current cell to mine from the stand (null = not searched yet). */
	private @Nullable List<BlockPos> inline;
	/** 0: floor not handled; 1: an ore of the floor was taken, its vein below then the floor is filled; 2: done. */
	private int floorStage;
	private List<BlockPos> floorVein = List.of();
	private @Nullable String failIssue;
	private @Nullable BlockPos failPos;
	// ---- vein job
	private @Nullable List<BlockPos> cluster;
	/** Where the miner stands to follow the vein: its start node, then the end of its approach tunnel. */
	private @Nullable BlockPos veinStand;
	private MineLayout.@Nullable Cell tunnelCell;
	private int tunnelLength;
	/** Start node of the last vein job: the way back out of its approach tunnel. */
	private int exitNode = -1;
	// ----
	private double budget;
	private int torchCredits;
	private long retryAt;
	private int walkDest = -1;
	private int walkBest;
	private long walkSince;
	private long idleSince = -1;
	private @Nullable BlockPos waitSpot;
	private boolean tripEnded;
	/** Filler stack kept in the bag at the deposit chest (identity). */
	private @Nullable ItemStack reserve;
	private final DepositFlow deposit = new DepositFlow();
	private MineWorld.Wanted wanted = new MineWorld.Wanted(Set.of());

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
		refreshWanted(ctx);
		int max = ctx.config.realMining.maxMinersPerZone;
		if (!Mines.admit(ctx.guild, zone, ctx.record.uuid, ctx.now, max, workLevel())) {
			// The zone has its maximum of miners at work: this one waits for a place (its bag emptied first).
			dropJob(ctx);
			if (phase == Phase.WORK && !ctx.record.carried.isEmpty()) {
				startDeposit(ctx);
			} else if (phase == Phase.DEPOSIT) {
				depositStep(ctx);
			} else {
				ctx.status("villager.status.mine.full", zone.name, max);
			}
			return tripEnded;
		}
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
		Mines.dismiss(ctx.guild, zone, ctx.record.uuid);
		deposit.reset();
	}

	/** Wanted items of the villager and of the zone ("objectifs prioritaires"), refreshed when they change. */
	private void refreshWanted(WorkContext ctx) {
		Set<Item> items = Wishlist.items(ctx.record, zone);
		if (!items.equals(wanted.items)) {
			wanted = new MineWorld.Wanted(items);
		}
	}

	/** Level of the current job (for the zone view), {@link MineLayout#STAIRS_LEVEL} on the stairs / the surface. */
	private int workLevel() {
		MineJobs.Job current = job;
		if (current == null || phase != Phase.WORK) {
			return MineLayout.STAIRS_LEVEL;
		}
		return switch (current.type()) {
			case SEGMENT, VEIN -> current.levelY();
			default -> MineLayout.STAIRS_LEVEL;
		};
	}

	// ================================================================ leaving the mine

	/**
	 * Bag full, goal reached, evening or nothing to dig: up through the tunnels to the fanion, then to the deposit chest
	 * (or wait at the surface).
	 */
	private void leave(WorkContext ctx, Mines.@Nullable Plan plan, boolean evening) {
		if (job != null) {
			// Let the other miners take the job meanwhile (dug cells are walked through quickly).
			dropJob(ctx);
		}
		if (plan == null && !zone.mine().exhausted && !ctx.mustReturn()) {
			// Terrain not ready yet (status set by the plan): wait where it is.
			return;
		}
		MineLayout layout = route;
		Villager villager = ctx.villager;
		if (layout != null && inMine(ctx, layout)) {
			if (!walkTo(ctx, layout, MineLayout.ENTRANCE, walkable(ctx, layout, plan))) {
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
			if (!MineWorld.isFiller(stack) || wanted.item(stack)) {
				return true;
			}
		}
		return MineWorld.fillerCount(ctx.record) > ctx.config.realMining.fillerReserve || carried.size() > 1;
	}

	/**
	 * Off to the deposit chest. A stack of filler blocks (at most {@code fillerReserve}, never a wanted item) stays in the
	 * bag to seal leaks and fill holes on the way back down; everything goes when the mission ends or the mine is over.
	 */
	private void startDeposit(WorkContext ctx) {
		phase = Phase.DEPOSIT;
		reserve = null;
		int keep = ctx.config.realMining.fillerReserve;
		List<ItemStack> carried = ctx.record.carried;
		if (keep > 0 && !ctx.record.goalReached && !zone.mine().exhausted) {
			for (ItemStack stack : carried) {
				if (MineWorld.isFiller(stack) && !wanted.item(stack)) {
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

	/** Somewhere in the tunnels (on a route node below the fanion, or underground inside or near the zone). */
	private boolean inMine(WorkContext ctx, MineLayout layout) {
		Villager villager = ctx.villager;
		int node = layout.nearestNode(villager.getX(), villager.getY(), villager.getZ(), ON_ROUTE, n -> true);
		if (node >= 0) {
			return node != MineLayout.ENTRANCE;
		}
		return villager.getY() < zone.pos.getY() - 2
				&& zone.containsWithMargin(villager.getX(), villager.getZ(), ctx.config.realMining.veinMaxOutside + 2);
	}

	// ================================================================ work

	private void work(WorkContext ctx, Mines.Plan plan) {
		phase = Phase.WORK;
		if ((job == null || !jobValid(ctx, plan)) && !pickJob(ctx, plan)) {
			idle(ctx, plan);
			return;
		}
		idleSince = -1;
		VillagerConfig.RealMining real = ctx.config.realMining;
		double rate = Math.min(MAX_BLOCKS_PER_STEP, 20.0 / real.ticksPerBlock * Upgrades.workRate(ctx.guild));
		budget = Math.min(budget + rate, Math.max(1.0, rate));
		MineWorld world = new MineWorld(ctx.level, ctx.guild, wanted);
		MineJobs.Job current = job;
		if (current != null && current.type() == MineJobs.Type.VEIN) {
			veinStep(ctx, plan, world, current);
		} else {
			cellsStep(ctx, plan, world);
		}
	}

	/**
	 * No job: waits in a gallery for a while, never on the stairs (the others walk there); else at the surface, a few
	 * blocks behind the fanion so that the way in stays free.
	 */
	private void idle(WorkContext ctx, Mines.Plan plan) {
		if (idleSince < 0) {
			idleSince = ctx.now;
		}
		MineLayout layout = plan.layout();
		Villager villager = ctx.villager;
		int node = layout.nearestNode(villager.getX(), villager.getY(), villager.getZ(), ON_ROUTE, n -> true);
		boolean onStairs = node > MineLayout.ENTRANCE && layout.nodeLevel(node) == MineLayout.STAIRS_LEVEL;
		boolean lost = node < 0 && inMine(ctx, layout);
		if (onStairs || lost || (node > MineLayout.ENTRANCE && ctx.now - idleSince > WAIT_IN_PLACE_TICKS)) {
			walkTo(ctx, layout, MineLayout.ENTRANCE, walkable(ctx, layout, plan));
		} else if (node == MineLayout.ENTRANCE || (node < 0 && ctx.distSq(zone.pos) < 6 * 6)) {
			Movement.moveTo(ctx, waitSpot(ctx, layout), 1.5, ctx.config.work.walkTimeoutSeconds);
		} else {
			Movement.hold(ctx, null);
		}
	}

	/** Where idle miners wait at the surface: behind the fanion, away from the staircase (else the fanion itself). */
	private BlockPos waitSpot(WorkContext ctx, MineLayout layout) {
		BlockPos spot = waitSpot;
		if (spot == null) {
			int back = MineLayout.turn(MineLayout.firstDirection(layout.orientation()), 2);
			BlockPos behind = zone.pos.offset(MineLayout.dx(back) * 3, 0, MineLayout.dz(back) * 3);
			spot = WorldUtil.findSafeSpot(ctx.level, behind, 2, false, pos -> !layout.inStairColumns(pos.getX(), pos.getZ()));
			if (spot == null) {
				spot = zone.pos;
			}
			waitSpot = spot;
		}
		return spot;
	}

	private void cellsStep(WorkContext ctx, Mines.Plan plan, MineWorld world) {
		MineLayout layout = plan.layout();
		for (int checked = 0; checked < MAX_CELLS_PER_STEP; checked++) {
			if (cursor >= cells.size()) {
				finishJob(ctx, plan);
				return;
			}
			MineLayout.Cell cell = cells.get(cursor);
			int standNode = stands[cursor];
			BlockPos stand = layout.node(standNode);
			if (!ctx.ticking(cell.feet()) || !ctx.ticking(stand)) {
				ctx.status("villager.status.zone_unloaded", zone.name);
				return;
			}
			switch (digCell(ctx, plan, world, cell, stand, atStand(ctx, stand))) {
				case DONE -> completeCell(plan, cell);
				case PENDING -> {
					if (!veinStatus(ctx, cell)) {
						workStatus(ctx, plan, cell);
					}
					return;
				}
				case WALK -> {
					if (!walkTo(ctx, layout, standNode, walkable(ctx, layout, plan))) {
						goingStatus(ctx, cell);
					}
					return;
				}
				case WAIT -> {
					ctx.status("villager.status.zone_unloaded", zone.name);
					return;
				}
				case FAILED -> {
					if (!cellFailed(ctx, plan, cell)) {
						return;
					}
				}
			}
		}
		workStatus(ctx, plan, cells.get(Math.min(cursor, cells.size() - 1)));
	}

	/**
	 * A cell could not be dug: the stairs stop above it, a segment is given up; a widening cell is simply skipped.
	 *
	 * @return true to go on with the next cell
	 */
	private boolean cellFailed(WorkContext ctx, Mines.Plan plan, MineLayout.Cell cell) {
		MineJobs.Job current = job;
		String issue = failIssue == null ? Mines.issueKey(MineRules.Verdict.BUILDING) : failIssue;
		BlockPos pos = failPos == null ? cell.feet() : failPos;
		if (current == null) {
			return false;
		}
		switch (current.type()) {
			case WIDEN -> {
				// The outer lane only gives room to pass: a cell that cannot be dug is left.
				completeCell(plan, cell);
				return true;
			}
			case STAIRS -> Mines.stairsBlocked(ctx, zone, plan, steps[cursor], issue, pos);
			case SEGMENT -> {
				plan.state().issue(issue, pos);
				Mines.finishSegment(plan, current.levelY(), current.id(), true);
			}
			default -> {
			}
		}
		ctx.status("villager.status.mine.blocked", Lang.raw(issue));
		dropJob(ctx);
		return false;
	}

	/** Advances the current cell as far as the budget allows; {@code stand}: where the miner digs it from. */
	private CellStep digCell(WorkContext ctx, Mines.Plan plan, MineWorld world, MineLayout.Cell cell, BlockPos stand, boolean near) {
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
						return fail(Mines.issueKey(MineRules.Verdict.CAVE_IN), pos);
					}
					if (world.decorated(pos)) {
						return fail(Mines.issueKey(MineRules.Verdict.BUILDING), pos);
					}
					if (!seal(ctx, world, check.seal())) {
						return fail("villager.mine.issue.no_filler", pos);
					}
					settle |= world.at(pos.getX(), pos.getY() + 1, pos.getZ()) == MineBlock.FALLING;
					Movement.hold(ctx, pos);
					collect(ctx, plan, world, pos);
					budget -= 1.0;
					redigs++;
				}
				default -> {
					return fail(Mines.issueKey(check.verdict()), pos);
				}
			}
		}
		if (settle) {
			// Sand or gravel above comes down a moment later: look again next step (dug again if it fell in).
			return CellStep.PENDING;
		}
		// 2) The floor: holes filled, unsafe blocks replaced, an ore or a wanted block taken.
		if (floorStage < 2) {
			CellStep floorStep = floor(ctx, plan, world, cell, stand, near);
			if (floorStep != CellStep.DONE) {
				return floorStep;
			}
			floorStage = 2;
		}
		// 3) Veins seen from the cell (walls and ceiling).
		if (ctx.config.realMining.maxVeinBlocks > 0) {
			CellStep veinStep = digVeins(ctx, plan, world, cell, stand, near);
			if (veinStep != CellStep.DONE) {
				return veinStep;
			}
		}
		// 4) Light.
		torch(ctx, plan, world, cell);
		return CellStep.DONE;
	}

	private CellStep fail(String issue, BlockPos pos) {
		failIssue = issue;
		failPos = pos.immutable();
		return CellStep.FAILED;
	}

	/** Floor of the cell (see {@link #floorStage}). */
	private CellStep floor(WorkContext ctx, Mines.Plan plan, MineWorld world, MineLayout.Cell cell, BlockPos stand, boolean near) {
		BlockPos floor = cell.floor();
		MineJobs.Job current = job;
		boolean stairs = current != null && (current.type() == MineJobs.Type.STAIRS || current.type() == MineJobs.Type.WIDEN);
		if (floorStage == 0 && world.target(floor)) {
			MineRules.Check check = MineRules.dig(world, floor.getX(), floor.getY(), floor.getZ());
			if (check.verdict() == MineRules.Verdict.WAIT) {
				return CellStep.WAIT;
			}
			if (check.verdict() == MineRules.Verdict.DIG && !world.decorated(floor)
					&& MineWorld.fillerCount(ctx.record, wanted::item) >= check.seal().size()) {
				if (!near) {
					return CellStep.WALK;
				}
				if (budget < 1.0 || ctx.carriedFull()) {
					Movement.hold(ctx, floor);
					return CellStep.PENDING;
				}
				seal(ctx, world, check.seal());
				Movement.hold(ctx, floor);
				collect(ctx, plan, world, floor);
				budget -= 1.0;
				floorStage = 1;
				// Under the stairs (everybody walks there) the floor is filled at once; elsewhere its vein is taken first.
				floorVein = stairs || ctx.config.realMining.maxVeinBlocks <= 0 ? List.of()
						: reachableOnly(MineRules.vein(world, world, List.of(floor), region(cell), excluded(plan.layout()),
						ctx.config.realMining.maxVeinBlocks), stand);
				return CellStep.PENDING;
			}
		}
		if (floorStage == 1 && !floorVein.isEmpty()) {
			CellStep step = mineList(ctx, plan, world, floorVein, near);
			if (step != CellStep.DONE) {
				return step;
			}
		}
		MineRules.FloorCheck floorCheck = MineRules.floor(world, floor.getX(), floor.getY(), floor.getZ());
		switch (floorCheck.fix()) {
			case OK -> {
				return CellStep.DONE;
			}
			case WAIT -> {
				return CellStep.WAIT;
			}
			case BLOCKED -> {
				return fail(Mines.issueKey(floorCheck.reason()), floor);
			}
			default -> {
			}
		}
		// FILL / REPLACE.
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
				return fail(check.verdict() == MineRules.Verdict.DIG ? "villager.mine.issue.no_filler" : Mines.issueKey(check.verdict()), floor);
			}
			collect(ctx, plan, world, floor);
		}
		BlockState filler = filler(ctx);
		if (filler == null) {
			return fail("villager.mine.issue.no_filler", floor);
		}
		Movement.hold(ctx, floor);
		WorldUtil.swing(ctx.villager);
		world.place(floor, filler);
		budget -= 1.0;
		return CellStep.PENDING;
	}

	/** A filler block of the bag (never a wanted item), else a free one when allowed. */
	private @Nullable BlockState filler(WorkContext ctx) {
		BlockState filler = MineWorld.takeFiller(ctx.record, wanted::item);
		if (filler == null && ctx.config.realMining.freeFloorBlocks) {
			filler = Blocks.COBBLESTONE.defaultBlockState();
		}
		return filler;
	}

	/** Ores and wanted blocks around the cell: those within reach right away, the rest as a pending vein. */
	private CellStep digVeins(WorkContext ctx, Mines.Plan plan, MineWorld world, MineLayout.Cell cell, BlockPos stand, boolean near) {
		if (inline == null) {
			List<BlockPos> open = new ArrayList<>(cell.height());
			for (int i = 0; i < cell.height(); i++) {
				open.add(cell.feet().above(i));
			}
			List<BlockPos> found = MineRules.vein(world, world, open, region(cell), excluded(plan.layout()), ctx.config.realMining.maxVeinBlocks);
			List<BlockPos> close = new ArrayList<>(found.size());
			List<BlockPos> far = new ArrayList<>();
			for (BlockPos block : found) {
				if (MineRules.reachable(stand, block, VEIN_REACH)) {
					close.add(block);
				} else {
					far.add(block);
				}
			}
			List<BlockPos> following = cluster;
			MineJobs.Job current = job;
			if (current != null && current.type() == MineJobs.Type.VEIN && following != null) {
				// Seen from an approach tunnel: part of the vein being followed.
				for (BlockPos block : far) {
					if (!following.contains(block)) {
						following.add(block);
					}
				}
			} else if (!far.isEmpty()) {
				BlockPos seed = far.getFirst();
				Mines.addVein(plan.state(), seed, veinNode(plan.layout(), stand), world.wantedBlock(seed), found);
			}
			inline = close;
		}
		return mineList(ctx, plan, world, inline, near);
	}

	/** Mines the listed blocks in order (each one exposed by the previous ones), as far as the budget allows. */
	private CellStep mineList(WorkContext ctx, Mines.Plan plan, MineWorld world, List<BlockPos> pending, boolean near) {
		while (!pending.isEmpty()) {
			BlockPos block = pending.getFirst();
			MineRules.Verdict verdict = MineRules.digVein(world, block.getX(), block.getY(), block.getZ());
			if (verdict == MineRules.Verdict.WAIT || !world.target(block) || verdict != MineRules.Verdict.DIG || world.decorated(block)
					|| !MineRules.exposed(world, block)) {
				// Mined by another miner, next to a fluid, under a building or a falling block, not seen...: left.
				pending.removeFirst();
				continue;
			}
			if (!near) {
				return CellStep.WALK;
			}
			if (budget < 1.0 || ctx.carriedFull()) {
				Movement.hold(ctx, block);
				return CellStep.PENDING;
			}
			Movement.hold(ctx, block);
			collect(ctx, plan, world, block);
			budget -= 1.0;
			pending.removeFirst();
		}
		return CellStep.DONE;
	}

	private static List<BlockPos> reachableOnly(List<BlockPos> blocks, BlockPos stand) {
		List<BlockPos> close = new ArrayList<>(blocks.size());
		for (BlockPos block : blocks) {
			if (MineRules.reachable(stand, block, VEIN_REACH)) {
				close.add(block);
			}
		}
		return close;
	}

	/** Box where the veins of a cell are followed: the zone and {@code veinMaxOutside} blocks around it. */
	private MineRules.Region region(MineLayout.Cell cell) {
		return MineRules.Region.around(zone.bounds(), VillagerModule.config().realMining.veinMaxOutside,
				cell.feet().getY() - VEIN_HEIGHT, cell.top().getY() + VEIN_HEIGHT);
	}

	private static LongPredicate excluded(MineLayout layout) {
		return packed -> layout.inStructure(packed, BlockPos.getY(packed));
	}

	/** Node position a pending vein is walked back to (the stand of the cell it was seen from). */
	private BlockPos veinNode(MineLayout layout, BlockPos stand) {
		int node = layout.nodeAt(stand);
		if (node < 0 && exitNode >= 0) {
			// Seen from an approach tunnel: back through its start.
			return layout.node(exitNode);
		}
		return stand;
	}

	/** Seals the fluids next to a block with filler blocks of the bag; false when the bag has not enough. */
	private boolean seal(WorkContext ctx, MineWorld world, List<BlockPos> fluids) {
		if (fluids.isEmpty()) {
			return true;
		}
		Predicate<ItemStack> keep = wanted::item;
		if (MineWorld.fillerCount(ctx.record, keep) < fluids.size()) {
			return false;
		}
		for (BlockPos pos : fluids) {
			BlockState filler = MineWorld.takeFiller(ctx.record, keep);
			if (filler == null) {
				return false;
			}
			world.place(pos, filler);
		}
		WorldUtil.swing(ctx.villager);
		return true;
	}

	/**
	 * Breaks a block: drops into the bag ("ores" objective: ores, wanted items and a reserve of filler blocks only). A
	 * wanted item that does not fit pushes rubble out of the bag.
	 */
	private void collect(WorkContext ctx, Mines.Plan plan, MineWorld world, BlockPos pos) {
		MineBlock kind = world.at(pos.getX(), pos.getY(), pos.getZ());
		List<ItemStack> drops = world.breakBlock(pos, ctx.villager);
		WorldUtil.swing(ctx.villager);
		boolean oresOnly = zone.objective == ZoneObjective.ORES && kind != MineBlock.ORE;
		int reserveLeft = oresOnly ? ctx.config.realMining.fillerReserve - MineWorld.fillerCount(ctx.record, wanted::item) : 0;
		MineState state = plan.state();
		MineState.Level level = statLevel(state);
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			boolean wantedItem = wanted.item(drop);
			ItemStack kept = drop;
			if (oresOnly && !wantedItem) {
				if (reserveLeft <= 0 || !MineWorld.isFiller(drop)) {
					continue;
				}
				kept = drop.copyWithCount(Math.min(reserveLeft, drop.getCount()));
				reserveLeft -= kept.getCount();
			}
			ItemStack leftover = ctx.addCarried(kept);
			if (!leftover.isEmpty() && wantedItem) {
				leftover = makeRoom(ctx, leftover);
			}
			int taken = kept.getCount() - leftover.getCount();
			ctx.record.stats.mined += taken;
			if (wantedItem) {
				state.wantedFound += drop.getCount();
				if (level != null) {
					level.wanted += drop.getCount();
				}
			}
			if (!leftover.isEmpty()) {
				drop(ctx, leftover);
			}
		}
		state.blocksMined++;
		if (kind == MineBlock.ORE) {
			state.oresFound++;
			if (level != null) {
				level.ores++;
			}
		}
		GuildManager.markDirty();
	}

	/** Level whose statistics count the blocks of the current job (null on the stairs). */
	private MineState.@Nullable Level statLevel(MineState state) {
		MineJobs.Job current = job;
		if (current == null || (current.type() != MineJobs.Type.SEGMENT && current.type() != MineJobs.Type.VEIN)) {
			return null;
		}
		MineState.Level level = state.level(current.levelY());
		if (level == null && current.type() == MineJobs.Type.SEGMENT) {
			level = state.levelOrCreate(current.levelY());
		}
		return level;
	}

	/** A wanted item did not fit: a rubble stack leaves the bag (dropped on the ground) to make room. */
	private ItemStack makeRoom(WorkContext ctx, ItemStack stack) {
		Iterator<ItemStack> it = ctx.record.carried.iterator();
		while (it.hasNext()) {
			ItemStack carried = it.next();
			if (!wanted.item(carried) && carried != reserve) {
				it.remove();
				drop(ctx, carried);
				return ctx.addCarried(stack);
			}
		}
		return stack;
	}

	private static void drop(WorkContext ctx, ItemStack stack) {
		Villager villager = ctx.villager;
		ItemEntity entity = new ItemEntity(ctx.level, villager.getX(), villager.getY() + 0.5, villager.getZ(), stack);
		entity.setDefaultPickUpDelay();
		ctx.level.addFreshEntity(entity);
	}

	/** Wall torch every {@code torchInterval} blocks, hung on rock that is not to be dug (coal of the bag if any). */
	private void torch(WorkContext ctx, Mines.Plan plan, MineWorld world, MineLayout.Cell cell) {
		VillagerConfig.RealMining real = ctx.config.realMining;
		int interval = real.torchInterval;
		MineJobs.Job current = job;
		if (interval <= 0 || current == null || current.type() == MineJobs.Type.WIDEN) {
			return;
		}
		BlockPos feet = cell.feet();
		boolean stairs = current.type() == MineJobs.Type.STAIRS;
		int index = stairs ? steps[Math.min(cursor, steps.length - 1)] + 1 : feet.getX() + feet.getZ();
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
		if (world.placeTorch(at, wall -> !layout.inStructure(wall) && !world.target(wall)
				&& world.at(wall.getX(), wall.getY(), wall.getZ()) != MineBlock.ORE) && paid) {
			torchCredits--;
		}
	}

	private void completeCell(Mines.Plan plan, MineLayout.Cell cell) {
		MineState state = plan.state();
		int y = cell.feet().getY();
		if (state.deepestY == null || y < state.deepestY) {
			state.deepestY = y;
		}
		MineJobs.Job current = job;
		if (current != null && current.type() == MineJobs.Type.STAIRS && state.stairsDone == steps[cursor]) {
			state.stairsDone = steps[cursor] + 1;
		}
		if (current != null && current.type() == MineJobs.Type.WIDEN && (cursor + 1 >= steps.length || steps[cursor + 1] != steps[cursor])) {
			state.widen(steps[cursor]);
		}
		cursor++;
		resetCell();
		GuildManager.markDirty();
	}

	private void resetCell() {
		redigs = 0;
		inline = null;
		floorStage = 0;
		floorVein = List.of();
		failIssue = null;
		failPos = null;
	}

	// ================================================================ vein jobs

	/**
	 * A pending vein: from the node it was seen from, its blocks in order, each mined once exposed and within reach;
	 * out of reach, an approach tunnel (2 high, same rules as a gallery, never through the staircase) is dug toward it,
	 * up to {@code veinMaxOutside} blocks outside the zone.
	 */
	private void veinStep(WorkContext ctx, Mines.Plan plan, MineWorld world, MineJobs.Job current) {
		MineState.Vein vein = current.vein();
		MineLayout layout = plan.layout();
		if (vein == null) {
			dropJob(ctx);
			return;
		}
		VillagerConfig.RealMining real = ctx.config.realMining;
		if (veinStand == null) {
			int start = layout.nodeAt(vein.stand);
			if (start < 0) {
				finishVein(ctx, plan, vein);
				return;
			}
			if (!walkTo(ctx, layout, start, walkable(ctx, layout, plan))) {
				ctx.status("villager.status.mine.going_to", ctx.villager.getBlockY(), vein.stand.getY());
				return;
			}
			veinStand = vein.stand;
			exitNode = start;
		}
		BlockPos stand = veinStand;
		if (cluster == null) {
			MineLayout.Cell standCell = new MineLayout.Cell(stand, MineLayout.TUNNEL_HEIGHT);
			cluster = new ArrayList<>(MineRules.cluster(world, world, vein.seed, region(standCell), excluded(layout), real.maxVeinBlocks));
		}
		List<BlockPos> blocks = cluster;
		for (int guard = 0; guard < MAX_CELLS_PER_STEP; guard++) {
			MineLayout.Cell digging = tunnelCell;
			if (digging != null) {
				switch (digCell(ctx, plan, world, digging, stand, atSpot(ctx, stand))) {
					case DONE -> {
						tunnelCell = null;
						tunnelLength++;
						veinStand = digging.feet();
						resetCell();
						return;
					}
					case PENDING -> {
						if (!veinStatus(ctx, digging)) {
							ctx.status("villager.status.mine.approach", digging.feet().getY());
						}
						return;
					}
					case WALK -> {
						moveToSpot(ctx, stand);
						return;
					}
					case WAIT -> {
						ctx.status("villager.status.zone_unloaded", zone.name);
						return;
					}
					case FAILED -> {
						// No way through: that block is left, the next ones may be reachable.
						tunnelCell = null;
						resetCell();
						if (!blocks.isEmpty()) {
							blocks.removeFirst();
						}
						continue;
					}
				}
			}
			while (!blocks.isEmpty() && !world.target(blocks.getFirst())) {
				blocks.removeFirst();
			}
			if (blocks.isEmpty()) {
				finishVein(ctx, plan, vein);
				return;
			}
			if (!atSpot(ctx, stand)) {
				moveToSpot(ctx, stand);
				ctx.status("villager.status.mine.going_to", ctx.villager.getBlockY(), stand.getY());
				return;
			}
			BlockPos head = blocks.getFirst();
			if (MineRules.reachable(stand, head, VEIN_REACH) && MineRules.exposed(world, head)) {
				MineRules.Verdict verdict = MineRules.digVein(world, head.getX(), head.getY(), head.getZ());
				if (verdict != MineRules.Verdict.DIG || world.decorated(head) || !ctx.ticking(head)) {
					blocks.removeFirst();
					continue;
				}
				if (budget < 1.0 || ctx.carriedFull()) {
					Movement.hold(ctx, head);
					veinJobStatus(ctx, blocks.size(), head);
					return;
				}
				Movement.hold(ctx, head);
				collect(ctx, plan, world, head);
				budget -= 1.0;
				blocks.removeFirst();
				veinJobStatus(ctx, blocks.size(), head);
				continue;
			}
			MineLayout.Cell next = approachCell(ctx, layout, stand, head);
			if (next == null) {
				blocks.removeFirst();
				continue;
			}
			tunnelCell = next;
			resetCell();
		}
	}

	/**
	 * Next approach tunnel cell toward {@code target}, or null when the tunnel may not go there. Tunnels start from a
	 * level only (at the height of its galleries, they never cut the floor or the ceiling of another tunnel), never
	 * through the staircase.
	 */
	private MineLayout.@Nullable Cell approachCell(WorkContext ctx, MineLayout layout, BlockPos stand, BlockPos target) {
		VillagerConfig.RealMining real = ctx.config.realMining;
		if (tunnelLength >= real.veinMaxOutside + TUNNEL_EXTRA || exitNode < 0 || exitNode >= layout.nodeCount()
				|| layout.nodeLevel(exitNode) == MineLayout.STAIRS_LEVEL) {
			return null;
		}
		BlockPos next = MineRules.approach(stand, target);
		if (next == null || layout.inStairColumns(next.getX(), next.getZ())
				|| MineRules.outside(next.getX(), next.getZ(), zone.bounds()) > real.veinMaxOutside || !ctx.ticking(next)) {
			return null;
		}
		return new MineLayout.Cell(next, MineLayout.TUNNEL_HEIGHT);
	}

	private void finishVein(WorkContext ctx, Mines.Plan plan, MineState.Vein vein) {
		plan.state().veins.remove(vein);
		GuildManager.markDirty();
		dropJob(ctx);
	}

	private boolean atSpot(WorkContext ctx, BlockPos spot) {
		return atStand(ctx, spot);
	}

	private static void moveToSpot(WorkContext ctx, BlockPos spot) {
		Movement.moveTo(ctx, spot, STAND_REACH, ctx.config.work.walkTimeoutSeconds);
	}

	private void veinJobStatus(WorkContext ctx, int left, BlockPos block) {
		boolean outside = !zone.contains(block);
		boolean wantedVein = job != null && job.vein() != null && job.vein().wanted;
		if (outside) {
			ctx.status("villager.status.mine.vein_outside", left, block.getY());
		} else if (wantedVein) {
			ctx.status("villager.status.mine.wanted_vein", left, block.getY());
		} else {
			ctx.status("villager.status.mine.vein", left, block.getY());
		}
	}

	/** Status while following the veins of a cell; false when there is none. */
	private boolean veinStatus(WorkContext ctx, MineLayout.Cell cell) {
		List<BlockPos> pending = floorStage == 1 && !floorVein.isEmpty() ? floorVein : inline;
		if (pending == null || pending.isEmpty()) {
			return false;
		}
		BlockPos block = pending.getFirst();
		if (!zone.contains(block)) {
			ctx.status("villager.status.mine.vein_outside", pending.size(), cell.feet().getY());
		} else if (wanted.isEmpty() || !new MineWorld(ctx.level, ctx.guild, wanted).wantedBlock(block)) {
			ctx.status("villager.status.mine.vein", pending.size(), cell.feet().getY());
		} else {
			ctx.status("villager.status.mine.wanted_vein", pending.size(), cell.feet().getY());
		}
		return true;
	}

	// ================================================================ jobs

	private boolean jobValid(WorkContext ctx, Mines.Plan plan) {
		MineJobs.Job current = job;
		if (current == null) {
			return false;
		}
		MineState state = plan.state();
		boolean valid;
		if (jobLayout != plan.layout()) {
			valid = false;
		} else {
			switch (current.type()) {
				case STAIRS -> {
					// In step with the shared progress (another miner may have dug on, a reset may have cleared it).
					valid = state.stairsDone < plan.stairsNeeded() && cursor < steps.length && steps[cursor] == state.stairsDone;
				}
				case WIDEN -> valid = cursor < cells.size();
				case SEGMENT -> {
					MineState.Level progress = state.level(current.levelY());
					valid = plan.levels().contains(current.levelY()) && (progress == null
							|| (!progress.done.contains(current.id()) && !progress.abandoned.contains(current.id())));
				}
				case VEIN -> valid = state.veins.contains(current.vein());
				default -> valid = false;
			}
		}
		if (valid && Mines.lease(leaseKey(ctx, current), ctx.record.uuid, ctx.now)) {
			return true;
		}
		dropJob(ctx);
		return false;
	}

	/** Takes the nearest open job nobody else holds. */
	private boolean pickJob(WorkContext ctx, Mines.Plan plan) {
		MineLayout layout = plan.layout();
		MineState state = plan.state();
		UUID me = ctx.record.uuid;
		List<MineJobs.Candidate> open = MineJobs.available(layout, state, plan.levels(), plan.stairsNeeded());
		Villager villager = ctx.villager;
		int from = layout.nearestNode(villager.getX(), villager.getY(), villager.getZ(), NEAR_ROUTE, n -> true);
		MineJobs.Candidate pick = MineJobs.nearest(layout, open, from < 0 ? MineLayout.ENTRANCE : from,
				candidate -> Mines.heldByOther(leaseKey(ctx, candidate), me, ctx.now));
		if (pick == null) {
			if (Mines.openDeeper(ctx, zone, plan)) {
				// The stairs go on to the next level: a job for this miner next step.
				ctx.status("villager.status.mine.planning", zone.name);
			} else if (open.isEmpty()) {
				ctx.status("villager.status.mine.no_work", zone.name);
			} else {
				ctx.status("villager.status.mine.all_taken", open.size(), zone.name);
			}
			return false;
		}
		if (!Mines.lease(leaseKey(ctx, pick.job()), me, ctx.now)) {
			return false;
		}
		start(plan, pick.job());
		return true;
	}

	private void start(Mines.Plan plan, MineJobs.Job picked) {
		MineLayout layout = plan.layout();
		MineState state = plan.state();
		job = picked;
		jobLayout = layout;
		cursor = 0;
		walkDest = -1;
		cluster = null;
		veinStand = null;
		tunnelCell = null;
		tunnelLength = 0;
		resetCell();
		List<MineLayout.Cell> list = new ArrayList<>();
		List<Integer> standList = new ArrayList<>();
		List<Integer> stepList = new ArrayList<>();
		switch (picked.type()) {
			case STAIRS -> {
				int needed = Math.min(plan.stairsNeeded(), layout.stairs().size());
				for (int k = state.stairsDone; k < needed; k++) {
					list.add(layout.stairs().get(k));
					standList.add(MineLayout.stairStand(k));
					stepList.add(k);
				}
			}
			case WIDEN -> {
				for (int k : MineJobs.widenSteps(layout, state, picked.id())) {
					for (MineLayout.Cell cell : layout.outer(k)) {
						list.add(cell);
						standList.add(MineLayout.stairNode(k));
						stepList.add(k);
					}
				}
			}
			case SEGMENT -> {
				MineLayout.Level level = layout.level(picked.levelY());
				MineLayout.Segment segment = level == null ? null : level.segment(picked.id());
				if (segment != null) {
					list.addAll(segment.cells());
					for (int stand : segment.stands()) {
						standList.add(stand);
						stepList.add(-1);
					}
				}
			}
			default -> {
			}
		}
		cells = list;
		stands = standList.stream().mapToInt(Integer::intValue).toArray();
		steps = stepList.stream().mapToInt(Integer::intValue).toArray();
	}

	private void finishJob(WorkContext ctx, Mines.Plan plan) {
		MineJobs.Job current = job;
		if (current != null && current.type() == MineJobs.Type.SEGMENT) {
			Mines.finishSegment(plan, current.levelY(), current.id(), false);
		}
		dropJob(ctx);
	}

	private void dropJob(WorkContext ctx) {
		Mines.release(ctx.record.uuid);
		job = null;
		jobLayout = null;
		cells = List.of();
		stands = new int[0];
		steps = new int[0];
		cursor = 0;
		cluster = null;
		veinStand = null;
		tunnelCell = null;
		tunnelLength = 0;
		resetCell();
	}

	private String leaseKey(WorkContext ctx, MineJobs.Job target) {
		return Mines.leaseKey(ctx.guild, zone, target.key());
	}

	// ================================================================ statuses

	private void workStatus(WorkContext ctx, Mines.Plan plan, MineLayout.Cell cell) {
		MineJobs.Job current = job;
		if (current == null) {
			return;
		}
		switch (current.type()) {
			case STAIRS -> ctx.status("villager.status.mine.stairs", cell.feet().getY(), plan.levels().isEmpty() ? cell.feet().getY()
					: plan.levels().getLast());
			case WIDEN -> ctx.status("villager.status.mine.widen", cell.feet().getY(), Math.min(cursor + 1, cells.size()), cells.size());
			case SEGMENT -> {
				MineLayout.Level level = plan.layout().level(current.levelY());
				MineLayout.Segment segment = level == null ? null : level.segment(current.id());
				if (segment == null) {
					return;
				}
				int done = Math.min(cursor + 1, segment.cells().size());
				String key = switch (segment.kind()) {
					case LANDING -> "villager.status.mine.landing";
					case MAIN -> "villager.status.mine.gallery_main";
					case BRANCH -> "villager.status.mine.gallery";
					case ROOM -> "villager.status.mine.quarry";
				};
				ctx.status(key, current.levelY(), done, segment.cells().size());
			}
			default -> {
			}
		}
	}

	private void goingStatus(WorkContext ctx, MineLayout.Cell cell) {
		int y = ctx.villager.getBlockY();
		if (cell.feet().getY() < y - 1) {
			ctx.status("villager.status.mine.going_down", zone.name, y, cell.feet().getY());
		} else {
			ctx.status("villager.status.mine.going_to", y, cell.feet().getY());
		}
	}

	// ================================================================ walking

	private static boolean atStand(WorkContext ctx, BlockPos stand) {
		return stand.equals(ctx.runtime.remoteTarget) || ctx.distSq(stand) <= STAND_REACH * STAND_REACH;
	}

	/** Nodes the miner may walk through: the fanion, the dug stairs, finished segments, any node that is open. */
	private IntPredicate walkable(WorkContext ctx, MineLayout layout, Mines.@Nullable Plan plan) {
		MineState state = zone.mine();
		MineWorld world = new MineWorld(ctx.level, ctx.guild);
		boolean current = plan != null && plan.layout() == layout;
		MineJobs.Job working = job;
		return node -> {
			if (node == MineLayout.ENTRANCE) {
				return true;
			}
			int level = layout.nodeLevel(node);
			if (current && level == MineLayout.STAIRS_LEVEL) {
				if (layout.nodeCell(node) < state.stairsDone) {
					return true;
				}
			} else if (current) {
				int segment = layout.nodeSegment(node);
				MineState.Level progress = state.level(level);
				if (progress != null && progress.done.contains(segment)) {
					return true;
				}
				if (working != null && working.type() == MineJobs.Type.SEGMENT && working.levelY() == level && working.id() == segment
						&& layout.nodeCell(node) < cursor) {
					return true;
				}
			}
			BlockPos feet = layout.node(node);
			return world.at(feet.getX(), feet.getY(), feet.getZ()).passable()
					&& world.at(feet.getX(), feet.getY() + 1, feet.getZ()).passable();
		};
	}

	/**
	 * Walks along the mine's walking graph toward node {@code dest}: waypoint a few nodes ahead (short vanilla paths),
	 * straight to the nearest node when off the route (out of an approach tunnel first). A watchdog teleports the miner
	 * to its destination when it makes no progress for a while (or lets it work from where it stands).
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
			exitNode = -1;
			List<Integer> path = layout.path(current, dest);
			waypoint = path.isEmpty() ? target : layout.node(path.get(Math.min(LOOKAHEAD, path.size()) - 1));
			remaining = path.size();
		} else {
			// Off the route (coming back from the deposit chest, out of an approach tunnel...): first to the nearest
			// open node, else to the start of the tunnel, else to the fanion.
			int near = layout.nearestNode(villager.getX(), villager.getY(), villager.getZ(), NEAR_ROUTE, walkable);
			if (near < 0 && exitNode >= 0 && exitNode < layout.nodeCount()) {
				near = exitNode;
			}
			waypoint = layout.node(near >= 0 ? near : MineLayout.ENTRANCE);
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
