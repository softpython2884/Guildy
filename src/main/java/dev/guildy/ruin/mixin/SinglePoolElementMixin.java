package dev.guildy.ruin.mixin;

import com.mojang.datafixers.util.Either;
import dev.guildy.ruin.MarkerCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ruin data markers: vanilla drops every structure block before {@code handleDataMarker} (see {@link MarkerCapture}),
 * so the markers of a placed ruin piece are read here, once per chunk the piece is placed in.
 */
@Mixin(SinglePoolElement.class)
public abstract class SinglePoolElementMixin {
	@Shadow
	@Final
	protected Either<Identifier, StructureTemplate> template;

	@Inject(method = "place", at = @At("RETURN"))
	private void guildy$captureRuinMarkers(StructureTemplateManager structureTemplateManager, WorldGenLevel level, StructureManager structureManager,
			ChunkGenerator generator, BlockPos position, BlockPos referencePos, Rotation rotation, BoundingBox chunkBB, RandomSource random,
			LiquidSettings liquidSettings, boolean keepJigsaws, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		Identifier location = this.template.left().orElse(null);
		if (location != null) {
			MarkerCapture.onPiecePlaced((SinglePoolElement) (Object) this, location, level, structureTemplateManager, position, rotation, chunkBB);
		}
	}
}
