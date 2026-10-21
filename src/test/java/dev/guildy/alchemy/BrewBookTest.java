package dev.guildy.alchemy;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Products, recipe lines, chains from a water bottle and the choice of what to brew (vanilla recipes of alchemy.json5 and
 * the elixirs).
 */
class BrewBookTest {
	private static final Set<String> WATER = Set.of("water");

	private static BrewBook defaults() {
		AlchemyConfig config = new AlchemyConfig();
		config.validate();
		return config.book();
	}

	private static List<String> reagents(List<BrewBook.Step> chain) {
		return chain.stream().map(BrewBook.Step::reagent).toList();
	}

	@Test
	void productKeys() {
		assertEquals("healing", PotionKey.canonical("minecraft:healing"));
		assertEquals("splash:strong_healing", PotionKey.canonical(" SPLASH:minecraft:strong_healing "));
		assertEquals("lingering:healing", PotionKey.canonical("lingering:healing"));
		assertEquals("elixir:arcanique", PotionKey.canonical("elixir:arcanique"));
		assertEquals("mymod:frost", PotionKey.canonical("mymod:frost"), "another namespace is kept");
		assertNull(PotionKey.canonical("elixir:Bad Id"));
		assertNull(PotionKey.canonical(""));
		assertNull(PotionKey.canonical("healing potion"));
		PotionKey key = PotionKey.parse("splash:healing");
		assertNotNull(key);
		assertEquals(PotionKey.Form.SPLASH, key.form());
		assertEquals("minecraft:healing", key.potionId());
		assertEquals("lingering:healing", key.in(PotionKey.Form.LINGERING).text());
		assertTrue(PotionKey.parse("elixir:titan").isElixir());
	}

	@Test
	void recipeLines() {
		BrewBook.Step step = BrewBook.parse("awkward + glistering_melon_slice -> healing");
		assertNotNull(step);
		assertEquals(new BrewBook.Step("awkward", "minecraft:glistering_melon_slice", "healing"), step);
		assertEquals(step, BrewBook.parse(step.text()), "the canonical text parses back");
		assertEquals("splash:healing", BrewBook.parse("healing+gunpowder->splash:healing").to());
		assertNull(BrewBook.parse("awkward -> healing"), "no reagent");
		assertNull(BrewBook.parse("awkward + melon"), "no output");
		assertNull(BrewBook.parse("healing + glowstone_dust -> healing"), "a step must change the product");
		assertNull(BrewBook.parse("awkward + sugar -> water"), "nothing brews back into water");
		assertNull(BrewBook.parse("bad potion + sugar -> swiftness"));
	}

	@Test
	void vanillaChainsFromWater() {
		BrewBook book = defaults();
		assertEquals(List.of("minecraft:nether_wart", "minecraft:glistering_melon_slice"), reagents(book.chains("healing", WATER, 6).getFirst()));
		assertEquals(List.of("minecraft:nether_wart", "minecraft:glistering_melon_slice", "minecraft:glowstone_dust"),
				reagents(book.chains("strong_healing", WATER, 6).getFirst()));
		assertEquals(List.of("minecraft:nether_wart", "minecraft:glistering_melon_slice", "minecraft:gunpowder"),
				reagents(book.chains("splash:healing", WATER, 6).getFirst()), "gunpowder makes any potion a splash potion");
		assertEquals(List.of("minecraft:nether_wart", "minecraft:glistering_melon_slice", "minecraft:gunpowder", "minecraft:dragon_breath"),
				reagents(book.chains("lingering:healing", WATER, 6).getFirst()), "lingering from the splash potion");
		assertEquals(List.of("minecraft:fermented_spider_eye"), reagents(book.chains("weakness", WATER, 6).getFirst()), "weakness straight from water");
		// Slowness: two chains of three brews (sugar or rabbit foot), the recipes' order first.
		List<List<BrewBook.Step>> slowness = book.chains("slowness", WATER, 6);
		assertTrue(slowness.size() >= 2);
		assertEquals(3, slowness.getFirst().size());
		assertEquals("minecraft:sugar", slowness.getFirst().get(1).reagent());
		assertTrue(book.chains("healing", WATER, 1).isEmpty(), "two brews needed");
		assertTrue(book.chains("luck", WATER, 6).isEmpty(), "no recipe for luck");
		// A chain may start from another bottle (in the bag, or spare in the storage).
		assertEquals(1, book.chains("strong_healing", Set.of("water", "healing"), 6).getFirst().size());
	}

	@Test
	void everyDefaultProductIsBrewable() {
		AlchemyConfig config = new AlchemyConfig();
		assertTrue(config.clean().isEmpty(), "the defaults are valid: " + config.clean());
		BrewBook book = config.book();
		for (String product : config.menuProducts()) {
			assertFalse(book.chains(product, WATER, BrewBook.MAX_STEPS).isEmpty(), product + " can be brewed from water");
		}
		// The elixirs: their base, then their reagent.
		List<BrewBook.Step> colosse = book.chains("elixir:colosse", WATER, BrewBook.MAX_STEPS).getFirst();
		assertEquals(List.of("minecraft:nether_wart", "minecraft:blaze_powder", "minecraft:glowstone_dust", "minecraft:armadillo_scute"),
				reagents(colosse));
		assertEquals(List.of("minecraft:nether_wart", "minecraft:amethyst_shard"), reagents(book.chains("elixir:arcanique", WATER, 6).getFirst()));
		assertEquals(List.of("minecraft:nether_wart", "minecraft:golden_carrot", "minecraft:echo_shard"),
				reagents(book.chains("elixir:clairvoyance", WATER, 6).getFirst()), "night vision, then the echo shard");
	}

	@Test
	void choiceFollowsTheShortages() {
		BrewBook book = defaults();
		List<String> products = List.of("healing", "strength", "fire_resistance");
		Map<String, Integer> targets = Map.of("healing", 6, "strength", 4, "fire_resistance", 4);
		Map<String, Integer> stock = new HashMap<>(Map.of("healing", 5, "strength", 0, "fire_resistance", 1));
		BrewBook.Choice choice = book.choose(products, targets, stock, WATER, chain -> true);
		assertEquals(BrewBook.Verdict.BREW, choice.verdict());
		assertEquals("strength", choice.plan().product(), "no strength at all: the furthest below its target");
		assertEquals("water", choice.plan().start());
		// Equal shortages: the menu order.
		stock.put("strength", 2);
		stock.put("fire_resistance", 2);
		assertEquals("strength", book.choose(products, targets, stock, WATER, chain -> true).plan().product());
		// What cannot be afforded is skipped (no blaze powder for strength).
		BrewBook.Choice noBlaze = book.choose(products, targets, stock, WATER,
				chain -> chain.stream().noneMatch(step -> step.reagent().equals("minecraft:blaze_powder")));
		assertEquals("fire_resistance", noBlaze.plan().product());
		// Nothing affordable: MISSING names the most wanted product.
		BrewBook.Choice none = book.choose(products, targets, stock, WATER, chain -> false);
		assertEquals(BrewBook.Verdict.MISSING, none.verdict());
		assertEquals("strength", none.wanted());
		// Every target reached (or none set): STOCK_FULL.
		Map<String, Integer> full = Map.of("healing", 6, "strength", 9, "fire_resistance", 4);
		assertEquals(BrewBook.Verdict.STOCK_FULL, book.choose(products, targets, full, WATER, chain -> true).verdict());
		assertEquals(BrewBook.Verdict.STOCK_FULL, book.choose(products, Map.of(), stock, WATER, chain -> true).verdict());
		// A product without any recipe is MISSING, never brewed.
		Map<String, Integer> luck = new LinkedHashMap<>();
		luck.put("luck", 2);
		assertEquals(BrewBook.Verdict.MISSING, book.choose(List.of("luck"), luck, Map.of(), WATER, chain -> true).verdict());
	}

	@Test
	void shortagesAndReagents() {
		assertEquals(1.0, BrewBook.shortage(4, 0));
		assertEquals(0.5, BrewBook.shortage(4, 2));
		assertEquals(0.0, BrewBook.shortage(4, 7));
		assertEquals(0.0, BrewBook.shortage(0, 0));
		List<BrewBook.Step> chain = List.of(new BrewBook.Step("water", "minecraft:a", "x"), new BrewBook.Step("x", "minecraft:b", "y"),
				new BrewBook.Step("y", "minecraft:a", "z"));
		assertEquals(Map.of("minecraft:a", 2, "minecraft:b", 1), BrewBook.reagents(chain), "one reagent per brew");
	}

	@Test
	void loopsAndDuplicatesAreHarmless() {
		BrewBook book = BrewBook.of(List.of(
				new BrewBook.Step("water", "minecraft:a", "x"),
				new BrewBook.Step("x", "minecraft:b", "y"),
				new BrewBook.Step("y", "minecraft:c", "x"),
				new BrewBook.Step("x", "minecraft:b", "y")));
		assertEquals(3, book.steps().size(), "duplicate step kept once");
		List<List<BrewBook.Step>> chains = book.chains("y", WATER, 10);
		assertEquals(1, chains.size(), "no endless loop");
		assertEquals(2, chains.getFirst().size());
	}
}
