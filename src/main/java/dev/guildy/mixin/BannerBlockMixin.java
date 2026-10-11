package dev.guildy.mixin;

import dev.guildy.claim.WorldProtection;
import dev.guildy.core.Safe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A heart banner never pops off when the block holding it disappears (enderman, falling sand, admin...).
 */
@Mixin({BannerBlock.class, WallBannerBlock.class})
public abstract class BannerBlockMixin {
	@Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
	private void guildy$keepHeart(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random, CallbackInfoReturnable<BlockState> cir) {
		try {
			if (level instanceof Level realLevel && !realLevel.isClientSide() && WorldProtection.isHeart(realLevel, pos)) {
				cir.setReturnValue(state);
			}
		} catch (Throwable t) {
			Safe.log("mixin.banner", t);
		}
	}
}
