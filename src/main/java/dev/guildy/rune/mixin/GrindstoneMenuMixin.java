package dev.guildy.rune.mixin;

import dev.guildy.core.Safe;
import dev.guildy.rune.RuneGrindstone;
import dev.guildy.rune.RuneGrinding;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grindstone result: a runed piece alone gives the same piece without its rune (enchantments kept); a vanilla merge of
 * two pieces never keeps a rune. The server's result is sent to the vanilla client like any slot change.
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin implements RuneGrindstone {
	@Unique
	private boolean guildy$runeRemoval;

	@Inject(method = "computeResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
			at = @At("HEAD"), cancellable = true)
	private void guildy$removeRune(ItemStack input, ItemStack additional, CallbackInfoReturnable<ItemStack> cir) {
		guildy$runeRemoval = false;
		ItemStack result = Safe.get("mixin.rune.grindstone", () -> RuneGrinding.runeRemoval(input, additional), null);
		if (result != null) {
			guildy$runeRemoval = true;
			cir.setReturnValue(result);
		}
	}

	@Inject(method = "computeResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
			at = @At("RETURN"), cancellable = true)
	private void guildy$noRuneInMerge(ItemStack input, ItemStack additional, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack result = cir.getReturnValue();
		if (result != null && !result.isEmpty()) {
			cir.setReturnValue(Safe.get("mixin.rune.grindstone", () -> RuneGrinding.withoutRune(result), result));
		}
	}

	@Override
	public boolean guildy$runeRemoval() {
		return guildy$runeRemoval;
	}
}
