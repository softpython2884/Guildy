package dev.guildy.scroll.mixin;

import dev.guildy.core.Safe;
import dev.guildy.scroll.Grimoires;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grimoire fusion in containers, like a bundle (server side, {@code AbstractContainerMenu.tryItemClickBehaviourOverride}):
 * a scroll right-clicked onto a grimoire, or a grimoire right-clicked onto a scroll, becomes one of its pages. The vanilla
 * click is skipped; the menu's usual sync then corrects what the vanilla client predicted.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackGrimoireMixin {
	@Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
	private void guildy$scrollOntoGrimoire(ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem,
			CallbackInfoReturnable<Boolean> cir) {
		ItemStack self = (ItemStack) (Object) this;
		try {
			if (clickAction == ClickAction.SECONDARY && !player.level().isClientSide() && self.has(DataComponents.CUSTOM_DATA)
					&& other.has(DataComponents.CUSTOM_DATA) && Grimoires.scrollOntoGrimoire(self, other, slot, clickAction, player, carriedItem)) {
				cir.setReturnValue(true);
			}
		} catch (Throwable t) {
			Safe.log("mixin.grimoire.fuse", t);
		}
	}

	@Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true)
	private void guildy$grimoireOntoScroll(Slot slot, ClickAction clickAction, Player player, CallbackInfoReturnable<Boolean> cir) {
		ItemStack self = (ItemStack) (Object) this;
		try {
			if (clickAction == ClickAction.SECONDARY && !player.level().isClientSide() && self.has(DataComponents.CUSTOM_DATA)
					&& slot.getItem().has(DataComponents.CUSTOM_DATA) && Grimoires.grimoireOntoScroll(self, slot, clickAction, player)) {
				cir.setReturnValue(true);
			}
		} catch (Throwable t) {
			Safe.log("mixin.grimoire.fuse", t);
		}
	}
}
