package dev.guildy.scroll;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.random.RandomGenerator;

/**
 * The cursed look of the cursed grimoire, drawn on top of the normal animations (never instead of them): an inverted
 * blood pentagram under the caster while channeling, a burst of blood, ink and souls at the cast, and on every running spell
 * dark motes, a slow ring of blood and wandering souls around its anchor, with a blood mark where it strikes (meteor
 * impacts, lightning jumps, the singularity's implosion). Darker sounds (wither, souls, heartbeat). Every particle goes
 * through the caller's {@link Fx}, so the shared particle budgets apply.
 */
final class CursedSpellFx {
	static final int VOID = 0x1A0026;
	static final int PURPLE = 0x7A1FD6;
	static final int VIOLET = 0xC97BFF;
	static final int BLOOD = 0x8E0012;
	static final int BLOOD_BRIGHT = 0xFF1A2B;
	/** Largest radius of the overlay ring (blocks): big areas only get a ring near their center. */
	private static final double MAX_RING = 14;

	private CursedSpellFx() {
	}

	/** Page turned or scroll bound: a whiff of blood and darkness around the book. */
	static void flourish(Fx fx, Vec3 book) {
		fx.burst(Fx.fade(BLOOD_BRIGHT, VOID, 0.8F), book, 6, 0.25, 0.02);
		fx.burst(ParticleTypes.SMOKE, book, 4, 0.2, 0.01);
	}

	// ================================================================== channel and cast

	/**
	 * Channel layer under the normal runic circle: an inverted pentagram of blood drawn with the circle (every other tick),
	 * a dark outer ring, black smoke rising from its rim, blood dripping from the hands, a soul now and then.
	 */
	static void channel(Fx fx, Vec3 feet, double radius, double progress, double phase, long elapsed, RandomGenerator random) {
		if (elapsed % 2 == 0) {
			fx.polygram(Fx.fade(BLOOD_BRIGHT, VOID, 0.8F), feet.add(0, 0.02, 0), radius * 0.95, 5, 2, -phase * 0.7 + Math.PI / 2, progress, 3);
			fx.ring(Fx.dust(VOID, 1.2F), feet.add(0, 0.03, 0), radius * 1.18, (int) Math.ceil(26 * progress), phase * 0.6);
		}
		if (elapsed % 3 == 0) {
			double angle = random.nextDouble() * Math.PI * 2;
			fx.move(ParticleTypes.LARGE_SMOKE, feet.x + Math.cos(angle) * radius, feet.y + 0.1, feet.z + Math.sin(angle) * radius, 0, 0.06, 0);
		}
		if (elapsed % 4 == 1) {
			fx.burst(Fx.dust(BLOOD, 0.9F), feet.x, feet.y + 1.0, feet.z, 2, 0.3, 0.1, 0.3, 0.0);
		}
		if (elapsed % 9 == 0) {
			fx.move(ParticleTypes.SOUL, feet.x + (random.nextDouble() - 0.5) * radius, feet.y + 0.2, feet.z + (random.nextDouble() - 0.5) * radius,
					0, 0.04, 0);
		}
	}

	/** Every few ticks of the channel: a slow heartbeat. */
	static void channelSound(ServerPlayer player, long elapsed, double progress) {
		if (elapsed % 16 == 8) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9F,
					0.7F + (float) progress * 0.4F);
		}
	}

	/** The start of a cursed incantation: a dark sound under the normal one. */
	static void beginSound(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.6F, 0.5F);
	}

	/** The cast: the pentagram blazes, blood bursts from the caster, ink and souls; wither shot, souls and heartbeat. */
	static void cast(Fx fx, ServerPlayer player, double radius, double phase) {
		Vec3 feet = player.position().add(0, 0.08, 0);
		Vec3 chest = player.position().add(0, 1.1, 0);
		fx.polygram(Fx.dust(BLOOD_BRIGHT, 1.1F), feet, radius * 0.95, 5, 2, -phase * 0.7 + Math.PI / 2, 1.0, 4);
		fx.dot(Fx.flash(VIOLET), chest);
		fx.sphereBurst(Fx.fade(BLOOD_BRIGHT, VOID, 1.4F), chest, 0.3, 36, 0.28);
		fx.burst(ParticleTypes.SQUID_INK, chest, 14, 0.4, 0.08);
		fx.burst(ParticleTypes.SOUL, feet.x, feet.y + 0.3, feet.z, 10, radius * 0.5, 0.2, radius * 0.5, 0.05);
		fx.arc(Fx.dust(PURPLE, 1.2F), feet, radius * 1.2, 0, Math.PI * 2, 28, true);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.8F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 2.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.6F);
	}

	/** The blood price paid: a few drops of blood falling from the caster. */
	static void bleed(Fx fx, ServerPlayer player) {
		fx.burst(Fx.dust(BLOOD, 1.0F), player.getX(), player.getY(0.6), player.getZ(), 8, 0.25, 0.35, 0.25, 0.0);
		fx.burst(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY(0.7), player.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
	}

	// ================================================================== running spells

	/**
	 * One tick of the overlay of a running cursed spell, around its anchor: rising dark motes, a slow ring of blood (the
	 * spell's area, at most {@link #MAX_RING} blocks), wandering souls and smoke; a dark sound when it starts.
	 */
	static void overlay(SpellEffect effect, Fx fx) {
		Vec3 at = effect.anchor();
		int age = effect.age;
		RandomGenerator random = effect.random;
		double r = areaRadius(effect.ctx);
		if (age == 0) {
			effect.sound(at, SoundEvents.WITHER_AMBIENT, 1.2F, 0.5F);
			effect.sound(at, SoundEvents.SOUL_ESCAPE, 2.0F, 0.5F);
		}
		if (age % 2 == 0) {
			for (int i = 0; i < 3; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double d = Math.sqrt(random.nextDouble()) * r;
				fx.move(Fx.dust(i == 0 ? PURPLE : VOID, 1.5F), at.x + Math.cos(angle) * d, at.y + 0.2 + random.nextDouble(), at.z + Math.sin(angle) * d,
						0, 0.05, 0);
			}
		}
		if (age % 5 == 0) {
			int points = (int) Math.max(10, Math.min(48, r * 4));
			fx.ring(Fx.fade(BLOOD_BRIGHT, VOID, 1.1F), at.add(0, 0.15, 0), r * 0.9, points, -age * 0.07);
		}
		if (age % 7 == 3) {
			fx.burst(ParticleTypes.SOUL, at.x, at.y + 0.5, at.z, 2, r * 0.4, 0.3, r * 0.4, 0.02);
		}
		if (age % 6 == 0) {
			fx.burst(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 2, r * 0.3, 0.2, r * 0.3, 0.01);
		}
	}

	/** Where a cursed spell strikes: blood and ink burst, a purple ring. */
	static void mark(Fx fx, Vec3 pos, double radius, int age) {
		double r = Math.max(0.6, Math.min(radius, 6));
		fx.burst(Fx.fade(BLOOD_BRIGHT, VOID, 1.6F), pos.x, pos.y + 0.4, pos.z, 10, r * 0.4, 0.3, r * 0.4, 0.05);
		fx.burst(ParticleTypes.SQUID_INK, pos.x, pos.y + 0.5, pos.z, 4, r * 0.3, 0.2, r * 0.3, 0.03);
		fx.ring(Fx.dust(PURPLE, 1.2F), pos.add(0, 0.2, 0), r * 0.8, 14, age * 0.3);
	}

	/** Size of a spell's area: its radius when it has one (bounded), else a small circle around its anchor. */
	static double areaRadius(SpellContext ctx) {
		if (ctx.spell.param("radius") == null) {
			return 1.5;
		}
		return Math.max(1.0, Math.min(MAX_RING, ctx.p("radius")));
	}
}
