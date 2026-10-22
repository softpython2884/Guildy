package dev.guildy.ruin.mixin;

import dev.guildy.ruin.Encounters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The dread form of the ruin guardians: the damage left after armour, enchantments and resistance
 * ({@code getDamageAfterMagicAbsorb} in {@code actuallyHurt}) is divided by the vitality of a guardian whose effective health
 * is beyond the vanilla cap, or multiplied on a player hit by a mob of a dread / Avatar fight; a heal of such a guardian is
 * divided the same way. Every other entity is untouched ({@link Encounters#adjustDamage}).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDreadMixin {
	@Unique
	private static final String MAGIC_ABSORB = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterMagicAbsorb("
			+ "Lnet/minecraft/world/damagesource/DamageSource;F)F";

	@Unique
	private DamageSource guildy$dreadSource;

	@Inject(method = "actuallyHurt", at = @At("HEAD"))
	private void guildy$dreadSource(ServerLevel level, DamageSource source, float damage, CallbackInfo ci) {
		guildy$dreadSource = source;
	}

	@ModifyVariable(method = "actuallyHurt", at = @At(value = "INVOKE_ASSIGN", target = MAGIC_ABSORB), argsOnly = true)
	private float guildy$dreadDamage(float damage) {
		DamageSource source = guildy$dreadSource;
		guildy$dreadSource = null;
		return source == null ? damage : Encounters.adjustDamage((LivingEntity) (Object) this, source, damage);
	}

	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float guildy$dreadHeal(float amount) {
		return Encounters.adjustHeal((LivingEntity) (Object) this, amount);
	}
}
