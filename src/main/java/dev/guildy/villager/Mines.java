package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Real mines, zone side (the miner side is {@link RealMiner}): the plan of a zone ready to be worked (staircase
 * orientation and width chosen by scanning the terrain, levels every few blocks, cached layout), level progress and the
 * stairs going deeper, a blocked staircase, pending veins, transient reservations (leases) of the jobs shared by the
 * miners ({@link MineJobs}), the miners at work in each zone (capped), the menu description and the reset.
 */
final class Mines {
	/** First steps checked for buildings next to the staircase when choosing its orientation. */
	private static final int NEAR_STEPS = 8;
	/** Steps scanned per orientation (deeper obstacles are met while digging). */
	private static final int SCAN_STEPS = 64;
	private static final long LEASE_TICKS = 15 * 20L;
	/** A miner seen this recently works in the zone. */
	private static final long ACTIVE_TICKS = 3 * 20L;
	private static final int MAX_LAYOUTS = 32;
	private static final int MAX_LEASES = 2048;
	/** Orientation not chosen yet: chunks around the fanion not loaded. */
	private static final int NOT_READY = -2;

	private record Lease(UUID owner, long until) {
	}

	/** Where a miner of the zone was last seen working: its level (Integer.MIN_VALUE = stairs / surface). */
	private record Activity(long seen, int levelY) {
	}

	/**
	 * A mine ready to be worked this step: its layout, progress, the levels planned (highest first) and the steps the
	 * stairs need to reach the deepest one.
	 */
	record Plan(MineLayout layout, MineState state, List<Integer> levels, int stairsNeeded) {
	}

	/** Read-only view of a mine for the menus (null layout: not planned yet). */
	record View(@Nullable MineLayout layout, MineState state, List<Integer> levels, int stairsNeeded) {
	}

	private static final Map<String, Lease> LEASES = new HashMap<>();
	private static final Map<String, MineLayout> LAYOUTS = new LinkedHashMap<>(16, 0.75f, true);
	private static final Map<String, Map<UUID, Activity>> ACTIVE = new HashMap<>();

	private Mines() {
	}

	static void clear() {
		LEASES.clear();
		LAYOUTS.clear();
		ACTIVE.clear();
	}

	static MineLayout.Settings settings(VillagerConfig.RealMining real, int width) {
		return new MineLayout.Settings(real.stairSide, width, real.mainTunnelLength, real.branchSpacing, real.branchLength,
				real.roomWidth, real.roomLength, real.roomHeight, real.roomGap);
	}

	// ================================================================ plan

	/**
	 * The plan of a real mine for this step, or null (status set) when there is nothing to dig: every level finished
	 * and no deeper one, no way down, terrain not loaded yet.
	 */
	static @Nullable Plan plan(WorkContext ctx, WorkZone zone) {
		MineState state = zone.mine();
		if (state.format < MineState.FORMAT) {
			state.migrate();
			GuildManager.markDirty();
		}
		VillagerConfig.RealMining real = ctx.config.realMining;
		int side = MineLayout.clampSide(real.stairSide);
		int[] bounds = zone.bounds();
		if (state.origin == null || !state.origin.equals(zone.pos) || state.stairSide != side
				|| (state.orientation >= 0 && !MineLayout.fits(zone.pos, state.orientation, side, state.stairWidth, bounds))) {
			// New fanion position (or the staircase no longer fits the zone): plan everything again.
			boolean moved = state.origin == null || !state.origin.equals(zone.pos);
			state.resetPlan();
			if (moved) {
				state.rejected.clear();
			}
			state.origin = zone.pos;
			state.stairSide = side;
			GuildManager.markDirty();
		}
		if (state.exhausted) {
			exhaustedStatus(ctx, zone, state);
			return null;
		}
		boolean rooms = zone.objective == ZoneObjective.STONE;
		if (state.orientation < 0) {
			MineRules.LevelPlan first = levels(ctx.level, zone, state, real);
			int chosen = chooseOrientation(ctx, zone, state, bounds, rooms, real, first.isEmpty() ? null : first.levels().getFirst());
			if (chosen == NOT_READY) {
				ctx.status("villager.status.mine.planning", zone.name);
				return null;
			}
			if (chosen < 0) {
				state.exhausted = true;
				state.issue("villager.mine.issue.zone_small", zone.pos);
				GuildManager.markDirty();
				ctx.status("villager.status.mine.blocked", Lang.raw("villager.mine.issue.zone_small"));
				return null;
			}
			state.orientation = chosen;
			state.format = MineState.FORMAT;
			GuildManager.markDirty();
		}
		MineLayout layout = layout(ctx.level, ctx.guild, zone, state, real);
		String persisted = Integer.toHexString(layoutKey(ctx.guild, zone, state, real, ctx.level).hashCode());
		if (!persisted.equals(state.layoutKey)) {
			// Other galleries (zone size, objective, settings): the levels start over (dug cells are walked through).
			if (!state.layoutKey.isEmpty()) {
				state.resetLevels();
			}
			state.layoutKey = persisted;
			GuildManager.markDirty();
		}
		if (!state.veins.isEmpty() && state.veins.removeIf(vein -> layout.nodeAt(vein.stand) < 0
				&& (layout.level(vein.stand.getY()) == null || layout.nodeAt(vein.stand) < 0))) {
			// Seen from galleries that no longer exist (other layout).
			GuildManager.markDirty();
		}
		for (int attempt = 0; attempt < 4; attempt++) {
			MineRules.LevelPlan levels = levels(ctx.level, zone, state, real);
			if (levels.isEmpty()) {
				state.exhausted = true;
				GuildManager.markDirty();
				exhaustedStatus(ctx, zone, state);
				return null;
			}
			int needed = Math.min(layout.stepAt(levels.lowest()) + 1, layout.stairs().size());
			Plan plan = new Plan(layout, state, levels.levels(), needed);
			if (!finished(plan)) {
				return plan;
			}
			// Every level is done: the stairs go deeper, or the mine is over.
			OptionalInt deeper = real.autoNextLevel && state.stairsBlockedAt < 0
					? MineRules.deeper(levels.gridBottom(), spacing(zone, real), lowest(ctx.level)) : OptionalInt.empty();
			if (deeper.isEmpty() || layout.level(deeper.getAsInt()) == null) {
				state.exhausted = true;
				GuildManager.markDirty();
				Msg.guild(ctx.guild, "villager.mine.exhausted", zone.name);
				exhaustedStatus(ctx, zone, state);
				return null;
			}
			state.bottomY = deeper.getAsInt();
			GuildManager.markDirty();
			Msg.guild(ctx.guild, "villager.mine.deeper", zone.name, levels.gridBottom(), deeper.getAsInt());
		}
		return null;
	}

	private static void exhaustedStatus(WorkContext ctx, WorkZone zone, MineState state) {
		if (state.finishedLevels.isEmpty() && !state.issue.isEmpty()) {
			ctx.status("villager.status.mine.stuck", zone.name, Lang.raw(state.issue));
		} else {
			ctx.status("villager.status.mine.exhausted", zone.name);
		}
	}

	/**
	 * Nothing left in the plan: stairs dug as deep as planned (or blocked), every level finished (levels without any
	 * open work are marked finished on the way), no pending vein.
	 */
	private static boolean finished(Plan plan) {
		MineState state = plan.state();
		boolean stairsDone = state.stairsDone >= plan.stairsNeeded()
				|| (state.stairsBlockedAt >= 0 && state.stairsDone >= state.stairsBlockedAt);
		if (!stairsDone || !state.veins.isEmpty()) {
			return false;
		}
		for (int y : plan.levels()) {
			MineState.Level progress = state.level(y);
			if (progress != null && progress.finished) {
				continue;
			}
			if (!MineJobs.reached(plan.layout(), state, y)) {
				return false;
			}
			MineLayout.Level level = plan.layout().level(y);
			if (level != null && !MineJobs.levelFinished(level, progress)) {
				return false;
			}
			markFinished(state, y);
		}
		return true;
	}

	private static void markFinished(MineState state, int y) {
		state.levelOrCreate(y).finished = true;
		if (!state.finishedLevels.contains(y)) {
			state.finishedLevels.add(y);
		}
		GuildManager.markDirty();
	}

	/** Levels of the zone for now (see {@link MineRules#levels}). */
	static MineRules.LevelPlan levels(ServerLevel level, WorkZone zone, MineState state, VillagerConfig.RealMining real) {
		int fanionY = zone.pos.getY();
		int lo = lowest(level);
		int hi = MineRules.maxLevel(fanionY, real.minDepthBelowFanion);
		if (hi < lo) {
			// Fanion planted right above the bedrock: galleries at its own height.
			return new MineRules.LevelPlan(List.of(fanionY), fanionY);
		}
		List<Integer> extra = new ArrayList<>(2);
		if (state.levelY != null) {
			extra.add(state.levelY);
		}
		if (state.stairsBlockedAt >= 0) {
			int stop = fanionY - state.stairsBlockedAt;
			lo = Math.max(lo, stop);
			extra.add(stop);
		}
		int spacing = spacing(zone, real);
		int target = targetLevel(level, zone, real);
		int bottom = state.bottomY != null ? state.bottomY : target;
		return MineRules.levels(target, spacing, lo, hi, bottom, extra, minGap(zone, real));
	}

	/** Blocks between two levels (quarry rooms never meet the floor of the level above). */
	static int spacing(WorkZone zone, VillagerConfig.RealMining real) {
		return Math.max(real.levelSpacing, minGap(zone, real));
	}

	private static int minGap(WorkZone zone, VillagerConfig.RealMining real) {
		return (zone.objective == ZoneObjective.STONE ? Math.max(MineLayout.TUNNEL_HEIGHT, real.roomHeight) : MineLayout.STAIR_HEIGHT) + 1;
	}

	private static int lowest(ServerLevel level) {
		return MineRules.minLevel(level.getMinY());
	}

	/** Level wished for the zone (player setting or objective default), kept in the allowed range. */
	static int targetLevel(ServerLevel level, WorkZone zone, VillagerConfig.RealMining real) {
		int fanionY = zone.pos.getY();
		Integer wanted = zone.mine().targetY;
		int target = wanted != null ? wanted
				: MineRules.defaultTarget(zone.objective, fanionY, real.oresTargetY, real.mixedTargetY, real.stoneDepth);
		return MineRules.clampLevel(target, fanionY, level.getMinY(), real.minDepthBelowFanion);
	}

	private static String layoutKey(Guild guild, WorkZone zone, MineState state, VillagerConfig.RealMining real, ServerLevel level) {
		return guild.id + "|" + zone.id + "|" + zone.pos.asLong() + "|" + state.orientation + "|" + Arrays.toString(zone.bounds()) + "|"
				+ (zone.objective == ZoneObjective.STONE) + "|" + settings(real, state.stairWidth) + "|" + lowest(level);
	}

	/** Cached layout of a planned mine. */
	private static MineLayout layout(ServerLevel level, Guild guild, WorkZone zone, MineState state, VillagerConfig.RealMining real) {
		String key = layoutKey(guild, zone, state, real, level);
		MineLayout layout = LAYOUTS.get(key);
		if (layout == null) {
			layout = MineLayout.build(zone.pos, state.orientation, lowest(level), zone.bounds(), zone.objective == ZoneObjective.STONE,
					settings(real, state.stairWidth));
			if (LAYOUTS.size() >= MAX_LAYOUTS) {
				Iterator<String> it = LAYOUTS.keySet().iterator();
				it.next();
				it.remove();
			}
			LAYOUTS.put(key, layout);
		}
		return layout;
	}

	/**
	 * Best staircase of the fanion: wide first (then 1-wide when no wide one fits), scanning each spiral that fits the
	 * zone (not given up before) and keeping the one that goes deepest without obstacle, away from buildings. Sets the
	 * width. -1 when none fits, {@link #NOT_READY} when the terrain around the fanion is not loaded.
	 */
	private static int chooseOrientation(WorkContext ctx, WorkZone zone, MineState state, int[] bounds, boolean rooms,
			VillagerConfig.RealMining real, @Nullable Integer levelY) {
		BlockPos origin = zone.pos;
		int reach = MineLayout.footprintSide(real.stairSide, real.stairWidth) + 1;
		for (int dx : new int[]{-reach, reach}) {
			for (int dz : new int[]{-reach, reach}) {
				if (!ctx.level.isLoaded(origin.offset(dx, 0, dz))) {
					return NOT_READY;
				}
			}
		}
		MineWorld world = new MineWorld(ctx.level, ctx.guild);
		int lowest = Math.max(lowest(ctx.level), origin.getY() - SCAN_STEPS - 1);
		if (levelY != null) {
			lowest = Math.min(lowest, levelY);
		}
		for (int width = real.stairWidth; width >= 1; width--) {
			int best = -1;
			MineRules.Score bestScore = null;
			for (int orientation = 0; orientation < MineLayout.ORIENTATIONS; orientation++) {
				if (state.rejected.contains(orientation) || !MineLayout.fits(origin, orientation, real.stairSide, width, bounds)) {
					continue;
				}
				MineLayout candidate = MineLayout.build(origin, orientation, lowest, bounds, rooms, settings(real, width));
				MineRules.Score score = MineRules.score(world, candidate, NEAR_STEPS, SCAN_STEPS, levelY);
				if (bestScore == null || score.betterThan(bestScore)) {
					best = orientation;
					bestScore = score;
				}
			}
			if (best >= 0) {
				state.stairWidth = width;
				return best;
			}
		}
		return -1;
	}

	/**
	 * A miner has nothing to do while the stairs stand at the deepest planned level: the stairs go on to the next level
	 * below (no miner stays idle while the world goes deeper). True when a level was added.
	 */
	static boolean openDeeper(WorkContext ctx, WorkZone zone, Plan plan) {
		MineState state = plan.state();
		VillagerConfig.RealMining real = ctx.config.realMining;
		if (!real.autoNextLevel || state.stairsBlockedAt >= 0 || state.stairsDone < plan.stairsNeeded()) {
			return false;
		}
		MineRules.LevelPlan levels = levels(ctx.level, zone, state, real);
		if (levels.isEmpty()) {
			return false;
		}
		OptionalInt deeper = MineRules.deeper(levels.gridBottom(), spacing(zone, real), lowest(ctx.level));
		if (deeper.isEmpty() || plan.layout().level(deeper.getAsInt()) == null) {
			return false;
		}
		state.bottomY = deeper.getAsInt();
		GuildManager.markDirty();
		return true;
	}

	/** A segment is finished or given up: the level may be finished. */
	static void finishSegment(Plan plan, int levelY, int segment, boolean abandoned) {
		MineState state = plan.state();
		MineState.Level progress = state.levelOrCreate(levelY);
		List<Integer> list = abandoned ? progress.abandoned : progress.done;
		if (!list.contains(segment)) {
			list.add(segment);
		}
		MineLayout.Level level = plan.layout().level(levelY);
		if (level != null && MineJobs.levelFinished(level, progress)) {
			markFinished(state, levelY);
		}
		GuildManager.markDirty();
	}

	/**
	 * Stair step {@code step} cannot be dug: no level below it (galleries where the stairs stop); right under the
	 * surface, another staircase is tried (up to every orientation).
	 */
	static void stairsBlocked(WorkContext ctx, WorkZone zone, Plan plan, int step, String issue, BlockPos pos) {
		MineState state = plan.state();
		state.issue(issue, pos);
		int fanionY = zone.pos.getY();
		state.stairsBlockedAt = state.stairsBlockedAt < 0 ? step : Math.min(state.stairsBlockedAt, step);
		int reachable = fanionY - state.stairsBlockedAt;
		VillagerConfig.RealMining real = ctx.config.realMining;
		if (reachable <= MineRules.maxLevel(fanionY, real.minDepthBelowFanion)) {
			Msg.guild(ctx.guild, "villager.mine.stairs_blocked", zone.name, Lang.raw(issue), pos.getY(), reachable);
		} else if (state.rejected.size() + 1 < MineLayout.ORIENTATIONS) {
			int rejected = state.orientation;
			List<Integer> previous = new ArrayList<>(state.rejected);
			BlockPos origin = state.origin;
			int side = state.stairSide;
			state.resetPlan();
			state.rejected.addAll(previous);
			state.rejected.add(rejected);
			state.origin = origin;
			state.stairSide = side;
			Msg.guild(ctx.guild, "villager.mine.stairs_retry", zone.name, Lang.raw(issue));
		} else {
			state.exhausted = true;
			Msg.guild(ctx.guild, "villager.mine.stairs_stuck", zone.name, Lang.raw(issue));
		}
		GuildManager.markDirty();
	}

	/**
	 * A vein seen from the tunnels that the miner could not reach: kept for later (any miner, approach tunnel). Not
	 * twice for the same vein ({@code cluster}: its blocks known so far).
	 */
	static void addVein(MineState state, BlockPos seed, BlockPos stand, boolean wanted, List<BlockPos> cluster) {
		for (MineState.Vein vein : state.veins) {
			if (vein.seed.equals(seed) || cluster.contains(vein.seed)) {
				return;
			}
		}
		if (state.veins.size() >= MineState.MAX_VEINS) {
			return;
		}
		state.veins.add(new MineState.Vein(seed, stand, wanted));
		GuildManager.markDirty();
	}

	static String issueKey(MineRules.Verdict verdict) {
		return "villager.mine.issue." + verdict.name().toLowerCase(Locale.ROOT);
	}

	// ================================================================ leases

	static String leaseKey(Guild guild, WorkZone zone, String part) {
		return guild.id + ":" + zone.id + ":" + part;
	}

	/** Takes or renews a lease; false when another villager holds it. */
	static boolean lease(String key, UUID owner, long now) {
		Lease lease = LEASES.get(key);
		if (lease != null && lease.until() >= now && !lease.owner().equals(owner)) {
			return false;
		}
		if (LEASES.size() >= MAX_LEASES) {
			LEASES.values().removeIf(old -> old.until() < now);
		}
		LEASES.put(key, new Lease(owner, now + LEASE_TICKS));
		return true;
	}

	/** Another villager works on that lease. */
	static boolean heldByOther(String key, UUID me, long now) {
		Lease lease = LEASES.get(key);
		return lease != null && lease.until() >= now && !lease.owner().equals(me);
	}

	static @Nullable UUID holder(String key, long now) {
		Lease lease = LEASES.get(key);
		return lease == null || lease.until() < now ? null : lease.owner();
	}

	static void release(UUID owner) {
		LEASES.values().removeIf(lease -> lease.owner().equals(owner));
	}

	// ================================================================ miners at work

	private static String zoneKey(Guild guild, WorkZone zone) {
		return guild.id + ":" + zone.id;
	}

	/**
	 * The miner works in the zone this step ({@code levelY}: where, {@link MineLayout#STAIRS_LEVEL} for the stairs /
	 * the surface). False when the zone already has {@code max} other miners at work.
	 */
	static boolean admit(Guild guild, WorkZone zone, UUID miner, long now, int max, int levelY) {
		Map<UUID, Activity> miners = ACTIVE.computeIfAbsent(zoneKey(guild, zone), key -> new HashMap<>());
		miners.values().removeIf(activity -> now - activity.seen() > ACTIVE_TICKS);
		if (!miners.containsKey(miner) && miners.size() >= max) {
			return false;
		}
		miners.put(miner, new Activity(now, levelY));
		return true;
	}

	/** The miner left the zone (task changed, mine changed). */
	static void dismiss(Guild guild, WorkZone zone, UUID miner) {
		Map<UUID, Activity> miners = ACTIVE.get(zoneKey(guild, zone));
		if (miners != null) {
			miners.remove(miner);
		}
	}

	/** Miners at work in the zone right now and their level. */
	static Map<UUID, Integer> activeMiners(Guild guild, WorkZone zone, long now) {
		Map<UUID, Integer> result = new LinkedHashMap<>();
		Map<UUID, Activity> miners = ACTIVE.get(zoneKey(guild, zone));
		if (miners != null) {
			for (Map.Entry<UUID, Activity> entry : miners.entrySet()) {
				if (now - entry.getValue().seen() <= ACTIVE_TICKS) {
					result.put(entry.getKey(), entry.getValue().levelY());
				}
			}
		}
		return result;
	}

	/** Zone menu: a real mine takes at most {@code maxMinersPerZone} miners (message to the player when refused). */
	static boolean refuseAssign(ServerPlayer player, Guild guild, WorkZone zone, VillagerRecord record) {
		if (!zone.isRealMine() || Zones.assignedZone(guild, record) == zone) {
			return false;
		}
		int max = VillagerModule.config().realMining.maxMinersPerZone;
		int count = Zones.assignedCount(guild, zone);
		if (count < max) {
			return false;
		}
		Msg.send(player, "villager.mine.error.full", zone.name, count, max);
		GuiUtil.errorSound(player);
		return true;
	}

	// ================================================================ menus

	/** The mine as the miners see it, for the menus (never changes the progress). */
	static View view(@Nullable ServerLevel level, Guild guild, WorkZone zone) {
		MineState state = zone.mine();
		VillagerConfig.RealMining real = VillagerModule.config().realMining;
		if (level == null || state.orientation < 0 || state.origin == null || !state.origin.equals(zone.pos)) {
			return new View(null, state, List.of(), 0);
		}
		MineLayout layout = layout(level, guild, zone, state, real);
		MineRules.LevelPlan levels = levels(level, zone, state, real);
		if (levels.isEmpty()) {
			return new View(layout, state, List.of(), 0);
		}
		int needed = Math.min(layout.stepAt(levels.lowest()) + 1, layout.stairs().size());
		return new View(layout, state, levels.levels(), needed);
	}

	/** Mine description lines of the zone menu (state, levels, miners, statistics, last problem). */
	static List<Component> describe(@Nullable ServerLevel level, Guild guild, WorkZone zone, long now) {
		List<Component> lines = new ArrayList<>();
		MineState state = zone.mine();
		View view = view(level, guild, zone);
		VillagerConfig.RealMining real = VillagerModule.config().realMining;
		int fanionY = zone.pos.getY();
		MineLayout layout = view.layout();
		if (state.exhausted) {
			lines.add(Lang.get(state.finishedLevels.isEmpty() ? "villager.mine.info.stuck" : "villager.mine.info.exhausted"));
		} else if (layout == null || view.levels().isEmpty()) {
			lines.add(Lang.get("villager.mine.info.not_started"));
		} else if (state.stairsDone < view.stairsNeeded() && state.stairsBlockedAt < 0) {
			lines.add(Lang.get("villager.mine.info.stairs", state.stairsDone, view.stairsNeeded(), fanionY - 1 - state.stairsDone,
					view.levels().getLast()));
		} else {
			lines.add(Lang.get("villager.mine.info.working"));
		}
		if (layout != null && !view.levels().isEmpty()) {
			int open = 0;
			int finished = 0;
			for (int y : view.levels()) {
				MineState.Level progress = state.level(y);
				if (progress != null && progress.finished) {
					finished++;
				} else if (MineJobs.reached(layout, state, y)) {
					open++;
				}
			}
			lines.add(Lang.get("villager.mine.info.levels_summary", open, finished, view.levels().size(), view.levels().getFirst(),
					view.levels().getLast()));
		}
		lines.add(Lang.get("villager.mine.info.miners", activeMiners(guild, zone, now).size(), real.maxMinersPerZone));
		lines.add(state.deepestY == null ? Lang.get("villager.mine.info.depth_none") : Lang.get("villager.mine.info.depth", state.deepestY));
		lines.add(Lang.get("villager.mine.info.stats", state.blocksMined, state.oresFound));
		if (state.wantedFound > 0) {
			lines.add(Lang.get("villager.mine.info.wanted", state.wantedFound));
		}
		if (!state.veins.isEmpty()) {
			lines.add(Lang.get("villager.mine.info.veins", state.veins.size()));
		}
		if (!state.issue.isEmpty()) {
			BlockPos pos = state.issuePos;
			lines.add(pos == null ? Lang.get("villager.mine.info.issue", Lang.raw(state.issue))
					: Lang.get("villager.mine.info.issue_at", Lang.raw(state.issue), pos.getX(), pos.getY(), pos.getZ()));
		}
		return lines;
	}

	/** Target level shown in the menu (null level: unknown dimension, the raw wish or default is shown). */
	static int shownTarget(@Nullable ServerLevel level, WorkZone zone) {
		VillagerConfig.RealMining real = VillagerModule.config().realMining;
		if (level != null) {
			return targetLevel(level, zone, real);
		}
		Integer wanted = zone.mine().targetY;
		return wanted != null ? wanted
				: MineRules.defaultTarget(zone.objective, zone.pos.getY(), real.oresTargetY, real.mixedTargetY, real.stoneDepth);
	}

	/** Leases and miners of a deleted / reset zone are dropped with the runtime of its miners (they restart). */
	static void forgetZone(Guild guild, WorkZone zone) {
		String leases = guild.id + ":" + zone.id + ":";
		LEASES.keySet().removeIf(key -> key.startsWith(leases));
		String layouts = guild.id + "|" + zone.id + "|";
		LAYOUTS.keySet().removeIf(key -> key.startsWith(layouts));
		ACTIVE.remove(zoneKey(guild, zone));
	}
}
