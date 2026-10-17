package dev.guildy.ruin;

import dev.guildy.ruin.RoomGuardRules.Cell;
import dev.guildy.ruin.RoomGuardRules.Leash;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Room guards: how many, where (markers or fallback posts), when they appear and leave, leash, ritual block. */
class RoomGuardRulesTest {
	private static final List<Integer> BY_TIER = List.of(2, 3, 4, 5, 6);

	@Test
	void countByTierPlayersAndThreat() {
		// count(fixed, byTier, tier, players, perExtraPlayer, threat, threatLevelsPerGuard, max)
		assertEquals(2, RoomGuardRules.count(-1, BY_TIER, 1, 1, 1, 1, 2, 12), "tier 1, alone, threat I");
		assertEquals(6, RoomGuardRules.count(-1, BY_TIER, 5, 1, 1, 1, 2, 12), "tier 5");
		assertEquals(5, RoomGuardRules.count(-1, BY_TIER, 3, 2, 1, 1, 2, 12), "one more guard per extra player");
		assertEquals(4 + 1, RoomGuardRules.count(-1, BY_TIER, 3, 1, 1, 3, 2, 12), "one more guard every 2 threat levels (III)");
		assertEquals(4 + 4, RoomGuardRules.count(-1, BY_TIER, 3, 1, 1, 9, 2, 12), "threat IX: +4");
		assertEquals(4, RoomGuardRules.count(-1, BY_TIER, 3, 1, 1, 9, 0, 12), "threat never adds guards with 0");
		assertEquals(12, RoomGuardRules.count(-1, BY_TIER, 5, 8, 1, 10, 2, 12), "capped");
		assertEquals(0, RoomGuardRules.count(-1, BY_TIER, 5, 8, 1, 10, 2, 0), "max 0: no guard");
		assertEquals(7, RoomGuardRules.count(7, BY_TIER, 1, 1, 1, 1, 2, 12), "fixed count of the ruin");
		assertEquals(0, RoomGuardRules.count(0, BY_TIER, 5, 6, 1, 9, 2, 12), "count 0: never any guard, whatever the group");
		assertEquals(6, RoomGuardRules.count(-1, BY_TIER, 9, 1, 1, 1, 2, 12), "tiers above the list use its last value");
		assertEquals(2, RoomGuardRules.count(-1, BY_TIER, 0, 0, 1, 0, 2, 12), "bad inputs are bounded");
		assertEquals(0, RoomGuardRules.count(-1, List.of(), 3, 4, 1, 5, 2, 12), "empty list: no guard");
		assertEquals(0, RoomGuardRules.count(-1, List.of(0, 0, 0, 0, 0), 3, 4, 1, 5, 2, 12), "tier without guards: none, even in a group");
	}

	@Test
	void appearAndLeave() {
		assertTrue(RoomGuardRules.spawns(true, RuinState.DORMANT, false, false, true));
		assertFalse(RoomGuardRules.spawns(false, RuinState.DORMANT, false, false, true), "disabled");
		assertFalse(RoomGuardRules.spawns(true, RuinState.AWAKE, false, false, true), "the guardian is awake");
		assertFalse(RoomGuardRules.spawns(true, RuinState.CLEARED, false, false, true), "defeated ruin");
		assertFalse(RoomGuardRules.spawns(true, RuinState.DORMANT, true, false, true), "fight or ritual running");
		assertFalse(RoomGuardRules.spawns(true, RuinState.DORMANT, false, true, true), "once per dormant period: killed guards stay dead");
		assertFalse(RoomGuardRules.spawns(true, RuinState.DORMANT, false, false, false), "no intruder");

		assertFalse(RoomGuardRules.resets(59, 60));
		assertTrue(RoomGuardRules.resets(60, 60), "nobody near for resetSeconds");
		assertTrue(RoomGuardRules.resets(1, 0), "at least one second");
	}

	@Test
	void ritualBlockedWhileGuardsLive() {
		assertTrue(RoomGuardRules.blocksRitual(true, 1));
		assertTrue(RoomGuardRules.blocksRitual(true, 5));
		assertFalse(RoomGuardRules.blocksRitual(true, 0), "every guard of the arena is dead");
		assertFalse(RoomGuardRules.blocksRitual(false, 5), "guards.blockRitual = false");
	}

	@Test
	void leash() {
		assertEquals(Leash.STAY, RoomGuardRules.leash(9 * 9, 10, false));
		assertEquals(Leash.RETURN, RoomGuardRules.leash(11 * 11, 10, false), "too far: back to its post");
		assertEquals(Leash.STAY, RoomGuardRules.leash(11 * 11, 10, true), "chasing an intruder near its post");
		assertEquals(Leash.TELEPORT, RoomGuardRules.leash(21 * 21, 10, true), "lured away: brought back");
		assertEquals(Leash.TELEPORT, RoomGuardRules.leash(3 * 3, 0, false), "radius at least one block");
	}

	@Test
	void fallbackPostsWithoutGuardMarkers() {
		// The test ruin: 13 x 13 floor, guardian in the middle, escort markers in two corners, entrance on the north edge.
		Cell boss = new Cell(6, 1, 6);
		List<Cell> minions = List.of(new Cell(2, 1, 2), new Cell(10, 1, 10));
		Cell entrance = new Cell(6, 1, 0);
		List<Cell> posts = RoomGuardRules.fallbackPosts(boss, entrance, minions, 0, 0, 12, 12, 7, 6);
		assertEquals(minions, posts.subList(0, 2), "the escort markers first");
		assertEquals(new Cell(6, 1, 3), posts.get(2), "just inside the entrance, towards the guardian");
		assertTrue(posts.containsAll(List.of(new Cell(4, 1, 3), new Cell(8, 1, 3))), "both sides of the doorway: " + posts);
		assertEquals(2 + 3 + 6, posts.size(), "then a circle of 6 posts: " + posts);
		assertEquals(posts.size(), new HashSet<>(posts).size(), "no duplicate");
		for (Cell post : posts) {
			assertTrue(post.x() > 0 && post.x() < 12 && post.z() > 0 && post.z() < 12, "inside the ruin, off its walls: " + post);
		}
		for (Cell post : posts.subList(5, posts.size())) {
			double r = Math.sqrt(post.distanceSq(boss));
			assertTrue(r >= 4 && r <= 5.8, "circle limited by the ruin (radius 5 here, not 7): " + post + " at " + r);
		}

		// No entrance, no escort marker: only the circle (and nothing when the ruin is too small for one).
		assertEquals(4, RoomGuardRules.fallbackPosts(boss, null, List.of(), 0, 0, 12, 12, 7, 4).size());
		assertTrue(RoomGuardRules.fallbackPosts(new Cell(1, 1, 1), null, List.of(), 0, 0, 2, 2, 7, 6).isEmpty(), "no room for a circle");
		// A marker on the wall line is skipped.
		assertTrue(RoomGuardRules.fallbackPosts(boss, null, List.of(new Cell(0, 1, 5)), 0, 0, 12, 12, 7, 0).isEmpty());
	}

	@Test
	void everyPostBeforeASecondGuard() {
		assertEquals(List.of(0, 1, 2, 0, 1), RoomGuardRules.assign(3, 5, null), "in order, round robin");
		assertEquals(List.of(0, 1), RoomGuardRules.assign(4, 2, null), "fewer guards than posts: the first posts");
		assertTrue(RoomGuardRules.assign(0, 3, null).isEmpty());
		assertTrue(RoomGuardRules.assign(3, 0, null).isEmpty());
		List<Integer> shuffled = RoomGuardRules.assign(6, 6, new Random(42));
		assertEquals(Set.of(0, 1, 2, 3, 4, 5), new HashSet<>(shuffled), "shuffled marker posts: each one gets a guard");
		List<Integer> twice = RoomGuardRules.assign(3, 7, new Random(7));
		List<Integer> counts = new ArrayList<>(List.of(0, 0, 0));
		twice.forEach(i -> counts.set(i, counts.get(i) + 1));
		assertTrue(counts.stream().allMatch(c -> c == 2 || c == 3), "spread evenly: " + counts);
	}

	@Test
	void forTier() {
		assertEquals(2, RoomGuardRules.forTier(BY_TIER, 1));
		assertEquals(6, RoomGuardRules.forTier(BY_TIER, 5));
		assertEquals(2, RoomGuardRules.forTier(BY_TIER, -3));
		assertEquals(0, RoomGuardRules.forTier(null, 3));
	}
}
