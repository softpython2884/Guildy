package dev.guildy.scroll;

import dev.guildy.core.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gardiens: runic circles open beside the caster and iron golems rise from them (gray dust pillars, flash, anvil clang).
 * The guardians are vanilla player-made iron golems (they fight monsters, never players), tagged, named after the caster,
 * escort them, and vanish after the duration, on logout or dimension change of the caster, at server stop, when killed
 * (no iron dropped) and when their chunk is loaded again later.
 */
final class GuardiansSpell extends SpellEffect {
	private static final int SUMMON = 16;
	/** Every guardian still bound to a running spell (the others are refused when their chunk loads). */
	private static final Set<UUID> ALIVE = new HashSet<>();

	private final List<Vec3> spots;
	private final List<UUID> golems = new ArrayList<>();
	private final int duration;
	private Vec3 anchor;

	GuardiansSpell(SpellContext ctx, List<Vec3> spots) {
		super(ctx);
		this.spots = spots;
		this.duration = ctx.ticks("duration");
		this.anchor = ctx.caster.position();
	}

	/** Lang key of the reason no guardian can be summoned, or null. */
	static @Nullable String check(SpellContext ctx) {
		int max = ScrollModule.config().limits.maxGuardiansPerPlayer;
		if (alive(ctx.caster.getUUID()) + ctx.i("count") > max) {
			return "scroll.fail.guardian_limit";
		}
		return spots(ctx).isEmpty() ? "scroll.fail.no_space" : null;
	}

	/** Free spots for the golems around the caster (sides, then behind, then front). */
	static List<Vec3> spots(SpellContext ctx) {
		ServerPlayer caster = ctx.caster;
		Vec3 look = caster.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0, look.z);
		forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
		Vec3 side = new Vec3(-forward.z, 0, forward.x);
		Vec3[] offsets = {side.scale(2.5), side.scale(-2.5), forward.scale(-2.5), forward.scale(2.5), side.scale(2.5).add(forward.scale(-2.5)),
				side.scale(-2.5).add(forward.scale(-2.5))};
		List<Vec3> out = new ArrayList<>();
		int count = ctx.i("count");
		for (Vec3 offset : offsets) {
			if (out.size() >= count) {
				break;
			}
			Vec3 spot = free(ctx.level, caster.position().add(offset));
			if (spot != null) {
				out.add(spot);
			}
		}
		return out;
	}

	/** A spot near {@code pos} (a block up or down) with room for an iron golem standing on solid ground, or null. */
	private static @Nullable Vec3 free(ServerLevel level, Vec3 pos) {
		for (int dy : new int[] {0, 1, -1}) {
			BlockPos feet = BlockPos.containing(pos.x, pos.y + dy, pos.z);
			if (!level.isLoaded(feet)) {
				continue;
			}
			Vec3 spot = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			AABB box = EntityTypes.IRON_GOLEM.getDimensions().makeBoundingBox(spot);
			BlockPos below = feet.below();
			if (level.noCollision(box) && !level.containsAnyLiquid(box)
					&& !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				return spot;
			}
		}
		return null;
	}

	/** Guardians alive for this player. */
	static int alive(UUID owner) {
		int n = 0;
		for (GuardiansSpell spell : EffectRunner.all(GuardiansSpell.class)) {
			if (spell.owner.equals(owner)) {
				n += spell.age < SUMMON ? spell.spots.size() : spell.golems.size();
			}
		}
		return n;
	}

	static boolean isGuardian(Entity entity) {
		return entity.entityTags().contains(SpellTargets.TAG_GUARDIAN);
	}

	/** ServerEntityEvents.ALLOW_LOAD: a guardian no running spell owns (restart, chunk reloaded later) never comes back. */
	static boolean allowLoad(Entity entity) {
		return !isGuardian(entity) || ALIVE.contains(entity.getUUID());
	}

	/** ServerLivingEntityEvents.ALLOW_DEATH: a killed guardian crumbles (no iron, no death message). */
	static boolean allowDeath(LivingEntity entity) {
		if (!isGuardian(entity)) {
			return true;
		}
		vanish(entity);
		return false;
	}

	private static void vanish(Entity entity) {
		ALIVE.remove(entity.getUUID());
		if (entity.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, true, false, entity.getX(), entity.getY(0.5), entity.getZ(), 30, 0.5, 0.9, 0.5, 0.05);
			level.sendParticles(Fx.dust(0xC8CCD4, 1.6F), true, false, entity.getX(), entity.getY(0.5), entity.getZ(), 30, 0.5, 1.0, 0.5, 0.0);
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.IRON_GOLEM_DEATH, net.minecraft.sounds.SoundSource.PLAYERS,
					1.0F, 1.4F);
		}
		entity.discard();
	}

	/** Server start / stop: no guardian survives. */
	static void clearAll() {
		ALIVE.clear();
	}

	/** Golem entities of this spell still in the world (tests). */
	List<IronGolem> golems() {
		List<IronGolem> out = new ArrayList<>();
		for (UUID uuid : golems) {
			if (level.getEntity(uuid) instanceof IronGolem golem && golem.isAlive()) {
				out.add(golem);
			}
		}
		return out;
	}

	@Override
	Vec3 anchor() {
		return anchor;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			sound(anchor, SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 0.8F);
		}
		if (age < SUMMON) {
			double progress = (age + 1) / (double) SUMMON;
			for (Vec3 spot : spots) {
				fx.runeCircle(spot.add(0, 0.12, 0), 1.4, spell.color, spell.accent, progress, age * 0.12);
				fx.move(Fx.fade(spell.color, spell.accent, 1.5F), spot.x + (random.nextDouble() - 0.5) * 1.2, spot.y + 0.1,
						spot.z + (random.nextDouble() - 0.5) * 1.2, 0, 0.12 + progress * 0.1, 0);
			}
			return;
		}
		if (age == SUMMON) {
			summon(fx);
			return;
		}
		List<IronGolem> present = golems();
		if (!present.isEmpty()) {
			anchor = present.getFirst().position();
		}
		ServerPlayer caster = caster();
		for (IronGolem golem : present) {
			if (age % 6 == 0) {
				fx.ring(Fx.dust(spell.color, 0.8F), golem.position().add(0, 0.1, 0), 1.0, 10, age * 0.15);
			}
			if (age % 20 == 0 && caster != null && caster.level() == level) {
				escort(golem, caster);
			}
		}
		// All destroyed (killed or unloaded for good).
		golems.removeIf(uuid -> !ALIVE.contains(uuid));
		if (golems.isEmpty() || age >= SUMMON + duration) {
			finish();
		}
	}

	private void summon(Fx fx) {
		ServerPlayer caster = caster();
		String name = caster == null ? "?" : caster.getName().getString();
		for (Vec3 spot : spots) {
			IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (golem == null) {
				continue;
			}
			golem.setPlayerCreated(true);
			golem.addTag(SpellTargets.TAG_GUARDIAN);
			golem.setCustomName(Lang.get("scroll.guardian.name", name));
			golem.setCustomNameVisible(true);
			golem.snapTo(spot.x, spot.y, spot.z, caster == null ? 0 : caster.getYRot(), 0);
			ALIVE.add(golem.getUUID());
			if (!level.addFreshEntity(golem)) {
				ALIVE.remove(golem.getUUID());
				continue;
			}
			golems.add(golem.getUUID());
			fx.dot(Fx.flash(0xE8ECF2), spot.x, spot.y + 1.4, spot.z);
			fx.burst(ParticleTypes.CLOUD, spot.x, spot.y + 1, spot.z, 20, 0.6, 0.8, 0.6, 0.05);
			fx.sphereBurst(Fx.dust(ctx.spell.color, 1.4F), spot.add(0, 1.3, 0), 0.6, 30, 0.12);
		}
		sound(anchor, SoundEvents.IRON_GOLEM_REPAIR, 2.0F, 0.8F);
		sound(anchor, SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
		if (golems.isEmpty()) {
			finish();
		}
	}

	private void escort(IronGolem golem, ServerPlayer caster) {
		double distance = golem.distanceToSqr(caster);
		if (distance > 40 * 40) {
			Vec3 spot = free(level, caster.position().add(1.5, 0, 1.5));
			if (spot != null) {
				golem.snapTo(spot.x, spot.y, spot.z, golem.getYRot(), 0);
			}
		} else if (golem.getTarget() == null && distance > 8 * 8) {
			golem.getNavigation().moveTo(caster, 1.0);
		}
	}

	@Override
	void end(boolean cancelled) {
		for (UUID uuid : golems) {
			Entity entity = level.getEntity(uuid);
			if (entity != null) {
				vanish(entity);
			} else {
				ALIVE.remove(uuid);
			}
		}
		golems.clear();
	}
}
