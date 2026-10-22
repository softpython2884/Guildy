package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.EscalationSettings;

/**
 * Pure rules (no Minecraft classes, unit-tested) of the dread form of the guardians ("Forme redoutable", from the threat
 * level {@code escalation.dreadThreshold}): its multipliers, its effective health beyond the vanilla cap of the
 * {@code max_health} attribute (1024: every hit taken is divided by the {@link #vitality}), the extra damage its mobs deal to
 * the players after their armour (never pushing a hit beyond a share of the victim's health), its rewards, the choice of
 * its next ability, and the geometry of its telegraphed attacks (lines, cones, rings with gaps, homing missiles).
 */
public final class DreadRules {
	/** Vanilla cap of the {@code max_health} attribute. */
	public static final double VANILLA_MAX_HEALTH = 1024.0;
	/** Shortest telegraph of a dread attack (ticks): always a fair dodge window. */
	public static final int MIN_TELEGRAPH_TICKS = 15;

	/** Statistics of a dread guardian at a threat level ({@link #NONE} below the threshold). */
	public record Stats(boolean active, int steps, double health, double damage, double cooldown, double escortHealth, double maxHitRatio,
			double gold, double xp, int extraLootRolls, double extraScrollChance, double extraUniqueChance) {
		public static final Stats NONE = new Stats(false, 0, 1.0, 1.0, 1.0, 1.0, 0.0, 1.0, 1.0, 0, 0.0, 0.0);
	}

	private DreadRules() {
	}

	// ================================================================== tier

	/** The dread form applies at {@code level}: escalation and dread enabled, a threshold set and reached. */
	public static boolean active(boolean escalationEnabled, boolean dreadEnabled, int threshold, int level) {
		return escalationEnabled && dreadEnabled && threshold > 0 && level >= threshold;
	}

	/** Levels above the threshold (0 at the threshold itself). */
	public static int steps(int level, int threshold) {
		return Math.max(0, level - Math.max(1, threshold));
	}

	/** {@code base + perLevel * steps}, never above {@code max} nor under 1. */
	public static double multiplier(double base, double perLevel, double max, int steps) {
		double value = Math.max(1.0, base) + Math.max(0, perLevel) * Math.max(0, steps);
		return Math.max(1.0, Math.min(Math.max(1.0, max), value));
	}

	/** Cooldown factor: {@code factor - perLevel * steps}, never under {@code floor}. */
	public static double cooldown(double factor, double perLevel, double floor, int steps) {
		double f = Math.max(0.05, Math.min(1.0, factor));
		double low = Math.max(0.05, Math.min(f, floor));
		return Math.max(low, f - Math.max(0, perLevel) * Math.max(0, steps));
	}

	/** Every statistic of a guardian at {@code level} (NONE when the dread form does not apply). */
	public static Stats stats(EscalationSettings e, int level) {
		if (e == null || e.dread == null || !active(e.enabled, e.dread.enabled, e.dreadThreshold, level)) {
			return Stats.NONE;
		}
		DreadSettings d = e.dread;
		int steps = steps(level, e.dreadThreshold);
		return new Stats(true, steps, multiplier(d.healthMultiplier, d.healthPerLevel, d.maxHealthMultiplier, steps),
				multiplier(d.damageMultiplier, d.damagePerLevel, d.maxDamageMultiplier, steps), cooldown(d.cooldownFactor, d.cooldownPerLevel,
				d.minCooldownFactor, steps), Math.max(1, d.escortHealthMultiplier), Math.max(0, d.maxHitHealthRatio),
				multiplier(d.goldMultiplier, d.goldPerLevel, 20, steps), multiplier(d.xpMultiplier, d.xpPerLevel, 20, steps), Math.max(0, d.extraLootRolls),
				clamp01(d.extraScrollChance), clamp01(d.extraUniqueChance));
	}

	// ================================================================== health

	/**
	 * Effective health of a dread guardian: {@code uncapped} (configured health times every multiplier) limited to
	 * {@code capPerPlayer} per player counted (0 = no cap).
	 */
	public static double effectiveHealth(double uncapped, int players, double capPerPlayer) {
		return clampHealth(uncapped, players, 0, capPerPlayer);
	}

	/** {@code value} between {@code minPerPlayer} and {@code capPerPlayer} (0 = none) per player counted (at least one). */
	public static double clampHealth(double value, int players, double minPerPlayer, double capPerPlayer) {
		int n = Math.max(1, players);
		double v = Math.max(1, value);
		if (minPerPlayer > 0) {
			v = Math.max(v, minPerPlayer * n);
		}
		if (capPerPlayer > 0) {
			v = Math.min(v, capPerPlayer * n);
		}
		return v;
	}

	/**
	 * How much more health the guardian really has than its {@code max_health} attribute (capped at 1024 by the game): the
	 * damage it takes and the health it regains are divided by this factor (1 = none).
	 */
	public static double vitality(double effective, double realMax) {
		if (!(realMax > 0) || !(effective > realMax)) {
			return 1.0;
		}
		return effective / realMax;
	}

	/** A hit on a guardian of vitality {@code v}: what its health really loses. */
	public static float toGuardian(float damage, double vitality) {
		return vitality > 1.0 ? (float) (damage / vitality) : damage;
	}

	/** Health shown for a real health of {@code real} (the boss bar): times the vitality, rounded up. */
	public static long shown(double real, double vitality) {
		return (long) Math.ceil(Math.max(0, real) * Math.max(1.0, vitality) - 1.0E-6);
	}

	// ================================================================== damage to the players

	/**
	 * Damage a player takes (after armour) from a mob of a dread guardian's fight: times {@code factor}, but the bonus never
	 * takes one hit beyond {@code maxHitRatio} of the player's maximum health (a hit already stronger stays as it is; 0 = no
	 * limit).
	 */
	public static float toPlayer(float damage, double factor, double maxHitRatio, float playerMaxHealth) {
		if (!(damage > 0) || factor <= 1.0) {
			return damage;
		}
		double scaled = damage * factor;
		if (maxHitRatio > 0 && playerMaxHealth > 0) {
			double cap = Math.max(damage, maxHitRatio * playerMaxHealth);
			scaled = Math.min(scaled, cap);
		}
		return (float) scaled;
	}

	// ================================================================== rewards

	/** Rewards of a dread victory: the threat (and curse) rewards times the dread gold and XP, extra rolls on top. */
	public static ThreatRules.Rewards rewards(ThreatRules.Rewards base, Stats s) {
		ThreatRules.Rewards b = base == null ? ThreatRules.Rewards.NONE : base;
		if (s == null || !s.active()) {
			return b;
		}
		return new ThreatRules.Rewards(b.gold() * s.gold(), b.xp() * s.xp(), Math.min(20, b.extraLootRolls() + s.extraLootRolls()),
				clamp01(b.extraUniqueChance() + s.extraUniqueChance()), clamp01(b.extraScrollChance() + s.extraScrollChance()), b.bonusUniqueRolls(),
				b.bonusScrollRolls());
	}

	// ================================================================== scheduling

	/** Ticks of a telegraph lasting {@code seconds} (never under {@link #MIN_TELEGRAPH_TICKS}). */
	public static int telegraphTicks(double seconds) {
		return Math.max(MIN_TELEGRAPH_TICKS, (int) Math.round(Math.max(0, seconds) * 20));
	}

	/** Ticks of a cooldown in seconds times a factor (one second at least). */
	public static long cooldownTicks(double seconds, double factor) {
		return Math.max(20, Math.round(Math.max(0, seconds) * 20 * Math.max(0.05, factor)));
	}

	/**
	 * Index of the next ability to use: the first one ready (its {@code readyAt} reached and its phase unlocked) in rotation
	 * from {@code rotation}, or -1.
	 *
	 * @param phases phase that unlocks each ability (1 = always)
	 * @param phase  current phase of the fight (1 for a fight without phases)
	 */
	public static int pick(long now, long[] readyAt, int[] phases, int phase, int rotation) {
		int n = readyAt.length;
		for (int i = 0; i < n; i++) {
			int index = Math.floorMod(rotation + i, n);
			if (phases[index] <= phase && now >= readyAt[index]) {
				return index;
			}
		}
		return -1;
	}

	// ================================================================== geometry

	/** Horizontal distance from (px, pz) to the segment (ax, az)-(bx, bz). */
	public static double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
		double dx = bx - ax;
		double dz = bz - az;
		double length = dx * dx + dz * dz;
		double t = length < 1.0E-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / length));
		double cx = ax + dx * t - px;
		double cz = az + dz * t - pz;
		return Math.sqrt(cx * cx + cz * cz);
	}

	/** Distance from the point p to the segment a-b, in three dimensions (a beam). */
	public static double distanceToSegment3(double px, double py, double pz, double ax, double ay, double az, double bx, double by, double bz) {
		double dx = bx - ax;
		double dy = by - ay;
		double dz = bz - az;
		double length = dx * dx + dy * dy + dz * dz;
		double t = length < 1.0E-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / length));
		double cx = ax + dx * t - px;
		double cy = ay + dy * t - py;
		double cz = az + dz * t - pz;
		return Math.sqrt(cx * cx + cy * cy + cz * cz);
	}

	/** Position along the segment (0 at a, 1 at b) of the point of (ax, az)-(bx, bz) closest to (px, pz). */
	public static double along(double px, double pz, double ax, double az, double bx, double bz) {
		double dx = bx - ax;
		double dz = bz - az;
		double length = dx * dx + dz * dz;
		return length < 1.0E-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / length));
	}

	/** (dx, dz) from the apex lies in the cone of direction (dirX, dirZ), half angle {@code halfAngle}, length {@code range}. */
	public static boolean inCone(double dx, double dz, double dirX, double dirZ, double halfAngle, double range) {
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist > range) {
			return false;
		}
		if (dist < 1.0) {
			// Right under its jaws.
			return true;
		}
		double dir = Math.sqrt(dirX * dirX + dirZ * dirZ);
		if (dir < 1.0E-9) {
			return true;
		}
		double cos = (dx * dirX + dz * dirZ) / (dist * dir);
		return cos >= Math.cos(halfAngle);
	}

	/** Smallest difference between two angles (radians, 0..PI). */
	public static double angleBetween(double a, double b) {
		double d = Math.abs(Math.IEEEremainder(a - b, Math.PI * 2));
		return Math.min(d, Math.PI * 2 - d);
	}

	/** The angle {@code angle} (radians) falls in one of the gaps (centred angles, each {@code halfWidth} wide on each side). */
	public static boolean inGap(double angle, double[] gaps, double halfWidth) {
		for (double gap : gaps) {
			if (angleBetween(angle, gap) <= halfWidth) {
				return true;
			}
		}
		return false;
	}

	/** {@code count} gap angles evenly spread from {@code offset} (radians). */
	public static double[] gaps(int count, double offset) {
		int n = Math.max(0, count);
		double[] gaps = new double[n];
		for (int i = 0; i < n; i++) {
			gaps[i] = offset + Math.PI * 2 * i / n;
		}
		return gaps;
	}

	/** A ring front of radius {@code front} sweeps a player at {@code distance} this tick (moving by {@code step} per tick). */
	public static boolean swept(double distance, double front, double step) {
		double s = Math.max(0.6, Math.abs(step));
		return distance <= front + 0.6 && distance >= front - s - 0.6;
	}

	/**
	 * Direction of a homing missile after one tick: its direction {@code dir} turned toward {@code toTarget} by at most
	 * {@code maxTurn} radians (both normalized; dodgeable by strafing). @return the new unit direction {x, y, z}
	 */
	public static double[] steer(double[] dir, double[] toTarget, double maxTurn) {
		double[] a = normalize(dir);
		double[] b = normalize(toTarget);
		double cos = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
		double angle = Math.acos(cos);
		if (angle <= Math.max(0, maxTurn) || angle < 1.0E-6) {
			return b;
		}
		double t = Math.max(0, maxTurn) / angle;
		double[] mixed = {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
		double length = Math.sqrt(mixed[0] * mixed[0] + mixed[1] * mixed[1] + mixed[2] * mixed[2]);
		return length < 1.0E-6 ? b : normalize(mixed);
	}

	static double[] normalize(double[] v) {
		double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return length < 1.0E-9 ? new double[] {1, 0, 0} : new double[] {v[0] / length, v[1] / length, v[2] / length};
	}

	/** Strength of a pull toward a well's center at {@code distance} (stronger near it, none beyond the radius). */
	public static double pull(double distance, double radius, double strength) {
		if (radius <= 0 || distance > radius || distance < 0.4) {
			return 0;
		}
		return Math.max(0, strength) * (0.45 + 0.55 * (1 - distance / radius));
	}

	/** Radius of a closing ring at {@code progress} (0..1): from {@code from} down to {@code to}. */
	public static double closing(double from, double to, double progress) {
		double p = Math.max(0, Math.min(1, progress));
		return from + (to - from) * p * p;
	}

	private static double clamp01(double v) {
		return Math.max(0, Math.min(1, v));
	}
}
