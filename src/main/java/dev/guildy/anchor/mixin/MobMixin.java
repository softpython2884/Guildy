package dev.guildy.anchor.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import dev.guildy.anchor.AnchorPoints;
import dev.guildy.anchor.AnchorRules;
import dev.guildy.core.Safe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Despawning around anchors: a mob takes its distance from the nearest player or anchor, so mobs near an anchor stay
 * (and the far ones go) as near a player; vanilla keeps every mob when its level has no player, which would fill the
 * mob caps of an anchor for good. Beyond 128 blocks of every anchor nothing changes.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
	@WrapOperation(method = "checkDespawn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getNearestPlayer(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;"))
	private @Nullable Player guildy$nearestPlayerOrAnchor(Level level, Entity self, double range, Operation<Player> original,
			@Share("guildyAnchorDistance") LocalDoubleRef anchorDistance) {
		Player player = original.call(level, self, range);
		anchorDistance.set(-1.0);
		try {
			double[] points = AnchorPoints.spawnPoints(level);
			if (points == null) {
				return player;
			}
			double distance = AnchorRules.anchorDistance(AnchorRules.nearestSqr(points, self.getX(), self.getY(), self.getZ()),
					player == null ? -1.0 : player.distanceToSqr(self));
			if (distance < 0.0) {
				return player;
			}
			Player result = player != null ? player : AnchorPoints.standIn(level);
			if (result != null) {
				anchorDistance.set(distance);
			}
			return result;
		} catch (Throwable t) {
			Safe.log("mixin.anchor.despawn", t);
			anchorDistance.set(-1.0);
			return player;
		}
	}

	@WrapOperation(method = "checkDespawn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"))
	private double guildy$playerOrAnchorDistance(Entity player, Entity self, Operation<Double> original,
			@Share("guildyAnchorDistance") LocalDoubleRef anchorDistance) {
		double distance = anchorDistance.get();
		return distance >= 0.0 ? distance : original.call(player, self);
	}
}
