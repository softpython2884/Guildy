package dev.guildy.alchemy.mixin;

import dev.guildy.alchemy.ElixirDrinks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * End of a drink (or a meal): {@code Consumable.onConsume} applies the potion's vanilla effects and uses the item up; at
 * its start the stack is still whole, so an elixir's magic boost starts here (server side only).
 */
@Mixin(Consumable.class)
abstract class ConsumableElixirMixin {
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void guildy$elixirDrunk(Level level, LivingEntity user, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (!level.isClientSide() && user instanceof ServerPlayer player) {
			ElixirDrinks.drunk(player, stack);
		}
	}
}
