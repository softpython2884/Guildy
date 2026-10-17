package dev.guildy.ruin.mixin;

import dev.guildy.core.Safe;
import dev.guildy.ruin.Sanctuaries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Pistons cannot push, pull or break the blocks of a beacon sanctuary of the ruins (see {@link Sanctuaries}). */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverSanctuaryMixin {
	@Shadow
	@Final
	private Level level;
	@Shadow
	@Final
	private List<BlockPos> toPush;
	@Shadow
	@Final
	private List<BlockPos> toDestroy;

	@Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
	private void guildy$protectSanctuaries(CallbackInfoReturnable<Boolean> cir) {
		try {
			if (Boolean.TRUE.equals(cir.getReturnValue()) && !this.level.isClientSide()
					&& !Sanctuaries.pistonAllowed(this.level, this.toPush, this.toDestroy)) {
				cir.setReturnValue(false);
			}
		} catch (Throwable t) {
			Safe.log("mixin.ruin.piston", t);
		}
	}
}
