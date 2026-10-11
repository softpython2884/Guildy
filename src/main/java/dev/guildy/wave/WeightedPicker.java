package dev.guildy.wave;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

/**
 * Weighted random selection (pure Java, unit-tested). Entries with a weight <= 0 (or NaN) are never picked.
 */
public final class WeightedPicker {
	private WeightedPicker() {
	}

	public static <T> @Nullable T pick(List<T> entries, ToDoubleFunction<T> weight, RandomGenerator random) {
		double total = 0;
		for (T entry : entries) {
			total += usable(weight.applyAsDouble(entry));
		}
		if (total <= 0) {
			return null;
		}
		double roll = random.nextDouble() * total;
		T last = null;
		for (T entry : entries) {
			double w = usable(weight.applyAsDouble(entry));
			if (w <= 0) {
				continue;
			}
			last = entry;
			roll -= w;
			if (roll < 0) {
				return entry;
			}
		}
		// Floating point leftovers: fall back to the last pickable entry.
		return last;
	}

	private static double usable(double w) {
		return Double.isFinite(w) && w > 0 ? w : 0;
	}
}
