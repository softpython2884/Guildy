package dev.guildy.rune.mixin;

import dev.guildy.core.Safe;
import dev.guildy.rune.RuneGrindstone;
import dev.guildy.rune.RuneGrinding;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The grindstone's result slot (anonymous class GrindstoneMenu$4 in 26.3): no experience for a rune removal (the
 * enchantments it would pay for stay on the piece), and a word for the rune that fades when the piece is taken.
 */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneResultSlotMixin {
	@Shadow
	@Final
	GrindstoneMenu this$0;

	@Inject(method = "getExperienceAmount(Lnet/minecraft/world/level/Level;)I", at = @At("HEAD"), cancellable = true)
	private void guildy$noExperienceForARune(Level level, CallbackInfoReturnable<Integer> cir) {
		if (((RuneGrindstone) this$0).guildy$runeRemoval()) {
			cir.setReturnValue(0);
		}
	}

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
	private void guildy$runeGround(Player player, ItemStack carried, CallbackInfo ci) {
		GrindstoneMenu menu = this$0;
		if (((RuneGrindstone) menu).guildy$runeRemoval()) {
			Safe.run("mixin.rune.grindstone.take", () -> RuneGrinding.taken(player, menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem(),
					menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).getItem()));
		}
	}
}
