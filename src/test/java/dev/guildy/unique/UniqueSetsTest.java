package dev.guildy.unique;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
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

/**
 * uniques.json5 format 5: the three armor sets (twelve pieces, set definitions, bonus tiers, powers), the pure rules of the
 * worn pieces (distinct pieces counted, tiers added up, stable modifier ids), the drops (favourites of the guardians, rarely
 * a legendary contract, never sold) and the migration that adds them once to older files.
 */
class UniqueSetsTest {
	private static final List<String> FROST = List.of("heaume_hiver_eternel", "cuirasse_hiver_eternel", "jambieres_hiver_eternel",
			"solerets_hiver_eternel");
	private static final List<String> JAGUAR = List.of("masque_guerrier_jaguar", "pectoral_guerrier_jaguar", "jambieres_guerrier_jaguar",
			"sandales_guerrier_jaguar");
	private static final List<String> SOUL = List.of("heaume_brasier_des_ames", "cuirasse_brasier_des_ames", "greves_brasier_des_ames",
			"bottes_brasier_des_ames");

	private static UniqueConfig defaults() {
		UniqueConfig cfg = new UniqueConfig();
		List<String> warnings = cfg.rebuild(id -> true);
		assertTrue(warnings.isEmpty(), "defaults need no fix: " + warnings);
		return cfg;
	}

	private static String slotOf(String item) {
		for (String[] suffix : new String[][] {{"_helmet", "head"}, {"_chestplate", "chest"}, {"_leggings", "legs"}, {"_boots", "feet"}}) {
			if (item.endsWith(suffix[0])) {
				return suffix[1];
			}
		}
		return null;
	}

	// ================================================================== definitions

	@Test
	void threeSetsOfFourPiecesFromHelmetToBoots() {
		UniqueConfig cfg = defaults();
		assertEquals(33, cfg.usable().size(), "21 uniques + 12 set pieces");
		assertEquals(12, UniqueConfig.SET_UNIQUES.size());
		assertEquals(UniqueConfig.SET_IDS, cfg.usableSets().stream().map(set -> set.id).toList());
		Map<String, List<String>> expected = Map.of("hiver_eternel", FROST, "guerrier_jaguar", JAGUAR, "brasier_des_ames", SOUL);
		for (UniqueConfig.SetDef set : cfg.usableSets()) {
			List<String> pieces = cfg.piecesOf(set.id);
			assertEquals(expected.get(set.id), pieces, set.id + " pieces");
			Set<String> slots = new HashSet<>();
			for (String id : pieces) {
				UniqueConfig.UniqueDef def = cfg.item(id);
				assertEquals(set.id, def.set, id + " belongs to its set");
				assertTrue(slots.add(slotOf(def.item)), id + ": one piece per armor slot");
				assertNull(def.power(), id + ": no power of its own (the power comes from the set)");
				assertFalse(def.lore.isEmpty(), id + " has a story");
				assertTrue(def.enchantments.size() >= 2, id + " is well enchanted");
				assertTrue(def.enchantments.containsKey("minecraft:protection"), id + " protects");
				assertFalse(def.attributes.isEmpty(), id + " has attributes");
				assertEquals(id, def.model, id + " has its texture");
				assertEquals(id, UniqueEquipment.assetFor(def.model, slotOf(def.item)), id + " has its worn look");
				for (UniqueConfig.AttributeEntry attribute : def.attributes) {
					assertEquals(slotOf(def.item), attribute.slot, id + ": attributes of its own slot");
				}
			}
			assertEquals(Set.of("head", "chest", "legs", "feet"), slots, set.id + ": helmet to boots");
			assertEquals(List.of(2, 4), set.bonuses().stream().map(bonus -> bonus.pieces).toList(), set.id + " tiers");
			UniqueConfig.SetBonus two = set.bonuses().get(0);
			UniqueConfig.SetBonus four = set.bonuses().get(1);
			assertFalse(two.attributes.isEmpty(), set.id + ": a passive bonus with two pieces");
			assertFalse(two.name.isBlank() || four.name.isBlank(), set.id + ": named bonuses");
			assertNotNull(four.power(), set.id + ": a power with four pieces");
		}
		assertEquals(SetPower.FROST_BLOOD, cfg.set("hiver_eternel").bonuses().get(0).power());
		assertEquals(SetPower.WINTER_HEART, cfg.set("hiver_eternel").bonuses().get(1).power());
		assertEquals(SetPower.JAGUAR_AMBUSH, cfg.set("guerrier_jaguar").bonuses().get(1).power());
		assertEquals(SetPower.SOUL_PYRE, cfg.set("brasier_des_ames").bonuses().get(1).power());
		assertEquals("minecraft:golden_helmet", cfg.item("masque_guerrier_jaguar").item);
		assertTrue(cfg.item("masque_guerrier_jaguar").unbreakable, "the jaguar warrior's gold never wears out");
		assertEquals("minecraft:netherite_leggings", cfg.item("greves_brasier_des_ames").item);
		assertEquals(UniqueRarity.LEGENDARY, cfg.item("bottes_brasier_des_ames").rarity());
		assertEquals("minecraft:diamond_boots", cfg.item("solerets_hiver_eternel").item);
	}

	// ================================================================== counting and tiers

	@Test
	void differentPiecesAreCountedPerSet() {
		UniqueConfig cfg = defaults();
		Map<String, Integer> counts = UniqueSets.count(Arrays.asList("heaume_hiver_eternel", null, "cuirasse_hiver_eternel",
				"heaume_hiver_eternel", "foudrelame", "unknown", "sandales_guerrier_jaguar"), cfg);
		assertEquals(Map.of("hiver_eternel", 2, "guerrier_jaguar", 1), counts, "a copy worn twice counts once, other uniques ignored");
		assertTrue(UniqueSets.count(Arrays.asList(null, null, null, null), cfg).isEmpty());
		assertTrue(UniqueSets.count(List.of("couronne_du_roi_de_givre", "bottes_de_zephyr"), cfg).isEmpty(), "uniques outside the sets");
		assertEquals(Map.of("brasier_des_ames", 4), UniqueSets.count(SOUL, cfg));
	}

	@Test
	void bonusTiersAddUp() {
		UniqueConfig cfg = defaults();
		UniqueConfig.SetDef frost = cfg.set("hiver_eternel");
		assertTrue(UniqueSets.active(frost, 0).isEmpty());
		assertTrue(UniqueSets.active(frost, 1).isEmpty(), "one piece: nothing");
		assertEquals(List.of(2), UniqueSets.active(frost, 2).stream().map(bonus -> bonus.pieces).toList());
		assertEquals(List.of(2), UniqueSets.active(frost, 3).stream().map(bonus -> bonus.pieces).toList(), "three pieces: still the first tier");
		assertEquals(List.of(2, 4), UniqueSets.active(frost, 4).stream().map(bonus -> bonus.pieces).toList(), "four pieces: both tiers");
		assertEquals(0, UniqueSets.tier(frost, 1));
		assertEquals(2, UniqueSets.tier(frost, 3));
		assertEquals(4, UniqueSets.tier(frost, 4));
		assertEquals(0, UniqueSets.tier(null, 4));
		assertEquals(EnumSet.of(SetPower.FROST_BLOOD), UniqueSets.powers(Map.of("hiver_eternel", 2), cfg));
		assertEquals(EnumSet.of(SetPower.FROST_BLOOD, SetPower.WINTER_HEART), UniqueSets.powers(Map.of("hiver_eternel", 4), cfg));
		assertEquals(EnumSet.noneOf(SetPower.class), UniqueSets.powers(Map.of("guerrier_jaguar", 3), cfg), "jaguar: attributes only with two");
		assertEquals(EnumSet.of(SetPower.FROST_BLOOD), UniqueSets.powers(Map.of("hiver_eternel", 2, "brasier_des_ames", 2), cfg),
				"two pieces of two sets: both two-piece bonuses");
		Map<String, Integer> tiers = UniqueSets.tiers(Map.of("hiver_eternel", 1, "guerrier_jaguar", 2), cfg);
		assertEquals(Map.of("guerrier_jaguar", 2), tiers, "sets without a tier reached are left out");
	}

	@Test
	void modifiersHaveStableIdsAndNeverStack() {
		UniqueConfig cfg = defaults();
		List<UniqueSets.Modifier> two = UniqueSets.modifiers(UniqueSets.count(FROST.subList(0, 2), cfg), cfg);
		assertEquals(List.of("set_hiver_eternel_2_0"), two.stream().map(UniqueSets.Modifier::path).toList());
		assertEquals("minecraft:armor_toughness", two.getFirst().attribute());
		List<UniqueSets.Modifier> four = UniqueSets.modifiers(UniqueSets.count(FROST, cfg), cfg);
		assertEquals(List.of("set_hiver_eternel_2_0", "set_hiver_eternel_4_0"), four.stream().map(UniqueSets.Modifier::path).toList());
		assertEquals(4.0, four.get(1).amount());
		List<String> doubled = new ArrayList<>(FROST);
		doubled.addAll(FROST);
		assertEquals(four, UniqueSets.modifiers(UniqueSets.count(doubled, cfg), cfg), "the same pieces twice: the same modifiers, once");
		List<UniqueSets.Modifier> jaguar = UniqueSets.modifiers(UniqueSets.count(JAGUAR.subList(1, 3), cfg), cfg);
		assertEquals(List.of("set_guerrier_jaguar_2_0", "set_guerrier_jaguar_2_1"), jaguar.stream().map(UniqueSets.Modifier::path).toList());
		assertEquals("add_multiplied_base", jaguar.getFirst().operation());
		Set<String> ids = new HashSet<>();
		for (UniqueConfig.SetDef set : cfg.usableSets()) {
			for (UniqueSets.Modifier modifier : UniqueSets.modifiers(Map.of(set.id, 4), cfg)) {
				assertTrue(ids.add(modifier.path()), "unique id " + modifier.path());
				assertTrue(UniqueSets.isSetModifier(modifier.path()), "set_ prefix (never a rune_ one)");
			}
		}
		assertFalse(UniqueSets.isSetModifier("rune_protection_0"));
	}

	// ================================================================== drops

	@Test
	void piecesDropFromTheirGuardiansAndRarelyFromLegendaryContracts() {
		UniqueConfig cfg = defaults();
		for (String id : UniqueConfig.SET_UNIQUES) {
			for (String source : List.of("boss", "blood_boss", "quest", "expedition", "trade", "ruin_boss", "nether_ruin_boss")) {
				assertFalse(cfg.table(source).items.containsKey(id), id + " never in the plain '" + source + "' table (never sold)");
			}
			double contract = UniqueRolls.probability(cfg.table("legendary_quest"), id, key -> true);
			assertTrue(contract > 0 && contract < 0.01, id + ": rarely from a legendary contract (" + contract + ")");
		}
		Map<String, List<String>> guardians = new LinkedHashMap<>();
		guardians.put("ruin_boss@bastion_de_givre", FROST);
		guardians.put("ruin_boss@grand_temple_maya", JAGUAR);
		guardians.put("ruin_boss@temple_maya", JAGUAR);
		guardians.put("nether_ruin_boss@citadelle_des_ames", SOUL);
		guardians.put("nether_ruin_boss@autel_infernal", SOUL);
		for (Map.Entry<String, List<String>> entry : guardians.entrySet()) {
			UniqueConfig.DropTable table = cfg.effectiveTable(entry.getKey());
			double set = 0;
			for (String id : entry.getValue()) {
				double chance = UniqueRolls.probability(table, id, key -> true);
				assertTrue(chance > 0, id + " from " + entry.getKey());
				set += chance;
			}
			assertTrue(set > 0.02, entry.getKey() + ": a piece of its set in more than 2 % of the kills (" + set + ")");
			for (String id : UniqueConfig.SET_UNIQUES) {
				if (!entry.getValue().contains(id)) {
					assertEquals(0, UniqueRolls.probability(table, id, key -> true), id + " is not a piece of " + entry.getKey());
				}
			}
		}
		// The main guardian of each set favours it more than the second one.
		assertTrue(UniqueRolls.probability(cfg.effectiveTable("ruin_boss@grand_temple_maya"), "masque_guerrier_jaguar", key -> true)
				> UniqueRolls.probability(cfg.effectiveTable("ruin_boss@temple_maya"), "masque_guerrier_jaguar", key -> true));
		// Other ruins never drop them.
		assertEquals(0, UniqueRolls.probability(cfg.effectiveTable("ruin_boss@crypte_oubliee"), "heaume_hiver_eternel", key -> true));
		// Every piece owned: the roll is exhausted (a spell scroll instead), never a duplicate.
		Set<String> owned = new HashSet<>();
		cfg.effectiveTable("ruin_boss@bastion_de_givre").items.keySet().forEach(owned::add);
		UniqueRolls.Outcome outcome = UniqueRolls.roll(cfg.effectiveTable("ruin_boss@bastion_de_givre"), key -> true, owned, () -> 0L);
		assertEquals(UniqueRolls.Kind.EXHAUSTED, outcome.kind());
		owned.remove("solerets_hiver_eternel");
		outcome = UniqueRolls.roll(cfg.effectiveTable("ruin_boss@bastion_de_givre"), key -> true, owned, () -> 0L);
		assertEquals("solerets_hiver_eternel", outcome.id(), "the last piece missing is the one given");
	}

	// ================================================================== migration

	/** A format 4 file: no set, no set piece, no set entry in the tables and favourites. */
	private static UniqueConfig format4() {
		UniqueConfig old = new UniqueConfig();
		old.configVersion = 4;
		old.items.removeIf(def -> UniqueConfig.SET_UNIQUES.contains(def.id));
		old.sets = new ArrayList<>();
		for (UniqueConfig.DropTable table : old.drops.values()) {
			table.items.keySet().removeIf(UniqueConfig.SET_UNIQUES::contains);
		}
		for (Map<String, Double> favorite : old.favorites.values()) {
			favorite.keySet().removeIf(UniqueConfig.SET_UNIQUES::contains);
		}
		return old;
	}

	@Test
	void olderFilesReceiveTheSetsOnce() {
		UniqueConfig old = format4();
		old.favorites.get("ruin_boss@bastion_de_givre").put("couronne_du_roi_de_givre", 99.0);
		old.favorites.remove("nether_ruin_boss@autel_infernal");
		old.drops.get("legendary_quest").items.put("foudrelame", 7.0);
		old.drops.remove("quest");
		old.rebuild(id -> true);
		assertEquals(UniqueConfig.CURRENT_VERSION, old.configVersion);
		assertEquals(5, UniqueConfig.CURRENT_VERSION);
		assertEquals(33, old.usable().size(), "the twelve pieces added");
		assertEquals(3, old.usableSets().size(), "the three sets added");
		assertEquals(99.0, old.favorites.get("ruin_boss@bastion_de_givre").get("couronne_du_roi_de_givre"), "admin weight untouched");
		assertEquals(15.0, old.favorites.get("ruin_boss@bastion_de_givre").get("heaume_hiver_eternel"), "set pieces added to the favourite");
		assertEquals(Set.copyOf(SOUL), old.favorites.get("nether_ruin_boss@autel_infernal").keySet(),
				"a missing guardian key gets only the set pieces");
		assertEquals(7.0, old.table("legendary_quest").items.get("foudrelame"), "existing weights untouched");
		assertEquals(0.5, old.table("legendary_quest").items.get("greves_brasier_des_ames"));
		assertNull(old.table("quest"), "a table removed by the admin stays removed");

		// Migrating twice never duplicates anything.
		UniqueConfig twice = format4();
		twice.rebuild(id -> true);
		twice.configVersion = 4;
		twice.rebuild(id -> true);
		assertEquals(33, twice.items.size(), "no duplicate definition");
		assertEquals(3, twice.sets.size(), "no duplicate set");

		// Removed after the migration: never added back.
		UniqueConfig current = new UniqueConfig();
		current.configVersion = UniqueConfig.CURRENT_VERSION;
		current.items.removeIf(def -> def.id.equals("solerets_hiver_eternel"));
		current.favorites.get("ruin_boss@bastion_de_givre").remove("heaume_hiver_eternel");
		List<String> warnings = current.rebuild(id -> true);
		assertNull(current.item("solerets_hiver_eternel"), "removed piece stays removed");
		assertEquals(3, current.piecesOf("hiver_eternel").size());
		assertFalse(current.favorites.get("ruin_boss@bastion_de_givre").containsKey("heaume_hiver_eternel"), "removed entry stays removed");
		assertTrue(warnings.stream().anyMatch(w -> w.contains("hiver_eternel") && w.contains("never reached")),
				"a four-piece bonus of a three-piece set is reported: " + warnings);
	}

	@Test
	void format4FileThroughJson5(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("uniques.json5");
		UniqueConfig v4 = format4();
		v4.sets = null;
		Json5.write(file, v4);
		// "sets" is the last section: its comma (before its comment lines) goes with it.
		String text = Files.readString(file).replaceAll(",(\\s*(?://[^\\n]*\\s*)*)\"sets\": null", "$1");
		Files.writeString(file, text);
		assertTrue(text.lines().noneMatch(line -> line.trim().startsWith("\"sets\"")), "a 1.12 file has no sets section");
		UniqueConfig read = Json5.read(file, UniqueConfig.class);
		read.rebuild(id -> true);
		assertEquals(33, read.usable().size());
		assertEquals(3, read.usableSets().size(), "default sets for a file without the section");
		assertEquals("hiver_eternel", read.item("heaume_hiver_eternel").set);
		assertEquals("", read.item("foudrelame").set, "older definitions: no set");
		assertTrue(read.effectiveTable("nether_ruin_boss@citadelle_des_ames").items.containsKey("bottes_brasier_des_ames"));

		// Round trip of the current format.
		Path current = dir.resolve("current.json5");
		Json5.write(current, defaults());
		UniqueConfig again = Json5.read(current, UniqueConfig.class);
		assertTrue(again.rebuild(id -> true).isEmpty());
		assertEquals(defaults().piecesOf("guerrier_jaguar"), again.piecesOf("guerrier_jaguar"));
		assertEquals(SetPower.JAGUAR_AMBUSH, again.set("guerrier_jaguar").bonuses().get(1).power());
		assertEquals(0.1, again.set("guerrier_jaguar").bonuses().get(0).attributes.getFirst().amount);
	}

	// ================================================================== validation

	@Test
	void badSetsAreFixed() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.items.stream().filter(def -> def.id.equals("foudrelame")).findFirst().orElseThrow().set = "no_such_set";
		UniqueConfig.SetDef broken = new UniqueConfig.SetDef();
		broken.id = "Broken_Set";
		UniqueConfig.SetBonus zero = new UniqueConfig.SetBonus();
		zero.pieces = 0;
		UniqueConfig.SetBonus two = new UniqueConfig.SetBonus();
		two.power = "teleport_everything";
		two.attributes.add(new UniqueConfig.AttributeEntry());
		two.attributes.getFirst().attribute = "not an attribute";
		UniqueConfig.SetBonus twoAgain = new UniqueConfig.SetBonus();
		broken.bonuses = new ArrayList<>(List.of(two, zero, twoAgain));
		UniqueConfig.SetDef lonely = new UniqueConfig.SetDef();
		lonely.id = "lonely";
		UniqueConfig.SetDef bad = new UniqueConfig.SetDef();
		bad.id = "Not Valid!";
		cfg.sets.addAll(List.of(broken, lonely, bad));
		cfg.items.stream().filter(def -> def.id.equals("bottes_de_zephyr")).findFirst().orElseThrow().set = "broken_set";
		List<String> warnings = cfg.rebuild(id -> true);
		assertEquals("", cfg.item("foudrelame").set, "unknown set: none");
		assertNull(cfg.set("lonely"), "a set without pieces is unused");
		assertNull(cfg.set("not valid!"));
		UniqueConfig.SetDef fixed = cfg.set("broken_set");
		assertNotNull(fixed, "id normalised");
		assertEquals(List.of("bottes_de_zephyr"), cfg.piecesOf("broken_set"));
		assertEquals(1, fixed.bonuses().size(), "invalid and duplicate tiers removed");
		assertNull(fixed.bonuses().getFirst().power(), "unknown power: none");
		assertTrue(fixed.bonuses().getFirst().attributes.isEmpty(), "invalid attribute removed");
		assertTrue(warnings.size() >= 6, "every problem reported: " + warnings);
		assertEquals(3 + 1, cfg.usableSets().size());
	}

	@Test
	void setPowersAreBounded() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.powers.winterHeart.healthThreshold = 5;
		cfg.powers.winterHeart.cooldownSeconds = 0;
		cfg.powers.jaguarAmbush.foliageSeconds = 99;
		cfg.powers.jaguarAmbush.maxTargets = -3;
		cfg.powers.soulPyre.pulseSeconds = 0;
		cfg.powers.soulPyre.healRatio = Double.NaN;
		cfg.powers.soulPyre = null;
		cfg.rebuild(id -> true);
		assertEquals(0.95, cfg.powers.winterHeart.healthThreshold);
		assertEquals(1, cfg.powers.winterHeart.cooldownSeconds);
		assertEquals(cfg.powers.jaguarAmbush.camouflageSeconds, cfg.powers.jaguarAmbush.foliageSeconds, "foliage never slower than the open");
		assertEquals(0, cfg.powers.jaguarAmbush.maxTargets);
		assertNotNull(cfg.powers.soulPyre, "missing section: defaults");
		assertEquals(1.0, cfg.cooldownSeconds(SetPower.WINTER_HEART), "at least a second");
		assertEquals(cfg.powers.jaguarAmbush.cooldownSeconds, cfg.cooldownSeconds(SetPower.JAGUAR_AMBUSH));
		assertEquals(2, cfg.cooldownSeconds(SetPower.SOUL_PYRE), "the pulse of the aura");
		assertEquals(0, cfg.cooldownSeconds(SetPower.FROST_BLOOD), "passive");
		assertEquals(SetPower.WINTER_HEART, SetPower.byId(" Winter_Heart "));
		assertNull(SetPower.byId("frost_crown"), "item powers are not set powers");
	}
}
