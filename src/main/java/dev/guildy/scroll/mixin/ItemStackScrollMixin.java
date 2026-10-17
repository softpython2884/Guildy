package dev.guildy.scroll.mixin;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.Safe;
import dev.guildy.scroll.ScrollCasting;
import dev.guildy.scroll.ScrollItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spell scrolls: the vanilla use of a scroll (its consumable component gives the channel animation) completed. The mod
 * decides instead of the vanilla consumable logic: the spell is cast, the scroll consumed and its cooldown started only on
 * success (server side; the client never completes a use itself).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackScrollMixin {
	@Inject(method = "finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
			at = @At("HEAD"), cancellable = true)
	private void guildy$castScroll(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack self = (ItemStack) (Object) this;
		try {
			if (!level.isClientSide() && self.has(DataComponents.CUSTOM_DATA) && GuildyItems.is(self, ScrollItems.KIND)) {
				cir.setReturnValue(ScrollCasting.finish(self, level, entity));
			}
		} catch (Throwable t) {
			Safe.log("mixin.scroll.finish", t);
		}
	}
}
