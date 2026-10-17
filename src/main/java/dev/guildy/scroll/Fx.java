package dev.guildy.scroll;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The particles of one effect for one tick: shapes (dots with exact velocities, bursts, lines, rings, arcs, spheres,
 * runic star polygons) collected as long-distance particle packets, within a particle budget, then sent in one bundle to
 * every player of the level close enough to the effect. Shapes beyond the budget are silently skipped: the animation gets
 * lighter, the gameplay never changes.
 */
final class Fx {
	/** Vanilla limit of packets in one bundle. */
	private static final int MAX_BUNDLE = 4000;
	private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

	private final ServerLevel level;
	private final Vec3 anchor;
	private final double viewDistance;
	private final List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
	private int budget;
	private int used;

	Fx(ServerLevel level, Vec3 anchor, int budget, double viewDistance) {
		this.level = level;
		this.anchor = anchor;
		this.budget = Math.max(0, budget);
		this.viewDistance = viewDistance;
	}

	/** Particles spent this tick. */
	int used() {
		return used;
	}

	private boolean spend(int n) {
		if (budget < n) {
			return false;
		}
		budget -= n;
		used += n;
		return true;
	}

	// ================================================================== particle options

	static ParticleOptions dust(int rgb, float scale) {
		return new DustParticleOptions(rgb & 0xFFFFFF, scale);
	}

	/** Dust whose color fades from {@code from} to {@code to} during its life. */
	static ParticleOptions fade(int from, int to, float scale) {
		return new DustColorTransitionOptions(from & 0xFFFFFF, to & 0xFFFFFF, scale);
	}

	static ParticleOptions flash(int rgb) {
		return ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | rgb & 0xFFFFFF);
	}

	/** A full-bright dot traveling in a straight line to {@code target} in {@code ticks} ticks. */
	static ParticleOptions trail(Vec3 target, int rgb, int ticks) {
		return new TrailParticleOption(target, rgb & 0xFFFFFF, Math.max(1, ticks));
	}

	/** RGB linear mix (t = 0: a, t = 1: b). */
	static int mix(int a, int b, double t) {
		double k = Math.max(0, Math.min(1, t));
		int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
		int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
		int bl = (int) Math.round((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
		return r << 16 | g << 8 | bl;
	}

	// ================================================================== primitives

	/** One particle at an exact position, with an exact velocity (blocks per tick). */
	boolean move(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		if (!spend(1)) {
			return false;
		}
		packets.add(new ClientboundLevelParticlesPacket(particle, true, false, x, y, z, 0.0F, 0.0F, 0.0F, (float) vx, (float) vy, (float) vz, 1,
				ClientboundLevelParticlesPacket.RandomizationType.ALTERNATIVE));
		return true;
	}

	boolean move(ParticleOptions particle, Vec3 pos, Vec3 velocity) {
		return move(particle, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
	}

	/** One still particle. */
	boolean dot(ParticleOptions particle, double x, double y, double z) {
		return move(particle, x, y, z, 0, 0, 0);
	}

	boolean dot(ParticleOptions particle, Vec3 pos) {
		return dot(particle, pos.x, pos.y, pos.z);
	}

	/** {@code count} particles scattered around a point (gaussian spread, random speed up to {@code speed}). */
	boolean burst(ParticleOptions particle, double x, double y, double z, int count, double spreadX, double spreadY, double spreadZ, double speed) {
		if (count <= 0 || !spend(count)) {
			return false;
		}
		packets.add(new ClientboundLevelParticlesPacket(particle, true, false, x, y, z, (float) spreadX, (float) spreadY, (float) spreadZ,
				(float) speed, count));
		return true;
	}

	boolean burst(ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
		return burst(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
	}

	// ================================================================== shapes

	/** A straight line of particles ({@code perBlock} per block, 2 at least). */
	void line(ParticleOptions particle, Vec3 from, Vec3 to, double perBlock) {
		Vec3 delta = to.subtract(from);
		int steps = (int) Math.max(1, Math.min(160, Math.ceil(delta.length() * perBlock)));
		for (int i = 0; i <= steps; i++) {
			if (!dot(particle, from.add(delta.scale((double) i / steps)))) {
				return;
			}
		}
	}

	/** A jagged electric line: {@code segments} kinks displaced up to {@code jitter} blocks, sparks at each kink. */
	void bolt(ParticleOptions core, Vec3 from, Vec3 to, int segments, double jitter, java.util.random.RandomGenerator random) {
		Vec3 previous = from;
		for (int i = 1; i <= segments; i++) {
			Vec3 point = from.add(to.subtract(from).scale((double) i / segments));
			if (i < segments) {
				point = point.add((random.nextDouble() - 0.5) * 2 * jitter, (random.nextDouble() - 0.5) * 2 * jitter,
						(random.nextDouble() - 0.5) * 2 * jitter);
			}
			line(core, previous, point, 4);
			previous = point;
		}
	}

	/** A horizontal circle of {@code points} particles, rotated by {@code phase} radians. */
	void ring(ParticleOptions particle, Vec3 center, double radius, int points, double phase) {
		arc(particle, center, radius, phase, phase + Math.PI * 2, points, false);
	}

	/**
	 * Part of a horizontal circle from angle {@code from} to {@code to} ({@code points} particles over the full arc);
	 * {@code outward}: particles drift slowly away from the center.
	 */
	void arc(ParticleOptions particle, Vec3 center, double radius, double from, double to, int points, boolean outward) {
		int n = Math.max(1, points);
		for (int i = 0; i < n; i++) {
			double angle = from + (to - from) * i / n;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			boolean ok = outward
					? move(particle, center.x + cos * radius, center.y, center.z + sin * radius, cos * 0.04, 0.0, sin * 0.04)
					: dot(particle, center.x + cos * radius, center.y, center.z + sin * radius);
			if (!ok) {
				return;
			}
		}
	}

	/** Points evenly spread on a sphere (Fibonacci lattice), rotated around the vertical axis by {@code phase}. */
	void sphere(ParticleOptions particle, Vec3 center, double radius, int points, double phase, boolean upperHalfOnly) {
		int n = Math.max(4, points);
		for (int i = 0; i < n; i++) {
			double y = 1 - (i + 0.5) * 2.0 / n;
			if (upperHalfOnly && y < 0) {
				y = -y;
			}
			double r = Math.sqrt(Math.max(0, 1 - y * y));
			double theta = GOLDEN_ANGLE * i + phase;
			if (!dot(particle, center.x + Math.cos(theta) * r * radius, center.y + y * radius, center.z + Math.sin(theta) * r * radius)) {
				return;
			}
		}
	}

	/** Same as {@link #sphere}, every particle drifting away from the center at {@code speed} blocks per tick. */
	void sphereBurst(ParticleOptions particle, Vec3 center, double radius, int points, double speed) {
		int n = Math.max(4, points);
		for (int i = 0; i < n; i++) {
			double y = 1 - (i + 0.5) * 2.0 / n;
			double r = Math.sqrt(Math.max(0, 1 - y * y));
			double theta = GOLDEN_ANGLE * i;
			double dx = Math.cos(theta) * r;
			double dz = Math.sin(theta) * r;
			if (!move(particle, center.x + dx * radius, center.y + y * radius, center.z + dz * radius, dx * speed, y * speed, dz * speed)) {
				return;
			}
		}
	}

	/**
	 * A horizontal star polygon (runic sign): {@code vertices} points on a circle, each joined to the one {@code step}
	 * further (5 / 2 = pentagram, 6 / 2 = two triangles, 8 / 3 = octagram). {@code progress} (0..1) draws only the first
	 * part of the strokes.
	 */
	void polygram(ParticleOptions particle, Vec3 center, double radius, int vertices, int step, double phase, double progress, double perBlock) {
		int strokes = vertices;
		double drawn = Math.max(0, Math.min(1, progress)) * strokes;
		for (int i = 0; i < strokes && i < drawn; i++) {
			double a0 = phase + Math.PI * 2 * i * step / vertices;
			double a1 = phase + Math.PI * 2 * (i + 1) * step / vertices;
			Vec3 from = new Vec3(center.x + Math.cos(a0) * radius, center.y, center.z + Math.sin(a0) * radius);
			Vec3 to = new Vec3(center.x + Math.cos(a1) * radius, center.y, center.z + Math.sin(a1) * radius);
			double part = Math.min(1, drawn - i);
			line(particle, from, from.add(to.subtract(from).scale(part)), perBlock);
		}
	}

	/**
	 * Runic circle on the ground: outer and inner circles drawn clockwise with the progress, a rotating star polygon,
	 * small glyph marks between the circles.
	 */
	void runeCircle(Vec3 center, double radius, int color, int accent, double progress, double phase) {
		double p = Math.max(0, Math.min(1, progress));
		int outer = (int) Math.max(12, radius * 14);
		ParticleOptions main = dust(color, 0.9F);
		ParticleOptions light = dust(accent, 0.7F);
		arc(main, center, radius, phase, phase + Math.PI * 2 * p, (int) Math.ceil(outer * p), false);
		arc(light, center, radius * 0.78, -phase, -phase - Math.PI * 2 * p, (int) Math.ceil(outer * 0.75 * p), false);
		if (p > 0.25) {
			polygram(main, center, radius * 0.74, 6, 2, phase * 0.5, (p - 0.25) / 0.75, 3.5);
		}
		if (p > 0.5) {
			// glyphs: short radial strokes between the two circles
			for (int i = 0; i < 8; i++) {
				double angle = -phase + Math.PI * 2 * i / 8;
				Vec3 a = new Vec3(center.x + Math.cos(angle) * radius * 0.82, center.y, center.z + Math.sin(angle) * radius * 0.82);
				Vec3 b = new Vec3(center.x + Math.cos(angle) * radius * 0.96, center.y, center.z + Math.sin(angle) * radius * 0.96);
				line(light, a, b, 6);
			}
		}
	}

	// ================================================================== sending

	/** Sends the collected packets to the players close to the effect (one bundle each). */
	void flush() {
		if (packets.isEmpty()) {
			return;
		}
		double max = viewDistance * viewDistance;
		List<ServerPlayer> viewers = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(anchor) <= max) {
				viewers.add(player);
			}
		}
		if (!viewers.isEmpty()) {
			for (int start = 0; start < packets.size(); start += MAX_BUNDLE) {
				List<Packet<? super ClientGamePacketListener>> part = packets.subList(start, Math.min(packets.size(), start + MAX_BUNDLE));
				ClientboundBundlePacket bundle = new ClientboundBundlePacket(new ArrayList<>(part));
				for (ServerPlayer viewer : viewers) {
					viewer.connection.send(bundle);
				}
			}
		}
		packets.clear();
	}
}
