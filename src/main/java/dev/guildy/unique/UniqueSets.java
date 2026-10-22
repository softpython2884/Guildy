package dev.guildy.unique;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure logic of the armor sets (unit-tested): which sets a player wears and how many different pieces of each, the bonus
 * tiers reached (they add up: four pieces = the two-piece bonus and the four-piece one), the powers they give and the
 * stable ids of their attribute modifiers ({@code guildy:set_<set>_<pieces>_<n>}: applied once, never stacked).
 */
public final class UniqueSets {
	/** Path prefix of the attribute modifiers of the set bonuses (namespace guildy). */
	public static final String MODIFIER_PREFIX = "set_";

	private UniqueSets() {
	}

	/**
	 * Different pieces worn per set: {@code worn} = unique ids of the worn items (any order, nulls and duplicates allowed:
	 * two copies of the same piece count once), only the usable pieces of usable sets count.
	 */
	public static Map<String, Integer> count(Collection<@Nullable String> worn, UniqueConfig cfg) {
		Map<String, Set<String>> pieces = new LinkedHashMap<>();
		for (String id : worn) {
			UniqueConfig.UniqueDef def = id == null ? null : cfg.item(id);
			if (def == null || def.set.isEmpty() || cfg.set(def.set) == null) {
				continue;
			}
			pieces.computeIfAbsent(def.set, key -> new LinkedHashSet<>()).add(def.id);
		}
		Map<String, Integer> counts = new LinkedHashMap<>();
		pieces.forEach((set, ids) -> counts.put(set, ids.size()));
		return counts;
	}

	/** Bonuses reached with {@code worn} different pieces (every tier up to it), by increasing number of pieces. */
	public static List<UniqueConfig.SetBonus> active(UniqueConfig.@Nullable SetDef set, int worn) {
		List<UniqueConfig.SetBonus> out = new ArrayList<>();
		if (set != null) {
			for (UniqueConfig.SetBonus bonus : set.bonuses()) {
				if (bonus.pieces <= worn) {
					out.add(bonus);
				}
			}
		}
		return out;
	}

	/** Number of pieces of the highest tier reached (0 = no bonus). */
	public static int tier(UniqueConfig.@Nullable SetDef set, int worn) {
		int tier = 0;
		for (UniqueConfig.SetBonus bonus : active(set, worn)) {
			tier = Math.max(tier, bonus.pieces);
		}
		return tier;
	}

	/** Tier reached per set ({@code counts} from {@link #count}); sets without any tier reached are left out. */
	public static Map<String, Integer> tiers(Map<String, Integer> counts, UniqueConfig cfg) {
		Map<String, Integer> tiers = new LinkedHashMap<>();
		counts.forEach((set, worn) -> {
			int tier = tier(cfg.set(set), worn);
			if (tier > 0) {
				tiers.put(set, tier);
			}
		});
		return tiers;
	}

	/** Powers of every bonus reached. */
	public static Set<SetPower> powers(Map<String, Integer> counts, UniqueConfig cfg) {
		Set<SetPower> powers = EnumSet.noneOf(SetPower.class);
		counts.forEach((set, worn) -> {
			for (UniqueConfig.SetBonus bonus : active(cfg.set(set), worn)) {
				if (bonus.power() != null) {
					powers.add(bonus.power());
				}
			}
		});
		return powers;
	}

	/** One attribute modifier of a bonus: its stable id path, the attribute, the amount and the operation. */
	public record Modifier(String path, String attribute, double amount, String operation) {
	}

	/** Every attribute modifier the worn pieces give (each one once, stable ids). */
	public static List<Modifier> modifiers(Map<String, Integer> counts, UniqueConfig cfg) {
		List<Modifier> out = new ArrayList<>();
		counts.forEach((set, worn) -> {
			for (UniqueConfig.SetBonus bonus : active(cfg.set(set), worn)) {
				for (int i = 0; i < bonus.attributes.size(); i++) {
					UniqueConfig.AttributeEntry entry = bonus.attributes.get(i);
					out.add(new Modifier(modifierPath(set, bonus.pieces, i), entry.attribute, entry.amount, entry.operation));
				}
			}
		});
		return out;
	}

	/** "set_hiver_eternel_4_0": the n-th attribute of the four-piece bonus of a set. */
	public static String modifierPath(String set, int pieces, int index) {
		return MODIFIER_PREFIX + set + "_" + pieces + "_" + index;
	}

	/** A modifier id path written by the sets (namespace guildy). */
	public static boolean isSetModifier(String path) {
		return path.startsWith(MODIFIER_PREFIX);
	}
}
