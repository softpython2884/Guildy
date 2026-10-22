package dev.guildy.ruin.mixin;

import dev.guildy.ruin.Encounters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The players override {@code actuallyHurt} without calling the living entity's: the damage a player takes from a mob of a
 * dread / Avatar fight, after armour, enchantments and resistance, gets the bonus of the fight here
 * ({@link Encounters#adjustDamage}).
 */
@Mixin(Player.class)
public abstract class PlayerDreadMixin {
	@Unique
	private static final String MAGIC_ABSORB = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb("
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
		return source == null ? damage : Encounters.adjustDamage((Player) (Object) this, source, damage);
	}
}
