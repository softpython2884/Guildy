package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a filter chest ({@link StorageChest.Role#FILTER}) accepts (persisted with the chest; no Minecraft classes: unit
 * tested through {@link ChestFilters}): an exact set of item ids, a material family ("diorite": every item whose id is
 * made of that material and shape words) or a type family ("wool": every item whose id holds that word).
 */
public class ChestFilter {
	public enum Mode {
		/** Exactly the items of {@link #items}. */
		ITEMS,
		/** Items whose material ({@link ChestFilters#material}) is {@link #family}. */
		MATERIAL,
		/** Items whose id holds the words of {@link #family} ("wool", "shulker_box"). */
		TYPE
	}

	public Mode mode = Mode.ITEMS;
	/** Material or type words, lower case, joined by '_' (MATERIAL / TYPE). */
	public String family = "";
	/** Accepted item ids, namespaced (ITEMS). */
	public List<String> items = new ArrayList<>();
	/** Item shown for the filter in the menus (the first item seen), "" if none. */
	public String icon = "";

	/** Acceptance per item id, computed once (not saved). */
	private transient @Nullable Map<String, Boolean> cache;

	public ChestFilter() {
	}

	public static ChestFilter items(List<String> ids) {
		ChestFilter filter = new ChestFilter();
		filter.mode = Mode.ITEMS;
		for (String id : ids) {
			String normalized = ChestFilters.normalize(id);
			if (!normalized.isEmpty() && !filter.items.contains(normalized)) {
				filter.items.add(normalized);
			}
		}
		filter.icon = filter.items.isEmpty() ? "" : filter.items.getFirst();
		return filter;
	}

	public static ChestFilter family(Mode mode, String family, String icon) {
		ChestFilter filter = new ChestFilter();
		filter.mode = mode == Mode.ITEMS ? Mode.MATERIAL : mode;
		filter.family = family;
		filter.icon = icon == null ? "" : ChestFilters.normalize(icon);
		return filter;
	}

	/** True when an item of this id belongs in the chest. */
	public boolean accepts(String itemId) {
		Map<String, Boolean> known = cache;
		if (known == null) {
			known = new HashMap<>();
			cache = known;
		}
		Boolean accepted = known.get(itemId);
		if (accepted == null) {
			accepted = ChestFilters.accepts(this, itemId);
			if (known.size() < 4096) {
				known.put(itemId, accepted);
			}
		}
		return accepted;
	}

	/** Same filter (same mode and family / items, whatever the order of the items). */
	public String key() {
		return ChestFilters.key(this);
	}

	/** Fixes nulls after loading; false when the filter accepts nothing (no item, no family). */
	boolean sanitize() {
		if (mode == null) {
			mode = Mode.ITEMS;
		}
		if (family == null) {
			family = "";
		}
		family = ChestFilters.normalizeFamily(family);
		if (items == null) {
			items = new ArrayList<>();
		}
		List<String> clean = new ArrayList<>();
		for (String id : items) {
			String normalized = id == null ? "" : ChestFilters.normalize(id);
			if (!normalized.isEmpty() && !clean.contains(normalized)) {
				clean.add(normalized);
			}
		}
		items = clean;
		if (icon == null) {
			icon = "";
		}
		cache = null;
		return mode == Mode.ITEMS ? !items.isEmpty() : !family.isEmpty();
	}

	@Override
	public String toString() {
		return key();
	}
}
