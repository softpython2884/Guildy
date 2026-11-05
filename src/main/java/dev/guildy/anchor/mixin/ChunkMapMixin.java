package dev.guildy.anchor.mixin;

import dev.guildy.anchor.AnchorPoints;
import dev.guildy.anchor.AnchorRules;
import dev.guildy.core.Safe;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Anchors spawn mobs like players: their entity-ticking chunks become spawning chunks (vanilla only lists the chunks
 * near players), and a chunk within 128 blocks of an anchor counts as near a player (nether portals, debug).
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Shadow
	private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

	@Inject(method = "collectSpawningChunks", at = @At("TAIL"))
	private void guildy$addAnchorChunks(List<LevelChunk> output, CallbackInfo ci) {
		try {
			int[] squares = AnchorPoints.spawnSquares(this.level);
			if (squares == null) {
				return;
			}
			LongOpenHashSet present = new LongOpenHashSet(output.size() * 2 + 16);
			for (LevelChunk chunk : output) {
				present.add(chunk.getPos().pack());
			}
			Long2ObjectLinkedOpenHashMap<ChunkHolder> holders = this.visibleChunkMap;
			for (int i = 0; i + 2 < squares.length; i += 3) {
				int radius = squares[i + 2];
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						long key = ChunkPos.pack(squares[i] + dx, squares[i + 1] + dz);
						if (!present.add(key)) {
							continue;
						}
						ChunkHolder holder = holders.get(key);
						LevelChunk chunk = holder == null ? null : holder.getTickingChunk();
						if (chunk != null) {
							output.add(chunk);
						}
					}
				}
			}
		} catch (Throwable t) {
			Safe.log("mixin.anchor.spawning_chunks", t);
		}
	}

	@Inject(method = "anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z", at = @At("RETURN"), cancellable = true)
	private void guildy$anchorCloseEnough(ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		double[] points = AnchorPoints.spawnPoints(this.level);
		if (points != null && AnchorRules.chunkInReach(points, pos.x(), pos.z())) {
			cir.setReturnValue(true);
		}
	}
}
