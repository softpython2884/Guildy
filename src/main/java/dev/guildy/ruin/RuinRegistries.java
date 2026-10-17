package dev.guildy.ruin;

import dev.guildy.core.ItemUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.jspecify.annotations.Nullable;

/**
 * Registry lookups used by the config validation and the spawners (ids from ruins.json5).
 */
final class RuinRegistries {
	private RuinRegistries() {
	}

	static @Nullable EntityType<?> entity(@Nullable String id) {
		Identifier identifier = id == null ? null : Identifier.tryParse(id);
		return identifier == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
	}

	/** Living, summonable, non-misc entity types only (a Mob is checked again at spawn). */
	static boolean entityExists(String id) {
		EntityType<?> type = entity(id);
		return type != null && type.canSummon() && type.getCategory() != MobCategory.MISC;
	}

	static boolean itemExists(String id) {
		return id != null && !id.isBlank() && ItemUtil.item(id) != null;
	}

	static @Nullable Holder<MobEffect> effect(@Nullable String id) {
		Identifier identifier = id == null ? null : Identifier.tryParse(id);
		return identifier == null ? null : BuiltInRegistries.MOB_EFFECT.get(identifier).map(h -> (Holder<MobEffect>) h).orElse(null);
	}

	static boolean effectExists(String id) {
		return effect(id) != null;
	}
}
