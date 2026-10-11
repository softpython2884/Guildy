package dev.guildy.wave;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedPickerTest {
	private record Entry(String name, double weight) {
	}

	@Test
	void emptyOrWeightlessGivesNull() {
		Random random = new Random(1);
		assertNull(WeightedPicker.pick(List.<Entry>of(), Entry::weight, random));
		assertNull(WeightedPicker.pick(List.of(new Entry("a", 0), new Entry("b", -3), new Entry("c", Double.NaN)), Entry::weight, random));
	}

	@Test
	void singleEntryAlwaysPicked() {
		Random random = new Random(2);
		Entry only = new Entry("only", 0.001);
		for (int i = 0; i < 100; i++) {
			assertEquals(only, WeightedPicker.pick(List.of(new Entry("zero", 0), only), Entry::weight, random));
		}
	}

	@Test
	void distributionFollowsWeights() {
		Random random = new Random(42);
		List<Entry> entries = List.of(new Entry("rare", 1), new Entry("common", 3), new Entry("never", 0),
				new Entry("infinite", Double.POSITIVE_INFINITY));
		Map<String, Integer> counts = new HashMap<>();
		int draws = 40_000;
		for (int i = 0; i < draws; i++) {
			counts.merge(WeightedPicker.pick(entries, Entry::weight, random).name(), 1, Integer::sum);
		}
		assertEquals(0, counts.getOrDefault("never", 0));
		assertEquals(0, counts.getOrDefault("infinite", 0), "non-finite weights are ignored");
		double rare = counts.get("rare") / (double) draws;
		assertTrue(Math.abs(rare - 0.25) < 0.02, "rare share was " + rare);
	}

	@Test
	void seededRandomIsDeterministic() {
		List<Entry> entries = List.of(new Entry("a", 5), new Entry("b", 2), new Entry("c", 9));
		Random first = new Random(1234);
		Random second = new Random(1234);
		StringBuilder a = new StringBuilder();
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 50; i++) {
			a.append(WeightedPicker.pick(entries, Entry::weight, first).name());
			b.append(WeightedPicker.pick(entries, Entry::weight, second).name());
		}
		assertEquals(a.toString(), b.toString());
		assertNotEquals(-1, a.indexOf("c"));
	}
}
