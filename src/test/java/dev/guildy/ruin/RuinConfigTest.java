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
	private static final List<String> EIGHT = List.of("crypte_oubliee", "tour_du_mage", "ziggourat_des_sables", "bastion_de_givre",
			"autel_infernal", "citadelle_des_ames", "temple_maya", "grand_temple_maya");

	private static List<String> rebuild(RuinConfig cfg) {
		return cfg.rebuild(id -> true, id -> true, id -> true);
	}

	@Test
	void defaultsNeedNoFix() {
		RuinConfig cfg = new RuinConfig();
		List<String> warnings = rebuild(cfg);
		assertTrue(warnings.isEmpty(), "ruins.json5 defaults: " + warnings);
		assertEquals(RuinConfig.CURRENT_VERSION, cfg.configVersion);
		assertEquals(EIGHT, new ArrayList<>(cfg.ruins.keySet()));
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
			assertTrue(def.rewards.goldMin >= 15 && def.rewards.goldMax <= 100 && def.rewards.goldMax > def.rewards.goldMin,
					id + " gold worth the fight, never a fortune: " + def.rewards.goldMin + "-" + def.rewards.goldMax);
			assertEquals(1, def.rewards.goldPerEscort, id + " one ingot per escort mob killed");
			assertEquals(12, def.rewards.escortGoldMax, id + " escort gold capped");
			assertTrue(def.rewards.lootTable.startsWith("guildy:chests/ruin/"), id + " ruin loot table");
			assertTrue(def.rewards.items.stream().noneMatch(i -> i.item.equals("minecraft:gold_block")), id + " never gold blocks");
			assertTrue(def.boss.health >= 200, id + " is a boss");
			assertEquals(-1, def.guards.count, id + " guards by tier");
			assertFalse(def.guards.name.isBlank(), id + " named guards");
			assertTrue(def.guards.healthMultiplier >= 1, id + " sturdy guards");
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
		def.rewards.goldPerEscort = -4;
		def.rewards.escortGoldMax = 100_000;
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
		assertEquals(RuinConfig.MAX_SCALE, fixed.boss.scale);
		assertEquals(1, fixed.boss.knockbackResistance);
		assertTrue(fixed.boss.equipment.isEmpty(), "unknown slot dropped");
		assertEquals(Map.of("minecraft:zombie", 2), fixed.minions.types, "unknown escort dropped, ids normalized");
		assertEquals(1, fixed.abilities.size(), "unknown ability dropped");
		assertEquals("fire_ring", fixed.abilities.getFirst().type);
		assertEquals("", fixed.abilities.getFirst().style);
		assertEquals(32, fixed.abilities.getFirst().radius);
		assertEquals(1, fixed.abilities.getFirst().healthBelow);
		assertEquals(10, fixed.rewards.goldMax, "goldMax >= goldMin");
		assertEquals(0, fixed.rewards.goldPerEscort);
		assertEquals(576, fixed.rewards.escortGoldMax);
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
		assertEquals(EIGHT.subList(1, 8), added);
		assertEquals(EIGHT, new ArrayList<>(ruins.keySet()));
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
		assertEquals(Set.of("minecraft:bogged", "minecraft:parched", "minecraft:cave_spider"), temple.minions.types.keySet(), "the temple's undead");
		assertEquals(Set.of("minecraft:bogged", "minecraft:parched", "minecraft:zombie", "minecraft:cave_spider"), temple.guards.types.keySet(),
				"undead on guard");
		assertEquals("minecraft:turtle_helmet", temple.guards.equipment.get("head"), "jade-green helmets");
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
		assertEquals(List.of("crypte_oubliee", "temple_maya", "grand_temple_maya"), new ArrayList<>(old.ruins.keySet()),
				"only the new default ruins are added");
		assertEquals("Ma crypte", old.ruins.get("crypte_oubliee").name, "the admin's ruins are untouched");
		old.ruins.remove("temple_maya");
		assertTrue(rebuild(old).isEmpty());
		assertFalse(old.ruins.containsKey("temple_maya"), "removed by the admin: never added back");
	}

	@Test
	void theCelestialJaguarTemple() {
		RuinConfig cfg = new RuinConfig();
		assertTrue(rebuild(cfg).isEmpty());
		RuinConfig.RuinDef temple = cfg.ruins.get("grand_temple_maya");
		assertNotNull(temple, "8th ruin");
		assertEquals("Grand Temple du Jaguar céleste", temple.name);
		assertEquals(5, temple.tier);
		assertEquals("YELLOW", temple.bossBarColor);
		assertEquals("minecraft:ocelot", temple.boss.entity, "a giant ocelot");
		assertTrue(temple.boss.name.contains("Jaguar céleste"));
		assertTrue(temple.boss.scale >= 4 && temple.boss.scale <= 5.5, "about x4-5: " + temple.boss.scale);
		// 0.6 x 0.7 ocelot: about 3 blocks wide and 3.5 high, in a 29 x 29 x 15 arena (like the giant ravager before).
		assertTrue(0.6 * temple.boss.scale <= 3.3 && 0.7 * temple.boss.scale <= 4.0, "fits the arena");
		assertTrue(temple.boss.speed > 0.2, "fast");
		assertEquals("minecraft:cat", temple.boss.cursedForm.entity, "the cursed form: a cat");
		assertEquals("minecraft:all_black", temple.boss.cursedForm.variant, "... all black");
		assertTrue(temple.boss.cursedForm.name.contains("obsidienne"), temple.boss.cursedForm.name);
		assertTrue(temple.leashRadius >= 20, "a large indoor arena");
		assertEquals(Set.of("minecraft:bogged", "minecraft:parched", "minecraft:cave_spider"), temple.minions.types.keySet(), "the temple's undead");
		assertEquals(Set.of("minecraft:bogged", "minecraft:parched", "minecraft:zombie", "minecraft:cave_spider"), temple.guards.types.keySet(),
				"undead jaguar warriors on guard");
		assertEquals("minecraft:golden_helmet", temple.guards.equipment.get("head"));
		assertTrue(temple.guards.name.contains("jaguar"));
		Set<String> styles = new HashSet<>();
		Set<String> types = new HashSet<>();
		temple.abilities.forEach(a -> {
			styles.add(a.style);
			types.add(a.type);
		});
		assertTrue(styles.containsAll(Set.of("solar", "gold", "jade")), "solar, gold and jade abilities: " + styles);
		assertTrue(types.containsAll(Set.of("pounce", "claw_sweep", "stalk", "roar", "spirit_jaguars")), "jaguar attacks: " + types);

		// Its cursed form: a copy with the black cat, the normal guardian untouched.
		RuinConfig.RuinDef cursed = temple.cursedForm();
		assertEquals("minecraft:cat", cursed.boss.entity);
		assertEquals("minecraft:all_black", cursed.boss.variant);
		assertTrue(cursed.boss.name.contains("obsidienne"));
		assertEquals(temple.abilities.size(), cursed.abilities.size(), "same abilities");
		assertEquals("minecraft:ocelot", temple.boss.entity, "the spotted ocelot stays the normal guardian");
		assertSame(cfg.ruins.get("crypte_oubliee"), cfg.ruins.get("crypte_oubliee").cursedForm(), "no cursed form: the same guardian");

		RuinConfig.RuinDef citadel = cfg.ruins.get("citadelle_des_ames");
		assertEquals(citadel.tier, temple.tier, "the other tier-5 ruin");
		assertTrue(temple.boss.health > citadel.boss.health, "the strongest guardian");
		assertTrue(temple.rewards.goldMin > citadel.rewards.goldMin && temple.rewards.goldMax > citadel.rewards.goldMax, "more gold");
		assertTrue(temple.rewards.xp > citadel.rewards.xp, "more XP");
		assertTrue(temple.rewards.lootRolls > citadel.rewards.lootRolls, "more loot rolls");
		assertEquals(-1, temple.fastKillSeconds, "the resurrection formula");

		// A ruins.json5 written before the 8th ruin: it is added once.
		RuinConfig old = Json5.fromString("{ \"knownDefaults\": [\"crypte_oubliee\", \"tour_du_mage\", \"ziggourat_des_sables\", \"bastion_de_givre\","
				+ " \"autel_infernal\", \"citadelle_des_ames\", \"temple_maya\"], \"ruins\": { \"temple_maya\": { \"name\": \"Mon temple\", \"tier\": 3 } } }",
				RuinConfig.class);
		assertTrue(rebuild(old).isEmpty());
		assertEquals(List.of("temple_maya", "grand_temple_maya"), new ArrayList<>(old.ruins.keySet()));
		assertEquals(-1, old.ruins.get("temple_maya").guards.count, "an older ruin definition gets the default guards");
		assertEquals(RuinDefaults.serpentGuards(), old.ruins.get("temple_maya").guards.types, "... the temple's undead (empty types migrated)");
	}

	@Test
	void noRaiderInTheMayanTemples() {
		RuinConfig cfg = new RuinConfig();
		assertTrue(rebuild(cfg).isEmpty());
		Set<String> raiders = Set.of("minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:illusioner", "minecraft:ravager",
				"minecraft:witch", "minecraft:vex");
		for (String id : List.of("temple_maya", "grand_temple_maya")) {
			RuinConfig.RuinDef def = cfg.ruins.get(id);
			Set<String> all = new HashSet<>(def.minions.types.keySet());
			// Room guards: their own types, the escort's when empty.
			all.addAll((def.guards.types.isEmpty() ? def.minions.types : def.guards.types).keySet());
			for (String raider : raiders) {
				assertFalse(all.contains(raider), id + " has no " + raider + ": " + all);
			}
			assertTrue(all.contains("minecraft:bogged") && all.contains("minecraft:parched"), id + ": bogged and parched");
		}
		assertTrue(cfg.ruins.get("ziggourat_des_sables").minions.types.containsKey("minecraft:parched"), "parched archers in the ziggurat");
		assertTrue(cfg.ruins.get("tour_du_mage").guards.types.containsKey("minecraft:vindicator"), "the mage tower keeps its illagers");
		assertTrue(cfg.ruins.get("bastion_de_givre").minions.types.containsKey("minecraft:stray"), "strays in the frost bastion");
	}

	@Test
	void olderDefaultsAreMigrated() {
		// A ruins.json5 of Guildy 1.8 (configVersion 1) still on its defaults, with a few values changed by the admin.
		RuinConfig written = new RuinConfig();
		written.configVersion = 1;
		written.knownDefaults = new ArrayList<>(EIGHT);
		RuinConfig.RuinDef ziggurat = written.ruins.get("ziggourat_des_sables");
		ziggurat.minions.types = new LinkedHashMap<>(Map.of("minecraft:husk", 1));
		RuinConfig.RuinDef serpent = written.ruins.get("temple_maya");
		serpent.minions.types = new LinkedHashMap<>(Map.of("minecraft:bogged", 3, "minecraft:cave_spider", 2));
		serpent.guards.types.clear();
		serpent.guards.equipment.clear();
		RuinConfig.RuinDef jaguar = written.ruins.get("grand_temple_maya");
		jaguar.boss.entity = "minecraft:ravager";
		jaguar.boss.scale = 1.6;
		jaguar.boss.speed = 0;
		jaguar.boss.cursedForm = new RuinConfig.CursedFormDef();
		jaguar.abilities = new ArrayList<>(RuinMigration.oldJaguarAbilities());
		jaguar.minions.types = new LinkedHashMap<>(Map.of("minecraft:vindicator", 3, "minecraft:bogged", 2, "minecraft:evoker", 1));
		jaguar.guards.types = new LinkedHashMap<>(Map.of("minecraft:evoker", 1, "minecraft:vindicator", 4, "minecraft:bogged", 2));
		// Changed by the admin: kept.
		written.ruins.get("autel_infernal").minions.types = new LinkedHashMap<>(Map.of("minecraft:husk", 1));
		RuinConfig old = Json5.fromString(Json5.GSON.toJson(written), RuinConfig.class);
		assertTrue(rebuild(old).isEmpty());
		assertEquals(RuinConfig.CURRENT_VERSION, old.configVersion);
		assertEquals(RuinDefaults.zigguratEscort(), old.ruins.get("ziggourat_des_sables").minions.types);
		assertEquals(RuinDefaults.serpentEscort(), old.ruins.get("temple_maya").minions.types);
		assertEquals(RuinDefaults.serpentGuards(), old.ruins.get("temple_maya").guards.types);
		assertEquals(RuinDefaults.JADE_HELMET, old.ruins.get("temple_maya").guards.equipment.get("head"));
		RuinConfig.RuinDef migrated = old.ruins.get("grand_temple_maya");
		assertEquals("minecraft:ocelot", migrated.boss.entity, "the ravager becomes the giant ocelot");
		assertEquals(RuinDefaults.JAGUAR_SCALE, migrated.boss.scale);
		assertEquals(RuinDefaults.JAGUAR_SPEED, migrated.boss.speed);
		assertEquals("minecraft:cat", migrated.boss.cursedForm.entity);
		assertTrue(RuinMigration.sameAbilities(migrated.abilities, RuinDefaults.jaguarAbilities()), "the jaguar attacks");
		assertEquals(RuinDefaults.jaguarEscort(), migrated.minions.types);
		assertEquals(RuinDefaults.jaguarGuards(), migrated.guards.types, "order ignored");
		assertEquals(Map.of("minecraft:husk", 1), old.ruins.get("autel_infernal").minions.types, "the admin's escort is kept");
		assertEquals(7, old.migrations().size(), "every migrated value reported: " + old.migrations());

		// Changed by the admin: nothing is touched.
		RuinConfig custom = Json5.fromString(Json5.GSON.toJson(written), RuinConfig.class);
		custom.ruins.get("grand_temple_maya").boss.scale = 2.0;
		custom.ruins.get("grand_temple_maya").abilities.getFirst().damage = 30;
		custom.ruins.get("grand_temple_maya").minions.types.put("minecraft:pillager", 1);
		custom.ruins.get("temple_maya").guards.equipment.put("head", "minecraft:iron_helmet");
		assertTrue(rebuild(custom).isEmpty());
		assertEquals("minecraft:ravager", custom.ruins.get("grand_temple_maya").boss.entity, "a boss changed by the admin is kept whole");
		assertEquals(2.0, custom.ruins.get("grand_temple_maya").boss.scale);
		assertEquals(30, custom.ruins.get("grand_temple_maya").abilities.getFirst().damage);
		assertTrue(custom.ruins.get("grand_temple_maya").minions.types.containsKey("minecraft:pillager"));
		assertTrue(custom.ruins.get("temple_maya").guards.types.isEmpty(), "guards with an equipment of the admin are kept");

		// Already migrated (version 2): never again, even on the old values.
		RuinConfig current = Json5.fromString(Json5.GSON.toJson(written), RuinConfig.class);
		current.configVersion = RuinConfig.CURRENT_VERSION;
		assertTrue(rebuild(current).isEmpty());
		assertEquals("minecraft:ravager", current.ruins.get("grand_temple_maya").boss.entity);
		assertTrue(current.migrations().isEmpty());

		// A fresh file: the defaults need no migration.
		RuinConfig fresh = new RuinConfig();
		assertTrue(rebuild(fresh).isEmpty());
		assertTrue(fresh.migrations().isEmpty(), "fresh defaults: " + fresh.migrations());
	}

	@Test
	void goldGrowsWithTheTier() {
		RuinConfig cfg = new RuinConfig();
		assertTrue(rebuild(cfg).isEmpty());
		int[] lastMin = new int[6];
		int[] lastMax = new int[6];
		for (int tier = 1; tier <= 5; tier++) {
			RuinConfig.RuinDef generic = RuinDefaults.generic(tier, false);
			assertEquals(RuinDefaults.GOLD_MIN[tier - 1], generic.rewards.goldMin, "generic guardian of tier " + tier);
			assertEquals(RuinDefaults.GOLD_MAX[tier - 1], generic.rewards.goldMax);
			lastMin[tier] = generic.rewards.goldMin;
			lastMax[tier] = generic.rewards.goldMax;
			if (tier > 1) {
				assertTrue(lastMin[tier] > lastMin[tier - 1] && lastMax[tier] > lastMax[tier - 1], "more gold at tier " + tier);
			}
		}
		for (Map.Entry<String, RuinConfig.RuinDef> entry : cfg.ruins.entrySet()) {
			RuinConfig.RuinDef def = entry.getValue();
			if (!entry.getKey().equals("grand_temple_maya")) {
				assertEquals(RuinDefaults.GOLD_MIN[def.tier - 1], def.rewards.goldMin, entry.getKey() + ": the gold of its tier");
				assertEquals(RuinDefaults.GOLD_MAX[def.tier - 1], def.rewards.goldMax, entry.getKey());
			}
			int[] old = RuinMigration.OLD_GOLD.get(entry.getKey());
			double ratio = (def.rewards.goldMin + def.rewards.goldMax) / (double) (old[0] + old[1]);
			assertTrue(ratio >= 3.0 && ratio <= 3.5, entry.getKey() + ": about three times the gold of 1.9, x" + ratio);
		}
		// One victory over the tier-1 guardian pays more than a level-1 quest, a tier-5 one about a third of a tier-4 upgrade.
		assertTrue(cfg.ruins.get("crypte_oubliee").rewards.goldMin >= 15);
		assertTrue(cfg.ruins.get("grand_temple_maya").rewards.goldMax <= 100);
	}

	@Test
	void olderGoldIsMigrated() {
		// A ruins.json5 of Guildy 1.9 (format 2) still on its gold defaults, two ranges changed by the admin.
		RuinConfig written = new RuinConfig();
		written.configVersion = 2;
		written.knownDefaults = new ArrayList<>(EIGHT);
		for (Map.Entry<String, int[]> old : RuinMigration.OLD_GOLD.entrySet()) {
			written.ruins.get(old.getKey()).rewards.goldMin = old.getValue()[0];
			written.ruins.get(old.getKey()).rewards.goldMax = old.getValue()[1];
		}
		written.ruins.get("autel_infernal").rewards.goldMax = 40;
		written.ruins.get("crypte_oubliee").rewards.goldMin = 5;
		RuinConfig old = Json5.fromString(Json5.GSON.toJson(written), RuinConfig.class);
		assertTrue(rebuild(old).isEmpty());
		assertEquals(RuinConfig.CURRENT_VERSION, old.configVersion);
		Map<String, RuinConfig.RuinDef> defaults = RuinDefaults.ruins();
		for (String id : List.of("tour_du_mage", "ziggourat_des_sables", "bastion_de_givre", "citadelle_des_ames", "temple_maya",
				"grand_temple_maya")) {
			assertEquals(defaults.get(id).rewards.goldMin, old.ruins.get(id).rewards.goldMin, id + ": new gold");
			assertEquals(defaults.get(id).rewards.goldMax, old.ruins.get(id).rewards.goldMax, id);
		}
		assertEquals(60, old.ruins.get("grand_temple_maya").rewards.goldMin, "the jaguar: 60 to 90 instead of 18 to 28");
		assertEquals(90, old.ruins.get("grand_temple_maya").rewards.goldMax);
		assertEquals(10, old.ruins.get("autel_infernal").rewards.goldMin, "a range changed by the admin is kept whole");
		assertEquals(40, old.ruins.get("autel_infernal").rewards.goldMax);
		assertEquals(5, old.ruins.get("crypte_oubliee").rewards.goldMin);
		assertEquals(8, old.ruins.get("crypte_oubliee").rewards.goldMax);
		assertEquals(6, old.migrations().size(), "every migrated range reported: " + old.migrations());
		assertTrue(old.migrations().contains("grand_temple_maya.rewards.gold"), old.migrations().toString());
		assertEquals(1, old.ruins.get("tour_du_mage").rewards.goldPerEscort, "escort gold: new values, defaults");

		// Already migrated (version 3): never again.
		RuinConfig current = Json5.fromString(Json5.GSON.toJson(written), RuinConfig.class);
		current.configVersion = RuinConfig.CURRENT_VERSION;
		assertTrue(rebuild(current).isEmpty());
		assertEquals(18, current.ruins.get("grand_temple_maya").rewards.goldMin, "values of the admin since version 3");
		assertTrue(current.migrations().isEmpty());

		// A file of 1.8 (version 1) gets both migrations.
		RuinConfig older = Json5.fromString(Json5.GSON.toJson(written), RuinConfig.class);
		older.configVersion = 1;
		assertTrue(rebuild(older).isEmpty());
		assertEquals(28, older.ruins.get("bastion_de_givre").rewards.goldMin, "28 to 42");
		assertEquals(42, older.ruins.get("bastion_de_givre").rewards.goldMax);
		assertTrue(older.migrations().contains("bastion_de_givre.rewards.gold"));
	}

	@Test
	void guardSettings() {
		RuinConfig cfg = new RuinConfig();
		assertTrue(rebuild(cfg).isEmpty());
		RuinConfig.GuardSettings g = cfg.guards;
		assertTrue(g.enabled);
		assertTrue(g.blockRitual, "the incantation waits for the guards");
		assertEquals(List.of(2, 3, 4, 5, 6), g.countByTier);
		assertEquals(8, g.triggerMargin);
		assertTrue(cfg.generic.guards);

		RuinConfig broken = new RuinConfig();
		broken.guards.countByTier = new ArrayList<>(List.of(3, 99));
		broken.guards.triggerMargin = -5;
		broken.guards.leashRadius = 0;
		broken.guards.max = 500;
		RuinConfig.RuinDef def = broken.ruins.get("crypte_oubliee");
		def.guards.count = -9;
		def.guards.types.put("minecraft:unicorn", 2);
		def.guards.types.put("husk", 1);
		def.guards.healthMultiplier = 0;
		def.guards.equipment.put("tail", "minecraft:iron_axe");
		def.guards.name = null;
		List<String> warnings = broken.rebuild(id -> !id.contains("unicorn"), id -> true, id -> true);
		assertEquals(2, warnings.size(), "unknown guard entity and slot: " + warnings);
		assertEquals(List.of(3, 32, 32, 32, 32), broken.guards.countByTier, "five tiers, the last value repeated, bounded");
		assertEquals(0, broken.guards.triggerMargin);
		assertEquals(3, broken.guards.leashRadius);
		assertEquals(32, broken.guards.max);
		assertEquals(-1, def.guards.count);
		assertEquals(java.util.Map.of("minecraft:husk", 1), def.guards.types);
		assertEquals(0.1, def.guards.healthMultiplier);
		assertTrue(def.guards.equipment.isEmpty());
		assertEquals("", def.guards.name);

		RuinConfig missing = new RuinConfig();
		missing.guards = null;
		missing.ruins.get("crypte_oubliee").guards = null;
		assertTrue(rebuild(missing).isEmpty(), "missing sections: defaults");
		assertTrue(missing.guards.enabled);
		assertEquals(-1, missing.ruins.get("crypte_oubliee").guards.count);
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
