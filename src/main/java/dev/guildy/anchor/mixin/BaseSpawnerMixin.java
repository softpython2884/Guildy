package dev.guildy.anchor.mixin;

import dev.guildy.anchor.AnchorPoints;
import dev.guildy.anchor.AnchorRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Monster spawners run when an anchor is within their player range (16 blocks by default), like a player. */
@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin {
	@Shadow
	private int requiredPlayerRange;

	@Inject(method = "isNearPlayer", at = @At("RETURN"), cancellable = true)
	private void guildy$anchorNear(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || level.isClientSide()) {
			return;
		}
		double[] points = AnchorPoints.spawnPoints(level);
		if (points == null) {
			return;
		}
		if (this.requiredPlayerRange < 0
				|| AnchorRules.anyWithin(points, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.requiredPlayerRange)) {
			cir.setReturnValue(true);
		}
	}
}
