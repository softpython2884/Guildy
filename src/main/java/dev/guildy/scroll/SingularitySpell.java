package dev.guildy.scroll;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Singularité: a tiny black star opens at the aimed point: an accretion disk spins around its dark core while streams of
 * violet light and portal motes are sucked into it, dragging every monster around toward the center. Then it implodes
 * (sonic boom, flash, shock ring): heavy damage, monsters flung away.
 */
final class SingularitySpell extends SpellEffect {
	private static final int FADE = 18;

	private final Vec3 core;
	private final double radius;
	private final int pullTicks;
	private final double strength;
	private final double damage;

	SingularitySpell(SpellContext ctx, Vec3 point) {
		super(ctx);
		this.core = point.add(0, 1.6, 0);
		this.radius = ctx.p("radius");
		this.pullTicks = ctx.ticks("pullSeconds");
		this.strength = ctx.p("pullStrength");
		this.damage = ctx.p("damage");
	}

	@Override
	Vec3 anchor() {
		return core;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			sound(core, SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.6F);
			sound(core, SoundEvents.PORTAL_TRIGGER, 1.2F, 1.8F);
		}
		if (age < pullTicks) {
			double grow = Math.min(1, (age + 1) / 20.0);
			// Dark core.
			fx.sphere(Fx.dust(spell.accent, 2.4F), core, 0.35 + 0.25 * grow, 14, age * 0.6, false);
			fx.burst(ParticleTypes.REVERSE_PORTAL, core, 3, 0.15, 0.02);
			// Accretion disk (tilted ring) and its outer halo.
			double phase = age * 0.45;
			for (int i = 0; i < 28; i++) {
				double angle = phase + i * Math.PI * 2 / 28;
				double r = 1.3 + 0.25 * Math.sin(angle * 3 + age * 0.2);
				double y = Math.sin(angle) * 0.35;
				fx.dot(Fx.fade(spell.color, spell.accent, 1.3F), core.x + Math.cos(angle) * r, core.y + y, core.z + Math.sin(angle) * r);
			}
			fx.ring(Fx.dust(0xE6B8FF, 0.7F), core, 2.0 + 0.2 * Math.sin(age * 0.3), 20, -phase * 0.5);
			// Streams of light and motes falling into the core.
			for (int i = 0; i < 10; i++) {
				Vec3 dir = randomDirection();
				double d = radius * (0.5 + random.nextDouble() * 0.5);
				Vec3 start = core.add(dir.scale(d));
				fx.dot(Fx.trail(core, i % 2 == 0 ? spell.color : 0xFFFFFF, 10 + random.nextInt(8)), start);
			}
			for (int i = 0; i < 4; i++) {
				Vec3 offset = randomDirection().scale(2 + random.nextDouble() * 3);
				// Portal particles travel from (pos + velocity) to pos: they converge on the core.
				fx.move(ParticleTypes.PORTAL, core, offset);
			}
			pull();
			if (age % 20 == 10) {
				sound(core, SoundEvents.PORTAL_AMBIENT, 1.5F, 0.5F);
			}
		} else if (age == pullTicks) {
			implode(fx);
		} else {
			double t = (age - pullTicks) / (double) FADE;
			fx.ring(Fx.fade(spell.color, 0x000000, 1.6F), core.add(0, -1.3, 0), radius * easeOut(t), (int) (radius * 6), age * 0.2);
			if (age >= pullTicks + FADE) {
				finish();
			}
		}
	}

	private Vec3 randomDirection() {
		double y = random.nextDouble() * 2 - 1;
		double angle = random.nextDouble() * Math.PI * 2;
		double r = Math.sqrt(1 - y * y);
		return new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);
	}

	private void pull() {
		for (LivingEntity target : SpellTargets.hostiles(ctx, level, core, radius, 40)) {
			Vec3 to = core.subtract(target.position().add(0, target.getBbHeight() * 0.5, 0));
			double distance = to.length();
			if (distance < 0.9) {
				target.setDeltaMovement(target.getDeltaMovement().scale(0.3));
				target.syncVelocity = true;
				continue;
			}
			double force = strength * (0.6 + 0.4 * (1 - distance / radius));
			Vec3 velocity = target.getDeltaMovement().scale(0.5).add(to.scale(force / distance)).add(0, 0.03, 0);
			target.setDeltaMovement(velocity);
			target.syncVelocity = true;
			target.resetFallDistance();
		}
	}

	private void implode(Fx fx) {
		Spell spell = ctx.spell;
		fx.dot(Fx.flash(spell.color), core);
		fx.burst(ParticleTypes.SONIC_BOOM, core, 3, 0.4, 0.0);
		fx.burst(ParticleTypes.EXPLOSION_EMITTER, core, 1, 0.0, 0.0);
		fx.sphereBurst(ParticleTypes.END_ROD, core, 0.5, 60, 0.45);
		fx.sphereBurst(Fx.fade(spell.color, spell.accent, 2.0F), core, 0.8, 50, 0.3);
		fx.burst(ParticleTypes.REVERSE_PORTAL, core, 80, 0.6, 0.6);
		curse(fx, core.add(0, -1.3, 0), radius * 0.5);
		sound(core, SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.8F);
		sound(core, SoundEvents.GENERIC_EXPLODE, 2.5F, 0.6F);
		for (LivingEntity target : SpellTargets.hostiles(ctx, level, core, radius, 40)) {
			double distance = Math.sqrt(target.distanceToSqr(core));
			double falloff = Math.max(0.5, 1 - 0.5 * distance / radius);
			SpellTargets.hurt(ctx, target, SpellTargets.Element.ARCANE, damage * falloff);
			Vec3 away = target.position().subtract(core);
			away = new Vec3(away.x, 0, away.z);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
			target.setDeltaMovement(away.x * 1.4 * falloff, 0.6 * falloff, away.z * 1.4 * falloff);
			target.syncVelocity = true;
		}
	}
}
