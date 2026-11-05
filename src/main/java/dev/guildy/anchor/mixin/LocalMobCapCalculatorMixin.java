package dev.guildy.anchor.mixin;

import dev.guildy.anchor.AnchorPoints;
import dev.guildy.anchor.AnchorRules;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Local mob caps of the anchors: vanilla keeps one count per player of the mobs in its spawning reach and lets a chunk
 * spawn while one of the players near it is under the cap; each anchor gets the same count and cap. The calculator is
 * built anew at every spawning tick, so the anchors are read once per tick.
 */
@Mixin(LocalMobCapCalculator.class)
public abstract class LocalMobCapCalculatorMixin {
	@Shadow
	@Final
	private ChunkMap chunkMap;

	@Unique
	private boolean guildy$read;
	@Unique
	private double @Nullable [] guildy$points;
	/** anchor × category → mobs in its reach. */
	@Unique
	private int @Nullable [] guildy$counts;
	@Unique
	private int guildy$categories;

	@Unique
	private double @Nullable [] guildy$anchors() {
		if (!guildy$read) {
			guildy$read = true;
			double[] points = AnchorPoints.spawnPoints(((ChunkMapAccessor) this.chunkMap).guildy$level());
			if (points != null) {
				guildy$categories = MobCategory.values().length;
				guildy$counts = new int[points.length / 3 * guildy$categories];
				guildy$points = points;
			}
		}
		return guildy$points;
	}

	@Inject(method = "addMob", at = @At("TAIL"))
	private void guildy$countForAnchors(ChunkPos pos, MobCategory category, CallbackInfo ci) {
		double[] points = guildy$anchors();
		int[] counts = guildy$counts;
		if (points == null || counts == null) {
			return;
		}
		for (int i = 0; i < points.length / 3; i++) {
			if (AnchorRules.chunkInReach(points[i * 3], points[i * 3 + 2], pos.x(), pos.z())) {
				counts[i * guildy$categories + category.ordinal()]++;
			}
		}
	}

	@Inject(method = "canSpawn", at = @At("RETURN"), cancellable = true)
	private void guildy$anchorCap(MobCategory category, ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		double[] points = guildy$anchors();
		int[] counts = guildy$counts;
		if (points == null || counts == null) {
			return;
		}
		for (int i = 0; i < points.length / 3; i++) {
			if (AnchorRules.chunkInReach(points[i * 3], points[i * 3 + 2], pos.x(), pos.z())
					&& counts[i * guildy$categories + category.ordinal()] < category.getMaxInstancesPerChunk()) {
				cir.setReturnValue(true);
				return;
			}
		}
	}
}
