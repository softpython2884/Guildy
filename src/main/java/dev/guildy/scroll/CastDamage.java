package dev.guildy.scroll;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What one cast has done to each target (pure Java, unit-tested): every spell hit goes through {@link #take} before it
 * lands, so that one cast never does too much to one target, however many meteors, waves or bolts it throws.
 *
 * <ul>
 * <li><b>Diminishing returns</b>: each further hit of the same cast on the same target is multiplied by {@code decay} once
 * more ({@code decay^k} after k earlier hits, never below {@code floor}). A continuous spell (blizzard pulses, arcane ray
 * ticks, aegis pulses) weighs only a fraction of a hit per tick ({@link #continuous}: one hit per
 * {@link #CONTINUOUS_SECONDS} seconds of damage), so it is not ruined by its own many small ticks.</li>
 * <li><b>Boss cap</b>: a boss (ruin guardian, cursed forms included, horde boss, vanilla boss) never loses more than
 * {@code bossCap} of its max health (raw spell damage) to one cast; ordinary monsters have no cap.</li>
 * </ul>
 * A new instance per cast (the {@link SpellContext}), so both reset with every cast.
 */
public final class CastDamage {
	/** A continuous spell counts one hit per this many seconds of damage. */
	public static final double CONTINUOUS_SECONDS = 5.0;

	private final double decay;
	private final double floor;
	private final double bossCap;
	private final Map<UUID, Tally> tallies = new HashMap<>();

	/** Hits (weighted) and raw damage already dealt to one target by this cast. */
	private static final class Tally {
		double hits;
		double dealt;
	}

	/**
	 * @param decay   multiplier of each further hit on the same target (1 = no diminishing return)
	 * @param floor   a hit keeps at least this part of its damage
	 * @param bossCap part of a boss's max health one cast may take at most (1 or more = no cap)
	 */
	public CastDamage(double decay, double floor, double bossCap) {
		this.decay = Double.isFinite(decay) ? Math.max(0, Math.min(1, decay)) : 1.0;
		this.floor = Double.isFinite(floor) ? Math.max(0, Math.min(1, floor)) : 0.0;
		this.bossCap = Double.isFinite(bossCap) ? Math.max(0, bossCap) : 1.0;
	}

	/** Weight of one tick of a continuous spell that hurts every {@code intervalTicks} ticks. */
	public static double continuous(int intervalTicks) {
		return Math.max(0, intervalTicks) / (20.0 * CONTINUOUS_SECONDS);
	}

	/** Multiplier of a hit after {@code priorHits} (weighted) earlier hits of the same cast: {@code decay^prior}, at least {@code floor}. */
	public static double factor(double decay, double floor, double priorHits) {
		if (!(priorHits > 0) || decay >= 1) {
			return 1.0;
		}
		return Math.max(Math.max(0, floor), Math.pow(Math.max(0, decay), priorHits));
	}

	/** Raw damage a boss of {@code maxHealth} may still take from a cast that already dealt {@code dealt} (cap 1+ = unlimited). */
	public static double room(double bossCap, double maxHealth, double dealt) {
		if (bossCap >= 1 || !(maxHealth > 0)) {
			return Double.POSITIVE_INFINITY;
		}
		return Math.max(0, bossCap * maxHealth - Math.max(0, dealt));
	}

	/**
	 * The damage this hit really deals (decayed, within the boss cap) and records it: {@code amount} of a hit of
	 * {@code weight} (1 = a meteor, a wave, a bolt; less for a tick of a continuous spell) on {@code target}.
	 */
	public double take(UUID target, boolean boss, double maxHealth, double amount, double weight) {
		double dealt = amount(target, boss, maxHealth, amount);
		record(target, weight, dealt);
		return dealt;
	}

	/** The damage a hit of {@code amount} would really deal now (decayed, within the boss cap), nothing recorded. */
	public double amount(UUID target, boolean boss, double maxHealth, double amount) {
		if (!(amount > 0)) {
			return 0;
		}
		Tally tally = tallies.get(target);
		double dealt = amount * factor(decay, floor, tally == null ? 0 : tally.hits);
		return boss ? Math.min(dealt, room(bossCap, maxHealth, tally == null ? 0 : tally.dealt)) : dealt;
	}

	/** Records a hit that landed: its weight and the raw damage it dealt (a hit the target ignored is not recorded). */
	public void record(UUID target, double weight, double dealt) {
		Tally tally = tallies.computeIfAbsent(target, k -> new Tally());
		tally.hits += Math.max(0, weight);
		tally.dealt += Math.max(0, dealt);
	}

	/**
	 * What {@code more} further hits of {@code amount} (weight 1) would deal to {@code target}, without recording anything
	 * (meteors still falling on it).
	 */
	public double expected(UUID target, boolean boss, double maxHealth, double amount, int more) {
		Tally tally = tallies.get(target);
		double hits = tally == null ? 0 : tally.hits;
		double dealt = tally == null ? 0 : tally.dealt;
		double total = 0;
		for (int i = 0; i < more && amount > 0; i++) {
			double hit = amount * factor(decay, floor, hits + i);
			if (boss) {
				hit = Math.min(hit, room(bossCap, maxHealth, dealt + total));
			}
			total += hit;
		}
		return total;
	}

	/** Raw damage this cast may still deal to {@code target} (infinite for an ordinary monster). */
	public double room(UUID target, boolean boss, double maxHealth) {
		if (!boss) {
			return Double.POSITIVE_INFINITY;
		}
		Tally tally = tallies.get(target);
		return room(bossCap, maxHealth, tally == null ? 0 : tally.dealt);
	}

	/** Raw damage this cast dealt to {@code target} so far. */
	public double dealt(UUID target) {
		Tally tally = tallies.get(target);
		return tally == null ? 0 : tally.dealt;
	}

	/** Hits (weighted) of this cast on {@code target} so far. */
	public double hits(UUID target) {
		Tally tally = tallies.get(target);
		return tally == null ? 0 : tally.hits;
	}

	public double decay() {
		return decay;
	}

	public double floor() {
		return floor;
	}

	public double bossCap() {
		return bossCap;
	}
}
