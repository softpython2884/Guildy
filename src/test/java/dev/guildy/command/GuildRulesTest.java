package dev.guildy.command;

import dev.guildy.data.GuildRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildRulesTest {

	@Test
	void kickRequiresStrictlyHigherRole() {
		assertTrue(GuildRules.canKick(GuildRole.LEADER, GuildRole.OFFICER));
		assertTrue(GuildRules.canKick(GuildRole.LEADER, GuildRole.MEMBER));
		assertTrue(GuildRules.canKick(GuildRole.OFFICER, GuildRole.MEMBER));
		assertFalse(GuildRules.canKick(GuildRole.OFFICER, GuildRole.OFFICER));
		assertFalse(GuildRules.canKick(GuildRole.OFFICER, GuildRole.LEADER));
		assertFalse(GuildRules.canKick(GuildRole.MEMBER, GuildRole.MEMBER));
		assertFalse(GuildRules.canKick(GuildRole.LEADER, GuildRole.LEADER));
	}

	@Test
	void promotionAndDemotionOnlyBetweenMemberAndOfficer() {
		assertEquals(GuildRole.OFFICER, GuildRules.promoted(GuildRole.MEMBER));
		assertNull(GuildRules.promoted(GuildRole.OFFICER));
		assertNull(GuildRules.promoted(GuildRole.LEADER));
		assertEquals(GuildRole.MEMBER, GuildRules.demoted(GuildRole.OFFICER));
		assertNull(GuildRules.demoted(GuildRole.MEMBER));
		assertNull(GuildRules.demoted(GuildRole.LEADER));
	}

	@Test
	void memberLimit() {
		assertFalse(GuildRules.isFull(5, 0), "0 = unlimited");
		assertFalse(GuildRules.isFull(19, 20));
		assertTrue(GuildRules.isFull(20, 20));
		assertTrue(GuildRules.isFull(25, 20));
	}

	@Test
	void namesAreTrimmedAndCollapsed() {
		assertEquals("Les Loups", GuildRules.normalizeName("  Les    Loups \t"));
		assertEquals("", GuildRules.normalizeName(null));
		assertEquals("L'Ordre", GuildRules.normalizeName("L'Ordre"));
	}

	@Test
	void treasuryClampedAtZero() {
		assertEquals(0, GuildRules.clampTreasury(10, -50));
		assertEquals(5, GuildRules.clampTreasury(10, -5));
		assertEquals(60, GuildRules.clampTreasury(10, 50));
		assertEquals(Long.MAX_VALUE, GuildRules.clampTreasury(Long.MAX_VALUE - 1, 10));
	}

	@Test
	void progress() {
		assertEquals(0, GuildRules.percent(0, 100));
		assertEquals(50, GuildRules.percent(50, 100));
		assertEquals(100, GuildRules.percent(150, 100));
		assertEquals(100, GuildRules.percent(5, 0));
		assertEquals(0, GuildRules.percent(-5, 100));

		assertEquals(0, GuildRules.filledSegments(0, 100, 10));
		assertEquals(4, GuildRules.filledSegments(49, 100, 10));
		assertEquals(10, GuildRules.filledSegments(100, 100, 10));
		assertEquals(10, GuildRules.filledSegments(3, 0, 10));
		assertEquals(0, GuildRules.filledSegments(3, 10, 0));

		assertEquals("&a||&8|||", GuildRules.progressBar(40, 100, 5, "&a", "&8", '|'));
	}

	@Test
	void levelProgressInsideCurrentLevel() {
		// level 2 starts at 100 XP, level 3 at 250 XP; the guild has 175 XP.
		assertArrayEquals(new long[]{75, 150}, GuildRules.levelProgress(175, 100, 250, false));
		assertArrayEquals(new long[]{0, 150}, GuildRules.levelProgress(90, 100, 250, false));
		assertArrayEquals(new long[]{150, 150}, GuildRules.levelProgress(400, 100, 250, false));
		assertArrayEquals(new long[]{0, 0}, GuildRules.levelProgress(5000, 3200, 3200, true));
	}

	@Test
	void paging() {
		assertEquals(1, GuildRules.pageCount(0, 8));
		assertEquals(1, GuildRules.pageCount(8, 8));
		assertEquals(2, GuildRules.pageCount(9, 8));
		assertEquals(0, GuildRules.clampPage(-3, 4));
		assertEquals(3, GuildRules.clampPage(10, 4));
		assertEquals(0, GuildRules.clampPage(5, 0));

		List<Integer> values = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			values.add(i);
		}
		assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), GuildRules.page(values, 0, 8));
		assertEquals(List.of(16, 17, 18, 19), GuildRules.page(values, 2, 8));
		assertTrue(GuildRules.page(values, 3, 8).isEmpty());
		assertTrue(GuildRules.page(values, -1, 8).isEmpty());
	}

	@Test
	void rankingByLevelThenXpThenName() {
		assertTrue(GuildRules.compareRanking(3, 0, "B", 2, 9999, "A") < 0, "higher level first");
		assertTrue(GuildRules.compareRanking(2, 500, "B", 2, 400, "A") < 0, "more XP first");
		assertTrue(GuildRules.compareRanking(2, 400, "alpha", 2, 400, "Beta") < 0, "then name, case-insensitive");
		assertEquals(0, GuildRules.compareRanking(1, 0, "x", 1, 0, "X"));
	}

	@Test
	void wrapKeepsWordsAndCarriesColors() {
		assertEquals(List.of("§cVotre rôle ne", "§cpermet pas cette", "§caction."),
				GuildRules.wrapLegacy("§cVotre rôle ne permet pas cette action.", 17));
		assertEquals(List.of("short"), GuildRules.wrapLegacy("short", 30));
		assertEquals(List.of(""), GuildRules.wrapLegacy("", 30));
		// a word longer than the width stays whole
		assertEquals(List.of("&aabcdefghijkl", "&amn"), GuildRules.wrapLegacy("&aabcdefghijkl mn", 5));
	}

	@Test
	void formatCodes() {
		assertEquals(5, GuildRules.visibleLength("§a§lHello"));
		assertEquals(6, GuildRules.visibleLength("&zHell"), "unknown code letters are visible");
		assertEquals("§c§l", GuildRules.activeFormat("§aGreen §c§lRed bold"));
		assertEquals("", GuildRules.activeFormat("§aGreen §rplain"));
		assertEquals("&e", GuildRules.activeFormat("&l bold &e yellow"));
	}
}
