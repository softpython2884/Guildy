package dev.guildy.ruin;

import dev.guildy.core.Lang;
import dev.guildy.ruin.RuinConfig.AbilityDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * The jaguar's abilities (ruins.json5 types pounce, claw_sweep, stalk, roar, spirit_jaguars), usable by any guardian but
 * made for the celestial jaguar: each one is telegraphed (a warning ring, a fan on the floor, a crouch, footprints) and
 * animated over a few ticks with bounded particles ({@link RuinFx}); damage only to survival / adventure players. Styles:
 * "gold" (golden: slows), "jade" (venom: poisons), "solar" (sunfire: burns); under the curse, violet, void and blood (and a
 * touch of wither). A ruined animation (guardian dead, rising, changing phase) stops at once and gives the hunt back.
 */
final class JaguarAbilities {
	private static final int POUNCE_TELEGRAPH = 12;
	private static final int CLAW_TELEGRAPH = 8;
	private static final int CLAW_SWIPE = 4;
	private static final int ROAR_TELEGRAPH = 10;
	private static final int ROAR_WAVE = 10;
	private static final int STALK_MAX = 90;
	private static final int SPIRIT_TELEGRAPH = 14;
	/** Lifetime, health and size of a spirit jaguar. */
	static final int SPIRIT_SECONDS = 20;
	static final double SPIRIT_HEALTH = 20;
	static final double SPIRIT_SCALE = 1.3;
	private static final double CLAW_HALF_ANGLE = Math.toRadians(65);
	private static final double ROAR_HALF_ANGLE = Math.toRadians(45);
	private static final double MAX_LEAP_SPEED = 2.5;
	private static final Identifier STALK_SPEED = Identifier.fromNamespaceAndPath("guildy", "ruin_stalk_speed");

	/** Main and accent colours of an ability. */
	record Palette(int primary, int secondary) {
	}

	private JaguarAbilities() {
	}

	static Palette palette(String style, boolean cursed) {
		if (cursed) {
			return "jade".equals(style) ? new Palette(CursedFx.VIOLET, CursedFx.VOID) : new Palette(CursedFx.PURPLE, CursedFx.BLOOD_BRIGHT);
		}
		return switch (style) {
			case "jade" -> new Palette(BossAbilities.JADE, BossAbilities.GOLD);
			case "solar" -> new Palette(BossAbilities.SOLAR, BossAbilities.GOLD);
			default -> new Palette(BossAbilities.GOLD, BossAbilities.JADE);
		};
	}

	// ================================================================== pounce

	/**
	 * A big leap onto a player 4 to 22 blocks away: the landing spot glows (a shrinking ring) while the jaguar crouches, it
	 * flies along an arc (limited by the ceiling) leaving a golden trail, then lands with a shockwave (full damage under it,
	 * less around, players thrown back).
	 */
	static boolean pounce(ServerLevel level, Encounter encounter, Mob boss, AbilityDef ability, List<ServerPlayer> targets, RandomGenerator random) {
		if (!boss.onGround()) {
			return false;
		}
		List<ServerPlayer> reachable = new ArrayList<>();
		for (ServerPlayer player : targets) {
			double d = horizontal(player.position(), boss.position());
			if (BossAbilities.valid(player, level) && d >= 4 && d <= 22 && Math.abs(player.getY() - boss.getY()) <= 6 && boss.hasLineOfSight(player)) {
				reachable.add(player);
			}
		}
		if (reachable.isEmpty()) {
			return false;
		}
		ServerPlayer target = reachable.get(random.nextInt(reachable.size()));
		boolean cursed = encounter.cursed != null;
		Palette pal = palette(ability.style, cursed);
		double width = boss.getBbWidth();
		double height = boss.getBbHeight();
		int headroom = Resurrection.headroom(level, BlockPos.containing(boss.getX(), boss.getY() + height, boss.getZ()), 8);
		ParticleOptions dirt = floor(level, boss.position());
		Felines.hold(boss, POUNCE_TELEGRAPH + 40);
		face(boss, target.position());
		RuinFx.sound(level, boss.position(), SoundEvents.POLAR_BEAR_WARNING, 2.0F, 0.6F);
		Vec3[] from = new Vec3[1];
		Vec3[] to = new Vec3[1];
		int[] flight = {0};
		double[] apex = {0};
		int[] landed = {-1};
		Vec3[] impact = new Vec3[1];
		RuinFx.play(level, (lvl, age) -> {
			if (!free(encounter, boss)) {
				Felines.release(boss);
				return false;
			}
			if (age < POUNCE_TELEGRAPH) {
				boolean aimed = BossAbilities.valid(target, lvl);
				Vec3 zone = aimed ? target.position() : boss.position();
				if (aimed) {
					face(boss, zone);
				}
				double k = (double) age / POUNCE_TELEGRAPH;
				RuinFx.ring(lvl, RitualFx.dust(pal.primary(), 1.3F), zone.add(0, 0.1, 0), 0.6 + ability.radius * (1.0 - 0.6 * k), (int) (8 + ability.radius * 5), 0.02);
				if (age % 3 == 0) {
					RuinFx.ring(lvl, RitualFx.dust(pal.secondary(), 1.0F), zone.add(0, 0.1, 0), 1.0, 8, 0.02);
				}
				// The cat crouches and digs its claws into the ground.
				lvl.sendParticles(dirt, boss.getX(), boss.getY() + 0.1, boss.getZ(), 3, width * 0.4, 0.05, width * 0.4, 0.1);
				return true;
			}
			int t = age - POUNCE_TELEGRAPH;
			if (from[0] == null) {
				from[0] = boss.position();
				Vec3 aim = BossAbilities.valid(target, lvl) ? target.position() : boss.position().add(boss.getLookAngle().scale(6));
				Vec3 d = new Vec3(aim.x - from[0].x, 0, aim.z - from[0].z);
				double len = Math.max(1.0E-3, d.length());
				// It lands on the player, not beyond.
				double stop = Math.max(0, len - width * 0.4);
				to[0] = new Vec3(from[0].x + d.x / len * stop, aim.y, from[0].z + d.z / len * stop);
				flight[0] = (int) Mth.clamp(Math.round(stop / 1.1), 8, 20);
				apex[0] = Mth.clamp(1.5 + stop * 0.15, 0.6, Math.max(0.6, headroom - 0.4));
				RuinFx.sound(lvl, from[0], SoundEvents.RAVAGER_ROAR, 1.4F, 1.3F);
				RuinFx.sound(lvl, from[0], SoundEvents.PHANTOM_SWOOP, 1.5F, 0.6F);
				RuinFx.burst(lvl, dirt, from[0].add(0, 0.2, 0), 25, width * 0.4, 0.2);
			}
			if (landed[0] < 0) {
				double s = Math.min(1.0, (t + 1) / (double) flight[0]);
				Vec3 a = from[0];
				Vec3 b = to[0];
				Vec3 want = new Vec3(a.x + (b.x - a.x) * s, a.y + (b.y - a.y) * s + 4 * apex[0] * s * (1 - s), a.z + (b.z - a.z) * s);
				Vec3 v = want.subtract(boss.position());
				if (v.length() > MAX_LEAP_SPEED) {
					v = v.normalize().scale(MAX_LEAP_SPEED);
				}
				boss.setDeltaMovement(v);
				boss.needsSync = true;
				boss.resetFallDistance();
				lvl.sendParticles(RitualFx.dust(pal.primary(), 1.4F), boss.getX(), boss.getY() + height * 0.5, boss.getZ(), 3, width * 0.3, height * 0.2,
						width * 0.3, 0.0);
				lvl.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + height * 0.4, boss.getZ(), 1, width * 0.2, height * 0.2, width * 0.2, 0.0);
				boolean down = boss.onGround() && t >= 3;
				if (down && s > 0.3 || t >= flight[0] + 10) {
					landed[0] = t;
					impact[0] = boss.position();
					land(lvl, encounter, boss, ability, pal, target, cursed);
				}
				return true;
			}
			int since = t - landed[0];
			double r = 1.0 + since * Math.max(0.5, ability.radius + width / 2) / 8.0;
			RuinFx.ring(lvl, RitualFx.dust(pal.primary(), 1.5F), impact[0].add(0, 0.2, 0), r, (int) (10 + r * 5), 0.05);
			RuinFx.ring(lvl, dirt, impact[0].add(0, 0.1, 0), r, (int) (8 + r * 4), 0.1);
			if (since % 2 == 0) {
				RuinFx.ring(lvl, ParticleTypes.CLOUD, impact[0].add(0, 0.3, 0), r * 0.9, (int) (6 + r * 2), 0.05);
			}
			return since < 8;
		});
		return true;
	}

	private static void land(ServerLevel level, Encounter encounter, Mob boss, AbilityDef ability, Palette pal, ServerPlayer target, boolean cursed) {
		Vec3 c = boss.position();
		double width = boss.getBbWidth();
		boss.setDeltaMovement(0, Math.min(0, boss.getDeltaMovement().y), 0);
		boss.resetFallDistance();
		RuinFx.sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.8F, 0.7F);
		RuinFx.sound(level, c, SoundEvents.RAVAGER_ATTACK, 1.6F, 0.9F);
		RuinFx.sound(level, c, SoundEvents.GENERIC_EXPLODE, 0.8F, 0.8F);
		RuinFx.burst(level, ParticleTypes.EXPLOSION, c.add(0, 0.5, 0), 2, width * 0.3, 0.0);
		RuinFx.burst(level, RitualFx.dust(pal.secondary(), 1.6F), c.add(0, 0.6, 0), 30, width * 0.5, 0.0);
		RuinFx.burst(level, floor(level, c), c.add(0, 0.2, 0), 40, width * 0.6, 0.3);
		double reach = ability.radius + width / 2;
		for (ServerPlayer player : level.players()) {
			if (!BossAbilities.valid(player, level) || Math.abs(player.getY() - c.y) > 3) {
				continue;
			}
			double d = horizontal(player.position(), c);
			if (d > reach) {
				continue;
			}
			double damage = d <= width / 2 + 1.5 ? ability.damage : ability.damage * 0.6;
			player.hurtServer(level, level.damageSources().mobAttack(boss), (float) damage);
			Vec3 away = direction(c, player.position());
			player.push(away.x * 0.9, 0.45, away.z * 0.9);
			player.syncVelocity = true;
			afflict(player, boss, ability.style, cursed, 3);
			level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
		}
		if (BossAbilities.valid(target, level)) {
			boss.setTarget(target);
		}
		Felines.release(boss);
	}

	// ================================================================== claw sweep

	/**
	 * A sweep of the claws in front of it: a fan shows on the floor while it raises a paw, then {@code count} claw streaks
	 * rake the arc from one side to the other; players in the fan are hit and thrown aside.
	 */
	static boolean clawSweep(ServerLevel level, Encounter encounter, Mob boss, AbilityDef ability, List<ServerPlayer> targets) {
		double width = boss.getBbWidth();
		double height = boss.getBbHeight();
		double reach = ability.radius + width / 2;
		ServerPlayer target = nearest(level, boss, targets, reach + 1.5);
		if (target == null) {
			return false;
		}
		boolean cursed = encounter.cursed != null;
		Palette pal = palette(ability.style, cursed);
		Felines.hold(boss, CLAW_TELEGRAPH + CLAW_SWIPE + 4);
		Vec3 dir = direction(boss.position(), target.position());
		face(boss, target.position());
		RuinFx.sound(level, boss.position(), SoundEvents.POLAR_BEAR_WARNING, 1.5F, 1.3F);
		int claws = Math.max(1, Math.min(5, ability.count));
		Set<UUID> hit = new HashSet<>();
		RuinFx.play(level, (lvl, age) -> {
			if (!free(encounter, boss)) {
				Felines.release(boss);
				return false;
			}
			Vec3 c = boss.position();
			if (age < CLAW_TELEGRAPH) {
				// The fan it is about to rake, drawn on the floor.
				arc(lvl, RitualFx.dust(pal.secondary(), 0.9F), c.add(0, 0.1, 0), dir, reach, -CLAW_HALF_ANGLE, CLAW_HALF_ANGLE, (int) (reach * 4));
				if (age % 2 == 0) {
					line(lvl, RitualFx.dust(pal.secondary(), 0.8F), c.add(0, 0.1, 0), dir, reach, CLAW_HALF_ANGLE, 6);
					line(lvl, RitualFx.dust(pal.secondary(), 0.8F), c.add(0, 0.1, 0), dir, reach, -CLAW_HALF_ANGLE, 6);
				}
				Vec3 paw = c.add(dir.scale(width * 0.5)).add(0, height * (0.6 + 0.04 * age), 0);
				lvl.sendParticles(RitualFx.dust(pal.primary(), 1.2F), paw.x, paw.y, paw.z, 2, 0.15, 0.1, 0.15, 0.0);
				return true;
			}
			int t = age - CLAW_TELEGRAPH;
			double a0 = CLAW_HALF_ANGLE - 2 * CLAW_HALF_ANGLE * t / CLAW_SWIPE;
			double a1 = CLAW_HALF_ANGLE - 2 * CLAW_HALF_ANGLE * (t + 1) / CLAW_SWIPE;
			for (int k = 0; k < claws; k++) {
				double r = reach * (claws == 1 ? 0.8 : 0.55 + 0.4 * k / (claws - 1));
				arc(lvl, RitualFx.dust(pal.primary(), 1.3F), new Vec3(c.x, c.y + height * (0.3 + 0.08 * k), c.z), dir, r, a0, a1, 4);
			}
			Vec3 tip = rotate(dir, a1).scale(reach * 0.8);
			lvl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + tip.x, c.y + height * 0.4, c.z + tip.z, 1, 0, 0, 0, 0);
			if (t == 1) {
				RuinFx.sound(lvl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
				RuinFx.sound(lvl, c, SoundEvents.RAVAGER_ATTACK, 1.2F, 1.4F);
				Vec3 aside = rotate(dir, -Math.PI / 2);
				for (ServerPlayer player : lvl.players()) {
					if (!BossAbilities.valid(player, lvl) || hit.contains(player.getUUID()) || Math.abs(player.getY() - c.y) > 2.5) {
						continue;
					}
					double d = horizontal(player.position(), c);
					if (d > reach + 0.5 || d > width / 2 + 1.0 && angle(dir, c, player.position()) > CLAW_HALF_ANGLE + 0.1) {
						continue;
					}
					hit.add(player.getUUID());
					player.hurtServer(lvl, lvl.damageSources().mobAttack(boss), (float) ability.damage);
					Vec3 away = direction(c, player.position());
					player.push(away.x * 0.6 + aside.x * 0.5, 0.3, away.z * 0.6 + aside.z * 0.5);
					player.syncVelocity = true;
					afflict(player, boss, ability.style, cursed, 4);
					lvl.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.4, 0.3, 0.25);
					lvl.sendParticles(RitualFx.dust(pal.primary(), 1.0F), player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
				}
			}
			if (t + 1 >= CLAW_SWIPE) {
				Felines.release(boss);
				return false;
			}
			return true;
		});
		return true;
	}

	// ================================================================== roar

	/**
	 * A roar in a cone in front of it: breath gathers at its mouth, then sound waves roll out; the players in the cone are
	 * slowed (level {@code amplifier}), weakened, hurt a little and pushed back.
	 */
	static boolean roar(ServerLevel level, Encounter encounter, Mob boss, AbilityDef ability, List<ServerPlayer> targets) {
		double width = boss.getBbWidth();
		double height = boss.getBbHeight();
		double reach = ability.radius + width / 2;
		ServerPlayer target = nearest(level, boss, targets, reach);
		if (target == null) {
			return false;
		}
		boolean cursed = encounter.cursed != null;
		Palette pal = palette(ability.style, cursed);
		Felines.hold(boss, ROAR_TELEGRAPH + ROAR_WAVE + 4);
		Vec3 dir = direction(boss.position(), target.position());
		face(boss, target.position());
		RuinFx.sound(level, boss.position(), SoundEvents.POLAR_BEAR_WARNING, 2.0F, 0.5F);
		int slowness = Math.max(0, Math.min(4, ability.amplifier));
		RuinFx.play(level, (lvl, age) -> {
			if (!free(encounter, boss)) {
				Felines.release(boss);
				return false;
			}
			Vec3 mouth = boss.position().add(dir.scale(width * 0.5)).add(0, height * 0.65, 0);
			if (age < ROAR_TELEGRAPH) {
				double r = 1.4 * (1.0 - (double) age / ROAR_TELEGRAPH) + 0.2;
				RuinFx.ring(lvl, RitualFx.dust(pal.primary(), 1.0F), mouth, r, 8, 0.02);
				lvl.sendParticles(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z, 1, 0.1, 0.1, 0.1, 0.0);
				return true;
			}
			int t = age - ROAR_TELEGRAPH;
			if (t == 0) {
				RuinFx.sound(lvl, mouth, SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
				RuinFx.sound(lvl, mouth, SoundEvents.ENDER_DRAGON_GROWL, 0.8F, 1.6F);
				Vec3 c = boss.position();
				for (ServerPlayer player : lvl.players()) {
					if (!BossAbilities.valid(player, lvl) || Math.abs(player.getY() - c.y) > 4) {
						continue;
					}
					double d = horizontal(player.position(), c);
					if (d > reach || d > width / 2 + 1.5 && angle(dir, c, player.position()) > ROAR_HALF_ANGLE + 0.1) {
						continue;
					}
					player.hurtServer(lvl, lvl.damageSources().mobAttack(boss), (float) ability.damage);
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, slowness), boss);
					player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0), boss);
					if (cursed) {
						player.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), boss);
					}
					Vec3 away = direction(c, player.position());
					player.push(away.x * 0.8, 0.3, away.z * 0.8);
					player.syncVelocity = true;
					lvl.sendParticles(RitualFx.dust(pal.secondary(), 1.0F), player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
				}
			}
			double r = (t + 1) * reach / ROAR_WAVE;
			int points = (int) Math.max(6, r * 3);
			arc(lvl, RitualFx.dust(pal.primary(), 1.5F), mouth.add(0, -height * 0.25, 0), dir, r, -ROAR_HALF_ANGLE, ROAR_HALF_ANGLE, points);
			if (t % 2 == 0) {
				arc(lvl, ParticleTypes.CLOUD, mouth.add(0, -height * 0.45, 0), dir, r * 0.92, -ROAR_HALF_ANGLE, ROAR_HALF_ANGLE, Math.max(4, points / 2));
			}
			if (t + 1 >= ROAR_WAVE) {
				Felines.release(boss);
				return false;
			}
			return true;
		});
		return true;
	}

	// ================================================================== stalk

	/**
	 * Stealth: the jaguar vanishes (invisible, its name hidden, faster) and stalks a player at least 5 blocks away, betrayed
	 * only by its golden footprints and a low growl; within {@code radius} blocks of its prey it reappears and ambushes
	 * (heavy damage, thrown up); after {@value #STALK_MAX} ticks without reaching it, it simply reappears.
	 */
	static boolean stalk(ServerLevel level, Encounter encounter, Mob boss, AbilityDef ability, List<ServerPlayer> targets, RandomGenerator random) {
		if (boss.hasEffect(MobEffects.INVISIBILITY)) {
			return false;
		}
		List<ServerPlayer> prey = new ArrayList<>();
		for (ServerPlayer player : targets) {
			if (BossAbilities.valid(player, level) && horizontal(player.position(), boss.position()) >= 5) {
				prey.add(player);
			}
		}
		if (prey.isEmpty()) {
			return false;
		}
		ServerPlayer target = prey.get(random.nextInt(prey.size()));
		boolean cursed = encounter.cursed != null;
		Palette pal = palette(ability.style, cursed);
		boolean nameVisible = boss.isCustomNameVisible();
		boss.setTarget(target);
		boss.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, STALK_MAX + 20, 0, false, false));
		multiply(boss, Attributes.MOVEMENT_SPEED, STALK_SPEED, 1.35);
		boss.setCustomNameVisible(false);
		Vec3 at = boss.position().add(0, boss.getBbHeight() * 0.5, 0);
		RuinFx.burst(level, ParticleTypes.LARGE_SMOKE, at, 30, boss.getBbWidth() * 0.4, 0.03);
		RuinFx.burst(level, RitualFx.dust(pal.primary(), 1.4F), at, 30, boss.getBbWidth() * 0.5, 0.0);
		RuinFx.sound(level, at, SoundEvents.OCELOT_AMBIENT, 1.2F, 0.5F);
		RuinFx.sound(level, at, SoundEvents.PHANTOM_SWOOP, 1.2F, 0.5F);
		Vec3[] last = {boss.position()};
		boolean[] left = {false};
		RuinFx.play(level, (lvl, age) -> {
			if (!free(encounter, boss)) {
				reveal(lvl, boss, nameVisible, pal, false);
				return false;
			}
			if (age % 4 == 0) {
				Vec3 pos = boss.position();
				Vec3 moved = new Vec3(pos.x - last[0].x, 0, pos.z - last[0].z);
				if (moved.lengthSqr() > 0.0025) {
					// Paw prints, left and right in turn.
					Vec3 side = new Vec3(-moved.z, 0, moved.x).normalize().scale(boss.getBbWidth() * 0.25 * (left[0] ? 1 : -1));
					lvl.sendParticles(RitualFx.dust(pal.secondary(), 0.9F), pos.x + side.x, pos.y + 0.05, pos.z + side.z, 4, 0.12, 0.0, 0.12, 0.0);
					lvl.sendParticles(RitualFx.dust(pal.primary(), 0.7F), pos.x + side.x, pos.y + 0.05, pos.z + side.z, 2, 0.08, 0.0, 0.08, 0.0);
					left[0] = !left[0];
				}
				last[0] = pos;
			}
			if (age % 20 == 10) {
				RuinFx.sound(lvl, boss.position(), SoundEvents.OCELOT_AMBIENT, 0.7F, 0.45F);
			}
			LivingEntity current = boss.getTarget() instanceof ServerPlayer p && BossAbilities.valid(p, lvl) ? p : target;
			if (!(current instanceof ServerPlayer victim) || !BossAbilities.valid(victim, lvl)) {
				reveal(lvl, boss, nameVisible, pal, false);
				return false;
			}
			double strike = boss.getBbWidth() / 2 + ability.radius;
			if (horizontal(victim.position(), boss.position()) <= strike && Math.abs(victim.getY() - boss.getY()) <= 3) {
				reveal(lvl, boss, nameVisible, pal, true);
				ambush(lvl, boss, victim, ability, pal, cursed);
				return false;
			}
			if (age >= STALK_MAX) {
				reveal(lvl, boss, nameVisible, pal, false);
				return false;
			}
			return true;
		});
		return true;
	}

	private static void reveal(ServerLevel level, Mob boss, boolean nameVisible, Palette pal, boolean pounce) {
		boss.removeEffect(MobEffects.INVISIBILITY);
		AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(STALK_SPEED);
		}
		boss.setCustomNameVisible(nameVisible);
		if (!boss.isAlive()) {
			return;
		}
		Vec3 at = boss.position().add(0, boss.getBbHeight() * 0.5, 0);
		RuinFx.burst(level, ParticleTypes.LARGE_SMOKE, at, pounce ? 20 : 12, boss.getBbWidth() * 0.4, 0.03);
		RuinFx.burst(level, RitualFx.dust(pal.primary(), 1.4F), at, pounce ? 30 : 16, boss.getBbWidth() * 0.5, 0.0);
	}

	private static void ambush(ServerLevel level, Mob boss, ServerPlayer victim, AbilityDef ability, Palette pal, boolean cursed) {
		face(boss, victim.position());
		Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
		victim.hurtServer(level, level.damageSources().mobAttack(boss), (float) ability.damage);
		Vec3 away = direction(boss.position(), victim.position());
		victim.push(away.x * 0.6, 0.55, away.z * 0.6);
		victim.syncVelocity = true;
		afflict(victim, boss, ability.style, cursed, 4);
		level.sendParticles(RitualFx.flash(pal.primary()), at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 2, 0.3, 0.2, 0.3, 0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.4, 0.5, 0.4, 0.3);
		level.sendParticles(RitualFx.dust(pal.secondary(), 1.3F), at.x, at.y, at.z, 20, 0.5, 0.5, 0.5, 0.0);
		RuinFx.sound(level, at, SoundEvents.RAVAGER_ATTACK, 1.8F, 0.8F);
		RuinFx.sound(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.5F, 0.6F);
		RuinFx.sound(level, at, SoundEvents.POLAR_BEAR_WARNING, 2.0F, 0.8F);
	}

	// ================================================================== spirit jaguars

	/**
	 * Spirit jaguars: glyphs glow on the floor around it, then {@code count} small spectral jaguars (glowing ocelots, black
	 * cats under the curse) leap out and hunt for {@value #SPIRIT_SECONDS} seconds (bite = {@code damage}), within the
	 * escort cap; tracked as escort mobs of the fight.
	 */
	static boolean spiritJaguars(ServerLevel level, Encounter encounter, Mob boss, AbilityDef ability, List<ServerPlayer> targets, RandomGenerator random) {
		int room = encounter.maxAlive() - encounter.minionEntities(level).size();
		int count = Math.min(Math.max(1, ability.count), room);
		if (count <= 0) {
			return false;
		}
		boolean feline = Felines.isFeline(boss);
		String entity = feline ? encounter.def.boss.entity : "minecraft:ocelot";
		String variant = feline ? encounter.def.boss.variant : "";
		double width = boss.getBbWidth();
		List<BlockPos> spots = new ArrayList<>();
		for (int i = 0; i < count * 4 && spots.size() < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = width / 2 + 1.5 + random.nextDouble() * Math.max(0.5, ability.radius - 1.5);
			BlockPos around = BlockPos.containing(boss.getX() + Math.cos(angle) * dist, boss.getY(), boss.getZ() + Math.sin(angle) * dist);
			BlockPos spot = BossFactory.standable(level, around, entity, 1);
			if (spot != null && !spots.contains(spot)) {
				spots.add(spot);
			}
		}
		if (spots.isEmpty()) {
			return false;
		}
		boolean cursed = encounter.cursed != null;
		Palette pal = palette(ability.style, cursed);
		double damage = ability.damage;
		RuinFx.sound(level, boss.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 1.5F);
		RuinFx.sound(level, boss.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.6F);
		RuinFx.play(level, (lvl, age) -> {
			if (!free(encounter, boss)) {
				return false;
			}
			if (age < SPIRIT_TELEGRAPH) {
				for (BlockPos spot : spots) {
					Vec3 base = Vec3.atBottomCenterOf(spot);
					RuinFx.ring(lvl, RitualFx.dust(pal.primary(), 1.1F), base.add(0, 0.1, 0), 0.8, 10, 0.02);
					RuinFx.helix(lvl, RitualFx.dust(pal.secondary(), 0.9F), base, 0.5, age, 1.6, SPIRIT_TELEGRAPH);
				}
				return true;
			}
			for (BlockPos spot : spots) {
				Mob spirit = spawnSpirit(lvl, encounter, entity, variant, spot, damage, pal, cursed, targets);
				if (spirit != null) {
					Vec3 c = spirit.position().add(0, 0.5, 0);
					RuinFx.burst(lvl, ParticleTypes.POOF, c, 10, 0.3, 0.05);
					RuinFx.burst(lvl, RitualFx.dust(pal.primary(), 1.3F), c, 16, 0.4, 0.0);
					RuinFx.burst(lvl, ParticleTypes.END_ROD, c, 6, 0.3, 0.08);
				}
			}
			RuinFx.sound(lvl, boss.position(), SoundEvents.EVOKER_CAST_SPELL, 1.4F, 1.6F);
			RuinFx.sound(lvl, boss.position(), SoundEvents.OCELOT_AMBIENT, 1.2F, 1.6F);
			return false;
		});
		return true;
	}

	private static @Nullable Mob spawnSpirit(ServerLevel level, Encounter encounter, String entity, String variant, BlockPos spot, double damage,
			Palette pal, boolean cursed, List<ServerPlayer> targets) {
		if (encounter.minionEntities(level).size() >= encounter.maxAlive()) {
			return null;
		}
		Mob mob = BossFactory.create(level, entity, spot, variant);
		if (mob == null) {
			return null;
		}
		setBase(mob, Attributes.MAX_HEALTH, SPIRIT_HEALTH);
		setBase(mob, Attributes.ATTACK_DAMAGE, Math.max(1, damage));
		setBase(mob, Attributes.SCALE, SPIRIT_SCALE);
		setBase(mob, Attributes.MOVEMENT_SPEED, 0.3);
		mob.setHealth(mob.getMaxHealth());
		mob.setCustomName(Component.literal(Lang.colorize(Felines.spiritName(cursed))));
		mob.setCustomNameVisible(false);
		mob.setGlowingTag(true);
		mob.setSilent(true);
		Mob added = BossFactory.add(level, mob, m -> {
			Encounters.track(encounter, m);
			return true;
		});
		if (added == null) {
			Encounters.untrack(encounter, mob.getUUID());
			return null;
		}
		Felines.addSpirit(added, encounter, level.getGameTime() + SPIRIT_SECONDS * 20L, pal.primary());
		ServerPlayer prey = nearest(level, added, targets, 32);
		if (prey != null) {
			added.setTarget(prey);
		}
		return added;
	}

	// ================================================================== helpers

	/** The guardian may act: alive, not rising, not changing phase. */
	static boolean free(Encounter encounter, Mob boss) {
		return boss.isAlive() && !boss.isRemoved() && encounter.rising == null && (encounter.cursed == null || !encounter.cursed.invulnerable());
	}

	/** Gold slows, jade poisons, sunfire burns; the curse withers a little more. */
	static void afflict(ServerPlayer player, Mob boss, String style, boolean cursed, int seconds) {
		switch (style) {
			case "jade" -> player.addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20, 1), boss);
			case "solar" -> player.igniteForSeconds(seconds);
			default -> player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, seconds * 20, 1), boss);
		}
		if (cursed) {
			player.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), boss);
		}
	}

	private static @Nullable ServerPlayer nearest(ServerLevel level, Mob mob, List<ServerPlayer> targets, double range) {
		ServerPlayer best = null;
		double bestDist = range;
		for (ServerPlayer player : targets) {
			if (!BossAbilities.valid(player, level) || Math.abs(player.getY() - mob.getY()) > 4) {
				continue;
			}
			double d = horizontal(player.position(), mob.position());
			if (d <= bestDist) {
				bestDist = d;
				best = player;
			}
		}
		return best;
	}

	static void face(Mob mob, Vec3 at) {
		double dx = at.x - mob.getX();
		double dz = at.z - mob.getZ();
		if (dx * dx + dz * dz < 1.0E-4) {
			return;
		}
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
	}

	static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Horizontal unit vector from {@code from} to {@code to} (east when they are on top of each other). */
	static Vec3 direction(Vec3 from, Vec3 to) {
		Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
		return d.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : d.normalize();
	}

	/** Angle (radians) between the facing {@code dir} and the direction from {@code center} to {@code pos}. */
	static double angle(Vec3 dir, Vec3 center, Vec3 pos) {
		Vec3 to = direction(center, pos);
		return Math.acos(Mth.clamp(dir.x * to.x + dir.z * to.z, -1.0, 1.0));
	}

	/** {@code dir} turned by {@code theta} radians around the vertical axis. */
	static Vec3 rotate(Vec3 dir, double theta) {
		double cos = Math.cos(theta);
		double sin = Math.sin(theta);
		return new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos);
	}

	/** Particles on a horizontal arc of radius {@code r} around {@code c}, from {@code a0} to {@code a1} off {@code dir}. */
	private static void arc(ServerLevel level, ParticleOptions particle, Vec3 c, Vec3 dir, double r, double a0, double a1, int points) {
		int n = Math.max(1, Math.min(48, points));
		for (int i = 0; i <= n; i++) {
			Vec3 d = rotate(dir, a0 + (a1 - a0) * i / n);
			level.sendParticles(particle, c.x + d.x * r, c.y, c.z + d.z * r, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** Particles on the radius of the fan at angle {@code theta}. */
	private static void line(ServerLevel level, ParticleOptions particle, Vec3 c, Vec3 dir, double r, double theta, int points) {
		Vec3 d = rotate(dir, theta);
		for (int i = 1; i <= points; i++) {
			double k = r * i / points;
			level.sendParticles(particle, c.x + d.x * k, c.y, c.z + d.z * k, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** Block particles of the floor under {@code pos} (stone over air). */
	private static ParticleOptions floor(ServerLevel level, Vec3 pos) {
		BlockState state = level.getBlockState(BlockPos.containing(pos.x, pos.y - 0.5, pos.z));
		return new BlockParticleOption(ParticleTypes.BLOCK, state.isAir() ? Blocks.STONE.defaultBlockState() : state);
	}

	private static void setBase(Mob mob, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static void multiply(Mob mob, Holder<Attribute> attribute, Identifier id, double multiplier) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
