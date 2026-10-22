package dev.guildy.ruin;

import dev.guildy.core.Msg;
import dev.guildy.ruin.RuinConfig.DreadAbilityDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The dread abilities of the guardians (their dread form, from Menace VII) and the attacks of the Avatar: each one is a
 * new pattern (lines, cones, rings with gaps, pulls, roots, zones on the ground, homing missiles, dives, storms with a safe
 * spot, marks...) themed by its guardian, always telegraphed (red circles, lines and cones on the floor, sounds, a warning
 * in the action bar) before it strikes, hurting only survival / adventure players, changing no block, drawn every tick within
 * the fight's particle budget. Damage: the configured value times the threat level, the group, the curse (and its phase),
 * the risen bonus; the dread bonus on the players is added after their armour by {@link Encounters#adjustDamage}.
 */
final class DreadAttacks {
	private DreadAttacks() {
	}

	/** Everything an attack needs for one tick. */
	static final class Context {
		final ServerLevel level;
		final Encounter encounter;
		final Mob boss;
		/** Survival / adventure players in reach of the guardian. */
		final List<ServerPlayer> targets;
		final RitualFx fx;
		final SplittableRandom random;
		final long now;

		Context(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> targets, RitualFx fx, SplittableRandom random, long now) {
			this.level = level;
			this.encounter = encounter;
			this.boss = boss;
			this.targets = targets;
			this.fx = fx;
			this.random = random;
			this.now = now;
		}

		/** Configured damage times the threat level, the group, the curse and its phase, the risen bonus. */
		double damage(double base) {
			return Math.max(0, base) * encounter.damageFactor();
		}

		DamageSource physical() {
			return level.damageSources().mobAttack(boss);
		}

		DamageSource magic() {
			return level.damageSources().indirectMagic(boss, boss);
		}

		DamageSource blast() {
			return level.damageSources().explosion(boss, boss);
		}

		Vec3 core() {
			return boss.position().add(0, boss.getBbHeight() * 0.6, 0);
		}

		Vec3 feet() {
			return floor(level, boss.getX(), boss.getY(), boss.getZ());
		}
	}

	/** A running dread attack. */
	abstract static class Running {
		final DreadAbilityDef def;
		final DreadFx.Theme theme;
		final String style;
		/** Ticks of its warning before it strikes. */
		final int telegraph;
		int age;

		Running(DreadAbilityDef def, String style, int telegraph) {
			this.def = def;
			this.style = style;
			this.theme = DreadFx.theme(style);
			this.telegraph = Math.max(DreadRules.MIN_TELEGRAPH_TICKS, telegraph);
		}

		/** One tick (age counted after). @return false once the attack is over */
		abstract boolean tick(Context ctx);

		/** Stopped before its end (phase change, rising, end of the fight): give back what it took. */
		void stop(Context ctx) {
		}

		/** It strikes this tick or later (still in its warning). */
		boolean warning() {
			return age < telegraph;
		}

		int durationTicks() {
			return (int) Math.round(Math.max(0, def.durationSeconds) * 20);
		}
	}

	/** Starts the dread ability {@code def}. @return null when it cannot be used now (no target in reach...) */
	static @Nullable Running start(DreadAbilityDef def, Context ctx, int telegraph) {
		return switch (def.type) {
			case "grasping_graves" -> DreadZones.Graves.cast(def, ctx, telegraph, "bone");
			case "bone_prison" -> DreadZones.Prison.cast(def, ctx, telegraph, "bone");
			case "bone_spikes" -> DreadStrikes.Lines.cast(def, ctx, telegraph, "bone");
			case "arcane_barrage" -> DreadStrikes.Missiles.cast(def, ctx, telegraph, "arcane");
			case "mirror_clones" -> DreadSummons.Mirrors.cast(def, ctx, telegraph, "arcane");
			case "gravity_well" -> DreadZones.Well.cast(def, ctx, telegraph, "arcane");
			case "quicksand" -> DreadZones.Well.cast(def, ctx, telegraph, "sand");
			case "mummy_curse" -> DreadZones.Mark.cast(def, ctx, telegraph, "sand");
			case "sandstorm" -> DreadZones.Storm.cast(def, ctx, telegraph, "sand");
			case "ice_spikes" -> DreadStrikes.Lines.cast(def, ctx, telegraph, "ice");
			case "frozen_ground" -> DreadZones.Patches.cast(def, ctx, telegraph, "ice", true);
			case "blizzard_dome" -> DreadZones.Storm.cast(def, ctx, telegraph, "ice");
			case "feather_storm" -> DreadStrikes.Spiral.cast(def, ctx, telegraph, "jade");
			case "dive_bomb" -> DreadStrikes.Dive.cast(def, ctx, telegraph, "jade");
			case "poison_rain" -> DreadStrikes.Rain.cast(def, ctx, telegraph, "poison");
			case "pounce_chain" -> DreadStrikes.Dive.cast(def, ctx, telegraph, "solar");
			case "stunning_roar" -> DreadStrikes.Cone.cast(def, ctx, telegraph, "solar");
			case "solar_beam" -> DreadStrikes.Beam.cast(def, ctx, telegraph, "solar");
			case "lava_geysers" -> DreadZones.Patches.cast(def, ctx, telegraph, "lava", false);
			case "magma_meteors" -> DreadStrikes.Rain.cast(def, ctx, telegraph, "lava");
			case "molten_ring" -> DreadStrikes.Wave.cast(def, ctx, telegraph, "lava", false);
			case "soul_chains" -> DreadZones.Tether.cast(def, ctx, telegraph, "soul");
			case "wither_aura" -> DreadZones.Aura.cast(def, ctx, telegraph, "wither");
			case "soul_eruption" -> DreadStrikes.Wave.cast(def, ctx, telegraph, "soul", false);
			case "soul_pillars" -> DreadZones.Patches.cast(def, ctx, telegraph, "soul", true);
			case "wither_skulls" -> DreadStrikes.Missiles.cast(def, ctx, telegraph, "wither");
			case "nether_rifts" -> DreadSummons.Rifts.cast(def, ctx, telegraph, "nether");
			case "ghast_rain" -> DreadStrikes.Rain.cast(def, ctx, telegraph, "ghast");
			case "collapsing_ring" -> DreadStrikes.Wave.cast(def, ctx, telegraph, "soul", true);
			default -> null;
		};
	}

	// ================================================================== helpers

	/** Hurts a player (survival / adventure only), credited to the guardian. @return the health (absorption included) lost */
	static float hurt(Context ctx, ServerPlayer player, double amount, DamageSource source) {
		if (amount <= 0 || !BossAbilities.valid(player, ctx.level)) {
			return 0;
		}
		float before = player.getHealth() + player.getAbsorptionAmount();
		player.hurtServer(ctx.level, source, (float) amount);
		return Math.max(0, before - player.getHealth() - player.getAbsorptionAmount());
	}

	static void effect(Context ctx, ServerPlayer player, Holder<MobEffect> effect, int ticks, int amplifier) {
		if (ticks > 0 && BossAbilities.valid(player, ctx.level)) {
			player.addEffect(new MobEffectInstance(effect, ticks, Math.max(0, amplifier), false, true, true), ctx.boss);
		}
	}

	static void push(ServerPlayer player, double x, double y, double z) {
		player.push(x, y, z);
		player.syncVelocity = true;
	}

	/** Top of the floor at or under (x, y, z). */
	static Vec3 floor(ServerLevel level, double x, double y, double z) {
		return CursedAttacks.floor(level, x, y, z);
	}

	static Vec3 floorUnder(ServerLevel level, ServerPlayer player) {
		return floor(level, player.getX(), player.getY(), player.getZ());
	}

	/** A random floor spot between {@code min} and {@code max} blocks from the guardian's spawn. */
	static Vec3 arenaSpot(Context ctx, double min, double max) {
		BlockPos spawn = ctx.encounter.spawn;
		double angle = ctx.random.nextDouble() * Math.PI * 2;
		double dist = min + ctx.random.nextDouble() * Math.max(0.1, max - min);
		return floor(ctx.level, spawn.getX() + 0.5 + Math.cos(angle) * dist, spawn.getY() + 0.5, spawn.getZ() + 0.5 + Math.sin(angle) * dist);
	}

	/** How far from the spawn the attacks may land (the arena, from the leash radius). */
	static double reach(Context ctx) {
		return Math.max(5, Math.min(16, ctx.encounter.def.leashRadius * 0.7));
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

	static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	/** The warning of an attack in the action bar of each player given ({@code ruin.dread.warn.<type>}). */
	static void warn(List<ServerPlayer> players, String type, Object... args) {
		for (ServerPlayer player : players) {
			Msg.actionBar(player, "ruin.dread.warn." + type, args);
		}
	}

	static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	static Vec3 away(Vec3 from, Vec3 to) {
		return CursedAttacks.away(from, to);
	}

	/** A player is standing in a floor zone of radius {@code radius} around {@code c}. */
	static boolean inside(ServerPlayer player, Vec3 c, double radius) {
		return CursedRules.inside(player.getX() - c.x, player.getY() - c.y, player.getZ() - c.z, radius);
	}

	/** The block at {@code pos} stops a projectile (or a wave). */
	static boolean solid(ServerLevel level, Vec3 pos) {
		BlockPos cell = BlockPos.containing(pos);
		return !level.isLoaded(cell) || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty();
	}

	/** Turns the guardian toward a point (yaw only). */
	static void face(Mob boss, Vec3 at) {
		double dx = at.x - boss.getX();
		double dz = at.z - boss.getZ();
		float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		boss.setYRot(yaw);
		boss.setYHeadRot(yaw);
		boss.setYBodyRot(yaw);
	}

	/** Holds the guardian still for this tick (its own AI would walk away during a channel). */
	static void hold(Mob boss, Vec3 at) {
		boss.getNavigation().stop();
		if (boss.position().distanceToSqr(at) > 0.04) {
			boss.teleportTo(at.x, at.y, at.z);
		}
		boss.setDeltaMovement(0, Math.min(0, boss.getDeltaMovement().y), 0);
	}
}
