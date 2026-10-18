package dev.guildy.stats.mixin;

import dev.guildy.stats.FightHealing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fight statistics: a regeneration tick heals on behalf of the player who gave that regeneration (Bénédiction, potion). */
@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RegenerationMobEffectFightMixin {
	@Inject(method = "applyEffectTick", at = @At("HEAD"))
	private void guildy$healerFromGiver(ServerLevel level, LivingEntity mob, int amplification, CallbackInfoReturnable<Boolean> cir) {
		FightHealing.pushRegeneration(mob);
	}

	@Inject(method = "applyEffectTick", at = @At("RETURN"))
	private void guildy$healerDone(ServerLevel level, LivingEntity mob, int amplification, CallbackInfoReturnable<Boolean> cir) {
		FightHealing.pop();
	}
}
