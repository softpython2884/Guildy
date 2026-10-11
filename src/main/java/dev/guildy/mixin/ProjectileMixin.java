package dev.guildy.mixin;

import dev.guildy.claim.WorldProtection;
import dev.guildy.core.Safe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Projectiles shot by players obey the territory BUILD rule when they affect blocks (TNT ignition, decorated pots,
 * pointed dripstone, campfires, chorus flowers...).
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {
	@Inject(method = "mayInteract", at = @At("HEAD"), cancellable = true)
	private void guildy$claimRules(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		try {
			if (!WorldProtection.projectileMayInteract(((Projectile) (Object) this).getOwner(), level, pos)) {
				cir.setReturnValue(false);
			}
		} catch (Throwable t) {
			Safe.log("mixin.projectile", t);
		}
	}
}
