package dev.guildy.scroll;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Égide: a golden dome rises over the cast spot (rotating latitude rings, a runic circle on the ground, light pillars on the
 * rim). Allies inside get absorption hearts (once) and resistance while they stay; monsters are pushed out and stung;
 * projectiles shot by monsters crumble on the dome.
 */
final class AegisSpell extends SpellEffect {
	private static final int RISE = 12;

	private final Vec3 center;
	private final double radius;
	private final int duration;
	private final int absorption;
	private final int resistance;
	private final double push;
	private final double damage;
	private final Set<UUID> shielded = new HashSet<>();

	AegisSpell(SpellContext ctx) {
		super(ctx);
		this.center = ctx.caster.position();
		this.radius = ctx.p("radius");
		this.duration = ctx.ticks("duration");
		this.absorption = ctx.i("absorptionLevel");
		this.resistance = ctx.i("resistanceLevel");
		this.push = ctx.p("push");
		this.damage = ctx.p("damage");
	}

	@Override
	Vec3 anchor() {
		return center;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			sound(center, SoundEvents.BEACON_ACTIVATE, 2.5F, 1.0F);
			sound(center, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 0.8F);
			fx.dot(Fx.flash(spell.color), center.x, center.y + 1, center.z);
		}
		drawDome(fx, spell);
		if (age % 10 == 0) {
			protect(fx);
		}
		repel(fx);
		if (age % 30 == 15) {
			sound(center, SoundEvents.BEACON_AMBIENT, 1.2F, 1.4F);
		}
		if (age >= duration) {
			fx.sphereBurst(Fx.dust(spell.accent, 1.2F), center, radius * 0.9, 50, 0.08);
			sound(center, SoundEvents.BEACON_DEACTIVATE, 2.0F, 1.0F);
			finish();
		}
	}

	private void drawDome(Fx fx, Spell spell) {
		double rise = easeOut(Math.min(1, (age + 1) / (double) RISE));
		double phase = age * 0.04;
		// Latitude rings, a few per tick (the whole dome every 3 ticks).
		for (int lat = age % 3; lat < 9; lat += 3) {
			double a = Math.PI / 2 * lat / 9.0;
			double y = Math.sin(a) * radius * rise;
			double r = Math.cos(a) * radius;
			int points = (int) Math.max(8, r * 5);
			fx.ring(Fx.dust(lat % 2 == 0 ? spell.color : spell.accent, 1.0F), center.add(0, y + 0.1, 0), r, points, phase * (lat % 2 == 0 ? 1 : -1));
		}
		// Meridians (golden arcs over the top), drawn progressively while rising.
		if (age % 2 == 0) {
			for (int m = 0; m < 6; m++) {
				double angle = phase * 0.5 + m * Math.PI / 3;
				for (int k = 0; k <= 8; k++) {
					double a = Math.PI / 2 * k / 8.0;
					double r = Math.cos(a) * radius;
					fx.dot(Fx.dust(spell.color, 0.8F), center.x + Math.cos(angle) * r, center.y + Math.sin(a) * radius * rise,
							center.z + Math.sin(angle) * r);
				}
			}
		}
		if (age % 4 == 0) {
			fx.runeCircle(center.add(0, 0.12, 0), radius, spell.color, spell.accent, 1.0, phase);
		}
		// Light pillars on the rim and a few sparkles.
		for (int i = 0; i < 6; i++) {
			double angle = -phase + i * Math.PI / 3;
			fx.move(ParticleTypes.END_ROD, center.x + Math.cos(angle) * radius, center.y + 0.2, center.z + Math.sin(angle) * radius, 0, 0.12, 0);
		}
		fx.burst(ParticleTypes.WAX_OFF, center.x, center.y + radius * 0.5, center.z, 2, radius * 0.4, radius * 0.25, radius * 0.4, 0.0);
	}

	private void protect(Fx fx) {
		for (LivingEntity ally : SpellTargets.allies(ctx, level, center, radius)) {
			if (shielded.add(ally.getUUID())) {
				int remaining = Math.max(20, duration - age);
				ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, remaining, absorption - 1, false, true));
				fx.burst(ParticleTypes.TOTEM_OF_UNDYING, ally.getX(), ally.getY(0.6), ally.getZ(), 16, 0.3, 0.5, 0.3, 0.2);
			}
			ally.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, resistance - 1, false, true));
			fx.ring(Fx.dust(ctx.spell.color, 0.7F), ally.position().add(0, 0.1, 0), 0.7, 8, age * 0.2);
		}
	}

	private void repel(Fx fx) {
		for (LivingEntity target : SpellTargets.hostiles(ctx, level, center, radius, 40)) {
			Vec3 away = target.position().subtract(center);
			away = new Vec3(away.x, 0, away.z);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
			if (push > 0) {
				SpellTargets.push(target, new Vec3(away.x * push * 0.35, 0.08, away.z * push * 0.35));
			}
			if (age % 20 == 0) {
				// A pulse of a continuous spell: a fraction of a hit for the diminishing returns.
				SpellTargets.hurt(ctx, target, SpellTargets.Element.ARCANE, damage, CastDamage.continuous(20));
				fx.burst(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
			}
			fx.burst(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 2, 0.3, 0.4, 0.3, 0.05);
		}
		// Monster projectiles crumble on the dome.
		AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
		double r2 = radius * radius;
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box, p -> p.distanceToSqr(center) <= r2)) {
			Entity shooter = projectile.getOwner();
			if (shooter instanceof Enemy) {
				fx.burst(ParticleTypes.WAX_ON, projectile.getX(), projectile.getY(), projectile.getZ(), 8, 0.15, 0.15, 0.15, 0.1);
				fx.dot(Fx.flash(ctx.spell.color), projectile.position());
				projectile.discard();
			}
		}
	}
}
