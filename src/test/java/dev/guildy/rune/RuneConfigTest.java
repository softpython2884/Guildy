package dev.guildy.rune;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** runes.json5: the default runes, validation of the definitions and settings, the JSON5 round trip. */
class RuneConfigTest {
	@Test
	void defaultRunes() {
		RuneConfig config = new RuneConfig();
		assertTrue(config.clean().isEmpty(), "no warning for the defaults");
		assertEquals(RuneConfig.CURRENT_FORMAT, config.formatVersion);
		Set<String> ids = new HashSet<>();
		int common = 0;
		int rare = 0;
		List<String> mythic = new ArrayList<>();
		for (RuneConfig.RuneDef def : config.runes) {
			assertTrue(ids.add(def.id), "unique id " + def.id);
			assertTrue(RuneConfig.RUNE_ID.matcher(def.id).matches());
			assertTrue(def.name.startsWith("Rune "), def.id + " has a French name");
			assertFalse(def.glyph.isBlank());
			assertTrue(def.enabled && def.weight > 0);
			switch (def.tier()) {
				case COMMON -> {
					common++;
					assertNull(def.power(), def.id + ": a common rune is a stat buff");
					assertFalse(def.attributes.isEmpty(), def.id + " gives a bonus");
				}
				case RARE -> {
					rare++;
					assertNotNull(def.power(), def.id + ": a rare rune has a small power");
					assertFalse(def.power().mythic);
				}
				case MYTHIC -> {
					mythic.add(def.id);
					assertNotNull(def.power());
					assertTrue(def.power().mythic);
					assertEquals(List.of("chest"), def.slots);
				}
			}
			for (RuneConfig.AttributeDef attribute : def.attributes) {
				assertTrue(attribute.attribute.startsWith("minecraft:"), attribute.attribute);
				assertTrue(RuneConfig.OPERATIONS.contains(attribute.operation));
			}
		}
		assertEquals(16, common);
		assertEquals(6, rare);
		assertEquals(List.of("phenix", "dragon"), mythic, "exactly two mythic runes");
		assertEquals(List.of("minecraft:netherite_chestplate"), config.mythicItems);
		assertEquals(80, config.tiers.common);
		assertTrue(config.tiers.mythic < config.tiers.rare && config.tiers.rare < config.tiers.common, "mythic runes are the rarest");
		assertEquals("minecraft:amethyst_shard", config.inscription.materialId());
		// Every rare power and both mythic powers are used by a default rune.
		Set<RunePower> powers = new HashSet<>();
		for (RuneConfig.RuneDef def : config.runes) {
			if (def.power() != null) {
				powers.add(def.power());
			}
		}
		assertEquals(Set.of(RunePower.values()), powers);
	}

	@Test
	void invalidDefinitionsAreFixedOrDropped() {
		RuneConfig config = new RuneConfig();
		config.runes = new ArrayList<>();
		RuneConfig.RuneDef bad = new RuneConfig.RuneDef("Bad Id!", "x", "ᚠ", RuneTier.COMMON, 1, "head");
		bad.attribute("armor", 1, "add_value");
		config.runes.add(bad);
		RuneConfig.RuneDef first = new RuneConfig.RuneDef("twice", "Rune A", "ᚠ", RuneTier.COMMON, 1, "head").attribute("armor", 1, "add_value");
		RuneConfig.RuneDef second = new RuneConfig.RuneDef("twice", "Rune B", "ᚠ", RuneTier.COMMON, 1, "head").attribute("armor", 1, "add_value");
		config.runes.add(first);
		config.runes.add(second);
		RuneConfig.RuneDef odd = new RuneConfig.RuneDef("odd", "", "ᚠᚢᚦᚨ", RuneTier.COMMON, 99999, "head", "tail", "FEET");
		odd.tier = "legendary";
		odd.attribute("armor_toughness", 1, "multiply").attribute("not a valid id", 1, "add_value");
		odd.power = "teleport";
		config.runes.add(odd);
		// A mythic power on a common rune is removed; a mythic rune always goes on the chest.
		RuneConfig.RuneDef cheat = new RuneConfig.RuneDef("cheat", "Rune triche", "ᚠ", RuneTier.COMMON, 1, "chest").power(RunePower.PHOENIX);
		cheat.attribute("armor", 1, "add_value");
		config.runes.add(cheat);
		RuneConfig.RuneDef wings = new RuneConfig.RuneDef("ailes", "Rune des ailes", "ᚠ", RuneTier.MYTHIC, 1, "head", "feet").power(RunePower.DRAGON);
		config.runes.add(wings);
		RuneConfig.RuneDef empty = new RuneConfig.RuneDef("vide", "Rune vide", "ᚠ", RuneTier.RARE, 1, "head");
		config.runes.add(empty);
		config.mythicItems = new ArrayList<>(List.of("netherite_chestplate", "NOT VALID", "minecraft:netherite_chestplate"));
		config.inscription.chance = 2;
		config.inscription.maxPerDay = -3;
		config.inscription.inspectionMinutes = 0;
		config.inscription.material = "?!";
		config.tiers.mythic = -5;
		config.powers.phoenix.healthRatio = Double.NaN;
		config.powers.dragon.breathAngle = 500;

		List<String> warnings = config.clean();
		assertFalse(warnings.isEmpty());
		assertEquals(List.of("twice", "odd", "cheat", "ailes", "vide"), config.runes.stream().map(def -> def.id).toList(),
				"invalid and duplicate ids dropped");
		assertEquals("Rune A", config.rune("twice").name, "the first of two duplicates is kept");
		RuneConfig.RuneDef fixed = config.rune("odd");
		assertEquals("odd", fixed.name, "a missing name becomes the id");
		assertEquals(2, fixed.glyph.codePointCount(0, fixed.glyph.length()), "glyph cut to 2 characters");
		assertEquals("common", fixed.tier, "unknown tier: common");
		assertEquals(List.of("head", "feet"), fixed.slots, "unknown slot dropped, case normalised");
		assertEquals(10000, fixed.weight);
		assertEquals(1, fixed.attributes.size(), "invalid attribute dropped");
		assertEquals("minecraft:armor_toughness", fixed.attributes.getFirst().attribute);
		assertEquals("add_value", fixed.attributes.getFirst().operation, "unknown operation: add_value");
		assertEquals("", fixed.power, "unknown power: none");
		assertNull(config.rune("cheat").power(), "a mythic power never goes on a common rune");
		assertEquals(List.of("chest"), config.rune("ailes").slots, "a mythic rune goes on the chest");
		assertEquals(RunePower.DRAGON, config.rune("ailes").power());
		assertTrue(warnings.stream().anyMatch(w -> w.contains("vide") && w.contains("does nothing")));
		assertEquals(List.of("minecraft:netherite_chestplate"), config.mythicItems, "mythic items normalised and deduplicated");
		assertEquals(1.0, config.inscription.chance);
		assertEquals(0, config.inscription.maxPerDay);
		assertEquals(1, config.inscription.inspectionMinutes);
		assertEquals("", config.inscription.material, "invalid material: free runes");
		assertEquals("", config.inscription.materialId());
		assertEquals(0, config.tiers.mythic);
		assertEquals(0.05, config.powers.phoenix.healthRatio);
		assertEquals(90, config.powers.dragon.breathAngle);
	}

	@Test
	void nullSectionsAndListsGetDefaults() {
		RuneConfig config = new RuneConfig();
		config.inscription = null;
		config.tiers = null;
		config.powers = null;
		config.runes = null;
		config.mythicItems = null;
		config.clean();
		assertNotNull(config.inscription);
		assertNotNull(config.tiers);
		assertNotNull(config.powers.phoenix);
		assertEquals(RuneConfig.defaultRunes().size(), config.runes.size());
		assertTrue(config.mythicItems.isEmpty(), "no mythic item: the mythic runes are never drawn");
	}

	@Test
	void jsonRoundTrip(@TempDir Path dir) throws Exception {
		RuneConfig written = new RuneConfig();
		written.clean();
		written.inscription.chance = 0.4;
		written.rune("phenix").weight = 3;
		Path file = dir.resolve("runes.json5");
		Json5.write(file, written);
		String text = Files.readString(file);
		assertTrue(text.contains("// Guildy - runes"), "header comment");
		assertTrue(text.contains("Rune du Phénix"), "accents kept");
		RuneConfig read = Json5.read(file, RuneConfig.class);
		assertTrue(read.clean().isEmpty());
		assertEquals(0.4, read.inscription.chance);
		assertEquals(written.runes.size(), read.runes.size());
		assertEquals(3, read.rune("phenix").weight);
		assertEquals(written.rune("rebond").attributes.size(), read.rune("rebond").attributes.size());
		assertEquals(RunePower.BOUNCE, read.rune("rebond").power());
		// A file without the runes list gets the default runes.
		RuneConfig partial = Json5.fromString("{ enabled: true, inscription: { chance: 1 } }", RuneConfig.class);
		partial.clean();
		assertEquals(RuneConfig.defaultRunes().size(), partial.runes.size());
		assertEquals(1.0, partial.inscription.chance);
		assertEquals(2, partial.inscription.maxPerDay, "missing keys keep their defaults");
	}
}
