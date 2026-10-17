package dev.guildy.ruin;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ruins.json5: defaults, validation, generic guardian, new default ruins, threat and resurrection settings, round trip. */
class RuinConfigTest {
	private static final List<String> SEVEN = List.of("crypte_oubliee", "tour_du_mage", "ziggourat_des_sables", "bastion_de_givre",
			"autel_infernal", "citadelle_des_ames", "temple_maya");

	private static List<String> rebuild(RuinConfig cfg) {
		return cfg.rebuild(id -> true, id -> true, id -> true);
	}

	@Test
	void defaultsNeedNoFix() {
		RuinConfig cfg = new RuinConfig();
		List<String> warnings = rebuild(cfg);
		assertTrue(warnings.isEmpty(), "ruins.json5 defaults: " + warnings);
		assertEquals(RuinConfig.CURRENT_VERSION, cfg.configVersion);
		assertEquals(SEVEN, new ArrayList<>(cfg.ruins.keySet()));
		Set<Integer> tiers = new HashSet<>();
		for (Map.Entry<String, RuinConfig.RuinDef> entry : cfg.ruins.entrySet()) {
			RuinConfig.RuinDef def = entry.getValue();
			String id = entry.getKey();
			tiers.add(def.tier);
			assertFalse(def.name.isBlank(), id + " name");
			assertFalse(def.boss.name.isBlank(), id + " boss name");
			assertTrue(def.boss.entity.startsWith("minecraft:"), id + " vanilla boss");
			assertFalse(def.minions.types.isEmpty(), id + " escort");
			assertTrue(def.abilities.size() >= 3, id + " has several abilities");
			assertTrue(def.abilities.stream().anyMatch(a -> a.type.equals("enrage")), id + " enrages");
			assertTrue(def.abilities.stream().filter(a -> !a.type.equals("enrage")).count() >= 3, id + " has 3 active abilities");
			assertEquals(7, def.respawnDays, id + " back after a week");
			assertTrue(def.rewards.goldMax <= 24 && def.rewards.goldMin >= 1, id + " modest gold");
			assertTrue(def.rewards.lootTable.startsWith("guildy:chests/ruin/"), id + " ruin loot table");
			assertTrue(def.rewards.items.stream().noneMatch(i -> i.item.equals("minecraft:gold_block")), id + " never gold blocks");
			assertTrue(def.boss.health >= 200, id + " is a boss");
		}
		assertEquals(Set.of(1, 2, 3, 4, 5), tiers, "tiers 1 to 5");
		assertTrue(cfg.ruins.get("citadelle_des_ames").boss.health > cfg.ruins.get("crypte_oubliee").boss.health, "tier 5 > tier 1");
		assertTrue(cfg.ruins.get("autel_infernal").rewards.lootTable.contains("nether"), "Nether loot for Nether ruins");
		Set<String> types = new HashSet<>();
		for (RuinConfig.RuinDef def : cfg.ruins.values()) {
			def.abilities.forEach(a -> types.add(a.type));
		}
		assertEquals(RuinConfig.ABILITIES, types, "every ability is used by a default ruin");
	}

	@Test
	void brokenValuesAreFixed() {
		RuinConfig cfg = new RuinConfig();
		RuinConfig.RuinDef def = new RuinConfig.RuinDef();
		def.tier = 9;
		def.resetSeconds = 0;
		def.respawnDays = -3;
		def.bossBarColor = "orange";
		def.boss.entity = "minecraft:unicorn";
		def.boss.scale = 50;
		def.boss.knockbackResistance = 4;
		def.boss.equipment.put("tail", "minecraft:diamond_sword");
		def.minions.types.put("minecraft:unicorn", 3);
		def.minions.types.put("zombie", 2);
		RuinConfig.AbilityDef unknown = new RuinConfig.AbilityDef();
		unknown.type = "meteor";
		RuinConfig.AbilityDef ring = new RuinConfig.AbilityDef();
		ring.type = "FIRE_RING";
		ring.style = "rainbow";
		ring.radius = 500;
		ring.healthBelow = 3;
		def.abilities = new ArrayList<>(List.of(unknown, ring));
		def.rewards.goldMin = 10;
		def.rewards.goldMax = 2;
		cfg.ruins.put("Ma_Ruine", def);
		cfg.cartography.researchMinutes = -1;
		cfg.cartography.overworldCost.put("minecraft:unicorn_horn", 2);
		cfg.encounter.maxActive = 0;
		List<String> warnings = cfg.rebuild(id -> !id.contains("unicorn"), id -> !id.contains("unicorn"), id -> true);
		assertFalse(warnings.isEmpty());
		RuinConfig.RuinDef fixed = cfg.ruins.get("ma_ruine");
		assertNotNull(fixed, "ids are lower case");
		assertEquals(5, fixed.tier);
		assertEquals(2, fixed.resetSeconds);
		assertEquals(0, fixed.respawnDays);
		assertEquals("RED", fixed.bossBarColor);
		assertEquals("minecraft:zombie", fixed.boss.entity, "unknown guardian replaced");
		assertEquals(4, fixed.boss.scale);
		assertEquals(1, fixed.boss.knockbackResistance);
		assertTrue(fixed.boss.equipment.isEmpty(), "unknown slot dropped");
		assertEquals(Map.of("minecraft:zombie", 2), fixed.minions.types, "unknown escort dropped, ids normalized");
		assertEquals(1, fixed.abilities.size(), "unknown ability dropped");
		assertEquals("fire_ring", fixed.abilities.getFirst().type);
		assertEquals("", fixed.abilities.getFirst().style);
		assertEquals(32, fixed.abilities.getFirst().radius);
		assertEquals(1, fixed.abilities.getFirst().healthBelow);
		assertEquals(10, fixed.rewards.goldMax, "goldMax >= goldMin");
		assertEquals(0.05, cfg.cartography.researchMinutes);
		assertFalse(cfg.cartography.overworldCost.containsKey("minecraft:unicorn_horn"));
		assertEquals(1, cfg.encounter.maxActive);
		assertEquals("Ma_Ruine".toLowerCase(), new ArrayList<>(cfg.ruins.keySet()).getLast());
	}

	@Test
	void unknownRuinsGetAGenericTierGuardian() {
		RuinConfig cfg = new RuinConfig();
		rebuild(cfg);
		assertFalse(cfg.isKnown("ruine_inconnue"));
		RuinConfig.RuinDef overworld = cfg.def("ruine_inconnue", false);
		RuinConfig.RuinDef nether = cfg.def("ruine_inconnue", true);
		assertEquals(cfg.generic.overworldTier, overworld.tier);
		assertEquals(cfg.generic.netherTier, nether.tier);
		assertEquals("minecraft:wither_skeleton", nether.boss.entity);
		assertTrue(nether.boss.health > overworld.boss.health, "higher tier, stronger guardian");
		assertSame(overworld, cfg.def("autre_ruine", false), "generic guardians are cached");
		assertSame(cfg.ruins.get("tour_du_mage"), cfg.def("tour_du_mage", true), "known ids use their own definition");
		for (int tier = 1; tier <= 5; tier++) {
			RuinConfig.RuinDef def = RuinDefaults.generic(tier, tier >= 4);
			List<String> warnings = new ArrayList<>();
			RuinConfig.fixDef("generic", def, id -> true, id -> true, id -> true, warnings);
			assertTrue(warnings.isEmpty(), "generic tier " + tier + ": " + warnings);
		}
	}

	@Test
	void summonSettings() {
		RuinConfig cfg = new RuinConfig();
		assertTrue(rebuild(cfg).isEmpty());
		RuinConfig.SummonSettings s = cfg.summon;
		assertEquals("scroll", s.mode, "the guardians wait for an incantation scroll");
		assertEquals(RuinConfig.DEFAULT_INCANTATION_ITEM, s.item);
		assertEquals(16, s.maxStack);
		assertEquals(4.0, s.drainHealth);
		assertEquals(4.0, s.minHealth);
		assertTrue(s.ritualSeconds >= 6 && s.ritualSeconds <= 8, "a 6 to 8 second ritual");
		assertEquals(1, cfg.cartography.incantationsPerResearch);

		RuinConfig broken = new RuinConfig();
		broken.summon.mode = "telepathy";
		broken.summon.item = "minecraft:unicorn_scroll";
		broken.summon.maxStack = 500;
		broken.summon.channelSeconds = -1;
		broken.summon.arenaRadius = 1;
		broken.summon.ritualSeconds = 999;
		broken.summon.drainHealth = -4;
		broken.summon.minHealth = 0;
		broken.summon.particleBudget = 1;
		broken.summon.tradeChance = 7;
		broken.cartography.incantationsPerResearch = -2;
		List<String> warnings = broken.rebuild(id -> true, id -> !id.contains("unicorn"), id -> true);
		assertEquals(2, warnings.size(), "mode and item reported: " + warnings);
		assertEquals("scroll", broken.summon.mode);
		assertEquals(RuinConfig.DEFAULT_INCANTATION_ITEM, broken.summon.item);
		assertEquals(64, broken.summon.maxStack);
		assertEquals(0.25, broken.summon.channelSeconds);
		assertEquals(4, broken.summon.arenaRadius);
		assertEquals(30, broken.summon.ritualSeconds);
		assertEquals(0, broken.summon.drainHealth, "no blood price");
		assertEquals(1, broken.summon.minHealth, "the ritual never kills");
		assertEquals(100, broken.summon.particleBudget);
		assertEquals(1, broken.summon.tradeChance);
		assertEquals(0, broken.cartography.incantationsPerResearch);

		RuinConfig proximity = new RuinConfig();
		proximity.summon.mode = " Proximity ";
		assertTrue(rebuild(proximity).isEmpty());
		assertEquals("proximity", proximity.summon.mode);
		proximity.summon = null;
		assertTrue(rebuild(proximity).isEmpty(), "missing section: defaults");
		assertEquals("scroll", proximity.summon.mode);
	}

	@Test
	void newDefaultRuinsAreAddedOnce() {
		Map<String, RuinConfig.RuinDef> ruins = new LinkedHashMap<>();
		ruins.put("crypte_oubliee", RuinDefaults.ruins().get("crypte_oubliee"));
		List<String> known = new ArrayList<>(List.of("crypte_oubliee"));
		List<String> added = RuinConfig.mergeNewDefaults(ruins, known);
		assertEquals(SEVEN.subList(1, 7), added);
		assertEquals(SEVEN, new ArrayList<>(ruins.keySet()));
		ruins.remove("tour_du_mage");
		assertTrue(RuinConfig.mergeNewDefaults(ruins, known).isEmpty(), "removed by the admin: never added back");
		assertFalse(ruins.containsKey("tour_du_mage"));
	}

	@Test
	void theFeatheredSerpentTemple() {
		RuinConfig cfg = new RuinConfig();
		assertTrue(rebuild(cfg).isEmpty());
		RuinConfig.RuinDef temple = cfg.ruins.get("temple_maya");
		assertNotNull(temple, "7th ruin");
		assertEquals("Temple du Serpent à plumes", temple.name);
		assertEquals(3, temple.tier);
		assertEquals("GREEN", temple.bossBarColor);
		assertEquals("minecraft:breeze", temple.boss.entity, "a giant breeze");
		assertTrue(temple.boss.name.contains("Serpent à plumes"));
		assertTrue(temple.boss.scale >= 1.8, "scaled up");
		assertTrue(temple.boss.equipment.isEmpty(), "a breeze wears nothing");
		assertEquals(Set.of("minecraft:bogged", "minecraft:cave_spider"), temple.minions.types.keySet(), "jungle escort");
		assertTrue(temple.abilities.stream().anyMatch(a -> a.type.equals("fire_ring") && a.style.equals("jade")), "jade ring");
		assertTrue(temple.abilities.stream().anyMatch(a -> a.type.equals("volley") && a.style.equals("wind")), "wind volley");
		assertTrue(temple.abilities.stream().anyMatch(a -> a.type.equals("blink")), "blink");
		RuinConfig.RuinDef bastion = cfg.ruins.get("bastion_de_givre");
		assertEquals(bastion.tier, temple.tier, "the other tier-3 ruin");
		assertEquals(bastion.rewards.goldMin, temple.rewards.goldMin, "same gold as the tier-3 ruin");
		assertEquals(bastion.rewards.goldMax, temple.rewards.goldMax);
		assertEquals(bastion.rewards.xp, temple.rewards.xp);
		assertEquals(bastion.rewards.lootTable, temple.rewards.lootTable);
		assertEquals(-1, temple.fastKillSeconds, "the resurrection formula");

		// A ruins.json5 written before the temple: it is added once, never again once removed.
		RuinConfig old = Json5.fromString("{ \"knownDefaults\": [\"crypte_oubliee\", \"tour_du_mage\", \"ziggourat_des_sables\", \"bastion_de_givre\","
				+ " \"autel_infernal\", \"citadelle_des_ames\"], \"ruins\": { \"crypte_oubliee\": { \"name\": \"Ma crypte\", \"tier\": 1 } } }", RuinConfig.class);
		assertTrue(rebuild(old).isEmpty());
		assertEquals(List.of("crypte_oubliee", "temple_maya"), new ArrayList<>(old.ruins.keySet()), "only the new default ruin is added");
		assertEquals("Ma crypte", old.ruins.get("crypte_oubliee").name, "the admin's ruins are untouched");
		old.ruins.remove("temple_maya");
		assertTrue(rebuild(old).isEmpty());
		assertFalse(old.ruins.containsKey("temple_maya"), "removed by the admin: never added back");
	}

	@Test
	void threatAndResurrectionSettings() {
		RuinConfig cfg = new RuinConfig();
		assertTrue(rebuild(cfg).isEmpty());
		assertTrue(cfg.escalation.enabled);
		assertEquals(10, cfg.escalation.maxLevel);
		assertEquals(0.2, cfg.escalation.healthPerLevel);
		assertEquals(0.1, cfg.escalation.damagePerLevel);
		assertEquals(2, cfg.escalation.minionsEveryLevels);
		assertEquals(0.05, cfg.escalation.cooldownPerLevel);
		assertTrue(cfg.resurrection.enabled);
		assertEquals(45, cfg.resurrection.fastKillSeconds);
		assertEquals(15, cfg.resurrection.fastKillPerTier);
		assertEquals(5, cfg.resurrection.fastKillPerThreat);
		assertTrue(cfg.resurrection.durationSeconds >= 4 && cfg.resurrection.durationSeconds <= 6, "about five seconds");
		assertEquals(1.0, cfg.resurrection.healthRatio, "back at full health");

		RuinConfig broken = new RuinConfig();
		broken.escalation.maxLevel = 0;
		broken.escalation.healthPerLevel = -2;
		broken.escalation.minCooldownFactor = 0;
		broken.escalation.uniqueChancePerLevel = 3;
		broken.resurrection.fastKillSeconds = -10;
		broken.resurrection.durationSeconds = 600;
		broken.resurrection.healthRatio = 0;
		broken.resurrection.scale = 9;
		broken.resurrection.particleBudget = 1;
		broken.resurrection.bonusLootRolls = 99;
		broken.ruins.get("crypte_oubliee").fastKillSeconds = -7;
		broken.ruins.get("tour_du_mage").fastKillSeconds = 99_999;
		assertTrue(rebuild(broken).isEmpty());
		assertEquals(1, broken.escalation.maxLevel);
		assertEquals(0, broken.escalation.healthPerLevel);
		assertEquals(0.1, broken.escalation.minCooldownFactor);
		assertEquals(1, broken.escalation.uniqueChancePerLevel);
		assertEquals(0, broken.resurrection.fastKillSeconds);
		assertEquals(15, broken.resurrection.durationSeconds);
		assertEquals(0.1, broken.resurrection.healthRatio);
		assertEquals(2, broken.resurrection.scale);
		assertEquals(100, broken.resurrection.particleBudget);
		assertEquals(10, broken.resurrection.bonusLootRolls);
		assertEquals(-1, broken.ruins.get("crypte_oubliee").fastKillSeconds, "negative = the formula");
		assertEquals(3600, broken.ruins.get("tour_du_mage").fastKillSeconds);

		RuinConfig missing = new RuinConfig();
		missing.escalation = null;
		missing.resurrection = null;
		assertTrue(rebuild(missing).isEmpty(), "missing sections: defaults");
		assertEquals(10, missing.escalation.maxLevel);
		assertTrue(missing.resurrection.enabled);
	}

	@Test
	void forcedDefinitionsAreDeepCopies() {
		RuinConfig cfg = new RuinConfig();
		rebuild(cfg);
		RuinConfig.RuinDef original = cfg.ruins.get("temple_maya");
		RuinConfig.RuinDef copy = original.copy();
		assertEquals(original.boss.entity, copy.boss.entity);
		assertEquals(original.minions.types, copy.minions.types);
		assertEquals(original.abilities.size(), copy.abilities.size());
		copy.boss.health = 1;
		copy.minions.types.clear();
		copy.abilities.getFirst().damage = 99;
		assertEquals(360, original.boss.health, "the configured guardian is untouched");
		assertFalse(original.minions.types.isEmpty());
		assertFalse(original.abilities.getFirst().damage == 99);
	}

	@Test
	void roundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("ruins.json5");
		RuinConfig written = new RuinConfig();
		rebuild(written);
		written.ruins.get("crypte_oubliee").respawnDays = 3;
		written.summon.mode = "proximity";
		written.summon.drainHealth = 6;
		Json5.write(file, written);
		RuinConfig read = Json5.read(file, RuinConfig.class);
		assertTrue(rebuild(read).isEmpty());
		assertEquals(written.ruins.keySet(), read.ruins.keySet());
		assertEquals(3, read.ruins.get("crypte_oubliee").respawnDays);
		RuinConfig.RuinDef a = written.ruins.get("citadelle_des_ames");
		RuinConfig.RuinDef b = read.ruins.get("citadelle_des_ames");
		assertEquals(a.boss.name, b.boss.name);
		assertEquals(a.boss.equipment, b.boss.equipment);
		assertEquals(a.abilities.size(), b.abilities.size());
		assertEquals(a.minions.types, b.minions.types);
		assertEquals(written.cartography.overworldCost, read.cartography.overworldCost);
		assertEquals(written.knownDefaults, read.knownDefaults);
		assertEquals("proximity", read.summon.mode);
		assertEquals(6, read.summon.drainHealth);
		assertEquals(written.cartography.incantationsPerResearch, read.cartography.incantationsPerResearch);
	}
}
