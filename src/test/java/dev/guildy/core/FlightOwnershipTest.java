package dev.guildy.core;

import dev.guildy.core.FlightOwnership.Owner;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightOwnershipTest {
	private static final Set<Owner> NONE = EnumSet.noneOf(Owner.class);
	private static final Set<Owner> GUILD = EnumSet.of(Owner.GUILD_FLY);
	private static final Set<Owner> JUMP = EnumSet.of(Owner.DOUBLE_JUMP);
	private static final Set<Owner> BOTH = EnumSet.allOf(Owner.class);

	@Test
	void tagsKeepTheSavedNames() {
		// Players saved by older versions carry the double jump tag: it must still be recognised (and cleaned) at login.
		assertEquals("guildy_double_jump", Owner.DOUBLE_JUMP.tag);
		assertEquals("guildy_guild_fly", Owner.GUILD_FLY.tag);
	}

	@Test
	void neverTakesOverAForeignFlight() {
		assertTrue(FlightOwnership.mayGrant(false, NONE), "mayfly off: any owner may grant");
		assertFalse(FlightOwnership.mayGrant(true, NONE), "mayfly from creative, an operator or another mod: left alone");
		assertTrue(FlightOwnership.mayGrant(true, JUMP), "given by the double jump: guild flight may join");
		assertTrue(FlightOwnership.mayGrant(true, GUILD), "given by guild flight: the double jump may join");
	}

	@Test
	void guildFlightOutranksTheDoubleJump() {
		assertEquals(Owner.GUILD_FLY, FlightOwnership.top(BOTH));
		assertNull(FlightOwnership.top(NONE));
		assertTrue(FlightOwnership.outranked(Owner.DOUBLE_JUMP, BOTH));
		assertTrue(FlightOwnership.outranked(Owner.DOUBLE_JUMP, GUILD), "the double jump steps aside while guild flight holds");
		assertFalse(FlightOwnership.outranked(Owner.GUILD_FLY, BOTH));
		assertFalse(FlightOwnership.outranked(Owner.GUILD_FLY, JUMP));
		assertFalse(FlightOwnership.outranked(Owner.DOUBLE_JUMP, JUMP));
	}

	@Test
	void flightIsABoostOnlyForTheDoubleJumpAlone() {
		assertTrue(FlightOwnership.flightIsBoost(JUMP));
		assertFalse(FlightOwnership.flightIsBoost(BOTH), "guild flight: the player really flies");
		assertFalse(FlightOwnership.flightIsBoost(GUILD));
		assertFalse(FlightOwnership.flightIsBoost(NONE));
	}

	@Test
	void mayflyStaysWhileAnotherOwnerHoldsIt() {
		assertTrue(FlightOwnership.revokeMayfly(NONE), "last owner gone: mayfly taken back");
		assertFalse(FlightOwnership.revokeMayfly(JUMP), "guild flight ends on the ground with the boots: the double jump keeps mayfly");
		assertFalse(FlightOwnership.revokeMayfly(GUILD));
	}

	@Test
	void flyingStopsWithTheLastRealFlightOwner() {
		assertTrue(FlightOwnership.stopFlying(Owner.GUILD_FLY, NONE));
		assertTrue(FlightOwnership.stopFlying(Owner.GUILD_FLY, JUMP), "the double jump does not allow real flight");
		assertTrue(FlightOwnership.stopFlying(Owner.DOUBLE_JUMP, NONE));
		assertFalse(FlightOwnership.stopFlying(Owner.DOUBLE_JUMP, GUILD), "the double jump stepping aside never stops guild flight");
	}
}
