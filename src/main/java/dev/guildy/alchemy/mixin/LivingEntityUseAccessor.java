package dev.guildy.alchemy.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Ticks left of the item in use: a clairvoyance elixir shortens the incantation of a scroll or a grimoire right after it
 * starts (the server completes the use earlier and tells the client, see {@code ServerPlayer.completeUsingItem}).
 */
@Mixin(LivingEntity.class)
public interface LivingEntityUseAccessor {
	@Accessor("useItemRemaining")
	void guildy$setUseItemRemaining(int ticks);
}
