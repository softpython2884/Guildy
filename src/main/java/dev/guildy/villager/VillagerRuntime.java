package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Transient (not persisted) per-villager state: current task handler, status shown in the menu, movement tracking.
 * Lost on restart: tasks simply start over (carried items are persisted in the record).
 */
final class VillagerRuntime {
	private static final Object[] NO_ARGS = new Object[0];

	final UUID uuid;
	String statusKey = "villager.status.idle";
	Object[] statusArgs = NO_ARGS;

	@Nullable TaskHandler handler;
	@Nullable Task handlerTask;
	/** The handler must be rebuilt at the next step (zone / route assignment or settings changed). */
	boolean restart;
	/** True while the task is paused (night, trading...): movement timers restart on resume. */
	boolean paused;
	/** Brain activity lock currently applied (see TaskEngine), null = vanilla brain. */
	@Nullable Task brainLock;

	// movement tracking (see Movement)
	@Nullable BlockPos moveTarget;
	long moveStart;
	long lastMoveCall;
	long lastProgress;
	@Nullable Vec3 lastMovePos;
	/** Target that could not be reached nor teleported to: actions on it are done from where the villager stands. */
	@Nullable BlockPos remoteTarget;

	VillagerRuntime(UUID uuid) {
		this.uuid = uuid;
	}

	void status(String key, Object... args) {
		this.statusKey = key;
		this.statusArgs = args == null ? NO_ARGS : args;
	}

	void resetMovement() {
		moveTarget = null;
		lastMovePos = null;
	}

	/** Also forgets the "remote" target (task restarted, villager paused). */
	void resetAllMovement() {
		resetMovement();
		remoteTarget = null;
	}
}
