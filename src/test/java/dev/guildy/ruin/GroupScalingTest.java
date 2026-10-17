package dev.guildy.ruin;

import dev.guildy.ruin.GroupScaling.Party;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Group scaling: players counted, multipliers, cap, combination with the threat level, shares of the gold. */
class GroupScalingTest {
	private static final double EPS = 1.0E-9;

	private static RuinConfig.ScalingSettings defaults() {
		RuinConfig cfg = new RuinConfig();
		cfg.rebuild(id -> true, id -> true, id -> true);
		return cfg.scaling;
	}

	@Test
	void defaultValues() {
		RuinConfig.ScalingSettings s = defaults();
		assertTrue(s.enabled);
		assertEquals(8, s.maxPlayers);
		assertEquals(0.5, s.healthPerPlayer);
		assertEquals(0.15, s.damagePerPlayer);
		assertTrue(s.shareGold);
	}

	@Test
	void aloneNothingChanges() {
		assertSame(Party.NONE, GroupScaling.party(defaults(), 1));
		assertSame(Party.NONE, GroupScaling.party(defaults(), 0), "nobody counted: the intruder alone");
		assertFalse(Party.NONE.group());
		RuinConfig.ScalingSettings off = defaults();
		off.enabled = false;
		assertSame(Party.NONE, GroupScaling.party(off, 6), "disabled");
		assertSame(Party.NONE, GroupScaling.party(null, 6));
	}

	@Test
	void groupMultipliers() {
		Party two = GroupScaling.party(defaults(), 2);
		assertEquals(2, two.players());
		assertTrue(two.group());
		assertEquals(1.5, two.health(), EPS, "+50 % health per extra player");
		assertEquals(1.15, two.damage(), EPS, "+15 % damage per extra player");
		assertEquals(0, two.extraMinions(), "0.5 minion per extra player, rounded down");
		assertEquals(1.5, two.gold(), EPS);
		assertEquals(1.5, two.xp(), EPS);

		Party four = GroupScaling.party(defaults(), 4);
		assertEquals(2.5, four.health(), EPS);
		assertEquals(1.45, four.damage(), EPS);
		assertEquals(1, four.extraMinions());
		assertEquals(150, GroupScaling.percent(four.health()), "+150 % health");
		assertEquals(45, GroupScaling.percent(four.damage()));
	}

	@Test
	void cappedByMaxPlayers() {
		assertEquals(8, GroupScaling.players(20, 8));
		assertEquals(1, GroupScaling.players(-2, 8));
		assertEquals(1, GroupScaling.players(5, 0), "max at least one");
		Party many = GroupScaling.party(defaults(), 20);
		assertEquals(8, many.players());
		assertEquals(1 + 0.5 * 7, many.health(), EPS);
		assertEquals(3, many.extraMinions(), "floor(0.5 x 7)");
	}

	@Test
	void combinedWithTheThreatLevel() {
		RuinConfig cfg = new RuinConfig();
		cfg.rebuild(id -> true, id -> true, id -> true);
		ThreatRules.Scaling threat = ThreatRules.scaling(cfg.escalation, 3);
		Party three = GroupScaling.party(cfg.scaling, 3);
		assertEquals(1.4, threat.health(), EPS, "Menace III: +40 %");
		assertEquals(2.0, three.health(), EPS, "3 players: +100 %");
		assertEquals(2.8, GroupScaling.combined(threat.health(), three.health()), EPS, "multiplicative: 1.4 x 2.0");
		assertEquals(1.2 * 1.3, GroupScaling.combined(threat.damage(), three.damage()), EPS);
		assertEquals(threat.health(), GroupScaling.combined(threat.health(), Party.NONE.health()), EPS, "alone: only the threat");
		assertEquals(three.health(), GroupScaling.combined(ThreatRules.Scaling.NONE.health(), three.health()), EPS, "Menace I: only the group");
		assertEquals(0, GroupScaling.combined(-1, 2), EPS);
		// Rewards: threat x group x risen bonus.
		ThreatRules.Rewards rewards = ThreatRules.rewards(cfg.escalation, cfg.resurrection, 3, false);
		assertEquals(ThreatRules.scaled(10, 1.3 * 2.0), ThreatRules.scaled(10, GroupScaling.combined(rewards.gold(), three.gold())), "26 gold");
	}

	@Test
	void sharesOfTheGold() {
		assertArrayEquals(new int[] {8, 7}, GroupScaling.shares(15, 2));
		assertArrayEquals(new int[] {5, 5, 5}, GroupScaling.shares(15, 3));
		assertArrayEquals(new int[] {15}, GroupScaling.shares(15, 1));
		assertArrayEquals(new int[] {15}, GroupScaling.shares(15, 0), "nobody to share with: one pile");
		assertArrayEquals(new int[] {1, 1, 0, 0}, GroupScaling.shares(2, 4));
		assertEquals(15, Arrays.stream(GroupScaling.shares(15, 4)).sum(), "nothing lost");
		assertArrayEquals(new int[] {0, 0}, GroupScaling.shares(-3, 2));
	}

	@Test
	void brokenSettingsAreBounded() {
		RuinConfig cfg = new RuinConfig();
		cfg.scaling.maxPlayers = 0;
		cfg.scaling.healthPerPlayer = -1;
		cfg.scaling.damagePerPlayer = 99;
		cfg.scaling.minionsPerPlayer = Double.NaN;
		assertTrue(cfg.rebuild(id -> true, id -> true, id -> true).isEmpty());
		assertEquals(1, cfg.scaling.maxPlayers);
		assertEquals(0, cfg.scaling.healthPerPlayer);
		assertEquals(2, cfg.scaling.damagePerPlayer);
		assertEquals(0, cfg.scaling.minionsPerPlayer);
		cfg.scaling = null;
		assertTrue(cfg.rebuild(id -> true, id -> true, id -> true).isEmpty(), "missing section: defaults");
		assertEquals(8, cfg.scaling.maxPlayers);
	}
}
