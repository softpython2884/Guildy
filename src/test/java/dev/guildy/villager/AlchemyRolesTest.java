package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roles of the alchemists: order of the jobs, fallback, balanced and priority distributions, manual roles. */
class AlchemyRolesTest {
	private static final List<AlchemyJob> ORDER = List.of(AlchemyJob.BOOKS, AlchemyJob.SCROLLS, AlchemyJob.POTIONS);
	private static final Map<AlchemyJob, Double> WEIGHTS = AlchemyRoles.weights(AlchemistConfig.defaultWeights());
	private static final Set<AlchemyJob> ALL = EnumSet.allOf(AlchemyJob.class);

	private static List<String> ids(int count) {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ids.add("a" + i);
		}
		return ids;
	}

	private static Map<AlchemyJob, Integer> counts(Map<String, AlchemyJob> roles) {
		Map<AlchemyJob, Integer> counts = new EnumMap<>(AlchemyJob.class);
		for (AlchemyJob job : roles.values()) {
			counts.merge(job, 1, Integer::sum);
		}
		return counts;
	}

	private static Map<String, AlchemyJob> balanced(int count, Set<AlchemyJob> possible) {
		return AlchemyRoles.assign(ids(count), Map.of(), Map.of(), ORDER, WEIGHTS, possible, AlchemyRoles.Distribution.BALANCED);
	}

	@Test
	void orderAndFallback() {
		assertEquals(ORDER, AlchemyRoles.parseOrder(List.of("books", "scrolls", "potions")));
		assertEquals(List.of(AlchemyJob.POTIONS, AlchemyJob.BOOKS, AlchemyJob.SCROLLS), AlchemyRoles.parseOrder(List.of("potions", "nope", "potions")),
				"unknown and repeated ids ignored, missing jobs appended");
		assertEquals(ORDER, AlchemyRoles.parseOrder(null));
		assertEquals(ORDER, AlchemyRoles.tryOrder(null, ORDER));
		assertEquals(List.of(AlchemyJob.POTIONS, AlchemyJob.BOOKS, AlchemyJob.SCROLLS), AlchemyRoles.tryOrder(AlchemyJob.POTIONS, ORDER),
				"its own job first, then the order");
		// Books > scrolls > potions: no books -> scrolls need books too -> potions.
		Map<AlchemyJob, String> reasons = Map.of(AlchemyJob.BOOKS, "pas de livres", AlchemyJob.SCROLLS, "pas de livres");
		assertEquals(AlchemyJob.POTIONS, AlchemyRoles.firstPossible(ORDER, reasons::get));
		assertEquals(AlchemyJob.SCROLLS, AlchemyRoles.firstPossible(ORDER, job -> job == AlchemyJob.BOOKS ? "plafond" : null),
				"books capped for the day: scrolls");
		assertEquals(AlchemyJob.BOOKS, AlchemyRoles.firstPossible(ORDER, job -> null));
		assertNull(AlchemyRoles.firstPossible(ORDER, job -> "rien"), "nothing possible");
		assertEquals(AlchemyJob.SCROLLS, AlchemyJob.parse(" Scrolls "));
		assertNull(AlchemyJob.parse(""));
		assertNull(AlchemyJob.parse("livres"));
	}

	@Test
	void balancedSharesByWeight() {
		assertEquals(Map.of(AlchemyJob.BOOKS, 1), counts(balanced(1, ALL)), "alone: the first job of the order");
		assertEquals(Map.of(AlchemyJob.BOOKS, 1, AlchemyJob.POTIONS, 1), counts(balanced(2, ALL)));
		assertEquals(Map.of(AlchemyJob.BOOKS, 1, AlchemyJob.SCROLLS, 1, AlchemyJob.POTIONS, 1), counts(balanced(3, ALL)),
				"three alchemists: one of each");
		assertEquals(Map.of(AlchemyJob.BOOKS, 2, AlchemyJob.SCROLLS, 1, AlchemyJob.POTIONS, 2), counts(balanced(5, ALL)),
				"five alchemists: 2 books, 1 scrolls, 2 potions");
		Map<AlchemyJob, Integer> ten = counts(balanced(10, ALL));
		assertEquals(4, ten.get(AlchemyJob.BOOKS));
		assertEquals(2, ten.get(AlchemyJob.SCROLLS));
		assertEquals(4, ten.get(AlchemyJob.POTIONS));
		assertTrue(balanced(0, ALL).isEmpty());
	}

	@Test
	void impossibleJobsGetNobody() {
		// No brewing stand: books and scrolls share everyone.
		Map<AlchemyJob, Integer> noStand = counts(balanced(5, EnumSet.of(AlchemyJob.BOOKS, AlchemyJob.SCROLLS)));
		assertEquals(null, noStand.get(AlchemyJob.POTIONS));
		assertEquals(5, noStand.getOrDefault(AlchemyJob.BOOKS, 0) + noStand.getOrDefault(AlchemyJob.SCROLLS, 0));
		assertTrue(noStand.get(AlchemyJob.BOOKS) > noStand.get(AlchemyJob.SCROLLS));
		// Only potions possible: everyone prefers potions.
		assertEquals(Map.of(AlchemyJob.POTIONS, 3), counts(balanced(3, EnumSet.of(AlchemyJob.POTIONS))));
		// Nothing possible: the shares of every weighted job (they will fall back anyway).
		assertEquals(counts(balanced(3, ALL)), counts(balanced(3, Set.of())));
		// A job without weight never gets an alchemist on its own.
		Map<AlchemyJob, Double> noScrolls = new HashMap<>(WEIGHTS);
		noScrolls.put(AlchemyJob.SCROLLS, 0.0);
		Map<String, AlchemyJob> roles = AlchemyRoles.assign(ids(4), Map.of(), Map.of(), ORDER, noScrolls, ALL, AlchemyRoles.Distribution.BALANCED);
		assertEquals(Map.of(AlchemyJob.BOOKS, 2, AlchemyJob.POTIONS, 2), counts(roles));
	}

	@Test
	void manualRolesCountAndStick() {
		// Two alchemists chose potions in their menu: the three others go to books and scrolls.
		Map<String, AlchemyJob> manual = Map.of("a0", AlchemyJob.POTIONS, "a1", AlchemyJob.POTIONS);
		Map<String, AlchemyJob> roles = AlchemyRoles.assign(ids(5), manual, Map.of(), ORDER, WEIGHTS, ALL, AlchemyRoles.Distribution.BALANCED);
		assertEquals(AlchemyJob.POTIONS, roles.get("a0"));
		assertEquals(AlchemyJob.POTIONS, roles.get("a1"));
		assertEquals(Map.of(AlchemyJob.BOOKS, 2, AlchemyJob.SCROLLS, 1, AlchemyJob.POTIONS, 2), counts(roles));
		// The distribution keeps each one's job while it still has a place.
		Map<String, AlchemyJob> first = balanced(3, ALL);
		Map<String, AlchemyJob> previous = new HashMap<>();
		previous.put("a0", AlchemyJob.POTIONS);
		previous.put("a1", AlchemyJob.SCROLLS);
		previous.put("a2", AlchemyJob.BOOKS);
		Map<String, AlchemyJob> again = AlchemyRoles.assign(ids(3), Map.of(), previous, ORDER, WEIGHTS, ALL, AlchemyRoles.Distribution.BALANCED);
		assertEquals(previous, again, "no reshuffle when nothing changed");
		assertEquals(counts(first), counts(again));
		// A fourth alchemist arrives: the others keep their jobs.
		Map<String, AlchemyJob> four = AlchemyRoles.assign(ids(4), Map.of(), previous, ORDER, WEIGHTS, ALL, AlchemyRoles.Distribution.BALANCED);
		assertEquals(AlchemyJob.POTIONS, four.get("a0"));
		assertEquals(AlchemyJob.SCROLLS, four.get("a1"));
		assertEquals(AlchemyJob.BOOKS, four.get("a2"));
		assertEquals(4, four.size());
	}

	@Test
	void priorityDistribution() {
		Map<String, AlchemyJob> roles = AlchemyRoles.assign(ids(4), Map.of("a3", AlchemyJob.SCROLLS), Map.of(), ORDER, WEIGHTS, ALL,
				AlchemyRoles.Distribution.PRIORITY);
		assertEquals(Map.of(AlchemyJob.BOOKS, 3, AlchemyJob.SCROLLS, 1), counts(roles), "everyone prefers the first job, manual roles kept");
		assertEquals(AlchemyRoles.Distribution.PRIORITY, AlchemyRoles.Distribution.parse(" Priority"));
		assertEquals(AlchemyRoles.Distribution.BALANCED, AlchemyRoles.Distribution.parse("whatever"));
	}

	@Test
	void eachJobHasItsOwnDailyCount() {
		CraftData data = new CraftData();
		data.countWork(3, false);
		data.countWork(3, false);
		data.countWork(3, true);
		data.countPotions(3, 3);
		assertEquals(2, data.booksToday(3));
		assertEquals(1, data.scrollsToday(3));
		assertEquals(3, data.potionsToday(3));
		assertEquals(3, data.worksToday(3), "books and scrolls at the table");
		assertEquals(3, data.potions);
		data.countPotions(4, 2);
		assertEquals(0, data.booksToday(4), "a new day starts every count over");
		assertEquals(2, data.potionsToday(4));
		assertEquals(5, data.potions);
	}

	@Test
	void jobSettingsAreBounded() {
		AlchemistConfig cfg = new AlchemistConfig();
		cfg.validate();
		assertEquals(List.of("books", "scrolls", "potions"), cfg.jobOrder);
		assertEquals("balanced", cfg.distribution);
		assertEquals(18, cfg.maxPotionsPerDay);
		assertEquals(3, cfg.bottlesPerBrew);
		assertEquals("minecraft:blaze_powder", cfg.brewFuel);
		cfg.jobOrder = new ArrayList<>(List.of("potions", "dance"));
		cfg.distribution = "chaos";
		cfg.roleWeights = new HashMap<>(Map.of("potions", 500.0, "magic", 2.0));
		cfg.bottlesPerBrew = 9;
		cfg.maxPotionsPerDay = -1;
		cfg.brewsPerFuel = 0;
		cfg.brewFuel = "Not An Id";
		cfg.secondsPerBrew = 0;
		cfg.waterRadius = 99;
		List<String> warnings = cfg.cleanJobs();
		assertEquals(2, warnings.size(), "unknown job and fuel: " + warnings);
		assertEquals(List.of("potions", "books", "scrolls"), cfg.jobOrder);
		assertEquals("balanced", cfg.distribution);
		assertEquals(100.0, cfg.roleWeights.get("potions"));
		assertEquals(0.0, cfg.roleWeights.get("books"), "missing weights at 0");
		assertEquals(3, cfg.bottlesPerBrew);
		assertEquals(0, cfg.maxPotionsPerDay);
		assertEquals(1, cfg.brewsPerFuel);
		assertEquals("minecraft:blaze_powder", cfg.brewFuel);
		assertEquals(1, cfg.secondsPerBrew);
		assertEquals(16, cfg.waterRadius);
	}
}
