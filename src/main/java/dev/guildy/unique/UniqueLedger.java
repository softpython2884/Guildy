package dev.guildy.unique;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Registry of the unique weapon copies (pure Java, unit-tested; persisted as {@code <world>/guildy/uniques.json}): every
 * copy has a serial (also written in the item), its id, source, first issue time, current holder (guild and / or player),
 * last sighting and state. A copy becomes LOST when the item is destroyed (or freed by an admin) and ACTIVE again
 * whenever it is seen. Also the guild collections (ids ever obtained), the completed collections and the duplicates
 * (same serial seen twice at once).
 */
public final class UniqueLedger {
	public static final int DATA_VERSION = 1;
	/** Source of a copy registered without one (items created before the registry, merchant offers without marker...). */
	public static final String LEGACY = "legacy";
	static final int MAX_DUPLICATES = 64;
	/** A sighting alone marks the ledger dirty at most once a minute (no save for a weapon held in hand). */
	static final long SEEN_PERSIST_MS = 60_000;
	/** A duplicate still seen is logged again after this delay. */
	static final long DUPLICATE_LOG_MS = 10 * 60_000;

	public enum State {
		ACTIVE,
		LOST
	}

	/** Result of a sighting. */
	public enum Sighting {
		/** Unknown serial: registered as is (registry lost, item from another world). */
		NEW,
		/** Same holder (or a container sighting of an active copy). */
		SAME,
		/** Carried by another holder now. */
		MOVED,
		/** A lost copy seen again: active again. */
		FOUND,
		/** The serial belongs to another id: the stack needs a new serial. */
		CONFLICT
	}

	/** One copy of a unique weapon. */
	public static final class Copy {
		public long serial;
		public String id = "";
		public String source = "";
		/** Epoch milliseconds. */
		public long issuedAt;
		public @Nullable UUID guild;
		public @Nullable UUID player;
		public String playerName = "";
		/** Epoch milliseconds (saved at most once a minute while it is only seen). */
		public long lastSeen;
		public State state = State.ACTIVE;
		public long lostAt;
		public String lostReason = "";
		/** Server tick of the last sighting (runtime: a loss is confirmed only when the copy was not seen since). */
		transient long seenTick = -1;

		public Copy() {
		}

		Copy(long serial, String id, String source, @Nullable UUID guild, @Nullable UUID player, String playerName, long now, long tick) {
			this.serial = serial;
			this.id = id;
			this.source = source;
			this.issuedAt = now;
			this.guild = guild;
			this.player = player;
			this.playerName = playerName == null ? "" : playerName;
			this.lastSeen = now;
			this.seenTick = tick;
		}

		public boolean active() {
			return state == State.ACTIVE;
		}
	}

	/** The same serial seen in two places at once (creative copy, duplication bug): logged and listed, never deleted. */
	public static final class Duplicate {
		public long serial;
		public String id = "";
		public String first = "";
		public String second = "";
		public long firstSeen;
		public long lastSeen;
		public int count;
		transient long lastLogged;
	}

	private record Pending(long tick, String reason) {
	}

	int dataVersion = DATA_VERSION;
	long nextSerial = 1;
	List<Copy> copies = new ArrayList<>();
	/** Guild id → unique ids ever obtained (the collection). */
	Map<String, List<String>> obtained = new LinkedHashMap<>();
	/** Guilds whose completed collection was announced. */
	List<String> completed = new ArrayList<>();
	List<Duplicate> duplicates = new ArrayList<>();

	private transient Map<Long, Copy> bySerial = new HashMap<>();
	private transient Map<Long, Pending> pending = new LinkedHashMap<>();
	private transient Set<UUID> grown = new LinkedHashSet<>();
	private transient boolean dirty;

	// ================================================================== loading

	/** After Gson: null lists, invalid and repeated serials, serial counter (also builds the index). */
	public void fixNulls() {
		if (copies == null) {
			copies = new ArrayList<>();
		}
		if (obtained == null) {
			obtained = new LinkedHashMap<>();
		}
		if (completed == null) {
			completed = new ArrayList<>();
		}
		if (duplicates == null) {
			duplicates = new ArrayList<>();
		}
		bySerial = new HashMap<>();
		pending = new LinkedHashMap<>();
		grown = new LinkedHashSet<>();
		long max = 0;
		Iterator<Copy> it = copies.iterator();
		while (it.hasNext()) {
			Copy copy = it.next();
			if (copy == null || copy.serial <= 0 || copy.id == null || copy.id.isEmpty() || bySerial.containsKey(copy.serial)) {
				it.remove();
				continue;
			}
			if (copy.state == null) {
				copy.state = State.ACTIVE;
			}
			copy.source = copy.source == null ? "" : copy.source;
			copy.playerName = copy.playerName == null ? "" : copy.playerName;
			copy.lostReason = copy.lostReason == null ? "" : copy.lostReason;
			copy.seenTick = -1;
			bySerial.put(copy.serial, copy);
			max = Math.max(max, copy.serial);
		}
		nextSerial = Math.max(Math.max(1, nextSerial), max + 1);
		obtained.values().removeIf(Objects::isNull);
		obtained.keySet().removeIf(Objects::isNull);
		for (List<String> ids : obtained.values()) {
			ids.removeIf(Objects::isNull);
		}
		completed.removeIf(Objects::isNull);
		duplicates.removeIf(d -> d == null || d.id == null);
		dataVersion = DATA_VERSION;
		dirty = false;
	}

	public boolean isDirty() {
		return dirty;
	}

	public void markSaved() {
		dirty = false;
	}

	// ================================================================== queries

	public @Nullable Copy copy(long serial) {
		return bySerial.get(serial);
	}

	public Collection<Copy> copies() {
		return bySerial.values();
	}

	/** Copies of an id, by serial. */
	public List<Copy> copiesOf(String id) {
		List<Copy> list = new ArrayList<>();
		for (Copy copy : bySerial.values()) {
			if (copy.id.equals(id)) {
				list.add(copy);
			}
		}
		list.sort(Comparator.comparingLong(c -> c.serial));
		return list;
	}

	/** Every id with at least one copy. */
	public Set<String> ids() {
		Set<String> ids = new LinkedHashSet<>();
		for (Copy copy : bySerial.values()) {
			ids.add(copy.id);
		}
		return ids;
	}

	/** Ids of the active copies held by the guild (or, for a guildless recipient, by the player). */
	public Set<String> heldBy(@Nullable UUID guild, @Nullable UUID player) {
		Set<String> ids = new LinkedHashSet<>();
		if (guild == null && player == null) {
			return ids;
		}
		for (Copy copy : bySerial.values()) {
			if (copy.active() && (guild != null ? guild.equals(copy.guild) : player.equals(copy.player))) {
				ids.add(copy.id);
			}
		}
		return ids;
	}

	/** Active copies held by the guild. */
	public List<Copy> heldCopies(UUID guild) {
		List<Copy> list = new ArrayList<>();
		for (Copy copy : bySerial.values()) {
			if (copy.active() && guild.equals(copy.guild)) {
				list.add(copy);
			}
		}
		list.sort(Comparator.comparingLong(c -> c.serial));
		return list;
	}

	/** Ids with at least one active copy anywhere. */
	public Set<String> activeIds() {
		Set<String> ids = new LinkedHashSet<>();
		for (Copy copy : bySerial.values()) {
			if (copy.active()) {
				ids.add(copy.id);
			}
		}
		return ids;
	}

	/**
	 * Ids a roll must skip for this recipient: GUILD = the active copies of the recipient's guild (or of the guildless
	 * player), SERVER = every active copy, NONE = nothing.
	 */
	public Set<String> excluded(UniquenessMode mode, @Nullable UUID guild, @Nullable UUID player) {
		return switch (mode) {
			case NONE -> Set.of();
			case SERVER -> activeIds();
			case GUILD -> heldBy(guild, player);
		};
	}

	/** Ids the guild ever obtained. */
	public Set<String> obtainedBy(@Nullable UUID guild) {
		List<String> ids = guild == null ? null : obtained.get(guild.toString());
		return ids == null ? Set.of() : new LinkedHashSet<>(ids);
	}

	public List<Duplicate> duplicates() {
		return duplicates;
	}

	public boolean hasPendingLosses() {
		return !pending.isEmpty();
	}

	public long nextSerial() {
		return nextSerial;
	}

	// ================================================================== changes

	/** A new copy (rolled, given, or a legacy item without serial): next serial, active, held by the recipient. */
	public Copy issue(String id, String source, @Nullable UUID guild, @Nullable UUID player, @Nullable String playerName, long now, long tick) {
		Copy copy = new Copy(nextSerial++, id, source, guild, player, playerName, now, tick);
		copies.add(copy);
		bySerial.put(copy.serial, copy);
		obtain(copy.guild, id);
		dirty = true;
		return copy;
	}

	/**
	 * A copy seen in the world. {@code carried} = in a player's inventory or ender chest (the holder becomes that player and
	 * his guild); otherwise (an opened container) only the sighting counts, the holder changes only for a new, lost or
	 * unheld copy.
	 */
	public Sighting see(long serial, String id, @Nullable UUID guild, @Nullable UUID player, @Nullable String playerName, boolean carried,
			String source, long now, long tick) {
		Copy copy = bySerial.get(serial);
		if (copy == null) {
			copy = new Copy(serial, id, source, guild, player, playerName, now, tick);
			copies.add(copy);
			bySerial.put(serial, copy);
			nextSerial = Math.max(nextSerial, serial + 1);
			obtain(guild, id);
			dirty = true;
			return Sighting.NEW;
		}
		if (!copy.id.equals(id)) {
			return Sighting.CONFLICT;
		}
		copy.seenTick = tick;
		if (now - copy.lastSeen >= SEEN_PERSIST_MS) {
			dirty = true;
		}
		copy.lastSeen = Math.max(copy.lastSeen, now);
		if (copy.state == State.LOST) {
			copy.state = State.ACTIVE;
			copy.lostAt = 0;
			copy.lostReason = "";
			setHolder(copy, guild, player, playerName);
			dirty = true;
			return Sighting.FOUND;
		}
		boolean unheld = copy.guild == null && copy.player == null && (guild != null || player != null);
		if (carried && (!Objects.equals(copy.guild, guild) || !Objects.equals(copy.player, player)) || !carried && unheld) {
			setHolder(copy, guild, player, playerName);
			dirty = true;
			return Sighting.MOVED;
		}
		if (carried && playerName != null && !playerName.isEmpty() && !playerName.equals(copy.playerName)) {
			copy.playerName = playerName;
			dirty = true;
		}
		return Sighting.SAME;
	}

	private void setHolder(Copy copy, @Nullable UUID guild, @Nullable UUID player, @Nullable String playerName) {
		copy.guild = guild;
		copy.player = player;
		copy.playerName = playerName == null ? "" : playerName;
		obtain(guild, copy.id);
	}

	/** Marks a copy lost (destroyed, broken, freed). @return false when unknown or already lost */
	public boolean markLost(long serial, String reason, long now) {
		Copy copy = bySerial.get(serial);
		pending.remove(serial);
		if (copy == null || copy.state == State.LOST) {
			return false;
		}
		copy.state = State.LOST;
		copy.lostAt = now;
		copy.lostReason = reason == null ? "" : reason;
		dirty = true;
		return true;
	}

	/**
	 * The item entity of an active copy was destroyed at this tick: lost unless the copy is seen again (a pickup also
	 * removes the entity: the inventory scan then sees the copy) before {@link #confirmLosses}.
	 */
	public void suspectLoss(long serial, long tick, String reason) {
		Copy copy = bySerial.get(serial);
		if (copy != null && copy.active()) {
			pending.put(serial, new Pending(tick, reason));
		}
	}

	/**
	 * Confirms the suspected losses older than {@code delayTicks} (all of them with {@code force}): lost when the copy was
	 * not seen since its removal. @return the copies marked lost
	 */
	public List<Copy> confirmLosses(long tick, int delayTicks, long now, boolean force) {
		List<Copy> lost = new ArrayList<>();
		Iterator<Map.Entry<Long, Pending>> it = pending.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Pending> entry = it.next();
			Pending suspect = entry.getValue();
			if (!force && tick - suspect.tick() < delayTicks) {
				continue;
			}
			it.remove();
			Copy copy = bySerial.get(entry.getKey());
			if (copy == null || !copy.active() || copy.seenTick >= suspect.tick()) {
				continue;
			}
			copy.state = State.LOST;
			copy.lostAt = now;
			copy.lostReason = suspect.reason();
			dirty = true;
			lost.add(copy);
		}
		return lost;
	}

	/** Admin: marks lost the active copies of an id (only those of a guild when given). @return the copies freed */
	public List<Copy> free(String id, @Nullable UUID guild, String reason, long now) {
		List<Copy> freed = new ArrayList<>();
		for (Copy copy : copiesOf(id)) {
			if (copy.active() && (guild == null || guild.equals(copy.guild)) && markLost(copy.serial, reason, now)) {
				freed.add(copy);
			}
		}
		return freed;
	}

	/**
	 * The same serial seen in two places at once. @return true when it should be logged (new duplicate, or not logged for
	 * a while)
	 */
	public boolean duplicate(long serial, String id, String first, String second, long now) {
		Duplicate found = null;
		for (Duplicate duplicate : duplicates) {
			if (duplicate.serial == serial) {
				found = duplicate;
				break;
			}
		}
		if (found == null) {
			found = new Duplicate();
			found.serial = serial;
			found.id = id;
			found.firstSeen = now;
			duplicates.add(found);
			while (duplicates.size() > MAX_DUPLICATES) {
				duplicates.removeFirst();
			}
			dirty = true;
		} else if (now - found.lastSeen >= SEEN_PERSIST_MS) {
			dirty = true;
		}
		found.first = first;
		found.second = second;
		found.lastSeen = now;
		found.count++;
		if (found.lastLogged == 0 || now - found.lastLogged >= DUPLICATE_LOG_MS) {
			found.lastLogged = now;
			return true;
		}
		return false;
	}

	/** Admin: forgets the duplicates of a serial (all of them when 0). @return the number removed */
	public int clearDuplicates(long serial) {
		int before = duplicates.size();
		duplicates.removeIf(d -> serial == 0 || d.serial == serial);
		if (duplicates.size() != before) {
			dirty = true;
		}
		return before - duplicates.size();
	}

	/** Adds an id to the guild's collection (remembered in {@link #drainGrown}). */
	void obtain(@Nullable UUID guild, String id) {
		if (guild == null) {
			return;
		}
		List<String> ids = obtained.computeIfAbsent(guild.toString(), key -> new ArrayList<>());
		if (!ids.contains(id)) {
			ids.add(id);
			grown.add(guild);
			dirty = true;
		}
	}

	/** Guilds whose collection grew since the last call. */
	public Set<UUID> drainGrown() {
		if (grown.isEmpty()) {
			return Set.of();
		}
		Set<UUID> list = new LinkedHashSet<>(grown);
		grown.clear();
		return list;
	}

	/** True once, when the guild's collection first contains every id of {@code all} (then remembered). */
	public boolean completes(UUID guild, Collection<String> all) {
		if (all.isEmpty() || completed.contains(guild.toString()) || !obtainedBy(guild).containsAll(all)) {
			return false;
		}
		completed.add(guild.toString());
		dirty = true;
		return true;
	}

	/** A disbanded guild: its copies keep their player holder only, its collection is forgotten. */
	public void forgetGuild(UUID guild) {
		for (Copy copy : bySerial.values()) {
			if (guild.equals(copy.guild)) {
				copy.guild = null;
				dirty = true;
			}
		}
		if (obtained.remove(guild.toString()) != null | completed.remove(guild.toString())) {
			dirty = true;
		}
		grown.remove(guild);
	}
}
