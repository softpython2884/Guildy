package dev.guildy.scroll;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chaînes spectrales: ghostly chains burst from runic anchors in the ground around the aimed monster and its neighbours, wrap
 * around them and hold them in place (no walking, no jumping, no knockback, no teleporting away) inside a slowly turning cage
 * of soul light, rattling whenever they struggle. Bosses are held for their {@code bossShare} of the duration
 * ({@link DebuffRules#holdTicks}). When a monster is released, its chains shatter and deal their soul damage.
 */
final class SpectralChainsSpell extends SpellEffect {
	/** Ticks the chains take to reach their prisoner. */
	private static final int SHOOT = 6;
	private static final int ANCHORS = 4;
	private static final double ANCHOR_RADIUS = 1.8;
	private static final int STEEL = 0x8FA6B8;
	private static final int HALT_TICKS = 8;
	private static final int HALT_LEVEL = 9;

	/** A chained monster: where it is held and until when (ticks of the spell). */
	private static final class Prisoner {
		final UUID uuid;
		final Vec3 anchor;
		final int until;
		final double phase;
		boolean released;

		Prisoner(UUID uuid, Vec3 anchor, int until, double phase) {
			this.uuid = uuid;
			this.anchor = anchor;
			this.until = until;
			this.phase = phase;
		}
	}

	private final List<Prisoner> prisoners = new ArrayList<>();
	private final double damage;
	private Vec3 center;

	SpectralChainsSpell(SpellContext ctx, LivingEntity first) {
		super(ctx);
		this.damage = ctx.p("damage");
		this.center = first.position();
		int ticks = ctx.ticks("duration");
		double share = ctx.p("bossShare");
		List<LivingEntity> targets = new ArrayList<>();
		targets.add(first);
		double radius = ctx.p("radius");
		int count = ctx.i("count");
		if (radius > 0) {
			for (LivingEntity near : SpellTargets.hostiles(ctx, ctx.level, first.position(), radius, count + 1)) {
				if (targets.size() >= count) {
					break;
				}
				if (near != first) {
					targets.add(near);
				}
			}
		}
		for (int i = 0; i < targets.size(); i++) {
			LivingEntity target = targets.get(i);
			int hold = SHOOT + DebuffRules.holdTicks(ticks, SpellTargets.isBoss(target), share);
			prisoners.add(new Prisoner(target.getUUID(), target.position(), hold, i * 0.7));
		}
	}

	/** The aimed monster, else the nearest one in sight within range (null when none). */
	static @Nullable LivingEntity firstTarget(SpellContext ctx) {
		double range = ctx.p("range");
		LivingEntity aimed = SpellTargets.aimedHostile(ctx, range);
		if (aimed != null) {
			return aimed;
		}
		for (LivingEntity candidate : SpellTargets.hostiles(ctx, ctx.level, ctx.caster.position(), range, 8)) {
			if (ctx.caster.hasLineOfSight(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	@Override
	Vec3 anchor() {
		return center;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			sound(center, SoundEvents.CHAIN_PLACE, 2.0F, 0.6F);
			sound(center, SoundEvents.SOUL_ESCAPE, 2.0F, 0.8F);
			sound(center, SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 1.4F);
		}
		if (age == SHOOT) {
			sound(center, SoundEvents.CHAIN_PLACE, 2.0F, 1.0F);
			sound(center, SoundEvents.ANVIL_LAND, 0.8F, 1.6F);
		}
		boolean any = false;
		for (Prisoner prisoner : prisoners) {
			if (prisoner.released) {
				continue;
			}
			LivingEntity target = level.getEntity(prisoner.uuid) instanceof LivingEntity living && living.isAlive() ? living : null;
			if (target == null || target.level() != level) {
				prisoner.released = true;
				continue;
			}
			any = true;
			if (age >= prisoner.until) {
				shatter(fx, prisoner, target);
				continue;
			}
			if (age >= SHOOT) {
				if (age == SHOOT) {
					// The chains bite: a flash and a burst of soul fire on the prisoner.
					fx.dot(Fx.flash(spell.accent), target.getX(), target.getY(0.55), target.getZ());
					fx.burst(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY(0.5), target.getZ(), 12, 0.4, 0.5, 0.4, 0.04);
					curse(fx, prisoner.anchor, 1.2);
				}
				hold(fx, prisoner, target);
			}
			draw(fx, spell, prisoner, target);
		}
		if (!any) {
			finish();
		}
	}

	/** Keeps the prisoner at its anchor (put back when moved, no velocity, no path), slowed to a halt. */
	private void hold(Fx fx, Prisoner prisoner, LivingEntity target) {
		if (target.position().distanceToSqr(prisoner.anchor) > 0.04) {
			target.teleportTo(prisoner.anchor.x, prisoner.anchor.y, prisoner.anchor.z);
			if (age % 6 == 0) {
				sound(prisoner.anchor, SoundEvents.CHAIN_HIT, 1.0F, 0.8F + random.nextFloat() * 0.4F);
				fx.burst(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY(0.5), target.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
			}
		}
		if (target.getDeltaMovement().lengthSqr() > 1.0E-6) {
			// No knockback, no leap: the velocity is cancelled (and sent only when there was one).
			target.setDeltaMovement(Vec3.ZERO);
			target.syncVelocity = true;
		}
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if ((age - SHOOT) % 5 == 0) {
			// Slowed to a halt: the monster stops trying to walk (short and refreshed: it fades a few ticks after the release).
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HALT_TICKS, HALT_LEVEL, false, false));
		}
		center = prisoner.anchor;
	}

	/** Chains from the ground anchors to the prisoner's chest (shooting out at first), the cage and the runes. */
	private void draw(Fx fx, Spell spell, Prisoner prisoner, LivingEntity target) {
		double reach = Math.min(1, (age + 1) / (double) SHOOT);
		Vec3 base = prisoner.anchor;
		Vec3 chest = target.position().add(0, target.getBbHeight() * 0.55, 0);
		double spread = Math.max(ANCHOR_RADIUS, target.getBbWidth() * 0.8 + 1.0);
		if (age % 2 == 0 || age < SHOOT) {
			for (int i = 0; i < ANCHORS; i++) {
				double angle = prisoner.phase + i * Math.PI * 2 / ANCHORS;
				Vec3 ground = base.add(Math.cos(angle) * spread, 0.05, Math.sin(angle) * spread);
				chain(fx, spell, ground, ground.add(chest.subtract(ground).scale(reach)));
				if (age % 4 == 0) {
					fx.ring(Fx.dust(spell.accent, 0.8F), ground.add(0, 0.05, 0), 0.35, 6, age * 0.4);
					fx.move(ParticleTypes.SOUL_FIRE_FLAME, ground.x, ground.y + 0.1, ground.z, 0, 0.03, 0);
				}
			}
		}
		if (age >= SHOOT && age % 3 == 0) {
			// A turning cage of soul light around the prisoner.
			double r = target.getBbWidth() * 0.5 + 0.45;
			double h = target.getBbHeight() + 0.3;
			for (int b = 0; b < 6; b++) {
				double angle = age * 0.05 + b * Math.PI / 3;
				Vec3 bottom = base.add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r);
				fx.line(Fx.dust(spell.color, 0.6F), bottom, bottom.add(0, h, 0), 2.5);
			}
			fx.ring(Fx.dust(spell.accent, 0.8F), base.add(0, h, 0), r, 10, -age * 0.05);
		}
		if (age % 5 == 0) {
			fx.runeCircle(base.add(0, 0.08, 0), Math.max(1.2, spread * 0.75), spell.color, spell.accent, Math.min(1, (age + 1) / 12.0), age * 0.06);
		}
	}

	/** A chain of alternating links (steel and soul light) from {@code from} to {@code to}. */
	private void chain(Fx fx, Spell spell, Vec3 from, Vec3 to) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 0.1) {
			return;
		}
		Vec3 dir = delta.scale(1 / length);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
		int links = (int) Math.min(24, Math.ceil(length / 0.3));
		for (int i = 0; i <= links; i++) {
			Vec3 point = from.add(delta.scale((double) i / links));
			// Every other link turned a quarter: a small sideways offset makes the chain read as links.
			Vec3 offset = i % 2 == 0 ? Vec3.ZERO : side.scale(0.07);
			if (!fx.dot(Fx.dust(i % 2 == 0 ? STEEL : spell.color, i % 2 == 0 ? 0.8F : 0.65F), point.add(offset))) {
				return;
			}
		}
	}

	/** The chains of a released monster shatter: soul damage, shards of light, the clink of broken chains. */
	private void shatter(Fx fx, Prisoner prisoner, LivingEntity target) {
		prisoner.released = true;
		Vec3 chest = target.position().add(0, target.getBbHeight() * 0.55, 0);
		fx.sphereBurst(Fx.fade(ctx.spell.color, ctx.spell.accent, 1.2F), chest, 0.4, 36, 0.25);
		fx.burst(ParticleTypes.SOUL, chest.x, chest.y, chest.z, 8, 0.4, 0.5, 0.4, 0.05);
		fx.dot(Fx.flash(ctx.spell.color), chest);
		curse(fx, target.position(), 1.2);
		sound(target.position(), SoundEvents.CHAIN_BREAK, 2.0F, 0.7F);
		sound(target.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.5F, 0.6F);
		SpellTargets.hurt(ctx, target, SpellTargets.Element.ARCANE, damage);
	}

	@Override
	void end(boolean cancelled) {
		if (!cancelled) {
			return;
		}
		// The caster left: the chains vanish (no damage); the halt fades within a few ticks.
		for (Prisoner prisoner : prisoners) {
			prisoner.released = true;
		}
	}
}
