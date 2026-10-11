package dev.guildy.unique;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Lightning powers: Foudrelame's "Appel de la foudre" (right click) and the Trident du Léviathan's "Colère des abysses"
 * (thrown hits). The bolts are VISUAL ONLY (no fire, no block change, no villager/creeper conversion): the damage is
 * dealt by the power to hostile mobs only. A bolt is never spawned on copper or a lightning rod (the vanilla strike
 * would change those blocks): sparks are shown instead.
 */
final class LightningPowers {
	private LightningPowers() {
	}

	/** Right click with Foudrelame. @return true when the power fired (cooldown starts) */
	static boolean stormCall(ServerPlayer player) {
		UniqueConfig.StormCall cfg = PowerUtil.cfg().stormCall;
		ServerLevel level = player.level();
		Vec3 point = aimPoint(player, cfg.range);
		List<LivingEntity> targets = PowerUtil.hostilesAround(level, point, cfg.radius, cfg.maxTargets, null);
		if (targets.isEmpty()) {
			bolt(level, point);
		}
		for (LivingEntity target : targets) {
			bolt(level, target.position());
			PowerUtil.damage(target, PowerUtil.FOUDRE, player, cfg.damage);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 12, 0.3, 0.6, 0.3, 0.2);
		}
		level.playSound(null, point.x, point.y, point.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 2.0F, 1.0F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY(1.0), player.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
		return true;
	}

	/** A thrown Trident du Léviathan hit {@code victim} (alive or killed). */
	static void tideCall(ServerPlayer player, LivingEntity victim) {
		UniqueConfig.TideCall cfg = PowerUtil.cfg().tideCall;
		if (!PowerUtil.ready(player, UniquePower.TIDE_CALL, cfg.cooldownSeconds)) {
			return;
		}
		ServerLevel level = player.level();
		bolt(level, victim.position());
		if (victim.isAlive()) {
			PowerUtil.damage(victim, PowerUtil.FOUDRE, player, cfg.damage);
		}
		Vec3 from = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
		for (LivingEntity next : PowerUtil.hostilesAround(level, victim.position(), cfg.chainRadius, cfg.chainTargets, victim)) {
			Vec3 to = next.position().add(0, next.getBbHeight() * 0.5, 0);
			PowerUtil.line(level, ParticleTypes.ELECTRIC_SPARK, from, to);
			PowerUtil.damage(next, PowerUtil.FOUDRE, player, cfg.chainDamage);
		}
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 2.0F, 1.2F);
	}

	/** The aimed hostile mob, else the aimed block (on the ground below when aiming at the sky). */
	static Vec3 aimPoint(ServerPlayer player, double range) {
		LivingEntity aimed = PowerUtil.aimedHostile(player, range);
		if (aimed != null) {
			return aimed.position();
		}
		HitResult hit = player.pick(range, 1.0F, false);
		Vec3 point = hit.getLocation();
		if (hit.getType() == HitResult.Type.MISS) {
			int ground = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(point.x), (int) Math.floor(point.z));
			point = new Vec3(point.x, Math.min(point.y, ground), point.z);
		}
		return point;
	}

	/** A visual-only lightning bolt at {@code pos} (sparks instead of a bolt above copper / lightning rods). */
	static void bolt(ServerLevel level, Vec3 pos) {
		BlockPos strike = BlockPos.containing(pos.x, pos.y - 1.0E-6, pos.z);
		if (level.isLoaded(strike) && safeStrike(level.getBlockState(strike))) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.setVisualOnly(true);
				bolt.snapTo(pos.x, pos.y, pos.z);
				if (level.addFreshEntity(bolt)) {
					return;
				}
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1, pos.z, 30, 0.2, 1.2, 0.2, 0.3);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.5F, 1.0F);
	}

	/** A vanilla strike changes copper (de-oxidation) and powers lightning rods, even a visual one: never there. */
	static boolean safeStrike(BlockState state) {
		Block block = state.getBlock();
		return !(block instanceof WeatheringCopper) && !(block instanceof LightningRodBlock)
				&& HoneycombItem.WAX_OFF_BY_BLOCK.get().get(block) == null;
	}
}
