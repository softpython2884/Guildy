package dev.guildy.ruin.mixin;

import dev.guildy.core.Safe;
import dev.guildy.ruin.Sanctuaries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Explosions never destroy the beacon sanctuaries of the ruins (a beacon and its pyramid, see {@link Sanctuaries}); only
 * the block list is filtered, entities are still hurt.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionSanctuaryMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"))
	private void guildy$protectSanctuaries(CallbackInfoReturnable<List<BlockPos>> cir) {
		try {
			List<BlockPos> positions = cir.getReturnValue();
			if (positions != null) {
				Sanctuaries.filterExplosion(this.level, positions);
			}
		} catch (Throwable t) {
			Safe.log("mixin.ruin.explosion", t);
		}
	}
}
