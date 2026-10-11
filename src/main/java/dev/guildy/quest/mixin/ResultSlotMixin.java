package dev.guildy.quest.mixin;

import dev.guildy.core.Safe;
import dev.guildy.quest.QuestHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CRAFT quests: crafting results taken out by a player (crafting table or inventory grid; click, shift-click, number key).
 * {@code removeCount} holds the items crafted since the previous call (shift-click calls this twice, the second time with 0).
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
	@Shadow
	@Final
	private Player player;

	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
	private void guildy$questCraft(ItemStack carried, CallbackInfo ci) {
		try {
			if (this.removeCount > 0) {
				QuestHooks.crafted(this.player, carried, this.removeCount);
			}
		} catch (Throwable t) {
			Safe.log("mixin.quest.craft", t);
		}
	}
}
