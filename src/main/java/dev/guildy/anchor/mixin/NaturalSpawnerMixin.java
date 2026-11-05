package dev.guildy.anchor.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import dev.guildy.anchor.AnchorPoints;
import dev.guildy.anchor.AnchorRules;
import dev.guildy.core.Safe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.NaturalSpawner;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Natural spawning around anchors: a spawn position takes its distance from the nearest player or anchor (vanilla
 * spawns 24 to 128 blocks from the nearest player and gives up when the level has no player). When only an anchor is
 * near, a stand-in player is returned and its distance replaced by the anchor's.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
	@WrapOperation(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;"
			+ "Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
			+ "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getNearestPlayer(DDDDZ)Lnet/minecraft/world/entity/player/Player;"))
	private static @Nullable Player guildy$nearestPlayerOrAnchor(ServerLevel level, double x, double y, double z, double range, boolean filter,
			Operation<Player> original, @Share("guildyAnchorDistance") LocalDoubleRef anchorDistance) {
		Player player = original.call(level, x, y, z, range, filter);
		anchorDistance.set(-1.0);
		try {
			double[] points = AnchorPoints.spawnPoints(level);
			if (points == null) {
				return player;
			}
			double distance = AnchorRules.anchorDistance(AnchorRules.nearestSqr(points, x, y, z),
					player == null ? -1.0 : player.distanceToSqr(x, y, z));
			if (distance < 0.0) {
				return player;
			}
			Player result = player != null ? player : AnchorPoints.standIn(level);
			if (result != null) {
				anchorDistance.set(distance);
			}
			return result;
		} catch (Throwable t) {
			Safe.log("mixin.anchor.spawn", t);
			anchorDistance.set(-1.0);
			return player;
		}
	}

	@WrapOperation(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;"
			+ "Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
			+ "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"))
	private static double guildy$playerOrAnchorDistance(Player player, double x, double y, double z, Operation<Double> original,
			@Share("guildyAnchorDistance") LocalDoubleRef anchorDistance) {
		double distance = anchorDistance.get();
		return distance >= 0.0 ? distance : original.call(player, x, y, z);
	}
}
