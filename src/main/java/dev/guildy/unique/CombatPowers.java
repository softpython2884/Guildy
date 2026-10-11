package dev.guildy.unique;

import dev.guildy.claim.ClaimGuard;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Combat powers: frost nova and blink strike (right click), shockwave (mace smash), phoenix arrows, soul harvest.
 * Only hostile mobs are affected; nothing ever changes a block.
 */
final class CombatPowers {
	private CombatPowers() {
	}

	// ================================================================== Croc-de-Givre

	/** Right click: slows, freezes and chills the hostile mobs around the player. */
	static boolean frostNova(ServerPlayer player) {
		UniqueConfig.FrostNova cfg = PowerUtil.cfg().frostNova;
		ServerLevel level = player.level();
		Vec3 center = player.position();
		for (LivingEntity target : PowerUtil.hostilesAround(level, center, cfg.radius, 24, player)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.slowSeconds * 20, cfg.slowLevel - 1));
			if (target.canFreeze()) {
				// Fully frozen (shaking, freeze damage) for about the slowness duration: thaws 2 ticks per tick.
				target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + cfg.slowSeconds * 40));
			}
			PowerUtil.damage(target, PowerUtil.GIVRE, player, cfg.damage);
			level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		}
		for (int ring = 1; ring <= 3; ring++) {
			PowerUtil.ring(level, ParticleTypes.SNOWFLAKE, center.add(0, 0.3, 0), cfg.radius * ring / 3.0, 12 * ring);
		}
		level.sendParticles(ParticleTypes.ITEM_SNOWBALL, center.x, center.y + 1, center.z, 30, cfg.radius / 3, 0.4, cfg.radius / 3, 0.1);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.5F, 0.8F);
		return true;
	}

	// ================================================================== Murmure du Néant

	/** Right click: teleport behind the aimed hostile mob and strike it. @return false when nothing happened */
	static boolean blinkStrike(ServerPlayer player) {
		UniqueConfig.BlinkStrike cfg = PowerUtil.cfg().blinkStrike;
		LivingEntity target = PowerUtil.aimedHostile(player, cfg.range);
		if (target == null) {
			Msg.actionBar(player, "unique.blink.no_target");
			return false;
		}
		ServerLevel level = player.level();
		Vec3 destination = blinkDestination(player, target);
		if (destination == null) {
			Msg.actionBar(player, "unique.blink.blocked");
			return false;
		}
		Vec3 from = player.position();
		double dx = target.getX() - destination.x;
		double dz = target.getZ() - destination.z;
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.4F);
		player.teleportTo(level, destination.x, destination.y, destination.z, Set.of(), yaw, player.getXRot(), false);
		player.resetFallDistance();
		PowerUtil.melee(target, player, cfg.damage);
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 20, 0.3, 0.4, 0.3, 0.2);
		level.sendParticles(ParticleTypes.PORTAL, destination.x, destination.y + 1, destination.z, 40, 0.3, 0.6, 0.3, 0.5);
		level.playSound(null, destination.x, destination.y, destination.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
		return true;
	}

	/** A free spot behind the target (or in front of it), never inside another guild's territory. */
	private static @Nullable Vec3 blinkDestination(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		Vec3 facing = horizontal(target.getLookAngle());
		Vec3 toPlayer = horizontal(player.position().subtract(target.position()));
		double distance = target.getBbWidth() / 2 + player.getBbWidth() / 2 + 0.6;
		Vec3[] candidates = {
				target.position().subtract(facing.scale(distance)),
				target.position().add(toPlayer.scale(distance)),
				target.position().add(toPlayer.scale(distance)).add(0, 1, 0)
		};
		for (Vec3 candidate : candidates) {
			if (safeSpot(player, level, candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean safeSpot(ServerPlayer player, ServerLevel level, Vec3 pos) {
		BlockPos block = BlockPos.containing(pos);
		if (!level.isLoaded(block) || !level.getWorldBorder().isWithinBounds(block)) {
			return false;
		}
		Guild owner = ClaimGuard.ownerAt(level, block);
		if (owner != null && !ClaimGuard.allowed(player, owner, GuildPermission.INTERACT)) {
			return false;
		}
		AABB box = player.getBoundingBox().move(pos.subtract(player.position()));
		return level.noCollision(player, box) && !level.containsAnyLiquid(box);
	}

	private static Vec3 horizontal(Vec3 v) {
		Vec3 flat = new Vec3(v.x, 0, v.z);
		return flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	// ================================================================== Marteau du Séisme

	/** A mace smash attack hit {@code victim}: the shockwave hits the other hostile mobs around. */
	static void shockwave(ServerPlayer player, LivingEntity victim) {
		UniqueConfig.Shockwave cfg = PowerUtil.cfg().shockwave;
		if (!PowerUtil.ready(player, UniquePower.SHOCKWAVE, cfg.cooldownSeconds)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 center = victim.position();
		for (LivingEntity target : PowerUtil.hostilesAround(level, center, cfg.radius, 16, victim)) {
			double distance = Math.max(0.5, Math.sqrt(target.distanceToSqr(center)));
			double falloff = Math.max(0.3, 1.0 - distance / (cfg.radius + 1));
			PowerUtil.melee(target, player, cfg.damage * falloff);
			Vec3 away = horizontal(target.position().subtract(center)).scale(0.6 + falloff * 0.6);
			target.setDeltaMovement(target.getDeltaMovement().add(away.x, cfg.lift * falloff, away.z));
			target.syncVelocity = true;
		}
		BlockPos below = BlockPos.containing(center.x, center.y - 0.5, center.z);
		BlockState ground = level.getBlockState(below);
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), center.x, center.y + 0.1, center.z, 60, cfg.radius / 2, 0.1,
					cfg.radius / 2, 0.3);
		}
		PowerUtil.ring(level, ParticleTypes.DUST_PLUME, center.add(0, 0.2, 0), cfg.radius * 0.6, 20);
		level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 2, 0.5, 0.2, 0.5, 0.0);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	// ================================================================== Arc du Phénix

	/** A phoenix arrow hit {@code victim} (alive or killed): the hostile mobs around catch fire. */
	static void phoenixBurst(ServerPlayer player, LivingEntity victim) {
		UniqueConfig.PhoenixArrows cfg = PowerUtil.cfg().phoenixArrows;
		if (!PowerUtil.ready(player, UniquePower.PHOENIX_ARROWS, cfg.cooldownSeconds)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 center = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
		for (LivingEntity target : PowerUtil.hostilesAround(level, victim.position(), cfg.radius, 12, null)) {
			if (cfg.fireSeconds > 0) {
				target.igniteForSeconds(cfg.fireSeconds);
			}
			PowerUtil.damage(target, PowerUtil.BRASIER, player, cfg.damage);
		}
		level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 40, cfg.radius / 3, 0.4, cfg.radius / 3, 0.08);
		level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 8, 0.4, 0.3, 0.4, 0.0);
		PowerUtil.ring(level, ParticleTypes.FLAME, victim.position().add(0, 0.2, 0), cfg.radius, 24);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	// ================================================================== Faux des Âmes

	/** The wielder killed a hostile mob with the scythe in hand. */
	static void soulHarvest(ServerPlayer player, LivingEntity victim) {
		UniqueConfig.SoulHarvest cfg = PowerUtil.cfg().soulHarvest;
		ServerLevel level = player.level();
		if (cfg.heal > 0) {
			player.heal((float) cfg.heal);
		}
		if (cfg.speedSeconds > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, cfg.speedSeconds * 20, 0));
		}
		Vec3 from = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
		PowerUtil.line(level, ParticleTypes.SOUL, from, player.position().add(0, 1, 0));
		level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y, from.z, 6, 0.2, 0.3, 0.2, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}
