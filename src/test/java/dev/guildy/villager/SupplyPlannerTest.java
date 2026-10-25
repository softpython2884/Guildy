package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyPlannerTest {
	private static final SupplyPlanner.Station HAND = SupplyPlanner.Station.HAND;
	private static final SupplyPlanner.Station TABLE = SupplyPlanner.Station.TABLE;
	private static final SupplyPlanner.Station FURNACE = SupplyPlanner.Station.FURNACE;
	private static final SupplyPlanner.Station CUTTER = SupplyPlanner.Station.STONECUTTER;

	/** Fake recipe book (a few vanilla-like recipes). */
	private static final class Book implements SupplyPlanner.Book {
		final Map<String, List<SupplyPlanner.Recipe>> recipes = new HashMap<>();
		final Map<String, Double> fuels = new HashMap<>();

		Book craft(String output, int count, SupplyPlanner.Station station, Object... inputs) {
			return add(SupplyPlanner.Kind.CRAFT, output, count, station, inputs);
		}

		Book add(SupplyPlanner.Kind kind, String output, int count, SupplyPlanner.Station station, Object... inputs) {
			List<SupplyPlanner.Input> list = new ArrayList<>();
			for (int i = 0; i < inputs.length; i += 2) {
				Object options = inputs[i];
				List<String> ids = options instanceof String id ? List.of(id) : List.of((String[]) options);
				list.add(new SupplyPlanner.Input(ids, (Integer) inputs[i + 1]));
			}
			recipes.computeIfAbsent(output, k -> new ArrayList<>()).add(new SupplyPlanner.Recipe(kind + ":" + output, kind, output, count, list, station));
			return this;
		}

		Book smelt(String output, String input) {
			return add(SupplyPlanner.Kind.SMELT, output, 1, FURNACE, input, 1);
		}

		@Override
		public List<SupplyPlanner.Recipe> recipesFor(String item) {
			return recipes.getOrDefault(item, List.of());
		}

		@Override
		public double smeltsPerFuel(String item) {
			return fuels.getOrDefault(item, 0.0);
		}

		@Override
		public int maxStack(String item) {
			return 64;
		}
	}

	private static Book book() {
		Book book = new Book()
				.craft("oak_planks", 4, HAND, "oak_log", 1)
				.craft("birch_planks", 4, HAND, "birch_log", 1)
				.craft("oak_stairs", 4, TABLE, "oak_planks", 6)
				.craft("stick", 4, HAND, new String[]{"oak_planks", "birch_planks"}, 2)
				.craft("torch", 4, HAND, "stick", 1, new String[]{"coal", "charcoal"}, 1)
				.smelt("glass", "sand")
				.smelt("stone", "cobblestone")
				.smelt("charcoal", "oak_log")
				.craft("stone_stairs", 4, TABLE, "stone", 6)
				.add(SupplyPlanner.Kind.CUT, "stone_stairs", 1, CUTTER, "stone", 1)
				.craft("stone_bricks", 4, HAND, "stone", 4)
				.craft("iron_block", 1, TABLE, "iron_ingot", 9)
				.craft("iron_ingot", 9, HAND, "iron_block", 1)
				.craft("diamond_block", 1, TABLE, "diamond", 9)
				.add(SupplyPlanner.Kind.TRANSFORM, "stripped_oak_log", 1, HAND, "oak_log", 1)
				.add(SupplyPlanner.Kind.TRANSFORM, "white_concrete", 1, SupplyPlanner.Station.WATER, "white_concrete_powder", 1);
		book.fuels.put("coal", 8.0);
		book.fuels.put("charcoal", 8.0);
		book.fuels.put("oak_log", 1.5);
		book.fuels.put("oak_planks", 1.5);
		return book;
	}

	private static SupplyPlanner.Context ctx(Book book, Map<String, Integer> stock, Map<String, Integer> reserved, Set<String> gatherable) {
		return new SupplyPlanner.Context(book, stock, reserved, gatherable, List.of("coal", "charcoal", "oak_log", "oak_planks"),
				SupplyPlanner.Settings.all(5));
	}

	private static SupplyPlanner.Context ctx(Map<String, Integer> stock) {
		return ctx(book(), stock, Map.of(), Set.of());
	}

	@Test
	void stairsFromLogsThroughPlanks() {
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(Map.of("oak_log", 30)), "oak_stairs", 64);
		assertTrue(plan.ready(), "everything is in the storage");
		assertEquals(Map.of("oak_log", 24), plan.take(), "96 planks = 24 logs");
		assertEquals(2, plan.steps().size());
		assertEquals("oak_planks", plan.steps().get(0).recipe().output(), "ingredients first");
		assertEquals(24, plan.steps().get(0).times());
		assertEquals(16, plan.steps().get(1).times());
		assertEquals(64, plan.made());
		assertTrue(plan.needs(TABLE), "stairs need a crafting table");
		assertFalse(plan.needs(FURNACE));
	}

	@Test
	void extraUnitsAreLeftovers() {
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(Map.of("oak_log", 30)), "oak_stairs", 10);
		// 3 crafts (12 stairs) need 18 planks: 5 logs give 20, 2 planks left over.
		assertEquals(12, plan.made());
		assertEquals(Map.of("oak_log", 5), plan.take());
		assertEquals(Map.of("oak_planks", 2), plan.leftovers());
	}

	@Test
	void anIngredientWithOptionsMixesTheStock() {
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(Map.of("oak_planks", 1, "birch_planks", 1)), "stick", 4);
		assertTrue(plan.ready());
		assertEquals(Map.of("oak_planks", 1, "birch_planks", 1), plan.take(), "one plank of each wood");
		assertEquals(1, plan.steps().size());
	}

	@Test
	void sharedIngredientsAreCountedOnce() {
		// Torches: sticks from planks, and coal; planks also needed by nothing else: 4 planks for 8 sticks.
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(Map.of("oak_log", 1, "coal", 8)), "torch", 32);
		assertTrue(plan.ready(), "torches from a log and coal: " + plan);
		assertEquals(1, plan.take().get("oak_log"));
		assertEquals(8, plan.take().get("coal"));
		assertEquals(3, plan.steps().size(), "planks, sticks, torches");
	}

	@Test
	void wantedItemsAreNeverUsedUp() {
		Map<String, Integer> stock = Map.of("oak_planks", 64, "oak_log", 10);
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(book(), stock, Map.of("oak_planks", Integer.MAX_VALUE), Set.of()), "oak_stairs", 4);
		assertTrue(plan.ready());
		assertFalse(plan.take().containsKey("oak_planks"), "the planks are wanted too: logs are used instead");
		assertEquals(Map.of("oak_log", 2), plan.take());
	}

	@Test
	void partlyReservedStockLeavesTheReservedPart() {
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(book(), Map.of("oak_log", 10), Map.of("oak_log", 8), Set.of()), "oak_planks", 16);
		assertFalse(plan.ready(), "only 2 of the 10 logs may be used");
		assertEquals(Map.of("oak_log", 2), plan.missing(), "the raw material lacking");
		SupplyPlanner.Plan two = SupplyPlanner.plan(ctx(book(), Map.of("oak_log", 10), Map.of("oak_log", 8), Set.of()), "oak_planks", 8);
		assertTrue(two.ready());
		assertEquals(Map.of("oak_log", 2), two.take());
	}

	@Test
	void theTargetIsNeverTakenFromTheStorage() {
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(Map.of("oak_planks", 64, "oak_log", 1)), "oak_planks", 4);
		assertEquals(Map.of("oak_log", 1), plan.take(), "planks wanted: new ones are made");
	}

	@Test
	void anItemWithNoWayIsMissing() {
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(Map.of()), "diamond_block", 12);
		assertFalse(plan.possible());
		assertEquals(Map.of("diamond", 108), plan.missing(), "the raw material lacking is reported");
		SupplyPlanner.Plan nothing = SupplyPlanner.plan(ctx(Map.of()), "bedrock", 3);
		assertEquals(Map.of("bedrock", 3), nothing.missing());
	}

	@Test
	void aGatherableLeafIsGatheredFirst() {
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(book(), Map.of("coal", 8), Map.of(), Set.of("sand")), "glass", 64);
		assertTrue(plan.possible());
		assertFalse(plan.ready());
		assertEquals(Map.of("sand", 64), plan.gather());
		assertEquals("sand", plan.firstGather());
		SupplyPlanner.Plan direct = SupplyPlanner.plan(ctx(book(), Map.of(), Map.of(), Set.of("sand")), "sand", 20);
		assertEquals(Map.of("sand", 20), direct.gather(), "a natural item is gathered itself");
	}

	@Test
	void smeltingPaysWithFuel() {
		SupplyPlanner.Plan coal = SupplyPlanner.plan(ctx(Map.of("sand", 16, "coal", 5)), "glass", 16);
		assertTrue(coal.ready());
		assertEquals(Map.of("coal", 2), coal.fuel(), "8 items per coal");
		assertTrue(coal.needs(FURNACE));
		SupplyPlanner.Plan logs = SupplyPlanner.plan(ctx(book(), Map.of("sand", 8), Map.of(), Set.of("oak_log")), "glass", 8);
		assertEquals(Map.of("oak_log", 6), logs.gather(), "no fuel stocked: 6 logs to gather (1.5 items each)");
		SupplyPlanner.Plan none = SupplyPlanner.plan(ctx(Map.of("sand", 8)), "glass", 8);
		assertEquals(Map.of(SupplyPlanner.FUEL, 8), none.missing());
	}

	@Test
	void theStonecutterIsCheaperThanTheTable() {
		Map<String, Integer> stock = Map.of("stone", 200);
		SupplyPlanner.Plan cut = SupplyPlanner.plan(ctx(stock), "stone_stairs", 64);
		assertEquals(Map.of("stone", 64), cut.take(), "1 stone per stair");
		assertEquals(SupplyPlanner.Kind.CUT, cut.steps().getFirst().recipe().kind());
		assertTrue(cut.needs(CUTTER));
		SupplyPlanner.Context noCutter = ctx(stock).with(new SupplyPlanner.Settings(5, EnumSet.allOf(SupplyPlanner.Kind.class),
				EnumSet.of(HAND, TABLE, FURNACE)));
		SupplyPlanner.Plan table = SupplyPlanner.plan(noCutter, "stone_stairs", 64);
		assertEquals(Map.of("stone", 96), table.take(), "without a stonecutter: 6 stones for 4 stairs");
		assertTrue(table.needs(TABLE));
	}

	@Test
	void deepChainsGoThroughEveryLevel() {
		// Cobblestone -> stone (furnace) -> stone stairs (stonecutter).
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(Map.of("cobblestone", 64, "coal", 8)), "stone_stairs", 16);
		assertTrue(plan.ready(), plan.toString());
		assertEquals(16, plan.take().get("cobblestone"));
		assertEquals(2, plan.steps().size());
		assertEquals(SupplyPlanner.Kind.SMELT, plan.steps().get(0).recipe().kind());
	}

	@Test
	void theDepthIsLimited() {
		SupplyPlanner.Context shallow = ctx(Map.of("oak_log", 10)).with(SupplyPlanner.Settings.all(1));
		SupplyPlanner.Plan plan = SupplyPlanner.plan(shallow, "oak_stairs", 4);
		assertFalse(plan.possible(), "planks would be a second recipe level");
	}

	@Test
	void missingWorkstationsSkipTheirRecipes() {
		SupplyPlanner.Context handOnly = ctx(Map.of("oak_log", 10, "white_concrete_powder", 8)).with(
				new SupplyPlanner.Settings(5, EnumSet.allOf(SupplyPlanner.Kind.class), EnumSet.of(HAND)));
		assertTrue(SupplyPlanner.plan(handOnly, "oak_planks", 8).ready(), "planks by hand");
		assertFalse(SupplyPlanner.plan(handOnly, "oak_stairs", 4).possible(), "no crafting table");
		assertFalse(SupplyPlanner.plan(handOnly, "white_concrete", 8).possible(), "no water");
		assertTrue(SupplyPlanner.plan(handOnly, "stripped_oak_log", 4).ready(), "stripping by hand with an axe");
	}

	@Test
	void cyclesEnd() {
		SupplyPlanner.Plan ingots = SupplyPlanner.plan(ctx(Map.of("iron_block", 1)), "iron_ingot", 9);
		assertTrue(ingots.ready());
		assertEquals(Map.of("iron_block", 1), ingots.take());
		SupplyPlanner.Plan block = SupplyPlanner.plan(ctx(Map.of()), "iron_block", 1);
		assertFalse(block.possible(), "ingots from blocks from ingots... never loops");
	}

	@Test
	void bestFromStockFindsTheLargestAmount() {
		SupplyPlanner.Plan plan = SupplyPlanner.bestFromStock(ctx(Map.of("oak_log", 5)), "oak_stairs", 64, 9);
		assertNotNull(plan);
		// 5 logs = 20 planks = 3 crafts of 6 planks = 12 stairs.
		assertEquals(12, plan.amount());
		assertTrue(plan.ready());
		assertNull(SupplyPlanner.bestFromStock(ctx(Map.of()), "oak_stairs", 64, 9));
		SupplyPlanner.Plan small = SupplyPlanner.bestFromStock(ctx(Map.of("oak_log", 640)), "oak_planks", 2000, 3);
		assertNotNull(small);
		assertTrue(small.bagStacks(book()) <= 3, "fits in 3 bag slots: " + small.bagStacks(book()));
	}

	@Test
	void manyOptionsStayWithinTheBudget() {
		// 40 kinds of planks, none in stock: the search ends quickly with a missing result.
		Book book = book();
		String[] planks = new String[40];
		for (int i = 0; i < planks.length; i++) {
			planks[i] = "planks_" + i;
			book.craft(planks[i], 4, HAND, "log_" + i, 1);
			book.craft("log_" + i, 1, HAND, "wood_" + i, 1);
			book.craft("wood_" + i, 1, HAND, "bark_" + i, 1);
		}
		book.craft("chest", 1, TABLE, planks, 8);
		Set<String> none = new HashSet<>();
		long start = System.nanoTime();
		SupplyPlanner.Plan plan = SupplyPlanner.plan(ctx(book, Map.of(), Map.of(), none), "chest", 64);
		assertFalse(plan.possible());
		assertTrue(System.nanoTime() - start < 2_000_000_000L, "bounded search");
	}
}
