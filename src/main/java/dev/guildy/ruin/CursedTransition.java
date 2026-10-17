package dev.guildy.ruin;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.SplittableRandom;

/**
 * A phase transition of a cursed guardian (about four seconds, {@code cursed.phases.transitionSeconds}): it becomes
 * invulnerable and frozen (no AI) and rises in the air while spinning, the arena darkens (darkness, fog), a giant inverted
 * pentagram ignites on the floor under it and pulses with a heartbeat, soul fire pillars rise from its five points, chains
 * of souls bind every player (slowed), energy rushes from the star into the guardian; then it slams down: shockwave pushing
 * the players back (no damage), sonic booms, visual lightning on the star's points, and new escort mobs burst out of the
 * ground. Drawn within the fight's particle budget; the fight logic (stats of the new phase) is in {@link CursedEncounter}.
 */
final class CursedTransition {
	private static final double WANTED_LIFT = 3.0;

	final int toPhase;
	final int duration;
	/** Feet position at the start (on the floor under the guardian). */
	final Vec3 anchor;
	final double lift;
	final double radius;
	private final double facing;
	private final ParticleOptions floor;
	private final SplittableRandom random = new SplittableRandom();
	int age;
	private float yaw;
	private boolean slammed;
	private boolean minions;
	private int nextBeat;

	CursedTransition(ServerLevel level, Encounter encounter, Mob boss, int toPhase, CursedSettings settings) {
		this.toPhase = toPhase;
		this.duration = SummonRules.ticks(settings.phases.transitionSeconds);
		this.anchor = CursedAttacks.floor(level, boss.getX(), boss.getY(), boss.getZ());
		double height = Math.max(0.5, boss.getBbHeight());
		this.lift = CursedRules.maxLift(Resurrection.headroom(level, BlockPos.containing(anchor), 12), height, WANTED_LIFT);
		this.radius = Math.max(4.5, Math.min(9.0, encounter.def.leashRadius * 0.5));
		this.floor = CursedAttacks.debris(level, anchor);
		this.yaw = boss.getYRot();
		BlockPos entrance = encounter.ruin.entrance != null ? encounter.ruin.entrance : encounter.ruin.center();
		double dx = entrance.getX() + 0.5 - anchor.x;
		double dz = entrance.getZ() + 0.5 - anchor.z;
		this.facing = dx * dx + dz * dz < 1.0E-4 ? 0 : Math.atan2(dz, dx);
	}

	double progress() {
		return Math.min(1.0, (double) age / Math.max(1, duration));
	}

	// ================================================================== lifecycle

	/** The guardian freezes, invulnerable; the curse is announced; the arena darkens. */
	void begin(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present, CursedSettings settings, RitualFx fx) {
		boss.setNoAi(true);
		boss.setPermanentlyInvulnerable(true);
		boss.stopUsingItem();
		boss.getNavigation().stop();
		boss.setTarget(null);
		boss.clearFire();
		boss.setDeltaMovement(Vec3.ZERO);
		Vec3 core = boss.position().add(0, boss.getBbHeight() / 2, 0);
		fx.dot(RitualFx.flash(CursedFx.PURPLE), core);
		fx.burst(ParticleTypes.SOUL, core, 40, 0.6, 0.1);
		fx.burst(RitualFx.dust(CursedFx.VOID, 2.0F), core, 40, 0.8, 0.0);
		fx.burst(ParticleTypes.REVERSE_PORTAL, core, 50, 0.6, 0.4);
		sound(level, SoundEvents.WARDEN_ROAR, 3.0F, 0.6F);
		sound(level, SoundEvents.ENDER_DRAGON_GROWL, 2.5F, 0.6F);
		sound(level, SoundEvents.WITHER_AMBIENT, 2.0F, 0.4F);
		encounter.bar.setCreateWorldFog(true);
		encounter.bar.setDarkenScreen(true);
		String bossName = Lang.colorize(encounter.def.boss.name);
		int darkness = (int) Math.round(settings.phases.darknessSeconds * 20);
		for (ServerPlayer player : present) {
			Msg.title(player, Lang.get("ruin.cursed.phase.title." + toPhase), Lang.get("ruin.cursed.phase.subtitle." + toPhase, bossName), 5, 60, 15);
			if (darkness > 0) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, darkness, 0, false, false, true), boss);
			}
			if (toPhase >= CursedRules.LAST_PHASE) {
				// The curse on the screen (the elder guardian's face and sound, a vanilla client effect).
				player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0F));
			} else {
				Msg.sound(player, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0F, 0.6F);
			}
		}
		nextBeat = 6;
	}

	/** One tick: body, sounds, particles, chains, slam, minions. @return true when the transition is over */
	boolean tick(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present, RitualFx fx, CursedSettings settings) {
		age++;
		double p = progress();
		double y = anchor.y + CursedRules.lift(p, lift);
		yaw += (float) (4 + 26 * Math.sin(Math.PI * Math.min(1, p / CursedRules.SLAM)));
		boss.setPos(anchor.x, y, anchor.z);
		boss.setDeltaMovement(Vec3.ZERO);
		boss.setYRot(yaw);
		boss.setYBodyRot(yaw);
		boss.setYHeadRot(yaw);
		Vec3 core = new Vec3(anchor.x, y + boss.getBbHeight() * 0.55, anchor.z);
		cues(level, p);
		drawStar(fx, p);
		drawBody(fx, core, p);
		if (p >= CursedRules.CHAINS_START && p <= CursedRules.CHAINS_END) {
			bind(level, boss, present, fx, core);
		}
		if (p >= 0.3 && p < CursedRules.SLAM) {
			drawConvergence(fx, core, SummonRules.phase(p, 0.3, CursedRules.SLAM));
		}
		if (!slammed && p >= CursedRules.SLAM) {
			slammed = true;
			slam(level, boss, present, fx, settings);
		}
		if (slammed) {
			drawShockwave(fx, age - (int) Math.round(duration * CursedRules.SLAM));
		}
		if (!minions && p >= CursedRules.MINIONS) {
			minions = true;
			burstMinions(level, encounter, settings.phases.minions, fx);
		}
		return age >= duration;
	}

	/** The guardian may move and be hurt again, back on the floor. */
	void release(Mob boss, Encounter encounter, RuinConfig cfg) {
		boss.setPos(anchor.x, anchor.y, anchor.z);
		boss.setDeltaMovement(Vec3.ZERO);
		boss.setNoAi(false);
		boss.setPermanentlyInvulnerable(false);
		encounter.bar.setDarkenScreen(cfg.encounter.darkenSky);
	}

	// ================================================================== sounds

	private void cues(ServerLevel level, double p) {
		if (age >= nextBeat && p < CursedRules.SLAM) {
			sound(level, SoundEvents.WARDEN_HEARTBEAT, 3.0F, 0.6F + 0.6F * (float) p);
			nextBeat = age + (int) Math.max(4, Math.round(14 - 10 * p));
		}
		if (age == at(0.12)) {
			sound(level, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.4F);
		}
		if (age == at(0.3)) {
			sound(level, SoundEvents.CHAIN_PLACE, 3.0F, 0.5F);
			sound(level, SoundEvents.GHAST_SCREAM, 1.5F, 0.4F);
		}
		if (age == at(0.5)) {
			sound(level, SoundEvents.SCULK_SHRIEKER_SHRIEK, 3.0F, 0.5F);
		}
		if (age == at(0.65)) {
			sound(level, SoundEvents.RESPAWN_ANCHOR_CHARGE, 3.0F, 0.5F);
			sound(level, SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.5F);
		}
	}

	// ================================================================== drawing

	/** The giant inverted pentagram on the floor: it draws itself, then pulses; soul fire pillars rise from its points. */
	private void drawStar(RitualFx fx, double p) {
		Vec3 c = anchor.add(0, 0.07, 0);
		double spin = facing + age * 0.01;
		double draw = SummonRules.phase(p, CursedRules.CIRCLE_START, CursedRules.CIRCLE_DRAWN);
		if (draw <= 0) {
			return;
		}
		if (age % 2 == 0) {
			double pulse = 1 + 0.03 * Math.sin(age * 0.7);
			int color = RitualFx.mix(CursedFx.PURPLE, CursedFx.BLOOD_BRIGHT, 0.5 + 0.5 * Math.sin(age * 0.45));
			CursedFx.pentagram(fx, c, radius * pulse, spin, draw, color, CursedFx.BLOOD_BRIGHT, 1.5F);
			if (draw >= 1) {
				fx.ring(RitualFx.dust(CursedFx.VOID, 1.6F), c, radius * 1.3, (int) (radius * 9), -spin);
			}
		}
		if (draw >= 0.6 && age % 2 == 1) {
			for (int i = 0; i < 5; i++) {
				double angle = spin + Math.PI / 2 + Math.PI + Math.PI * 2 * i * 2 / 5;
				double x = c.x + Math.cos(angle) * radius * 0.97;
				double z = c.z + Math.sin(angle) * radius * 0.97;
				fx.move(ParticleTypes.SOUL_FIRE_FLAME, x, c.y + random.nextDouble() * 0.6, z, 0, 0.12 + random.nextDouble() * 0.1, 0);
				fx.move(ParticleTypes.LARGE_SMOKE, x, c.y + 1.0 + random.nextDouble(), z, 0, 0.06, 0);
			}
		}
		if (age % 3 == 0) {
			// Flames running along the star's lines.
			double t = random.nextDouble();
			int i = random.nextInt(5);
			double a0 = spin + Math.PI / 2 + Math.PI + Math.PI * 2 * i * 2 / 5;
			double a1 = spin + Math.PI / 2 + Math.PI + Math.PI * 2 * (i + 1) * 2 / 5;
			double x = c.x + (Math.cos(a0) + (Math.cos(a1) - Math.cos(a0)) * t) * radius * 0.97;
			double z = c.z + (Math.sin(a0) + (Math.sin(a1) - Math.sin(a0)) * t) * radius * 0.97;
			fx.move(ParticleTypes.SOUL_FIRE_FLAME, x, c.y + 0.05, z, 0, 0.06, 0);
		}
	}

	/** Void and souls spiralling around the rising guardian. */
	private void drawBody(RitualFx fx, Vec3 core, double p) {
		if (p >= CursedRules.SLAM) {
			return;
		}
		double r = 1.8 - 0.8 * p;
		for (int strand = 0; strand < 3; strand++) {
			double angle = age * 0.35 + strand * Math.PI * 2 / 3;
			double h = (age * 0.15 + strand) % 3.0 - 1.5;
			fx.dot(strand == 0 ? ParticleTypes.SOUL_FIRE_FLAME : RitualFx.dust(strand == 1 ? CursedFx.VOID : CursedFx.VIOLET, 1.3F),
					core.x + Math.cos(angle) * r, core.y + h, core.z + Math.sin(angle) * r);
		}
		if (age % 4 == 0) {
			fx.burst(ParticleTypes.SCULK_SOUL, core.x, core.y, core.z, 2, 0.6, 0.6, 0.6, 0.02);
		}
	}

	/** Chains of souls from the guardian to every player, who are slowed while bound. */
	private void bind(ServerLevel level, Mob boss, List<ServerPlayer> present, RitualFx fx, Vec3 core) {
		int bound = 0;
		for (ServerPlayer player : present) {
			if (!BossAbilities.valid(player, level) || player.distanceToSqr(core) > 30.0 * 30.0 || bound >= 8) {
				continue;
			}
			bound++;
			Vec3 chest = player.position().add(0, player.getBbHeight() * 0.55, 0);
			CursedFx.chain(fx, core, chest, Math.min(2.0, chest.distanceTo(core) * 0.1), age, true);
			if (age % 20 == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 2, false, false, true), boss);
				fx.burst(ParticleTypes.SOUL, chest.x, chest.y, chest.z, 4, 0.3, 0.4, 0.3, 0.02);
			}
		}
		if (age % 12 == 0) {
			sound(level, SoundEvents.CHAIN_PLACE, 2.0F, 0.5F + random.nextFloat() * 0.3F);
		}
	}

	/** Energy rushing from the star's points into the guardian, a sphere of void closing on it. */
	private void drawConvergence(RitualFx fx, Vec3 core, double k) {
		double spin = facing + age * 0.01;
		if (age % 2 == 0) {
			for (int i = 0; i < 5; i++) {
				double angle = spin + Math.PI / 2 + Math.PI + Math.PI * 2 * i * 2 / 5;
				fx.dot(RitualFx.trail(core, i % 2 == 0 ? CursedFx.PURPLE : CursedFx.BLOOD_BRIGHT, 10 + random.nextInt(6)), anchor.x + Math.cos(angle) * radius,
						anchor.y + 0.3, anchor.z + Math.sin(angle) * radius);
			}
		}
		if (age % 3 == 0) {
			CursedFx.implode(fx, core, 3.5, k, CursedFx.VOID, 34, age * 0.2);
		}
	}

	/** The slam: flash, booms, explosion of void (no real explosion), lightning, players pushed back (no damage). */
	private void slam(ServerLevel level, Mob boss, List<ServerPlayer> present, RitualFx fx, CursedSettings settings) {
		boss.setPos(anchor.x, anchor.y, anchor.z);
		Vec3 core = anchor.add(0, Math.max(0.5, boss.getBbHeight()) * 0.5, 0);
		fx.dot(RitualFx.flash(CursedFx.PURPLE), core);
		fx.dot(RitualFx.flash(CursedFx.BLOOD_BRIGHT), core.add(0, 1.5, 0));
		fx.burst(ParticleTypes.EXPLOSION_EMITTER, core, 1, 0, 0);
		fx.dot(ParticleTypes.SONIC_BOOM, core);
		for (int i = 0; i < 10; i++) {
			double angle = Math.PI * 2 * i / 10;
			fx.dot(ParticleTypes.SONIC_BOOM, anchor.x + Math.cos(angle) * 3.5, anchor.y + 1.0, anchor.z + Math.sin(angle) * 3.5);
		}
		fx.sphereBurst(RitualFx.fade(CursedFx.PURPLE, CursedFx.VOID, 1.8F), core, 0.5, 100, 0.55);
		fx.burst(ParticleTypes.SOUL_FIRE_FLAME, core, 50, 0.8, 0.2);
		fx.burst(floor, anchor.x, anchor.y + 0.2, anchor.z, 60, 2.5, 0.1, 2.5, 0.35);
		sound(level, SoundEvents.WARDEN_SONIC_BOOM, 3.5F, 0.6F);
		level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.5F);
		sound(level, SoundEvents.MACE_SMASH_GROUND_HEAVY, 3.0F, 0.5F);
		sound(level, SoundEvents.LIGHTNING_BOLT_THUNDER, 5.0F, 0.5F);
		double spin = facing + age * 0.01;
		for (int i = 0; i < 5; i += 2) {
			double angle = spin + Math.PI / 2 + Math.PI + Math.PI * 2 * i * 2 / 5;
			CursedFx.bolt(level, new Vec3(anchor.x + Math.cos(angle) * radius, anchor.y, anchor.z + Math.sin(angle) * radius));
		}
		double reach = settings.phases.shockwaveRadius;
		double strength = settings.phases.shockwaveStrength;
		if (reach <= 0 || strength <= 0) {
			return;
		}
		for (ServerPlayer player : present) {
			if (!BossAbilities.valid(player, level)) {
				continue;
			}
			double dist = CursedAttacks.horizontal(player.position(), anchor);
			if (dist > reach || Math.abs(player.getY() - anchor.y) > 4) {
				continue;
			}
			Vec3 away = CursedAttacks.away(anchor, player.position());
			double push = strength * (1.0 - 0.6 * dist / reach);
			player.push(away.x * push, 0.35 + 0.15 * push, away.z * push);
			player.syncVelocity = true;
		}
	}

	/** Rings of debris and void running outwards after the slam. */
	private void drawShockwave(RitualFx fx, int t) {
		if (t < 0 || t > 14) {
			return;
		}
		double r = 1.0 + t * 0.9;
		fx.ring(floor, anchor.add(0, 0.1, 0), r, (int) (12 + r * 4), t * 0.1);
		fx.ring(RitualFx.dust(CursedFx.VOID, 1.6F), anchor.add(0, 0.4, 0), r, (int) (10 + r * 3), -t * 0.1);
		if (t % 2 == 0) {
			fx.ring(ParticleTypes.SOUL_FIRE_FLAME, anchor.add(0, 0.2, 0), r * 0.9, (int) (6 + r * 2), 0);
		}
	}

	/** New escort mobs burst out of the ground around the arena (within the escort cap). */
	private void burstMinions(ServerLevel level, Encounter encounter, int count, RitualFx fx) {
		for (int i = 0; i < count; i++) {
			double angle = facing + Math.PI * 2 * i / Math.max(1, count) + random.nextDouble() * 0.5;
			double dist = 3 + random.nextDouble() * Math.max(1, radius - 3);
			BlockPos around = BlockPos.containing(anchor.x + Math.cos(angle) * dist, anchor.y, anchor.z + Math.sin(angle) * dist);
			Mob mob = Encounters.summon(level, encounter, around);
			if (mob == null) {
				continue;
			}
			Vec3 at = mob.position();
			fx.burst(CursedAttacks.debris(level, at), at.x, at.y + 0.2, at.z, 30, 0.4, 0.1, 0.4, 0.3);
			fx.burst(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.3, at.z, 16, 0.4, 0.4, 0.4, 0.05);
			fx.burst(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.8, at.z, 8, 0.3, 0.3, 0.3, 0.02);
		}
		sound(level, SoundEvents.WARDEN_EMERGE, 2.5F, 0.8F);
	}

	// ================================================================== helpers

	private int at(double fraction) {
		return Math.max(1, (int) Math.round(duration * fraction));
	}

	private void sound(ServerLevel level, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		level.playSound(null, anchor.x, anchor.y, anchor.z, sound, SoundSource.HOSTILE, volume, pitch);
	}
}
