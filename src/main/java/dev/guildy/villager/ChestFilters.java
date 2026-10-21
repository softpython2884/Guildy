package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Pure rules of the filter chests (no Minecraft classes: unit tested): which items a {@link ChestFilter} accepts, how a
 * filter is inferred from the items a player put in a chest, how the chat scan classifies a container, and the parts
 * of a filter's name.
 * <p>
 * An id is cut into words at '_' ("polished_diorite_slab" = polished, diorite, slab; "bricks" / "tiles" count as
 * "brick" / "tile"). Words are:
 * <ul>
 * <li>modifiers ({@link #MODIFIERS}: polished, smooth, cut, chiseled, cracked, mossy, stripped, waxed, oxidized...):
 * never a family on their own, ignored when reading the material;</li>
 * <li>shape words ({@link #SHAPES}: slab, stairs, wall, fence, door, log, planks, block, ore, ingot, wool, concrete,
 * glass, bed, dye, seeds...): ignored when reading the material, and possible type families;</li>
 * <li>type words ({@link #TYPES}: sword, helmet, boots, boat, sapling, leaves, spawn egg, music disc...): possible type
 * families, but part of the material ("iron_sword" is not made of the "iron" family: tools and armor keep their own
 * chests);</li>
 * <li>any other word is a material word ("diorite", "oak", "dark" + "oak", "copper", the 16 colours...).</li>
 * </ul>
 * The material of an id = its words without modifiers and shape words ("polished_diorite_slab" → "diorite",
 * "dark_oak_planks" → "dark_oak", "white_stained_glass_pane" → "white"); when nothing is left, its first word that is
 * not a modifier ("brick_slab" → "brick", "glass_pane" → "glass").
 * <ul>
 * <li>{@link ChestFilter.Mode#MATERIAL}: accepts the ids of exactly that material ("oak" refuses "dark_oak_planks",
 * "sandstone" refuses "red_sandstone", "gray" refuses "light_gray_wool");</li>
 * <li>{@link ChestFilter.Mode#TYPE}: accepts the ids holding those words ("wool": every wool, "seeds": every seed);</li>
 * <li>{@link ChestFilter.Mode#ITEMS}: exactly these ids.</li>
 * </ul>
 * Inference ({@link #infer}) from the distinct ids of a chest: one id = that item; else a material family when every id
 * has the same material, else a type family when every id holds the same type words (the longest), else the exact set
 * when there are at most {@code maxItems} ids, else nothing.
 */
public final class ChestFilters {
	/** Modifier words: never a family, ignored when reading the material of an id. */
	public static final Set<String> MODIFIERS = Set.of("polished", "smooth", "cut", "chiseled", "cracked", "mossy", "stripped",
			"waxed", "exposed", "weathered", "oxidized", "glazed", "stained", "cobbled", "infested", "carved", "raw", "dried",
			"packed", "chipped", "damaged");
	/** Shape words: ignored when reading the material of an id, possible type families. */
	public static final Set<String> SHAPES = Set.of("slab", "stairs", "wall", "fence", "gate", "door", "trapdoor", "button",
			"pressure", "plate", "sign", "hanging", "pillar", "brick", "tile", "block", "log", "wood", "planks", "stem", "hyphae",
			"shelf", "mosaic", "ore", "ingot", "nugget", "scrap", "shard", "crystals", "wool", "carpet", "concrete", "powder",
			"terracotta", "glass", "pane", "bed", "banner", "candle", "shulker", "box", "dye", "harness", "bundle", "seeds", "slice",
			"lantern", "chain", "bars", "grate", "bulb", "torch", "chest", "statue", "golem", "lamp", "cushion");
	/** Type words: possible type families, but part of the material (tools, armor, plants, eggs, discs...). */
	public static final Set<String> TYPES = Set.of("sword", "pickaxe", "axe", "shovel", "hoe", "spear", "helmet", "chestplate",
			"leggings", "boots", "horse", "armor", "boat", "raft", "sapling", "leaves", "propagule", "spawn", "egg", "music", "disc",
			"pottery", "sherd", "smithing", "template", "pattern", "bucket", "potion", "arrow", "skull", "head", "coral", "fan",
			"tulip", "mushroom", "fungus", "roots", "nylium", "trim");
	/** Lang key prefix of the family names ("villager.storage.family.oak" = "Chêne"). */
	public static final String FAMILY_LANG = "villager.storage.family.";

	/** What the chat scan proposes for a container. */
	public enum Outcome {
		/** A filter chest (family or exact items). */
		FILTER,
		/** Too many different items for a filter, but mostly one storage category. */
		CATEGORY,
		/** Too many different items, no main category: left as it is. */
		MIXED,
		/** Nothing inside: left as it is. */
		EMPTY
	}

	/** Items seen in a container: an item id, its storage category and its number of stacks. */
	public record Seen(String id, String category, int stacks, List<String> parents) {
		/** An item of a top-level category (no parent category). */
		public Seen(String id, String category, int stacks) {
			this(id, category, stacks, List.of());
		}
	}

	/** Proposal of the scan for one container: a filter, a category (CATEGORY) or nothing. */
	public record Proposal(Outcome outcome, @Nullable ChestFilter filter, String category) {
	}

	/** One part of a filter's name: a lang text (with a number), an item name, or literal text. */
	public record LabelPart(PartKind kind, String value, int number) {
		static LabelPart lang(String key) {
			return new LabelPart(PartKind.LANG, key, 0);
		}

		static LabelPart lang(String key, int number) {
			return new LabelPart(PartKind.LANG, key, number);
		}

		static LabelPart item(String id) {
			return new LabelPart(PartKind.ITEM, id, 0);
		}

		static LabelPart text(String text) {
			return new LabelPart(PartKind.TEXT, text, 0);
		}
	}

	public enum PartKind {
		LANG,
		ITEM,
		TEXT
	}

	private ChestFilters() {
	}

	// ================================================================ words

	/** Namespaced, lower-case item id ("Diorite" → "minecraft:diorite"); "" for blank input. */
	public static String normalize(@Nullable String id) {
		if (id == null) {
			return "";
		}
		String trimmed = id.trim().toLowerCase(Locale.ROOT);
		if (trimmed.isEmpty()) {
			return "";
		}
		return trimmed.indexOf(':') < 0 ? "minecraft:" + trimmed : trimmed;
	}

	/** Family words in their canonical form ("Stone Bricks" → "stone_brick"). */
	public static String normalizeFamily(@Nullable String family) {
		if (family == null || family.isBlank()) {
			return "";
		}
		String path = family.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		int colon = path.indexOf(':');
		if (colon >= 0) {
			path = path.substring(colon + 1);
		}
		return String.join("_", words(path));
	}

	/** Words of an id (namespace dropped, "bricks" → "brick", "tiles" → "tile"). */
	public static List<String> words(String id) {
		String path = id.toLowerCase(Locale.ROOT);
		int colon = path.indexOf(':');
		if (colon >= 0) {
			path = path.substring(colon + 1);
		}
		int slash = path.lastIndexOf('/');
		if (slash >= 0) {
			path = path.substring(slash + 1);
		}
		List<String> words = new ArrayList<>();
		for (String word : path.split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			words.add(switch (word) {
				case "bricks" -> "brick";
				case "tiles" -> "tile";
				default -> word;
			});
		}
		return words;
	}

	/** True for words a type family can be made of (shape and type words). */
	static boolean typeWord(String word) {
		return SHAPES.contains(word) || TYPES.contains(word);
	}

	/**
	 * Material of an id: its words without modifiers and shape words; when nothing is left, its first word that is not
	 * a modifier (its last word if all are modifiers).
	 */
	public static String material(String id) {
		List<String> words = words(id);
		List<String> material = new ArrayList<>();
		for (String word : words) {
			if (!MODIFIERS.contains(word) && !SHAPES.contains(word)) {
				material.add(word);
			}
		}
		if (material.isEmpty()) {
			for (String word : words) {
				if (!MODIFIERS.contains(word)) {
					return word;
				}
			}
			return words.isEmpty() ? "" : words.getLast();
		}
		return String.join("_", material);
	}

	private static boolean containsRun(List<String> words, List<String> run) {
		if (run.isEmpty() || run.size() > words.size()) {
			return false;
		}
		for (int start = 0; start + run.size() <= words.size(); start++) {
			if (words.subList(start, start + run.size()).equals(run)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================ filters

	/** True when the filter accepts the item of this id (components never matter). */
	public static boolean accepts(ChestFilter filter, String itemId) {
		String id = normalize(itemId);
		if (id.isEmpty()) {
			return false;
		}
		return switch (filter.mode) {
			case ITEMS -> filter.items.contains(id);
			case MATERIAL -> !filter.family.isEmpty() && material(id).equals(filter.family);
			case TYPE -> !filter.family.isEmpty() && containsRun(words(id), List.of(filter.family.split("_")));
		};
	}

	/** 0 = exact items (most specific), 1 = material family, 2 = type family. */
	public static int specificity(ChestFilter filter) {
		return switch (filter.mode) {
			case ITEMS -> 0;
			case MATERIAL -> 1;
			case TYPE -> 2;
		};
	}

	/** Identity of a filter: "items:a,b" (sorted ids), "material:diorite" or "type:wool". */
	public static String key(ChestFilter filter) {
		if (filter.mode == ChestFilter.Mode.ITEMS) {
			return "items:" + String.join(",", new TreeSet<>(filter.items));
		}
		return filter.mode.name().toLowerCase(Locale.ROOT) + ":" + filter.family;
	}

	/**
	 * Filter for the distinct item ids of a chest (first seen first: it gives the icon), or null when there is none
	 * (more than {@code maxItems} different items without a common family).
	 */
	public static @Nullable ChestFilter infer(Collection<String> rawIds, int maxItems) {
		List<String> ids = new ArrayList<>(new LinkedHashSet<>(normalizeAll(rawIds)));
		if (ids.isEmpty()) {
			return null;
		}
		String icon = ids.getFirst();
		if (ids.size() == 1) {
			return ChestFilter.items(ids);
		}
		String material = material(icon);
		boolean same = !material.isEmpty();
		for (String id : ids) {
			same &= material(id).equals(material);
		}
		if (same) {
			return ChestFilter.family(ChestFilter.Mode.MATERIAL, material, icon);
		}
		String type = commonType(ids);
		if (type != null) {
			return ChestFilter.family(ChestFilter.Mode.TYPE, type, icon);
		}
		return ids.size() <= Math.max(1, maxItems) ? ChestFilter.items(ids) : null;
	}

	/** Longest run of type words (shape or type words) that every id holds, the last one of the first id on a tie. */
	private static @Nullable String commonType(List<String> ids) {
		List<String> first = words(ids.getFirst());
		for (int length = Math.min(3, first.size()); length >= 1; length--) {
			for (int start = first.size() - length; start >= 0; start--) {
				List<String> run = first.subList(start, start + length);
				boolean typed = true;
				for (String word : run) {
					typed &= typeWord(word);
				}
				if (!typed) {
					continue;
				}
				boolean everywhere = true;
				for (String id : ids) {
					everywhere &= containsRun(words(id), run);
				}
				if (everywhere) {
					return String.join("_", run);
				}
			}
		}
		return null;
	}

	private static List<String> normalizeAll(Collection<String> ids) {
		List<String> normalized = new ArrayList<>(ids.size());
		for (String id : ids) {
			String clean = normalize(id);
			if (!clean.isEmpty()) {
				normalized.add(clean);
			}
		}
		return normalized;
	}

	// ================================================================ chat scan

	/**
	 * What the scan proposes for a container holding {@code seen} (first seen first): nothing inside = EMPTY; a filter
	 * when one fits; else the main storage category when it holds at least {@code categoryPercent} % of the stacks (never
	 * the fallback category); else MIXED. An item also counts for the parent categories of its category
	 * ({@link Seen#parents}, nearest first): the most precise category reaching the share wins (a chest of enchanted
	 * books and paper is proposed as "Livres et enchantements" when neither sub-category reaches it).
	 */
	public static Proposal propose(List<Seen> seen, int maxItems, int categoryPercent, String fallback) {
		if (seen.isEmpty()) {
			return new Proposal(Outcome.EMPTY, null, "");
		}
		List<String> ids = new ArrayList<>(seen.size());
		for (Seen item : seen) {
			ids.add(item.id());
		}
		ChestFilter filter = infer(ids, maxItems);
		if (filter != null) {
			return new Proposal(Outcome.FILTER, filter, "");
		}
		Map<String, Integer> perCategory = new LinkedHashMap<>();
		Map<String, Integer> depth = new LinkedHashMap<>();
		int total = 0;
		for (Seen item : seen) {
			int stacks = Math.max(1, item.stacks());
			List<String> parents = item.parents() == null ? List.of() : item.parents();
			perCategory.merge(item.category(), stacks, Integer::sum);
			depth.merge(item.category(), parents.size(), Math::max);
			for (int i = 0; i < parents.size(); i++) {
				perCategory.merge(parents.get(i), stacks, Integer::sum);
				depth.merge(parents.get(i), parents.size() - 1 - i, Math::max);
			}
			total += stacks;
		}
		String best = null;
		int bestStacks = 0;
		int bestDepth = -1;
		for (Map.Entry<String, Integer> entry : perCategory.entrySet()) {
			int stacks = entry.getValue();
			int level = depth.getOrDefault(entry.getKey(), 0);
			boolean reaches = stacks * 100L >= (long) categoryPercent * total && !entry.getKey().equalsIgnoreCase(fallback);
			boolean bestReaches = best != null && bestStacks * 100L >= (long) categoryPercent * total && !best.equalsIgnoreCase(fallback);
			// The most precise category reaching the share; without one, the biggest (reported as MIXED below).
			boolean better = reaches ? !bestReaches || level > bestDepth || (level == bestDepth && stacks > bestStacks)
					: !bestReaches && stacks > bestStacks;
			if (better) {
				best = entry.getKey();
				bestStacks = stacks;
				bestDepth = level;
			}
		}
		if (best != null && !best.isBlank() && !best.equalsIgnoreCase(fallback) && bestStacks * 100L >= (long) categoryPercent * total) {
			return new Proposal(Outcome.CATEGORY, null, best);
		}
		return new Proposal(Outcome.MIXED, null, "");
	}

	// ================================================================ names

	/**
	 * Parts of a filter's name. Families: the lang text {@code villager.storage.family.<family>} when {@code hasLang}
	 * knows it, else the name of the item of that id when {@code isItem} says it exists ("minecraft:diorite"), else
	 * the words themselves ("Dark oak"). Items: "A", "A et B", "A, B et C", "A, B et N autres".
	 */
	public static List<LabelPart> label(ChestFilter filter, Predicate<String> hasLang, Predicate<String> isItem) {
		List<LabelPart> parts = new ArrayList<>();
		if (filter.mode != ChestFilter.Mode.ITEMS) {
			String key = FAMILY_LANG + filter.family;
			if (hasLang.test(key)) {
				parts.add(LabelPart.lang(key));
			} else if (isItem.test("minecraft:" + filter.family)) {
				parts.add(LabelPart.item("minecraft:" + filter.family));
			} else {
				parts.add(LabelPart.text(pretty(filter.family)));
			}
			return parts;
		}
		List<String> items = filter.items;
		if (items.isEmpty()) {
			return parts;
		}
		parts.add(LabelPart.item(items.getFirst()));
		if (items.size() == 2) {
			parts.add(LabelPart.lang("villager.storage.filter.name.and"));
			parts.add(LabelPart.item(items.get(1)));
		} else if (items.size() == 3) {
			parts.add(LabelPart.lang("villager.storage.filter.name.comma"));
			parts.add(LabelPart.item(items.get(1)));
			parts.add(LabelPart.lang("villager.storage.filter.name.and"));
			parts.add(LabelPart.item(items.get(2)));
		} else if (items.size() > 3) {
			parts.add(LabelPart.lang("villager.storage.filter.name.comma"));
			parts.add(LabelPart.item(items.get(1)));
			parts.add(LabelPart.lang("villager.storage.filter.name.others", items.size() - 2));
		}
		return parts;
	}

	/** "dark_oak" → "Dark oak". */
	public static String pretty(String family) {
		String text = family.replace('_', ' ').trim();
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
