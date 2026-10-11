package dev.guildy.villager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure category matching for the storage system (no Minecraft classes: unit tested).
 * A category lists item ids ("minecraft:diamond", namespace optional) and item tags ("#minecraft:logs").
 * The first category (config order) that matches wins; otherwise the fallback category is returned.
 */
public final class StorageCategories {
	private final List<Category> categories;
	private final String fallback;

	private record Category(String name, Set<String> items, Set<String> tags) {
	}

	public StorageCategories(Map<String, List<String>> config, String fallback) {
		this.fallback = fallback;
		this.categories = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : config.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			Set<String> items = new HashSet<>();
			Set<String> tags = new HashSet<>();
			for (String value : entry.getValue()) {
				if (value == null || value.isBlank()) {
					continue;
				}
				String trimmed = value.trim();
				if (trimmed.startsWith("#")) {
					tags.add(normalize(trimmed.substring(1)));
				} else {
					items.add(normalize(trimmed));
				}
			}
			categories.add(new Category(entry.getKey(), items, tags));
		}
	}

	/** Category of an item given its id and the ids of all its item tags. */
	public String categoryOf(String itemId, Collection<String> tagIds) {
		String id = normalize(itemId);
		Set<String> itemTags = new HashSet<>();
		for (String tag : tagIds) {
			itemTags.add(normalize(tag));
		}
		for (Category category : categories) {
			if (category.items.contains(id)) {
				return category.name;
			}
			for (String tag : category.tags) {
				if (itemTags.contains(tag)) {
					return category.name;
				}
			}
		}
		return fallback;
	}

	public String fallback() {
		return fallback;
	}

	/** Category names in config order (without the fallback). */
	public List<String> names() {
		List<String> names = new ArrayList<>();
		for (Category category : categories) {
			names.add(category.name);
		}
		return names;
	}

	/** "Diamond" → "minecraft:diamond", lower case, trimmed. */
	public static String normalize(String id) {
		String value = id.trim().toLowerCase(Locale.ROOT);
		if (value.startsWith("#")) {
			value = value.substring(1);
		}
		return value.indexOf(':') < 0 ? "minecraft:" + value : value;
	}
}
