package dev.guildy.ruin;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Pure rules (no Minecraft classes, unit-tested) of the room guards of a ruin: how many guards are posted (tier, players
 * present, threat level), where (the {@code guildy:guard} markers, else fallback posts: escort markers, the entrance and a
 * circle around the sleeping guardian), when they appear and leave, how far they may stray from their post, and whether
 * they keep the guardian asleep.
 */
public final class RoomGuardRules {
	/** A block position without Minecraft classes. */
	public record Cell(int x, int y, int z) {
		/** Squared distance between the centres of two cells. */
		public double distanceSq(Cell other) {
			double dx = x - other.x;
			double dy = y - other.y;
			double dz = z - other.z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	/** What a guard does this second, seen from its post. */
	public enum Leash {
		/** Near its post (or chasing an intruder close to it). */
		STAY,
		/** Too far: walks back to its post. */
		RETURN,
		/** Lured far away (or stuck): brought back to its post at once. */
		TELEPORT
	}

	private RoomGuardRules() {
	}

	/**
	 * Number of guards posted in a ruin.
	 *
	 * @param fixed               the ruin's own count ({@code guards.count}: -1 = the count of its tier, 0 = no guards)
	 * @param byTier              guards per tier (index 0 = tier 1; the last value is used for higher tiers)
	 * @param players             survival / adventure players present (at least one: the intruder)
	 * @param perExtraPlayer      guards added for each player beyond the first
	 * @param threat              threat level of the ruin (I = 1)
	 * @param threatLevelsPerGuard one more guard every N threat levels above I (0 = never)
	 * @param max                 cap
	 */
	public static int count(int fixed, @Nullable List<Integer> byTier, int tier, int players, int perExtraPlayer, int threat, int threatLevelsPerGuard,
			int max) {
		int base = fixed >= 0 ? fixed : forTier(byTier, tier);
		if (base <= 0) {
			return 0;
		}
		int count = base + Math.max(0, perExtraPlayer) * Math.max(0, players - 1) + ThreatRules.extraMinions(threat, threatLevelsPerGuard);
		return Math.max(0, Math.min(Math.max(0, max), count));
	}

	/** Guards of a tier from the per-tier list (0 when the list is empty). */
	public static int forTier(@Nullable List<Integer> byTier, int tier) {
		if (byTier == null || byTier.isEmpty()) {
			return 0;
		}
		int index = Math.max(1, Math.min(byTier.size(), tier)) - 1;
		Integer value = byTier.get(index);
		return value == null ? 0 : Math.max(0, value);
	}

	/**
	 * The guards of a dormant ruin appear: guards enabled for it, the ruin dormant with neither a fight nor a ritual
	 * running, not posted yet in this dormant period, and a survival / adventure intruder close enough.
	 */
	public static boolean spawns(boolean enabled, RuinState state, boolean fightOrRitual, boolean alreadyPosted, boolean intruder) {
		return enabled && state == RuinState.DORMANT && !fightOrRitual && !alreadyPosted && intruder;
	}

	/** Nobody around for {@code resetSeconds}: the guards leave, the next approach posts fresh ones. */
	public static boolean resets(int secondsAlone, int resetSeconds) {
		return secondsAlone >= Math.max(1, resetSeconds);
	}

	/** The incantation (and the proximity awakening) waits while guards are alive in the arena. */
	public static boolean blocksRitual(boolean blockRitual, int guardsInArena) {
		return blockRitual && guardsInArena > 0;
	}

	/**
	 * Leash of a guard.
	 *
	 * @param distanceSq      squared distance between the guard and its post
	 * @param radius          leash radius (the guard returns beyond it, is brought back beyond twice that)
	 * @param chasingNearPost the guard is chasing an intruder who is itself close to the post
	 */
	public static Leash leash(double distanceSq, double radius, boolean chasingNearPost) {
		double r = Math.max(1, radius);
		if (distanceSq > 4 * r * r) {
			return Leash.TELEPORT;
		}
		if (distanceSq > r * r) {
			return chasingNearPost ? Leash.STAY : Leash.RETURN;
		}
		return Leash.STAY;
	}

	/**
	 * Posts of a ruin without guard markers (older templates), by priority: the escort markers, three spots just inside
	 * the entrance (towards the guardian), then {@code points} spots on a circle of {@code radius} around the guardian. Only
	 * cells inside the ruin's horizontal box (one block from its walls) are kept; duplicates are removed.
	 */
	public static List<Cell> fallbackPosts(Cell boss, @Nullable Cell entrance, List<Cell> minions, int minX, int minZ, int maxX, int maxZ, double radius,
			int points) {
		List<Cell> posts = new ArrayList<>();
		for (Cell minion : minions) {
			add(posts, minion, minX, minZ, maxX, maxZ);
		}
		if (entrance != null) {
			double dx = boss.x() - entrance.x();
			double dz = boss.z() - entrance.z();
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length > 1.0E-6) {
				double ux = dx / length;
				double uz = dz / length;
				double inward = Math.min(3, length / 2);
				double cx = entrance.x() + ux * inward;
				double cz = entrance.z() + uz * inward;
				add(posts, new Cell((int) Math.round(cx), entrance.y(), (int) Math.round(cz)), minX, minZ, maxX, maxZ);
				// Both sides of the doorway, across the way in.
				add(posts, new Cell((int) Math.round(cx - uz * 2), entrance.y(), (int) Math.round(cz + ux * 2)), minX, minZ, maxX, maxZ);
				add(posts, new Cell((int) Math.round(cx + uz * 2), entrance.y(), (int) Math.round(cz - ux * 2)), minX, minZ, maxX, maxZ);
			}
		}
		int n = Math.max(0, points);
		double r = Math.max(0, Math.min(radius, maxRadius(boss, minX, minZ, maxX, maxZ)));
		if (r >= 2) {
			for (int i = 0; i < n; i++) {
				double angle = Math.PI * 2 * i / n;
				add(posts, new Cell((int) Math.round(boss.x() + Math.cos(angle) * r), boss.y(), (int) Math.round(boss.z() + Math.sin(angle) * r)),
						minX, minZ, maxX, maxZ);
			}
		}
		return posts;
	}

	/** Largest circle around the guardian that stays one block inside the box. */
	static double maxRadius(Cell boss, int minX, int minZ, int maxX, int maxZ) {
		return Math.max(0, Math.min(Math.min(boss.x() - minX, maxX - boss.x()), Math.min(boss.z() - minZ, maxZ - boss.z())) - 1);
	}

	private static void add(List<Cell> posts, Cell cell, int minX, int minZ, int maxX, int maxZ) {
		if (cell.x() > minX && cell.x() < maxX && cell.z() > minZ && cell.z() < maxZ && !posts.contains(cell)) {
			posts.add(cell);
		}
	}

	/**
	 * Post of each guard: every post gets a guard before any gets a second one. The posts are taken in their order
	 * (fallback posts: by priority), or in a random order when {@code shuffle} is given (marker posts).
	 *
	 * @return the post index of each guard (empty without posts)
	 */
	public static List<Integer> assign(int posts, int guards, @Nullable Random shuffle) {
		List<Integer> result = new ArrayList<>();
		if (posts <= 0 || guards <= 0) {
			return result;
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < posts; i++) {
			order.add(i);
		}
		if (shuffle != null) {
			Collections.shuffle(order, shuffle);
		}
		for (int i = 0; i < guards; i++) {
			result.add(order.get(i % posts));
		}
		return result;
	}
}
