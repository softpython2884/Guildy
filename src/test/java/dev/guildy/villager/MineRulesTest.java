package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineRulesTest {
	private static final MineLayout.Settings SETTINGS = new MineLayout.Settings(4, 32, 3, 12, 5, 5, 3, 2);

	/** Fake world: natural stone everywhere unless set; foreign columns; solid blocks are sturdy unless listed. */
	private static final class FakeGrid implements MineRules.Grid {
		private final Map<Long, MineBlock> blocks = new HashMap<>();
		private final Set<Long> foreignColumns = new HashSet<>();
		private final Set<Long> unsturdy = new HashSet<>();

		FakeGrid set(int x, int y, int z, MineBlock block) {
			blocks.put(BlockPos.asLong(x, y, z), block);
			return this;
		}

		FakeGrid foreign(int x, int z) {
			foreignColumns.add(BlockPos.asLong(x, 0, z));
			return this;
		}

		FakeGrid unsturdy(int x, int y, int z) {
			unsturdy.add(BlockPos.asLong(x, y, z));
			return this;
		}

		@Override
		public MineBlock at(int x, int y, int z) {
			return blocks.getOrDefault(BlockPos.asLong(x, y, z), MineBlock.NATURAL);
		}

		@Override
		public boolean foreign(int x, int y, int z) {
			return foreignColumns.contains(BlockPos.asLong(x, 0, z));
		}

		@Override
		public boolean sturdy(int x, int y, int z) {
			MineBlock block = at(x, y, z);
			boolean solid = block == MineBlock.NATURAL || block == MineBlock.FALLING || block == MineBlock.ORE || block == MineBlock.PROTECTED;
			return solid && !unsturdy.contains(BlockPos.asLong(x, y, z));
		}
	}

	private static MineRules.Verdict verdict(FakeGrid grid, int x, int y, int z) {
		return MineRules.dig(grid, x, y, z).verdict();
	}

	@Test
	void naturalBlocksAreDugOthersNever() {
		FakeGrid grid = new FakeGrid();
		MineRules.Check check = MineRules.dig(grid, 0, 0, 0);
		assertEquals(MineRules.Verdict.DIG, check.verdict());
		assertTrue(check.seal().isEmpty());
		grid.set(1, 0, 0, MineBlock.ORE).set(2, 0, 0, MineBlock.FALLING);
		assertEquals(MineRules.Verdict.DIG, verdict(grid, 1, 0, 0));
		assertEquals(MineRules.Verdict.DIG, verdict(grid, 2, 0, 0));
		// Planks, bricks, chests, bedrock...: never broken.
		grid.set(3, 0, 0, MineBlock.PROTECTED);
		assertEquals(MineRules.Verdict.BUILDING, verdict(grid, 3, 0, 0));
		// Torches, rails, plants: walkable, nothing to break.
		grid.set(4, 0, 0, MineBlock.FIXTURE).set(5, 0, 0, MineBlock.AIR);
		assertEquals(MineRules.Verdict.CLEAR, verdict(grid, 4, 0, 0));
		assertEquals(MineRules.Verdict.CLEAR, verdict(grid, 5, 0, 0));
	}

	@Test
	void neverUndermineSomethingBuilt() {
		FakeGrid grid = new FakeGrid();
		grid.set(0, 1, 0, MineBlock.PROTECTED);
		assertEquals(MineRules.Verdict.UNDERMINE, verdict(grid, 0, 0, 0), "a natural block carrying planks stays");
		grid.set(1, 1, 0, MineBlock.FIXTURE);
		assertEquals(MineRules.Verdict.UNDERMINE, verdict(grid, 1, 0, 0), "nor one carrying a torch or a rail");
		grid.set(2, 1, 0, MineBlock.FALLING);
		assertEquals(MineRules.Verdict.DIG, verdict(grid, 2, 0, 0), "gravel above just comes down into the tunnel");
		grid.set(3, 1, 0, MineBlock.AIR).set(4, 1, 0, MineBlock.ORE);
		assertEquals(MineRules.Verdict.DIG, verdict(grid, 3, 0, 0));
		assertEquals(MineRules.Verdict.DIG, verdict(grid, 4, 0, 0));
		// A planks block on the side does not matter (only what stands on the block).
		grid.set(6, 0, 0, MineBlock.PROTECTED);
		assertEquals(MineRules.Verdict.DIG, verdict(grid, 5, 0, 0));
	}

	@Test
	void fluidsAreSealedFirstOrRefused() {
		FakeGrid grid = new FakeGrid();
		grid.set(1, 0, 0, MineBlock.WATER).set(0, -1, 0, MineBlock.LAVA);
		MineRules.Check check = MineRules.dig(grid, 0, 0, 0);
		assertEquals(MineRules.Verdict.DIG, check.verdict());
		assertEquals(Set.of(new BlockPos(1, 0, 0), new BlockPos(0, -1, 0)), new HashSet<>(check.seal()), "side and floor fluids are sealed first");
		// Water or lava above: never opened.
		grid = new FakeGrid().set(0, 1, 0, MineBlock.WATER);
		assertEquals(MineRules.Verdict.WATER, verdict(grid, 0, 0, 0));
		grid = new FakeGrid().set(0, 1, 0, MineBlock.LAVA);
		assertEquals(MineRules.Verdict.LAVA, verdict(grid, 0, 0, 0));
		// A waterlogged block next to it cannot be sealed.
		grid = new FakeGrid().set(0, 0, 1, MineBlock.WET);
		assertEquals(MineRules.Verdict.WATER, verdict(grid, 0, 0, 0));
		// The block itself holds a fluid: never dug.
		grid = new FakeGrid().set(0, 0, 0, MineBlock.LAVA);
		assertEquals(MineRules.Verdict.LAVA, verdict(grid, 0, 0, 0));
		// Lava in another guild's land cannot be sealed by us.
		grid = new FakeGrid().set(1, 0, 0, MineBlock.LAVA).foreign(1, 0);
		assertEquals(MineRules.Verdict.FOREIGN, verdict(grid, 0, 0, 0));
	}

	@Test
	void otherGuildsAndUnloadedChunks() {
		FakeGrid grid = new FakeGrid().foreign(0, 0);
		assertEquals(MineRules.Verdict.FOREIGN, verdict(grid, 0, 5, 0));
		grid = new FakeGrid().set(0, 0, 0, MineBlock.UNLOADED);
		assertEquals(MineRules.Verdict.WAIT, verdict(grid, 0, 0, 0));
		grid = new FakeGrid().set(0, 0, 1, MineBlock.UNLOADED);
		assertEquals(MineRules.Verdict.WAIT, verdict(grid, 0, 0, 0), "a neighbour must be seen to know there is no fluid");
	}

	@Test
	void floorsAreFilledReplacedOrRefused() {
		FakeGrid grid = new FakeGrid();
		assertEquals(MineRules.FloorFix.OK, MineRules.floor(grid, 0, 0, 0).fix());
		grid.set(1, 0, 0, MineBlock.AIR).set(2, 0, 0, MineBlock.WATER).set(3, 0, 0, MineBlock.LAVA);
		assertEquals(MineRules.FloorFix.FILL, MineRules.floor(grid, 1, 0, 0).fix(), "small hole: bridged");
		assertEquals(MineRules.FloorFix.FILL, MineRules.floor(grid, 2, 0, 0).fix());
		assertEquals(MineRules.FloorFix.FILL, MineRules.floor(grid, 3, 0, 0).fix());
		grid.unsturdy(4, 0, 0);
		assertEquals(MineRules.FloorFix.REPLACE, MineRules.floor(grid, 4, 0, 0).fix(), "magma: replaced");
		grid.set(5, 0, 0, MineBlock.PROTECTED).unsturdy(5, 0, 0);
		MineRules.FloorCheck slab = MineRules.floor(grid, 5, 0, 0);
		assertEquals(MineRules.FloorFix.BLOCKED, slab.fix(), "a player's slab is never replaced");
		assertEquals(MineRules.Verdict.BUILDING, slab.reason());
		grid.set(6, 0, 0, MineBlock.WET);
		assertEquals(MineRules.Verdict.WATER, MineRules.floor(grid, 6, 0, 0).reason());
		grid.foreign(7, 0);
		assertEquals(MineRules.Verdict.FOREIGN, MineRules.floor(grid, 7, 0, 0).reason());
	}

	@Test
	void veinsFollowConnectedOresAroundTheCell() {
		FakeGrid grid = new FakeGrid();
		grid.set(1, 0, 0, MineBlock.ORE).set(2, 0, 0, MineBlock.ORE).set(3, 0, 0, MineBlock.ORE).set(3, 1, 0, MineBlock.ORE)
				.set(0, 2, 0, MineBlock.ORE).set(0, 3, 0, MineBlock.ORE)
				.set(7, 0, 0, MineBlock.ORE)
				.set(-1, 1, 0, MineBlock.ORE);
		MineLayout.Cell cell = new MineLayout.Cell(new BlockPos(0, 0, 0), 2);
		Set<Long> structure = Set.of(new BlockPos(-1, 1, 0).asLong());
		List<BlockPos> vein = MineRules.vein(grid, cell, structure, 16, 4);
		Set<BlockPos> found = new HashSet<>(vein);
		assertEquals(Set.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), new BlockPos(3, 0, 0), new BlockPos(3, 1, 0),
				new BlockPos(0, 2, 0), new BlockPos(0, 3, 0)), found, "connected ores only, never the tunnels themselves");
		assertTrue(vein.indexOf(new BlockPos(1, 0, 0)) < vein.indexOf(new BlockPos(2, 0, 0)), "nearest first: each one is exposed");
		assertEquals(3, MineRules.vein(grid, cell, structure, 3, 4).size(), "capped");
		// Reach: a long vein is followed 4 blocks away at most.
		FakeGrid line = new FakeGrid();
		for (int x = 1; x <= 8; x++) {
			line.set(x, 0, 0, MineBlock.ORE);
		}
		assertEquals(4, MineRules.vein(line, cell, Set.of(), 16, 4).size());
		assertTrue(MineRules.vein(new FakeGrid(), cell, Set.of(), 16, 4).isEmpty());
	}

	@Test
	void veinBlocksNextToFluidsOrUnderGravelAreLeft() {
		FakeGrid grid = new FakeGrid().set(1, 0, 0, MineBlock.ORE).set(2, 0, 0, MineBlock.WATER);
		assertEquals(MineRules.Verdict.WATER, MineRules.digVein(grid, 1, 0, 0), "no sealing for a vein: left in place");
		grid = new FakeGrid().set(1, 0, 0, MineBlock.ORE).set(1, 1, 0, MineBlock.FALLING);
		assertEquals(MineRules.Verdict.CAVE_IN, MineRules.digVein(grid, 1, 0, 0), "gravel would fall on the miner");
		grid = new FakeGrid().set(1, 0, 0, MineBlock.ORE).set(1, 1, 0, MineBlock.PROTECTED);
		assertEquals(MineRules.Verdict.UNDERMINE, MineRules.digVein(grid, 1, 0, 0));
		grid = new FakeGrid().set(1, 0, 0, MineBlock.ORE);
		assertEquals(MineRules.Verdict.DIG, MineRules.digVein(grid, 1, 0, 0));
	}

	@Test
	void staircaseOrientationAvoidsBuildingsAndObstacles() {
		BlockPos fanion = new BlockPos(0, 70, 0);
		int[] wide = {-200, -200, 200, 200};
		FakeGrid grid = new FakeGrid();
		// Fanion and the air above the ground.
		for (int x = -8; x <= 8; x++) {
			for (int z = -8; z <= 8; z++) {
				for (int y = 70; y <= 75; y++) {
					grid.set(x, y, z, MineBlock.AIR);
				}
			}
		}
		grid.set(0, 70, 0, MineBlock.PROTECTED);
		// A planks block buried in the 4th step of the spirals going north first (orientations 0 and 4).
		MineLayout.Cell blockedStep = MineLayout.stairCell(fanion, 0, 4, 3);
		assertEquals(blockedStep.feet(), MineLayout.stairCell(fanion, 4, 4, 3).feet());
		grid.set(blockedStep.feet().getX(), blockedStep.feet().getY() + 1, blockedStep.feet().getZ(), MineBlock.PROTECTED);
		// A chest on the ground next to the first step of the spirals going east first (orientations 1 and 5).
		grid.set(1, 70, -1, MineBlock.PROTECTED);
		int best = -1;
		MineRules.Score bestScore = null;
		MineRules.Score[] scores = new MineRules.Score[MineLayout.ORIENTATIONS];
		for (int orientation = 0; orientation < MineLayout.ORIENTATIONS; orientation++) {
			MineLayout layout = MineLayout.build(fanion, orientation, 40, wide, false, SETTINGS);
			MineRules.Score score = MineRules.score(grid, layout, 8, 64);
			scores[orientation] = score;
			if (bestScore == null || score.betterThan(bestScore)) {
				best = orientation;
				bestScore = score;
			}
		}
		assertEquals(3, scores[0].clearSteps(), "blocked at the 4th step");
		assertEquals(3, scores[4].clearSteps());
		assertTrue(scores[1].buildingsNear() > 0);
		assertEquals(30, scores[2].clearSteps());
		assertEquals(0, scores[2].buildingsNear());
		assertTrue(Set.of(2, 3, 6, 7).contains(best), "south or west first, away from the chest and the planks (" + best + ")");
		assertEquals(30, bestScore.clearSteps());
		// Water next to a step is avoided too.
		FakeGrid wet = new FakeGrid();
		MineLayout.Cell step = MineLayout.stairCell(fanion, 2, 4, 5);
		wet.set(step.feet().getX(), step.feet().getY() - 1, step.feet().getZ(), MineBlock.WATER);
		assertEquals(5, MineRules.score(wet, MineLayout.build(fanion, 2, 40, wide, false, SETTINGS), 8, 64).clearSteps());
	}

	@Test
	void galleryLevels() {
		assertEquals(-54, MineRules.clampLevel(-54, 70, -64, 4));
		assertEquals(-59, MineRules.clampLevel(-70, 70, -64, 4), "5 blocks above the bottom of the world");
		assertEquals(66, MineRules.clampLevel(80, 70, -64, 4), "never less than 4 blocks under the fanion");
		assertEquals(-58, MineRules.clampLevel(-54, -58, -64, 4), "fanion right above the bedrock: its own height");
		assertEquals(-54, MineRules.defaultTarget(ZoneObjective.ORES, 70, -54, 16, 12));
		assertEquals(58, MineRules.defaultTarget(ZoneObjective.STONE, 70, -54, 16, 12));
		assertEquals(16, MineRules.defaultTarget(ZoneObjective.MIXED, 70, -54, 16, 12));
		assertEquals(Integer.MIN_VALUE, MineRules.lowestReachable(70, -1));
		assertEquals(60, MineRules.lowestReachable(70, 10), "step 10 blocked: the last step dug stands at 60");
	}

	@Test
	void nextLevelGoesDeeperFirstThenHigher() {
		assertEquals(OptionalInt.of(-54), MineRules.nextLevel(-54, List.of(), 8, -59, 66));
		assertEquals(OptionalInt.of(-46), MineRules.nextLevel(-54, List.of(-54), 8, -59, 66), "no room below: up");
		assertEquals(OptionalInt.of(-38), MineRules.nextLevel(-54, List.of(-54, -46), 8, -59, 66));
		assertEquals(OptionalInt.of(8), MineRules.nextLevel(16, List.of(16), 8, -59, 66), "deeper first");
		assertEquals(OptionalInt.of(24), MineRules.nextLevel(16, List.of(16, 8), 8, -59, 66));
		assertEquals(OptionalInt.empty(), MineRules.nextLevel(10, List.of(10, 2, 18), 8, 0, 20), "every level done");
		assertEquals(OptionalInt.empty(), MineRules.nextLevel(10, List.of(), 8, 30, 20));
		assertEquals(OptionalInt.of(-59), MineRules.nextLevel(-100, List.of(), 8, -59, 66), "out of range: nearest allowed level");
		assertFalse(MineRules.nextLevel(-54, List.of(-54), 8, -54, -54).isPresent());
	}
}
