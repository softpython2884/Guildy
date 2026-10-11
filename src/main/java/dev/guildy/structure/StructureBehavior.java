package dev.guildy.structure;

import dev.guildy.data.Guild;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Behaviour attached to a structure type (quest board, shop, reception...). Registered by feature modules with
 * {@link #register}; the structure system calls these hooks.
 */
public interface StructureBehavior {
	Map<String, StructureBehavior> REGISTRY = new HashMap<>();

	static void register(String structureId, StructureBehavior behavior) {
		REGISTRY.put(structureId, behavior);
	}

	static @Nullable StructureBehavior get(String structureId) {
		return REGISTRY.get(structureId);
	}

	/** Called once right after the structure was validated and its signs placed. */
	default void onBuilt(ServerLevel level, Guild guild, BuiltStructure structure, ServerPlayer builder) {
	}

	/** A player right-clicked one of the managed signs (or a block of the structure, signIndex = -1). */
	default void onUse(ServerPlayer player, Guild guild, BuiltStructure structure, int signIndex) {
	}

	/** The structure was broken / dismantled (signs already removed). */
	default void onRemoved(ServerLevel level, Guild guild, BuiltStructure structure) {
	}

	/**
	 * Text of a managed sign (exactly 4 lines, use Component.empty() for blank lines), or null to leave it unchanged.
	 * Called periodically by the structure system (only for loaded chunks).
	 */
	default @Nullable List<Component> signText(Guild guild, BuiltStructure structure, int signIndex) {
		return null;
	}
}
