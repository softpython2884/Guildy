package dev.guildy.mixin;

import dev.guildy.claim.WorldProtection;
import dev.guildy.core.Safe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fluids do not flow into a claimed chunk from a chunk with another owner, wilderness included
 * (config protection.fluids).
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
	@Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
	private void guildy$noFlowIntoClaims(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target,
			CallbackInfo ci) {
		try {
			if (level instanceof Level realLevel && !realLevel.isClientSide() && !WorldProtection.fluidAllowed(realLevel, pos, direction)) {
				ci.cancel();
			}
		} catch (Throwable t) {
			Safe.log("mixin.fluid", t);
		}
	}
}
