package dev.guildy.unique.mixin;

import dev.guildy.core.Safe;
import dev.guildy.unique.UniqueRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * A damageable unique weapon broke (every hurtAndBreak path ends in applyDamage): its copy is lost, the weapon may drop
 * again. Called just before the stack shrinks, while it still carries its serial.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackBreakMixin {
	@Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
	private void guildy$uniqueBroken(int newDamage, @Nullable ServerPlayer player, Consumer<ItemStack> onBreak, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		try {
			if (self.has(DataComponents.CUSTOM_DATA)) {
				UniqueRegistry.onBroken(self);
			}
		} catch (Throwable t) {
			Safe.log("mixin.unique.break", t);
		}
	}
}
