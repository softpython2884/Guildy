package dev.guildy.ruin;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * A sealed incantation: read while room guards still hold the arena ({@link SummonRules.Refusal#GUARDED}), the
 * incantation circle of the reader starts drawing itself on the floor (the same rings and pentagram as the channel,
 * {@code summon.sealSeconds}) while dark strands of the presence close in on it and its rotation stalls; then it cracks
 * (jagged fissures run across the runes, sparks, glass breaking), shatters (its runes fly outwards and turn to ash, a dull
 * broken sound) and the last fragments scatter and fade. The reader sees "Une présence scelle encore l'entité… le cercle
 * se brise." Nothing is consumed.
 * <p>
 * One effect per player; another sealed attempt is ignored until {@code summon.sealCooldownSeconds} after the start (at
 * least the length of the effect). Runtime only, particles bounded: {@code summon.particleBudget} shared between the
 * effects playing, {@value #MAX_BUDGET} per effect at most.
 */
public final class Seals {
	/** Ticks the fissures spread across the runes before the circle shatters. */
	static final int CRACK_TICKS = 5;
	/** Ticks the fragments fly and fade after the shatter. */
	static final int SCATTER_TICKS = 16;
	/** Radii of the reader's circle (same as the channel's). */
	static final double OUTER = 1.6;
	static final double INNER = 1.25;
	private static final int MAX_BUDGET = 260;
	private static final int MIN_BUDGET = 60;
	/** Grey the broken runes fade into. */
	private static final int ASH = 0x3A3A40;
	/** The presence sealing the circle. */
	private static final int SHADOW = 0x15111C;
	private static final Map<UUID, Seal> PLAYING = new LinkedHashMap<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	/** One sealed circle. */
	private static final class Seal {
		final UUID player;
		final ResourceKey<Level> dimension;
		/** Center of the circle, on the reader's floor. */
		final Vec3 center;
		final int color;
		final boolean cursed;
		final int drawTicks;
		final SplittableRandom random;
		/** Angle of the first strand / fissure. */
		final double base;
		/** Fissures: jagged polylines from near the center to beyond the outer ring. */
		final List<List<Vec3>> cracks = new ArrayList<>();
		int age;
		double spin;

		Seal(ServerPlayer player, int color, boolean cursed, int drawTicks, boolean drawn) {
			this.player = player.getUUID();
			this.dimension = player.level().dimension();
			this.center = player.position().add(0, 0.08, 0);
			this.color = color;
			this.cursed = cursed;
			this.drawTicks = Math.max(1, drawTicks);
			this.random = new SplittableRandom();
			this.base = random.nextDouble() * Math.PI * 2;
			this.age = drawn ? this.drawTicks : 0;
			int count = cursed ? 6 : 5;
			for (int i = 0; i < count; i++) {
				double angle = base + Math.PI * 2 * i / count + (random.nextDouble() - 0.5) * 0.5;
				List<Vec3> crack = fissure(angle, 0.12 + random.nextDouble() * 0.2, OUTER + 0.25 + random.nextDouble() * 0.35, 5);
				cracks.add(crack);
				if (random.nextBoolean()) {
					// A branch splitting off a middle kink, reaching about the rim.
					Vec3 from = crack.get(2);
					double side = angle + (random.nextBoolean() ? 0.7 : -0.7);
					double r = Math.hypot(from.x - center.x, from.z - center.z);
					double length = Math.max(0.3, (OUTER - r) * 0.8 + 0.2);
					List<Vec3> branch = new ArrayList<>();
					for (int k = 0; k <= 3; k++) {
						Vec3 straight = from.add(Math.cos(side) * length * k / 3, 0, Math.sin(side) * length * k / 3);
						branch.add(k == 0 || k == 3 ? straight : straight.add(jitter(side, 0.1)));
					}
					cracks.add(branch);
				}
			}
		}

		/** A jagged line along {@code angle}, from radius {@code from} to {@code to} ({@code segments} kinks displaced sideways). */
		private List<Vec3> fissure(double angle, double from, double to, int segments) {
			List<Vec3> points = new ArrayList<>();
			for (int k = 0; k <= segments; k++) {
				double r = from + (to - from) * k / segments;
				Vec3 point = new Vec3(center.x + Math.cos(angle) * r, center.y + 0.02, center.z + Math.sin(angle) * r);
				points.add(k == 0 || k == segments ? point : point.add(jitter(angle, 0.2)));
			}
			return points;
		}

		/** A sideways offset (perpendicular to {@code angle}) up to {@code amount} blocks. */
		private Vec3 jitter(double angle, double amount) {
			double d = (random.nextDouble() - 0.5) * 2 * amount;
			return new Vec3(-Math.sin(angle) * d, 0, Math.cos(angle) * d);
		}

		int total() {
			return drawTicks + CRACK_TICKS + SCATTER_TICKS;
		}
	}

	private Seals() {
	}

	static void init() {
		Scheduler.every("ruin.seal", 1, 0, Seals::tick);
	}

	// ================================================================== queries

	/** A sealed circle is forming or breaking under this player's reading right now. */
	public static boolean playing(ServerPlayer player) {
		return PLAYING.containsKey(player.getUUID());
	}

	/** Another sealed attempt of this player is ignored for now (the circle is still breaking, or just broke). */
	public static boolean cooling(ServerPlayer player) {
		Long until = COOLDOWN.get(player.getUUID());
		return until != null && Scheduler.ticks() < until;
	}

	/** Sealed circles playing on the server. */
	public static int count() {
		return PLAYING.size();
	}

	/** Ticks since this player's sealed circle started forming (-1 when none is playing). */
	public static int age(ServerPlayer player) {
		Seal seal = PLAYING.get(player.getUUID());
		return seal == null ? -1 : seal.age;
	}

	// ================================================================== start / stop

	/**
	 * Plays the sealed circle under the player, unless their previous attempt is still cooling down.
	 *
	 * @param drawn the circle is already drawn (the channel ended when the guards came): it cracks at once
	 * @return true when the effect starts
	 */
	static boolean play(ServerPlayer player, RuinRecord ruin, boolean cursed, boolean drawn) {
		if (cooling(player)) {
			return false;
		}
		RuinConfig cfg = RuinModule.config();
		int color = cursed ? CursedFx.PURPLE : SummonRules.themeColor(Encounters.def(ruin).bossBarColor);
		Seal seal = new Seal(player, color, cursed, SummonRules.ticks(cfg.summon.sealSeconds), drawn);
		PLAYING.put(player.getUUID(), seal);
		COOLDOWN.put(player.getUUID(), Scheduler.ticks() + Math.max(SummonRules.ticks(cfg.summon.sealCooldownSeconds), seal.total()));
		if (!drawn) {
			ServerLevel level = player.level();
			Vec3 c = seal.center;
			level.playSound(null, c.x, c.y, c.z, cursed ? SoundEvents.EVOKER_PREPARE_WOLOLO : SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0F,
					cursed ? 0.5F : 0.7F);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
		return true;
	}

	/** The player reads for real (the arena is free): no broken circle left under the new one. */
	static void stop(UUID player) {
		PLAYING.remove(player);
	}

	/** The player left the server. */
	static void forget(UUID player) {
		PLAYING.remove(player);
		COOLDOWN.remove(player);
	}

	/** Server stopping. */
	static void clear() {
		PLAYING.clear();
		COOLDOWN.clear();
	}

	// ================================================================== loop

	private static void tick() {
		if (!COOLDOWN.isEmpty()) {
			long now = Scheduler.ticks();
			COOLDOWN.values().removeIf(until -> until <= now);
		}
		if (PLAYING.isEmpty()) {
			return;
		}
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			PLAYING.clear();
			return;
		}
		RuinConfig cfg = RuinModule.config();
		int budget = Math.max(MIN_BUDGET, Math.min(MAX_BUDGET, cfg.summon.particleBudget / PLAYING.size()));
		Iterator<Seal> it = PLAYING.values().iterator();
		while (it.hasNext()) {
			Seal seal = it.next();
			ServerLevel level = server.getLevel(seal.dimension);
			if (level == null || seal.age > seal.total()) {
				it.remove();
				continue;
			}
			boolean ok = Safe.get("ruin.seal", () -> {
				step(level, seal, budget, cfg);
				return true;
			}, false);
			seal.age++;
			if (!ok) {
				it.remove();
			}
		}
	}

	private static void step(ServerLevel level, Seal seal, int budget, RuinConfig cfg) {
		RitualFx fx = new RitualFx(level, seal.center, budget, cfg.summon.viewDistance);
		int crackAt = seal.drawTicks;
		int shatterAt = crackAt + CRACK_TICKS;
		if (seal.age < crackAt) {
			forming(level, fx, seal);
		} else if (seal.age < shatterAt) {
			cracking(level, fx, seal);
		} else if (seal.age == shatterAt) {
			shatter(level, fx, seal);
		} else {
			scatter(fx, seal, SummonRules.phase(seal.age, shatterAt, shatterAt + SCATTER_TICKS));
		}
		fx.flush();
	}

	/** The circle draws itself while the presence closes in: dark strands, a stalling rotation, sparks on the rim. */
	private static void forming(ServerLevel level, RitualFx fx, Seal seal) {
		double p = Math.min(1.0, (seal.age + 1) / (double) seal.drawTicks);
		// The rotation stalls as the seal grips the circle.
		seal.spin += p < 0.6 ? 0.1 : 0.1 * Math.max(0.12, (1 - p) / 0.4);
		Vec3 c = seal.center;
		Incantations.drawReaderCircle(fx, c, p, seal.spin, seal.color, seal.cursed);
		if (p > 0.5) {
			double k = SummonRules.phase(p, 0.5, 1.0);
			ParticleOptions shadow = RitualFx.dust(SHADOW, 1.1F);
			int strands = seal.cursed ? 5 : 4;
			for (int i = 0; i < strands; i++) {
				double angle = seal.base + Math.PI * 2 * (i + 0.5) / strands;
				double outer = OUTER + 1.7;
				fx.line(shadow, point(c, angle, outer, 0.05), point(c, angle, outer - 1.6 * k, 0.05), 4);
			}
			if (seal.age % 2 == 0) {
				// The runes resist.
				double angle = seal.random.nextDouble() * Math.PI * 2;
				fx.burst(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(angle) * OUTER, c.y + 0.1, c.z + Math.sin(angle) * OUTER, 2, 0.05, 0.05, 0.05, 0.05);
			}
		}
		if (seal.cursed && seal.age % 3 == 0) {
			fx.burst(RitualFx.dust(CursedFx.VOID, 1.0F), c.x, c.y + 0.5, c.z, 2, 0.6, 0.3, 0.6, 0.0);
		}
		if (seal.age % 10 == 5) {
			sound(level, c, SoundEvents.SCULK_CLICKING, 0.8F, 0.6F + (float) p * 0.6F);
		}
		if (seal.age == (int) Math.round(seal.drawTicks * 0.6)) {
			// The seal grips: chains tightening.
			sound(level, c, SoundEvents.CHAIN_HIT, 1.0F, 0.5F);
		}
	}

	/** Fissures run across the runes, the circle dims; at the first tick the glass breaks and the reader is told why. */
	private static void cracking(ServerLevel level, RitualFx fx, Seal seal) {
		int step = seal.age - seal.drawTicks;
		double k = (step + 1) / (double) CRACK_TICKS;
		Vec3 c = seal.center;
		Incantations.drawReaderCircle(fx, c, 1.0, seal.spin, RitualFx.mix(seal.color, ASH, 0.55 * k), seal.cursed);
		ParticleOptions crack = RitualFx.dust(RitualFx.mix(seal.color, 0xFFFFFF, 0.75), 0.55F);
		for (List<Vec3> line : seal.cracks) {
			polyline(fx, crack, line, Math.min(1.0, k * 1.4));
		}
		if (step == 0) {
			fx.burst(ParticleTypes.CRIT, c.x, c.y + 0.2, c.z, 14, 0.9, 0.1, 0.9, 0.25);
			for (List<Vec3> line : seal.cracks) {
				Vec3 kink = line.get(line.size() / 2);
				fx.burst(ParticleTypes.ELECTRIC_SPARK, kink.x, kink.y + 0.1, kink.z, 3, 0.1, 0.05, 0.1, 0.08);
			}
			sound(level, c, SoundEvents.GLASS_BREAK, 1.0F, 0.55F);
			sound(level, c, SoundEvents.CHAIN_BREAK, 0.9F, 0.5F);
			ServerPlayer reader = GuildyServer.player(seal.player);
			if (reader != null) {
				Msg.actionBar(reader, seal.cursed ? "ruin.summon.sealed.cursed" : "ruin.summon.sealed");
			}
		}
	}

	/** The circle shatters: its runes and fissures fly outwards and turn to ash, a dull broken sound. */
	private static void shatter(ServerLevel level, RitualFx fx, Seal seal) {
		Vec3 c = seal.center;
		ParticleOptions rune = RitualFx.fade(seal.color, ASH, 0.9F);
		ParticleOptions blood = RitualFx.fade(Ritual.BLOOD_BRIGHT, ASH, 0.8F);
		ParticleOptions shard = RitualFx.fade(RitualFx.mix(seal.color, 0xFFFFFF, 0.75), ASH, 0.6F);
		fling(fx, rune, c, OUTER, seal.spin, 24, seal);
		fling(fx, RitualFx.fade(Ritual.BLOOD, ASH, 0.8F), c, INNER, -seal.spin, 18, seal);
		double phase = Incantations.pentagramPhase(seal.spin, seal.cursed);
		for (int i = 0; i < 5; i++) {
			Vec3 a = point(c, phase + Math.PI * 2 * i * 2 / 5, INNER, 0);
			Vec3 b = point(c, phase + Math.PI * 2 * (i + 1) * 2 / 5, INNER, 0);
			for (int k = 0; k < 4; k++) {
				flingFrom(fx, blood, c, a.add(b.subtract(a).scale((k + 0.5) / 4)), seal, 0.7);
			}
		}
		for (List<Vec3> line : seal.cracks) {
			for (Vec3 point : line) {
				flingFrom(fx, shard, c, point, seal, 1.2);
			}
		}
		fx.burst(ParticleTypes.SMOKE, c.x, c.y + 0.1, c.z, 18, 1.0, 0.05, 1.0, 0.03);
		fx.burst(ParticleTypes.CRIT, c.x, c.y + 0.3, c.z, 10, 0.6, 0.2, 0.6, 0.3);
		if (seal.cursed) {
			fx.burst(ParticleTypes.SOUL, c.x, c.y + 0.3, c.z, 8, 0.8, 0.2, 0.8, 0.04);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.PLAYERS, 1.0F, 0.6F);
		sound(level, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.9F, 0.5F);
		// Muffled, far below: whatever sleeps here did not wake.
		sound(level, c, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.25F, 0.45F);
		if (seal.cursed) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2F, 0.5F);
		}
	}

	/** The last fragments drift away and fade, a ring of smoke spreading ({@code t}: 0..1 of the scatter). */
	private static void scatter(RitualFx fx, Seal seal, double t) {
		Vec3 c = seal.center;
		double radius = OUTER + 1.6 * t;
		int n = (int) Math.round(9 * (1 - t));
		ParticleOptions dust = RitualFx.fade(RitualFx.mix(seal.color, ASH, 0.6), ASH, 0.7F);
		for (int i = 0; i < n; i++) {
			double angle = seal.random.nextDouble() * Math.PI * 2;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			fx.move(dust, c.x + cos * radius, c.y + 0.05 + seal.random.nextDouble() * 0.25, c.z + sin * radius, cos * 0.04, 0.01, sin * 0.04);
		}
		if (seal.age % 3 == 0) {
			fx.ring(ParticleTypes.SMOKE, c.add(0, 0.1, 0), radius, 14, seal.base);
		}
		if (seal.age % 2 == 0) {
			fx.burst(ParticleTypes.WHITE_ASH, c.x, c.y + 0.3, c.z, 4, radius * 0.5, 0.2, radius * 0.5, 0.01);
		}
		if (seal.cursed && seal.age % 4 == 0) {
			double angle = seal.random.nextDouble() * Math.PI * 2;
			fx.move(ParticleTypes.SOUL, c.x + Math.cos(angle) * radius * 0.6, c.y + 0.2, c.z + Math.sin(angle) * radius * 0.6, 0, 0.05, 0);
		}
	}

	// ================================================================== helpers

	/** {@code points} particles of a ring flung outwards (a little upwards), from angle {@code phase}. */
	private static void fling(RitualFx fx, ParticleOptions particle, Vec3 c, double radius, double phase, int points, Seal seal) {
		for (int i = 0; i < points; i++) {
			if (!flingFrom(fx, particle, c, point(c, phase + Math.PI * 2 * i / points, radius, 0), seal, 1.0)) {
				return;
			}
		}
	}

	/** One particle at {@code from}, flying away from the center. */
	private static boolean flingFrom(RitualFx fx, ParticleOptions particle, Vec3 c, Vec3 from, Seal seal, double strength) {
		double dx = from.x - c.x;
		double dz = from.z - c.z;
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-4) {
			double angle = seal.random.nextDouble() * Math.PI * 2;
			dx = Math.cos(angle);
			dz = Math.sin(angle);
			length = 1;
		}
		double speed = (0.1 + seal.random.nextDouble() * 0.12) * strength;
		return fx.move(particle, from.x, from.y, from.z, dx / length * speed, 0.02 + seal.random.nextDouble() * 0.06, dz / length * speed);
	}

	/** The first {@code progress} (0..1) of a polyline, by length. */
	private static void polyline(RitualFx fx, ParticleOptions particle, List<Vec3> points, double progress) {
		double total = 0;
		for (int i = 1; i < points.size(); i++) {
			total += points.get(i).distanceTo(points.get(i - 1));
		}
		double left = total * Math.max(0, Math.min(1, progress));
		for (int i = 1; i < points.size() && left > 1.0E-4; i++) {
			Vec3 a = points.get(i - 1);
			Vec3 b = points.get(i);
			double length = b.distanceTo(a);
			double part = Math.min(1, left / Math.max(1.0E-6, length));
			fx.line(particle, a, a.add(b.subtract(a).scale(part)), 7);
			left -= length;
		}
	}

	private static Vec3 point(Vec3 c, double angle, double radius, double dy) {
		return new Vec3(c.x + Math.cos(angle) * radius, c.y + dy, c.z + Math.sin(angle) * radius);
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}
}
