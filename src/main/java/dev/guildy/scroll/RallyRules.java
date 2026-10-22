package dev.guildy.scroll;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ralliement (pure Java, unit-tested): who is called, why the others are not, and whether a called member may still join.
 * Every online member of the caster's guild is called, wherever they are, unless they are already next to the rally point
 * ({@link #NEAR}), dead, asleep or spectating; an admin may limit the call to a range ({@code range} &gt; 0, 0 = unlimited) and
 * to the caster's dimension ({@code crossDimension} 0). Nobody moves without clicking [Rejoindre]: a click joins only while
 * the call is open (caster still online in the rally's dimension), before it expires, for a member who was called, is still
 * in the guild, has not joined yet, in a call that is not full, from an allowed dimension, alive and awake.
 */
public final class RallyRules {
	/** Members closer than this to the rally point (blocks) are not called: they are already there. */
	public static final double NEAR = 8.0;

	/** Why a member of the caster's guild is called or not (in the order the checks are made). */
	public enum Reason {
		CALLED,
		/** The caster themself. */
		CASTER,
		OFFLINE,
		/** Dead, asleep or spectating. */
		UNAVAILABLE,
		/** In another dimension and the call does not cross dimensions (admin setting). */
		OTHER_DIMENSION,
		/** Already within {@link #NEAR} blocks of the rally point. */
		NEAR,
		/** Farther than the range set by the admin. */
		FAR
	}

	/** A refusal of the cast: lang key (scroll kept) and its arguments. */
	public record Refusal(String key, List<Object> args) {
	}

	/** Answer to a click on [Rejoindre]. */
	public enum Verdict {
		OK,
		/** The call is not addressed to this player (another guild, called later, unknown call). */
		NOT_INVITED,
		/** The caster logged out or left the rally's dimension: the call is over. */
		CLOSED,
		EXPIRED,
		ALREADY_JOINED,
		FULL,
		/** The member is in another dimension and the call does not cross dimensions. */
		WRONG_DIMENSION,
		/** Dead, asleep or spectating. */
		UNAVAILABLE
	}

	/** What the click knows about the call and the member. */
	public record Click(long now, long expiresAt, boolean open, boolean invited, boolean stillMember, boolean joined, int joinedCount, int max,
			boolean sameDimension, boolean crossDimension, boolean available) {
	}

	private RallyRules() {
	}

	/** A range of 0 (or less) calls the members at any distance. */
	public static boolean unlimited(double range) {
		return !(range > 0);
	}

	/**
	 * Why a member of the caster's guild is (not) called: the caster never; then offline, dead / asleep / spectating, in
	 * another dimension when the call stays in the caster's one, already within {@link #NEAR} blocks, beyond the admin's
	 * {@code range} (0 = unlimited).
	 */
	public static Reason reason(boolean caster, boolean online, boolean available, boolean sameDimension, double distance, double range,
			boolean crossDimension) {
		if (caster) {
			return Reason.CASTER;
		}
		if (!online) {
			return Reason.OFFLINE;
		}
		if (!available) {
			return Reason.UNAVAILABLE;
		}
		if (!sameDimension) {
			return crossDimension ? Reason.CALLED : Reason.OTHER_DIMENSION;
		}
		if (distance <= NEAR) {
			return Reason.NEAR;
		}
		return unlimited(range) || distance <= range ? Reason.CALLED : Reason.FAR;
	}

	/**
	 * A guild member receives the call (see {@link #reason}): not the caster, online, alive and awake, not already next to the
	 * rally point, within the range (0 = unlimited) and the dimensions allowed.
	 */
	public static boolean called(boolean caster, boolean member, boolean available, boolean sameDimension, double distance, double range,
			boolean crossDimension) {
		return member && reason(caster, true, available, sameDimension, distance, range, crossDimension) == Reason.CALLED;
	}

	/** Members of the guild counted by reason. */
	public static Map<Reason, Integer> census(List<Reason> reasons) {
		Map<Reason, Integer> counts = new EnumMap<>(Reason.class);
		for (Reason reason : Reason.values()) {
			counts.put(reason, 0);
		}
		for (Reason reason : reasons) {
			if (reason != null) {
				counts.merge(reason, 1, Integer::sum);
			}
		}
		return counts;
	}

	/**
	 * Why nobody can be called, or null when someone is: nobody else online; else the one reason shared by every online
	 * member (already near, asleep / dead / spectating, too far, another dimension), with their number; else every reason
	 * with its number ({@code scroll.fail.rally.mixed} + one part per reason, see {@link #parts}).
	 */
	public static @Nullable Refusal refusal(Map<Reason, Integer> counts, double range) {
		if (count(counts, Reason.CALLED) > 0) {
			return null;
		}
		List<Refusal> parts = parts(counts, range);
		if (parts.isEmpty()) {
			return new Refusal("scroll.fail.rally.alone", List.of());
		}
		if (parts.size() == 1) {
			Refusal only = parts.getFirst();
			return new Refusal("scroll.fail.rally." + only.key().substring(only.key().lastIndexOf('.') + 1), only.args());
		}
		return new Refusal("scroll.fail.rally.mixed", List.of());
	}

	/**
	 * One part per reason an online member was not called ({@code scroll.rally.reason.<near|unavailable|far|dimension>}),
	 * with the number of members and the distance concerned.
	 */
	public static List<Refusal> parts(Map<Reason, Integer> counts, double range) {
		List<Refusal> parts = new ArrayList<>();
		int near = count(counts, Reason.NEAR);
		if (near > 0) {
			parts.add(new Refusal("scroll.rally.reason.near", List.of(near, (int) NEAR)));
		}
		int unavailable = count(counts, Reason.UNAVAILABLE);
		if (unavailable > 0) {
			parts.add(new Refusal("scroll.rally.reason.unavailable", List.of(unavailable)));
		}
		int far = count(counts, Reason.FAR);
		if (far > 0) {
			parts.add(new Refusal("scroll.rally.reason.far", List.of(far, (int) Math.round(range))));
		}
		int dimension = count(counts, Reason.OTHER_DIMENSION);
		if (dimension > 0) {
			parts.add(new Refusal("scroll.rally.reason.dimension", List.of(dimension)));
		}
		return parts;
	}

	private static int count(Map<Reason, Integer> counts, Reason reason) {
		Integer n = counts == null ? null : counts.get(reason);
		return n == null ? 0 : Math.max(0, n);
	}

	/** Whether a click on [Rejoindre] teleports the member, else why not (first reason in the order of the enum). */
	public static Verdict verdict(Click click) {
		if (!click.invited() || !click.stillMember()) {
			return Verdict.NOT_INVITED;
		}
		if (!click.open()) {
			return Verdict.CLOSED;
		}
		if (click.now() > click.expiresAt()) {
			return Verdict.EXPIRED;
		}
		if (click.joined()) {
			return Verdict.ALREADY_JOINED;
		}
		if (click.joinedCount() >= click.max()) {
			return Verdict.FULL;
		}
		if (!click.sameDimension() && !click.crossDimension()) {
			return Verdict.WRONG_DIMENSION;
		}
		return click.available() ? Verdict.OK : Verdict.UNAVAILABLE;
	}

	/** Whole seconds left before the call expires (0 when over). */
	public static int secondsLeft(long now, long expiresAt) {
		return (int) Math.max(0, (expiresAt - now + 19) / 20);
	}

	/** Lang key of a refused click (null for OK). */
	public static @Nullable String failureKey(Verdict verdict) {
		return switch (verdict) {
			case OK -> null;
			case NOT_INVITED -> "scroll.rally.fail.not_invited";
			case CLOSED -> "scroll.rally.fail.closed";
			case EXPIRED -> "scroll.rally.fail.expired";
			case ALREADY_JOINED -> "scroll.rally.fail.already";
			case FULL -> "scroll.rally.fail.full";
			case WRONG_DIMENSION -> "scroll.rally.fail.dimension";
			case UNAVAILABLE -> "scroll.rally.fail.unavailable";
		};
	}
}
