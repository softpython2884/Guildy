package dev.guildy.mixin;

import dev.guildy.core.Safe;
import dev.guildy.villager.VillagerManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Guild villagers and candidates never go through portals: a worker walking into a Nether portal by accident used to
 * end up in another dimension, out of reach of its guild (and of "Appeler ici").
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPortalMixin {
	@Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
	private void guildy$noPortalForGuildVillagers(boolean ignorePassenger, CallbackInfoReturnable<Boolean> cir) {
		try {
			if ((Object) this instanceof Villager villager && (villager.entityTags().contains(VillagerManager.TAG_VILLAGER)
					|| villager.entityTags().contains(VillagerManager.TAG_CANDIDATE))) {
				cir.setReturnValue(false);
			}
		} catch (Throwable t) {
			Safe.log("mixin.portal", t);
		}
	}
}
