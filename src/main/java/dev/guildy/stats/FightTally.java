package dev.guildy.stats;

/**
 * Statistics of one player in one fight (pure, no Minecraft class): damage dealt and kills by kind of monster, final blow
 * on the guardian, damage taken, deaths, healing and absorption given to other players, spells cast, seconds spent in the
 * arena. Every amount is in health points; invalid amounts (negative, NaN, infinite) are ignored and a single event never
 * adds more than {@link #MAX_EVENT}.
 */
public final class FightTally {
	/** Largest amount one event may add (a /kill on a huge mob would otherwise drown every other number). */
	public static final double MAX_EVENT = 100_000;

	private final double[] damage = new double[FightKind.values().length];
	private final int[] kills = new int[FightKind.values().length];
	private double taken;
	private int deaths;
	private double healing;
	private double shielding;
	private int spells;
	private int seconds;
	private boolean finalBlow;

	static double clean(double amount) {
		return amount > 0 && Double.isFinite(amount) ? Math.min(amount, MAX_EVENT) : 0;
	}

	public void addDamage(FightKind kind, double amount) {
		damage[kind.ordinal()] += clean(amount);
	}

	public void addKill(FightKind kind) {
		kills[kind.ordinal()]++;
	}

	public void addTaken(double amount) {
		taken += clean(amount);
	}

	public void addDeath() {
		deaths++;
	}

	public void addHealing(double amount) {
		healing += clean(amount);
	}

	public void addShield(double amount) {
		shielding += clean(amount);
	}

	public void addSpell() {
		spells++;
	}

	public void addSecond() {
		seconds++;
	}

	public void setFinalBlow() {
		finalBlow = true;
	}

	public double damage(FightKind kind) {
		return damage[kind.ordinal()];
	}

	public int kills(FightKind kind) {
		return kills[kind.ordinal()];
	}

	/** Damage dealt to every kind of monster of the fight. */
	public double damageDealt() {
		double total = 0;
		for (double d : damage) {
			total += d;
		}
		return total;
	}

	/** Monsters of the fight killed (the guardian included). */
	public int kills() {
		int total = 0;
		for (int k : kills) {
			total += k;
		}
		return total;
	}

	public double taken() {
		return taken;
	}

	public int deaths() {
		return deaths;
	}

	public double healing() {
		return healing;
	}

	public double shielding() {
		return shielding;
	}

	public int spells() {
		return spells;
	}

	public int seconds() {
		return seconds;
	}

	public boolean finalBlow() {
		return finalBlow;
	}

	/** Took part beyond being there: at least {@code min} points dealt, healed, shielded or taken, a kill or the final blow. */
	public boolean qualifies(double min) {
		if (finalBlow || kills() > 0) {
			return true;
		}
		double threshold = Math.max(0, min);
		double best = Math.max(Math.max(damageDealt(), healing), Math.max(shielding, taken));
		return threshold <= 0 ? best > 0 || seconds > 0 || spells > 0 : best >= threshold;
	}

	public FightTally copy() {
		FightTally copy = new FightTally();
		System.arraycopy(damage, 0, copy.damage, 0, damage.length);
		System.arraycopy(kills, 0, copy.kills, 0, kills.length);
		copy.taken = taken;
		copy.deaths = deaths;
		copy.healing = healing;
		copy.shielding = shielding;
		copy.spells = spells;
		copy.seconds = seconds;
		copy.finalBlow = finalBlow;
		return copy;
	}
}
