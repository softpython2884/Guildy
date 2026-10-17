package dev.guildy.mixin;

import dev.guildy.core.Safe;
import dev.guildy.villager.FieldMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Farmland turning back to dirt (trampled by a fall, dried out without water nor crop, a block placed on it): the
 * field zones that allow it remember the spot so their farmers till and sow it again (see {@link FieldMemory}).
 */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
	@Inject(method = "turnToBaseBlock", at = @At("HEAD"))
	private void guildy$rememberRevertedFarmland(@Nullable Entity sourceEntity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		try {
			FieldMemory.onFarmlandReverted(level, pos);
		} catch (Throwable t) {
			Safe.log("mixin.farmland", t);
		}
	}
}
