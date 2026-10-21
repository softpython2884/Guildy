package dev.guildy.scroll;

import org.jspecify.annotations.Nullable;

/**
 * Ralliement (pure Java, unit-tested): who is called, and whether a called member may still join. Only the members of the
 * caster's guild who are online, far enough from the rally point to need it, within the spell's range in the same dimension
 * (or anywhere when {@code crossDimension} is on) are called. Nobody moves without clicking [Rejoindre]: a click joins only
 * while the call is open (caster still online in the rally's dimension), before it expires, for a member who was called,
 * is still in the guild, has not joined yet, in a call that is not full, from an allowed dimension, alive and awake.
 */
public final class RallyRules {
	/** Members closer than this to the rally point (blocks) are not called: they are already there. */
	public static final double NEAR = 8.0;

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

	/**
	 * A guild member receives the call: not the caster, online and alive, in the same dimension within {@code range} blocks
	 * but farther than {@link #NEAR}, or in another dimension when the call crosses dimensions.
	 */
	public static boolean called(boolean caster, boolean member, boolean available, boolean sameDimension, double distance, double range,
			boolean crossDimension) {
		if (caster || !member || !available) {
			return false;
		}
		if (!sameDimension) {
			return crossDimension;
		}
		return distance > NEAR && distance <= range;
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
