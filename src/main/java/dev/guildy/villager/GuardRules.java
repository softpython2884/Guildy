package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

/**
 * Pure guard rules (no Minecraft classes, unit tested): role balancing, equipment comparison, archer positioning,
 * retreat thresholds and healing rates.
 */
final class GuardRules {
	/** A fresher copy of an equal piece is only worth the trip when it has at least this much more durability (0..1). */
	static final double DURABILITY_STEP = 0.25;
	private static final double EPSILON = 1e-6;

	private GuardRules() {
	}

	// ------------------------------------------------------------------ roles

	/**
	 * Role of a guard whose role is "Auto": the one that brings the share of archers among the guild's guards closest
	 * to {@code archerShare} once this guard is counted. Ties go to the sword (the first guard is a swordsman).
	 *
	 * @param swords guards of the guild already fighting with a sword (this one excluded)
	 * @param bows   guards of the guild already fighting with a bow (this one excluded)
	 */
	static GuardRole pickRole(int swords, int bows, double archerShare) {
		int s = Math.max(0, swords);
		int b = Math.max(0, bows);
		double share = Double.isNaN(archerShare) ? 0.5 : Math.max(0.0, Math.min(1.0, archerShare));
		double total = s + b + 1.0;
		double ifBow = Math.abs((b + 1) / total - share);
		double ifSword = Math.abs(b / total - share);
		return ifBow < ifSword - EPSILON ? GuardRole.BOW : GuardRole.SWORD;
	}

	/** Menu cycle of the role setting: Auto → Épéiste → Archer → Auto. */
	static Setting nextSetting(boolean autoRole, @Nullable GuardRole role) {
		if (autoRole) {
			return new Setting(false, GuardRole.SWORD);
		}
		if (role != GuardRole.BOW) {
			return new Setting(false, GuardRole.BOW);
		}
		return new Setting(true, null);
	}

	/** A role setting: "Auto" ({@code forced} null) or a role forced from the menu. */
	record Setting(boolean auto, @Nullable GuardRole forced) {
	}

	// ------------------------------------------------------------------ equipment

	/**
	 * Protection of an armor piece in its slot: armor points, toughness, remaining durability (0..1, 1 for unbreakable
	 * pieces). {@link #NONE} = empty slot.
	 */
	record Piece(double armor, double toughness, double durability) {
		static final Piece NONE = new Piece(0, 0, 0);

		/** Worth wearing at all (an elytra or a carved pumpkin protects nothing). */
		boolean useful() {
			return armor > EPSILON || toughness > EPSILON;
		}
	}

	/** Orders pieces by armor points, then toughness, then remaining durability (best = greatest). */
	static int compare(Piece a, Piece b) {
		int c = compare(a.armor(), b.armor());
		if (c != 0) {
			return c;
		}
		c = compare(a.toughness(), b.toughness());
		if (c != 0) {
			return c;
		}
		return compare(a.durability(), b.durability());
	}

	/**
	 * True when {@code candidate} should replace {@code current}: it protects something and has more armor, or the same
	 * armor and more toughness, or is the same piece much less worn ({@link #DURABILITY_STEP}).
	 */
	static boolean better(Piece candidate, Piece current) {
		if (!candidate.useful()) {
			return false;
		}
		int c = compare(candidate.armor(), current.armor());
		if (c != 0) {
			return c > 0;
		}
		c = compare(candidate.toughness(), current.toughness());
		if (c != 0) {
			return c > 0;
		}
		return candidate.durability() > current.durability() + DURABILITY_STEP;
	}

	/** Weapon score of a sword: attack damage + the bonus of Sharpness (0.5 per level + 0.5). */
	static double swordScore(double attackDamage, int sharpness) {
		return attackDamage + (sharpness > 0 ? 0.5 * sharpness + 0.5 : 0.0);
	}

	/** Weapon score of a bow: 1 for a plain bow, + Power (0.5 per level + 0.5), Flame and Punch. */
	static double bowScore(int power, int flame, int punch) {
		return 1.0 + (power > 0 ? 0.5 * power + 0.5 : 0.0) + (flame > 0 ? 0.5 : 0.0) + 0.25 * Math.max(0, punch);
	}

	static boolean betterWeapon(double candidate, double current) {
		return candidate > current + EPSILON;
	}

	/** Extra melee damage of a sword compared to the guard's default iron sword. */
	static double swordBonus(double attackDamage, double baseline) {
		return Math.max(0.0, attackDamage - baseline);
	}

	// ------------------------------------------------------------------ combat

	enum ArcherMove {
		/** Out of range or no line of sight: walk toward the target. */
		APPROACH,
		/** The target is too close: step back while shooting. */
		BACK_OFF,
		/** In range: stand still and shoot. */
		HOLD
	}

	static ArcherMove archerMove(double distance, boolean lineOfSight, double minDistance, double range) {
		if (!lineOfSight || distance > range) {
			return ArcherMove.APPROACH;
		}
		if (distance < minDistance) {
			return ArcherMove.BACK_OFF;
		}
		return ArcherMove.HOLD;
	}

	/** Base damage of an arrow so that it deals {@code wanted} at {@code speed} (vanilla: damage = speed × base). */
	static double arrowBaseDamage(double wanted, double speed) {
		return Math.max(0.0, wanted) / Math.max(0.1, speed);
	}

	// ------------------------------------------------------------------ retreat & healing

	/** Below {@code ratio} of its health the guard stops fighting and goes to heal (ratio 0 = never). */
	static boolean mustRetreat(double health, double maxHealth, double ratio) {
		return maxHealth > 0 && ratio > 0 && health < maxHealth * ratio - EPSILON;
	}

	/** Healed enough to go back to its post. */
	static boolean canResume(double health, double maxHealth, double ratio) {
		return maxHealth <= 0 || health >= Math.min(maxHealth, maxHealth * ratio) - EPSILON;
	}

	/**
	 * Health regained this second: at its bed while retreating, elsewhere while retreating, or the slow passive
	 * regeneration once out of combat (0 otherwise).
	 */
	static double healPerSecond(boolean retreating, boolean atBed, boolean outOfCombat, double heal, double bedHeal, double passive) {
		if (retreating) {
			return Math.max(0.0, atBed ? Math.max(bedHeal, heal) : heal);
		}
		return outOfCombat ? Math.max(0.0, passive) : 0.0;
	}

	/** Health shown to players ("12/20 ❤"): rounded up so that a living guard never shows 0. */
	static int displayHealth(double health) {
		return health <= 0 ? 0 : (int) Math.ceil(health - EPSILON);
	}

	private static int compare(double a, double b) {
		if (Math.abs(a - b) <= EPSILON) {
			return 0;
		}
		return a < b ? -1 : 1;
	}
}
