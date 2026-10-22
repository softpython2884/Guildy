package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The live index of what the filtered-room chests hold: kinds per chest, chests per kind. */
class ContentIndexTest {
	private static long[] sorted(long[] values) {
		long[] copy = values.clone();
		Arrays.sort(copy);
		return copy;
	}

	@Test
	void chestsHoldingAKindAreFoundAndEmptyChestsHoldNothing() {
		ContentIndex index = new ContentIndex();
		assertTrue(index.update(1, List.of("minecraft:diorite", "minecraft:granite")));
		assertTrue(index.update(2, List.of("minecraft:wheat")));
		assertFalse(index.update(3, List.of()), "an empty chest changes nothing");
		assertFalse(index.update(1, List.of("minecraft:granite", "minecraft:diorite")), "same kinds, any order");
		assertArrayEquals(new long[]{1}, index.holders("minecraft:diorite"));
		assertArrayEquals(new long[]{2}, index.holders("minecraft:wheat"));
		assertEquals(0, index.holders("minecraft:oak_log").length, "nobody holds logs");
		assertTrue(index.kinds(3).isEmpty(), "the empty chest holds nothing");
		assertTrue(index.known(3));
		assertFalse(index.known(4));
		assertTrue(index.holds(1, "minecraft:granite"));
		assertFalse(index.holds(3, "minecraft:granite"));
	}

	@Test
	void aKindAddedOrRemovedByHandChangesTheFilter() {
		ContentIndex index = new ContentIndex();
		index.update(1, List.of("minecraft:diorite"));
		index.update(2, List.of("minecraft:diorite"));
		Set<String> view = index.kinds(2);
		// A player puts andesite in chest 2, and takes the last diorite out of chest 1.
		assertTrue(index.update(2, List.of("minecraft:diorite", "minecraft:andesite")));
		assertTrue(index.update(1, List.of()));
		assertArrayEquals(new long[]{2}, index.holders("minecraft:diorite"));
		assertArrayEquals(new long[]{2}, index.holders("minecraft:andesite"));
		assertEquals(Set.of("minecraft:diorite", "minecraft:andesite"), view, "the live view follows the chest");
		assertEquals(1, index.holderCount("minecraft:andesite"));
		assertEquals(0, index.holderCount("minecraft:granite"));
	}

	@Test
	void chestsLeavingTheRoomsAreForgotten() {
		ContentIndex index = new ContentIndex();
		index.update(1, List.of("minecraft:diorite"));
		index.update(2, List.of("minecraft:diorite", "minecraft:wheat"));
		index.update(3, List.of("minecraft:wheat"));
		index.remove(1);
		assertArrayEquals(new long[]{2}, index.holders("minecraft:diorite"));
		index.retain(Set.of(3L));
		assertEquals(0, index.holders("minecraft:diorite").length);
		assertArrayEquals(new long[]{3}, index.holders("minecraft:wheat"));
		assertEquals(1, index.size());
		assertFalse(index.known(2));
	}

	@Test
	void otherIdentityRulesForgetEveryKindButKeepTheViews() {
		ContentIndex index = new ContentIndex();
		assertFalse(index.useRules(ContentKeys.Rules.DEFAULT), "first rules on an empty index");
		index.update(1, List.of("minecraft:potion[minecraft:healing]"));
		index.update(2, List.of("minecraft:potion[minecraft:healing]", "minecraft:stone"));
		Set<String> view = index.kinds(1);
		assertFalse(index.useRules(ContentKeys.Rules.DEFAULT), "same rules: nothing forgotten");
		assertArrayEquals(new long[]{1, 2}, sorted(index.holders("minecraft:potion[minecraft:healing]")));
		assertTrue(index.useRules(new ContentKeys.Rules(ContentKeys.PotionMatch.EXACT, ContentKeys.BookMatch.ITEM)));
		assertEquals(0, index.holders("minecraft:potion[minecraft:healing]").length);
		assertFalse(index.known(1), "read again later");
		index.update(1, List.of("minecraft:potion[minecraft:strong_healing]"));
		assertEquals(Set.of("minecraft:potion[minecraft:strong_healing]"), view, "the same live view");
	}
}
