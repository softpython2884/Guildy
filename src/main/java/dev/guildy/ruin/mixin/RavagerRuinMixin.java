package dev.guildy.ruin.mixin;

import dev.guildy.ruin.RoomGuards;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A ravager of the ruins (the celestial jaguar, an escort mob or a guard) never breaks the leaves of the ruin it charges
 * through (vanilla ravagers destroy the leaves they bump into when mobGriefing is on); other ravagers are untouched.
 */
@Mixin(Ravager.class)
public abstract class RavagerRuinMixin {
	@Redirect(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
	private boolean guildy$keepRuinLeaves(ServerLevel level, BlockPos pos, boolean dropResources, Entity breaker) {
		if (RoomGuards.isRuinMob(breaker)) {
			return false;
		}
		return level.destroyBlock(pos, dropResources, breaker);
	}
}
