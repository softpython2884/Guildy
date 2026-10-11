package dev.guildy.quest.mixin;

import dev.guildy.core.Safe;
import dev.guildy.quest.QuestHooks;
import net.minecraft.advancements.triggers.TradeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TRADE quests: called by {@code AbstractVillager.notifyTrade} once per trade with a villager or a wandering trader (Guildy
 * merchants included), whether or not an advancement listens.
 */
@Mixin(TradeTrigger.class)
public abstract class TradeTriggerMixin {
	@Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/npc/villager/AbstractVillager;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"))
	private void guildy$questTrade(ServerPlayer player, AbstractVillager villager, ItemStack result, CallbackInfo ci) {
		try {
			QuestHooks.traded(player, result);
		} catch (Throwable t) {
			Safe.log("mixin.quest.trade", t);
		}
	}
}
