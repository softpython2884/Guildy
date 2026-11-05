package dev.guildy.anchor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.guildy.anchor.AnchorPoints;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Global mob caps: vanilla scales them with the chunks within the spawning reach of the players (17 × 17 per player);
 * each spawning anchor adds a player's worth (without it, a level without players never spawns at all).
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@ModifyExpressionValue(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager;getNaturalSpawnChunkCount()I"))
	private int guildy$anchorSpawnChunks(int chunks) {
		return chunks + AnchorPoints.spawnChunkBonus(this.level);
	}
}
