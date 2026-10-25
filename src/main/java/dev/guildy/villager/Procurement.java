package dev.guildy.villager;

import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Materials the villagers of a guild should fetch (craft, smelt or gather), by requester ("build:&lt;project id&gt;"...).
 * Not saved: each requester posts its current shortfall again regularly, and a post older than {@link #TTL_TICKS} is
 * forgotten (a requester that disappears stops asking by itself).
 */
public final class Procurement {
	/** A post is forgotten after this many scheduler ticks without being renewed. */
	public static final long TTL_TICKS = 20L * 60;

	private record Post(Map<Item, Integer> items, long at) {
	}

	private static final Map<UUID, Map<String, Post>> POSTS = new HashMap<>();

	private Procurement() {
	}

	/** Replaces what {@code requester} wants from the guild's villagers (an empty map clears it). */
	public static synchronized void want(Guild guild, String requester, Map<Item, Integer> items) {
		Map<Item, Integer> copy = new LinkedHashMap<>();
		items.forEach((item, count) -> {
			if (item != null && count != null && count > 0) {
				copy.put(item, count);
			}
		});
		if (copy.isEmpty()) {
			clear(guild, requester);
			return;
		}
		POSTS.computeIfAbsent(guild.id, id -> new LinkedHashMap<>()).put(requester, new Post(copy, Scheduler.ticks()));
	}

	public static synchronized void clear(Guild guild, String requester) {
		Map<String, Post> posts = POSTS.get(guild.id);
		if (posts != null) {
			posts.remove(requester);
			if (posts.isEmpty()) {
				POSTS.remove(guild.id);
			}
		}
	}

	/** Everything still wanted from the guild's villagers, summed over the requesters (recent posts only). */
	public static synchronized Map<Item, Integer> wanted(Guild guild) {
		Map<Item, Integer> total = new LinkedHashMap<>();
		Map<String, Post> posts = POSTS.get(guild.id);
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
		return total;
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

	/** Forgets everything (server stop). */
	public static synchronized void reset() {
		POSTS.clear();
	}
}
