package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Real mines, zone side (the miner side is {@link RealMiner}): the plan of a zone ready to be worked (staircase
 * orientation chosen by scanning the terrain, gallery level, layout), level changes, a blocked staircase, transient
 * reservations (leases) of work segments and vein blocks between miners, the menu description and the reset.
 */
final class Mines {
	/** First steps checked for buildings next to the staircase when choosing its orientation. */
	private static final int NEAR_STEPS = 8;
	/** Steps scanned per orientation (deeper obstacles are met while digging). */
	private static final int SCAN_STEPS = 64;
	private static final long LEASE_TICKS = 15 * 20L;
	private static final int MAX_LAYOUTS = 64;
	private static final int MAX_LEASES = 512;
	/** Orientation not chosen yet: chunks around the fanion not loaded. */
	private static final int NOT_READY = -2;

	private record Lease(UUID owner, long until) {
	}

	/** A mine ready to be worked this step. */
	record Plan(MineLayout layout, MineState state, int stairsNeeded) {
		int levelY() {
			return layout.levelY();
		}
	}

	private static final Map<String, Lease> LEASES = new HashMap<>();
	private static final Map<String, MineLayout> LAYOUTS = new LinkedHashMap<>();

	private Mines() {
	}

	static void clear() {
		LEASES.clear();
		LAYOUTS.clear();
	}

	static MineLayout.Settings settings(VillagerConfig.RealMining real) {
		return new MineLayout.Settings(real.stairSide, real.mainTunnelLength, real.branchSpacing, real.branchLength, real.roomWidth,
				real.roomLength, real.roomHeight, real.roomGap);
	}

	// ================================================================ plan

	/**
	 * The plan of a real mine for this step, or null (status set) when there is nothing to dig: no level left, no way
	 * down, terrain not loaded yet.
	 */
	static @Nullable Plan plan(WorkContext ctx, WorkZone zone) {
		MineState state = zone.mine();
		VillagerConfig.RealMining real = ctx.config.realMining;
		int side = MineLayout.clampSide(real.stairSide);
		int[] bounds = zone.bounds();
		if (state.origin == null || !state.origin.equals(zone.pos) || state.stairSide != side
				|| (state.orientation >= 0 && !MineLayout.fits(zone.pos, state.orientation, side, bounds))) {
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
		if (state.levelY == null) {
			OptionalInt next = state.exhausted ? OptionalInt.empty() : nextLevel(ctx.level, zone, state, real);
			if (next.isEmpty()) {
				if (!state.exhausted) {
					state.exhausted = true;
					GuildManager.markDirty();
				}
				if (state.finishedLevels.isEmpty() && !state.issue.isEmpty()) {
					ctx.status("villager.status.mine.stuck", zone.name, Lang.raw(state.issue));
				} else {
					ctx.status("villager.status.mine.exhausted", zone.name);
				}
				return null;
			}
			state.levelY = next.getAsInt();
			GuildManager.markDirty();
		}
		int levelY = state.levelY;
		boolean rooms = zone.objective == ZoneObjective.STONE;
		MineLayout.Settings settings = settings(real);
		if (state.orientation < 0) {
			int chosen = chooseOrientation(ctx, zone, state, levelY, bounds, rooms, settings);
			if (chosen == NOT_READY) {
				ctx.status("villager.status.mine.planning", zone.name);
				return null;
			}
			if (chosen < 0) {
				state.exhausted = true;
				state.levelY = null;
				state.issue("villager.mine.issue.zone_small", zone.pos);
				GuildManager.markDirty();
				ctx.status("villager.status.mine.blocked", Lang.raw("villager.mine.issue.zone_small"));
				return null;
			}
			state.orientation = chosen;
			GuildManager.markDirty();
		}
		String key = ctx.guild.id + "|" + zone.id + "|" + zone.pos.asLong() + "|" + state.orientation + "|" + levelY + "|"
				+ Arrays.toString(bounds) + "|" + rooms + "|" + settings;
		MineLayout layout = LAYOUTS.get(key);
		if (layout == null) {
			layout = MineLayout.build(zone.pos, state.orientation, levelY, bounds, rooms, settings);
			if (LAYOUTS.size() >= MAX_LAYOUTS) {
				Iterator<String> it = LAYOUTS.keySet().iterator();
				it.next();
				it.remove();
			}
			LAYOUTS.put(key, layout);
		}
		String persisted = Integer.toHexString(key.hashCode());
		if (!persisted.equals(state.layoutKey)) {
			// Other galleries (level, zone size, objective, settings): their progress starts over (dug cells are skipped).
			state.layoutKey = persisted;
			state.done.clear();
			state.abandoned.clear();
			GuildManager.markDirty();
		}
		return new Plan(layout, state, zone.pos.getY() - levelY);
	}

	/** Level wished for the zone (player setting or objective default), kept in the allowed range. */
	static int targetLevel(ServerLevel level, WorkZone zone, VillagerConfig.RealMining real) {
		int fanionY = zone.pos.getY();
		Integer wanted = zone.mine().targetY;
		int target = wanted != null ? wanted
				: MineRules.defaultTarget(zone.objective, fanionY, real.oresTargetY, real.mixedTargetY, real.stoneDepth);
		return MineRules.clampLevel(target, fanionY, level.getMinY(), real.minDepthBelowFanion);
	}

	/** Next gallery level not finished yet, reachable by the stairs. */
	static OptionalInt nextLevel(ServerLevel level, WorkZone zone, MineState state, VillagerConfig.RealMining real) {
		int fanionY = zone.pos.getY();
		int lo = MineRules.minLevel(level.getMinY());
		int hi = MineRules.maxLevel(fanionY, real.minDepthBelowFanion);
		if (hi < lo) {
			// Fanion planted right above the bedrock: galleries at its own height.
			lo = fanionY;
			hi = fanionY;
		}
		lo = Math.max(lo, MineRules.lowestReachable(fanionY, state.stairsBlockedAt));
		int target = targetLevel(level, zone, real);
		if (!real.autoNextLevel) {
			int only = Math.max(lo, Math.min(hi, target));
			return lo <= hi && !state.finishedLevels.contains(only) ? OptionalInt.of(only) : OptionalInt.empty();
		}
		return MineRules.nextLevel(target, state.finishedLevels, real.levelSpacing, lo, hi);
	}

	/**
	 * Best staircase orientation of the fanion: scans each spiral that fits the zone (not given up before) and keeps the
	 * one that goes deepest without obstacle, away from buildings. -1 when none fits, {@link #NOT_READY} when the
	 * terrain around the fanion is not loaded.
	 */
	private static int chooseOrientation(WorkContext ctx, WorkZone zone, MineState state, int levelY, int[] bounds, boolean rooms,
			MineLayout.Settings settings) {
		BlockPos origin = zone.pos;
		int reach = MineLayout.clampSide(settings.stairSide()) + 1;
		for (int dx : new int[]{-reach, reach}) {
			for (int dz : new int[]{-reach, reach}) {
				if (!ctx.level.isLoaded(origin.offset(dx, 0, dz))) {
					return NOT_READY;
				}
			}
		}
		MineWorld world = new MineWorld(ctx.level, ctx.guild);
		int best = -1;
		MineRules.Score bestScore = null;
		for (int orientation = 0; orientation < MineLayout.ORIENTATIONS; orientation++) {
			if (state.rejected.contains(orientation) || !MineLayout.fits(origin, orientation, settings.stairSide(), bounds)) {
				continue;
			}
			MineLayout candidate = MineLayout.build(origin, orientation, levelY, bounds, rooms, settings);
			MineRules.Score score = MineRules.score(world, candidate, NEAR_STEPS, SCAN_STEPS);
			if (bestScore == null || score.betterThan(bestScore)) {
				best = orientation;
				bestScore = score;
			}
		}
		return best;
	}

	/** Every gallery of the level is finished (or given up): next level, or the mine is exhausted. */
	static void finishLevel(WorkContext ctx, WorkZone zone, Plan plan) {
		MineState state = plan.state();
		int finished = plan.levelY();
		if (!state.finishedLevels.contains(finished)) {
			state.finishedLevels.add(finished);
		}
		state.levelY = null;
		state.layoutKey = "";
		state.done.clear();
		state.abandoned.clear();
		OptionalInt next = nextLevel(ctx.level, zone, state, ctx.config.realMining);
		if (next.isPresent()) {
			state.levelY = next.getAsInt();
			Msg.guild(ctx.guild, "villager.mine.level_next", zone.name, finished, next.getAsInt());
		} else {
			state.exhausted = true;
			Msg.guild(ctx.guild, "villager.mine.exhausted", zone.name);
		}
		GuildManager.markDirty();
	}

	/**
	 * Stair step {@code step} cannot be dug: galleries as deep as the stairs go; right under the surface, another
	 * staircase is tried (up to every orientation).
	 */
	static void stairsBlocked(WorkContext ctx, WorkZone zone, Plan plan, int step, String issue, BlockPos pos) {
		MineState state = plan.state();
		state.issue(issue, pos);
		int fanionY = zone.pos.getY();
		state.stairsBlockedAt = state.stairsBlockedAt < 0 ? step : Math.min(state.stairsBlockedAt, step);
		int reachable = fanionY - state.stairsBlockedAt;
		VillagerConfig.RealMining real = ctx.config.realMining;
		if (reachable <= MineRules.maxLevel(fanionY, real.minDepthBelowFanion)) {
			state.levelY = reachable;
			state.layoutKey = "";
			state.done.clear();
			state.abandoned.clear();
			Msg.guild(ctx.guild, "villager.mine.stairs_blocked", zone.name, Lang.raw(issue), pos.getY(), reachable);
		} else if (state.rejected.size() + 1 < MineLayout.ORIENTATIONS) {
			state.rejected.add(state.orientation);
			state.orientation = -1;
			state.stairsDone = 0;
			state.stairsBlockedAt = -1;
			state.levelY = null;
			state.layoutKey = "";
			state.done.clear();
			state.abandoned.clear();
			Msg.guild(ctx.guild, "villager.mine.stairs_retry", zone.name, Lang.raw(issue));
		} else {
			state.levelY = null;
			state.exhausted = true;
			Msg.guild(ctx.guild, "villager.mine.stairs_stuck", zone.name, Lang.raw(issue));
		}
		GuildManager.markDirty();
	}

	static String issueKey(MineRules.Verdict verdict) {
		return "villager.mine.issue." + verdict.name().toLowerCase(java.util.Locale.ROOT);
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

	static @Nullable UUID holder(String key, long now) {
		Lease lease = LEASES.get(key);
		return lease == null || lease.until() < now ? null : lease.owner();
	}

	static void release(UUID owner) {
		LEASES.values().removeIf(lease -> lease.owner().equals(owner));
	}

	// ================================================================ zone settings

	/** Mine description lines of the zone menu (mode, target, progress, statistics, last problem). */
	static List<Component> describe(@Nullable ServerLevel level, WorkZone zone) {
		List<Component> lines = new ArrayList<>();
		MineState state = zone.mine();
		VillagerConfig.RealMining real = VillagerModule.config().realMining;
		int fanionY = zone.pos.getY();
		if (state.exhausted) {
			lines.add(Lang.get(state.finishedLevels.isEmpty() ? "villager.mine.info.stuck" : "villager.mine.info.exhausted"));
		} else if (state.orientation < 0 || state.levelY == null) {
			lines.add(Lang.get("villager.mine.info.not_started"));
		} else {
			int levelY = state.levelY;
			int needed = fanionY - levelY;
			if (state.stairsDone < needed) {
				lines.add(Lang.get("villager.mine.info.stairs", state.stairsDone, needed, fanionY - 1 - state.stairsDone, levelY));
			} else {
				MineLayout layout = MineLayout.build(zone.pos, state.orientation, levelY, zone.bounds(),
						zone.objective == ZoneObjective.STONE, settings(real));
				int total = layout.segments().size();
				lines.add(Lang.get("villager.mine.info.galleries", Math.min(total, state.done.size()), total, levelY));
				if (!state.abandoned.isEmpty()) {
					lines.add(Lang.get("villager.mine.info.abandoned", state.abandoned.size()));
				}
			}
		}
		lines.add(state.deepestY == null ? Lang.get("villager.mine.info.depth_none") : Lang.get("villager.mine.info.depth", state.deepestY));
		lines.add(Lang.get("villager.mine.info.stats", state.blocksMined, state.oresFound));
		if (!state.finishedLevels.isEmpty()) {
			lines.add(Lang.get("villager.mine.info.levels", state.finishedLevels.size()));
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

	/** Leases of a deleted / reset zone are dropped with the runtime of its miners (they restart). */
	static void forgetZone(Guild guild, WorkZone zone) {
		String leases = guild.id + ":" + zone.id + ":";
		LEASES.keySet().removeIf(key -> key.startsWith(leases));
		String layouts = guild.id + "|" + zone.id + "|";
		LAYOUTS.keySet().removeIf(key -> key.startsWith(layouts));
	}
}
