package dev.guildy.quest.mixin;

import dev.guildy.core.Safe;
import dev.guildy.quest.QuestHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SMELT quests: results taken out of a furnace, smoker or blast furnace by a player. {@code removeCount} holds the items
 * taken since the previous call (shift-click calls this twice, the second time with 0). Hoppers never count.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
	@Shadow
	@Final
	private Player player;

	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
	private void guildy$questSmelt(ItemStack carried, CallbackInfo ci) {
		try {
			if (this.removeCount > 0) {
				QuestHooks.smelted(this.player, carried, this.removeCount);
			}
		} catch (Throwable t) {
			Safe.log("mixin.quest.smelt", t);
		}
	}
}
