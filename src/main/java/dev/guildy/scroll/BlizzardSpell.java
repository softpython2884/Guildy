package dev.guildy.scroll;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Blizzard: a frozen storm settles on the aimed area for several seconds: a frost ring and a counter-rotating inner ring on
 * the ground, a three-armed snow vortex, heavy snowfall from a cloud ceiling. Every second, the monsters inside are slowed,
 * frozen solid and chilled (cold damage).
 */
final class BlizzardSpell extends SpellEffect {
	private static final int PULSE = 20;

	private final Vec3 center;
	private final double radius;
	private final int duration;
	private final double damage;
	private final int slowLevel;

	BlizzardSpell(SpellContext ctx, Vec3 center) {
		super(ctx);
		this.center = center;
		this.radius = ctx.p("radius");
		this.duration = ctx.ticks("duration");
		this.damage = ctx.p("damage");
		this.slowLevel = ctx.i("slowLevel");
	}

	@Override
	Vec3 anchor() {
		return center;
	}

	@Override
	void tick(Fx fx) {
		if (age == 0) {
			sound(center, SoundEvents.BREEZE_WIND_CHARGE_BURST, 2.5F, 0.6F);
			sound(center, SoundEvents.POWDER_SNOW_BREAK, 2.0F, 0.6F);
			fx.dot(Fx.flash(ctx.spell.color), center.x, center.y + 1.5, center.z);
		}
		double phase = age * 0.12;
		double fadeIn = Math.min(1, (age + 1) / 15.0);
		double r = radius * (0.35 + 0.65 * easeOut(fadeIn));
		// Ground: frost ring and inner counter-rotating ring.
		fx.ring(Fx.fade(0xFFFFFF, 0x9FDBFF, 1.4F), center.add(0, 0.15, 0), r, (int) (r * 6), phase);
		fx.ring(Fx.dust(0x7CC7F2, 1.0F), center.add(0, 0.2, 0), r * 0.6, (int) (r * 3.5), -phase * 1.6);
		// Vortex: three spiral arms of snowflakes blown around.
		for (int k = 0; k < 3; k++) {
			for (int j = 0; j < 9; j++) {
				double angle = phase * 2.2 + k * Math.PI * 2 / 3 + j * 0.42;
				double armRadius = r * (0.95 - j * 0.085);
				double h = 0.3 + j * 0.6;
				double cos = Math.cos(angle);
				double sin = Math.sin(angle);
				fx.move(ParticleTypes.SNOWFLAKE, center.x + cos * armRadius, center.y + h, center.z + sin * armRadius, -sin * 0.14, 0.02, cos * 0.14);
			}
		}
		// Snowfall from a cloud ceiling.
		for (int i = 0; i < 16; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(random.nextDouble()) * r;
			fx.move(ParticleTypes.SNOWFLAKE, center.x + Math.cos(angle) * d, center.y + 6.5, center.z + Math.sin(angle) * d,
					0.06 * Math.cos(phase), -0.22, 0.06 * Math.sin(phase));
		}
		if (age % 2 == 0) {
			fx.burst(ParticleTypes.CLOUD, center.x, center.y + 6.9, center.z, 6, r * 0.45, 0.15, r * 0.45, 0.005);
			fx.burst(ParticleTypes.ITEM_SNOWBALL, center.x, center.y + 1.2, center.z, 4, r * 0.4, 0.6, r * 0.4, 0.05);
		}
		if (age % PULSE == 0) {
			pulse(fx);
		}
		if (age >= duration) {
			fx.arc(ParticleTypes.SNOWFLAKE, center.add(0, 0.6, 0), radius * 0.5, 0, Math.PI * 2, 40, true);
			sound(center, SoundEvents.GLASS_BREAK, 1.5F, 0.6F);
			finish();
		}
	}

	private void pulse(Fx fx) {
		fx.arc(Fx.dust(0xFFFFFF, 1.2F), center.add(0, 0.4, 0), radius * 0.3, 0, Math.PI * 2, 24, true);
		sound(center, SoundEvents.POWDER_SNOW_STEP, 1.8F, 0.5F + random.nextFloat() * 0.2F);
		for (LivingEntity target : SpellTargets.hostiles(ctx, level, center, radius + 6, 40)) {
			double dx = target.getX() - center.x;
			double dz = target.getZ() - center.z;
			if (dx * dx + dz * dz > radius * radius || Math.abs(target.getY() - center.y) > 6) {
				continue;
			}
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PULSE * 2, slowLevel - 1, false, true));
			if (target.canFreeze()) {
				target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + PULSE * 2));
			}
			SpellTargets.hurt(ctx, target, SpellTargets.Element.FROST, damage);
			fx.burst(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.5, 0.3, 0.03);
			fx.burst(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY(0.6), target.getZ(), 4, 0.2, 0.3, 0.2, 0.05);
		}
	}
}
