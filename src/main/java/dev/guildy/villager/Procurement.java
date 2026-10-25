package dev.guildy.villager;

import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Materials the villagers of a guild should fetch (craft, smelt or gather), by requester ("build:&lt;project id&gt;"...).
 * Not saved: each requester posts its current shortfall again regularly, and a post older than {@link #TTL_TICKS} is
 * forgotten (a requester that disappears stops asking by itself).
 * <p>
 * Also kept here (same lifetime rules):
 * <ul>
 * <li>what each gatherer is fetching right now ({@link #fetching(Guild, UUID, Item, int)}): two gatherers never chase the
 * same shortfall, and a requester may show it ("12 verre en cours");</li>
 * <li>what a requester counts on in the storage ({@link #keep}): the gatherers never use it up as an ingredient (a
 * wanted item is never used up either: its whole stock already belongs to the requester).</li>
 * </ul>
 */
public final class Procurement {
	/** A post is forgotten after this many scheduler ticks without being renewed. */
	public static final long TTL_TICKS = 20L * 60;
	/** A gatherer's reservation is forgotten after this many ticks without being renewed (it renews it every step). */
	public static final long FETCH_TTL_TICKS = 20L * 30;

	private record Post(Map<Item, Integer> items, long at) {
	}

	private record Fetch(Item item, int count, long at) {
	}

	private static final Map<UUID, Map<String, Post>> POSTS = new HashMap<>();
	private static final Map<UUID, Map<String, Post>> KEEPS = new HashMap<>();
	private static final Map<UUID, Map<UUID, Fetch>> FETCHES = new HashMap<>();

	private Procurement() {
	}

	/** Replaces what {@code requester} wants from the guild's villagers (an empty map clears it). */
	public static synchronized void want(Guild guild, String requester, Map<Item, Integer> items) {
		post(POSTS, guild, requester, items);
	}

	/** Forgets what {@code requester} wants (and what it counts on in the storage, {@link #keep}). */
	public static synchronized void clear(Guild guild, String requester) {
		remove(POSTS, guild, requester);
		remove(KEEPS, guild, requester);
	}

	/** Everything still wanted from the guild's villagers, summed over the requesters (recent posts only). */
	public static synchronized Map<Item, Integer> wanted(Guild guild) {
		return sum(POSTS, guild);
	}

	/** A fetched item reached the storage: lowers the wants until the requesters post again. */
	public static synchronized void delivered(Guild guild, Item item, int count) {
		Map<String, Post> posts = POSTS.get(guild.id);
		if (posts == null) {
			return;
		}
		int left = count;
		for (Post post : posts.values()) {
			if (left <= 0) {
				break;
			}
			Integer wanted = post.items().get(item);
			if (wanted != null) {
				int take = Math.min(wanted, left);
				left -= take;
				if (wanted - take > 0) {
					post.items().put(item, wanted - take);
				} else {
					post.items().remove(item);
				}
			}
		}
	}

	// ------------------------------------------------------------------ items counted on in the storage

	/**
	 * Replaces what {@code requester} counts on in the guild's storage (an empty map clears it): the gatherers never use
	 * these items up as ingredients. Optional: same lifetime as the wants (post it again regularly).
	 */
	public static synchronized void keep(Guild guild, String requester, Map<Item, Integer> items) {
		post(KEEPS, guild, requester, items);
	}

	/** Items the requesters count on in the storage, summed (recent posts only). */
	public static synchronized Map<Item, Integer> kept(Guild guild) {
		return sum(KEEPS, guild);
	}

	// ------------------------------------------------------------------ being fetched

	/**
	 * {@code gatherer} is now fetching {@code count} × {@code item} for the wants (null item or count 0: nothing any
	 * more). Renewed by the gatherer while it works.
	 */
	public static synchronized void fetching(Guild guild, UUID gatherer, @Nullable Item item, int count) {
		if (item == null || count <= 0) {
			release(guild, gatherer);
			return;
		}
		FETCHES.computeIfAbsent(guild.id, id -> new HashMap<>()).put(gatherer, new Fetch(item, count, Scheduler.ticks()));
	}

	/** The gatherer fetches nothing any more. */
	public static synchronized void release(Guild guild, UUID gatherer) {
		Map<UUID, Fetch> fetches = FETCHES.get(guild.id);
		if (fetches != null) {
			fetches.remove(gatherer);
			if (fetches.isEmpty()) {
				FETCHES.remove(guild.id);
			}
		}
	}

	/** Everything the guild's gatherers are fetching right now, summed by item. */
	public static synchronized Map<Item, Integer> fetching(Guild guild) {
		Map<Item, Integer> total = new LinkedHashMap<>();
		Map<UUID, Fetch> fetches = FETCHES.get(guild.id);
		if (fetches == null) {
			return total;
		}
		long now = Scheduler.ticks();
		Iterator<Fetch> it = fetches.values().iterator();
		while (it.hasNext()) {
			Fetch fetch = it.next();
			if (now - fetch.at() > FETCH_TTL_TICKS) {
				it.remove();
				continue;
			}
			total.merge(fetch.item(), fetch.count(), Integer::sum);
		}
		return total;
	}

	/** Amount of {@code item} the other gatherers (all but {@code except}) are fetching. */
	public static synchronized int fetchingByOthers(Guild guild, Item item, @Nullable UUID except) {
		Map<UUID, Fetch> fetches = FETCHES.get(guild.id);
		if (fetches == null) {
			return 0;
		}
		long now = Scheduler.ticks();
		int total = 0;
		for (Map.Entry<UUID, Fetch> entry : fetches.entrySet()) {
			Fetch fetch = entry.getValue();
			if (!entry.getKey().equals(except) && fetch.item() == item && now - fetch.at() <= FETCH_TTL_TICKS) {
				total += fetch.count();
			}
		}
		return total;
	}

	/** Forgets everything (server stop). */
	public static synchronized void reset() {
		POSTS.clear();
		KEEPS.clear();
		FETCHES.clear();
	}

	// ------------------------------------------------------------------ posts

	private static void post(Map<UUID, Map<String, Post>> store, Guild guild, String requester, Map<Item, Integer> items) {
		Map<Item, Integer> copy = new LinkedHashMap<>();
		items.forEach((item, count) -> {
			if (item != null && count != null && count > 0) {
				copy.merge(item, count, Integer::sum);
			}
		});
		if (copy.isEmpty()) {
			remove(store, guild, requester);
			return;
		}
		store.computeIfAbsent(guild.id, id -> new LinkedHashMap<>()).put(requester, new Post(copy, Scheduler.ticks()));
	}

	private static void remove(Map<UUID, Map<String, Post>> store, Guild guild, String requester) {
		Map<String, Post> posts = store.get(guild.id);
		if (posts != null) {
			posts.remove(requester);
			if (posts.isEmpty()) {
				store.remove(guild.id);
			}
		}
	}

	private static Map<Item, Integer> sum(Map<UUID, Map<String, Post>> store, Guild guild) {
		Map<Item, Integer> total = new LinkedHashMap<>();
		Map<String, Post> posts = store.get(guild.id);
		if (posts == null) {
			return total;
		}
		long now = Scheduler.ticks();
		Iterator<Post> it = posts.values().iterator();
		while (it.hasNext()) {
			Post post = it.next();
			if (now - post.at() > TTL_TICKS) {
				it.remove();
				continue;
			}
			post.items().forEach((item, count) -> total.merge(item, count, Integer::sum));
		}
		if (posts.isEmpty()) {
			store.remove(guild.id);
		}
		return total;
	}
}
