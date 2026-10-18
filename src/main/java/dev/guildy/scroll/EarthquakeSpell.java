package dev.guildy.scroll;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Séisme: the ground shakes in successive shock waves racing out from the caster: each wave throws chunks of the ground
 * (block particles of the real surface, dust plumes, cracks) and launches the monsters it crosses into the air, hurt and
 * slowed. No block is ever changed.
 */
final class EarthquakeSpell extends SpellEffect {
	private static final int WAVE_TICKS = 12;
	private static final int GAP = 12;

	private final Vec3 center;
	private final double radius;
	private final int waves;
	private final double damage;
	private final double lift;
	private final int slowTicks;
	private final List<Set<UUID>> hit = new ArrayList<>();

	EarthquakeSpell(SpellContext ctx) {
		super(ctx);
		this.center = ctx.caster.position();
		this.radius = ctx.p("radius");
		this.waves = ctx.i("waves");
		this.damage = ctx.p("damage");
		this.lift = ctx.p("lift");
		this.slowTicks = ctx.ticks("slowSeconds");
		for (int i = 0; i < waves; i++) {
			hit.add(new HashSet<>());
		}
	}

	@Override
	Vec3 anchor() {
		return center;
	}

	@Override
	void tick(Fx fx) {
		for (int wave = 0; wave < waves; wave++) {
			int t = age - wave * GAP;
			if (t < 0 || t > WAVE_TICKS) {
				continue;
			}
			if (t == 0) {
				sound(center, SoundEvents.GENERIC_EXPLODE, 2.0F, 0.45F);
				sound(center, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.5F, 0.6F);
				fx.burst(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, 2, 0.6, 0.1, 0.6, 0.0);
				curse(fx, center, 2.5);
			}
			double inner = radius * easeOut(Math.max(0, t - 1) / (double) WAVE_TICKS);
			double outer = radius * easeOut(t / (double) WAVE_TICKS);
			drawFront(fx, outer, wave);
			strike(fx, wave, inner - 1.2, outer + 0.6);
		}
		if (age > (waves - 1) * GAP + WAVE_TICKS + 6) {
			finish();
		}
	}

	private void drawFront(Fx fx, double r, int wave) {
		Spell spell = ctx.spell;
		int points = (int) Math.max(10, Math.min(70, r * 5));
		double phase = wave * 0.7;
		for (int i = 0; i < points; i++) {
			double angle = phase + Math.PI * 2 * i / points;
			double x = center.x + Math.cos(angle) * r;
			double z = center.z + Math.sin(angle) * r;
			Vec3 ground = SpellTargets.ground(level, new Vec3(x, center.y + 2, z), 5);
			if (i % 2 == 0) {
				BlockPos below = BlockPos.containing(ground.x, ground.y - 0.5, ground.z);
				BlockState state = level.isLoaded(below) ? level.getBlockState(below) : null;
				if (state != null && !state.isAir()) {
					fx.move(new BlockParticleOption(ParticleTypes.BLOCK, state), ground.x, ground.y + 0.1, ground.z, Math.cos(angle) * 0.1, 0.35,
							Math.sin(angle) * 0.1);
				}
			}
			if (i % 3 == 0) {
				fx.dot(ParticleTypes.DUST_PLUME, ground.x, ground.y + 0.2, ground.z);
			}
			if (i % 4 == 0) {
				fx.dot(Fx.fade(spell.color, spell.accent, 1.4F), ground.x, ground.y + 0.15, ground.z);
			}
		}
	}

	private void strike(Fx fx, int wave, double inner, double outer) {
		Set<UUID> done = hit.get(wave);
		for (LivingEntity target : SpellTargets.hostiles(ctx, level, center, outer + 4, 40)) {
			double dx = target.getX() - center.x;
			double dz = target.getZ() - center.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d < Math.max(0, inner) || d > outer || Math.abs(target.getY() - center.y) > 4 || !done.add(target.getUUID())) {
				continue;
			}
			SpellTargets.hurt(ctx, target, SpellTargets.Element.ARCANE, damage);
			if (target.isAlive()) {
				if (slowTicks > 0) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slowTicks, 2, false, true));
				}
				Vec3 away = d < 1.0E-3 ? Vec3.ZERO : new Vec3(dx / d, 0, dz / d);
				SpellTargets.push(target, new Vec3(away.x * 0.3, lift, away.z * 0.3));
			}
			BlockPos below = target.blockPosition().below();
			BlockState state = level.isLoaded(below) ? level.getBlockState(below) : null;
			if (state != null && !state.isAir()) {
				fx.burst(new BlockParticleOption(ParticleTypes.BLOCK, state), target.getX(), target.getY() + 0.1, target.getZ(), 16, 0.4, 0.1, 0.4, 0.2);
			}
		}
	}
}
