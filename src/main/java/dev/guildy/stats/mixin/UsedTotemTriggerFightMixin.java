package dev.guildy.stats.mixin;

import dev.guildy.core.Safe;
import dev.guildy.stats.FightTracker;
import net.minecraft.advancements.triggers.UsedTotemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fight statistics: a totem of undying saved a player. Called by {@code LivingEntity.checkTotemDeathProtection} once per
 * totem consumed (main hand or off hand, only when the death was not already cancelled by an {@code ALLOW_DEATH} handler),
 * whether or not an advancement listens.
 */
@Mixin(UsedTotemTrigger.class)
public abstract class UsedTotemTriggerFightMixin {
	@Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
	private void guildy$fightTotem(ServerPlayer player, ItemStack totem, CallbackInfo ci) {
		if (FightTracker.tracking()) {
			try {
				FightTracker.totemUsed(player);
			} catch (Throwable t) {
				Safe.log("mixin.fight.totem", t);
			}
		}
	}
}
