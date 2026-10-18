package dev.guildy.scroll;

import dev.guildy.config.Json5;
import dev.guildy.unique.UniqueRarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cursed grimoire: amplification of the spell parameters ({@link SpellPower}: general multipliers, per-spell overrides,
 * level bonus, bounds; the plain grimoire unchanged), its blood price and its floor, the drop rule (only the cursed guardian
 * of the mage tower), and the cursedGrimoire section of scrolls.json5.
 */
class CursedGrimoireRulesTest {
	private static final double EPS = 1.0E-9;

	private static ScrollConfig config() {
		ScrollConfig cfg = new ScrollConfig();
		assertTrue(cfg.rebuild(id -> true).isEmpty(), "defaults need no fix");
		return cfg;
	}

	private static double cursed(ScrollConfig cfg, Spell spell, String param) {
		Spell.Param p = spell.param(param);
		return cfg.cursedGrimoire.power(spell).apply(p, p.defaultValue());
	}

	// ================================================================== amplification

	@Test
	void scrollsAndGrimoiresKeepTheirValues() {
		for (Spell spell : Spell.values()) {
			for (Spell.Param param : spell.params()) {
				double value = param.defaultValue();
				assertEquals(value, SpellPower.NONE.apply(param, value), spell.id + "." + param.name() + ": a scroll is unchanged");
				for (double power : new double[] {0.1, 1.0, 1.5, 5.0}) {
					// The grimoire's power multiplier: exactly what it did before (damage, heals and durations only).
					assertEquals(param.powered(value, power), SpellPower.of(power).apply(param, value), EPS,
							spell.id + "." + param.name() + " at power " + power);
					assertEquals(param.powered(param.max(), power), SpellPower.of(power).apply(param, param.max()), EPS,
							spell.id + "." + param.name() + " at its maximum, power " + power);
				}
			}
		}
		assertTrue(SpellPower.NONE.neutral() && SpellPower.of(1.0).neutral() && !SpellPower.of(1.5).neutral());
		assertEquals(7.0, SpellPower.NONE.apply(null, 7.0), "unknown parameter: unchanged");
	}

	@Test
	void cursedGrimoireAmplifiesTheShowpieces() {
		ScrollConfig cfg = config();
		// Pluie de météores: 2.5x the meteors over a 1.6x wider area, 1.75x the damage.
		assertEquals(25, cursed(cfg, Spell.METEOR_SHOWER, "count"), EPS);
		assertEquals(9.6, cursed(cfg, Spell.METEOR_SHOWER, "radius"), EPS);
		assertEquals(49.0, cursed(cfg, Spell.METEOR_SHOWER, "damage"), EPS);
		assertEquals(3.9, cursed(cfg, Spell.METEOR_SHOWER, "impactRadius"), EPS);
		assertEquals(7.0, cursed(cfg, Spell.METEOR_SHOWER, "fireSeconds"), EPS);
		assertEquals(54.0, cursed(cfg, Spell.METEOR_SHOWER, "range"), EPS);
		// Tempête de foudre: 20 monsters, chains 15 blocks away.
		assertEquals(20, cursed(cfg, Spell.CHAIN_STORM, "count"), EPS);
		assertEquals(15.0, cursed(cfg, Spell.CHAIN_STORM, "chainRadius"), EPS);
		assertEquals(42.0, cursed(cfg, Spell.CHAIN_STORM, "damage"), EPS);
		// Singularité: pulls from 16 blocks, harder and longer.
		assertEquals(16.0, cursed(cfg, Spell.SINGULARITY, "radius"), EPS);
		assertEquals(0.49, cursed(cfg, Spell.SINGULARITY, "pullStrength"), EPS);
		assertEquals(5.25, cursed(cfg, Spell.SINGULARITY, "pullSeconds"), EPS);
		// Séisme: 5 waves; levels +1; flags never change.
		assertEquals(5, cursed(cfg, Spell.EARTHQUAKE, "waves"), EPS);
		assertEquals(4, cursed(cfg, Spell.AEGIS, "absorptionLevel"), EPS);
		assertEquals(3, cursed(cfg, Spell.AEGIS, "resistanceLevel"), EPS);
		assertEquals(5, cursed(cfg, Spell.BLIZZARD, "slowLevel"), EPS);
		assertEquals(3, cursed(cfg, Spell.BLESSING, "regenLevel"), EPS);
		assertEquals(1, cursed(cfg, Spell.BLESSING, "cleanse"), EPS);
		assertEquals(3, cursed(cfg, Spell.GUARDIANS, "count"), EPS, "2 golems -> 3 (within the golem limit)");
		assertEquals(12, cursed(cfg, Spell.RECALL, "maxAllies"), EPS);
		assertEquals(1.5, cursed(cfg, Spell.SOLAR_NOVA, "undeadMultiplier"), EPS, "multipliers only change with an override");
		for (Spell spell : Spell.values()) {
			SpellPower power = cfg.cursedGrimoire.power(spell);
			assertTrue(power.cursed(), spell.id + ": cursed visuals");
			boolean stronger = false;
			for (Spell.Param param : spell.params()) {
				double value = param.defaultValue();
				double amplified = power.apply(param, value);
				assertTrue(amplified >= value, spell.id + "." + param.name() + " never weaker: " + value + " -> " + amplified);
				assertTrue(amplified <= SpellPower.bound(param, Double.MAX_VALUE), spell.id + "." + param.name() + " bounded");
				if (param.integer()) {
					assertEquals(Math.rint(amplified), amplified, EPS, spell.id + "." + param.name() + " stays an integer");
				}
				stronger |= amplified > value;
			}
			assertTrue(stronger, spell.id + ": the cursed grimoire changes something");
		}
	}

	@Test
	void amplifiedValuesAreBounded() {
		Spell.Param count = Spell.METEOR_SHOWER.param("count");
		Spell.Param radius = Spell.METEOR_SHOWER.param("radius");
		Spell.Param damage = Spell.METEOR_SHOWER.param("damage");
		Spell.Param resistance = Spell.AEGIS.param("resistanceLevel");
		Spell.Param golems = Spell.GUARDIANS.param("count");
		SpellPower huge = new SpellPower(5, 3, 5, 3, Map.of(), true);
		assertEquals(30, huge.apply(count, 30), EPS, "counts never above the parameter's maximum");
		assertEquals(4, huge.apply(golems, 2), EPS, "golems: never above 4");
		assertEquals(18.0, huge.apply(radius, 12), EPS, "radii: 1.5x the maximum at most");
		assertEquals(1000.0, huge.apply(damage, 200), EPS, "damage: 5x the maximum at most");
		assertEquals(4, huge.apply(resistance, 2), EPS, "levels never above the parameter's maximum (no Résistance V)");
		SpellPower weak = new SpellPower(0.1, 0.1, 0.1, 0, Map.of(), true);
		assertEquals(1, weak.apply(count, 10), EPS, "never below the minimum");
		assertEquals(2, weak.apply(radius, 6), EPS);
		assertEquals(1.2, weak.apply(damage, 12), EPS, "damage only above zero");
		SpellPower own = new SpellPower(1, 1, 1, 1, Map.of("count", 2.0, "absorptionLevel", 1.5), true);
		assertEquals(20, own.apply(count, 10), EPS, "per-spell override");
		assertEquals(5, own.apply(Spell.AEGIS.param("absorptionLevel"), 3), EPS, "a level override multiplies (4.5 -> 5)");
		assertEquals(3, own.apply(resistance, 2), EPS, "the other levels get the bonus");
		assertEquals(3, own.apply(Spell.EARTHQUAKE.param("waves"), 3), EPS, "no override and a general count multiplier of 1: unchanged");
		SpellPower half = new SpellPower(1, 1, 1.5, 0, Map.of(), true);
		assertEquals(5, half.apply(Spell.EARTHQUAKE.param("waves"), 3), EPS, "4.5 waves round up to 5");
	}

	// ================================================================== blood price

	@Test
	void bloodPriceNeverGoesBelowTheFloor() {
		assertTrue(CursedGrimoireRules.canBleed(20F, 2, 6));
		assertTrue(CursedGrimoireRules.canBleed(8F, 2, 6), "exactly down to the floor");
		assertFalse(CursedGrimoireRules.canBleed(7.5F, 2, 6), "would go under the floor: refused");
		assertFalse(CursedGrimoireRules.canBleed(4F, 1, 6));
		assertTrue(CursedGrimoireRules.canBleed(1F, 0, 6), "no price: always");
		assertEquals(18F, CursedGrimoireRules.bleed(20F, 2, 6));
		assertEquals(6F, CursedGrimoireRules.bleed(8F, 2, 6));
		assertEquals(6F, CursedGrimoireRules.bleed(7F, 2, 6), "never below the floor");
		assertEquals(5F, CursedGrimoireRules.bleed(5F, 2, 6), "already under it: nothing taken");
		assertEquals(9F, CursedGrimoireRules.bleed(9F, 0, 6));
		ScrollConfig cfg = config();
		for (UniqueRarity rarity : UniqueRarity.values()) {
			int blood = cfg.cursedGrimoire.bloodCost(rarity);
			assertTrue(blood >= 1 && blood <= 2, rarity + ": a small blood price, got " + blood);
			assertTrue(cfg.cursedGrimoire.foodCost(rarity) > cfg.grimoire.foodCost(rarity), rarity + ": hungrier than the grimoire");
		}
		assertTrue(cfg.cursedGrimoire.minHealth >= 4 && cfg.cursedGrimoire.minHealth <= 10, "a floor of a few hearts");
	}

	// ================================================================== drops

	@Test
	void onlyTheCursedArchmageDropsIt() {
		ScrollConfig cfg = config();
		ScrollConfig.CursedGrimoire c = cfg.cursedGrimoire;
		double chance = c.dropChance(CursedGrimoireRules.MAGE_TOWER, true);
		assertTrue(chance >= 0.08 && chance <= 0.12, "rare: " + chance);
		assertEquals(chance, c.dropChance("Tour_du_Mage ", true), "ids are normalized");
		assertEquals(0.0, c.dropChance(CursedGrimoireRules.MAGE_TOWER, false), "never from the normal archmage");
		for (String other : List.of("crypte_oubliee", "ziggourat_des_sables", "bastion_de_givre", "autel_infernal", "citadelle_des_ames",
				"temple_maya", "grand_temple_maya", "test_ruin")) {
			assertEquals(0.0, c.dropChance(other, true), other + ": never from another cursed guardian");
		}
		assertEquals(0.0, c.dropChance(null, true));
		assertEquals(Map.of(CursedGrimoireRules.MAGE_TOWER, chance), c.ruinDrops, "its only source by default");
		c.enabled = false;
		assertEquals(0.0, c.dropChance(CursedGrimoireRules.MAGE_TOWER, true), "disabled: never dropped");
		assertTrue(CursedGrimoireRules.drops(0.1, 0.05));
		assertFalse(CursedGrimoireRules.drops(0.1, 0.1));
		assertFalse(CursedGrimoireRules.drops(0, 0.0));
		assertTrue(CursedGrimoireRules.drops(1.0, 0.999));
		assertEquals(1.0, CursedGrimoireRules.dropChance(Map.of("x", 7.0), "x", true, true), "chance clamped");
	}

	// ================================================================== config

	@Test
	void cursedGrimoireSectionDefaultsAndBounds() {
		ScrollConfig cfg = config();
		ScrollConfig.CursedGrimoire c = cfg.cursedGrimoire;
		assertTrue(c.enabled);
		assertEquals("minecraft:enchanted_book", c.item);
		assertTrue(c.cooldownSeconds > cfg.grimoire.cooldownSeconds && c.cooldownSeconds <= 10, "a slightly longer cooldown");
		assertTrue(c.powerMultiplier > 1.5, "much stronger");
		assertEquals("grimoire_maudit", c.model);

		ScrollConfig bad = new ScrollConfig();
		ScrollConfig.CursedGrimoire b = bad.cursedGrimoire;
		b.item = "Ender_Pearl";
		b.powerMultiplier = 99;
		b.radiusMultiplier = -1;
		b.countMultiplier = Double.NaN;
		b.levelBonus = 9;
		Map<String, Map<String, Double>> spells = new LinkedHashMap<>();
		spells.put("meteor_shower", new LinkedHashMap<>(Map.of("count", 50.0, "bogus", 2.0)));
		spells.put("dance", new LinkedHashMap<>(Map.of("count", 2.0)));
		b.spells = spells;
		b.cooldownSeconds = -1;
		b.foodCost = new LinkedHashMap<>(Map.of("mythic", 40));
		b.bloodCost = new LinkedHashMap<>(Map.of("rare", -3));
		b.minHealth = 0;
		b.ruinDrops = new LinkedHashMap<>(Map.of("Tour_Du_Mage", 4.0));
		b.model = "Bad Model";
		List<String> warnings = bad.rebuild(id -> !id.contains("ender_pearl"));
		assertTrue(warnings.size() >= 4, "warnings: " + warnings);
		assertEquals("minecraft:enchanted_book", b.item);
		assertEquals(5.0, b.powerMultiplier);
		assertEquals(0.1, b.radiusMultiplier);
		assertEquals(0.1, b.countMultiplier, "NaN: the minimum");
		assertEquals(3, b.levelBonus);
		assertEquals(Map.of("meteor_shower", Map.of("count", 5.0)), b.spells, "unknown spells and parameters dropped, multipliers clamped");
		assertEquals(0.05, b.cooldownSeconds);
		assertEquals(20, b.foodCost.get("MYTHIC"));
		assertEquals(4, b.foodCost.get("RARE"), "missing rarities get their default cost");
		assertEquals(0, b.bloodCost.get("RARE"));
		assertEquals(1.0, b.minHealth);
		assertEquals(Map.of("tour_du_mage", 1.0), b.ruinDrops);
		assertEquals("", b.model);
	}

	@Test
	void cursedGrimoireSectionSurvivesTheJson5RoundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("scrolls.json5");
		ScrollConfig defaults = config();
		defaults.cursedGrimoire.spells.get("meteor_shower").put("count", 3.0);
		Json5.write(file, defaults);
		String text = Files.readString(file);
		assertTrue(text.contains("cursedGrimoire") && text.contains("Grimoire maudit") && text.contains("ruinDrops"), "written with its comments");
		ScrollConfig read = Json5.read(file, ScrollConfig.class);
		assertTrue(read.rebuild(id -> true).isEmpty());
		assertEquals(3.0, read.cursedGrimoire.spells.get("meteor_shower").get("count"));
		assertEquals(30, read.cursedGrimoire.power(Spell.METEOR_SHOWER).apply(Spell.METEOR_SHOWER.param("count"), 10), EPS);
		// A file of an older version (no cursedGrimoire section): the defaults.
		Files.writeString(file, "{ configVersion: 2, grimoire: { powerMultiplier: 1.0 } }");
		ScrollConfig old = Json5.read(file, ScrollConfig.class);
		assertTrue(old.rebuild(id -> true).isEmpty());
		assertEquals(new ScrollConfig().cursedGrimoire.fingerprint(), old.cursedGrimoire.fingerprint());
	}
}
