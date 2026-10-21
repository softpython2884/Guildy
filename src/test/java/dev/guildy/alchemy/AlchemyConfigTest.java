package dev.guildy.alchemy;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** alchemy.json5: defaults, elixir definitions, bounds and the JSON5 round trip. */
class AlchemyConfigTest {
	@Test
	void defaultElixirs() {
		AlchemyConfig config = new AlchemyConfig();
		assertTrue(config.clean().isEmpty());
		Set<String> ids = new HashSet<>();
		for (AlchemyConfig.ElixirDef def : config.elixirs) {
			assertTrue(ids.add(def.id), "unique id " + def.id);
			assertTrue(def.enabled);
			assertTrue(!def.effects.isEmpty() || def.magical(), def.id + " does something");
			assertTrue(def.name.contains("Élixir"), def.id + " is named");
			assertNotNull(def.step(), def.id + " has a brewing step");
			assertFalse(def.reagent.equals("minecraft:nether_wart"), def.id + " needs a rarer reagent");
			for (AlchemyConfig.EffectDef effect : def.effects) {
				assertTrue(effect.effectId().startsWith("minecraft:"));
				assertTrue(effect.level >= 1 && effect.level <= AlchemyConfig.MAX_EFFECT_LEVEL);
			}
		}
		assertEquals(9, config.elixirs.size());
		// The three magic elixirs of the alchemist.
		AlchemyConfig.ElixirDef arcane = config.elixir("arcanique");
		assertEquals(0.25, arcane.boost().power());
		assertEquals(300, arcane.magicSeconds);
		AlchemyConfig.ElixirDef sight = config.elixir("clairvoyance");
		assertTrue(sight.boost().cooldown() > 0 && sight.boost().channel() > 0);
		assertEquals(0.5, config.elixir("satiete").boost().food());
		assertFalse(config.elixir("colosse").magical(), "a fighting elixir has no magic boost");
		assertEquals(0x8E3B1F, config.elixir("colosse").rgb());
		assertNull(config.elixir("nope"));
		// Every elixir is listed in the stock menu, the rare ones at 0 by default.
		for (AlchemyConfig.ElixirDef def : config.elixirs) {
			assertTrue(config.menuProducts().contains(def.product()));
		}
		assertEquals(0, config.defaultTarget("elixir:abysses"), "the heart of the sea only on demand");
		assertTrue(config.defaultTarget("healing") > 0);
	}

	@Test
	void invalidEntriesAreFixedOrDropped() {
		AlchemyConfig config = new AlchemyConfig();
		config.recipes = new ArrayList<>(List.of("awkward + sugar -> swiftness", "garbage", "awkward + sugar -> water"));
		config.splashReagent = "Bad Item!";
		config.lingeringReagent = "";
		Map<String, Integer> stock = new LinkedHashMap<>();
		stock.put("minecraft:healing", 500);
		stock.put("not a product", 3);
		stock.put("water", 4);
		stock.put("splash:healing", -2);
		config.stock = stock;
		config.maxStockTarget = 20;
		AlchemyConfig.ElixirDef good = new AlchemyConfig.ElixirDef();
		good.id = " Fort ";
		good.color = "red";
		good.base = "nonsense potion";
		good.reagent = "minecraft:diamond";
		AlchemyConfig.EffectDef effect = new AlchemyConfig.EffectDef();
		effect.effect = "strength";
		effect.level = 40;
		effect.seconds = 999999;
		good.effects = new ArrayList<>(List.of(effect));
		good.spellPower = 9;
		good.magicSeconds = -5;
		AlchemyConfig.ElixirDef duplicate = new AlchemyConfig.ElixirDef();
		duplicate.id = "fort";
		AlchemyConfig.ElixirDef invalid = new AlchemyConfig.ElixirDef();
		invalid.id = "pas bon";
		config.elixirs = new ArrayList<>(List.of(good, duplicate, invalid));
		List<String> warnings = config.clean();
		assertTrue(warnings.size() >= 7, "every problem reported: " + warnings);
		assertEquals(List.of("awkward + minecraft:sugar -> swiftness"), config.recipes, "valid lines only, canonical");
		assertEquals("", config.splashReagent, "invalid splash reagent: no splash potions");
		assertEquals(Map.of("healing", 20, "splash:healing", 0), config.stock, "canonical products, bounded targets, no water");
		assertEquals(1, config.elixirs.size());
		AlchemyConfig.ElixirDef kept = config.elixirs.getFirst();
		assertEquals("fort", kept.id);
		assertEquals("#A64DFF", kept.color);
		assertEquals("awkward", kept.base);
		assertEquals(AlchemyConfig.MAX_EFFECT_LEVEL, kept.effects.getFirst().level);
		assertEquals(AlchemyConfig.MAX_EFFECT_SECONDS, kept.effects.getFirst().seconds);
		assertEquals("minecraft:strength", kept.effects.getFirst().effect);
		assertEquals(0, kept.magicSeconds);
		assertFalse(kept.magical(), "no magic time: no magic boost");
		// Without splash reagent the book has no bottle change.
		assertTrue(config.book().chains("splash:swiftness", Set.of("awkward"), 6).isEmpty());
		config.magic.display = "hologram";
		config.magic.maxCooldownReduction = 3;
		config.clean();
		assertEquals("bossbar", config.magic.display);
		assertEquals(0.95, config.magic.maxCooldownReduction);
	}

	@Test
	void survivesTheJson5RoundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("alchemy.json5");
		AlchemyConfig config = new AlchemyConfig();
		config.validate();
		config.stock.put("elixir:titan", 3);
		config.elixir("arcanique").spellPower = 0.4;
		Json5.write(file, config);
		String text = Files.readString(file);
		assertTrue(text.contains("// Version du fichier"), "commented");
		assertTrue(text.contains("Élixir arcanique"), "names kept with their accents");
		AlchemyConfig read = Json5.read(file, AlchemyConfig.class);
		read.validate();
		assertEquals(config.recipes, read.recipes);
		assertEquals(config.stock, read.stock);
		assertEquals(List.copyOf(config.stock.keySet()), List.copyOf(read.stock.keySet()), "menu order kept");
		assertEquals(0.4, read.elixir("arcanique").spellPower);
		assertEquals(config.elixirs.size(), read.elixirs.size());
		assertEquals(config.elixir("colosse").effects.size(), read.elixir("colosse").effects.size());
		assertEquals(AlchemyConfig.CURRENT_FORMAT, read.formatVersion);
	}
}
