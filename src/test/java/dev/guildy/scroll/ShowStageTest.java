package dev.guildy.scroll;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stage of the Grand spectacle: a line perpendicular to the caster's view, behind them, as wide as asked, evenly spaced,
 * ordered from the audience's left to right, its ends curved toward the audience; never two points too close.
 */
class ShowStageTest {
	private static final double EPS = 1.0E-9;

	@Test
	void lineIsPerpendicularToTheView() {
		double[][] looks = {{0, 1}, {1, 0}, {0.6, -0.8}, {-1, -1}, {3, 0.5}};
		for (double[] look : looks) {
			ShowStage.Layout layout = ShowStage.layout(10, -4, look[0], look[1], 18, 9);
			String at = "look " + look[0] + ", " + look[1];
			double fx = layout.forwardX();
			double fz = layout.forwardZ();
			assertEquals(1, Math.hypot(fx, fz), EPS, at);
			assertEquals(1, Math.hypot(layout.rightX(), layout.rightZ()), EPS, at);
			assertEquals(0, fx * layout.rightX() + fz * layout.rightZ(), EPS, at + ": right is perpendicular to the view");
			assertEquals(10 - fx * ShowStage.BACK, layout.centerX(), EPS, at + ": behind the caster");
			assertEquals(-4 - fz * ShowStage.BACK, layout.centerZ(), EPS, at);
			List<ShowStage.Spot> spots = layout.spots();
			assertEquals(9, spots.size(), at);
			assertEquals(18, layout.width(), EPS);
			assertEquals(2.25, layout.spacing(), EPS, at);
			double previous = Double.NEGATIVE_INFINITY;
			for (ShowStage.Spot spot : spots) {
				double dx = spot.x() - layout.centerX();
				double dz = spot.z() - layout.centerZ();
				double lateral = dx * layout.rightX() + dz * layout.rightZ();
				double depth = dx * fx + dz * fz;
				assertEquals(spot.u() * 9, lateral, EPS, at + ": evenly spread along the line");
				assertEquals(ShowStage.ARC * 18 * spot.u() * spot.u(), depth, EPS, at + ": the ends curve toward the audience");
				assertTrue(lateral > previous, at + ": left to right");
				if (previous > Double.NEGATIVE_INFINITY) {
					assertEquals(2.25, lateral - previous, EPS, at + ": same gap");
				}
				previous = lateral;
				assertTrue(Math.hypot(spot.x() - 10, spot.z() + 4) >= 2.5, at + ": the caster never stands on a circle");
			}
			assertEquals(-1, spots.getFirst().u(), EPS);
			assertEquals(1, spots.getLast().u(), EPS);
		}
	}

	@Test
	void leftAndRightAsTheAudienceSeesThem() {
		// The caster faces south (+z): the audience faces north, their right hand is east (+x).
		ShowStage.Layout south = ShowStage.layout(0, 0, 0, 1, 18, 9);
		assertTrue(south.spots().getFirst().x() < south.spots().getLast().x(), "west to east");
		assertEquals(-ShowStage.BACK, south.centerZ(), EPS, "north of the caster");
		// The caster faces east (+x): the audience faces west, their right hand is north (-z).
		ShowStage.Layout east = ShowStage.layout(0, 0, 1, 0, 18, 9);
		assertTrue(east.spots().getFirst().z() > east.spots().getLast().z(), "south to north");
		assertEquals(-1, east.rightZ(), EPS);
		// Looking straight up or down: south.
		ShowStage.Layout up = ShowStage.layout(0, 0, 0, 0, 18, 9);
		assertEquals(1, up.forwardZ(), EPS);
	}

	@Test
	void neverTwoPointsTooClose() {
		assertEquals(9, ShowStage.count(18, 9));
		assertEquals(15, ShowStage.count(32, 15));
		assertEquals(6, ShowStage.count(8, 15), "8 blocks hold 6 points 1.5 blocks apart");
		assertEquals(1, ShowStage.count(0, 9));
		ShowStage.Layout narrow = ShowStage.layout(0, 0, 0, 1, 8, 15);
		assertEquals(6, narrow.spots().size());
		assertTrue(narrow.spacing() >= ShowStage.MIN_SPACING - EPS);
		ShowStage.Layout single = ShowStage.layout(5, 5, 0, 1, 18, 1);
		assertEquals(0, single.spots().getFirst().u(), EPS);
		assertEquals(single.centerX(), single.spots().getFirst().x(), EPS);
	}

	@Test
	void ranksFromTheMiddle() {
		assertEquals(0, ShowStage.fromMiddle(4, 9));
		assertEquals(4, ShowStage.fromMiddle(0, 9));
		assertEquals(4, ShowStage.fromMiddle(8, 9));
		assertEquals(0, ShowStage.fromMiddle(3, 8));
		assertEquals(0, ShowStage.fromMiddle(4, 8));
		assertEquals(3, ShowStage.fromMiddle(7, 8));
	}
}
