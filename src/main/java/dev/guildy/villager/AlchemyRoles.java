package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Who does what among the alchemists of a guild (pure, unit tested). Every alchemist has a preferred job (its role): a
 * manual one chosen in its menu, or one given by the distribution. It tries its preferred job first, then the others in
 * the configured order (books > scrolls > potions by default), and works at the first one that is possible.
 *
 * <p>Distribution {@code balanced}: the jobs that are possible for the guild share the alchemists by weight (highest
 * averages: each next alchemist goes to the job with the highest weight / (alchemists + 1); ties: the job with fewer
 * alchemists, then the order), manual roles counted first; an alchemist keeps its role while its job still has a place
 * (no reshuffle when nothing changed). Distribution {@code priority}: everyone prefers the first job of the order.
 */
public final class AlchemyRoles {
	/** How the preferred jobs are given. */
	public enum Distribution {
		BALANCED,
		PRIORITY;

		public static Distribution parse(@Nullable String text) {
			return text != null && text.trim().equalsIgnoreCase("priority") ? PRIORITY : BALANCED;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private AlchemyRoles() {
	}

	/** The configured order: valid ids once each, the missing jobs appended (default order books, scrolls, potions). */
	public static List<AlchemyJob> parseOrder(@Nullable List<String> ids) {
		List<AlchemyJob> order = new ArrayList<>();
		if (ids != null) {
			for (String id : ids) {
				AlchemyJob job = AlchemyJob.parse(id);
				if (job != null && !order.contains(job)) {
					order.add(job);
				}
			}
		}
		for (AlchemyJob job : AlchemyJob.values()) {
			if (!order.contains(job)) {
				order.add(job);
			}
		}
		return order;
	}

	/** Jobs in the order an alchemist tries them: its preferred job first, then the others in the configured order. */
	public static List<AlchemyJob> tryOrder(@Nullable AlchemyJob preferred, List<AlchemyJob> order) {
		List<AlchemyJob> jobs = new ArrayList<>();
		if (preferred != null) {
			jobs.add(preferred);
		}
		for (AlchemyJob job : order) {
			if (!jobs.contains(job)) {
				jobs.add(job);
			}
		}
		return jobs;
	}

	/**
	 * The first job of {@code jobs} without a blocking reason ({@code reason} returns null when the job is possible), null
	 * when every job is blocked.
	 */
	public static <R> @Nullable AlchemyJob firstPossible(List<AlchemyJob> jobs, Function<AlchemyJob, @Nullable R> reason) {
		for (AlchemyJob job : jobs) {
			if (reason.apply(job) == null) {
				return job;
			}
		}
		return null;
	}

	/**
	 * Preferred job of each alchemist ({@code ids} in a stable order).
	 *
	 * @param manual   roles chosen in the menus (they keep them; counted in the shares of the balanced distribution)
	 * @param previous the last distribution (kept while the job still has a place)
	 * @param weights  share of each job (balanced); a job without weight gets no alchemist
	 * @param possible jobs the guild can do right now (balanced: only they get alchemists; all of them when none is)
	 */
	public static Map<String, AlchemyJob> assign(List<String> ids, Map<String, AlchemyJob> manual, Map<String, AlchemyJob> previous,
			List<AlchemyJob> order, Map<AlchemyJob, Double> weights, Set<AlchemyJob> possible, Distribution mode) {
		Map<String, AlchemyJob> result = new LinkedHashMap<>();
		List<String> auto = new ArrayList<>();
		Map<AlchemyJob, Integer> counts = new EnumMap<>(AlchemyJob.class);
		for (String id : ids) {
			AlchemyJob chosen = manual.get(id);
			if (chosen != null) {
				result.put(id, chosen);
				counts.merge(chosen, 1, Integer::sum);
			} else {
				auto.add(id);
			}
		}
		if (auto.isEmpty()) {
			return result;
		}
		if (mode == Distribution.PRIORITY) {
			for (String id : auto) {
				result.put(id, order.getFirst());
			}
			return result;
		}
		List<AlchemyJob> jobs = new ArrayList<>();
		for (AlchemyJob job : order) {
			if (possible.contains(job) && weight(weights, job) > 0) {
				jobs.add(job);
			}
		}
		if (jobs.isEmpty()) {
			for (AlchemyJob job : order) {
				if (weight(weights, job) > 0) {
					jobs.add(job);
				}
			}
		}
		if (jobs.isEmpty()) {
			jobs.add(order.getFirst());
		}
		Map<AlchemyJob, Integer> slots = slots(auto.size(), counts, jobs, weights);
		List<String> waiting = new ArrayList<>();
		for (String id : auto) {
			AlchemyJob before = previous.get(id);
			if (before != null && slots.getOrDefault(before, 0) > 0) {
				result.put(id, before);
				slots.merge(before, -1, Integer::sum);
			} else {
				waiting.add(id);
			}
		}
		for (String id : waiting) {
			for (AlchemyJob job : jobs) {
				if (slots.getOrDefault(job, 0) > 0) {
					result.put(id, job);
					slots.merge(job, -1, Integer::sum);
					break;
				}
			}
			result.putIfAbsent(id, jobs.getFirst());
		}
		return result;
	}

	/**
	 * Places of each job for {@code autoCount} alchemists by highest averages, starting from the alchemists already counted
	 * ({@code counts}, manual roles).
	 */
	static Map<AlchemyJob, Integer> slots(int autoCount, Map<AlchemyJob, Integer> counts, List<AlchemyJob> jobs, Map<AlchemyJob, Double> weights) {
		Map<AlchemyJob, Integer> taken = new EnumMap<>(AlchemyJob.class);
		for (AlchemyJob job : jobs) {
			taken.put(job, counts.getOrDefault(job, 0));
		}
		Map<AlchemyJob, Integer> slots = new EnumMap<>(AlchemyJob.class);
		for (int i = 0; i < autoCount; i++) {
			AlchemyJob best = null;
			double bestValue = -1;
			for (AlchemyJob job : jobs) {
				double w = weight(weights, job);
				double value = (w > 0 ? w : 1.0E-9) / (taken.get(job) + 1);
				if (best == null || value > bestValue + 1.0E-12
						|| (Math.abs(value - bestValue) <= 1.0E-12 && taken.get(job) < taken.get(best))) {
					best = job;
					bestValue = value;
				}
			}
			taken.merge(best, 1, Integer::sum);
			slots.merge(best, 1, Integer::sum);
		}
		return slots;
	}

	private static double weight(Map<AlchemyJob, Double> weights, AlchemyJob job) {
		Double w = weights.get(job);
		return w == null || !Double.isFinite(w) ? 0 : Math.max(0, w);
	}

	/** Weights of the config (job id -> weight), unknown ids ignored, missing jobs at 0. */
	public static Map<AlchemyJob, Double> weights(@Nullable Map<String, Double> config) {
		Map<AlchemyJob, Double> weights = new EnumMap<>(AlchemyJob.class);
		if (config != null) {
			for (Map.Entry<String, Double> entry : config.entrySet()) {
				AlchemyJob job = AlchemyJob.parse(entry.getKey());
				if (job != null && entry.getValue() != null && Double.isFinite(entry.getValue())) {
					weights.put(job, Math.max(0, entry.getValue()));
				}
			}
		}
		return weights;
	}
}
