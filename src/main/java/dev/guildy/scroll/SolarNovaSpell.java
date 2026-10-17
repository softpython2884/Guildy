package dev.guildy.scroll;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Nova solaire: the caster flares like a small sun (golden flash, totem sparks), then a burning sphere expands around them;
 * every monster the shell sweeps is burnt (undead even more), set on fire and thrown back. Embers rain down afterwards.
 */
final class SolarNovaSpell extends SpellEffect {
	private static final int EXPANSION = 14;
	private static final int EMBERS = 16;

	private final Vec3 center;
	private final double radius;
	private final double damage;
	private final float fireSeconds;
	private final double undeadMultiplier;
	private final double knockback;
	private final Set<UUID> hit = new HashSet<>();

	SolarNovaSpell(SpellContext ctx) {
		super(ctx);
		this.center = ctx.caster.position().add(0, 1.0, 0);
		this.radius = ctx.p("radius");
		this.damage = ctx.p("damage");
		this.fireSeconds = (float) ctx.p("fireSeconds");
		this.undeadMultiplier = ctx.p("undeadMultiplier");
		this.knockback = ctx.p("knockback");
	}

	@Override
	Vec3 anchor() {
		return center;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			fx.dot(Fx.flash(0xFFF2B0), center);
			fx.burst(ParticleTypes.TOTEM_OF_UNDYING, center, 60, 0.4, 0.6);
			fx.burst(ParticleTypes.END_ROD, center, 30, 0.3, 0.25);
			sound(center, SoundEvents.BEACON_ACTIVATE, 2.5F, 1.4F);
			sound(center, SoundEvents.GENERIC_EXPLODE, 1.6F, 1.6F);
			sound(center, SoundEvents.BLAZE_SHOOT, 2.0F, 0.6F);
		}
		if (age <= EXPANSION) {
			double t = (double) age / EXPANSION;
			double current = Math.max(0.6, radius * easeOut(t));
			int points = (int) Math.min(220, 24 + current * current * 2.2);
			int color = Fx.mix(spell.color, spell.accent, t);
			fx.sphere(Fx.fade(color, 0xFF3000, 1.9F), center, current, points, age * 0.35, false);
			fx.sphere(ParticleTypes.END_ROD, center, current * 0.97, Math.max(8, points / 6), -age * 0.5, false);
			// Burning ring racing on the ground.
			fx.arc(ParticleTypes.FLAME, center.add(0, -0.85, 0), current, 0, Math.PI * 2, (int) Math.min(90, current * 7), true);
			sweep(fx, current);
			if (age % 4 == 0) {
				sound(center, SoundEvents.FIRECHARGE_USE, 1.4F, 0.8F + (float) t * 0.6F);
			}
		} else {
			// Embers falling back.
			for (int i = 0; i < 12; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double d = Math.sqrt(random.nextDouble()) * radius;
				fx.move(Fx.fade(0xFFD27A, 0x5A1A00, 1.2F), center.x + Math.cos(angle) * d, center.y + 2 + random.nextDouble() * 2,
						center.z + Math.sin(angle) * d, 0, -0.08, 0);
			}
			if (age >= EXPANSION + EMBERS) {
				finish();
			}
		}
	}

	private void sweep(Fx fx, double current) {
		for (LivingEntity target : SpellTargets.hostiles(ctx, level, center, current + 0.8, 40)) {
			if (!hit.add(target.getUUID())) {
				continue;
			}
			double amount = damage * (target.isInvertedHealAndHarm() ? undeadMultiplier : 1.0);
			SpellTargets.hurt(ctx, target, SpellTargets.Element.FIRE, amount);
			if (fireSeconds > 0) {
				target.igniteForSeconds(fireSeconds);
			}
			Vec3 away = target.position().subtract(center);
			away = new Vec3(away.x, 0, away.z);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
			SpellTargets.push(target, new Vec3(away.x * knockback, 0.25 + knockback * 0.2, away.z * knockback));
			fx.burst(ParticleTypes.FLAME, target.getX(), target.getY(0.5), target.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
			fx.dot(Fx.flash(0xFFB347), target.getX(), target.getY(0.6), target.getZ());
		}
	}
}
