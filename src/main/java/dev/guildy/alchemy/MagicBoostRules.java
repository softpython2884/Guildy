package dev.guildy.alchemy;

import java.util.Collection;

/**
 * Math of the magic elixirs (pure, unit tested). Each elixir drunk gives one boost per elixir id: drinking the same elixir
 * again refreshes its time (never adds up), different elixirs add their bonuses, each bonus bounded by alchemy.json5
 * ({@code magic.max*}).
 */
public final class MagicBoostRules {
	/** Shortest channel left by a boost (ticks): the incantation stays visible. */
	public static final int MIN_CHANNEL_TICKS = 4;

	/**
	 * Bonuses of one elixir, as fractions: {@code power} 0.25 = spells +25 % (damage, heals, durations), {@code cooldown}
	 * 0.3 = cooldowns −30 %, {@code channel} 0.3 = incantations −30 %, {@code food} 0.5 = grimoire food cost −50 %.
	 */
	public record Boost(double power, double cooldown, double channel, double food) {
		public static final Boost NONE = new Boost(0, 0, 0, 0);

		public Boost {
			power = fraction(power, 10);
			cooldown = fraction(cooldown, 1);
			channel = fraction(channel, 1);
			food = fraction(food, 1);
		}

		public boolean any() {
			return power > 0 || cooldown > 0 || channel > 0 || food > 0;
		}
	}

	/** Bounds of the summed bonuses. */
	public record Caps(double power, double cooldown, double channel, double food) {
		public static final Caps DEFAULT = new Caps(1.0, 0.75, 0.75, 1.0);

		public Caps {
			power = fraction(power, 10);
			cooldown = fraction(cooldown, 1);
			channel = fraction(channel, 1);
			food = fraction(food, 1);
		}
	}

	private MagicBoostRules() {
	}

	private static double fraction(double value, double max) {
		return Double.isFinite(value) ? Math.max(0, Math.min(max, value)) : 0;
	}

	/** Spell power multiplier: 1 + the bonuses, up to 1 + {@code caps.power}. */
	public static double powerMultiplier(Collection<Boost> boosts, Caps caps) {
		double sum = 0;
		for (Boost boost : boosts) {
			sum += boost.power();
		}
		return 1.0 + Math.min(caps.power(), sum);
	}

	/** Cooldown multiplier: 1 − the reductions, down to 1 − {@code caps.cooldown}. */
	public static double cooldownMultiplier(Collection<Boost> boosts, Caps caps) {
		double sum = 0;
		for (Boost boost : boosts) {
			sum += boost.cooldown();
		}
		return 1.0 - Math.min(caps.cooldown(), sum);
	}

	/** Channel time multiplier: 1 − the reductions, down to 1 − {@code caps.channel}. */
	public static double channelMultiplier(Collection<Boost> boosts, Caps caps) {
		double sum = 0;
		for (Boost boost : boosts) {
			sum += boost.channel();
		}
		return 1.0 - Math.min(caps.channel(), sum);
	}

	/** Grimoire food cost multiplier: 1 − the reductions, down to 1 − {@code caps.food} (0 = free). */
	public static double foodMultiplier(Collection<Boost> boosts, Caps caps) {
		double sum = 0;
		for (Boost boost : boosts) {
			sum += boost.food();
		}
		return 1.0 - Math.min(caps.food(), sum);
	}

	/** Food points of a grimoire cast after a reduction: rounded up (−50 % of 3 = 2), 0 only when fully free. */
	public static int foodCost(int cost, double multiplier) {
		if (cost <= 0) {
			return 0;
		}
		double m = Double.isFinite(multiplier) ? Math.max(0, Math.min(1, multiplier)) : 1;
		return (int) Math.max(0, Math.ceil(cost * m - 1.0E-9));
	}

	/** Channel ticks after a reduction, never below {@link #MIN_CHANNEL_TICKS} (nor above the original length). */
	public static int channelTicks(int ticks, double multiplier) {
		if (ticks <= MIN_CHANNEL_TICKS) {
			return ticks;
		}
		double m = Double.isFinite(multiplier) ? Math.max(0, Math.min(1, multiplier)) : 1;
		return (int) Math.max(MIN_CHANNEL_TICKS, Math.min(ticks, Math.round(ticks * m)));
	}

	/** Cooldown ticks of {@code seconds} after a reduction (at least one tick). */
	public static int cooldownTicks(double seconds, double multiplier) {
		double m = Double.isFinite(multiplier) ? Math.max(0, Math.min(1, multiplier)) : 1;
		double s = Double.isFinite(seconds) ? Math.max(0, seconds) : 0;
		return (int) Math.max(1, Math.round(s * 20 * m));
	}

	/** Time left after drinking an elixir again: the longer of what is left and the new duration (ticks). */
	public static int refresh(int remaining, int added) {
		return Math.max(Math.max(0, remaining), Math.max(0, added));
	}

	/** "4:05" for 245 seconds of ticks (rounded up to the second). */
	public static String clock(int ticks) {
		int seconds = (int) Math.ceil(Math.max(0, ticks) / 20.0);
		return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
	}

	/** A bonus in percent for the texts (0.25 -> 25). */
	public static int percent(double fraction) {
		return (int) Math.round(fraction * 100);
	}
}
