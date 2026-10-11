package dev.guildy.quest.mixin;

import dev.guildy.core.Safe;
import dev.guildy.quest.QuestHooks;
import net.minecraft.advancements.triggers.FishingRodHookedTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * FISH quests: called by {@code FishingHook.retrieve} for every reeled-in catch (with its loot) and for hooked entities
 * (empty list, ignored), whether or not an advancement listens.
 */
@Mixin(FishingRodHookedTrigger.class)
public abstract class FishingRodHookedTriggerMixin {
	@Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/projectile/FishingHook;Ljava/util/Collection;)V",
			at = @At("HEAD"))
	private void guildy$questFish(ServerPlayer player, ItemStack rod, FishingHook hook, Collection<ItemStack> items, CallbackInfo ci) {
		try {
			QuestHooks.fished(player, items);
		} catch (Throwable t) {
			Safe.log("mixin.quest.fish", t);
		}
	}
}
