package dev.guildy.villager;

import dev.guildy.core.Hooks;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Attacks the nearest hostile mob ({@link Enemy}) inside the territory around its post (work position, else the heart).
 * During a wave the post is the heart. Melee within reach with a cooldown; unreachable targets are dropped after a
 * timeout and ignored for a while. Guards never panic, sleep or flee (see TaskEngine brain locks).
 */
final class GuardTask extends TaskHandler {
	private static final float CHASE_SPEED = 0.75f;
	private static final long IGNORE_TICKS = 30 * 20L;

	private @Nullable UUID targetId;
	private long chaseStart;
	private long lastAttack = -10_000;
	private final Map<UUID, Long> ignored = new HashMap<>();

	@Override
	void tick(WorkContext ctx) {
		boolean wave = Hooks.isWaveActive.test(ctx.guild);
		BlockPos post = wave || ctx.record.workPos == null ? ctx.guild.heart : ctx.record.workPos;
		if (post == null) {
			ctx.status("villager.status.no_territory");
			return;
		}
		if (ctx.villager.isSleeping()) {
			ctx.villager.stopSleeping();
		}
		ignored.values().removeIf(until -> until < ctx.now);
		int radius = ctx.config.guard.radius;
		Mob target = currentTarget(ctx, post, radius);
		if (target == null) {
			target = findTarget(ctx, post, radius);
			if (target != null) {
				targetId = target.getUUID();
				chaseStart = ctx.now;
			}
		}
		if (target != null) {
			fight(ctx, target);
			return;
		}
		targetId = null;
		double home = wave ? 5.0 : 8.0;
		if (ctx.distSq(post) > home * home) {
			switch (Movement.moveTo(ctx, post, 3.0, ctx.config.work.walkTimeoutSeconds)) {
				case UNREACHABLE -> ctx.status("villager.status.workpos_unloaded");
				case MOVING -> ctx.status("villager.status.guard.returning");
				case ARRIVED -> ctx.status(wave ? "villager.status.guard.heart" : "villager.status.guard.patrol");
			}
			return;
		}
		ctx.status(wave ? "villager.status.guard.heart" : "villager.status.guard.patrol");
	}

	private void fight(WorkContext ctx, Mob target) {
		ServerLevel level = ctx.level;
		double reach = ctx.config.guard.attackReach;
		boolean inReach = ctx.villager.distanceToSqr(target) <= reach * reach && Math.abs(ctx.villager.getY() - target.getY()) <= 2.0;
		ctx.villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
		if (!inReach) {
			if (ctx.now - chaseStart > WorkContext.ticks(ctx.config.guard.chaseTimeoutSeconds)) {
				// Unreachable (flying, across water...): give up for a while.
				ignored.put(target.getUUID(), ctx.now + IGNORE_TICKS);
				targetId = null;
				Movement.stop(ctx);
				ctx.status("villager.status.guard.patrol");
				return;
			}
			Movement.follow(ctx, target, CHASE_SPEED);
			ctx.status("villager.status.guard.chasing");
			return;
		}
		chaseStart = ctx.now;
		Movement.follow(ctx, target, CHASE_SPEED);
		ctx.status("villager.status.guard.fighting");
		if (ctx.now - lastAttack < ctx.config.guard.attackCooldownTicks) {
			return;
		}
		lastAttack = ctx.now;
		WorldUtil.swing(ctx.villager);
		float damage = (float) ctx.config.guardDamage(ctx.guild.level);
		boolean hurt = target.hurtServer(level, level.damageSources().mobAttack(ctx.villager), damage);
		if (hurt) {
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
			WorldUtil.playSound(level, ctx.villager, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.1f);
		}
		if (!target.isAlive()) {
			ctx.record.stats.kills++;
			targetId = null;
			GuildManager.markDirty();
		}
	}

	private @Nullable Mob currentTarget(WorkContext ctx, BlockPos post, int radius) {
		if (targetId == null) {
			return null;
		}
		Entity entity = ctx.level.getEntity(targetId);
		if (entity instanceof Mob mob && isValidTarget(ctx, mob)) {
			double max = radius + 8.0;
			if (mob.distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5) <= max * max) {
				return mob;
			}
		}
		targetId = null;
		return null;
	}

	private @Nullable Mob findTarget(WorkContext ctx, BlockPos post, int radius) {
		AABB box = new AABB(post).inflate(radius, Math.min(radius, 12), radius);
		List<Mob> mobs = ctx.level.getEntitiesOfClass(Mob.class, box, mob -> isValidTarget(ctx, mob));
		Mob best = null;
		double bestDist = Double.MAX_VALUE;
		for (Mob mob : mobs) {
			double dist = mob.distanceToSqr(ctx.villager);
			if (dist < bestDist) {
				bestDist = dist;
				best = mob;
			}
		}
		return best;
	}

	private boolean isValidTarget(WorkContext ctx, Mob mob) {
		if (!(mob instanceof Enemy) || !mob.isAlive() || mob.isRemoved() || ignored.containsKey(mob.getUUID())) {
			return false;
		}
		if (!ctx.inTerritory(mob.blockPosition())) {
			return false;
		}
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
		return !ctx.config.guard.ignoredMobs.contains(id);
	}
}
