package dev.guildy.ruin;

import dev.guildy.core.Safe;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Animated particle effects of the ruin bosses: an animation is stepped every tick until it says it is done (or after
 * {@link #MAX_AGE} ticks). Bounded: at most {@link #MAX_RUNNING} animations at once (the oldest are dropped).
 */
final class RuinFx {
	/** One animated effect. */
	@FunctionalInterface
	interface Animation {
		/** @return true to keep running next tick */
		boolean step(ServerLevel level, int age);
	}

	private static final int MAX_RUNNING = 96;
	private static final int MAX_AGE = 200;
	private static final List<Running> RUNNING = new ArrayList<>();
	/** Animations started by another one during {@link #tick} (a volley firing its blades): added after the loop. */
	private static final List<Running> STARTED = new ArrayList<>();
	private static boolean ticking;

	private static final class Running {
		final ResourceKey<Level> dimension;
		final Animation animation;
		int age;

		Running(ResourceKey<Level> dimension, Animation animation) {
			this.dimension = dimension;
			this.animation = animation;
		}
	}

	private RuinFx() {
	}

	/** Starts an animation (its first step runs right away). */
	static void play(ServerLevel level, Animation animation) {
		Running running = new Running(level.dimension(), animation);
		boolean keep = Safe.get("ruin.fx", () -> animation.step(level, 0), false);
		if (!keep) {
			return;
		}
		running.age = 1;
		if (ticking) {
			STARTED.add(running);
		} else {
			add(running);
		}
	}

	private static void add(Running running) {
		if (RUNNING.size() >= MAX_RUNNING) {
			RUNNING.removeFirst();
		}
		RUNNING.add(running);
	}

	static void tick(MinecraftServer server) {
		if (RUNNING.isEmpty()) {
			return;
		}
		ticking = true;
		try {
			Iterator<Running> it = RUNNING.iterator();
			while (it.hasNext()) {
				Running running = it.next();
				ServerLevel level = server.getLevel(running.dimension);
				int age = running.age++;
				if (level == null || age > MAX_AGE || !Safe.get("ruin.fx", () -> running.animation.step(level, age), false)) {
					it.remove();
				}
			}
		} finally {
			ticking = false;
			for (Running running : STARTED) {
				add(running);
			}
			STARTED.clear();
		}
	}

	static void clear() {
		RUNNING.clear();
		STARTED.clear();
	}

	// ------------------------------------------------------------------ drawing helpers

	/** Horizontal ring of {@code points} particles. */
	static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points, double spread) {
		int n = Math.max(4, points);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2 * i / n;
			level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 1, spread, 0.05, spread, 0.0);
		}
	}

	/** Particles along a segment, {@code points} evenly spaced. */
	static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, int points) {
		int n = Math.max(2, points);
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			level.sendParticles(particle, from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.z + (to.z - from.z) * t, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** Rising helix of two strands around {@code center} (one step = one height slice). */
	static void helix(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int age, double height, int steps) {
		double t = (double) (age % Math.max(1, steps)) / Math.max(1, steps);
		double y = center.y + t * height;
		for (int strand = 0; strand < 2; strand++) {
			double angle = t * Math.PI * 4 + strand * Math.PI;
			level.sendParticles(particle, center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
		level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
	}

	static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}
}
