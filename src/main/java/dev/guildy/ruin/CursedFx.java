package dev.guildy.ruin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

import java.util.SplittableRandom;

/**
 * Drawing helpers of the cursed rituals and fights, on top of the budgeted {@link RitualFx} (every shape is cut when the
 * tick's particle budget runs out): inverted pentagrams in double rune circles, warning circles of the telegraphed attacks,
 * sagging chains of souls, blood streams flowing into the guardian, jagged electric arcs, visual lightning. Colours: void
 * (black purple), purple, violet, blood.
 */
final class CursedFx {
	static final int VOID = 0x1A0026;
	static final int PURPLE = 0x7A1FD6;
	static final int VIOLET = 0xC97BFF;
	static final int BLOOD = 0x8E0012;
	static final int BLOOD_BRIGHT = 0xFF1A2B;
	static final int SOUL = 0x5FE6FF;
	static final int WARNING = 0xFF3030;
	static final int ELECTRIC = 0xE8E2FF;

	private CursedFx() {
	}

	/**
	 * An inverted pentagram in a double rune circle, flat on the floor: {@code progress} (0..1) draws the outer ring, then
	 * the star, then the outer ring of runes. {@code spin} (radians) rotates it.
	 */
	static void pentagram(RitualFx fx, Vec3 c, double radius, double spin, double progress, int color, int accent, float size) {
		double p = Math.max(0, Math.min(1, progress));
		double ring = SummonRules.phase(p, 0.0, 0.35);
		double star = SummonRules.phase(p, 0.2, 0.75);
		double inner = SummonRules.phase(p, 0.55, 1.0);
		int points = (int) Math.ceil(Math.max(16, radius * 11) * ring);
		fx.arc(RitualFx.dust(color, size), c, radius, spin, spin + Math.PI * 2 * ring, points);
		if (star > 0) {
			// Inverted: the lone point at spin - 90 degrees, the two "horns" on the other side.
			fx.polygram(RitualFx.dust(accent, size), c, radius * 0.97, 5, 2, spin + Math.PI / 2 + Math.PI, star, Math.max(1.6, 3.2 - radius * 0.15));
		}
		if (inner > 0) {
			fx.arc(RitualFx.fade(color, VOID, size * 0.85F), c, radius * 1.14, -spin, -spin - Math.PI * 2 * inner, (int) Math.ceil(radius * 12 * inner));
			runes(fx, c, radius * 1.0, radius * 1.13, Math.max(5, (int) Math.round(radius * 3)), -spin, RitualFx.dust(accent, size * 0.75F), inner);
		}
	}

	/** Rune strokes between two rings: radial bars, every other one crossed. */
	static void runes(RitualFx fx, Vec3 c, double rIn, double rOut, int count, double spin, ParticleOptions particle, double progress) {
		int n = (int) Math.ceil(count * Math.max(0, Math.min(1, progress)));
		for (int i = 0; i < n; i++) {
			double angle = spin + Math.PI * 2 * i / count;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			Vec3 in = new Vec3(c.x + cos * rIn, c.y, c.z + sin * rIn);
			Vec3 out = new Vec3(c.x + cos * rOut, c.y, c.z + sin * rOut);
			fx.line(particle, in, out, 6);
			if (i % 2 == 0) {
				Vec3 mid = in.add(out).scale(0.5);
				Vec3 side = new Vec3(-sin * 0.18, 0, cos * 0.18);
				fx.line(particle, mid.subtract(side), mid.add(side), 7);
			}
		}
	}

	/**
	 * Warning circle of a telegraphed attack: a red ring on the floor and an inner ring closing on it as the strike nears
	 * ({@code k} = 0 at the warning, 1 at the strike).
	 */
	static void warning(RitualFx fx, Vec3 c, double radius, double k, double phase) {
		double t = Math.max(0, Math.min(1, k));
		fx.ring(RitualFx.dust(WARNING, 1.0F), c, radius, (int) Math.max(14, radius * 9), phase);
		double inner = radius * t;
		if (inner > 0.2) {
			fx.ring(RitualFx.fade(WARNING, VOID, 0.8F), c, inner, (int) Math.max(8, inner * 7), -phase);
		}
	}

	/** A chain of souls between two points, sagging under its weight, links alternating flame and dark dust. */
	static void chain(RitualFx fx, Vec3 from, Vec3 to, double sag, int age, boolean solid) {
		Vec3 delta = to.subtract(from);
		double dist = delta.length();
		if (dist < 0.5) {
			return;
		}
		Vec3 control = from.add(to).scale(0.5).add(0, -Math.max(0, sag), 0);
		int links = (int) Math.max(6, Math.min(48, dist * 2.2));
		ParticleOptions dark = RitualFx.dust(VOID, 1.0F);
		ParticleOptions glow = RitualFx.dust(solid ? SOUL : PURPLE, 0.8F);
		for (int i = 0; i <= links; i++) {
			if (!solid && (i + age) % 3 != 0) {
				continue;
			}
			Vec3 p = RitualFx.bezier(from, control, to, (double) i / links);
			boolean flame = (i + age / 2) % 4 == 0;
			if (!fx.dot(flame ? ParticleTypes.SOUL_FIRE_FLAME : i % 2 == 0 ? dark : glow, p)) {
				return;
			}
		}
	}

	/** A stream of blood flowing from {@code from} into {@code to}: a faint curve and pulses running along it. */
	static void stream(RitualFx fx, Vec3 from, Vec3 to, int age, int index) {
		Vec3 delta = to.subtract(from);
		double dist = delta.length();
		if (dist < 0.6) {
			return;
		}
		Vec3 side = new Vec3(-delta.z, 0, delta.x);
		side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
		double sway = Math.sin(age * 0.15 + index * 1.9) * Math.min(1.5, 0.2 * dist);
		Vec3 control = from.add(to).scale(0.5).add(0, 0.8 + dist * 0.12, 0).add(side.scale(sway));
		if (age % 2 == 0) {
			int n = (int) Math.max(6, Math.min(30, dist * 1.4));
			ParticleOptions path = RitualFx.fade(BLOOD, PURPLE, 0.6F);
			for (int k = 0; k <= n; k++) {
				if (!fx.dot(path, RitualFx.bezier(from, control, to, (double) k / n))) {
					return;
				}
			}
		}
		ParticleOptions head = RitualFx.dust(BLOOD_BRIGHT, 1.3F);
		for (int k = 0; k < 3; k++) {
			double t = frac(age / 14.0 + k / 3.0 + index * 0.17);
			fx.dot(head, RitualFx.bezier(from, control, to, t));
		}
	}

	/** A jagged electric arc ({@code segments} kinks displaced up to {@code jitter} blocks). */
	static void jagged(RitualFx fx, ParticleOptions particle, Vec3 from, Vec3 to, int segments, double jitter, SplittableRandom random) {
		Vec3 previous = from;
		int n = Math.max(2, segments);
		for (int i = 1; i <= n; i++) {
			Vec3 next = from.add(to.subtract(from).scale((double) i / n));
			if (i < n) {
				next = next.add(random.nextGaussian() * jitter, random.nextGaussian() * jitter, random.nextGaussian() * jitter);
			}
			fx.line(particle, previous, next, 4);
			previous = next;
		}
	}

	/** Spheres of dust collapsing into a point (energy gathering): {@code k} = 0 wide, 1 collapsed. */
	static void implode(RitualFx fx, Vec3 core, double radius, double k, int color, int points, double phase) {
		double r = Math.max(0.2, radius * (1 - Math.max(0, Math.min(1, k))));
		int n = Math.max(6, points);
		double golden = Math.PI * (3 - Math.sqrt(5));
		for (int i = 0; i < n; i++) {
			double y = 1 - (i + 0.5) * 2.0 / n;
			double rr = Math.sqrt(Math.max(0, 1 - y * y));
			double theta = golden * i + phase;
			if (!fx.dot(RitualFx.dust(color, 1.1F), core.x + Math.cos(theta) * rr * r, core.y + y * r, core.z + Math.sin(theta) * rr * r)) {
				return;
			}
		}
	}

	/** Rising two-strand helix around {@code center} (one height slice per step, like {@link RuinFx#helix}). */
	static void helix(RitualFx fx, ParticleOptions particle, Vec3 center, double radius, int age, double height, int steps) {
		double t = (double) (age % Math.max(1, steps)) / Math.max(1, steps);
		double y = center.y + t * height;
		for (int strand = 0; strand < 2; strand++) {
			double angle = t * Math.PI * 4 + strand * Math.PI;
			fx.dot(particle, center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius);
		}
	}

	/**
	 * A visual-only lightning bolt (sparks instead above copper or a lightning rod, which even a visual strike alters): no
	 * fire, no damage, no block changed.
	 */
	static void bolt(ServerLevel level, Vec3 pos) {
		BlockPos strike = BlockPos.containing(pos.x, pos.y - 1.0E-6, pos.z);
		if (level.isLoaded(strike) && Resurrection.safeStrike(level.getBlockState(strike))) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.setVisualOnly(true);
				bolt.snapTo(pos.x, pos.y, pos.z);
				if (level.addFreshEntity(bolt)) {
					return;
				}
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1, pos.z, 30, 0.2, 1.2, 0.2, 0.3);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.5F, 1.0F);
	}

	static double frac(double value) {
		return value - Math.floor(value);
	}
}
