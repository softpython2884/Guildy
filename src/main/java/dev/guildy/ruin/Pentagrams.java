package dev.guildy.ruin;

import dev.guildy.core.Msg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The arena pentagrams of a cursed guardian (phase 2 and after): a set of glowing, rotating inverted pentagrams appears on
 * the arena floor, one under some of the players and the others around the arena. Each one draws itself during a warning
 * second (red circle), then burns for {@code pentagramSeconds}: every half second, a player standing in it is drained
 * ({@code pentagramDamage}, magic credited to the guardian) and the guardian heals {@code pentagramHeal} times what was
 * drained, while red streams flow from the player into it. Then the pentagrams fade in smoke.
 */
final class Pentagrams extends CursedAttacks.Running {
	private static final int FADE_TICKS = 12;

	/** One pentagram on the floor. */
	record Circle(Vec3 center, double radius, double spin) {
	}

	final List<Circle> circles;
	private final int active;

	private Pentagrams(List<Circle> circles, int active) {
		this.circles = circles;
		this.active = active;
	}

	/** A new set: one pentagram under up to half of the targets (at least one), the others on the arena floor. */
	static @Nullable Pentagrams cast(CursedAttacks.Context ctx) {
		if (ctx.targets.isEmpty()) {
			return null;
		}
		CursedSettings.AttackSettings cfg = ctx.cfg();
		int count = CursedRules.pentagrams(cfg.pentagramCount, ctx.cursed.phase);
		double radius = cfg.pentagramRadius;
		List<Circle> circles = new ArrayList<>();
		int under = Math.max(1, Math.min(count, (ctx.targets.size() + 1) / 2));
		for (ServerPlayer player : CursedAttacks.pick(ctx, under)) {
			circles.add(new Circle(CursedAttacks.floor(ctx.level, player.getX(), player.getY(), player.getZ()), radius, ctx.random.nextDouble() * Math.PI * 2));
			Msg.actionBar(player, "ruin.cursed.warn.pentagram");
		}
		double reach = Math.max(4, Math.min(12, ctx.encounter.def.leashRadius * 0.6));
		for (int attempt = 0; circles.size() < count && attempt < count * 6; attempt++) {
			Vec3 spot = CursedAttacks.arenaSpot(ctx, 2.5, reach);
			boolean overlaps = false;
			for (Circle other : circles) {
				if (CursedAttacks.horizontal(other.center(), spot) < radius * 1.6) {
					overlaps = true;
					break;
				}
			}
			if (!overlaps) {
				circles.add(new Circle(spot, radius, ctx.random.nextDouble() * Math.PI * 2));
			}
		}
		CursedAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.EVOKER_PREPARE_WOLOLO, 2.0F, 0.5F);
		for (Circle circle : circles) {
			CursedAttacks.sound(ctx.level, circle.center(), SoundEvents.SCULK_CLICKING, 1.5F, 0.5F);
		}
		return new Pentagrams(circles, (int) Math.round(cfg.pentagramSeconds * 20));
	}

	/** The pentagrams are burning (drain) right now. */
	boolean burning() {
		return age >= CursedRules.TELEGRAPH_TICKS && age < CursedRules.TELEGRAPH_TICKS + active;
	}

	@Override
	boolean tick(CursedAttacks.Context ctx) {
		int telegraph = CursedRules.TELEGRAPH_TICKS;
		int t = age - telegraph;
		Vec3 core = ctx.core();
		for (int i = 0; i < circles.size(); i++) {
			Circle circle = circles.get(i);
			Vec3 c = circle.center().add(0, 0.07, 0);
			double spin = circle.spin() + age * 0.04 * (i % 2 == 0 ? 1 : -1);
			if (age < telegraph) {
				if (age % 2 == 0) {
					double k = (double) age / telegraph;
					CursedFx.warning(ctx.fx, c, circle.radius() * 1.08, k, spin);
					CursedFx.pentagram(ctx.fx, c, circle.radius(), spin, k * 0.8, CursedFx.PURPLE, CursedFx.BLOOD_BRIGHT, 0.9F);
				}
				continue;
			}
			if (t >= active) {
				// Fading in smoke.
				if (t == active) {
					ctx.fx.ring(ParticleTypes.LARGE_SMOKE, c.add(0, 0.2, 0), circle.radius(), 18, spin);
					ctx.fx.burst(ParticleTypes.SOUL, c.x, c.y + 0.3, c.z, 6, circle.radius() * 0.4, 0.1, circle.radius() * 0.4, 0.02);
				}
				continue;
			}
			if (t == 0) {
				ctx.level.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 0.4F);
				ctx.fx.ring(RitualFx.fade(CursedFx.BLOOD_BRIGHT, CursedFx.PURPLE, 1.4F), c.add(0, 0.1, 0), circle.radius(), 30, spin);
			}
			if (age % 2 == 0) {
				double glow = 0.5 + 0.5 * Math.sin(age * 0.35 + i);
				int color = RitualFx.mix(CursedFx.PURPLE, CursedFx.BLOOD_BRIGHT, glow * 0.6);
				CursedFx.pentagram(ctx.fx, c, circle.radius(), spin, 1.0, color, CursedFx.BLOOD_BRIGHT, 1.0F);
			}
			if (age % 3 == 0) {
				double angle = ctx.random.nextDouble() * Math.PI * 2;
				double dist = ctx.random.nextDouble() * circle.radius();
				ctx.fx.move(ParticleTypes.WITCH, c.x + Math.cos(angle) * dist, c.y + 0.1, c.z + Math.sin(angle) * dist, 0, 0.05, 0);
				ctx.fx.move(ParticleTypes.SOUL_FIRE_FLAME, c.x + Math.cos(angle + 2) * dist, c.y + 0.1, c.z + Math.sin(angle + 2) * dist, 0, 0.04, 0);
			}
			boolean pulse = CursedRules.pulse(age, telegraph, active);
			for (int p = 0; p < ctx.targets.size(); p++) {
				ServerPlayer player = ctx.targets.get(p);
				Vec3 feet = player.position();
				if (!CursedRules.inside(feet.x - circle.center().x, feet.y - circle.center().y, feet.z - circle.center().z, circle.radius())) {
					continue;
				}
				Vec3 chest = feet.add(0, player.getBbHeight() * 0.55, 0);
				CursedFx.stream(ctx.fx, chest, core, age, i * 7 + p);
				if (!pulse) {
					continue;
				}
				float lost = CursedAttacks.hurt(ctx, player, ctx.damage(ctx.cfg().pentagramDamage), ctx.magic());
				if (lost > 0) {
					ctx.cursed.drained += lost;
					CursedAttacks.heal(ctx, CursedRules.heal(lost, ctx.cfg().pentagramHeal));
					ctx.fx.burst(ParticleTypes.DAMAGE_INDICATOR, chest.x, chest.y + 0.2, chest.z, 2, 0.2, 0.1, 0.2, 0.05);
					for (int k = 0; k < 4; k++) {
						ctx.fx.dot(RitualFx.trail(core, k % 2 == 0 ? CursedFx.BLOOD_BRIGHT : CursedFx.BLOOD, 12 + k * 2), chest.x + ctx.random.nextGaussian() * 0.15,
								chest.y + ctx.random.nextGaussian() * 0.2, chest.z + ctx.random.nextGaussian() * 0.15);
					}
					ctx.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 1.3F);
				}
			}
		}
		return age < telegraph + active + FADE_TICKS;
	}
}
