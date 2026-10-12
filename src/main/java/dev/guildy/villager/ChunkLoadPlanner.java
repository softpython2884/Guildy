package dev.guildy.villager;

import dev.guildy.data.ChunkKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure planning of the chunk tickets that keep working guild villagers active without players around (unit tested,
 * no Minecraft classes). An {@link Anchor} asks for the square of (2r+1)² chunks around a chunk to be entity-ticking;
 * the plan says which ticket centres to hold and how many chunks each guild keeps active.
 * <ul>
 * <li>Mode: {@code NEVER} plans nothing; {@code ONLINE} only for guilds with a member online; {@code ALWAYS} for all.</li>
 * <li>Order: anchors by {@link Kind} (the villagers themselves, then where they go, then their work zones, then the
 * storage chests), inside a kind nearest to the guild's heart first; guilds are served in turn (round robin) so the
 * server-wide cap is shared.</li>
 * <li>Caps: an anchor that would exceed the guild's or the server's chunk count is tried with a smaller radius, then
 * skipped (its villager is reported as capped). Chunks shared by several anchors count once.</li>
 * <li>Players: an anchor whose whole square is already kept active by a player gets no ticket and costs nothing.</li>
 * </ul>
 */
public final class ChunkLoadPlanner {
	public enum Mode {
		ONLINE,
		ALWAYS,
		NEVER;

		/** Config id ("online", "always", "never"). */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Case-insensitive, trimmed; null when unknown. */
		public static @Nullable Mode parse(@Nullable String value) {
			if (value == null) {
				return null;
			}
			String id = value.trim().toUpperCase(Locale.ROOT);
			for (Mode mode : values()) {
				if (mode.name().equals(id)) {
					return mode;
				}
			}
			return null;
		}
	}

	/** Priority of an anchor (declaration order). */
	public enum Kind {
		/** Around the villager itself: without it nothing works. */
		SELF,
		/** Where it is walking to, its bed at dusk, the deposit chests while it carries items, its return point. */
		DESTINATION,
		/** Its work zone (or the automatic search area). */
		ZONE,
		/** Storage chests of a sorter. */
		STORAGE
	}

	/**
	 * Square of (2·radius+1)² chunks around chunk (chunkX, chunkZ), asked by {@code villager} (null: no villager).
	 * {@code distanceSq}: squared distance to the guild's heart (blocks), nearest first inside a kind.
	 */
	public record Anchor(@Nullable UUID villager, Kind kind, int chunkX, int chunkZ, int radius, long distanceSq) {
	}

	/** Anchors of one guild; {@code memberOnline}: at least one of its members is connected. */
	public record GuildRequest(UUID guild, boolean memberOnline, List<Anchor> anchors) {
	}

	public record Limits(Mode mode, int maxChunksPerGuild, int maxChunksTotal) {
	}

	/**
	 * Chunks (packed {@link ChunkKey}) where players stand and how far around them (Chebyshev, in chunks) chunks are
	 * entity-ticking anyway; reach &lt; 0 = none.
	 */
	public record Players(long[] chunks, int reach) {
		public static final Players NONE = new Players(new long[0], -1);

		/** The whole square of the anchor is within the reach of one player. */
		public boolean covers(int chunkX, int chunkZ, int radius) {
			if (reach < 0) {
				return false;
			}
			for (long key : chunks) {
				int distance = Math.max(Math.abs(ChunkKey.x(key) - chunkX), Math.abs(ChunkKey.z(key) - chunkZ));
				if (distance + radius <= reach) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Result: {@code tickets} = ticket centre (packed chunk) → radius; {@code guildChunks} = chunks kept active per
	 * guild; {@code kept} = villagers with at least one ticket; {@code capped} = villagers with at least one anchor
	 * refused by the caps; {@code totalChunks} = distinct chunks kept active on the server.
	 */
	public record Plan(Map<Long, Integer> tickets, Map<UUID, Integer> guildChunks, Set<UUID> kept, Set<UUID> capped, int totalChunks) {
		public static final Plan EMPTY = new Plan(Map.of(), Map.of(), Set.of(), Set.of(), 0);

		public int chunks(UUID guild) {
			return guildChunks.getOrDefault(guild, 0);
		}
	}

	private ChunkLoadPlanner() {
	}

	/** True when the guild's villagers get tickets in this mode. */
	public static boolean active(Mode mode, boolean memberOnline) {
		return mode == Mode.ALWAYS || (mode == Mode.ONLINE && memberOnline);
	}

	public static Plan plan(Limits limits, List<GuildRequest> guilds, Players players) {
		if (limits.mode() == Mode.NEVER || guilds.isEmpty()) {
			return Plan.EMPTY;
		}
		List<GuildRequest> active = new ArrayList<>();
		for (GuildRequest request : guilds) {
			if (active(limits.mode(), request.memberOnline()) && !request.anchors().isEmpty()) {
				active.add(request);
			}
		}
		State state = new State(limits, players);
		Comparator<Anchor> nearestFirst = Comparator.comparingLong(Anchor::distanceSq);
		for (Kind kind : Kind.values()) {
			List<List<Anchor>> queues = new ArrayList<>();
			int longest = 0;
			for (GuildRequest request : active) {
				List<Anchor> queue = new ArrayList<>();
				for (Anchor anchor : request.anchors()) {
					if (anchor.kind() == kind) {
						queue.add(anchor);
					}
				}
				queue.sort(nearestFirst);
				queues.add(queue);
				longest = Math.max(longest, queue.size());
			}
			for (int i = 0; i < longest; i++) {
				for (int g = 0; g < active.size(); g++) {
					List<Anchor> queue = queues.get(g);
					if (i < queue.size()) {
						state.offer(active.get(g).guild(), queue.get(i));
					}
				}
			}
		}
		return state.result();
	}

	/** Bookkeeping of one planning pass. */
	private static final class State {
		private final Limits limits;
		private final Players players;
		private final Map<Long, Integer> tickets = new LinkedHashMap<>();
		private final Set<Long> all = new HashSet<>();
		private final Map<UUID, Set<Long>> byGuild = new HashMap<>();
		private final Set<UUID> kept = new HashSet<>();
		private final Set<UUID> capped = new HashSet<>();

		State(Limits limits, Players players) {
			this.limits = limits;
			this.players = players;
		}

		void offer(UUID guild, Anchor anchor) {
			int radius = Math.max(0, anchor.radius());
			if (players.covers(anchor.chunkX(), anchor.chunkZ(), radius)) {
				return;
			}
			Set<Long> mine = byGuild.computeIfAbsent(guild, id -> new HashSet<>());
			for (int r = radius; r >= 0; r--) {
				int newForGuild = 0;
				int newOverall = 0;
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						long key = ChunkKey.of(anchor.chunkX() + dx, anchor.chunkZ() + dz);
						if (!mine.contains(key)) {
							newForGuild++;
						}
						if (!all.contains(key)) {
							newOverall++;
						}
					}
				}
				if (mine.size() + newForGuild <= limits.maxChunksPerGuild() && all.size() + newOverall <= limits.maxChunksTotal()) {
					for (int dx = -r; dx <= r; dx++) {
						for (int dz = -r; dz <= r; dz++) {
							long key = ChunkKey.of(anchor.chunkX() + dx, anchor.chunkZ() + dz);
							mine.add(key);
							all.add(key);
						}
					}
					tickets.merge(ChunkKey.of(anchor.chunkX(), anchor.chunkZ()), r, Math::max);
					if (anchor.villager() != null) {
						kept.add(anchor.villager());
					}
					return;
				}
			}
			if (anchor.villager() != null) {
				capped.add(anchor.villager());
			}
		}

		Plan result() {
			Map<UUID, Integer> counts = new HashMap<>();
			for (Map.Entry<UUID, Set<Long>> entry : byGuild.entrySet()) {
				if (!entry.getValue().isEmpty()) {
					counts.put(entry.getKey(), entry.getValue().size());
				}
			}
			return new Plan(tickets, counts, kept, capped, all.size());
		}
	}
}
