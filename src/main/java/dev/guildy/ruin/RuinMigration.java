package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.AbilityDef;
import dev.guildy.ruin.RuinConfig.BossDef;
import dev.guildy.ruin.RuinConfig.GuardDef;
import dev.guildy.ruin.RuinConfig.RuinDef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure migrations of ruins.json5 (no Minecraft classes, unit-tested): a value of a default ruin still equal to the default
 * of an older version is replaced by the new default; anything the admin changed is kept as it is.
 * <p>
 * Version 2 (Guildy 1.8.2, themed dungeons): no raiders in the Mayan temples (their undead: bogged, parched, zombies, cave
 * spiders), parched archers in the ziggurat, the celestial jaguar becomes a giant ocelot (a giant black cat when cursed)
 * with its jaguar abilities. The boss is migrated as a whole (entity, scale and speed all on the 1.8 defaults).
 * <p>
 * Version 3 (Guildy 1.10, balance): about three times more gold from every guardian; the gold range of a default ruin is
 * migrated as a pair (both bounds still on their 1.9 defaults), so that a range changed by the admin is never mixed with a
 * new bound.
 * <p>
 * Version 4 (Guildy 1.14, the dread form from Menace VII): the default ruins get their dread abilities.
 */
final class RuinMigration {
	/** Gold ranges {min, max} of the default ruins up to Guildy 1.9 (format 2), in the order of the file. */
	static final Map<String, int[]> OLD_GOLD = oldGold();

	private RuinMigration() {
	}

	private static Map<String, int[]> oldGold() {
		Map<String, int[]> map = new LinkedHashMap<>();
		map.put("crypte_oubliee", new int[] {4, 8});
		map.put("tour_du_mage", new int[] {6, 10});
		map.put("ziggourat_des_sables", new int[] {6, 10});
		map.put("bastion_de_givre", new int[] {8, 14});
		map.put("autel_infernal", new int[] {10, 18});
		map.put("citadelle_des_ames", new int[] {14, 24});
		map.put("temple_maya", new int[] {8, 14});
		map.put("grand_temple_maya", new int[] {18, 28});
		return Collections.unmodifiableMap(map);
	}

	/** Migrates the definitions read from a file of version {@code fromVersion}. @return the values changed ("ruin.field") */
	static List<String> migrate(@Nullable Map<String, RuinDef> ruins, int fromVersion) {
		List<String> changed = new ArrayList<>();
		if (ruins == null) {
			return changed;
		}
		if (fromVersion < 2) {
			themedDungeons(ruins, changed);
		}
		if (fromVersion < 3) {
			moreGold(ruins, changed);
		}
		if (fromVersion < 4) {
			dreadAbilities(ruins, changed);
		}
		return changed;
	}

	/**
	 * Version 4: every default ruin without dread abilities (a file written before they existed) gets those of its guardian;
	 * a list already filled by the admin is kept.
	 */
	private static void dreadAbilities(Map<String, RuinDef> ruins, List<String> changed) {
		for (String id : RuinDefaults.ruins().keySet()) {
			RuinDef def = ruins.get(id);
			if (def != null && (def.dreadAbilities == null || def.dreadAbilities.isEmpty())) {
				def.dreadAbilities = RuinDefaults.dreadOf(id);
				changed.add(id + ".dreadAbilities");
			}
		}
	}

	/** Version 3: the gold range of each default ruin still on its 1.9 defaults becomes the new one. */
	private static void moreGold(Map<String, RuinDef> ruins, List<String> changed) {
		Map<String, RuinDef> defaults = RuinDefaults.ruins();
		for (Map.Entry<String, int[]> old : OLD_GOLD.entrySet()) {
			RuinDef def = ruins.get(old.getKey());
			RuinDef fresh = defaults.get(old.getKey());
			if (def == null || def.rewards == null || fresh == null) {
				continue;
			}
			if (def.rewards.goldMin == old.getValue()[0] && def.rewards.goldMax == old.getValue()[1]) {
				def.rewards.goldMin = fresh.rewards.goldMin;
				def.rewards.goldMax = fresh.rewards.goldMax;
				changed.add(old.getKey() + ".rewards.gold");
			}
		}
	}

	/** Version 2: themed escorts and guards, the celestial jaguar. */
	private static void themedDungeons(Map<String, RuinDef> ruins, List<String> changed) {
		RuinDef ziggurat = ruins.get("ziggourat_des_sables");
		if (ziggurat != null && ziggurat.minions != null && sameTypes(ziggurat.minions.types, Map.of("minecraft:husk", 1))) {
			ziggurat.minions.types = new LinkedHashMap<>(RuinDefaults.zigguratEscort());
			changed.add("ziggourat_des_sables.minions.types");
		}
		RuinDef serpent = ruins.get("temple_maya");
		if (serpent != null) {
			if (serpent.minions != null && sameTypes(serpent.minions.types, Map.of("minecraft:bogged", 3, "minecraft:cave_spider", 2))) {
				serpent.minions.types = new LinkedHashMap<>(RuinDefaults.serpentEscort());
				changed.add("temple_maya.minions.types");
			}
			// 1.8 default: the escort's types and gear (empty), under the temple's name (none before 1.8).
			GuardDef g = serpent.guards;
			if (g != null && empty(g.types) && empty(g.equipment) && (g.effects == null || g.effects.isEmpty())) {
				g.types = new LinkedHashMap<>(RuinDefaults.serpentGuards());
				g.equipment = new LinkedHashMap<>(Map.of("head", RuinDefaults.JADE_HELMET));
				if (g.name == null || g.name.isBlank()) {
					g.name = "&2Gardien du temple";
				}
				changed.add("temple_maya.guards");
			}
		}
		RuinDef jaguar = ruins.get("grand_temple_maya");
		if (jaguar != null) {
			BossDef b = jaguar.boss;
			if (b != null && "minecraft:ravager".equals(RuinConfig.normalize(b.entity)) && near(b.scale, 1.6) && near(b.speed, 0)) {
				BossDef fresh = RuinDefaults.jaguar();
				b.entity = fresh.entity;
				b.scale = fresh.scale;
				b.speed = fresh.speed;
				if (b.cursedForm == null || b.cursedForm.isEmpty()) {
					b.cursedForm = RuinDefaults.jaguarCursedForm();
				}
				changed.add("grand_temple_maya.boss");
			}
			if (sameAbilities(jaguar.abilities, oldJaguarAbilities())) {
				jaguar.abilities = RuinDefaults.jaguarAbilities();
				changed.add("grand_temple_maya.abilities");
			}
			Map<String, Integer> warriors = Map.of("minecraft:vindicator", 3, "minecraft:bogged", 2, "minecraft:evoker", 1);
			if (jaguar.minions != null && sameTypes(jaguar.minions.types, warriors)) {
				jaguar.minions.types = new LinkedHashMap<>(RuinDefaults.jaguarEscort());
				changed.add("grand_temple_maya.minions.types");
			}
			Map<String, Integer> guards = Map.of("minecraft:vindicator", 4, "minecraft:bogged", 2, "minecraft:evoker", 1);
			if (jaguar.guards != null && sameTypes(jaguar.guards.types, guards)) {
				jaguar.guards.types = new LinkedHashMap<>(RuinDefaults.jaguarGuards());
				changed.add("grand_temple_maya.guards.types");
			}
		}
	}

	/** The celestial jaguar's abilities in Guildy 1.8 (a giant ravager). */
	static List<AbilityDef> oldJaguarAbilities() {
		return List.of(
				RuinDefaults.ability("ground_slam", 11, 11, 7, 1, "gold", 1.0),
				RuinDefaults.ability("fire_ring", 13, 9, 8, 1, "solar", 1.0),
				RuinDefaults.ability("volley", 10, 6, 4, 6, "solar", 1.0),
				RuinDefaults.ability("blink", 12, 12, 4, 1, "solar", 0.85),
				RuinDefaults.ability("fire_ring", 16, 8, 9, 1, "jade", 0.7),
				RuinDefaults.ability("life_drain", 18, 5, 12, 1, "gold", 0.5),
				RuinDefaults.ability("summon", 24, 0, 5, 3, "", 0.6),
				RuinDefaults.enrage(0.3, 2));
	}

	// ------------------------------------------------------------------ comparisons

	/** Same entity weights (ids normalized, order ignored). */
	static boolean sameTypes(@Nullable Map<String, Integer> actual, Map<String, Integer> expected) {
		if (actual == null) {
			return false;
		}
		Map<String, Integer> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : actual.entrySet()) {
			normalized.merge(RuinConfig.normalize(entry.getKey()), entry.getValue() == null ? 0 : entry.getValue(), Integer::sum);
		}
		return normalized.equals(expected);
	}

	/** Same abilities, in the same order (types and styles ignoring case, numbers within a rounding error). */
	static boolean sameAbilities(@Nullable List<AbilityDef> actual, List<AbilityDef> expected) {
		if (actual == null || actual.size() != expected.size()) {
			return false;
		}
		for (int i = 0; i < expected.size(); i++) {
			AbilityDef a = actual.get(i);
			AbilityDef b = expected.get(i);
			if (a == null || !lower(a.type).equals(lower(b.type)) || !lower(a.style).equals(lower(b.style)) || !near(a.cooldownSeconds, b.cooldownSeconds)
					|| !near(a.damage, b.damage) || !near(a.radius, b.radius) || a.count != b.count || !near(a.healthBelow, b.healthBelow)
					|| a.amplifier != b.amplifier) {
				return false;
			}
		}
		return true;
	}

	private static boolean empty(@Nullable Map<?, ?> map) {
		return map == null || map.isEmpty();
	}

	private static String lower(@Nullable String s) {
		return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0E-6;
	}
}
