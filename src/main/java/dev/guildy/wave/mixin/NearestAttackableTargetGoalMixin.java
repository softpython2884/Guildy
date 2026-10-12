package dev.guildy.wave.mixin;

import dev.guildy.core.Safe;
import dev.guildy.wave.HordeTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * Horde mobs (tag {@code guildy_wave}) skip the villagers when they look for the nearest target of a non-player type
 * (see {@link HordeTargets}): the entity filter of the search is narrowed, so another target of the same type can
 * still be found. Extends {@link TargetGoal} only to reach its {@code mob} field.
 */
@Mixin(NearestAttackableTargetGoal.class)
public abstract class NearestAttackableTargetGoalMixin extends TargetGoal {
	private NearestAttackableTargetGoalMixin(Mob mob, boolean mustSee) {
		super(mob, mustSee);
	}

	@ModifyArg(method = "findTarget",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
			index = 2)
	private Predicate<? super Entity> guildy$spareVillagers(Predicate<? super Entity> selector) {
		Mob self = this.mob;
		try {
			if (!HordeTargets.restricted(self)) {
				return selector;
			}
		} catch (Throwable t) {
			Safe.log("mixin.wave.targets", t);
			return selector;
		}
		return entity -> selector.test(entity) && !HordeTargets.spared(self, entity);
	}
}
