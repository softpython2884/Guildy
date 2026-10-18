package dev.guildy.unique;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Lance du Jaguar céleste, "Bond du jaguar": the right click that starts the spear's charge makes the wielder pounce
 * forward (golden trail); at the landing, the jaguar's claws rake the monsters around (three golden slashes on each one,
 * slowness, a short push) and the charge that follows hits harder thanks to the speed. Monsters only; no block changed.
 */
final class JaguarPowers {
	static final int GOLD = 0xF5C542;
	static final int AMBER = 0xD9822B;
	static final int PALE = 0xFFF0B0;
	static final int SPOT = 0x3B2410;

	private JaguarPowers() {
	}

	/** Right click with the spear on the ground: the pounce. @return false in the air (plain vanilla charge, no cooldown) */
	static boolean pounce(ServerPlayer player) {
		if (!player.onGround() || player.isPassenger() || player.isFallFlying() || player.isInWater()) {
			return false;
		}
		UniqueConfig.JaguarPounce cfg = PowerUtil.cfg().jaguarPounce;
		Vec3 dir = PowerUtil.horizontal(player.getLookAngle());
		player.setDeltaMovement(dir.x * cfg.leap, cfg.lift, dir.z * cfg.leap);
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
		Leaps.start(player, Leaps.Kind.POUNCE);
		ServerLevel level = player.level();
		Vec3 feet = player.position();
		UniqueFx.ring(level, ParticleTypes.DUST_PLUME, feet.add(0, 0.1, 0), 0.7, 10, 0);
		UniqueFx.burst(level, UniqueFx.fade(PALE, AMBER, 1.3F), feet.add(0, 0.5, 0), 14, 0.35, 0.02);
		UniqueFx.burst(level, UniqueFx.dust(SPOT, 1.0F), feet.add(0, 0.6, 0), 5, 0.3, 0.0);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 0.9F, 1.4F);
		return true;
	}

	/** Every tick of the pounce: a golden, spotted trail behind the jaguar. */
	static void trail(ServerPlayer player, int age, Vec3 motion) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0, 0.9, 0);
		Vec3 back = motion.lengthSqr() > 1.0E-4 ? motion.normalize().scale(-0.5) : Vec3.ZERO;
		UniqueFx.burst(level, UniqueFx.fade(PALE, AMBER, 1.4F), at.add(back), 3, 0.22, 0.0);
		UniqueFx.dot(level, UniqueFx.dust(SPOT, 1.1F), at.add(back.scale(1.6)).add(0, (age % 3 - 1) * 0.25, 0));
		if (age % 2 == 0) {
			UniqueFx.dot(level, ParticleTypes.WAX_ON, at.add(back.scale(2.2)));
		}
	}

	/** The landing: claws on the monsters around. */
	static void land(ServerPlayer player) {
		UniqueConfig.JaguarPounce cfg = PowerUtil.cfg().jaguarPounce;
		ServerLevel level = player.level();
		Vec3 center = player.position();
		Vec3 facing = PowerUtil.horizontal(player.getLookAngle());
		List<LivingEntity> targets = PowerUtil.hostilesAround(level, center, cfg.radius, cfg.maxTargets, null);
		for (LivingEntity target : targets) {
			PowerUtil.melee(target, player, cfg.damage);
			if (cfg.slowSeconds > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.slowSeconds * 20, cfg.slowLevel - 1));
			}
			Vec3 away = PowerUtil.horizontal(target.position().subtract(center)).scale(0.35);
			PowerUtil.push(target, new Vec3(away.x, 0.15, away.z));
			claws(level, target, facing);
		}
		// Impact: a dust plume and a golden ring racing outward to the claws' reach.
		UniqueFx.ring(level, ParticleTypes.DUST_PLUME, center.add(0, 0.15, 0), 0.9, 12, 0);
		UniqueFx.burst(level, ParticleTypes.CRIT, center.add(0, 0.6, 0), 12, 0.5, 0.25);
		double radius = cfg.radius;
		UniqueFx.play(level, 6, (lvl, age) -> {
			double r = 0.6 + (radius - 0.6) * age / 5.0;
			UniqueFx.ring(lvl, UniqueFx.fade(PALE, GOLD, 1.1F), center.add(0, 0.2, 0), r, (int) (10 + r * 5), age * 0.4);
			return age < 5;
		});
		level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 0.9F, 1.4F);
		if (!targets.isEmpty()) {
			level.playSound(null, center.x, center.y, center.z, SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 0.9F, 1.3F);
		}
	}

	/** Three parallel golden slashes across the front of a monster, drawn one per tick. */
	private static void claws(ServerLevel level, LivingEntity target, Vec3 facing) {
		Vec3 right = new Vec3(-facing.z, 0, facing.x);
		Vec3 base = target.position().subtract(facing.scale(target.getBbWidth() * 0.55));
		double height = target.getBbHeight();
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
		UniqueFx.play(level, 3, (lvl, age) -> {
			double offset = (age - 1) * 0.28;
			Vec3 top = base.add(right.scale(offset - 0.45)).add(0, height * 0.9, 0);
			Vec3 bottom = base.add(right.scale(offset + 0.45)).add(0, height * 0.2, 0);
			UniqueFx.line(lvl, UniqueFx.fade(PALE, AMBER, 0.75F), top, bottom, 7);
			UniqueFx.dot(lvl, ParticleTypes.CRIT, bottom);
			return age < 2;
		});
	}
}
