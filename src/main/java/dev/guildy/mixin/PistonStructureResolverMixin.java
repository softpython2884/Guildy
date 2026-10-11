package dev.guildy.mixin;

import dev.guildy.claim.WorldProtection;
import dev.guildy.core.Safe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Pistons cannot push, pull or break blocks across a territory border (a moved block or its destination in a claim
 * owned by another guild than the piston's chunk), nor move a heart or its support (config protection.pistons).
 */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
	@Shadow
	@Final
	private Level level;
	@Shadow
	@Final
	private BlockPos pistonPos;
	@Shadow
	@Final
	private boolean extending;
	@Shadow
	@Final
	private BlockPos startPos;
	@Shadow
	@Final
	private Direction pushDirection;
	@Shadow
	@Final
	private List<BlockPos> toPush;
	@Shadow
	@Final
	private List<BlockPos> toDestroy;

	@Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
	private void guildy$claimBorders(CallbackInfoReturnable<Boolean> cir) {
		try {
			if (Boolean.TRUE.equals(cir.getReturnValue()) && !this.level.isClientSide()
					&& !WorldProtection.pistonAllowed(this.level, this.pistonPos, this.pushDirection,
					this.extending ? this.startPos : null, this.toPush, this.toDestroy)) {
				cir.setReturnValue(false);
			}
		} catch (Throwable t) {
			Safe.log("mixin.piston", t);
		}
	}
}
