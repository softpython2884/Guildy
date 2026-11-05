package dev.guildy.anchor.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The level of a chunk map (read by {@link LocalMobCapCalculatorMixin}). */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
	@Accessor("level")
	ServerLevel guildy$level();
}
