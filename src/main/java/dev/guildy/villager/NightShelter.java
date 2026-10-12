package dev.guildy.villager;

import dev.guildy.core.Hooks;
import dev.guildy.core.Lifecycle;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Workers get home safely ({@code night} in villagers.json5). Why they used to die: they worked until the vanilla rest
 * time (12000), one hour of game time before the horde, then walked home at the slow vanilla pace from zones up to 192
 * blocks away; the vanilla rest behaviour gives up a bed it cannot reach for a minute (the villager loses its bed; a
 * stale "cannot reach" mark left by an old failed walk does it at once) and only looks for a new one within 48 blocks;
 * and horde mobs that spotted them hunted them down even out of sight (see {@code mobs.targetVillagers} in
 * waves.json5). Now:
 * <ul>
 * <li>{@code homeTime}: workers stop and walk to their bed (a free bed of the territory is claimed for a worker without
 * one; else the reception, else the heart) with a speed boost; stuck or too slow: teleported next to it; once there they
 * stay (no stroll) until the vanilla rest puts them to bed;</li>
 * <li>{@code shelterTime} (nightfall) and the start of a horde: a worker still outside a safe place is teleported next to
 * its bed (in the bed's room when possible), else to the next spot.</li>
 * </ul>
 * Guards (they stay out) and villagers without a task (players may keep them in trading halls) are not concerned.
 * While this runs the task is paused, it resumes in the morning.
 */
final class NightShelter {
	/** At most one nightfall / horde teleport per villager in this delay (a panicking villager is walked back instead). */
	private static final long TELEPORT_COOLDOWN_TICKS = 60 * 20L;
	/** The chosen spot is looked up again after this delay. */
	private static final long SPOT_REFRESH_TICKS = 10 * 20L;
	private static final int SPEED_TICKS = 60;
	/** Height difference still counted as "near" a safe spot. */
	private static final double SAFE_DY = 3.0;
	private static final Map<UUID, State> STATES = new HashMap<>();

	private NightShelter() {
	}

	enum Kind {
		BED,
		RECEPTION,
		HEART
	}

	/** Where a worker spends the night. */
	record Spot(BlockPos pos, Kind kind) {
	}

	/** Transient state of a worker during the evening / night / horde. */
	private static final class State {
		@Nullable Spot spot;
		long spotAt = Long.MIN_VALUE / 2;
		long lastTeleport = Long.MIN_VALUE / 2;
		// walk home
		@Nullable BlockPos walkTarget;
		long walkStart;
		long lastProgress;
		@Nullable Vec3 lastPos;
	}

	static void init() {
		GuildEvents.WAVE_STARTED.add(NightShelter::onHordeStarted);
		Lifecycle.STARTED.add(server -> STATES.clear());
		Lifecycle.STOPPING.add(server -> STATES.clear());
	}

	/** A horde starts: its workers outside are sheltered at their next step (≤ 1 s), whatever their last teleport. */
	private static void onHordeStarted(Guild guild) {
		for (UUID id : guild.villagers.members.keySet()) {
			State state = STATES.get(id);
			if (state != null) {
				state.lastTeleport = Long.MIN_VALUE / 2;
				state.spot = null;
			}
		}
	}

	/**
	 * Evening, night or horde: the worker goes home (the task is paused). Called by {@link TaskEngine} each step.
	 *
	 * @return true when this step was handled here (the task does not run)
	 */
	static boolean step(WorkContext ctx) {
		UUID id = ctx.record.uuid;
		Task task = ctx.record.task;
		if (task == Task.GUARD || task == Task.IDLE) {
			STATES.remove(id);
			return false;
		}
		NightConfig cfg = ctx.config.night;
		boolean horde = Hooks.isWaveActive.test(ctx.guild);
		NightRules.Phase phase = NightRules.phase(ctx.level.getOverworldClockTime(), cfg.homeTime, cfg.shelterTime);
		if (phase == NightRules.Phase.DAY && !(horde && cfg.shelterOnHorde)) {
			// Morning (or settings changed): the task resumes, TaskEngine restarts its movement timers.
			STATES.remove(id);
			return false;
		}
		Villager villager = ctx.villager;
		if (villager.isTrading() || villager.isPassenger()) {
			return false;
		}
		State state = STATES.get(id);
		if (state == null) {
			state = new State();
			STATES.put(id, state);
			ctx.runtime.paused = true;
			Movement.stop(ctx);
		}
		if (villager.isSleeping()) {
			ctx.status("villager.status.sleeping");
			return true;
		}
		Spot spot = spot(ctx, state, cfg);
		if (spot == null) {
			// No bed, no reception, heart unloaded: the vanilla behaviour takes over.
			ctx.status("villager.status.night.no_shelter");
			return true;
		}
		if (isSafe(ctx, spot, cfg)) {
			state.walkTarget = null;
			if (spot.kind() == Kind.BED) {
				// A stale "cannot reach" mark (an old failed work walk) would make the vanilla rest give the bed up.
				villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
			}
			// At its own bed after the vanilla rest time: the vanilla brain puts it to bed. Elsewhere: it stays there.
			boolean vanillaBed = spot.kind() == Kind.BED && villager.getBrain().isActive(Activity.REST);
			if (!vanillaBed) {
				Movement.hold(ctx, null);
			}
			ctx.status(horde ? "villager.status.wave" : vanillaBed ? "villager.status.resting" : "villager.status.night.home");
			return true;
		}
		if (NightRules.mayTeleport(phase, horde, cfg.shelterOnHorde) && ctx.now - state.lastTeleport >= TELEPORT_COOLDOWN_TICKS) {
			state.lastTeleport = ctx.now;
			if (shelter(ctx, state, spot)) {
				return true;
			}
		}
		walk(ctx, state, spot, cfg);
		return true;
	}

	// ================================================================ where to

	private static @Nullable Spot spot(WorkContext ctx, State state, NightConfig cfg) {
		Spot spot = state.spot;
		if (spot != null && ctx.now - state.spotAt < SPOT_REFRESH_TICKS && (spot.kind() != Kind.BED || GuardRetreat.isBed(ctx.level, spot.pos()))) {
			return spot;
		}
		state.spotAt = ctx.now;
		state.spot = findSpot(ctx, cfg);
		return state.spot;
	}

	/** Its own bed; else a free bed of the territory (claimed); else the reception; else the heart (loaded only). */
	private static @Nullable Spot findSpot(WorkContext ctx, NightConfig cfg) {
		BlockPos home = GuardRetreat.home(ctx);
		if (home != null) {
			return new Spot(home, Kind.BED);
		}
		if (cfg.claimFreeBeds && homeLost(ctx)) {
			BlockPos bed = claimFreeBed(ctx);
			if (bed != null) {
				return new Spot(bed, Kind.BED);
			}
		}
		BlockPos reception = GuardRetreat.reception(ctx.guild);
		if (reception != null && ctx.loaded(reception)) {
			return new Spot(reception, Kind.RECEPTION);
		}
		BlockPos heart = ctx.guild.heart;
		if (heart != null && ctx.loaded(heart)) {
			return new Spot(heart, Kind.HEART);
		}
		return null;
	}

	/** No bed remembered, or the remembered one is loaded and not a bed anymore (an unloaded bed may still exist). */
	private static boolean homeLost(WorkContext ctx) {
		Optional<GlobalPos> memory = ctx.villager.getBrain().getMemory(MemoryModuleType.HOME);
		if (memory.isEmpty()) {
			return true;
		}
		GlobalPos home = memory.get();
		if (!home.dimension().equals(ctx.level.dimension())) {
			return true;
		}
		if (ctx.loaded(home.pos()) && !GuardRetreat.isBed(ctx.level, home.pos())) {
			ctx.villager.getBrain().eraseMemory(MemoryModuleType.HOME);
			return true;
		}
		return false;
	}

	/**
	 * Nearest free bed of the loaded territory (no distance limit, unlike the vanilla search), claimed like vanilla does
	 * (point of interest ticket + HOME memory + happy particles). Null when there is none.
	 */
	private static @Nullable BlockPos claimFreeBed(WorkContext ctx) {
		ServerLevel level = ctx.level;
		PoiManager poi = level.getPoiManager();
		Villager villager = ctx.villager;
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (long key : ctx.guild.claims()) {
			int cx = ChunkKey.x(key);
			int cz = ChunkKey.z(key);
			if (!level.hasChunk(cx, cz)) {
				continue;
			}
			List<BlockPos> beds = poi.getInChunk(type -> type.is(PoiTypes.HOME), new ChunkPos(cx, cz), PoiManager.Occupancy.HAS_SPACE)
					.map(PoiRecord::getPos).toList();
			for (BlockPos bed : beds) {
				double dist = villager.distanceToSqr(Vec3.atCenterOf(bed));
				if (dist < bestDist && freeBed(level, bed)) {
					bestDist = dist;
					best = bed;
				}
			}
		}
		if (best == null) {
			return null;
		}
		BlockPos target = best;
		Optional<BlockPos> taken = poi.take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(target), target, 1);
		if (taken.isEmpty()) {
			return null;
		}
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), target));
		level.broadcastEntityEvent(villager, (byte) 14);
		return target;
	}

	private static boolean freeBed(ServerLevel level, BlockPos pos) {
		if (!GuardRetreat.isBed(level, pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !state.hasProperty(AbstractBedBlock.OCCUPIED) || !state.getValue(AbstractBedBlock.OCCUPIED);
	}

	/**
	 * Safe: next to its own bed; without a bed of its own, next to the chosen spot, the reception or any bed of the
	 * territory.
	 */
	private static boolean isSafe(WorkContext ctx, Spot spot, NightConfig cfg) {
		double radius = cfg.safeRadius;
		BlockPos home = GuardRetreat.home(ctx);
		if (home != null) {
			return near(ctx, home, radius);
		}
		if (near(ctx, spot.pos(), radius)) {
			return true;
		}
		BlockPos reception = GuardRetreat.reception(ctx.guild);
		if (reception != null && near(ctx, reception, radius + 2)) {
			return true;
		}
		return ctx.level.getPoiManager().findClosest(type -> type.is(PoiTypes.HOME), ctx::inTerritory, ctx.villager.blockPosition(),
				(int) Math.ceil(radius), PoiManager.Occupancy.ANY).isPresent();
	}

	private static boolean near(WorkContext ctx, BlockPos pos, double radius) {
		return ctx.horizontalDistSq(pos) <= radius * radius && Math.abs(ctx.villager.getY() - pos.getY()) <= SAFE_DY;
	}

	// ================================================================ going there

	/** Walks home (speed boost); stuck or too long: teleported next to the spot. */
	private static void walk(WorkContext ctx, State state, Spot spot, NightConfig cfg) {
		BlockPos target = spot.pos();
		Villager villager = ctx.villager;
		if (!ctx.loaded(target)) {
			ctx.status("villager.status.night.unloaded");
			return;
		}
		Vec3 pos = villager.position();
		if (!target.equals(state.walkTarget)) {
			state.walkTarget = target;
			state.walkStart = ctx.now;
			state.lastProgress = ctx.now;
			state.lastPos = pos;
		}
		if (state.lastPos == null || pos.distanceToSqr(state.lastPos) > 1.0) {
			state.lastPos = pos;
			state.lastProgress = ctx.now;
		}
		boolean timedOut = ctx.now - state.walkStart > WorkContext.ticks(ctx.config.work.walkTimeoutSeconds);
		boolean stuck = ctx.now - state.lastProgress > WorkContext.ticks(ctx.config.work.stuckSeconds);
		if ((timedOut || stuck) && shelter(ctx, state, spot)) {
			return;
		}
		Brain<Villager> brain = villager.getBrain();
		brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, (float) ctx.config.work.walkSpeed, 1));
		brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		if (cfg.speedLevel > 0) {
			villager.addEffect(new MobEffectInstance(MobEffects.SPEED, SPEED_TICKS, cfg.speedLevel - 1, false, false));
		}
		ctx.status("villager.status.night.going_home", target.getX(), target.getY(), target.getZ());
	}

	/**
	 * Teleports the worker next to the spot (a bed: in its room when possible), else next to the reception, else the
	 * heart. @return false when no standing spot was found around any of them
	 */
	private static boolean shelter(WorkContext ctx, State state, Spot spot) {
		List<Spot> spots = new ArrayList<>();
		spots.add(spot);
		BlockPos reception = GuardRetreat.reception(ctx.guild);
		if (reception != null && spot.kind() == Kind.BED) {
			spots.add(new Spot(reception, Kind.RECEPTION));
		}
		if (ctx.guild.heart != null && spot.kind() != Kind.HEART) {
			spots.add(new Spot(ctx.guild.heart, Kind.HEART));
		}
		for (Spot candidate : spots) {
			BlockPos stand = standNear(ctx.level, candidate);
			if (stand != null) {
				teleport(ctx, stand, candidate.pos());
				state.walkTarget = null;
				ctx.status("villager.status.night.sheltered");
				return true;
			}
		}
		return false;
	}

	/** A standing spot next to the spot: for a bed, a covered one in sight of the bed first (its room), never through a wall. */
	static @Nullable BlockPos standNear(ServerLevel level, Spot spot) {
		BlockPos pos = spot.pos();
		if (!level.isLoaded(pos)) {
			return null;
		}
		if (spot.kind() != Kind.BED) {
			return WorldUtil.findSafeSpot(level, pos, 3, true, p -> true);
		}
		BlockPos room = WorldUtil.findSafeSpot(level, pos, 2, false, p -> !level.canSeeSky(p) && sees(level, p, pos));
		if (room != null) {
			return room;
		}
		BlockPos inSight = WorldUtil.findSafeSpot(level, pos, 2, false, p -> sees(level, p, pos));
		return inSight != null ? inSight : WorldUtil.findSafeSpot(level, pos, 2, false, p -> true);
	}

	/** Nothing but the bed itself between a standing spot (eye height) and the bed. */
	private static boolean sees(ServerLevel level, BlockPos stand, BlockPos bed) {
		Vec3 from = new Vec3(stand.getX() + 0.5, stand.getY() + 1.5, stand.getZ() + 0.5);
		Vec3 to = Vec3.atCenterOf(bed);
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || level.getBlockState(hit.getBlockPos()).getBlock() instanceof AbstractBedBlock;
	}

	private static void teleport(WorkContext ctx, BlockPos stand, BlockPos lookAt) {
		Villager villager = ctx.villager;
		WorldUtil.portal(ctx.level, villager.getX(), villager.getY(), villager.getZ());
		Movement.stop(ctx);
		villager.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(lookAt));
		WorldUtil.portal(ctx.level, villager.getX(), villager.getY(), villager.getZ());
	}
}
