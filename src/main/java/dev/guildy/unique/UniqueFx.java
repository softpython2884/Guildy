package dev.guildy.unique;

import dev.guildy.Guildy;
import dev.guildy.core.Safe;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated effects of the powers: an animation is ticked once when played, then every server tick until it says it is
 * done or reaches its maximum age (bounded number of running animations, every tick isolated by {@link Safe}). Some
 * animations carry the gameplay of their power (a rift that advances, a pentagram that bursts at the end); they only
 * ever affect monsters. Also the particle shapes shared by the powers (rings, arcs, lines, columns, star polygons).
 */
final class UniqueFx {
	/** One animation step: {@code age} 0 on the first call. @return false when finished */
	@FunctionalInterface
	interface Animation {
		boolean tick(ServerLevel level, int age);
	}

	private static final int MAX_RUNNING = 512;
	private static final List<Running> RUNNING = new ArrayList<>();
	/** Animations started while the running ones tick (added after the pass). */
	private static final List<Running> STARTED = new ArrayList<>();
	private static boolean ticking;
	private static boolean warned;

	private static final class Running {
		final ServerLevel level;
		final Animation animation;
		final int maxAge;
		int age;

		Running(ServerLevel level, Animation animation, int maxAge) {
			this.level = level;
			this.animation = animation;
			this.maxAge = maxAge;
		}
	}

	private UniqueFx() {
	}

	/** Plays an animation: its first step now, the next ones on the following ticks (at most {@code maxAge} steps). */
	static void play(ServerLevel level, int maxAge, Animation animation) {
		if (RUNNING.size() + STARTED.size() >= MAX_RUNNING) {
			if (!warned) {
				warned = true;
				Guildy.LOGGER.warn("[uniques] too many power animations at once: some are skipped");
			}
			return;
		}
		boolean more = Safe.get("unique.fx", () -> animation.tick(level, 0), false);
		if (more && maxAge > 1) {
			Running running = new Running(level, animation, maxAge);
			running.age = 1;
			(ticking ? STARTED : RUNNING).add(running);
		}
	}

	/** Every server tick. */
	static void tick() {
		if (RUNNING.isEmpty()) {
			return;
		}
		ticking = true;
		try {
			RUNNING.removeIf(running -> {
				int age = running.age++;
				boolean more = Safe.get("unique.fx", () -> running.animation.tick(running.level, age), false);
				return !more || running.age >= running.maxAge;
			});
		} finally {
			ticking = false;
		}
		RUNNING.addAll(STARTED);
		STARTED.clear();
	}

	static int running() {
		return RUNNING.size();
	}

	static void clear() {
		RUNNING.clear();
		STARTED.clear();
	}

	// ================================================================== particle options

	static ParticleOptions dust(int rgb, float scale) {
		return new DustParticleOptions(rgb & 0xFFFFFF, scale);
	}

	/** Dust whose colour fades from {@code from} to {@code to} during its life. */
	static ParticleOptions fade(int from, int to, float scale) {
		return new DustColorTransitionOptions(from & 0xFFFFFF, to & 0xFFFFFF, scale);
	}

	static ParticleOptions flash(int rgb) {
		return ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | rgb & 0xFFFFFF);
	}

	/** A full-bright dot travelling in a straight line to {@code target} in {@code ticks} ticks. */
	static ParticleOptions trail(Vec3 target, int rgb, int ticks) {
		return new TrailParticleOption(target, rgb & 0xFFFFFF, Math.max(1, ticks));
	}

	// ================================================================== shapes

	static void dot(ServerLevel level, ParticleOptions particle, Vec3 pos) {
		level.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
	}

	/** One particle with an exact velocity (blocks per tick). */
	static void move(ServerLevel level, ParticleOptions particle, Vec3 pos, Vec3 velocity) {
		double speed = velocity.length();
		if (speed < 1.0E-6) {
			dot(level, particle, pos);
			return;
		}
		Vec3 dir = velocity.scale(1 / speed);
		level.sendParticles(particle, pos.x, pos.y, pos.z, 0, dir.x, dir.y, dir.z, speed);
	}

	static void burst(ServerLevel level, ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
		level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
	}

	/** A horizontal ring, rotated by {@code phase} radians. */
	static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points, double phase) {
		arc(level, particle, center, radius, phase, phase + Math.PI * 2, points);
	}

	/** A horizontal arc from angle {@code from} to {@code to} (radians). */
	static void arc(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, double from, double to, int points) {
		int n = Math.max(1, points);
		for (int i = 0; i < n; i++) {
			double angle = from + (to - from) * i / n;
			dot(level, particle, center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
		}
	}

	/** Particles every {@code 1 / perBlock} block between two points (64 at most). */
	static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double perBlock) {
		Vec3 delta = to.subtract(from);
		int steps = (int) Math.min(64, Math.max(1, delta.length() * perBlock));
		for (int i = 0; i <= steps; i++) {
			dot(level, particle, from.add(delta.scale((double) i / steps)));
		}
	}

	/** A vertical column of {@code points} particles. */
	static void column(ServerLevel level, ParticleOptions particle, Vec3 base, double height, int points) {
		int n = Math.max(1, points);
		for (int i = 0; i < n; i++) {
			dot(level, particle, base.add(0, height * i / Math.max(1, n - 1), 0));
		}
	}

	/**
	 * A flat star polygon {n/step} (5/2 = pentagram) of radius {@code radius}, its first point at angle {@code spin};
	 * {@code progress} (0..1) draws its edges one after the other.
	 */
	static void star(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int n, int step, double spin, double progress,
			double perBlock) {
		double p = Math.max(0, Math.min(1, progress)) * n;
		for (int edge = 0; edge < n && edge < p; edge++) {
			double a = spin + Math.PI * 2 * edge * step / n;
			double b = spin + Math.PI * 2 * (edge + 1) * step / n;
			Vec3 from = center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
			Vec3 to = center.add(Math.cos(b) * radius, 0, Math.sin(b) * radius);
			double part = Math.min(1, p - edge);
			line(level, particle, from, from.add(to.subtract(from).scale(part)), perBlock);
		}
	}

	/** Two strands spiralling up around {@code center}, drawn up to {@code height} (one point per strand per call). */
	static void helix(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, double height, int age, int period) {
		double t = (age % period) / (double) period;
		for (int strand = 0; strand < 2; strand++) {
			double angle = Math.PI * 2 * (t * 2 + strand / 2.0);
			dot(level, particle, center.add(Math.cos(angle) * radius, height * t, Math.sin(angle) * radius));
		}
	}
}
