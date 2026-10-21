package dev.guildy.alchemy;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The brewing steps the alchemist knows (pure, unit tested): one step turns the bottles of a product into another product
 * with one reagent, like one brew of a vanilla brewing stand (up to three bottles, one reagent). Built from the
 * {@code recipes} lines of alchemy.json5 ("awkward + glistering_melon_slice -> healing"), the bottle changes (any drinkable
 * potion + gunpowder -> its splash potion, splash + dragon's breath -> lingering, like vanilla) and one step per elixir
 * (its base + its reagent). Plans the chains from a water bottle to a product and chooses what to brew from the stock
 * targets.
 */
public final class BrewBook {
	/** Longest chain the alchemist plans (water, awkward, effect, upgrade, splash, lingering: 5 brews). */
	public static final int MAX_STEPS = 6;

	/** One brew: the bottles of {@code from} become {@code to} with one {@code reagent} (namespaced item id). */
	public record Step(String from, String reagent, String to) {
		public String text() {
			return from + " + " + reagent + " -> " + to;
		}
	}

	/** A batch to brew: the product and its steps from the bottle it starts from ({@link #start()}). */
	public record Plan(String product, List<Step> steps) {
		public String start() {
			return steps.isEmpty() ? product : steps.getFirst().from();
		}
	}

	/** What {@link #choose} found. */
	public enum Verdict {
		/** A product below its target with a brewable chain. */
		BREW,
		/** Every product reached its target (or none has one). */
		STOCK_FULL,
		/** Products are missing but none of their chains can be brewed with the supplies at hand. */
		MISSING
	}

	/** Result of {@link #choose}: the plan (BREW), or the most wanted product that cannot be brewed (MISSING). */
	public record Choice(Verdict verdict, @Nullable Plan plan, @Nullable String wanted) {
	}

	private final List<Step> steps;
	private final Map<String, List<Step>> byOutput = new HashMap<>();
	private final Map<String, List<List<Step>>> chainCache = new HashMap<>();

	private BrewBook(List<Step> steps) {
		this.steps = List.copyOf(steps);
		for (Step step : this.steps) {
			byOutput.computeIfAbsent(step.to(), k -> new ArrayList<>()).add(step);
		}
	}

	public static BrewBook of(Collection<Step> steps) {
		// Duplicates (same input, reagent and output) are kept once.
		return new BrewBook(new ArrayList<>(new LinkedHashSet<>(steps)));
	}

	/**
	 * The book of a config: recipe lines, bottle changes for every drinkable potion that appears in them (when the reagents
	 * are given), then the elixir steps.
	 */
	public static BrewBook build(List<Step> recipes, @Nullable String splashReagent, @Nullable String lingeringReagent, List<Step> elixirs) {
		List<Step> all = new ArrayList<>(recipes);
		Set<String> drinkable = new LinkedHashSet<>();
		for (Step step : recipes) {
			for (String product : List.of(step.from(), step.to())) {
				PotionKey key = PotionKey.parse(product);
				if (key != null && key.form() == PotionKey.Form.POTION) {
					drinkable.add(key.text());
				}
			}
		}
		for (String potion : drinkable) {
			PotionKey key = PotionKey.parse(potion);
			if (key == null) {
				continue;
			}
			if (splashReagent != null && !splashReagent.isBlank()) {
				all.add(new Step(potion, splashReagent, key.in(PotionKey.Form.SPLASH).text()));
				if (lingeringReagent != null && !lingeringReagent.isBlank()) {
					all.add(new Step(key.in(PotionKey.Form.SPLASH).text(), lingeringReagent, key.in(PotionKey.Form.LINGERING).text()));
				}
			}
		}
		all.addAll(elixirs);
		return of(all);
	}

	public List<Step> steps() {
		return steps;
	}

	/**
	 * Parses a recipe line "input + reagent -> output" (products as in {@link PotionKey}, the reagent an item id whose
	 * {@code minecraft:} namespace is optional), or null when the line is not one.
	 */
	public static @Nullable Step parse(@Nullable String line) {
		if (line == null) {
			return null;
		}
		int arrow = line.indexOf("->");
		int plus = arrow < 0 ? -1 : line.lastIndexOf('+', arrow);
		if (arrow < 0 || plus < 0) {
			return null;
		}
		String from = PotionKey.canonical(line.substring(0, plus));
		String reagent = itemId(line.substring(plus + 1, arrow));
		String to = PotionKey.canonical(line.substring(arrow + 2));
		if (from == null || reagent == null || to == null || from.equals(to) || to.equals(PotionKey.WATER.text())) {
			return null;
		}
		return new Step(from, reagent, to);
	}

	/** Namespaced item id ("glowstone_dust" -> "minecraft:glowstone_dust"), or null when the text is not an id. */
	public static @Nullable String itemId(@Nullable String text) {
		if (text == null) {
			return null;
		}
		String value = text.trim().toLowerCase(Locale.ROOT);
		if (!value.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) {
			return null;
		}
		return value.contains(":") ? value : "minecraft:" + value;
	}

	/** True when some step makes this product. */
	public boolean makes(String product) {
		return byOutput.containsKey(product);
	}

	/**
	 * Every chain without loops from one of the {@code sources} to {@code target}, at most {@code maxSteps} brews: the
	 * shortest first, then in the order of the recipes. Empty when the target cannot be brewed from them.
	 */
	public List<List<Step>> chains(String target, Set<String> sources, int maxSteps) {
		String cacheKey = target + "|" + maxSteps + "|" + String.join(",", new java.util.TreeSet<>(sources));
		List<List<Step>> cached = chainCache.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		List<List<Step>> found = new ArrayList<>();
		Set<String> visiting = new HashSet<>();
		visiting.add(target);
		collect(target, sources, Math.max(1, Math.min(MAX_STEPS + 2, maxSteps)), visiting, new ArrayList<>(), found);
		found.sort(Comparator.comparingInt(List::size));
		List<List<Step>> result = List.copyOf(found);
		if (chainCache.size() > 512) {
			chainCache.clear();
		}
		chainCache.put(cacheKey, result);
		return result;
	}

	/** Walks back from {@code product}; {@code suffix} holds the steps after it (reversed order). */
	private void collect(String product, Set<String> sources, int budget, Set<String> visiting, List<Step> suffix, List<List<Step>> found) {
		if (budget <= 0 || found.size() >= 64) {
			return;
		}
		for (Step step : byOutput.getOrDefault(product, List.of())) {
			if (visiting.contains(step.from())) {
				continue;
			}
			suffix.add(step);
			if (sources.contains(step.from())) {
				List<Step> chain = new ArrayList<>(suffix);
				java.util.Collections.reverse(chain);
				found.add(List.copyOf(chain));
			} else {
				visiting.add(step.from());
				collect(step.from(), sources, budget - 1, visiting, suffix, found);
				visiting.remove(step.from());
			}
			suffix.removeLast();
		}
	}

	/** Reagents a chain uses up: one per brew (item id -> count). */
	public static Map<String, Integer> reagents(List<Step> chain) {
		Map<String, Integer> needed = new LinkedHashMap<>();
		for (Step step : chain) {
			needed.merge(step.reagent(), 1, Integer::sum);
		}
		return needed;
	}

	/** How far below its target a product is (0 = reached, 1 = none at all). */
	public static double shortage(int target, int have) {
		if (target <= 0 || have >= target) {
			return 0;
		}
		return (double) (target - Math.max(0, have)) / target;
	}

	/**
	 * What to brew: among the {@code products} (menu order) below their target ({@code stock} counts what the guild holds,
	 * batches in progress included), the furthest below it first (ties: menu order), with the shortest of its chains from
	 * the {@code sources} that {@code affordable} accepts (reagents and bottles at hand).
	 */
	public Choice choose(List<String> products, Map<String, Integer> targets, Map<String, Integer> stock, Set<String> sources,
			Predicate<List<Step>> affordable) {
		List<String> wanted = new ArrayList<>();
		for (String product : products) {
			if (shortage(targets.getOrDefault(product, 0), stock.getOrDefault(product, 0)) > 0 && !wanted.contains(product)) {
				wanted.add(product);
			}
		}
		if (wanted.isEmpty()) {
			return new Choice(Verdict.STOCK_FULL, null, null);
		}
		// Stable sort: equal shortages keep the menu order.
		wanted.sort(Comparator.comparingDouble(p -> -shortage(targets.getOrDefault(p, 0), stock.getOrDefault(p, 0))));
		for (String product : wanted) {
			for (List<Step> chain : chains(product, sources, MAX_STEPS)) {
				if (affordable.test(chain)) {
					return new Choice(Verdict.BREW, new Plan(product, chain), product);
				}
			}
		}
		return new Choice(Verdict.MISSING, null, wanted.getFirst());
	}
}
