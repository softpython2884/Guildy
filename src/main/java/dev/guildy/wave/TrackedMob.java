package dev.guildy.wave;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Runtime state of one living wave mob (not persisted).
 */
final class TrackedMob {
	final UUID uuid;
	final WavePlanner.PlannedMob planned;
	/** Stagger index for steering. */
	final int index;
	private @Nullable Mob cached;
	double lastX;
	double lastY;
	double lastZ;
	int stuckSeconds;
	int missingSeconds;

	TrackedMob(UUID uuid, WavePlanner.PlannedMob planned, int index) {
		this.uuid = uuid;
		this.planned = planned;
		this.index = index;
	}

	void bind(Mob mob) {
		this.cached = mob;
		checkpoint(mob);
	}

	void checkpoint(Mob mob) {
		lastX = mob.getX();
		lastY = mob.getY();
		lastZ = mob.getZ();
	}

	/** The loaded entity, or null while it is unloaded / gone. */
	@Nullable Mob resolve(ServerLevel level) {
		Mob mob = cached;
		if (mob != null && !mob.isRemoved()) {
			return mob;
		}
		Entity entity = level.getEntity(uuid);
		if (entity instanceof Mob found && !found.isRemoved()) {
			cached = found;
			return found;
		}
		return null;
	}

	/** True when the cached entity was destroyed (killed/discarded) rather than unloaded. */
	boolean isDestroyed() {
		Mob mob = cached;
		if (mob == null || !mob.isRemoved()) {
			return false;
		}
		Entity.RemovalReason reason = mob.getRemovalReason();
		return reason != null && reason.shouldDestroy();
	}

	@Nullable Mob cached() {
		return cached;
	}
}
