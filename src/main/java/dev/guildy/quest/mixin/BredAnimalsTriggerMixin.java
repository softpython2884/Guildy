package dev.guildy.quest.mixin;

import dev.guildy.core.Safe;
import dev.guildy.quest.QuestHooks;
import net.minecraft.advancements.triggers.BredAnimalsTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BREED quests: called when two animals fed by a player have a baby ({@code Animal.finalizeSpawnChildFromBreeding}, foxes,
 * turtle eggs), whether or not an advancement listens.
 */
@Mixin(BredAnimalsTrigger.class)
public abstract class BredAnimalsTriggerMixin {
	@Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/animal/Animal;Lnet/minecraft/world/entity/animal/Animal;Lnet/minecraft/world/entity/AgeableMob;)V",
			at = @At("HEAD"))
	private void guildy$questBreed(ServerPlayer player, Animal parent, Animal partner, @Nullable AgeableMob child, CallbackInfo ci) {
		try {
			QuestHooks.bred(player, parent, child);
		} catch (Throwable t) {
			Safe.log("mixin.quest.breed", t);
		}
	}
}
