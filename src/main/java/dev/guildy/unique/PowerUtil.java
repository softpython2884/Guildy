package dev.guildy.unique;

import dev.guildy.Guildy;
import dev.guildy.claim.ClaimGuard;
import dev.guildy.claim.HeartIndex;
import dev.guildy.core.Scheduler;
import dev.guildy.data.GuildPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared helpers of the powers: targets (hostile mobs only: powers never hurt players, villagers, animals or pets),
 * damage attributed to the wielder, per-player cooldowns of the passive powers, particle lines.
 */
final class PowerUtil {
	/** Damage types of the guildy procs (data/guildy/damage_type): they land on top of the hit that triggered them. */
	static final ResourceKey<DamageType> FOUDRE = key("foudre");
	static final ResourceKey<DamageType> BRASIER = key("brasier");
	static final ResourceKey<DamageType> GIVRE = key("givre");

	private static final Map<UUID, Map<UniquePower, Long>> COOLDOWNS = new HashMap<>();
	/** > 0 while a power deals damage: the damage events ignore it (no chain reactions). */
	private static int dealing;

	private PowerUtil() {
	}

	private static ResourceKey<DamageType> key(String path) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Guildy.MOD_ID, path));
	}

	static UniqueConfig.Powers cfg() {
		return UniqueModule.config().powers;
	}

	static boolean dealing() {
		return dealing > 0;
	}

	// ================================================================== targets

	/** Hostile mobs ({@link Enemy}) alive around {@code center}, nearest first. */
	static List<LivingEntity> hostilesAround(ServerLevel level, Vec3 center, double radius, int max, @Nullable Entity excluded) {
		AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
		double r2 = radius * radius;
		List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != excluded && isHostile(e) && e.distanceToSqr(center) <= r2));
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
		return found.size() > max ? new ArrayList<>(found.subList(0, Math.max(0, max))) : found;
	}

	static boolean isHostile(Entity entity) {
		return isHostileType(entity) && entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator();
	}

	/** A monster (alive or not): powers never affect players, villagers, animals or pets. */
	static boolean isHostileType(Entity entity) {
		return entity instanceof Enemy;
	}

	/** The hostile mob under the crosshair (within {@code range}, in sight), or null. */
	static @Nullable LivingEntity aimedHostile(ServerPlayer player, double range) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(range));
		AABB area = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area, PowerUtil::isHostile)) {
			Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.6).clip(eye, end);
			if (hit.isEmpty()) {
				continue;
			}
			double distance = eye.distanceToSqr(hit.get());
			if (distance < bestDistance && player.hasLineOfSight(candidate)) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	// ================================================================== damage

	/** Damage of a guildy type, attributed to the player (kill credit, loot, aggro). */
	static boolean damage(LivingEntity target, ResourceKey<DamageType> type, ServerPlayer player, double amount) {
		if (amount <= 0 || !target.isAlive()) {
			return false;
		}
		ServerLevel level = player.level();
		Holder<DamageType> holder = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type);
		return hurt(level, target, new DamageSource(holder, player), amount);
	}

	/** Melee-like damage of the player (player_attack). */
	static boolean melee(LivingEntity target, ServerPlayer player, double amount) {
		if (amount <= 0 || !target.isAlive()) {
			return false;
		}
		ServerLevel level = player.level();
		return hurt(level, target, level.damageSources().playerAttack(player), amount);
	}

	private static boolean hurt(ServerLevel level, LivingEntity target, DamageSource source, double amount) {
		dealing++;
		try {
			return target.hurtServer(level, source, (float) amount);
		} finally {
			dealing--;
		}
	}

	/** Never in another guild's territory nor on a heart (block powers). */
	static boolean mayAffectBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
		return ClaimGuard.allowedAt(player, level, pos, GuildPermission.BUILD) && HeartIndex.heartOrSupport(level, pos) == null;
	}

	// ================================================================== cooldowns (passive powers)

	/** True (and the cooldown starts) when the power is ready for this player. */
	static boolean ready(ServerPlayer player, UniquePower power, double seconds) {
		long now = Scheduler.ticks();
		Map<UniquePower, Long> map = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		Long until = map.get(power);
		if (until != null && now < until) {
			return false;
		}
		map.put(power, now + Math.round(seconds * 20));
		return true;
	}

	static void forget(UUID player) {
		COOLDOWNS.remove(player);
	}

	static void clear() {
		COOLDOWNS.clear();
	}

	// ================================================================== effects

	/** A line of particles between two points (about 3 per block, 48 at most). */
	static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to) {
		Vec3 delta = to.subtract(from);
		int steps = (int) Math.min(48, Math.max(2, delta.length() * 3));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.add(delta.scale((double) i / steps));
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** A horizontal ring of particles. */
	static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2 * i / points;
			level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 1, 0.05, 0.05,
					0.05, 0.01);
		}
	}
}
