package dev.guildy.ruin.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The synced "living entity flags" (bit 1 = using an item): a refused incantation sends the real value back to the client,
 * which had already started the channel animation on its side.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityFlagsAccessor {
	@Accessor("DATA_LIVING_ENTITY_FLAGS")
	static EntityDataAccessor<Byte> guildy$ruinLivingFlags() {
		throw new AssertionError();
	}
}
