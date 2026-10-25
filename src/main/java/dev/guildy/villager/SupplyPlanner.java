package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure planner of the gatherers' crafts (no Minecraft classes: unit tested on fake recipe books). Items are ids
 * ("minecraft:oak_planks").
 * <p>
 * {@link #plan} resolves an amount of a target item recursively, at most {@link Settings#maxDepth} recipe levels deep:
 * an item is first taken from what the plan already produced (leftovers of an earlier craft), then from the storage
 * (never its {@code reserved} part: the items other wants still need, what the requesters count on and what the other
 * gatherers already planned to take), then made by the cheapest recipe (crafting by hand or at a crafting table,
 * smelting, stonecutting, in-world transformation such as stripping a log or hardening concrete powder in water; an
 * ingredient accepting several items mixes them from the stock, then makes the rest from the cheapest option), else
 * gathered when it has a natural source, else it is missing. Routes are compared by missing units, then units to gather,
 * then units taken from the storage, then steps: the stonecutter's 1 stone per stair beats the crafting table's 6 stones
 * for 4 stairs. Recipes whose workstation the guild lacks are skipped. Smelting needs fuel (units of "items smelted" per
 * fuel item), taken from the stock in the order of {@link Context#fuels}, else gathered (logs...), else missing
 * ({@link #FUEL}). Steps come out in the order they must be done (ingredients first); extra units of a craft are
 * leftovers, brought to the storage. The search is bounded ({@link #SEARCH_BUDGET} recipe / option trials per plan):
 * past the budget, the first workable choice is kept.
 */
public final class SupplyPlanner {
	/** Pseudo item of the missing fuel. */
	public static final String FUEL = "#fuel";
	/** Recipe and option trials per plan before the search keeps the first workable choice. */
	public static final int SEARCH_BUDGET = 6000;

	public enum Kind {
		CRAFT,
		SMELT,
		/** Stonecutter: one input item gives {@code count} items. */
		CUT,
		/** In-world transformation of one item (axe on a log, concrete powder in water...). */
		TRANSFORM
	}

	/** Where a recipe is made. */
	public enum Station {
		/** Anywhere, by hand (2×2 crafts, tool transformations). */
		HAND,
		TABLE,
		FURNACE,
		STONECUTTER,
		/** At a water source (concrete, mud). */
		WATER
	}

	/** An ingredient: any of {@code options} (item ids), {@code count} of them per craft. */
	public record Input(List<String> options, int count) {
	}

	/** A recipe: {@code count} × {@code output} per craft (a smelting, cutting or transforming recipe takes one input item). */
	public record Recipe(String id, Kind kind, String output, int count, List<Input> inputs, Station station) {
	}

	/** Recipe book of the server (or of a test). */
	public interface Book {
		/** Recipes making {@code item}. */
		List<Recipe> recipesFor(String item);

		/** Items smelted by one of this fuel item, 0 when it is not a fuel. */
		double smeltsPerFuel(String item);

		/** Stack size of the item. */
		int maxStack(String item);
	}

	/** Limits of the planning: recipe depth, kinds of recipes allowed, workstations the guild has ({@link Station#HAND} always). */
	public record Settings(int maxDepth, Set<Kind> kinds, Set<Station> stations) {
		/** Every kind and every station. */
		public static Settings all(int maxDepth) {
			return new Settings(maxDepth, EnumSet.allOf(Kind.class), EnumSet.allOf(Station.class));
		}
	}

	/**
	 * What the planner may use: the storage {@code stock} (givable units per item), the {@code reserved} part of it that is
	 * never used up ({@link Integer#MAX_VALUE} = all), the items with a natural source, the fuels in preference order.
	 */
	public record Context(Book book, Map<String, Integer> stock, Map<String, Integer> reserved, Set<String> gatherable, List<String> fuels,
			Settings settings) {
		int available(String item) {
			int stocked = stock.getOrDefault(item, 0);
			int kept = reserved.getOrDefault(item, 0);
			return kept >= stocked ? 0 : stocked - kept;
		}

		/** Same, with other limits. */
		public Context with(Settings other) {
			return new Context(book, stock, reserved, gatherable, fuels, other);
		}
	}

	/** A recipe to make {@code times} times. */
	public record Step(Recipe recipe, int times) {
		/** Units made. */
		public int made() {
			return recipe.count() * times;
		}
	}

	/**
	 * Result: {@code made} units of the target (the amount asked, plus the extra units of the last craft), what to take
	 * from the storage, what to gather first, what is missing, the steps in order, the leftovers, the fuel items used and
	 * the workstations needed.
	 */
	public record Plan(String target, int amount, int made, Map<String, Integer> take, Map<String, Integer> gather,
			Map<String, Integer> missing, List<Step> steps, Map<String, Integer> leftovers, Map<String, Integer> fuel, Set<Station> stations) {
		/** Everything is in the storage: the plan can be done right now. */
		public boolean ready() {
			return gather.isEmpty() && missing.isEmpty();
		}

		/** Nothing is missing (some items may have to be gathered first). */
		public boolean possible() {
			return missing.isEmpty();
		}

		public boolean needs(Station station) {
			return stations.contains(station);
		}

		public int takeUnits() {
			return sum(take) + sum(fuel);
		}

		public int gatherUnits() {
			return sum(gather);
		}

		/** The item to gather first (the largest amount), or null. */
		public @Nullable String firstGather() {
			String best = null;
			int bestCount = 0;
			for (Map.Entry<String, Integer> entry : gather.entrySet()) {
				if (entry.getValue() > bestCount) {
					best = entry.getKey();
					bestCount = entry.getValue();
				}
			}
			return best;
		}

		/** Bag slots used by the inputs taken (fuel included) and by everything made (target, leftovers). */
		public int bagStacks(Book book) {
			int stacks = 0;
			for (Map.Entry<String, Integer> entry : take.entrySet()) {
				stacks += stacks(book, entry.getKey(), entry.getValue() + fuel.getOrDefault(entry.getKey(), 0));
			}
			for (Map.Entry<String, Integer> entry : fuel.entrySet()) {
				if (!take.containsKey(entry.getKey())) {
					stacks += stacks(book, entry.getKey(), entry.getValue());
				}
			}
			stacks += stacks(book, target, made);
			for (Map.Entry<String, Integer> entry : leftovers.entrySet()) {
				if (!entry.getKey().equals(target)) {
					stacks += stacks(book, entry.getKey(), entry.getValue());
				}
			}
			return stacks;
		}
	}

	private SupplyPlanner() {
	}

	/** Plans {@code amount} units of {@code target} (see the class description). */
	public static Plan plan(Context ctx, String target, int amount) {
		State state = new State();
		int asked = Math.max(1, amount);
		Set<String> path = new HashSet<>();
		path.add(target);
		makeRoot(new Search(ctx), state, target, asked, path);
		int extra = state.pool.getOrDefault(target, 0);
		Map<String, Integer> leftovers = new LinkedHashMap<>();
		state.pool.forEach((item, count) -> {
			if (count > 0 && !item.equals(target)) {
				leftovers.put(item, count);
			}
		});
		Set<Station> stations = EnumSet.noneOf(Station.class);
		for (Step step : state.steps) {
			stations.add(step.recipe().station());
		}
		stations.remove(Station.HAND);
		return new Plan(target, asked, asked + extra, freeze(state.take), freeze(state.gather), freeze(state.missing),
				List.copyOf(state.steps), freeze(leftovers), freeze(state.fuel), Collections.unmodifiableSet(stations));
	}

	/**
	 * The largest amount (1..{@code cap}) of {@code target} that can be made right now from the stock alone and whose
	 * inputs and products fit in {@code bagSlots} bag slots; null when not even one can.
	 */
	public static @Nullable Plan bestFromStock(Context ctx, String target, int cap, int bagSlots) {
		if (cap <= 0 || bagSlots <= 0) {
			return null;
		}
		Plan one = plan(ctx, target, 1);
		if (!one.ready() || one.steps().isEmpty() || one.bagStacks(ctx.book()) > bagSlots) {
			return null;
		}
		int lo = 1;
		int hi = cap;
		Plan best = one;
		while (lo < hi) {
			int mid = lo + (hi - lo + 1) / 2;
			Plan candidate = plan(ctx, target, mid);
			if (candidate.ready() && candidate.bagStacks(ctx.book()) <= bagSlots) {
				lo = mid;
				best = candidate;
			} else {
				hi = mid - 1;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ solver

	/** One search: the context and the trials left. */
	private static final class Search {
		final Context ctx;
		int budget = SEARCH_BUDGET;

		Search(Context ctx) {
			this.ctx = ctx;
		}

		/** Spends one trial; false once the budget is spent (only the first workable choice is tried then). */
		boolean spend() {
			return --budget > 0;
		}

		/** Twice the budget spent: nothing new is explored any more. */
		boolean exhausted() {
			return budget < -SEARCH_BUDGET;
		}
	}

	/** Working state of one branch (copied for each alternative). */
	private static final class State {
		/** Storage units used so far (on top of the reserved part). */
		final Map<String, Integer> used = new HashMap<>();
		/** Units made and not used yet. */
		final Map<String, Integer> pool = new LinkedHashMap<>();
		final Map<String, Integer> take = new LinkedHashMap<>();
		final Map<String, Integer> gather = new LinkedHashMap<>();
		final Map<String, Integer> missing = new LinkedHashMap<>();
		final List<Step> steps = new ArrayList<>();
		final Map<String, Integer> fuel = new LinkedHashMap<>();
		/** Smelts still paid by the fuel already counted. */
		double fuelCredit;

		State copy() {
			State copy = new State();
			copy.assign(this);
			return copy;
		}

		void assign(State other) {
			if (other == this) {
				return;
			}
			used.clear();
			used.putAll(other.used);
			pool.clear();
			pool.putAll(other.pool);
			take.clear();
			take.putAll(other.take);
			gather.clear();
			gather.putAll(other.gather);
			missing.clear();
			missing.putAll(other.missing);
			steps.clear();
			steps.addAll(other.steps);
			fuel.clear();
			fuel.putAll(other.fuel);
			fuelCredit = other.fuelCredit;
		}

		int stockLeft(Context ctx, String item) {
			return Math.max(0, ctx.available(item) - used.getOrDefault(item, 0));
		}

		int atHand(Context ctx, String item) {
			return pool.getOrDefault(item, 0) + stockLeft(ctx, item);
		}

		/** Takes up to {@code count} from the pool then the storage; returns what is still needed. */
		int useExisting(Context ctx, String item, int count) {
			int left = count;
			int pooled = pool.getOrDefault(item, 0);
			if (pooled > 0) {
				int use = Math.min(pooled, left);
				pool.put(item, pooled - use);
				left -= use;
			}
			if (left > 0) {
				int stocked = stockLeft(ctx, item);
				int use = Math.min(stocked, left);
				if (use > 0) {
					used.merge(item, use, Integer::sum);
					take.merge(item, use, Integer::sum);
					left -= use;
				}
			}
			return left;
		}
	}

	/** Cost of a branch: missing units first, then units to gather, then units taken, then steps. */
	private static long[] cost(State state) {
		return new long[]{sum(state.missing), sum(state.gather), sum(state.take) + sum(state.fuel), state.steps.size()};
	}

	private static boolean cheaper(State a, @Nullable State b) {
		if (b == null) {
			return true;
		}
		long[] ca = cost(a);
		long[] cb = cost(b);
		for (int i = 0; i < ca.length; i++) {
			if (ca[i] != cb[i]) {
				return ca[i] < cb[i];
			}
		}
		return false;
	}

	private static boolean workable(@Nullable State state) {
		return state != null && state.missing.isEmpty() && state.gather.isEmpty();
	}

	/** The target is made (never taken from the storage: the requester already counts it). */
	private static void makeRoot(Search search, State state, String target, int amount, Set<String> path) {
		State made = bestRecipe(search, state, target, amount, 1, path);
		if (workable(made)) {
			state.assign(made);
			return;
		}
		if (search.ctx.gatherable().contains(target)) {
			state.gather.merge(target, amount, Integer::sum);
			return;
		}
		if (made != null) {
			// Possible after gathering, or missing: the raw materials lacking are reported (diamonds for a diamond block).
			state.assign(made);
			return;
		}
		state.missing.merge(target, amount, Integer::sum);
	}

	/** Obtains {@code amount} of an ingredient at recipe depth {@code depth}. */
	private static void obtain(Search search, State state, String item, int amount, int depth, Set<String> path) {
		int left = state.useExisting(search.ctx, item, amount);
		if (left <= 0) {
			return;
		}
		State made = path.contains(item) ? null : bestRecipe(search, state, item, left, depth, path);
		if (workable(made)) {
			state.assign(made);
			return;
		}
		if (search.ctx.gatherable().contains(item)) {
			state.gather.merge(item, left, Integer::sum);
			return;
		}
		if (made != null) {
			state.assign(made);
			return;
		}
		state.missing.merge(item, left, Integer::sum);
	}

	/** The cheapest recipe making {@code amount} of {@code item} from {@code state}, or null (none, or too deep). */
	private static @Nullable State bestRecipe(Search search, State state, String item, int amount, int depth, Set<String> path) {
		Context ctx = search.ctx;
		if (depth > ctx.settings().maxDepth() || search.exhausted()) {
			return null;
		}
		State best = null;
		Set<String> deeper = new HashSet<>(path);
		deeper.add(item);
		for (Recipe recipe : ctx.book().recipesFor(item)) {
			if (!usable(ctx, recipe)) {
				continue;
			}
			State branch = state.copy();
			craft(search, branch, recipe, amount, depth, deeper);
			if (cheaper(branch, best)) {
				best = branch;
			}
			if (!search.spend() && workable(best)) {
				break;
			}
		}
		return best;
	}

	private static boolean usable(Context ctx, Recipe recipe) {
		Settings settings = ctx.settings();
		if (recipe.count() <= 0 || recipe.inputs().isEmpty() || !settings.kinds().contains(recipe.kind())) {
			return false;
		}
		return recipe.station() == Station.HAND || settings.stations().contains(recipe.station());
	}

	private static void craft(Search search, State state, Recipe recipe, int amount, int depth, Set<String> path) {
		Context ctx = search.ctx;
		int times = (amount + recipe.count() - 1) / recipe.count();
		for (Input input : recipe.inputs()) {
			int need = input.count() * times;
			if (need <= 0 || input.options().isEmpty()) {
				continue;
			}
			// Mixed from what is at hand first (most plentiful option first)...
			List<String> options = new ArrayList<>(input.options());
			options.sort((a, b) -> Integer.compare(state.atHand(ctx, b), state.atHand(ctx, a)));
			for (String option : options) {
				if (need <= 0) {
					break;
				}
				if (state.atHand(ctx, option) > 0) {
					need = state.useExisting(ctx, option, need);
				}
			}
			if (need <= 0) {
				continue;
			}
			// ...then the rest from the cheapest single option.
			State bestOption = null;
			for (String option : options) {
				if (bestOption != null && search.exhausted()) {
					break;
				}
				State branch = state.copy();
				obtain(search, branch, option, need, depth + 1, path);
				if (cheaper(branch, bestOption)) {
					bestOption = branch;
				}
				if (!search.spend() && workable(bestOption)) {
					break;
				}
				if (workable(bestOption) && bestOption.steps.size() == state.steps.size()) {
					// Taken as it is (nothing to make): no option can be cheaper.
					break;
				}
			}
			if (bestOption != null) {
				state.assign(bestOption);
			}
		}
		if (recipe.kind() == Kind.SMELT) {
			fuel(ctx, state, times, path);
		}
		state.steps.add(new Step(recipe, times));
		int extra = times * recipe.count() - amount;
		if (extra > 0) {
			state.pool.merge(recipe.output(), extra, Integer::sum);
		}
	}

	/** Fuel for {@code smelts} items: credit of the fuel already counted, then the stock, then a gatherable fuel. */
	private static void fuel(Context ctx, State state, int smelts, Set<String> path) {
		double need = smelts - state.fuelCredit;
		if (need <= 0) {
			state.fuelCredit -= smelts;
			return;
		}
		for (String fuel : ctx.fuels()) {
			double per = ctx.book().smeltsPerFuel(fuel);
			if (per <= 0 || path.contains(fuel)) {
				continue;
			}
			while (need > 0 && state.atHand(ctx, fuel) > 0) {
				int pooled = state.pool.getOrDefault(fuel, 0);
				if (pooled > 0) {
					state.pool.put(fuel, pooled - 1);
				} else {
					state.used.merge(fuel, 1, Integer::sum);
					state.fuel.merge(fuel, 1, Integer::sum);
				}
				need -= per;
			}
			if (need <= 0) {
				break;
			}
		}
		if (need > 0) {
			for (String fuel : ctx.fuels()) {
				double per = ctx.book().smeltsPerFuel(fuel);
				if (per > 0 && ctx.gatherable().contains(fuel) && !path.contains(fuel)) {
					int units = (int) Math.ceil(need / per);
					state.gather.merge(fuel, units, Integer::sum);
					need -= units * per;
					break;
				}
			}
		}
		if (need > 0) {
			state.missing.merge(FUEL, (int) Math.ceil(need), Integer::sum);
			need = 0;
		}
		state.fuelCredit = -need;
	}

	// ------------------------------------------------------------------ helpers

	static int sum(Map<String, Integer> map) {
		int total = 0;
		for (int count : map.values()) {
			total += count;
		}
		return total;
	}

	private static int stacks(Book book, String item, int count) {
		if (count <= 0) {
			return 0;
		}
		int max = Math.max(1, book.maxStack(item));
		return (count + max - 1) / max;
	}

	private static Map<String, Integer> freeze(Map<String, Integer> map) {
		Map<String, Integer> copy = new LinkedHashMap<>();
		map.forEach((item, count) -> {
			if (count != null && count > 0) {
				copy.put(item, count);
			}
		});
		return Collections.unmodifiableMap(copy);
	}
}
