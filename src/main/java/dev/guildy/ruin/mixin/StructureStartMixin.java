package dev.guildy.ruin.mixin;

import dev.guildy.ruin.MarkerCapture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers which structure start is placing its pieces on this thread, so that the ruin data markers captured by
 * {@link SinglePoolElementMixin} know their ruin (start chunk, structure, box).
 */
@Mixin(StructureStart.class)
public abstract class StructureStartMixin {
	@Inject(method = "placeInChunk", at = @At("HEAD"))
	private void guildy$enterStart(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, CallbackInfo ci) {
		MarkerCapture.enterStart((StructureStart) (Object) this);
	}

	@Inject(method = "placeInChunk", at = @At("RETURN"))
	private void guildy$exitStart(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, CallbackInfo ci) {
		MarkerCapture.exitStart();
	}
}
