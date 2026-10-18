package dev.guildy.ruin.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The target goals of a mob (the goal selector itself is public): the felines of the ruins hunt players instead of chickens,
 * rabbits and turtles.
 */
@Mixin(Mob.class)
public interface MobTargetSelectorAccessor {
	@Accessor("targetSelector")
	GoalSelector guildy$targetSelector();
}
