package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-guild villagers and storage setup (persisted inside the guild).
 */
public class GuildVillagerData {
	/** Accepted guild villagers by entity UUID. */
	public Map<UUID, VillagerRecord> members = new LinkedHashMap<>();
	/** Assigned storage chests (deposit + sorted categories). */
	public List<StorageChest> chests = new ArrayList<>();
	/** Villagers that left the guild while their chunk was unloaded: despawned when they load. */
	public Set<UUID> leaving = new LinkedHashSet<>();

	public @Nullable VillagerRecord get(UUID uuid) {
		return members.get(uuid);
	}

	public @Nullable StorageChest chestAt(BlockPos pos) {
		for (StorageChest chest : chests) {
			if (pos.equals(chest.pos)) {
				return chest;
			}
		}
		return null;
	}

	/** Fixes nulls after deserialization. */
	public void sanitize() {
		if (members == null) {
			members = new LinkedHashMap<>();
		}
		members.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
		for (Map.Entry<UUID, VillagerRecord> entry : members.entrySet()) {
			entry.getValue().sanitize(entry.getKey());
		}
		if (chests == null) {
			chests = new ArrayList<>();
		}
		chests.removeIf(chest -> chest == null || chest.pos == null);
		for (StorageChest chest : chests) {
			chest.sanitize();
		}
		if (leaving == null) {
			leaving = new LinkedHashSet<>();
		}
		leaving.remove(null);
	}
}
