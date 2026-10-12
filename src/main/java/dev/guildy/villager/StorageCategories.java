package dev.guildy.villager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure category matching for the storage system (no Minecraft classes: unit tested).
 * <p>
 * Each category is a list of rules (namespace optional, case ignored):
 * <ul>
 * <li>{@code minecraft:diamond}: an item id;</li>
 * <li>{@code #minecraft:logs}: an item tag;</li>
 * <li>{@code *_ore}, {@code minecraft:stripped_*}: an id pattern ({@code *} = any text; a pattern without namespace
 * applies to every namespace);</li>
 * <li>{@code @food}, {@code @armor}...: an item property ({@link #PROPERTIES}, computed by {@link ItemClassifier});</li>
 * <li>{@code @tab:building_blocks}: an item shown in this creative inventory tab.</li>
 * </ul>
 * The most precise rule wins: an item id, then a tag (the smallest tag first), then a pattern (the one with the most
 * literal characters first), then a property, then a creative tab. On a tie, the first category in config order wins.
 * Nothing matches: the fallback category.
 */
public final class StorageCategories {
	/** Item properties usable as "@name" rules. */
	public static final List<String> PROPERTIES = List.of("food", "armor", "tool", "weapon", "potion", "enchanted_book",
			"enchanted", "music_disc", "banner_pattern", "smithing_template", "spawn_egg", "dye", "block");
	/** Prefix of creative tab properties ("tab:minecraft:combat"). */
	public static final String TAB = "tab:";

	/** Kind of rule, from the most precise to the least precise. */
	public enum Kind {
		ID,
		TAG,
		PATTERN,
		PROPERTY,
		TAB,
		/** No rule matched. */
		FALLBACK
	}

	/**
	 * What is known about an item: its id, its tags (id → number of items in the tag, 0 when unknown) and its
	 * properties ("food", "tab:minecraft:combat"...).
	 */
	public record Facts(String id, Map<String, Integer> tags, Set<String> properties) {
		/** Facts with tags of unknown size. */
		public static Facts of(String id, Collection<String> tags, Collection<String> properties) {
			Map<String, Integer> sized = new LinkedHashMap<>();
			for (String tag : tags) {
				sized.put(tag, 0);
			}
			return new Facts(id, sized, Set.copyOf(properties));
		}
	}

	/** The category of an item and the rule that chose it. */
	public record Match(String category, String rule, Kind kind) {
	}

	private record Hit(int category, int order, String rule) {
	}

	private record PatternRule(Pattern regex, boolean fullId, int literals, Hit hit) {
		boolean matches(String id, String path) {
			return regex.matcher(fullId ? id : path).matches();
		}
	}

	private final List<String> names = new ArrayList<>();
	private final String fallback;
	private final Map<String, Hit> ids = new HashMap<>();
	private final Map<String, Hit> tags = new HashMap<>();
	private final Map<String, Hit> properties = new HashMap<>();
	private final Map<String, Hit> tabs = new HashMap<>();
	private final List<PatternRule> patterns = new ArrayList<>();

	/**
	 * @param rules    category name → rules, in priority order for ties (a category named like the fallback is ignored)
	 * @param fallback category of the items that match no rule
	 */
	public StorageCategories(Map<String, List<String>> rules, String fallback) {
		this.fallback = fallback == null || fallback.isBlank() ? "Divers" : fallback.trim();
		int order = 0;
		for (Map.Entry<String, List<String>> entry : rules.entrySet()) {
			String name = entry.getKey() == null ? "" : entry.getKey().trim();
			if (name.isEmpty() || entry.getValue() == null || name.equalsIgnoreCase(this.fallback) || indexOf(name) >= 0) {
				continue;
			}
			int category = names.size();
			names.add(name);
			for (String raw : entry.getValue()) {
				if (raw == null || raw.isBlank()) {
					continue;
				}
				String rule = raw.trim();
				Hit hit = new Hit(category, order++, rule);
				if (rule.startsWith("#")) {
					tags.putIfAbsent(normalize(rule.substring(1)), hit);
				} else if (rule.startsWith("@")) {
					String property = property(rule);
					(property.startsWith(TAB) ? tabs : properties).putIfAbsent(property, hit);
				} else if (rule.indexOf('*') >= 0) {
					patterns.add(pattern(rule, hit));
				} else {
					ids.putIfAbsent(normalize(rule), hit);
				}
			}
		}
		patterns.sort(Comparator.comparingInt((PatternRule p) -> -p.literals()).thenComparingInt(p -> p.hit().order()));
	}

	/** Category of an item given its id and the ids of its tags (no properties: only ids, tags and patterns apply). */
	public String categoryOf(String itemId, Collection<String> tagIds) {
		return match(Facts.of(itemId, tagIds, Set.of())).category();
	}

	public String categoryOf(Facts facts) {
		return match(facts).category();
	}

	/** Category of an item and the rule that chose it. */
	public Match match(Facts facts) {
		String id = normalize(facts.id());
		Hit hit = ids.get(id);
		if (hit != null) {
			return match(hit, Kind.ID);
		}
		Hit best = null;
		long bestSize = Long.MAX_VALUE;
		for (Map.Entry<String, Integer> tag : facts.tags().entrySet()) {
			Hit candidate = tags.get(normalize(tag.getKey()));
			if (candidate == null) {
				continue;
			}
			long size = tag.getValue() == null || tag.getValue() <= 0 ? Integer.MAX_VALUE : tag.getValue();
			if (best == null || size < bestSize || (size == bestSize && candidate.order() < best.order())) {
				best = candidate;
				bestSize = size;
			}
		}
		if (best != null) {
			return match(best, Kind.TAG);
		}
		String path = id.substring(id.indexOf(':') + 1);
		for (PatternRule pattern : patterns) {
			if (pattern.matches(id, path)) {
				return match(pattern.hit(), Kind.PATTERN);
			}
		}
		Hit property = null;
		Hit tab = null;
		for (String raw : facts.properties()) {
			String name = property(raw);
			if (name.startsWith(TAB)) {
				tab = first(tab, tabs.get(name));
			} else {
				property = first(property, properties.get(name));
			}
		}
		if (property != null) {
			return match(property, Kind.PROPERTY);
		}
		if (tab != null) {
			return match(tab, Kind.TAB);
		}
		return new Match(fallback, "", Kind.FALLBACK);
	}

	private static Hit first(Hit current, Hit candidate) {
		return candidate != null && (current == null || candidate.order() < current.order()) ? candidate : current;
	}

	private Match match(Hit hit, Kind kind) {
		return new Match(names.get(hit.category()), hit.rule(), kind);
	}

	public String fallback() {
		return fallback;
	}

	/** Category names in config order (without the fallback). */
	public List<String> names() {
		return List.copyOf(names);
	}

	/** The configured spelling of a category name (case ignored), the fallback name for the fallback, else null. */
	public String canonical(String name) {
		if (name == null) {
			return null;
		}
		String trimmed = name.trim();
		if (trimmed.equalsIgnoreCase(fallback)) {
			return fallback;
		}
		int index = indexOf(trimmed);
		return index < 0 ? null : names.get(index);
	}

	/** Position of a category in config order (case ignored), -1 if unknown (the fallback included). */
	public int indexOf(String name) {
		for (int i = 0; i < names.size(); i++) {
			if (names.get(i).equalsIgnoreCase(name)) {
				return i;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------ rules

	/** "Diamond" → "minecraft:diamond", lower case, trimmed, without a leading '#'. */
	public static String normalize(String id) {
		String value = id.trim().toLowerCase(Locale.ROOT);
		if (value.startsWith("#")) {
			value = value.substring(1);
		}
		return value.indexOf(':') < 0 ? "minecraft:" + value : value;
	}

	/** "@Tab:Combat" → "tab:minecraft:combat", "@FOOD" → "food". */
	static String property(String rule) {
		String value = rule.trim().toLowerCase(Locale.ROOT);
		if (value.startsWith("@")) {
			value = value.substring(1);
		}
		return value.startsWith(TAB) ? TAB + normalize(value.substring(TAB.length())) : value;
	}

	/** "*_ore" → regex ".*\Q_ore\E" on the id path (4 literal characters); with a namespace: on the whole id. */
	private static PatternRule pattern(String rule, Hit hit) {
		String value = rule.toLowerCase(Locale.ROOT);
		boolean fullId = value.indexOf(':') >= 0;
		StringBuilder regex = new StringBuilder();
		int literals = 0;
		String[] parts = value.split("\\*", -1);
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				regex.append(".*");
			}
			if (!parts[i].isEmpty()) {
				regex.append(Pattern.quote(parts[i]));
				literals += parts[i].length();
			}
		}
		return new PatternRule(Pattern.compile(regex.toString()), fullId, literals, hit);
	}

	/**
	 * Problems of a rule list (unknown property, empty pattern...), for the config warnings; empty when every rule is
	 * usable.
	 */
	public static List<String> problems(Collection<String> rules) {
		List<String> problems = new ArrayList<>();
		for (String raw : rules) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String rule = raw.trim();
			if (rule.startsWith("@")) {
				String property = property(rule);
				if (property.startsWith(TAB) ? property.length() <= TAB.length() + "minecraft:".length() : !PROPERTIES.contains(property)) {
					problems.add("unknown property '" + rule + "'");
				}
			} else if (rule.replace("*", "").replace("#", "").isBlank()) {
				problems.add("empty rule '" + rule + "'");
			} else if (rule.indexOf(' ') >= 0) {
				problems.add("space in rule '" + rule + "'");
			}
		}
		return problems;
	}
}
