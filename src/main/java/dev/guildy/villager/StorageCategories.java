package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

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
 * <p>
 * Categories form a tree plus a set of transversal categories:
 * <ul>
 * <li>top-level categories compete with their own rules (as above): an item belongs to exactly one of them;</li>
 * <li>a sub-category (parent = another category) only takes items of its parent: once the parent is chosen, the rules of
 * its sub-categories refine the choice (most precise rule again), level by level;</li>
 * <li>a transversal category (parent "*") takes its items from every category: its rules are tested on every item, and
 * the item goes there first when such a chest exists.</li>
 * </ul>
 * {@link #resolve} gives the routing chain of an item, most specific first: the transversal categories it matches, then
 * its deepest category, its parent... up to its top-level category. A chest of an earlier category of the chain is
 * preferred; without one (or full), the next category of the chain, then the fallback.
 */
public final class StorageCategories {
	/** Item properties usable as "@name" rules ("magic": Guildy scrolls, grimoires and incantation scrolls). */
	public static final List<String> PROPERTIES = List.of("food", "armor", "tool", "weapon", "potion", "enchanted_book",
			"enchanted", "music_disc", "banner_pattern", "smithing_template", "spawn_egg", "dye", "block", "magic");
	/** Prefix of creative tab properties ("tab:minecraft:combat"). */
	public static final String TAB = "tab:";
	/** Parent value of a transversal category. */
	public static final String TRANSVERSAL = StorageCategory.TRANSVERSAL;
	/** Deepest sub-category level followed (a top-level category is level 0). */
	public static final int MAX_DEPTH = 4;

	private static final int TOP = -1;
	private static final int ACROSS = -2;

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

	/**
	 * Where an item belongs: {@code chain} = the transversal categories it matches (most precise rule first), then its
	 * deepest category, that category's parent... its top-level category (empty when only the fallback applies);
	 * {@code home} = its deepest category of the tree (the fallback when none) and the rule that chose it.
	 */
	public record Resolution(List<String> chain, Match home) {
		public String category() {
			return home.category();
		}
	}

	private record Hit(int category, int order, String rule) {
	}

	/** A rule hit with what makes it precise (kind, tag size, pattern length). */
	private record Found(Hit hit, Kind kind, long tagSize, int literals) {
	}

	private record PatternRule(Pattern regex, boolean fullId, int literals, Hit hit) {
		boolean matches(String id, String path) {
			return regex.matcher(fullId ? id : path).matches();
		}
	}

	/** Rules competing together: the top-level categories, the sub-categories of one parent, or one transversal category. */
	private static final class Scope {
		final Map<String, Hit> ids = new HashMap<>();
		final Map<String, Hit> tags = new HashMap<>();
		final Map<String, Hit> properties = new HashMap<>();
		final Map<String, Hit> tabs = new HashMap<>();
		final List<PatternRule> patterns = new ArrayList<>();

		void add(String rule, Hit hit) {
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

		void seal() {
			patterns.sort(Comparator.comparingInt((PatternRule p) -> -p.literals()).thenComparingInt(p -> p.hit().order()));
		}

		/** The most precise rule of this scope matching the item, or null. */
		@Nullable Found best(Facts facts, String id, String path) {
			Hit hit = ids.get(id);
			if (hit != null) {
				return new Found(hit, Kind.ID, 0, 0);
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
				return new Found(best, Kind.TAG, bestSize, 0);
			}
			for (PatternRule pattern : patterns) {
				if (pattern.matches(id, path)) {
					return new Found(pattern.hit(), Kind.PATTERN, 0, pattern.literals());
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
				return new Found(property, Kind.PROPERTY, 0, 0);
			}
			return tab == null ? null : new Found(tab, Kind.TAB, 0, 0);
		}
	}

	private static final Comparator<Found> PRECISION = Comparator.<Found>comparingInt(f -> f.kind().ordinal())
			.thenComparingLong(Found::tagSize).thenComparingInt(f -> -f.literals()).thenComparingInt(f -> f.hit().order());

	private final List<String> names = new ArrayList<>();
	private final Map<String, Integer> byLowerName = new HashMap<>();
	private final String fallback;
	/** Parent index per category: {@link #TOP}, {@link #ACROSS} or the index of the parent. */
	private final List<Integer> parentIndex = new ArrayList<>();
	private final Scope top = new Scope();
	private final Map<Integer, Scope> childScopes = new HashMap<>();
	/** One scope per transversal category (each is matched on its own: an item may belong to several of them). */
	private final List<Scope> transversals = new ArrayList<>();

	/**
	 * Categories without sub-categories nor transversal categories.
	 *
	 * @param rules    category name → rules, in priority order for ties (a category named like the fallback is ignored)
	 * @param fallback category of the items that match no rule
	 */
	public StorageCategories(Map<String, List<String>> rules, String fallback) {
		this(rules, Map.of(), fallback);
	}

	/**
	 * @param rules    category name → rules, in priority order for ties (a category named like the fallback is ignored)
	 * @param parents  category name → parent ("" or missing = top-level, "*" = transversal, else a category name; an
	 *                 unknown parent, a transversal parent or a cycle makes the category top-level)
	 * @param fallback category of the items that match no rule
	 */
	public StorageCategories(Map<String, List<String>> rules, Map<String, String> parents, String fallback) {
		this.fallback = fallback == null || fallback.isBlank() ? "Divers" : fallback.trim();
		List<List<String>> ruleLists = new ArrayList<>();
		List<String> parentNames = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : rules.entrySet()) {
			String name = entry.getKey() == null ? "" : entry.getKey().trim();
			if (name.isEmpty() || entry.getValue() == null || name.equalsIgnoreCase(this.fallback) || indexOf(name) >= 0) {
				continue;
			}
			byLowerName.put(name.toLowerCase(Locale.ROOT), names.size());
			names.add(name);
			ruleLists.add(entry.getValue());
			String parent = parentOf(parents, entry.getKey());
			parentNames.add(parent == null ? "" : parent.trim());
		}
		for (int i = 0; i < names.size(); i++) {
			String parent = parentNames.get(i);
			if (parent.isEmpty()) {
				parentIndex.add(TOP);
			} else if (parent.equals(TRANSVERSAL)) {
				parentIndex.add(ACROSS);
			} else {
				int index = indexOf(parent);
				boolean usable = index >= 0 && index != i && !TRANSVERSAL.equals(parentNames.get(index));
				parentIndex.add(usable ? index : TOP);
			}
		}
		// A cycle (A under B under A) or a chain deeper than MAX_DEPTH: the category becomes top-level.
		for (int i = 0; i < names.size(); i++) {
			int steps = 0;
			int current = i;
			while (parentIndex.get(current) >= 0 && steps <= MAX_DEPTH + 1) {
				current = parentIndex.get(current);
				steps++;
			}
			if (parentIndex.get(current) >= 0 || steps > MAX_DEPTH) {
				parentIndex.set(i, TOP);
			}
		}
		int order = 0;
		for (int i = 0; i < names.size(); i++) {
			Scope scope;
			int parent = parentIndex.get(i);
			if (parent == TOP) {
				scope = top;
			} else if (parent == ACROSS) {
				scope = new Scope();
				transversals.add(scope);
			} else {
				scope = childScopes.computeIfAbsent(parent, p -> new Scope());
			}
			for (String raw : ruleLists.get(i)) {
				if (raw != null && !raw.isBlank()) {
					scope.add(raw.trim(), new Hit(i, order++, raw.trim()));
				}
			}
		}
		top.seal();
		childScopes.values().forEach(Scope::seal);
		transversals.forEach(Scope::seal);
	}

	private static @Nullable String parentOf(Map<String, String> parents, String name) {
		String parent = parents.get(name);
		if (parent == null) {
			for (Map.Entry<String, String> entry : parents.entrySet()) {
				if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(name.trim())) {
					return entry.getValue();
				}
			}
		}
		return parent;
	}

	// ------------------------------------------------------------------ matching

	/** Category of an item given its id and the ids of its tags (no properties: only ids, tags and patterns apply). */
	public String categoryOf(String itemId, Collection<String> tagIds) {
		return match(Facts.of(itemId, tagIds, Set.of())).category();
	}

	/** Deepest category of the tree holding the item (the fallback when none). */
	public String categoryOf(Facts facts) {
		return match(facts).category();
	}

	/** Deepest category of the tree holding the item and the rule that chose it. */
	public Match match(Facts facts) {
		return resolve(facts, facts).home();
	}

	/** Routing chain of the item ({@link Resolution#chain}). */
	public List<String> chain(Facts facts) {
		return resolve(facts, facts).chain();
	}

	public Resolution resolve(Facts facts) {
		return resolve(facts, facts);
	}

	/**
	 * Where an item belongs.
	 *
	 * @param root   facts choosing its top-level category
	 * @param detail facts refining it (sub-categories) and matched by the transversal categories; the same facts for
	 *               ordinary items (Guildy magic items: an enchanted book chooses the top-level category, the magic item
	 *               itself the rest)
	 */
	public Resolution resolve(Facts root, Facts detail) {
		String rootId = normalize(root.id());
		String detailId = normalize(detail.id());
		String detailPath = detailId.substring(detailId.indexOf(':') + 1);
		Found chosen = top.best(root, rootId, rootId.substring(rootId.indexOf(':') + 1));
		List<String> hierarchy = new ArrayList<>(2);
		Match home;
		if (chosen == null) {
			home = new Match(fallback, "", Kind.FALLBACK);
		} else {
			List<Integer> path = new ArrayList<>(2);
			path.add(chosen.hit().category());
			for (int level = 0; level < MAX_DEPTH; level++) {
				Scope children = childScopes.get(path.getLast());
				Found refined = children == null ? null : children.best(detail, detailId, detailPath);
				if (refined == null) {
					break;
				}
				chosen = refined;
				path.add(refined.hit().category());
			}
			home = new Match(names.get(chosen.hit().category()), chosen.hit().rule(), chosen.kind());
			for (int i = path.size() - 1; i >= 0; i--) {
				hierarchy.add(names.get(path.get(i)));
			}
		}
		if (transversals.isEmpty()) {
			return new Resolution(List.copyOf(hierarchy), home);
		}
		List<Found> across = new ArrayList<>(1);
		for (Scope scope : transversals) {
			Found found = scope.best(detail, detailId, detailPath);
			if (found != null) {
				across.add(found);
			}
		}
		if (across.isEmpty()) {
			return new Resolution(List.copyOf(hierarchy), home);
		}
		across.sort(PRECISION);
		List<String> chain = new ArrayList<>(across.size() + hierarchy.size());
		for (Found found : across) {
			chain.add(names.get(found.hit().category()));
		}
		chain.addAll(hierarchy);
		return new Resolution(List.copyOf(chain), home);
	}

	private static Hit first(Hit current, Hit candidate) {
		return candidate != null && (current == null || candidate.order() < current.order()) ? candidate : current;
	}

	// ------------------------------------------------------------------ names and tree

	public String fallback() {
		return fallback;
	}

	/** Category names in config order (without the fallback): top-level, sub- and transversal categories. */
	public List<String> names() {
		return List.copyOf(names);
	}

	/** Top-level categories in config order. */
	public List<String> roots() {
		List<String> roots = new ArrayList<>();
		for (int i = 0; i < names.size(); i++) {
			if (parentIndex.get(i) == TOP) {
				roots.add(names.get(i));
			}
		}
		return roots;
	}

	/** Transversal categories in config order. */
	public List<String> transversals() {
		List<String> list = new ArrayList<>();
		for (int i = 0; i < names.size(); i++) {
			if (parentIndex.get(i) == ACROSS) {
				list.add(names.get(i));
			}
		}
		return list;
	}

	/** Menu order: each top-level category followed by its sub-categories (depth first), then the transversal ones. */
	public List<String> menuOrder() {
		List<String> order = new ArrayList<>(names.size());
		for (int i = 0; i < names.size(); i++) {
			if (parentIndex.get(i) == TOP) {
				addTree(i, order);
			}
		}
		order.addAll(transversals());
		return order;
	}

	private void addTree(int index, List<String> order) {
		order.add(names.get(index));
		for (int i = 0; i < names.size(); i++) {
			if (parentIndex.get(i) == index) {
				addTree(i, order);
			}
		}
	}

	/** Direct sub-categories of a category, in config order. */
	public List<String> children(String name) {
		int index = indexOf(name);
		List<String> children = new ArrayList<>();
		for (int i = 0; index >= 0 && i < names.size(); i++) {
			if (parentIndex.get(i) == index) {
				children.add(names.get(i));
			}
		}
		return children;
	}

	/** Parent of a sub-category, null for a top-level, transversal or unknown category. */
	public @Nullable String parent(String name) {
		int index = indexOf(name);
		return index < 0 || parentIndex.get(index) < 0 ? null : names.get(parentIndex.get(index));
	}

	public boolean isTransversal(String name) {
		int index = indexOf(name);
		return index >= 0 && parentIndex.get(index) == ACROSS;
	}

	/** Sub-category level: 0 for a top-level or transversal category, 1 for its sub-categories...; -1 if unknown. */
	public int depth(String name) {
		int index = indexOf(name);
		if (index < 0) {
			return -1;
		}
		int depth = 0;
		while (parentIndex.get(index) >= 0) {
			index = parentIndex.get(index);
			depth++;
		}
		return depth;
	}

	/** The category, its parent... its top-level category (just the category for a transversal one; empty if unknown). */
	public List<String> ancestors(String name) {
		List<String> list = new ArrayList<>();
		int index = indexOf(name);
		while (index >= 0) {
			list.add(names.get(index));
			index = parentIndex.get(index);
		}
		return list;
	}

	/** Top-level category of a category (itself for a top-level or transversal one), null if unknown. */
	public @Nullable String root(String name) {
		List<String> ancestors = ancestors(name);
		return ancestors.isEmpty() ? null : ancestors.getLast();
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
		if (name == null) {
			return -1;
		}
		Integer index = byLowerName.get(name.trim().toLowerCase(Locale.ROOT));
		return index == null ? -1 : index;
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
