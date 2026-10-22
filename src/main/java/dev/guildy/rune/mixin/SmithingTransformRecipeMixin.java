package dev.guildy.rune.mixin;

import dev.guildy.core.Safe;
import dev.guildy.rune.RuneItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A runed piece upgraded at the smithing table (diamond → netherite) copies its attribute modifiers, which still hold the
 * diamond piece's own values: they become the netherite piece's own values plus the rune's bonus.
 */
@Mixin(SmithingTransformRecipe.class)
public abstract class SmithingTransformRecipeMixin {
	@Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SmithingRecipeInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
	private void guildy$rebaseRune(SmithingRecipeInput input, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack result = cir.getReturnValue();
		if (result != null && !result.isEmpty()) {
			Safe.run("mixin.rune.smithing", () -> RuneItems.rebase(result, input.base()));
		}
	}
}
