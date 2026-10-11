package dev.guildy.wave;

import dev.guildy.core.Safe;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Goal added to goal-based wave mobs: when they have no target, walk toward the base heart through intermediate
 * waypoints (path recomputed every ~2 s, staggered by entity id) and stay next to it once arrived.
 * Holding the MOVE flag keeps vanilla idle goals (random stroll...) from dragging the mob away; attack goals have a
 * higher priority and take over as soon as the mob gets a target. Every method is exception-safe: an error here
 * would otherwise crash the entity tick.
 */
final class MoveToHeartGoal extends Goal {
	static final int PRIORITY = 3;
	private static final int REPATH_TICKS = 40;
	private static final int REPATH_IDLE_TICKS = 10;

	private final PathfinderMob mob;
	private final WaveSession session;
	private int lastPathTick;

	MoveToHeartGoal(PathfinderMob mob, WaveSession session) {
		this.mob = mob;
		this.session = session;
		this.lastPathTick = -REPATH_TICKS + Math.floorMod(mob.getId(), REPATH_TICKS);
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		// Evaluated every tick: plain try/catch instead of a capturing lambda.
		try {
			return session.isRunning() && mob.getTarget() == null;
		} catch (Throwable t) {
			Safe.log("wave.goal", t);
			return false;
		}
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		lastPathTick = mob.tickCount - REPATH_TICKS;
	}

	@Override
	public void stop() {
		Safe.run("wave.goal", () -> mob.getNavigation().stop());
	}

	@Override
	public void tick() {
		try {
			doTick();
		} catch (Throwable t) {
			Safe.log("wave.goal", t);
		}
	}

	private void doTick() {
		if (session.isAtHeart(mob)) {
			if (!mob.getNavigation().isDone()) {
				mob.getNavigation().stop();
			}
			mob.getLookControl().setLookAt(Vec3.atCenterOf(session.heart()));
			return;
		}
		int elapsed = mob.tickCount - lastPathTick;
		boolean idle = mob.getNavigation().isDone();
		if (elapsed < REPATH_TICKS && !(idle && elapsed >= REPATH_IDLE_TICKS)) {
			return;
		}
		lastPathTick = mob.tickCount;
		Vec3 waypoint = session.waypoint(mob, 1.0);
		if (!mob.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 1.0)) {
			// Unreachable waypoint (cliff, water...): try a closer one.
			Vec3 closer = session.waypoint(mob, 0.5);
			mob.getNavigation().moveTo(closer.x, closer.y, closer.z, 1.0);
		}
	}
}
