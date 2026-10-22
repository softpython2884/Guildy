package dev.guildy.scroll;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The timeline of the Grand spectacle: acts scaled from 20 s to 2 min with the finale at the end, the final burst of every
 * circle after a silence, symmetric salvos, the same show for the same seed, never more rockets in flight than the cap,
 * vanilla rocket physics that reach the planned burst points, the cursed show's dark palettes, the figures' outlines.
 */
class ShowPlanTest {
	private static final List<Integer> GUILD = List.of(0xF9FFFE, 0xB02E26);

	private static ShowPlan plan(long seed, int seconds, int points, boolean cursed) {
		return ShowPlan.build(new ShowPlan.Settings(seed, seconds * 20, points, 18, 26, cursed, GUILD, cursed ? 48 : 40));
	}

	private static List<ShowPlan.Rocket> burstingAt(ShowPlan plan, int tick) {
		List<ShowPlan.Rocket> out = new ArrayList<>();
		for (ShowPlan.Rocket rocket : plan.rockets()) {
			if (rocket.burst() == tick) {
				out.add(rocket);
			}
		}
		return out;
	}

	private static int burstsBetween(ShowPlan plan, int from, int to) {
		int n = 0;
		for (ShowPlan.Rocket rocket : plan.rockets()) {
			if (rocket.burst() >= from && rocket.burst() < to) {
				n++;
			}
		}
		return n;
	}

	@Test
	void scalesFromTwentySecondsToTwoMinutes() {
		int previous = 0;
		for (int seconds : new int[] {20, 30, 40, 60, 90, 120}) {
			ShowPlan plan = plan(7, seconds, 9, false);
			String at = seconds + " s";
			assertEquals(seconds * 20, plan.ticks(), at);
			List<ShowPlan.Span> acts = plan.acts();
			assertEquals(ShowPlan.Act.OPENING, acts.getFirst().act(), at + ": the opening first");
			assertEquals(ShowPlan.Act.CRESCENDO, acts.get(acts.size() - 2).act(), at + ": the crescendo before the finale");
			assertEquals(ShowPlan.Act.FINALE, acts.getLast().act(), at + ": the finale at the end");
			assertEquals(ShowPlan.INTRO + ShowPlan.LEAD, acts.getFirst().start(), at + ": first bursts after the ignition");
			for (int i = 0; i < acts.size(); i++) {
				assertTrue(acts.get(i).end() > acts.get(i).start(), at + ": act " + i + " is not empty");
				if (i > 0) {
					assertEquals(acts.get(i - 1).end(), acts.get(i).start(), at + ": contiguous acts");
				}
			}
			assertEquals(plan.finalBurst() + 1, plan.finale().end(), at);
			assertEquals(plan.ticks() - ShowPlan.OUTRO, plan.finalBurst(), at + ": two seconds of afterglow");
			assertTrue(plan.finale().start() >= plan.ticks() * 0.6, at + ": the finale is the last part: " + plan.finale());
			int last = 0;
			for (ShowPlan.Rocket rocket : plan.rockets()) {
				assertTrue(rocket.launch() >= 0, at + ": " + rocket);
				assertTrue(rocket.flight() >= ShowPlan.MIN_FLIGHT, at + ": " + rocket);
				assertTrue(rocket.burst() <= plan.finalBurst(), at + ": " + rocket);
				assertTrue(rocket.point() >= 0 && rocket.point() < 9, at + ": " + rocket);
				assertFalse(rocket.explosions().isEmpty(), at + ": every rocket explodes");
				last = Math.max(last, rocket.burst());
			}
			assertEquals(plan.finalBurst(), last, at + ": nothing after the final burst");
			for (ShowPlan.Act act : ShowPlan.Act.values()) {
				assertTrue(plan.rockets().stream().anyMatch(r -> r.act() == act), at + ": " + act + " fires something");
			}
			assertTrue(plan.rockets().size() > previous, at + ": a longer show fires more (" + plan.rockets().size() + ")");
			assertTrue(plan.rockets().size() >= seconds * 4, at + ": at least 4 rockets a second on average, " + plan.rockets().size());
			previous = plan.rockets().size();
		}
		assertEquals(1, plan(7, 40, 9, false).acts().stream().filter(s -> s.act() == ShowPlan.Act.SALVOS).count(), "40 s: one cycle");
		assertTrue(plan(7, 120, 9, false).acts().stream().filter(s -> s.act() == ShowPlan.Act.SALVOS).count() >= 3,
				"2 min: the middle acts come back with other colours");
		// Out of bounds: 20 s .. 2 min.
		assertEquals(ShowPlan.MIN_TICKS, plan(7, 5, 9, false).ticks());
		assertEquals(ShowPlan.MAX_TICKS, plan(7, 600, 9, false).ticks());
	}

	@Test
	void finaleEndsWithEveryCircleAtOnce() {
		for (boolean cursed : new boolean[] {false, true}) {
			for (int seconds : new int[] {20, 40, 120}) {
				for (int points : new int[] {3, 8, 9, 15}) {
					ShowPlan plan = plan(11, seconds, points, cursed);
					String at = seconds + " s, " + points + " points" + (cursed ? ", cursed" : "");
					int last = plan.finalBurst();
					List<ShowPlan.Rocket> burst = burstingAt(plan, last);
					Set<Integer> circles = new HashSet<>();
					burst.forEach(r -> circles.add(r.point()));
					assertEquals(points, circles.size(), at + ": every circle fires the last burst");
					assertTrue(burst.size() >= points + 1, at + ": layered (" + burst.size() + ")");
					ShowPlan.Figure crown = cursed ? ShowPlan.Figure.PENTAGRAM : ShowPlan.Figure.CROWN;
					assertTrue(burst.stream().anyMatch(r -> r.figure() == crown), at + ": " + crown + " above it");
					assertEquals(0, burstsBetween(plan, last - ShowPlan.HUSH + 1, last), at + ": a silence before the last burst");
					// The bouquet is the densest part of the show.
					ShowPlan.Span finale = plan.finale();
					int quiet = last - ShowPlan.HUSH;
					double bouquet = burstsBetween(plan, finale.start(), quiet + 1) / (double) (quiet + 1 - finale.start());
					for (ShowPlan.Span span : plan.acts()) {
						if (span.act() != ShowPlan.Act.FINALE) {
							double rate = burstsBetween(plan, span.start(), span.end()) / (double) (span.end() - span.start());
							assertTrue(bouquet > rate, at + ": the bouquet (" + bouquet + "/tick) beats " + span.act() + " (" + rate + ")");
						}
					}
				}
			}
		}
	}

	@Test
	void salvosAreSymmetric() {
		for (long seed = 1; seed <= 5; seed++) {
			for (int points : new int[] {3, 5, 8, 9, 15}) {
				ShowPlan plan = plan(seed, 60, points, seed % 2 == 0);
				Map<Integer, List<ShowPlan.Rocket>> beats = new TreeMap<>();
				for (ShowPlan.Rocket rocket : plan.rockets()) {
					if (rocket.act() == ShowPlan.Act.SALVOS) {
						beats.computeIfAbsent(rocket.burst(), k -> new ArrayList<>()).add(rocket);
					}
				}
				assertTrue(beats.size() >= 4, "salvos at " + points + " points: " + beats.size());
				for (Map.Entry<Integer, List<ShowPlan.Rocket>> beat : beats.entrySet()) {
					Map<String, Integer> balance = new HashMap<>();
					for (ShowPlan.Rocket r : beat.getValue()) {
						balance.merge(key(r.point(), r.drift(), r), 1, Integer::sum);
						balance.merge(key(points - 1 - r.point(), -r.drift(), r), -1, Integer::sum);
					}
					balance.values().removeIf(v -> v == 0);
					assertTrue(balance.isEmpty(), "salvo at tick " + beat.getKey() + " (" + points + " points) is mirrored: " + beat.getValue());
				}
			}
		}
	}

	private static String key(int point, double drift, ShowPlan.Rocket r) {
		return point + "/" + Math.round(drift * 1000) + "/" + r.height() + "/" + r.flight() + "/" + r.explosions();
	}

	@Test
	void sameSeedSameShow() {
		ShowPlan a = plan(42, 60, 9, false);
		ShowPlan b = plan(42, 60, 9, false);
		assertEquals(a.rockets(), b.rockets());
		assertEquals(a.acts(), b.acts());
		assertNotEquals(a.rockets(), plan(43, 60, 9, false).rockets(), "another seed, another show");
		assertEquals(plan(9, 30, 5, true).rockets(), plan(9, 30, 5, true).rockets());
	}

	@Test
	void neverMoreRocketsInFlightThanTheCap() {
		for (boolean cursed : new boolean[] {false, true}) {
			for (int seconds : new int[] {20, 40, 75, 120}) {
				for (int points : new int[] {3, 9, 15}) {
					ShowPlan plan = plan(seconds + points, seconds, points, cursed);
					int cap = plan.settings().maxAlive();
					int top = 0;
					for (int t = 0; t <= plan.ticks(); t++) {
						int alive = plan.alive(t);
						assertTrue(alive <= cap, seconds + " s, " + points + " points: " + alive + " in flight at " + t + " (cap " + cap + ")");
						top = Math.max(top, alive);
					}
					if (points >= 9) {
						assertTrue(top >= cap * 3 / 4, "the finale fills the sky: at most " + top + " of " + cap);
					}
				}
			}
		}
		// A tight cap still gives a show, and keeps it.
		ShowPlan tight = ShowPlan.build(new ShowPlan.Settings(3, 800, 9, 18, 26, false, List.of(), 12));
		for (int t = 0; t <= tight.ticks(); t++) {
			assertTrue(tight.alive(t) <= 12);
		}
		assertTrue(tight.rockets().size() > 60);
	}

	@Test
	void rocketsReachTheirBurstPoints() {
		for (ShowPlan.Rocket rocket : plan(5, 60, 9, false).rockets()) {
			double vy = ShowPlan.launchSpeed(rocket.height(), rocket.flight());
			assertTrue(vy >= ShowPlan.MIN_SPEED - 1.0E-9 && vy <= ShowPlan.MAX_SPEED + 1.0E-9, "climbs, never shoots: " + vy + " " + rocket);
			// The vanilla rocket: each tick its speed is updated (x1.15 sideways, +0.04 up), then it moves.
			double y = 0;
			double side = 0;
			double vSide = ShowPlan.sideSpeed(rocket.drift(), rocket.flight());
			for (int k = 0; k < rocket.flight(); k++) {
				vy += ShowPlan.LIFT;
				vSide *= ShowPlan.SPREAD;
				y += vy;
				side += vSide;
			}
			assertEquals(rocket.height(), y, 1.0E-6, "burst height of " + rocket);
			assertEquals(rocket.drift(), side, 1.0E-6, "burst drift of " + rocket);
			assertTrue(rocket.height() >= 10 && rocket.height() <= 26 * 1.4 + 1.0E-9, "within the sky of the show: " + rocket);
		}
		assertEquals(31, ShowPlan.flightFor(26, 0.2), "about 1.5 s to 26 blocks");
		assertTrue(ShowPlan.flightFor(60, 0.2) <= 60 && ShowPlan.launchSpeed(60, ShowPlan.flightFor(60, 0.2)) >= ShowPlan.MIN_SPEED);
		assertTrue(ShowPlan.launchSpeed(14, ShowPlan.flightFor(14, 5.0)) <= ShowPlan.MAX_SPEED, "never faster than the bound");
	}

	@Test
	void cursedShowUsesItsDarkPalettes() {
		Set<Integer> dark = new HashSet<>();
		for (ShowPlan.Palette palette : ShowPlan.CURSED_PALETTES) {
			dark.addAll(palette.colors());
			dark.addAll(palette.fades());
		}
		ShowPlan cursed = plan(13, 70, 14, true);
		Set<ShowPlan.Figure> figures = new HashSet<>();
		for (ShowPlan.Rocket rocket : cursed.rockets()) {
			for (ShowPlan.Explosion explosion : rocket.explosions()) {
				for (int color : explosion.colors()) {
					assertTrue(dark.contains(color), "cursed colour " + Integer.toHexString(color) + " in " + rocket);
				}
				for (int color : explosion.fades()) {
					assertTrue(dark.contains(color), "cursed fade " + Integer.toHexString(color) + " in " + rocket);
				}
			}
			figures.add(rocket.figure());
		}
		assertTrue(figures.contains(ShowPlan.Figure.PENTAGRAM) && !figures.contains(ShowPlan.Figure.HEART), "pentagrams, no hearts: " + figures);
		assertTrue(cursed.rockets().stream().anyMatch(r -> r.explosions().stream().anyMatch(e -> e.shape() == ShowPlan.Shape.CREEPER)),
				"bone-white skulls");
		ShowPlan normal = plan(13, 40, 9, false);
		assertTrue(cursed.rockets().size() > normal.rockets().size(), "longer and bigger");
		Set<ShowPlan.Figure> happy = new HashSet<>();
		normal.rockets().forEach(r -> happy.add(r.figure()));
		assertTrue(happy.containsAll(List.of(ShowPlan.Figure.HEART, ShowPlan.Figure.STAR, ShowPlan.Figure.CROWN, ShowPlan.Figure.WILLOW)), "figures: " + happy);
	}

	@Test
	void theOpeningWearsTheGuildColours() {
		ShowPlan plan = plan(3, 40, 9, false);
		ShowPlan.Palette opening = plan.acts().getFirst().palette();
		assertEquals(GUILD.getFirst(), opening.color(0), "banner base colour first");
		assertTrue(opening.colors().containsAll(GUILD));
		ShowPlan none = ShowPlan.build(new ShowPlan.Settings(3, 800, 9, 18, 26, false, List.of(), 40));
		assertEquals(ShowPlan.GOLD, none.acts().getFirst().palette(), "no guild: gold");
		// The opening wave goes left to right, then back.
		List<Integer> wave = new ArrayList<>();
		for (ShowPlan.Rocket rocket : plan.rockets()) {
			if (rocket.act() == ShowPlan.Act.OPENING && rocket.explosions().getFirst().trail()) {
				wave.add(rocket.point());
			}
		}
		assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), wave.subList(0, 9), "left to right: " + wave);
	}

	@Test
	void figuresHaveOutlines() {
		for (ShowPlan.Figure figure : ShowPlan.Figure.values()) {
			List<double[]> outline = ShowFigures.outline(figure);
			if (figure == ShowPlan.Figure.NONE || figure == ShowPlan.Figure.WILLOW) {
				assertTrue(outline.isEmpty(), figure + " has no outline");
				continue;
			}
			assertEquals(ShowFigures.POINTS, outline.size(), figure.name());
			for (double[] p : outline) {
				assertTrue(Math.abs(p[0]) <= 1.0001 && Math.abs(p[1]) <= 1.0001, figure + ": inside the square " + p[0] + ", " + p[1]);
			}
		}
		// The heart stands upright and is symmetric (left lobe = right lobe).
		List<double[]> heart = ShowFigures.outline(ShowPlan.Figure.HEART);
		double lowest = 1;
		for (double[] p : heart) {
			lowest = Math.min(lowest, p[1]);
			boolean mirrored = false;
			for (double[] q : heart) {
				mirrored |= Math.abs(q[0] + p[0]) < 0.12 && Math.abs(q[1] - p[1]) < 0.12;
			}
			assertTrue(mirrored, "heart point " + p[0] + ", " + p[1] + " has its mirror");
		}
		assertTrue(lowest < -0.8, "the tip of the heart points down");
	}
}
