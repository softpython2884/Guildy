package dev.guildy.ruin;

import dev.guildy.core.Msg;
import dev.guildy.ruin.DreadAttacks.Context;
import dev.guildy.ruin.DreadAttacks.Running;
import dev.guildy.ruin.RuinConfig.DreadAbilityDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dread strikes: lines of spikes erupting from the floor (bone_spikes, ice_spikes), homing missiles (arcane_barrage,
 * wither_skulls), a spiral of feathers (feather_storm), a rain of projectiles on marked spots (poison_rain, magma_meteors,
 * ghast_rain), a stunning roar in a cone (stunning_roar), a sweeping beam (solar_beam), rings with gaps sweeping the arena
 * outward or collapsing inward (molten_ring, soul_eruption, collapsing_ring) and dives onto marked players (dive_bomb,
 * pounce_chain).
 */
final class DreadStrikes {
	private DreadStrikes() {
	}

	// ================================================================== lines of spikes

	/** Red lines drawn on the floor from the guardian toward the players, then spikes erupt along them, front after front. */
	static final class Lines extends Running {
		private static final double STEP = 0.75;
		private static final double SPEED = 1.3;
		private final List<List<Vec3>> lines = new ArrayList<>();
		private final Set<UUID> hit = new HashSet<>();

		private Lines(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph);
		}

		static @Nullable Lines cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			Lines lines = new Lines(def, style, telegraph);
			Vec3 origin = ctx.feet();
			int count = Math.max(1, def.count);
			List<ServerPlayer> aimed = DreadAttacks.pick(ctx, count);
			double base = ctx.random.nextDouble() * Math.PI * 2;
			for (int i = 0; i < count; i++) {
				double angle;
				if (i < aimed.size()) {
					Vec3 to = aimed.get(i).position();
					angle = Math.atan2(to.z - origin.z, to.x - origin.x);
				} else {
					angle = base + Math.PI * 2 * i / count;
				}
				lines.lines.add(trace(ctx, origin, angle, def.radius));
			}
			DreadAttacks.warn(ctx.targets, style.equals("ice") ? "ice_spikes" : "bone_spikes");
			DreadAttacks.sound(ctx.level, origin, style.equals("ice") ? SoundEvents.PLAYER_HURT_FREEZE : SoundEvents.SKELETON_AMBIENT, 2.0F, 0.5F);
			return lines;
		}

		/** Points every {@link #STEP} blocks along the floor, stopped by a wall or a drop. */
		private static List<Vec3> trace(Context ctx, Vec3 origin, double angle, double length) {
			List<Vec3> points = new ArrayList<>();
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			double y = origin.y;
			for (double d = 1.0; d <= length; d += STEP) {
				Vec3 floor = DreadAttacks.floor(ctx.level, origin.x + cos * d, y + 1.5, origin.z + sin * d);
				if (Math.abs(floor.y - y) > 1.6 || DreadAttacks.solid(ctx.level, floor.add(0, 0.6, 0))) {
					break;
				}
				y = floor.y;
				points.add(floor);
			}
			return points;
		}

		@Override
		boolean tick(Context ctx) {
			if (age < telegraph) {
				if (age % 2 == 0) {
					double k = (double) age / telegraph;
					for (List<Vec3> line : lines) {
						if (line.size() >= 2) {
							DreadFx.lineWarning(ctx.fx, line.getFirst().add(0, 0.08, 0), line.getLast().add(0, 0.08, 0), k, age);
						}
					}
				}
				return true;
			}
			int t = age - telegraph;
			int reached = (int) Math.floor((t + 1) * SPEED / STEP);
			int previous = (int) Math.floor(t * SPEED / STEP);
			boolean pending = false;
			boolean ice = "ice".equals(style);
			for (List<Vec3> line : lines) {
				pending |= previous < line.size();
				for (int i = Math.max(0, previous); i < Math.min(reached, line.size()); i++) {
					Vec3 at = line.get(i);
					DreadFx.spikes(ctx.fx, theme, at, 1.3 + ctx.random.nextDouble() * 0.6);
					if (i % 3 == 0) {
						DreadAttacks.sound(ctx.level, at, ice ? SoundEvents.GLASS_BREAK : SoundEvents.BONE_BLOCK_BREAK, 1.0F, 0.6F + ctx.random.nextFloat() * 0.3F);
					}
					for (ServerPlayer player : ctx.targets) {
						if (hit.contains(player.getUUID()) || Math.abs(player.getY() - at.y) > 2.0
								|| DreadAttacks.horizontal(player.position(), at) > 1.0) {
							continue;
						}
						hit.add(player.getUUID());
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.physical());
						DreadAttacks.push(player, 0, 0.75, 0);
						if (ice) {
							DreadAttacks.effect(ctx, player, MobEffects.SLOWNESS, 60, 1);
							player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 80, player.getTicksFrozen() + 120));
						} else {
							DreadAttacks.effect(ctx, player, MobEffects.WITHER, 60, 0);
						}
					}
				}
			}
			return pending;
		}
	}

	// ================================================================== homing missiles

	/** Orbs gather around the guardian, then homing missiles leave one after the other (slow to turn: run sideways). */
	static final class Missiles extends Running {
		private static final int STAGGER = 4;
		private static final int LIFE = 80;
		private final List<Missile> missiles = new ArrayList<>();

		private static final class Missile {
			final UUID target;
			final int launch;
			Vec3 pos;
			double[] dir;
			boolean done;

			Missile(UUID target, int launch) {
				this.target = target;
				this.launch = launch;
			}
		}

		private Missiles(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph);
		}

		static @Nullable Missiles cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			Missiles missiles = new Missiles(def, style, telegraph);
			for (int i = 0; i < Math.max(1, def.count); i++) {
				ServerPlayer target = ctx.targets.get(i % ctx.targets.size());
				missiles.missiles.add(new Missile(target.getUUID(), missiles.telegraph + i * STAGGER));
			}
			boolean wither = "wither".equals(style);
			DreadAttacks.warn(ctx.targets, wither ? "wither_skulls" : "arcane_barrage");
			DreadAttacks.sound(ctx.level, ctx.boss.position(), wither ? SoundEvents.WITHER_AMBIENT : SoundEvents.EVOKER_PREPARE_ATTACK, 2.0F, 0.6F);
			return missiles;
		}

		@Override
		boolean tick(Context ctx) {
			boolean wither = "wither".equals(style);
			double speed = wither ? 0.5 : 0.62;
			double turn = wither ? 0.055 : 0.075;
			Vec3 core = ctx.core();
			boolean pending = false;
			int waiting = 0;
			for (Missile missile : missiles) {
				if (missile.done) {
					continue;
				}
				pending = true;
				if (age < missile.launch) {
					waiting++;
					continue;
				}
				ServerPlayer target = ctx.level.getServer().getPlayerList().getPlayer(missile.target);
				if (missile.pos == null) {
					double a = ctx.random.nextDouble() * Math.PI * 2;
					missile.pos = core.add(Math.cos(a) * 0.8, 0.6, Math.sin(a) * 0.8);
					Vec3 aim = target != null ? target.getEyePosition().subtract(missile.pos) : new Vec3(Math.cos(a), 0.3, Math.sin(a));
					missile.dir = DreadRules.normalize(new double[] {aim.x + Math.cos(a) * 2, aim.length() * 0.6, aim.z + Math.sin(a) * 2});
					DreadAttacks.sound(ctx.level, missile.pos, wither ? SoundEvents.WITHER_SHOOT : SoundEvents.ILLUSIONER_CAST_SPELL, 1.2F,
							0.8F + ctx.random.nextFloat() * 0.4F);
				}
				if (target != null && BossAbilities.valid(target, ctx.level)) {
					Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(missile.pos);
					missile.dir = DreadRules.steer(missile.dir, new double[] {to.x, to.y, to.z}, turn);
				}
				Vec3 from = missile.pos;
				Vec3 next = from.add(missile.dir[0] * speed, missile.dir[1] * speed, missile.dir[2] * speed);
				if (DreadAttacks.solid(ctx.level, next) || age - missile.launch > LIFE) {
					burst(ctx, from, false);
					missile.done = true;
					continue;
				}
				missile.pos = next;
				ctx.fx.line(RitualFx.dust(theme.primary(), wither ? 1.4F : 1.0F), from, next, 4);
				ctx.fx.dot(wither ? RitualFx.dust(0x101010, 2.2F) : ParticleTypes.END_ROD, next);
				if (age % 2 == 0) {
					ctx.fx.dot(wither ? ParticleTypes.SMOKE : ParticleTypes.ENCHANT, next);
				}
				for (ServerPlayer player : ctx.targets) {
					AABB box = player.getBoundingBox().inflate(0.35);
					if (box.contains(next) || box.clip(from, next).isPresent()) {
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.magic());
						if (wither) {
							DreadAttacks.effect(ctx, player, MobEffects.WITHER, 80, 1);
						}
						Vec3 push = new Vec3(missile.dir[0], 0, missile.dir[2]);
						DreadAttacks.push(player, push.x * 0.5, 0.25, push.z * 0.5);
						burst(ctx, next, true);
						missile.done = true;
						break;
					}
				}
			}
			if (waiting > 0 && age % 2 == 0) {
				// The orbs still waiting, circling the guardian.
				for (int i = 0; i < Math.min(8, waiting); i++) {
					double a = age * 0.2 + Math.PI * 2 * i / Math.max(1, waiting);
					ctx.fx.dot(wither ? RitualFx.dust(0x151515, 1.8F) : RitualFx.dust(theme.secondary(), 1.2F), core.x + Math.cos(a) * 1.2, core.y + 0.8,
							core.z + Math.sin(a) * 1.2);
				}
			}
			return pending;
		}

		private void burst(Context ctx, Vec3 at, boolean hit) {
			boolean wither = "wither".equals(style);
			if (wither) {
				ctx.fx.burst(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.03);
				ctx.fx.dot(ParticleTypes.EXPLOSION, at);
				DreadAttacks.sound(ctx.level, at, SoundEvents.GENERIC_EXPLODE, 0.8F, 1.3F);
				// The blast of a wither skull hurts around it too.
				for (ServerPlayer player : ctx.targets) {
					if (player.position().add(0, 1, 0).distanceTo(at) <= 1.8 && !hit) {
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage * 0.5), ctx.blast());
					}
				}
			} else {
				ctx.fx.burst(ParticleTypes.ENCHANT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.4);
				ctx.fx.burst(RitualFx.dust(theme.secondary(), 1.0F), at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.0);
				DreadAttacks.sound(ctx.level, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 1.2F);
			}
		}
	}

	// ================================================================== spiral of feathers

	/** The guardian whirls (gusts rising), then spinning arms of feathers fly out of it: find the gaps between the arms. */
	static final class Spiral extends Running {
		private static final double SPEED = 0.5;
		private static final int MAX_ALIVE = 140;
		private final List<double[]> feathers = new ArrayList<>();
		private final Map<UUID, Integer> lastHit = new HashMap<>();
		private double spin;

		private Spiral(DreadAbilityDef def, String style, int telegraph, double spin) {
			super(def, style, telegraph);
			this.spin = spin;
		}

		static @Nullable Spiral cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			DreadAttacks.warn(ctx.targets, "feather_storm");
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.BREEZE_INHALE, 2.0F, 0.5F);
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.PHANTOM_FLAP, 2.0F, 0.6F);
			return new Spiral(def, style, telegraph, ctx.random.nextDouble() * Math.PI * 2);
		}

		@Override
		boolean tick(Context ctx) {
			Vec3 feet = ctx.feet();
			int arms = Math.max(2, def.count);
			int duration = Math.max(20, durationTicks());
			if (age < telegraph) {
				if (age % 2 == 0) {
					double k = (double) age / telegraph;
					CursedFx.warning(ctx.fx, feet.add(0, 0.08, 0), Math.min(def.radius, 6), k, age * 0.1);
					for (int i = 0; i < arms; i++) {
						double a = spin + Math.PI * 2 * i / arms;
						// The first stretch of each arm, so that the gaps are seen before the storm.
						DreadFx.lineWarning(ctx.fx, feet.add(Math.cos(a) * 1.5, 0.08, Math.sin(a) * 1.5), feet.add(Math.cos(a) * 5, 0.08, Math.sin(a) * 5), k, age);
					}
					ctx.fx.burst(ParticleTypes.SMALL_GUST, feet.x, feet.y + 1, feet.z, 2, 0.8, 0.6, 0.8, 0.0);
				}
				return true;
			}
			int t = age - telegraph;
			double y = feet.y + 1.0;
			if (t < duration && t % 3 == 0 && feathers.size() < MAX_ALIVE) {
				for (int i = 0; i < arms; i++) {
					double a = spin + Math.PI * 2 * i / arms;
					feathers.add(new double[] {feet.x + Math.cos(a) * 0.8, y, feet.z + Math.sin(a) * 0.8, Math.cos(a), Math.sin(a), 0});
				}
				spin += 0.2;
				if (t % 12 == 0) {
					DreadAttacks.sound(ctx.level, feet, SoundEvents.BREEZE_SHOOT, 1.2F, 1.2F);
				}
			}
			ParticleOptions dust = RitualFx.dust(theme.primary(), 1.1F);
			Iterator<double[]> it = feathers.iterator();
			while (it.hasNext()) {
				double[] f = it.next();
				Vec3 from = new Vec3(f[0], f[1], f[2]);
				f[0] += f[3] * SPEED;
				f[2] += f[4] * SPEED;
				f[5] += SPEED;
				Vec3 at = new Vec3(f[0], f[1], f[2]);
				if (f[5] > def.radius || DreadAttacks.solid(ctx.level, at)) {
					it.remove();
					continue;
				}
				ctx.fx.dot(dust, at);
				if (((int) (f[5] * 2)) % 3 == 0) {
					ctx.fx.dot(RitualFx.dust(theme.secondary(), 0.7F), from);
				}
				for (ServerPlayer player : ctx.targets) {
					Integer last = lastHit.get(player.getUUID());
					if (last != null && age - last < 10) {
						continue;
					}
					AABB box = player.getBoundingBox().inflate(0.3);
					if (box.contains(at) || box.clip(from, at).isPresent()) {
						lastHit.put(player.getUUID(), age);
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.magic());
						DreadAttacks.effect(ctx, player, MobEffects.POISON, 50, 0);
						ctx.fx.burst(ParticleTypes.SMALL_GUST, at.x, at.y, at.z, 1, 0, 0, 0, 0);
						it.remove();
						break;
					}
				}
			}
			return t < duration || !feathers.isEmpty();
		}
	}

	// ================================================================== rain

	/** Drops fall all over the arena, each one marked by a red circle before it lands (poison, magma, ghast fireballs). */
	static final class Rain extends Running {
		private static final int FALL = 10;
		private final List<Vec3> spots = new ArrayList<>();
		private final List<Integer> starts = new ArrayList<>();
		/** Burning patches left by the magma (center, end tick). */
		private final List<Vec3> patches = new ArrayList<>();
		private final List<Integer> patchEnds = new ArrayList<>();

		private Rain(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph);
		}

		static @Nullable Rain cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			Rain rain = new Rain(def, style, telegraph);
			int count = Math.max(1, def.count);
			int spacing = Math.max(2, (int) Math.round(Math.max(1, def.durationSeconds) * 20 / count));
			double reach = DreadAttacks.reach(ctx);
			for (int i = 0; i < count; i++) {
				Vec3 spot;
				if (ctx.random.nextBoolean() || i == 0) {
					ServerPlayer near = ctx.targets.get(ctx.random.nextInt(ctx.targets.size()));
					double spread = i == 0 ? 0.0 : 2.0;
					spot = DreadAttacks.floor(ctx.level, near.getX() + ctx.random.nextGaussian() * spread, near.getY(), near.getZ() + ctx.random.nextGaussian() * spread);
				} else {
					spot = DreadAttacks.arenaSpot(ctx, 1.5, reach);
				}
				rain.spots.add(spot);
				rain.starts.add(i * spacing);
			}
			DreadAttacks.warn(ctx.targets, "poison".equals(style) ? "poison_rain" : "lava".equals(style) ? "magma_meteors" : "ghast_rain");
			DreadAttacks.sound(ctx.level, ctx.boss.position(), "ghast".equals(style) ? SoundEvents.GHAST_WARN : SoundEvents.BREWING_STAND_BREW, 2.0F, 0.6F);
			return rain;
		}

		@Override
		boolean tick(Context ctx) {
			double radius = def.radius;
			boolean pending = false;
			for (int i = 0; i < spots.size(); i++) {
				int start = starts.get(i);
				int impact = start + telegraph;
				if (age < start) {
					pending = true;
					continue;
				}
				if (age > impact) {
					continue;
				}
				pending = true;
				Vec3 at = spots.get(i);
				double k = (double) (age - start) / Math.max(1, telegraph);
				if ((age - start) % 2 == 0 || age == impact) {
					CursedFx.warning(ctx.fx, at.add(0, 0.08, 0), radius, k, age * 0.1);
				}
				int fall = impact - age;
				if (fall > 0 && fall <= FALL) {
					Vec3 head = at.add(fall * 0.2, fall * 1.6, fall * 0.1);
					switch (style) {
						case "poison" -> {
							ctx.fx.burst(RitualFx.dust(theme.primary(), 2.0F), head.x, head.y, head.z, 4, 0.2, 0.2, 0.2, 0.0);
							ctx.fx.dot(ParticleTypes.ITEM_SLIME, head);
						}
						case "lava" -> {
							ctx.fx.burst(ParticleTypes.FLAME, head.x, head.y, head.z, 4, 0.25, 0.25, 0.25, 0.02);
							ctx.fx.burst(RitualFx.dust(theme.secondary(), 2.4F), head.x, head.y, head.z, 3, 0.2, 0.2, 0.2, 0.0);
							ctx.fx.move(ParticleTypes.LARGE_SMOKE, head.x, head.y + 0.5, head.z, 0, 0.05, 0);
						}
						default -> {
							ctx.fx.burst(ParticleTypes.FLAME, head.x, head.y, head.z, 8, 0.4, 0.4, 0.4, 0.02);
							ctx.fx.burst(RitualFx.dust(theme.primary(), 3.0F), head.x, head.y, head.z, 3, 0.3, 0.3, 0.3, 0.0);
							ctx.fx.move(ParticleTypes.LARGE_SMOKE, head.x, head.y + 0.6, head.z, 0, 0.05, 0);
						}
					}
				}
				if (age == impact) {
					land(ctx, at, radius);
				}
			}
			// The burning patches of the magma.
			for (int p = 0; p < patches.size(); p++) {
				if (age >= patchEnds.get(p)) {
					continue;
				}
				pending = true;
				Vec3 c = patches.get(p);
				if (age % 3 == 0) {
					ctx.fx.burst(ParticleTypes.FLAME, c.x, c.y + 0.1, c.z, 3, radius * 0.35, 0.05, radius * 0.35, 0.01);
					ctx.fx.dot(ParticleTypes.LAVA, c.x, c.y + 0.1, c.z);
				}
				if (age % 10 == 0) {
					for (ServerPlayer player : ctx.targets) {
						if (DreadAttacks.inside(player, c, radius * 0.8)) {
							player.igniteForSeconds(3.0F);
						}
					}
				}
			}
			return pending;
		}

		private void land(Context ctx, Vec3 at, double radius) {
			switch (style) {
				case "poison" -> {
					ctx.fx.burst(ParticleTypes.ITEM_SLIME, at.x, at.y + 0.3, at.z, 16, radius * 0.4, 0.2, radius * 0.4, 0.15);
					ctx.fx.burst(RitualFx.dust(theme.primary(), 1.4F), at.x, at.y + 0.4, at.z, 12, radius * 0.4, 0.3, radius * 0.4, 0.0);
					DreadAttacks.sound(ctx.level, at, SoundEvents.SLIME_SQUISH, 1.4F, 0.6F);
				}
				case "lava" -> {
					ctx.fx.burst(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
					ctx.fx.burst(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 12, radius * 0.4, 0.2, radius * 0.4, 0.3);
					ctx.fx.burst(theme.debris(), at.x, at.y + 0.2, at.z, 16, radius * 0.4, 0.1, radius * 0.4, 0.3);
					DreadAttacks.sound(ctx.level, at, SoundEvents.GENERIC_EXPLODE, 1.2F, 0.7F);
					patches.add(at);
					patchEnds.add(age + 40);
				}
				default -> {
					ctx.fx.burst(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
					ctx.fx.burst(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 24, radius * 0.4, 0.3, radius * 0.4, 0.1);
					DreadAttacks.sound(ctx.level, at, SoundEvents.GENERIC_EXPLODE, 1.6F, 0.6F);
				}
			}
			for (ServerPlayer player : ctx.targets) {
				if (Math.abs(player.getY() - at.y) > 2.5) {
					continue;
				}
				double damage = CursedRules.impact(ctx.damage(def.damage), DreadAttacks.horizontal(player.position(), at), radius);
				if (damage <= 0) {
					continue;
				}
				DreadAttacks.hurt(ctx, player, damage, "poison".equals(style) ? ctx.magic() : ctx.blast());
				switch (style) {
					case "poison" -> DreadAttacks.effect(ctx, player, MobEffects.POISON, 80, 1);
					case "lava" -> player.igniteForSeconds(4.0F);
					default -> {
						player.igniteForSeconds(3.0F);
						Vec3 push = DreadAttacks.away(at, player.position()).scale(0.8);
						DreadAttacks.push(player, push.x, 0.45, push.z);
					}
				}
			}
		}
	}

	// ================================================================== stunning roar

	/** The guardian turns to a player and draws a cone on the floor, then roars: whoever is in it is stunned (rooted, dazed). */
	static final class Cone extends Running {
		private static final double HALF_ANGLE = Math.toRadians(40);
		private final double yaw;
		private final Vec3 apex;
		private final Vec3 anchor;

		private Cone(DreadAbilityDef def, String style, int telegraph, double yaw, Vec3 apex, Vec3 anchor) {
			super(def, style, telegraph);
			this.yaw = yaw;
			this.apex = apex;
			this.anchor = anchor;
		}

		static @Nullable Cone cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			ServerPlayer target = null;
			double best = Double.MAX_VALUE;
			for (ServerPlayer player : ctx.targets) {
				double d = player.distanceToSqr(ctx.boss);
				if (d < best && d <= def.radius * def.radius * 1.5) {
					best = d;
					target = player;
				}
			}
			if (target == null) {
				return null;
			}
			Vec3 apex = ctx.feet();
			double yaw = Math.atan2(target.getZ() - apex.z, target.getX() - apex.x);
			DreadAttacks.face(ctx.boss, target.position());
			if (Felines.isFeline(ctx.boss)) {
				Felines.hold(ctx.boss, telegraph + 12);
			}
			List<ServerPlayer> inside = new ArrayList<>();
			for (ServerPlayer player : ctx.targets) {
				if (DreadRules.inCone(player.getX() - apex.x, player.getZ() - apex.z, Math.cos(yaw), Math.sin(yaw), HALF_ANGLE + 0.3, def.radius + 2)) {
					inside.add(player);
				}
			}
			DreadAttacks.warn(inside, "stunning_roar");
			DreadAttacks.sound(ctx.level, apex, SoundEvents.POLAR_BEAR_WARNING, 2.0F, 0.5F);
			return new Cone(def, style, telegraph, yaw, apex, ctx.boss.position());
		}

		@Override
		boolean tick(Context ctx) {
			if (age < telegraph) {
				DreadAttacks.hold(ctx.boss, anchor);
				DreadAttacks.face(ctx.boss, apex.add(Math.cos(yaw) * 3, 0, Math.sin(yaw) * 3));
				if (age % 2 == 0) {
					DreadFx.coneWarning(ctx.fx, apex.add(0, 0.08, 0), yaw, HALF_ANGLE, def.radius, (double) age / telegraph);
				}
				if (age % 6 == 0) {
					ctx.fx.burst(RitualFx.dust(theme.primary(), 1.2F), ctx.core().x, ctx.core().y, ctx.core().z, 6, 0.6, 0.4, 0.6, 0.0);
				}
				return true;
			}
			int t = age - telegraph;
			if (t == 0) {
				DreadAttacks.sound(ctx.level, apex, SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
				DreadAttacks.sound(ctx.level, apex, SoundEvents.WARDEN_SONIC_BOOM, 1.5F, 0.8F);
				if (Felines.isFeline(ctx.boss)) {
					Felines.growl(ctx.level, ctx.boss);
				}
				int stun = Math.max(20, durationTicks());
				for (ServerPlayer player : ctx.targets) {
					if (Math.abs(player.getY() - apex.y) <= 3
							&& DreadRules.inCone(player.getX() - apex.x, player.getZ() - apex.z, Math.cos(yaw), Math.sin(yaw), HALF_ANGLE, def.radius)) {
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.physical());
						Roots.root(player, stun, ctx.now);
						DreadAttacks.effect(ctx, player, MobEffects.NAUSEA, stun + 60, 0);
						DreadAttacks.effect(ctx, player, MobEffects.BLINDNESS, 20, 0);
						DreadAttacks.effect(ctx, player, MobEffects.MINING_FATIGUE, stun, 2);
						Msg.actionBar(player, "ruin.dread.stunned");
					}
				}
			}
			// The roar's shock waves sweeping the cone.
			double r = def.radius * Math.min(1, (t + 1) / 8.0);
			ctx.fx.arc(RitualFx.dust(theme.primary(), 1.5F), apex.add(0, 0.6, 0), r, yaw - HALF_ANGLE, yaw + HALF_ANGLE, (int) Math.max(6, r * 1.4));
			if (t % 2 == 0) {
				ctx.fx.dot(ParticleTypes.SONIC_BOOM, apex.x + Math.cos(yaw) * r * 0.7, apex.y + 1.2, apex.z + Math.sin(yaw) * r * 0.7);
			}
			return t < 8;
		}
	}

	// ================================================================== solar beam

	/** A thin red line marks the aim, then a beam of sunlight fires and sweeps slowly after its target (move across it!). */
	static final class Beam extends Running {
		private final UUID target;
		private final Vec3 anchor;
		private double yaw;
		private double pitch;

		private Beam(DreadAbilityDef def, String style, int telegraph, UUID target, double yaw, double pitch, Vec3 anchor) {
			super(def, style, telegraph);
			this.target = target;
			this.yaw = yaw;
			this.pitch = pitch;
			this.anchor = anchor;
		}

		static @Nullable Beam cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			List<ServerPlayer> seen = new ArrayList<>();
			for (ServerPlayer player : ctx.targets) {
				if (ctx.boss.hasLineOfSight(player) && player.distanceTo(ctx.boss) <= def.radius) {
					seen.add(player);
				}
			}
			if (seen.isEmpty()) {
				return null;
			}
			ServerPlayer target = seen.get(ctx.random.nextInt(seen.size()));
			Vec3 eye = ctx.boss.getEyePosition();
			Vec3 to = target.position().add(0, 1.0, 0).subtract(eye);
			if (Felines.isFeline(ctx.boss)) {
				Felines.hold(ctx.boss, telegraph + Math.max(20, (int) Math.round(def.durationSeconds * 20)) + 5);
			}
			DreadAttacks.warn(List.of(target), "solar_beam");
			DreadAttacks.sound(ctx.level, eye, SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.6F);
			return new Beam(def, style, telegraph, target.getUUID(), Math.atan2(to.z, to.x), Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)),
					ctx.boss.position());
		}

		@Override
		boolean tick(Context ctx) {
			ServerPlayer target = ctx.level.getServer().getPlayerList().getPlayer(this.target);
			Vec3 eye = ctx.boss.getEyePosition();
			boolean firing = age >= telegraph;
			if (target != null && BossAbilities.valid(target, ctx.level)) {
				Vec3 to = target.position().add(0, 1.0, 0).subtract(eye);
				double wantYaw = Math.atan2(to.z, to.x);
				double wantPitch = Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z));
				double max = firing ? 0.03 : 0.07;
				yaw += Math.max(-max, Math.min(max, Math.IEEEremainder(wantYaw - yaw, Math.PI * 2)));
				pitch += Math.max(-max, Math.min(max, wantPitch - pitch));
			}
			DreadAttacks.hold(ctx.boss, anchor);
			Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
			DreadAttacks.face(ctx.boss, eye.add(dir.scale(3)));
			Vec3 end = eye;
			for (double d = 0.5; d <= def.radius; d += 0.5) {
				Vec3 next = eye.add(dir.scale(d));
				if (DreadAttacks.solid(ctx.level, next)) {
					break;
				}
				end = next;
			}
			if (!firing) {
				if (age % 2 == 0) {
					ctx.fx.line(RitualFx.dust(DreadFx.WARNING, 0.6F), eye, end, 1.5);
					CursedFx.implode(ctx.fx, eye, 1.2, (double) age / telegraph, theme.primary(), 12, age * 0.3);
				}
				return true;
			}
			int t = age - telegraph;
			int duration = Math.max(20, durationTicks());
			if (t == 0) {
				DreadAttacks.sound(ctx.level, eye, SoundEvents.BEACON_ACTIVATE, 3.0F, 1.6F);
				DreadAttacks.sound(ctx.level, eye, SoundEvents.FIRECHARGE_USE, 2.0F, 0.6F);
			}
			ctx.fx.line(RitualFx.dust(theme.primary(), 1.8F), eye, end, 2.5);
			if (age % 2 == 0) {
				ctx.fx.line(ParticleTypes.END_ROD, eye, end, 0.8);
				ctx.fx.burst(ParticleTypes.FLAME, end.x, end.y, end.z, 4, 0.2, 0.2, 0.2, 0.05);
			}
			if (t % 5 == 0) {
				for (ServerPlayer player : ctx.targets) {
					Vec3 chest = player.position().add(0, 1.0, 0);
					if (DreadRules.distanceToSegment3(chest.x, chest.y, chest.z, eye.x, eye.y, eye.z, end.x, end.y, end.z) <= 1.0) {
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.magic());
						player.igniteForSeconds(3.0F);
					}
				}
			}
			if (t % 15 == 0) {
				DreadAttacks.sound(ctx.level, end, SoundEvents.FIRE_AMBIENT, 2.0F, 1.4F);
			}
			return t < duration;
		}
	}

	// ================================================================== rings with gaps

	/**
	 * A ring of lava / soul fire sweeps the arena, outward from the guardian or collapsing from the arena's edge onto it; its
	 * gaps (green posts, drawn with the ring before it starts) are the only way through.
	 */
	static final class Wave extends Running {
		private static final double SPEED = 0.45;
		private static final double GAP_HALF = Math.toRadians(24);
		private final Vec3 center;
		private final double[] gaps;
		private final boolean inward;
		private final Set<UUID> hit = new HashSet<>();

		private Wave(DreadAbilityDef def, String style, int telegraph, Vec3 center, double[] gaps, boolean inward) {
			super(def, style, telegraph);
			this.center = center;
			this.gaps = gaps;
			this.inward = inward;
		}

		static @Nullable Wave cast(DreadAbilityDef def, Context ctx, int telegraph, String style, boolean inward) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			Vec3 center = inward ? DreadAttacks.floor(ctx.level, ctx.encounter.spawn.getX() + 0.5, ctx.encounter.spawn.getY() + 0.5, ctx.encounter.spawn.getZ() + 0.5)
					: ctx.feet();
			double[] gaps = DreadRules.gaps(Math.max(1, def.count), ctx.random.nextDouble() * Math.PI * 2);
			DreadAttacks.warn(ctx.targets, inward ? "collapsing_ring" : "lava".equals(style) ? "molten_ring" : "soul_eruption");
			DreadAttacks.sound(ctx.level, center, "lava".equals(style) ? SoundEvents.LAVA_AMBIENT : SoundEvents.SOUL_ESCAPE.value(), 2.5F, 0.5F);
			DreadAttacks.sound(ctx.level, center, SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0F, 0.5F);
			return new Wave(def, style, (int) Math.round(telegraph * 1.4), center, gaps, inward);
		}

		@Override
		boolean tick(Context ctx) {
			double max = Math.max(4, def.radius);
			ParticleOptions wall = "lava".equals(style) ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME;
			Vec3 c = center.add(0, 0.1, 0);
			if (age < telegraph) {
				if (age % 3 == 0) {
					double k = (double) age / telegraph;
					// The ring as it will come, its gaps, and the corridors through them.
					DreadFx.gapRing(ctx.fx, c, inward ? max : 3.0, gaps, GAP_HALF, RitualFx.dust(DreadFx.WARNING, 1.0F), true);
					DreadFx.gapRing(ctx.fx, c, max * (inward ? 0.5 : 0.6), gaps, GAP_HALF, RitualFx.dust(DreadFx.WARNING, 0.8F), true);
					for (double gap : gaps) {
						Vec3 a = c.add(Math.cos(gap) * 2, 0, Math.sin(gap) * 2);
						Vec3 b = c.add(Math.cos(gap) * max * k, 0, Math.sin(gap) * max * k);
						ctx.fx.line(RitualFx.dust(DreadFx.SAFE, 0.8F), a, b, 1.2);
					}
				}
				return true;
			}
			int t = age - telegraph;
			double front = inward ? max - t * SPEED : 1.5 + t * SPEED;
			if (inward ? front < 1.5 : front > max) {
				return false;
			}
			DreadFx.gapRing(ctx.fx, c, front, gaps, GAP_HALF, wall, t % 4 == 0);
			if (t % 2 == 0) {
				DreadFx.gapRing(ctx.fx, c.add(0, 0.8, 0), front, gaps, GAP_HALF, RitualFx.dust(theme.primary(), 1.4F), false);
			}
			if (t % 10 == 0) {
				DreadAttacks.sound(ctx.level, c, "lava".equals(style) ? SoundEvents.LAVA_POP : SoundEvents.SOUL_ESCAPE.value(), 1.5F, 0.6F);
			}
			for (ServerPlayer player : ctx.targets) {
				if (hit.contains(player.getUUID()) || Math.abs(player.getY() - c.y) > 2.5) {
					continue;
				}
				double dist = DreadAttacks.horizontal(player.position(), c);
				if (!DreadRules.swept(dist, front, SPEED)) {
					continue;
				}
				double angle = Math.atan2(player.getZ() - c.z, player.getX() - c.x);
				if (DreadRules.inGap(angle, gaps, GAP_HALF * 0.9)) {
					continue;
				}
				hit.add(player.getUUID());
				DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.magic());
				if ("lava".equals(style)) {
					player.igniteForSeconds(5.0F);
				} else {
					DreadAttacks.effect(ctx, player, MobEffects.WITHER, 60, 1);
					DreadAttacks.effect(ctx, player, MobEffects.SLOWNESS, 40, 0);
				}
				Vec3 push = DreadAttacks.away(c, player.position()).scale(inward ? -0.6 : 0.6);
				DreadAttacks.push(player, push.x, 0.4, push.z);
			}
			return true;
		}
	}

	// ================================================================== dives

	/**
	 * The guardian marks a player (a red circle following them, then frozen in place: the dodge window), leaps and crashes
	 * on the mark (damage around it, players thrown back); a chain of pounces does it again on other players.
	 */
	static final class Dive extends Running {
		private static final int LOCK = 10;
		private static final int FLIGHT = 10;
		private static final int REST = 6;
		private final List<UUID> victims = new ArrayList<>();
		private int index;
		private int stageStart;
		/** 0 = tracking, 1 = locked, 2 = flying, 3 = resting. */
		private int stage;
		private Vec3 mark;
		private Vec3 from;
		private Vec3 hold;
		private double arc;

		private Dive(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph);
		}

		static @Nullable Dive cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			// A leap starts from the floor (the serpent dives from wherever it flies): a guardian in mid-air waits.
			boolean airborne = !ctx.boss.onGround() && ctx.boss.getY() - ctx.feet().y > 0.5;
			if (ctx.targets.isEmpty() || airborne && !"jade".equals(style)) {
				return null;
			}
			Dive dive = new Dive(def, style, telegraph);
			for (int i = 0; i < Math.max(1, def.count); i++) {
				dive.victims.add(ctx.targets.get((i + ctx.random.nextInt(ctx.targets.size())) % ctx.targets.size()).getUUID());
			}
			dive.hold = ctx.boss.position();
			DreadAttacks.sound(ctx.level, ctx.boss.position(), "solar".equals(style) ? SoundEvents.POLAR_BEAR_WARNING : SoundEvents.PHANTOM_SWOOP, 2.0F, 0.6F);
			return dive;
		}

		@Override
		boolean tick(Context ctx) {
			if (index >= victims.size()) {
				return false;
			}
			ServerPlayer victim = ctx.level.getServer().getPlayerList().getPlayer(victims.get(index));
			int t = age - stageStart;
			switch (stage) {
				case 0 -> {
					if (t == 0 && victim != null) {
						Msg.actionBar(victim, "ruin.dread.warn." + ("solar".equals(style) ? "pounce_chain" : "dive_bomb"));
					}
					if (victim != null && BossAbilities.valid(victim, ctx.level)) {
						mark = DreadAttacks.floorUnder(ctx.level, victim);
					}
					if (mark == null) {
						return next(ctx);
					}
					DreadAttacks.hold(ctx.boss, hold);
					DreadAttacks.face(ctx.boss, mark);
					if (t % 2 == 0) {
						CursedFx.warning(ctx.fx, mark.add(0, 0.08, 0), def.radius, (double) t / telegraph, t * 0.1);
					}
					if (Felines.isFeline(ctx.boss)) {
						Felines.hold(ctx.boss, 5);
					}
					if (t >= telegraph) {
						stage = 1;
						stageStart = age;
						DreadAttacks.sound(ctx.level, mark, SoundEvents.NOTE_BLOCK_BASEDRUM, 2.0F, 1.6F);
					}
				}
				case 1 -> {
					DreadAttacks.hold(ctx.boss, hold);
					if (t % 2 == 0) {
						ctx.fx.ring(RitualFx.dust(0xFFE0A0, 1.3F), mark.add(0, 0.1, 0), def.radius, (int) Math.max(14, def.radius * 8), 0);
						CursedFx.warning(ctx.fx, mark.add(0, 0.08, 0), def.radius, 1.0, t * 0.2);
					}
					if (t >= LOCK) {
						stage = 2;
						stageStart = age;
						from = ctx.boss.position();
						int room = Resurrection.headroom(ctx.level, BlockPos.containing(from.x, from.y + ctx.boss.getBbHeight(), from.z), 6);
						arc = Math.max(0.5, Math.min(5, room - 0.5));
						DreadAttacks.sound(ctx.level, from, "solar".equals(style) ? SoundEvents.RAVAGER_ROAR : SoundEvents.BREEZE_JUMP, 2.0F,
								"solar".equals(style) ? 1.2F : 0.6F);
						if (Felines.isFeline(ctx.boss)) {
							Felines.hold(ctx.boss, FLIGHT + 4);
						}
					}
				}
				case 2 -> {
					double k = Math.min(1, (t + 1) / (double) FLIGHT);
					Vec3 landing = landing(ctx);
					double x = from.x + (landing.x - from.x) * k;
					double z = from.z + (landing.z - from.z) * k;
					double y = from.y + (landing.y - from.y) * k + Math.sin(Math.PI * k) * arc;
					ctx.boss.teleportTo(x, y, z);
					ctx.boss.setDeltaMovement(Vec3.ZERO);
					ctx.boss.getNavigation().stop();
					ctx.fx.burst(RitualFx.dust(theme.primary(), 1.4F), x, y + ctx.boss.getBbHeight() * 0.5, z, 4, 0.3, 0.3, 0.3, 0.0);
					if (k >= 1) {
						impact(ctx, landing);
						hold = ctx.boss.position();
						stage = 3;
						stageStart = age;
					}
				}
				default -> {
					DreadAttacks.hold(ctx.boss, hold);
					if (t >= REST) {
						return next(ctx);
					}
				}
			}
			return true;
		}

		/** Where it lands: the mark, or as close to it as the guardian fits. */
		private Vec3 landing(Context ctx) {
			Mob boss = ctx.boss;
			Vec3 delta = mark.subtract(boss.position());
			if (ctx.level.noCollision(boss, boss.getBoundingBox().move(delta))) {
				return mark;
			}
			Vec3 back = from;
			for (double k = 0.85; k > 0.1; k -= 0.15) {
				Vec3 spot = from.add(mark.subtract(from).scale(k));
				if (ctx.level.noCollision(boss, boss.getBoundingBox().move(spot.subtract(boss.position())))) {
					return spot;
				}
			}
			return back;
		}

		private void impact(Context ctx, Vec3 at) {
			double radius = def.radius;
			boolean solar = "solar".equals(style);
			DreadAttacks.sound(ctx.level, at, SoundEvents.GENERIC_EXPLODE, 1.4F, 0.7F);
			DreadAttacks.sound(ctx.level, at, solar ? SoundEvents.RAVAGER_ROAR : SoundEvents.BREEZE_LAND, 2.0F, solar ? 0.8F : 0.5F);
			ctx.fx.burst(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.4, 0.2, 0.4, 0);
			ctx.fx.burst(theme.particle(), at.x, at.y + 0.4, at.z, 24, radius * 0.4, 0.3, radius * 0.4, 0.15);
			ctx.fx.ring(RitualFx.dust(theme.primary(), 1.5F), at.add(0, 0.2, 0), radius, (int) (radius * 9), 0);
			ctx.fx.burst(CursedAttacks.debris(ctx.level, at), at.x, at.y + 0.2, at.z, 30, radius * 0.4, 0.1, radius * 0.4, 0.3);
			for (ServerPlayer player : ctx.targets) {
				if (Math.abs(player.getY() - at.y) > 2.5) {
					continue;
				}
				double damage = CursedRules.impact(ctx.damage(def.damage), DreadAttacks.horizontal(player.position(), at), radius);
				if (damage <= 0) {
					continue;
				}
				DreadAttacks.hurt(ctx, player, damage, ctx.physical());
				Vec3 push = DreadAttacks.away(at, player.position()).scale(0.9);
				DreadAttacks.push(player, push.x, 0.5, push.z);
				if (solar) {
					player.igniteForSeconds(3.0F);
					DreadAttacks.effect(ctx, player, MobEffects.SLOWNESS, 40, 1);
				} else {
					DreadAttacks.effect(ctx, player, MobEffects.POISON, 60, 1);
				}
			}
		}

		/** The next pounce of the chain (or the end). */
		private boolean next(Context ctx) {
			index++;
			stage = 0;
			stageStart = age + 1;
			mark = null;
			hold = ctx.boss.position();
			return index < victims.size();
		}

		@Override
		boolean warning() {
			return stage <= 1;
		}

		@Override
		void stop(Context ctx) {
			if (Felines.isFeline(ctx.boss)) {
				Felines.release(ctx.boss);
			}
		}

		/** Where the current dive will land (tests). */
		@Nullable Vec3 mark() {
			return mark;
		}
	}
}
