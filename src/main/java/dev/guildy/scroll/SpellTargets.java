package dev.guildy.scroll;

import dev.guildy.Guildy;
import dev.guildy.data.Guild;
import dev.guildy.villager.VillagerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Minecraft side of {@link TargetRules}: what an entity is for a caster, the monsters / allies around a point, aiming,
 * and the spell damage (attributed to the caster, guildy damage types, invulnerability frames reset for them).
 */
final class SpellTargets {
	/** Tag of the iron golems summoned by a scroll (Gardiens). */
	static final String TAG_GUARDIAN = "guildy_scroll_guardian";
	static final ResourceKey<DamageType> ARCANE = key("arcane");
	static final ResourceKey<DamageType> FOUDRE = key("foudre");
	static final ResourceKey<DamageType> BRASIER = key("brasier");
	static final ResourceKey<DamageType> GIVRE = key("givre");

	/** Damage element: the matching guildy damage type, arcane when the target is immune to it (spell magic still lands). */
	enum Element {
		FIRE,
		LIGHTNING,
		FROST,
		ARCANE
	}

	private SpellTargets() {
	}

	private static ResourceKey<DamageType> key(String path) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Guildy.MOD_ID, path));
	}

	// ================================================================== rules

	static TargetRules.Facts facts(Entity entity, ServerPlayer caster, @Nullable Guild guild) {
		boolean alive = entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator() && !entity.isRemoved();
		boolean player = entity instanceof Player;
		boolean member = player && guild != null && guild.isMember(entity.getUUID());
		boolean guildVillager = guild != null && entity instanceof AbstractVillager && guild.villagers.members.containsKey(entity.getUUID());
		boolean villager = entity instanceof AbstractVillager || entity.entityTags().contains(VillagerManager.TAG_VILLAGER);
		boolean owned = entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null;
		return new TargetRules.Facts(alive, entity instanceof Enemy, player, entity == caster, member, guildVillager, villager, owned,
				entity.entityTags().contains(TAG_GUARDIAN));
	}

	static boolean harmable(Entity entity, ServerPlayer caster, @Nullable Guild guild) {
		return entity instanceof LivingEntity && TargetRules.mayHarm(facts(entity, caster, guild));
	}

	static boolean helpable(Entity entity, ServerPlayer caster, @Nullable Guild guild) {
		return entity instanceof LivingEntity && TargetRules.mayHelp(facts(entity, caster, guild));
	}

	/** Monsters within {@code radius} of {@code center}, nearest first ({@code max} at most). */
	static List<LivingEntity> hostiles(SpellContext ctx, ServerLevel level, Vec3 center, double radius, int max) {
		AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
		double r2 = radius * radius;
		List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.distanceToSqr(center) <= r2 && harmable(e, ctx.caster, ctx.guild)));
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
		return found.size() > max ? new ArrayList<>(found.subList(0, Math.max(0, max))) : found;
	}

	/** Caster, guild members and guild villagers within {@code radius} of {@code center} (the caster first when there). */
	static List<LivingEntity> allies(SpellContext ctx, ServerLevel level, Vec3 center, double radius) {
		AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
		double r2 = radius * radius;
		List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.distanceToSqr(center) <= r2 && helpable(e, ctx.caster, ctx.guild)));
		found.sort(Comparator.comparingInt((LivingEntity e) -> e == ctx.caster ? 0 : 1).thenComparingDouble(e -> e.distanceToSqr(center)));
		return found;
	}

	// ================================================================== aiming

	/** The monster under the crosshair (within {@code range}, in sight), or null. */
	static @Nullable LivingEntity aimedHostile(SpellContext ctx, double range) {
		ServerPlayer player = ctx.caster;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(range));
		AABB area = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : ctx.level.getEntitiesOfClass(LivingEntity.class, area, e -> harmable(e, ctx.caster, ctx.guild))) {
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

	/** The aimed monster's feet, else the aimed block (the ground below when aiming at the sky). */
	static Vec3 aimPoint(SpellContext ctx, double range) {
		LivingEntity aimed = aimedHostile(ctx, range);
		if (aimed != null) {
			return aimed.position();
		}
		Vec3 eye = ctx.caster.getEyePosition();
		Vec3 end = eye.add(ctx.caster.getViewVector(1.0F).scale(range));
		HitResult hit = ctx.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.caster));
		Vec3 point = hit.getLocation();
		if (hit.getType() == HitResult.Type.MISS) {
			return ground(ctx.level, point, 48);
		}
		return point;
	}

	/** First solid ground at or below {@code pos} (at most {@code depth} blocks down), else {@code pos}. */
	static Vec3 ground(ServerLevel level, Vec3 pos, int depth) {
		BlockPos start = BlockPos.containing(pos);
		if (!level.isLoaded(start)) {
			return pos;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.getX(), start.getZ());
		if (top <= start.getY() && top >= start.getY() - depth) {
			return new Vec3(pos.x, top, pos.z);
		}
		BlockPos.MutableBlockPos cursor = start.mutable();
		for (int i = 0; i < depth && cursor.getY() > level.getMinY(); i++) {
			BlockPos below = cursor.below();
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				return new Vec3(pos.x, cursor.getY(), pos.z);
			}
			cursor.move(0, -1, 0);
		}
		return pos;
	}

	// ================================================================== damage

	/** Spell damage to a monster, attributed to the caster (kill credit, loot); false when nothing was dealt. */
	static boolean hurt(SpellContext ctx, LivingEntity target, Element element, double amount) {
		if (amount <= 0 || !target.isAlive() || !harmable(target, ctx.caster, ctx.guild)) {
			return false;
		}
		ResourceKey<DamageType> type = switch (element) {
			case FIRE -> target.fireImmune() ? ARCANE : BRASIER;
			case LIGHTNING -> FOUDRE;
			case FROST -> target.canFreeze() ? GIVRE : ARCANE;
			case ARCANE -> ARCANE;
		};
		ServerLevel level = (ServerLevel) target.level();
		Holder<DamageType> holder = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type);
		// No direct entity: the hit is not a melee hit of the caster (unique weapon passives stay out of it).
		ServerPlayer caster = ctx.caster.isRemoved() ? null : ctx.caster;
		return target.hurtServer(level, new DamageSource(holder, null, caster), (float) amount);
	}

	/** Guildy spell damage types land on top of any other hit (invulnerability frames reset). */
	static boolean allowDamage(LivingEntity entity, DamageSource source) {
		if (source.is(ARCANE) || source.is(FOUDRE) || source.is(BRASIER) || source.is(GIVRE)) {
			entity.damageCooldownTime = 0;
		}
		return true;
	}

	/** Pushes an entity (velocity added and sent to the clients). */
	static void push(Entity entity, Vec3 velocity) {
		entity.push(velocity.x, velocity.y, velocity.z);
		entity.syncVelocity = true;
	}
}
