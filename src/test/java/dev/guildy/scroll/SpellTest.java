package dev.guildy.scroll;

import org.junit.jupiter.api.Test;

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

/**
 * Spell parameters: sane defaults within their bounds, clamping and rounding, resolution of partial maps, lore formatting.
 */
class SpellTest {
	@Test
	void parametersHaveSaneBounds() {
		Set<String> ids = new HashSet<>();
		for (Spell spell : Spell.values()) {
			assertTrue(ids.add(spell.id), "unique id " + spell.id);
			assertEquals(spell, Spell.byId(" " + spell.id.toUpperCase() + " "));
			assertFalse(spell.params().isEmpty(), spell.id + " has parameters");
			for (Spell.Param param : spell.params()) {
				assertTrue(param.min() <= param.defaultValue() && param.defaultValue() <= param.max(),
						spell.id + "." + param.name() + " default within its bounds");
				assertTrue(param.min() >= 0, spell.id + "." + param.name() + " never negative");
				if (param.integer()) {
					assertEquals(Math.rint(param.defaultValue()), param.defaultValue(), spell.id + "." + param.name() + " integer default");
				}
			}
		}
		assertNull(Spell.byId("summon_dragon"));
		assertNull(Spell.byId(null));
	}

	@Test
	void spellsAreStrongerThanTheUniqueWeaponPowers() {
		// Foudrelame: 4 bolts of 10 health points; Croc-de-Givre: 4 cold damage at 6 blocks.
		assertTrue(Spell.METEOR_SHOWER.param("count").defaultValue() * Spell.METEOR_SHOWER.param("damage").defaultValue() > 40);
		assertTrue(Spell.CHAIN_STORM.param("count").defaultValue() * Spell.CHAIN_STORM.param("damage").defaultValue() > 40);
		assertTrue(Spell.BLIZZARD.param("damage").defaultValue() * Spell.BLIZZARD.param("duration").defaultValue() > 4);
		assertTrue(Spell.SOLAR_NOVA.param("damage").defaultValue() >= 20, "a nova kills a zombie with the undead bonus");
		assertTrue(Spell.SINGULARITY.param("damage").defaultValue() > Spell.SOLAR_NOVA.param("damage").defaultValue(), "mythic hits hardest");
	}

	@Test
	void resolveFillsClampsAndDrops() {
		List<String> warnings = new ArrayList<>();
		Map<String, Double> raw = new LinkedHashMap<>();
		raw.put("radius", 500.0);
		raw.put("count", 2.6);
		raw.put("damage", -4.0);
		raw.put("nonsense", 1.0);
		Map<String, Double> out = Spell.METEOR_SHOWER.resolve(raw, "test", warnings);
		assertEquals(12.0, out.get("radius"), "clamped to the maximum");
		assertEquals(3.0, out.get("count"), "counts are rounded");
		assertEquals(0.0, out.get("damage"), "clamped to the minimum");
		assertFalse(out.containsKey("nonsense"));
		assertEquals(40.0, out.get("range"), "missing: default");
		assertEquals(Spell.METEOR_SHOWER.params().size(), out.size());
		assertEquals(4, warnings.size(), "radius, count, damage and the unknown parameter: " + warnings);
		List<String> none = new ArrayList<>();
		assertEquals(Spell.AEGIS.defaults(), Spell.AEGIS.resolve(null, "test", none));
		assertTrue(none.isEmpty());
	}

	@Test
	void formatting() {
		Spell.Param damage = Spell.METEOR_SHOWER.param("damage");
		assertNotNull(damage);
		assertEquals("6", damage.format(12), "health points shown as hearts");
		assertEquals("2,5", damage.format(5));
		assertEquals("III", Spell.AEGIS.param("absorptionLevel").format(3));
		assertEquals("×1,5", Spell.SOLAR_NOVA.param("undeadMultiplier").format(1.5));
		assertEquals("0,75", Spell.number(0.75));
		assertEquals("10", Spell.number(10.0000001));
	}
}
