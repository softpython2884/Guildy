package dev.guildy.unique;

import dev.guildy.core.Msg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Masse du Zénith, "Ascension solaire": the right click throws the wielder into the sky in a sheaf of flames (no fall
 * damage until the landing: the mace's smash attack is the point); at the landing, or at the first smash attack, a sun
 * bursts: a flash, a ring of fire racing outward, rays turning on the ground and a pillar of light. The monsters around
 * burn (guildy:brasier, twice for the undead, never the fire-immune ones). No block is set on fire.
 */
final class SolarPowers {
	static final int CORE = 0xFFF7D0;
	static final int SUN = 0xFFCF3A;
	static final int EMBER = 0xFF7A1A;
	static final int CRIMSON = 0xD8401C;

	private SolarPowers() {
	}

	/** Right click with the mace. @return true when the leap started (cooldown) */
	static boolean leap(ServerPlayer player) {
		if (player.isPassenger() || player.isFallFlying()) {
			return false;
		}
		ServerLevel level = player.level();
		if (!level.noCollision(player, player.getBoundingBox().expandTowards(0, 2.0, 0))) {
			Msg.actionBar(player, "unique.leap.blocked");
			return false;
		}
		UniqueConfig.SolarLeap cfg = PowerUtil.cfg().solarLeap;
		Vec3 dir = PowerUtil.horizontal(player.getLookAngle()).scale(cfg.forward);
		player.setDeltaMovement(dir.x, cfg.upward, dir.z);
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
		Leaps.start(player, Leaps.Kind.SOLAR);
		Vec3 feet = player.position();
		UniqueFx.burst(level, ParticleTypes.FLAME, feet.add(0, 0.2, 0), 30, 0.35, 0.06);
		UniqueFx.burst(level, ParticleTypes.LAVA, feet.add(0, 0.2, 0), 6, 0.3, 0.0);
		UniqueFx.play(level, 6, (lvl, age) -> {
			double r = 0.5 + age * 0.35;
			UniqueFx.ring(lvl, UniqueFx.fade(CORE, EMBER, 1.2F), feet.add(0, 0.15, 0), r, (int) (8 + r * 6), age * 0.5);
			return age < 5;
		});
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.8F, 1.2F);
		return true;
	}

	/** Every tick of the leap: a column of fire on the way up, a small turning sun on the way down. */
	static void trail(ServerPlayer player, int age, Vec3 motion) {
		ServerLevel level = player.level();
		Vec3 at = player.position();
		if (motion.y >= 0) {
			for (int i = 0; i < 3; i++) {
				UniqueFx.move(level, ParticleTypes.FLAME, at.add((i - 1) * 0.15, 0.1, (i % 2) * 0.15 - 0.07), new Vec3(0, -0.08, 0));
			}
			UniqueFx.dot(level, UniqueFx.fade(SUN, CRIMSON, 1.2F), at.add(0, -0.2, 0));
		} else {
			UniqueFx.ring(level, UniqueFx.fade(CORE, SUN, 1.0F), at.add(0, 0.9, 0), 0.7, 6, age * 0.6);
			UniqueFx.burst(level, ParticleTypes.FLAME, at.add(0, 0.4, 0), 2, 0.2, 0.01);
			if (age % 2 == 0) {
				UniqueFx.dot(level, ParticleTypes.END_ROD, at.add(0, 1.8, 0));
			}
		}
	}

	/** A smash attack of the mace during the leap: the sun bursts at the victim (once per leap). */
	static void smash(ServerPlayer player, LivingEntity victim) {
		Leaps.Leap leap = Leaps.get(player);
		if (leap == null || leap.kind != Leaps.Kind.SOLAR || leap.spent) {
			return;
		}
		Leaps.spend(player);
		burst(player, victim.position());
	}

	/** The sun bursts at {@code center}: the monsters around burn, then the animation plays. */
	static void burst(ServerPlayer player, Vec3 center) {
		UniqueConfig.SolarLeap cfg = PowerUtil.cfg().solarLeap;
		ServerLevel level = player.level();
		for (LivingEntity target : PowerUtil.hostilesAround(level, center, cfg.radius, 24, null)) {
			double damage = cfg.damage * (PowerUtil.isUndead(target) ? cfg.undeadMultiplier : 1);
			if (cfg.fireSeconds > 0 && !target.fireImmune()) {
				target.igniteForSeconds(cfg.fireSeconds);
			}
			PowerUtil.damage(target, PowerUtil.BRASIER, player, damage);
			UniqueFx.burst(level, ParticleTypes.FLAME, target.position().add(0, target.getBbHeight() * 0.5, 0), 10, 0.3, 0.04);
		}
		double radius = cfg.radius;
		Vec3 ground = center.add(0, 0.15, 0);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.5F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.8F);
		UniqueFx.play(level, 14, (lvl, age) -> {
			if (age == 0) {
				UniqueFx.dot(lvl, UniqueFx.flash(SUN), center.add(0, 1, 0));
				UniqueFx.burst(lvl, ParticleTypes.EXPLOSION, center.add(0, 0.6, 0), 1, 0, 0);
				UniqueFx.burst(lvl, ParticleTypes.FLAME, center.add(0, 0.5, 0), 40, radius / 3, 0.12);
				UniqueFx.burst(lvl, ParticleTypes.LAVA, center.add(0, 0.4, 0), 10, radius / 4, 0.0);
			}
			if (age <= 7) {
				// The ring of fire racing outward.
				double r = 0.5 + (radius - 0.5) * age / 7.0;
				UniqueFx.ring(lvl, ParticleTypes.FLAME, ground, r, (int) (8 + r * 5), age * 0.3);
				UniqueFx.ring(lvl, UniqueFx.fade(CORE, SUN, 1.3F), ground.add(0, 0.1, 0), r * 0.85, (int) (6 + r * 4), -age * 0.3);
			}
			if (age <= 10) {
				// Eight sun rays turning on the ground, the pillar of light shrinking.
				double length = radius * Math.min(1, (age + 1) / 5.0);
				for (int ray = 0; ray < 8; ray++) {
					double angle = age * 0.15 + Math.PI * ray / 4;
					Vec3 tip = ground.add(Math.cos(angle) * length, 0, Math.sin(angle) * length);
					UniqueFx.line(lvl, UniqueFx.fade(SUN, EMBER, 0.9F), ground.add(Math.cos(angle) * 0.6, 0, Math.sin(angle) * 0.6), tip, 1.5);
				}
				UniqueFx.column(lvl, ParticleTypes.END_ROD, center.add(0, 0.5, 0), 6.0 * (1 - age / 11.0), 6);
			}
			if (age == 12) {
				UniqueFx.ring(lvl, ParticleTypes.SMALL_FLAME, ground, radius, (int) (10 + radius * 4), 0);
			}
			return age < 13;
		});
	}
}
