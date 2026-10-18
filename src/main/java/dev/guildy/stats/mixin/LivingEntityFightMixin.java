package dev.guildy.stats.mixin;

import dev.guildy.core.Safe;
import dev.guildy.stats.FightHealing;
import dev.guildy.stats.FightTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fight statistics: the health + absorption really lost around each {@code actuallyHurt} of {@code hurtServer} (armour,
 * enchantments, resistance and overkill included, before any death cancellation puts health back), heals done inside a
 * healer context (see {@link FightHealing}) and the absorption / regeneration effects received.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFightMixin {
	@Unique
	private static final String ACTUALLY_HURT = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;"
			+ "Lnet/minecraft/world/damagesource/DamageSource;F)V";
	@Unique
	private static final String ADD_EFFECT = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z";

	@Unique
	private float guildy$fightHealthBefore;
	@Unique
	private float guildy$fightAbsorptionBefore;

	@Inject(method = "hurtServer", at = @At(value = "INVOKE", target = ACTUALLY_HURT))
	private void guildy$beforeActuallyHurt(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		guildy$fightHealthBefore = self.getHealth() + self.getAbsorptionAmount();
	}

	@Inject(method = "hurtServer", at = @At(value = "INVOKE", target = ACTUALLY_HURT, shift = At.Shift.AFTER))
	private void guildy$afterActuallyHurt(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		float lost = guildy$fightHealthBefore - (self.getHealth() + self.getAbsorptionAmount());
		if (lost > 0) {
			FightTracker.hurt(self, source, lost);
		}
	}

	@Inject(method = "heal", at = @At("HEAD"))
	private void guildy$heal(float amount, CallbackInfo ci) {
		if (FightHealing.healer() != null) {
			try {
				LivingEntity self = (LivingEntity) (Object) this;
				FightTracker.healed(self, self.getHealth(), self.getMaxHealth(), amount);
			} catch (Throwable t) {
				Safe.log("mixin.fight.heal", t);
			}
		}
	}

	@Inject(method = ADD_EFFECT, at = @At("HEAD"))
	private void guildy$beforeEffect(MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
		guildy$fightAbsorptionBefore = ((LivingEntity) (Object) this).getAbsorptionAmount();
	}

	@Inject(method = ADD_EFFECT, at = @At("RETURN"))
	private void guildy$afterEffect(MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
		if (FightTracker.tracking()) {
			FightTracker.effectAdded((LivingEntity) (Object) this, effect, source, guildy$fightAbsorptionBefore, cir.getReturnValueZ());
		}
	}
}
