package dev.guildy.rune;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pure rules of the runes (no Minecraft classes, unit tested): which runes may go on a piece (slot pools, the mythic
 * rule), the weighted draw (tier first, then a rune of that tier), the inscription chance, the daily cap and the
 * inspection interval of an armor stand.
 */
public final class RuneRules {
	private RuneRules() {
	}

	/**
	 * May this rune go on a piece worn in {@code slot} (head, chest, legs, feet) whose item id is {@code itemId}? Enabled,
	 * weight above 0, allowed slot; a mythic rune only on a chestplate listed in {@code mythicItems}.
	 */
	public static boolean eligible(RuneConfig.RuneDef def, String slot, String itemId, List<String> mythicItems) {
		if (def == null || !def.enabled || def.weight <= 0 || !def.allows(slot)) {
			return false;
		}
		if (def.tier() == RuneTier.MYTHIC) {
			return "chest".equals(slot) && mythicItems != null && mythicItems.contains(itemId);
		}
		return true;
	}

	/** The runes that may go on that piece, in config order. */
	public static List<RuneConfig.RuneDef> candidates(List<RuneConfig.RuneDef> runes, String slot, String itemId, List<String> mythicItems) {
		List<RuneConfig.RuneDef> out = new ArrayList<>();
		for (RuneConfig.RuneDef def : runes == null ? List.<RuneConfig.RuneDef>of() : runes) {
			if (eligible(def, slot, itemId, mythicItems)) {
				out.add(def);
			}
		}
		return out;
	}

	/**
	 * Tier drawn among {@code weights} (tiers without a weight above 0 are skipped) with {@code roll} in [0, 1).
	 *
	 * @return null when no tier has a weight
	 */
	public static @Nullable RuneTier pickTier(Map<RuneTier, Integer> weights, double roll) {
		long total = 0;
		for (RuneTier tier : RuneTier.values()) {
			total += Math.max(0, weights.getOrDefault(tier, 0));
		}
		if (total <= 0) {
			return null;
		}
		double target = clampRoll(roll) * total;
		double acc = 0;
		RuneTier last = null;
		for (RuneTier tier : RuneTier.values()) {
			int weight = Math.max(0, weights.getOrDefault(tier, 0));
			if (weight <= 0) {
				continue;
			}
			acc += weight;
			last = tier;
			if (target < acc) {
				return tier;
			}
		}
		return last;
	}

	/** A rune drawn among {@code pool} by weight with {@code roll} in [0, 1), null when the pool is empty. */
	public static RuneConfig.@Nullable RuneDef pickWeighted(List<RuneConfig.RuneDef> pool, double roll) {
		long total = 0;
		for (RuneConfig.RuneDef def : pool) {
			total += Math.max(0, def.weight);
		}
		if (total <= 0) {
			return null;
		}
		double target = clampRoll(roll) * total;
		double acc = 0;
		RuneConfig.RuneDef last = null;
		for (RuneConfig.RuneDef def : pool) {
			if (def.weight <= 0) {
				continue;
			}
			acc += def.weight;
			last = def;
			if (target < acc) {
				return def;
			}
		}
		return last;
	}

	/**
	 * The rune inscribed on a piece: a tier is drawn first among the tiers that have a rune for that piece (weights of
	 * {@code config.tiers}), then a rune of that tier by weight.
	 *
	 * @return null when no rune can go on that piece
	 */
	public static RuneConfig.@Nullable RuneDef pick(RuneConfig config, String slot, String itemId, double tierRoll, double runeRoll) {
		List<RuneConfig.RuneDef> pool = candidates(config.runes, slot, itemId, config.mythicItems);
		Map<RuneTier, List<RuneConfig.RuneDef>> byTier = new EnumMap<>(RuneTier.class);
		for (RuneConfig.RuneDef def : pool) {
			byTier.computeIfAbsent(def.tier(), k -> new ArrayList<>()).add(def);
		}
		Map<RuneTier, Integer> weights = new EnumMap<>(RuneTier.class);
		for (Map.Entry<RuneTier, List<RuneConfig.RuneDef>> entry : byTier.entrySet()) {
			weights.put(entry.getKey(), config.tiers.weight(entry.getKey()));
		}
		RuneTier tier = pickTier(weights, tierRoll);
		return tier == null ? null : pickWeighted(byTier.get(tier), runeRoll);
	}

	/** Chance of each tier for that piece (sums to 1, or all 0 when no rune can go on it): shown in the docs and tests. */
	public static Map<RuneTier, Double> tierChances(RuneConfig config, String slot, String itemId) {
		Map<RuneTier, Double> out = new EnumMap<>(RuneTier.class);
		List<RuneConfig.RuneDef> pool = candidates(config.runes, slot, itemId, config.mythicItems);
		long total = 0;
		Map<RuneTier, Integer> weights = new EnumMap<>(RuneTier.class);
		for (RuneConfig.RuneDef def : pool) {
			if (!weights.containsKey(def.tier())) {
				int weight = Math.max(0, config.tiers.weight(def.tier()));
				weights.put(def.tier(), weight);
				total += weight;
			}
		}
		for (RuneTier tier : RuneTier.values()) {
			Integer weight = weights.get(tier);
			out.put(tier, weight == null || total <= 0 ? 0.0 : weight / (double) total);
		}
		return out;
	}

	/** One inscription chance: succeeds when the roll (0 inclusive to 1 exclusive) is below the chance. */
	public static boolean succeeds(double roll, double chance) {
		return roll < chance;
	}

	/** Runes made on {@code today} by a blacksmith whose count {@code count} belongs to day {@code day}. */
	public static int madeToday(long day, int count, long today) {
		return day == today ? Math.max(0, count) : 0;
	}

	/** May one more rune be inscribed today (cap {@code maxPerDay}, 0 = never)? */
	public static boolean underCap(long day, int count, long today, int maxPerDay) {
		return madeToday(day, count, today) < maxPerDay;
	}

	/** Is an inspection of a stand last inspected at {@code last} (game ticks, -1 = never) due at {@code now}? */
	public static boolean inspectionDue(long last, long now, long intervalTicks) {
		return last < 0 || now - last >= intervalTicks;
	}

	/** Game day of a clock time (ticks). */
	public static long day(long clockTime) {
		return Math.floorDiv(clockTime, 24000L);
	}

	private static double clampRoll(double roll) {
		if (!Double.isFinite(roll) || roll < 0) {
			return 0;
		}
		return Math.min(roll, Math.nextDown(1.0));
	}
}
