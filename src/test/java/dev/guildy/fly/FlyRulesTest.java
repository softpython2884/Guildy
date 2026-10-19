package dev.guildy.fly;

import dev.guildy.config.GuildyConfig;
import dev.guildy.config.Json5;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.GuildRole;
import dev.guildy.fly.FlyRules.Reason;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlyRulesTest {
	private static final BooleanSupplier ALLOWED = () -> true;

	/** Everything allowed by default; each test changes one fact. */
	private static Reason check(boolean enabled, boolean survival, GuildRole role, GuildRole minRole, int level, int minLevel,
			BooleanSupplier permission, boolean horde, boolean own, boolean combat) {
		return FlyRules.check(enabled, survival, role, minRole, level, minLevel, permission, horde, own, combat);
	}

	private static Reason ok() {
		return check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 1, 1, ALLOWED, false, true, false);
	}

	@Test
	void eligibleMemberMayFly() {
		assertEquals(Reason.NONE, ok());
	}

	@Test
	void eachReasonIsReported() {
		assertEquals(Reason.DISABLED, check(false, true, GuildRole.MEMBER, GuildRole.MEMBER, 1, 1, ALLOWED, false, true, false));
		assertEquals(Reason.GAME_MODE, check(true, false, GuildRole.MEMBER, GuildRole.MEMBER, 1, 1, ALLOWED, false, true, false));
		assertEquals(Reason.NO_GUILD, check(true, true, null, GuildRole.MEMBER, 0, 1, ALLOWED, false, false, false));
		assertEquals(Reason.ROLE, check(true, true, GuildRole.MEMBER, GuildRole.OFFICER, 1, 1, ALLOWED, false, true, false));
		assertEquals(Reason.NONE, check(true, true, GuildRole.LEADER, GuildRole.OFFICER, 1, 1, ALLOWED, false, true, false));
		assertEquals(Reason.LEVEL, check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 4, 5, ALLOWED, false, true, false));
		assertEquals(Reason.PERMISSION, check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 5, 5, () -> false, false, true, false));
		assertEquals(Reason.HORDE, check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 1, 1, ALLOWED, true, true, false));
		assertEquals(Reason.OUTSIDE, check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 1, 1, ALLOWED, false, false, false));
		assertEquals(Reason.COMBAT, check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 1, 1, ALLOWED, false, true, true));
	}

	@Test
	void lastingReasonsComeBeforeSituationalOnes() {
		// No permission and outside: the lasting reason is shown (entering the territory would not help).
		assertEquals(Reason.PERMISSION, check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 1, 1, () -> false, true, false, true));
		// The horde wins over "outside": the player learns why flight will not come back at the border.
		assertEquals(Reason.HORDE, check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 1, 1, ALLOWED, true, false, true));
		assertEquals(Reason.OUTSIDE, check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 1, 1, ALLOWED, false, false, true));
		// Creative / spectator are never managed, whatever the rest.
		assertEquals(Reason.GAME_MODE, check(true, false, null, GuildRole.MEMBER, 0, 1, () -> false, true, false, true));
	}

	@Test
	void permissionIsReadOnlyWhenNeeded() {
		AtomicInteger reads = new AtomicInteger();
		BooleanSupplier counted = () -> {
			reads.incrementAndGet();
			return true;
		};
		check(true, true, null, GuildRole.MEMBER, 0, 1, counted, false, true, false);
		check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 1, 3, counted, false, true, false);
		assertEquals(0, reads.get(), "no guild / level too low: the permission lookup is skipped");
		check(true, true, GuildRole.MEMBER, GuildRole.MEMBER, 1, 1, counted, false, true, false);
		assertEquals(1, reads.get());
	}

	@Test
	void switchingOnIsRefusedOnlyForLastingReasons() {
		assertTrue(FlyRules.canSwitchOn(Reason.NONE));
		assertTrue(FlyRules.canSwitchOn(Reason.OUTSIDE));
		assertTrue(FlyRules.canSwitchOn(Reason.HORDE));
		assertTrue(FlyRules.canSwitchOn(Reason.COMBAT));
		assertTrue(FlyRules.canSwitchOn(Reason.GAME_MODE));
		assertFalse(FlyRules.canSwitchOn(Reason.DISABLED));
		assertFalse(FlyRules.canSwitchOn(Reason.NO_GUILD));
		assertFalse(FlyRules.canSwitchOn(Reason.ROLE));
		assertFalse(FlyRules.canSwitchOn(Reason.LEVEL));
		assertFalse(FlyRules.canSwitchOn(Reason.PERMISSION));
	}

	@Test
	void combatCooldown() {
		assertFalse(FlyRules.inCombat(1000, 990, 0), "0 = off");
		assertFalse(FlyRules.inCombat(1000, Long.MIN_VALUE, 10), "never hurt");
		assertTrue(FlyRules.inCombat(1000, 1000, 10));
		assertTrue(FlyRules.inCombat(1199, 1000, 10));
		assertFalse(FlyRules.inCombat(1200, 1000, 10), "10 s = 200 ticks later: free again");
		assertEquals(10, FlyRules.combatSecondsLeft(1000, 1000, 10));
		assertEquals(1, FlyRules.combatSecondsLeft(1199, 1000, 10));
		assertEquals(0, FlyRules.combatSecondsLeft(1200, 1000, 10));
	}

	@Test
	void messageCooldown() {
		assertTrue(FlyRules.ready(5, Long.MIN_VALUE, 40), "never sent");
		assertFalse(FlyRules.ready(100, 70, 40));
		assertTrue(FlyRules.ready(110, 70, 40));
	}

	@Test
	void fallProtection() {
		assertTrue(FlyRules.needsFallProtection(false, false), "in the air");
		assertTrue(FlyRules.needsFallProtection(true, true), "flying");
		assertFalse(FlyRules.needsFallProtection(true, false), "standing on the ground");
		// armed at 100, deadline 700
		assertFalse(FlyRules.fallProtectionOver(103, 100, 700, true, 5), "too early to trust the ground flag");
		assertTrue(FlyRules.fallProtectionOver(105, 100, 700, true, 5), "landed: over");
		assertFalse(FlyRules.fallProtectionOver(600, 100, 700, false, 5), "still falling");
		assertTrue(FlyRules.fallProtectionOver(701, 100, 700, false, 5), "deadline");
	}

	@Test
	void borderWarningAroundAndAhead() {
		// A 3 x 1 strip of chunks along x: chunks (0,0), (1,0), (2,0).
		Set<Long> claims = new HashSet<>(Set.of(ChunkKey.of(0, 0), ChunkKey.of(1, 0), ChunkKey.of(2, 0)));
		LongPredicate owned = claims::contains;
		// Middle of the strip along x, but only 8 blocks from the z borders.
		assertFalse(FlyRules.nearBorder(24, 8, 0, 0, 6, owned), "8 blocks from every border, warning at 6");
		assertTrue(FlyRules.nearBorder(24, 3, 0, 0, 6, owned), "3 blocks from the north border");
		assertTrue(FlyRules.nearBorder(44, 8, 0, 0, 6, owned), "4 blocks from the east border");
		assertFalse(FlyRules.nearBorder(24, 3, 0, 0, 0, owned), "0 = never");
		// Fast flight towards the east end: 4 blocks per check interval, 20 blocks from the border.
		assertTrue(FlyRules.nearBorder(28, 8, 4, 0, 6, owned), "border on the path");
		assertFalse(FlyRules.nearBorder(28, 8, -1, 0, 6, owned), "slow, away from the end: nothing ahead");
	}

	@Test
	void configDefaultsAndBounds() {
		GuildyConfig defaults = Json5.fromString("{}", GuildyConfig.class);
		defaults.validate();
		assertTrue(defaults.fly.enabled);
		assertEquals(1, defaults.fly.minGuildLevel);
		assertEquals(GuildRole.MEMBER, defaults.fly.role());
		assertTrue(defaults.fly.disableDuringWaves);
		assertEquals(0, defaults.fly.combatCooldownSeconds);

		GuildyConfig edited = Json5.fromString("{ \"fly\": { \"minRole\": \"officer\", \"minGuildLevel\": -3, \"borderWarningBlocks\": 99,"
				+ " \"fallProtectionSeconds\": 0, \"combatCooldownSeconds\": -1 } }", GuildyConfig.class);
		edited.validate();
		assertEquals(GuildRole.OFFICER, edited.fly.role());
		assertEquals("OFFICER", edited.fly.minRole);
		assertEquals(1, edited.fly.minGuildLevel);
		assertEquals(16, edited.fly.borderWarningBlocks);
		assertEquals(5, edited.fly.fallProtectionSeconds);
		assertEquals(0, edited.fly.combatCooldownSeconds);

		GuildyConfig bad = Json5.fromString("{ \"fly\": { \"minRole\": \"captain\" } }", GuildyConfig.class);
		bad.validate();
		assertEquals(GuildRole.MEMBER, bad.fly.role(), "unknown role: MEMBER");
	}
}
