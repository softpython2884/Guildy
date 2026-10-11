package dev.guildy.villager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-guild villagers and storage setup (persisted inside the guild).
 */
public class GuildVillagerData {
	/** Accepted guild villagers by entity UUID. */
	public Map<UUID, VillagerRecord> members = new LinkedHashMap<>();
	/** Assigned storage chests (deposit + sorted categories). */
	public List<StorageChest> chests = new ArrayList<>();
}
