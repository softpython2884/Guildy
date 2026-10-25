package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PitRulesTest {
	/**
	 * Fake world: natural ground up to y = 10 (air above), wanted blocks ("sand") from y = 8 down unless set; columns can
	 * be forbidden (claims), heights changed, blocks set.
	 */
	private static final class Grid implements PitRules.Grid {
		final Map<Long, MineBlock> blocks = new HashMap<>();
		final Map<Long, Integer> heights = new HashMap<>();
		final Set<Long> forbidden = new HashSet<>();
		final Set<Long> notSource = new HashSet<>();
		int sourceTop = 8;

		Grid set(int x, int y, int z, MineBlock block) {
			blocks.put(BlockPos.asLong(x, y, z), block);
			return this;
		}

		Grid height(int x, int z, int y) {
			heights.put(BlockPos.asLong(x, 0, z), y);
			return this;
		}

		@Override
		public MineBlock at(int x, int y, int z) {
			MineBlock set = blocks.get(BlockPos.asLong(x, y, z));
			if (set != null) {
				return set;
			}
			return y <= surface(x, z) ? MineBlock.NATURAL : MineBlock.AIR;
		}

		@Override
		public boolean foreign(int x, int y, int z) {
			return false;
		}

		@Override
		public boolean sturdy(int x, int y, int z) {
			return at(x, y, z).breakable();
		}

		@Override
		public int surface(int x, int z) {
			return heights.getOrDefault(BlockPos.asLong(x, 0, z), 10);
		}

		@Override
		public boolean allowed(int x, int z) {
			return !forbidden.contains(BlockPos.asLong(x, 0, z));
		}

		@Override
		public boolean source(int x, int y, int z) {
			return y <= sourceTop && !notSource.contains(BlockPos.asLong(x, y, z)) && at(x, y, z).breakable();
		}
	}

	private static PitRules.Site evaluate(Grid grid) {
		return PitRules.evaluate(grid, 0, 0, 6, 10, 1, -50, 3);
	}

	@Test
	void flatNaturalGroundIsAccepted() {
		Grid grid = new Grid();
		PitRules.Site site = evaluate(grid);
		assertTrue(site.ok(), site.reject().toString());
		assertEquals(10, site.top());
		assertEquals(10, site.depth());
		// Levels 3..10 (y 8 .. 1) hold the wanted blocks, minus the ramp steps dug less deep.
		PitPlan plan = site.plan();
		int expected = 0;
		for (int cell = 0; cell < plan.cells(); cell++) {
			expected += Math.max(0, plan.limit(cell) - 2);
		}
		assertEquals(expected, site.score());
	}

	@Test
	void theEntryFacesTheVillage() {
		PitRules.Site site = evaluate(new Grid());
		BlockPos outside = site.plan().entryOutside();
		assertEquals(-1, outside.getX(), "the side facing the heart (west)");
		int[] local = PitPlan.ringLocal(6, site.entry());
		boolean corner = (local[0] == 0 || local[0] == 5) && (local[1] == 0 || local[1] == 5);
		assertFalse(corner, "never a corner");
	}

	@Test
	void claimsAndSpawnAreNeverDug() {
		Grid grid = new Grid();
		grid.forbidden.add(BlockPos.asLong(6, 0, 2));
		assertEquals(PitRules.Reject.NOT_ALLOWED, evaluate(grid).reject(), "the rim touches a forbidden column");
	}

	@Test
	void unevenGroundIsRefused() {
		Grid grid = new Grid().height(2, 2, 12);
		assertEquals(PitRules.Reject.UNEVEN, evaluate(grid).reject());
		Grid step = new Grid().height(2, 2, 11);
		PitRules.Site site = evaluate(step);
		assertTrue(site.ok(), "one block of difference is fine");
		assertEquals(10, site.top(), "the lowest ground of the footprint");
	}

	@Test
	void buildingsAndTreesKeepThePitAway() {
		Grid building = new Grid().set(-1, 10, 3, MineBlock.PROTECTED);
		assertEquals(PitRules.Reject.OBSTACLE, evaluate(building).reject(), "a player block on the rim");
		Grid tree = new Grid().set(3, 13, 3, MineBlock.PROTECTED);
		assertEquals(PitRules.Reject.OBSTACLE, evaluate(tree).reject(), "leaves above the pit");
		Grid torch = new Grid().set(2, 11, 2, MineBlock.FIXTURE);
		assertEquals(PitRules.Reject.OBSTACLE, evaluate(torch).reject(), "a torch on the ground");
		Grid lake = new Grid().set(6, 11, 3, MineBlock.WATER);
		assertEquals(PitRules.Reject.OBSTACLE, evaluate(lake).reject(), "water next to the pit");
		Grid plant = new Grid().set(2, 11, 2, MineBlock.AIR);
		assertTrue(evaluate(plant).ok(), "plants are fine");
	}

	@Test
	void caveAndFluidStopTheDepth() {
		Grid cave = new Grid().set(3, 5, 3, MineBlock.AIR);
		PitRules.Site site = evaluate(cave);
		assertTrue(site.ok());
		assertEquals(5, site.depth(), "level 6 (y 5) would open into the cave");
		Grid water = new Grid().set(2, 3, 2, MineBlock.WATER);
		assertEquals(7, evaluate(water).depth());
		Grid shallow = new Grid().set(3, 9, 3, MineBlock.AIR);
		assertEquals(PitRules.Reject.SHALLOW, evaluate(shallow).reject());
	}

	@Test
	void aBuriedBlockStaysAsAPillarUnlessItIsOnTheRamp() {
		Grid buried = new Grid().set(2, 6, 2, MineBlock.PROTECTED);
		PitRules.Site site = evaluate(buried);
		assertEquals(10, site.depth(), "an inner column is left as a pillar");
		PitPlan plan = site.plan();
		// A step of the ramp: the pit stops above it.
		BlockPos step = plan.stepFeet(8);
		Grid onRamp = new Grid().set(step.getX(), 3, step.getZ(), MineBlock.PROTECTED);
		PitRules.Site stopped = evaluate(onRamp);
		assertEquals(7, stopped.depth(), "level 8 (y 3) would dig the ramp's step");
	}

	@Test
	void remainingCountsOnlyWhatIsLeft() {
		Grid grid = new Grid();
		PitRules.Site site = evaluate(grid);
		PitPlan plan = site.plan();
		int all = PitRules.count(grid, plan, site.top(), 0);
		int afterFive = PitRules.count(grid, plan, site.top(), 5);
		assertEquals(site.score(), all);
		assertTrue(afterFive < all && afterFive > 0);
		assertEquals(0, PitRules.count(grid, plan, site.top(), plan.depth()));
	}
}
