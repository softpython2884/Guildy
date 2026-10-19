package dev.guildy.core;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Shared ownership of a survival player's vanilla flight permission ({@code abilities.mayfly}) between the Guildy features
 * that grant it (pure rules, tested; {@link FlightGrants} applies them to the players):
 * <ul>
 *   <li>a feature only takes the grant when mayfly is off, or already given by another Guildy feature: Guildy never takes
 *       over a flight it did not give (creative, operators, other mods), so it never takes one back either;</li>
 *   <li>owners are ranked by declaration order: while a higher owner holds the grant, a lower one steps aside (it neither
 *       grants, nor turns a flight start into a boost, nor revokes);</li>
 *   <li>mayfly is taken back only when the last owner lets go, and flying stops when the last owner allowing real flight
 *       lets go.</li>
 * </ul>
 */
public final class FlightOwnership {
	/** The Guildy features granting mayfly, highest rank first. */
	public enum Owner {
		/** /guild fly: real flight inside the guild's own territory. */
		GUILD_FLY(true),
		/** Bottes de Zéphyr: mayfly on the ground only, a flight start becomes an upward boost. */
		DOUBLE_JUMP(false);

		/** Entity tag marking the grant (saved with the player): a leftover after a crash is taken back at login. */
		public final String tag;
		/** The owner lets the player really fly (for the double jump, mayfly is only the trigger of the boost). */
		public final boolean realFlight;

		Owner(boolean realFlight) {
			this.tag = "guildy_" + name().toLowerCase(Locale.ROOT);
			this.realFlight = realFlight;
		}
	}

	private FlightOwnership() {
	}

	/** An owner may take the grant when mayfly is off or already held by a Guildy owner (never over a foreign flight). */
	public static boolean mayGrant(boolean mayfly, Set<Owner> holders) {
		return !mayfly || !holders.isEmpty();
	}

	/** Highest-ranked holder, or null when nobody holds the grant. */
	public static @Nullable Owner top(Set<Owner> holders) {
		for (Owner owner : Owner.values()) {
			if (holders.contains(owner)) {
				return owner;
			}
		}
		return null;
	}

	/** A higher-ranked owner holds the grant: {@code owner} steps aside. */
	public static boolean outranked(Owner owner, Set<Owner> holders) {
		Owner top = top(holders);
		return top != null && top.ordinal() < owner.ordinal();
	}

	/** The double jump turns a flight start into a boost only while it is the top owner of the grant. */
	public static boolean flightIsBoost(Set<Owner> holders) {
		return top(holders) == Owner.DOUBLE_JUMP;
	}

	/** After an owner let go: mayfly is taken back only when nobody holds the grant any more. */
	public static boolean revokeMayfly(Set<Owner> remaining) {
		return remaining.isEmpty();
	}

	/** After {@code released} let go: flying stops when no remaining owner allows real flight. */
	public static boolean stopFlying(Owner released, Set<Owner> remaining) {
		if (remaining.isEmpty()) {
			return true;
		}
		if (!released.realFlight) {
			return false;
		}
		for (Owner owner : remaining) {
			if (owner.realFlight) {
				return false;
			}
		}
		return true;
	}
}
