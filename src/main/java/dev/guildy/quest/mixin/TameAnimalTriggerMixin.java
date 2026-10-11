package dev.guildy.quest.mixin;

import dev.guildy.core.Safe;
import dev.guildy.quest.QuestHooks;
import net.minecraft.advancements.triggers.TameAnimalTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TAME quests: called by {@code TamableAnimal.tame} (wolves, cats, parrots...) and {@code AbstractHorse.tameWithName}
 * (horses, donkeys, llamas...), whether or not an advancement listens.
 */
@Mixin(TameAnimalTrigger.class)
public abstract class TameAnimalTriggerMixin {
	@Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/animal/Animal;)V", at = @At("HEAD"))
	private void guildy$questTame(ServerPlayer player, Animal animal, CallbackInfo ci) {
		try {
			QuestHooks.tamed(player, animal);
		} catch (Throwable t) {
			Safe.log("mixin.quest.tame", t);
		}
	}
}
