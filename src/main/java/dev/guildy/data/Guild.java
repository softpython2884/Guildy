package dev.guildy.data;

import dev.guildy.core.GuildyServer;
import dev.guildy.event.GuildEventData;
import dev.guildy.quest.GuildQuestData;
import dev.guildy.structure.GuildStructureData;
import dev.guildy.trade.GuildTradeData;
import dev.guildy.upgrade.GuildUpgradeData;
import dev.guildy.upgrade.UpgradeEffects;
import dev.guildy.upgrade.Upgrades;
import dev.guildy.villager.GuildVillagerData;
import dev.guildy.wave.GuildWaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A guild. Serialized as-is to guilds.json by {@link GuildStorage}.
 * <p>
 * Members and claims must be changed through {@link GuildManager} (it maintains the lookup indexes);
 * everything else can be modified directly, followed by {@link GuildManager#markDirty()}.
 */
public final class Guild {
	public UUID id;
	public String name;
	public long createdAt;

	private Map<UUID, GuildMember> members = new LinkedHashMap<>();
	private Set<Long> claims = new LinkedHashSet<>();

	public int level = 1;
	public long xp;
	/** Treasury gold (1 = one gold ingot). Use GuildManager.addTreasury/takeTreasury. */
	public long treasury;

	/** Position of the heart flag (base core). Null until the founding flag is planted. */
	public @Nullable BlockPos heart;
	public @Nullable BlockPos home;
	public float homeYaw;

	public BannerDesign banner = new BannerDesign();
	public int extensionFlagsBought;
	/** Highest level whose one-shot level rewards were granted (see {@code LevelRewardRules.between}). */
	public int rewardedLevel;
	/** Free extension flags earned with level milestones, taken from the territory menu (or /guild flag buy). */
	public int freeFlags;

	public GuildWaveData waves = new GuildWaveData();
	public GuildStructureData structures = new GuildStructureData();
	public GuildQuestData quests = new GuildQuestData();
	public GuildVillagerData villagers = new GuildVillagerData();
	public GuildUpgradeData upgrades = new GuildUpgradeData();
	public GuildEventData events = new GuildEventData();
	public GuildTradeData trade = new GuildTradeData();

	public Guild() {
	}

	public Guild(UUID id, String name) {
		this.id = id;
		this.name = name;
		this.createdAt = System.currentTimeMillis();
	}

	// ---------------------------------------------------------------- members

	public Set<UUID> memberIds() {
		return Collections.unmodifiableSet(members.keySet());
	}

	public Map<UUID, GuildMember> members() {
		return Collections.unmodifiableMap(members);
	}

	public @Nullable GuildMember member(UUID player) {
		return members.get(player);
	}

	public boolean isMember(UUID player) {
		return members.containsKey(player);
	}

	public @Nullable GuildRole role(UUID player) {
		GuildMember member = members.get(player);
		return member == null ? null : member.role;
	}

	public @Nullable UUID leaderId() {
		for (var entry : members.entrySet()) {
			if (entry.getValue().role == GuildRole.LEADER) {
				return entry.getKey();
			}
		}
		return null;
	}

	public int size() {
		return members.size();
	}

	/** True if the player is a member whose role allows the action (see config rolePermissions). */
	public boolean can(UUID player, GuildPermission permission) {
		GuildRole role = role(player);
		return role != null && role.atLeast(GuildyServer.config().minRole(permission));
	}

	public List<ServerPlayer> onlineMembers() {
		List<ServerPlayer> list = new ArrayList<>();
		for (UUID uuid : members.keySet()) {
			ServerPlayer player = GuildyServer.player(uuid);
			if (player != null) {
				list.add(player);
			}
		}
		return list;
	}

	// ---------------------------------------------------------------- territory

	public Set<Long> claims() {
		return Collections.unmodifiableSet(claims);
	}

	public boolean owns(long chunkKey) {
		return claims.contains(chunkKey);
	}

	public int claimCount() {
		return claims.size();
	}

	public boolean isFounded() {
		return heart != null;
	}

	public int maxChunks() {
		return GuildyServer.config().maxChunks(level) + Upgrades.count(this, UpgradeEffects.EXTRA_CHUNKS);
	}

	/** Chunk key of the heart, or 0 when not founded. */
	public long heartChunk() {
		return heart == null ? 0L : ChunkKey.ofBlock(heart.getX(), heart.getZ());
	}

	/** True when the chunk has a claimed 4-neighbour that is NOT claimed by this guild (or unclaimed): border chunk. */
	public boolean isBorderChunk(long chunkKey) {
		return claims.contains(chunkKey) && (!claims.contains(ChunkKey.offset(chunkKey, 1, 0))
				|| !claims.contains(ChunkKey.offset(chunkKey, -1, 0))
				|| !claims.contains(ChunkKey.offset(chunkKey, 0, 1))
				|| !claims.contains(ChunkKey.offset(chunkKey, 0, -1)));
	}

	/** Max Chebyshev distance (in blocks) from the heart to the far edge of any claimed chunk. */
	public int territoryRadiusBlocks() {
		if (heart == null || claims.isEmpty()) {
			return 0;
		}
		long hc = heartChunk();
		int max = 0;
		for (long key : claims) {
			max = Math.max(max, ChunkKey.distance(hc, key));
		}
		return max * 16 + 16;
	}

	// package-private mutators used by GuildManager

	Map<UUID, GuildMember> mutableMembers() {
		return members;
	}

	Set<Long> mutableClaims() {
		return claims;
	}

	/** Called after deserialization: replaces nulls with defaults. */
	void fixNulls() {
		if (members == null) {
			members = new LinkedHashMap<>();
		}
		if (claims == null) {
			claims = new LinkedHashSet<>();
		}
		if (banner == null) {
			banner = new BannerDesign();
		}
		if (banner.layers == null) {
			banner.layers = new ArrayList<>();
		}
		if (waves == null) {
			waves = new GuildWaveData();
		}
		if (structures == null) {
			structures = new GuildStructureData();
		}
		if (quests == null) {
			quests = new GuildQuestData();
		}
		if (villagers == null) {
			villagers = new GuildVillagerData();
		}
		if (upgrades == null) {
			upgrades = new GuildUpgradeData();
		}
		upgrades.fixNulls();
		if (events == null) {
			events = new GuildEventData();
		}
		events.fixNulls();
		if (trade == null) {
			trade = new GuildTradeData();
		}
		trade.fixNulls();
		if (level < 1) {
			level = 1;
		}
		if (treasury < 0) {
			treasury = 0;
		}
		if (freeFlags < 0) {
			freeFlags = 0;
		}
	}
}
