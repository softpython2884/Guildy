package dev.guildy.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestFormatTest {

	@Test
	void progressBar() {
		assertEquals("§a§7||||||||||", QuestFormat.bar(0, 10, 10));
		assertEquals("§a|||||§7|||||", QuestFormat.bar(5, 10, 10));
		assertEquals("§a||||||||||§7", QuestFormat.bar(10, 10, 10));
		// Any progress shows at least one segment; unfinished never looks full.
		assertEquals("§a|§7|||||||||", QuestFormat.bar(1, 1000, 10));
		assertEquals("§a|||||||||§7|", QuestFormat.bar(999, 1000, 10));
	}

	@Test
	void durations() {
		assertEquals("0 s", QuestFormat.duration(-5));
		assertEquals("45 s", QuestFormat.duration(45 * 20));
		assertEquals("12 min", QuestFormat.duration(12 * 60 * 20));
		assertEquals("2 h 05 min", QuestFormat.duration((2 * 60 + 5) * 60 * 20L));
		assertEquals("3 j 4 h", QuestFormat.duration((76L * 60) * 60 * 20));
		assertEquals(1, QuestFormat.minutesCeil(1));
		assertEquals(2, QuestFormat.minutesCeil(1201));
		assertEquals(0, QuestFormat.minutesCeil(0));
	}
}
