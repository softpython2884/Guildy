package dev.guildy.data;

import dev.guildy.Guildy;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Owner of all guild data and lookup indexes:
 * guild id → guild, lower-case name → guild, player → guild, chunk key → guild (claim dimension only).
 * Everything runs on the server thread.
 */
public final class GuildManager {
	private static final Map<UUID, Guild> GUILDS = new LinkedHashMap<>();
	private static final Map<String, Guild> BY_NAME = new HashMap<>();
	private static final Map<UUID, Guild> BY_PLAYER = new HashMap<>();
	private static final Long2ObjectOpenHashMap<Guild> CLAIMS = new Long2ObjectOpenHashMap<>();
	/** player → (guild id → expiry epoch ms). Not persisted. */
	private static final Map<UUID, Map<UUID, Long>> INVITES = new HashMap<>();
	private static boolean dirty;
	private static boolean loaded;

	private GuildManager() {
	}

	// ================================================================ queries

	public static Collection<Guild> all() {
		return Collections.unmodifiableCollection(GUILDS.values());
	}

	public static @Nullable Guild get(UUID id) {
		return GUILDS.get(id);
	}

	public static @Nullable Guild byName(String name) {
		return name == null ? null : BY_NAME.get(name.toLowerCase(Locale.ROOT));
	}

	public static @Nullable Guild of(UUID player) {
		return BY_PLAYER.get(player);
	}

	public static @Nullable Guild of(ServerPlayer player) {
		return BY_PLAYER.get(player.getUUID());
	}

	/** Guild owning the chunk at this position, or null (always null outside the claim dimension). */
	public static @Nullable Guild at(Level level, BlockPos pos) {
		if (!GuildyServer.isClaimLevel(level)) {
			return null;
		}
		return CLAIMS.get(ChunkKey.ofBlock(pos.getX(), pos.getZ()));
	}

	/** Guild owning the chunk (claim dimension). */
	public static @Nullable Guild atChunk(long chunkKey) {
		return CLAIMS.get(chunkKey);
	}

	public static boolean isClaimed(long chunkKey) {
		return CLAIMS.containsKey(chunkKey);
	}

	// ================================================================ guild lifecycle

	/** Validates a guild name. @return lang key of the error, or null if valid. */
	public static @Nullable String validateName(String name) {
		GuildyConfig config = GuildyServer.config();
		if (name == null) {
			return "error.name.length";
		}
		String trimmed = name.trim();
		if (trimmed.length() < config.guilds.nameMinLength || trimmed.length() > config.guilds.nameMaxLength) {
			return "error.name.length";
		}
		if (!trimmed.matches("[\\p{L}\\p{N} _\\-']+")) {
			return "error.name.chars";
		}
		if (byName(trimmed) != null) {
			return "error.name.taken";
		}
		return null;
	}

	/** Creates a guild with the player as leader. Caller must have validated name and membership. */
	public static Guild create(String name, ServerPlayer leader) {
		Guild guild = new Guild(UUID.randomUUID(), name.trim());
		guild.treasury = GuildyServer.config().economy.startingTreasury;
		guild.mutableMembers().put(leader.getUUID(),
				new GuildMember(GuildRole.LEADER, leader.getName().getString(), System.currentTimeMillis()));
		index(guild);
		INVITES.remove(leader.getUUID());
		markDirty();
		GuildEvents.fire(GuildEvents.CREATED, guild);
		return guild;
	}

	/** Disbands: fires DISBANDED (modules clean up), then removes all data and claims. */
	public static void disband(Guild guild) {
		GuildEvents.fire(GuildEvents.DISBANDED, guild);
		unindex(guild);
		GUILDS.remove(guild.id);
		for (Map<UUID, Long> invites : INVITES.values()) {
			invites.remove(guild.id);
		}
		markDirty();
	}

	public static void addMember(Guild guild, UUID player, String name) {
		Guild previous = BY_PLAYER.get(player);
		if (previous != null && previous != guild) {
			throw new IllegalStateException("Player already in another guild");
		}
		guild.mutableMembers().put(player, new GuildMember(GuildRole.MEMBER, name, System.currentTimeMillis()));
		BY_PLAYER.put(player, guild);
		INVITES.remove(player);
		markDirty();
		GuildEvents.fire2(GuildEvents.MEMBER_JOINED, guild, player);
	}

	public static void removeMember(Guild guild, UUID player) {
		if (guild.mutableMembers().remove(player) != null) {
			BY_PLAYER.remove(player);
			markDirty();
			GuildEvents.fire2(GuildEvents.MEMBER_LEFT, guild, player);
		}
	}

	public static void setRole(Guild guild, UUID player, GuildRole role) {
		GuildMember member = guild.member(player);
		if (member != null) {
			member.role = role;
			markDirty();
		}
	}

	/** Updates the stored name of a member (called on join). */
	public static void refreshName(ServerPlayer player) {
		Guild guild = of(player);
		if (guild != null) {
			GuildMember member = guild.member(player.getUUID());
			String name = player.getName().getString();
			if (member != null && !name.equals(member.name)) {
				member.name = name;
				markDirty();
			}
		}
	}

	// ================================================================ claims

	public static void claim(Guild guild, long chunkKey) {
		Guild owner = CLAIMS.get(chunkKey);
		if (owner != null && owner != guild) {
			throw new IllegalStateException("Chunk already claimed by " + owner.name);
		}
		guild.mutableClaims().add(chunkKey);
		CLAIMS.put(chunkKey, guild);
		markDirty();
	}

	public static void unclaim(Guild guild, long chunkKey) {
		if (guild.mutableClaims().remove(chunkKey)) {
			CLAIMS.remove(chunkKey);
			markDirty();
		}
	}

	public static void unclaimAll(Guild guild) {
		for (long key : guild.mutableClaims()) {
			CLAIMS.remove(key);
		}
		guild.mutableClaims().clear();
		markDirty();
	}

	public static void fireTerritoryChanged(Guild guild) {
		GuildEvents.fire(GuildEvents.TERRITORY_CHANGED, guild);
	}

	// ================================================================ progression & treasury

	/** Adds guild XP, handles level-ups (announced to online members). */
	public static void addXp(Guild guild, long amount) {
		if (amount <= 0) {
			return;
		}
		GuildyConfig config = GuildyServer.config();
		guild.xp += amount;
		while (guild.level < config.maxLevel() && guild.xp >= config.xpForLevel(guild.level + 1)) {
			guild.level++;
			Msg.guild(guild, "guild.levelup", guild.level);
			Msg.guildTitle(guild, Lang.get("guild.levelup.title"), Lang.get("guild.levelup.subtitle", guild.level));
			Msg.guildSound(guild, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
			GuildEvents.fire2(GuildEvents.LEVEL_UP, guild, guild.level);
		}
		markDirty();
	}

	/** Sets the level directly (admin), keeping XP consistent. */
	public static void setLevel(Guild guild, int level) {
		GuildyConfig config = GuildyServer.config();
		guild.level = Math.max(1, Math.min(config.maxLevel(), level));
		guild.xp = Math.max(guild.xp, config.xpForLevel(guild.level));
		if (guild.level < config.maxLevel()) {
			guild.xp = Math.min(guild.xp, config.xpForLevel(guild.level + 1) - 1);
		}
		markDirty();
	}

	public static void addTreasury(Guild guild, long amount) {
		if (amount > 0) {
			guild.treasury += amount;
			markDirty();
		}
	}

	/** @return false (nothing taken) if the treasury holds less than {@code amount}. */
	public static boolean takeTreasury(Guild guild, long amount) {
		if (amount < 0 || guild.treasury < amount) {
			return false;
		}
		guild.treasury -= amount;
		markDirty();
		return true;
	}

	// ================================================================ invites

	public static void invite(UUID player, Guild guild) {
		long expiry = System.currentTimeMillis() + GuildyServer.config().guilds.inviteExpireSeconds * 1000L;
		INVITES.computeIfAbsent(player, k -> new LinkedHashMap<>()).put(guild.id, expiry);
	}

	/** Valid (non-expired) invitations of a player. */
	public static List<Guild> invitesOf(UUID player) {
		Map<UUID, Long> invites = INVITES.get(player);
		List<Guild> list = new ArrayList<>();
		if (invites == null) {
			return list;
		}
		long now = System.currentTimeMillis();
		invites.entrySet().removeIf(e -> e.getValue() < now || !GUILDS.containsKey(e.getKey()));
		for (UUID id : invites.keySet()) {
			Guild guild = GUILDS.get(id);
			if (guild != null) {
				list.add(guild);
			}
		}
		return list;
	}

	public static boolean hasInvite(UUID player, Guild guild) {
		return invitesOf(player).contains(guild);
	}

	public static void removeInvite(UUID player, Guild guild) {
		Map<UUID, Long> invites = INVITES.get(player);
		if (invites != null) {
			invites.remove(guild.id);
		}
	}

	// ================================================================ persistence

	public static void markDirty() {
		dirty = true;
	}

	public static boolean isDirty() {
		return dirty;
	}

	public static void load(MinecraftServer server) {
		clear();
		for (Guild guild : GuildStorage.load(server)) {
			if (GUILDS.containsKey(guild.id) || BY_NAME.containsKey(guild.name.toLowerCase(Locale.ROOT))) {
				Guildy.LOGGER.warn("Skipping duplicate guild {}", guild.name);
				continue;
			}
			index(guild);
		}
		loaded = true;
		dirty = false;
		Guildy.LOGGER.info("Loaded {} guild(s)", GUILDS.size());
	}

	/** Saves if dirty (or always when {@code force}). Never throws. */
	public static void save(MinecraftServer server, boolean force) {
		if (!loaded || (!dirty && !force)) {
			return;
		}
		try {
			GuildStorage.save(server, GUILDS.values());
			dirty = false;
		} catch (Exception e) {
			Guildy.LOGGER.error("Failed to save guild data", e);
		}
	}

	public static void clear() {
		GUILDS.clear();
		BY_NAME.clear();
		BY_PLAYER.clear();
		CLAIMS.clear();
		INVITES.clear();
		loaded = false;
		dirty = false;
	}

	private static void index(Guild guild) {
		GUILDS.put(guild.id, guild);
		BY_NAME.put(guild.name.toLowerCase(Locale.ROOT), guild);
		for (UUID member : guild.memberIds()) {
			Guild other = BY_PLAYER.get(member);
			if (other != null && other != guild) {
				Guildy.LOGGER.warn("Player {} is in two guilds ({} and {}); keeping the first", member, other.name, guild.name);
				guild.mutableMembers().remove(member);
				continue;
			}
			BY_PLAYER.put(member, guild);
		}
		for (long key : new ArrayList<>(guild.mutableClaims())) {
			Guild other = CLAIMS.get(key);
			if (other != null && other != guild) {
				Guildy.LOGGER.warn("Chunk {} claimed by two guilds; keeping {}", ChunkKey.toString(key), other.name);
				guild.mutableClaims().remove(key);
				continue;
			}
			CLAIMS.put(key, guild);
		}
	}

	private static void unindex(Guild guild) {
		BY_NAME.remove(guild.name.toLowerCase(Locale.ROOT));
		for (UUID member : guild.memberIds()) {
			if (BY_PLAYER.get(member) == guild) {
				BY_PLAYER.remove(member);
			}
		}
		for (long key : guild.mutableClaims()) {
			if (CLAIMS.get(key) == guild) {
				CLAIMS.remove(key);
			}
		}
	}
}
