package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Objectifs prioritaires": a short list of wanted items set on a villager ({@link VillagerRecord#wishlist}) and/or on a
 * work zone ({@link WorkZone#wishlist}). Workers favour these items and pick them up even a little outside their zone
 * (never in another guild's territory). Stored as item ids ("minecraft:diorite"); the two lists are merged, the
 * villager's own entries first.
 */
public final class Wishlist {
	/** Maximum entries per list (one menu row). */
	public static final int MAX = 9;

	private Wishlist() {
	}

	/** Wanted items of a villager working in this zone (zone may be null), villager's own entries first. */
	public static Set<Item> items(VillagerRecord record, @Nullable WorkZone zone) {
		Set<Item> items = new LinkedHashSet<>();
		addAll(items, record.wishlist);
		if (zone != null) {
			addAll(items, zone.wishlist);
		}
		return items;
	}

	public static boolean isEmpty(VillagerRecord record, @Nullable WorkZone zone) {
		return record.wishlist.isEmpty() && (zone == null || zone.wishlist.isEmpty());
	}

	public static boolean wanted(VillagerRecord record, @Nullable WorkZone zone, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String id = id(stack.getItem());
		return record.wishlist.contains(id) || (zone != null && zone.wishlist.contains(id));
	}

	public static String id(Item item) {
		return ItemUtil.id(item);
	}

	/**
	 * Adds an item to a list (no duplicate, at most {@link #MAX}).
	 *
	 * @return false when the list is full, the item already listed or air
	 */
	public static boolean add(List<String> list, Item item) {
		if (item == Items.AIR) {
			return false;
		}
		String id = id(item);
		if (list.contains(id) || list.size() >= MAX) {
			return false;
		}
		list.add(id);
		return true;
	}

	/** Drops unknown ids and duplicates, keeps at most {@link #MAX} entries (after deserialization). */
	static List<String> sanitize(@Nullable List<String> list) {
		List<String> clean = new ArrayList<>();
		if (list == null) {
			return clean;
		}
		for (String id : list) {
			Item item = id == null ? null : ItemUtil.item(id);
			if (item != null && item != Items.AIR && !clean.contains(id(item)) && clean.size() < MAX) {
				clean.add(id(item));
			}
		}
		return clean;
	}

	private static void addAll(Set<Item> items, List<String> ids) {
		for (String id : ids) {
			Item item = ItemUtil.item(id);
			if (item != null && item != Items.AIR) {
				items.add(item);
			}
		}
	}
}
