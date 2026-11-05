package dev.guildy.anchor;

import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chunk tickets of the anchor flags. Every 5 seconds (and right after any change) the state of every anchor is worked
 * out ({@link State}); each active one holds a ticket keeping its chunk square entity-ticking, the others hold nothing.
 * <p>
 * Tickets work as for the villagers' work chunks (see {@code WorkChunkLoader}, from the 26.3 sources): a dedicated,
 * unregistered ticket type (loading + simulation + keeps the dimension ticking, never saved, expiring 15 s after its
 * last refresh); {@code addTicketWithRadius(type, pos, r)} makes a square of radius r - 2 entity-ticking. Entity-ticking
 * chunks get random ticks (crops, saplings), block ticks (redstone, furnaces, hoppers) and entities (villagers, golems,
 * animals); the mob spawning of a player comes from the mixins of {@code dev.guildy.anchor.mixin}, fed by
 * {@link AnchorPoints}.
 * <p>
 * A loaded anchor whose banner is gone (explosion, water, piston, admin) is removed and its guild told.
 */
public final class AnchorTickets {
	/** Ticks without refresh before a ticket expires by itself (refreshed every 100 ticks). */
	private static final long EXPIRY_TICKS = 300L;
	static final TicketType TICKET = new TicketType(EXPIRY_TICKS,
			TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
	/** Entity-ticking radius → vanilla ticket radius (ticket level 31 - radius). */
	private static final int VANILLA_RADIUS_OFFSET = 2;

	/** Why an anchor is (not) active, in the order the conditions are checked. */
	public enum State {
		ACTIVE, DISABLED, DIMENSION, LEVEL, LIMIT, FOREIGN, OUTSIDE, OFFLINE;

		public String langKey() {
			return "anchor.state." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Level → (packed chunk → entity-ticking radius) held right now. */
	private static final Map<ServerLevel, Map<Long, Integer>> HELD = new IdentityHashMap<>();
	/** "guild id/anchor id" → state at the last refresh. */
	private static Map<String, State> states = Map.of();

	private AnchorTickets() {
	}

	static void init() {
		Scheduler.every("anchor.tickets", 100, 37, AnchorTickets::refresh);
		Lifecycle.STARTED.add(server -> {
			HELD.clear();
			states = Map.of();
			AnchorPoints.clear();
			refresh();
		});
		Lifecycle.STOPPING.add(server -> releaseAll());
		Lifecycle.RELOADED.add(AnchorTickets::refresh);
		// The guild is removed right after this event: plan again without it.
		GuildEvents.DISBANDED.add(guild -> Scheduler.later("anchor.disband", 1, AnchorTickets::refresh));
		GuildEvents.TERRITORY_CHANGED.add(guild -> Scheduler.later("anchor.territory", 1, AnchorTickets::refresh));
		GuildEvents.LEVEL_UP.add((guild, level) -> Scheduler.later("anchor.level", 1, AnchorTickets::refresh));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refreshIfOnlineMatters());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> refreshIfOnlineMatters());
	}

	private static void refreshIfOnlineMatters() {
		if (GuildyServer.config().anchors.onlyWhenMemberOnline) {
			Scheduler.later("anchor.online", 1, AnchorTickets::refresh);
		}
	}

	// ================================================================ queries

	/** State of an anchor at the last refresh (a new anchor is refreshed at once). */
	public static State state(Guild guild, ChunkAnchor anchor) {
		State state = states.get(key(guild, anchor));
		return state != null ? state : State.OFFLINE;
	}

	/** Anchor flags the guild may have planted at its level. */
	public static int allowed(Guild guild) {
		GuildyConfig.Anchors config = GuildyServer.config().anchors;
		return AnchorRules.allowed(guild.level, config.baseCount, config.extraEveryLevels, config.maxCount, config.minGuildLevel);
	}

	/** Chunks one active anchor keeps active. */
	public static int chunksPerAnchor() {
		int side = 2 * GuildyServer.config().anchors.radius + 1;
		return side * side;
	}

	/** Active anchors on the server at the last refresh. */
	public static int activeCount() {
		int count = 0;
		for (State state : states.values()) {
			if (state == State.ACTIVE) {
				count++;
			}
		}
		return count;
	}

	/** Tickets held right now (all levels). */
	public static int tickets() {
		int count = 0;
		for (Map<Long, Integer> held : HELD.values()) {
			count += held.size();
		}
		return count;
	}

	static @Nullable ServerLevel level(MinecraftServer server, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	static String dimensionId(Level level) {
		return level.dimension().identifier().toString();
	}

	private static String key(Guild guild, ChunkAnchor anchor) {
		return guild.id + "/" + anchor.id;
	}

	// ================================================================ refresh

	/** Works the states out and applies the tickets now (also after any change). */
	public static void refresh() {
		Safe.run("anchor.refresh", AnchorTickets::refreshNow);
	}

	private static void refreshNow() {
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return;
		}
		GuildyConfig.Anchors config = GuildyServer.config().anchors;
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer player : GuildyServer.players()) {
			online.add(player.getUUID());
		}
		Map<ServerLevel, Map<Long, Integer>> wanted = new IdentityHashMap<>();
		Map<ServerLevel, List<ChunkAnchor>> spawning = new IdentityHashMap<>();
		Map<String, State> next = new HashMap<>();
		boolean anyActive = false;
		for (Guild guild : GuildManager.all()) {
			if (guild.anchors.flags.isEmpty()) {
				continue;
			}
			checkBanners(server, guild);
			boolean memberOnline = false;
			for (UUID member : guild.memberIds()) {
				if (online.contains(member)) {
					memberOnline = true;
					break;
				}
			}
			int allowed = allowed(guild);
			int index = 0;
			for (ChunkAnchor anchor : guild.anchors.flags) {
				ServerLevel level = level(server, anchor.dimension);
				State state = state(config, guild, anchor, level, memberOnline, index++ < allowed);
				next.put(key(guild, anchor), state);
				if (state != State.ACTIVE || level == null) {
					continue;
				}
				anyActive = true;
				wanted.computeIfAbsent(level, l -> new HashMap<>())
						.merge(ChunkKey.of(anchor.chunkX(), anchor.chunkZ()), config.radius, Math::max);
				if (config.mobSpawning) {
					spawning.computeIfAbsent(level, l -> new ArrayList<>()).add(anchor);
				}
			}
		}
		apply(wanted);
		states = next;
		publish(spawning, config.radius, anyActive && config.keepServerAwake);
	}

	private static State state(GuildyConfig.Anchors config, Guild guild, ChunkAnchor anchor, @Nullable ServerLevel level,
			boolean memberOnline, boolean withinLimit) {
		if (!config.enabled) {
			return State.DISABLED;
		}
		if (level == null) {
			return State.DIMENSION;
		}
		if (guild.level < config.minGuildLevel) {
			return State.LEVEL;
		}
		if (!withinLimit) {
			return State.LIMIT;
		}
		Guild owner = GuildyServer.isClaimLevel(level) ? GuildManager.at(level, anchor.pos) : null;
		if (owner != null && owner != guild) {
			return State.FOREIGN;
		}
		if (owner == null && !config.outsideTerritory) {
			return State.OUTSIDE;
		}
		if (config.onlyWhenMemberOnline && !memberOnline) {
			return State.OFFLINE;
		}
		return State.ACTIVE;
	}

	/** Hands the spawning anchors over to the mixins. */
	private static void publish(Map<ServerLevel, List<ChunkAnchor>> spawning, int radius, boolean keepAwake) {
		Map<ServerLevel, double[]> points = new IdentityHashMap<>();
		Map<ServerLevel, int[]> squares = new IdentityHashMap<>();
		for (Map.Entry<ServerLevel, List<ChunkAnchor>> entry : spawning.entrySet()) {
			List<ChunkAnchor> anchors = entry.getValue();
			double[] xyz = new double[anchors.size() * 3];
			int[] square = new int[anchors.size() * 3];
			for (int i = 0; i < anchors.size(); i++) {
				ChunkAnchor anchor = anchors.get(i);
				xyz[i * 3] = anchor.pos.getX() + 0.5;
				xyz[i * 3 + 1] = anchor.pos.getY() + 0.5;
				xyz[i * 3 + 2] = anchor.pos.getZ() + 0.5;
				square[i * 3] = anchor.chunkX();
				square[i * 3 + 1] = anchor.chunkZ();
				square[i * 3 + 2] = radius;
			}
			points.put(entry.getKey(), xyz);
			squares.put(entry.getKey(), square);
		}
		AnchorPoints.set(points, squares, keepAwake);
	}

	/** Loaded anchors whose banner is gone are removed (the flag is lost; the guild may take another one). */
	private static void checkBanners(MinecraftServer server, Guild guild) {
		Iterator<ChunkAnchor> it = guild.anchors.flags.iterator();
		while (it.hasNext()) {
			ChunkAnchor anchor = it.next();
			ServerLevel level = level(server, anchor.dimension);
			if (level == null || !level.isLoaded(anchor.pos) || level.getBlockState(anchor.pos).getBlock() instanceof AbstractBannerBlock) {
				continue;
			}
			it.remove();
			GuildManager.markDirty();
			Msg.guild(guild, "anchor.lost", anchor.pos.getX(), anchor.pos.getY(), anchor.pos.getZ());
		}
	}

	// ================================================================ tickets

	/** Removes the tickets no longer wanted (or wanted with another radius), adds / refreshes the wanted ones. */
	private static void apply(Map<ServerLevel, Map<Long, Integer>> wanted) {
		Iterator<Map.Entry<ServerLevel, Map<Long, Integer>>> levels = HELD.entrySet().iterator();
		while (levels.hasNext()) {
			Map.Entry<ServerLevel, Map<Long, Integer>> entry = levels.next();
			ServerChunkCache chunks = entry.getKey().getChunkSource();
			Map<Long, Integer> next = wanted.getOrDefault(entry.getKey(), Map.of());
			Iterator<Map.Entry<Long, Integer>> it = entry.getValue().entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Long, Integer> held = it.next();
				Integer radius = next.get(held.getKey());
				if (radius == null || !radius.equals(held.getValue())) {
					chunks.removeTicketWithRadius(TICKET, chunk(held.getKey()), held.getValue() + VANILLA_RADIUS_OFFSET);
					it.remove();
				}
			}
			if (entry.getValue().isEmpty()) {
				levels.remove();
			}
		}
		for (Map.Entry<ServerLevel, Map<Long, Integer>> entry : wanted.entrySet()) {
			ServerChunkCache chunks = entry.getKey().getChunkSource();
			Map<Long, Integer> held = HELD.computeIfAbsent(entry.getKey(), l -> new HashMap<>());
			for (Map.Entry<Long, Integer> chunk : entry.getValue().entrySet()) {
				// Adding an existing ticket only resets its expiry.
				chunks.addTicketWithRadius(TICKET, chunk(chunk.getKey()), chunk.getValue() + VANILLA_RADIUS_OFFSET);
				held.put(chunk.getKey(), chunk.getValue());
			}
		}
	}

	/** Server stopping: every ticket goes (they are not saved anyway). */
	private static void releaseAll() {
		for (Map.Entry<ServerLevel, Map<Long, Integer>> entry : HELD.entrySet()) {
			ServerChunkCache chunks = entry.getKey().getChunkSource();
			for (Map.Entry<Long, Integer> held : entry.getValue().entrySet()) {
				chunks.removeTicketWithRadius(TICKET, chunk(held.getKey()), held.getValue() + VANILLA_RADIUS_OFFSET);
			}
		}
		HELD.clear();
		states = Map.of();
		AnchorPoints.clear();
	}

	private static ChunkPos chunk(long key) {
		return new ChunkPos(ChunkKey.x(key), ChunkKey.z(key));
	}
}
