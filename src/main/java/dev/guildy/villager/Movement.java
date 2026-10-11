package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Villager movement through the vanilla brain (WALK_TARGET memory, executed by the core MoveToTargetSink behaviour),
 * with a timeout + stuck detection. On failure the villager is teleported next to the target (loaded chunk) or the
 * action is simply performed from where it stands, so a task can never stay stuck forever.
 */
final class Movement {
	enum Result {
		/** Close enough (or fallback applied): perform the action. */
		ARRIVED,
		/** Still walking. */
		MOVING,
		/** Target chunk not loaded: the caller must pick something else or wait. */
		UNREACHABLE
	}

	private Movement() {
	}

	static Result moveTo(WorkContext ctx, BlockPos target, double reach, int timeoutSeconds) {
		if (!ctx.loaded(target)) {
			return Result.UNREACHABLE;
		}
		VillagerRuntime rt = ctx.runtime;
		Villager villager = ctx.villager;
		if (target.equals(rt.remoteTarget)) {
			return Result.ARRIVED;
		}
		rt.remoteTarget = null;
		// New walk (other target, or same target after a break of more than a few seconds): restart the timers.
		if (!target.equals(rt.moveTarget) || ctx.now - rt.lastMoveCall > 60) {
			rt.moveTarget = target;
			rt.moveStart = ctx.now;
			rt.lastProgress = ctx.now;
			rt.lastMovePos = villager.position();
		}
		rt.lastMoveCall = ctx.now;
		if (ctx.distSq(target) <= reach * reach) {
			rt.resetMovement();
			return Result.ARRIVED;
		}
		Vec3 pos = villager.position();
		if (rt.lastMovePos == null || pos.distanceToSqr(rt.lastMovePos) > 1.0) {
			rt.lastMovePos = pos;
			rt.lastProgress = ctx.now;
		}
		boolean timedOut = ctx.now - rt.moveStart > WorkContext.ticks(timeoutSeconds);
		boolean stuck = ctx.now - rt.lastProgress > WorkContext.ticks(ctx.config.work.stuckSeconds);
		if (timedOut || stuck) {
			teleportNear(ctx, target);
			rt.resetMovement();
			return Result.ARRIVED;
		}
		Brain<Villager> brain = villager.getBrain();
		int closeEnough = Math.max(1, (int) Math.floor(reach) - 1);
		brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, (float) ctx.config.work.walkSpeed, closeEnough));
		brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		return Result.MOVING;
	}

	/** Follows an entity (guards). No fallback: the caller handles the chase timeout. */
	static void follow(WorkContext ctx, Entity target, float speed) {
		Brain<Villager> brain = ctx.villager.getBrain();
		brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), speed, 1));
		brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
		ctx.runtime.resetMovement();
	}

	/** Cancels any walk (ours or a vanilla stroll) and looks at {@code lookAt}. Used while working on a spot. */
	static void hold(WorkContext ctx, @Nullable BlockPos lookAt) {
		Villager villager = ctx.villager;
		Brain<Villager> brain = villager.getBrain();
		if (brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
			brain.eraseMemory(MemoryModuleType.WALK_TARGET);
			brain.eraseMemory(MemoryModuleType.PATH);
			villager.getNavigation().stop();
		}
		if (lookAt != null) {
			brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(lookAt));
		}
	}

	/** Stops our walk (used when pausing or switching task) so the vanilla brain takes over. */
	static void stop(WorkContext ctx) {
		Brain<Villager> brain = ctx.villager.getBrain();
		brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		brain.eraseMemory(MemoryModuleType.PATH);
		ctx.villager.getNavigation().stop();
		ctx.runtime.resetMovement();
	}

	/** True when the villager stands close enough to {@code pos} to work on it (or works on it remotely). */
	static boolean near(WorkContext ctx, BlockPos pos, double maxHorizontalDist, double maxVerticalDist) {
		return pos.equals(ctx.runtime.remoteTarget) || (ctx.horizontalDistSq(pos) <= maxHorizontalDist * maxHorizontalDist
				&& Math.abs(ctx.villager.getY() - pos.getY()) <= maxVerticalDist);
	}

	/** Fallback: teleports to a safe spot next to the target; if none, the action happens remotely. */
	static void teleportNear(WorkContext ctx, BlockPos target) {
		Villager villager = ctx.villager;
		BlockPos spot = villager.isPassenger() ? null : WorldUtil.findSafeSpot(ctx.level, target, 2, false, p -> true);
		if (spot == null) {
			ctx.runtime.remoteTarget = target;
			return;
		}
		WorldUtil.portal(ctx.level, villager.getX(), villager.getY(), villager.getZ());
		Brain<Villager> brain = villager.getBrain();
		brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		brain.eraseMemory(MemoryModuleType.PATH);
		villager.getNavigation().stop();
		villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		WorldUtil.portal(ctx.level, villager.getX(), villager.getY(), villager.getZ());
	}
}
