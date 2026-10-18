package dev.guildy.ruin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The statue trail to the grand temple: waypoints, orientation, map frame, reuse, research checks, guidance cooldown. */
class StatueRulesTest {
	private static final StatueRules.Spacing DEFAULT = new StatueSettings().spacing();

	private static double distance(StatueRules.Point p, double x, double z) {
		return Math.hypot(p.x() - x, p.z() - z);
	}

	/** Distance of a point from the line origin → temple. */
	private static double offLine(StatueRules.Point p, double ox, double oz, double tx, double tz) {
		double dx = tx - ox;
		double dz = tz - oz;
		return Math.abs((p.x() - ox) * dz - (p.z() - oz) * dx) / Math.hypot(dx, dz);
	}

	@Test
	void fourStatuesEvenlySpacedTheLastInSightOfTheTemple() {
		for (long seed = 0; seed < 40; seed++) {
			List<StatueRules.Point> points = StatueRules.waypoints(0, 0, 1500, 0, DEFAULT, seed);
			assertEquals(4, points.size(), "default count");
			StatueRules.Point last = points.getLast();
			double toTemple = distance(last, 1500, 0);
			assertTrue(toTemple >= 149 && toTemple <= 251, "last statue 150-250 blocks before the temple, got " + toTemple);
			assertTrue(offLine(last, 0, 0, 1500, 0) < 1.01, "the last statue stands on the line to the temple");
			double reach = 1500 - toTemple;
			for (int i = 0; i < points.size(); i++) {
				StatueRules.Point p = points.get(i);
				double along = p.x();
				assertEquals(reach * (i + 1) / 4, along, 1.01, "statue " + i + " evenly spaced along the way");
				assertTrue(offLine(p, 0, 0, 1500, 0) <= 32 + 1, "lateral shift within lateralJitter");
			}
		}
	}

	@Test
	void sameInputsSameTrail() {
		assertEquals(StatueRules.waypoints(10, 20, -900, 1400, DEFAULT, 77), StatueRules.waypoints(10, 20, -900, 1400, DEFAULT, 77));
	}

	@Test
	void spacingAddsOrRemovesStatues() {
		// 6000 blocks: 4 statues would stand ~1450 apart, more than maxSpacing 800 → 8 (maxCount)
		List<StatueRules.Point> far = StatueRules.waypoints(0, 0, 0, 6000, DEFAULT, 1);
		assertEquals(8, far.size(), "capped by maxCount");
		// 3000 blocks: ~2800 / 800 → 4 statues are not enough: ceil(2800 / 800) = 4 or 5 depending on the last distance
		List<StatueRules.Point> mid = StatueRules.waypoints(0, 0, 0, 3600, DEFAULT, 1);
		assertTrue(mid.size() >= 5 && mid.size() <= 8, "more statues when the spacing exceeds maxSpacing, got " + mid.size());
		for (int i = 1; i < mid.size(); i++) {
			assertTrue(Math.hypot(mid.get(i).x() - mid.get(i - 1).x(), mid.get(i).z() - mid.get(i - 1).z()) <= 800 + 70,
					"spacing within maxSpacing (+ lateral shifts)");
		}
		// 400 blocks: the reach (~200) allows only 3 statues 64 blocks apart
		List<StatueRules.Point> near = StatueRules.waypoints(0, 0, 400, 0, DEFAULT, 3);
		assertTrue(near.size() >= 2 && near.size() <= 3, "fewer statues under minSpacing, got " + near.size());
		// 180 blocks: a single statue half way at least
		List<StatueRules.Point> close = StatueRules.waypoints(0, 0, 180, 0, DEFAULT, 3);
		assertEquals(1, close.size(), "one statue for a very close temple");
		assertTrue(close.getFirst().x() >= 89 && close.getFirst().x() <= 180, "half way or more, got " + close.getFirst());
		// no limit: always count statues
		StatueRules.Spacing unlimited = new StatueRules.Spacing(4, 8, 64, 0, 150, 250, 0);
		assertEquals(4, StatueRules.waypoints(0, 0, 0, 9000, unlimited, 5).size(), "maxSpacing 0 keeps count");
		assertEquals(1, StatueRules.waypoints(5, 5, 5, 5, DEFAULT, 1).size(), "temple on the origin: one point");
	}

	@Test
	void statuesFaceTheNextTarget() {
		assertEquals(0, StatueRules.quarterTurnsToward(0, 0, 3, -50), "north");
		assertEquals(1, StatueRules.quarterTurnsToward(0, 0, 50, 10), "east");
		assertEquals(2, StatueRules.quarterTurnsToward(0, 0, -5, 40), "south");
		assertEquals(3, StatueRules.quarterTurnsToward(0, 0, -40, 39), "west");
		assertEquals("n", StatueRules.compass(0, -10));
		assertEquals("ne", StatueRules.compass(10, -10));
		assertEquals("e", StatueRules.compass(10, 1));
		assertEquals("se", StatueRules.compass(7, 8));
		assertEquals("s", StatueRules.compass(0, 10));
		assertEquals("sw", StatueRules.compass(-10, 9));
		assertEquals("w", StatueRules.compass(-10, 0));
		assertEquals("nw", StatueRules.compass(-10, -11));
		// every statue of a trail faces the next point (the last one, the temple)
		List<StatueRules.Point> points = StatueRules.waypoints(0, 0, 0, -2000, DEFAULT, 9);
		for (int i = 0; i < points.size(); i++) {
			StatueRules.Point p = points.get(i);
			double tx = i + 1 < points.size() ? points.get(i + 1).x() : 0;
			double tz = i + 1 < points.size() ? points.get(i + 1).z() : -2000;
			assertEquals(0, StatueRules.quarterTurnsToward(p.x(), p.z(), tx, tz), "trail to the north: every statue faces north");
		}
	}

	@Test
	void mapFramesTheStatues() {
		List<StatueRules.Point> points = new ArrayList<>(List.of(new StatueRules.Point(0, 0)));
		points.addAll(StatueRules.waypoints(0, 0, 1500, 300, DEFAULT, 4));
		StatueRules.MapView view = StatueRules.mapView(points, 24, 2, 4);
		assertTrue(view.zoom() >= 2 && view.zoom() <= 4, "zoom in bounds");
		assertTrue(StatueRules.fits(points, view.centerX(), view.centerZ(), view.zoom(), 24), "every statue drawn on the map");
		int size = 128 << view.zoom();
		assertEquals(view.centerX(), StatueRules.cellCenter(view.centerX(), size), "centre on the vanilla map grid");
		assertEquals(view.centerZ(), StatueRules.cellCenter(view.centerZ(), size), "centre on the vanilla map grid");
		if (view.zoom() > 2) {
			StatueRules.MapView smaller = StatueRules.mapView(points, 24, 2, view.zoom() - 1);
			assertFalse(StatueRules.fits(points, smaller.centerX(), smaller.centerZ(), smaller.zoom(), 24), "the smallest zoom that fits");
		}
		// close statues: the minimum zoom
		StatueRules.MapView small = StatueRules.mapView(List.of(new StatueRules.Point(10, 10), new StatueRules.Point(60, 40)), 24, 2, 4);
		assertEquals(2, small.zoom());
		// too far apart for any map: the largest zoom, the statues off the map on its edge
		StatueRules.MapView huge = StatueRules.mapView(List.of(new StatueRules.Point(0, 0), new StatueRules.Point(9000, 0)), 24, 2, 4);
		assertEquals(4, huge.zoom());
	}

	@Test
	void reuseTheNearestUsableStatue() {
		List<StatueRules.Candidate> candidates = List.of(new StatueRules.Candidate(1, 100, 0, true), new StatueRules.Candidate(2, 40, 10, false),
				new StatueRules.Candidate(3, 60, 30, true));
		assertEquals(3, StatueRules.pickReuse(30, 0, candidates, 96), "nearest usable");
		assertEquals(-1, StatueRules.pickReuse(500, 500, candidates, 96), "none within the radius");
		assertEquals(-1, StatueRules.pickReuse(30, 0, candidates, 0), "reuse disabled");
	}

	@Test
	void guidanceSkipsAbandonedStatues() {
		Map<Integer, Integer> next = new LinkedHashMap<>(Map.of(1, 2, 2, 3, 3, -1));
		assertEquals(2, StatueRules.liveTarget(2, id -> false, next::get, 16));
		assertEquals(3, StatueRules.liveTarget(2, id -> id == 2, next::get, 16), "abandoned statue skipped");
		assertEquals(-1, StatueRules.liveTarget(2, id -> id >= 2, next::get, 16), "every statue abandoned: the temple");
		assertEquals(-1, StatueRules.liveTarget(-1, id -> false, next::get, 16), "the temple itself");
		Map<Integer, Integer> loop = Map.of(1, 2, 2, 1);
		assertEquals(-1, StatueRules.liveTarget(1, id -> true, loop::get, 16), "a loop ends at the temple");
	}

	@Test
	void legendChecksAndCost() {
		assertEquals("ruin.research.legend.disabled", StatueRules.legendRefusal(false, 10, 7));
		assertEquals("ruin.research.legend.level", StatueRules.legendRefusal(true, 6, 7));
		assertNull(StatueRules.legendRefusal(true, 7, 7));
		Map<String, Integer> cost = new LinkedHashMap<>(Map.of("minecraft:paper", 16));
		cost.put("minecraft:emerald", 8);
		Map<String, Integer> inventory = new LinkedHashMap<>(Map.of("minecraft:paper", 20, "minecraft:emerald", 3));
		Map.Entry<String, Integer> missing = StatueRules.missing(cost, id -> inventory.getOrDefault(id, 0));
		assertNotNull(missing);
		assertEquals("minecraft:emerald", missing.getKey());
		inventory.put("minecraft:emerald", 8);
		assertNull(StatueRules.missing(cost, id -> inventory.getOrDefault(id, 0)), "everything paid");
		assertTrue(StatueRules.guideReady(100, -1, 900), "never guided");
		assertFalse(StatueRules.guideReady(500, 100, 900), "cooldown running");
		assertTrue(StatueRules.guideReady(1000, 100, 900), "cooldown over");
		assertTrue(StatueRules.guideReady(50, 100, 900), "clock reset");
	}

	@Test
	void settingsAreClamped() {
		StatueSettings s = new StatueSettings();
		List<String> warnings = new ArrayList<>();
		s.fix(id -> true, warnings);
		assertTrue(warnings.isEmpty(), "defaults need no fix: " + warnings);
		assertEquals(StatueSettings.DEFAULT_TEMPLE, s.templeStructure);
		assertEquals(4, s.count);
		s.count = 0;
		s.maxCount = -3;
		s.lastDistanceMin = 400;
		s.lastDistanceMax = 100;
		s.maxSpacing = 10;
		s.minGuildLevel = -2;
		s.templeStructure = "  ";
		s.guideSeconds = 99;
		s.mapMinZoom = 9;
		s.mapMaxZoom = 1;
		s.groundBlocks = null;
		s.cost = new LinkedHashMap<>(Map.of("paper", 5, "nope:thing", 3));
		s.fix(id -> !id.startsWith("nope:"), warnings);
		assertEquals(1, s.count);
		assertEquals(1, s.maxCount);
		assertTrue(s.lastDistanceMax >= s.lastDistanceMin);
		assertTrue(s.maxSpacing >= s.minSpacing);
		assertEquals(1, s.minGuildLevel);
		assertEquals(StatueSettings.DEFAULT_TEMPLE, s.templeStructure);
		assertEquals(9, s.guideSeconds);
		assertTrue(s.mapMinZoom <= s.mapMaxZoom && s.mapMaxZoom <= 4);
		assertFalse(s.groundBlocks.isEmpty(), "default ground blocks");
		assertEquals(Map.of("minecraft:paper", 5), s.cost);
		assertEquals(1, warnings.size(), "unknown item reported");
		// part of ruins.json5
		RuinConfig cfg = new RuinConfig();
		cfg.cartography.statues = null;
		assertTrue(cfg.rebuild(id -> true, id -> true, id -> true).isEmpty());
		assertNotNull(cfg.cartography.statues, "missing section: defaults");
	}
}
