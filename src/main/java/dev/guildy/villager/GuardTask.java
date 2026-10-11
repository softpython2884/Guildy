package dev.guildy.villager;

import dev.guildy.core.Hooks;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.UpgradeEffects;
import dev.guildy.upgrade.Upgrades;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Guards attack hostile mobs ({@link Enemy}):
 * <ul>
 * <li>patrol route: walk the waypoints (loop or back and forth, short pause at each), fight what comes within their
 * sight but never further than {@code routes.leash} from the path, then resume at the nearest waypoint;</li>
 * <li>patrol zone: walk random points of the zone, fight inside it (+ a small margin);</li>
 * <li>otherwise (and always during a wave): defend the heart, inside the territory.</li>
 * </ul>
 * Melee within reach with a cooldown; unreachable targets are dropped after a timeout and ignored for a while. Never in
 * another guild's territory. Guards never panic, sleep or flee (see TaskEngine brain locks).
 */
final class GuardTask extends TaskHandler {
	private static final float CHASE_SPEED = 0.75f;
	private static final long IGNORE_TICKS = 30 * 20L;
	private static final int RANDOM_POINT_TRIES = 6;
	/** Patrol points stay roughly at the height of the fanion (no mountain tops). */
	private static final int MAX_PATROL_DY = 24;

	private @Nullable UUID targetId;
	private long chaseStart;
	private long lastAttack = -10_000;
	private final Map<UUID, Long> ignored = new HashMap<>();
	private String modeKey = "";
	// what the guard protects this step
	private @Nullable PatrolRoute route;
	private @Nullable WorkZone zone;
	private @Nullable BlockPos heart;
	// patrol state
	private @Nullable BlockPos patrolPoint;
	private long pauseUntil;
	private int routeIndex = -1;
	private int routeDirection = 1;
	private boolean fought;

	@Override
	void tick(WorkContext ctx) {
		if (ctx.villager.isSleeping()) {
			ctx.villager.stopSleeping();
		}
		ignored.values().removeIf(until -> until < ctx.now);
		boolean wave = Hooks.isWaveActive.test(ctx.guild);
		route = wave ? null : Zones.assignedRoute(ctx.guild, ctx.record);
		zone = wave || route != null ? null : Zones.assignedZone(ctx.guild, ctx.record);
		heart = ctx.guild.heart;
		String key = wave ? "wave" : route != null ? route.settingsKey() : areaKey(zone);
		if (!key.equals(modeKey)) {
			modeKey = key;
			targetId = null;
			patrolPoint = null;
			routeIndex = -1;
			pauseUntil = 0;
		}
		if (route == null && zone == null && heart == null) {
			ctx.status("villager.status.no_territory");
			return;
		}
		Mob target = currentTarget(ctx);
		if (target == null) {
			target = findTarget(ctx);
			if (target != null) {
				targetId = target.getUUID();
				chaseStart = ctx.now;
			}
		}
		if (target != null) {
			fight(ctx, target);
			fought = true;
			return;
		}
		targetId = null;
		if (route != null) {
			patrolRoute(ctx, route);
		} else if (zone != null) {
			patrolZone(ctx, zone);
		} else {
			guardHeart(ctx, heart, wave);
		}
	}

	// ------------------------------------------------------------------ fighting

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
		float damage = (float) Upgrades.boost(ctx.guild, UpgradeEffects.GUARD_DAMAGE_PCT, ctx.config.guardDamage(ctx.guild.level));
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

	private @Nullable Mob currentTarget(WorkContext ctx) {
		if (targetId == null) {
			return null;
		}
		Entity entity = ctx.level.getEntity(targetId);
		if (entity instanceof Mob mob && isValidTarget(ctx, mob) && guarded(ctx, mob, 4.0)) {
			return mob;
		}
		targetId = null;
		return null;
	}

	/** Nearest valid target: around the heart (heart defence), around the guard (patrols). */
	private @Nullable Mob findTarget(WorkContext ctx) {
		int radius = ctx.config.guard.radius;
		BlockPos center = route == null && zone == null && heart != null ? heart : ctx.villager.blockPosition();
		AABB box = new AABB(center).inflate(radius, Math.min(radius, 12), radius);
		List<Mob> mobs = ctx.level.getEntitiesOfClass(Mob.class, box, mob -> isValidTarget(ctx, mob) && guarded(ctx, mob, 0.0));
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

	/** The mob is in the area this guard protects ({@code slack} blocks of tolerance for a target being chased). */
	private boolean guarded(WorkContext ctx, Mob mob, double slack) {
		PatrolRoute currentRoute = route;
		if (currentRoute != null) {
			return !ctx.foreign(mob.blockPosition())
					&& ZoneMath.distanceToPath(currentRoute.points, currentRoute.loops(), mob.getX(), mob.getZ()) <= ctx.config.routes.leash + slack;
		}
		WorkZone currentZone = zone;
		if (currentZone != null) {
			return !ctx.foreign(mob.blockPosition()) && currentZone.containsWithMargin(mob.getX(), mob.getZ(), ctx.config.guard.zoneMargin + (int) slack);
		}
		BlockPos post = heart;
		if (post == null || !ctx.inTerritory(mob.blockPosition())) {
			return false;
		}
		double max = ctx.config.guard.radius + (slack > 0 ? 8.0 : 0.0);
		return slack <= 0 || mob.distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5) <= max * max;
	}

	private boolean isValidTarget(WorkContext ctx, Mob mob) {
		if (!(mob instanceof Enemy) || !mob.isAlive() || mob.isRemoved() || ignored.containsKey(mob.getUUID())) {
			return false;
		}
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
		return !ctx.config.guard.ignoredMobs.contains(id);
	}

	// ------------------------------------------------------------------ walking

	private void guardHeart(WorkContext ctx, @Nullable BlockPos post, boolean wave) {
		if (post == null) {
			ctx.status("villager.status.no_territory");
			return;
		}
		double home = wave ? 5.0 : 8.0;
		if (ctx.distSq(post) > home * home) {
			switch (Movement.moveTo(ctx, post, 3.0, ctx.config.work.walkTimeoutSeconds)) {
				case UNREACHABLE -> ctx.status("villager.status.heart_unloaded");
				case MOVING -> ctx.status("villager.status.guard.returning");
				case ARRIVED -> ctx.status(wave ? "villager.status.guard.heart" : "villager.status.guard.patrol");
			}
			return;
		}
		ctx.status(wave ? "villager.status.guard.heart" : "villager.status.guard.patrol");
	}

	private void patrolZone(WorkContext ctx, WorkZone current) {
		if (!ctx.ticking(current.pos)) {
			ctx.status("villager.status.zone_unloaded", current.name);
			return;
		}
		if (ctx.now < pauseUntil) {
			Movement.hold(ctx, null);
			ctx.status("villager.status.guard.zone", current.name);
			return;
		}
		if (patrolPoint == null) {
			patrolPoint = randomPoint(ctx, current);
		}
		switch (Movement.moveTo(ctx, patrolPoint, 2.0, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				patrolPoint = null;
				ctx.status("villager.status.zone_unloaded", current.name);
			}
			case MOVING -> ctx.status("villager.status.guard.zone", current.name);
			case ARRIVED -> {
				patrolPoint = null;
				pauseUntil = ctx.now + WorkContext.ticks(ctx.config.routes.pauseSeconds);
				ctx.status("villager.status.guard.zone", current.name);
			}
		}
	}

	/** A random standing spot of the zone (at the fanion's level, else on the surface), else the fanion. */
	private static BlockPos randomPoint(WorkContext ctx, WorkZone current) {
		int[] bounds = current.bounds();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int baseY = current.pos.getY();
		for (int i = 0; i < RANDOM_POINT_TRIES; i++) {
			int x = bounds[0] + random.nextInt(bounds[2] - bounds[0] + 1);
			int z = bounds[1] + random.nextInt(bounds[3] - bounds[1] + 1);
			BlockPos column = new BlockPos(x, baseY, z);
			if (!ctx.loaded(column) || !ctx.ticking(column) || ctx.foreign(column)) {
				continue;
			}
			BlockPos atLevel = WorldUtil.findSafeSpot(ctx.level, column, 0, false, pos -> true);
			if (atLevel != null) {
				return atLevel;
			}
			int surface = ctx.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos onTop = new BlockPos(x, surface, z);
			if (Math.abs(surface - baseY) <= MAX_PATROL_DY && WorldUtil.isSafeStand(ctx.level, onTop)) {
				return onTop;
			}
		}
		return current.pos;
	}

	private void patrolRoute(WorkContext ctx, PatrolRoute current) {
		List<BlockPos> points = current.points;
		if (routeIndex < 0 || routeIndex >= points.size() || fought) {
			// Start, or back from a fight: resume at the nearest waypoint.
			routeIndex = Math.max(0, ZoneMath.nearestIndex(points, ctx.villager.getX(), ctx.villager.getZ()));
			fought = false;
		}
		if (ctx.now < pauseUntil) {
			Movement.hold(ctx, null);
			ctx.status("villager.status.guard.route", current.name, routeIndex + 1, points.size());
			return;
		}
		for (int tries = 0; tries < points.size(); tries++) {
			BlockPos point = points.get(routeIndex);
			if (ctx.ticking(point)) {
				Movement.Result result = Movement.moveTo(ctx, point, 2.0, ctx.config.work.walkTimeoutSeconds);
				if (result == Movement.Result.MOVING) {
					ctx.status("villager.status.guard.route", current.name, routeIndex + 1, points.size());
					return;
				}
				if (result == Movement.Result.ARRIVED) {
					pauseUntil = ctx.now + WorkContext.ticks(ctx.config.routes.pauseSeconds);
					ctx.status("villager.status.guard.route", current.name, routeIndex + 1, points.size());
					advance(current);
					return;
				}
			}
			// Waypoint out of reach (no player around): skip it.
			advance(current);
		}
		ctx.status("villager.status.route_unloaded", current.name);
	}

	private void advance(PatrolRoute current) {
		ZoneMath.Step step = ZoneMath.next(routeIndex, routeDirection, current.points.size(), current.pingPong);
		routeIndex = step.index();
		routeDirection = step.direction();
	}
}
