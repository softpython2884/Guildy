package dev.guildy.ruin;

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
 * The particles of one ritual (or incantation) for one tick: shapes collected as long-distance particle packets within a
 * particle budget, then sent in one bundle to every player of the level close enough. Shapes beyond the budget are
 * silently skipped: the animation gets lighter, the ritual never changes. Same approach as the spell scrolls' effects.
 */
final class RitualFx {
	/** Vanilla limit of packets in one bundle. */
	private static final int MAX_BUNDLE = 4000;
	private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

	private final ServerLevel level;
	private final Vec3 anchor;
	private final double viewDistance;
	private final List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
	private int budget;
	private int used;

	RitualFx(ServerLevel level, Vec3 anchor, int budget, double viewDistance) {
		this.level = level;
		this.anchor = anchor;
		this.budget = Math.max(0, budget);
		this.viewDistance = viewDistance;
	}

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

	/** Dust whose colour fades from {@code from} to {@code to} during its life (blood turning into the ruin's colour). */
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
		int steps = (int) Math.max(1, Math.min(120, Math.ceil(delta.length() * perBlock)));
		for (int i = 0; i <= steps; i++) {
			if (!dot(particle, from.add(delta.scale((double) i / steps)))) {
				return;
			}
		}
	}

	/** Part of a horizontal circle from angle {@code from} to {@code to} ({@code points} particles over the arc). */
	void arc(ParticleOptions particle, Vec3 center, double radius, double from, double to, int points) {
		int n = Math.max(1, points);
		for (int i = 0; i < n; i++) {
			double angle = from + (to - from) * i / n;
			if (!dot(particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius)) {
				return;
			}
		}
	}

	void ring(ParticleOptions particle, Vec3 center, double radius, int points, double phase) {
		arc(particle, center, radius, phase, phase + Math.PI * 2, points);
	}

	/**
	 * A horizontal star polygon: {@code vertices} points on a circle, each joined to the one {@code step} further
	 * (8 / 3 = octagram, 3 / 1 = triangle). {@code progress} (0..1) draws only the first part of the strokes.
	 */
	void polygram(ParticleOptions particle, Vec3 center, double radius, int vertices, int step, double phase, double progress, double perBlock) {
		double drawn = Math.max(0, Math.min(1, progress)) * vertices;
		for (int i = 0; i < vertices && i < drawn; i++) {
			double a0 = phase + Math.PI * 2 * i * step / vertices;
			double a1 = phase + Math.PI * 2 * (i + 1) * step / vertices;
			Vec3 from = new Vec3(center.x + Math.cos(a0) * radius, center.y, center.z + Math.sin(a0) * radius);
			Vec3 to = new Vec3(center.x + Math.cos(a1) * radius, center.y, center.z + Math.sin(a1) * radius);
			double part = Math.min(1, drawn - i);
			line(particle, from, from.add(to.subtract(from).scale(part)), perBlock);
		}
	}

	/** Every particle of a sphere drifting away from its center at {@code speed} blocks per tick. */
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

	/** Point of a quadratic Bezier curve (t = 0: a, t = 1: b, pulled towards {@code control}). */
	static Vec3 bezier(Vec3 a, Vec3 control, Vec3 b, double t) {
		double u = 1 - t;
		return new Vec3(u * u * a.x + 2 * u * t * control.x + t * t * b.x, u * u * a.y + 2 * u * t * control.y + t * t * b.y,
				u * u * a.z + 2 * u * t * control.z + t * t * b.z);
	}

	// ================================================================== sending

	/** Sends the collected packets to the players close to the anchor (one bundle each). */
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
