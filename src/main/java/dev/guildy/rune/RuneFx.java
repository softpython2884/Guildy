package dev.guildy.rune;

import dev.guildy.Guildy;
import dev.guildy.core.Safe;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated effects of the runes (inscriptions, powers): an animation is ticked once when played, then every server tick
 * until it says it is done or reaches its maximum age (bounded number of running animations, each tick isolated by
 * {@link Safe}); plus the particle shapes they share.
 */
final class RuneFx {
	/** One animation step: {@code age} 0 on the first call. @return false when finished */
	@FunctionalInterface
	interface Animation {
		boolean tick(ServerLevel level, int age);
	}

	private static final int MAX_RUNNING = 256;
	private static final List<Running> RUNNING = new ArrayList<>();
	private static final List<Running> STARTED = new ArrayList<>();
	private static boolean ticking;
	private static boolean warned;

	/** Colours of the tiers and powers. */
	static final int COMMON = 0x7CF29A;
	static final int RARE = 0x5BD7F2;
	static final int MYTHIC = 0xD86BFF;
	static final int GOLD = 0xFFD45E;
	static final int EMBER = 0xFF7A1A;
	static final int FLAME = 0xFFB238;
	static final int ASH = 0x4A4A4A;
	static final int DRAGON = 0x7A2BD9;
	static final int DRAGON_DARK = 0x220A3D;

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

	private RuneFx() {
	}

	/** Plays an animation: its first step now, the next ones on the following ticks (at most {@code maxAge} steps). */
	static void play(ServerLevel level, int maxAge, Animation animation) {
		if (RUNNING.size() + STARTED.size() >= MAX_RUNNING) {
			if (!warned) {
				warned = true;
				Guildy.LOGGER.warn("[runes] too many rune animations at once: some are skipped");
			}
			return;
		}
		boolean more = Safe.get("rune.fx", () -> animation.tick(level, 0), false);
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
				boolean more = Safe.get("rune.fx", () -> running.animation.tick(running.level, age), false);
				return !more || running.age >= running.maxAge;
			});
		} finally {
			ticking = false;
		}
		RUNNING.addAll(STARTED);
		STARTED.clear();
	}

	static void clear() {
		RUNNING.clear();
		STARTED.clear();
	}

	// ================================================================== particle options

	static int tierColor(RuneTier tier) {
		return switch (tier) {
			case COMMON -> COMMON;
			case RARE -> RARE;
			case MYTHIC -> MYTHIC;
		};
	}

	static ParticleOptions dust(int rgb, float scale) {
		return new DustParticleOptions(rgb & 0xFFFFFF, scale);
	}

	static ParticleOptions fade(int from, int to, float scale) {
		return new DustColorTransitionOptions(from & 0xFFFFFF, to & 0xFFFFFF, scale);
	}

	static ParticleOptions flash(int rgb) {
		return ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | rgb & 0xFFFFFF);
	}

	// ================================================================== shapes

	static void dot(ServerLevel level, ParticleOptions particle, Vec3 pos) {
		level.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
	}

	/** One particle with an exact velocity (blocks per tick); for the enchanting glyphs: the offset they fly in from. */
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
		int n = Math.max(1, points);
		for (int i = 0; i < n; i++) {
			double angle = phase + Math.PI * 2 * i / n;
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

	/** The horizontal unit vector of {@code v} (south when vertical). */
	static Vec3 horizontal(Vec3 v) {
		Vec3 flat = new Vec3(v.x, 0, v.z);
		return flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
	}
}
