package dev.guildy.scroll;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bénédiction: a ring of blossoms and light spreads from the caster; healing streams fly to every ally around (the caster,
 * guild members, guild villagers) who is healed, regenerating, cleansed of harmful effects and fire, and wrapped in a
 * spiral of hearts and golden sparks.
 */
final class BlessingSpell extends SpellEffect {
	private static final int SPREAD = 16;
	private static final int DURATION = 40;

	private final Vec3 center;
	private final double radius;
	private final List<UUID> healed = new ArrayList<>();

	BlessingSpell(SpellContext ctx) {
		super(ctx);
		this.center = ctx.caster.position();
		this.radius = ctx.p("radius");
	}

	@Override
	Vec3 anchor() {
		return center;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			bless(fx);
			fx.dot(Fx.flash(0xFFF4C8), center.x, center.y + 1.2, center.z);
			fx.burst(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y + 1, center.z, 40, 0.4, 0.6, 0.4, 0.3);
			sound(center, SoundEvents.AMETHYST_BLOCK_CHIME, 2.5F, 0.8F);
			sound(center, SoundEvents.PLAYER_LEVELUP, 1.2F, 1.6F);
			sound(center, SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.4F);
		}
		if (age <= SPREAD) {
			double r = radius * easeOut(age / (double) SPREAD);
			fx.ring(Fx.fade(spell.color, spell.accent, 1.3F), center.add(0, 0.15, 0), r, (int) Math.max(12, r * 5), age * 0.2);
			fx.arc(ParticleTypes.HAPPY_VILLAGER, center.add(0, 0.3, 0), r, 0, Math.PI * 2, (int) Math.max(6, r * 2), false);
		}
		// Falling petals and a light column above the caster.
		fx.burst(ParticleTypes.CHERRY_LEAVES, center.x, center.y + 3.5, center.z, 4, 2.5, 0.3, 2.5, 0.0);
		fx.move(ParticleTypes.END_ROD, center.x, center.y + 4, center.z, 0, -0.15, 0);
		// Spirals of hearts and sparks around the healed allies.
		for (UUID uuid : healed) {
			if (!(level.getEntity(uuid) instanceof LivingEntity ally) || !ally.isAlive()) {
				continue;
			}
			double angle = age * 0.5;
			double h = (age % 20) * 0.1;
			fx.dot(Fx.dust(spell.color, 1.0F), ally.getX() + Math.cos(angle) * 0.7, ally.getY() + h, ally.getZ() + Math.sin(angle) * 0.7);
			fx.dot(Fx.dust(spell.accent, 1.0F), ally.getX() - Math.cos(angle) * 0.7, ally.getY() + h, ally.getZ() - Math.sin(angle) * 0.7);
			if (age % 6 == 0) {
				fx.burst(ParticleTypes.HEART, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3, ally.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
			}
		}
		if (age >= DURATION) {
			finish();
		}
	}

	private void bless(Fx fx) {
		double heal = ctx.p("heal");
		int regenTicks = ctx.ticks("regenSeconds");
		int regenLevel = ctx.i("regenLevel");
		boolean cleanse = ctx.i("cleanse") > 0;
		Vec3 from = ctx.caster.position().add(0, 1.2, 0);
		for (LivingEntity ally : SpellTargets.allies(ctx, level, center, radius)) {
			if (heal > 0) {
				ally.heal((float) heal);
			}
			if (regenTicks > 0) {
				ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenTicks, regenLevel - 1, false, true));
			}
			if (cleanse) {
				List<Holder<MobEffect>> harmful = new ArrayList<>();
				for (MobEffectInstance effect : ally.getActiveEffects()) {
					if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
						harmful.add(effect.getEffect());
					}
				}
				harmful.forEach(ally::removeEffect);
				ally.clearFire();
			}
			healed.add(ally.getUUID());
			Vec3 to = ally.position().add(0, ally.getBbHeight() * 0.6, 0);
			if (ally != ctx.caster) {
				for (int i = 0; i < 6; i++) {
					Vec3 start = from.add((random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.8);
					fx.dot(Fx.trail(to, i % 2 == 0 ? ctx.spell.color : ctx.spell.accent, 12 + random.nextInt(6)), start);
				}
			}
			fx.burst(ParticleTypes.HEART, to, 4, 0.4, 0.0);
			fx.burst(ParticleTypes.HAPPY_VILLAGER, to, 10, 0.4, 0.0);
		}
	}
}
