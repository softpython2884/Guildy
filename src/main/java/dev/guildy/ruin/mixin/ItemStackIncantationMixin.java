package dev.guildy.ruin.mixin;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.Safe;
import dev.guildy.ruin.IncantationItems;
import dev.guildy.ruin.Incantations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Incantation scrolls: the vanilla use of the scroll (its consumable component gives the channel animation) completed. The
 * mod decides instead of the vanilla consumable logic: the ritual starts and one scroll is consumed only when the checks
 * pass (server side; the client never completes a use itself).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackIncantationMixin {
	@Inject(method = "finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
			at = @At("HEAD"), cancellable = true)
	private void guildy$readIncantation(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack self = (ItemStack) (Object) this;
		try {
			if (!level.isClientSide() && self.has(DataComponents.CUSTOM_DATA) && GuildyItems.is(self, IncantationItems.KIND)) {
				cir.setReturnValue(Incantations.finish(self, level, entity));
			}
		} catch (Throwable t) {
			Safe.log("mixin.ruin.incantation", t);
		}
	}
}
