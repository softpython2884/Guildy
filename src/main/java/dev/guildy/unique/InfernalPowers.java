package dev.guildy.unique;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Hache du Seigneur de guerre, "Faille infernale": the right click splits the ground; a magma rift runs straight ahead,
 * one block per tick, following the terrain (it stops at a wall or a drop), spitting fire, lava and smoke. Each monster it
 * passes under is hit once: guildy:brasier damage, set on fire and thrown up. Purely particles: no block is changed.
 */
final class InfernalPowers {
	static final int MAGMA = 0xFF5A14;
	static final int EMBER = 0xFFB03A;
	static final int ASH = 0x3A2A24;
	/** The rift climbs or goes down at most this many blocks per step. */
	private static final int STEP = 2;

	private InfernalPowers() {
	}

	/** Right click with the axe. @return true when the rift started (cooldown) */
	static boolean rift(ServerPlayer player) {
		UniqueConfig.InfernalRift cfg = PowerUtil.cfg().infernalRift;
		ServerLevel level = player.level();
		Vec3 dir = PowerUtil.horizontal(player.getLookAngle());
		Vec3 origin = player.position();
		BlockPos below = BlockPos.containing(origin.x, origin.y - 0.5, origin.z);
		BlockState ground = level.getBlockState(below);
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), origin.x, origin.y + 0.1, origin.z, 30, 0.6, 0.05, 0.6, 0.2);
		}
		UniqueFx.burst(level, ParticleTypes.LAVA, origin.add(dir.scale(0.8)).add(0, 0.2, 0), 6, 0.2, 0.0);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0F, 0.6F);
		Set<UUID> hit = new HashSet<>();
		double[] floor = {origin.y};
		int length = cfg.length;
		UniqueFx.play(level, length + 2, (lvl, age) -> {
			if (player.isRemoved() || age >= length) {
				return false;
			}
			Vec3 step = origin.add(dir.scale(1.2 + age));
			Double y = groundY(lvl, step.x, floor[0], step.z);
			if (y == null) {
				// A wall or a drop: the rift dies in smoke.
				UniqueFx.burst(lvl, ParticleTypes.LARGE_SMOKE, new Vec3(step.x, floor[0] + 0.4, step.z), 10, 0.3, 0.03);
				lvl.playSound(null, step.x, floor[0], step.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.0F);
				return false;
			}
			floor[0] = y;
			Vec3 at = new Vec3(step.x, y, step.z);
			erupt(lvl, at, age, age == length - 1);
			AABB area = new AABB(at.x - cfg.width, at.y - 1, at.z - cfg.width, at.x + cfg.width, at.y + 2.5, at.z + cfg.width);
			for (LivingEntity target : lvl.getEntitiesOfClass(LivingEntity.class, area, PowerUtil::isHostile)) {
				if (!hit.add(target.getUUID())) {
					continue;
				}
				if (cfg.fireSeconds > 0 && !target.fireImmune()) {
					target.igniteForSeconds(cfg.fireSeconds);
				}
				PowerUtil.damage(target, PowerUtil.BRASIER, player, cfg.damage);
				if (cfg.lift > 0) {
					PowerUtil.push(target, new Vec3(dir.x * 0.2, cfg.lift, dir.z * 0.2));
				}
				UniqueFx.burst(lvl, ParticleTypes.FLAME, target.position().add(0, target.getBbHeight() * 0.5, 0), 12, 0.3, 0.05);
			}
			return true;
		});
		return true;
	}

	/** One step of the rift: a fissure glowing on the ground, flames, lava, a geyser of fire every other step. */
	private static void erupt(ServerLevel level, Vec3 at, int age, boolean last) {
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, 8, 0.3, 0.05, 0.3, 0.1);
		}
		UniqueFx.burst(level, UniqueFx.fade(EMBER, MAGMA, 1.3F), at.add(0, 0.1, 0), 6, 0.35, 0.0);
		UniqueFx.burst(level, ParticleTypes.LAVA, at.add(0, 0.2, 0), 2, 0.2, 0.0);
		for (int i = 0; i < 5; i++) {
			UniqueFx.move(level, ParticleTypes.FLAME, at.add((i - 2) * 0.12, 0.1, ((i * 7) % 5 - 2) * 0.12), new Vec3(0, 0.12 + i * 0.04, 0));
		}
		UniqueFx.burst(level, ParticleTypes.LARGE_SMOKE, at.add(0, 0.6, 0), 2, 0.2, 0.02);
		UniqueFx.dot(level, UniqueFx.dust(ASH, 1.0F), at.add(0, 1.0, 0));
		if (age % 2 == 0) {
			UniqueFx.column(level, ParticleTypes.FLAME, at.add(0, 0.2, 0), 2.4, 7);
			UniqueFx.burst(level, ParticleTypes.DRIPPING_LAVA, at.add(0, 2.2, 0), 3, 0.2, 0.0);
		}
		if (age % 3 == 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 0.5F + age * 0.03F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.8F, 0.8F);
		}
		if (last) {
			UniqueFx.burst(level, ParticleTypes.EXPLOSION, at.add(0, 0.5, 0), 1, 0, 0);
			UniqueFx.burst(level, ParticleTypes.FLAME, at.add(0, 0.5, 0), 30, 0.6, 0.1);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.2F);
		}
	}

	/**
	 * The floor height near {@code y} at (x, z): a free block (no collision) above a block with collision, at most
	 * {@link #STEP} blocks up or down; null when there is none (wall, drop, unloaded).
	 */
	static @Nullable Double groundY(ServerLevel level, double x, double y, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int by = (int) Math.floor(y + 0.01);
		for (int dy : new int[] {0, 1, -1, 2, -2}) {
			if (Math.abs(dy) > STEP) {
				continue;
			}
			BlockPos feet = new BlockPos(bx, by + dy, bz);
			if (!level.isLoaded(feet)) {
				return null;
			}
			if (free(level, feet) && free(level, feet.above()) && !free(level, feet.below())) {
				return (double) feet.getY();
			}
		}
		return null;
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}
