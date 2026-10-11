package dev.guildy.mixin;

import dev.guildy.claim.WorldProtection;
import dev.guildy.core.Safe;
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
 * Explosions never destroy blocks in claimed chunks (config protection.explosions) nor the hearts; entities are still
 * hurt (only the block list is filtered, which also prevents fire creation there).
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"))
	private void guildy$protectClaims(CallbackInfoReturnable<List<BlockPos>> cir) {
		try {
			List<BlockPos> positions = cir.getReturnValue();
			if (positions != null) {
				WorldProtection.filterExplosion(this.level, positions);
			}
		} catch (Throwable t) {
			Safe.log("mixin.explosion", t);
		}
	}
}
