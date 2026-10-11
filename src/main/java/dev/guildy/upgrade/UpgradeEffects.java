package dev.guildy.upgrade;

import java.util.List;
import java.util.Locale;

/**
 * Effect keys usable in upgrades.json5 ("effects" of a node) and how their totals are capped. Pure (no Minecraft classes).
 * The value of an effect for a guild is the sum over its unlocked nodes ({@link Upgrades#value}).
 */
public final class UpgradeEffects {
	/** Heart integrity during waves: +N %. */
	public static final String HEART_INTEGRITY_PCT = "heart_integrity_pct";
	/** Guard villagers damage: +N %. */
	public static final String GUARD_DAMAGE_PCT = "guard_damage_pct";
	/** Regeneration (level N) for members standing in their territory while a wave is running. */
	public static final String WAVE_REGEN = "wave_regen";
	/** Treasury gold lost on a defeat: -N % (capped at 100). */
	public static final String DEFEAT_LOSS_REDUCTION_PCT = "defeat_loss_reduction_pct";
	/** Chance that each villager leaves after a defeat: -N % (capped at 100). */
	public static final String VILLAGER_LOYALTY_PCT = "villager_loyalty_pct";
	/** Claimed quest rewards: +N % guild XP and treasury gold (never the gold of DELIVER quests). */
	public static final String QUEST_REWARD_PCT = "quest_reward_pct";
	/** Wave victory treasury gold and guild XP: +N %. */
	public static final String WAVE_REWARD_PCT = "wave_reward_pct";
	/** Extension flag price: -N % (capped at 100, never below 1 gold). */
	public static final String FLAG_DISCOUNT_PCT = "flag_discount_pct";
	/** Maximum claimed chunks: +N. */
	public static final String EXTRA_CHUNKS = "extra_chunks";
	/** Maximum guild villagers: +N. */
	public static final String EXTRA_VILLAGERS = "extra_villagers";
	/** Villager work durations: -N % (capped at {@link #MAX_WORK_SPEED_PCT}). */
	public static final String WORK_SPEED_PCT = "work_speed_pct";
	/** Haste (level N) for members standing in their territory. */
	public static final String TERRITORY_HASTE = "territory_haste";

	/** Every known key, in display order. */
	public static final List<String> ALL = List.of(HEART_INTEGRITY_PCT, GUARD_DAMAGE_PCT, WAVE_REGEN, DEFEAT_LOSS_REDUCTION_PCT,
			VILLAGER_LOYALTY_PCT, QUEST_REWARD_PCT, WAVE_REWARD_PCT, FLAG_DISCOUNT_PCT, EXTRA_CHUNKS, EXTRA_VILLAGERS,
			WORK_SPEED_PCT, TERRITORY_HASTE);

	public static final double MAX_WORK_SPEED_PCT = 75;
	/** Highest potion effect level given by the territory buffs. */
	public static final int MAX_EFFECT_LEVEL = 5;

	private UpgradeEffects() {
	}

	public static boolean isKnown(String key) {
		return ALL.contains(key);
	}

	/** Keys whose value is a potion effect level (1 = level I). */
	public static boolean isLevel(String key) {
		return WAVE_REGEN.equals(key) || TERRITORY_HASTE.equals(key);
	}

	/** Total as actually applied by the game (caps), never negative. */
	public static double effective(String key, double total) {
		double value = Double.isFinite(total) ? Math.max(0, total) : 0;
		return switch (key) {
			case DEFEAT_LOSS_REDUCTION_PCT, VILLAGER_LOYALTY_PCT, FLAG_DISCOUNT_PCT -> Math.min(100, value);
			case WORK_SPEED_PCT -> Math.min(MAX_WORK_SPEED_PCT, value);
			case WAVE_REGEN, TERRITORY_HASTE -> Math.min(MAX_EFFECT_LEVEL, Math.floor(value + 1e-9));
			default -> value;
		};
	}

	/** "25", "12,5" (French decimal comma, at most one decimal). */
	public static String format(double value) {
		double rounded = Math.round(value * 10) / 10.0;
		if (Math.abs(rounded - Math.rint(rounded)) < 1e-9) {
			return Long.toString((long) Math.rint(rounded));
		}
		return String.format(Locale.FRANCE, "%.1f", rounded);
	}

	/** Roman numeral of an effect level (I to X, plain number beyond). */
	public static String roman(int level) {
		String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return level >= 1 && level <= numerals.length ? numerals[level - 1] : Integer.toString(level);
	}

	/** Display argument of an effect line: roman numeral for levels, formatted number otherwise. */
	public static String displayValue(String key, double value) {
		if (isLevel(key)) {
			return roman((int) Math.max(1, Math.floor(value + 1e-9)));
		}
		return format(value);
	}
}
