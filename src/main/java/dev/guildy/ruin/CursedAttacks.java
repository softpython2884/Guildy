package dev.guildy.ruin;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The attacks of the cursed guardians (besides the arena {@link Pentagrams}): soul chains, dark meteors, darkness pulses,
 * lightning cages, the blood nova and the shadow clones. Each one is telegraphed (warning circle or charge) before it
 * strikes, hurts only survival / adventure players ({@link BossAbilities#valid}), changes no block (visual lightning, no
 * real explosion) and draws every tick within the fight's particle budget. Damage values are the configured ones times
 * the threat level and the phase bonus ({@link Context#damage}).
 */
final class CursedAttacks {
	private CursedAttacks() {
	}

	/** Everything an attack needs for one tick. */
	static final class Context {
		final ServerLevel level;
		final Encounter encounter;
		final CursedEncounter cursed;
		final Mob boss;
		/** Survival / adventure players in reach of the guardian. */
		final List<ServerPlayer> targets;
		final RitualFx fx;
		final CursedSettings settings;
		final SplittableRandom random;

		Context(ServerLevel level, Encounter encounter, CursedEncounter cursed, Mob boss, List<ServerPlayer> targets, RitualFx fx, CursedSettings settings,
				SplittableRandom random) {
			this.level = level;
			this.encounter = encounter;
			this.cursed = cursed;
			this.boss = boss;
			this.targets = targets;
			this.fx = fx;
			this.settings = settings;
			this.random = random;
		}

		CursedSettings.AttackSettings cfg() {
			return settings.attacks;
		}

		/** Configured damage times the threat level and the phase bonus. */
		double damage(double base) {
			return Math.max(0, base) * encounter.scaling.damage() * CursedRules.phaseDamage(cursed.phase, settings.phases.damageBonus);
		}

		Vec3 core() {
			return boss.position().add(0, boss.getBbHeight() * 0.6, 0);
		}

		DamageSource magic() {
			return level.damageSources().indirectMagic(boss, boss);
		}
	}

	/** A running attack. */
	abstract static class Running {
		int age;

		/** One tick (age already counted). @return false once the attack is over */
		abstract boolean tick(Context ctx);
	}

	// ================================================================== helpers

	/**
	 * Hurts a player (only survival / adventure ones), credited to the guardian. @return the health (absorption included)
	 * the player actually lost
	 */
	static float hurt(Context ctx, ServerPlayer player, double amount, DamageSource source) {
		if (amount <= 0 || !BossAbilities.valid(player, ctx.level)) {
			return 0;
		}
		float before = player.getHealth() + player.getAbsorptionAmount();
		player.hurtServer(ctx.level, source, (float) amount);
		return Math.max(0, before - player.getHealth() - player.getAbsorptionAmount());
	}

	/** Heals the guardian. @return the health it really regained */
	static float heal(Context ctx, double amount) {
		if (amount <= 0 || !ctx.boss.isAlive()) {
			return 0;
		}
		float before = ctx.boss.getHealth();
		ctx.boss.heal((float) amount);
		float gained = Math.max(0, ctx.boss.getHealth() - before);
		ctx.cursed.healed += gained;
		return gained;
	}

	/** Top of the floor at or under the feet position (x, y, z) (scans down a few blocks), or y itself. */
	static Vec3 floor(ServerLevel level, double x, double y, double z) {
		BlockPos start = BlockPos.containing(x, y + 0.5, z);
		for (int dy = 0; dy <= 10; dy++) {
			BlockPos pos = start.below(dy);
			if (!level.isLoaded(pos)) {
				break;
			}
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				continue;
			}
			BlockPos above = pos.above();
			if (level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
				return new Vec3(x, pos.getY() + shape.max(Direction.Axis.Y), z);
			}
		}
		return new Vec3(x, y, z);
	}

	/** Block crack particles of the floor at {@code at} (stone when it is air). */
	static ParticleOptions debris(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at.x, at.y - 0.5, at.z);
		BlockState state = level.getBlockState(pos);
		return new BlockParticleOption(ParticleTypes.BLOCK, state.isAir() || !state.getFluidState().isEmpty() ? Blocks.DEEPSLATE.defaultBlockState() : state);
	}

	/** A random spot of the arena floor around the guardian's spawn, between {@code min} and {@code max} blocks from it. */
	static Vec3 arenaSpot(Context ctx, double min, double max) {
		BlockPos spawn = ctx.encounter.spawn;
		double angle = ctx.random.nextDouble() * Math.PI * 2;
		double dist = min + ctx.random.nextDouble() * Math.max(0.1, max - min);
		return floor(ctx.level, spawn.getX() + 0.5 + Math.cos(angle) * dist, spawn.getY(), spawn.getZ() + 0.5 + Math.sin(angle) * dist);
	}

	/** Up to {@code count} targets, shuffled. */
	static List<ServerPlayer> pick(Context ctx, int count) {
		List<ServerPlayer> list = new ArrayList<>(ctx.targets);
		for (int i = list.size() - 1; i > 0; i--) {
			int j = ctx.random.nextInt(i + 1);
			ServerPlayer tmp = list.get(i);
			list.set(i, list.get(j));
			list.set(j, tmp);
		}
		return list.subList(0, Math.min(Math.max(0, count), list.size()));
	}

	static void sound(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	static Vec3 away(Vec3 from, Vec3 to) {
		Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
		return d.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : d.normalize();
	}

	// ================================================================== soul chains (phase 2)

	/** Chains of souls lash out at a few players (telegraphed), drag them toward the guardian, then snap (damage, slowness). */
	static final class Chains extends Running {
		private static final int ACTIVE = 34;
		private final List<UUID> victims = new ArrayList<>();

		static @Nullable Chains cast(Context ctx) {
			List<ServerPlayer> far = new ArrayList<>();
			for (ServerPlayer player : ctx.targets) {
				if (player.distanceToSqr(ctx.boss) >= 16.0) {
					far.add(player);
				}
			}
			if (far.isEmpty()) {
				return null;
			}
			Chains chains = new Chains();
			Context narrowed = new Context(ctx.level, ctx.encounter, ctx.cursed, ctx.boss, far, ctx.fx, ctx.settings, ctx.random);
			for (ServerPlayer player : pick(narrowed, ctx.cfg().chainTargets)) {
				chains.victims.add(player.getUUID());
				Msg.actionBar(player, "ruin.cursed.warn.chains");
			}
			sound(ctx.level, ctx.boss.position(), SoundEvents.VEX_CHARGE, 1.8F, 0.5F);
			sound(ctx.level, ctx.boss.position(), SoundEvents.CHAIN_PLACE, 2.0F, 0.6F);
			return chains;
		}

		@Override
		boolean tick(Context ctx) {
			boolean telegraph = age < CursedRules.TELEGRAPH_TICKS;
			int t = age - CursedRules.TELEGRAPH_TICKS;
			Vec3 core = ctx.core();
			boolean any = false;
			for (UUID uuid : victims) {
				ServerPlayer player = ctx.level.getServer().getPlayerList().getPlayer(uuid);
				if (player == null || !BossAbilities.valid(player, ctx.level) || player.distanceToSqr(ctx.boss) > 32.0 * 32.0) {
					continue;
				}
				any = true;
				Vec3 chest = player.position().add(0, player.getBbHeight() * 0.55, 0);
				double sag = Math.min(2.5, chest.distanceTo(core) * 0.12) * (telegraph ? 1.0 : 0.4);
				CursedFx.chain(ctx.fx, core, chest, sag, age, !telegraph);
				if (telegraph) {
					if (age % 5 == 0) {
						ctx.fx.burst(ParticleTypes.SOUL, chest.x, chest.y, chest.z, 3, 0.3, 0.4, 0.3, 0.02);
					}
					continue;
				}
				if (t == 0) {
					sound(ctx.level, chest, SoundEvents.CHAIN_HIT, 2.0F, 0.5F);
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ACTIVE, 1, false, true, true), ctx.boss);
				}
				// Dragged toward the guardian, every other tick (until close to it).
				if (t % 2 == 0 && player.distanceToSqr(ctx.boss) > 9.0) {
					Vec3 pull = away(player.position(), ctx.boss.position()).scale(0.22 * ctx.cfg().chainPull);
					player.push(pull.x, 0.04, pull.z);
					player.syncVelocity = true;
				}
				if (t % 6 == 0) {
					ctx.fx.burst(ParticleTypes.SOUL_FIRE_FLAME, chest.x, chest.y, chest.z, 4, 0.25, 0.3, 0.25, 0.02);
				}
				if (t == ACTIVE - 1) {
					hurt(ctx, player, ctx.damage(ctx.cfg().chainDamage), ctx.magic());
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, true, true), ctx.boss);
					ctx.fx.burst(RitualFx.dust(CursedFx.SOUL, 1.2F), chest.x, chest.y, chest.z, 20, 0.4, 0.4, 0.4, 0.0);
					sound(ctx.level, chest, SoundEvents.CHAIN_BREAK, 2.0F, 0.6F);
				}
			}
			if (t % 10 == 0 && !telegraph) {
				sound(ctx.level, core, SoundEvents.CHAIN_PLACE, 1.2F, 0.5F + ctx.random.nextFloat() * 0.3F);
			}
			return any && t < ACTIVE;
		}
	}

	// ================================================================== dark meteors (phase 2)

	/** Dark meteors fall on and around the players: a warning circle, the falling meteor, an impact (no block changed). */
	static final class Meteors extends Running {
		private static final int FALL_TICKS = 12;
		private static final int STAGGER = 5;
		private final List<Vec3> spots = new ArrayList<>();
		private final List<Integer> impacts = new ArrayList<>();

		static @Nullable Meteors cast(Context ctx) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			Meteors meteors = new Meteors();
			int count = ctx.cfg().meteorCount;
			for (int i = 0; i < count; i++) {
				ServerPlayer target = ctx.targets.get(i % ctx.targets.size());
				double spread = i < ctx.targets.size() ? 0.0 : 3.5;
				Vec3 at = floor(ctx.level, target.getX() + ctx.random.nextGaussian() * spread, target.getY(), target.getZ() + ctx.random.nextGaussian() * spread);
				meteors.spots.add(at);
				meteors.impacts.add(CursedRules.TELEGRAPH_TICKS + FALL_TICKS + i * STAGGER);
			}
			for (ServerPlayer player : ctx.targets) {
				Msg.actionBar(player, "ruin.cursed.warn.meteors");
			}
			sound(ctx.level, ctx.boss.position(), SoundEvents.WITHER_SHOOT, 1.6F, 0.4F);
			sound(ctx.level, ctx.boss.position(), SoundEvents.GHAST_WARN, 1.4F, 0.5F);
			return meteors;
		}

		@Override
		boolean tick(Context ctx) {
			double radius = ctx.cfg().meteorRadius;
			boolean pending = false;
			for (int i = 0; i < spots.size(); i++) {
				Vec3 at = spots.get(i);
				int impact = impacts.get(i);
				int start = i * STAGGER;
				if (age < start || age > impact) {
					continue;
				}
				pending |= age < impact;
				double k = (double) (age - start) / Math.max(1, impact - start);
				if (age % 2 == 0 || age == impact) {
					CursedFx.warning(ctx.fx, at.add(0, 0.08, 0), radius, k, age * 0.1);
				}
				int fall = impact - age;
				if (fall <= FALL_TICKS && fall > 0) {
					double y = at.y + 1.2 * fall * 1.5;
					Vec3 head = new Vec3(at.x + fall * 0.15, y, at.z + fall * 0.1);
					ctx.fx.burst(RitualFx.dust(CursedFx.VOID, 2.5F), head.x, head.y, head.z, 6, 0.25, 0.25, 0.25, 0.0);
					ctx.fx.burst(ParticleTypes.SOUL_FIRE_FLAME, head.x, head.y, head.z, 3, 0.2, 0.2, 0.2, 0.02);
					ctx.fx.move(ParticleTypes.LARGE_SMOKE, head.x, head.y + 0.5, head.z, 0, 0.05, 0);
					ctx.fx.dot(RitualFx.dust(CursedFx.PURPLE, 1.6F), head.x, head.y + 0.9, head.z);
				}
				if (age == impact) {
					impact(ctx, at, radius);
				}
			}
			return pending || age < impacts.getLast();
		}

		private void impact(Context ctx, Vec3 at, double radius) {
			ctx.fx.burst(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.4, 0.2, 0.4, 0.0);
			ctx.fx.burst(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.4, at.z, 16, radius * 0.4, 0.3, radius * 0.4, 0.04);
			ctx.fx.burst(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.3, at.z, 20, radius * 0.4, 0.2, radius * 0.4, 0.08);
			ctx.fx.burst(debris(ctx.level, at), at.x, at.y + 0.2, at.z, 24, radius * 0.4, 0.1, radius * 0.4, 0.3);
			ctx.fx.ring(RitualFx.fade(CursedFx.PURPLE, CursedFx.VOID, 1.5F), at.add(0, 0.2, 0), radius, (int) (radius * 9), 0);
			ctx.level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 0.6F + ctx.random.nextFloat() * 0.2F);
			for (ServerPlayer player : ctx.targets) {
				double dist = horizontal(player.position(), at);
				if (Math.abs(player.getY() - at.y) > 2.5) {
					continue;
				}
				double damage = CursedRules.impact(ctx.damage(ctx.cfg().meteorDamage), dist, radius);
				if (damage > 0) {
					hurt(ctx, player, damage, ctx.level.damageSources().explosion(ctx.boss, ctx.boss));
					Vec3 push = away(at, player.position()).scale(0.6);
					player.push(push.x, 0.35, push.z);
					player.syncVelocity = true;
				}
			}
		}
	}

	// ================================================================== darkness pulse (phase 3)

	/** The guardian draws the light in (a black ring collapsing on it, a shriek), then a pulse of darkness sweeps the arena. */
	static final class Darkness extends Running {
		private static final int CHARGE = 30;
		private static final int WAVE = 16;
		private final Set<UUID> hit = new HashSet<>();

		static @Nullable Darkness cast(Context ctx) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			for (ServerPlayer player : ctx.targets) {
				Msg.actionBar(player, "ruin.cursed.warn.darkness");
			}
			sound(ctx.level, ctx.boss.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 3.0F, 0.4F);
			return new Darkness();
		}

		@Override
		boolean tick(Context ctx) {
			Vec3 feet = ctx.boss.position().add(0, 0.2, 0);
			if (age < CHARGE) {
				double k = (double) age / CHARGE;
				if (age % 2 == 0) {
					ctx.fx.ring(RitualFx.dust(CursedFx.VOID, 1.8F), feet, 12 * (1 - k) + 0.5, 40, age * 0.1);
					ctx.fx.ring(ParticleTypes.SQUID_INK, feet.add(0, 0.6, 0), 9 * (1 - k) + 0.4, 16, -age * 0.1);
				}
				if (age == 10) {
					sound(ctx.level, feet, SoundEvents.WARDEN_SONIC_CHARGE, 2.5F, 0.6F);
				}
				return true;
			}
			int t = age - CHARGE;
			if (t == 0) {
				sound(ctx.level, feet, SoundEvents.WARDEN_SONIC_BOOM, 2.5F, 0.5F);
				ctx.fx.dot(RitualFx.flash(CursedFx.VOID), feet.add(0, 1.5, 0));
				ctx.fx.dot(ParticleTypes.SONIC_BOOM, feet.add(0, 1.2, 0));
			}
			double front = 1 + 22.0 * (t + 1) / WAVE;
			ctx.fx.ring(RitualFx.dust(CursedFx.VOID, 2.0F), feet, front, (int) Math.min(90, 14 + front * 4), t * 0.05);
			if (t % 2 == 0) {
				ctx.fx.ring(ParticleTypes.SQUID_INK, feet.add(0, 0.5, 0), front, (int) Math.min(40, 8 + front * 1.5), 0);
			}
			int seconds = (int) Math.round(ctx.cfg().darknessSeconds * 20);
			for (ServerPlayer player : ctx.targets) {
				if (hit.contains(player.getUUID()) || horizontal(player.position(), feet) > front) {
					continue;
				}
				hit.add(player.getUUID());
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, seconds, 0, false, false, true), ctx.boss);
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, false, true, true), ctx.boss);
				hurt(ctx, player, ctx.damage(ctx.cfg().darknessDamage), ctx.magic());
			}
			return t < WAVE;
		}
	}

	// ================================================================== lightning cages (phase 3)

	/** Cages of lightning close around a few players: posts telegraphed, then bolts and arcs; staying inside hurts. */
	static final class Cage extends Running {
		private static final int TELEGRAPH = 25;
		private static final int POSTS = 6;
		private final List<Vec3> centers = new ArrayList<>();
		private final int active;

		private Cage(int active) {
			this.active = active;
		}

		static @Nullable Cage cast(Context ctx) {
			List<ServerPlayer> victims = pick(ctx, ctx.cfg().cageTargets);
			if (victims.isEmpty()) {
				return null;
			}
			Cage cage = new Cage((int) Math.round(ctx.cfg().cageSeconds * 20));
			for (ServerPlayer player : victims) {
				cage.centers.add(floor(ctx.level, player.getX(), player.getY(), player.getZ()));
				Msg.actionBar(player, "ruin.cursed.warn.cage");
			}
			sound(ctx.level, ctx.boss.position(), SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 2.0F, 1.4F);
			return cage;
		}

		private Vec3 post(Vec3 c, int i, double radius) {
			double angle = Math.PI * 2 * i / POSTS;
			return new Vec3(c.x + Math.cos(angle) * radius, c.y, c.z + Math.sin(angle) * radius);
		}

		@Override
		boolean tick(Context ctx) {
			double radius = ctx.cfg().cageRadius;
			ParticleOptions arc = RitualFx.dust(CursedFx.ELECTRIC, 0.7F);
			for (Vec3 c : centers) {
				if (age < TELEGRAPH) {
					if (age % 2 == 0) {
						CursedFx.warning(ctx.fx, c.add(0, 0.08, 0), radius, (double) age / TELEGRAPH, age * 0.12);
					}
					for (int i = 0; i < POSTS; i++) {
						Vec3 p = post(c, i, radius);
						ctx.fx.move(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + ctx.random.nextDouble() * 3, p.z, 0, 0.05, 0);
					}
					continue;
				}
				int t = age - TELEGRAPH;
				if (t == 0 || t == active / 2) {
					for (int i = t == 0 ? 0 : 1; i < POSTS; i += 2) {
						CursedFx.bolt(ctx.level, post(c, i, radius));
					}
				}
				// Bars of the cage: arcs between neighbouring posts at three heights, one height per tick.
				double h = 0.4 + (t % 3) * 1.0;
				for (int i = 0; i < POSTS; i++) {
					Vec3 a = post(c, i, radius).add(0, h, 0);
					Vec3 b = post(c, (i + 1) % POSTS, radius).add(0, h, 0);
					CursedFx.jagged(ctx.fx, arc, a, b, 3, 0.18, ctx.random);
				}
				if (t % 4 == 0) {
					for (int i = 0; i < POSTS; i++) {
						Vec3 p = post(c, i, radius);
						CursedFx.jagged(ctx.fx, arc, p, p.add(0, 3.2, 0), 4, 0.12, ctx.random);
					}
				}
				if (CursedRules.pulse(t, 0, active)) {
					for (ServerPlayer player : ctx.targets) {
						if (CursedRules.inside(player.getX() - c.x, player.getY() - c.y, player.getZ() - c.z, radius)) {
							hurt(ctx, player, ctx.damage(ctx.cfg().cageDamage), ctx.magic());
							ctx.fx.burst(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.5, 0.3, 0.2);
							sound(ctx.level, player.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.4F);
						}
					}
				}
			}
			return age < TELEGRAPH + active;
		}
	}

	// ================================================================== blood nova (phase 3)

	/** The guardian gathers the arena's life (charge), then a nova of blood drains every player it sweeps and heals it. */
	static final class Nova extends Running {
		private static final int CHARGE = 40;
		private static final int WAVE = 14;
		private final Set<UUID> hit = new HashSet<>();
		private final List<Vec3> drained = new ArrayList<>();

		static @Nullable Nova cast(Context ctx) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			String name = Lang.colorize(ctx.encounter.def.boss.name);
			for (ServerPlayer player : ctx.targets) {
				Msg.actionBar(player, "ruin.cursed.warn.nova", name);
			}
			sound(ctx.level, ctx.boss.position(), SoundEvents.WARDEN_HEARTBEAT, 3.0F, 0.5F);
			sound(ctx.level, ctx.boss.position(), SoundEvents.EVOKER_PREPARE_ATTACK, 2.0F, 0.5F);
			return new Nova();
		}

		@Override
		boolean tick(Context ctx) {
			double radius = ctx.cfg().novaRadius;
			Vec3 feet = ctx.boss.position();
			Vec3 core = ctx.core();
			if (age < CHARGE) {
				double k = (double) age / CHARGE;
				if (age % 2 == 0) {
					CursedFx.warning(ctx.fx, feet.add(0, 0.1, 0), radius, k, age * 0.05);
					CursedFx.implode(ctx.fx, core, 4.0, k, CursedFx.BLOOD_BRIGHT, 30, age * 0.3);
				}
				int beat = (int) Math.max(4, Math.round(12 - 8 * k));
				if (age % beat == 0) {
					sound(ctx.level, feet, SoundEvents.WARDEN_HEARTBEAT, 2.5F, 0.6F + 0.6F * (float) k);
				}
				for (int i = 0; i < ctx.targets.size() && i < 6; i++) {
					ServerPlayer player = ctx.targets.get(i);
					if (horizontal(player.position(), feet) <= radius && age % 3 == 0) {
						CursedFx.stream(ctx.fx, player.position().add(0, 1.0, 0), core, age, i);
					}
				}
				return true;
			}
			int t = age - CHARGE;
			if (t == 0) {
				sound(ctx.level, feet, SoundEvents.WITHER_SHOOT, 2.0F, 0.5F);
				sound(ctx.level, feet, SoundEvents.ENDER_DRAGON_GROWL, 1.5F, 0.7F);
				ctx.fx.dot(RitualFx.flash(CursedFx.BLOOD_BRIGHT), core);
				ctx.fx.sphereBurst(RitualFx.fade(CursedFx.BLOOD_BRIGHT, CursedFx.VOID, 1.6F), core, 0.4, 60, 0.6);
			}
			double front = radius * Math.min(1.0, (t + 1) / (double) WAVE);
			ctx.fx.ring(RitualFx.fade(CursedFx.BLOOD_BRIGHT, CursedFx.BLOOD, 1.8F), feet.add(0, 0.3, 0), front, (int) Math.min(90, 14 + front * 5), t * 0.07);
			if (t % 2 == 0) {
				ctx.fx.ring(ParticleTypes.DAMAGE_INDICATOR, feet.add(0, 0.9, 0), front, (int) Math.min(30, 6 + front), 0);
			}
			for (ServerPlayer player : ctx.targets) {
				if (hit.contains(player.getUUID()) || Math.abs(player.getY() - feet.y) > 3) {
					continue;
				}
				double dist = horizontal(player.position(), feet);
				if (dist <= radius && CursedRules.onFront(dist, front)) {
					hit.add(player.getUUID());
					float lost = hurt(ctx, player, ctx.damage(ctx.cfg().novaDamage), ctx.magic());
					if (lost > 0) {
						ctx.cursed.drained += lost;
						heal(ctx, CursedRules.heal(lost, ctx.cfg().novaHeal));
						drained.add(player.position().add(0, 1.0, 0));
						ctx.fx.burst(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1, player.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
					}
				}
			}
			// Blood running back from every drained player into the guardian.
			for (Vec3 from : drained) {
				for (int i = 0; i < 3; i++) {
					ctx.fx.dot(RitualFx.trail(core, i % 2 == 0 ? CursedFx.BLOOD_BRIGHT : CursedFx.PURPLE, 10 + ctx.random.nextInt(8)),
							from.x + ctx.random.nextGaussian() * 0.2, from.y + ctx.random.nextGaussian() * 0.3, from.z + ctx.random.nextGaussian() * 0.2);
				}
			}
			if (t == WAVE && !drained.isEmpty()) {
				sound(ctx.level, core, SoundEvents.SOUL_ESCAPE.value(), 2.0F, 1.2F);
			}
			return t < WAVE + 6;
		}
	}

	// ================================================================== shadow clones (phase 3)

	/** Shadows peel off the guardian near the players (telegraphed columns of ink), then weak copies of it appear there. */
	static final class Clones extends Running {
		private final List<Vec3> spots = new ArrayList<>();

		static @Nullable Clones cast(Context ctx) {
			int count = ctx.cfg().cloneCount;
			if (count <= 0 || ctx.targets.isEmpty()) {
				return null;
			}
			Clones clones = new Clones();
			for (int i = 0; i < count; i++) {
				ServerPlayer target = ctx.targets.get(ctx.random.nextInt(ctx.targets.size()));
				double angle = ctx.random.nextDouble() * Math.PI * 2;
				Vec3 at = floor(ctx.level, target.getX() + Math.cos(angle) * 3, target.getY(), target.getZ() + Math.sin(angle) * 3);
				clones.spots.add(at);
			}
			String name = Lang.colorize(ctx.encounter.def.boss.name);
			for (ServerPlayer player : ctx.targets) {
				Msg.actionBar(player, "ruin.cursed.warn.clones", name);
			}
			sound(ctx.level, ctx.boss.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 2.0F, 0.6F);
			return clones;
		}

		@Override
		boolean tick(Context ctx) {
			if (age < CursedRules.TELEGRAPH_TICKS) {
				for (Vec3 at : spots) {
					CursedFx.helix(ctx.fx, RitualFx.dust(CursedFx.VOID, 1.3F), at, 0.6, age, 2.4, CursedRules.TELEGRAPH_TICKS);
					ctx.fx.move(ParticleTypes.SQUID_INK, at.x + ctx.random.nextGaussian() * 0.2, at.y + 0.1, at.z + ctx.random.nextGaussian() * 0.2, 0, 0.12, 0);
					if (age % 4 == 0) {
						ctx.fx.dot(RitualFx.trail(at.add(0, 1, 0), CursedFx.PURPLE, 10), ctx.boss.getX(), ctx.boss.getY() + 1.2, ctx.boss.getZ());
					}
				}
				return true;
			}
			for (Vec3 at : spots) {
				Mob clone = CursedClones.spawn(ctx, BlockPos.containing(at));
				Vec3 c = clone != null ? clone.position().add(0, clone.getBbHeight() / 2, 0) : at.add(0, 1, 0);
				ctx.fx.burst(ParticleTypes.LARGE_SMOKE, c, 24, 0.5, 0.03);
				ctx.fx.burst(ParticleTypes.SQUID_INK, c, 16, 0.5, 0.05);
				ctx.fx.burst(RitualFx.dust(CursedFx.PURPLE, 1.4F), c, 16, 0.6, 0.0);
			}
			sound(ctx.level, ctx.boss.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 2.0F, 0.5F);
			return false;
		}
	}
}
