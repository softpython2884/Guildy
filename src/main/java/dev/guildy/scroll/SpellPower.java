package dev.guildy.scroll;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * How strong one cast is (pure, unit-tested): every spell parameter goes through {@link #apply} when a spell reads it, so
 * the spells never need to know who cast them. A scroll casts at {@link #NONE}; a grimoire multiplies damage, heals and
 * durations ({@link #of}); the cursed grimoire also amplifies ranges and radii, counts (meteors, chained monsters,
 * waves...), effect levels, and may set its own multiplier per parameter of a spell.
 *
 * <p>Bounds of an amplified value: damage, heals and durations up to {@link #POWER_CEILING} times the parameter's maximum
 * (the old bound of the grimoire's power, never reached by it), ranges, radii and strengths up to {@link #REACH_CEILING}
 * times its maximum, counts, levels and flags never above the maximum (performance, golem limit, effect levels); never
 * below the parameter's minimum (damage, heals and durations never below zero).
 *
 * @param power     damage, heals and durations (hearts and seconds)
 * @param radius    ranges and radii (blocks)
 * @param count     counts (rounded to the nearest integer)
 * @param levels    effect levels added (Lenteur, Absorption, Résistance, Régénération)
 * @param overrides multiplier of a parameter by name, replacing the general one (levels: multiplied, then rounded)
 * @param cursed    the cast comes from a cursed grimoire (its own visuals on top of the spell's)
 */
public record SpellPower(double power, double radius, double count, int levels, Map<String, Double> overrides, boolean cursed) {
	/** A scroll: every parameter as written in scrolls.json5. */
	public static final SpellPower NONE = new SpellPower(1.0, 1.0, 1.0, 0, Map.of(), false);
	/** Damage, heals and durations: never more than this many times the parameter's maximum. */
	public static final double POWER_CEILING = 5.0;
	/** Ranges, radii and strengths: never more than this many times the parameter's maximum. */
	public static final double REACH_CEILING = 1.5;

	public SpellPower {
		power = finite(power);
		radius = finite(radius);
		count = finite(count);
		levels = Math.max(0, levels);
		overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
	}

	private static double finite(double value) {
		return Double.isFinite(value) ? Math.max(0, value) : 1.0;
	}

	/** A grimoire cast: damage, heals and durations multiplied by {@code power}, nothing else changes. */
	public static SpellPower of(double power) {
		return new SpellPower(power, 1.0, 1.0, 0, Map.of(), false);
	}

	/**
	 * This power with damage, heals and durations further multiplied by {@code factor} (the caster's magic elixirs, see
	 * core MagicBoost), overrides of those units included; unchanged for a factor of 1.
	 */
	public SpellPower boosted(double factor, @Nullable Spell spell) {
		if (!Double.isFinite(factor) || factor <= 0 || factor == 1.0) {
			return this;
		}
		Map<String, Double> scaled = new java.util.HashMap<>(overrides);
		for (Map.Entry<String, Double> entry : overrides.entrySet()) {
			Spell.Param param = spell == null ? null : spell.param(entry.getKey());
			if (param != null && (param.unit() == Spell.Unit.HEARTS || param.unit() == Spell.Unit.SECONDS) && entry.getValue() != null) {
				scaled.put(entry.getKey(), entry.getValue() * factor);
			}
		}
		return new SpellPower(power * factor, radius, count, levels, scaled, cursed);
	}

	/** Nothing is amplified (a scroll, or a grimoire at power 1). */
	public boolean neutral() {
		return power == 1.0 && radius == 1.0 && count == 1.0 && levels == 0 && overrides.isEmpty();
	}

	/** Multiplier of a parameter: its override when there is one, else the general one of its unit (1 for the others). */
	public double multiplier(Spell.Param param) {
		Double own = overrides.get(param.name());
		if (own != null && Double.isFinite(own)) {
			return Math.max(0, own);
		}
		return switch (param.unit()) {
			case HEARTS, SECONDS -> power;
			case BLOCKS -> radius;
			case COUNT -> count;
			default -> 1.0;
		};
	}

	/** The value of a parameter for this cast ({@code value} = the scroll's own value), bounded (see the class). */
	public double apply(Spell.@Nullable Param param, double value) {
		if (param == null || !Double.isFinite(value)) {
			return value;
		}
		double out = switch (param.unit()) {
			case FLAG -> value;
			case LEVEL -> overrides.containsKey(param.name()) ? value * multiplier(param) : value + levels;
			default -> value * multiplier(param);
		};
		if (out == value) {
			// Nothing amplified: exactly the scroll's value (no rounding, no bound).
			return value;
		}
		return bound(param, out);
	}

	/** Bounds of an amplified value (rounded for counts and levels). */
	static double bound(Spell.Param param, double value) {
		double v = Double.isFinite(value) ? value : param.defaultValue();
		return switch (param.unit()) {
			case HEARTS, SECONDS -> Math.max(0, Math.min(param.max() * POWER_CEILING, v));
			case COUNT, LEVEL, FLAG -> Math.max(param.min(), Math.min(param.max(), Math.round(v)));
			default -> Math.max(param.min(), Math.min(param.max() * REACH_CEILING, v));
		};
	}
}
