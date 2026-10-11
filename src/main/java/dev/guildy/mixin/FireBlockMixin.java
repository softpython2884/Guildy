package dev.guildy.mixin;

import dev.guildy.claim.WorldProtection;
import dev.guildy.core.Safe;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fire never burns blocks in claimed chunks (config protection.fire) nor the hearts, and does not spread into them.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	/** Burning a neighbour (destroying it or replacing it by fire). */
	@Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
	private void guildy$noBurnInClaims(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		try {
			if (WorldProtection.fireProtected(level, pos)) {
				ci.cancel();
			}
		} catch (Throwable t) {
			Safe.log("mixin.fire", t);
		}
	}

	/** Spread target selection: an air block in a claim never catches fire from a spreading fire. */
	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
	private void guildy$noSpreadIntoClaims(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		try {
			if (level instanceof Level realLevel && WorldProtection.fireProtected(realLevel, pos)) {
				cir.setReturnValue(0);
			}
		} catch (Throwable t) {
			Safe.log("mixin.fire", t);
		}
	}
}
