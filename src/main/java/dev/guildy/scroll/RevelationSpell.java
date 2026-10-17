package dev.guildy.scroll;

import dev.guildy.core.Msg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Révélation: three sonar waves of light ripple out from the caster (rings on the ground, a bell's resonance); every monster
 * they reach starts glowing through the walls for the duration, marked by a pillar of light above it.
 */
final class RevelationSpell extends SpellEffect {
	private static final int WAVE_TICKS = 24;
	private static final int WAVES = 3;
	private static final int GAP = 10;

	private final Vec3 center;
	private final double radius;
	private final int glowTicks;
	private final Set<UUID> revealed = new HashSet<>();

	RevelationSpell(SpellContext ctx) {
		super(ctx);
		this.center = ctx.caster.position();
		this.radius = ctx.p("radius");
		this.glowTicks = ctx.ticks("duration");
	}

	/** Monsters the caster could reveal (none = the scroll is kept). */
	static boolean anyMonster(SpellContext ctx) {
		return !SpellTargets.hostiles(ctx, ctx.level, ctx.caster.position(), ctx.p("radius"), 1).isEmpty();
	}

	@Override
	Vec3 anchor() {
		return center;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			sound(center, SoundEvents.BELL_BLOCK, 2.5F, 0.8F);
			sound(center, SoundEvents.BELL_RESONATE, 3.0F, 1.0F);
			fx.dot(Fx.flash(spell.color), center.x, center.y + 1.5, center.z);
			fx.sphereBurst(ParticleTypes.END_ROD, center.add(0, 1.2, 0), 0.4, 40, 0.3);
		}
		boolean waving = false;
		for (int wave = 0; wave < WAVES; wave++) {
			int start = wave * GAP;
			int t = age - start;
			if (t < 0 || t > WAVE_TICKS) {
				continue;
			}
			waving = true;
			double r = radius * easeOut(t / (double) WAVE_TICKS);
			int points = (int) Math.min(140, Math.max(16, r * 2.5));
			fx.ring(Fx.fade(spell.color, spell.accent, 1.5F), center.add(0, 0.2 + wave * 0.15, 0), r, points, age * 0.1 + wave);
			if (wave == 0) {
				reveal(fx, r);
			}
		}
		if (age % 8 == 0) {
			for (UUID uuid : revealed) {
				if (level.getEntity(uuid) instanceof LivingEntity target && target.isAlive()) {
					fx.move(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 0, 0.25, 0);
				}
			}
		}
		if (!waving && age > WAVES * GAP + WAVE_TICKS) {
			ServerPlayer caster = caster();
			if (caster != null) {
				Msg.actionBar(caster, "scroll.revelation.count", revealed.size());
			}
			finish();
		}
	}

	private void reveal(Fx fx, double r) {
		List<LivingEntity> targets = SpellTargets.hostiles(ctx, level, center, r, 200);
		for (LivingEntity target : targets) {
			if (!revealed.add(target.getUUID())) {
				continue;
			}
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowTicks, 0, false, false));
			fx.burst(ParticleTypes.SCULK_CHARGE_POP, target.getX(), target.getY(0.5), target.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
			fx.line(Fx.fade(ctx.spell.color, 0xFFFFFF, 0.8F), target.position().add(0, target.getBbHeight(), 0),
					target.position().add(0, target.getBbHeight() + 4, 0), 3);
		}
	}
}
