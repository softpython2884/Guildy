package dev.guildy.unique;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * uniques.json5: valid defaults (every table, enchantment and model they reference exists in the mod resources),
 * clamping of bad values, Json5 round trip.
 */
class UniqueConfigTest {
	private static final Set<String> GUILDY_ENCHANTMENTS = Set.of("foudroiement", "vampirisme", "givre", "brasier", "echo", "seisme",
			"apesanteur", "salve", "moisson", "marque");

	@Test
	void defaultsNeedNoFix() {
		UniqueConfig cfg = new UniqueConfig();
		List<String> warnings = cfg.rebuild(id -> true);
		assertTrue(warnings.isEmpty(), "uniques.json5 defaults: " + warnings);
		assertEquals(cfg.items.size(), cfg.usable().size(), "every default item is usable");
		assertTrue(cfg.usable().size() >= 12, "about twelve uniques");
		for (String source : UniqueConfig.SOURCES) {
			UniqueConfig.DropTable table = cfg.table(source);
			assertNotNull(table, "table for " + source);
			assertFalse(table.items.isEmpty(), source + " drops something");
			assertTrue(table.chance > 0 && table.chance <= 1, source + " chance");
		}
		assertTrue(cfg.table("boss").chance < cfg.table("blood_boss").chance, "blood night bosses drop uniques more often");
		assertTrue(cfg.table("quest").chance < cfg.table("legendary_quest").chance, "legendary quests are generous");
		assertFalse(cfg.table("trade").announce, "a purchase is not announced");
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			assertTrue(def.item.startsWith("minecraft:"), def.id + " base item");
			assertFalse(def.lore.isEmpty(), def.id + " has a story");
			assertFalse(def.enchantments.isEmpty(), def.id + " is enchanted");
		}
		// Every rarity is used, mythic items only from the rarest sources.
		Set<UniqueRarity> rarities = new java.util.HashSet<>();
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			rarities.add(def.rarity());
		}
		assertEquals(Set.of(UniqueRarity.values()), rarities);
		for (String source : List.of("boss", "quest", "expedition", "trade")) {
			for (String id : cfg.table(source).items.keySet()) {
				assertTrue(cfg.item(id).rarity() != UniqueRarity.MYTHIC, "no mythic item in '" + source + "': " + id);
			}
		}
	}

	@Test
	void defaultsOnlyReferenceShippedContent() throws Exception {
		UniqueConfig cfg = new UniqueConfig();
		cfg.rebuild(id -> true);
		List<String> missing = new ArrayList<>();
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			for (String enchantment : def.enchantments.keySet()) {
				if (enchantment.startsWith("guildy:")) {
					String path = enchantment.substring("guildy:".length());
					assertTrue(GUILDY_ENCHANTMENTS.contains(path), def.id + ": unknown guildy enchantment " + enchantment);
					check(missing, "/data/guildy/enchantment/" + path + ".json");
				} else {
					assertTrue(enchantment.startsWith("minecraft:"), def.id + ": enchantment namespace " + enchantment);
				}
			}
			if (!def.model.isEmpty()) {
				check(missing, "/assets/guildy/items/" + def.model + ".json");
				check(missing, "/assets/guildy/models/item/" + def.model + ".json");
				check(missing, "/assets/guildy/textures/item/" + def.model + ".png");
			}
			if (def.power() != null) {
				assertEquals(def.power, def.power().id);
			}
		}
		for (String id : GUILDY_ENCHANTMENTS) {
			check(missing, "/data/guildy/enchantment/" + id + ".json");
		}
		for (String type : List.of("foudre", "brasier", "givre")) {
			check(missing, "/data/guildy/damage_type/" + type + ".json");
		}
		assertTrue(missing.isEmpty(), "missing resources: " + missing);
	}

	private static void check(List<String> missing, String path) throws Exception {
		try (InputStream in = UniqueConfigTest.class.getResourceAsStream(path)) {
			if (in == null) {
				missing.add(path);
			}
		}
	}

	@Test
	void badValuesAreFixed() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.customModels = "MAYBE";
		cfg.announce.rarities = new ArrayList<>(List.of("legendary", "COMMON"));
		UniqueConfig.UniqueDef bad = new UniqueConfig.UniqueDef();
		bad.id = "Not Valid!";
		UniqueConfig.UniqueDef unknownItem = new UniqueConfig.UniqueDef();
		unknownItem.id = "ghost";
		unknownItem.item = "minecraft:nope";
		UniqueConfig.UniqueDef duplicate = new UniqueConfig.UniqueDef();
		duplicate.id = "foudrelame";
		UniqueConfig.UniqueDef odd = new UniqueConfig.UniqueDef();
		odd.id = "Odd_Blade ";
		odd.item = "Iron_Sword";
		odd.rarity = "divine";
		odd.power = "teleport_everything";
		odd.model = "Bad Model";
		odd.enchantments = new java.util.LinkedHashMap<>(Map.of("Sharpness", 900, "not an id", 2));
		odd.attributes = new ArrayList<>(List.of(new UniqueConfig.AttributeEntry(), badAttribute()));
		cfg.items.addAll(List.of(bad, unknownItem, duplicate, odd));
		UniqueConfig.DropTable table = cfg.drops.get("boss");
		table.chance = 7;
		table.items.put("ghost", 5.0);
		table.items.put("odd_blade", -3.0);
		cfg.drops.put("Weird_Source", new UniqueConfig.DropTable());
		cfg.powers.stormCall.maxTargets = 500;
		cfg.powers.treeFeller.maxLogs = -2;
		cfg.powers.doubleJump.upward = Double.NaN;

		List<String> warnings = cfg.rebuild(id -> !id.contains("nope"));
		assertFalse(warnings.isEmpty());
		assertEquals("auto", cfg.customModels);
		assertTrue(cfg.announces(UniqueRarity.LEGENDARY));
		assertFalse(cfg.announces(UniqueRarity.MYTHIC));
		assertNull(cfg.item("ghost"), "unknown base item refused");
		assertNull(cfg.item("not valid!"));
		assertEquals("Foudrelame", cfg.item("foudrelame").name, "the first definition of an id wins");
		UniqueConfig.UniqueDef fixed = cfg.item("odd_blade");
		assertNotNull(fixed);
		assertEquals("minecraft:iron_sword", fixed.item);
		assertEquals(UniqueRarity.RARE, fixed.rarity());
		assertEquals("", fixed.power);
		assertNull(fixed.power());
		assertEquals("", fixed.model);
		assertEquals(Map.of("minecraft:sharpness", 255), fixed.enchantments);
		assertEquals(1, fixed.attributes.size(), "invalid attribute dropped");
		assertEquals(1.0, cfg.table("boss").chance);
		assertFalse(cfg.table("boss").items.containsKey("ghost"));
		assertEquals(0.0, (double) cfg.table("boss").items.get("odd_blade"));
		assertNotNull(cfg.table("weird_source"), "kept (with a warning) for later use");
		assertEquals(12, cfg.powers.stormCall.maxTargets);
		assertEquals(1, cfg.powers.treeFeller.maxLogs);
		assertEquals(0.1, cfg.powers.doubleJump.upward);
	}

	private static UniqueConfig.AttributeEntry badAttribute() {
		UniqueConfig.AttributeEntry entry = new UniqueConfig.AttributeEntry();
		entry.attribute = "minecraft:attack_damage";
		entry.operation = "explode";
		return entry;
	}

	@Test
	void survivesTheJson5RoundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("uniques.json5");
		UniqueConfig defaults = new UniqueConfig();
		defaults.rebuild(id -> true);
		Json5.write(file, defaults);
		UniqueConfig read = Json5.read(file, UniqueConfig.class);
		assertTrue(read.rebuild(id -> true).isEmpty());
		assertEquals(defaults.usable().size(), read.usable().size());
		for (UniqueConfig.UniqueDef def : defaults.usable()) {
			UniqueConfig.UniqueDef copy = read.item(def.id);
			assertNotNull(copy, def.id);
			assertEquals(def.name, copy.name);
			assertEquals(def.enchantments, copy.enchantments);
			assertEquals(def.attributes.size(), copy.attributes.size());
			assertEquals(def.power, copy.power);
			assertEquals(def.color, copy.color);
		}
		assertEquals(defaults.table("blood_boss").chance, read.table("blood_boss").chance);
		assertEquals(defaults.table("blood_boss").items, read.table("blood_boss").items);
		assertEquals(defaults.powers.tideCall.chainTargets, read.powers.tideCall.chainTargets);
		assertEquals(defaults.powers.excavate.blocks, read.powers.excavate.blocks);
	}

	@Test
	void cooldownsOfThePowers() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.rebuild(id -> true);
		for (UniquePower power : UniquePower.values()) {
			assertTrue(cfg.cooldownSeconds(power) >= 0);
			if (power.active) {
				assertTrue(cfg.cooldownSeconds(power) >= 1, power + " has a real cooldown");
			}
		}
		assertEquals(UniquePower.STORM_CALL, UniquePower.byId(" Storm_Call "));
		assertNull(UniquePower.byId("nope"));
		assertEquals(UniqueRarity.MYTHIC, UniqueRarity.parse("mythic"));
		assertEquals("&6&l", UniqueRarity.LEGENDARY.namePrefix());
	}
}
