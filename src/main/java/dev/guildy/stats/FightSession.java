package dev.guildy.stats;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The statistics of one fight while it runs (runtime only, bounded): the tallies of its players (at most
 * {@code maxPlayers}), the kind of every monster hit (to credit its kill after the fight forgot it), the room guards
 * posted, when it opened and when its guardian appeared. Opened and closed by the module of the fight
 * ({@link FightTracker#open} / {@link FightTracker#close}).
 */
public final class FightSession {
	/** Monsters whose kind is remembered at most (oldest forgotten first). */
	static final int MAX_KINDS = 2048;

	public final String key;
	final FightArena arena;
	public final long openedTick;
	private long fightStartTick = -1;
	private final int maxPlayers;
	private final Map<UUID, FightTally> tallies = new LinkedHashMap<>();
	private final Map<UUID, String> names = new LinkedHashMap<>();
	private final Map<UUID, FightKind> kinds = new LinkedHashMap<>(64, 0.75F, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, FightKind> eldest) {
			return size() > MAX_KINDS;
		}
	};
	private final int[] killed = new int[FightKind.values().length];
	private int guardsPosted;
	/** Seconds without any player in the arena (used by the module to forget an idle session). */
	public int secondsAlone;

	FightSession(String key, FightArena arena, long now, int maxPlayers) {
		this.key = key;
		this.arena = arena;
		this.openedTick = now;
		this.maxPlayers = Math.max(1, maxPlayers);
	}

	public FightArena arena() {
		return arena;
	}

	/** The tally of a player, created on first use (null when the session already follows {@code maxPlayers} players). */
	public @Nullable FightTally tally(UUID player, String name) {
		FightTally tally = tallies.get(player);
		if (tally == null) {
			if (tallies.size() >= maxPlayers) {
				return null;
			}
			tally = new FightTally();
			tallies.put(player, tally);
		}
		names.put(player, name);
		return tally;
	}

	public @Nullable FightTally existing(UUID player) {
		return tallies.get(player);
	}

	public Map<UUID, FightTally> tallies() {
		return Collections.unmodifiableMap(tallies);
	}

	public Map<UUID, String> names() {
		return Collections.unmodifiableMap(names);
	}

	public void kind(UUID mob, FightKind kind) {
		kinds.put(mob, kind);
	}

	public @Nullable FightKind kindOf(UUID mob) {
		return kinds.get(mob);
	}

	/** A monster of this kind died (whoever killed it). */
	public void killed(FightKind kind) {
		killed[kind.ordinal()]++;
	}

	public int killedCount(FightKind kind) {
		return killed[kind.ordinal()];
	}

	public void addGuardsPosted(int count) {
		guardsPosted += Math.max(0, count);
	}

	public int guardsPosted() {
		return guardsPosted;
	}

	/** The guardian appeared (the first time only: a fight keeps its start). */
	public void fightStarted(long now) {
		if (fightStartTick < 0) {
			fightStartTick = now;
		}
	}

	/** Tick the guardian appeared, -1 before. */
	public long fightStartTick() {
		return fightStartTick;
	}
}
