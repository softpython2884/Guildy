package dev.guildy.scroll.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The synced "living entity flags" (bit 1 = using an item): a refused scroll use sends the real value back to the client,
 * which had already started the channel animation on its side. The damage of the last hit (invulnerability frames): a
 * spell hit puts it back as it was.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("DATA_LIVING_ENTITY_FLAGS")
	static EntityDataAccessor<Byte> guildy$livingFlags() {
		throw new AssertionError();
	}

	@Accessor("lastHurt")
	float guildy$lastHurt();

	@Accessor("lastHurt")
	void guildy$setLastHurt(float value);
}
