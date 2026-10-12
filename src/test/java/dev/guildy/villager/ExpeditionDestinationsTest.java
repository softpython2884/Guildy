package dev.guildy.villager;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expedition destinations: default list, validation, migration of older villagers.json5 files (once, never over
 * customised tables), fallback of unknown ids, durations, wishlist weights and unique weapon attempts.
 */
class ExpeditionDestinationsTest {
	private static final Set<String> EXPECTED = Set.of(
			"desert", "jungle", "taiga", "ocean", "swamp", "mountains", "cherry_grove",
			"village", "mineshaft", "shipwreck", "desert_temple", "jungle_temple", "pillager_outpost", "ocean_monument", "ancient_city",
			"crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas", "nether_fortress", "bastion");

	private static VillagerConfig validated() {
		VillagerConfig config = new VillagerConfig();
		config.validate();
		return config;
	}

	private static ExpeditionDestination destination(VillagerConfig config, String id) {
		return config.expeditions.destinations.stream().filter(d -> d.id.equals(id)).findFirst().orElseThrow(() -> new AssertionError(id));
	}

	private static VillagerConfig.LootEntry entry(List<VillagerConfig.LootEntry> loot, String item) {
		return loot.stream().filter(e -> e.item.equals(item)).findFirst().orElseThrow(() -> new AssertionError(item));
	}

	@Test
	void defaultsAreValidAndComplete() {
		List<ExpeditionDestination> defaults = ExpeditionDestinations.defaults();
		Set<String> ids = new HashSet<>();
		for (ExpeditionDestination d : defaults) {
			assertTrue(ids.add(d.id), "unique id " + d.id);
			assertEquals(ExpeditionDestinations.normalizeId(d.id), d.id);
			assertFalse(ExpeditionDestinations.isReserved(d.id), d.id);
			assertTrue(d.kind.equals(ExpeditionDestinations.EXPLORATION) || d.kind.equals(ExpeditionDestinations.NETHER), d.id);
			assertFalse(d.name.isBlank(), d.id);
			assertTrue(d.icon.startsWith("minecraft:"), d.id);
			assertTrue(d.minLevel >= 1 && d.minLevel <= 10, d.id);
			assertEquals(0, d.durationSeconds, d.id + ": duration relative to the default destination");
			assertTrue(d.durationMultiplier >= 1.0 && d.durationMultiplier <= 2.0, d.id);
			assertTrue(d.lossChance > 0 && d.lossChance < 0.3, d.id);
			assertTrue(d.rolls >= 6 && d.rolls <= 7, d.id);
			assertTrue(d.uniqueChanceMultiplier >= 1.0, d.id);
			assertTrue(d.loot.size() >= 5, d.id);
			for (VillagerConfig.LootEntry e : d.loot) {
				assertTrue(e.item.startsWith("minecraft:") && e.weight > 0 && e.min >= 1 && e.min <= e.max && e.max <= 64, d.id + " " + e.item);
			}
		}
		assertEquals(EXPECTED, ids);
		assertTrue(ExpeditionDestinations.validate(new VillagerConfig().expeditions, true).isEmpty(), "defaults need no fix");
		VillagerConfig config = validated();
		assertEquals(defaults.size(), config.expeditions.destinations.size());
		assertEquals(16, ExpeditionDestinations.list(config.expeditions, false).size(), "default + 15 exploration destinations");
		assertEquals(7, ExpeditionDestinations.list(config.expeditions, true).size(), "default + 6 Nether destinations");
		assertEquals(ExpeditionDestinations.PLAINS, ExpeditionDestinations.list(config.expeditions, false).getFirst().id());
		assertEquals(ExpeditionDestinations.NETHER_WASTES, ExpeditionDestinations.list(config.expeditions, true).getFirst().id());
		// Structures are worth a level and a higher risk than the default exploration.
		for (String id : List.of("desert_temple", "jungle_temple", "pillager_outpost", "ocean_monument", "ancient_city")) {
			ExpeditionDestination d = destination(config, id);
			assertTrue(d.minLevel >= 3 && d.lossChance > config.expeditions.exploration.lossChance, id);
		}
	}

	@Test
	void defaultDestinationsAreTheHistoricalTables() {
		VillagerConfig config = validated();
		ExpeditionDestinations.Destination plains = ExpeditionDestinations.defaultDestination(config.expeditions, false);
		assertSame(config.expeditions.exploration.loot, plains.loot());
		assertEquals(config.expeditions.exploration.durationSeconds, plains.durationSeconds());
		assertEquals(config.expeditions.exploration.lossChance, plains.lossChance());
		assertEquals(config.expeditions.exploration.rolls, plains.rolls());
		assertEquals(config.expeditions.exploration.treasureMapChance, plains.treasureMapChance());
		assertEquals(1.0, plains.uniqueChanceMultiplier());
		assertEquals(1, plains.minLevel());
		assertTrue(plains.isDefault() && plains.name().isEmpty(), "named by lang.json");
		ExpeditionDestinations.Destination wastes = ExpeditionDestinations.defaultDestination(config.expeditions, true);
		assertSame(config.expeditions.nether.loot, wastes.loot());
		assertEquals(720, wastes.durationSeconds());
		assertTrue(wastes.nether() && wastes.isDefault());
	}

	@Test
	void unknownIdsFallBackToTheDefaultOfTheKind() {
		VillagerConfig.Expeditions e = validated().expeditions;
		assertEquals(ExpeditionDestinations.PLAINS, ExpeditionDestinations.find(e, false, "atlantis").id());
		assertEquals(ExpeditionDestinations.PLAINS, ExpeditionDestinations.find(e, false, null).id());
		assertEquals(ExpeditionDestinations.PLAINS, ExpeditionDestinations.find(e, false, "").id());
		assertEquals(ExpeditionDestinations.PLAINS, ExpeditionDestinations.find(e, false, "bastion").id(), "a Nether destination for an explorer");
		assertEquals(ExpeditionDestinations.NETHER_WASTES, ExpeditionDestinations.find(e, true, "desert").id(), "a surface destination for the Nether");
		assertEquals("bastion", ExpeditionDestinations.find(e, true, "bastion").id());
		assertEquals("desert_temple", ExpeditionDestinations.find(e, false, " Desert Temple ").id(), "normalized id");
		assertEquals(ExpeditionDestinations.PLAINS, ExpeditionDestinations.find(e, false, ExpeditionDestinations.PLAINS).id());

		// Locked (guild level too low): the default destination until the guild grows.
		assertEquals(ExpeditionDestinations.NETHER_WASTES, ExpeditionDestinations.chosen(e, true, "bastion", 1).id());
		assertEquals("bastion", ExpeditionDestinations.chosen(e, true, "bastion", 6).id());
		assertEquals("desert", ExpeditionDestinations.chosen(e, false, "desert", 1).id());

		// Removed from the config while a villager is away: its return uses the default destination.
		e.destinations.removeIf(d -> d.id.equals("desert"));
		assertEquals(ExpeditionDestinations.PLAINS, ExpeditionDestinations.find(e, false, "desert").id());
	}

	@Test
	void durationIsAMultiplierOfTheDefaultDestination() {
		VillagerConfig config = validated();
		VillagerConfig.Expeditions e = config.expeditions;
		assertEquals(480, ExpeditionDestinations.find(e, false, "desert").durationSeconds());
		assertEquals(528, ExpeditionDestinations.find(e, false, "jungle").durationSeconds());
		assertEquals(720, ExpeditionDestinations.find(e, false, "desert_temple").durationSeconds());
		assertEquals(900, ExpeditionDestinations.find(e, true, "nether_fortress").durationSeconds());
		e.exploration.durationSeconds = 60;
		assertEquals(90, ExpeditionDestinations.find(e, false, "desert_temple").durationSeconds(), "follows the default destination");
		destination(config, "desert_temple").durationSeconds = 200;
		assertEquals(200, ExpeditionDestinations.find(e, false, "desert_temple").durationSeconds(), "own duration wins");
		e.exploration.durationSeconds = 1;
		assertEquals(1, ExpeditionDestinations.find(e, false, "desert").durationSeconds(), "never below one second");
	}

	@Test
	void validationCleansAndBoundsEveryValue() {
		VillagerConfig.Expeditions e = new VillagerConfig.Expeditions();
		ExpeditionDestination noId = new ExpeditionDestination();
		ExpeditionDestination named = new ExpeditionDestination();
		named.name = "Mon Île !";
		named.kind = "the_end";
		named.minLevel = 0;
		named.durationSeconds = -5;
		named.durationMultiplier = -1;
		named.lossChance = 2;
		named.rolls = 100;
		named.treasureMapChance = -1;
		named.uniqueChanceMultiplier = 50;
		named.loot = new ArrayList<>(List.of(new VillagerConfig.LootEntry(" Sand ", 0, -3, 4), new VillagerConfig.LootEntry("minecraft:stone", 1, 2, 0)));
		named.loot.add(null);
		ExpeditionDestination duplicate = new ExpeditionDestination();
		duplicate.id = "mon_ile";
		ExpeditionDestination reserved = new ExpeditionDestination();
		reserved.id = "Plains";
		ExpeditionDestination nether = new ExpeditionDestination();
		nether.id = "lava_lake";
		nether.kind = " NETHER ";
		nether.durationSeconds = 999_999;
		e.destinations = new ArrayList<>();
		e.destinations.add(null);
		e.destinations.add(noId);
		e.destinations.add(named);
		e.destinations.add(duplicate);
		e.destinations.add(reserved);
		e.destinations.add(nether);
		e.wishlistWeightMultiplier = 0;

		List<String> warnings = ExpeditionDestinations.validate(e, false);
		assertEquals(2, e.destinations.size(), "null, no id, duplicate and reserved ids dropped: " + warnings);
		assertTrue(warnings.size() >= 4, warnings.toString());
		ExpeditionDestination cleaned = e.destinations.getFirst();
		assertEquals("mon_ile", cleaned.id, "id made from the name");
		assertEquals(ExpeditionDestinations.EXPLORATION, cleaned.kind);
		assertEquals("minecraft:compass", cleaned.icon);
		assertEquals(1, cleaned.minLevel);
		assertEquals(0, cleaned.durationSeconds);
		assertEquals(0.05, cleaned.durationMultiplier);
		assertEquals(1.0, cleaned.lossChance);
		assertEquals(64, cleaned.rolls);
		assertEquals(0.0, cleaned.treasureMapChance);
		assertEquals(ExpeditionDestinations.MAX_UNIQUE_MULTIPLIER, cleaned.uniqueChanceMultiplier);
		assertEquals(1, cleaned.loot.size(), "zero weight and null entries dropped");
		VillagerConfig.LootEntry sand = cleaned.loot.getFirst();
		assertEquals("minecraft:sand", sand.item);
		assertTrue(sand.min == 1 && sand.max == 1);
		ExpeditionDestination lava = e.destinations.get(1);
		assertEquals(ExpeditionDestinations.NETHER, lava.kind);
		assertEquals("minecraft:netherrack", lava.icon);
		assertEquals(ExpeditionDestinations.MAX_SECONDS, lava.durationSeconds);
		assertEquals(1.0, e.wishlistWeightMultiplier);
		e.wishlistWeightMultiplier = 99;
		e.destinations = null;
		ExpeditionDestinations.validate(e, false);
		assertEquals(ExpeditionDestinations.MAX_WISHLIST_MULTIPLIER, e.wishlistWeightMultiplier);
		assertEquals(EXPECTED.size(), e.destinations.size(), "missing list: defaults");
	}

	@Test
	void olderFilesReceiveTheDestinationsOnce() {
		// villagers.json5 written before the destinations: no list at all.
		VillagerConfig old = Json5.fromString("{ \"configVersion\": 1, \"expeditions\": { \"exploration\": { \"durationSeconds\": 300,"
				+ " \"loot\": [ { \"item\": \"minecraft:apple\", \"min\": 1, \"max\": 2, \"weight\": 5 } ] } } }", VillagerConfig.class);
		old.validate();
		assertEquals(VillagerConfig.CURRENT_VERSION, old.configVersion);
		assertEquals(EXPECTED.size(), old.expeditions.destinations.size());
		assertEquals(1, old.expeditions.exploration.loot.size(), "customised default table untouched");
		assertEquals(300, old.expeditions.exploration.durationSeconds);
		assertEquals(450, ExpeditionDestinations.find(old.expeditions, false, "desert_temple").durationSeconds());

		// A list already there (customised desert, an admin destination): the missing defaults are appended, nothing replaced.
		VillagerConfig partial = Json5.fromString("{ \"configVersion\": 5, \"expeditions\": { \"destinations\": ["
				+ " { \"id\": \"desert\", \"name\": \"Mon désert\", \"loot\": [ { \"item\": \"minecraft:cactus\", \"min\": 1, \"max\": 1, \"weight\": 1 } ] },"
				+ " { \"id\": \"lagoon\", \"name\": \"Lagon\", \"loot\": [ { \"item\": \"minecraft:kelp\", \"min\": 1, \"max\": 3, \"weight\": 2 } ] } ] } }",
				VillagerConfig.class);
		partial.validate();
		List<ExpeditionDestination> list = partial.expeditions.destinations;
		assertEquals(EXPECTED.size() + 1, list.size());
		assertEquals("desert", list.get(0).id);
		assertEquals("Mon désert", list.get(0).name);
		assertEquals(1, list.get(0).loot.size(), "customised loot kept");
		assertEquals("lagoon", list.get(1).id);

		// Up to date: an admin removes a destination, it never comes back.
		partial.expeditions.destinations.removeIf(d -> d.id.equals("bastion"));
		partial.validate();
		assertTrue(partial.expeditions.destinations.stream().noneMatch(d -> d.id.equals("bastion")));
		VillagerConfig current = Json5.fromString("{ \"configVersion\": " + VillagerConfig.CURRENT_VERSION
				+ ", \"expeditions\": { \"destinations\": [ { \"id\": \"village\", \"loot\": [ { \"item\": \"minecraft:bread\", \"weight\": 1 } ] } ] } }",
				VillagerConfig.class);
		current.validate();
		assertEquals(1, current.expeditions.destinations.size(), "no re-adding once migrated");
	}

	@Test
	void configFileRoundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("villagers.json5");
		VillagerConfig written = validated();
		Json5.write(file, written);
		String text = Files.readString(file);
		assertTrue(text.contains("\"destinations\"") && text.contains("\"wishlistWeightMultiplier\""));
		assertTrue(text.contains("\"Temple du désert\""));
		VillagerConfig read = Json5.read(file, VillagerConfig.class);
		read.validate();
		assertEquals(written.expeditions.destinations.size(), read.expeditions.destinations.size());
		for (int i = 0; i < written.expeditions.destinations.size(); i++) {
			ExpeditionDestination a = written.expeditions.destinations.get(i);
			ExpeditionDestination b = read.expeditions.destinations.get(i);
			assertEquals(a.id, b.id);
			assertEquals(a.name, b.name);
			assertEquals(a.icon, b.icon);
			assertEquals(a.kind, b.kind);
			assertEquals(a.minLevel, b.minLevel);
			assertEquals(a.durationMultiplier, b.durationMultiplier);
			assertEquals(a.lossChance, b.lossChance);
			assertEquals(a.uniqueChanceMultiplier, b.uniqueChanceMultiplier);
			assertEquals(a.loot.size(), b.loot.size());
		}
	}

	@Test
	void wishlistMultipliesTheWeightOfWantedItems() {
		List<VillagerConfig.LootEntry> loot = ExpeditionDestinations.find(validated().expeditions, false, "desert").loot();
		int cactus = entry(loot, "minecraft:cactus").weight;
		int sand = entry(loot, "minecraft:sand").weight;

		List<VillagerConfig.LootEntry> boosted = ExpeditionDestinations.boosted(loot, List.of("minecraft:cactus", "minecraft:diamond"), 3.0);
		assertNotSame(loot, boosted);
		assertEquals(cactus * 3, entry(boosted, "minecraft:cactus").weight);
		assertEquals(sand, entry(boosted, "minecraft:sand").weight, "other items unchanged");
		assertEquals(cactus, entry(loot, "minecraft:cactus").weight, "the config table is never modified");
		assertEquals(loot.size(), boosted.size(), "unknown wanted items add nothing");
		assertEquals(cactus * 3, entry(ExpeditionDestinations.boosted(loot, List.of("Cactus"), 3.0), "minecraft:cactus").weight, "id without namespace");
		assertSame(loot, ExpeditionDestinations.boosted(loot, List.of(), 3.0));
		assertSame(loot, ExpeditionDestinations.boosted(loot, null, 3.0));
		assertSame(loot, ExpeditionDestinations.boosted(loot, List.of("minecraft:cactus"), 1.0));

		assertEquals(List.of("minecraft:cactus", "minecraft:bone"),
				ExpeditionDestinations.favoured(loot, List.of("minecraft:diamond", "minecraft:cactus", "minecraft:bone")));
		assertTrue(ExpeditionDestinations.favoured(loot, List.of("minecraft:netherite_scrap")).isEmpty());

		// The wanted item is really picked more often: its share of the rolls goes from 7/48 to 21/62 (x2.3).
		int plain = picks(loot, "minecraft:cactus", new Random(11));
		int favoured = picks(boosted, "minecraft:cactus", new Random(11));
		assertTrue(favoured > plain * 2.1 && favoured < plain * 2.6, plain + " -> " + favoured);
	}

	/** Number of rolls (out of 24000) that pick {@code item}. */
	private static int picks(List<VillagerConfig.LootEntry> loot, String item, Random random) {
		VillagerConfig.DepthTable table = new VillagerConfig.DepthTable(0, loot);
		int picks = 0;
		for (int i = 0; i < 24_000; i++) {
			if (DepthLoot.roll(table, 1, random).getFirst().item().equals(item)) {
				picks++;
			}
		}
		return picks;
	}

	@Test
	void uniqueWeaponAttemptsFollowTheMultiplier() {
		Random random = new Random(5);
		assertEquals(0, ExpeditionDestinations.uniqueAttempts(0, random));
		assertEquals(0, ExpeditionDestinations.uniqueAttempts(-2, random));
		assertEquals(0, ExpeditionDestinations.uniqueAttempts(Double.NaN, random));
		assertEquals(1, ExpeditionDestinations.uniqueAttempts(1, random));
		assertEquals(3, ExpeditionDestinations.uniqueAttempts(3, random));
		assertEquals((int) ExpeditionDestinations.MAX_UNIQUE_MULTIPLIER, ExpeditionDestinations.uniqueAttempts(1000, random));
		for (double multiplier : new double[]{0.5, 1.5, 2.5}) {
			long total = 0;
			int runs = 20_000;
			for (int i = 0; i < runs; i++) {
				int attempts = ExpeditionDestinations.uniqueAttempts(multiplier, random);
				assertTrue(attempts == (int) multiplier || attempts == (int) multiplier + 1, multiplier + " -> " + attempts);
				total += attempts;
			}
			assertEquals(multiplier, (double) total / runs, 0.03, "mean attempts");
		}
	}

	@Test
	void riskLevelsAndLootSummaries() {
		assertEquals("low", ExpeditionDestinations.risk(0.05));
		assertEquals("low", ExpeditionDestinations.risk(0.06));
		assertEquals("medium", ExpeditionDestinations.risk(0.12));
		assertEquals("high", ExpeditionDestinations.risk(0.18));
		assertEquals("high", ExpeditionDestinations.risk(0.2));
		assertEquals("very_high", ExpeditionDestinations.risk(0.25));

		List<VillagerConfig.LootEntry> temple = ExpeditionDestinations.find(validated().expeditions, false, "desert_temple").loot();
		List<VillagerConfig.LootEntry> typical = ExpeditionDestinations.typical(temple, 4);
		assertEquals(List.of("minecraft:bone", "minecraft:rotten_flesh", "minecraft:sand", "minecraft:gunpowder"),
				typical.stream().map(e -> e.item).toList());
		assertEquals(List.of("minecraft:diamond", "minecraft:enchanted_book"),
				ExpeditionDestinations.rare(temple, 3, typical).stream().map(e -> e.item).toList(), "rarest first");
		assertTrue(ExpeditionDestinations.rare(temple, 3, temple).isEmpty(), "never an item already shown");
	}

	@Test
	void idsAndItemsAreNormalized() {
		assertEquals("temple_du_desert", ExpeditionDestinations.normalizeId("Temple du désert"));
		assertEquals("ile_mystere_2", ExpeditionDestinations.normalizeId("  Île--Mystère  2 !! "));
		assertEquals("", ExpeditionDestinations.normalizeId("!!!"));
		assertEquals("", ExpeditionDestinations.normalizeId(null));
		assertEquals("minecraft:sand", ExpeditionDestinations.normalizeItem(" Sand "));
		assertEquals("guildy:thing", ExpeditionDestinations.normalizeItem("guildy:thing"));
	}
}
