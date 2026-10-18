package dev.guildy.unique;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * uniques.json5 format 4: the seven uniques of the ruins (definitions, powers, drop sources), the favourite weapons of the
 * guardians (context of a source), the migration that adds them once to older files, the banner look of the shield.
 */
class UniqueRuinUniquesTest {
	private static final double EPSILON = 1e-9;

	private static UniqueConfig defaults() {
		UniqueConfig cfg = new UniqueConfig();
		assertTrue(cfg.rebuild(id -> true).isEmpty(), "defaults need no fix");
		return cfg;
	}

	@Test
	void sevenNewUniquesMakeACollectionOfTwentyOne() {
		UniqueConfig cfg = defaults();
		assertEquals(21, cfg.usable().size(), "14 + 7 uniques");
		assertEquals(7, UniqueConfig.RUIN_UNIQUES.size());
		Map<UniqueRarity, Integer> rarities = new EnumMap<>(UniqueRarity.class);
		Set<UniquePower> powers = new HashSet<>();
		for (String id : UniqueConfig.RUIN_UNIQUES) {
			UniqueConfig.UniqueDef def = cfg.item(id);
			assertNotNull(def, id);
			assertFalse(def.lore.isEmpty(), id + " has a story");
			assertTrue(def.enchantments.size() >= 2, id + " is well enchanted");
			assertFalse(def.attributes.isEmpty(), id + " has attributes");
			assertEquals(id, def.model, id + " has its texture");
			assertNotNull(def.power(), id + " has a power");
			assertTrue(powers.add(def.power()), id + ": a power of its own");
			rarities.merge(def.rarity(), 1, Integer::sum);
		}
		assertEquals(1, rarities.getOrDefault(UniqueRarity.MYTHIC, 0), "one mythic");
		assertTrue(rarities.getOrDefault(UniqueRarity.LEGENDARY, 0) >= 2 && rarities.getOrDefault(UniqueRarity.EPIC, 0) >= 2,
				"epic and legendary ones: " + rarities);
		// No power is shared with the fourteen older uniques.
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			if (!UniqueConfig.RUIN_UNIQUES.contains(def.id) && def.power() != null) {
				assertFalse(powers.contains(def.power()), def.id + " shares a power");
			}
		}
		Set<String> bases = new HashSet<>();
		for (String id : UniqueConfig.RUIN_UNIQUES) {
			bases.add(cfg.item(id).item);
		}
		assertTrue(bases.containsAll(List.of("minecraft:netherite_spear", "minecraft:shield", "minecraft:diamond_helmet", "minecraft:mace",
				"minecraft:leather_chestplate")), "varied base items: " + bases);
		assertEquals("minecraft:netherite_spear", cfg.item("lance_du_jaguar_celeste").item);
		assertTrue(cfg.item("lance_du_jaguar_celeste").power().active, "the pounce is the spear's charge (right click)");
	}

	@Test
	void ruinUniquesDropFromTheRuinsAndRarelyFromLegendaryContracts() {
		UniqueConfig cfg = defaults();
		for (String id : UniqueConfig.RUIN_UNIQUES) {
			double ruins = UniqueRolls.probability(cfg.table("ruin_boss"), id, key -> true)
					+ UniqueRolls.probability(cfg.table("nether_ruin_boss"), id, key -> true);
			assertTrue(ruins > 0, id + " drops from the ruin guardians");
			double contract = UniqueRolls.probability(cfg.table("legendary_quest"), id, key -> true);
			assertTrue(contract > 0 && contract < 0.02, id + ": rarely from a legendary contract (" + contract + ")");
			for (String source : List.of("boss", "blood_boss", "quest", "expedition", "trade")) {
				assertFalse(cfg.table(source).items.containsKey(id), id + " never in '" + source + "' (never sold, not a horde drop)");
			}
			boolean favoured = false;
			for (Map<String, Double> favorite : cfg.favorites.values()) {
				favoured |= favorite.getOrDefault(id, 0.0) > 0;
			}
			assertTrue(favoured, id + " is the favourite of a guardian");
		}
	}

	@Test
	void eachGuardianFavoursItsOwnWeapon() {
		UniqueConfig cfg = defaults();
		UniqueConfig.DropTable base = cfg.table("ruin_boss");
		double total = base.items.values().stream().mapToDouble(Double::doubleValue).sum();
		assertEquals(54.5, total, EPSILON, "ruin_boss weights");
		double plain = UniqueRolls.probability(base, "lance_du_jaguar_celeste", key -> true);
		assertEquals(0.12 * 2 / 54.5, plain, EPSILON);
		UniqueConfig.DropTable jaguar = cfg.effectiveTable("ruin_boss@grand_temple_maya");
		assertEquals(base.chance, jaguar.chance, "the table's chance does not change");
		assertEquals(0.12 * 27 / 79.5, UniqueRolls.probability(jaguar, "lance_du_jaguar_celeste", key -> true), EPSILON);
		assertEquals(0.12 * 6 / 79.5, UniqueRolls.probability(jaguar, "croc_de_givre", key -> true), EPSILON, "the others weigh less");
		assertEquals(2.0, base.items.get("lance_du_jaguar_celeste"), "the base table is never modified");

		// Cursed guardians favour the cursed blade, on top of their own ruin's favourite.
		UniqueConfig.DropTable cursed = cfg.effectiveTable("ruin_boss@bastion_de_givre,cursed");
		assertEquals(0.12 * 30.5 / 109.5, UniqueRolls.probability(cursed, "lame_du_pacte", key -> true), EPSILON);
		assertEquals(0.12 * 28 / 109.5, UniqueRolls.probability(cursed, "couronne_du_roi_de_givre", key -> true), EPSILON);
		double plainBlade = UniqueRolls.probability(base, "lame_du_pacte", key -> true);
		assertTrue(UniqueRolls.probability(cursed, "lame_du_pacte", key -> true) > 20 * plainBlade, "the blade of the cursed guardians");
		UniqueConfig.DropTable netherCursed = cfg.effectiveTable("nether_ruin_boss@cursed");
		assertTrue(UniqueRolls.probability(netherCursed, "lame_du_pacte", key -> true) > 0.08, "Nether cursed guardians too");
		assertTrue(UniqueRolls.probability(cfg.effectiveTable("nether_ruin_boss@citadelle_des_ames"), "egide_des_ames", key -> true)
				> 4 * UniqueRolls.probability(cfg.table("nether_ruin_boss"), "egide_des_ames", key -> true), "the soul citadel's shield");

		// No favourite for this context, or another source: the plain table.
		assertSame(base, cfg.effectiveTable("ruin_boss@crypte_oubliee"));
		assertSame(base, cfg.effectiveTable("ruin_boss"));
		assertSame(cfg.table("boss"), cfg.effectiveTable("boss@grand_temple_maya"));
		assertNull(cfg.effectiveTable("nothing@grand_temple_maya"));
	}

	@Test
	void contextsOfASource() {
		assertEquals("ruin_boss", UniqueConfig.baseSource(" Ruin_Boss@Grand_Temple_Maya,cursed"));
		assertEquals(List.of("grand_temple_maya", "cursed"), UniqueConfig.contexts("ruin_boss@Grand_Temple_Maya, cursed,,cursed"));
		assertEquals(List.of(), UniqueConfig.contexts("ruin_boss"));
		assertEquals("boss", UniqueConfig.baseSource("boss"));
	}

	@Test
	void olderFilesReceiveTheSevenUniquesOnce() {
		UniqueConfig old = new UniqueConfig();
		old.configVersion = 3;
		old.items.removeIf(def -> UniqueConfig.RUIN_UNIQUES.contains(def.id));
		for (UniqueConfig.DropTable table : old.drops.values()) {
			table.items.keySet().removeIf(UniqueConfig.RUIN_UNIQUES::contains);
		}
		old.drops.get("ruin_boss").items.put("foudrelame", 9.0);
		old.drops.remove("legendary_quest");
		old.rebuild(id -> true);
		assertEquals(UniqueConfig.CURRENT_VERSION, old.configVersion);
		assertEquals(21, old.usable().size(), "the seven definitions added");
		assertEquals(9.0, old.table("ruin_boss").items.get("foudrelame"), "existing weights untouched");
		assertEquals(25.0, old.favorites.get("ruin_boss@grand_temple_maya").get("lance_du_jaguar_celeste"), "favourites (new section)");
		UniqueConfig reference = defaults();
		for (String id : UniqueConfig.RUIN_UNIQUES) {
			for (String source : List.of("ruin_boss", "nether_ruin_boss")) {
				assertEquals(reference.table(source).items.get(id), old.table(source).items.get(id), id + " in " + source);
			}
		}
		assertNull(old.table("legendary_quest"), "a table removed by the admin stays removed");

		// Removed after the migration: never added back.
		UniqueConfig current = new UniqueConfig();
		current.configVersion = UniqueConfig.CURRENT_VERSION;
		current.items.removeIf(def -> def.id.equals("lance_du_jaguar_celeste"));
		current.drops.get("ruin_boss").items.remove("masse_du_zenith");
		List<String> warnings = current.rebuild(id -> true);
		assertNull(current.item("lance_du_jaguar_celeste"), "removed definition stays removed");
		assertFalse(current.table("ruin_boss").items.containsKey("masse_du_zenith"), "removed entry stays removed");
		assertTrue(warnings.stream().anyMatch(w -> w.contains("lance_du_jaguar_celeste")), "its favourite / table entries are reported");

		// Migrating twice never duplicates anything.
		UniqueConfig twice = new UniqueConfig();
		twice.configVersion = 3;
		twice.rebuild(id -> true);
		twice.configVersion = 3;
		twice.rebuild(id -> true);
		assertEquals(21, twice.items.size(), "no duplicate definition");
	}

	@Test
	void olderFileThroughJson5(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("uniques.json5");
		UniqueConfig v3 = new UniqueConfig();
		v3.items.removeIf(def -> UniqueConfig.RUIN_UNIQUES.contains(def.id));
		for (UniqueConfig.DropTable table : v3.drops.values()) {
			table.items.keySet().removeIf(UniqueConfig.RUIN_UNIQUES::contains);
		}
		v3.favorites = null;
		v3.configVersion = 3;
		Json5.write(file, v3);
		// A 1.8 file has no "favorites" section at all.
		String text = java.nio.file.Files.readString(file).replace("\"favorites\": null,", "");
		java.nio.file.Files.writeString(file, text);
		assertFalse(text.contains("\"favorites\""), "no favourites section");
		assertFalse(text.contains("\"id\": \"lance_du_jaguar_celeste\""), "a 1.8 file");
		UniqueConfig read = Json5.read(file, UniqueConfig.class);
		read.rebuild(id -> true);
		assertEquals(21, read.usable().size());
		assertNotNull(read.favorites.get("ruin_boss@cursed"), "default favourites for a file without the section");
		assertTrue(read.table("nether_ruin_boss").items.containsKey("egide_des_ames"));
		assertTrue(read.table("legendary_quest").items.containsKey("lame_du_pacte"));
	}

	@Test
	void powersOfTheRuinUniquesAreBounded() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.powers.jaguarPounce.leap = 99;
		cfg.powers.jaguarPounce.maxTargets = 0;
		cfg.powers.frostCrown.slowLevel = 40;
		cfg.powers.soulAegis.reflectMin = 500;
		cfg.powers.solarLeap.undeadMultiplier = Double.NaN;
		cfg.powers.pactSeal.seals = 0;
		cfg.powers.infernalRift.length = 1000;
		cfg.powers.jadeGale.healthThreshold = 2;
		cfg.powers.frostCrown = null;
		cfg.rebuild(id -> true);
		assertEquals(2.5, cfg.powers.jaguarPounce.leap);
		assertEquals(1, cfg.powers.jaguarPounce.maxTargets);
		assertNotNull(cfg.powers.frostCrown, "missing section: defaults");
		assertEquals(30.0, cfg.powers.soulAegis.reflectMin, "minimum never above the maximum");
		assertEquals(1.0, cfg.powers.solarLeap.undeadMultiplier);
		assertEquals(1, cfg.powers.pactSeal.seals);
		assertEquals(32, cfg.powers.infernalRift.length);
		assertEquals(0.95, cfg.powers.jadeGale.healthThreshold);
		for (UniquePower power : List.of(UniquePower.JAGUAR_POUNCE, UniquePower.SOLAR_LEAP, UniquePower.INFERNAL_RIFT)) {
			assertTrue(power.active && cfg.cooldownSeconds(power) >= 1, power + ": right click with a real cooldown");
		}
		for (UniquePower power : List.of(UniquePower.FROST_CROWN, UniquePower.SOUL_AEGIS, UniquePower.PACT_SEAL, UniquePower.JADE_GALE)) {
			assertFalse(power.active, power + " is passive");
			assertTrue(cfg.cooldownSeconds(power) > 0, power + " has a cooldown");
		}
	}

	@Test
	void badFavoritesAndBannersAreFixed() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.favorites = new LinkedHashMap<>(cfg.favorites);
		cfg.favorites.put("no_context", new LinkedHashMap<>(Map.of("foudrelame", 3.0)));
		cfg.favorites.put("ruin_boss@a,b", new LinkedHashMap<>(Map.of("foudrelame", 3.0)));
		cfg.favorites.put("Ruin_Boss@Crypte_Oubliee", new LinkedHashMap<>(Map.of("ghost", 3.0, "foudrelame", -4.0, "croc_de_givre", 1e9)));
		UniqueConfig.UniqueDef aegis = cfg.items.stream().filter(def -> def.id.equals("egide_des_ames")).findFirst().orElseThrow();
		aegis.banner.patterns = new ArrayList<>(aegis.banner.patterns);
		aegis.banner.patterns.add(new UniqueConfig.BannerLayer("minecraft:skull", "rainbow"));
		List<String> warnings = cfg.rebuild(id -> true);
		assertFalse(cfg.favorites.containsKey("no_context"));
		assertFalse(cfg.favorites.containsKey("ruin_boss@a,b"), "one context per key");
		Map<String, Double> crypt = cfg.favorites.get("ruin_boss@crypte_oubliee");
		assertNotNull(crypt, "key normalised");
		assertFalse(crypt.containsKey("ghost"), "unknown weapon removed");
		assertEquals(0.0, crypt.get("foudrelame"));
		assertEquals(10000.0, crypt.get("croc_de_givre"));
		assertNull(cfg.item("egide_des_ames").banner, "invalid banner: vanilla look");
		assertTrue(warnings.size() >= 4, "every problem reported: " + warnings);

		UniqueConfig fine = defaults();
		UniqueConfig.Banner banner = fine.item("egide_des_ames").banner;
		assertNotNull(banner, "the shield has its banner look");
		assertEquals("black", banner.base);
		assertEquals(4, banner.patterns.size());
		assertEquals("minecraft:skull", banner.patterns.get(1).pattern);
		assertNull(fine.item("foudrelame").banner);
	}
}
