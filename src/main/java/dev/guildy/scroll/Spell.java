package dev.guildy.scroll;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The spells a scroll can cast (pure Java): id, colors of the shared visuals (channel circle), and the parameters a scroll
 * definition may tune, each with its default and its bounds. Formatting of the parameters for the item description.
 */
public enum Spell {
	METEOR_SHOWER("meteor_shower", 0xFF7A1A, 0x8B1A00, true,
			p("range", 40, 8, 64, Unit.BLOCKS),
			p("radius", 6, 2, 12, Unit.BLOCKS),
			p("count", 10, 1, 30, Unit.COUNT),
			p("damage", 12, 0, 200, Unit.HEARTS),
			p("impactRadius", 3, 1, 6, Unit.BLOCKS),
			p("fireSeconds", 4, 0, 20, Unit.SECONDS)),
	CHAIN_STORM("chain_storm", 0x7FDBFF, 0xF4FBFF, true,
			p("range", 24, 6, 48, Unit.BLOCKS),
			p("count", 8, 1, 20, Unit.COUNT),
			p("damage", 14, 0, 200, Unit.HEARTS),
			p("chainRadius", 10, 3, 20, Unit.BLOCKS),
			p("stunSeconds", 2, 0, 10, Unit.SECONDS)),
	BLIZZARD("blizzard", 0xBDEBFF, 0xFFFFFF, true,
			p("range", 24, 0, 48, Unit.BLOCKS),
			p("radius", 7, 3, 14, Unit.BLOCKS),
			p("duration", 8, 2, 30, Unit.SECONDS),
			p("damage", 4, 0, 100, Unit.HEARTS),
			p("slowLevel", 4, 1, 6, Unit.LEVEL)),
	SOLAR_NOVA("solar_nova", 0xFFE066, 0xFF7A00, true,
			p("radius", 10, 3, 20, Unit.BLOCKS),
			p("damage", 20, 0, 200, Unit.HEARTS),
			p("fireSeconds", 6, 0, 20, Unit.SECONDS),
			p("undeadMultiplier", 1.5, 1, 4, Unit.MULTIPLIER),
			p("knockback", 1.2, 0, 3, Unit.RAW)),
	SINGULARITY("singularity", 0xA43BFF, 0x14001F, true,
			p("range", 32, 6, 64, Unit.BLOCKS),
			p("radius", 10, 3, 18, Unit.BLOCKS),
			p("pullSeconds", 3, 1, 8, Unit.SECONDS),
			p("pullStrength", 0.35, 0.05, 1.5, Unit.RAW),
			p("damage", 26, 0, 300, Unit.HEARTS)),
	AEGIS("aegis", 0xFFD54A, 0xFFF6CC, false,
			p("radius", 6, 3, 12, Unit.BLOCKS),
			p("duration", 12, 3, 60, Unit.SECONDS),
			p("absorptionLevel", 3, 1, 5, Unit.LEVEL),
			p("resistanceLevel", 2, 1, 4, Unit.LEVEL),
			p("push", 0.8, 0, 3, Unit.RAW),
			p("damage", 2, 0, 50, Unit.HEARTS)),
	RECALL("recall", 0x3FE0D0, 0xB57CFF, false,
			p("radius", 6, 0, 16, Unit.BLOCKS),
			p("maxAllies", 8, 0, 20, Unit.COUNT)),
	GUARDIANS("guardians", 0xC8CCD4, 0x5E6B7A, false,
			p("count", 2, 1, 4, Unit.COUNT),
			p("duration", 60, 10, 300, Unit.SECONDS)),
	BLESSING("blessing", 0x9BFF7A, 0xFF9EC7, false,
			p("radius", 12, 3, 32, Unit.BLOCKS),
			p("heal", 16, 0, 100, Unit.HEARTS),
			p("regenSeconds", 8, 0, 60, Unit.SECONDS),
			p("regenLevel", 2, 1, 5, Unit.LEVEL),
			p("cleanse", 1, 0, 1, Unit.FLAG)),
	REVELATION("revelation", 0x00E5FF, 0x2B6CFF, false,
			p("radius", 64, 8, 128, Unit.BLOCKS),
			p("duration", 30, 5, 120, Unit.SECONDS)),
	EARTHQUAKE("earthquake", 0xA0703A, 0xE0C08A, true,
			p("radius", 10, 3, 20, Unit.BLOCKS),
			p("waves", 3, 1, 6, Unit.COUNT),
			p("damage", 10, 0, 200, Unit.HEARTS),
			p("lift", 0.6, 0, 2, Unit.RAW),
			p("slowSeconds", 4, 0, 20, Unit.SECONDS)),
	ARCANE_RAY("arcane_ray", 0xFF3EDB, 0x6BE7FF, true,
			p("length", 32, 8, 64, Unit.BLOCKS),
			p("duration", 3, 1, 10, Unit.SECONDS),
			p("damage", 5, 0, 100, Unit.HEARTS),
			p("width", 0.9, 0.3, 3, Unit.BLOCKS));

	/** How a parameter is shown in the item description. */
	public enum Unit {
		/** Health points shown as hearts (value / 2). */
		HEARTS,
		BLOCKS,
		SECONDS,
		COUNT,
		/** Effect level (1 = level I). */
		LEVEL,
		/** "×1,5". */
		MULTIPLIER,
		/** 0 / 1. */
		FLAG,
		RAW
	}

	/** A tunable parameter: default value and bounds (integers for counts, levels and flags). */
	public record Param(String name, double defaultValue, double min, double max, Unit unit) {
		public boolean integer() {
			return unit == Unit.COUNT || unit == Unit.LEVEL || unit == Unit.FLAG;
		}

		/** Clamped (and rounded for integers); NaN / infinite = default. */
		public double clamp(double value) {
			if (!Double.isFinite(value)) {
				return defaultValue;
			}
			double v = Math.max(min, Math.min(max, value));
			return integer() ? Math.rint(v) : v;
		}

		/** "5" / "2,5" / "×1,5" / "III" (lore value). */
		public String format(double value) {
			return switch (unit) {
				case HEARTS -> number(value / 2.0);
				case LEVEL -> roman((int) Math.rint(value));
				case MULTIPLIER -> "×" + number(value);
				default -> number(value);
			};
		}
	}

	public final String id;
	/** Main color (RGB) of the shared visuals. */
	public final int color;
	/** Second color (RGB) of the shared visuals. */
	public final int accent;
	/** Hurts monsters (an attack): changes the channel sound. */
	public final boolean offensive;
	private final Map<String, Param> params;

	Spell(String id, int color, int accent, boolean offensive, Param... params) {
		this.id = id;
		this.color = color;
		this.accent = accent;
		this.offensive = offensive;
		Map<String, Param> map = new LinkedHashMap<>();
		for (Param param : params) {
			map.put(param.name(), param);
		}
		this.params = Collections.unmodifiableMap(map);
	}

	private static Param p(String name, double defaultValue, double min, double max, Unit unit) {
		return new Param(name, defaultValue, min, max, unit);
	}

	public Collection<Param> params() {
		return params.values();
	}

	public @Nullable Param param(String name) {
		return params.get(name);
	}

	/** Every parameter with its default value, in declaration order. */
	public Map<String, Double> defaults() {
		Map<String, Double> map = new LinkedHashMap<>();
		for (Param param : params.values()) {
			map.put(param.name(), param.defaultValue());
		}
		return map;
	}

	/**
	 * Complete, clamped parameters from a definition's (partial) map: missing ones get their default, unknown ones are
	 * dropped. Problems are added to {@code warnings}.
	 */
	public Map<String, Double> resolve(@Nullable Map<String, Double> raw, String owner, List<String> warnings) {
		Map<String, Double> out = new LinkedHashMap<>();
		Map<String, Double> given = new LinkedHashMap<>();
		if (raw != null) {
			for (Map.Entry<String, Double> entry : raw.entrySet()) {
				if (entry.getKey() == null) {
					continue;
				}
				Param param = params.get(entry.getKey().trim());
				if (param == null) {
					warnings.add(owner + ": unknown parameter '" + entry.getKey() + "' for spell " + id + " (" + String.join(", ", params.keySet())
							+ "): ignored");
					continue;
				}
				given.put(param.name(), entry.getValue());
			}
		}
		for (Param param : params.values()) {
			Double value = given.get(param.name());
			if (value == null) {
				out.put(param.name(), param.defaultValue());
				continue;
			}
			double fixed = param.clamp(value);
			if (fixed != value) {
				warnings.add(owner + ": " + param.name() + " " + value + " -> " + number(fixed) + " (" + number(param.min()) + ".."
						+ number(param.max()) + ")");
			}
			out.put(param.name(), fixed);
		}
		return out;
	}

	/** Lang key prefix: "scroll.spell.meteor_shower". */
	public String langKey() {
		return "scroll.spell." + id;
	}

	public static @Nullable Spell byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		String key = id.trim().toLowerCase(Locale.ROOT);
		for (Spell spell : values()) {
			if (spell.id.equals(key)) {
				return spell;
			}
		}
		return null;
	}

	public static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (Spell spell : values()) {
			ids.add(spell.id);
		}
		return ids;
	}

	/** "5", "2,5" (French decimal comma, one decimal at most). */
	public static String number(double value) {
		if (Math.abs(value - Math.rint(value)) < 1e-6) {
			return String.valueOf((long) Math.rint(value));
		}
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		return text.replace('.', ',');
	}

	static String roman(int n) {
		String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 1 && n <= numerals.length ? numerals[n - 1] : String.valueOf(n);
	}
}
