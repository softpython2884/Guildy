package dev.guildy.alchemy;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Simple brewing: classes of the products, their cost, the share of a smaller batch, the choice and brewing in alchemy.json5. */
class SimpleBrewingTest {
	private static final Set<String> MAGIC = Set.of("arcanique", "clairvoyance", "satiete");

	private static AlchemyConfig defaults() {
		AlchemyConfig config = new AlchemyConfig();
		config.validate();
		return config;
	}

	private static SimpleBrewing.Kind kind(String product) {
		return SimpleBrewing.classify(product, MAGIC::contains);
	}

	private static SimpleBrewing.Cost cost(int blaze, int seconds) {
		return new SimpleBrewing.Cost(blaze, seconds);
	}

	@Test
	void classes() {
		assertEquals(new SimpleBrewing.Kind(SimpleBrewing.Base.PLAIN, PotionKey.Form.POTION), kind("healing"));
		assertEquals(new SimpleBrewing.Kind(SimpleBrewing.Base.LONG, PotionKey.Form.POTION), kind("long_swiftness"));
		assertEquals(new SimpleBrewing.Kind(SimpleBrewing.Base.STRONG, PotionKey.Form.POTION), kind("minecraft:strong_healing"));
		assertEquals(new SimpleBrewing.Kind(SimpleBrewing.Base.PLAIN, PotionKey.Form.SPLASH), kind("splash:healing"));
		assertEquals(new SimpleBrewing.Kind(SimpleBrewing.Base.STRONG, PotionKey.Form.SPLASH), kind("splash:strong_harming"));
		assertEquals(new SimpleBrewing.Kind(SimpleBrewing.Base.PLAIN, PotionKey.Form.LINGERING), kind("lingering:healing"));
		assertEquals(new SimpleBrewing.Kind(SimpleBrewing.Base.LONG, PotionKey.Form.LINGERING), kind("lingering:long_poison"));
		assertEquals(new SimpleBrewing.Kind(SimpleBrewing.Base.ELIXIR, PotionKey.Form.ELIXIR), kind("elixir:colosse"));
		assertEquals(new SimpleBrewing.Kind(SimpleBrewing.Base.MAGIC_ELIXIR, PotionKey.Form.ELIXIR), kind("elixir:arcanique"));
		assertEquals(SimpleBrewing.Base.STRONG, kind("mymod:strong_frost").base(), "another namespace: by its path");
		assertEquals(SimpleBrewing.Base.PLAIN, kind("awkward").base());
		assertEquals(SimpleBrewing.Base.PLAIN, kind("mymod:longing").base(), "only the long_ prefix makes a long potion");
		assertNull(kind("not a product"));
		assertNull(kind(null));
	}

	@Test
	void defaultCosts() {
		AlchemyConfig config = defaults();
		assertEquals(cost(1, 20), SimpleBrewing.cost("healing", config), "plain potion");
		assertEquals(cost(1, 30), SimpleBrewing.cost("long_fire_resistance", config), "long potion");
		assertEquals(cost(1, 30), SimpleBrewing.cost("strong_healing", config), "strong potion");
		assertEquals(cost(1, 30), SimpleBrewing.cost("splash:healing", config), "splash: +10 s");
		assertEquals(cost(1, 40), SimpleBrewing.cost("splash:strong_harming", config));
		assertEquals(cost(2, 40), SimpleBrewing.cost("lingering:healing", config), "lingering: +1 blaze, +20 s");
		assertEquals(cost(2, 50), SimpleBrewing.cost("lingering:long_poison", config));
		assertEquals(cost(2, 60), SimpleBrewing.cost("elixir:colosse", config), "elixir");
		assertEquals(cost(2, 60), SimpleBrewing.cost("elixir:titan", config));
		for (String magic : MAGIC) {
			assertEquals(cost(4, 90), SimpleBrewing.cost("elixir:" + magic, config), "magic elixir " + magic);
		}
		assertEquals(cost(2, 60), SimpleBrewing.cost("elixir:unknown", config), "an unknown elixir has no magic");
		assertNull(SimpleBrewing.cost("not a product", config));
		assertEquals(6, config.brewing.maxElixirsPerDay);
		assertTrue(config.brewing.simple(), "simple mode by default");
		assertEquals("minecraft:blaze_powder", config.brewing.item);
	}

	@Test
	void ownCostsReplaceTheClass() {
		AlchemyConfig config = new AlchemyConfig();
		config.brewing.products.put("minecraft:splash:strong_harming", new AlchemyConfig.CostDef(2, 45));
		config.brewing.products.put("elixir:titan", new AlchemyConfig.CostDef(3, 75));
		config.brewing.magicElixir = new AlchemyConfig.CostDef(3, 80);
		config.validate();
		assertEquals(cost(3, 75), SimpleBrewing.cost("elixir:titan", config));
		assertEquals(cost(3, 80), SimpleBrewing.cost("elixir:arcanique", config), "class cost changed");
		assertEquals(cost(1, 40), SimpleBrewing.cost("splash:strong_harming", config), "an invalid key is not a product");
		config.brewing.products.put("splash:strong_harming", new AlchemyConfig.CostDef(2, 45));
		assertEquals(cost(2, 45), SimpleBrewing.cost("SPLASH:minecraft:strong_harming", config), "canonical product");
		// A disabled magic boost: the elixir costs like the others.
		config.elixir("satiete").magicSeconds = 0;
		assertEquals(cost(2, 60), SimpleBrewing.cost("elixir:satiete", config));
	}

	@Test
	void smallerBatchesPayTheirShare() {
		SimpleBrewing.Cost magic = cost(4, 90);
		assertEquals(4, SimpleBrewing.blazeFor(magic, 3, 3));
		assertEquals(3, SimpleBrewing.blazeFor(magic, 2, 3), "8/3 rounded up");
		assertEquals(2, SimpleBrewing.blazeFor(magic, 1, 3), "4/3 rounded up");
		assertEquals(1, SimpleBrewing.blazeFor(cost(1, 20), 1, 3), "never less than one when it costs some");
		assertEquals(0, SimpleBrewing.blazeFor(cost(0, 20), 3, 3), "a free product");
		assertEquals(0, SimpleBrewing.blazeFor(magic, 0, 3));
		assertEquals(4, SimpleBrewing.blazeFor(magic, 9, 3), "never more than a full batch");
		SimpleBrewing.Choice full = new SimpleBrewing.Choice(SimpleBrewing.Verdict.BREW, "elixir:arcanique", 3, 4, magic);
		SimpleBrewing.Choice small = full.resized(1, 3);
		assertEquals(1, small.bottles());
		assertEquals(2, small.blaze());
		assertEquals(90, small.seconds(), "the time does not depend on the bottles");
	}

	private static SimpleBrewing.Choice choose(Map<String, Integer> targets, Map<String, Integer> held, int blaze, int limit, int elixirsLeft) {
		AlchemyConfig config = defaults();
		return SimpleBrewing.choose(List.copyOf(targets.keySet()), targets, held, product -> !product.equals("unknown_potion"),
				product -> SimpleBrewing.cost(product, config), blaze, 3, limit, elixirsLeft);
	}

	@Test
	void choice() {
		Map<String, Integer> targets = new LinkedHashMap<>();
		targets.put("healing", 6);
		targets.put("strength", 4);
		targets.put("elixir:arcanique", 2);
		Map<String, Integer> held = new HashMap<>(Map.of("healing", 3, "strength", 0, "elixir:arcanique", 0));
		// strength and the arcane elixir are both at 0 (menu order), healing half full.
		SimpleBrewing.Choice choice = choose(targets, held, 10, 18, 6);
		assertEquals(SimpleBrewing.Verdict.BREW, choice.verdict());
		assertEquals("strength", choice.product());
		assertEquals(3, choice.bottles());
		assertEquals(1, choice.blaze());
		assertEquals(20, choice.seconds());
		// The daily cap left makes a smaller batch.
		choice = choose(targets, held, 10, 2, 6);
		assertEquals(2, choice.bottles());
		// Without blaze powder: nothing, the most wanted product named.
		choice = choose(targets, held, 0, 18, 6);
		assertEquals(SimpleBrewing.Verdict.NO_BLAZE, choice.verdict());
		assertEquals("strength", choice.product());
		assertEquals(1, choice.blaze());
		// Only the elixir is missing: 4 blaze powder for a full batch, a cheaper product skipped to.
		held.put("strength", 4);
		held.put("healing", 6);
		choice = choose(targets, held, 3, 18, 6);
		assertEquals(SimpleBrewing.Verdict.NO_BLAZE, choice.verdict(), "3 blaze powder do not pay for a magic elixir");
		assertEquals("elixir:arcanique", choice.product());
		assertEquals(4, choice.blaze());
		held.put("healing", 5);
		choice = choose(targets, held, 3, 18, 6);
		assertEquals("healing", choice.product(), "a cheaper missing product instead");
		// The elixirs of the day: a batch of what is left of the cap, then none.
		held.put("healing", 6);
		choice = choose(targets, held, 10, 18, 1);
		assertEquals(SimpleBrewing.Verdict.BREW, choice.verdict());
		assertEquals(1, choice.bottles());
		assertEquals(2, choice.blaze());
		choice = choose(targets, held, 10, 18, 0);
		assertEquals(SimpleBrewing.Verdict.ELIXIR_CAP, choice.verdict());
		assertEquals("elixir:arcanique", choice.product());
		held.put("healing", 0);
		choice = choose(targets, held, 10, 18, 0);
		assertEquals("healing", choice.product(), "other potions after the elixirs of the day");
		// Everything held (or only unknown products missing): stocks full.
		held.put("healing", 6);
		held.put("elixir:arcanique", 2);
		assertEquals(SimpleBrewing.Verdict.STOCK_FULL, choose(targets, held, 10, 18, 6).verdict());
		targets.put("unknown_potion", 3);
		assertEquals(SimpleBrewing.Verdict.STOCK_FULL, choose(targets, held, 10, 18, 6).verdict());
	}

	@Test
	void brewingIsValidated() {
		AlchemyConfig config = new AlchemyConfig();
		assertTrue(config.clean().isEmpty(), "defaults are valid");
		config.brewing.mode = " RECIPES ";
		config.clean();
		assertEquals("recipes", config.brewing.mode);
		assertFalse(config.brewing.simple());
		config.brewing.mode = "magic";
		config.brewing.item = "Not An Item!";
		config.brewing.plainPotion = new AlchemyConfig.CostDef(-3, 0);
		config.brewing.splashExtra = new AlchemyConfig.CostDef(0, 0);
		config.brewing.magicElixir = new AlchemyConfig.CostDef(500, 5000);
		config.brewing.longPotion = null;
		config.brewing.maxElixirsPerDay = -4;
		config.brewing.products.put("water", new AlchemyConfig.CostDef(1, 10));
		config.brewing.products.put("nonsense potion", new AlchemyConfig.CostDef(1, 10));
		config.brewing.products.put("strength", null);
		config.brewing.products.put("minecraft:healing", new AlchemyConfig.CostDef(2, 0));
		List<String> warnings = config.clean();
		assertTrue(warnings.size() >= 9, "every problem reported: " + warnings);
		assertEquals("simple", config.brewing.mode, "unknown mode: simple");
		assertTrue(config.brewing.simple());
		assertEquals("minecraft:blaze_powder", config.brewing.item);
		assertEquals(0, config.brewing.plainPotion.blaze);
		assertEquals(1, config.brewing.plainPotion.seconds, "at least one second");
		assertEquals(0, config.brewing.splashExtra.seconds, "an extra may add no time");
		assertEquals(AlchemyConfig.Brewing.MAX_BLAZE, config.brewing.magicElixir.blaze);
		assertEquals(AlchemyConfig.Brewing.MAX_SECONDS, config.brewing.magicElixir.seconds);
		assertEquals(30, config.brewing.longPotion.seconds, "a missing cost takes its default");
		assertEquals(0, config.brewing.maxElixirsPerDay);
		assertEquals(Set.of("healing"), config.brewing.products.keySet(), "valid products only, canonical");
		assertEquals(1, config.brewing.products.get("healing").seconds);
		config.brewing = null;
		config.clean();
		assertTrue(config.brewing.simple(), "a missing section: simple mode");
	}

	@Test
	void anOldFileGetsSimpleBrewing(@TempDir Path dir) throws Exception {
		// alchemy.json5 of 1.12 to 1.14: no brewing section.
		AlchemyConfig old = Json5.fromString("{formatVersion: 1, maxStockTarget: 32}", AlchemyConfig.class);
		old.validate();
		assertTrue(old.brewing.simple(), "the new key takes its default: simple");
		assertEquals(32, old.maxStockTarget, "the other values are kept");
		assertEquals(4, old.brewing.magicElixir.blaze);
		Path file = dir.resolve("alchemy.json5");
		AlchemyConfig config = defaults();
		config.brewing.mode = "recipes";
		config.brewing.maxElixirsPerDay = 3;
		config.brewing.products.put("elixir:titan", new AlchemyConfig.CostDef(3, 75));
		Json5.write(file, config);
		String text = Files.readString(file);
		assertTrue(text.contains("\"brewing\": {") && text.contains("\"magicElixir\""), "the section is written: " + text.substring(0, 400));
		AlchemyConfig read = Json5.read(file, AlchemyConfig.class);
		read.validate();
		assertEquals("recipes", read.brewing.mode);
		assertEquals(3, read.brewing.maxElixirsPerDay);
		assertEquals(new SimpleBrewing.Cost(3, 75), SimpleBrewing.cost("elixir:titan", read));
		assertEquals(new SimpleBrewing.Cost(1, 30), read.brewing.strongPotion.cost());
	}
}
