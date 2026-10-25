package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PitPlanTest {
	@Test
	void ringOrderGoesAroundTheSquare() {
		int side = 6;
		assertEquals(20, PitPlan.ringLength(side));
		assertEquals(19, PitPlan.maxDepthFor(side));
		Set<Long> seen = new HashSet<>();
		int[] previous = PitPlan.ringLocal(side, 0);
		for (int i = 0; i < PitPlan.ringLength(side); i++) {
			int[] cell = PitPlan.ringLocal(side, i);
			assertTrue(cell[0] == 0 || cell[0] == side - 1 || cell[1] == 0 || cell[1] == side - 1, "on the border");
			assertTrue(seen.add(BlockPos.asLong(cell[0], 0, cell[1])), "each border column once");
			if (i > 0) {
				assertEquals(1, Math.abs(cell[0] - previous[0]) + Math.abs(cell[1] - previous[1]), "next to the previous one");
			}
			previous = cell;
		}
	}

	@Test
	void theRampClimbsOneBlockPerStep() {
		for (boolean clockwise : new boolean[]{true, false}) {
			PitPlan plan = new PitPlan(100, 200, 6, 60, 10, 7, clockwise);
			assertEquals(10, plan.depth());
			for (int i = 0; i < plan.depth(); i++) {
				BlockPos here = plan.stepFeet(i);
				BlockPos next = plan.stepFeet(i + 1);
				assertEquals(here.getY() - 1, next.getY(), "one block lower per step");
				assertEquals(1, Math.abs(here.getX() - next.getX()) + Math.abs(here.getZ() - next.getZ()), "steps side by side");
				int cell = plan.cell(here.getX(), here.getZ());
				assertEquals(i, plan.step(cell));
				assertEquals(i, plan.limit(cell), "a step is dug down to its own level only");
			}
			assertEquals(61, plan.stepFeet(0).getY(), "the entry is at ground level (nothing dug)");
			assertEquals(plan.floorFeet(plan.depth()), plan.stepFeet(plan.depth()).getY(), "the last step is level with the floor");
		}
	}

	@Test
	void levelsKeepTheStepsAbove() {
		PitPlan plan = new PitPlan(0, 0, 5, 10, 6, 2, true);
		for (int k = 1; k <= plan.depth(); k++) {
			List<BlockPos> blocks = plan.level(k);
			int expected = 0;
			for (int cell = 0; cell < plan.cells(); cell++) {
				if (plan.limit(cell) >= k) {
					expected++;
				}
			}
			assertEquals(expected, blocks.size());
			for (BlockPos pos : blocks) {
				assertEquals(plan.levelY(k), pos.getY());
				int step = plan.step(plan.cell(pos.getX(), pos.getZ()));
				assertTrue(step < 0 || step >= k, "the steps above this level stay");
			}
			// Dig order: from the step this level is reached from, outwards.
			BlockPos from = plan.stepFeet(k - 1);
			int last = -1;
			for (BlockPos pos : blocks) {
				int dist = Math.abs(pos.getX() - from.getX()) + Math.abs(pos.getZ() - from.getZ());
				assertTrue(dist >= last, "nearest to the step first");
				last = dist;
			}
		}
		assertTrue(plan.level(0).isEmpty());
		assertTrue(plan.level(plan.depth() + 1).isEmpty());
	}

	@Test
	void volumeAndBounds() {
		PitPlan plan = new PitPlan(-3, 4, 4, 0, 5, 0, true);
		int volume = plan.volume().size();
		// 16 columns x 5 levels, minus the ramp: step i keeps 5 - i levels (steps 0..5).
		assertEquals(16 * 5 - (5 + 4 + 3 + 2 + 1), volume);
		assertTrue(plan.inVolume(-2, 0, 5) || plan.step(plan.cell(-2, 5)) == 0);
		assertFalse(plan.inVolume(-4, 0, 5), "outside the footprint");
		assertFalse(plan.inVolume(-2, 1, 5), "above the ground");
		BlockPos outside = plan.entryOutside();
		assertFalse(plan.contains(outside.getX(), outside.getZ()), "the way in is just outside the pit");
		BlockPos entry = plan.stepFeet(0);
		assertEquals(1, Math.abs(outside.getX() - entry.getX()) + Math.abs(outside.getZ() - entry.getZ()));
	}

	@Test
	void depthIsCappedByTheRing() {
		PitPlan plan = new PitPlan(0, 0, 4, 0, 50, 0, true);
		assertEquals(PitPlan.maxDepthFor(4), plan.depth());
	}
}
