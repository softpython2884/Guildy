package dev.guildy.stats.mixin;

import dev.guildy.stats.FightHealing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fight statistics: an instant health effect from a splash potion or a lingering cloud heals on behalf of its owner (the
 * player who threw it); drinking one is the player's own heal (owner = target, not counted).
 */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class HealOrHarmMobEffectFightMixin {
	@Inject(method = "applyInstantaneousEffect", at = @At("HEAD"))
	private void guildy$healerFromOwner(ServerLevel level, @Nullable Entity source, @Nullable Entity owner, LivingEntity mob, int amplification,
			double scale, CallbackInfo ci) {
		FightHealing.pushEffect(source, owner);
	}

	@Inject(method = "applyInstantaneousEffect", at = @At("RETURN"))
	private void guildy$healerDone(ServerLevel level, @Nullable Entity source, @Nullable Entity owner, LivingEntity mob, int amplification,
			double scale, CallbackInfo ci) {
		FightHealing.pop();
	}
}
