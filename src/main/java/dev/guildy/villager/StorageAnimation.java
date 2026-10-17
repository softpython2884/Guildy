package dev.guildy.villager;

import dev.guildy.core.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Visible tidying: chest lids opened then closed with the vanilla block event (both halves of a double chest; closing
 * sends the real number of viewers so a player's open chest stays open), barrels opened through their OPEN state (only
 * one nobody uses, closed back unless someone opened it meanwhile), and the vanilla sounds. Shulker boxes only play
 * their sound (their open state also moves entities).
 */
final class StorageAnimation {
	private static final int EVENT_SET_OPEN_COUNT = 1;

	private StorageAnimation() {
	}

	/** Opens the containers now and closes them after {@code ticks}. */
	static void openThenClose(ServerLevel level, List<BlockPos> containers, int ticks) {
		List<BlockPos> chests = new ArrayList<>();
		List<BlockPos> barrels = new ArrayList<>();
		SoundEvent openSound = null;
		BlockPos soundAt = null;
		for (BlockPos pos : containers) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			for (BlockPos half : Storage.halves(level, pos)) {
				BlockState state = level.getBlockState(half);
				Block block = state.getBlock();
				if (block instanceof ChestBlock) {
					level.blockEvent(half, block, EVENT_SET_OPEN_COUNT, Math.max(1, ChestBlockEntity.getOpenCount(level, half)));
					chests.add(half.immutable());
					openSound = openSound == null ? SoundEvents.CHEST_OPEN : openSound;
				} else if (block instanceof BarrelBlock && state.hasProperty(BarrelBlock.OPEN) && !state.getValue(BarrelBlock.OPEN)) {
					level.setBlock(half, state.setValue(BarrelBlock.OPEN, true), Block.UPDATE_ALL);
					barrels.add(half.immutable());
					openSound = openSound == null ? SoundEvents.BARREL_OPEN : openSound;
				} else if (block instanceof ShulkerBoxBlock) {
					openSound = openSound == null ? SoundEvents.SHULKER_BOX_OPEN : openSound;
				}
				soundAt = soundAt == null ? half.immutable() : soundAt;
			}
		}
		if (openSound != null) {
			level.playSound(null, soundAt, openSound, SoundSource.BLOCKS, 0.5f, level.getRandom().nextFloat() * 0.1f + 0.9f);
		}
		BlockPos closeSoundAt = soundAt;
		Scheduler.later("villager.storage.lids", Math.max(1, ticks), () -> close(level, chests, barrels, closeSoundAt));
	}

	private static void close(ServerLevel level, List<BlockPos> chests, List<BlockPos> barrels, BlockPos soundAt) {
		SoundEvent closeSound = null;
		for (BlockPos pos : chests) {
			if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof ChestBlock chest) {
				level.blockEvent(pos, chest, EVENT_SET_OPEN_COUNT, ChestBlockEntity.getOpenCount(level, pos));
				closeSound = SoundEvents.CHEST_CLOSE;
			}
		}
		for (BlockPos pos : barrels) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			boolean used = level.getBlockEntity(pos) instanceof Container container && !container.getEntitiesWithContainerOpen().isEmpty();
			if (state.getBlock() instanceof BarrelBlock && state.getValue(BarrelBlock.OPEN) && !used) {
				level.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), Block.UPDATE_ALL);
				closeSound = closeSound == null ? SoundEvents.BARREL_CLOSE : closeSound;
			}
		}
		if (closeSound != null && soundAt != null) {
			level.playSound(null, soundAt, closeSound, SoundSource.BLOCKS, 0.5f, level.getRandom().nextFloat() * 0.1f + 0.9f);
		}
	}
}
