package dev.guildy.quest.mixin;

import dev.guildy.core.Safe;
import dev.guildy.quest.QuestHooks;
import net.minecraft.advancements.triggers.EnchantedItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ENCHANT quests: called only by {@code EnchantmentMenu.clickMenuButton} (enchanting table, never the anvil) once per
 * enchanted item, whether or not an advancement listens.
 */
@Mixin(EnchantedItemTrigger.class)
public abstract class EnchantedItemTriggerMixin {
	@Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"))
	private void guildy$questEnchant(ServerPlayer player, ItemStack item, int levels, CallbackInfo ci) {
		try {
			QuestHooks.enchanted(player, item);
		} catch (Throwable t) {
			Safe.log("mixin.quest.enchant", t);
		}
	}
}
