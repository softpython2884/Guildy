package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Small world helpers: safe standing spots and visual / sound effects.
 */
final class WorldUtil {
	private static final int[] DY = {0, 1, -1, 2, -2};

	private WorldUtil() {
	}

	/** True if a villager can stand with its feet at {@code feet} (sturdy floor, 2 free blocks, no fluid/fire). */
	static boolean isSafeStand(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet)) {
			return false;
		}
		BlockPos below = feet.below();
		BlockState floor = level.getBlockState(below);
		if (!floor.isFaceSturdy(level, below, Direction.UP) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(BlockTags.CAMPFIRES)) {
			return false;
		}
		BlockPos head = feet.above();
		BlockState feetState = level.getBlockState(feet);
		BlockState headState = level.getBlockState(head);
		return feetState.getCollisionShape(level, feet).isEmpty() && headState.getCollisionShape(level, head).isEmpty()
				&& feetState.getFluidState().isEmpty() && headState.getFluidState().isEmpty()
				&& !feetState.is(BlockTags.FIRE) && !feetState.is(Blocks.SWEET_BERRY_BUSH) && !feetState.is(Blocks.POWDER_SNOW);
	}

	/**
	 * Closest safe standing spot around {@code center} (rings of growing radius, small height offsets).
	 *
	 * @param skipCenter ignore the centre column itself (e.g. the sign or chest block)
	 */
	static @Nullable BlockPos findSafeSpot(ServerLevel level, BlockPos center, int radius, boolean skipCenter, Predicate<BlockPos> filter) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int r = skipCenter ? 1 : 0; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy : DY) {
						pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
						if (isSafeStand(level, pos) && filter.test(pos)) {
							return pos.immutable();
						}
					}
				}
			}
		}
		return null;
	}

	static void playSound(ServerLevel level, Entity entity, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
	}

	/** Cloud of particles + sound when a villager arrives or leaves. */
	static void poof(ServerLevel level, Entity entity) {
		level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.8, entity.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		playSound(level, entity, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.6f, 1.2f);
	}

	static void happy(ServerLevel level, Entity entity) {
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY() + 1.2, entity.getZ(), 10, 0.4, 0.5, 0.4, 0.0);
	}

	static void angry(ServerLevel level, Entity entity) {
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, entity.getX(), entity.getY() + 1.2, entity.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
	}

	/** Teleport effect (fallback when a path fails). */
	static void portal(ServerLevel level, double x, double y, double z) {
		level.sendParticles(ParticleTypes.PORTAL, x, y + 0.8, z, 24, 0.3, 0.6, 0.3, 0.2);
	}

	/** Block-break particles + sound without breaking the block (simulated mining). */
	static void breakEffect(ServerLevel level, BlockPos pos, BlockState state) {
		level.levelEvent(null, LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(state));
	}

	static void swing(Mob mob) {
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
	}
}
