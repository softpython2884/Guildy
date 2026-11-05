package dev.guildy.anchor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.guildy.anchor.AnchorPoints;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * An empty dedicated server pauses after pause-when-empty-seconds (MinecraftServer.tickServer); while an anchor is
 * active (and anchors.keepServerAwake) it keeps ticking, as it would with a player online.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@ModifyExpressionValue(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;pauseWhenEmptySeconds()I",
			ordinal = 0))
	private int guildy$keepAwakeForAnchors(int seconds) {
		return seconds > 0 && AnchorPoints.keepAwake() ? 0 : seconds;
	}
}
