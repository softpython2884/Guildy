package dev.guildy.villager;

import dev.guildy.core.Hooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
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
 * Two roles ({@link GuardRole}): swordsmen chase and strike in melee, archers keep their distance (they step back when
 * a monster comes too close) and shoot real arrows that only hurt monsters. Hits and shots happen in
 * {@link GuardCombat} (finer cadence); this once-per-second step picks the target and moves.
 * <p>
 * Under {@code guard.retreatHealthRatio} of its health a guard stops fighting and runs (speed boost) to its bed to heal
 * (still striking monsters within reach), then goes back to its post at {@code guard.resumeHealthRatio}; out of
 * combat it regenerates slowly and fetches better equipment from the guild storage ({@link GuardEquipment}).
 * Unreachable targets are dropped after a timeout and ignored for a while. Never in another guild's territory. Guards
 * never panic or sleep (see TaskEngine brain locks): they heal standing by their bed.
 */
final class GuardTask extends TaskHandler {
	private static final float CHASE_SPEED = 0.75f;
	private static final float BACK_OFF_SPEED = 0.8f;
	private static final long IGNORE_TICKS = 30 * 20L;
	private static final int RANDOM_POINT_TRIES = 6;
	/** Patrol points stay roughly at the height of the fanion (no mountain tops). */
	private static final int MAX_PATROL_DY = 24;
	/** Without fighting nor being hurt for this long, the slow passive regeneration starts. */
	private static final long OUT_OF_COMBAT_TICKS = 10 * 20L;
	private static final double BED_REACH = 2.5;
	private static final double SPOT_REACH = 3.0;
	private static final double CHEST_REACH = 2.5;
	/** A retreating guard still strikes monsters this close (blocks beyond its attack reach). */
	private static final double THREAT_MARGIN = 1.0;
	private static final int SPEED_TICKS = 50;
	/** Archers look for a visible target among the nearest few monsters. */
	private static final int MAX_SIGHT_CHECKS = 6;

	private @Nullable UUID targetId;
	private long chaseStart;
	private final Map<UUID, Long> ignored = new HashMap<>();
	private final GuardCombat.Fighter fighter = new GuardCombat.Fighter();
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
	// health
	private float lastHealth = -1.0f;
	private long lastCombat = -OUT_OF_COMBAT_TICKS;
	private boolean retreating;
	private GuardRetreat.@Nullable Spot retreatSpot;
	private int healSteps;
	// equipment
	private long nextEquipmentScan;
	private @Nullable BlockPos equipmentChest;

	@Override
	void tick(WorkContext ctx) {
		Villager villager = ctx.villager;
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		if (lastHealth >= 0 && villager.getHealth() < lastHealth - 0.01f) {
			lastCombat = ctx.now;
		}
		try {
			step(ctx);
		} finally {
			lastHealth = villager.getHealth();
		}
	}

	@Override
	void stop(WorkContext ctx) {
		GuardCombat.disengage(fighter);
	}

	private void step(WorkContext ctx) {
		ignored.values().removeIf(until -> until < ctx.now);
		GuardRole role = Guards.role(ctx.guild, ctx.record);
		GuardEquipment.checkWeapon(ctx, role);
		GuardEquipment.applyDisplay(ctx.villager, role);
		GuardEquipment.syncDropChances(ctx.villager, ctx.config.guard);
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
		if (retreat(ctx, role, wave)) {
			return;
		}
		if (route == null && zone == null && heart == null) {
			GuardCombat.disengage(fighter);
			regenerate(ctx);
			ctx.status("villager.status.no_territory");
			return;
		}
		Mob target = currentTarget(ctx, role);
		if (target == null) {
			target = findTarget(ctx, role);
			if (target != null) {
				targetId = target.getUUID();
				chaseStart = ctx.now;
				equipmentChest = null;
			}
		}
		if (target != null) {
			lastCombat = ctx.now;
			fight(ctx, target, role);
			fought = true;
			return;
		}
		targetId = null;
		GuardCombat.disengage(fighter);
		regenerate(ctx);
		if (equip(ctx, role)) {
			return;
		}
		if (route != null) {
			patrolRoute(ctx, route);
		} else if (zone != null) {
			patrolZone(ctx, zone);
		} else {
			guardHeart(ctx, heart, wave);
		}
	}

	// ------------------------------------------------------------------ retreat & healing

	/** Below the threshold: runs to its bed and heals until the resume threshold. @return true while retreating */
	private boolean retreat(WorkContext ctx, GuardRole role, boolean wave) {
		VillagerConfig.Guard cfg = ctx.config.guard;
		Villager villager = ctx.villager;
		double health = villager.getHealth();
		double max = villager.getMaxHealth();
		if (!retreating) {
			if (!GuardRules.mustRetreat(health, max, cfg.retreatHealthRatio)) {
				return false;
			}
			retreating = true;
			retreatSpot = null;
			targetId = null;
			equipmentChest = null;
			patrolPoint = null;
			Movement.stop(ctx);
		} else if (GuardRules.canResume(health, max, cfg.resumeHealthRatio)) {
			retreating = false;
			retreatSpot = null;
			// Back to its post: a route guard resumes at the nearest waypoint.
			fought = true;
			return false;
		}
		Mob threat = nearestThreat(ctx, role);
		if (threat != null) {
			GuardCombat.engage(fighter, ctx, threat, role, true);
		} else {
			GuardCombat.disengage(fighter);
		}
		GuardRetreat.Spot spot = retreatSpot;
		if (spot == null || (spot.bed() && !GuardRetreat.isBed(ctx.level, spot.pos()))) {
			spot = GuardRetreat.find(ctx, wave);
			retreatSpot = spot;
		}
		boolean atBed = false;
		if (spot != null) {
			switch (Movement.moveTo(ctx, spot.pos(), spot.bed() ? BED_REACH : SPOT_REACH, ctx.config.work.walkTimeoutSeconds)) {
				case MOVING -> {
					speedUp(ctx);
					heal(ctx, GuardRules.healPerSecond(true, false, false, cfg.healPerSecond, cfg.bedHealPerSecond, cfg.passiveRegenPerSecond));
					ctx.status("villager.status.guard.retreating");
					return true;
				}
				case UNREACHABLE -> retreatSpot = null;
				case ARRIVED -> {
					// Faster healing only really next to the bed (not when the walk fell back to a remote action).
					atBed = spot.bed() && ctx.distSq(spot.pos()) <= (BED_REACH + 1.0) * (BED_REACH + 1.0);
					Movement.hold(ctx, threat == null ? spot.pos() : null);
				}
			}
		}
		heal(ctx, GuardRules.healPerSecond(true, atBed, false, cfg.healPerSecond, cfg.bedHealPerSecond, cfg.passiveRegenPerSecond));
		if (atBed && healSteps++ % 3 == 0) {
			ctx.level.sendParticles(ParticleTypes.HEART, villager.getX(), villager.getY() + villager.getBbHeight() + 0.3, villager.getZ(),
					1, 0.2, 0.1, 0.2, 0.0);
		}
		ctx.status(atBed ? "villager.status.guard.healing_bed" : "villager.status.guard.healing",
				GuardRules.displayHealth(villager.getHealth()), GuardRules.displayHealth(max));
		return true;
	}

	/** Nearest monster within arm's reach of a retreating guard (it strikes back without stopping). */
	private @Nullable Mob nearestThreat(WorkContext ctx, GuardRole role) {
		double range = ctx.config.guard.attackReach + THREAT_MARGIN;
		AABB box = ctx.villager.getBoundingBox().inflate(range, 2.0, range);
		Mob best = null;
		double bestDist = range * range;
		for (Mob mob : ctx.level.getEntitiesOfClass(Mob.class, box, mob -> isValidTarget(ctx, mob, role) && !ctx.foreign(mob.blockPosition()))) {
			double dist = mob.distanceToSqr(ctx.villager);
			if (dist <= bestDist) {
				bestDist = dist;
				best = mob;
			}
		}
		return best;
	}

	/** Slow passive regeneration once out of combat for a while. */
	private void regenerate(WorkContext ctx) {
		if (ctx.now - lastCombat >= OUT_OF_COMBAT_TICKS) {
			VillagerConfig.Guard cfg = ctx.config.guard;
			heal(ctx, GuardRules.healPerSecond(false, false, true, cfg.healPerSecond, cfg.bedHealPerSecond, cfg.passiveRegenPerSecond));
		}
	}

	private static void heal(WorkContext ctx, double amount) {
		Villager villager = ctx.villager;
		if (amount > 0 && villager.isAlive() && villager.getHealth() < villager.getMaxHealth()) {
			villager.heal((float) amount);
		}
	}

	private static void speedUp(WorkContext ctx) {
		int level = ctx.config.guard.retreatSpeedLevel;
		if (level > 0) {
			ctx.villager.addEffect(new MobEffectInstance(MobEffects.SPEED, SPEED_TICKS, level - 1, false, false));
		}
	}

	// ------------------------------------------------------------------ equipment

	/** Out of combat: walks to a storage chest holding better equipment and puts it on. @return true while busy */
	private boolean equip(WorkContext ctx, GuardRole role) {
		VillagerConfig.Guard cfg = ctx.config.guard;
		if (!cfg.equipmentFromChests && !cfg.weaponsFromChests) {
			equipmentChest = null;
			return false;
		}
		if (equipmentChest == null) {
			if (ctx.now < nextEquipmentScan) {
				return false;
			}
			nextEquipmentScan = ctx.now + WorkContext.ticks(cfg.equipmentScanSeconds);
			equipmentChest = GuardEquipment.findUpgradeChest(ctx, role);
			if (equipmentChest == null) {
				return false;
			}
		}
		BlockPos chestPos = equipmentChest;
		StorageChest chest = ctx.guild.villagers.chestAt(chestPos);
		if (chest == null || !Storage.usable(ctx.level, ctx.guild, chest)) {
			equipmentChest = null;
			return false;
		}
		switch (Movement.moveTo(ctx, chestPos, CHEST_REACH, ctx.config.work.walkTimeoutSeconds)) {
			case MOVING -> {
				ctx.status("villager.status.guard.equipping", chestPos.getX(), chestPos.getY(), chestPos.getZ());
				return true;
			}
			case UNREACHABLE -> {
				equipmentChest = null;
				return false;
			}
			default -> {
				equipmentChest = null;
				Container container = Storage.container(ctx.level, chestPos);
				int equipped = container == null ? 0 : GuardEquipment.equipFrom(ctx, container, role);
				if (equipped <= 0) {
					return false;
				}
				Movement.hold(ctx, chestPos);
				WorldUtil.swing(ctx.villager);
				// Another chest may hold more: look again at the next step.
				nextEquipmentScan = ctx.now;
				ctx.status("villager.status.guard.equipped", equipped);
				return true;
			}
		}
	}

	// ------------------------------------------------------------------ fighting

	private void fight(WorkContext ctx, Mob target, GuardRole role) {
		ctx.villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
		GuardCombat.engage(fighter, ctx, target, role, false);
		if (role == GuardRole.BOW) {
			archer(ctx, target);
			return;
		}
		double reach = ctx.config.guard.attackReach;
		boolean inReach = ctx.villager.distanceToSqr(target) <= reach * reach && Math.abs(ctx.villager.getY() - target.getY()) <= 2.0;
		if (!inReach) {
			if (giveUp(ctx, target)) {
				return;
			}
			Movement.follow(ctx, target, CHASE_SPEED);
			ctx.status("villager.status.guard.chasing");
			return;
		}
		chaseStart = ctx.now;
		Movement.follow(ctx, target, CHASE_SPEED);
		ctx.status("villager.status.guard.fighting");
	}

	/** Archer: closes in until in range and sight, steps back when the target is too close, else stands and shoots. */
	private void archer(WorkContext ctx, Mob target) {
		VillagerConfig.Guard cfg = ctx.config.guard;
		Villager villager = ctx.villager;
		switch (GuardRules.archerMove(villager.distanceTo(target), villager.hasLineOfSight(target), cfg.bowMinDistance, cfg.bowRange)) {
			case APPROACH -> {
				if (giveUp(ctx, target)) {
					return;
				}
				Movement.follow(ctx, target, CHASE_SPEED);
				ctx.status("villager.status.guard.chasing");
			}
			case BACK_OFF -> {
				chaseStart = ctx.now;
				backOff(ctx, target);
				ctx.status("villager.status.guard.backing_off");
			}
			case HOLD -> {
				chaseStart = ctx.now;
				Movement.hold(ctx, null);
				villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
				ctx.status("villager.status.guard.shooting");
			}
		}
	}

	/** A few blocks away from the target (never into another guild's land); cornered: stands and shoots. */
	private static void backOff(WorkContext ctx, Mob target) {
		Vec3 away = DefaultRandomPos.getPosAway(ctx.villager, 6, 3, target.position());
		Brain<Villager> brain = ctx.villager.getBrain();
		if (away == null || ctx.foreign(BlockPos.containing(away))) {
			Movement.hold(ctx, null);
		} else {
			brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(away, BACK_OFF_SPEED, 0));
			ctx.runtime.resetMovement();
		}
		brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
	}

	/** Unreachable target (flying, across water...): given up after the chase timeout and ignored for a while. */
	private boolean giveUp(WorkContext ctx, Mob target) {
		if (ctx.now - chaseStart <= WorkContext.ticks(ctx.config.guard.chaseTimeoutSeconds)) {
			return false;
		}
		ignored.put(target.getUUID(), ctx.now + IGNORE_TICKS);
		targetId = null;
		GuardCombat.disengage(fighter);
		Movement.stop(ctx);
		ctx.status("villager.status.guard.patrol");
		return true;
	}

	private @Nullable Mob currentTarget(WorkContext ctx, GuardRole role) {
		if (targetId == null) {
			return null;
		}
		Entity entity = ctx.level.getEntity(targetId);
		if (entity instanceof Mob mob && isValidTarget(ctx, mob, role) && guarded(ctx, mob, 4.0)) {
			return mob;
		}
		targetId = null;
		return null;
	}

	/**
	 * Nearest valid target: around the heart (heart defence), around the guard (patrols). Archers prefer the nearest
	 * monster they can see.
	 */
	private @Nullable Mob findTarget(WorkContext ctx, GuardRole role) {
		int radius = ctx.config.guard.radius;
		BlockPos center = route == null && zone == null && heart != null ? heart : ctx.villager.blockPosition();
		AABB box = new AABB(center).inflate(radius, Math.min(radius, 12), radius);
		List<Mob> mobs = new ArrayList<>(ctx.level.getEntitiesOfClass(Mob.class, box, mob -> isValidTarget(ctx, mob, role) && guarded(ctx, mob, 0.0)));
		if (mobs.isEmpty()) {
			return null;
		}
		mobs.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(ctx.villager)));
		if (role == GuardRole.BOW) {
			for (int i = 0; i < Math.min(mobs.size(), MAX_SIGHT_CHECKS); i++) {
				if (ctx.villager.hasLineOfSight(mobs.get(i))) {
					return mobs.get(i);
				}
			}
		}
		return mobs.getFirst();
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

	/** A monster of the guard's role (archers have their own ignore list), alive and not ignored for a while. */
	private boolean isValidTarget(WorkContext ctx, Mob mob, GuardRole role) {
		if (!(mob instanceof Enemy) || !mob.isAlive() || mob.isRemoved() || ignored.containsKey(mob.getUUID())) {
			return false;
		}
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
		List<String> ignoredMobs = role == GuardRole.BOW ? ctx.config.guard.bowIgnoredMobs : ctx.config.guard.ignoredMobs;
		return !ignoredMobs.contains(id);
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
