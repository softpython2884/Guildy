package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionsTest {

	@Test
	void anyItemCountsWhenNoTargetIsSet() {
		assertTrue(Missions.counts("", "minecraft:raw_iron"));
		assertTrue(Missions.counts(null, "minecraft:coal"));
	}

	@Test
	void onlyTheTargetItemCounts() {
		assertTrue(Missions.counts("minecraft:raw_iron", "minecraft:raw_iron"));
		assertFalse(Missions.counts("minecraft:raw_iron", "minecraft:coal"));
	}

	@Test
	void quotaAccumulatesMatchingItemsOnly() {
		int progress = 0;
		progress = Missions.count(Goal.QUOTA, "minecraft:oak_log", progress, "minecraft:oak_log", 12);
		progress = Missions.count(Goal.QUOTA, "minecraft:oak_log", progress, "minecraft:stick", 30);
		progress = Missions.count(Goal.QUOTA, "minecraft:oak_log", progress, "minecraft:oak_log", 4);
		assertEquals(16, progress);
	}

	@Test
	void continuousAndFillGoalsDoNotCountItems() {
		assertEquals(3, Missions.count(Goal.CONTINUOUS, "", 3, "minecraft:coal", 10));
		assertEquals(3, Missions.count(Goal.FILL, "", 3, "minecraft:coal", 10));
	}

	@Test
	void goalsCycle() {
		assertEquals(Goal.FILL, Goal.CONTINUOUS.next());
		assertEquals(Goal.QUOTA, Goal.FILL.next());
		assertEquals(Goal.CONTINUOUS, Goal.QUOTA.next());
	}
}
